import type { EvalRunDetail } from '@/types/eval'
import { savedMetrics, type EvalSavedMetrics } from './format'

export type CompareTone = 'better' | 'worse' | 'neutral'

export interface EvalCompareRow {
  key: string
  label: string
  aText: string
  bText: string
  deltaText: string
  tone: CompareTone
}

type MetricKey = keyof EvalSavedMetrics
type MetricDirection = 'higher' | 'lower' | 'none'
type MetricFormat = 'count' | 'rate' | 'mrr' | 'latency'

const METRICS: {
  key: MetricKey
  label: string
  format: MetricFormat
  direction: MetricDirection
}[] = [
  { key: 'total', label: '用例数', format: 'count', direction: 'none' },
  { key: 'hitRate', label: 'Hit@5', format: 'rate', direction: 'higher' },
  { key: 'recall', label: 'Recall@5', format: 'rate', direction: 'higher' },
  { key: 'mrr', label: 'MRR', format: 'mrr', direction: 'higher' },
  { key: 'avgLatencyMs', label: '平均检索耗时', format: 'latency', direction: 'lower' },
  { key: 'p95LatencyMs', label: 'P95', format: 'latency', direction: 'lower' },
  { key: 'failedCount', label: '失败用例数', format: 'count', direction: 'lower' },
  { key: 'durationMs', label: '整次耗时', format: 'latency', direction: 'lower' },
]

/** 差值一律按 B−A。任一侧缺失时不把空值当成 0。 */
export function buildCompareRows(runA: EvalRunDetail, runB: EvalRunDetail): EvalCompareRow[] {
  const metricsA = savedMetrics(runA)
  const metricsB = savedMetrics(runB)
  return METRICS.map((metric) => {
    const a = readMetric(metricsA, metric.key)
    const b = readMetric(metricsB, metric.key)
    const delta = a == null || b == null ? null : b - a
    return {
      key: metric.key,
      label: metric.label,
      aText: formatMetric(a, metric.format),
      bText: formatMetric(b, metric.format),
      deltaText: formatDelta(delta, metric.format),
      tone: deltaTone(delta, metric.format, metric.direction),
    }
  })
}

function readMetric(metrics: EvalSavedMetrics, key: MetricKey) {
  const value = metrics[key]
  if (value == null || Number.isNaN(Number(value))) return null
  return Number(value)
}

function formatMetric(value: number | null, format: MetricFormat) {
  if (value == null) return '—'
  if (format === 'rate') return `${(value * 100).toFixed(1)}%`
  if (format === 'mrr') return value.toFixed(2)
  if (format === 'latency') return `${Math.round(value)} ms`
  return String(Math.round(value))
}

function formatDelta(delta: number | null, format: MetricFormat) {
  if (delta == null) return '—'
  if (format === 'rate') {
    const points = Number((delta * 100).toFixed(1))
    if (points === 0) return '0'
    const sign = points > 0 ? '+' : ''
    return `${sign}${points.toFixed(1)} 个百分点`
  }
  const digits = format === 'mrr' ? 2 : 0
  const rounded = Number(delta.toFixed(digits))
  if (rounded === 0) return '0'
  const sign = rounded > 0 ? '+' : ''
  const body = `${sign}${rounded.toFixed(digits)}`
  return format === 'latency' ? `${body} ms` : body
}

function deltaTone(delta: number | null, format: MetricFormat, direction: MetricDirection): CompareTone {
  if (delta == null || direction === 'none') return 'neutral'
  const digits = format === 'rate' ? 1 : format === 'mrr' ? 2 : 0
  const rounded = format === 'rate' ? Number((delta * 100).toFixed(1)) : Number(delta.toFixed(digits))
  if (rounded === 0) return 'neutral'
  const improved = direction === 'higher' ? rounded > 0 : rounded < 0
  return improved ? 'better' : 'worse'
}
