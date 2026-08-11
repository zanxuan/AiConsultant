<template>
  <PageContainer title="对话历史" desc="查看与继续历史会话">
    <HistoryList
      :list="historyStore.list"
      :loading="historyStore.loading"
      @select="onSelect"
      @delete="onDelete"
    />
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import HistoryList from '@/components/history/HistoryList.vue'
import { useHistoryStore } from '@/stores/history'
import { useChatStore } from '@/stores/chat'
import { useKnowledgeStore } from '@/stores/knowledge'
import { useAuth } from '@/composables/useAuth'
import { getConversationMessagesApi, getHistoryDetailApi } from '@/api/history'

const router = useRouter()
const historyStore = useHistoryStore()
const chatStore = useChatStore()
const knowledgeStore = useKnowledgeStore()
const { hasToken } = useAuth()

async function onSelect(id: number) {
  if (!hasToken()) {
    ElMessage.warning('请先登录后再查看对话')
    return
  }
  const [detail, messages] = await Promise.all([
    getHistoryDetailApi(id),
    getConversationMessagesApi(id),
  ])
  chatStore.setConversationId(detail.id)
  // setMessages 内会规范化角色/字段；兼容 messages 接口或详情内嵌 messages
  chatStore.setMessages(messages ?? detail.messages ?? [])
  if (detail.knowledgeId != null) {
    knowledgeStore.setCurrentId(detail.knowledgeId)
  }
  router.push({ name: 'chat' })
}

async function onDelete(id: number) {
  if (!hasToken()) {
    ElMessage.warning('请先登录后再操作')
    return
  }
  await ElMessageBox.confirm('确认删除该对话？', '提示', { type: 'warning' })
  await historyStore.remove(id)
  if (chatStore.conversationId === id) {
    chatStore.reset()
  }
  ElMessage.success('已删除')
}

onMounted(() => {
  if (!hasToken()) {
    historyStore.list = []
    return
  }
  historyStore.fetchList()
})
</script>
