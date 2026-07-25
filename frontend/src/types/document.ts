import type { DocumentStatus } from '@/constants/enum'

export interface DocumentItem {
  id: number | string
  knowledgeId: number | string
  /** 后端字段：文件名 */
  fileName: string
  fileType?: string
  fileSize?: number
  storagePath?: string
  status: DocumentStatus
  chunkCount?: number
  createTime?: string
  updateTime?: string
}

export interface UploadParams {
  knowledgeId: number | string
  file: File
}

/** POST /api/v1/documents/upload 响应 data */
export interface UploadResult {
  documentId: number | string
  status: DocumentStatus
}

/** GET /api/v1/documents 查询参数 */
export interface DocumentListQuery {
  knowledgeId: number | string
  page?: number
  /** 每页条数（后端参数名为 size） */
  size?: number
}

/** GET /api/v1/documents/{documentId}/status 响应 data */
export interface DocumentStatusResult {
  status: DocumentStatus
}
