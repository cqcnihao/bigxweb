import { defineStore } from 'pinia'
import { login, logout, getCurrentUser } from '@/api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null'),
    roles: [],
    permissions: []
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.username || '',
    nickname: (state) => state.userInfo?.nickname || '',
    hasRole: (state) => (role) => state.roles.includes(role),
    hasPermission: (state) => (perm) => state.permissions.includes(perm)
  },
  actions: {
    async loginAction(loginForm) {
      const res = await login(loginForm)
      const data = res.data
      this.token = data.token
      this.userInfo = {
        userId: data.userId,
        username: data.username,
        nickname: data.nickname,
        tokenTimeout: data.tokenTimeout
      }
      this.roles = data.roles || []
      this.permissions = data.permissions || []
      localStorage.setItem('token', data.token)
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
      return data
    },
    async logoutAction() {
      try {
        await logout()
      } catch (e) {
        // ignore
      }
      this.clearAuth()
    },
    async fetchUserInfo() {
      const res = await getCurrentUser()
      const data = res.data
      this.userInfo = {
        userId: data.userId,
        username: data.username,
        nickname: data.nickname,
        tokenTimeout: data.tokenTimeout
      }
      this.roles = data.roles || []
      this.permissions = data.permissions || []
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    clearAuth() {
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.permissions = []
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }
})
