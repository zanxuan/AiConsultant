<template>
  <section class="eval-page__card">
    <div class="eval-page__list-title">评测指标</div>
    <div class="eval-page__metrics">
      <div class="eval-page__metric">
        <div class="eval-page__metric-value">{{ formatCount(metrics.total) }}</div>
        <div class="eval-page__metric-label">Test Cases</div>
      </div>
      <div class="eval-page__metric">
        <div class="eval-page__metric-value">{{ formatRate(metrics.hitRate) }}</div>
        <div class="eval-page__metric-label">Hit@5</div>
      </div>
      <div class="eval-page__metric">
        <div class="eval-page__metric-value">{{ formatRate(metrics.recall) }}</div>
        <div class="eval-page__metric-label">Recall@5</div>
      </div>
      <div class="eval-page__metric">
        <div class="eval-page__metric-value">{{ formatMrr(metrics.mrr) }}</div>
        <div class="eval-page__metric-label">MRR</div>
      </div>
      <div class="eval-page__metric">
        <div class="eval-page__metric-value">{{ formatLatency(metrics.avgLatencyMs) }}</div>
        <div class="eval-page__metric-label">Avg Latency</div>
      </div>
      <div class="eval-page__metric">
        <div class="eval-page__metric-value">{{ formatLatency(metrics.p95LatencyMs) }}</div>
        <div class="eval-page__metric-label">P95 Latency</div>
      </div>
      <div class="eval-page__metric">
        <div class="eval-page__metric-value">{{ formatCount(metrics.failedCount) }}</div>
        <div class="eval-page__metric-label">失败用例数</div>
      </div>
      <div class="eval-page__metric">
        <div class="eval-page__metric-value">{{ formatLatency(metrics.durationMs) }}</div>
        <div class="eval-page__metric-label">整次耗时</div>
      </div>
    </div>
    <p v-if="!detail.result && !detail.errorMessage" class="eval-page__note eval-page__note--after">
      该记录没有评测结果
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EvalRunDetail } from '@/types/eval'
import { formatCount, formatLatency, formatMrr, formatRate, savedMetrics } from './format'

const props = defineProps<{
  detail: EvalRunDetail
}>()

const metrics = computed(() => savedMetrics(props.detail))
</script>
