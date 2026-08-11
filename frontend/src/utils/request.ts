import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { getApiMessage, isApiSuccess, type ApiResult } from '@/types/api'

/** 防止并发 401 重复提示与重复跳转 */
let isHandlingUnauthorized = false

/**
 * Java 雪花 ID 超过 Number.MAX_SAFE_INTEGER，JSON.parse 会丢精度。
 * 将 16 位及以上数字转为字符串再解析。
 */
function parseResponseData(data: unknown): unknown {
  if (typeof data !== 'string' || !data) return data
  try {
    const safe = data.replace(/([:\[,]\s*)(\d{16,})(\s*[,\]}])/g, '$1"$2"$3')
    return JSON.parse(safe)
  } catch {
    try {
      return JSON.parse(data)
    } catch {
      return data
    }
  }
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 60000,
  transformResponse: [parseResponseData],
})

service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      // 后端 JWT 校验的是 authentication 请求头（不是 Authorization）
      config.headers.set('authentication', `Bearer ${token}`)
    }
    return config
  },
  (error) => Promise.reject(error),
)

service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const res = response.data
    if (isApiSuccess(res.code)) {
      return res as unknown as AxiosResponse
    }
    const message = getApiMessage(res) || '请求失败'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  async (error) => {
    const status = error.response?.status
    const data = error.response?.data as ApiResult | undefined
    if (status === 401) {
      if (!isHandlingUnauthorized) {
        isHandlingUnauthorized = true
        try {
          const { useUserStore } = await import('@/stores/user')
          const userStore = useUserStore()
          const { default: router } = await import('@/router')
          const current = router.currentRoute.value

          userStore.resetAuth()
          ElMessage.error('登录已过期，请重新登录')
          // 游客可停留的页面：只提示，不强制弹窗/跳转
          const guestStay =
            current.name === 'chat' ||
            current.name === 'knowledge' ||
            current.name === 'document' ||
            current.name === 'history'
          if (!guestStay) {
            userStore.openLoginDialog(current.fullPath)
            await router.replace({ name: 'chat' })
          }
        } finally {
          isHandlingUnauthorized = false
        }
      }
    } else {
      ElMessage.error(getApiMessage(data) || error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  return service.request<any, ApiResult<T>>(config).then((res) => (res as unknown as ApiResult<T>).data)
}

export default service
