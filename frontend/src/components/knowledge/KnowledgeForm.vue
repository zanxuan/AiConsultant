<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
    <el-form-item label="名称" prop="name">
      <el-input v-model="form.name" maxlength="50" show-word-limit placeholder="知识库名称" />
    </el-form-item>
    <el-form-item label="描述" prop="description">
      <el-input
        v-model="form.description"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="可选描述"
      />
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { KnowledgeForm } from '@/types/knowledge'
import { requiredRule } from '@/utils/validate'

const props = defineProps<{
  modelValue?: KnowledgeForm
}>()

const formRef = ref<FormInstance>()
const form = reactive<KnowledgeForm>({
  name: '',
  description: '',
})

watch(
  () => props.modelValue,
  (val) => {
    form.name = val?.name ?? ''
    form.description = val?.description ?? ''
  },
  { immediate: true, deep: true },
)

const rules: FormRules = {
  name: [requiredRule('请输入知识库名称')],
}

async function validate() {
  return formRef.value?.validate()
}

function getForm(): KnowledgeForm {
  return { ...form }
}

defineExpose({ validate, getForm })
</script>
