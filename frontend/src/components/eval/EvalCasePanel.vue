<template>
  <section class="eval-page__card">
    <div class="eval-page__list-head">
      <div class="eval-page__list-title">{{ onlyFailed ? '失败用例' : '测试用例' }}</div>
      <el-input
        v-model="keyword"
        class="eval-page__search"
        clearable
        placeholder="搜索 Case..."
      />
    </div>
    <div v-if="!onlyFailed" class="eval-page__filters">
      <el-radio-group v-model="statusFilter" size="small">
        <el-radio-button label="all">全部 {{ caseViews.length }}</el-radio-button>
        <el-radio-button label="passed">Passed {{ passedCount }}</el-radio-button>
        <el-radio-button label="failed">Failed {{ failedCount }}</el-radio-button>
      </el-radio-group>
    </div>
    <el-table
      :data="visibleCases"
      class="eval-page__table"
      header-cell-class-name="eval-page__table-header"
      highlight-current-row
      row-key="id"
      :row-class-name="tableRowClass"
      @row-click="onSelectCase"
    >
      <el-table-column label="Question" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">{{ row.query }}</template>
      </el-table-column>
      <el-table-column label="Hit@5" width="110" align="right">
        <template #default="{ row }">{{ row.failed ? 'Failed' : 'Passed' }}</template>
      </el-table-column>
      <el-table-column label="Recall@5" width="110" align="right">
        <template #default="{ row }">{{ formatCaseRate(row.recall) }}</template>
      </el-table-column>
      <el-table-column label="MRR" width="90" align="right">
        <template #default="{ row }">{{ formatMrr(row.mrr) }}</template>
      </el-table-column>
      <el-table-column label="Latency" width="120" align="right">
        <template #default="{ row }">{{ formatLatency(row.latencyMs) }}</template>
      </el-table-column>
      <el-table-column label="Status" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.failed ? 'danger' : 'success'" size="small" effect="plain">
            {{ row.failed ? 'Failed' : 'Passed' }}
          </el-tag>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="emptyText" :image-size="100" />
      </template>
    </el-table>

    <el-drawer
      v-model="drawerVisible"
      :title="selectedCase ? `Case #${selectedCase.id}` : 'Case 详情'"
      direction="rtl"
      size="480px"
      append-to-body
      class="eval-drawer"
      @close="drawerVisible = false"
    >
      <div v-if="selectedCase" class="eval-drawer__body">
        <p class="eval-drawer__query">{{ selectedCase.query }}</p>

        <section class="eval-drawer__section">
          <h3>Evaluation Result</h3>
          <div class="eval-drawer__metrics">
            <div><span>Hit@5</span>{{ selectedCase.failed ? 'Failed' : 'Passed' }}</div>
            <div><span>Recall@5</span>{{ formatCaseRate(selectedCase.recall) }}</div>
            <div><span>MRR</span>{{ formatMrr(selectedCase.mrr) }}</div>
            <div><span>Latency</span>{{ formatLatency(selectedCase.latencyMs) }}</div>
            <div><span>Top Score</span>{{ formatScore(selectedCase.topScore) }}</div>
            <div><span>Status</span>{{ selectedCase.failed ? 'Failed' : 'Passed' }}</div>
          </div>
        </section>

        <section class="eval-drawer__section">
          <h3>Retrieval Pipeline</h3>
          <el-steps direction="vertical" :active="3" finish-status="success">
            <el-step title="Query" :description="selectedCase.query" />
            <el-step title="Hybrid Retrieval" description="现有评测走 HybridRetriever" />
            <el-step
              title="Retrieved Documents"
              :description="`${selectedCase.retrievedDocIds.length} documents`"
            />
            <el-step
              title="Ground Truth"
              :description="`${selectedCase.expectedDocIds.length} documents`"
            />
          </el-steps>
        </section>

        <section class="eval-drawer__section">
          <h3>
            Retrieved Documents
            <em>{{ selectedCase.retrievedDocIds.length }}</em>
          </h3>
          <p v-if="selectedCase.failed" class="eval-drawer__note">
            Ground Truth 未出现在 Top 5 检索结果中
          </p>
          <ul v-if="selectedCase.retrievedDocIds.length" class="eval-drawer__docs">
            <li
              v-for="(docId, index) in selectedCase.retrievedDocIds"
              :key="`${selectedCase.id}-r-${index}-${docId}`"
              class="eval-drawer__doc"
            >
              <div class="eval-drawer__doc-head">
                <span>#{{ index + 1 }}</span>
                <strong>Document ID: {{ docId }}</strong>
                <el-tag
                  v-if="isGroundTruth(selectedCase, docId)"
                  size="small"
                  effect="plain"
                  type="success"
                >
                  Hit
                </el-tag>
              </div>
              <div class="eval-drawer__meta">Score: {{ retrievedScore(selectedCase, index) }}</div>
            </li>
          </ul>
          <p v-else class="eval-drawer__empty">本次检索未返回文档。</p>
        </section>

        <section class="eval-drawer__section">
          <h3>
            Ground Truth
            <em>{{ selectedCase.expectedDocIds.length }}</em>
          </h3>
          <ul v-if="selectedCase.expectedDocIds.length" class="eval-drawer__docs">
            <li
              v-for="docId in selectedCase.expectedDocIds"
              :key="`${selectedCase.id}-e-${docId}`"
              class="eval-drawer__doc"
            >
              <div class="eval-drawer__doc-head">
                <span>✓</span>
                <strong>Document ID: {{ docId }}</strong>
              </div>
            </li>
          </ul>
          <p v-else class="eval-drawer__empty">无 Expected Documents</p>
        </section>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { EvalCase, EvalCaseResult, EvalCaseView, EvalFailedCase, EvalRunDetail } from '@/types/eval'
