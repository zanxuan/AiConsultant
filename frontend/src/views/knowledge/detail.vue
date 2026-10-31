<template>
  <PageContainer :title="detail?.name || '知识库详情'" :desc="detail?.description">
    <template #extra>
      <el-button @click="$router.push({ name: 'knowledge' })">返回列表</el-button>
      <el-button @click="openEdit">编辑</el-button>
      <el-button type="danger" :loading="deleting" @click="onDelete">删除</el-button>
      <el-button type="primary" @click="goUpload">上传文档</el-button>
    </template>

    <el-descriptions v-loading="loading" :column="2" border>
      <el-descriptions-item label="ID">{{ detail?.id }}</el-descriptions-item>
      <el-descriptions-item label="文档数">{{ detail?.documentCount ?? 0 }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatDateTime(detail?.createTime || detail?.createdAt) }}</el-descriptions-item>
      <el-descriptions-item label="更新时间">{{ formatDateTime(detail?.updateTime || detail?.updatedAt) }}</el-descriptions-item>
    </el-descriptions>

    <!-- 当前知识库下的文档，字段与文档管理列表对齐，操作只保留删除 -->
    <section class="kb-docs">
      <div class="kb-docs__title">文档列表</div>
      <el-table v-loading="docsLoading" :data="documents" class="kb-docs__table">
        <el-table-column label="文档名字" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.fileName }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <span class="kb-docs__type">{{ (row.fileType || '-').toUpperCase() }}</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <ParseStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime || row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" link @click="onDeleteDocument(row.id)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :image-size="80" description="暂无文档" />
        </template>
      </el-table>
      <div v-if="pagination.total > 0" class="kb-docs__pager">
        <el-pagination
          v-model:current-page="pagination.page"
          background
          layout="total, prev, pager, next"
          :total="pagination.total"
          @current-change="fetchDocuments"
        />
      </div>
    </section>

    <el-dialog v-model="editVisible" title="修改知识库" width="480px" destroy-on-close>
      <KnowledgeForm ref="formRef" :model-value="editForm" />
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import KnowledgeForm from '@/components/knowledge/KnowledgeForm.vue'
import ParseStatusTag from '@/components/document/ParseStatusTag.vue'
import {
  deleteKnowledgeApi,
  getKnowledgeDetailApi,
  updateKnowledgeApi,
} from '@/api/knowledge'
import { deleteDocumentApi, getDocumentListApi, getDocumentStatusApi } from '@/api/document'
import type { KnowledgeBase, KnowledgeForm as KnowledgeFormData } from '@/types/knowledge'
import type { DocumentItem, DocumentStatusResult } from '@/types/document'
import { DocumentStatus } from '@/constants/enum'
import { formatDateTime, formatFileSize } from '@/utils/format'
import { useKnowledgeStore } from '@/stores/knowledge'
import { usePagination } from '@/composables/usePagination'

/** 解析中的状态需要轮询；后端向量化阶段是 INDEXING，文档管理页还会用到 EMBEDDING */
const PENDING_STATUSES = new Set<string>([
  DocumentStatus.UPLOADING,
  DocumentStatus.PARSING,
  DocumentStatus.EMBEDDING,
  'INDEXING',
])

const route = useRoute()
const router = useRouter()
const knowledgeStore = useKnowledgeStore()
const knowledgeId = computed(() => route.params.id as string)

const detail = ref<KnowledgeBase | null>(null)
const loading = ref(false)
const editVisible = ref(false)
const saving = ref(false)
const deleting = ref(false)
const formRef = ref<InstanceType<typeof KnowledgeForm>>()
const editForm = ref<KnowledgeFormData>({ name: '', description: '' })

const documents = ref<DocumentItem[]>([])
const docsLoading = ref(false)
const { pagination, setTotal, resetPage } = usePagination()
let statusTimer: ReturnType<typeof setInterval> | null = null

async function fetchDetail() {
  loading.value = true
  try {
    detail.value = await getKnowledgeDetailApi(knowledgeId.value)
  } finally {
    loading.value = false
  }
}

