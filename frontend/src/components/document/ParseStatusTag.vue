<template>
  <el-tag :type="tagType" effect="light" class="parse-status-tag" round>
    <span class="parse-status-tag__dot" :class="`is-${status}`" />
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DocumentStatus, DocumentStatusLabel } from '@/constants/enum'

const props = defineProps<{
  status: DocumentStatus
}>()

const label = computed(() => DocumentStatusLabel[props.status] || props.status)

const tagType = computed(() => {
  switch (props.status) {
    case DocumentStatus.READY:
      return 'success'
    case DocumentStatus.FAILED:
      return 'danger'
    case DocumentStatus.PARSING:
    case DocumentStatus.EMBEDDING:
      return 'warning'
    case DocumentStatus.UPLOADING:
    default:
      return 'info'
  }
})
</script>

<style scoped lang="scss">
.parse-status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;

  &__dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: currentColor;
    opacity: 0.85;

    &.is-READY {
      background: #16a34a;
    }

    &.is-FAILED {
      background: #dc2626;
    }

    &.is-PARSING,
    &.is-EMBEDDING {
      background: #d97706;
    }

    &.is-UPLOADING {
      background: #64748b;
    }
  }
}
</style>
