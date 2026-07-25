import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getKnowledgeListApi } from '@/api/knowledge'
import type { KnowledgeBase } from '@/types/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const list = ref<KnowledgeBase[]>([])
  const currentId = ref<number | string | null>(null)
  const loading = ref(false)

  async function fetchList() {
    loading.value = true
    try {
      const res = await getKnowledgeListApi({ page: 1, pageSize: 100 })
      list.value = res.list || []
      if (!currentId.value && list.value.length) {
        currentId.value = list.value[0].id
      }
    } finally {
      loading.value = false
    }
  }

  function setCurrentId(id: number | string | null) {
    currentId.value = id
  }

  return {
    list,
    currentId,
    loading,
    fetchList,
    setCurrentId,
  }
})
