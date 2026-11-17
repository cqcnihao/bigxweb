import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import smCrypto from './utils/smCrypto'
import './style.css'

async function bootstrapCrypto() {
  // 客户端 SM2 密钥：严格验签要求每个加密请求都带合法签名，
  // 浏览器只能用自己的私钥签名，因此启动即确保本地密钥存在且公钥未过期（过期自动续期）。
  smCrypto.ensureClientKeyPair()
  await smCrypto.ensureClientKeyRegistered()

  // 加密配置始终拉取最新（接口本身仅几毫秒）：服务端轮换 SM4/SM2 密钥后，
  // 若命中 sessionStorage 旧缓存会导致 MAC/解密/验签全部失败且无自愈路径。
  // 缓存仅在拉取失败时兜底（离线/服务未启动时至少能解析历史会话）。
  try {
    const resp = await fetch('/auth/public-key')
    const json = await resp.json()
    if (json && json.success && json.data) {
      smCrypto.initCryptoConfig(json.data)
      return
    }
    console.warn('[SM4] /auth/public-key 返回异常:', json)
  } catch (e) {
    console.warn('[SM4] 拉取加密配置失败:', e.message)
  }
  if (!smCrypto.loadCryptoConfigFromCache()) {
    console.warn('[SM4] 加密配置不可用，登录请求将无法加密')
  }
}

bootstrapCrypto().finally(() => {
  const app = createApp(App)

  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }

  app.use(createPinia())
  app.use(router)
  app.use(ElementPlus)
  app.mount('#app')
})