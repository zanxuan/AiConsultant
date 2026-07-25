import { ref, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/auth'

export function useAuth() {
  const userStore = useUserStore()

  const isLoggedIn = computed(() => userStore.isLoggedIn || Boolean(getToken()))

  function hasToken() {
    return Boolean(getToken() || userStore.token)
  }

  return {
    isLoggedIn,
    hasToken,
    userInfo: computed(() => userStore.userInfo),
    login: userStore.login,
    logout: userStore.logout,
  }
}
