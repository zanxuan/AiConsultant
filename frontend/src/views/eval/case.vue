<template>
  <div class="eval-case">
    <div class="eval-case__back-bar">
      <button type="button" class="eval-case__back" @click="onBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>{{ backLabel }}</span>
      </button>
    </div>

    <div v-loading="loading" class="eval-case__body">
      <p v-if="!loading && error" class="eval-page__note">
        {{ error }}
        <el-button link type="primary" @click="load">重试</el-button>
      </p>
      <p v-else-if="!loading && missing" class="eval-page__note">
        {{ missingText }}
        <el-button link type="primary" @click="onBack">返回列表</el-button>
      </p>

      <template v-else-if="caseView">
        <header class="eval-page__card eval-case__header">
          <div class="eval-case__title">
            <h2>Case #{{ caseView.id }}</h2>
            <el-tag :type="caseView.failed ? 'danger' : 'success'" effect="plain">
              {{ caseView.failed ? 'Failed' : 'Passed' }}
            </el-tag>
          </div>
          <p class="eval-case__kicker">Query</p>
          <p class="eval-case__query">{{ caseView.query }}</p>
        </header>

        <section class="eval-page__card">
          <div class="eval-page__list-title">评测指标</div>
          <div class="eval-case__metrics">
            <div v-for="item in metrics" :key="item.label" class="eval-page__metric">
              <div class="eval-page__metric-value">{{ item.value }}</div>
              <div class="eval-page__metric-label">{{ item.label }}</div>
            </div>
          </div>
        </section>

        <div class="eval-case__tabs" role="tablist">
          <button
            type="button"
            role="tab"
            class="eval-case__tab"
            :class="{ 'is-active': caseTab === 'results' }"
            :aria-selected="caseTab === 'results'"
            @click="caseTab = 'results'"
          >
            检索结果
          </button>
          <button
            type="button"
            role="tab"
            class="eval-case__tab"
            :class="{ 'is-active': caseTab === 'pipeline' }"
            :aria-selected="caseTab === 'pipeline'"
            @click="caseTab = 'pipeline'"
          >
            检索过程
          </button>
          <button
            type="button"
            role="tab"
            class="eval-case__tab"
            :class="{ 'is-active': caseTab === 'trace' }"
            :aria-selected="caseTab === 'trace'"
            @click="caseTab = 'trace'"
          >
            检索链路
          </button>
        </div>

        <section v-show="caseTab === 'results'" class="eval-page__card" role="tabpanel">
          <div class="eval-case__split">
            <div class="eval-case__column">
              <h3>
                Ground Truth
                <em>{{ caseView.expectedDocIds.length }}</em>
              </h3>
              <ul v-if="caseView.expectedDocIds.length" class="eval-drawer__docs">
                <li
                  v-for="(docId, index) in caseView.expectedDocIds"
                  :key="`${caseView.id}-e-${index}-${docId}`"
                  class="eval-drawer__doc"
                  :class="{ 'is-expected': inRetrieved(docId) }"
                >
                  <div class="eval-drawer__doc-head">
                    <span>✓</span>
                    <strong>{{ docTitle(docId) }}</strong>
                    <el-tag
                      size="small"
                      effect="plain"
                      :type="inRetrieved(docId) ? 'success' : 'danger'"
                    >
                      {{ inRetrieved(docId) ? '已命中' : '未命中' }}
                    </el-tag>
                  </div>
                  <div v-if="docName(docId)" class="eval-drawer__meta">Document ID: {{ docId }}</div>
                </li>
              </ul>
              <p v-else class="eval-drawer__empty">无 Expected Documents</p>
            </div>

            <div class="eval-case__column">
              <h3>
                Retrieved Documents
                <em>{{ caseView.retrievedDocIds.length }}</em>
              </h3>
              <p v-if="caseView.failed" class="eval-drawer__note">
                Ground Truth 未出现在 Top 5 检索结果中
              </p>
              <ul v-if="caseView.retrievedDocIds.length" class="eval-drawer__docs">
                <li
                  v-for="(docId, index) in caseView.retrievedDocIds"
                  :key="`${caseView.id}-r-${index}-${docId}`"
                  class="eval-drawer__doc"
                  :class="{ 'is-expected': isGroundTruthDoc(caseView, docId) }"
                >
                  <div class="eval-drawer__doc-head">
                    <span>#{{ index + 1 }}</span>
                    <strong>{{ docTitle(docId) }}</strong>
                    <el-tag
                      v-if="isGroundTruthDoc(caseView, docId)"
                      size="small"
                      effect="plain"
                      type="success"
                    >
                      Hit
                    </el-tag>
                  </div>
                  <div v-if="docName(docId)" class="eval-drawer__meta">Document ID: {{ docId }}</div>
                  <div class="eval-drawer__meta">Score: {{ formatRetrievedScore(caseView, index) }}</div>
                </li>
              </ul>
              <p v-else class="eval-drawer__empty">本次检索未返回文档。</p>
            </div>
          </div>
        </section>

        <section v-show="caseTab === 'pipeline'" class="eval-page__card" role="tabpanel">
          <h3 class="eval-case__subhead">Retrieval Pipeline</h3>
          <el-steps class="eval-case__steps" direction="vertical" :active="3" finish-status="success">
            <el-step title="Query" :description="caseView.query" />
            <el-step title="Hybrid Retrieval" description="现有评测走 HybridRetriever" />
            <el-step
              title="Retrieved Documents"
              :description="`${caseView.retrievedDocIds.length} documents`"
            />
            <el-step
              title="Ground Truth"
              :description="`${caseView.expectedDocIds.length} documents`"
            />
          </el-steps>
        </section>

        <section v-show="caseTab === 'trace'" class="eval-page__card eval-case__trace" role="tabpanel">
          <EvalRetrievalTrace
            :run-id="runId"
            :case-id="caseView.id"
            @loaded="onTraceLoaded"
          />
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getEvalRunDetailApi } from '@/api/eval'
import EvalRetrievalTrace from '@/components/eval/EvalRetrievalTrace.vue'
import { formatRetrievedScore, isGroundTruthDoc, resolveCaseView } from '@/components/eval/case-view'
import { formatCaseRate, formatLatency, formatMrr, formatScore } from '@/components/eval/format'
import {
  clearReturnToEvalList,
  isEvalListPath,
  markReturnToEvalList,
  writeMainScrollTop,
} from '@/components/eval/list-state'
import type { EvalCaseView, RetrievalTrace, RetrievalTraceChunk } from '@/types/eval'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const missing = ref(false)
const missingText = ref('未找到该用例')
const caseView = ref<EvalCaseView | null>(null)
const docNames = ref(new Map<string, string>())
const caseTab = ref<'results' | 'pipeline' | 'trace'>('results')
let requestSeq = 0

