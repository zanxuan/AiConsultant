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

  const isLoggedIn = computed(() => Boolean(token.value))

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

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 即便后端登出失败，前端仍清理本地态
    } finally {
      token.value = null
      userInfo.value = null
      clearAuthStorage()
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
    login,
    logout,
    fetchProfile,
    hydrateFromStorage,
    setUserInfo,
  }
})
