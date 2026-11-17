import request from './request'

export function getCaptcha() {
  return request.get('/auth/captcha')
}

/**
 * AJ-Captcha 获取滑动拼图底图（后端 @SmPlain，明文交互，跳过 SM4 信封）
 */
export function getSlideCaptcha() {
  return request.post('/auth/captcha/get', { captchaType: 'blockPuzzle' }, { _skipEncrypt: true })
}

/**
 * AJ-Captcha 核对滑块位置，成功后前端据此换取 captchaVerification 凭证
 */
export function checkSlideCaptcha(data) {
  return request.post('/auth/captcha/check', data, { _skipEncrypt: true })
}

export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

export function getCurrentUser() {
  return request.get('/auth/me')
}
