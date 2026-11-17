import axios from 'axios';
import smCrypto from 'sm-crypto';
const { sm4, sm3, sm2 } = smCrypto;
import CryptoJS from 'crypto-js';
import { Buffer } from 'buffer';
import https from 'https';

// 网关默认走 HTTPS 自签证书，用 GATEWAY_URL 覆盖（如 http://localhost:8080）
const GW = process.env.GATEWAY_URL || 'https://localhost:8443';
const SIGN_SECRET = process.env.GATEWAY_VALIDATION_SECRET || 'dev-gateway-secret-do-not-use-in-prod';
const MAC_BINDING_DEFAULT = 'big-encrypt-etm-v1';
const CBC_IV_LEN = 16;

const httpClient = axios.create({
  httpsAgent: new https.Agent({ rejectUnauthorized: false }),
  proxy: false
});

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

// 与后端 SmCryptoService.resolveMacKey 一致：未配置独立 MAC 密钥时
// 由 SM3(binding|keyId) 的 32 字节摘要 + SM4 密钥 16 字节拼接派生
function resolveMacKeyBytes(sm4KeyBytes, keyId, macBinding) {
  const binding = macBinding || MAC_BINDING_DEFAULT;
  const saltHex = sm3(Buffer.from(binding + '|' + keyId, 'utf-8'));
  return Buffer.concat([Buffer.from(saltHex, 'hex'), sm4KeyBytes]);
}

// 与后端 SmCryptoService.mac 一致：HMAC-SM3(key, alg|keyId|cipher) 后取 Base64
function computeMacBase64(alg, keyId, cipherBase64, sm4KeyBytes, macBinding) {
  const macKeyBytes = resolveMacKeyBytes(sm4KeyBytes, keyId, macBinding);
  const payload = Buffer.from(alg + '|' + keyId + '|' + cipherBase64, 'utf-8');
  const macHex = sm3(payload, { mode: 'hmac', key: Array.from(macKeyBytes) });
  return Buffer.from(macHex, 'hex').toString('base64');
}

function signEnvelope(timestamp, nonce, cipher, privateKey) {
  const payload = timestamp + '\n' + nonce + '\n' + cipher;
  const derHex = sm2.doSignature(payload, privateKey, { der: true, hash: true });
  return Buffer.from(derHex, 'hex').toString('base64');
}

function encryptReq(plain, sm4KeyBase64, alg, keyId, macBinding, clientKey) {
  const sm4KeyBytes = Buffer.from(sm4KeyBase64, 'base64');
  const sm4KeyHex = sm4KeyBytes.toString('hex');
  const ivBytes = Buffer.from('0123456789abcdef0123456789abcdef', 'hex');
  const cipherHex = sm4.encrypt(plain, sm4KeyHex, { mode: 'cbc', iv: ivBytes.toString('hex') });
  // 后端约定 cipher = Base64(IV || 密文)
  const cipherBase64 = Buffer.concat([ivBytes, Buffer.from(cipherHex, 'hex')]).toString('base64');
  const mac = computeMacBase64(alg, keyId, cipherBase64, sm4KeyBytes, macBinding);
  const timestamp = Date.now();
  const nonce = Math.random().toString(36).substring(2, 18);
  const envelope = { alg, keyId, cipher: cipherBase64, mac, timestamp, nonce };
  if (clientKey) {
    envelope.sign = signEnvelope(timestamp, nonce, cipherBase64, clientKey.privateKey);
    if (clientKey.clientKeyId) envelope.clientKeyId = clientKey.clientKeyId;
  }
  return envelope;
}

function decryptPayload(payload, sm4KeyBase64) {
  if (!payload || !payload.encrypt) return payload;
  const sm4KeyBytes = Buffer.from(sm4KeyBase64, 'base64');
  const sm4KeyHex = sm4KeyBytes.toString('hex');
  const raw = Buffer.from(payload.cipher, 'base64');
  const ivHex = raw.subarray(0, CBC_IV_LEN).toString('hex');
  const cipherHex = raw.subarray(CBC_IV_LEN).toString('hex');
  const plain = sm4.decrypt(cipherHex, sm4KeyHex, { mode: 'cbc', iv: ivHex });
  return JSON.parse(plain);
}

async function main() {
  console.log('=== 0. Register client SM2 key ===');
  const keyPair = sm2.generateKeyPairHex();
  const regResp = await httpClient.post(GW + '/crypto/sm2/client-key',
    { publicKey: keyPair.publicKey },
    { headers: gwHeaders('POST', '/crypto/sm2/client-key'), timeout: 10000 });
  const clientKeyId = regResp.data?.data?.clientKeyId;
  console.log('clientKeyId: ' + clientKeyId);
  if (!clientKeyId) throw new Error('客户端公钥登记失败: ' + JSON.stringify(regResp.data));
  const clientKey = { privateKey: keyPair.privateKey, clientKeyId };

  console.log('\n=== 1. Get crypto config ===');
  const cfg = (await httpClient.get(GW + '/auth/public-key')).data.data;
  const sm4KeyBase64 = cfg.sm4Keys.v1;
  console.log('OK. keyId=v1, alg=' + cfg.sm4Alg);

  console.log('\n=== 2. Get captcha ===');
  const cap = (await httpClient.get(GW + '/auth/captcha')).data.data;
  console.log('captchaKey: ' + cap.captchaKey);

  console.log('\n=== 3. Encrypt login ===');
  const loginPlain = JSON.stringify({
    username: 'admin', password: 'admin123',
    captchaCode: '1234', captchaKey: cap.captchaKey
  });
  const envelope = encryptReq(loginPlain, sm4KeyBase64, cfg.sm4Alg, 'v1', cfg.macBinding, clientKey);
  console.log('Envelope sign: ' + (envelope.sign ? 'YES' : 'NO') + ', clientKeyId=' + envelope.clientKeyId);
  console.log('Envelope: ' + JSON.stringify(envelope));

  console.log('\n=== 4. POST /auth/login ===');
  try {
    const loginResp = await httpClient.post(GW + '/auth/login', envelope, {
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
      const inner = decryptPayload(raw, sm4KeyBase64);
      console.log('Decrypted top-level: ' + JSON.stringify(inner).substring(0, 300));
      token = inner.token;
    } else if (raw.success && raw.data) {
      if (raw.data.encrypt === true) {
        const inner = decryptPayload(raw.data, sm4KeyBase64);
        console.log('Decrypted data field: ' + JSON.stringify(inner).substring(0, 300));
        token = inner.token;
      } else {
        token = raw.data.token;
      }
    }
    console.log('TOKEN: ' + token);

    if (token) {
      console.log('\n=== 5. GET /api/product/page?current=1&size=10 ===');
      const prodResp = await httpClient.get(GW + '/api/product/page', {
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
            pageData = decryptPayload(prodRaw.data, sm4KeyBase64);
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