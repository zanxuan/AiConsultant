import type { FormItemRule } from 'element-plus'

export const requiredRule = (message: string): FormItemRule => ({
  required: true,
  message,
  trigger: 'blur',
})

export const loginRules = {
  username: [requiredRule('请输入用户名')],
  password: [
    requiredRule('请输入密码'),
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
}
