<template>
  <div class="tab-system">
    <div class="tab-header">
      <div class="tab-list">
        <div
          v-for="tab in computedTabs"
          :key="tab.id"
          class="tab-item"
          :class="{ active: activeTabId === tab.id }"
          @click="selectTab(tab.id)"
        >
          <el-icon class="tab-icon" v-if="tab.icon">
            <component :is="tab.icon" />
          </el-icon>
          <span class="tab-title">{{ tab.title }}</span>
          <span
            class="tab-close"
            @click.stop="closeTab(tab.id)"
            v-if="!tab.pinned"
          >
            <el-icon><Close /></el-icon>
          </span>
        </div>
      </div>

      <div class="tab-actions">
        <el-dropdown @command="handleDropdownCommand" trigger="click">
          <el-button size="small" text>
            <el-icon><MoreFilled /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="close-all">关闭所有</el-dropdown-item>
              <el-dropdown-item command="close-others"
                >关闭其他</el-dropdown-item
              >
              <el-dropdown-item command="close-left">关闭左侧</el-dropdown-item>
              <el-dropdown-item command="close-right"
                >关闭右侧</el-dropdown-item
              >
              <el-dropdown-item divided command="refresh"
                >刷新当前</el-dropdown-item
              >
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="tab-content">
      <div
        v-for="tab in computedTabs"
        :key="tab.id"
        class="tab-pane"
        :class="{ active: activeTabId === tab.id }"
      >
        <!-- 🎯 性能优化：使用 v-if 控制组件创建/销毁，而不是只通过 CSS 隐藏 -->
        <!-- 这样可以确保非活动 tab 的组件被销毁，触发 beforeUnmount 钩子 -->
        <Suspense v-if="activeTabId === tab.id">
          <template #default>
            <component
              :is="getComponent(tab.component)"
              v-bind="tab.props"
              :ref="
                (el: any) => {
                  if (tab.component === 'StrategyList' && el)
                    strategyListRefs[tab.id] = el;
                }
              "
              @view-strategy="handleViewStrategy"
              @edit-strategy="handleEditStrategy"
              @create-strategy="handleCreateStrategy"
              @back-to-list="handleBackToList"
              @back-to-detail="handleBackToDetail"
              @save-success="handleSaveSuccess"
              @create-success="handleCreateSuccess"
              @select="handleTemplateSelect"
            />
          </template>
          <template #fallback>
            <div class="loading-placeholder">
              <el-icon class="loading-icon"><Loading /></el-icon>
              <span>加载中...</span>
            </div>
          </template>
        </Suspense>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  ref,
  computed,
  watch,
  markRaw,
  defineAsyncComponent,
  getCurrentInstance,
  h,
} from "vue";
import { ElMessage } from "element-plus";
import { Close, MoreFilled, Loading } from "@element-plus/icons-vue";

// 组件映射
const componentMap = {
  DashboardOverview: defineAsyncComponent(
    () => import("@/views/dashboard/DashboardOverview.vue"),
  ),
  StrategyList: defineAsyncComponent(
    () => import("@/views/strategy/StrategyList.vue"),
  ),
  StrategyDetail: defineAsyncComponent(
    () => import("@/views/strategy/StrategyDetail.vue"),
  ),
  EditStrategy: defineAsyncComponent(
    () => import("@/views/strategy/EditStrategy.vue"),
  ),
  CreateStrategy: defineAsyncComponent(
    () => import("@/views/strategy/CreateStrategy.vue"),
  ),
  StrategyTemplates: defineAsyncComponent(
    () => import("@/components/strategy/TemplateList.vue"),
  ),
  ParameterOptimization: defineAsyncComponent(
    () => import("@/views/optimization/ParameterOptimization.vue"),
  ),
  BacktestSettings: defineAsyncComponent(
    () => import("@/views/backtest/BacktestSettings.vue"),
  ),
  TradingPanel: defineAsyncComponent(
    () => import("@/views/trading/TradingPanel.vue"),
  ),
  OrdersManagement: defineAsyncComponent(
    () => import("@/views/trading/OrdersManagement.vue"),
  ),
  PositionsManagement: defineAsyncComponent(
    () => import("@/views/trading/PositionsManagement.vue"),
  ),
  Accounts: defineAsyncComponent(() => import("@/views/accounts/Accounts.vue")),
  Monitoring: defineAsyncComponent(
    () => import("@/views/monitoring/Monitoring.vue"),
  ),
  Users: defineAsyncComponent(() => import("@/views/user/Users.vue")),
  Settings: defineAsyncComponent(() => import("@/views/settings/Settings.vue")),
  SystemLogs: defineAsyncComponent(() => import("@/views/logs/SystemLogs.vue")),
};

