<template>
  <el-dialog
    v-model="dialogVisible"
    :close-on-click-modal="false"
    :width="dialogWidth"
    top="20vh"
    class="auth-dialog forgot-dialog"
    @closed="handleClosed"
  >
    <div class="auth-dialog-inner">
      <div class="auth-header">
        <div class="auth-logo">
          <img src="@/assets/xiaolingbao-logo.svg" alt="灵猞量化" />
          <span class="auth-brand">灵猞量化</span>
        </div>
        <h2>重置密码</h2>
        <p>输入您的邮箱地址，我们将发送重置链接</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="auth-form"
        @keyup.enter="handleSubmit"
      >
        <el-form-item prop="email">
          <el-input
            v-model="form.email"
            type="email"
            placeholder="请输入邮箱地址"
            :prefix-icon="Message"
            size="large"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="auth-submit-btn"
            :loading="loading"
            @click="handleSubmit"
          >
            {{ loading ? '发送中...' : '发送重置邮件' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        <span>想起密码了？</span>
        <a class="form-link highlight" @click="switchToLogin">返回登录</a>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Message } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'switch-to-login': []
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const dialogWidth = computed(() => {
  return window.innerWidth < 480 ? '95%' : '420px'
})

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  email: ''
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    loading.value = true
    await new Promise((resolve) => setTimeout(resolve, 1000))
    ElMessage.success('重置密码邮件已发送，请查收')
    dialogVisible.value = false
  } catch (error) {
    console.error('Form validation failed:', error)
  } finally {
    loading.value = false
  }
}

const switchToLogin = () => {
  dialogVisible.value = false
  emit('switch-to-login')
}

const handleClosed = () => {
  form.email = ''
}
</script>

<style scoped>
.auth-dialog :deep(.el-dialog__header) {
  display: none;
}

.auth-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.auth-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.auth-dialog-inner {
  padding: 40px 32px;
}

.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

.auth-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 20px;
}

.auth-logo img {
  width: 36px;
  height: 36px;
}

.auth-brand {
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #2de2e6 0%, #7a5cff 50%, #00ffa3 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.auth-header h2 {
  font-size: 24px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 8px;
}

.auth-header p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
}

.auth-form {
  margin-bottom: 16px;
}

.auth-form :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.08) !important;
  border: 1px solid rgba(255, 255, 255, 0.12) !important;
  border-radius: 10px !important;
  box-shadow: none !important;
  padding: 4px 12px !important;
  transition: all 0.3s ease;
}

.auth-form :deep(.el-input__wrapper:hover) {
  border-color: rgba(122, 92, 255, 0.5) !important;
  background: rgba(255, 255, 255, 0.12) !important;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  border-color: #7a5cff !important;
  background: rgba(255, 255, 255, 0.12) !important;
  box-shadow: 0 0 0 3px rgba(122, 92, 255, 0.15) !important;
}

.auth-form :deep(.el-input__inner) {
  color: #ffffff !important;
  height: 44px !important;
}

.auth-form :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.4) !important;
}

.auth-form :deep(.el-input__prefix) {
  color: rgba(255, 255, 255, 0.4) !important;
}

.auth-form :deep(.el-form-item) {
  margin-bottom: 20px;
}

.form-link {
  color: rgba(122, 92, 255, 0.9);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.3s ease;
  text-decoration: none;
}

.form-link:hover {
  color: #7a5cff;
}

.form-link.highlight {
  font-weight: 600;
  color: #7a5cff;
}

.auth-submit-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 10px !important;
  background: linear-gradient(135deg, #7a5cff 0%, #2de2e6 100%) !important;
  border: none !important;
  transition: all 0.3s ease !important;
  letter-spacing: 1px;
}

.auth-submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(122, 92, 255, 0.4) !important;
}

.auth-submit-btn:active {
  transform: translateY(0);
}

.auth-submit-btn.is-loading {
  opacity: 0.8;
}

.auth-footer {
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
  font-size: 14px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.auth-footer .form-link {
  margin-left: 4px;
}
</style>
