export function formatFileSize(bytes?: number): string {
  if (bytes === undefined || bytes === null || Number.isNaN(bytes)) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function formatDateTime(value?: string | number | Date): string {
  if (!value) return '-'
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}/.test(value)) {
    // 后端已格式化的时间串（如 2026-07-23 09:58）直接展示
    return value.length >= 16 ? value.slice(0, 16) : value
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 相对日期：今天 / 昨天 / N天前 / 具体日期 */
export function formatRelativeDate(value?: string | number | Date): string {
  if (!value) return '-'
  const date =
    typeof value === 'string' && /^\d{4}-\d{2}-\d{2}/.test(value)
      ? new Date(value.replace(' ', 'T'))
      : value instanceof Date
        ? value
        : new Date(value)
  if (Number.isNaN(date.getTime())) return '-'

  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const startOfTarget = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const diffDays = Math.round((startOfToday.getTime() - startOfTarget.getTime()) / 86400000)

  if (diffDays === 0) return '今天'
  if (diffDays === 1) return '昨天'
  if (diffDays > 1 && diffDays < 7) return `${diffDays} 天前`
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

