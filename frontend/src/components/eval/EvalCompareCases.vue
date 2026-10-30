<template>
  <div class="eval-compare-cases">
    <section class="eval-compare-cases__pane">
      <div class="eval-compare-cases__bar">
        <div class="eval-compare-cases__heading">
          <div class="eval-compare-cases__label">评测 A</div>
          <div class="eval-compare-cases__name">{{ runA.datasetName || '—' }}</div>
        </div>
        <button v-if="selectedA" type="button" class="eval-case__back" @click="closeCase('a')">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回用例列表</span>
        </button>
      </div>
      <div ref="scrollA" class="eval-compare-cases__scroll">
        <EvalCaseDetail v-if="selectedA" :run-id="runA.id" :case-view="selectedA" />
        <section v-else class="eval-page__card eval-compare-cases__list">
          <div class="eval-page__list-head">
            <div class="eval-page__list-title">用例</div>
            <el-input
              v-model="keywordA"
              class="eval-page__search"
              clearable
              placeholder="搜索 Case..."
            />
          </div>
          <div class="eval-page__filters">
            <el-radio-group v-model="statusA" size="small">
              <el-radio-button label="all">全部 {{ countA.total }}</el-radio-button>
              <el-radio-button label="passed">Passed {{ countA.passed }}</el-radio-button>
              <el-radio-button label="failed">Failed {{ countA.failed }}</el-radio-button>
            </el-radio-group>
          </div>
          <ul v-if="visibleA.length" class="eval-compare-cases__items">
            <li v-for="row in visibleA" :key="row.id">
              <button type="button" class="eval-compare-cases__item" @click="openCase('a', row)">
                <span class="eval-compare-cases__query">{{ row.query }}</span>
                <span class="eval-compare-cases__meta">
                  <el-tag :type="row.failed ? 'danger' : 'success'" size="small" effect="plain">
                    {{ row.failed ? 'Failed' : 'Passed' }}
                  </el-tag>
                  <span>#{{ row.id }}</span>
                  <span>Hit@5 {{ row.failed ? 'Failed' : 'Passed' }}</span>
                  <span>Recall {{ formatCaseRate(row.recall) }}</span>
                  <span>MRR {{ formatMrr(row.mrr) }}</span>
                  <span>{{ formatLatency(row.latencyMs) }}</span>
                </span>
              </button>
            </li>
          </ul>
          <el-empty v-else :description="emptyText(runA, keywordA)" :image-size="88" />
        </section>
      </div>
    </section>

    <section class="eval-compare-cases__pane">
      <div class="eval-compare-cases__bar">
        <div class="eval-compare-cases__heading">
          <div class="eval-compare-cases__label">评测 B</div>
          <div class="eval-compare-cases__name">{{ runB.datasetName || '—' }}</div>
        </div>
        <button v-if="selectedB" type="button" class="eval-case__back" @click="closeCase('b')">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回用例列表</span>
        </button>
      </div>
      <div ref="scrollB" class="eval-compare-cases__scroll">
        <EvalCaseDetail v-if="selectedB" :run-id="runB.id" :case-view="selectedB" />
        <section v-else class="eval-page__card eval-compare-cases__list">
          <div class="eval-page__list-head">
            <div class="eval-page__list-title">用例</div>
            <el-input
              v-model="keywordB"
              class="eval-page__search"
              clearable
              placeholder="搜索 Case..."
            />
          </div>
          <div class="eval-page__filters">
            <el-radio-group v-model="statusB" size="small">
              <el-radio-button label="all">全部 {{ countB.total }}</el-radio-button>
              <el-radio-button label="passed">Passed {{ countB.passed }}</el-radio-button>
              <el-radio-button label="failed">Failed {{ countB.failed }}</el-radio-button>
            </el-radio-group>
          </div>
          <ul v-if="visibleB.length" class="eval-compare-cases__items">
            <li v-for="row in visibleB" :key="row.id">
              <button type="button" class="eval-compare-cases__item" @click="openCase('b', row)">
                <span class="eval-compare-cases__query">{{ row.query }}</span>
                <span class="eval-compare-cases__meta">
                  <el-tag :type="row.failed ? 'danger' : 'success'" size="small" effect="plain">
                    {{ row.failed ? 'Failed' : 'Passed' }}
                  </el-tag>
                  <span>#{{ row.id }}</span>
                  <span>Hit@5 {{ row.failed ? 'Failed' : 'Passed' }}</span>
                  <span>Recall {{ formatCaseRate(row.recall) }}</span>
                  <span>MRR {{ formatMrr(row.mrr) }}</span>
                  <span>{{ formatLatency(row.latencyMs) }}</span>
                </span>
              </button>
            </li>
          </ul>
          <el-empty v-else :description="emptyText(runB, keywordB)" :image-size="88" />
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'
import type { EvalCaseView, EvalRunDetail } from '@/types/eval'
import EvalCaseDetail from './EvalCaseDetail.vue'
import { buildCaseViews, type EvalCaseStatusFilter } from './case-view'
import { formatCaseRate, formatLatency, formatMrr } from './format'

