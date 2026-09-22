<template>
  <PageContainer title="RAG 评测" desc="基于现有 golden-set 的离线检索评测">
    <section class="eval-page__card">
      <div class="eval-page__list-title">当前测试集</div>
      <div v-loading="datasetLoading" class="eval-page__summary">
        <div class="eval-page__summary-name">{{ datasetName || '—' }}</div>
        <div class="eval-page__summary-meta">共 {{ caseCount }} 条测试用例</div>
        <div class="eval-page__actions">
          <el-button :disabled="!cases.length" @click="showDataset = !showDataset">
            {{ showDataset ? '收起测试集' : '查看测试集' }}
          </el-button>
          <el-button type="primary" :loading="running" :disabled="running" @click="onRun">
            开始评测
          </el-button>
        </div>
      </div>
    </section>

    <section v-if="showDataset" class="eval-page__card">
      <div class="eval-page__list-title">测试集</div>
      <el-table
        :data="cases"
        class="eval-page__table"
        header-cell-class-name="eval-page__table-header"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="query" label="Query" min-width="280" show-overflow-tooltip />
        <el-table-column label="Expected Document IDs" min-width="240">
          <template #default="{ row }">
            <span class="eval-page__ids">{{ formatIds(row.expectedDocIds) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无测试用例" :image-size="100" />
        </template>
      </el-table>
    </section>

    <section v-if="result" class="eval-page__card">
      <div class="eval-page__list-title">最新评测结果</div>
      <div class="eval-page__metrics">
        <div class="eval-page__metric">
          <div class="eval-page__metric-value">{{ result.total }}</div>
          <div class="eval-page__metric-label">Test Cases</div>
        </div>
        <div class="eval-page__metric">
          <div class="eval-page__metric-value">{{ formatRate(result.hitRate) }}</div>
          <div class="eval-page__metric-label">Hit@5</div>
        </div>
        <div class="eval-page__metric">
          <div class="eval-page__metric-value">{{ formatRate(result.recall) }}</div>
          <div class="eval-page__metric-label">Recall@5</div>
        </div>
        <div class="eval-page__metric">
          <div class="eval-page__metric-value">{{ formatMrr(result.mrr) }}</div>
          <div class="eval-page__metric-label">MRR</div>
        </div>
        <div class="eval-page__metric">
          <div class="eval-page__metric-value">{{ formatLatency(result.avgLatencyMs) }}</div>
          <div class="eval-page__metric-label">Avg Latency</div>
        </div>
        <div class="eval-page__metric">
          <div class="eval-page__metric-value">{{ formatLatency(result.p95LatencyMs) }}</div>
          <div class="eval-page__metric-label">P95 Latency</div>
        </div>
      </div>
    </section>

    <section v-if="result" class="eval-page__card">
      <div class="eval-page__list-head">
        <div class="eval-page__list-title">Case 列表</div>
        <el-input
          v-model="keyword"
          class="eval-page__search"
          clearable
          placeholder="搜索 Case..."
        />
      </div>
      <div class="eval-page__filters">
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
          <el-empty description="暂无评测 Case" :image-size="100" />
        </template>
      </el-table>
    </section>

    <el-drawer
      v-model="drawerVisible"
      :title="selectedCase ? `Case #${selectedCase.id}` : 'Case 详情'"
      direction="rtl"
      size="480px"
      append-to-body
      class="eval-drawer"
      @close="onDrawerClose"
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
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import { getEvalDatasetApi, runEvalApi } from '@/api/eval'
import type { EvalCase, EvalCaseResult, EvalCaseView, EvalFailedCase, EvalResult } from '@/types/eval'
import { useAuth } from '@/composables/useAuth'

const { hasToken } = useAuth()

const datasetLoading = ref(false)
const datasetName = ref('')
const cases = ref<EvalCase[]>([])
const showDataset = ref(false)
const running = ref(false)
const result = ref<EvalResult | null>(null)
const drawerVisible = ref(false)
const selectedCase = ref<EvalCaseView | null>(null)
const statusFilter = ref<'all' | 'passed' | 'failed'>('all')
const keyword = ref('')

const caseCount = computed(() => cases.value.length)

function caseKey(id: number | string | null | undefined) {
  return id == null ? '' : String(id)
}

const detailMap = computed(() => {
  const map = new Map<string, EvalCaseResult | EvalFailedCase>()
  for (const item of result.value?.cases ?? []) {
    const key = caseKey(item?.id)
    if (key) map.set(key, item)
  }
  for (const item of result.value?.failedCases ?? []) {
    const key = caseKey(item?.id)
    if (key && !map.has(key)) map.set(key, item)
  }
  return map
})

const caseViews = computed<EvalCaseView[]>(() => {
  if (!result.value) return []
  if (result.value.cases?.length) {
    return result.value.cases.map((item) => toCaseViewFromResult(item))
  }
  const source = cases.value.length ? cases.value : (result.value.failedCases ?? [])
  return source.map((item) => toCaseView(item))
})

const failedCount = computed(() => caseViews.value.filter((item) => item.failed).length)
const passedCount = computed(() => caseViews.value.length - failedCount.value)

const visibleCases = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return caseViews.value.filter((item) => {
    if (statusFilter.value === 'failed' && !item.failed) return false
    if (statusFilter.value === 'passed' && item.failed) return false
    if (q && !item.query.toLowerCase().includes(q)) return false
    return true
  })
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

function formatIds(ids?: string[]) {
  if (!ids || !ids.length) return '-'
  return ids.join(', ')
}

function formatRate(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '-'
  return `${(Number(value) * 100).toFixed(1)}%`
}

function formatMrr(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return Number(value).toFixed(2)
}

function formatCaseRate(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return `${Math.round(Number(value) * 100)}%`
}

function formatLatency(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return `${Math.round(Number(value))} ms`
}

function formatScore(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return Number(value).toFixed(2)
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

function onDrawerClose() {
  drawerVisible.value = false
}

async function fetchDataset() {
  if (!hasToken()) {
    cases.value = []
    datasetName.value = ''
    return
  }
  datasetLoading.value = true
  try {
    const data = await getEvalDatasetApi()
    datasetName.value = data?.name ?? ''
    cases.value = data?.cases ?? []
  } finally {
    datasetLoading.value = false
  }
}

async function onRun() {
  if (!hasToken()) {
    ElMessage.warning('请先登录后再开始评测')
    return
  }
  if (running.value) return
  running.value = true
  try {
    result.value = await runEvalApi()
    selectedCase.value = null
    drawerVisible.value = false
    ElMessage.success('评测完成')
  } finally {
    running.value = false
  }
}

onMounted(fetchDataset)
</script>

<style scoped lang="scss">
.eval-page {
  &__card {
    background: #fff;
    border: 1px solid #e6ecef;
    border-radius: 14px;
    box-shadow: 0 6px 18px rgba(15, 45, 60, 0.04);
    margin-bottom: 16px;
    padding: 16px 18px 18px;
  }

  &__list-title {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 14px;
    font-size: 15px;
    font-weight: 600;
    color: #1f2d3d;
  }

  &__list-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;

    .eval-page__list-title {
      margin-bottom: 0;
    }
  }

  &__search {
    width: 240px;

    :deep(.el-input__wrapper) {
      border-radius: 8px;
    }
  }

  &__filters {
    margin-bottom: 12px;
  }

  &__summary-name {
    font-size: 18px;
    font-weight: 600;
    color: #1f2d3d;
  }

  &__summary-meta {
    margin-top: 6px;
    color: #64748b;
    font-size: 13px;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-top: 16px;
  }

  &__hint {
    margin: -6px 0 12px;
    color: #64748b;
    font-size: 13px;
  }

  &__table {
    width: 100%;

    :deep(.el-table__row) {
      cursor: pointer;
    }

    :deep(.eval-page__row--selected > td) {
      background: rgba(15, 76, 92, 0.08) !important;
    }
  }

  &__ids {
    white-space: normal;
    word-break: break-all;
    line-height: 1.5;
  }

  &__metrics {
    display: grid;
    grid-template-columns: repeat(6, minmax(0, 1fr));
    gap: 12px;
  }

  &__metric {
    padding: 14px 12px;
    background: #f6f9fa;
    border: 1px solid #e6ecef;
    border-radius: 10px;
    text-align: center;
  }

  &__metric-value {
    font-size: 20px;
    font-weight: 700;
    color: #0f4c5c;
    word-break: break-all;
  }

  &__metric-label {
    margin-top: 6px;
    font-size: 13px;
    color: #64748b;
  }

  &__block {
    margin-top: 18px;

    &--half {
      flex: 1;
      min-width: 0;
      margin-top: 0;
    }
  }

  &__block-title {
    margin-bottom: 10px;
    font-size: 13px;
    font-weight: 600;
    color: #64748b;
  }

  &__query {
    margin: 0;
    font-size: 16px;
    line-height: 1.6;
    color: #1f2d3d;
  }

  &__note {
    margin: 0 0 10px;
    padding: 8px 10px;
    background: #f8fafb;
    border: 1px solid #e6ecef;
    border-radius: 8px;
    color: #64748b;
    font-size: 13px;
  }

  &__compare {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-top: 18px;
  }

  &__docs {
    margin: 0;
    padding: 0;
    list-style: none;
  }

  &__doc {
    padding: 10px 12px;
    border: 1px solid #e6ecef;
    border-radius: 10px;
    background: #fafcfd;

    + .eval-page__doc {
      margin-top: 8px;
    }
  }

  &__doc-head {
    display: flex;
    align-items: flex-start;
    gap: 8px;
  }

  &__rank,
  &__gt-mark {
    flex-shrink: 0;
    color: #0f4c5c;
    font-weight: 700;
    font-size: 13px;
  }

  &__doc-name {
    flex: 1;
    min-width: 0;
    word-break: break-all;
    color: #1f2d3d;
    font-size: 13px;
    line-height: 1.5;
  }

  &__doc-score {
    margin: 6px 0 0 28px;
    color: #64748b;
    font-size: 12px;
  }

  &__case-metrics {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;

    > div {
      padding: 12px;
      background: #f6f9fa;
      border: 1px solid #e6ecef;
      border-radius: 10px;
      color: #1f2d3d;
      font-size: 14px;
      font-weight: 600;
    }

    span {
      display: block;
      margin-bottom: 4px;
      color: #64748b;
      font-size: 12px;
      font-weight: 500;
    }
  }
}

@media (max-width: 1100px) {
  .eval-page__metrics,
  .eval-page__case-metrics,
  .eval-page__compare {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 900px) {
  .eval-page__metrics,
  .eval-page__case-metrics,
  .eval-page__compare {
    grid-template-columns: 1fr;
  }
}
</style>

<style lang="scss">
.eval-drawer {
  .el-drawer__body {
    overflow: auto;
    padding: 8px 20px 24px;
  }
}

.eval-drawer__query {
  margin: 0 0 8px;
  font-size: 16px;
  line-height: 1.6;
  color: #1f2d3d;
  font-weight: 600;
}

.eval-drawer__section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #eef2f5;

  h3 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0 0 12px;
    font-size: 13px;
    font-weight: 600;
    color: #64748b;

    em {
      font-style: normal;
      color: #0f4c5c;
    }
  }
}

.eval-drawer__metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;

  > div {
    padding: 10px 12px;
    background: #f6f9fa;
    border: 1px solid #e6ecef;
    border-radius: 10px;
    color: #1f2d3d;
    font-size: 14px;
    font-weight: 600;
  }

  span {
    display: block;
    margin-bottom: 4px;
    color: #64748b;
    font-size: 12px;
    font-weight: 500;
  }
}

.eval-drawer__note {
  margin: 0 0 10px;
  padding: 8px 10px;
  background: #f8fafb;
  border: 1px solid #e6ecef;
  border-radius: 8px;
  color: #64748b;
  font-size: 13px;
}

.eval-drawer__empty {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
}

.eval-drawer__docs {
  margin: 0;
  padding: 0;
  list-style: none;
}

.eval-drawer__doc {
  padding: 10px 12px;
  border: 1px solid #e6ecef;
  border-radius: 10px;
  background: #fafcfd;

  + .eval-drawer__doc {
    margin-top: 8px;
  }
}

.eval-drawer__doc-head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: #1f2d3d;
  font-size: 13px;
  line-height: 1.5;

  span {
    flex-shrink: 0;
    color: #0f4c5c;
    font-weight: 700;
  }

  strong {
    flex: 1;
    min-width: 0;
    font-weight: 600;
    word-break: break-all;
  }
}

.eval-drawer__meta {
  margin: 6px 0 0 24px;
  color: #64748b;
  font-size: 12px;
}
</style>
