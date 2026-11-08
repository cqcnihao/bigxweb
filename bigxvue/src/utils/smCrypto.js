import { sm4 } from 'sm-crypto'
import sm2Sm3 from 'sm-crypto/src/sm2/sm3.js'

const MAC_BINDING_DEFAULT = 'big-encrypt-etm-v1'
const MAC_BINDING_FALLBACK = 'big-encrypt-etm-v1'
const SM4_ALG_DEFAULT = 'SM4/CBC/PKCS7Padding'
const CBC_IV_LEN = 16

let runtimeKeys = null
let runtimeAlg = null
let runtimeMacBinding = null

const textEncoder = new TextEncoder()

function utf8ToBytes(str) {
  return textEncoder.encode(str)
}

function hexToBytes(hex) {
  const out = new Uint8Array(hex.length / 2)
  for (let i = 0; i < hex.length; i += 2) {
    out[i / 2] = parseInt(hex.substr(i, 2), 16)
  }
  return out
}

function bytesToHex(bytes) {
  let hex = ''
  for (let i = 0; i < bytes.length; i++) {
    hex += (bytes[i] < 16 ? '0' : '') + bytes[i].toString(16)
  }
  return hex
}

function base64ToBytes(b64) {
  const bin = atob(b64)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return out
}

function bytesToBase64(bytes) {
  let bin = ''
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i])
  return btoa(bin)
}

function randomBytes(n) {
  if (window.crypto && window.crypto.getRandomValues) {
    const out = new Uint8Array(n)
    window.crypto.getRandomValues(out)
    return out
  }
  const out = new Uint8Array(n)
  for (let i = 0; i < n; i++) out[i] = Math.floor(Math.random() * 256)
  return out
}

function resolveMacKeyBytes(sm4KeyBytes, keyId) {
  const binding = runtimeMacBinding || MAC_BINDING_DEFAULT
  const salt = sm2Sm3.sm3(Array.from(utf8ToBytes(binding + '|' + keyId)))
  const macKey = new Uint8Array(salt.length + sm4KeyBytes.length)
  macKey.set(salt, 0)
  macKey.set(sm4KeyBytes, salt.length)
  return macKey
}

function computeMacBase64(alg, keyId, cipherBase64, sm4KeyBytes) {
  const macKeyBytes = resolveMacKeyBytes(sm4KeyBytes, keyId)
  const binding = runtimeMacBinding || MAC_BINDING_DEFAULT
  const macPayload = alg + '|' + keyId + '|' + cipherBase64
  const macArr = sm2Sm3.hmac(
    Array.from(utf8ToBytes(macPayload)),
    Array.from(macKeyBytes)
  )
  return bytesToBase64(macArr)
}

export function initCryptoConfig(config) {
  if (!config) return
  runtimeKeys = config.sm4Keys || null
  runtimeAlg = config.sm4Alg || SM4_ALG_DEFAULT
  runtimeMacBinding = config.macBinding || MAC_BINDING_DEFAULT
  if (typeof window !== 'undefined') {
    try {
      sessionStorage.setItem('crypto_keys', JSON.stringify(runtimeKeys))
      sessionStorage.setItem('crypto_alg', runtimeAlg)
      sessionStorage.setItem('crypto_binding', runtimeMacBinding)
    } catch (e) {}
  }
}

export function loadCryptoConfigFromCache() {
  if (typeof window === 'undefined') return false
  try {
    const k = sessionStorage.getItem('crypto_keys')
    const a = sessionStorage.getItem('crypto_alg')
    const b = sessionStorage.getItem('crypto_binding')
    if (k) {
      runtimeKeys = JSON.parse(k)
      runtimeAlg = a || SM4_ALG_DEFAULT
      runtimeMacBinding = b || MAC_BINDING_DEFAULT
      return true
    }
  } catch (e) {}
  return false
}

function getSm4KeyBytes(keyId) {
  if (!runtimeKeys) {
    throw new Error('SM4 密钥未初始化，请先调用 initCryptoConfig() 或拉取 /auth/public-key')
  }
  const b64 = runtimeKeys[keyId]
  if (!b64) throw new Error('未配置 SM4 密钥: keyId=' + keyId)
  return base64ToBytes(b64)
}

function encrypt(plainText, keyId = null) {
  const alg = runtimeAlg || SM4_ALG_DEFAULT
  const useKeyId = keyId || Object.keys(runtimeKeys)[0]
  const sm4KeyBytes = getSm4KeyBytes(useKeyId)
  const sm4KeyHex = bytesToHex(sm4KeyBytes)

  const ivBytes = randomBytes(CBC_IV_LEN)
  const ivHex = bytesToHex(ivBytes)

  const cipherHex = sm4.encrypt(plainText, sm4KeyHex, { mode: 'cbc', iv: ivHex })

  const ivCipherBytes = new Uint8Array(CBC_IV_LEN + cipherHex.length / 2)
  ivCipherBytes.set(ivBytes, 0)
  ivCipherBytes.set(hexToBytes(cipherHex), CBC_IV_LEN)
  const cipherBase64 = bytesToBase64(ivCipherBytes)

  const macBase64 = computeMacBase64(alg, useKeyId, cipherBase64, sm4KeyBytes)

  return {
    alg,
    keyId: useKeyId,
    cipher: cipherBase64,
    mac: macBase64,
    timestamp: Date.now(),
    nonce: bytesToHex(randomBytes(16))
  }
}

function decrypt(envelope, keyId) {
  const useKeyId = keyId || envelope.keyId || Object.keys(runtimeKeys)[0]
  const useAlg = envelope.alg || runtimeAlg || SM4_ALG_DEFAULT
  const sm4KeyBytes = getSm4KeyBytes(useKeyId)
  const sm4KeyHex = bytesToHex(sm4KeyBytes)

  const ivCipherBytes = base64ToBytes(envelope.cipher)
  if (ivCipherBytes.length <= CBC_IV_LEN) {
    throw new Error('密文长度非法')
  }
  const ivHex = bytesToHex(ivCipherBytes.subarray(0, CBC_IV_LEN))
  const cipherHex = bytesToHex(ivCipherBytes.subarray(CBC_IV_LEN))

  if (envelope.mac) {
    const expectedMac = computeMacBase64(useAlg, useKeyId, envelope.cipher, sm4KeyBytes)
    if (envelope.mac !== expectedMac) {
      throw new Error('MAC 校验失败，报文完整性被破坏')
    }
  }

  return sm4.decrypt(cipherHex, sm4KeyHex, { mode: 'cbc', iv: ivHex })
}

function decryptPayload(data) {
  if (!data || typeof data !== 'object') return data
  if (data.encrypt !== true) return data
  const plainJson = decrypt(data, data.keyId)
  try {
    return JSON.parse(plainJson)
  } catch (e) {
    return plainJson
  }
}

const smCrypto = {
  encrypt,
  decrypt,
  decryptPayload,
  SM4_ALG: SM4_ALG_DEFAULT,
  MAC_BINDING: MAC_BINDING_DEFAULT,
  initCryptoConfig,
  loadCryptoConfigFromCache,
  get isReady() { return !!runtimeKeys }
}

export default smCrypto