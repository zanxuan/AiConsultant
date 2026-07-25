/** 文档解析 / 向量化状态 — 与 GET /documents/{id}/status 对齐 */
export enum DocumentStatus {
  UPLOADING = 'UPLOADING',
  PARSING = 'PARSING',
  EMBEDDING = 'EMBEDDING',
  READY = 'READY',
  FAILED = 'FAILED',
}

export const DocumentStatusLabel: Record<DocumentStatus, string> = {
  [DocumentStatus.UPLOADING]: '上传中',
  [DocumentStatus.PARSING]: '解析中',
  [DocumentStatus.EMBEDDING]: '向量化中',
  [DocumentStatus.READY]: '已就绪',
  [DocumentStatus.FAILED]: '失败',
}

/** 对话消息角色 */
export enum MessageRole {
  USER = 'USER',
  ASSISTANT = 'ASSISTANT',
  SYSTEM = 'SYSTEM',
}
