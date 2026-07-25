import { API } from '@/constants/api'
import { request } from '@/utils/request'
import type { ChatSendParams, ChatSendResult } from '@/types/chat'

/**
 * POST /api/v1/chat
 * 当前为非流式；后续可升级 SSE，地址保持不变
 */
export function sendChatApi(data: ChatSendParams) {
  return request<ChatSendResult>({
    url: API.CHAT.SEND,
    method: 'post',
    data,
  })
}

/** 与发送同一地址，供后续 SSE 流式升级使用 */
export function getChatStreamUrl() {
  return `${import.meta.env.VITE_API_BASE_URL}${API.CHAT.SEND}`
}
