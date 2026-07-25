import { API } from '@/constants/api'
import { request } from '@/utils/request'
import type { LoginForm, LoginResult } from '@/types/auth'

/** POST /api/v1/auth/login */
export function loginApi(data: LoginForm) {
  return request<LoginResult>({
    url: API.AUTH.LOGIN,
    method: 'post',
    data,
  })
}

/** POST /api/v1/auth/logout */
export function logoutApi() {
  return request<void>({
    url: API.AUTH.LOGOUT,
    method: 'post',
  })
}
