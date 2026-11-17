import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import smCrypto from '@/utils/smCrypto'

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

function isEncryptable(config) {
  const method = (config.method || 'get').toLowerCase()
  if (method === 'get') return false
  if (!config.data) return false
  if (config._skipEncrypt) return false
  if (config.headers && config.headers['X-Encrypted'] === 'true') return false
  return true
}

/**
 * 确保客户端密钥有效 CRY-4003 异常
 * 严格验签下信封必须带签名，而签名依赖本地私钥 + 服务端已登记且未过期的公钥。
 * 若本地密钥被清空（换浏览器/清理缓存）或公钥已临近过期，这里即时补建/续期，
 * 避免请求被 CODE_SIGN_INVALID 拒绝。
 * <p>
 * 续期是异步的，但请求拦截器本身是同步的：这里只在密钥确实缺失或已过期时才 await，
 * 正常路径（密钥有效）不产生任何额外请求开销。
 */
async function ensureClientKey() {
  if (smCrypto.isClientKeyValid && smCrypto.isClientKeyValid()) {
    return
  }
  if (!smCrypto.hasClientKey()) {
    smCrypto.ensureClientKeyPair()
  }
  try {
    await smCrypto.ensureClientKeyRegistered()
  } catch (e) {
    console.warn('[SM2] 客户端密钥续期失败:', e)
  }
}

function applyAuthHeader(config) {
  config.headers = config.headers || {}
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = token
  }
  return config
}

request.interceptors.request.use(async config => {
  applyAuthHeader(config)

  if (isEncryptable(config)) {
    try {
      await ensureClientKey()
      const plainText = typeof config.data === 'string'
        ? config.data
        : JSON.stringify(config.data)
      // 留存明文：CRY-4003 自愈重试时需用重新登记的新密钥再次加密，而不是重放旧信封
      config._plainData = plainText
      const envelope = smCrypto.encrypt(plainText)
      config.data = envelope
      config.headers['Content-Type'] = 'application/json'
      config.headers['X-Encrypted'] = 'true'
      if (envelope.clientKeyId) {
        config.headers['X-Sm-Client-Key-Id'] = envelope.clientKeyId
      }
    } catch (e) {
      console.error('[SM4] 请求加密失败:', e)
      ElMessage.error('请求加密失败: ' + e.message)
      return Promise.reject(e)
    }
  }

  return config
}, error => {
  return Promise.reject(error)
})

/**
 * 校验响应信封的服务端签名
 * <p>
 * 服务端开启 big.encrypt.sign-response 后，每个加密响应的 data 都会带 sign。
 * 必须验签后再解密：否则中间人可以替换密文，客户端解密出来就是伪造数据，
 * 加密只保证了「别人看不懂」，没保证「内容没被改过」。
 * <p>
 * 服务端未开启响应签名（信封里没有 sign）时不强制，避免与后端配置强耦合；
 * 但只要带了 sign 就必须通过，验不过一律丢弃响应。
 */
function verifyResponse(envelope, where) {
  if (!envelope || typeof envelope !== 'object' || envelope.encrypt !== true) {
    return true
  }
  if (!envelope.sign) {
    if (smCrypto.hasServerSm2PublicKey()) {
      console.warn('[SM2] 响应未携带签名，无法校验来源:', where)
    }
    return true
  }
  if (!smCrypto.hasServerSm2PublicKey()) {
    // 服务端签了名却拿不到公钥，说明加密配置没装载成功。
    // 这里不能放行：放行等于「有签名就跳过校验」，攻击者删掉公钥即可绕过，验签形同虚设。
    throw new Error('缺少服务端 SM2 公钥，无法校验响应签名，请刷新页面重新拉取加密配置')
  }
  if (!smCrypto.verifyResponseSign(envelope)) {
    throw new Error('响应签名校验失败，报文可能被篡改')
  }
  return true
}

request.interceptors.response.use(
  async response => {
    const res = response.data
    if (res && typeof res === 'object') {
      if (res.encrypt === true) {
        try {
          verifyResponse(res, 'raw')
          response.data = smCrypto.decryptPayload(res)
        } catch (e) {
          console.error('[SM4] 响应校验失败:', e)
          ElMessage.error(e.message || '响应解密失败')
          return Promise.reject(e)
        }
        return response
      }
      if ('success' in res) {
        if (res.data && typeof res.data === 'object' && res.data.encrypt === true) {
          try {
            verifyResponse(res.data, 'result')
            res.data = smCrypto.decryptPayload(res.data)
          } catch (e) {
            console.error('[SM4] 响应校验失败:', e)
            ElMessage.error(e.message || '响应解密失败')
            return Promise.reject(e)
          }
        }
        if (res.success) {
          return res
        }
        const code = String(res.code || '')
        const msg = res.message || '请求失败'
        // 客户端密钥失效自愈：服务端查不到登记公钥（Redis 重启/密钥过期清理）时会以
        // CRY-4003 拒绝所有加密请求，而前端本地有效期未过不会主动续期，陷入死锁。
        // 这里强制重新登记一次并原样重试；仅重试一次（_smRetried），避免异常时形成循环
        if (code === 'CRY-4003' && !response.config._smRetried) {
          response.config._smRetried = true
          try {
            await smCrypto.registerClientKey('/crypto/sm2/client-key', true)
          } catch (e) {
            console.warn('[SM2] 签名被拒后重新登记失败:', e)
          }
          if (response.config._plainData !== undefined) {
            response.config.data = response.config._plainData
            delete response.config.headers['X-Encrypted']
          }
          return request(response.config)
        }
        if (code === '401') {
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          if (router.currentRoute.value.path !== '/login') {
            ElMessage.error('登录已过期，请重新登录')
            router.push('/login')
          }
        } else if (code === '403') {
          ElMessage.error(msg)
        } else if (code === '429') {
          ElMessage.warning(msg)
        } else {
          ElMessage.error(msg)
        }
        return Promise.reject(new Error(msg))
      }
    }
    return response
  },
  error => {
    const status = error.response?.status
    const data = error.response?.data
    const msg = data?.message || error.message || '网络异常'
    if (status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      if (router.currentRoute.value.path !== '/login') {
        ElMessage.error('未登录或登录已过期')
        router.push('/login')
      }
    } else if (status === 403) {
      ElMessage.error(msg)
    } else if (status === 404) {
      ElMessage.error('接口不存在')
    } else if (status >= 500) {
      ElMessage.error('服务器内部错误')
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request