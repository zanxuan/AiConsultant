/** 与后端 EvalCase 对齐。chunk 字段缺失表示旧记录，不能当成空数组。 */
export interface EvalCase {
  id: number
  sourceCaseId?: string | null
  query: string
  expectedDocIds?: string[] | null
  groundTruthChunkIds?: Array<string | number | null> | null
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
  /** 旧快照没有这些字段时为 null 或缺失 */
  groundTruthChunkIds?: Array<string | number | null> | null
  retrievedChunkIds?: Array<string | number | null> | null
  scoreNote?: string | null
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
  /** 旧快照没有这些字段时为 null 或缺失，不能默认成 [] */
  groundTruthChunkIds?: Array<string | number | null> | null
  retrievedChunkIds?: Array<string | number | null> | null
  scoreNote?: string | null
  /** 旧记录没有这些字段 */
  vectorHitCount?: number | null
  bm25HitCount?: number | null
  bm25Query?: string | null
  expectedHits?: ExpectedDocHit[] | null
  missStage?: string | null
}

/** 单个期望文档在各检索阶段的命中。空排名表示该阶段没有出现 */
export interface ExpectedDocHit {
  expectedDocId: string
  vectorRank?: number | null
  bm25Rank?: number | null
  rrfRank?: number | null
  filtered?: boolean | null
  filterReason?: string | null
  finalRank?: number | null
  evalDocRank?: number | null
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
    retrieverBm25MinScore?: number | null
    rrfK?: number | null
    goldenSetPath?: string | null
  }
  /** 参与计算的用例快照，不是评测明细 */
  cases?: EvalCase[]
  /** 完整评测结果；失败且尚未算出结果时为空 */
  result?: EvalResult | null
}

/** 检索链路中的一个候选 chunk。正文和文档名由详情接口临时查出。 */
export interface RetrievalTraceChunk {
  chunkId?: number | string | null
  documentId?: number | string | null
  documentName?: string | null
  page?: number | null
  content?: string | null
  vectorRank?: number | null
  vectorScore?: number | null
  bm25Rank?: number | null
  bm25Score?: number | null
  rrfRank?: number | null
  rrfScore?: number | null
  filtered?: boolean | null
  filterReason?: string | null
  afterFilterRank?: number | null
  finalRank?: number | null
  evalDocRank?: number | null
}

export interface RetrievalEvalDoc {
  rank: number
  documentId?: number | string | null
  documentName?: string | null
  expected?: boolean | null
}

/** GET /eval/runs/{id}/chunk-contents。content 为空表示这条原文读不到 */
export interface EvalChunkContent {
  chunkId?: string | number | null
  content?: string | null
}

/** GET /eval/runs/{id}/cases/{caseId}/retrieval-trace */
export interface RetrievalTrace {
  runId?: number | string
  caseId?: number
  saved: boolean
  vectorHitCount?: number | null
  bm25HitCount?: number | null
  bm25Query?: string | null
  missStage?: string | null
  expectedHits?: ExpectedDocHit[]
  vectorHits?: RetrievalTraceChunk[]
  bm25Hits?: RetrievalTraceChunk[]
  rrfHits?: RetrievalTraceChunk[]
  afterFilter?: RetrievalTraceChunk[]
  finalTopK?: RetrievalTraceChunk[]
  evalDocs?: RetrievalEvalDoc[]
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

/** 页面 Case 列表展示模型。chunk 列表为 null 表示沿用文档级展示。 */
export interface EvalCaseView {
  id: number
  query: string
  expectedDocIds: string[]
  retrievedDocIds: string[]
  /** true 只表示结果里带了 chunk 字段，不重新计算指标 */
  chunkScored: boolean
  groundTruthChunkIds: string[] | null
  /** 评测前 5 条，空 chunk ID 保留为 null，名次不前移 */
  retrievedChunkIds: Array<string | null> | null
  scoreNote: string | null
  topScore: number | null
  latencyMs: number | null
  failed: boolean
  recall: number | null
  mrr: number | null
}
