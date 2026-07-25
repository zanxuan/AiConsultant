import type { ChatMessage } from './chat'

export interface Conversation {
  id: number
  title: string
  userId?: number
  knowledgeId?: number | string
  knowledgeName?: string
  /** 兼容前端命名 */
  updatedAt?: string
  createdAt?: string
  /** 后端实际字段 */
  updateTime?: string
  createTime?: string
}

export interface ConversationDetail extends Conversation {
  messages?: ChatMessage[]
}

/** POST /api/v1/conversations 请求体 */
export interface CreateConversationParams {
  knowledgeId: number | string
  title: string
}
