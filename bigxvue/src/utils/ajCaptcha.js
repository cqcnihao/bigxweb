import CryptoJS from 'crypto-js'

/**
 * AJ-Captcha 滑动拼图的坐标/凭证加密工具
 * <p>
 * 必须与后端 com.anji.captcha.util.AESUtil 完全一致，否则 check 阶段解不开 pointJson：
 * - 算法：AES/ECB/PKCS5Padding（CryptoJS 的 Pkcs7 与 Java PKCS5 在 16 字节块下等价）
 * - key：get 返回的 secretKey 是 16 位随机字符串，按 UTF-8 原始字节使用（16 字节 = AES-128），
 *   不能 Base64 解码
 * - 输出：Base64
 */

/** AES-ECB 加密，返回 Base64 */
export function aesEncrypt(word, keyStr) {
  const key = CryptoJS.enc.Utf8.parse(keyStr)
  const srcs = CryptoJS.enc.Utf8.parse(word)
  const encrypted = CryptoJS.AES.encrypt(srcs, key, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7
  })
  return encrypted.toString()
}

/**
 * 构造坐标明文（check 提交的 pointJson 与二次校验凭证共用，必须逐字节一致）
 * <p>
 * 坐标 {x, y}：x 为滑块在底图 310px 原始坐标系下的水平偏移（显示缩放需换算还原）；
 * y 固定 5——服务端拼图模板与底图等高，secret 点的 y 恒为 5 且要求精确相等。
 * <p>
 * 服务端契约（反编译 1.4.0 BlockPuzzleCaptchaServiceImpl 确认）：
 * - check：pointJson = AES(坐标明文, secretKey)；服务端解密比对坐标后，
 *   以 AES(token + "---" + 坐标明文, secretKey) 作为二次校验缓存键（ECB 确定性，前后端算出的密文一致）；
 * - verification：把登录请求提交的 captchaVerification 原样作为缓存键查 exists，不解密；
 * - repData 中 captchaVerification 恒为 null，凭证必须由前端自行计算。
 */
export function buildPointPlain(x) {
  return JSON.stringify({ x: x, y: 5 })
}

/** check 阶段提交：坐标明文的 AES-ECB 密文（Base64） */
export function buildPointJson(plainPoint, secretKey) {
  return aesEncrypt(plainPoint, secretKey)
}

/** 登录凭证：AES-ECB(token + "---" + 坐标明文, secretKey)，与服务端写入的缓存键逐字节一致 */
export function buildCaptchaVerification(token, plainPoint, secretKey) {
  return aesEncrypt(token + '---' + plainPoint, secretKey)
}
