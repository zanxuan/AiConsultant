<template>
  <section class="eval-page__card">
    <div class="eval-page__list-title">{{ label }}</div>
    <el-descriptions class="eval-page__desc" :column="1" border>
      <el-descriptions-item label="名称">{{ detail.datasetName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="运行时间">{{ detail.createTime || '—' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="statusTagType(detail.status)" size="small" effect="plain">
          {{ statusLabel(detail.status) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="知识库">{{ detail.knowledgeName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="向量模型">{{ detail.modelName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="evalTopK">
        {{ formatConfig(detail.configSnapshot?.evalTopK) }}
      </el-descriptions-item>
      <el-descriptions-item label="retrieverMaxResults">
        {{ formatConfig(detail.configSnapshot?.retrieverMaxResults) }}
      </el-descriptions-item>
      <el-descriptions-item label="retrieverMinScore">
        {{ formatConfig(detail.configSnapshot?.retrieverMinScore) }}
      </el-descriptions-item>
      <el-descriptions-item label="retrieverBm25MinScore">
        {{ formatConfig(detail.configSnapshot?.retrieverBm25MinScore) }}
      </el-descriptions-item>
      <el-descriptions-item label="rrfK">
        {{ formatConfig(detail.configSnapshot?.rrfK) }}
      </el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<script setup lang="ts">
import type { EvalRunDetail } from '@/types/eval'
import { formatConfig, statusLabel, statusTagType } from './format'

defineProps<{
  label: string
  detail: EvalRunDetail
}>()
</script>
