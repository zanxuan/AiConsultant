import { MessageRole } from '@/constants/enum'
import type { ChatMessage, CiteSource } from '@/types/chat'

function unwrapMessageList(raw: unknown): Record<string, unknown>[] {
  if (Array.isArray(raw)) return raw
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    if (Array.isArray(obj.list)) return obj.list as Record<string, unknown>[]
    if (Array.isArray(obj.records)) return obj.records as Record<string, unknown>[]
    if (Array.isArray(obj.messages)) return obj.messages as Record<string, unknown>[]
  }
  return []
}

function normalizeRole(raw: unknown): MessageRole {
  const value = String(raw ?? 'USER').trim().toUpperCase()
  if (value === 'ASSISTANT' || value === 'AI' || value === 'BOT') {
    return MessageRole.ASSISTANT
  }
  if (value === 'SYSTEM') return MessageRole.SYSTEM
  return MessageRole.USER
}

function pickContent(item: Record<string, unknown>): string {
  const value = item.content ?? item.message ?? item.text ?? item.answer ?? ''
  return value == null ? '' : String(value)
}

/**
 * 后端落库字段为 reference（JSON 字符串）；
 * 即时问答返回为 references（数组）。
 */
function pickSources(item: Record<string, unknown>): CiteSource[] | undefined {
  const raw = item.references ?? item.sources ?? item.reference
  if (raw == null || raw === '') return undefined

  let list: unknown = raw
  if (typeof raw === 'string') {
    try {
      list = JSON.parse(raw)
    } catch {
      // 极旧数据可能只是纯文本文件名
      return [{ documentName: raw }]
    }
  }

  if (!Array.isArray(list) || !list.length) return undefined

  return list.map((entry) => {
    if (entry == null || typeof entry !== 'object') {
      return { documentName: String(entry) }
    }
    const obj = entry as Record<string, unknown>
    return {
      documentId: (obj.documentId ?? obj.docId) as string | number | undefined,
      documentName: String(obj.documentName ?? obj.name ?? '未知文档'),
      page: (obj.page ?? obj.pageNumber) as number | string | undefined,
      content: obj.content != null ? String(obj.content) : undefined,
      score: typeof obj.score === 'number' ? obj.score : undefined,
    }
  })
}

/** 将后端历史消息统一成前端 ChatMessage（角色 / 引用字段差异） */
export function normalizeChatMessages(raw: unknown): ChatMessage[] {
  return unwrapMessageList(raw).map((item) => ({
    id: item.id as string | number | undefined,
    role: normalizeRole(item.role ?? item.messageRole),
    content: pickContent(item),
    sources: pickSources(item),
    createdAt: (item.createdAt ?? item.createTime) as string | undefined,
  }))
}
