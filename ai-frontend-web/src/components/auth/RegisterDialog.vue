<template>
  <el-dialog
    v-model="dialogVisible"
    :close-on-click-modal="false"
    :width="dialogWidth"
    top="3vh"
    class="auth-dialog register-dialog"
    @closed="handleClosed"
  >
    <div class="auth-dialog-inner">
      <div class="auth-header">
        <div class="auth-logo">
          <img src="@/assets/xiaolingbao-logo.svg" alt="灵猞量化" />
          <span class="auth-brand">灵猞量化</span>
        </div>
        <h2>创建账户</h2>
        <p>开启您的智能量化交易之旅</p>
      </div>

      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        class="auth-form"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="email">
          <el-input
            v-model="registerForm.email"
            type="email"
            placeholder="请输入邮箱地址"
            :prefix-icon="Message"
            size="large"
            :disabled="sendCodeLoading || countdown > 0"
          >
            <template #suffix>
              <el-button
                class="send-code-btn"
                text
                :loading="sendCodeLoading"
                :disabled="!isEmailValid || countdown > 0"
                @click="handleSendCode"
              >
                {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </el-button>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item prop="emailCode">
          <el-input
            v-model="registerForm.emailCode"
            placeholder="请输入邮箱验证码"
            prefix-icon="Message"
            size="large"
            maxlength="6"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请确认密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item prop="inviteCode">
          <el-input
            v-model="registerForm.inviteCode"
            placeholder="邀请码（选填）"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="agreeTerms">
          <el-checkbox v-model="registerForm.agreeTerms">
            <span class="terms-text">我已阅读并同意</span>
            <a class="form-link" @click.stop="showTerms">服务条款</a>
            <span class="terms-text">和</span>
            <a class="form-link" @click.stop="showPrivacy">隐私政策</a>
          </el-checkbox>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="auth-submit-btn"
            :loading="authStore.isLoading"
            @click="handleRegister"
          >
            {{ authStore.isLoading ? '注册中...' : '注册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        <span>已有账户？</span>
        <a class="form-link highlight" @click="switchToLogin">立即登录</a>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Message, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import type { RegisterData } from '@/types/auth'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'switch-to-login': []
}>()

const router = useRouter()
const authStore = useAuthStore()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const dialogWidth = computed(() => {
  return window.innerWidth < 480 ? '95%' : '420px'
})

const registerFormRef = ref<FormInstance>()

const registerForm = reactive<RegisterData & { agreeTerms: boolean }>({
  username: '',
  email: '',
  emailCode: '',
  inviteCode: '',
  password: '',
  confirmPassword: '',
  agreeTerms: false
})

// 发送验证码状态
const sendCodeLoading = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const isEmailValid = computed(() => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(registerForm.email)
})

const startCountdown = () => {
  countdown.value = 60
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      if (countdownTimer) clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

const handleSendCode = async () => {
  if (!isEmailValid.value) return
  sendCodeLoading.value = true
  try {
    await authStore.sendEmailCode(registerForm.email)
    startCountdown()
  } catch {
    // 错误已在 store 中处理
  } finally {
    sendCodeLoading.value = false
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3-20个字符之间', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]+$/,
      message: '用户名只能包含字母、数字和下划线',
      trigger: 'blur'
    }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' },
    {
      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]/,
      message: '密码必须包含大小写字母和数字',
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  agreeTerms: [
    {
      required: true,
      message: '请先同意服务条款和隐私政策',
      trigger: 'change'
    }
  ]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return

  try {
    const validation = await registerFormRef.value.validate()
    if (!validation) return

    const { agreeTerms, ...registerData } = registerForm
    await authStore.register(registerData)
    dialogVisible.value = false
    ElMessage.success('注册成功！欢迎加入灵猞量化')
    router.push('/app')
  } catch (error) {
    console.error('Registration failed:', error)
  }
}

const switchToLogin = () => {
  dialogVisible.value = false
  emit('switch-to-login')
}

const showTerms = () => {
  ElMessage.info('服务条款内容')
}

const showPrivacy = () => {
  ElMessage.info('隐私政策内容')
}

const handleClosed = () => {
  registerForm.username = ''
  registerForm.email = ''
  registerForm.emailCode = ''
  registerForm.inviteCode = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  registerForm.agreeTerms = false
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

.send-code-btn {
  padding: 0;
  font-size: 12px;
  color: #7a5cff !important;
  white-space: nowrap;
}

.send-code-btn.is-disabled {
  color: rgba(255, 255, 255, 0.35) !important;
}

.send-code-btn .el-button__loading {
  color: #7a5cff;
}

.auth-header {
  text-align: center;
  margin-bottom: 28px;
}

.auth-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 16px;
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
  margin-bottom: 18px;
}

.terms-text {
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
}

.auth-form :deep(.el-checkbox) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}

.auth-form :deep(.el-checkbox__label) {
  display: inline;
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
}

.auth-form :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: #7a5cff;
  border-color: #7a5cff;
}

.form-link {
  color: rgba(122, 92, 255, 0.9);
  font-size: 13px;
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
