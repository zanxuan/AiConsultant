import { ref } from 'vue'
import { uploadDocumentApi } from '@/api/document'
import type { UploadResult } from '@/types/document'

const ALLOWED_EXT = ['.pdf', '.md', '.txt']

export function useUpload() {
  const percent = ref(0)
  const uploading = ref(false)
  const error = ref<string | null>(null)

  function isAllowedFile(file: File) {
    const name = file.name.toLowerCase()
    return ALLOWED_EXT.some((ext) => name.endsWith(ext))
  }

  async function upload(knowledgeId: number | string, file: File): Promise<UploadResult | null> {
    if (!isAllowedFile(file)) {
      error.value = '仅支持 pdf / md / txt'
      return null
    }

    uploading.value = true
    percent.value = 0
    error.value = null
    try {
      return await uploadDocumentApi(knowledgeId, file, (p) => {
        percent.value = p
      })
    } catch (e) {
      error.value = (e as Error).message || '上传失败'
      return null
    } finally {
      uploading.value = false
    }
  }

  function reset() {
    percent.value = 0
    uploading.value = false
    error.value = null
  }

  return {
    percent,
    uploading,
    error,
    isAllowedFile,
    upload,
    reset,
  }
}
