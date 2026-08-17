import axios from "axios";
import { ElMessage } from "element-plus";
import { useAuthStore } from "@/stores/auth";
import { refreshToken } from "./auth";

// 创建axios实例
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "/api",
  timeout: 600000, // 10分钟超时
  headers: {
    "Content-Type": "application/json",
  },
});

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    console.log(
      "🚀 API请求:",
      config.method?.toUpperCase(),
      config.url,
      "Full URL:",
      config.baseURL + config.url,
    );

    // 仅阻止误触发的根路径 /login；允许 /auth/login 正常请求
    if (config.url === "/login") {
      console.warn("🚫 取消自动登录请求:", config.url);
      return Promise.reject(new Error("自动登录请求已被取消"));
    }

    const authStore = useAuthStore();
    if (authStore.token && !config.url?.includes("/auth/refresh")) {
      config.headers.Authorization = `Bearer ${authStore.token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// Refresh lock to prevent concurrent token refresh race conditions
let isRefreshing = false;
let refreshSubscribers: Array<(token: string) => void> = [];
let failedToken: string | null = null;

function onRefreshed(token: string) {
  refreshSubscribers.forEach((cb) => cb(token));
  refreshSubscribers = [];
}

function addRefreshSubscriber(cb: (token: string) => void) {
  refreshSubscribers.push(cb);
}

function getStorage(): Storage {
  return localStorage.getItem("token") ? localStorage : sessionStorage;
}

function getToken(): string | null {
  return localStorage.getItem("token") ?? sessionStorage.getItem("token");
}

function getRefreshToken(): string | null {
  return localStorage.getItem("refreshToken") ?? sessionStorage.getItem("refreshToken");
}

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    const { data } = response;

    // 如果返回的是二进制数据（如文件下载），直接返回
    if (response.config.responseType === "blob") {
      return response;
    }

    // 对于K线数据API，采用更宽松的处理方式
    if (
      response.config.url?.includes("/kline/data") ||
      response.config.url?.includes("/backtest/kline-chart-data")
    ) {
      return data;
    }

    // 如果响应是纯数组（如菜单列表），直接返回
    if (Array.isArray(data)) {
      return data;
    }

    // 对于新的v1 API（使用code字段），检查code === 200
    if (response.config.url?.includes("/v1/kline/") || response.config.url?.includes("/ai-radar/") || response.config.url?.includes("/ai/tasks")) {
      if (data.code === 200) {
        return data;
      } else {
        console.error("v1 API响应失败:", data.message);
        ElMessage.error(data.message || "请求失败");
        return Promise.reject(new Error(data.message || "请求失败"));
      }
    }

    if (data.success) {
      if (
        response.config.url?.includes("/price-signal/list") ||
        response.config.url?.includes("/trading-bots")
      ) {
        return data;
      }

      const result = data.data !== undefined ? data.data : data;
      const isObjectResult = result !== null && typeof result === 'object';

      if (response.config.url?.includes("/auth/")) {
        return result;
      }

      if (isObjectResult && "success" in result) {
        return result;
      } else {
        return {
          success: data.success,
          message: data.message,
          data: result,
        };
      }
    } else {
      console.error("响应失败:", data.message);
      const silent = Boolean((response.config as any)?.silent);
      if (!silent) ElMessage.error(data.message || "请求失败");
      return Promise.reject(new Error(data.message || "请求失败"));
    }
  },
  (error) => {
    if (error.response) {
      console.error("HTTP错误:", error.response.status, error.response.data);
    } else if (error.request) {
      console.error("网络错误:", error.message);
    }

    const { response } = error;
    const silent = Boolean((error?.config as any)?.silent);

    if (response) {
      switch (response.status) {
        case 401: {
          const isRefreshRequest = error.config?.url?.includes("/auth/refresh");
          if (isRefreshRequest) {
            ElMessage.error("登录已过期，请重新登录");
            const authStore = useAuthStore();
            authStore.clearAuth();
            window.location.href = "/login";
            return Promise.reject(error);
          }
          if (silent) break;

          const savedRefreshToken = getRefreshToken();
          const savedToken = getToken();
          if (!savedRefreshToken) {
            ElMessage.error("登录已过期，请重新登录");
            const authStore = useAuthStore();
            authStore.clearAuth();
            window.location.href = "/login";
            return Promise.reject(error);
          }

          if (isRefreshing) {
            return new Promise((resolve) => {
              addRefreshSubscriber((newToken: string) => {
                error.config.headers.Authorization = `Bearer ${newToken}`;
                resolve(api(error.config));
              });
            });
          }

          isRefreshing = true;
          failedToken = savedToken;

          return refreshToken(savedRefreshToken, savedToken ?? undefined)
            .then((res: any) => {
              const newToken = res.token;
              const newRefreshToken = res.refreshToken;

              const authStore = useAuthStore();
              authStore.token = newToken;
              authStore.refreshToken = newRefreshToken;

              const storage = getStorage();
              storage.setItem("token", newToken);
              storage.setItem("refreshToken", newRefreshToken);

              onRefreshed(newToken);

              error.config.headers.Authorization = `Bearer ${newToken}`;
              return api(error.config);
            })
            .catch(() => {
              ElMessage.error("登录已过期，请重新登录");
              const authStore = useAuthStore();
              authStore.clearAuth();
              window.location.href = "/login";
              return Promise.reject(error);
            })
            .finally(() => {
              isRefreshing = false;
              failedToken = null;
            });
        }
        case 403:
          if (!silent) ElMessage.error("权限不足");
          break;
        case 404:
          if (!silent) ElMessage.error("请求的资源不存在");
          break;
        case 500:
          if (!silent) ElMessage.error("服务器内部错误");
          break;
        default:
          if (!silent) ElMessage.error(response.data?.message || "网络错误");
      }
    } else {
      if (!silent) ElMessage.error("网络连接失败");
    }

    return Promise.reject(error);
  },
);

// HTTP请求方法
export const get = (url: string, config?: any) => api.get(url, config);
export const post = (url: string, data?: any, config?: any) =>
  api.post(url, data, config);
export const put = (url: string, data?: any, config?: any) =>
  api.put(url, data, config);
export const del = (url: string, config?: any) => api.delete(url, config);
export const patch = (url: string, data?: any, config?: any) =>
  api.patch(url, data, config);

// 文件上传
export const upload = (
  url: string,
  file: File,
  onProgress?: (progress: number) => void,
) => {
  const formData = new FormData();
  formData.append("file", file);

  return api.post(url, formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const progress = Math.round(
          (progressEvent.loaded * 100) / progressEvent.total,
        );
        onProgress(progress);
      }
    },
  });
};

export default api;
