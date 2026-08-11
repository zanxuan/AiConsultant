<template>
  <div class="main-layout">
    <AppSidebar />
    <div class="main-layout__body">
      <AppHeader v-if="!isChat" />
      <main class="main-layout__content" :class="{ 'is-chat': isChat }">
        <RouterView />
      </main>
    </div>

    <LoginDialog
      :model-value="userStore.loginDialogVisible"
      @update:model-value="onDialogVisible"
      @success="onLoginSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppSidebar from '@/components/common/AppSidebar.vue'
import AppHeader from '@/components/common/AppHeader.vue'
import LoginDialog from '@/components/auth/LoginDialog.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isChat = computed(() => route.name === 'chat')

function onDialogVisible(visible: boolean) {
  if (visible) userStore.openLoginDialog(userStore.loginRedirect)
  else userStore.closeLoginDialog()
}

function onLoginSuccess() {
  const redirect = userStore.loginRedirect
  userStore.closeLoginDialog()
  userStore.loginRedirect = null
  if (redirect && redirect !== '/chat' && redirect !== route.fullPath) {
    router.push(redirect)
  }
}
</script>

<style scoped lang="scss">
.main-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  background: #f3f6f8;

  &__body {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
    min-height: 0;
    overflow: hidden;
  }

  &__content {
    flex: 1;
    padding: 20px 24px;
    overflow: auto;
    min-height: 0;

    &.is-chat {
      padding: 0;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      background: #fafbfc;
    }
  }
}
</style>
