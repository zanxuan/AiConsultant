import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'
import { setupPermission } from './permission'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

setupPermission(router)

export default router
