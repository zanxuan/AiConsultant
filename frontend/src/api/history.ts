import { API } from '@/constants/api'
import { request } from '@/utils/request'
import type { ChatMessage } from '@/types/chat'
import type {
  Conversation,
  ConversationDetail,
  CreateConversationParams,
} from '@/types/history'
import type { PageQuery, PageResult } from '@/types/api'

/** POST /api/v1/conversations */
export function createConversationApi(data: CreateConversationParams) {
  return request<Conversation>({
    url: API.HISTORY.CREATE,
    method: 'post',
    data,
  })
}

/** GET /api/v1/conversations — data 可能是数组，也可能是分页对象 */
export function getHistoryListApi(params?: PageQuery) {
  return request<PageResult<Conversation> | Conversation[]>({
    url: API.HISTORY.LIST,
    method: 'get',
    params,
  })
}

/** GET /api/v1/conversations/{conversationId} */
export function getHistoryDetailApi(conversationId: number | string) {
  return request<ConversationDetail>({
    url: API.HISTORY.DETAIL(conversationId),
    method: 'get',
  })
}

/** DELETE /api/v1/conversations/{conversationId} */
export function deleteHistoryApi(conversationId: number | string) {
  return request<void>({
    url: API.HISTORY.DELETE(conversationId),
    method: 'delete',
  })
}

/** GET /api/v1/conversations/{conversationId}/messages */
export function getConversationMessagesApi(conversationId: number | string) {
  return request<ChatMessage[]>({
    url: API.HISTORY.MESSAGES(conversationId),
    method: 'get',
  })
}
