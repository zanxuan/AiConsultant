<template>
  <PageContainer title="RAG 评测" desc="上传测试集并选择知识库，运行离线检索评测">
    <section class="eval-page__card">
      <div class="eval-page__list-title">开始评测</div>
      <div class="eval-page__form">
        <div class="eval-page__field">
          <span class="eval-page__label">测试集</span>
          <el-upload
            class="eval-page__dataset"
            :auto-upload="false"
            :show-file-list="false"
            accept=".json,application/json"
            :disabled="running"
            @change="onDatasetChange"
          >
            <el-tooltip :disabled="!datasetName" :content="datasetName" placement="top">
              <el-button class="eval-page__dataset-btn" :disabled="running">
                {{ datasetName || '选择测试集' }}
              </el-button>
            </el-tooltip>
          </el-upload>
        </div>
        <div class="eval-page__field">
          <span class="eval-page__label">知识库</span>
          <KnowledgeSelect v-model="knowledgeId" placeholder="请选择知识库" />
        </div>
        <el-button
          type="primary"
          :loading="running"
          :disabled="running || !datasetFile || !knowledgeId"
          @click="onRun"
        >
          开始评测
        </el-button>
        <el-button :disabled="running || historyLoading" @click="onCompare">对比</el-button>
      </div>
    </section>

    <div class="eval-history">
      <div class="eval-history__main">
        <section v-if="section === 'history'" class="eval-page__card">
          <div class="eval-page__list-head">
            <div class="eval-page__list-title">历史评测</div>
            <div class="eval-history__actions">
              <el-button :disabled="historyLoading || running" @click="fetchHistory">刷新</el-button>
              <template v-if="comparing">
                <el-button :disabled="historyLoading || running" @click="cancelCompare">取消</el-button>
                <el-button
                  type="primary"
                  :disabled="historyLoading || running"
                  @click="startCompare"
                >
                  开始对比
                </el-button>
              </template>
            </div>
          </div>
          <p v-if="comparing" class="eval-page__hint">
            先选择的记录为评测 A，后选择的为评测 B。已选 {{ compareSelection.length }} / 2
            <template v-if="compareSelection[0]"> · A：{{ comparePickLabel(compareSelection[0]) }}</template>
            <template v-if="compareSelection[1]"> · B：{{ comparePickLabel(compareSelection[1]) }}</template>
          </p>
          <p v-if="historyError" class="eval-page__note">
            {{ historyError }}
            <el-button link type="primary" @click="fetchHistory">重试</el-button>
          </p>
          <el-table
            v-loading="historyLoading"
            :data="historyList"
            class="eval-page__table"
            header-cell-class-name="eval-page__table-header"
            row-key="id"
            :row-class-name="historyRowClass"
            @row-click="onOpenHistory"
          >
            <el-table-column v-if="comparing" width="88" align="center">
              <template #default="{ row }">
                <div class="eval-history__pick" @click.stop="toggleCompare(row)">
                  <el-checkbox
                    :model-value="compareRank(row) >= 0"
                    :disabled="compareSelection.length >= 2 && compareRank(row) < 0"
                    @click.stop
                    @change="toggleCompare(row)"
                  />
                  <span v-if="compareRank(row) === 0">A</span>
                  <span v-else-if="compareRank(row) === 1">B</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="评测时间" width="160">
              <template #default="{ row }">{{ row.createTime || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.errorMessage"
                  :content="row.errorMessage"
                  placement="top"
                >
                  <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
                    {{ statusLabel(row.status) }}
                  </el-tag>
                </el-tooltip>
                <el-tag v-else :type="statusTagType(row.status)" size="small" effect="plain">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="数据集" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <div>{{ row.datasetName || '—' }}</div>
                <div class="eval-page__sub">{{ datasetSourceLabel(row.datasetSource) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="Hit@5" width="100" align="right">
              <template #default="{ row }">{{ formatRate(row.hitRate) }}</template>
            </el-table-column>
            <el-table-column label="Recall@5" width="110" align="right">
              <template #default="{ row }">{{ formatRate(row.recall) }}</template>
            </el-table-column>
            <el-table-column label="MRR" width="90" align="right">
              <template #default="{ row }">{{ formatMrr(row.mrr) }}</template>
            </el-table-column>
            <el-table-column label="耗时" width="110" align="right">
              <template #default="{ row }">{{ formatLatency(row.durationMs) }}</template>
            </el-table-column>
            <template #empty>
              <el-empty
                :description="historyEmptyText"
                :image-size="100"
              />
            </template>
          </el-table>
          <div v-if="pagination.total > 0" class="eval-page__pager">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.pageSize"
              background
              layout="total, prev, pager, next"
              :total="pagination.total"
              @current-change="fetchHistory"
            />
          </div>
        </section>

        <div v-else v-loading="detailLoading" class="eval-history__detail">
          <p v-if="detailError && !detailLoading" class="eval-page__note">
            {{ detailError }}
            <el-button link type="primary" @click="loadDetail(selectedId)">重试</el-button>
          </p>
          <EvalOverview v-else-if="detail && section === 'overview'" :detail="detail" />
          <EvalCasePanel
            v-else-if="detail && section === 'cases'"
            v-model:keyword="caseKeyword"
            v-model:status-filter="caseStatus"
            :detail="detail"
            :active-case-id="activeCaseId"
          />
          <EvalConfigPanel v-else-if="detail && section === 'config'" :detail="detail" />
          <EvalMetricsPanel v-else-if="detail && section === 'metrics'" :detail="detail" />
          <EvalCasePanel
            v-else-if="detail && section === 'failed'"
            v-model:keyword="failedKeyword"
            :detail="detail"
            only-failed
            :active-case-id="activeCaseId"
          />
        </div>
      </div>

      <nav class="eval-history__nav" aria-label="历史评测分区">
        <button
          v-for="item in sections"
          :key="item.key"
          type="button"
          class="eval-history__nav-item"
          :class="{ 'is-active': section === item.key }"
          :disabled="item.key !== 'history' && !selectedId"
          @click="onSection(item.key)"
        >
          <span>{{ item.label }}</span>
          <em v-if="item.key === 'failed' && failedCountLabel">{{ failedCountLabel }}</em>
        </button>
      </nav>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import KnowledgeSelect from '@/components/knowledge/KnowledgeSelect.vue'
import EvalCasePanel from '@/components/eval/EvalCasePanel.vue'
import EvalConfigPanel from '@/components/eval/EvalConfigPanel.vue'
import EvalMetricsPanel from '@/components/eval/EvalMetricsPanel.vue'
import EvalOverview from '@/components/eval/EvalOverview.vue'
import { getEvalRunDetailApi, getEvalRunsApi, runUploadedEvalApi } from '@/api/eval'
import {
  datasetSourceLabel,
  formatLatency,
  formatMrr,
  formatRate,
  savedMetrics,
  statusLabel,
  statusTagType,
} from '@/components/eval/format'
import type { EvalCaseStatusFilter } from '@/components/eval/case-view'
import {
  consumeEvalCaseListSnapshot,
  consumeReturnToEvalList,
  markReturnToEvalList,
  parseEvalCasePath,
  peekEvalCaseListSnapshot,
  readMainScrollTop,
  saveEvalCaseListSnapshot,
  writeMainScrollTop,
  type EvalCaseListSnapshot,
} from '@/components/eval/list-state'
import type { EvalRun, EvalRunDetail } from '@/types/eval'
import { useAuth } from '@/composables/useAuth'
import { usePagination } from '@/composables/usePagination'

type EvalSection = 'history' | 'overview' | 'cases' | 'config' | 'metrics' | 'failed'

const sections: { key: EvalSection; label: string }[] = [
  { key: 'history', label: '历史评测' },
  { key: 'overview', label: '评测概览' },
  { key: 'cases', label: '测试用例' },
  { key: 'config', label: '评测配置' },
  { key: 'metrics', label: '评测指标' },
  { key: 'failed', label: '失败用例' },
]

interface ComparePick {
  id: string
  datasetName: string
  createTime: string
}

const route = useRoute()
const router = useRouter()
const { hasToken } = useAuth()
const { pagination, setTotal, resetPage } = usePagination(10)

const datasetFile = ref<File | null>(null)
const datasetName = computed(() => {
  const name = datasetFile.value?.name?.trim() ?? ''
  if (!name) return ''
  const withoutSuffix = name.replace(/\.json$/i, '')
  return withoutSuffix || name
})
const knowledgeId = ref<number | string | null>(null)
const running = ref(false)
const historyList = ref<EvalRun[]>([])
const historyLoading = ref(false)
const historyError = ref('')
const section = ref<EvalSection>('history')
const selectedId = ref('')
const detail = ref<EvalRunDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const comparing = ref(false)
const compareSelection = ref<ComparePick[]>([])
const caseKeyword = ref('')
const caseStatus = ref<EvalCaseStatusFilter>('all')
const failedKeyword = ref('')
const activeCaseId = ref('')
let detailRequest = 0

const historyEmptyText = computed(() => {
  if (!hasToken()) return '请先登录后查看评测历史'
  if (historyError.value) return '评测历史加载失败'
  return '暂无评测历史'
})

const failedCountLabel = computed(() => {
  const value = savedMetrics(detail.value).failedCount
  if (value == null) return ''
  return String(Math.round(value))
})

function historyRowClass({ row }: { row: EvalRun }) {
  if (comparing.value) return compareRank(row) >= 0 ? 'eval-page__row--selected' : ''
  return String(row.id) === selectedId.value ? 'eval-page__row--selected' : ''
}

function compareRank(row: { id?: unknown }) {
  const id = row.id == null ? '' : String(row.id)
  return compareSelection.value.findIndex((item) => item.id === id)
}

function comparePickLabel(item: ComparePick) {
  return [item.datasetName, item.createTime].filter(Boolean).join(' · ') || item.id
}

function toggleCompare(row: { id?: unknown; datasetName?: unknown; createTime?: unknown }) {
  if (row.id == null || row.id === '') return
  const id = String(row.id)
  const index = compareSelection.value.findIndex((item) => item.id === id)
  if (index >= 0) {
    compareSelection.value.splice(index, 1)
    return
  }
  if (compareSelection.value.length >= 2) return
  compareSelection.value.push({
    id,
    datasetName: typeof row.datasetName === 'string' ? row.datasetName : '',
    createTime: typeof row.createTime === 'string' ? row.createTime : '',
  })
}

async function fetchHistory() {
  if (!hasToken()) {
    historyList.value = []
    historyError.value = ''
    setTotal(0)
    return
  }
  historyLoading.value = true
  historyError.value = ''
  try {
    const res = await getEvalRunsApi({
      pageNum: pagination.page,
      pageSize: pagination.pageSize,
    })
    historyList.value = res?.list ?? []
    setTotal(Number(res?.total ?? 0))
  } catch {
    historyList.value = []
    setTotal(0)
    historyError.value = '评测历史加载失败'
  } finally {
    historyLoading.value = false
  }
}

async function loadDetail(id: string) {
  const requestId = ++detailRequest
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const data = await getEvalRunDetailApi(id)
    if (requestId !== detailRequest) return
    detail.value = data
  } catch {
    if (requestId !== detailRequest) return
    detailError.value = '评测详情加载失败'
  } finally {
    if (requestId === detailRequest) detailLoading.value = false
  }
}

function onSection(key: EvalSection) {
  if (key !== 'history' && !selectedId.value) return
  section.value = key
}

function onOpenHistory(row: EvalRun) {
  if (running.value || row.id == null) return
  if (comparing.value) {
    toggleCompare(row)
    return
  }
  caseKeyword.value = ''
  caseStatus.value = 'all'
  failedKeyword.value = ''
  activeCaseId.value = ''
  selectedId.value = String(row.id)
  section.value = 'overview'
  loadDetail(selectedId.value)
}

function onCompare() {
  if (running.value || historyLoading.value) return
  if (historyError.value) {
    ElMessage.warning('评测历史加载失败')
    return
  }
  if (!pagination.total) {
    ElMessage.warning('暂无评测记录，请先运行一次评测')
    return
  }
  comparing.value = true
  section.value = 'history'
}

function cancelCompare() {
  comparing.value = false
  compareSelection.value = []
}

function startCompare() {
  const selected = compareSelection.value
  if (selected.length === 0) {
    ElMessage.warning('请至少选择两条评测记录')
    return
  }
  if (selected.length === 1) {
    ElMessage.warning('还需选择 1 条评测记录')
    return
  }
  router.push({
    name: 'eval-compare',
    query: { a: selected[0].id, b: selected[1].id },
  })
}

function onDatasetChange(uploadFile: UploadFile) {
  datasetFile.value = uploadFile.raw ?? null
}

async function onRun() {
  if (!hasToken()) {
    ElMessage.warning('请先登录后再开始评测')
    return
  }
  if (!datasetFile.value) {
    ElMessage.warning('请选择测试集')
    return
  }
  if (!knowledgeId.value) {
    ElMessage.warning('请选择知识库')
    return
  }
  if (running.value) return
  running.value = true
  try {
    const executed = await runUploadedEvalApi(datasetFile.value, knowledgeId.value)
    ElMessage.success('评测完成')
    resetPage()
    const historyPromise = fetchHistory()
    if (executed?.runId != null && executed.runId !== '') {
      selectedId.value = String(executed.runId)
      section.value = 'overview'
      await Promise.all([loadDetail(selectedId.value), historyPromise])
      return
    }
    ElMessage.warning('评测已完成，但没有保存可查看的历史记录')
    section.value = 'history'
    await historyPromise
  } finally {
    running.value = false
  }
}

function queryText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function applyListSnapshot(snapshot: EvalCaseListSnapshot) {
  pagination.page = snapshot.page
  pagination.pageSize = snapshot.pageSize
  selectedId.value = snapshot.runId
  section.value = snapshot.section
  activeCaseId.value = snapshot.caseId
  if (snapshot.section === 'failed') {
    failedKeyword.value = snapshot.keyword
    return
  }
  caseKeyword.value = snapshot.keyword
  caseStatus.value = snapshot.statusFilter
}

async function restoreMainScroll(top: number) {
  await nextTick()
  writeMainScrollTop(top)
  await new Promise((resolve) => requestAnimationFrame(() => resolve(undefined)))
  writeMainScrollTop(top)
}

/**
 * 从用例详情返回时恢复离开前的列表。
 * 同一次会话用内存标记识别返回；刷新详情后无法回到原列表时，靠 fromCase 查询参数打开对应评测。
 */
async function restoreListFromCase() {
  const fromQuery = route.query.fromCase === '1'
  if (!consumeReturnToEvalList() && !fromQuery) return false
  const forward = typeof window.history.state?.forward === 'string' ? window.history.state.forward : ''
  const fromForward = parseEvalCasePath(forward)
  const querySection = queryText(route.query.section)
  const fromQueryLocation = fromQuery
    ? {
        runId: queryText(route.query.runId),
        caseId: queryText(route.query.caseId),
        section: querySection === 'failed' || querySection === 'cases' ? querySection : '',
      }
    : null
  if (!fromForward && !fromQueryLocation) return false

  const returned = fromForward ?? fromQueryLocation
  const snapshot = peekEvalCaseListSnapshot()
  const runId = returned?.runId || ''
  if (!runId) {
    if (fromQuery) await router.replace({ name: 'eval' }).catch(() => {})
    return false
  }
  const scrollTop = snapshot && snapshot.runId === runId ? snapshot.scrollTop : 0
  if (snapshot && snapshot.runId === runId) {
    consumeEvalCaseListSnapshot()
    applyListSnapshot(snapshot)
    if (returned?.caseId) activeCaseId.value = returned.caseId
  } else {
    selectedId.value = runId
    section.value = returned?.section === 'failed' ? 'failed' : 'cases'
    activeCaseId.value = returned?.caseId || ''
  }

  await Promise.all([fetchHistory(), loadDetail(selectedId.value)])
  await restoreMainScroll(scrollTop)
  if (fromQuery) await router.replace({ name: 'eval' }).catch(() => {})
  return true
}

onBeforeRouteLeave((to) => {
  if (to.name !== 'eval-case') return
  if (section.value !== 'cases' && section.value !== 'failed') return
  const caseId = Array.isArray(to.params.caseId) ? to.params.caseId[0] : String(to.params.caseId ?? '')
  markReturnToEvalList()
  saveEvalCaseListSnapshot({
    section: section.value,
    runId: String(detail.value?.id ?? selectedId.value),
    caseId,
    keyword: section.value === 'failed' ? failedKeyword.value : caseKeyword.value,
    statusFilter: section.value === 'failed' ? 'all' : caseStatus.value,
    page: pagination.page,
    pageSize: pagination.pageSize,
    scrollTop: readMainScrollTop(),
  })
})

onMounted(async () => {
  const restored = await restoreListFromCase()
  if (!restored) fetchHistory()
})
</script>

<style lang="scss">
@import '@/components/eval/eval-shared.scss';
</style>

<style scoped lang="scss">
.eval-history {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 168px;
  gap: 16px;
  align-items: start;

  &__main,
  &__detail {
    min-width: 0;
  }

  &__detail {
    min-height: 160px;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  &__pick {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    color: #0f4c5c;
    font-size: 12px;
    font-weight: 700;
  }

  &__nav {
    position: sticky;
    top: 16px;
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 8px;
    background: #fff;
    border: 1px solid #e6ecef;
    border-radius: 14px;
    box-shadow: 0 6px 18px rgba(15, 45, 60, 0.04);
  }

  &__nav-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    width: 100%;
    padding: 10px 12px;
    border: 0;
    border-radius: 8px;
    background: transparent;
    color: #334155;
    font-size: 14px;
    text-align: left;
    cursor: pointer;

    em {
      font-style: normal;
      color: #0f4c5c;
      font-size: 12px;
      font-weight: 600;
    }

    &.is-active {
      background: rgba(15, 76, 92, 0.1);
      color: #0f4c5c;
      font-weight: 600;
    }

    &:disabled {
      color: #94a3b8;
      cursor: not-allowed;
    }
  }
}

@media (max-width: 900px) {
  .eval-history {
    grid-template-columns: 1fr;
  }

  .eval-history__nav {
    position: static;
    flex-direction: row;
    overflow-x: auto;
  }

  .eval-history__nav-item {
    flex: 0 0 auto;
    width: auto;
  }
}
</style>
