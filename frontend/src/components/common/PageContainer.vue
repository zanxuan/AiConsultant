<template>
  <div class="page-container">
    <div v-if="title || $slots.extra" class="page-container__header">
      <div>
        <h2 v-if="title" class="page-container__title">{{ title }}</h2>
        <p v-if="desc" class="page-container__desc">{{ desc }}</p>
      </div>
      <div class="page-container__extra">
        <slot name="extra" />
      </div>
    </div>
    <div class="page-container__body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  desc?: string
}>()
</script>

<style scoped lang="scss">
.page-container {
  &__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 16px;
  }

  &__title {
    margin: 0;
    font-size: 20px;
    color: #1f2d3d;
  }

  &__desc {
    margin: 6px 0 0;
    color: #64748b;
    font-size: 13px;
  }
}
</style>