// 回退组件：当组件解析失败时使用，避免运行时报错
const FallbackComponent = {
  name: "FallbackComponent",
  setup() {
    return () =>
      h(
        "div",
        {
          class: "missing-component",
          style: {
            padding: "16px",
            color: "#f56c6c",
            fontSize: "13px",
          },
        },
        "组件加载失败：目标组件未找到或加载出错",
      );
  },
};

interface Tab {
  id: string;
  title: string;
  icon?: any;
  component: any;
  props?: Record<string, any>;
  pinned?: boolean;
  path?: string;
}

interface Props {
  modelValue?: string;
  tabs?: Tab[];
  onViewStrategy?: (strategy: any) => void;
  onEditStrategy?: (strategy: any) => void;
  onCreateStrategy?: () => void;
  onBackToList?: () => void;
  onBackToDetail?: () => void;
  onSaveSuccess?: () => void;
  onCreateSuccess?: () => void;
}

interface Emits {
  (e: "update:modelValue", value: string): void;
  (e: "tab-change", tab: Tab): void;
  (e: "tab-close", tabId: string): void;
  (e: "tabs-update", tabs: Tab[]): void;
  (e: "view-strategy", strategy: any): void;
  (e: "edit-strategy", strategy: any): void;
  (e: "create-strategy"): void;
  (e: "back-to-list"): void;
  (e: "back-to-detail"): void;
  (e: "save-success"): void;
  (e: "create-success"): void;
  (e: "select", template: any): void;
}

const instance = getCurrentInstance();
const getContext = () => instance;

const props = withDefaults(defineProps<Props>(), {
  modelValue: "",
  tabs: () => [],
});

// 调试props
console.log("🔥 TabSystem props:", props);
console.log("🔥 TabSystem props keys:", Object.keys(props));
console.log("🔥 TabSystem onViewStrategy:", props.onViewStrategy);
console.log("🔥 TabSystem attrs:", getContext()?.attrs || {});

const emit = defineEmits<Emits>();

const internalTabs = ref<Tab[]>([...props.tabs]);
const activeTabId = ref(props.modelValue || props.tabs[0]?.id || "");

// 计算属性
const computedTabs = computed({
  get: () => internalTabs.value,
  set: (value) => {
    internalTabs.value = value;
    emit("tabs-update", value);
  },
});

const activeTab = computed(() => {
  return computedTabs.value.find((tab) => tab.id === activeTabId.value);
});

// 监听 props 变化并同步；若父列表比当前短，说明父尚未收到 addTab 的 tabs-update（时序问题），不覆盖以免刚加的 tab 被冲掉
watch(
  () => props.tabs,
  (newTabs) => {
    const next = Array.isArray(newTabs) ? [...newTabs] : [];
    if (next.length < internalTabs.value.length) {
      return;
    }
    internalTabs.value = next;
  },
  { deep: true },
);

watch(
  () => props.modelValue,
  (newValue) => {
    if (newValue && newValue !== activeTabId.value) {
      activeTabId.value = newValue;
    }
  },
);

// 方法
const selectTab = (tabId: string) => {
  console.log("🔥 TabSystem selectTab called with:", tabId);
  console.log("🔥 Current activeTabId before:", activeTabId.value);

  activeTabId.value = tabId;
  console.log("🔥 Set activeTabId to:", activeTabId.value);

  emit("update:modelValue", tabId);

  const tab = computedTabs.value.find((t) => t.id === tabId);
  if (tab) {
    console.log("🔥 Found tab, emitting tab-change:", tab);
    emit("tab-change", tab);
  } else {
    console.error("🔥 Tab not found for id:", tabId);
  }
};

const closeTab = (tabId: string) => {
  const tab = computedTabs.value.find((t) => t.id === tabId);
  if (!tab || tab.pinned) return;

  const index = computedTabs.value.findIndex((t) => t.id === tabId);
  const newTabs = computedTabs.value.filter((t) => t.id !== tabId);

  // 如果关闭的是当前活动标签页，需要选择新的活动标签页
  if (tabId === activeTabId.value) {
    if (newTabs.length > 0) {
      // 优先选择关闭标签页右侧的标签页，如果没有则选择左侧的
      const newIndex = Math.min(index, newTabs.length - 1);
      selectTab(newTabs[newIndex].id);
    } else {
      activeTabId.value = "";
    }
  }

  computedTabs.value = newTabs;
  emit("tab-close", tabId);
};

