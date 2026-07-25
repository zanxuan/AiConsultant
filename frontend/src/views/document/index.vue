<template>
  <PageContainer title="文档管理" desc="上传企业文档并查看解析状态">
    <template #extra>
      <div class="doc-page__toolbar">
        <KnowledgeSelect v-model="knowledgeId" />
        <el-button class="doc-page__refresh" :loading="loading" @click="fetchList">
          <el-icon class="el-icon--left"><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </template>

    <section class="doc-page__card doc-page__card--upload">
      <DocumentUploader :knowledge-id="knowledgeId" @success="onUploadSuccess" />
    </section>

    <section class="doc-page__card doc-page__card--list">
      <div class="doc-page__list-head">
        <div class="doc-page__list-title">
          <el-icon><Document /></el-icon>
          <span>文档列表</span>
        </div>
        <el-input
          v-model="keyword"
          class="doc-page__search"
          clearable
          placeholder="搜索文件名"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <DocumentTable
        :data="filteredList"
        :loading="loading"
        @delete="onDelete"
        @reindex="onReindex"
      />

      <div class="doc-page__pager">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          @current-change="fetchList"
          @size-change="onSizeChange"
        />
      </div>
    </section>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Document, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import KnowledgeSelect from '@/components/knowledge/KnowledgeSelect.vue'
import DocumentUploader from '@/components/document/DocumentUploader.vue'
import DocumentTable from '@/components/document/DocumentTable.vue'
import {
  deleteDocumentApi,
  getDocumentListApi,
  getDocumentStatusApi,
  reindexDocumentApi,
} from '@/api/document'
import type { DocumentItem } from '@/types/document'
import { DocumentStatus } from '@/constants/enum'
import { usePagination } from '@/composables/usePagination'

const PENDING_STATUSES = new Set([
  DocumentStatus.UPLOADING,
  DocumentStatus.PARSING,
  DocumentStatus.EMBEDDING,
])

const route = useRoute()
const knowledgeId = ref<number | string | null>(
  route.query.kbId ? String(route.query.kbId) : null,
)
const list = ref<DocumentItem[]>([])
const keyword = ref('')
const loading = ref(false)
const { pagination, setTotal, resetPage } = usePagination()

let statusTimer: ReturnType<typeof setInterval> | null = null

const filteredList = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return list.value
  return list.value.filter((item) => (item.fileName || '').toLowerCase().includes(q))
})

async function fetchList() {
  if (!knowledgeId.value) {
    list.value = []
    setTotal(0)
    stopStatusPolling()
    return
  }
  loading.value = true
  try {
    const res = await getDocumentListApi({
      knowledgeId: knowledgeId.value,
      page: pagination.page,
      size: pagination.pageSize,
    })
    list.value = res.list || []
    setTotal(res.total ?? 0)
    syncStatusPolling()
  } finally {
    loading.value = false
  }
}

function onSizeChange() {
  resetPage()
  fetchList()
}

function hasPendingDocs() {
  return list.value.some((item) => PENDING_STATUSES.has(item.status))
}

function syncStatusPolling() {
  if (hasPendingDocs()) {
    startStatusPolling()
  } else {
    stopStatusPolling()
  }
}

function startStatusPolling() {
  if (statusTimer) return
  statusTimer = setInterval(pollStatuses, 3000)
}

function stopStatusPolling() {
  if (!statusTimer) return
  clearInterval(statusTimer)
  statusTimer = null
}

async function pollStatuses() {
  const pending = list.value.filter((item) => PENDING_STATUSES.has(item.status))
  if (!pending.length) {
    stopStatusPolling()
    return
  }

  await Promise.all(
    pending.map(async (item) => {
      try {
        const res = await getDocumentStatusApi(item.id)
        if (res.status !== item.status) {
          item.status = res.status
        }
      } catch {
        // 单条状态查询失败不打断轮询
      }
    }),
  )

  if (!hasPendingDocs()) {
    stopStatusPolling()
    await fetchList()
  }
}

async function onUploadSuccess() {
  resetPage()
  await fetchList()
}

async function onDelete(id: number | string) {
  await ElMessageBox.confirm(
    '确认删除该文档？将同步清理数据库、向量索引与源文件。',
    '提示',
    { type: 'warning' },
  )
  await deleteDocumentApi(id)
  ElMessage.success('已删除')
  await fetchList()
}

async function onReindex(id: number | string) {
  await ElMessageBox.confirm('确认重新构建该文档索引？', '提示', { type: 'info' })
  await reindexDocumentApi(id)
  ElMessage.success('已提交重建索引')
  await fetchList()
}

watch(knowledgeId, () => {
  keyword.value = ''
  resetPage()
  fetchList()
})

onMounted(fetchList)

onUnmounted(stopStatusPolling)
</script>

<style scoped lang="scss">
.doc-page {
  &__toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  &__refresh {
    border-radius: 8px;
    color: #0f4c5c;
    border-color: rgba(15, 76, 92, 0.25);

    &:hover {
      background: rgba(15, 76, 92, 0.06);
      border-color: #0f4c5c;
      color: #0f4c5c;
    }
  }

  &__card {
    background: #fff;
    border: 1px solid #e6ecef;
    border-radius: 14px;
    box-shadow: 0 6px 18px rgba(15, 45, 60, 0.04);
    margin-bottom: 16px;

    &--upload {
      padding: 18px;
    }

    &--list {
      padding: 16px 18px 12px;
    }
  }

  &__list-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
  }

  &__list-title {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
    font-weight: 600;
    color: #1f2d3d;

    .el-icon {
      color: #0f4c5c;
      font-size: 18px;
    }
  }

  &__search {
    width: 240px;

    :deep(.el-input__wrapper) {
      border-radius: 8px;
    }
  }

  &__pager {
    margin-top: 14px;
    padding-top: 12px;
    border-top: 1px solid #eef2f5;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
