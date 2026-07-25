<template>
  <el-table
    :data="data"
    v-loading="loading"
    class="doc-table"
    header-cell-class-name="doc-table__header"
  >
    <el-table-column label="文件名" min-width="220" show-overflow-tooltip>
      <template #default="{ row }">
        <div class="doc-table__name">
          <el-icon class="doc-table__file-icon" :class="`is-${row.fileType || 'file'}`">
            <Document />
          </el-icon>
          <span>{{ row.fileName }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="类型" width="100">
      <template #default="{ row }">
        <span class="doc-table__type">{{ (row.fileType || '-').toUpperCase() }}</span>
      </template>
    </el-table-column>
    <el-table-column label="大小" width="110">
      <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
    </el-table-column>
    <el-table-column v-if="showKnowledge" label="所属知识库" min-width="140" show-overflow-tooltip>
      <template #default="{ row }">
        <div class="doc-table__kb">
          <el-icon><Folder /></el-icon>
          <span>{{ resolveKnowledgeName(row.knowledgeId) }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="120">
      <template #default="{ row }">
        <ParseStatusTag :status="row.status" />
      </template>
    </el-table-column>
    <el-table-column label="上传时间" width="170">
      <template #default="{ row }">{{ formatDateTime(row.createTime || row.updateTime) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="180" fixed="right">
      <template #default="{ row }">
        <el-button
          v-if="row.status === DocumentStatus.FAILED || row.status === DocumentStatus.READY"
          type="primary"
          link
          @click="$emit('reindex', row.id)"
        >
          <el-icon class="el-icon--left"><RefreshRight /></el-icon>
          重建索引
        </el-button>
        <el-button type="danger" link @click="$emit('delete', row.id)">
          <el-icon class="el-icon--left"><Delete /></el-icon>
          删除
        </el-button>
      </template>
    </el-table-column>

    <template #empty>
      <div class="doc-table__empty">
        <el-empty :image-size="120" description="暂无文档">
          <template #description>
            <p class="doc-table__empty-title">暂无文档</p>
            <p class="doc-table__empty-desc">快去上传企业文档，构建专属知识库吧~</p>
          </template>
        </el-empty>
      </div>
    </template>
  </el-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Delete, Document, Folder, RefreshRight } from '@element-plus/icons-vue'
import type { DocumentItem } from '@/types/document'
import { DocumentStatus } from '@/constants/enum'
import { formatDateTime, formatFileSize } from '@/utils/format'
import { useKnowledgeStore } from '@/stores/knowledge'
import ParseStatusTag from './ParseStatusTag.vue'

withDefaults(
  defineProps<{
    data: DocumentItem[]
    loading?: boolean
    showKnowledge?: boolean
  }>(),
  {
    showKnowledge: true,
  },
)

defineEmits<{
  delete: [id: number | string]
  reindex: [id: number | string]
}>()

const knowledgeStore = useKnowledgeStore()

const knowledgeMap = computed(() => {
  const map = new Map<string, string>()
  for (const item of knowledgeStore.list) {
    map.set(String(item.id), item.name)
  }
  return map
})

function resolveKnowledgeName(knowledgeId: number | string) {
  return knowledgeMap.value.get(String(knowledgeId)) || `知识库 #${knowledgeId}`
}
</script>

<style scoped lang="scss">
.doc-table {
  width: 100%;
  --el-table-header-bg-color: #f8fafc;
  --el-table-row-hover-bg-color: #f3fafb;
  border-radius: 10px;
  overflow: hidden;

  &__name {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    color: #1f2d3d;
    font-weight: 500;
  }

  &__file-icon {
    font-size: 18px;
    color: #0f4c5c;

    &.is-pdf {
      color: #c2410c;
    }

    &.is-md {
      color: #0369a1;
    }

    &.is-txt {
      color: #64748b;
    }
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

  &__kb {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: #475569;

    .el-icon {
      color: #0f4c5c;
    }
  }

  &__empty {
    padding: 28px 0 12px;
  }

  &__empty-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #334155;
  }

  &__empty-desc {
    margin: 6px 0 0;
    font-size: 13px;
    color: #94a3b8;
  }
}
</style>
