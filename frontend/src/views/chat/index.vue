<template>
  <div class="chat-page">
    <ChatWindow />
  </div>
</template>

<script setup lang="ts">
import ChatWindow from '@/components/chat/ChatWindow.vue'
</script>

<style scoped lang="scss">
.chat-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
