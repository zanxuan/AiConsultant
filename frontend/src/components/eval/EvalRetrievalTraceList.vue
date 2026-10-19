<template>
  <ul v-if="rows?.length" class="eval-drawer__docs">
    <li
      v-for="row in rows"
      :key="rowKey(row)"
      class="eval-drawer__doc eval-trace__row"
      :class="{ 'is-expected': isExpected(row), 'is-filtered': row.filtered }"
    >
      <div class="eval-drawer__doc-head">
        <span>{{ rankText(displayRank(row)) }}</span>
        <strong>{{ row.documentName || `Document ${row.documentId ?? '—'}` }}</strong>
        <el-tag v-if="isExpected(row)" size="small" effect="plain" type="success">期望文档</el-tag>
        <el-tag v-if="row.filtered" size="small" effect="plain" type="danger">已过滤</el-tag>
      </div>
      <div class="eval-drawer__meta">
        chunk {{ row.chunkId ?? '—' }}
        <template v-if="row.page != null"> · 第 {{ row.page }} 页</template>
        · {{ scoreText(row) }}
      </div>
      <div v-if="showFilter && row.filterReason" class="eval-drawer__meta">{{ row.filterReason }}</div>
      <p v-if="row.content" class="eval-trace__content">{{ preview(row.content) }}</p>
    </li>
  </ul>
</template>

<script setup lang="ts">
import type { RetrievalTraceChunk } from '@/types/eval'

const props = withDefaults(defineProps<{
  rows?: RetrievalTraceChunk[]
  expectedIds?: string[]
  scoreKind?: 'vector' | 'bm25' | 'rrf' | 'final'
  showFilter?: boolean
}>(), {
  rows: () => [],
  expectedIds: () => [],
  scoreKind: 'rrf',
  showFilter: false,
})

function rowKey(row: RetrievalTraceChunk) {
  return `${row.chunkId ?? 'c'}-${row.documentId ?? 'd'}-${displayRank(row)}`
}

function isExpected(row: RetrievalTraceChunk) {
  return props.expectedIds.map(String).includes(String(row.documentId ?? ''))
}

function displayRank(row: RetrievalTraceChunk) {
  if (props.scoreKind === 'vector') return row.vectorRank
  if (props.scoreKind === 'bm25') return row.bm25Rank
  if (props.scoreKind === 'final') return row.finalRank
  return row.rrfRank
}

function rankText(rank?: number | null) {
  return rank == null ? '—' : `#${rank}`
}

function scoreText(row: RetrievalTraceChunk) {
  if (props.scoreKind === 'vector') return `向量分 ${formatScore(row.vectorScore)}`
  if (props.scoreKind === 'bm25') return `BM25 ${formatScore(row.bm25Score)}`
  if (props.scoreKind === 'final') return `RRF ${formatScore(row.rrfScore)}`
  const parts = [`RRF ${formatScore(row.rrfScore)}`]
  if (row.vectorRank != null) parts.push(`向量 ${rankText(row.vectorRank)} / ${formatScore(row.vectorScore)}`)
  if (row.bm25Rank != null) parts.push(`BM25 ${rankText(row.bm25Rank)} / ${formatScore(row.bm25Score)}`)
  return parts.join(' · ')
}

function formatScore(value?: number | null) {
  if (value == null || Number.isNaN(Number(value))) return '—'
  return Number(value).toFixed(4)
}

function preview(content: string) {
  const text = content.replace(/\s+/g, ' ').trim()
  return text.length > 180 ? `${text.slice(0, 180)}...` : text
}
</script>