const runId = computed(() => {
  const value = route.params.runId
  return typeof value === 'string' ? value : ''
})
const caseId = computed(() => {
  const value = route.params.caseId
  return typeof value === 'string' ? value : ''
})
const fromSection = computed(() => {
  const from = route.query.from
  return from === 'failed' || from === 'cases' ? from : ''
})
const backLabel = computed(() => {
  if (fromSection.value === 'failed') return '返回失败用例'
  if (fromSection.value === 'cases') return '返回测试用例'
  return '返回评测'
})

const metrics = computed(() => {
  const row = caseView.value
  if (!row) return []
  return [
    { label: 'Hit@5', value: row.failed ? 'Failed' : 'Passed' },
    { label: 'Recall@5', value: formatCaseRate(row.recall) },
    { label: 'MRR', value: formatMrr(row.mrr) },
    { label: 'Latency', value: formatLatency(row.latencyMs) },
    { label: 'Top Score', value: formatScore(row.topScore) },
    { label: 'Status', value: row.failed ? 'Failed' : 'Passed' },
  ]
})

watch(() => [runId.value, caseId.value] as const, () => {
  caseTab.value = 'results'
  load()
}, { immediate: true })

onBeforeUnmount(() => {
  requestSeq += 1
})

async function load() {
  const seq = ++requestSeq
  loading.value = true
  error.value = ''
  missing.value = false
  missingText.value = '未找到该用例'
  caseView.value = null
  docNames.value = new Map()
  writeMainScrollTop(0)
  if (!runId.value || !caseId.value) {
    loading.value = false
    missing.value = true
    return
  }
  try {
    const detail = await getEvalRunDetailApi(runId.value)
    if (seq !== requestSeq) return
    if (!detail) {
      missing.value = true
      missingText.value = '未找到该评测记录'
      return
    }
    if (!detail.result) {
      missing.value = true
      missingText.value = '该记录没有评测结果'
      return
    }
    const view = resolveCaseView(detail, caseId.value)
    if (!view) {
      missing.value = true
      return
    }
    caseView.value = view
  } catch {
    if (seq !== requestSeq) return
    error.value = '用例详情加载失败'
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

onBeforeRouteLeave((to) => {
  if (to.name === 'eval') markReturnToEvalList()
  else clearReturnToEvalList()
})

function onBack() {
  const back = typeof window.history.state?.back === 'string' ? window.history.state.back : ''
  markReturnToEvalList()
  if (isEvalListPath(back)) {
    router.back()
    return
  }
  router.push({
    name: 'eval',
    query: {
      fromCase: '1',
      runId: runId.value,
      caseId: caseId.value,
      section: fromSection.value || 'cases',
    },
  })
}

function onTraceLoaded(trace: RetrievalTrace | null) {
  docNames.value = collectDocNames(trace)
}

function rememberName(names: Map<string, string>, id: unknown, name: unknown) {
  if (id == null || id === '' || typeof name !== 'string' || !name.trim()) return
  names.set(String(id), name.trim())
}

function collectDocNames(trace: RetrievalTrace | null) {
  const names = new Map<string, string>()
  if (!trace?.saved) return names
  for (const doc of trace.evalDocs ?? []) rememberName(names, doc.documentId, doc.documentName)
  const lists: Array<RetrievalTraceChunk[] | undefined> = [
    trace.vectorHits,
    trace.bm25Hits,
    trace.rrfHits,
    trace.afterFilter,
    trace.finalTopK,
  ]
  for (const list of lists) {
    for (const row of list ?? []) rememberName(names, row.documentId, row.documentName)
  }
  return names
}

function docName(docId: string) {
  return docNames.value.get(String(docId)) || ''
}

function docTitle(docId: string) {
  return docName(docId) || `Document ID: ${docId}`
}

function inRetrieved(docId: string) {
  return (caseView.value?.retrievedDocIds ?? []).map(String).includes(String(docId))
}
</script>

<style lang="scss">
@import '@/components/eval/eval-shared.scss';
</style>
