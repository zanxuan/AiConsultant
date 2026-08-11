<template>
  <div class="auth-layout">
    <AuthPanel>
      <RouterView />
    </AuthPanel>
  </div>
</template>

<script setup lang="ts">
import AuthPanel from '@/components/auth/AuthPanel.vue'
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
}
</style>
