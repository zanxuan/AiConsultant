<template>
  <div ref="listRef" class="message-list" :class="{ 'is-empty': !messages.length }">
    <div v-if="!messages.length" class="message-list__hero">
      <h1 class="message-list__greeting">有什么我能帮你的吗？</h1>
      <p class="message-list__sub">基于企业知识库，直接提问即可开始对话</p>
    </div>
    <div v-else class="message-list__thread">
      <MessageItem v-for="(msg, index) in messages" :key="msg.id ?? index" :message="msg" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import type { ChatMessage } from '@/types/chat'
import MessageItem from './MessageItem.vue'

const props = defineProps<{
  messages: ChatMessage[]
}>()

const listRef = ref<HTMLElement>()

watch(
  () => props.messages.map((m) => m.content).join(''),
  async () => {
    await nextTick()
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  },
)
</script>

<style scoped lang="scss">
.message-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;

  &.is-empty {
    justify-content: center;
    align-items: center;
  }

  &__hero {
    text-align: center;
    padding: 24px 20px 8px;
    animation: chat-hero-in 0.45s ease both;
  }

  &__greeting {
    margin: 0;
    font-size: clamp(28px, 4vw, 40px);
    font-weight: 700;
    letter-spacing: -0.02em;
    line-height: 1.25;
    color: #0f172a;
  }

  &__sub {
    margin: 14px 0 0;
    font-size: 14px;
    color: #94a3b8;
  }

  &__thread {
    width: min(960px, 100%);
    margin: 0 auto;
    padding: 12px 24px 28px;
    box-sizing: border-box;
  }
}

@keyframes chat-hero-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
