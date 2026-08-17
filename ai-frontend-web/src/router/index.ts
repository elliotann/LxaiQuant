import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { ElMessage } from "element-plus";

// 路由配置
const routes = [
  {
    path: "/",
    name: "Home",
    component: () => import("@/views/landing/HomePage.vue"),
    meta: {
      requiresAuth: false,
      title: "灵猞量化 - 智能量化交易系统",
    },
  },
  {
    path: "/app",
    name: "Dashboard",
    component: () => import("@/views/Dashboard.vue"),
    meta: {
      requiresAuth: true,
      title: "量化交易控制台",
    },
  },
  {
    path: "/dashboard",
    redirect: "/app",
    meta: {
      requiresAuth: true,
      title: "仪表板",
    },
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/auth/Login.vue"),
    meta: {
      requiresAuth: false,
      title: "登录",
    },
  },
  {
    path: "/register",
    name: "Register",
    component: () => import("@/views/auth/Register.vue"),
    meta: {
      requiresAuth: false,
      title: "注册",
    },
  },
  {
    path: "/forgot-password",
    name: "ForgotPassword",
    component: () => import("@/views/auth/ForgotPassword.vue"),
    meta: {
      requiresAuth: false,
      title: "忘记密码",
    },
  },
  {
    path: "/profile",
    name: "Profile",
    component: () => import("@/views/user/Profile.vue"),
    meta: {
      requiresAuth: true,
      title: "个人资料",
    },
  },
  {
    path: "/users",
    name: "Users",
    component: () => import("@/views/user/Users.vue"),
    meta: {
      requiresAuth: true,
      requiresAdmin: true,
      requiresPermission: "user:read",
      title: "用户管理",
    },
  },
  {
    path: "/strategies",
    name: "Strategies",
    component: () => import("@/views/strategy/StrategyManagement.vue"),
    meta: {
      requiresAuth: true,
      title: "策略管理",
    },
    children: [
      {
        path: "",
        name: "StrategyList",
        component: () => import("@/views/strategy/StrategyList.vue"),
        meta: {
          requiresAuth: true,
          title: "策略列表",
        },
      },
      {
        path: "create",
        name: "CreateStrategy",
        component: () => import("@/views/strategy/CreateStrategy.vue"),
        meta: {
          requiresAuth: true,
          title: "创建策略",
        },
      },
      {
        path: ":id",
        name: "StrategyDetail",
        component: () => import("@/views/strategy/StrategyDetail.vue"),
        meta: {
          requiresAuth: true,
          title: "策略详情",
        },
      },
      {
        path: ":id/edit",
        name: "EditStrategy",
        component: () => import("@/views/strategy/EditStrategy.vue"),
        meta: {
          requiresAuth: true,
          title: "编辑策略",
        },
      },
    ],
  },
  {
    path: "/strategies/:id/performance",
    name: "StrategyPerformance",
    component: () => import("@/views/strategy/StrategyPerformance.vue"),
    meta: {
      requiresAuth: true,
      title: "策略性能",
    },
  },
  {
    path: "/strategies/:id/logs",
    name: "StrategyLogs",
    component: () => import("@/views/strategy/StrategyLogs.vue"),
    meta: {
      requiresAuth: true,
      title: "策略日志",
    },
  },
  {
    path: "/backtest",
    name: "Backtest",
    component: () => import("@/views/backtest/Backtest.vue"),
    meta: {
      requiresAuth: true,
      title: "回测分析",
    },
  },
  {
    path: "/backtest/results",
    name: "BacktestTaskList",
    component: () => import("@/views/backtest/BacktestTaskList.vue"),
    meta: {
      requiresAuth: true,
      title: "回测任务列表",
    },
  },
  {
    path: "/backtest/optimization",
    name: "ParameterOptimization",
    component: () => import("@/views/optimization/ParameterOptimization.vue"),
    meta: {
      requiresAuth: true,
      title: "策略优化测试",
    },
  },
  {
    path: "/market-kline-v1",
    name: "MarketKlineV1",
    component: () => import("@/views/market/MarketKlineV1.vue"),
    meta: {
      requiresAuth: true,
      title: "市场行情",
    },
  },
  {
    path: "/data-import",
    name: "DataImport",
    component: () => import("@/views/market/DataImport.vue"),
    meta: {
      requiresAuth: true,
      title: "数据导入",
    },
  },
  {
    path: "/data-source-test",
    name: "DataSourceTest",
    component: () => import("@/views/market/DataSourceTest.vue"),
    meta: {
      requiresAuth: true,
      title: "数据源测试",
    },
  },
  {
    path: "/ai-radar",
    name: "AiRadar",
    component: () => import("@/views/market/AiRadar.vue"),
    meta: {
      requiresAuth: true,
      title: "AI雷达",
    },
  },
  {
    path: "/signal-service-management",
    name: "SignalServiceManagement",
    component: () => import("@/views/market/SignalServiceManagement.vue"),
    meta: {
      requiresAuth: true,
      title: "信号服务管理",
    },
  },
  {
    path: "/price-signal-management",
    name: "PriceSignalManagement",
    component: () => import("@/views/market/PriceSignalManagement.vue"),
    meta: {
      requiresAuth: true,
      title: "信号管理",
    },
  },
  {
    path: "/weight-rule-engine",
    name: "WeightRuleEngine",
    component: () => import("@/views/market/WeightRuleEngine.vue"),
    meta: {
      requiresAuth: true,
      title: "权重规则引擎",
    },
  },
  {
    path: "/trading",
    name: "Trading",
    component: () => import("@/views/trading/Trading.vue"),
    meta: {
      requiresAuth: true,
      title: "交易管理",
    },
  },
  {
    path: "/realtime-trading",
    name: "RealTimeTrading",
    component: () => import("@/views/trading/RealTimeTrading.vue"),
    meta: {
      requiresAuth: true,
      title: "实时交易",
    },
  },
  {
    path: "/trading-bots",
    name: "TradingBots",
    component: () => import("@/views/trading/TradingBots.vue"),
    meta: {
      requiresAuth: true,
      title: "交易机器人",
    },
  },
  {
    path: "/trading-logs",
    name: "TradingLogs",
    component: () => import("@/views/trading/TradingLogs.vue"),
    meta: {
      requiresAuth: true,
      title: "交易日志",
    },
  },
  {
    path: "/accounts",
    name: "Accounts",
    component: () => import("@/views/accounts/Accounts.vue"),
    meta: {
      requiresAuth: true,
      title: "账户管理",
    },
  },
  {
    path: "/monitoring",
    name: "Monitoring",
    component: () => import("@/views/monitoring/Monitoring.vue"),
    meta: {
      requiresAuth: true,
      title: "系统监控",
    },
  },
  {
    path: "/settings",
    name: "Settings",
    component: () => import("@/views/settings/Settings.vue"),
    meta: {
      requiresAuth: true,
      title: "系统设置",
    },
  },
  {
    path: "/admin/permissions",
    name: "RoleList",
    component: () => import("@/views/admin/RoleList.vue"),
    meta: {
      requiresAuth: true,
      requiresPermission: "permission:manage",
      title: "权限管理",
    },
  },
  {
    path: "/system/menus",
    name: "MenuManagement",
    component: () => import("@/views/system/MenuManagement.vue"),
    meta: {
      requiresAuth: true,
      requiresPermission: "system:menu",
      title: "菜单维护",
    },
  },
  {
    path: "/strategy-templates",
    name: "StrategyTemplates",
    component: () => import("@/components/strategy/TemplateList.vue"),
    meta: {
      requiresAuth: true,
      title: "策略模板",
    },
  },
  {
    path: "/alerts",
    name: "Alerts",
    component: () => import("@/views/monitoring/Monitoring.vue"),
    meta: {
      requiresAuth: true,
      title: "预警监控",
    },
  },
  {
    path: "/risk-control",
    name: "RiskControl",
    component: () => import("@/views/RiskControl.vue"),
    meta: {
      requiresAuth: true,
      title: "风控规则",
    },
  },
  {
    path: "/factor-mining",
    name: "FactorMining",
    component: () => import("@/views/quant-factor-mining/index.vue"),
    meta: {
      requiresAuth: true,
      title: "因子挖掘",
    },
  },
  {
    path: "/membership/credits",
    name: "CreditsTopUp",
    component: () => import("@/views/membership/CreditsTopUp.vue"),
    meta: {
      requiresAuth: true,
      title: "积分充值",
    },
  },
  // ==================== 社区市场路由 ====================
  {
    path: "/community-market",
    name: "CommunityMarket",
    component: () => import("@/views/CommunityMarket/index.vue"),
    meta: {
      requiresAuth: false,
      title: "社区市场"
    }
  },
  {
    path: "/community-market/item/:id",
    name: "CommunityMarketDetail",
    component: () => import("@/views/CommunityMarket/Detail.vue"),
    meta: {
      requiresAuth: true,
      title: "商品详情"
    }
  },
  {
    path: "/community-market/publish",
    name: "CommunityMarketPublish",
    component: () => import("@/views/CommunityMarket/Publish.vue"),
    meta: {
      requiresAuth: true,
      title: "发布商品"
    }
  },
  {
    path: "/community-market/my-purchases",
    name: "CommunityMarketMyPurchases",
    component: () => import("@/views/CommunityMarket/MyPurchases.vue"),
    meta: {
      requiresAuth: true,
      title: "我的已购"
    }
  },
  {
    path: "/community-market/review",
    name: "CommunityMarketReview",
    component: () => import("@/views/CommunityMarket/Review.vue"),
    meta: {
      requiresAuth: true,
      requiresAdmin: true,
      title: "商品审核"
    }
  },

  {
    path: "/403",
    name: "Forbidden",
    component: () => import("@/views/error/403.vue"),
    meta: {
      requiresAuth: false,
      title: "权限不足",
    },
  },
  {
    path: "/404",
    name: "NotFound",
    component: () => import("@/views/error/404.vue"),
    meta: {
      requiresAuth: false,
      title: "页面未找到",
    },
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/404",
  },
];

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition;
    } else {
      return { top: 0 };
    }
  },
});