/** 复用文档管理同一套列表接口：按知识库分页，底层仍走 DocumentMapper */
async function fetchDocuments() {
  if (!knowledgeId.value) {
    documents.value = []
    setTotal(0)
    stopStatusPolling()
    return
  }
  docsLoading.value = true
  try {
    const res = await getDocumentListApi({
      knowledgeId: knowledgeId.value,
      page: pagination.page,
      size: pagination.pageSize,
    })
    documents.value = res.list || []
    setTotal(res.total ?? 0)
    syncStatusPolling()
  } finally {
    docsLoading.value = false
  }
}

function hasPendingDocs() {
  return documents.value.some((item) => PENDING_STATUSES.has(item.status))
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

/** 状态接口 data 是字符串，兼容文档管理页使用的 { status } 结构 */
function readStatus(res: DocumentStatusResult | string): string | undefined {
  if (typeof res === 'string') return res
  return res?.status
}

async function pollStatuses() {
  const pending = documents.value.filter((item) => PENDING_STATUSES.has(item.status))
  if (!pending.length) {
    stopStatusPolling()
    return
  }

  await Promise.all(
    pending.map(async (item) => {
      try {
        const status = readStatus(await getDocumentStatusApi(item.id))
        if (status && status !== item.status) {
          item.status = status as DocumentStatus
        }
      } catch {
        // 单条状态查询失败不打断轮询
      }
    }),
  )

  if (!hasPendingDocs()) {
    stopStatusPolling()
    await Promise.all([fetchDocuments(), fetchDetail()])
  }
}

async function onDeleteDocument(id: number | string) {
  await ElMessageBox.confirm(
    '确认删除该文档？将同步清理数据库、向量索引与源文件。',
    '提示',
    { type: 'warning' },
  )
  await deleteDocumentApi(id)
  ElMessage.success('已删除')
  if (documents.value.length <= 1 && pagination.page > 1) {
    pagination.page -= 1
  }
  // 删除会扣减知识库文档数，列表和顶部文档数一起刷新
  await Promise.all([fetchDocuments(), fetchDetail()])
}

function openEdit() {
  if (!detail.value) return
  editForm.value = {
    name: detail.value.name,
    description: detail.value.description || '',
  }
  editVisible.value = true
}

async function onSave() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    const data = formRef.value!.getForm()
    detail.value = await updateKnowledgeApi(knowledgeId.value, data)
    ElMessage.success('保存成功')
    editVisible.value = false
    await knowledgeStore.fetchList()
  } finally {
    saving.value = false
  }
}

async function onDelete() {
  await ElMessageBox.confirm('确认删除该知识库？删除后不可恢复。', '提示', {
    type: 'warning',
  })
  deleting.value = true
  try {
    await deleteKnowledgeApi(knowledgeId.value)
    if (String(knowledgeStore.currentId) === String(knowledgeId.value)) {
      knowledgeStore.setCurrentId(null)
    }
    await knowledgeStore.fetchList()
    ElMessage.success('已删除')
    router.push({ name: 'knowledge' })
  } finally {
    deleting.value = false
  }
}

function goUpload() {
  router.push({ name: 'document', query: { kbId: knowledgeId.value } })
}

watch(
  knowledgeId,
  () => {
    resetPage()
    fetchDetail()
    fetchDocuments()
  },
  { immediate: true },
)

onUnmounted(stopStatusPolling)
</script>

<style scoped lang="scss">
.kb-docs {
  margin-top: 20px;

  &__title {
    margin-bottom: 12px;
    font-size: 15px;
    font-weight: 600;
    color: #1f2d3d;
  }

  &__table {
    width: 100%;
    --el-table-header-bg-color: #f8fafc;
    border-radius: 10px;
    overflow: hidden;
  }

  &__type {
    display: inline-block;
    min-width: 42px;
    padding: 2px 8px;
    border-radius: 6px;
    background: #eef2f7;
    color: #475569;
    font-size: 12px;
    text-align: center;
  }

  &__pager {
    margin-top: 14px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
