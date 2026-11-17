import { sm4 } from 'sm-crypto'
import sm2 from 'sm-crypto/src/sm2/index.js'
import sm2Sm3 from 'sm-crypto/src/sm2/sm3.js'

const MAC_BINDING_DEFAULT = 'big-encrypt-etm-v1'
const SM4_ALG_DEFAULT = 'SM4/CBC/PKCS7Padding'
const CBC_IV_LEN = 16

/**
 * 客户端 SM2 密钥在 localStorage 中的键名。
 * <p>
 * 为什么私钥必须留在本地：服务端要求「加密请求必须携带合法 SM2 签名」，
 * 而浏览器不可能持有服务端私钥——把服务端私钥下发到前端等于把签名能力交给所有人，
 * 验签就形同虚设。正确做法是客户端自己生成密钥对，私钥只存本地、公钥登记到服务端。
 */
const CLIENT_KEY_PRIVATE = 'sm2_client_private_key'
const CLIENT_KEY_PUBLIC = 'sm2_client_public_key'
const CLIENT_KEY_ID = 'sm2_client_key_id'
const CLIENT_KEY_EXPIRE_AT = 'sm2_client_key_expire_at'
/** 提前续期的时间余量：过期前 10 分钟就重新登记，避免临界点请求被拒 */
const RENEW_AHEAD_MS = 10 * 60 * 1000

let runtimeKeys = null
let runtimeAlg = null
let runtimeMacBinding = null
/** 服务端 SM2 公钥（X.509 Base64），用于校验响应签名 */
let runtimeSm2PublicKey = null
let runtimeSm2KeyId = null
/** 服务端 SM2 公钥的裸公钥点 hex 缓存（04||X||Y），避免每次验签都重新解析 DER */
let runtimeSm2RawPointHex = null

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
  if (config.sm2PublicKey) {
    setServerSm2PublicKey(config.sm2PublicKey, config.sm2KeyId)
  }
  if (typeof window !== 'undefined') {
    try {
      sessionStorage.setItem('crypto_keys', JSON.stringify(runtimeKeys))
      sessionStorage.setItem('crypto_alg', runtimeAlg)
      sessionStorage.setItem('crypto_binding', runtimeMacBinding)
      if (config.sm2PublicKey) {
        sessionStorage.setItem('crypto_sm2_public_key', config.sm2PublicKey)
        sessionStorage.setItem('crypto_sm2_key_id', config.sm2KeyId || '')
      }
    } catch (e) {}
  }
}

