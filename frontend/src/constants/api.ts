/** API 路径常量（baseURL 已为 /api/v1，此处不再重复写版本前缀） */
export const API = {
  AUTH: {
    LOGIN: '/auth/login',
    LOGOUT: '/auth/logout',
  },
  USER: {
    ME: '/users/me',
  },
  KNOWLEDGE: {
    LIST: '/knowledge-bases',
    DETAIL: (id: number | string) => `/knowledge-bases/${id}`,
    CREATE: '/knowledge-bases',
    UPDATE: (id: number | string) => `/knowledge-bases/${id}`,
    DELETE: (id: number | string) => `/knowledge-bases/${id}`,
  },
  DOCUMENT: {
    LIST: '/documents',
    UPLOAD: '/documents/upload',
    DETAIL: (id: number | string) => `/documents/${id}`,
    DELETE: (id: number | string) => `/documents/${id}`,
    REINDEX: (id: number | string) => `/documents/${id}/reindex`,
    STATUS: (id: number | string) => `/documents/${id}/status`,
  },
  CHAT: {
    SEND: '/chat',
    STREAM: (taskId: string) => `/chat/stream/${taskId}`,
  },
  /** 会话（对话历史） */
  EVAL: {
    DATASET: '/eval/dataset',
    RUN: '/eval/run',
  },
  HISTORY: {
    LIST: '/conversations',
    CREATE: '/conversations',
    DETAIL: (id: number | string) => `/conversations/${id}`,
    DELETE: (id: number | string) => `/conversations/${id}`,
    MESSAGES: (id: number | string) => `/conversations/${id}/messages`,
  },
} as const
