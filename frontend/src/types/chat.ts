import type { MessageRole } from '@/constants/enum'

/** 引用片段（后端字段为 references） */
export interface CiteSource {
  documentId?: number | string
  documentName: string
  /** 页码 */
  page?: number | string
  content?: string
  score?: number
}

export interface ChatMessage {
  id?: string | number
  role: MessageRole
  content: string
  sources?: CiteSource[]
  createdAt?: string
  /** 游客引导：在助手消息下方展示「立即登录」链接 */
  showLoginLink?: boolean
}

/** POST /api/v1/chat 请求体 */
export interface ChatSendParams {
  conversationId: number | string
  message: string
}

/** POST /api/v1/chat 响应 data */
export interface ChatSendResult {
  answer: string
  /** 后端实际字段 */
  references?: CiteSource[]
  /** 兼容旧字段 */
  sources?: CiteSource[]
}

/** 前端发起问答时的入参（含创建会话所需信息） */
export interface ChatAskParams {
  knowledgeId: number | string
  message: string
  conversationId?: number | string
  title?: string
}

/** 预留：SSE 流式 chunk（接口地址仍为 /v1/chat） */
export interface StreamChunk {
  content?: string
  done?: boolean
  conversationId?: number | string
  references?: CiteSource[]
  sources?: CiteSource[]
  answer?: string
}
