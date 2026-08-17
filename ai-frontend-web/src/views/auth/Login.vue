<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>量化交易系统</h1>
        <p>欢迎回来，请登录您的账户</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
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
              prefix-icon="User"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              size="large"
              show-password
            />
          </el-form-item>

          <el-form-item>
            <div class="form-options">
              <el-checkbox v-model="loginForm.rememberMe"> 记住我 </el-checkbox>
              <router-link to="/forgot-password" class="forgot-password">
                忘记密码？
              </router-link>
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
            class="login-button"
            :loading="authStore.isLoading"
            @click="handleLogin"
          >
            {{ authStore.isLoading ? "登录中..." : "登录" }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <p>
          还没有账户？
          <router-link to="/register" class="register-link">
            立即注册
          </router-link>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { User, Lock, Message } from "@element-plus/icons-vue";
import { useAuthStore } from "@/stores/auth";
import type { LoginData } from "@/types/auth";

const router = useRouter();
const authStore = useAuthStore();

// 表单引用
const loginFormRef = ref<FormInstance>();

// 登录方式
const loginMode = ref<'password' | 'email'>('password');

// 登录表单数据
const loginForm = reactive({
  account: "",
  password: "",
  rememberMe: false,
  email: "",
  emailCode: ""
});

// 发送验证码状态
const sendCodeLoading = ref(false);
const countdown = ref(0);
let countdownTimer: ReturnType<typeof setInterval> | null = null;

const isEmailValid = computed(() => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(loginForm.email);
});

const startCountdown = () => {
  countdown.value = 60;
  if (countdownTimer) clearInterval(countdownTimer);
  countdownTimer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      if (countdownTimer) clearInterval(countdownTimer);
      countdownTimer = null;
    }
  }, 1000);
};

const handleSendCode = async () => {
  if (!isEmailValid.value) return;
  sendCodeLoading.value = true;
  try {
    await authStore.sendEmailCode(loginForm.email);
    startCountdown();
  } catch {
    // 错误已在 store 中处理
  } finally {
    sendCodeLoading.value = false;
  }
};

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer);
});

// 表单验证规则
const loginRules: FormRules = {
  account: [
    { required: true, message: "请输入用户名或邮箱", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, message: "密码长度至少6位", trigger: "blur" },
  ],
  email: [
    { required: true, message: "请输入邮箱地址", trigger: "blur" },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  emailCode: [
    { required: true, message: "请输入邮箱验证码", trigger: "blur" },
    { len: 6, message: "验证码为6位数字", trigger: "blur" },
  ],
};

// 处理登录
const handleLogin = async () => {
  if (!loginFormRef.value) return;

  try {
    await loginFormRef.value.validate();

    if (loginMode.value === 'password') {
      const isEmail = loginForm.account.includes("@");
      const loginData: LoginData = {
        password: loginForm.password,
        rememberMe: loginForm.rememberMe,
      };
      if (isEmail) {
        loginData.email = loginForm.account;
      } else {
        loginData.username = loginForm.account;
      }
      await authStore.login(loginData);
    } else {
      await authStore.loginByEmail({
        email: loginForm.email,
        emailCode: loginForm.emailCode,
      });
    }

    await router.push("/app");
  } catch (error) {
    console.error("Login failed:", error);
  }
};
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient-primary);
  padding: 20px;
  position: relative;
}

.login-container::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background:
    radial-gradient(
      circle at 10% 20%,
      rgba(0, 122, 255, 0.1) 0%,
      transparent 50%
    ),
    radial-gradient(
      circle at 90% 80%,
      rgba(0, 212, 170, 0.1) 0%,
      transparent 50%
    );
  pointer-events: none;
}

.login-box {
  background: var(--glass-bg) !important;
  backdrop-filter: blur(20px) !important;
  border: 1px solid var(--glass-border) !important;
  border-radius: var(--radius-xl) !important;
  box-shadow: var(--shadow-premium-lg) !important;
  padding: 48px;
  width: 100%;
  max-width: 440px;
  position: relative;
  z-index: 1;
  transition: all var(--transition-smooth) var(--transition-spring);
}

.login-box::before {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.1),
    transparent
  );
  transition: left 0.5s ease;
}

.login-box:hover::before {
  left: 100%;
}

