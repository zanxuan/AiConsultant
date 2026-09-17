import { ElMessage } from 'element-plus'
import { API } from '@/constants/api'
import { request } from '@/utils/request'
import { getToken } from '@/utils/auth'
import { getApiMessage, type ApiResult } from '@/types/api'
import type { ChatCompleteResult, ChatSendParams, ChatSendResult } from '@/types/chat'

/**
 * POST /api/v1/chat
 * 立即返回 taskId；最终 ChatResp 由 GET /chat/stream/{taskId} 以 SSE complete 推送。
 */
export function sendChatApi(data: ChatSendParams) {
  return request<ChatSendResult>({
    url: API.CHAT.SEND,
    method: 'post',
    data,
  })
}

/** GET /api/v1/chat/stream/{taskId} */
export function getChatStreamUrl(taskId: string) {
  return `${import.meta.env.VITE_API_BASE_URL}${API.CHAT.STREAM(taskId)}`
}

/**
 * 与 axios transformResponse 相同：雪花 ID 超过 Number.MAX_SAFE_INTEGER 时先转成字符串。
 */
function parseJsonSafe(raw: string): unknown {
  if (!raw) return raw
  try {
    const safe = raw.replace(/([:\[,]\s*)(\d{16,})(\s*[,\]}])/g, '$1"$2"$3')
    return JSON.parse(safe)
  } catch {
    return JSON.parse(raw)
  }
}

function isAbortError(error: unknown): boolean {
  return (
    (error instanceof DOMException && error.name === 'AbortError') ||
    (error instanceof Error && error.name === 'AbortError')
  )
}

async function handleStreamHttpError(response: Response): Promise<never> {
  let message = `问答流请求失败 (${response.status})`
  try {
    const body = (await response.json()) as ApiResult
    message = getApiMessage(body) || message
  } catch {
    // 拦截器 401 等场景可能没有 JSON body
  }

  if (response.status === 401) {
    const { useUserStore } = await import('@/stores/user')
    useUserStore().resetAuth()
    ElMessage.error('登录已过期，请重新登录')
  } else {
    ElMessage.error(message)
  }
  throw new Error(message)
}

function splitSseField(line: string): { field: string; value: string } | null {
  if (!line || line.startsWith(':')) return null
  const colon = line.indexOf(':')
  if (colon === -1) return { field: line, value: '' }
  const field = line.slice(0, colon)
  let value = line.slice(colon + 1)
  if (value.startsWith(' ')) value = value.slice(1)
  return { field, value }
}

/**
 * 按 SSE 规范拆 event/data；一块 buffer 里可能有多条事件，且事件可能被拆到多次 read。
 */
function consumeSseBlock(
  block: string,
  handlers: {
    onProgress?: (message: string) => void
    onComplete: (data: ChatCompleteResult) => void
  },
): 'complete' | 'continue' {
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of block.split('\n')) {
    const parsed = splitSseField(line)
    if (!parsed) continue
    if (parsed.field === 'event') eventName = parsed.value
    else if (parsed.field === 'data') dataLines.push(parsed.value)
  }

  const data = dataLines.join('\n')
  if (!eventName && !data) return 'continue'

  if (eventName === 'progress') {
    handlers.onProgress?.(data)
    return 'continue'
  }

  if (eventName === 'complete') {
    let payload: ChatCompleteResult
    try {
      payload = parseJsonSafe(data) as ChatCompleteResult
    } catch {
      ElMessage.error('问答结果解析失败')
      throw new Error('问答结果解析失败')
    }
    handlers.onComplete(payload)
    return 'complete'
  }

  if (eventName === 'error') {
    const message = data || '问答失败'
    ElMessage.error(message)
    throw new Error(message)
  }

  return 'continue'
}

/**
 * fetch + ReadableStream 订阅聊天 SSE。
 * 必须显式带 authentication Header：原生 EventSource 无法设置自定义头。
 */
export async function consumeChatSse(
  taskId: string,
  handlers: {
    onProgress?: (message: string) => void
    onComplete: (data: ChatCompleteResult) => void
    signal?: AbortSignal
  },
): Promise<void> {
  const token = getToken()
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
  }
  if (token) {
    headers.authentication = `Bearer ${token}`
  }

  let response: Response
  try {
    response = await fetch(getChatStreamUrl(taskId), {
      method: 'GET',
      headers,
      signal: handlers.signal,
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    ElMessage.error((error as Error).message || '网络异常')
    throw error
  }

  if (!response.ok) {
    await handleStreamHttpError(response)
  }
  if (!response.body) {
    ElMessage.error('无法读取问答流')
    throw new Error('无法读取问答流')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value || new Uint8Array(), { stream: !done })
      buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

      let separator = buffer.indexOf('\n\n')
      while (separator >= 0) {
        const block = buffer.slice(0, separator)
        buffer = buffer.slice(separator + 2)
        if (block.trim()) {
          const status = consumeSseBlock(block, handlers)
          if (status === 'complete') {
            await reader.cancel()
            return
          }
        }
        separator = buffer.indexOf('\n\n')
      }

      if (done) break
    }

    if (handlers.signal?.aborted) {
      throw new DOMException('The operation was aborted.', 'AbortError')
    }

    if (buffer.trim()) {
      const status = consumeSseBlock(buffer, handlers)
      if (status === 'complete') return
    }

    ElMessage.error('问答流已结束但未收到结果')
    throw new Error('问答流已结束但未收到结果')
  } finally {
    try {
      reader.releaseLock()
    } catch {
      // cancel() 之后锁可能已经释放
    }
  }
}
