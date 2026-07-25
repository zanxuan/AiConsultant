import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({
  gfm: true,
  breaks: true,
})

/** 将 Markdown 转为可安全注入的 HTML */
export function renderMarkdown(source: string): string {
  if (!source) return ''
  const html = marked.parse(source, { async: false }) as string
  return DOMPurify.sanitize(html)
}