const closeAllTabs = () => {
  const pinnedTabs = computedTabs.value.filter((tab) => tab.pinned);
  computedTabs.value = pinnedTabs;

  if (pinnedTabs.length > 0) {
    selectTab(pinnedTabs[0].id);
  } else {
    activeTabId.value = "";
  }

  ElMessage.success("已关闭所有标签页");
};

const closeOtherTabs = () => {
  const currentTab = computedTabs.value.find(
    (tab) => tab.id === activeTabId.value,
  );
  const pinnedTabs = computedTabs.value.filter((tab) => tab.pinned);

  const newTabs = [...new Set([...pinnedTabs, currentTab])].filter(Boolean);
  computedTabs.value = newTabs;

  ElMessage.success("已关闭其他标签页");
};

const closeLeftTabs = () => {
  const currentIndex = computedTabs.value.findIndex(
    (tab) => tab.id === activeTabId.value,
  );
  if (currentIndex === -1) return;

  const pinnedTabs = computedTabs.value.filter((tab) => tab.pinned);
  const leftTabs = computedTabs.value.slice(0, currentIndex);
  const keepTabs = leftTabs.filter((tab) => tab.pinned);

  computedTabs.value = [...keepTabs, ...computedTabs.value.slice(currentIndex)];
  ElMessage.success("已关闭左侧标签页");
};

const closeRightTabs = () => {
  const currentIndex = computedTabs.value.findIndex(
    (tab) => tab.id === activeTabId.value,
  );
  if (currentIndex === -1) return;

  computedTabs.value = computedTabs.value.slice(0, currentIndex + 1);
  ElMessage.success("已关闭右侧标签页");
};

const refreshCurrentTab = () => {
  const currentTab = activeTab.value;
  if (currentTab) {
    try {
      // 查找当前活动的标签页内容
      const activePane = document.querySelector(".tab-pane.active");
      if (activePane) {
        // 发送自定义事件到当前组件
        const event = new CustomEvent("tab-refresh", {
          detail: {
            tabId: currentTab.id,
            tabTitle: currentTab.title,
          },
        });
        activePane.dispatchEvent(event);
        console.log(`发送刷新事件到标签页: ${currentTab.title}`);

        // 同时尝试通过组件实例调用刷新方法
        // 遍历所有子元素，找到Vue组件实例
        const findVueInstance = (element) => {
          if (element.__vue__) return element.__vue__;
          for (const child of element.children) {
            const instance = findVueInstance(child);
            if (instance) return instance;
          }
          return null;
        };

        const componentInstance = findVueInstance(activePane);
        if (componentInstance) {
          // 尝试不同的刷新方法名
          const refreshMethods = [
            "refresh",
            "reloadData",
            "loadData",
            "reload",
            "fetchData",
          ];
          let refreshed = false;

          for (const method of refreshMethods) {
            if (typeof componentInstance[method] === "function") {
              componentInstance[method]();
              console.log(`调用组件刷新方法: ${method}`);
              refreshed = true;
              break;
            }
          }

          if (
            !refreshed &&
            typeof componentInstance.$forceUpdate === "function"
          ) {
            componentInstance.$forceUpdate();
            console.log("强制更新组件");
            refreshed = true;
          }

          if (refreshed) {
            ElMessage.success(`已刷新: ${currentTab.title}`);
          } else {
            ElMessage.warning(`${currentTab.title} 组件不支持自动刷新`);
          }
        } else {
          ElMessage.success(`发送刷新信号: ${currentTab.title}`);
        }
      } else {
        ElMessage.warning("找不到活动标签页内容");
      }
    } catch (error) {
      console.error("刷新标签页时出错:", error);
      ElMessage.error(`刷新失败: ${error.message}`);
    }
  } else {
    ElMessage.warning("没有活动标签页");
  }
};

const handleDropdownCommand = (command: string) => {
  switch (command) {
    case "close-all":
      closeAllTabs();
      break;
    case "close-others":
      closeOtherTabs();
      break;
    case "close-left":
      closeLeftTabs();
      break;
    case "close-right":
      closeRightTabs();
      break;
    case "refresh":
      refreshCurrentTab();
      break;
  }
};

