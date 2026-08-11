<template>
  <div class="auth-panel">
    <h1 class="auth-panel__brand">{{ appTitle }}</h1>
    <p class="auth-panel__desc">Spring Boot + RAG 企业知识助手</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
const appTitle = import.meta.env.VITE_APP_TITLE || '企业知识助手'
</script>

<style scoped lang="scss">
.auth-panel {
  width: min(420px, 92vw);
  padding: 40px 36px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);

  &__brand {
    margin: 0;
    font-size: 28px;
    font-weight: 700;
    color: #0f4c5c;
    letter-spacing: 0.02em;
  }

  &__desc {
    margin: 8px 0 28px;
    color: #5b6b73;
    font-size: 14px;
  }
}
</style>
