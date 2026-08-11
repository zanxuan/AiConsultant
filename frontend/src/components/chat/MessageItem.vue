<template>
  <div class="message-item" :class="`message-item--${roleKey.toLowerCase()}`">
    <div class="message-item__bubble">
      <div
        v-if="isAssistant"
        class="message-item__content message-item__content--md"
        v-html="renderedHtml"
      />
      <div v-else class="message-item__content">{{ message.content || '...' }}</div>
      <a
        v-if="message.showLoginLink"
        href="#"
        class="message-item__login-link"
        @click.prevent="userStore.openLoginDialog()"
      >
        立即登录
      </a>
      <SourcePanel v-if="message.sources?.length" :sources="message.sources" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage } from '@/types/chat'
import { MessageRole } from '@/constants/enum'
import { renderMarkdown } from '@/utils/markdown'
import SourcePanel from './SourcePanel.vue'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  message: ChatMessage
}>()

const userStore = useUserStore()

const roleKey = computed(() => String(props.message.role || '').toUpperCase())

const isAssistant = computed(
  () => roleKey.value === MessageRole.ASSISTANT || roleKey.value === MessageRole.SYSTEM,
)

const renderedHtml = computed(() => {
  const text = props.message.content
  if (!text) return '<p>...</p>'
  return renderMarkdown(text)
})
</script>

<style scoped lang="scss">
.message-item {
  display: flex;
  margin-bottom: 26px;

  &--user {
    justify-content: flex-end;

    .message-item__bubble {
      background: #0f4c5c;
      color: #fff;
    }

    .message-item__content {
      white-space: pre-wrap;
    }
  }

  &--assistant,
  &--system {
    justify-content: flex-start;

    .message-item__bubble {
      background: #f1f5f9;
      color: #1e293b;
    }
  }

  &__bubble {
    max-width: min(100%, 92%);
    padding: 12px 14px;
    border-radius: 12px;
    line-height: 1.6;
    word-break: break-word;
  }

  &__login-link {
    display: inline-block;
    margin-top: 8px;
    font-size: 14px;
    color: #2563eb;
    text-decoration: underline;
    text-underline-offset: 2px;
    cursor: pointer;

    &:hover {
      color: #1d4ed8;
    }
  }

  &__content--md {
    :deep(p) {
      margin: 0 0 0.75em;

      &:last-child {
        margin-bottom: 0;
      }
    }

    :deep(h1),
    :deep(h2),
    :deep(h3),
    :deep(h4) {
      margin: 1em 0 0.5em;
      font-weight: 650;
      line-height: 1.35;
      color: #0f172a;

      &:first-child {
        margin-top: 0;
      }
    }

    :deep(h1) {
      font-size: 1.25em;
    }

    :deep(h2) {
      font-size: 1.15em;
    }

    :deep(h3),
    :deep(h4) {
      font-size: 1.05em;
    }

    :deep(ul),
    :deep(ol) {
      margin: 0.5em 0 0.75em;
      padding-left: 1.4em;
    }

    :deep(li) {
      margin: 0.25em 0;
    }

    :deep(blockquote) {
      margin: 0.75em 0;
      padding: 0.35em 0.85em;
      border-left: 3px solid #0f4c5c;
      color: #475569;
      background: rgba(15, 76, 92, 0.06);
    }

    :deep(a) {
      color: #0f4c5c;
      text-decoration: underline;
      text-underline-offset: 2px;
    }

    :deep(code) {
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 0.9em;
      padding: 0.12em 0.35em;
      border-radius: 4px;
      background: rgba(15, 23, 42, 0.08);
    }

    :deep(pre) {
      margin: 0.75em 0;
      padding: 12px 14px;
      overflow-x: auto;
      border-radius: 8px;
      background: #0f172a;
      color: #e2e8f0;

      code {
        padding: 0;
        background: transparent;
        color: inherit;
        font-size: 0.85em;
      }
    }

    :deep(table) {
      width: 100%;
      margin: 0.75em 0;
      border-collapse: collapse;
      font-size: 0.92em;
    }

    :deep(th),
    :deep(td) {
      padding: 6px 10px;
      border: 1px solid #cbd5e1;
      text-align: left;
    }

    :deep(th) {
      background: rgba(15, 76, 92, 0.08);
      font-weight: 600;
    }

    :deep(hr) {
      margin: 1em 0;
      border: none;
      border-top: 1px solid #cbd5e1;
    }

    :deep(img) {
      max-width: 100%;
      border-radius: 6px;
    }
  }
}
</style>
