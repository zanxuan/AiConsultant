import { reactive } from 'vue'

export function usePagination(defaultPageSize = 10) {
  const pagination = reactive({
    page: 1,
    pageSize: defaultPageSize,
    total: 0,
  })

  function setTotal(total: number) {
    pagination.total = total
  }

  function resetPage() {
    pagination.page = 1
  }

  return {
    pagination,
    setTotal,
    resetPage,
  }
}
