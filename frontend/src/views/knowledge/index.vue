<template>
  <PageContainer title="知识库管理" desc="创建与管理企业 RAG 知识库">
    <template #extra>
      <el-button type="primary" @click="openCreate">新建知识库</el-button>
    </template>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="按名称关键字搜索"
        style="width: 280px"
        @clear="onSearch"
        @keyup.enter="onSearch"
      />
      <el-button type="primary" @click="onSearch">搜索</el-button>
    </div>

    <el-row v-loading="loading" :gutter="16">
      <el-col v-for="item in list" :key="item.id" :xs="24" :sm="12" :md="8" :lg="6">
        <KnowledgeCard :item="item" class="kb-card" @click="goDetail(item.id)" />
      </el-col>
    </el-row>
    <EmptyState v-if="!loading && !list.length" description="暂无知识库，点击右上角创建" />

    <div v-if="pagination.total > 0" class="pager">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        layout="total, prev, pager, next"
        :total="pagination.total"
        @current-change="fetchList"
      />
    </div>

    <el-dialog v-model="dialogVisible" title="新建知识库" width="480px" destroy-on-close>
      <KnowledgeForm ref="formRef" />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import KnowledgeCard from '@/components/knowledge/KnowledgeCard.vue'
import KnowledgeForm from '@/components/knowledge/KnowledgeForm.vue'
import { createKnowledgeApi, getKnowledgeListApi } from '@/api/knowledge'
import type { KnowledgeBase } from '@/types/knowledge'
import { usePagination } from '@/composables/usePagination'
import { useKnowledgeStore } from '@/stores/knowledge'

const router = useRouter()
const knowledgeStore = useKnowledgeStore()
const list = ref<KnowledgeBase[]>([])
const loading = ref(false)
const keyword = ref('')
const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<InstanceType<typeof KnowledgeForm>>()
const { pagination, setTotal, resetPage } = usePagination(12)

async function fetchList() {
  loading.value = true
  try {
    const res = await getKnowledgeListApi({
      page: pagination.page,
      pageSize: pagination.pageSize,
      keyword: keyword.value.trim() || undefined,
    })
    list.value = res.list
    setTotal(res.total)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  resetPage()
  fetchList()
}

function openCreate() {
  dialogVisible.value = true
}

function goDetail(id: number | string) {
  router.push({ name: 'knowledge-detail', params: { id: String(id) } })
}

async function onSave() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    const data = formRef.value!.getForm()
    await createKnowledgeApi(data)
    ElMessage.success('创建成功')
    dialogVisible.value = false
    resetPage()
    await Promise.all([fetchList(), knowledgeStore.fetchList()])
  } finally {
    saving.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.kb-card {
  margin-bottom: 16px;
}

.pager {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>
