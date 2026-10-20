<template>
  <section class="eval-drawer__section eval-trace">
    <h3>检索链路</h3>

    <p v-if="loading" class="eval-drawer__empty">正在加载检索链路...</p>
    <p v-else-if="error" class="eval-trace__error">
      检索链路加载失败，不影响本次评测结果。{{ error }}
    </p>
    <p v-else-if="!trace || !trace.saved" class="eval-drawer__note">
      该次评测未保存检索链路
    </p>

    <template v-else>
      <p class="eval-trace__banner" :class="bannerClass">{{ bannerText }}</p>
      <p class="eval-trace__counts">
        向量召回 {{ countText(trace.vectorHitCount) }} 条，BM25 召回 {{ countText(trace.bm25HitCount) }} 条
      </p>
      <p v-if="trace.bm25Query" class="eval-trace__query">{{ trace.bm25Query }}</p>

      <h4>期望文档</h4>
      <ul v-if="trace.expectedHits?.length" class="eval-drawer__docs">
        <li
          v-for="hit in trace.expectedHits"
          :key="hit.expectedDocId"
          class="eval-drawer__doc eval-trace__hit"
          :class="stageOf(hit) === 'IN_EVAL_TOPK' ? 'is-ok' : 'is-miss'"
        >
          <div class="eval-drawer__doc-head">
            <strong>Document {{ hit.expectedDocId }}</strong>
            <el-tag size="small" effect="plain" :type="stageOf(hit) === 'IN_EVAL_TOPK' ? 'success' : 'danger'">
              {{ stageLabel(stageOf(hit)) }}
            </el-tag>
          </div>
          <div class="eval-drawer__meta">
            向量 {{ rankText(hit.vectorRank) }}
            · BM25 {{ rankText(hit.bm25Rank) }}
            · RRF {{ rankText(hit.rrfRank) }}
            · Hybrid {{ rankText(hit.finalRank) }}
            · 评测 {{ rankText(hit.evalDocRank) }}
          </div>
          <div v-if="hit.filtered" class="eval-drawer__meta">过滤原因：{{ hit.filterReason || '已过滤' }}</div>
        </li>
      </ul>
      <p v-else class="eval-drawer__empty">无期望文档</p>

      <h4>1. 向量召回</h4>
      <p v-if="!trace.vectorHitCount" class="eval-drawer__empty">向量召回 0 条。</p>
      <TraceList v-else :rows="trace.vectorHits" :expected-ids="expectedIds" score-kind="vector" />

      <h4>2. BM25 召回</h4>
      <p v-if="!trace.bm25HitCount" class="eval-drawer__empty">BM25 召回 0 条，没有额外候选。</p>
      <TraceList v-else :rows="trace.bm25Hits" :expected-ids="expectedIds" score-kind="bm25" />

      <h4>3. RRF 融合及过滤</h4>
      <TraceList :rows="trace.rrfHits" :expected-ids="expectedIds" score-kind="rrf" show-filter />
      <p v-if="!trace.rrfHits?.length" class="eval-drawer__empty">没有进入融合的候选。</p>
      <p v-if="trace.afterFilter?.length" class="eval-trace__sub">
        过滤后 {{ trace.afterFilter.length }} 条
      </p>

      <h4>4. 最终 Hybrid TopK</h4>
      <TraceList :rows="trace.finalTopK" :expected-ids="expectedIds" score-kind="final" />
      <p v-if="!trace.finalTopK?.length" class="eval-drawer__empty">最终 Hybrid TopK 为空。</p>

      <h4>5. 最终文档 TopK</h4>
      <ul v-if="trace.evalDocs?.length" class="eval-drawer__docs">
        <li
          v-for="doc in trace.evalDocs"
          :key="`${doc.rank}-${doc.documentId}`"
          class="eval-drawer__doc"
          :class="{ 'is-expected': doc.expected }"
        >
          <div class="eval-drawer__doc-head">
            <span>#{{ doc.rank }}</span>
            <strong>{{ doc.documentName || `Document ${doc.documentId}` }}</strong>
            <el-tag v-if="doc.expected" size="small" effect="plain" type="success">期望文档</el-tag>
          </div>
        </li>
      </ul>
      <p v-else class="eval-drawer__empty">去重后的评测文档 TopK 为空。</p>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getEvalRetrievalTraceApi } from '@/api/eval'
