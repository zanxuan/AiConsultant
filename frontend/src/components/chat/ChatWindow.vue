<template>
  <div class="chat-window" :class="{ 'is-empty': !chatStore.messages.length }">
    <header class="chat-window__top">
      <div class="chat-window__top-center">
        <span class="chat-window__session">{{ sessionLabel }}</span>
        <span class="chat-window__hint">AI 生成可能有误，请注意核实</span>
      </div>
      <div class="chat-window__top-actions">
        <KnowledgeSelect v-model="knowledgeId" class="chat-window__kb" />
        <button type="button" class="chat-window__new" @click="onNewChat">新对话</button>
      </div>
    </header>

    <MessageList :messages="chatStore.messages" />

    <div class="chat-window__composer">
      <ChatInput :disabled="chatStore.isStreaming || !knowledgeId" @send="onSend" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import KnowledgeSelect from '@/components/knowledge/KnowledgeSelect.vue'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import { useChatStore } from '@/stores/chat'
import { useKnowledgeStore } from '@/stores/knowledge'
import { useChatStream } from '@/composables/useChatStream'

const chatStore = useChatStore()
const knowledgeStore = useKnowledgeStore()
const { send } = useChatStream()

const knowledgeId = ref<number | string | null>(knowledgeStore.currentId)

const sessionLabel = computed(() =>
  chatStore.messages.length ? '当前对话' : '新对话',
)

onMounted(() => {
  knowledgeStore.fetchList()
  if (knowledgeStore.currentId) {
    knowledgeId.value = knowledgeStore.currentId
  }
})

watch(knowledgeId, (id, prev) => {
  knowledgeStore.setCurrentId(id)
  if (prev != null && id !== prev) {
    chatStore.reset()
  }
})

function onNewChat() {
  chatStore.reset()
}

async function onSend(message: string) {
  if (!knowledgeId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  await send({
    knowledgeId: knowledgeId.value,
    message,
    conversationId: chatStore.conversationId ?? undefined,
  })
}
</script>

<style scoped lang="scss">
.chat-window {
  --chat-max: 960px;
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(ellipse 80% 50% at 50% 0%, rgba(15, 76, 92, 0.05), transparent 60%),
    #fafbfc;

  &__top {
    position: relative;
    flex-shrink: 0;
    display: flex;
    align-items: flex-start;
    justify-content: flex-end;
    padding: 12px 20px 4px;
    min-height: 56px;
  }

  &__top-center {
    position: absolute;
    left: 50%;
    top: 14px;
    transform: translateX(-50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    pointer-events: none;
  }

  &__session {
    font-size: 15px;
    font-weight: 600;
    color: #1e293b;
  }

  &__hint {
    font-size: 11px;
    color: #94a3b8;
  }

  &__top-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    z-index: 1;
  }

  &__kb {
    width: 180px;
  }

  &__new {
    appearance: none;
    border: 1px solid #e2e8f0;
    background: #fff;
    color: #0f4c5c;
    font-size: 13px;
    font-weight: 600;
    padding: 7px 12px;
    border-radius: 999px;
    cursor: pointer;
    transition:
      background 0.15s ease,
      border-color 0.15s ease;

    &:hover {
      background: #f0f7f8;
      border-color: rgba(15, 76, 92, 0.28);
    }
  }

  &__composer {
    flex-shrink: 0;
    width: min(var(--chat-max), calc(100% - 48px));
    margin: 0 auto 20px;
  }
}
</style>
