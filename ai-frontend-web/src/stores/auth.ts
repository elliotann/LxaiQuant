import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  login as loginApi,
  loginByEmail as loginByEmailApi,
  register as registerApi,
  sendEmailCode as sendEmailCodeApi,
  getCurrentUser,
  refreshToken as refreshTokenApi,
  logout as logoutApi,
} from "@/api";
import type { User, LoginData, RegisterData, AuthState } from "@/types/auth";

export const useAuthStore = defineStore("auth", () => {
  const router = useRouter();

  const TOKEN_KEY = "token";
  const REFRESH_TOKEN_KEY = "refreshToken";
  const USER_KEY = "user";

  const getStorageValue = (key: string): string | null => {
    return localStorage.getItem(key) ?? sessionStorage.getItem(key);
  };

  // 状态
  const user = ref<User | null>(null);
  const token = ref<string | null>(getStorageValue(TOKEN_KEY));
  const refreshToken = ref<string | null>(getStorageValue(REFRESH_TOKEN_KEY));
  const isAuthenticated = computed(() => !!token.value);
  const isLoading = ref(false);

  // 计算属性
  const userName = computed(() => user.value?.username || "");
  const userEmail = computed(() => user.value?.email || "");
  const userRole = computed(() => user.value?.role || "user");
  const userPermissions = computed(() => {
    const perms = user.value?.permissions;
    if (perms && perms.length > 0) {
      return perms;
    }
    if (userRole.value === "admin") {
      return [
        "user:read", "user:create", "user:update", "user:delete", "user:assign-role",
        "strategy:create", "strategy:read", "strategy:update", "strategy:delete", "strategy:ai-generate",
        "backtest:run", "backtest:advanced",
        "trade:execute", "trade:view-orderbook",
        "factor:run", "factor:create",
        "ml:train",
        "membership:view", "membership:manage",
        "system:config", "system:logs", "system:menu",
        "permission:manage",
        "role:read", "role:assign",
      ];
    }
    return [];
  });

  // 方法
  const setUser = (userData: User) => {
    user.value = userData;
  };

  const setTokens = (accessToken: string, refresh: string, rememberMe = true) => {
    token.value = accessToken;
    refreshToken.value = refresh;
    const storage = rememberMe ? localStorage : sessionStorage;
    const otherStorage = rememberMe ? sessionStorage : localStorage;
    storage.setItem(TOKEN_KEY, accessToken);
    storage.setItem(REFRESH_TOKEN_KEY, refresh);
    otherStorage.removeItem(TOKEN_KEY);
    otherStorage.removeItem(REFRESH_TOKEN_KEY);
  };

  const clearAuth = () => {
    user.value = null;
    token.value = null;
    refreshToken.value = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
  };

  // 登录
  const login = async (loginData: LoginData) => {
    try {
      isLoading.value = true;
      const response = await loginApi(loginData);
      const {
        user: userData,
        token: accessToken,
        refreshToken: refresh,
      } = response;

      setUser(userData);
      setTokens(accessToken, refresh, Boolean(loginData.rememberMe));

      // 记住我：勾选后存 localStorage，否则存 sessionStorage（浏览器会话级）
      const storage = loginData.rememberMe ? localStorage : sessionStorage;
      const otherStorage = loginData.rememberMe ? sessionStorage : localStorage;
      storage.setItem(USER_KEY, JSON.stringify(userData));
      otherStorage.removeItem(USER_KEY);

      ElMessage.success("登录成功");

      return true;
    } catch (error) {
      console.error("Login failed:", error);
      clearAuth();
      throw error;
    } finally {
      isLoading.value = false;
    }
  };

  // 注册
  const register = async (registerData: RegisterData) => {
    try {
      isLoading.value = true;

      const response = await registerApi(registerData);
      const {
        user: userData,
        token: accessToken,
        refreshToken: refresh,
      } = response;

      setUser(userData);
      setTokens(accessToken, refresh, true);

      // 保存用户信息到localStorage
      localStorage.setItem(USER_KEY, JSON.stringify(userData));

      ElMessage.success("注册成功");

      return true;
    } catch (error) {
      console.error("Registration failed:", error);
      clearAuth();
      throw error;
    } finally {
      isLoading.value = false;
    }
  };

  // 邮箱验证码登录
  const loginByEmail = async (data: { email: string; emailCode: string }) => {
    try {
      isLoading.value = true;
      const response = await loginByEmailApi(data);
      const {
        user: userData,
        token: accessToken,
        refreshToken: refresh,
      } = response;

      setUser(userData);
      setTokens(accessToken, refresh, false);
      sessionStorage.setItem(USER_KEY, JSON.stringify(userData));

      ElMessage.success("登录成功");
      return true;
    } catch (error) {
      console.error("Login by email failed:", error);
      clearAuth();
      throw error;
    } finally {
      isLoading.value = false;
    }
  };

  // 发送邮箱验证码
  const sendEmailCode = async (email: string) => {
    try {
      await sendEmailCodeApi(email);
      ElMessage.success("验证码已发送到您的邮箱");
      return true;
    } catch (error) {
      console.error("Send email code failed:", error);
      throw error;
    }
  };

  // 获取当前用户信息
  const fetchCurrentUser = async () => {
    try {
      if (!token.value) return false;

      const userData = await getCurrentUser();
      setUser(userData);

      // 更新localStorage中的用户信息
      const target = localStorage.getItem(TOKEN_KEY) ? localStorage : sessionStorage;
      target.setItem(USER_KEY, JSON.stringify(userData));

      return true;
    } catch (error) {
      console.error("Failed to fetch current user:", error);
      clearAuth();
      return false;
    }
  };

  // 刷新token
  const refreshTokenFn = async () => {
    try {
      if (!refreshToken.value) {
        throw new Error("No refresh token available");
      }

      const response = await refreshTokenApi(refreshToken.value, token.value ?? undefined);
      const { token: accessToken, refreshToken: refresh } = response;

      setTokens(accessToken, refresh);

      return true;
    } catch (error) {
      console.error("Token refresh failed:", error);
      clearAuth();
      return false;
    }
  };

  // 登出
  const logout = async () => {
    try {
      if (token.value) {
        await logoutApi();
      }
    } catch (error) {
      console.error("Logout API call failed:", error);
    } finally {
      clearAuth();
      ElMessage.success("已退出登录");
      await router.push("/");
    }
  };

  // 检查权限
  const hasPermission = (permission: string) => {
    return userPermissions.value.includes(permission);
  };

  // 检查角色
  const hasRole = (role: string) => {
    return userRole.value === role;
  };

  // 初始化认证状态
  const initializeAuth = async () => {
    try {
      const savedToken = getStorageValue(TOKEN_KEY);
      const savedUser = getStorageValue(USER_KEY);
      const savedRefreshToken = getStorageValue(REFRESH_TOKEN_KEY);

      if (savedToken && savedUser) {
        token.value = savedToken;
        refreshToken.value = savedRefreshToken;

        try {
          user.value = JSON.parse(savedUser);
        } catch (parseError) {
          console.error("Failed to parse saved user:", parseError);
          clearAuth();
          return false;
        }

        return await fetchCurrentUser();
      }
      return false;
    } catch (error) {
      console.error("Auth initialization failed:", error);
      clearAuth();
      return false;
    }
  };

  // 更新用户信息
  const updateUser = (userData: Partial<User>) => {
    if (user.value) {
      user.value = { ...user.value, ...userData };
      const target = localStorage.getItem(TOKEN_KEY) ? localStorage : sessionStorage;
      target.setItem(USER_KEY, JSON.stringify(user.value));
    }
  };

  return {
    // 状态
    user,
    token,
    refreshToken,
    isAuthenticated,
    isLoading,

    // 计算属性
    userName,
    userEmail,
    userRole,
    userPermissions,

    // 方法
    login,
    loginByEmail,
    register,
    sendEmailCode,
    logout,
    fetchCurrentUser,
    refreshTokenFn,
    hasPermission,
    hasRole,
    initializeAuth,
    updateUser,
    setUser,
    setTokens,
    clearAuth,
  };
});
