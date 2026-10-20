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
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import type { EvalCaseView, EvalRunDetail } from '@/types/eval'
import { buildCaseViews, caseKey, type EvalCaseStatusFilter } from './case-view'
import { formatCaseRate, formatLatency, formatMrr } from './format'

const props = withDefaults(defineProps<{
  detail: EvalRunDetail
  onlyFailed?: boolean
  activeCaseId?: string
}>(), {
  onlyFailed: false,
  activeCaseId: '',
})

const keyword = defineModel<string>('keyword', { default: '' })
const statusFilter = defineModel<EvalCaseStatusFilter>('statusFilter', { default: 'all' })

const router = useRouter()

const caseViews = computed(() => buildCaseViews(props.detail))
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

function tableRowClass({ row }: { row: EvalCaseView }) {
  return caseKey(props.activeCaseId) === caseKey(row.id) ? 'eval-page__row--selected' : ''
}

function onSelectCase(row: EvalCaseView) {
  router.push({
    name: 'eval-case',
    params: {
      runId: String(props.detail.id),
      caseId: String(row.id),
    },
    query: { from: props.onlyFailed ? 'failed' : 'cases' },
  })
}
</script>
