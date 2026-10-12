import type { EvalRunDetail } from '@/types/eval'

/** 详情页 8 项已保存指标。前 6 项可回退到同一次 result，整次耗时只在运行记录上。 */
export interface EvalSavedMetrics {
  total: number | null
  hitRate: number | null
  recall: number | null
  mrr: number | null
  avgLatencyMs: number | null
  p95LatencyMs: number | null
  failedCount: number | null
  durationMs: number | null
}

export function savedMetrics(detail: EvalRunDetail | null | undefined): EvalSavedMetrics {
  const saved = detail?.result
  return {
    total: firstNumber(detail?.totalCases, saved?.total),
    hitRate: firstNumber(detail?.hitRate, saved?.hitRate),
    recall: firstNumber(detail?.recall, saved?.recall),
    mrr: firstNumber(detail?.mrr, saved?.mrr),
    avgLatencyMs: firstNumber(detail?.avgLatencyMs, saved?.avgLatencyMs),
    p95LatencyMs: firstNumber(detail?.p95LatencyMs, saved?.p95LatencyMs),
    failedCount: firstNumber(detail?.failedCount, saved?.failedCases?.length),
    durationMs: firstNumber(detail?.durationMs),
  }
}

function firstNumber(...values: Array<number | null | undefined>) {
  for (const value of values) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) continue
    return Number(value)
  }
  return null
}

export function formatCount(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return String(Math.round(Number(value)))
}

export function formatConfig(value: unknown) {
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

export function formatDocIds(ids?: string[] | null) {
  if (!ids?.length) return '—'
  return ids.join(', ')
}

export function formatRate(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '-'
  return `${(Number(value) * 100).toFixed(1)}%`
}

export function formatMrr(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return Number(value).toFixed(2)
}

export function formatCaseRate(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return `${Math.round(Number(value) * 100)}%`
}

export function formatLatency(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return `${Math.round(Number(value))} ms`
}

export function formatScore(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return Number(value).toFixed(2)
}

export function statusLabel(status?: string | null) {
  if (status === 'RUNNING') return '进行中'
  if (status === 'SUCCESS') return '成功'
  if (status === 'FAILED') return '失败'
  return status || '—'
}

export function statusTagType(status?: string | null): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

export function datasetSourceLabel(source?: string | null) {
  if (source === 'GOLDEN_SET') return '固定测试集'
  if (source === 'UPLOAD') return '上传测试集'
  return source || '—'
}
