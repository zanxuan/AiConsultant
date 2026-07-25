<template>
  <el-form ref="formRef" :model="form" :rules="loginRules" label-position="top" @submit.prevent>
    <el-form-item label="用户名" prop="username">
      <el-input v-model="form.username" placeholder="请输入用户名" clearable />
    </el-form-item>
    <el-form-item label="密码" prop="password">
      <el-input
        v-model="form.password"
        type="password"
        show-password
        placeholder="请输入密码"
        @keyup.enter="onSubmit"
      />
    </el-form-item>
    <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">
      登录
    </el-button>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { loginRules } from '@/utils/validate'
import type { LoginForm } from '@/types/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive<LoginForm>({
  username: '',
  password: '',
})

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/chat'
    router.replace(redirect)
  } catch {
    // 错误已在 request 拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-btn {
  width: 100%;
  margin-top: 8px;
}
</style>
