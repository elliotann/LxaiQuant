<template>
  <div class="v3-layout">
    <SidebarNav
      v-model="activeNav"
      v-model:collapsed="sidebarCollapsed"
      @item-click="handleNavClick"
    />

    <div class="layout-body">
      <!-- 顶部状态栏 -->
      <div class="top-status-bar">
        <div class="status-left">
          <div class="system-status">
            <el-tooltip
              :content="
                sidebarCollapsed
                  ? '展开左侧菜单 (Ctrl+B)'
                  : '收缩左侧菜单 (Ctrl+B)'
              "
              placement="bottom"
            >
              <el-button
                class="sidebar-collapse-btn"
                size="small"
                text
                @click="toggleSidebar"
              >
                <el-icon :size="18">
                  <component :is="sidebarCollapsed ? Expand : Fold" />
                </el-icon>
              </el-button>
            </el-tooltip>

            <el-icon class="status-icon" :class="systemStatus">
              <component :is="statusIcon" />
            </el-icon>
            <span class="status-text">{{ statusText }}</span>
          </div>
          <div class="market-time">市场时间: {{ marketTime }}</div>
        </div>

        <div class="status-center">
          <div class="quick-stats">
            <div class="stat-item">
              <span class="stat-label">总资产</span>
              <div class="stat-content">
                <span class="stat-value"
                  >${{ totalAssets.toLocaleString() }}</span
                >
                <span
                  class="stat-change"
                  :class="totalChange >= 0 ? 'positive' : 'negative'"
                >
                  {{ totalChange >= 0 ? "+" : "" }}{{ totalChange.toFixed(2) }}%
                </span>
              </div>
            </div>
            <div class="stat-item">
              <span class="stat-label">今日盈亏</span>
              <div class="stat-content">
                <span
                  class="stat-value"
                  :class="todayPnL >= 0 ? 'positive' : 'negative'"
                >
                  {{ todayPnL >= 0 ? "+" : "" }}${{ todayPnL.toFixed(2) }}
                </span>
              </div>
            </div>
            <div class="stat-item">
              <span class="stat-label">运行策略</span>
              <div class="stat-content">
                <span class="stat-value"
                  >{{ runningStrategies }}/{{ totalStrategies }}</span
                >
              </div>
            </div>
          </div>
        </div>

        <div class="status-right">
          <el-dropdown trigger="hover" @command="handleUserCommand">
            <div class="user-info">
              <el-avatar :size="32" :src="userAvatar">{{
                userInitials
              }}</el-avatar>
              <div class="user-details">
                <div class="user-name">{{ userName }}</div>
                <div class="user-role">{{ userRole }}</div>
              </div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <div class="quick-actions">
            <el-button size="small" text @click="toggleFullscreen">
              <el-icon><full-screen /></el-icon>
            </el-button>
            <el-button size="small" text @click="showNotifications">
              <el-badge
                :value="notificationCount"
                :hidden="notificationCount === 0"
              >
                <el-icon><bell /></el-icon>
              </el-badge>
            </el-button>
            <el-button size="small" text @click="showSettings">
              <el-icon><setting /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <!-- 页签系统区域 -->
      <div class="tab-area">
        <TabSystem
          v-model="activeTab"
          :tabs="tabs"
          @tab-change="handleTabChange"
          @tab-close="handleTabClose"
          @tabs-update="handleTabsUpdate"
          @view-strategy="handleViewStrategy"
          @edit-strategy="handleEditStrategy"
          @create-strategy="handleCreateStrategy"
          @create-success="handleCreateSuccess"
          @back-to-list="handleBackToList"
          ref="tabSystemRef"
        />
      </div>

      <!-- 右侧信息面板 -->
      <div class="right-panel" v-if="showRightPanel">
        <div class="panel-header">
          <h3>信息面板</h3>
          <el-button size="small" text @click="showRightPanel = false">
            <el-icon><close /></el-icon>
          </el-button>
        </div>
        <div class="panel-content">
          <!-- 系统信息 -->
          <div class="info-section">
            <h4>系统状态</h4>
            <div class="info-item">
              <span class="info-label">CPU使用率</span>
              <div class="info-value">
                <el-progress
                  :percentage="cpuUsage"
                  :color="getCpuColor(cpuUsage)"
                />
              </div>
            </div>
            <div class="info-item">
              <span class="info-label">内存使用率</span>
              <div class="info-value">
                <el-progress
                  :percentage="memoryUsage"
                  :color="getMemoryColor(memoryUsage)"
                />
              </div>
            </div>
            <div class="info-item">
              <span class="info-label">数据库连接</span>
              <el-tag :type="dbConnected ? 'success' : 'danger'" size="small">
                {{ dbConnected ? "正常" : "断开" }}
              </el-tag>
            </div>
          </div>

          <!-- 快速操作 -->
          <div class="info-section">
            <h4>快速操作</h4>
            <div class="quick-actions-grid">
              <el-button size="small" @click="quickOrder">
                <el-icon><plus /></el-icon>
                快速下单
              </el-button>
              <el-button size="small" @click="pauseAllStrategies">
                <el-icon><video-pause /></el-icon>
                暂停策略
              </el-button>
              <el-button size="small" @click="refreshData">
                <el-icon><refresh /></el-icon>
                刷新数据
              </el-button>
              <el-button size="small" @click="exportReport">
                <el-icon><download /></el-icon>
                导出报告
              </el-button>
            </div>
          </div>

          <!-- 最近活动 -->
          <div class="info-section">
            <h4>最近活动</h4>
            <div class="activity-list">
              <div
                v-for="activity in recentActivities"
                :key="activity.id"
                class="activity-item"
              >
                <el-icon class="activity-icon" :class="activity.type">
                  <component :is="activity.icon" />
                </el-icon>
                <div class="activity-content">
                  <div class="activity-text">{{ activity.text }}</div>
                  <div class="activity-time">{{ activity.time }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部信息栏 -->
    <div class="bottom-status-bar">
      <div class="bottom-left">
        <div class="connection-status">
          <el-icon class="connection-icon" :class="connectionStatus">
            <component :is="connectionIcon" />
          </el-icon>
          <span>{{ connectionText }}</span>
        </div>
        <div class="data-sync">数据同步: {{ lastSync }}</div>
      </div>

      <div class="bottom-center">
        <div class="system-messages">
          <span
            v-for="message in systemMessages"
            :key="message.id"
            class="message-item"
          >
            {{ message.text }}
          </span>
        </div>
      </div>

      <div class="bottom-right">
        <div class="quick-shortcuts">
          <el-tooltip content="切换右侧面板 (Ctrl+R)" placement="top">
            <el-button
              size="small"
              text
              @click="showRightPanel = !showRightPanel"
            >
              <el-icon><expand /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip content="帮助 (F1)" placement="top">
            <el-button size="small" text @click="showHelp">
              <el-icon><question-filled /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>
    </div>

    <!-- 通知中心 -->
    <el-drawer
      v-model="notificationsOpen"
      title="通知中心"
      direction="rtl"
      size="400px"
    >
      <div class="notifications-list">
        <div
          v-for="notification in notifications"
          :key="notification.id"
          class="notification-item"
        >
          <div class="notification-header">
            <el-icon class="notification-icon" :class="notification.type">
              <component :is="notification.icon" />
            </el-icon>
            <span class="notification-time">{{ notification.time }}</span>
          </div>
          <div class="notification-content">{{ notification.content }}</div>
        </div>
      </div>
    </el-drawer>

    <!-- 编辑策略抽屉 -->
    <el-drawer
      v-model="editStrategyDrawerOpen"
      :title="editStrategyDrawerTitle"
      direction="rtl"
      size="1400px"
      :destroy-on-close="true"
    >
      <EditStrategy
        v-if="editStrategyDrawerOpen"
        :strategy-id="currentEditStrategyId"
        :strategy-data="currentEditStrategyData"
        @close="closeEditStrategyDrawer"
        @saved="handleStrategySaved"
      />
    </el-drawer>

    <!-- 设置对话框 -->
    <el-dialog v-model="settingsOpen" title="系统设置" width="600px">
      <el-tabs v-model="activeSettingsTab">
        <el-tab-pane label="显示设置" name="display">
          <el-form :model="displaySettings" label-width="120px">
            <el-form-item label="主题模式">
              <el-radio-group v-model="displaySettings.theme">
                <el-radio value="dark">深色主题</el-radio>
                <el-radio value="light">浅色主题</el-radio>
                <el-radio value="auto">跟随系统</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="语言">
              <el-select v-model="displaySettings.language" style="width: 100%">
                <el-option label="简体中文" value="zh-CN" />
                <el-option label="English" value="en-US" />
              </el-select>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="交易设置" name="trading">
          <el-form :model="tradingSettings" label-width="120px">
            <el-form-item label="默认交易对">
              <el-select
                v-model="tradingSettings.defaultSymbol"
                style="width: 100%"
              >
                <el-option label="BTC/USDT" value="BTC/USDT" />
                <el-option label="ETH/USDT" value="ETH/USDT" />
                <el-option label="BNB/USDT" value="BNB/USDT" />
              </el-select>
            </el-form-item>
            <el-form-item label="下单确认">
              <el-switch v-model="tradingSettings.confirmOrder" />
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
  </div>
</template>

<script setup lang="ts">
import {
  ref,
  reactive,
  computed,
  onMounted,
  onUnmounted,
  provide,
  inject,
  nextTick,
} from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import SidebarNav from "./SidebarNav.vue";
import TabSystem from "./TabSystem.vue";
import EditStrategy from "@/views/strategy/EditStrategy.vue";
import {
  FullScreen,
  Bell,
  Setting,
  Close,
  Plus,
  VideoPause,
  Refresh,
  Download,
  Fold,
  Expand,
  Connection,
  Lightning,
  QuestionFilled,
  SuccessFilled,
  WarningFilled,
  InfoFilled,
  CircleCloseFilled,
} from "@element-plus/icons-vue";
import { useAuthStore } from "@/stores/auth";
import { useAppStore } from "@/stores/app";
import { getTradingSummary } from "@/api/trading";

interface Tab {
  id: string;
  title: string;
  icon?: any;
  component: any;
  props?: Record<string, any>;
  pinned?: boolean;
  path?: string;
}

interface Activity {
  id: string;
  type: string;
  icon: any;
  text: string;
  time: string;
}

interface Notification {
  id: string;
  type: string;
  icon: any;
  content: string;
  time: string;
}

interface SystemMessage {
  id: string;
  text: string;
}

const router = useRouter();
const authStore = useAuthStore();
const appStore = useAppStore();

// 页签组件映射
const tabComponents: Record<string, any> = {
  dashboard: "DashboardOverview",
  strategies: "StrategyList",
  "strategy-editor": "CreateStrategy",
  "strategy-templates": "StrategyTemplates",
  "strategy-optimization": "ParameterOptimization",
  "backtest-results": () => import("@/views/backtest/BacktestTaskList.vue"),
  trading: "TradingPanel",
  "exchange-mgmt": () => import("@/views/trading/Trading.vue"),
  "realtime-trading": () => import("@/views/trading/RealTimeTrading.vue"),
  "trading-bots": () => import("@/views/trading/TradingBots.vue"),
  orders: "OrdersManagement",
  positions: "PositionsManagement",
  accounts: "Accounts",
  "trading-logs": () => import("@/views/trading/TradingLogs.vue"),
  "risk-control": () => import("@/views/RiskControl.vue"),
  alerts: "Monitoring",
  "market-kline-v1": () => import("@/views/market/MarketKlineV1.vue"),
  "ai-radar": () => import("@/views/market/AiRadar.vue"),
  "data-source-test": () => import("@/views/market/DataSourceTest.vue"),
  "data-import": () => import("@/views/market/DataImport.vue"),
  "signal-service-management": () =>
    import("@/views/market/SignalServiceManagement.vue"),
  "price-signal-management": () =>
    import("@/views/market/PriceSignalManagement.vue"),
  "weight-rule-engine": () =>
    import("@/views/market/WeightRuleEngine.vue"),
  "quant-ml": () => import("@/views/quant-ml/index.vue"),
  "factor-mining": () => import("@/views/quant-factor-mining/index.vue"),
  users: () => import("@/views/user/Users.vue"),
  permissions: () => import("@/views/admin/RoleList.vue"),
  settings: () => import("@/views/settings/Settings.vue"),
  logs: () => import("@/views/logs/SystemLogs.vue"),
  credits: () => import("@/views/membership/CreditsTopUp.vue"),
  menus: () => import("@/views/system/MenuManagement.vue"),
  "community-market": () => import("@/views/CommunityMarket/index.vue"),
  "community-market:publish": () => import("@/views/CommunityMarket/Publish.vue"),
  "community-market:my-purchases": () => import("@/views/CommunityMarket/MyPurchases.vue"),
  "community-market:review": () => import("@/views/CommunityMarket/Review.vue"),
};

const tabTitles: Record<string, string> = {
  dashboard: "总览",
  strategies: "策略列表",
  "strategy-editor": "策略编辑器",
  "strategy-templates": "策略模板",
  "strategy-optimization": "策略优化测试",
  "backtest-results": "回测任务",
  "backtest-result-detail": "回测结果详情",
  trading: "交易面板",
  "exchange-mgmt": "交易所维护",
  "realtime-trading": "实时交易",
  "trading-bots": "交易机器人",
  orders: "订单管理",
  positions: "持仓管理",
  accounts: "账户管理",
  "trading-logs": "交易日志",
  "risk-control": "风控规则",
  alerts: "预警监控",
  "market-kline-v1": "市场行情",
  "ai-radar": "AI雷达",
  "data-source-test": "数据源测试",
  "data-import": "数据导入",
  "signal-service-management": "信号服务管理",
  "price-signal-management": "价格信号管理",
  "weight-rule-engine": "权重规则引擎",
  "factor-mining": "因子挖掘",
  users: "用户管理",
  settings: "系统设置",
  logs: "系统日志",
  credits: "积分充值",
  menus: "菜单维护",
  "community-market": "社区市场",
  "community-market:publish": "发布商品",
  "community-market:my-purchases": "我的已购",
  "community-market:review": "商品审核",
};

// 动态菜单路由路径 → 页签 key 映射
const routeToTabKey: Record<string, string> = {
  "/dashboard": "dashboard",
  "/strategies": "strategies",
  "/strategies/create": "strategy-editor",
  "/backtest": "backtest-results",
  "/backtest/results": "backtest-result-detail",
  "/backtest/optimization": "strategy-optimization",
  "/market-kline-v1": "market-kline-v1",
  "/data-import": "data-import",
  "/data-source-test": "data-source-test",
  "/ai-radar": "ai-radar",
  "/signal-service-management": "signal-service-management",
  "/price-signal-management": "price-signal-management",
  "/weight-rule-engine": "weight-rule-engine",
  "/trading": "exchange-mgmt",
  "/trading/real-time": "realtime-trading",
  "/trading/positions": "positions",
  "/trading/orders": "orders",
  "/factor-mining": "factor-mining",
  "/ml": "quant-ml",
  "/membership/credits": "credits",
  "/users": "users",
  "/admin/permissions": "permissions",
  "/settings": "settings",
  "/system/logs": "logs",
  "/system/menus": "menus",
  "/strategy-templates": "strategy-templates",
  "/trading-bots": "trading-bots",
  "/trading-logs": "trading-logs",
  "/accounts": "accounts",
  "/risk-control": "risk-control",
  "/alerts": "alerts",
  "/community-market": "community-market",
  "/community-market/publish": "community-market:publish",
  "/community-market/my-purchases": "community-market:my-purchases",
  "/community-market/review": "community-market:review",
};

// 导航状态
const activeNav = ref("dashboard");
const sidebarCollapsed = ref(false);
const activeTab = ref("");
const weightRuleEngineConfigId = ref<number | null>(null);
const showRightPanel = ref(false);

// 页签管理
const tabSystemRef = ref();
const tabs = ref<Tab[]>([]);

// 系统状态
const systemStatus = ref("success");
const statusText = ref("系统运行正常");
const marketTime = ref("--:--:--");
const totalAssets = ref(0);
const totalChange = ref(0);
const todayPnL = ref(0);
const runningStrategies = ref(3);
const totalStrategies = ref(5);

let summaryPollTimer: number | null = null;

const fetchSummary = async () => {
  try {
    const resp = await getTradingSummary();
    const data = resp && typeof resp === "object" && "data" in resp ? (resp as any).data : resp;
    if (data) {
      totalAssets.value = Number(data.totalAssets ?? 0);
      todayPnL.value = Number(data.dailyPnL ?? 0);
      totalChange.value = Number(data.dailyPnLPercent ?? 0);
    }
  } catch {
    // ignore
  }
};

// 用户信息
const userAvatar = ref("");
const userName = computed(() => authStore.userName || "未登录用户");
const userRole = computed(() => (authStore.userRole === "admin" ? "管理员" : "用户"));
const userInitials = computed(() => {
  return userName.value
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase();
});

// 系统监控
const cpuUsage = ref(45);
const memoryUsage = ref(62);
const dbConnected = ref(true);
const connectionStatus = ref("success");
const connectionText = ref("连接正常");
const lastSync = ref("刚刚");

// 通知和消息
const notificationCount = computed(() => notifications.value.length);
const notificationsOpen = ref(false);
const settingsOpen = ref(false);
const activeSettingsTab = ref("display");

// 编辑策略抽屉
const editStrategyDrawerOpen = ref(false);
const currentEditStrategyId = ref<string | null>(null);
const currentEditStrategyData = ref<any>(null);
const editStrategyDrawerTitle = computed(() => {
  return currentEditStrategyData.value?.name
    ? `编辑策略 - ${currentEditStrategyData.value.name}`
    : "编辑策略";
});

// 设置表单
const displaySettings = reactive({
  theme: appStore.theme || "dark",
  language: "zh-CN",
});

watch(() => displaySettings.theme, (newTheme) => {
  appStore.setTheme(newTheme);
});

const tradingSettings = reactive({
  defaultSymbol: "BTC/USDT",
  confirmOrder: true,
});

// 模拟数据
const recentActivities = ref<Activity[]>([
  {
    id: "1",
    type: "success",
    icon: SuccessFilled,
    text: "MA策略已启动",
    time: "5分钟前",
  },
  {
    id: "2",
    type: "info",
    icon: InfoFilled,
    text: "买入订单已成交",
    time: "15分钟前",
  },
  {
    id: "3",
    type: "warning",
    icon: WarningFilled,
    text: "数据同步延迟",
    time: "30分钟前",
  },
]);

const notifications = ref<Notification[]>([]);

const systemMessages = ref<SystemMessage[]>([
  {
    id: "1",
    text: "所有策略运行正常",
  },
  {
    id: "2",
    text: "市场数据实时同步",
  },
]);

// 计算属性
const statusIcon = computed(() => {
  switch (systemStatus.value) {
    case "success":
      return SuccessFilled;
    case "warning":
      return WarningFilled;
    case "error":
      return CircleCloseFilled;
    default:
      return InfoFilled;
  }
});

const connectionIcon = computed(() => {
  switch (connectionStatus.value) {
    case "success":
      return SuccessFilled;
    case "warning":
      return WarningFilled;
    case "error":
      return CircleCloseFilled;
    default:
      return InfoFilled;
  }
});

// 方法
// 提供全局方法给子组件使用
provide("switchToBacktestResult", (taskId: string, task: any) => {
  console.log("🔥 通过 provide 调用切换到回测结果:", taskId, task);
  handleSwitchToBacktestResult({ detail: { taskId, task } } as CustomEvent);
});

provide("openOrderManagementDetail", (orderSn: string) => {
  handleNavClick("orders");
  nextTick(() => {
    if (!tabSystemRef.value) {
      return;
    }
    if (typeof tabSystemRef.value.updateTab === "function") {
      tabSystemRef.value.updateTab("orders", {
        props: { initialOrderSn: orderSn },
      });
    }
    if (typeof tabSystemRef.value.selectTab === "function") {
      tabSystemRef.value.selectTab("orders");
    } else {
      activeTab.value = "orders";
    }
  });
});

provide("openWeightRuleEngine", (configId?: number) => {
  if (configId !== undefined) {
    weightRuleEngineConfigId.value = configId;
  }
  handleNavClick("/weight-rule-engine");
});

provide("weightRuleEngineConfigId", weightRuleEngineConfigId);

const handleNavClick = (routePath: string, icon?: string) => {
  const item = routeToTabKey[routePath];
  if (!item || !tabComponents[item]) {
    router.push(routePath);
    return;
  }

  const existingTab = tabs.value.find((tab) => tab.id === item);
  if (existingTab) {
    activeTab.value = item;
    return;
  }

  const tabIcons: Record<string, any> = {
    dashboard: "Monitor",
    strategies: "TrendCharts",
    "strategy-editor": "Edit",
    "strategy-templates": "Files",
    "strategy-optimization": "Aim",
    "backtest-results": "DataLine",
    trading: "Money",
    "exchange-mgmt": "Wallet",
    "realtime-trading": "Lightning",
    "trading-bots": "Setting",
    orders: "List",
    positions: "PieChart",
    accounts: "Wallet",
    "trading-logs": "Document",
    "risk-control": "Warning",
    alerts: "Bell",
    "market-data": "DataBoard",
    "market-kline-v1": "TrendCharts",
    "ai-radar": "Aim",
    "data-source-test": "Connection",
    "data-import": "Upload",
    "signal-service-management": "Setting",
    "price-signal-management": "TrendCharts",
    "weight-rule-engine": "Operation",
    "factor-mining": "Cpu",
    users: "User",
    settings: "Setting",
    logs: "Document",
    credits: "Money",
    "community-market": "ShoppingCart",
    "community-market:publish": "Edit",
    "community-market:my-purchases": "Wallet",
    "community-market:review": "CircleCheckFilled",
};

  if (tabComponents[item]) {
    const newTab: Tab = {
      id: item,
      title: tabTitles[item],
      icon: icon || tabIcons[item] || "Monitor",
      component: tabComponents[item],
    };

    if (tabSystemRef.value) {
      tabSystemRef.value.addTab(newTab);
      activeTab.value = item;
    } else {
      nextTick(() => {
        if (tabSystemRef.value) {
          tabSystemRef.value.addTab(newTab);
          activeTab.value = item;
        }
      });
    }
  }
};

const handleTabChange = (tab: Tab) => {
  activeTab.value = tab.id;
};

const handleTabClose = (tabId: string) => {
  tabs.value = tabs.value.filter((tab) => tab.id !== tabId);
};

const handleTabsUpdate = (updatedTabs: Tab[]) => {
  tabs.value = updatedTabs;
};

// 处理策略查看
const handleViewStrategy = (strategy: any) => {
  console.log("🔥 V3Layout handleViewStrategy called with:", strategy);

  const tabConfig: Tab = {
    id: `strategy-detail-${strategy.id}`,
    title: `策略详情 - ${strategy.name}`,
    icon: "TrendCharts",
    component: () => import("@/views/strategy/StrategyDetail.vue"),
    props: {
      strategyId: strategy.strategyId || strategy.id,
      strategy: strategy,
    },
  };

  if (tabSystemRef.value) {
    tabSystemRef.value.addTab(tabConfig);
  }
};

// 处理策略编辑
const handleEditStrategy = (strategy: any) => {
  console.log("🔥 V3Layout handleEditStrategy called with:", strategy);

  // 使用抽屉而不是标签页
  currentEditStrategyId.value = strategy.id;
  currentEditStrategyData.value = strategy;
  editStrategyDrawerOpen.value = true;
};

// 关闭编辑策略抽屉
const closeEditStrategyDrawer = () => {
  editStrategyDrawerOpen.value = false;
  currentEditStrategyId.value = null;
  currentEditStrategyData.value = null;
};

// 处理刷新策略列表 - 通过 TabSystem 触发刷新
const handleRefreshStrategies = () => {
  // TabSystem 会直接调用 StrategyList 的 loadStrategies 方法
  if (
    tabSystemRef.value &&
    typeof tabSystemRef.value.handleRefreshStrategies === "function"
  ) {
    tabSystemRef.value.handleRefreshStrategies();
  }
};

// 处理策略保存成功
const handleStrategySaved = () => {
  closeEditStrategyDrawer();
  ElMessage.success("策略保存成功");
  // 触发策略列表刷新
  handleRefreshStrategies();
};

// 处理创建策略
const handleCreateStrategy = () => {
  console.log("🔥 V3Layout handleCreateStrategy called");

  const tabConfig: Tab = {
    id: "strategy-create",
    title: "创建策略",
    icon: "Plus",
    component: () => import("@/views/strategy/CreateStrategy.vue"),
  };

  if (tabSystemRef.value) {
    tabSystemRef.value.addTab(tabConfig);
  }
};

// 处理创建策略成功
const handleCreateSuccess = () => {
  console.log(
    "🔥 V3Layout handleCreateSuccess called - refreshing strategy list",
  );

  // 切换到策略列表标签
  const strategyListTab = tabs.value.find((tab) => tab.id === "strategies");
  if (strategyListTab) {
    activeTab.value = "strategies";
  }

  // 这里可以通过其他方式刷新策略列表，比如通过事件总线或者直接调用策略列表的刷新方法
  ElMessage.success("策略创建成功！");
};

// 处理返回到列表
const handleBackToList = () => {
  console.log(
    "🔥 V3Layout handleBackToList called - switching to backtest results tab",
  );

  // 切换到回测任务列表标签
  const backtestResultsTab = tabs.value.find(
    (tab) => tab.id === "backtest-results",
  );
  if (backtestResultsTab) {
    activeTab.value = "backtest-results";
  } else {
    // 如果没有打开的任务列表tab，创建一个
    const tabConfig: Tab = {
      id: "backtest-results",
      title: "回测任务",
      icon: "DataLine",
      component: () => import("@/views/backtest/BacktestTaskList.vue"),
      closable: true,
      props: {},
    };

    if (tabSystemRef.value) {
      tabSystemRef.value.addTab(tabConfig);
    }
  }
};

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
};

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen();
  } else {
    document.exitFullscreen();
  }
};

