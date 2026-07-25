import { TOKEN_KEY, USER_INFO_KEY } from '@/constants/storage'
import type { UserInfo } from '@/types/user'

/** 去掉后端可能自带的 Bearer 前缀，避免请求头变成 Bearer Bearer xxx */
function normalizeToken(token: string): string {
  return token.replace(/^Bearer\s+/i, '').trim()
}

export function getToken(): string | null {
  const raw = localStorage.getItem(TOKEN_KEY)
  if (!raw) return null
  return normalizeToken(raw)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, normalizeToken(token))
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function getStoredUserInfo(): UserInfo | null {
  const raw = localStorage.getItem(USER_INFO_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export function setStoredUserInfo(user: UserInfo): void {
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(user))
}

export function removeStoredUserInfo(): void {
  localStorage.removeItem(USER_INFO_KEY)
}

export function clearAuthStorage(): void {
  removeToken()
  removeStoredUserInfo()
}
