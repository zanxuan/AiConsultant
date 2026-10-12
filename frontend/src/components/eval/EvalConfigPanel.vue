<template>
  <section class="eval-page__card">
    <div class="eval-page__list-title">评测配置</div>
    <p class="eval-page__hint">以下为本次评测保存的测试集快照和检索参数。</p>
    <el-descriptions class="eval-page__desc" :column="2" border>
      <el-descriptions-item label="测试集">
        {{ detail.datasetName || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="来源">
        {{ datasetSourceLabel(detail.datasetSource) }}
      </el-descriptions-item>
      <el-descriptions-item label="evalTopK">
        {{ formatConfig(detail.configSnapshot?.evalTopK) }}
      </el-descriptions-item>
      <el-descriptions-item label="retrieverMaxResults">
        {{ formatConfig(detail.configSnapshot?.retrieverMaxResults) }}
      </el-descriptions-item>
      <el-descriptions-item label="retrieverMinScore">
        {{ formatConfig(detail.configSnapshot?.retrieverMinScore) }}
      </el-descriptions-item>
      <el-descriptions-item label="rrfK">
        {{ formatConfig(detail.configSnapshot?.rrfK) }}
      </el-descriptions-item>
    </el-descriptions>
    <div class="eval-page__block">
      <div class="eval-page__block-title">测试集内容（{{ cases.length }}）</div>
      <el-table
        :data="cases"
        class="eval-page__table eval-page__table--static"
        header-cell-class-name="eval-page__table-header"
        row-key="id"
      >
        <el-table-column label="用例 ID" width="100" prop="id" />
        <el-table-column label="问题" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">{{ row.query || '—' }}</template>
        </el-table-column>
        <el-table-column label="期望文档" min-width="220">
          <template #default="{ row }">
            <span class="eval-page__ids">{{ formatDocIds(row.expectedDocIds) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="本次评测没有保存测试集快照" :image-size="80" />
        </template>
      </el-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EvalRunDetail } from '@/types/eval'
import { datasetSourceLabel, formatConfig, formatDocIds } from './format'

const props = defineProps<{
  detail: EvalRunDetail
}>()

const cases = computed(() => props.detail.cases ?? [])
</script>