export function loadCryptoConfigFromCache() {
  if (typeof window === 'undefined') return false
  try {
    const k = sessionStorage.getItem('crypto_keys')
    const a = sessionStorage.getItem('crypto_alg')
    const b = sessionStorage.getItem('crypto_binding')
    const pk = sessionStorage.getItem('crypto_sm2_public_key')
    const kid = sessionStorage.getItem('crypto_sm2_key_id')
    if (k) {
      runtimeKeys = JSON.parse(k)
      runtimeAlg = a || SM4_ALG_DEFAULT
      runtimeMacBinding = b || MAC_BINDING_DEFAULT
      if (pk) setServerSm2PublicKey(pk, kid)
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

// ==================== 服务端响应签名校验 ====================

/**
 * 缓存服务端 SM2 公钥，并预先把 X.509 DER 解析成裸公钥点
 * <p>
 * sm-crypto 的验签接口只接受裸公钥点 hex（04||X||Y），而服务端下发的是 X.509 Base64。
 * SM2/P-256 的 SubjectPublicKeyInfo 末尾固定是 65 字节的未压缩点，
 * 直接取尾部即可，无需引入 ASN.1 解析库。
 */
function setServerSm2PublicKey(x509Base64, keyId) {
  if (!x509Base64) return
  runtimeSm2PublicKey = x509Base64
  runtimeSm2KeyId = keyId || null
  try {
    const der = base64ToBytes(x509Base64.trim())
    const point = der.subarray(der.length - 65)
    if (point.length === 65 && point[0] === 0x04) {
      runtimeSm2RawPointHex = bytesToHex(point)
    } else {
      runtimeSm2RawPointHex = null
    }
  } catch (e) {
    runtimeSm2RawPointHex = null
  }
}

/**
 * 校验响应签名，确认响应确实来自持有服务端私钥的一方
 * <p>
 * 服务端签名原文与请求侧对称：{@code timestamp + "\n" + cipher}。
 * 校验通过说明密文未被中间人替换；失败则必须丢弃响应，否则攻击者可返回伪造数据。
 *
 * @param {object} envelope 响应密文信封，含 timestamp / cipher / sign
 * @returns {boolean} true 表示签名有效；无公钥或无签名时返回 false
 */
function verifyResponseSign(envelope) {
  if (!envelope || !envelope.sign) return false
  if (!runtimeSm2RawPointHex) return false
  try {
    const payload = String(envelope.timestamp) + '\n' + envelope.cipher
    const signHex = bytesToHex(base64ToBytes(envelope.sign))
    return sm2.doVerifySignature(payload, signHex, runtimeSm2RawPointHex, { der: true, hash: true })
  } catch (e) {
    return false
  }
}

// ==================== 客户端 SM2 密钥（严格验签的落地前提） ====================

/**
 * 读取本地已有的客户端密钥对
 */
function readClientKey() {
  if (typeof window === 'undefined') return null
  try {
    const privateKey = localStorage.getItem(CLIENT_KEY_PRIVATE)
    const publicKey = localStorage.getItem(CLIENT_KEY_PUBLIC)
    if (!privateKey || !publicKey) return null
    return {
      privateKey,
      publicKey,
      clientKeyId: localStorage.getItem(CLIENT_KEY_ID) || null,
      expireAt: Number(localStorage.getItem(CLIENT_KEY_EXPIRE_AT) || 0)
    }
  } catch (e) {
    return null
  }
}

function persistClientKey(key) {
  if (typeof window === 'undefined') return
  try {
    localStorage.setItem(CLIENT_KEY_PRIVATE, key.privateKey)
    localStorage.setItem(CLIENT_KEY_PUBLIC, key.publicKey)
    if (key.clientKeyId) {
      localStorage.setItem(CLIENT_KEY_ID, key.clientKeyId)
    }
  } catch (e) {}
}

function persistClientKeyId(clientKeyId, expireAt) {
  if (typeof window === 'undefined' || !clientKeyId) return
  try {
    localStorage.setItem(CLIENT_KEY_ID, clientKeyId)
    if (expireAt) {
      localStorage.setItem(CLIENT_KEY_EXPIRE_AT, String(expireAt))
    }
  } catch (e) {}
}

/**
 * 客户端公钥是否已登记且仍在有效期内
 * <p>
 * 留 {@link RENEW_AHEAD_MS} 的安全余量：公钥在服务端过期后，
 * 客户端仍会用本地私钥签名，但服务端查不到公钥，所有加密请求都会被 CODE_SIGN_INVALID 拒绝。
 * 因此必须在过期前主动续期，而不是等失败再补救。
 */
function isClientKeyValid() {
  const key = readClientKey()
  if (!key || !key.clientKeyId) return false
  if (!key.expireAt) return true
  return Date.now() < key.expireAt - RENEW_AHEAD_MS
}

/**
 * 生成并持久化客户端 SM2 密钥对（首次访问 / 本地密钥丢失时调用）
 * <p>
 * 公钥是 sm-crypto 的裸公钥点（04||X||Y，130 位 hex），服务端会归一化成 X.509。
 * 私钥仅存本地 localStorage，绝不上传。
 */
export function ensureClientKeyPair() {
  const existing = readClientKey()
  if (existing) return existing
  const pair = sm2.generateKeyPairHex()
  const key = { privateKey: pair.privateKey, publicKey: pair.publicKey, clientKeyId: null }
  persistClientKey(key)
  return key
}

/**
 * 登记客户端公钥到服务端，换取 clientKeyId
 * <p>
 * 必须在拿到 token 前后都可调用：登记端点本身在网关白名单内且不要求信封加密，
 * 否则会陷入「要验签必须先登记、要登记必须先验签」的死循环。
 *
 * @param {string} endpoint 登记端点，默认 /crypto/sm2/client-key
 * @param {boolean} force 为 true 时忽略本地已有 clientKeyId 强制重新登记（过期/失效后使用）
 * @returns {Promise<string|null>} 服务端签发的 clientKeyId
 */
export async function registerClientKey(endpoint = '/crypto/sm2/client-key', force = false) {
  const key = ensureClientKeyPair()
  if (!force && key.clientKeyId && isClientKeyValid()) {
    return key.clientKeyId
  }
  try {
    const resp = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ publicKey: key.publicKey })
    })
    if (!resp.ok) return null
    const body = await resp.json()
    const clientKeyId = body?.data?.clientKeyId
    const expiresIn = Number(body?.data?.expiresIn || 0)
    const expireAt = expiresIn > 0 ? Date.now() + expiresIn * 1000 : 0
    if (clientKeyId) persistClientKeyId(clientKeyId, expireAt)
    return clientKeyId || null
  } catch (e) {
    console.warn('[SM2] 客户端公钥登记失败，将回退服务端全局公钥验签:', e)
    return null
  }
}

