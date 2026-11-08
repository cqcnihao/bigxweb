<template>
  <el-container class="layout-container">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <el-icon :size="24" color="#409EFF"><Monitor /></el-icon>
        <span v-if="!collapsed" class="logo-text">BigX 管理</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#409EFF"
        router
      >
        <el-menu-item v-for="item in menuRoutes" :key="item.path" :index="'/' + item.path">
          <el-icon><component :is="item.meta.icon" /></el-icon>
          <template #title>{{ item.meta.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="(item, i) in breadcrumbs" :key="i">{{ item }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" class="avatar">
                <el-icon><User /></el-icon>
              </el-avatar>
              <span class="nickname">{{ authStore.nickname || authStore.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessageBox, ElMessage } from 'element-plus'

const collapsed = ref(false)
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))

const menuRoutes = computed(() => {
  const mainRoute = router.options.routes.find(r => r.path === '/')
  return mainRoute?.children?.filter(c => c.meta?.title) || []
})

const breadcrumbs = computed(() => {
  return route.matched.filter(r => r.meta?.title).map(r => r.meta.title)
})

async function handleCommand(cmd) {
  if (cmd === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await authStore.logoutAction()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch (e) {
      // cancelled
    }
  } else if (cmd === 'profile') {
    ElMessage.info('个人信息功能开发中')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.aside {
  background: #001529;
  transition: width 0.3s;
  overflow: hidden;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid #ffffff1a;
  white-space: nowrap;
}
.logo-text {
  color: #fff;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
}
.collapse-btn:hover {
  color: #409EFF;
}
.header-right .user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.avatar {
  background: #409EFF;
}
.main {
  background: #f0f2f5;
  padding: 16px;
}
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
