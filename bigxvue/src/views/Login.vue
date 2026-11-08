<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-header">
        <el-icon :size="36" color="#409EFF"><Monitor /></el-icon>
        <h2>BigX 管理平台</h2>
        <p>欢迎回来，请登录您的账号</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="请输入验证码" :prefix-icon="Key" />
            <div class="captcha-img" @click="refreshCaptcha">
              <img v-if="captchaBase64" :src="captchaBase64" alt="验证码" />
              <span v-else>加载中...</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
      <div class="login-footer">
        <span>默认账号: admin / admin123</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { getCaptcha } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref()
const loading = ref(false)
const captchaKey = ref('')
const captchaBase64 = ref('')

const form = ref({
  username: 'admin',
  password: 'admin123',
  captchaCode: '',
  captchaKey: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function refreshCaptcha() {
  try {
    const res = await getCaptcha()
    captchaKey.value = res.data.captchaKey
    captchaBase64.value = res.data.captchaBase64
    form.value.captchaKey = captchaKey.value
    form.value.captchaCode = ''
  } catch (e) {
    // error handled in interceptor
  }
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await authStore.loginAction({
        username: form.value.username,
        password: form.value.password,
        captchaCode: form.value.captchaCode,
        captchaKey: form.value.captchaKey
      })
      ElMessage.success('登录成功')
      const redirect = route.query.redirect || '/dashboard'
      router.push(redirect)
    } catch (e) {
      refreshCaptcha()
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-box {
  width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}
.login-header {
  text-align: center;
  margin-bottom: 32px;
}
.login-header h2 {
  margin: 12px 0 8px;
  color: #303133;
}
.login-header p {
  margin: 0;
  color: #909399;
  font-size: 14px;
}
.captcha-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.captcha-row .el-input {
  flex: 1;
}
.captcha-img {
  width: 130px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  overflow: hidden;
}
.captcha-img img {
  width: 100%;
  height: 100%;
}
.captcha-img span {
  color: #909399;
  font-size: 12px;
}
.login-footer {
  text-align: center;
  margin-top: 16px;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
