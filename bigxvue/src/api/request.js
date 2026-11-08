import axios from 'axios'
import CryptoJS from 'crypto-js'
import { ElMessage } from 'element-plus'
import router from '@/router'
import smCrypto from '@/utils/smCrypto'

const GATEWAY_NAME = 'big-gateway'
const GATEWAY_VERSION = '1.0.0'
const SIGN_SECRET = 'dev-gateway-secret-do-not-use-in-prod'

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

function hmacSha256Hex(secret, data) {
  const hmac = CryptoJS.HmacSHA256(data, secret)
  return hmac.toString(CryptoJS.enc.Hex)
}

function isEncryptable(config) {
  const method = (config.method || 'get').toLowerCase()
  if (method === 'get') return false
  if (!config.data) return false
  if (config._skipEncrypt) return false
  if (config.headers && config.headers['X-Encrypted'] === 'true') return false
  return true
}

function buildGatewayHeaders(config) {
  const timestamp = String(Date.now())
  const uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
  const traceId = GATEWAY_NAME + '-' + uuid
  const method = (config.method || 'get').toUpperCase()
  const urlObj = new URL(config.url, 'http://placeholder')
  const path = urlObj.pathname + urlObj.search
  const payload = GATEWAY_NAME + '|' + timestamp + '|' + method + '|' + path
  const sign = hmacSha256Hex(SIGN_SECRET, payload)

  config.headers = config.headers || {}
  config.headers['X-Gateway-Name'] = GATEWAY_NAME
  config.headers['X-Gateway-Version'] = GATEWAY_VERSION
  config.headers['X-Request-From'] = GATEWAY_NAME
  config.headers['X-Trace-Id'] = traceId
  config.headers['X-Gateway-Timestamp'] = timestamp
  config.headers['X-Gateway-Sign'] = sign

  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = token
  }

  return config
}

request.interceptors.request.use(config => {
  buildGatewayHeaders(config)

  if (isEncryptable(config)) {
    try {
      const plainText = typeof config.data === 'string'
        ? config.data
        : JSON.stringify(config.data)
      const envelope = smCrypto.encrypt(plainText)
      config.data = envelope
      config.headers['Content-Type'] = 'application/json'
      config.headers['X-Encrypted'] = 'true'
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

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res && typeof res === 'object') {
      if (res.encrypt === true) {
        try {
          response.data = smCrypto.decryptPayload(res)
        } catch (e) {
          console.error('[SM4] 响应解密失败:', e)
          ElMessage.error('响应解密失败: ' + e.message)
          return Promise.reject(e)
        }
        return response
      }
      if ('success' in res) {
        if (res.data && typeof res.data === 'object' && res.data.encrypt === true) {
          try {
            res.data = smCrypto.decryptPayload(res.data)
          } catch (e) {
            console.error('[SM4] 响应解密失败:', e)
            ElMessage.error('响应解密失败: ' + e.message)
            return Promise.reject(e)
          }
        }
        if (res.success) {
          return res
        }
        const code = String(res.code || '')
        const msg = res.message || '请求失败'
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