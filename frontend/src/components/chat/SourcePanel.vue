<template>
  <div class="source-panel">
    <div class="source-panel__title">参考来源</div>
    <ul class="source-panel__list">
      <li
        v-for="(item, index) in displaySources"
        :key="`${item.documentName}-${item.page}-${index}`"
        class="source-panel__item"
      >
        <span class="source-panel__name">{{ item.documentName || '未知文档' }}</span>
        <span v-if="item.page != null && item.page !== ''" class="source-panel__page">
          第 {{ item.page }} 页
        </span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CiteSource } from '@/types/chat'

const props = defineProps<{
  sources: CiteSource[]
}>()

/** 按「文档名 + 页码」去重展示，不暴露 id */
const displaySources = computed(() => {
  const seen = new Set<string>()
  const result: CiteSource[] = []
  for (const item of props.sources) {
    const name = item.documentName || '未知文档'
    const page = item.page ?? ''
    const key = `${name}::${page}`
    if (seen.has(key)) continue
    seen.add(key)
    result.push({ documentName: name, page: item.page })
  }
  return result
})
</script>

<style scoped lang="scss">
.source-panel {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(15, 76, 92, 0.25);

  &__title {
    font-size: 12px;
    font-weight: 600;
    margin-bottom: 8px;
    color: #64748b;
  }

  &__list {
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__item {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 8px;
    font-size: 12px;
    line-height: 1.4;
  }

  &__name {
    font-weight: 600;
    color: #0f4c5c;
  }

  &__page {
    color: #64748b;
  }
}
</style>
