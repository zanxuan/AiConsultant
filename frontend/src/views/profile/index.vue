<template>
  <PageContainer title="用户信息" desc="查看与修改个人资料">
    <el-card shadow="never" style="max-width: 560px">
      <el-form ref="formRef" :model="form" label-width="88px">
        <el-form-item label="用户 ID">
          <el-input :model-value="userStore.userInfo?.userId" disabled />
        </el-form-item>
        <el-form-item v-if="userStore.userInfo?.username" label="用户名">
          <el-input :model-value="userStore.userInfo?.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="手机" prop="phone">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </PageContainer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageContainer from '@/components/common/PageContainer.vue'
import { useUserStore } from '@/stores/user'
import { updateProfileApi } from '@/api/user'

const userStore = useUserStore()
const saving = ref(false)
const form = reactive({
  nickname: '',
  email: '',
  phone: '',
})

function syncForm() {
  form.nickname = userStore.userInfo?.nickname || ''
  form.email = userStore.userInfo?.email || ''
  form.phone = userStore.userInfo?.phone || ''
}

async function onSave() {
  saving.value = true
  try {
    const profile = await updateProfileApi({ ...form })
    userStore.setUserInfo(profile)
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  if (!userStore.userInfo) {
    await userStore.fetchProfile()
  }
  syncForm()
})
</script>