const showNotifications = () => {
  notificationsOpen.value = true;
};

function addAiSignalNotification(symbol: string, direction: string, strength: number) {
  const dirText = direction === "LB" ? "做多" : "做空";
  const now = new Date();
  const timeStr = `${now.getHours().toString().padStart(2, "0")}:${now.getMinutes().toString().padStart(2, "0")}`;
  notifications.value.unshift({
    id: Date.now().toString(),
    type: "success",
    icon: InfoFilled,
    content: `AI信号 ${symbol} ${dirText} 强度:${strength}`,
    time: timeStr,
  });
  appStore.showNotification({
    type: "info",
    message: `AI信号 ${symbol} ${dirText}`,
    duration: 5000,
  });
}

provide("addAiSignalNotification", addAiSignalNotification);

const showSettings = () => {
  settingsOpen.value = true;
};

const handleUserCommand = async (command: string) => {
  if (command === "logout") {
    await authStore.logout();
  }
};

const showHelp = () => {
  ElMessage.info("帮助文档功能开发中");
};

const quickOrder = () => {
  ElMessage.info("快速下单功能开发中");
};

const pauseAllStrategies = () => {
  ElMessage.success("所有策略已暂停");
};

const refreshData = () => {
  ElMessage.success("数据已刷新");
};

