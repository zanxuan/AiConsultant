<template>
  <div v-loading="loading" class="history-list">
    <EmptyState v-if="!list?.length && !loading" description="暂无对话历史" />
    <HistoryItem
      v-for="item in list"
      :key="item.id"
      :item="item"
      @select="$emit('select', item.id)"
      @delete="$emit('delete', item.id)"
    />
  </div>
</template>

<script setup lang="ts">
import type { Conversation } from '@/types/history'
import HistoryItem from './HistoryItem.vue'
import EmptyState from '@/components/common/EmptyState.vue'

defineProps<{
  list: Conversation[]
  loading?: boolean
}>()

defineEmits<{
  select: [id: number]
  delete: [id: number]
}>()
</script>

<style scoped lang="scss">
.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
