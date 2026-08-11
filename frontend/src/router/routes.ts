import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/layouts/AuthLayout.vue'),
    meta: { public: true, title: '登录' },
    children: [
      {
        path: '',
        name: 'login-page',
        component: () => import('@/views/login/index.vue'),
        meta: { public: true, title: '登录' },
      },
    ],
  },
  {
    path: '/',
    name: 'root',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'chat',
        component: () => import('@/views/chat/index.vue'),
        meta: { title: 'AI 聊天', requiresAuth: false },
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('@/views/knowledge/index.vue'),
        meta: { title: '知识库管理', requiresAuth: false },
      },
      {
        path: 'knowledge/:id',
        name: 'knowledge-detail',
        component: () => import('@/views/knowledge/detail.vue'),
        meta: { title: '知识库详情', requiresAuth: true },
      },
      {
        path: 'document',
        name: 'document',
        component: () => import('@/views/document/index.vue'),
        meta: { title: '文档管理', requiresAuth: false },
      },
      {
        path: 'history',
        name: 'history',
        component: () => import('@/views/history/index.vue'),
        meta: { title: '对话历史', requiresAuth: false },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '用户信息', requiresAuth: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/chat',
  },
]