/**
 * 确保客户端密钥已生成且公钥仍在有效期内，过期则自动续期
 * <p>
 * 调用方（应用启动、请求拦截器）都应走这里，而不是直接读本地 key：
 * 只读本地会漏掉「公钥已在服务端过期」的情况，导致签名被 CODE_SIGN_INVALID 拒绝。
 *
 * @returns {Promise<string|null>} 有效的 clientKeyId
 */
export async function ensureClientKeyRegistered(endpoint = '/crypto/sm2/client-key') {
  const key = ensureClientKeyPair()
  if (isClientKeyValid()) {
    return key.clientKeyId
  }
  return registerClientKey(endpoint)
}

/**
 * 计算请求签名（SM3withSM2，DER 编码后转 Base64）
 * <p>
 * 签名原文必须与服务端 {@code SmCryptoService#signPayload} 完全一致：
 * {@code timestamp + "\n" + nonce + "\n" + cipher}。
 * 任何字段顺序/分隔符差异都会导致验签失败，因此这里集中构造，避免各处手写。
 */
function signEnvelope(timestamp, nonce, cipher) {
  const key = readClientKey()
  if (!key) return null
  const payload = timestamp + '\n' + nonce + '\n' + cipher
  const derHex = sm2.doSignature(payload, key.privateKey, { der: true, hash: true })
  return bytesToBase64(hexToBytes(derHex))
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

  const timestamp = Date.now()
  const nonce = bytesToHex(randomBytes(16))
  const envelope = {
    alg,
    keyId: useKeyId,
    cipher: cipherBase64,
    mac: macBase64,
    timestamp,
    nonce
  }

  // 严格验签：服务端要求信封必须携带合法签名，缺失即 CODE_SIGN_INVALID
  const key = readClientKey()
  if (key) {
    const sign = signEnvelope(timestamp, nonce, cipherBase64)
    if (sign) envelope.sign = sign
    if (key.clientKeyId) envelope.clientKeyId = key.clientKeyId
  }

  return envelope
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
  verifyResponseSign,
  hasServerSm2PublicKey() { return !!runtimeSm2RawPointHex },
  SM4_ALG: SM4_ALG_DEFAULT,
  MAC_BINDING: MAC_BINDING_DEFAULT,
  initCryptoConfig,
  loadCryptoConfigFromCache,
  ensureClientKeyPair,
  registerClientKey,
  ensureClientKeyRegistered,
  signEnvelope,
  hasClientKey() { return !!readClientKey() },
  isClientKeyValid,
  getClientKeyId() {
    const key = readClientKey()
    return key ? key.clientKeyId : null
  },
  clearClientKey() {
    if (typeof window === 'undefined') return
    try {
      localStorage.removeItem(CLIENT_KEY_PRIVATE)
      localStorage.removeItem(CLIENT_KEY_PUBLIC)
      localStorage.removeItem(CLIENT_KEY_ID)
      localStorage.removeItem(CLIENT_KEY_EXPIRE_AT)
    } catch (e) {}
  },
  get isReady() { return !!runtimeKeys }
}

export default smCrypto