import type { ExpectedDocHit, RetrievalTrace } from '@/types/eval'
import TraceList from './EvalRetrievalTraceList.vue'

const props = defineProps<{
  runId?: number | string | null
  caseId?: number | string | null
}>()

const emit = defineEmits<{
  loaded: [trace: RetrievalTrace | null]
}>()

const loading = ref(false)
const error = ref('')
const trace = ref<RetrievalTrace | null>(null)
let requestSeq = 0

const expectedIds = computed(() => (trace.value?.expectedHits ?? []).map((item) => String(item.expectedDocId)))

const bannerClass = computed(() => (trace.value?.missStage === 'IN_EVAL_TOPK' ? 'is-ok' : 'is-miss'))

const bannerText = computed(() => {
  switch (trace.value?.missStage) {
    case 'IN_EVAL_TOPK':
      return '期望文档已进入评测 TopK，可查看各阶段排名。'
    case 'NOT_RECALLED':
      return '期望文档未进入向量召回和 BM25 召回，因此没有 RRF 排名。'
    case 'FILTERED':
      return '期望文档进入融合候选后被过滤。'
    case 'NOT_IN_FINAL_TOPK':
      return '期望文档通过过滤，但未进入最终 Hybrid TopK。'
    case 'NOT_IN_EVAL_TOPK':
      return '期望文档进入了 Hybrid TopK，但去重后未进入评测文档 TopK。'
    case 'MIXED':
      return '多个期望文档停在不同阶段，请逐条查看。'
    case 'NO_EXPECTED':
      return '该用例没有期望文档。'
    default:
      return '已保存本次检索链路。'
  }
})

watch(() => [props.runId, props.caseId] as const, () => {
  load()
}, { immediate: true })

async function load() {
  const seq = ++requestSeq
  error.value = ''
  trace.value = null
  emit('loaded', null)
  if (props.runId == null || props.runId === '' || props.caseId == null || props.caseId === '') {
    trace.value = { saved: false }
    emit('loaded', trace.value)
    return
  }
  loading.value = true
  try {
    const data = await getEvalRetrievalTraceApi(props.runId, props.caseId)
    if (seq !== requestSeq) return
    trace.value = data
    emit('loaded', data ?? null)
  } catch (caught) {
    if (seq !== requestSeq) return
    error.value = caught instanceof Error ? caught.message : '请稍后重试'
    emit('loaded', null)
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function stageOf(hit: ExpectedDocHit) {
  if (hit.evalDocRank != null) return 'IN_EVAL_TOPK'
  if (hit.vectorRank == null && hit.bm25Rank == null && hit.rrfRank == null) return 'NOT_RECALLED'
  if (hit.filtered) return 'FILTERED'
  if (hit.finalRank == null) return 'NOT_IN_FINAL_TOPK'
  return 'NOT_IN_EVAL_TOPK'
}

function stageLabel(stage: string) {
  switch (stage) {
    case 'IN_EVAL_TOPK':
      return '进入评测 TopK'
    case 'NOT_RECALLED':
      return '未召回'
    case 'FILTERED':
      return '已过滤'
    case 'NOT_IN_FINAL_TOPK':
      return '未进 Hybrid TopK'
    case 'NOT_IN_EVAL_TOPK':
      return '未进评测 TopK'
    default:
      return stage
  }
}

function rankText(rank?: number | null) {
  return rank == null ? '—' : `#${rank}`
}

function countText(count?: number | null) {
  return count == null ? '—' : String(count)
}
</script>
