import { EVAL_CASE_LIST_STATE_KEY } from '@/constants/storage'
import type { EvalCaseStatusFilter } from './case-view'

export interface EvalCaseListSnapshot {
  section: 'cases' | 'failed'
  runId: string
  caseId: string
  keyword: string
  statusFilter: EvalCaseStatusFilter
  page: number
  pageSize: number
  scrollTop: number
}

export interface EvalCaseLocation {
  runId: string
  caseId: string
  section: 'cases' | 'failed' | ''
}

const MAIN_SCROLL_SELECTOR = '.main-layout__content'

/** 只在同一次页面会话里标记「正从用例详情回到列表」，刷新列表页不会误恢复 */
let returnToEvalList = false

export function markReturnToEvalList() {
  returnToEvalList = true
}

export function clearReturnToEvalList() {
  returnToEvalList = false
}

export function consumeReturnToEvalList() {
  const returning = returnToEvalList
  returnToEvalList = false
  return returning
}

function safeDecode(value: string) {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

function normalizeSnapshot(value: unknown): EvalCaseListSnapshot | null {
  if (!value || typeof value !== 'object') return null
  const item = value as Partial<EvalCaseListSnapshot>
  if (item.section !== 'cases' && item.section !== 'failed') return null
  if (typeof item.runId !== 'string' || !item.runId) return null
  if (typeof item.caseId !== 'string') return null
  if (typeof item.keyword !== 'string') return null
  if (item.statusFilter !== 'all' && item.statusFilter !== 'passed' && item.statusFilter !== 'failed') return null
  const page = Number(item.page)
  const pageSize = Number(item.pageSize)
  const scrollTop = Number(item.scrollTop)
  if (!Number.isFinite(page) || !Number.isFinite(pageSize) || !Number.isFinite(scrollTop)) return null
  return {
    section: item.section,
    runId: item.runId,
    caseId: item.caseId,
    keyword: item.keyword,
    statusFilter: item.statusFilter,
    page: Math.max(1, Math.round(page)),
    pageSize: Math.max(1, Math.round(pageSize)),
    scrollTop: Math.max(0, scrollTop),
  }
}

export function saveEvalCaseListSnapshot(snapshot: EvalCaseListSnapshot) {
  try {
    sessionStorage.setItem(EVAL_CASE_LIST_STATE_KEY, JSON.stringify(snapshot))
  } catch {
    // 隐私模式等写不进会话存储时，返回列表退回默认评测页
  }
}

export function peekEvalCaseListSnapshot() {
  try {
    const raw = sessionStorage.getItem(EVAL_CASE_LIST_STATE_KEY)
    if (!raw) return null
    const snapshot = normalizeSnapshot(JSON.parse(raw))
    if (!snapshot) sessionStorage.removeItem(EVAL_CASE_LIST_STATE_KEY)
    return snapshot
  } catch {
    return null
  }
}

export function consumeEvalCaseListSnapshot() {
  const snapshot = peekEvalCaseListSnapshot()
  if (!snapshot) return null
  try {
    sessionStorage.removeItem(EVAL_CASE_LIST_STATE_KEY)
  } catch {
    // 读到了快照即可恢复；删不掉时下次进入仍会再判断 runId
  }
  return snapshot
}

/** 详情地址：/eval/runs/:runId/cases/:caseId?from=cases|failed */
export function parseEvalCasePath(path: string | null | undefined): EvalCaseLocation | null {
  if (!path) return null
  const match = path.match(/\/eval\/runs\/([^/?#]+)\/cases\/([^/?#]+)/)
  if (!match) return null
  const queryIndex = path.indexOf('?')
  const from = new URLSearchParams(queryIndex >= 0 ? path.slice(queryIndex + 1) : '').get('from')
  return {
    runId: safeDecode(match[1]),
    caseId: safeDecode(match[2]),
    section: from === 'failed' || from === 'cases' ? from : '',
  }
}

/** 只匹配评测列表，不把对比页和用例详情当成列表 */
export function isEvalListPath(path: string | null | undefined) {
  if (!path) return false
  const pathname = path.split(/[?#]/)[0].replace(/\/+$/, '') || '/'
  return pathname === '/eval' || pathname.endsWith('/eval')
}

export function readMainScrollTop() {
  const el = document.querySelector(MAIN_SCROLL_SELECTOR)
  return el instanceof HTMLElement ? el.scrollTop : 0
}

export function writeMainScrollTop(top: number) {
  const el = document.querySelector(MAIN_SCROLL_SELECTOR)
  if (el instanceof HTMLElement) el.scrollTop = top
}
