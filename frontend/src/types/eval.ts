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

/**
 * GET /eval/runs 列表项，与后端 RagEvalRunVO 对齐。
 * 雪花 ID 经请求层转为字符串。
 */
export interface EvalRun {
  id: number | string
  knowledgeId: number | string
  modelName: string | null
  knowledgeName: string | null
  datasetSource: string
  datasetName: string
  status: string
  totalCases: number | null
  hitRate: number | null
  recall: number | null
  mrr: number | null
  avgLatencyMs: number | null
  p95LatencyMs: number | null
  failedCount: number | null
  durationMs: number | null
  errorMessage: string | null
  createTime: string | null
  endTime: string | null
}

/**
 * GET /eval/runs/{id}，与后端 RagEvalRunDetailVO 对齐。
 * configSnapshot 为当时写入的检索参数，cases 为本次评测保存的测试集快照。
 */
export interface EvalRunDetail extends EvalRun {
  configSnapshot?: {
    evalTopK?: number | null
    retrieverMaxResults?: number | null
    retrieverMinScore?: number | null
    rrfK?: number | null
    goldenSetPath?: string | null
  }
  /** 参与计算的用例快照，不是评测明细 */
  cases?: EvalCase[]
  /** 完整评测结果；失败且尚未算出结果时为空 */
  result?: EvalResult | null
}

/** POST /eval/run，与后端 EvalRunExecuteVO 对齐。详情页按 runId 再查一次。 */
export interface EvalRunExecute {
  runId: number | string | null
  result: EvalResult | null
}

/** GET /eval/runs 查询参数，与后端 pageNum / pageSize 对齐 */
export interface EvalRunQuery {
  knowledgeId?: number | string
  status?: string
  pageNum?: number
  pageSize?: number
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
