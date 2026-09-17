import { ref } from 'vue'
import { consumeChatSse, sendChatApi } from '@/api/chat'
import { createConversationApi } from '@/api/history'
import { useChatStore } from '@/stores/chat'
import { MessageRole } from '@/constants/enum'
import type { ChatAskParams, ChatMessage } from '@/types/chat'

const THINKING_FALLBACK = '正在思考...'

/**
 * RAG 问答：POST /api/v1/chat 拿到 taskId，再 GET /chat/stream/{taskId} 收 SSE。
 */
export function useChatStream() {
  const chatStore = useChatStore()
  const error = ref<string | null>(null)
  let aborted = false
  let abortController: AbortController | null = null

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

  function isCurrentRequest(ac: AbortController) {
    return !aborted && !ac.signal.aborted
  }

  /** 只改当前这条 assistant 占位，不往 messages 追加 */
  function patchLastAssistant(ac: AbortController, patch: Partial<ChatMessage>) {
    if (!isCurrentRequest(ac)) return
    const last = chatStore.messages[chatStore.messages.length - 1]
    if (last?.role !== MessageRole.ASSISTANT) return
    if (patch.content != null) last.content = patch.content
    if (patch.sources) last.sources = patch.sources
    if ('status' in patch) last.status = patch.status
    // 允许 complete / error 时把 progress 清成 undefined
    if ('progress' in patch) last.progress = patch.progress
  }

  /** Spring 可能把纯文本 progress 编成 JSON 字符串，展示时去掉外层引号 */
  function asProgressText(raw: string) {
    const text = raw.trim()
    if (!text) return ''
    if (text.startsWith('"')) {
      try {
        const parsed = JSON.parse(text)
        if (typeof parsed === 'string') return parsed
      } catch {
        // 非 JSON 时原样作为进度文案
      }
    }
    return text
  }

  /** 有效 progress 原样展示；去掉 JSON 引号后仍为空才用兜底 */
  function toThinkingLabel(raw: string) {
    const text = asProgressText(raw)
    return text || THINKING_FALLBACK
  }

  async function send(params: ChatAskParams) {
    error.value = null
    aborted = false
    abortController?.abort()
    const ac = new AbortController()
    abortController = ac
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
        status: 'thinking',
      })

      const result = await sendChatApi({
        conversationId,
        message: params.message,
      })

      if (!isCurrentRequest(ac)) return
      if (!result?.taskId) {
        throw new Error('未返回任务 ID')
      }

      await consumeChatSse(result.taskId, {
        signal: ac.signal,
        onProgress(message) {
          patchLastAssistant(ac, { progress: toThinkingLabel(message) })
        },
        onComplete(payload) {
          patchLastAssistant(ac, {
            content: payload.answer || '',
            sources: payload.references || payload.sources || [],
            status: 'completed',
            progress: undefined,
          })
        },
      })
    } catch (e) {
      // 用户主动 abort / 新对话 不算失败，不改消息、不弹错误
      if (!isCurrentRequest(ac) || (e instanceof Error && e.name === 'AbortError')) return
      error.value = (e as Error).message || '问答失败'
      patchLastAssistant(ac, {
        status: 'error',
        content: error.value,
        progress: undefined,
      })
    } finally {
      // 只清理本次请求，避免覆盖新对话里已开始的下一次 send
      if (abortController === ac) {
        abortController = null
        chatStore.isStreaming = false
      }
    }
  }

  function abort() {
    aborted = true
    abortController?.abort()
    chatStore.isStreaming = false
  }

  return {
    error,
    send,
    abort,
  }
}