.login-box:hover {
  transform: translateY(-4px) !important;
  box-shadow: var(--shadow-premium-xl) !important;
  border-color: var(--border-glow-primary) !important;
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-header h1 {
  font-size: 32px;
  color: var(--text-primary);
  margin-bottom: 12px;
  font-weight: 700;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.login-header p {
  color: var(--text-secondary);
  font-size: var(--font-base);
  margin: 0;
  font-weight: var(--font-normal);
}

.login-form {
  margin-bottom: 24px;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.forgot-password {
  color: var(--btn-primary);
  text-decoration: none;
  font-size: var(--font-sm);
  font-weight: var(--font-medium);
  transition: all var(--transition-smooth) var(--transition-spring);
}

.forgot-password:hover {
  color: var(--btn-primary-hover);
  text-shadow: 0 0 8px rgba(0, 212, 170, 0.4);
}

.login-button {
  width: 100%;
  height: 48px;
  font-size: var(--font-lg);
  font-weight: var(--font-semibold);
  background: var(--btn-primary) !important;
  border-color: var(--btn-primary) !important;
  border-radius: var(--radius-lg) !important;
  transition: all var(--transition-smooth) var(--transition-spring);
  position: relative;
  overflow: hidden;
}

.login-button::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  background: radial-gradient(
    circle,
    rgba(255, 255, 255, 0.3) 0%,
    transparent 70%
  );
  transition: all 0.5s ease;
  transform: translate(-50%, -50%);
}

.login-button:hover::before {
  width: 300px;
  height: 300px;
}

.login-button:hover {
  background: var(--btn-primary-hover) !important;
  border-color: var(--btn-primary-hover) !important;
  box-shadow: var(--glow-primary) !important;
  transform: translateY(-2px);
}

.login-footer {
  text-align: center;
  color: var(--text-secondary);
  font-size: var(--font-base);
  margin-top: 24px;
}

.register-link {
  color: var(--btn-primary);
  text-decoration: none;
  font-weight: var(--font-semibold);
  transition: all var(--transition-smooth) var(--transition-spring);
}

.register-link:hover {
  color: var(--btn-primary-hover);
  text-shadow: 0 0 8px rgba(0, 212, 170, 0.4);
}

/* 表单样式 */
.el-form-item__label {
  color: var(--text-secondary) !important;
  font-weight: var(--font-medium) !important;
  font-size: var(--font-sm) !important;
}

.el-input__wrapper {
  background: var(--input-bg) !important;
  border-color: var(--input-border) !important;
  border-radius: var(--radius-md) !important;
  transition: all var(--transition-smooth) var(--transition-spring) !important;
}

.el-input__wrapper:hover {
  border-color: var(--input-hover) !important;
}

.el-input__wrapper.is-focus {
  border-color: var(--input-focus) !important;
  box-shadow: 0 0 0 2px var(--glow-primary) !important;
}

.el-input__inner {
  color: var(--input-text) !important;
  font-size: var(--font-base) !important;
}

/* 复选框样式 */
.el-checkbox__label {
  color: var(--text-secondary) !important;
  font-size: var(--font-base) !important;
}

.el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: var(--btn-primary) !important;
  border-color: var(--btn-primary) !important;
}

.el-checkbox__inner:hover {
  border-color: var(--btn-primary) !important;
}

.el-checkbox__inner {
  background: var(--input-bg) !important;
  border-color: var(--input-border) !important;
  border-radius: var(--radius-sm) !important;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-container {
    padding: 12px;
  }

  .login-box {
    padding: 32px 24px;
    max-width: 100%;
  }

  .login-header h1 {
    font-size: var(--font-xl);
  }

  .login-header p {
    font-size: var(--font-sm);
  }

  .form-options {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .login-button {
    height: 44px;
    font-size: var(--font-base);
  }

  .login-footer {
    font-size: var(--font-sm);
  }
}

/* 登录方式切换样式 */
.login-mode-switch {
  display: flex !important;
  justify-content: center !important;
  width: 100% !important;
}

.login-mode-switch :deep(.el-radio-button__inner) {
  justify-content: center;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  color: var(--text-secondary);
  padding: 8px 20px;
  font-size: 14px;
}

.login-mode-switch :deep(.el-radio-button:first-child .el-radio-button__inner) {
  border-radius: var(--radius-md) 0 0 var(--radius-md);
}

.login-mode-switch :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
}

.login-mode-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--btn-primary);
  border-color: var(--btn-primary);
  color: #fff;
  box-shadow: none;
}

.send-code-btn {
  padding: 0;
  font-size: 12px;
  color: var(--btn-primary) !important;
  white-space: nowrap;
}

.send-code-btn.is-disabled {
  color: var(--text-disabled) !important;
}
</style>