// 添加标签页的方法（同步后需通知父组件，避免父 tabs 未更新导致 watch 覆盖新 tab）
const addTab = (tab: Tab) => {
  console.log("🔥 TabSystem addTab called with:", tab);
  console.log("🔥 Current tabs before add:", computedTabs.value);

  const existingTab = computedTabs.value.find((t) => t.id === tab.id);
  if (existingTab) {
    console.log("🔥 Tab already exists, selecting it:", existingTab);
    selectTab(tab.id);
    return existingTab;
  }

  console.log("🔥 Adding new tab to tabs array");
  computedTabs.value.push(tab);
  emit("tabs-update", [...computedTabs.value]);
  console.log("🔥 Tabs after add:", computedTabs.value);

  console.log("🔥 Selecting new tab:", tab.id);
  selectTab(tab.id);
  return tab;
};

// 更新标签页的方法（变更后同步父组件）
const updateTab = (tabId: string, updates: Partial<Tab>) => {
  const index = computedTabs.value.findIndex((t) => t.id === tabId);
  if (index !== -1) {
    computedTabs.value[index] = { ...computedTabs.value[index], ...updates };
    emit("tabs-update", [...computedTabs.value]);
  }
};

// 检查标签页是否存在
const hasTab = (tabId: string) => {
  return computedTabs.value.some((t) => t.id === tabId);
};

// 解析组件
const getComponent = (component: any) => {
  if (typeof component === "string") {
    const mapped = componentMap[component];
    if (!mapped) {
      console.error("TabSystem: 未找到组件映射，对应 key 为:", component);
      return FallbackComponent;
    }
    return mapped;
  }
  // 如果是函数（动态导入），需要用 defineAsyncComponent 包装
  if (typeof component === "function") {
    return defineAsyncComponent(component);
  }
  if (component) {
    return component;
  }

  console.error("TabSystem: 收到无效的组件定义:", component);
  return FallbackComponent;
};

// 事件处理函数
const handleViewStrategy = (strategy) => {
  console.log("🔥 TabSystem handleViewStrategy called with:", strategy);
  console.log("🔥 Props onViewStrategy exists:", !!props.onViewStrategy);

  if (props.onViewStrategy) {
    console.log("🔥 Calling props.onViewStrategy");
    props.onViewStrategy(strategy);
  } else {
    console.log("🔥 No onViewStrategy prop, emitting event");
    console.log("🔥 About to emit view-strategy event with:", strategy);
    emit("view-strategy", strategy);
    console.log("🔥 view-strategy event emitted");
  }
};

const handleEditStrategy = (strategy) => {
  if (props.onEditStrategy) {
    props.onEditStrategy(strategy);
  } else {
    emit("edit-strategy", strategy);
  }
};

const handleCreateStrategy = () => {
  if (props.onCreateStrategy) {
    props.onCreateStrategy();
  } else {
    emit("create-strategy");
  }
};

const handleBackToList = () => {
  if (props.onBackToList) {
    props.onBackToList();
  } else {
    emit("back-to-list");
  }
};

const handleBackToDetail = () => {
  if (props.onBackToDetail) {
    props.onBackToDetail();
  } else {
    emit("back-to-detail");
  }
};

const handleSaveSuccess = () => {
  if (props.onSaveSuccess) {
    props.onSaveSuccess();
  } else {
    emit("save-success");
  }
};

const handleCreateSuccess = () => {
  if (props.onCreateSuccess) {
    props.onCreateSuccess();
  } else {
    emit("create-success");
  }
};

// 策略列表组件引用
const strategyListRefs = ref<Record<string, any>>({});

const handleRefreshStrategies = () => {
  // 查找策略列表标签页并直接调用其刷新方法
  const strategyListTab = computedTabs.value.find(
    (tab) => tab.component === "StrategyList" || tab.id === "strategies",
  );

  if (strategyListTab && strategyListRefs.value[strategyListTab.id]) {
    const ref = strategyListRefs.value[strategyListTab.id];
    if (ref && typeof ref.loadStrategies === "function") {
      ref.loadStrategies();
      return;
    }
  }

  // 如果没有找到引用，emit 事件（向后兼容）
  emit("refresh-strategies");
};

