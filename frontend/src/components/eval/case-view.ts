import type {
  EvalCase,
  EvalCaseResult,
  EvalCaseView,
  EvalFailedCase,
  EvalRunDetail,
  RetrievalTrace,
  RetrievalTraceChunk,
} from '@/types/eval'
import { formatScore } from './format'

/** 与后端 RagEvalService.TOP_K 一致。链路里更靠后的结果不计入 Hit@5。 */
export const EVAL_CHUNK_TOP_K = 5

export type ChunkMark = 'standard' | 'same-document' | 'plain'

export interface GroundTruthChunkRow {
  chunkId: string
  hit: boolean | null
  documentId: string | null
  documentName: string | null
}

export interface RetrievedChunkRow {
  rank: number
  chunkId: string | null
  documentId: string | null
  documentName: string | null
  /** 只描述展示，不参与指标 */
  mark: ChunkMark
}

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
  const chunks = chunkFields(item)
  return {
    id: Number(item.id),
    query: item.query,
    expectedDocIds: item.expectedDocIds ?? [],
    retrievedDocIds: item.retrievedDocIds ?? [],
    ...chunks,
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
  const chunks = full ? chunkFields(full) : failedDetail ? chunkFields(failedDetail) : emptyChunkFields()
  return {
    id: Number(item.id),
    query: full?.query ?? failedDetail?.query ?? item.query,
    expectedDocIds: full?.expectedDocIds ?? failedDetail?.expectedDocIds ?? item.expectedDocIds ?? [],
    retrievedDocIds: full?.retrievedDocIds ?? failedDetail?.retrievedDocIds ?? [],
    ...chunks,
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
      ...chunkFields(matched),
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

/** 雪花 ID 保持字符串。不用 Number / parseInt，避免超过安全整数后对不齐。 */
export function chunkIdText(value: number | string | null | undefined): string {
  if (value == null) return ''
  return String(value).trim()
}

function emptyChunkFields(): Pick<EvalCaseView, 'chunkScored' | 'groundTruthChunkIds' | 'retrievedChunkIds' | 'scoreNote'> {
  return {
    chunkScored: false,
    groundTruthChunkIds: null,
    retrievedChunkIds: null,
    scoreNote: null,
  }
}

/** 字段缺失保持 null。只有后端真正返回了数组，才进入 chunk 展示。 */
function chunkFields(item: {
  groundTruthChunkIds?: Array<string | number | null> | null
  retrievedChunkIds?: Array<string | number | null> | null
  scoreNote?: string | null
}): Pick<EvalCaseView, 'chunkScored' | 'groundTruthChunkIds' | 'retrievedChunkIds' | 'scoreNote'> {
  const hasGroundTruth = item.groundTruthChunkIds != null
  const hasRetrieved = item.retrievedChunkIds != null
  if (!hasGroundTruth && !hasRetrieved) return emptyChunkFields()
  return {
    chunkScored: true,
    groundTruthChunkIds: hasGroundTruth ? normalizeGroundTruth(item.groundTruthChunkIds) : null,
    retrievedChunkIds: hasRetrieved ? normalizeRetrieved(item.retrievedChunkIds) : null,
    scoreNote: item.scoreNote ?? null,
  }
}

function normalizeGroundTruth(values: Array<string | number | null> | null | undefined): string[] {
  const ids: string[] = []
  const seen = new Set<string>()
  for (const value of values ?? []) {
    const id = chunkIdText(value)
    if (!id || seen.has(id)) continue
    seen.add(id)
    ids.push(id)
  }
  return ids
}

function normalizeRetrieved(values: Array<string | number | null> | null | undefined): Array<string | null> {
  return (values ?? []).map((value) => {
    const id = chunkIdText(value)
    return id || null
  })
}

export function isStandardChunk(groundTruth: string[] | null | undefined, chunkId: number | string | null | undefined) {
  const id = chunkIdText(chunkId)
  if (!id || groundTruth == null) return false
  return groundTruth.includes(id)
}

/** 只收集链路里确实出现过的标准 chunk 所属文档，找不到就不编造。 */
export function groundTruthDocumentIds(
  groundTruth: string[] | null | undefined,
  rows: RetrievalTraceChunk[] | null | undefined,
) {
  const docs = new Set<string>()
  if (!groundTruth?.length || !rows?.length) return docs
  for (const row of rows) {
    if (!isStandardChunk(groundTruth, row.chunkId)) continue
    const documentId = chunkIdText(row.documentId)
    if (documentId) docs.add(documentId)
  }
  return docs
}

export function chunkMark(
  groundTruth: string[] | null | undefined,
  chunkId: number | string | null | undefined,
  documentId: number | string | null | undefined,
  knownGroundTruthDocIds: ReadonlySet<string>,
): ChunkMark {
  if (isStandardChunk(groundTruth, chunkId)) return 'standard'
  const document = chunkIdText(documentId)
  if (document && knownGroundTruthDocIds.has(document)) return 'same-document'
  return 'plain'
}

export function traceChunkRows(trace: RetrievalTrace | null | undefined): RetrievalTraceChunk[] {
  if (!trace?.saved) return []
  return [
    ...(trace.vectorHits ?? []),
    ...(trace.bm25Hits ?? []),
    ...(trace.rrfHits ?? []),
    ...(trace.afterFilter ?? []),
    ...(trace.finalTopK ?? []),
  ]
}

function sameChunk(left: number | string | null | undefined, right: string | null) {
  const text = chunkIdText(left)
  if (!right) return text === ''
  return text === right
}

function alignedTraceRow(rows: RetrievalTraceChunk[], rank: number, chunkId: string | null) {
  return rows.find((row) => row.finalRank === rank && sameChunk(row.chunkId, chunkId))
}

function documentOfChunk(rows: RetrievalTraceChunk[], chunkId: string) {
  const matched = rows.filter((row) => chunkIdText(row.chunkId) === chunkId)
  return matched.find((row) => chunkIdText(row.documentId)) ?? matched[0]
}

export function buildGroundTruthRows(
  groundTruthChunkIds: string[] | null,
  retrievedChunkIds: Array<string | null> | null,
  traceRows: RetrievalTraceChunk[],
  names?: Map<string, string>,
): GroundTruthChunkRow[] {
  if (groundTruthChunkIds == null) return []
  return groundTruthChunkIds.map((chunkId) => {
    const source = documentOfChunk(traceRows, chunkId)
    const documentId = chunkIdText(source?.documentId) || null
    return {
      chunkId,
      hit: retrievedChunkIds == null ? null : retrievedChunkIds.includes(chunkId),
      documentId,
      documentName: source?.documentName || (documentId ? names?.get(documentId) ?? null : null),
    }
  })
}

export function buildRetrievedChunkRows(
  retrievedChunkIds: Array<string | null> | null,
  groundTruthChunkIds: string[] | null,
  traceRows: RetrievalTraceChunk[],
  names?: Map<string, string>,
): RetrievedChunkRow[] {
  if (retrievedChunkIds == null) return []
  const knownDocs = groundTruthDocumentIds(groundTruthChunkIds, traceRows)
  return retrievedChunkIds.map((chunkId, index) => {
    const rank = index + 1
    const aligned = alignedTraceRow(traceRows, rank, chunkId)
    const fallback = chunkId ? documentOfChunk(traceRows, chunkId) : undefined
    const source = chunkIdText(aligned?.documentId) ? aligned : (fallback ?? aligned)
    const documentId = chunkIdText(source?.documentId) || null
    return {
      rank,
      chunkId,
      documentId,
      documentName: source?.documentName || (documentId ? names?.get(documentId) ?? null : null),
      mark: chunkMark(groundTruthChunkIds, chunkId, documentId, knownDocs),
    }
  })
}

export function inEvalChunkScope(rank: number | null | undefined) {
  return rank != null && rank >= 1 && rank <= EVAL_CHUNK_TOP_K
}

/** 用例结果只保存 topScore，历史上只展示在第一条召回文档上 */
export function formatRetrievedScore(row: EvalCaseView, index: number) {
  if (index !== 0) return '—'
  return formatScore(row.topScore)
}
