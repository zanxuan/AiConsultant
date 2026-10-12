import { API } from '@/constants/api'
import { request } from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { EvalDataset, EvalRun, EvalRunDetail, EvalRunExecute, EvalRunQuery } from '@/types/eval'

/** GET /api/v1/eval/dataset — 读取现有 golden-set，不执行评测 */
export function getEvalDatasetApi() {
  return request<EvalDataset>({
    url: API.EVAL.DATASET,
    method: 'get',
  })
}

/** POST /api/v1/eval/run — 同步跑固定 golden-set，不上传文件 */
export function runEvalApi(knowledgeId?: number | string) {
  return request<EvalRunExecute>({
    url: API.EVAL.RUN,
    method: 'post',
    params: knowledgeId == null ? undefined : { knowledgeId: String(knowledgeId) },
    // 后端同步跑完检索，超过默认 60s
    timeout: 10 * 60 * 1000,
  })
}

/**
 * POST /api/v1/eval/run
 * multipart：dataset 为 EvalCase JSON，knowledgeId 为知识库 ID（字符串，避免雪花 ID 丢精度）
 */
export function runUploadedEvalApi(dataset: File, knowledgeId: number | string) {
  const formData = new FormData()
  formData.append('dataset', dataset)
  formData.append('knowledgeId', String(knowledgeId))
  return request<EvalRunExecute>({
    url: API.EVAL.RUN,
    method: 'post',
    data: formData,
    timeout: 10 * 60 * 1000,
  })
}

/** GET /api/v1/eval/runs — 当前用户的评测历史，后端按 pageNum / pageSize 分页 */
export function getEvalRunsApi(params?: EvalRunQuery) {
  return request<PageResult<EvalRun>>({
    url: API.EVAL.RUNS,
    method: 'get',
    params,
  })
}

/** GET /api/v1/eval/runs/{id} — 单条详情，含完整 EvalResult */
export function getEvalRunDetailApi(id: number | string) {
  return request<EvalRunDetail>({
    url: API.EVAL.RUN_DETAIL(id),
    method: 'get',
  })
}
