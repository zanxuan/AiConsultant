<template>
  <section class="eval-page__card">
    <div class="eval-page__list-title">评测概览</div>
    <el-descriptions class="eval-page__desc" :column="2" border>
      <el-descriptions-item label="状态">
        <el-tag :type="statusTagType(detail.status)" size="small" effect="plain">
          {{ statusLabel(detail.status) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="知识库">
        {{ detail.knowledgeName || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="向量模型">
        {{ detail.modelName || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="开始时间">
        {{ detail.createTime || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="结束时间">
        {{ detail.endTime || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="整次耗时">
        {{ formatLatency(detail.durationMs) }}
      </el-descriptions-item>
    </el-descriptions>
    <p v-if="detail.errorMessage" class="eval-page__note eval-page__note--after">
      {{ detail.errorMessage }}
    </p>
  </section>
</template>

<script setup lang="ts">
import type { EvalRunDetail } from '@/types/eval'
import { formatLatency, statusLabel, statusTagType } from './format'

defineProps<{
  detail: EvalRunDetail
}>()
</script>
