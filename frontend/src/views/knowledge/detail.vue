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
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import KnowledgeForm from '@/components/knowledge/KnowledgeForm.vue'
import {
  deleteKnowledgeApi,
  getKnowledgeDetailApi,
  updateKnowledgeApi,
} from '@/api/knowledge'
import type { KnowledgeBase, KnowledgeForm as KnowledgeFormData } from '@/types/knowledge'
import { formatDateTime } from '@/utils/format'
import { useKnowledgeStore } from '@/stores/knowledge'

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

async function fetchDetail() {
  loading.value = true
  try {
    detail.value = await getKnowledgeDetailApi(knowledgeId.value)
  } finally {
    loading.value = false
  }
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

onMounted(fetchDetail)
</script>
