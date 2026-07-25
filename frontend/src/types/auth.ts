import type { UserInfo } from '@/types/user'

export interface LoginForm {
  username: string
  password: string
}

/** POST /api/v1/auth/login 响应 data */
export interface LoginResult {
  token: string
  userInfo: UserInfo
}

export interface TokenPayload {
  userId: number
  username?: string
  exp?: number
}
