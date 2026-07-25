import { ref } from 'vue'
import { sendChatApi } from '@/api/chat'
import { createConversationApi } from '@/api/history'
import { useChatStore } from '@/stores/chat'
import { MessageRole } from '@/constants/enum'
import type { ChatAskParams } from '@/types/chat'

/**
 * RAG 问答
 * 当前：POST /api/v1/chat 非流式 { answer, references }
 * 预留：同地址可升级为 SSE 流式
 */
export function useChatStream() {
  const chatStore = useChatStore()
  const error = ref<string | null>(null)
  let aborted = false

  async function ensureConversation(params: ChatAskParams) {
    if (params.conversationId ?? chatStore.conversationId) {
      return (params.conversationId ?? chatStore.conversationId)!
    }

    const title = (params.title || params.message).trim().slice(0, 40) || '新对话'
    const conversation = await createConversationApi({
      knowledgeId: params.knowledgeId,
      title,
    })
    chatStore.setConversationId(conversation.id)
    return conversation.id
  }

  async function send(params: ChatAskParams) {
    error.value = null
    aborted = false
    chatStore.isStreaming = true

    try {
      const conversationId = await ensureConversation(params)

      chatStore.appendMessage({
        role: MessageRole.USER,
        content: params.message,
      })
      chatStore.appendMessage({
        role: MessageRole.ASSISTANT,
        content: '',
      })

      const result = await sendChatApi({
        conversationId,
        message: params.message,
      })

      if (aborted) return

      const last = chatStore.messages[chatStore.messages.length - 1]
      if (last?.role === MessageRole.ASSISTANT) {
        last.content = result.answer || ''
        last.sources = result.references || result.sources || []
      }
    } catch (e) {
      if (aborted) return
      error.value = (e as Error).message || '问答失败'

      const last = chatStore.messages[chatStore.messages.length - 1]
      if (last?.role === MessageRole.ASSISTANT && !last.content) {
        chatStore.messages.pop()
      }
    } finally {
      chatStore.isStreaming = false
    }
  }

  function abort() {
    aborted = true
    chatStore.isStreaming = false
  }

  return {
    error,
    send,
    abort,
  }
}
