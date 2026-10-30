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

      <EvalCaseDetail v-else-if="caseView" :run-id="runId" :case-view="caseView" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getEvalRunDetailApi } from '@/api/eval'
import EvalCaseDetail from '@/components/eval/EvalCaseDetail.vue'
import { resolveCaseView } from '@/components/eval/case-view'
import {
  clearReturnToEvalList,
  isEvalListPath,
  markReturnToEvalList,
  writeMainScrollTop,
} from '@/components/eval/list-state'
import type { EvalCaseView } from '@/types/eval'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const missing = ref(false)
const missingText = ref('未找到该用例')
const caseView = ref<EvalCaseView | null>(null)
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

watch(() => [runId.value, caseId.value] as const, () => {
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

</script>

<style lang="scss">
@import '@/components/eval/eval-shared.scss';
</style>
