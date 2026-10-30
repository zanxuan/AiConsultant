<template>
  <div class="eval-case-detail">
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
      <div v-if="caseView.chunkScored" class="eval-case__split">
        <div class="eval-case__column">
          <h3>
            标准 Chunk
            <em>{{ caseView.groundTruthChunkIds?.length ?? 0 }}</em>
          </h3>
          <p v-if="caseView.scoreNote" class="eval-drawer__note">{{ caseView.scoreNote }}</p>
          <ul v-if="groundTruthRows.length" class="eval-drawer__docs">
            <li
              v-for="item in groundTruthRows"
              :key="`${caseView.id}-gt-${item.chunkId}`"
              class="eval-drawer__doc"
              :class="{ 'is-expected': item.hit === true, 'is-open': isChunkOpen(item.chunkId) }"
            >
              <button
                type="button"
                class="eval-chunk-toggle"
                :aria-expanded="isChunkOpen(item.chunkId)"
                :aria-controls="chunkPreviewId(item.chunkId)"
                @click="toggleChunk(item.chunkId)"
              >
                <el-icon
                  class="eval-chunk-toggle__arrow"
                  :class="{ 'is-open': isChunkOpen(item.chunkId) }"
                  aria-hidden="true"
                >
                  <ArrowRight />
                </el-icon>
                <strong>Chunk {{ item.chunkId }}</strong>
                <el-tag
                  v-if="item.hit != null"
                  size="small"
                  effect="plain"
                  :type="item.hit ? 'success' : 'danger'"
                >
                  {{ item.hit ? '命中标准 Chunk' : '未进入评测前 5 条' }}
                </el-tag>
              </button>
              <div v-if="item.documentId" class="eval-drawer__meta">
                Document ID: {{ item.documentId }}
                <template v-if="item.documentName"> · {{ item.documentName }}</template>
              </div>
              <div
                v-if="isChunkOpen(item.chunkId)"
                :id="chunkPreviewId(item.chunkId)"
                class="eval-chunk-preview"
              >
                <div class="eval-chunk-preview__label">原文</div>
                <p v-if="isChunkLoading(item.chunkId)" class="eval-chunk-preview__state">正在读取 Chunk 原文...</p>
                <p v-else-if="chunkPreview(item.chunkId) == null" class="eval-chunk-preview__state">
                  暂时无法获取 Chunk 内容
                </p>
                <p v-else class="eval-chunk-preview__text">{{ chunkPreview(item.chunkId) }}</p>
              </div>
            </li>
          </ul>
          <p v-else class="eval-drawer__empty">无标准 Chunk</p>
        </div>

        <div class="eval-case__column">
          <h3>
            评测前 5 个 Chunk
            <em>{{ caseView.retrievedChunkIds?.length ?? 0 }}</em>
          </h3>
          <p v-if="caseView.failed" class="eval-drawer__note">
            标准 Chunk 未出现在评测前 5 条中
          </p>
          <p v-if="caseView.retrievedChunkIds == null" class="eval-drawer__empty">
            本次结果没有返回参与评测的 chunk 列表。
          </p>
          <ul v-else-if="retrievedRows.length" class="eval-drawer__docs">
            <li
              v-for="item in retrievedRows"
              :key="`${caseView.id}-rc-${item.rank}-${item.chunkId ?? 'empty'}`"
              class="eval-drawer__doc"
              :class="{
                'is-expected': item.mark === 'standard',
                'is-same-doc': item.mark === 'same-document',
              }"
            >
              <div class="eval-drawer__doc-head">
                <span>#{{ item.rank }}</span>
                <strong>{{ item.chunkId ? `Chunk ${item.chunkId}` : 'Chunk —' }}</strong>
                <el-tag v-if="item.mark === 'standard'" size="small" effect="plain" type="success">
                  命中标准 Chunk
                </el-tag>
                <el-tag v-else-if="item.mark === 'same-document'" size="small" effect="plain" type="info">
                  同一文档
                </el-tag>
              </div>
              <div class="eval-drawer__meta">Chunk ID: {{ item.chunkId ?? '—' }}</div>
              <div v-if="item.documentId" class="eval-drawer__meta">
                Document ID: {{ item.documentId }}
                <template v-if="item.documentName"> · {{ item.documentName }}</template>
              </div>
              <div class="eval-drawer__meta">Score: {{ formatRetrievedScore(caseView, item.rank - 1) }}</div>
            </li>
          </ul>
          <p v-else class="eval-drawer__empty">评测前 5 条为空。</p>
        </div>
      </div>

      <div v-else class="eval-case__split">
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
          :title="caseView.chunkScored ? 'Retrieved Chunks' : 'Retrieved Documents'"
          :description="pipelineRetrieved"
        />
        <el-step
          :title="caseView.chunkScored ? 'Ground Truth Chunks' : 'Ground Truth'"
          :description="pipelineExpected"
        />
      </el-steps>
    </section>

    <section v-show="caseTab === 'trace'" class="eval-page__card eval-case__trace" role="tabpanel">
      <EvalRetrievalTrace
        :run-id="runId"
        :case-id="caseView.id"
        :chunk-scored="caseView.chunkScored"
        :ground-truth-chunk-ids="caseView.groundTruthChunkIds"
        :retrieved-chunk-ids="caseView.retrievedChunkIds"
        @loaded="onTraceLoaded"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { getEvalChunkContentsApi } from '@/api/eval'
