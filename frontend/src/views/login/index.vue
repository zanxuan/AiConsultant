<template>
  <LoginForm @success="onSuccess" />
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import LoginForm from '@/components/auth/LoginForm.vue'

const router = useRouter()
const route = useRoute()

function onSuccess() {
  const redirect = (route.query.redirect as string) || '/chat'
  router.replace(redirect)
}
</script>
