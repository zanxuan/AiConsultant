import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/user'

/**
 * 路由权限层（保持简单）：
 * - 游客可进：聊天 / 知识库列表 / 文档管理 / 对话历史（空初始页）
 * - 用户信息：未登录仅友好提示，停留当前页
 * - 其它需登录页无 Token → 回聊天并打开登录弹窗
 */
export function setupPermission(router: Router) {
  router.beforeEach(async (to, from, next) => {
    const title = (to.meta.title as string) || '企业知识助手'
    document.title = `${title} - ${import.meta.env.VITE_APP_TITLE || '企业知识助手'}`

    const token = getToken()
    const userStore = useUserStore()
    const isPublic = Boolean(to.meta.public)
    const requiresAuth = to.meta.requiresAuth !== false && !isPublic
    const isLoginRoute = to.name === 'login' || to.name === 'login-page'

    // /login 不再作为主流程：统一回聊天，未登录则弹窗
    if (isLoginRoute) {
      if (!token) {
        const redirect = (to.query.redirect as string) || null
        userStore.openLoginDialog(redirect)
      }
      next({ name: 'chat' })
      return
    }

    // 公开页直接放行
    if (isPublic) {
      next()
      return
    }

    // 需登录但无 Token
    if (requiresAuth && !token) {
      // 用户信息：顶部轻提示，不跳转、不弹登录框
      if (to.name === 'profile') {
        ElMessage.warning('请先登录后再查看用户信息')
        if (from.name) {
          next(false)
        } else {
          next({ name: 'chat' })
        }
        return
      }

      userStore.openLoginDialog(to.fullPath)
      if (from.name === 'chat' || from.name === 'root') {
        next(false)
      } else {
        next({ name: 'chat' })
      }
      return
    }

    // 有 Token 但用户信息未加载时尝试拉取 GET /api/v1/users/me
    if (token) {
      if (!userStore.userInfo) {
        try {
          await userStore.fetchProfile()
        } catch {
          userStore.resetAuth()
          if (requiresAuth) {
            userStore.openLoginDialog(to.fullPath)
            next({ name: 'chat' })
            return
          }
        }
      }
    }

    next()
  })
}
