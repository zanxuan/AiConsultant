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

/** POST /api/v1/eval/run — 同步执行现有 evaluate()，不传 knowledgeId */
export function runEvalApi() {
  return request<EvalResult>({
    url: API.EVAL.RUN,
    method: 'post',
    // 后端同步跑完 50 条检索，超过默认 60s
    timeout: 10 * 60 * 1000,
  })
}
