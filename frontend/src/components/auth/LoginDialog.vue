<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="login-dialog-mask"
      @click.self="$emit('update:modelValue', false)"
    >
      <div class="login-dialog__wrap" role="dialog" aria-modal="true" aria-label="登录">
        <button
          type="button"
          class="login-dialog__close"
          aria-label="关闭"
          @click="$emit('update:modelValue', false)"
        >
          ×
        </button>
        <AuthPanel>
          <LoginForm @success="onSuccess" />
        </AuthPanel>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import AuthPanel from './AuthPanel.vue'
import LoginForm from './LoginForm.vue'

defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

function onSuccess() {
  emit('update:modelValue', false)
  emit('success')
}
</script>

<style scoped lang="scss">
.login-dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  padding: 24px;
  background: rgba(0, 0, 0, 0.45);
}

.login-dialog__wrap {
  position: absolute;
  top: 43%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.login-dialog__close {
  position: absolute;
  top: 12px;
  right: 14px;
  z-index: 1;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;

  &:hover {
    background: rgba(15, 76, 92, 0.08);
    color: #0f4c5c;
  }
}
</style>
