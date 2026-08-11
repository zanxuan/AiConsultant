<template>
  <el-select
    :model-value="modelValue"
    class="knowledge-select"
    placeholder="选择知识库"
    clearable
    filterable
    :loading="knowledgeStore.loading"
    @visible-change="onVisibleChange"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #prefix>
      <el-icon class="knowledge-select__icon"><Folder /></el-icon>
    </template>
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="item.name"
      :value="item.id"
    />
  </el-select>
</template>

<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { Folder } from '@element-plus/icons-vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { useAuth } from '@/composables/useAuth'

defineProps<{
  modelValue: number | string | null
}>()

defineEmits<{
  'update:modelValue': [value: number | string | null]
}>()

const knowledgeStore = useKnowledgeStore()
const { isLoggedIn } = useAuth()
const options = computed(() => knowledgeStore.list)

onMounted(() => {
  if (isLoggedIn.value) {
    knowledgeStore.fetchList()
  }
})

/** 每次展开下拉都刷新，避免新建知识库后选项仍是旧缓存 */
function onVisibleChange(visible: boolean) {
  if (visible && isLoggedIn.value) {
    knowledgeStore.fetchList()
  }
}
</script>

<style scoped lang="scss">
.knowledge-select {
  width: 100%;
  max-width: 240px;

  &__icon {
    color: #0f4c5c;
  }
}
</style>
