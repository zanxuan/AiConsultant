<template>
  <aside class="app-sidebar" :class="{ 'is-collapsed': collapsed }">
    <div class="app-sidebar__brand">
      <div v-show="!collapsed" class="app-sidebar__brand-text" :title="appTitle">
        {{ appTitle }}
      </div>
      <button
        type="button"
        class="app-sidebar__toggle"
        :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
        :title="collapsed ? '展开' : '收起'"
        @click="toggleCollapsed"
      >
        <span class="app-sidebar__toggle-icon" aria-hidden="true" />
      </button>
    </div>

    <el-menu
      :default-active="active"
      :collapse="collapsed"
      :collapse-transition="false"
      router
      background-color="#0f4c5c"
      text-color="rgba(255,255,255,0.82)"
      active-text-color="#ffffff"
      class="app-sidebar__menu"
    >
      <el-menu-item
        v-for="item in menus"
        :key="item.path"
        :index="item.path"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>{{ item.title }}</template>
      </el-menu-item>
    </el-menu>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  ChatDotRound,
  Collection,
  Document,
  Clock,
  User,
} from '@element-plus/icons-vue'
import { SIDEBAR_COLLAPSED_KEY } from '@/constants/storage'

const appTitle = import.meta.env.VITE_APP_TITLE || '企业知识助手'
const route = useRoute()

const menus = [
  { path: '/chat', title: 'AI 聊天', icon: ChatDotRound },
  { path: '/knowledge', title: '知识库', icon: Collection },
  { path: '/document', title: '文档管理', icon: Document },
  { path: '/history', title: '对话历史', icon: Clock },
  { path: '/profile', title: '用户信息', icon: User },
]

const collapsed = ref(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1')

function toggleCollapsed() {
  collapsed.value = !collapsed.value
  localStorage.setItem(SIDEBAR_COLLAPSED_KEY, collapsed.value ? '1' : '0')
}

const active = computed(() => {
  if (route.path.startsWith('/knowledge')) return '/knowledge'
  return route.path
})
</script>

<style scoped lang="scss">
.app-sidebar {
  --sidebar-w: 220px;
  --sidebar-w-collapsed: 64px;

  width: var(--sidebar-w);
  flex-shrink: 0;
  background: #0f4c5c;
  display: flex;
  flex-direction: column;
  transition: width 0.22s ease;

  &.is-collapsed {
    width: var(--sidebar-w-collapsed);

    .app-sidebar__brand {
      justify-content: center;
      padding: 0 8px;
    }
  }

  &__brand {
    height: 56px;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 12px 0 16px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  }

  &__brand-text {
    flex: 1;
    min-width: 0;
    color: #fff;
    font-size: 15px;
    font-weight: 700;
    letter-spacing: 0.02em;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__toggle {
    flex-shrink: 0;
    width: 32px;
    height: 32px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 1px solid rgba(255, 255, 255, 0.22);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.06);
    cursor: pointer;
    transition:
      background 0.15s ease,
      border-color 0.15s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.14);
      border-color: rgba(255, 255, 255, 0.4);
    }
  }

  /* Gemini 风格：左侧竖线的面板图标 */
  &__toggle-icon {
    width: 14px;
    height: 12px;
    border: 1.5px solid rgba(255, 255, 255, 0.9);
    border-radius: 2px;
    position: relative;
    box-sizing: border-box;

    &::before {
      content: '';
      position: absolute;
      left: 3px;
      top: -1.5px;
      bottom: -1.5px;
      width: 1.5px;
      background: rgba(255, 255, 255, 0.9);
    }
  }

  &__menu {
    border-right: none;
    flex: 1;
    width: 100%;
    padding: 8px 0;
    box-sizing: border-box;

    :deep(.el-menu-item) {
      height: 40px;
      line-height: 40px;
      margin: 2px 8px;
      padding: 0 14px !important;
      border-radius: 10px;
      transition: background-color 0.15s ease;

      /* 覆盖 Element Plus 默认直角高亮 */
      &:hover,
      &:focus {
        background-color: rgba(255, 255, 255, 0.12) !important;
      }

      &.is-active {
        background-color: rgba(255, 255, 255, 0.18) !important;
        font-weight: 600;
      }
    }

    /* 折叠态：图标居中，同样圆角块 */
    &.el-menu--collapse {
      :deep(.el-menu-item) {
        margin: 4px 8px;
        padding: 0 !important;
        justify-content: center;
      }

      :deep(.el-tooltip__trigger) {
        justify-content: center;
        border-radius: 10px;
      }
    }
  }
}
</style>
