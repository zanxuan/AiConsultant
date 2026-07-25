<template>
  <div class="auth-layout">
    <div class="auth-layout__panel">
      <h1 class="auth-layout__brand">{{ appTitle }}</h1>
      <p class="auth-layout__desc">Spring Boot + RAG 企业知识助手</p>
      <RouterView />
    </div>
  </div>
</template>

<script setup lang="ts">
const appTitle = import.meta.env.VITE_APP_TITLE || '企业知识助手'
</script>

<style scoped lang="scss">
.auth-layout {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(14, 116, 144, 0.18), transparent 50%),
    radial-gradient(ellipse at 80% 80%, rgba(15, 76, 92, 0.22), transparent 45%),
    linear-gradient(160deg, #0b1c24 0%, #12303a 45%, #0f4c5c 100%);

  &__panel {
    width: min(420px, 92vw);
    padding: 40px 36px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.96);
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25);
  }

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