import EvalRetrievalTrace from '@/components/eval/EvalRetrievalTrace.vue'
import {
  buildGroundTruthRows,
  buildRetrievedChunkRows,
  caseKey,
  chunkIdText,
  formatRetrievedScore,
  isGroundTruthDoc,
  traceChunkRows,
} from '@/components/eval/case-view'
import { formatCaseRate, formatLatency, formatMrr, formatScore } from '@/components/eval/format'
import type { EvalCaseView, RetrievalTrace, RetrievalTraceChunk } from '@/types/eval'

const props = defineProps<{
  runId: string | number
  caseView: EvalCaseView
}>()

const docNames = ref(new Map<string, string>())
const traceChunks = ref<RetrievalTraceChunk[]>([])
const openChunkIds = ref<string[]>([])
/** 成功为原文，null 表示已确认读不到。请求失败不写入，便于再次展开时重试 */
const chunkContentCache = ref(new Map<string, string | null>())
const chunkContentLoading = ref<string[]>([])
const caseTab = ref<'results' | 'pipeline' | 'trace'>('results')
let contentSeq = 0

const groundTruthRows = computed(() => {
  const row = props.caseView
  if (!row.chunkScored) return []
  return buildGroundTruthRows(row.groundTruthChunkIds, row.retrievedChunkIds, traceChunks.value, docNames.value)
})

const retrievedRows = computed(() => {
  const row = props.caseView
  if (!row.chunkScored) return []
  return buildRetrievedChunkRows(row.retrievedChunkIds, row.groundTruthChunkIds, traceChunks.value, docNames.value)
})

const pipelineRetrieved = computed(() => {
  const row = props.caseView
  if (row.chunkScored) return `评测前 5 个 Chunk：${row.retrievedChunkIds?.length ?? 0} 条`
  return `${row.retrievedDocIds.length} documents`
})

const pipelineExpected = computed(() => {
  const row = props.caseView
  if (row.chunkScored) return `标准 Chunk：${row.groundTruthChunkIds?.length ?? 0} 个`
  return `${row.expectedDocIds.length} documents`
})

const metrics = computed(() => {
  const row = props.caseView
  return [
    { label: 'Hit@5', value: row.failed ? 'Failed' : 'Passed' },
    { label: 'Recall@5', value: formatCaseRate(row.recall) },
    { label: 'MRR', value: formatMrr(row.mrr) },
    { label: 'Latency', value: formatLatency(row.latencyMs) },
    { label: 'Top Score', value: formatScore(row.topScore) },
    { label: 'Status', value: row.failed ? 'Failed' : 'Passed' },
  ]
})