const props = defineProps<{
  runA: EvalRunDetail
  runB: EvalRunDetail
}>()

const keywordA = ref('')
const keywordB = ref('')
const statusA = ref<EvalCaseStatusFilter>('all')
const statusB = ref<EvalCaseStatusFilter>('all')
const selectedA = ref<EvalCaseView | null>(null)
const selectedB = ref<EvalCaseView | null>(null)
const scrollA = ref<HTMLElement | null>(null)
const scrollB = ref<HTMLElement | null>(null)

const casesA = computed(() => buildCaseViews(props.runA))
const casesB = computed(() => buildCaseViews(props.runB))
const countA = computed(() => countOf(casesA.value))
const countB = computed(() => countOf(casesB.value))
const visibleA = computed(() => filterCases(casesA.value, keywordA.value, statusA.value))
const visibleB = computed(() => filterCases(casesB.value, keywordB.value, statusB.value))

function countOf(rows: EvalCaseView[]) {
  const failed = rows.filter((item) => item.failed).length
  return { total: rows.length, failed, passed: rows.length - failed }
}

function filterCases(rows: EvalCaseView[], keyword: string, status: EvalCaseStatusFilter) {
  const q = keyword.trim().toLowerCase()
  return rows.filter((item) => {
    if (status === 'failed' && !item.failed) return false
    if (status === 'passed' && item.failed) return false
    if (q && !item.query.toLowerCase().includes(q)) return false
    return true
  })
}

function emptyText(detail: EvalRunDetail, keyword: string) {
  if (!detail.result) return '该记录没有评测结果'
  if (keyword.trim()) return '没有匹配的用例'
  return '暂无评测 Case'
}

/** 只替换被点击的那一侧，另一侧保持列表或自己已打开的详情。 */
function openCase(side: 'a' | 'b', row: EvalCaseView) {
  if (side === 'a') selectedA.value = row
  else selectedB.value = row
  scrollPane(side)
}

function closeCase(side: 'a' | 'b') {
  if (side === 'a') selectedA.value = null
  else selectedB.value = null
  scrollPane(side)
}

function scrollPane(side: 'a' | 'b') {
  nextTick(() => {
    const pane = side === 'a' ? scrollA.value : scrollB.value
    if (pane) pane.scrollTop = 0
  })
}
</script>

<style scoped lang="scss">
.eval-compare-cases {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  height: calc(100vh - 172px);
  min-height: 520px;
}

.eval-compare-cases__pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.eval-compare-cases__bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-shrink: 0;
  margin-bottom: 12px;
}

.eval-compare-cases__heading {
  min-width: 0;
}

.eval-compare-cases__label {
  color: #1f2d3d;
  font-size: 15px;
  font-weight: 600;
}

.eval-compare-cases__name {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
}

.eval-compare-cases__scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  container-type: inline-size;
  container-name: compare-pane;
}

.eval-compare-cases__list {
  margin-bottom: 0;

  .eval-page__list-head {
    flex-wrap: wrap;
  }

  .eval-page__search {
    width: min(240px, 100%);
  }

  .el-radio-group {
    flex-wrap: wrap;
  }
}

.eval-compare-cases__items {
  margin: 0;
  padding: 0;
  list-style: none;
}

.eval-compare-cases__item {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  width: 100%;
  margin: 0 0 8px;
  padding: 10px 12px;
  border: 1px solid #e6ecef;
  border-radius: 10px;
  background: #fff;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: #0f4c5c;
  }

  &:focus-visible {
    outline: 2px solid #0f4c5c;
    outline-offset: 2px;
  }
}

.eval-compare-cases__query {
  color: #1f2d3d;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-word;
}

.eval-compare-cases__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .eval-compare-cases {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .eval-compare-cases__pane {
    height: 70vh;
  }
}
</style>

<style lang="scss">
@container compare-pane (max-width: 860px) {
  .eval-case__metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .eval-case__split {
    grid-template-columns: 1fr;
  }
}
</style>
