import { API } from '@/constants/api'
import { request } from '@/utils/request'
import type { EvalDataset, EvalResult } from '@/types/eval'

/** GET /api/v1/eval/dataset — 读取现有 golden-set，不执行评测 */
export function getEvalDatasetApi() {
  return request<EvalDataset>({
    url: API.EVAL.DATASET,
    method: 'get',
  })
}

/** POST /api/v1/eval/run — 同步跑固定 golden-set，不上传文件 */
export function runEvalApi(knowledgeId?: number | string) {
  return request<EvalResult>({
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
  return request<EvalResult>({
    url: API.EVAL.RUN,
    method: 'post',
    data: formData,
    timeout: 10 * 60 * 1000,
  })
}
