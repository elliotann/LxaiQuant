<template>
  <div class="sidebar-nav" :class="{ collapsed: isCollapsed }">
    <div class="nav-header">
      <div class="logo">
        <div class="logo-svg">
          <!-- 猞猁 (Lynx) 形象 LOGO - 象征 AI 敏锐洞察 -->
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="32" height="32" rx="8" fill="url(#lynx-grad)" />
            <!-- 猞猁头部轮廓 -->
            <path d="M16 6L10 14V24L16 28L22 24V14L16 6Z" fill="white" fill-opacity="0.1"/>
            <!-- 猞猁耳朵 (尖锐 tufted ears) -->
            <path d="M10 14L7 4L13 11" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M22 14L25 4L19 11" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <!-- 眼睛 (AI 核心) -->
            <path d="M12 18H14M18 18H20" stroke="#00D2FF" stroke-width="2" stroke-linecap="round"/>
            <!-- 几何连接线 (Quant/AI) -->
            <path d="M16 22V28M10 24H22" stroke="white" stroke-width="1" stroke-opacity="0.5"/>
            <defs>
              <linearGradient id="lynx-grad" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
                <stop stop-color="#409EFF" />
                <stop offset="1" stop-color="#00D2FF" />
              </linearGradient>
            </defs>
          </svg>
        </div>
        <span class="logo-text">LxAiQuant</span>
      </div>
    </div>

    <div class="nav-content" :class="{ collapsed: isCollapsed }">
      <!-- 仪表盘 - 始终显示 -->
      <div class="nav-section">
        <div class="section-title" v-if="!isCollapsed">仪表盘</div>
        <div class="nav-items">
          <div
            class="nav-item"
            :class="{ active: activeItem === '/dashboard' }"
            @click="selectNavItem('/dashboard')"
          >
            <el-icon class="nav-icon"><Monitor /></el-icon>
            <span class="nav-text" v-if="!isCollapsed">总览</span>
          </div>
        </div>
      </div>

      <!-- 动态菜单 -->
      <template v-for="(section, index) in menuSections" :key="index">
        <div v-if="(section.children && section.children.length > 0) || section.routePath" class="nav-section">
          <div class="section-title" v-if="!isCollapsed">{{ section.menuName }}</div>
          <div class="nav-items">
            <template v-if="section.children && section.children.length > 0">
              <div
                v-for="child in section.children"
                :key="child.menuCode"
                class="nav-item"
                :class="{ active: activeItem === child.routePath }"
                @click="selectNavItem(child.routePath, child.icon)"
              >
                <el-icon class="nav-icon">
                  <component :is="getIconComponent(child.icon)" />
                </el-icon>
                <span class="nav-text" v-if="!isCollapsed">{{ child.menuName }}</span>
              </div>
            </template>
            <template v-else>
              <div
                class="nav-item"
                :class="{ active: activeItem === section.routePath }"
                @click="selectNavItem(section.routePath, section.icon)"
              >
                <el-icon class="nav-icon">
                  <component :is="getIconComponent(section.icon)" />
                </el-icon>
                <span class="nav-text" v-if="!isCollapsed">{{ section.menuName }}</span>
              </div>
            </template>
          </div>
        </div>
      </template>
    </div>

    <div class="nav-footer" :class="{ collapsed: isCollapsed }">
      <div class="nav-item" @click="toggleTheme">
        <el-icon class="nav-icon"
          ><Sunny v-if="isDarkTheme" /><Moon v-else
        /></el-icon>
        <span class="nav-text" v-if="!isCollapsed">{{
          isDarkTheme ? "浅色主题" : "深色主题"
        }}</span>
      </div>
      <div class="nav-item" @click="showHelp">
        <el-icon class="nav-icon"><QuestionFilled /></el-icon>
        <span class="nav-text" v-if="!isCollapsed">帮助</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { useAppStore } from "@/stores/app";
import { getMenus } from "@/api/admin";
import {
  Monitor,
  TrendCharts,
  Files,
  VideoPlay,
  DataLine,
  Aim,
  Cpu,
  Money,
  Lightning,
  List,
  PieChart,
  Wallet,
  Warning,
  Bell,
  Connection,
  Upload,
  Operation,
  User,
  Setting,
  Document,
  Sunny,
  Moon,
  QuestionFilled,
  Search,
  DataBoard,
  Star,
  Edit,
  Key,
} from "@element-plus/icons-vue";

interface Props {
  modelValue?: string;
  collapsed?: boolean;
}