const exportReport = () => {
  ElMessage.success("报告导出中...");
};

const getCpuColor = (percentage: number) => {
  if (percentage < 50) return "#67c23a";
  if (percentage < 80) return "#e6a23c";
  return "#f56c6c";
};

const getMemoryColor = (percentage: number) => {
  if (percentage < 60) return "#67c23a";
  if (percentage < 85) return "#e6a23c";
  return "#f56c6c";
};

// 更新市场时间
const updateMarketTime = () => {
  const now = new Date();
  marketTime.value = now.toLocaleTimeString("zh-CN");
};

// 键盘快捷键
const handleKeyDown = (event: KeyboardEvent) => {
  if (event.ctrlKey && event.key === "b") {
    event.preventDefault();
    toggleSidebar();
  } else if (event.ctrlKey && event.key === "r") {
    event.preventDefault();
    showRightPanel.value = !showRightPanel.value;
  } else if (event.key === "F1") {
    event.preventDefault();
    showHelp();
  }
};

// 生命周期
onMounted(() => {
  // 延迟初始化默认页签，确保 TabSystem 已完全挂载
  // 仅在没有任何页签时，且用户没有主动点击菜单时加载
  nextTick(() => {
    if (tabs.value.length === 0 && activeTab.value === "") {
      // 再等待一个 tick，确保所有组件都已挂载
      setTimeout(() => {
        if (tabs.value.length === 0 && activeTab.value === "") {
          handleNavClick("/dashboard");
        }
      }, 100);
    }
  });

  // 启动定时器
  const timer = setInterval(updateMarketTime, 1000);
  updateMarketTime();

  fetchSummary();
  summaryPollTimer = window.setInterval(fetchSummary, 15000);

  // 添加键盘事件监听
  document.addEventListener("keydown", handleKeyDown);

  // 监听切换到回测结果详情的事件
  const globalEventHandler = (event: CustomEvent) => {
    console.log("🔥 全局事件处理器: 收到事件", event.type, event.detail);
    handleSwitchToBacktestResult(event);
  };
  document.addEventListener("switchToBacktestResult", globalEventHandler);

  // 同时也监听 window 事件
  window.addEventListener("switchToBacktestResult", globalEventHandler);

  // 清理函数
  onUnmounted(() => {
    clearInterval(timer);
    if (summaryPollTimer !== null) {
      clearInterval(summaryPollTimer);
      summaryPollTimer = null;
    }
    document.removeEventListener("keydown", handleKeyDown);
    document.removeEventListener("switchToBacktestResult", globalEventHandler);
    window.removeEventListener("switchToBacktestResult", globalEventHandler);
  });
});
</script>