// 全局前置守卫
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore();

  // 设置页面标题
  document.title = to.meta.title
    ? `${to.meta.title} - 量化交易系统`
    : "量化交易系统";

  // 检查是否需要认证
  if (to.meta.requiresAuth) {
    // 检查本地存储中是否有token，以防状态同步延迟
    const hasToken = localStorage.getItem("token");
    if (!authStore.isAuthenticated && !hasToken) {
      ElMessage.warning("请先登录");
      next("/");
      return;
    }

    // 如果有token但状态未更新，尝试初始化认证状态
    if (hasToken && !authStore.isAuthenticated) {
      try {
        await authStore.initializeAuth();
      } catch (error) {
        console.error("Auth initialization failed:", error);
      }
    }

    // 再次检查认证状态
    if (!authStore.isAuthenticated) {
      ElMessage.warning("请先登录");
      next("/");
      return;
    }

    // 检查是否需要管理员权限
    if (to.meta.requiresAdmin) {
      if (!authStore.hasRole("admin")) {
        ElMessage.error("权限不足");
        next("/403");
        return;
      }
    }

    // 检查是否需要特定权限
    const requiredPerm = to.meta.requiresPermission as string | undefined;
    if (requiredPerm) {
      if (!authStore.hasPermission(requiredPerm)) {
        ElMessage.error("权限不足");
        next("/403");
        return;
      }
    }
  }

  // 如果用户已登录，访问登录、注册或首页（landing），重定向到应用
  if (
    authStore.isAuthenticated &&
    (to.path === "/login" || to.path === "/register" || to.path === "/")
  ) {
    next("/app");
    return;
  }

  next();
});

// 全局后置钩子
router.afterEach((to, from) => {
  // 可以在这里添加页面访问统计等逻辑
});

// 路由错误处理
router.onError((error) => {
  console.error("Router error:", error);
  ElMessage.error("路由错误，请刷新页面重试");
});

export default router;
