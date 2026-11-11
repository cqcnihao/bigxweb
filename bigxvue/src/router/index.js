import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '首页', icon: 'Odometer' }
      },
      {
        path: 'product',
        name: 'Product',
        component: () => import('@/views/ProductList.vue'),
        meta: { title: '产品管理', icon: 'Goods' }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/OrderList.vue'),
        meta: { title: '订单管理', icon: 'Tickets' }
      },
      {
        path: 'invoice',
        name: 'Invoice',
        component: () => import('@/views/InvoiceList.vue'),
        meta: { title: '发票管理', icon: 'Document' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { public: true }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!authStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router