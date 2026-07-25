import { API } from '@/constants/api'
import { request } from '@/utils/request'
import type { KnowledgeBase, KnowledgeForm, KnowledgeListQuery } from '@/types/knowledge'
import type { PageResult } from '@/types/api'

/** GET /api/v1/knowledge-bases — 分页 + 关键字搜索 */
export function getKnowledgeListApi(params?: KnowledgeListQuery) {
  return request<PageResult<KnowledgeBase>>({
    url: API.KNOWLEDGE.LIST,
    method: 'get',
    params,
  })
}

/** GET /api/v1/knowledge-bases/{knowledgeId} */
export function getKnowledgeDetailApi(knowledgeId: number | string) {
  return request<KnowledgeBase>({
    url: API.KNOWLEDGE.DETAIL(knowledgeId),
    method: 'get',
  })
}

/** POST /api/v1/knowledge-bases */
export function createKnowledgeApi(data: KnowledgeForm) {
  return request<KnowledgeBase>({
    url: API.KNOWLEDGE.CREATE,
    method: 'post',
    data,
  })
}

/** PUT /api/v1/knowledge-bases/{knowledgeId} */
export function updateKnowledgeApi(knowledgeId: number | string, data: KnowledgeForm) {
  return request<KnowledgeBase>({
    url: API.KNOWLEDGE.UPDATE(knowledgeId),
    method: 'put',
    data,
  })
}

/** DELETE /api/v1/knowledge-bases/{knowledgeId} */
export function deleteKnowledgeApi(knowledgeId: number | string) {
  return request<void>({
    url: API.KNOWLEDGE.DELETE(knowledgeId),
    method: 'delete',
  })
}
