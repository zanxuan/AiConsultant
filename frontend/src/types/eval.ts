/** 与后端 EvalCase 对齐 */
export interface EvalCase {
  id: number
  query: string
  expectedDocIds: string[]
}

/** GET /eval/dataset */
export interface EvalDataset {
  name: string
  cases: EvalCase[]
}

/** 与后端 FailedCase 对齐 */
export interface EvalFailedCase {
  id: number
  query: string
  expectedDocIds: string[]
  retrievedDocIds: string[]
  topScore: number | null
  latencyMs?: number | null
}

/** 与后端 EvalCaseResult 对齐：Passed / Failed 都有检索明细 */
export interface EvalCaseResult {
  id: number
  query: string
  expectedDocIds: string[]
  retrievedDocIds: string[]
  topScore: number | null
  latencyMs?: number | null
  hit: boolean
  recall: number
  mrr: number
}

/** 与后端 EvalResult 对齐 */
export interface EvalResult {
  total: number
  hitRate: number
  recall: number
  mrr: number
  failedCases: EvalFailedCase[]
  cases?: EvalCaseResult[]
  avgLatencyMs?: number | null
  p95LatencyMs?: number | null
}

/** 页面 Case 列表展示模型 */
export interface EvalCaseView {
  id: number
  query: string
  expectedDocIds: string[]
  retrievedDocIds: string[]
  topScore: number | null
  latencyMs: number | null
  failed: boolean
  recall: number | null
  mrr: number | null
}
