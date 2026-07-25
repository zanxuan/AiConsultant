import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getHistoryListApi, deleteHistoryApi } from '@/api/history'
import type { Conversation } from '@/types/history'

export const useHistoryStore = defineStore('history', () => {
  const list = ref<Conversation[]>([])
  const loading = ref(false)
  const total = ref(0)

  async function fetchList(page = 1, pageSize = 20) {
    loading.value = true
    try {
      const res = await getHistoryListApi({ page, pageSize })
      // 后端可能直接返回数组，也可能返回 { list, total } 分页结构
      if (Array.isArray(res)) {
        list.value = res
        total.value = res.length
      } else {
        list.value = res?.list || []
        total.value = res?.total ?? list.value.length
      }
    } finally {
      loading.value = false
    }
  }

  async function remove(id: number | string) {
    await deleteHistoryApi(id)
    list.value = list.value.filter((item) => item.id !== id)
  }

  return {
    list,
    loading,
    total,
    fetchList,
    remove,
  }
})
