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
  if (smCrypto.loadCryptoConfigFromCache()) {
    return
  }
  try {
    const resp = await fetch('/auth/public-key')
    const json = await resp.json()
    if (json && json.success && json.data) {
      smCrypto.initCryptoConfig(json.data)
    } else {
      console.warn('[SM4] /auth/public-key 返回异常:', json)
    }
  } catch (e) {
    console.warn('[SM4] 拉取加密配置失败:', e.message, '登录请求将无法加密')
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