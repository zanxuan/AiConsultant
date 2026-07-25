/** 后端统一响应结构 */
export interface ApiResult<T = unknown> {
  code: number
  message?: string | null
  /** 兼容部分接口返回 msg */
  msg?: string | null
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  page?: number
  /** 后端实际分页字段 */
  pageNum?: number
  pageSize: number
  pages?: number
}

export interface PageQuery {
  page?: number
  pageSize?: number
}

/** 统一约定成功码为 200；兼容历史返回 1 */
export const API_SUCCESS_CODES = [200, 1] as const

export function isApiSuccess(code: number): boolean {
  return (API_SUCCESS_CODES as readonly number[]).includes(code)
}

export function getApiMessage(res: Pick<ApiResult, 'msg' | 'message'> | undefined | null): string {
  return res?.message || res?.msg || ''
}
