<template>
  <div class="chat-input" :class="{ 'is-disabled': disabled }">
    <el-input
      v-model="text"
      type="textarea"
      :autosize="{ minRows: 1, maxRows: 6 }"
      resize="none"
      placeholder="发消息…"
      :disabled="disabled"
      @keydown.enter.exact.prevent="onSend"
    />
    <div class="chat-input__bar">
      <span class="chat-input__tip">Enter 发送 · Shift+Enter 换行</span>
      <button
        type="button"
        class="chat-input__send"
        :disabled="disabled || !text.trim()"
        @click="onSend"
      >
        发送
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  disabled?: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
}>()

const text = ref('')

function onSend() {
  const content = text.value.trim()
  if (!content) return
  emit('send', content)
  text.value = ''
}
</script>

<style scoped lang="scss">
.chat-input {
  padding: 14px 16px 12px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 22px;
  box-shadow:
    0 0 0 1px rgba(15, 76, 92, 0.03),
    0 8px 28px rgba(15, 76, 92, 0.06);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;

  &:focus-within {
    border-color: rgba(15, 76, 92, 0.35);
    box-shadow:
      0 0 0 3px rgba(15, 76, 92, 0.08),
      0 10px 30px rgba(15, 76, 92, 0.08);
  }

  &.is-disabled {
    opacity: 0.72;
  }

  :deep(.el-textarea__inner) {
    box-shadow: none;
    border: none;
    padding: 4px 2px 8px;
    background: transparent;
    font-size: 15px;
    line-height: 1.55;
    color: #0f172a;

    &::placeholder {
      color: #94a3b8;
    }

    &:focus {
      box-shadow: none;
    }
  }

  &__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-top: 4px;
  }

  &__tip {
    font-size: 12px;
    color: #cbd5e1;
  }

  &__send {
    appearance: none;
    border: none;
    background: #0f4c5c;
    color: #fff;
    font-size: 13px;
    font-weight: 600;
    padding: 8px 16px;
    border-radius: 999px;
    cursor: pointer;
    transition:
      background 0.15s ease,
      opacity 0.15s ease;

    &:hover:not(:disabled) {
      background: #0c3d4a;
    }

    &:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }
  }
}
</style>
