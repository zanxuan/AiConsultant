<template>
  <el-card shadow="hover" class="knowledge-card" @click="$emit('click')">
    <div class="knowledge-card__name">{{ item.name }}</div>
    <p class="knowledge-card__desc">{{ item.description || '暂无描述' }}</p>
    <div class="knowledge-card__meta">
      <span>文档 {{ item.documentCount ?? 0 }}</span>
      <span>{{ formatDateTime(item.updateTime || item.updatedAt || item.createTime || item.createdAt) }}</span>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { KnowledgeBase } from '@/types/knowledge'
import { formatDateTime } from '@/utils/format'

defineProps<{ item: KnowledgeBase }>()
defineEmits<{ click: [] }>()
</script>

<style scoped lang="scss">
.knowledge-card {
  cursor: pointer;

  &__name {
    font-size: 16px;
    font-weight: 600;
    color: #0f4c5c;
  }

  &__desc {
    margin: 10px 0;
    min-height: 40px;
    color: #64748b;
    font-size: 13px;
  }

  &__meta {
    display: flex;
    justify-content: space-between;
    color: #94a3b8;
    font-size: 12px;
  }
}
</style>
