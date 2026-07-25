import type { PageQuery } from '@/types/api'

export interface KnowledgeBase {
  /** 雪花 ID，按字符串处理避免精度丢失 */
  id: number | string
  userId?: number | string
  name: string
  description?: string
  documentCount?: number
  createTime?: string
  updateTime?: string
  /** 兼容字段 */
  createdAt?: string
  updatedAt?: string
}

export interface KnowledgeForm {
  name: string
  description?: string
}

/** GET /api/v1/knowledge-bases 查询参数 */
export interface KnowledgeListQuery extends PageQuery {
  /** 关键字搜索 */
  keyword?: string
}