interface Emits {
  (e: "update:modelValue", value: string): void;
  (e: "update:collapsed", value: boolean): void;
  (e: "item-click", item: string, icon?: string): void;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: "dashboard",
  collapsed: false,
});

const emit = defineEmits<Emits>();

const activeItem = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value),
});

const isCollapsed = computed({
  get: () => props.collapsed,
  set: (value) => emit("update:collapsed", value),
});

const appStore = useAppStore();

const isDarkTheme = computed(() => appStore.isDarkMode);

const menuSections = ref<any[]>([]);

const iconMap: Record<string, any> = {
  Monitor, TrendCharts, Files, VideoPlay, DataLine, Aim, Cpu,
  Money, Lightning, List, PieChart, Wallet, Warning, Bell,
  Connection, Upload, Operation, User, Setting, Document,
  Search, DataBoard, Star, Edit, Key,
};

function getIconComponent(iconName: string) {
  return iconMap[iconName] || Monitor;
}

const selectNavItem = (routePath: string, icon?: string) => {
  activeItem.value = routePath;
  emit("item-click", routePath, icon);
};

const fetchMenu = async () => {
  try {
    const res: any = await getMenus();
    const roots = Array.isArray(res) ? res : res.data || [];
    menuSections.value = roots.filter((m: any) => m.menuCode !== 'dashboard');
  } catch (e) {
    menuSections.value = [];
  }
};

onMounted(() => {
  fetchMenu();
});

const toggleTheme = () => {
  appStore.setTheme(appStore.isDarkMode ? "light" : "dark");
  ElMessage.success(`已切换到${appStore.isDarkMode ? "浅色" : "深色"}主题`);
};

const showHelp = () => {
  ElMessage.info("帮助文档功能开发中");
};
</script>

<style scoped>
.sidebar-nav {
  width: 240px;
  height: 100vh;
  background: var(--secondary-bg);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease;
}

.sidebar-nav.collapsed {
  width: 64px;
}

.nav-header {
  height: 60px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 0 16px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-svg {
  display: flex;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 0 5px rgba(64, 158, 255, 0.3));
}

.logo-text {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--primary-text);
  white-space: nowrap;
}

.nav-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}

.nav-content.collapsed {
  padding: 8px 0;
}

.nav-section {
  margin-bottom: 16px;
}

.section-title {
  padding: 0 16px 8px 16px;
  font-size: 13px;
  font-weight: 700;
  color: var(--muted-text);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.nav-items {
  display: flex;
  flex-direction: column;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--secondary-text);
  border-radius: 0;
  margin: 0 8px;
}

.nav-item:hover {
  background: var(--hover-bg);
  color: var(--primary-text);
}

.nav-item.active {
  background: rgba(0, 255, 136, 0.1);
  color: var(--primary-text);
  border-left: 3px solid var(--primary-text);
  margin-left: 8px;
  padding-left: 13px;
}

.nav-icon {
  font-size: 18px;
  min-width: 18px;
  text-align: center;
}

.nav-text {
  font-size: 14px;
  white-space: nowrap;
}

.nav-footer {
  border-top: 1px solid var(--border-color);
  padding: 12px 0;
}

.nav-footer.collapsed {
  padding: 8px 0;
}

.nav-footer .nav-item {
  margin: 0 8px;
  padding: 8px 16px;
  border-radius: 4px;
}

.nav-footer .nav-item:hover {
  background: var(--hover-bg);
}

/* 滚动条样式 */
.nav-content::-webkit-scrollbar {
  width: 6px;
}

.nav-content::-webkit-scrollbar-track {
  background: var(--primary-bg);
}

.nav-content::-webkit-scrollbar-thumb {
  background: var(--brand-secondary);
  border-radius: 3px;
}

.nav-content::-webkit-scrollbar-thumb:hover {
  background: var(--primary-text);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .sidebar-nav {
    position: fixed;
    left: 0;
    top: 0;
    z-index: 1000;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }

  .sidebar-nav.mobile-open {
    transform: translateX(0);
  }

  .sidebar-nav.collapsed {
    width: 240px;
  }
}

/* 动画效果 */
.nav-item {
  position: relative;
  overflow: hidden;
}

.nav-item::before {
  content: "";
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(0, 255, 136, 0.2),
    transparent
  );
  transition: left 0.5s ease;
}

.nav-item:hover::before {
  left: 100%;
}
</style>

<style>
html.dark .nav-item:hover {
  background: oklch(.7 .16 31 / 0.08) !important;
}

html.dark .nav-item.active {
  background: oklch(.7 .16 31 / 0.08) !important;
}
</style>
