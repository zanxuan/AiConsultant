<template>
  <div class="empty-state">
    <el-empty :description="description" :image-size="imageSize" />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    description?: string
    imageSize?: number
  }>(),
  {
    description: '暂无数据',
    imageSize: 120,
  },
)
</script>

<style scoped lang="scss">
.empty-state {
  padding: 40px 0;
}
</style>
