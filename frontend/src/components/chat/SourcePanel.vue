<template>
  <div class="source-panel">
    <div class="source-panel__title">参考来源</div>
    <ul class="source-panel__list">
      <li
        v-for="(item, index) in displaySources"
        :key="`${item.documentName}-${index}`"
        class="source-panel__item"
      >
        <span class="source-panel__name">{{ item.documentName }}</span>
        <span v-if="item.pagesLabel" class="source-panel__page">
          第 {{ item.pagesLabel }} 页
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

interface DisplaySource {
  documentName: string
  /** 合并后的页码文案，如 1、3、5；无页码时为空 */
  pagesLabel?: string
}

/**
 * 仅按「文档名完全一致」合并为一行，页码去重后汇总展示。
 * 名称不同（哪怕差一个字）仍分行。
 */
const displaySources = computed(() => {
  const order: string[] = []
  const pagesByName = new Map<string, Set<string>>()

  for (const item of props.sources) {
    const name = item.documentName || '未知文档'
    if (!pagesByName.has(name)) {
      pagesByName.set(name, new Set())
      order.push(name)
    }
    if (item.page != null && item.page !== '') {
      pagesByName.get(name)!.add(String(item.page))
    }
  }

  return order.map((documentName): DisplaySource => {
    const pages = [...(pagesByName.get(documentName) ?? [])].sort((a, b) => {
      const na = Number(a)
      const nb = Number(b)
      if (!Number.isNaN(na) && !Number.isNaN(nb)) return na - nb
      return a.localeCompare(b)
    })
    return {
      documentName,
      pagesLabel: pages.length ? pages.join('、') : undefined,
    }
  })
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
