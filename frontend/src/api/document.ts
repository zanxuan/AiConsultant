import { API } from '@/constants/api'
import { request } from '@/utils/request'
import type {
  DocumentItem,
  DocumentListQuery,
  DocumentStatusResult,
  UploadResult,
} from '@/types/document'
import type { PageResult } from '@/types/api'

/** GET /api/v1/documents — knowledgeId / page / size */
export function getDocumentListApi(params: DocumentListQuery) {
  return request<PageResult<DocumentItem>>({
    url: API.DOCUMENT.LIST,
    method: 'get',
    params,
  })
}

/** GET /api/v1/documents/{documentId} */
export function getDocumentDetailApi(documentId: number | string) {
  return request<DocumentItem>({
    url: API.DOCUMENT.DETAIL(documentId),
    method: 'get',
  })
}

/**
 * POST /api/v1/documents/upload
 * FormData: knowledgeId + file（pdf / md / txt）
 */
export function uploadDocumentApi(
  knowledgeId: number | string,
  file: File,
  onProgress?: (percent: number) => void,
) {
  const formData = new FormData()
  formData.append('knowledgeId', String(knowledgeId))
  formData.append('file', file)

  return request<UploadResult>({
    url: API.DOCUMENT.UPLOAD,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (!onProgress || !event.total) return
      onProgress(Math.round((event.loaded / event.total) * 100))
    },
  })
}

/** DELETE /api/v1/documents/{documentId} — MySQL / Redis Vector / 文件 */
export function deleteDocumentApi(documentId: number | string) {
  return request<void>({
    url: API.DOCUMENT.DELETE(documentId),
    method: 'delete',
  })
}

/** POST /api/v1/documents/{documentId}/reindex — V1 预留 */
export function reindexDocumentApi(documentId: number | string) {
  return request<void>({
    url: API.DOCUMENT.REINDEX(documentId),
    method: 'post',
  })
}

/** GET /api/v1/documents/{documentId}/status */
export function getDocumentStatusApi(documentId: number | string) {
  return request<DocumentStatusResult>({
    url: API.DOCUMENT.STATUS(documentId),
    method: 'get',
  })
}
