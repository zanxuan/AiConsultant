import { defineStore } from 'pinia'
import { ref } from 'vue'
import { MessageRole } from '@/constants/enum'
import type { ChatMessage, CiteSource } from '@/types/chat'
import { normalizeChatMessages } from '@/utils/chatMessage'

export const useChatStore = defineStore('chat', () => {
  const conversationId = ref<number | string | null>(null)
  const messages = ref<ChatMessage[]>([])
  const isStreaming = ref(false)

  function reset() {
    conversationId.value = null
    messages.value = []
    isStreaming.value = false
  }

  function setConversationId(id: number | string | null) {
    conversationId.value = id
  }

  function setMessages(list: unknown) {
    messages.value = normalizeChatMessages(list)
  }

  function appendMessage(message: ChatMessage) {
    messages.value.push(message)
  }

  function appendAssistantChunk(chunk: string) {
    const last = messages.value[messages.value.length - 1]
    if (last?.role === MessageRole.ASSISTANT) {
      last.content += chunk
    } else {
      messages.value.push({
        role: MessageRole.ASSISTANT,
        content: chunk,
      })
    }
  }

  function setLastAssistantSources(sources: CiteSource[]) {
    const last = messages.value[messages.value.length - 1]
    if (last?.role === MessageRole.ASSISTANT) {
      last.sources = sources
    }
  }

  return {
    conversationId,
    messages,
    isStreaming,
    reset,
    setConversationId,
    setMessages,
    appendMessage,
    appendAssistantChunk,
    setLastAssistantSources,
  }
})
