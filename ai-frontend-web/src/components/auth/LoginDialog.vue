<template>
  <el-dialog
    v-model="dialogVisible"
    :close-on-click-modal="false"
    :width="dialogWidth"
    top="5vh"
    class="auth-dialog login-dialog"
    @closed="handleClosed"
  >
    <div class="auth-dialog-inner">
      <div class="auth-header">
        <div class="auth-logo">
          <img src="@/assets/xiaolingbao-logo.svg" alt="灵猞量化" />
          <span class="auth-brand">灵猞量化</span>
        </div>
        <h2>欢迎回来</h2>
        <p>登录您的账户，开始量化交易之旅</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="auth-form"
        @keyup.enter="handleLogin"
      >
        <!-- 登录方式切换 -->
        <el-form-item>
          <el-radio-group v-model="loginMode" class="login-mode-switch" size="large">
            <el-radio-button value="password">密码登录</el-radio-button>
            <el-radio-button value="email">验证码登录</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- 密码登录 -->
        <template v-if="loginMode === 'password'">
          <el-form-item prop="account">
            <el-input
              v-model="loginForm.account"
              placeholder="请输入用户名或邮箱"
              :prefix-icon="User"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              size="large"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <div class="form-options">
              <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
              <a class="form-link" @click="switchToForgotPassword">忘记密码？</a>
            </div>
          </el-form-item>
        </template>

        <!-- 验证码登录 -->
        <template v-if="loginMode === 'email'">
          <el-form-item prop="email">
            <el-input
              v-model="loginForm.email"
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
              v-model="loginForm.emailCode"
              placeholder="请输入邮箱验证码"
              :prefix-icon="Message"
              size="large"
              maxlength="6"
            />
          </el-form-item>
        </template>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="auth-submit-btn"
            :loading="authStore.isLoading"
            @click="handleLogin"
          >
            {{ authStore.isLoading ? '登录中...' : '登录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        <span>还没有账户？</span>
        <a class="form-link highlight" @click="switchToRegister">立即注册</a>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Message } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import type { LoginData } from '@/types/auth'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'switch-to-register': []
  'switch-to-forgot-password': []
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

const loginFormRef = ref<FormInstance>()

const loginMode = ref<'password' | 'email'>('password')

const loginForm = reactive({
  account: '',
  password: '',
  rememberMe: false,
  email: '',
  emailCode: ''
})

// 发送验证码状态
const sendCodeLoading = ref(false)
const countdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

const isEmailValid = computed(() => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(loginForm.email)
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
    await authStore.sendEmailCode(loginForm.email)
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

const loginRules: FormRules = {
  account: [
    { required: true, message: '请输入用户名或邮箱', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  emailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()

    if (loginMode.value === 'password') {
      const isEmail = loginForm.account.includes('@')
      const loginData: LoginData = {
        password: loginForm.password,
        rememberMe: loginForm.rememberMe
      }
      if (isEmail) {
        loginData.email = loginForm.account
      } else {
        loginData.username = loginForm.account
      }
      await authStore.login(loginData)
    } else {
      await authStore.loginByEmail({
        email: loginForm.email,
        emailCode: loginForm.emailCode
      })
    }

    dialogVisible.value = false
    router.push('/app')
  } catch (error) {
    console.error('Login failed:', error)
  }
}

const switchToRegister = () => {
  dialogVisible.value = false
  emit('switch-to-register')
}

const switchToForgotPassword = () => {
  dialogVisible.value = false
  emit('switch-to-forgot-password')
}

const handleClosed = () => {
  loginForm.account = ''
  loginForm.password = ''
  loginForm.rememberMe = false
  loginForm.email = ''
  loginForm.emailCode = ''
  loginMode.value = 'password'
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

/* 登录方式切换样式 */
.login-mode-switch {
  display: flex !important;
  justify-content: center !important;
  width: 100% !important;
}

.login-mode-switch :deep(.el-radio-button__inner) {
  justify-content: center;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.6);
  padding: 8px 20px;
  font-size: 14px;
}

.login-mode-switch :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: 10px 0 0 10px;
}

.login-mode-switch :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0 10px 10px 0;
}

.login-mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: linear-gradient(135deg, #7a5cff 0%, #2de2e6 100%);
  border-color: transparent;
  color: #fff;
  box-shadow: none;
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

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.form-options :deep(.el-checkbox__label) {
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
}

.form-options :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
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
