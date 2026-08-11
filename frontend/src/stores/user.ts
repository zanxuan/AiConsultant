import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, logoutApi } from '@/api/auth'
import { getProfileApi } from '@/api/user'
import type { LoginForm } from '@/types/auth'
import type { UserInfo } from '@/types/user'
import {
  getToken,
  setToken,
  clearAuthStorage,
  getStoredUserInfo,
  setStoredUserInfo,
} from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const userInfo = ref<UserInfo | null>(getStoredUserInfo())
  /** 全局登录弹窗：未登录时不跳 /login，弹窗承接 */
  const loginDialogVisible = ref(false)
  const loginRedirect = ref<string | null>(null)

  const isLoggedIn = computed(() => Boolean(token.value))

  function openLoginDialog(redirect?: string | null) {
    loginRedirect.value = redirect || null
    loginDialogVisible.value = true
  }

  function closeLoginDialog() {
    loginDialogVisible.value = false
  }

  async function login(form: LoginForm) {
    const result = await loginApi(form)
    token.value = result.token
    setToken(result.token)
    if (result.userInfo) {
      userInfo.value = result.userInfo
      setStoredUserInfo(result.userInfo)
    } else {
      await fetchProfile()
    }
  }

  async function fetchProfile() {
    const profile = await getProfileApi()
    userInfo.value = profile
    setStoredUserInfo(profile)
    return profile
  }

  /** 仅清理本地登录态（不请求后端），用于 JWT 过期等场景 */
  function resetAuth() {
    token.value = null
    userInfo.value = null
    clearAuthStorage()
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 即便后端登出失败，前端仍清理本地态
    } finally {
      resetAuth()
    }
  }

  function hydrateFromStorage() {
    token.value = getToken()
    userInfo.value = getStoredUserInfo()
  }

  function setUserInfo(profile: UserInfo) {
    userInfo.value = profile
    setStoredUserInfo(profile)
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    loginDialogVisible,
    loginRedirect,
    openLoginDialog,
    closeLoginDialog,
    login,
    logout,
    resetAuth,
    fetchProfile,
    hydrateFromStorage,
    setUserInfo,
  }
})
