<template>
  <div class="history-item" @click="$emit('select')">
    <div class="history-item__main">
      <div class="history-item__title">{{ item.title }}</div>
      <div class="history-item__meta">
        <span>{{ item.knowledgeName || '未关联知识库' }}</span>
        <span>{{ formatDateTime(item.updateTime || item.updatedAt || item.createTime || item.createdAt) }}</span>
      </div>
    </div>
    <el-button type="danger" link @click.stop="$emit('delete')">删除</el-button>
  </div>
</template>

<script setup lang="ts">
import type { Conversation } from '@/types/history'
import { formatDateTime } from '@/utils/format'

defineProps<{
  item: Conversation
}>()

defineEmits<{
  select: []
  delete: []
}>()
</script>

<style scoped lang="scss">
.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e6ecef;
  border-radius: 10px;
  cursor: pointer;

  &:hover {
    border-color: #0f4c5c;
  }

  &__title {
    font-weight: 600;
    color: #1f2d3d;
  }

  &__meta {
    margin-top: 6px;
    display: flex;
    gap: 16px;
    color: #94a3b8;
    font-size: 12px;
  }
}
</style>
