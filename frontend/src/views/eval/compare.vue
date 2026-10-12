<template>
  <PageContainer title="评测对比" desc="两次历史评测的已保存指标，差值按 B−A 计算">
    <template #extra>
      <el-button @click="goBack">返回评测</el-button>
    </template>

    <div v-loading="loading">
      <p v-if="error" class="eval-page__note">
        {{ error }}
        <el-button v-if="canRetry" link type="primary" @click="load">重试</el-button>
      </p>
      <template v-else-if="runA && runB">
        <section class="eval-page__card">
          <div class="eval-page__list-title">指标对比</div>
          <el-table
            :data="rows"
            class="eval-page__table eval-page__table--static"
            header-cell-class-name="eval-page__table-header"
          >
            <el-table-column label="指标" prop="label" min-width="140" />
            <el-table-column label="评测 A" min-width="140" align="right">
              <template #default="{ row }">{{ row.aText }}</template>
            </el-table-column>
            <el-table-column label="评测 B" min-width="140" align="right">
              <template #default="{ row }">{{ row.bText }}</template>
            </el-table-column>
            <el-table-column label="差值（B−A）" min-width="180" align="right">
              <template #default="{ row }">
                <span :class="deltaClass(row.tone)">{{ row.deltaText }}</span>
              </template>
            </el-table-column>
          </el-table>
        </section>
        <div class="eval-compare__pair">
          <EvalCompareSide label="评测 A" :detail="runA" />
          <EvalCompareSide label="评测 B" :detail="runB" />
        </div>
      </template>
    </div>
  </PageContainer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageContainer from '@/components/common/PageContainer.vue'
import EvalCompareSide from '@/components/eval/EvalCompareSide.vue'
import { buildCompareRows, type CompareTone } from '@/components/eval/compare'
import { getEvalRunDetailApi } from '@/api/eval'
import type { EvalRunDetail } from '@/types/eval'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const canRetry = ref(false)
const runA = ref<EvalRunDetail | null>(null)
const runB = ref<EvalRunDetail | null>(null)
let requestSeq = 0

const idA = computed(() => String(route.query.a ?? '').trim())
const idB = computed(() => String(route.query.b ?? '').trim())
const rows = computed(() => (runA.value && runB.value ? buildCompareRows(runA.value, runB.value) : []))

watch([idA, idB], () => {
  load()
}, { immediate: true })

async function load() {
  const currentA = idA.value
  const currentB = idB.value
  const seq = ++requestSeq
  runA.value = null
  runB.value = null
  canRetry.value = false
  if (!currentA || !currentB) {
    error.value = '对比参数不完整'
    return
  }
  if (currentA === currentB) {
    error.value = '请选择两条不同的评测记录'
    return
  }
  loading.value = true
  error.value = ''
  const [resultA, resultB] = await Promise.allSettled([
    getEvalRunDetailApi(currentA),
    getEvalRunDetailApi(currentB),
  ])
  if (seq !== requestSeq) return
  loading.value = false
  if (resultA.status !== 'fulfilled' || resultB.status !== 'fulfilled' || !resultA.value || !resultB.value) {
    error.value = '评测对比加载失败'
    canRetry.value = true
    return
  }
  runA.value = resultA.value
  runB.value = resultB.value
}

function deltaClass(tone: CompareTone) {
  if (tone === 'better') return 'eval-compare__delta eval-compare__delta--better'
  if (tone === 'worse') return 'eval-compare__delta eval-compare__delta--worse'
  return 'eval-compare__delta'
}

function goBack() {
  router.push({ name: 'eval' })
}
</script>

<style lang="scss">
@import '@/components/eval/eval-shared.scss';
</style>

<style scoped lang="scss">
.eval-compare__pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.eval-compare__delta {
  font-variant-numeric: tabular-nums;

  &--better {
    color: #0f7b4c;
    font-weight: 700;
  }

  &--worse {
    color: #c45656;
    font-weight: 700;
  }
}

@media (max-width: 900px) {
  .eval-compare__pair {
    grid-template-columns: 1fr;
  }
}
</style>