// 处理策略模板选择
const handleTemplateSelect = (template: any) => {
  console.log("🔥 TabSystem handleTemplateSelect called with:", template);

  // 如果模板有完整的策略信息，创建一个新的策略编辑标签页
  if (template && template.config) {
    const strategyConfig = {
      id: `new-strategy-${Date.now()}`,
      name: template.name,
      description: template.description,
      code: template.code,
      parameters: template.parameters,
      config: template.config,
      category: template.category,
      tags: template.tags,
      difficulty: template.difficulty,
      language: template.language,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      status: "draft",
    };

    // 创建策略管理标签页，并通过路由参数控制显示创建策略页面
    const tabConfig: Tab = {
      id: `strategy-management-${Date.now()}`,
      title: `创建策略 - ${template.name}`,
      icon: "Edit",
      component: () => import("@/views/strategy/StrategyManagement.vue"),
      props: {
        initialMode: "create",
        templateData: strategyConfig,
        isFromTemplate: true,
      },
    };

    console.log("🔥 Creating strategy from template, tab config:", tabConfig);

    // 添加新的标签页
    addTab(tabConfig);

    // 显示成功消息
    ElMessage.success(`已使用模板"${template.name}"创建策略`);
  } else {
    // 如果只是模板信息，发出select事件
    emit("select", template);
  }
};

// 暴露方法给父组件
defineExpose({
  addTab,
  updateTab,
  hasTab,
  closeTab,
  selectTab,
  handleRefreshStrategies,
  tabs: computedTabs.value,
  activeTab: activeTab.value,
});
</script>

<style scoped>
.tab-system {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-secondary);
}

.tab-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-primary);
  background: var(--surface-elevated);
  height: 40px;
  min-height: 40px;
  max-height: 40px;
}

.tab-list {
  display: flex;
  align-items: center;
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  flex-wrap: nowrap;
  min-height: 40px;
}

.tab-list::-webkit-scrollbar {
  height: 2px;
}

.tab-list::-webkit-scrollbar-track {
  background: var(--bg-primary);
}

.tab-list::-webkit-scrollbar-thumb {
  background: var(--btn-primary);
  border-radius: 1px;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  transition: all 0.2s;
  border-right: 1px solid var(--border-primary);
  white-space: nowrap;
  user-select: none;
  min-width: 80px;
  max-width: 200px;
  flex-shrink: 0;
  height: 40px;
}

.tab-item:hover {
  background: var(--bg-hover);
}

.tab-item.active {
  background: var(--bg-secondary);
  color: var(--text-primary);
  border-bottom: 2px solid var(--text-primary);
}

.tab-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.tab-title {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.tab-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 2px;
  opacity: 0;
  transition: all 0.2s;
  flex-shrink: 0;
}

.tab-item:hover .tab-close {
  opacity: 1;
}

.tab-close:hover {
  background: var(--market-down);
  color: white;
}

.tab-close .el-icon {
  font-size: 12px;
}

.tab-actions {
  display: flex;
  align-items: center;
  padding: 0 8px;
  border-left: 1px solid var(--border-primary);
}

.tab-content {
  flex: 1;
  overflow-y: auto; /* 改为允许垂直滚动 */
  overflow-x: hidden;
  position: relative;
  padding-bottom: 50px; /* 为底部状态栏留出空间 */
}

.tab-pane {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  min-height: 100%; /* 改为 min-height，允许内容超出 */
  opacity: 0;
  visibility: hidden;
  transition:
    opacity 0.2s,
    visibility 0.2s;
}

.tab-pane.active {
  opacity: 1;
  visibility: visible;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .tab-item {
    padding: 8px 8px;
    min-width: 60px;
  }

  .tab-title {
    font-size: 12px;
  }

  .tab-icon {
    font-size: 12px;
  }
}

/* 动画效果 */
.tab-item {
  position: relative;
}

.tab-item::after {
  content: "";
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: var(--text-primary);
  transform: scaleX(0);
  transition: transform 0.2s ease;
}

.tab-item.active::after {
  transform: scaleX(1);
}

/* 加载占位符 */
.loading-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  gap: 12px;
}

.loading-icon {
  font-size: 24px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* 自定义滚动条样式 */
.tab-list {
  scrollbar-width: thin;
  scrollbar-color: var(--btn-primary) var(--bg-primary);
}

/* Element Plus 组件样式覆盖 */
:deep(.el-button) {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  padding: 4px 8px;
  height: 24px;
}

:deep(.el-button:hover) {
  background: var(--bg-hover);
  color: var(--text-primary);
}

:deep(.el-dropdown-menu) {
  background: var(--bg-secondary);
  border: 1px solid var(--border-primary);
}

:deep(.el-dropdown-menu__item) {
  color: var(--text-secondary);
  font-size: 12px;
}

:deep(.el-dropdown-menu__item:hover) {
  background: var(--bg-hover);
  color: var(--text-primary);
}
</style>