// 对比页左右两侧会复用同一个组件实例切换用例，切走后清掉上一条的展开和原文缓存。
watch(() => [String(props.runId), caseKey(props.caseView.id)] as const, () => {
  contentSeq += 1
  caseTab.value = 'results'
  docNames.value = new Map()
  traceChunks.value = []
  openChunkIds.value = []
  chunkContentCache.value = new Map()
  chunkContentLoading.value = []
})

onBeforeUnmount(() => {
  contentSeq += 1
})

function onTraceLoaded(trace: RetrievalTrace | null) {
  docNames.value = collectDocNames(trace)
  traceChunks.value = traceChunkRows(trace)
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

function isChunkOpen(chunkId: string) {
  return openChunkIds.value.includes(chunkId)
}

function isChunkLoading(chunkId: string) {
  return chunkContentLoading.value.includes(chunkId)
}

function chunkPreviewId(chunkId: string) {
  return `chunk-preview-${props.runId}-${props.caseView.id}-${chunkId}`
}

function chunkPreview(chunkId: string) {
  return chunkContentCache.value.get(chunkId)
}

function toggleChunk(chunkId: string) {
  if (isChunkOpen(chunkId)) {
    openChunkIds.value = openChunkIds.value.filter((id) => id !== chunkId)
    return
  }
  openChunkIds.value = [...openChunkIds.value, chunkId]
  void ensureChunkContent(chunkId)
}

function traceChunkContent(chunkId: string) {
  for (const row of traceChunks.value) {
    if (chunkIdText(row.chunkId) !== chunkId) continue
    if (typeof row.content === 'string') return row.content
  }
  return null
}

async function ensureChunkContent(chunkId: string) {
  const seq = contentSeq
  const ids = props.caseView.groundTruthChunkIds ?? []
  if (!ids.includes(chunkId) || props.runId === '' || props.runId == null) return
  const seeded = new Map(chunkContentCache.value)
  for (const id of ids) {
    if (seeded.has(id)) continue
    const traced = traceChunkContent(id)
    if (traced != null) seeded.set(id, traced)
  }
  chunkContentCache.value = seeded
  const missing = ids.filter((id) => !chunkContentCache.value.has(id) && !chunkContentLoading.value.includes(id))
  if (!missing.length) return
  chunkContentLoading.value = [...chunkContentLoading.value, ...missing]
  try {
    const rows = await getEvalChunkContentsApi(props.runId, missing)
    if (seq !== contentSeq) return
    const next = new Map(chunkContentCache.value)
    for (const id of missing) {
      const row = (rows ?? []).find((item) => chunkIdText(item?.chunkId) === id)
      next.set(id, typeof row?.content === 'string' ? row.content : null)
    }
    chunkContentCache.value = next
  } catch {
    // 不写入缓存，收起后再展开可以重试。页面其余部分不依赖这次请求。
  } finally {
    if (seq !== contentSeq) return
    const pending = new Set(missing)
    chunkContentLoading.value = chunkContentLoading.value.filter((id) => !pending.has(id))
  }
}

function inRetrieved(docId: string) {
  return (props.caseView.retrievedDocIds ?? []).map(String).includes(String(docId))
}
</script>

<style lang="scss">
.eval-chunk-toggle {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;

  strong {
    flex: 1;
    min-width: 0;
    color: #1f2d3d;
    font-size: 13px;
    font-weight: 600;
    word-break: break-all;
  }

  &:focus-visible {
    outline: 2px solid #0f4c5c;
    outline-offset: 2px;
    border-radius: 6px;
  }
}

.eval-chunk-toggle__arrow {
  flex-shrink: 0;
  margin-top: 2px;
  color: #0f4c5c;
  transition: transform 0.15s ease;

  &.is-open {
    transform: rotate(90deg);
  }
}

.eval-chunk-preview {
  margin: 8px 0 0 24px;
  padding-top: 8px;
  border-top: 1px solid #e6ecef;
  min-width: 0;
}

.eval-chunk-preview__label {
  margin-bottom: 4px;
  color: #64748b;
  font-size: 12px;
}

.eval-chunk-preview__state {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.6;
}

.eval-chunk-preview__text {
  margin: 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}
</style>
