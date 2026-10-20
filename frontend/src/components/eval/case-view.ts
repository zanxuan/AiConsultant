import type { EvalCase, EvalCaseResult, EvalCaseView, EvalFailedCase, EvalRunDetail } from '@/types/eval'
import { formatScore } from './format'

export type EvalCaseStatusFilter = 'all' | 'passed' | 'failed'

export function caseKey(id: number | string | null | undefined) {
  return id == null ? '' : String(id)
}

function detailMap(detail: EvalRunDetail) {
  const map = new Map<string, EvalCaseResult | EvalFailedCase>()
  for (const item of detail.result?.cases ?? []) {
    const key = caseKey(item?.id)
    if (key) map.set(key, item)
  }
  for (const item of detail.result?.failedCases ?? []) {
    const key = caseKey(item?.id)
    if (key && !map.has(key)) map.set(key, item)
  }
  return map
}

function toCaseViewFromResult(item: EvalCaseResult): EvalCaseView {
  return {
    id: Number(item.id),
    query: item.query,
    expectedDocIds: item.expectedDocIds ?? [],
    retrievedDocIds: item.retrievedDocIds ?? [],
    topScore: item.topScore ?? null,
    latencyMs: item.latencyMs ?? null,
    failed: item.hit === false,
    recall: item.recall ?? null,
    mrr: item.mrr ?? null,
  }
}

function toCaseView(detail: EvalRunDetail, item: EvalCase | EvalFailedCase): EvalCaseView {
  const matched = detailMap(detail).get(caseKey(item.id))
  const failedDetail = matched && !('hit' in matched) ? matched : undefined
  const full = matched && 'hit' in matched ? matched : undefined
  return {
    id: Number(item.id),
    query: full?.query ?? failedDetail?.query ?? item.query,
    expectedDocIds: full?.expectedDocIds ?? failedDetail?.expectedDocIds ?? item.expectedDocIds ?? [],
    retrievedDocIds: full?.retrievedDocIds ?? failedDetail?.retrievedDocIds ?? [],
    topScore: full?.topScore ?? failedDetail?.topScore ?? null,
    latencyMs: full?.latencyMs ?? failedDetail?.latencyMs ?? null,
    failed: full ? full.hit === false : Boolean(failedDetail),
    recall: full?.recall ?? (failedDetail ? 0 : null),
    mrr: full?.mrr ?? (failedDetail ? 0 : null),
  }
}

/** 与列表点击抽屉前的补齐逻辑一致，避免详情和列表各算一套指标 */
function enrichCaseView(detail: EvalRunDetail, row: EvalCaseView): EvalCaseView {
  const matched = detailMap(detail).get(caseKey(row.id))
  if (matched && 'hit' in matched) return toCaseViewFromResult(matched)
  if (matched) {
    return {
      ...row,
      query: matched.query ?? row.query,
      expectedDocIds: matched.expectedDocIds?.length ? matched.expectedDocIds : row.expectedDocIds,
      retrievedDocIds: matched.retrievedDocIds ?? [],
      topScore: matched.topScore ?? null,
      latencyMs: matched.latencyMs ?? null,
      failed: true,
      recall: 0,
      mrr: 0,
    }
  }
  return { ...row }
}

export function buildCaseViews(detail: EvalRunDetail | null | undefined): EvalCaseView[] {
  const current = detail?.result
  if (!detail || !current) return []
  if (current.cases?.length) return current.cases.map((item) => toCaseViewFromResult(item))
  return (current.failedCases ?? []).map((item) => toCaseView(detail, item))
}

/** 从评测详情里按用例 ID 取出和列表同一份展示数据。刷新详情页也走这里。 */
export function resolveCaseView(detail: EvalRunDetail, caseId: number | string): EvalCaseView | null {
  const row = buildCaseViews(detail).find((item) => caseKey(item.id) === caseKey(caseId))
  if (!row) return null
  return enrichCaseView(detail, row)
}

export function isGroundTruthDoc(row: EvalCaseView, docId: string) {
  return (row.expectedDocIds ?? []).map(String).includes(String(docId))
}

/** 用例结果只保存 topScore，历史上只展示在第一条召回文档上 */
export function formatRetrievedScore(row: EvalCaseView, index: number) {
  if (index !== 0) return '—'
  return formatScore(row.topScore)
}
