<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <p class="stat-label">{{ card.label }}</p>
              <p class="stat-value">{{ card.value }}</p>
            </div>
            <el-icon :size="48" :color="card.color" class="stat-icon">
              <component :is="card.icon" />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-card class="welcome-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-icon><User /></el-icon>
          <span>欢迎回来</span>
        </div>
      </template>
      <div class="welcome-content">
        <p class="welcome-title">Hi, {{ authStore.nickname || authStore.username }} 👋</p>
        <p class="welcome-desc">
          您已成功登录 BigX 管理平台，当前拥有 {{ authStore.permissions.length }} 项权限，
          属于 {{ authStore.roles.length }} 个角色。
        </p>
        <el-descriptions :column="2" border v-if="authStore.userInfo">
          <el-descriptions-item label="用户ID">{{ authStore.userInfo.userId }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ authStore.userInfo.username }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag v-for="r in authStore.roles" :key="r" size="small" style="margin-right: 4px">{{ r }}</el-tag>
            <span v-if="!authStore.roles.length">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="Token 剩余">
            {{ formatTimeout(authStore.userInfo.tokenTimeout) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const statCards = computed(() => [
  { label: '用户ID', value: authStore.userInfo?.userId || '-', icon: 'User', color: '#409EFF' },
  { label: '角色数', value: authStore.roles.length, icon: 'UserFilled', color: '#67C23A' },
  { label: '权限数', value: authStore.permissions.length, icon: 'Lock', color: '#E6A23C' },
  { label: '在线状态', value: '在线', icon: 'CircleCheck', color: '#F56C6C' }
])

function formatTimeout(seconds) {
  if (!seconds && seconds !== 0) return '-'
  if (seconds < 60) return seconds + ' 秒'
  if (seconds < 3600) return Math.floor(seconds / 60) + ' 分钟'
  return (seconds / 3600).toFixed(1) + ' 小时'
}
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.stat-card {
  border-radius: 8px;
}
.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-label {
  color: #909399;
  font-size: 14px;
  margin: 0 0 8px;
}
.stat-value {
  color: #303133;
  font-size: 28px;
  font-weight: bold;
  margin: 0;
}
.stat-icon {
  opacity: 0.6;
}
.welcome-card {
  border-radius: 8px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: bold;
}
.welcome-title {
  font-size: 20px;
  font-weight: bold;
  margin: 0 0 8px;
}
.welcome-desc {
  color: #606266;
  margin: 0 0 16px;
}
</style>