import { formatCaseRate, formatLatency, formatMrr, formatScore } from './format'

const props = withDefaults(defineProps<{
  detail: EvalRunDetail
  onlyFailed?: boolean
}>(), {
  onlyFailed: false,
})

const drawerVisible = ref(false)
const selectedCase = ref<EvalCaseView | null>(null)
const statusFilter = ref<'all' | 'passed' | 'failed'>('all')
const keyword = ref('')

function caseKey(id: number | string | null | undefined) {
  return id == null ? '' : String(id)
}

const detailMap = computed(() => {
  const map = new Map<string, EvalCaseResult | EvalFailedCase>()
  for (const item of props.detail.result?.cases ?? []) {
    const key = caseKey(item?.id)
    if (key) map.set(key, item)
  }
  for (const item of props.detail.result?.failedCases ?? []) {
    const key = caseKey(item?.id)
    if (key && !map.has(key)) map.set(key, item)
  }
  return map
})

const caseViews = computed<EvalCaseView[]>(() => {
  const current = props.detail.result
  if (!current) return []
  if (current.cases?.length) {
    return current.cases.map((item) => toCaseViewFromResult(item))
  }
  return (current.failedCases ?? []).map((item) => toCaseView(item))
})

const failedCount = computed(() => caseViews.value.filter((item) => item.failed).length)
const passedCount = computed(() => caseViews.value.length - failedCount.value)

const visibleCases = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return caseViews.value.filter((item) => {
    if (props.onlyFailed && !item.failed) return false
    if (!props.onlyFailed && statusFilter.value === 'failed' && !item.failed) return false
    if (!props.onlyFailed && statusFilter.value === 'passed' && item.failed) return false
    if (q && !item.query.toLowerCase().includes(q)) return false
    return true
  })
})

const emptyText = computed(() => {
  if (!props.detail.result) return '该记录没有评测结果'
  if (keyword.value.trim()) return '没有匹配的用例'
  if (props.onlyFailed) return '无失败用例'
  return '暂无评测 Case'
})

function toCaseViewFromResult(item: EvalCaseResult): EvalCaseView {
  return {
    id: Number(item.id),
    query: item.query,
    expectedDocIds: item.expectedDocIds ?? [],
    retrievedDocIds: item.retrievedDocIds ?? [],
    topScore: item.topScore ?? null,
    latencyMs: item.latencyMs ?? null,
    failed: item.hit === false,
    recall: item.recall ?? null,
    mrr: item.mrr ?? null,
  }
}

function toCaseView(item: EvalCase | EvalFailedCase): EvalCaseView {
  const detail = detailMap.value.get(caseKey(item.id))
  const failedDetail = detail && !('hit' in detail) ? (detail as EvalFailedCase) : undefined
  const full = detail && 'hit' in detail ? (detail as EvalCaseResult) : undefined
  return {
    id: Number(item.id),
    query: full?.query ?? failedDetail?.query ?? item.query,
    expectedDocIds: full?.expectedDocIds ?? failedDetail?.expectedDocIds ?? item.expectedDocIds ?? [],
    retrievedDocIds: full?.retrievedDocIds ?? failedDetail?.retrievedDocIds ?? [],
    topScore: full?.topScore ?? failedDetail?.topScore ?? null,
    latencyMs: full?.latencyMs ?? failedDetail?.latencyMs ?? null,
    failed: full ? full.hit === false : Boolean(failedDetail),
    recall: full?.recall ?? (failedDetail ? 0 : null),
    mrr: full?.mrr ?? (failedDetail ? 0 : null),
  }
}

function resolveCaseDetail(row: EvalCaseView): EvalCaseView {
  const detail = detailMap.value.get(caseKey(row.id))
  if (detail && 'hit' in detail) {
    return toCaseViewFromResult(detail as EvalCaseResult)
  }
  if (detail) {
    const failed = detail as EvalFailedCase
    return {
      ...row,
      query: failed.query ?? row.query,
      expectedDocIds: failed.expectedDocIds?.length ? failed.expectedDocIds : row.expectedDocIds,
      retrievedDocIds: failed.retrievedDocIds ?? [],
      topScore: failed.topScore ?? null,
      latencyMs: failed.latencyMs ?? null,
      failed: true,
      recall: 0,
      mrr: 0,
    }
  }
  return { ...row }
}

function retrievedScore(row: EvalCaseView, index: number) {
  if (index !== 0) return '—'
  return formatScore(row.topScore)
}

function isGroundTruth(row: EvalCaseView, docId: string) {
  return (row.expectedDocIds ?? []).map(String).includes(String(docId))
}

function tableRowClass({ row }: { row: EvalCaseView }) {
  return caseKey(selectedCase.value?.id) === caseKey(row.id) ? 'eval-page__row--selected' : ''
}

function onSelectCase(row: EvalCaseView) {
  selectedCase.value = resolveCaseDetail(row)
  drawerVisible.value = true
}
</script>