<style scoped>
.v3-layout {
  height: 100vh;
  display: flex;
  flex-direction: row;
  background: var(--bg-primary);
  color: var(--text-primary);
  overflow: hidden;
}

.layout-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* 顶部状态栏 */
.top-status-bar {
  height: 60px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.system-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar-collapse-btn {
  width: 30px;
  height: 30px;
  padding: 0;
}

.status-icon {
  font-size: 16px;
}

.status-icon.success {
  color: #67c23a;
}

.status-icon.warning {
  color: #e6a23c;
}

.status-icon.error {
  color: #f56c6c;
}

.status-text {
  font-size: 14px;
  color: var(--text-secondary);
}

.market-time {
  font-size: 13px;
  color: var(--text-muted);
}

.status-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.quick-stats {
  display: flex;
  gap: 60px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 140px;
}

.stat-content {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.stat-label {
  font-size: 11px;
  color: var(--text-muted);
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-change {
  font-size: 10px;
  font-weight: 500;
}

.stat-change.positive {
  color: #67c23a;
}

.stat-change.negative {
  color: #f56c6c;
}

.status-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-details {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.user-role {
  font-size: 12px;
  color: var(--text-muted);
}

.quick-actions {
  display: flex;
  gap: 8px;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.tab-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.right-panel {
  width: 300px;
  background: var(--bg-secondary);
  border-left: 1px solid var(--border-primary);
  display: flex;
  flex-direction: column;
}

.right-toolbar {
  width: 44px;
  background: var(--bg-secondary);
  border-left: 1px solid var(--border-primary);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
  gap: 6px;
  flex-shrink: 0;
}

.right-toolbar-btn {
  width: 32px;
  height: 32px;
  padding: 0;
}

.right-toolbar :deep(.el-badge) {
  line-height: 1;
}

.right-toolbar :deep(.el-badge__content.is-fixed) {
  top: 2px;
  right: 10px;
}

.panel-header {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 15px;
  border-bottom: 1px solid var(--border-primary);
}

.panel-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 15px;
}

.info-section {
  margin-bottom: 25px;
}

.info-section h4 {
  margin: 0 0 15px 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.info-item {
  margin-bottom: 12px;
}

.info-label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.quick-actions-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.quick-actions-grid .el-button {
  height: 32px;
  font-size: 12px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.activity-item {
  display: flex;
  gap: 10px;
  padding: 8px;
  background: var(--surface-elevated);
  border-radius: 6px;
}

.activity-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.activity-icon.success {
  color: #67c23a;
}

.activity-icon.info {
  color: #409eff;
}

.activity-icon.warning {
  color: #e6a23c;
}

.activity-content {
  flex: 1;
  min-width: 0;
}

.activity-text {
  font-size: 12px;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.activity-time {
  font-size: 11px;
  color: var(--text-muted);
}

/* 底部信息栏 */
.bottom-status-bar {
  height: 35px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  font-size: 12px;
}

.bottom-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 6px;
}

.connection-icon {
  font-size: 12px;
}

.connection-icon.success {
  color: #67c23a;
}

.connection-icon.warning {
  color: #e6a23c;
}

.connection-icon.error {
  color: #f56c6c;
}

.bottom-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.system-messages {
  display: flex;
  gap: 15px;
}

.message-item {
  color: var(--text-muted);
}

.bottom-right {
  display: flex;
  align-items: center;
}

.quick-shortcuts {
  display: flex;
  gap: 4px;
}

/* 通知中心 */
.notifications-list {
  padding: 15px;
}

.notification-item {
  margin-bottom: 15px;
  padding: 12px;
  background: var(--card-bg);
  border-radius: 6px;
}

.notification-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.notification-icon {
  font-size: 14px;
}

.notification-icon.success {
  color: #67c23a;
}

.notification-icon.warning {
  color: #e6a23c;
}

.notification-icon.info {
  color: #409eff;
}

.notification-time {
  font-size: 11px;
  color: var(--text-muted);
}

.notification-content {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.4;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .right-panel {
    width: 250px;
  }

  .quick-stats {
    gap: 40px;
  }
}

@media (max-width: 768px) {
  .top-status-bar {
    padding: 0 10px;
  }

  .status-center {
    display: none;
  }

  .user-details {
    display: none;
  }

  .right-panel {
    position: fixed;
    right: 44px;
    top: 60px;
    bottom: 35px;
    z-index: 1000;
    transform: translateX(100%);
    transition: transform 0.3s ease;
  }

  .right-panel.show {
    transform: translateX(0);
  }

  .bottom-status-bar {
    padding: 0 10px;
  }

  .system-messages {
    display: none;
  }
}

/* Element Plus 样式覆盖 */
:deep(.el-button) {
  background: transparent;
  border: none;
  color: var(--text-secondary);
}

:deep(.el-button:hover) {
  color: var(--text-primary);
  background: var(--bg-hover);
}

:deep(.el-progress-bar__outer) {
  background-color: var(--border-primary);
}

:deep(.el-drawer) {
  background: var(--bg-secondary);
}

:deep(.el-drawer__header) {
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-primary);
}

:deep(.el-drawer__body) {
  background: var(--bg-primary);
}

:deep(.el-dialog) {
  background: var(--bg-secondary);
  border: 1px solid var(--border-primary);
}

:deep(.el-dialog__header) {
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-primary);
}

:deep(.el-dialog__body) {
  background: var(--bg-primary);
  color: var(--text-primary);
}

:deep(.el-tabs__nav-wrap::after) {
  background-color: var(--border-primary);
}

:deep(.el-tabs__item) {
  color: var(--text-secondary);
}

:deep(.el-tabs__item.is-active) {
  color: var(--text-primary);
}

:deep(.el-form-item__label) {
  color: var(--text-primary);
}

:deep(.el-input__inner) {
  background: var(--surface-elevated);
  border-color: var(--border-primary);
  color: var(--text-primary);
}
</style>
