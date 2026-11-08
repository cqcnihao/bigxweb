import axios from 'axios';
import smCrypto from 'sm-crypto';
const { sm4, sm3 } = smCrypto;
import CryptoJS from 'crypto-js';
import { Buffer } from 'buffer';

const GW = 'http://localhost:8080';
const SIGN_SECRET = 'dev-gateway-secret-do-not-use-in-prod';

function gwHeaders(method, path) {
  const ts = String(Date.now());
  const payload = 'big-gateway|' + ts + '|' + method + '|' + path;
  const sign = CryptoJS.HmacSHA256(SIGN_SECRET, payload).toString(CryptoJS.enc.Hex);
  return {
    'X-Gateway-Name': 'big-gateway',
    'X-Gateway-Timestamp': ts,
    'X-Gateway-Sign': sign,
    'Content-Type': 'application/json'
  };
}

function encryptReq(plain, sm4KeyHex, alg, keyId, macBinding) {
  const iv = '0123456789abcdef0123456789abcdef';
  const cipherHex = sm4.encrypt(plain, sm4KeyHex, { mode: 'cbc', iv });
  const cipherBase64 = Buffer.from(cipherHex, 'hex').toString('base64');
  const macKeyHex = sm3(Buffer.from(macBinding + '|' + keyId, 'utf-8')) + sm4KeyHex;
  const macData = alg + '|' + keyId + '|' + cipherBase64;
  const macHex = sm3(Buffer.from(macData, 'utf-8'));
  return {
    alg, keyId, cipher: cipherBase64, mac: macHex,
    timestamp: Date.now(),
    nonce: Math.random().toString(36).substring(2, 18)
  };
}

function decryptPayload(payload, sm4KeyHex) {
  if (!payload || !payload.encrypt) return payload;
  const cipherHex = Buffer.from(payload.cipher, 'base64').toString('hex');
  const jsonPlain = sm4.decrypt(cipherHex, sm4KeyHex, { mode: 'cbc', iv: '0123456789abcdef0123456789abcdef' });
  return JSON.parse(jsonPlain);
}

async function main() {
  console.log('=== 1. Get crypto config ===');
  const cfg = (await axios.get(GW + '/auth/public-key')).data.data;
  const sm4KeyHex = Buffer.from(cfg.sm4Keys.v1, 'base64').toString('hex');
  console.log('OK. keyId=v1, alg=' + cfg.sm4Alg);

  console.log('\n=== 2. Get captcha ===');
  const cap = (await axios.get(GW + '/auth/captcha')).data.data;
  console.log('captchaKey: ' + cap.captchaKey);

  console.log('\n=== 3. Encrypt login ===');
  const loginPlain = JSON.stringify({
    username: 'admin', password: 'admin123',
    captchaCode: '1234', captchaKey: cap.captchaKey
  });
  const envelope = encryptReq(loginPlain, sm4KeyHex, cfg.sm4Alg, 'v1', cfg.macBinding);
  console.log('Envelope: ' + JSON.stringify(envelope));

  console.log('\n=== 4. POST /auth/login ===');
  try {
    const loginResp = await axios.post(GW + '/auth/login', envelope, {
      headers: gwHeaders('POST', '/auth/login'), timeout: 10000
    });
    console.log('Status: ' + loginResp.status);
    const raw = loginResp.data;
    console.log('FULL RAW: ' + JSON.stringify(raw));
    console.log('Raw response keys: ' + Object.keys(raw).join(', '));
    console.log('Raw encrypt flag: ' + raw.encrypt);
    if (raw.data !== null && raw.data !== undefined) {
      console.log('Raw.data type: ' + typeof raw.data);
      if (typeof raw.data === 'object') {
        console.log('Raw.data keys: ' + Object.keys(raw.data).join(', '));
        console.log('Raw.data: ' + JSON.stringify(raw.data));
      } else {
        console.log('Raw.data value: ' + raw.data);
      }
    } else {
      console.log('Raw.data is NULL/UNDEFINED');
    }

    let token = null;
    if (raw.encrypt === true) {
      const inner = decryptPayload(raw, sm4KeyHex);
      console.log('Decrypted top-level: ' + JSON.stringify(inner).substring(0, 300));
      token = inner.token;
    } else if (raw.success && raw.data) {
      if (raw.data.encrypt === true) {
        const inner = decryptPayload(raw.data, sm4KeyHex);
        console.log('Decrypted data field: ' + JSON.stringify(inner).substring(0, 300));
        token = inner.token;
      } else {
        token = raw.data.token;
      }
    }
    console.log('TOKEN: ' + token);

    if (token) {
      console.log('\n=== 5. GET /api/product/page?current=1&size=10 ===');
      const prodResp = await axios.get(GW + '/api/product/page', {
        params: { current: 1, size: 10 },
        headers: { ...gwHeaders('GET', '/api/product/page'), Authorization: token },
        timeout: 10000
      });
      console.log('Status: ' + prodResp.status);
      const prodRaw = prodResp.data;
      console.log('Raw keys: ' + Object.keys(prodRaw).join(', '));
      console.log('Raw success: ' + prodRaw.success);
      console.log('Raw encrypt: ' + prodRaw.encrypt);
      if (prodRaw.data) {
        console.log('Raw.data type: ' + typeof prodRaw.data);
        if (typeof prodRaw.data === 'object') {
          console.log('Raw.data keys: ' + Object.keys(prodRaw.data).join(', '));
          console.log('Raw.data.encrypt: ' + prodRaw.data.encrypt);

          let pageData = prodRaw.data;
          if (prodRaw.data.encrypt === true) {
            pageData = decryptPayload(prodRaw.data, sm4KeyHex);
            console.log('DECRYPTED product page: ' + JSON.stringify(pageData).substring(0, 500));
          }
          if (pageData.records) {
            console.log('records count: ' + pageData.records.length);
            for (const r of pageData.records) {
              console.log('  - id=' + r.id + ' name=' + r.productName + ' sku=' + r.productCode);
            }
          } else {
            console.log('NO records field! Full data: ' + JSON.stringify(pageData).substring(0, 800));
          }
        }
      } else {
        console.log('NO data field! Full raw: ' + JSON.stringify(prodRaw).substring(0, 500));
      }
    }
  } catch (e) {
    console.log('ERROR status: ' + e.response?.status);
    console.log('ERROR body: ' + JSON.stringify(e.response?.data).substring(0, 800));
    console.log('Message: ' + e.message);
  }
}

main().catch(e => console.error('FATAL:', e));