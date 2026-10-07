import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import { adminApi } from '@/api'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  {
    path: '/login',
    name: 'login',
    // 后台路由懒加载：访客永远下载不到这部分代码
    component: () => import('@/views/admin/LoginView.vue')
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/views/admin/AdminView.vue'),
    meta: { requiresAuth: true }
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(_to, _from, saved) {
    return saved || { top: 0 }
  }
})

/**
 * 后台路由守卫。
 * 真正的权限在后端 —— 前端只做跳转，token 无效时后端一律返回 401。
 */
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true
  const token = localStorage.getItem('yc_admin_token')
  if (!token) return { name: 'login', query: { r: to.fullPath } }
  try {
    await adminApi.me()
    return true
  } catch {
    localStorage.removeItem('yc_admin_token')
    return { name: 'login', query: { r: to.fullPath } }
  }
})

export default router
