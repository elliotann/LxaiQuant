import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { ElMessage } from "element-plus";

export interface Notification {
  id: string;
  type: "success" | "warning" | "error" | "info";
  message: string;
  duration?: number;
}

export const useAppStore = defineStore("app", () => {
  // 鐘舵€?  const isLoading = ref(false);
  const notifications = ref<Notification[]>([]);
  const theme = ref<"light" | "dark" | "auto">("dark");
  const language = ref("zh-CN");
  const sidebarCollapsed = ref(false);
  const pageHeader = ref({
    title: "",
    breadcrumbs: [] as Array<{ name: string; path: string }>,
  });

  // 璁＄畻灞炴€?  const isDarkMode = computed(() => {
    if (theme.value === "auto") {
      return window.matchMedia("(prefers-color-scheme: dark)").matches;
    }
    return theme.value === "dark";
  });

  // 鏂规硶
  const setLoading = (loading: boolean) => {
    isLoading.value = loading;
  };

  const showNotification = (notification: Omit<Notification, "id">) => {
    const id = Date.now().toString();
    const newNotification: Notification = {
      id,
      duration: 3000,
      ...notification,
    };

    notifications.value.push(newNotification);

    // 鑷姩绉婚櫎閫氱煡
    if (newNotification.duration && newNotification.duration > 0) {
      setTimeout(() => {
        removeNotification(id);
      }, newNotification.duration);
    }

    return id;
  };

  const removeNotification = (id: string) => {
    const index = notifications.value.findIndex((n) => n.id === id);
    if (index > -1) {
      notifications.value.splice(index, 1);
    }
  };

  const clearNotifications = () => {
    notifications.value = [];
  };

  const setTheme = (newTheme: "light" | "dark" | "auto") => {
    theme.value = newTheme;
    localStorage.setItem("theme", newTheme);
    applyTheme();
  };

  const applyTheme = () => {
    const root = document.documentElement;
    if (isDarkMode.value) {
      root.classList.add("dark");
      root.classList.remove("clean-theme");
    } else {
      root.classList.remove("dark");
      root.classList.add("clean-theme");
    }
  };

  const setLanguage = (newLanguage: string) => {
    language.value = newLanguage;
    localStorage.setItem("language", newLanguage);
  };

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value;
    localStorage.setItem("sidebarCollapsed", sidebarCollapsed.value.toString());
  };

  const setPageHeader = (
    title: string,
    breadcrumbs?: Array<{ name: string; path: string }>,
  ) => {
    pageHeader.value = {
      title,
      breadcrumbs: breadcrumbs || [],
    };
  };

  const clearPageHeader = () => {
    pageHeader.value = {
      title: "",
      breadcrumbs: [],
    };
  };

  const showSuccess = (message: string, duration?: number) => {
    return showNotification({ type: "success", message, duration });
  };

  const showWarning = (message: string, duration?: number) => {
    return showNotification({ type: "warning", message, duration });
  };

  const showError = (message: string, duration?: number) => {
    return showNotification({ type: "error", message, duration });
  };

  const showInfo = (message: string, duration?: number) => {
    return showNotification({ type: "info", message, duration });
  };

  const handleError = (error: any, defaultMessage = "鎿嶄綔澶辫触") => {
    const message =
      error?.response?.data?.message || error?.message || defaultMessage;
    showError(message);
    console.error("Error:", error);
  };

  const initializeApp = async () => {
    try {
      // 浠巐ocalStorage鎭㈠璁剧疆
      const savedTheme = localStorage.getItem("theme") as
        | "light"
        | "dark"
        | "auto";
      const savedLanguage = localStorage.getItem("language");
      const savedSidebarCollapsed = localStorage.getItem("sidebarCollapsed");

      if (savedTheme) {
        theme.value = savedTheme;
      }

      if (savedLanguage) {
        language.value = savedLanguage;
      }

      if (savedSidebarCollapsed) {
        sidebarCollapsed.value = savedSidebarCollapsed === "true";
      }

      // 搴旂敤涓婚
      applyTheme();

      // 鐩戝惉绯荤粺涓婚鍙樺寲
      if (theme.value === "auto") {
        window
          .matchMedia("(prefers-color-scheme: dark)")
          .addEventListener("change", applyTheme);
      }

      // 鍒濆鍖栧畬鎴?      console.log("App initialized successfully");
    } catch (error) {
      console.error("App initialization failed:", error);
      handleError(error, "搴旂敤鍒濆鍖栧け璐?);
    }
  };

  return {
    // 鐘舵€?    isLoading,
    notifications,
    theme,
    language,
    sidebarCollapsed,
    pageHeader,

    // 璁＄畻灞炴€?    isDarkMode,

    // 鏂规硶
    setLoading,
    showNotification,
    removeNotification,
    clearNotifications,
    setTheme,
    applyTheme,
    setLanguage,
    toggleSidebar,
    setPageHeader,
    clearPageHeader,
    showSuccess,
    showWarning,
    showError,
    showInfo,
    handleError,
    initializeApp,
  };
});

