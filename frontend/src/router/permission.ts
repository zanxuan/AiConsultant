import type { Router } from 'vue-router'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'

/**
 * 路由权限层：
 * 进入页面 → 是否有 Token → 是否已登录 → 放行 / 跳转登录
 */
export function setupPermission(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    const title = (to.meta.title as string) || '企业知识助手'
    document.title = `${title} - ${import.meta.env.VITE_APP_TITLE || '企业知识助手'}`

    const token = getToken()
    const isPublic = Boolean(to.meta.public)
    const requiresAuth = to.meta.requiresAuth !== false && !isPublic

    // 已登录访问登录页 → 回首页
    if (token && (to.name === 'login' || to.name === 'login-page')) {
      next({ name: 'chat' })
      return
    }

    // 公开页直接放行
    if (isPublic) {
      next()
      return
    }

    // 需登录但无 Token → 跳转登录，并带回跳地址
    if (requiresAuth && !token) {
      next({
        name: 'login',
        query: { redirect: to.fullPath },
      })
      return
    }

    // 有 Token 但用户信息未加载时尝试拉取 GET /api/v1/users/me
    if (token) {
      const userStore = useUserStore()
      if (!userStore.userInfo) {
        try {
          await userStore.fetchProfile()
        } catch {
          await userStore.logout()
          next({
            name: 'login',
            query: { redirect: to.fullPath },
          })
          return
        }
      }
    }

    next()
  })
}
