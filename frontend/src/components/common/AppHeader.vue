<template>
  <header class="app-header">
    <div class="app-header__title">{{ pageTitle }}</div>
    <div class="app-header__actions">
      <button
        v-if="!userStore.isLoggedIn"
        type="button"
        class="app-header__login"
        @click="userStore.openLoginDialog()"
      >
        登录
      </button>
      <el-dropdown v-else trigger="click" @command="onCommand">
        <span class="app-header__user">
          <el-avatar :size="32">{{ avatarText }}</el-avatar>
          <span class="app-header__name">{{ displayName }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人中心</el-dropdown-item>
            <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const pageTitle = computed(() => (route.meta.title as string) || '')
const displayName = computed(
  () => userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
)
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())

async function onCommand(command: string) {
  if (command === 'profile') {
    router.push({ name: 'profile' })
    return
  }
  if (command === 'logout') {
    await userStore.logout()
    router.push({ name: 'chat' })
  }
}
</script>

<style scoped lang="scss">
.app-header {
  height: 56px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6ecef;

  &__title {
    font-size: 16px;
    font-weight: 600;
    color: #1f2d3d;
  }

  &__login {
    appearance: none;
    border: 1px solid rgba(15, 76, 92, 0.35);
    background: #0f4c5c;
    color: #fff;
    font-size: 13px;
    font-weight: 600;
    padding: 7px 16px;
    border-radius: 999px;
    cursor: pointer;
    transition:
      background 0.15s ease,
      border-color 0.15s ease;

    &:hover {
      background: #136377;
      border-color: #136377;
    }
  }

  &__user {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
  }

  &__name {
    color: #334155;
    font-size: 14px;
  }
}
</style>
