<template>
  <div class="ai-radar-page">
    <div class="radar-section" v-if="opportunities.length > 0">
      <div class="radar-header">
        <div class="radar-header-left">
          <h2 class="radar-title">AI雷达</h2>
          <p class="radar-subtitle">多市场交易机会扫描，AI智能识别潜在信号</p>
        </div>
        <div class="header-actions">
          <el-tag v-if="lastUpdated" type="info" effect="plain" size="small">
            最后更新: {{ lastUpdated }}
          </el-tag>
          <el-button
            :icon="Refresh"
            :loading="loading"
            size="small"
            class="radar-refresh-btn"
            @click="loadData(true)"
          >
            刷新
          </el-button>
        </div>
      </div>

      <div
        class="radar-carousel"
        @mouseenter="hover = true"
        @mouseleave="hover = false"
      >
        <div class="radar-track" :class="{ paused: hover }" :style="trackStyle">
          <div
            class="radar-card"
            v-for="(opp, idx) in carouselItems"
            :key="'opp-' + idx"
            :class="[opp.impact]"
          >
            <div class="rc-head">
              <span class="rc-symbol">{{ opp.symbol }}</span>
              <span class="rc-market" :class="'rc-market-' + (opp.market || '').toLowerCase()">
                {{ marketLabel(opp.market) }}
              </span>
            </div>
            <div class="rc-metrics">
              <div class="rc-metric">
                <span class="rc-metric-label">价格</span>
                <span class="rc-metric-value">${{ formatPrice(opp.price) }}</span>
              </div>
              <div class="rc-metric">
                <span class="rc-metric-label">24h涨跌</span>
                <span class="rc-metric-value" :class="opp.change_24h >= 0 ? 'rc-up' : 'rc-down'">
                  {{ opp.change_24h >= 0 ? '+' : '' }}{{ (opp.change_24h || 0).toFixed(2) }}%
                </span>
              </div>
              <div class="rc-metric">
                <span class="rc-metric-label">信号</span>
                <span class="rc-metric-value rc-signal-val" :class="'rc-signal-' + (opp.signal || '')">
                  {{ signalLabel(opp.signal) }}
                </span>
              </div>
            </div>
            <div class="rc-footer">
              <span class="rc-reason">{{ opp.reason }}</span>
              <span class="rc-impact-badge" :class="'impact-' + opp.impact">
                {{ impactLabel(opp.impact) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-card class="workspace-card" shadow="never">
      <el-tabs v-model="activeTab" class="workspace-tabs">
        <el-tab-pane label="即时分析" name="quick">
          <div class="tab-body">
            <div class="ai-analysis-container embedded">
              <div class="main-content-full">
                <div class="top-index-bar">
                  <div class="indicator-box fear-greed" :class="fearGreedLevel">
                    <span class="ind-label">F&G</span>
                    <span class="ind-value">{{ fearGreed }}</span>
                  </div>
                  <div class="indicator-box vix" :class="vixLevel">
                    <span class="ind-label">VIX</span>
                    <span class="ind-value">{{ vix }}</span>
                  </div>
                  <div class="indicator-box dxy">
                    <span class="ind-label">DXY</span>
                    <span class="ind-value">{{ dxy }}</span>
                  </div>

                  <div class="indices-marquee">
                    <div class="marquee-track" v-if="marqueeItems.length > 0">
                      <div class="index-item" v-for="(idx, i) in marqueeItems" :key="idx.symbol + '-' + i">
                        <span class="idx-flag">{{ idx.flag }}</span>
                        <span class="idx-symbol">{{ idx.symbol }}</span>
                        <span class="idx-price">{{ formatPrice(idx.price) }}</span>
                        <span class="idx-change" :class="idx.change >= 0 ? 'up' : 'down'">
                          {{ idx.change >= 0 ? '▲' : '▼' }} {{ Math.abs(idx.change).toFixed(2) }}%
                        </span>
                      </div>
                    </div>
                    <div class="indices-empty" v-else>--</div>
                  </div>

                  <el-button text size="small" class="refresh-btn" :loading="loading" @click="loadData(true)">刷新</el-button>
                </div>

                <div class="main-body">
                  <div class="left-panel">
                    <div class="heatmap-box">
                      <div class="box-header">
                        <el-radio-group v-model="activeMarket" size="small">
                          <el-radio-button label="Crypto">加密</el-radio-button>
                          <el-radio-button label="USStock">美股</el-radio-button>
                          <el-radio-button label="CNStock">A股</el-radio-button>
                          <el-radio-button label="HKStock">港股</el-radio-button>
                          <el-radio-button label="Forex">外汇</el-radio-button>
                        </el-radio-group>
                      </div>
                      <div class="heatmap-grid" v-loading="loading">
                        <div
                          class="heat-cell"
                          v-for="item in heatmapItems"
                          :key="item.symbol + '-' + item.timestamp"
                          :style="heatmapStyle(item.change_24h)"
                        >
                          <span class="heat-name">{{ item.symbol }}</span>
                          <span class="heat-price" v-if="item.price">${{ formatPrice(item.price) }}</span>
                          <span class="heat-val">{{ item.change_24h >= 0 ? '+' : '' }}{{ (item.change_24h || 0).toFixed(2) }}%</span>
                        </div>
                        <div class="heatmap-empty" v-if="heatmapItems.length === 0 && !loading">暂无数据</div>
                      </div>
                    </div>

                    <div class="calendar-box">
                      <div class="box-header">
                        <span class="box-title">财经日历</span>
                      </div>
                      <div class="calendar-list" v-loading="loading">
                        <div class="cal-item" v-for="evt in calendarItems" :key="evt.key">
                          <span class="cal-date">{{ evt.date }}</span>
                          <span class="cal-time">{{ evt.time }}</span>
                          <span class="cal-flag">{{ evt.flag }}</span>
                          <span class="cal-name">{{ evt.name }}</span>
                          <span class="cal-impact" :class="evt.impactClass">
                            {{ evt.impactIcon }} {{ evt.impactValue }}
                          </span>
                        </div>
                        <div class="cal-empty" v-if="calendarItems.length === 0 && !loading">暂无事件</div>
                      </div>
                    </div>
                  </div>

                  <div class="right-panel">
                    <div class="analysis-toolbar">
                      <el-select
                        v-model="analysisSymbol"
                        filterable
                        clearable
                        remote
                        reserve-keyword
                        allow-create
                        default-first-option
                        placeholder="选择标的开始分析"
                        class="symbol-selector"
                        :remote-method="onSymbolRemoteSearch"
                        :loading="symbolSearchLoading"
                      >
                        <el-option
                          v-for="s in analysisSymbols"
                          :key="s"
                          :label="s"
                          :value="s"
                        />
                      </el-select>
                      <el-button type="primary" :loading="loading" :disabled="!analysisSymbol || loading" class="analyze-button" @click="startAnalysis">开始分析</el-button>
                      <el-button :disabled="loading" class="history-button" @click="openHistory">历史记录</el-button>
                    </div>

                    <div class="analysis-main">
                      <div class="analysis-placeholder" v-if="!loading">
                        <div class="placeholder-hero">
                          <div class="hero-bg">
                            <div class="hero-bg-circle c1"></div>
                            <div class="hero-bg-circle c2"></div>
                            <div class="hero-bg-grid"></div>
                          </div>
                          <div class="hero-body">
                            <div class="hero-badge">AI-POWERED</div>
                            <h2 class="hero-title">AI 智能分析引擎</h2>
                            <p class="hero-subtitle">多维度市场监测 · 机构级洞察 · 实时市场趋势</p>
                            <div class="hero-stats">
                              <div class="hstat">
                                <div class="hstat-icon">📈</div>
                                <div class="hstat-body">
                                  <span class="hstat-val">多周期趋势预测</span>
                                  <span class="hstat-label">多时间尺度融合</span>
                                </div>
                              </div>
                              <div class="hstat">
                                <div class="hstat-icon">🧭</div>
                                <div class="hstat-body">
                                  <span class="hstat-val">专业指标矩阵</span>
                                  <span class="hstat-label">量化信号筛选</span>
                                </div>
                              </div>
                              <div class="hstat">
                                <div class="hstat-icon">⭐</div>
                                <div class="hstat-body">
                                  <span class="hstat-val">自选联动分析</span>
                                  <span class="hstat-label">一键跟踪监控</span>
                                </div>
                              </div>
                            </div>
                            <div class="hero-cta">
                              <el-button type="primary" :disabled="!analysisSymbol" @click="addFavoriteFromSelected">添加到自选</el-button>
                              <el-button :disabled="!analysisSymbol || loading" @click="startAnalysis">开始分析</el-button>
                            </div>
                            <p class="hero-hint">右侧自选股列表选择标的，或在上方选择后快速开始</p>
                          </div>
                        </div>
                      </div>
                      <div class="analysis-loading" v-else>分析中...</div>
                    </div>
                  </div>

                  <div class="watchlist-panel">
                    <div class="panel-header">
                      <span class="panel-title">我的自选股</span>
                      <span class="panel-count">{{ favorites.length }}</span>
                      <div class="panel-actions">
                        <el-button size="small" :type="batchMode ? 'primary' : ''" class="batch-toggle-btn" @click="toggleBatchMode">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="18" rx="2"/><line x1="2" y1="9" x2="22" y2="9"/><line x1="9" y1="3" x2="9" y2="21"/></svg>
                          <span>{{ batchMode ? '退出' : '批量' }}</span>
                        </el-button>
                        <el-button size="small" @click="showAddModal = true">添加</el-button>
                      </div>
                    </div>

                    <div class="watchlist-summary">
                      <div class="ws-item">
                        <span class="ws-label">持仓</span>
                        <span class="ws-value">{{ watchlistPositions }}</span>
                      </div>
                      <div class="ws-item">
                        <span class="ws-label">监控</span>
                        <span class="ws-value ws-tasks" @click="openTaskDrawer">
                          {{ watchlistTasks }}
                          <svg class="ws-arrow" width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 2l3 3-3 3"/></svg>
                        </span>
                      </div>
                      <div class="ws-item">
                        <span class="ws-label">盈亏</span>
                        <span class="ws-value" :class="watchlistPnlClass">{{ watchlistPnl }}</span>
                      </div>
                    </div>

                    <div v-if="batchMode" class="batch-bar">
                      <label class="batch-select-all">
                        <input type="checkbox" :checked="batchSelectedAll" :indeterminate="batchIndeterminate" @change="onBatchSelectAll" />
                        <span>全选</span>
                      </label>
                      <el-button size="small" type="primary" plain @click="openBatchScheduleModal">批量定时分析</el-button>
                      <el-button size="small" plain @click="openTaskDrawer">任务管理</el-button>
                    </div>

                    <div class="watchlist">
                      <div class="watch-item" v-for="item in favorites" :key="item.symbol">
                        <template v-if="batchMode">
                          <input type="checkbox" :checked="batchSelectedKeys.includes(item.symbol)" class="wi-checkbox" @change="onBatchItemToggle(item)" />
                          <div class="wi-info" @click="selectWatchlistItem(item)">
                            <div class="wi-symbol">{{ watchlistName(item) }}</div>
                          </div>
                          <div class="wi-price">{{ watchlistPrice(item) }}</div>
                          <div class="wi-change" :class="watchlistChange(item) >= 0 ? 'up' : 'down'">
                            {{ watchlistChange(item) >= 0 ? '+' : '' }}{{ watchlistChange(item).toFixed(2) }}%
                          </div>
                        </template>
                        <template v-else>
                          <div class="wi-info" @click="selectWatchlistItem(item)">
                            <div class="wi-symbol">
                              {{ watchlistName(item) }}
                            </div>
                          </div>
                          <div class="wi-price">{{ watchlistPrice(item) }}</div>
                          <div class="wi-mini">
                            <svg width="56" height="22" viewBox="0 0 56 22">
                              <polyline :points="watchlistSparkline(item)" fill="none" :stroke="watchlistChange(item) >= 0 ? '#10b981' : '#ef4444'" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                            </svg>
                          </div>
                          <div class="wi-hover-actions">
                            <el-tooltip v-if="getTaskForSymbol(item.symbol)" placement="top" :content="taskTooltip(item.symbol)">
                              <span class="wi-icon-btn wi-monitor-btn active">●</span>
                            </el-tooltip>
                            <el-tooltip v-else placement="top" content="创建监控任务">
                              <span class="wi-icon-btn wi-monitor-btn" @click.stop="openQuickTaskModal(item)">
                                <svg width="11" height="11" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="6" cy="6" r="4.5"/><path d="M6 4v4M4 6h4"/></svg>
                              </span>
                            </el-tooltip>
                            <el-tooltip placement="top" content="移除自选">
                              <span class="wi-icon-btn wi-remove-btn" @click.stop="removeFavorite(item)">
                                <svg width="11" height="11" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M3 3l6 6M9 3l-6 6"/></svg>
                              </span>
                            </el-tooltip>
                          </div>
                        </template>
                      </div>
                      <div class="watch-empty" v-if="favorites.length === 0">
                        <el-empty description="暂无自选" :image-size="60" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </el-tab-pane>

        <el-tab-pane label="预测市场" name="polymarket">
          <div class="tab-body">
            <div class="polymarket-placeholder">
              <el-empty description="预测市场功能开发中" />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>

  <el-dialog v-model="showAddModal" title="添加自选" width="420px">
    <div class="add-symbol-body">
      <el-input v-model="addSearchQuery" placeholder="搜索标的..." clearable>
        <template #prefix>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        </template>
      </el-input>
      <div class="add-symbol-list">
                        <div class="add-symbol-item" v-for="opp in addSearchResults" :key="opp.symbol" @click="toggleFavoriteFromAdd(opp)">
          <div class="asi-left">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" :stroke="isInFavorites(opp.symbol) ? '#eab308' : '#888'" stroke-width="2">
              <polygon points="12,2 15.09,8.26 22,9.27 17,14.14 18.18,21.02 12,17.77 5.82,21.02 7,14.14 2,9.27 8.91,8.26"/>
            </svg>
            <span class="asi-symbol">{{ opp.symbol }}</span>
            <span class="asi-name">{{ opp.name || opp.market }}</span>
          </div>
          <div class="asi-right">
            <span class="asi-price">{{ formatPrice(opp.price) }}</span>
            <span class="asi-change" :class="(opp.change_24h || 0) >= 0 ? 'up' : 'down'">
              {{ (opp.change_24h || 0) >= 0 ? '+' : '' }}{{ (opp.change_24h || 0).toFixed(2) }}%
            </span>
          </div>
        </div>
        <div class="add-symbol-empty" v-if="addSearchResults.length === 0">
          无匹配结果
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="showAddModal = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="showBatchScheduleModal" title="批量定时分析" width="400px">
    <el-form :model="batchScheduleForm" label-width="80px">
      <el-form-item label="分析间隔">
        <el-select v-model="batchScheduleForm.intervalMin" style="width:100%">
          <el-option v-for="opt in intervalOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="通知渠道">
        <el-checkbox-group v-model="batchScheduleForm.notifyChannels">
          <el-checkbox value="app">站内通知</el-checkbox>
          <el-checkbox value="dingtalk">钉钉</el-checkbox>
          <el-checkbox value="wechat">微信</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
    </el-form>
    <div class="batch-schedule-info">
      将对 <strong>{{ batchSelectedKeys.length }}</strong> 个标的创建监控任务
    </div>
    <template #footer>
      <el-button @click="showBatchScheduleModal = false">取消</el-button>
      <el-button type="primary" @click="saveBatchSchedule">创建任务</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="showQuickTaskModal" title="创建监控任务" width="400px">
    <el-form :model="quickTaskForm" label-width="80px">
      <el-form-item label="监控标的">
        <el-tag>{{ quickTaskSymbol }}</el-tag>
      </el-form-item>
      <el-form-item label="分析间隔">
        <el-select v-model="quickTaskForm.intervalMin" style="width:100%">
          <el-option v-for="opt in intervalOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="通知渠道">
        <el-checkbox-group v-model="quickTaskForm.notifyChannels">
          <el-checkbox value="app">站内通知</el-checkbox>
          <el-checkbox value="dingtalk">钉钉</el-checkbox>
          <el-checkbox value="wechat">微信</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showQuickTaskModal = false">取消</el-button>
      <el-button type="primary" @click="saveQuickTask">创建</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="showTaskDrawer" title="监控任务" size="380px">
    <div class="task-drawer-body">
      <div class="task-empty" v-if="monitorTasks.length === 0">
        <el-empty description="暂无监控任务" :image-size="60" />
      </div>
      <div class="task-item" v-for="t in monitorTasks" :key="t.id">
        <div class="task-header">
          <div class="task-symbols">{{ t.symbols.join(', ') }}</div>
          <el-switch :model-value="t.enabled" @change="(v: boolean) => handleToggleTask(t.id, v)" />
        </div>
        <div class="task-meta">
          <span>间隔: {{ formatIntervalText(t.intervalMin) }}</span>
          <span>创建: {{ new Date(t.createdAt).toLocaleDateString() }}</span>
        </div>
        <div class="task-actions">
          <el-button size="small" text @click="handleEditTask(t)">编辑</el-button>
          <el-button size="small" text type="danger" @click="handleDeleteTask(t.id)">删除</el-button>
        </div>
      </div>
    </div>
  </el-drawer>

  <el-dialog v-model="showEditTaskModal" title="编辑监控任务" width="400px">
    <el-form :model="editTaskForm" label-width="80px">
      <el-form-item label="监控标的">
        <div class="edit-symbols">{{ editTaskForm.symbols.join(', ') }}</div>
      </el-form-item>
      <el-form-item label="分析间隔">
        <el-select v-model="editTaskForm.intervalMin" style="width:100%">
          <el-option v-for="opt in intervalOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="通知渠道">
        <el-checkbox-group v-model="editTaskForm.notifyChannels">
          <el-checkbox value="app">站内通知</el-checkbox>
          <el-checkbox value="dingtalk">钉钉</el-checkbox>
          <el-checkbox value="wechat">微信</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showEditTaskModal = false">取消</el-button>
      <el-button type="primary" @click="saveEditTask">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { Refresh } from "@element-plus/icons-vue";
import { getAiRadarOpportunities, type AiRadarOpportunity } from "@/api/aiRadar";
import { createTask, listTasks, updateTask, deleteTask, executeTask, listReports, type MonitorTask as ApiMonitorTask } from "@/api/aiAnalysisTask";
import { getSupportedSymbols, searchSymbols } from "@/api/kline";
import { useAuthStore } from "@/stores/auth";
import { getFavorites, addFavorite as addFavApi, removeFavorite as removeFavApi, getSymbols, type SymbolItem, type UserFavoriteItem } from "@/api/symbols";

const loading = ref(false);
const opportunities = ref<AiRadarOpportunity[]>([]);
const activeMarket = ref("Crypto");
const activeTab = ref("quick");
const lastUpdated = ref("");
const hover = ref(false);
const authStore = useAuthStore();
let refreshTimer: ReturnType<typeof setInterval> | null = null;
interface WatchlistItem {
  symbol: string;
  market: string;
  symbolId?: number;
}

const analysisSymbol = ref<string>("");
const favorites = ref<WatchlistItem[]>([]);
const showAddModal = ref(false);
const addSearchQuery = ref("");

const supportedSymbols = ref<string[]>([]);
const symbolSearchLoading = ref(false);
const symbolSearchResults = ref<string[]>([]);
let symbolSearchTimer: ReturnType<typeof setTimeout> | null = null;
const allSymbols = ref<SymbolItem[]>([]);
const addSymbolResults = ref<SymbolItem[]>([]);
const addSymbolLoading = ref(false);

function normalizeSymbolsResponse(data: any): string[] {
  if (Array.isArray(data)) return data.filter((s) => typeof s === "string");
  if (Array.isArray(data?.symbols)) return data.symbols.filter((s: any) => typeof s === "string");
  if (Array.isArray(data?.data)) return data.data.filter((s: any) => typeof s === "string");
  return [];
}

async function loadSupportedSymbols() {
  try {
    const res = await getSupportedSymbols();
    supportedSymbols.value = normalizeSymbolsResponse(res?.data);
  } catch {
    supportedSymbols.value = [];
  }
}

async function fetchSymbolSearch(keyword: string) {
  const q = (keyword || "").trim();
  if (!q) {
    symbolSearchResults.value = [];
    return;
  }
  symbolSearchLoading.value = true;
  try {
    const res = await searchSymbols(q);
    symbolSearchResults.value = normalizeSymbolsResponse(res?.data).slice(0, 50);
  } catch {
    symbolSearchResults.value = [];
  } finally {
    symbolSearchLoading.value = false;
  }
}

function onSymbolRemoteSearch(keyword: string) {
  if (symbolSearchTimer) clearTimeout(symbolSearchTimer);
  symbolSearchTimer = setTimeout(() => fetchSymbolSearch(keyword), 250);
}

const addSearchResults = computed(() => {
  if (!addSearchQuery.value || !addSearchQuery.value.trim()) {
    return opportunities.value.slice(0, 20);
  }
  const q = addSearchQuery.value.toLowerCase().trim();
  const map = new Map<string, AiRadarOpportunity>();
  // 机会中匹配
  opportunities.value
    .filter((o) => o.symbol.toLowerCase().includes(q) || (o.name || "").toLowerCase().includes(q))
    .forEach((o) => map.set(o.symbol, o));
  // 标的字典中匹配
  addSymbolResults.value.forEach((s) => {
    if (map.has(s.symbol)) return;
    map.set(s.symbol, {
      symbol: s.symbol,
      name: s.name || "",
      price: Number.NaN,
      change_24h: Number.NaN,
      signal: "",
      strength: "",
      reason: "",
      impact: "neutral",
      market: s.market || "Crypto",
      timestamp: Date.now(),
    });
  });

  return Array.from(map.values()).slice(0, 50);
});

function handleAddSearch() {
}

const marketLabelMap: Record<string, string> = {
  Crypto: "加密货币",
  USStock: "美股",
  CNStock: "A股",
  HKStock: "港股",
  Forex: "外汇",
};

const signalLabelMap: Record<string, string> = {
  overbought: "超买",
  oversold: "超卖",
  bullish_momentum: "看涨动能",
  bearish_momentum: "看跌动能",
  consolidation: "盘整",
};

const impactLabelMap: Record<string, string> = {
  bullish: "看多",
  bearish: "看空",
  neutral: "中性",
};



function marketLabel(market: string): string {
  return marketLabelMap[market] || market;
}

function signalLabel(signal: string): string {
  return signalLabelMap[signal] || signal;
}

function impactLabel(impact: string): string {
  return impactLabelMap[impact] || impact;
}

function formatPrice(price: number): string {
  if (!price && price !== 0) return "--";
  if (price >= 10000) return (price / 1000).toFixed(1) + "K";
  if (price >= 1) return price.toFixed(2);
  return price.toFixed(4);
}

const carouselItems = computed(() => {
  if (opportunities.value.length === 0) return [];
  return [...opportunities.value, ...opportunities.value];
});

const trackStyle = computed(() => {
  const duration = opportunities.value.length * 3;
  return {
    animationDuration: duration + "s",
  };
});

const filteredOpportunities = computed(() => {
  return opportunities.value.filter((o) => o.market === activeMarket.value);
});

const uniqueOpportunities = computed(() => {
  const map = new Map<string, AiRadarOpportunity>();
  opportunities.value.forEach((o) => {
    if (!map.has(o.symbol)) map.set(o.symbol, o);
  });
  return Array.from(map.values());
});

const analysisSymbols = computed(() => {
  const set = new Set<string>();
  const preferred: string[] = [];

  for (const s of ["BTC-USDT-SWAP"]) {
    if (supportedSymbols.value.includes(s)) preferred.push(s);
  }

  preferred.forEach((s) => set.add(s));
  favorites.value.forEach((f) => set.add(f.symbol));
  uniqueOpportunities.value.forEach((o) => set.add(o.symbol));
  symbolSearchResults.value.forEach((s) => {
    const sym = String(s || "").trim();
    if (sym) set.add(sym);
  });

  return Array.from(set).slice(0, 200);
});

const fearGreed = computed(() => {
  if (opportunities.value.length === 0) return "--";
  const avg = opportunities.value.reduce((sum, o) => sum + (o.change_24h || 0), 0) / opportunities.value.length;
  const score = Math.max(0, Math.min(100, 50 + avg * 2));
  return Math.round(score);
});

const fearGreedLevel = computed(() => {
  if (typeof fearGreed.value !== "number") return "neutral";
  if (fearGreed.value <= 20) return "extreme-fear";
  if (fearGreed.value <= 40) return "fear";
  if (fearGreed.value <= 60) return "neutral";
  if (fearGreed.value <= 80) return "greed";
  return "extreme-greed";
});

const vix = computed(() => {
  if (opportunities.value.length === 0) return "--";
  const avgAbs = opportunities.value.reduce((sum, o) => sum + Math.abs(o.change_24h || 0), 0) / opportunities.value.length;
  return Math.max(10, Math.min(50, Math.round(12 + avgAbs * 2)));
});

const vixLevel = computed(() => {
  if (typeof vix.value !== "number") return "medium";
  if (vix.value < 16) return "low";
  if (vix.value < 25) return "medium";
  return "high";
});

const dxy = computed(() => {
  if (opportunities.value.length === 0) return "--";
  return 103.8;
});

function marketFlag(market: string): string {
  const map: Record<string, string> = {
    Crypto: "🪙",
    USStock: "🇺🇸",
    CNStock: "🇨🇳",
    HKStock: "🇭🇰",
    Forex: "💱",
  };
  return map[market] || "•";
}

const indicesItems = computed(() => {
  return uniqueOpportunities.value.slice(0, 20).map((o) => ({
    symbol: o.symbol,
    price: o.price,
    change: o.change_24h || 0,
    flag: marketFlag(o.market),
  }));
});

const marqueeItems = computed(() => {
  if (indicesItems.value.length === 0) return [];
  return [...indicesItems.value, ...indicesItems.value];
});

const heatmapItems = computed(() => {
  const items = filteredOpportunities.value.slice().sort((a, b) => Math.abs(b.change_24h || 0) - Math.abs(a.change_24h || 0));
  return items.slice(0, 12);
});

function heatmapStyle(value: number) {
  const v = value || 0;
  const abs = Math.min(10, Math.abs(v));
  const alpha = 0.08 + (abs / 10) * 0.22;
  if (v >= 0) {
    return { background: `rgba(16, 185, 129, ${alpha})`, color: "#065f46" };
  }
  return { background: `rgba(239, 68, 68, ${alpha})`, color: "#7f1d1d" };
}

const COUNTRY_FLAGS: Record<string, string> = {
  US: '🇺🇸', EU: '🇪🇺', JP: '🇯🇵', UK: '🇬🇧',
  CN: '🇨🇳', AU: '🇦🇺', DE: '🇩🇪', CH: '🇨🇭', CA: '🇨🇦', NZ: '🇳🇿',
  INTL: '🌐',
}

const SAMPLE_EVENTS = [
  { name: '美国非农就业数据', country: 'US', importance: 'high', forecast: '180K', previous: '175K', impact_if_above: 'bullish', impact_if_below: 'bearish' },
  { name: '美联储利率决议', country: 'US', importance: 'high', forecast: '5.25%', previous: '5.25%', impact_if_above: 'bearish', impact_if_below: 'bullish' },
  { name: '美国CPI月率', country: 'US', importance: 'high', forecast: '0.3%', previous: '0.4%', impact_if_above: 'bearish', impact_if_below: 'bullish' },
  { name: '欧洲央行利率决议', country: 'EU', importance: 'high', forecast: '4.50%', previous: '4.50%', impact_if_above: 'bearish', impact_if_below: 'bullish' },
  { name: '日本央行利率决议', country: 'JP', importance: 'high', forecast: '0.10%', previous: '0.10%', impact_if_above: 'bullish', impact_if_below: 'bearish' },
  { name: '美国初请失业金人数', country: 'US', importance: 'medium', forecast: '215K', previous: '212K', impact_if_above: 'bearish', impact_if_below: 'bullish' },
  { name: '英国央行利率决议', country: 'UK', importance: 'high', forecast: '5.25%', previous: '5.25%', impact_if_above: 'bullish', impact_if_below: 'bearish' },
  { name: '美国零售销售月率', country: 'US', importance: 'medium', forecast: '0.4%', previous: '0.6%', impact_if_above: 'bullish', impact_if_below: 'bearish' },
  { name: 'OPEC月度报告', country: 'INTL', importance: 'medium', forecast: '-', previous: '-', impact_if_above: 'bullish', impact_if_below: 'bearish' },
  { name: '澳大利亚利率决议', country: 'AU', importance: 'high', forecast: '4.10%', previous: '4.10%', impact_if_above: 'bullish', impact_if_below: 'bearish' },
  { name: '中国GDP季率', country: 'CN', importance: 'high', forecast: '5.2%', previous: '5.3%', impact_if_above: 'bullish', impact_if_below: 'bearish' },
  { name: '德国IFO商业景气指数', country: 'DE', importance: 'medium', forecast: '85.5', previous: '85.2', impact_if_above: 'bullish', impact_if_below: 'bearish' },
]

const calendarItems = computed(() => {
  const today = new Date()
  return SAMPLE_EVENTS.map((evt, i) => {
    const daysOffset = (i % 14) - 5
    const eventDate = new Date(today)
    eventDate.setDate(today.getDate() + daysOffset)
    const hour = (8 + i * 3) % 24

    const month = String(eventDate.getMonth() + 1).padStart(2, '0')
    const day = String(eventDate.getDate()).padStart(2, '0')
    const timeStr = `${String(hour).padStart(2, '0')}:30`

    const isSameDay = eventDate.toDateString() === today.toDateString()
    const isPast = eventDate < new Date(today.getFullYear(), today.getMonth(), today.getDate())
    const isReleased = isPast || (isSameDay && hour < today.getHours())

    let impactClass = ''
    let impactIcon = ''
    let impactValue = evt.forecast

    if (isReleased) {
      const numStr = evt.forecast.replace(/[^0-9.]/g, '')
      if (numStr && parseFloat(numStr) > 0) {
        const base = parseFloat(numStr)
        const variation = base * (1 + (Math.random() - 0.5) * 0.3)
        const isAbove = variation > base
        impactClass = isAbove ? evt.impact_if_above : evt.impact_if_below
        impactIcon = isAbove ? '▲' : '▼'

        if (evt.forecast.includes('K')) impactValue = `${variation.toFixed(0)}K`
        else if (evt.forecast.includes('%')) impactValue = `${variation.toFixed(2)}%`
        else impactValue = variation.toFixed(1)
      }
    } else {
      impactValue = `预 ${evt.forecast}`
    }

    return {
      key: `cal-${i}`,
      date: `${month}/${day}`,
      time: timeStr,
      flag: COUNTRY_FLAGS[evt.country] || '•',
      name: evt.name,
      impactClass,
      impactIcon,
      impactValue,
    }
  }).sort((a, b) => b.date.localeCompare(a.date) || b.time.localeCompare(a.time))
})

async function loadFavorites() {
  try {
    if (!authStore.isAuthenticated) return;
    const res = await getFavorites();
    const items: UserFavoriteItem[] = res?.data || res || [];
    // 按 symbol 去重
    const seen = new Set<string>();
    const items2: UserFavoriteItem[] = [];
    items.forEach(f => {
      const key = f.symbol?.symbol || "";
      if (key && !seen.has(key)) {
        seen.add(key);
        items2.push(f);
      }
    });
    favorites.value = items2.map((f: UserFavoriteItem) => ({
      symbol: f.symbol?.symbol || "",
      market: f.symbol?.market || "Crypto",
      name: f.symbol?.name,
      symbolId: f.symbolId,
    }));
    // 缓存标的映射
    const map = new Map<number, SymbolItem>();
    items2.forEach((f: UserFavoriteItem) => {
      if (f.symbol) map.set(f.symbol.id, f.symbol);
    });
    allSymbols.value = Array.from(map.values());
  } catch {
    favorites.value = [];
  }
}

function findOpportunity(symbol: string): AiRadarOpportunity | undefined {
  return opportunities.value.find((o) => o.symbol === symbol);
}

async function addFavorite(item: { symbol: string; market: string }) {
  if (favorites.value.some((f) => f.symbol === item.symbol)) return;
  try {
    // 查找 symbolId
    let symbolId = item.symbolId || allSymbols.value.find(s => s.symbol === item.symbol)?.id;
    if (!symbolId) {
      const res = await getSymbols({ keyword: item.symbol });
      const symbols: SymbolItem[] = res?.data || [];
      const found = symbols.find(s => s.symbol === item.symbol);
      if (found) symbolId = found.id;
    }
    if (!symbolId) {
      console.warn("未找到标的 symbolId:", item.symbol);
      return;
    }
    await addFavApi(symbolId);
    await loadFavorites();
    console.log(`已添加自选：${item.symbol}`);
  } catch {
    console.warn(`添加自选失败：${item.symbol}`);
  }
}

async function addFavoriteFromSelected() {
  if (!analysisSymbol.value) return;
  const opp = findOpportunity(analysisSymbol.value);
  await addFavorite({ symbol: analysisSymbol.value, market: opp?.market || "Crypto" });
}

async function removeFavorite(item: { symbol: string; market: string }) {
  const fav = favorites.value.find((f) => f.symbol === item.symbol);
  const symbolId = item.symbolId || fav?.symbolId;
  if (!symbolId) {
    console.warn("无法移除自选：缺少 symbolId");
    return;
  }
  try {
    await removeFavApi(symbolId);
    favorites.value = favorites.value.filter((f) => f.symbol !== item.symbol);
    console.log(`已移除自选：${item.symbol}`);
  } catch {
    console.warn(`移除自选失败：${item.symbol}`);
  }
}

function isInFavorites(symbol: string): boolean {
  return favorites.value.some((f) => f.symbol === symbol);
}

async function toggleFavoriteFromAdd(opp: AiRadarOpportunity) {
  if (isInFavorites(opp.symbol)) {
    await removeFavorite({ symbol: opp.symbol, market: opp.market });
  } else {
    await addFavorite({ symbol: opp.symbol, market: opp.market });
  }
}

function selectWatchlistItem(item: WatchlistItem) {
  analysisSymbol.value = item.symbol;
}

function watchlistName(item: WatchlistItem): string {
  return item.name || findOpportunity(item.symbol)?.name || item.symbol;
}

function watchlistPrice(item: WatchlistItem): string {
  const opp = findOpportunity(item.symbol);
  return opp ? formatPrice(opp.price) : "--";
}

function watchlistChange(item: WatchlistItem): number {
  const opp = findOpportunity(item.symbol);
  return opp?.change_24h || 0;
}

function watchlistSparkline(item: WatchlistItem): string {
  const v = watchlistChange(item);
  const absVal = Math.min(10, Math.abs(v) || 0.5);
  const points: string[] = [];
  for (let i = 0; i < 8; i++) {
    const x = i * 8;
    const mid = 11;
    const y = v >= 0
      ? mid - (absVal / 10) * 8 * Math.sin((i / 7) * Math.PI)
      : mid + (absVal / 10) * 8 * Math.sin((i / 7) * Math.PI);
    points.push(`${x},${Math.round(y)}`);
  }
  return points.join(" ");
}

const watchlistPositions = computed(() => 0);
const watchlistTasks = computed(() => monitorTasks.value.length);
const watchlistPnl = computed(() => "0.00");
const watchlistPnlClass = computed(() => "");

const batchMode = ref(false);
const batchSelectedKeys = ref<string[]>([]);
const showBatchScheduleModal = ref(false);
const showTaskDrawer = ref(false);
const showEditTaskModal = ref(false);
const editTaskId = ref<string>("");

const batchScheduleForm = ref({
  intervalMin: 60,
  notifyChannels: ["app"] as string[],
});

const editTaskForm = ref<{
  symbols: string[];
  intervalMin: number;
  notifyChannels: string[];
}>({
  symbols: [],
  intervalMin: 60,
  notifyChannels: ["app"],
});

const monitorTasks = ref<ApiMonitorTask[]>([]);

const intervalOptions = [
  { value: 30, label: "30分钟" },
  { value: 60, label: "1小时" },
  { value: 240, label: "4小时" },
  { value: 720, label: "12小时" },
  { value: 1440, label: "24小时" },
];

const batchSelectedAll = computed(() => {
  if (favorites.value.length === 0) return false;
  return batchSelectedKeys.value.length === favorites.value.length;
});

const batchIndeterminate = computed(() => {
  if (favorites.value.length === 0) return false;
  return batchSelectedKeys.value.length > 0 && batchSelectedKeys.value.length < favorites.value.length;
});

function toggleBatchMode() {
  batchMode.value = !batchMode.value;
  if (!batchMode.value) {
    batchSelectedKeys.value = [];
  }
}

function onBatchSelectAll(e: Event) {
  const checked = (e.target as HTMLInputElement).checked;
  if (checked) {
    batchSelectedKeys.value = favorites.value.map((f) => f.symbol);
  } else {
    batchSelectedKeys.value = [];
  }
}

function onBatchItemToggle(item: WatchlistItem) {
  const idx = batchSelectedKeys.value.indexOf(item.symbol);
  if (idx >= 0) {
    batchSelectedKeys.value.splice(idx, 1);
  } else {
    batchSelectedKeys.value.push(item.symbol);
  }
}

function openBatchScheduleModal() {
  showBatchScheduleModal.value = true;
}

function saveBatchSchedule() {
  if (batchSelectedKeys.value.length === 0) return;
  const interval = batchScheduleForm.value.intervalMin;
  const channels = [...batchScheduleForm.value.notifyChannels];
  createTask({
    symbols: [...batchSelectedKeys.value],
    intervalMin: interval,
    notifyChannels: channels,
  }).then(() => {
    loadMonitorTasks();
    showBatchScheduleModal.value = false;
    addLog(`已创建监控任务 (${interval}分钟): ${batchSelectedKeys.value.join(", ")}`);
  });
}

async function loadMonitorTasks() {
  try {
    const res = await listTasks({ page: 1, size: 50 });
    monitorTasks.value = (res.records || []).map((t) => ({
      ...t,
      symbols: typeof t.symbols === "string" ? JSON.parse(t.symbols) : t.symbols,
      notifyChannels: typeof t.notifyChannels === "string" ? JSON.parse(t.notifyChannels) : t.notifyChannels,
    }));
  } catch {
    monitorTasks.value = [];
  }
}

function handleToggleTask(id: string, enabled: boolean) {
  updateTask(id, { enabled }).then(() => {
    loadMonitorTasks();
    addLog(`${enabled ? "启用" : "停用"}监控任务`);
  });
}

function handleEditTask(task: ApiMonitorTask) {
  editTaskId.value = task.id;
  editTaskForm.value = {
    symbols: [...task.symbols],
    intervalMin: task.intervalMin,
    notifyChannels: [...task.notifyChannels],
  };
  showEditTaskModal.value = true;
}

function saveEditTask() {
  const interval = editTaskForm.value.intervalMin;
  const channels = [...editTaskForm.value.notifyChannels];
  updateTask(editTaskId.value, {
    intervalMin: interval,
    notifyChannels: channels,
  }).then(() => {
    loadMonitorTasks();
    addLog(`已更新监控任务`);
  });
  showEditTaskModal.value = false;
}

function handleDeleteTask(id: string) {
  deleteTask(id).then(() => {
    loadMonitorTasks();
    addLog(`已删除监控任务`);
  });
}

function openTaskDrawer() {
  showTaskDrawer.value = true;
}

function formatIntervalText(minutes: number): string {
  const opt = intervalOptions.find((o) => o.value === minutes);
  return opt ? opt.label : `${minutes}分钟`;
}

// 获取某个标的的监控任务
function getTaskForSymbol(symbol: string): ApiMonitorTask | undefined {
  return monitorTasks.value.find(t => t.symbols.includes(symbol));
}

// 监控状态 tooltip 文本
function taskTooltip(symbol: string): string {
  const task = getTaskForSymbol(symbol);
  if (!task) return '无监控任务';
  const status = task.enabled ? '已启用' : '已停用';
  return `${status} | 间隔: ${formatIntervalText(task.intervalMin)}`;
}

const showQuickTaskModal = ref(false);
const quickTaskSymbol = ref("");
const quickTaskForm = ref({
  intervalMin: 60,
  notifyChannels: ["app"] as string[],
});

// 打开快速创建监控弹窗
function openQuickTaskModal(item: WatchlistItem) {
  quickTaskSymbol.value = item.symbol;
  quickTaskForm.value = { intervalMin: 60, notifyChannels: ["app"] };
  showQuickTaskModal.value = true;
}

// 保存快速创建监控
function saveQuickTask() {
  const interval = quickTaskForm.value.intervalMin;
  const channels = [...quickTaskForm.value.notifyChannels];
  createTask({
    symbols: [quickTaskSymbol.value],
    intervalMin: interval,
    notifyChannels: channels,
  }).then(() => {
    loadMonitorTasks();
    showQuickTaskModal.value = false;
    addLog(`已创建监控任务：${quickTaskSymbol.value}`);
  });
}

function startAnalysis() {
  if (!analysisSymbol.value) {
    addLog("请选择标的后再开始分析");
    return;
  }
  addLog(`开始分析：${analysisSymbol.value}`);
}

function openHistory() {
}

async function loadData(force = false) {
  if (loading.value) return;
  loading.value = true;
  try {
    const params: any = {};
    if (force) params.force = true;
    const res = await getAiRadarOpportunities(params);
    if (res?.data) {
      opportunities.value = res.data;
      lastUpdated.value = new Date().toLocaleTimeString();
    }
  } catch (e) {
    console.error("加载AI雷达数据失败:", e);
  } finally {
    loading.value = false;
  }
}

function handleMarketTabChange() {
}

function startAutoRefresh() {
  stopAutoRefresh();
  refreshTimer = setInterval(() => loadData(false), 60000);
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

onMounted(async () => {
  await loadFavorites();
  loadMonitorTasks();
  loadSupportedSymbols();
  loadData(false);
  startAutoRefresh();
});

onUnmounted(() => {
  stopAutoRefresh();
  if (symbolSearchTimer) clearTimeout(symbolSearchTimer);
});

watch(addSearchQuery, async (v) => {
  const q = (v || "").trim();
  if (!q) {
    addSymbolResults.value = [];
    return;
  }
  addSymbolLoading.value = true;
  try {
    const res = await getSymbols({ keyword: q });
    addSymbolResults.value = res?.data || [];
  } catch {
    addSymbolResults.value = [];
  } finally {
    addSymbolLoading.value = false;
  }
});
</script>

<style scoped lang="scss">
.ai-radar-page {
  padding: 20px;
  min-height: calc(100vh - 120px);
  background: var(--primary-bg);
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  overflow-x: hidden;

  .radar-section {
    margin-bottom: 20px;

    .radar-header {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      margin-bottom: 14px;

      .radar-header-left {
        display: flex;
        flex-direction: column;
        gap: 2px;

        .radar-title {
          margin: 0;
          font-size: 16px;
          font-weight: 700;
          color: var(--text-primary);
          letter-spacing: -0.2px;
        }

        .radar-subtitle {
          margin: 2px 0 0;
          font-size: 12px;
          color: var(--text-muted);
        }
      }

      .header-actions {
        display: flex;
        align-items: center;
        gap: 8px;
      }
    }

    .radar-carousel {
      overflow: hidden;
      position: relative;
      border-radius: 12px;
      padding: 2px 0;

      &::before,
      &::after {
        content: '';
        position: absolute;
        top: 0;
        bottom: 0;
        width: 50px;
        z-index: 2;
        pointer-events: none;
      }
      &::before {
        left: 0;
        background: linear-gradient(to right, var(--primary-bg), transparent);
      }
      &::after {
        right: 0;
        background: linear-gradient(to left, var(--primary-bg), transparent);
      }
    }

    .radar-track {
      display: flex;
      gap: 12px;
      animation: radar-scroll 60s linear infinite;
      width: max-content;
      padding: 4px 0;

      &.paused {
        animation-play-state: paused;
      }
    }

    @keyframes radar-scroll {
      0% { transform: translateX(0); }
      100% { transform: translateX(-50%); }
    }

    .radar-card {
      width: 200px;
      background: var(--secondary-bg);
      border-radius: 10px;
      padding: 12px;
      cursor: pointer;
      flex-shrink: 0;
      border: 1px solid var(--border-color);
      transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
      position: relative;
      overflow: hidden;

      &:hover {
        transform: translateY(-2px);
        border-color: var(--accent-blue);
        box-shadow: var(--card-shadow);
      }

      .rc-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 6px;
        gap: 6px;

        .rc-symbol {
          font-weight: 800;
          font-size: 12px;
          color: var(--text-primary);
          letter-spacing: -0.2px;
        }

        .rc-market {
          font-size: 9px;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 0.6px;
          padding: 2px 7px;
          border-radius: 5px;
          flex-shrink: 0;
          background: var(--tertiary-bg);
          color: var(--text-muted);

          &.rc-market-crypto   { color: #7c3aed; background: color-mix(in srgb, #7c3aed 12%, transparent); }
          &.rc-market-usstock  { color: #16a34a; background: color-mix(in srgb, #16a34a 12%, transparent); }
          &.rc-market-cnstock  { color: #d97706; background: color-mix(in srgb, #d97706 12%, transparent); }
          &.rc-market-hkstock  { color: #2f54eb; background: color-mix(in srgb, #2f54eb 12%, transparent); }
          &.rc-market-forex    { color: #d97706; background: color-mix(in srgb, #d97706 12%, transparent); }
        }
      }

      .rc-metrics {
        display: flex;
        gap: 3px;
        margin-bottom: 6px;

        .rc-metric {
          flex: 1;
          display: flex;
          flex-direction: column;
          gap: 2px;
          padding: 4px;
          border-radius: 6px;
          background: var(--tertiary-bg);

          .rc-metric-label {
            font-size: 9px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.4px;
            color: var(--text-muted);
          }

          .rc-metric-value {
            font-size: 11px;
            font-weight: 700;
            color: var(--text-primary);

            &.rc-up   { color: var(--accent-green); }
            &.rc-down { color: var(--accent-red); }
            &.rc-signal-val { font-size: 10px; font-weight: 600; }
            &.rc-signal-bullish_momentum { color: #0891b2; }
            &.rc-signal-overbought       { color: #d97706; }
            &.rc-signal-oversold         { color: #16a34a; }
            &.rc-signal-bearish_momentum { color: #dc2626; }
          }
        }
      }

      .rc-footer {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 6px;

        .rc-reason {
          font-size: 10px;
          color: var(--text-muted);
          line-height: 1.3;
          flex: 1;
          display: -webkit-box;
          -webkit-line-clamp: 1;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }

        .rc-impact-badge {
          font-size: 10px;
          font-weight: 700;
          color: #fff;
          background: linear-gradient(135deg, var(--accent-blue), #8b5cf6);
          padding: 3px 8px;
          border-radius: 6px;
          white-space: nowrap;
          flex-shrink: 0;

          &.impact-bullish { background: linear-gradient(135deg, var(--accent-green), #22c55e); }
          &.impact-bearish { background: linear-gradient(135deg, var(--accent-red), #ef4444); }
          &.impact-neutral { background: linear-gradient(135deg, #6b7280, #9ca3af); }
        }
      }
    }
  }

  .workspace-card {
    border-radius: 14px;
    box-shadow: var(--card-shadow);
    border: 1px solid var(--border-color);
    background: var(--secondary-bg);

    :deep(.el-card__body) {
      padding: 0;
    }

    .workspace-tabs {
      :deep(.el-tabs__header) {
        margin: 0;
        padding: 0 20px;
        background: var(--secondary-bg);
        border-bottom: 1px solid var(--border-color);
        border-radius: 14px 14px 0 0;
      }

      :deep(.el-tabs__nav-wrap::after) {
        display: none;
      }

      :deep(.el-tabs__item) {
        font-size: 15px;
        font-weight: 600;
        color: var(--text-primary);
        height: 48px;
        line-height: 48px;
        padding: 0 16px;
      }

      :deep(.el-tabs__active-bar) {
        background: var(--accent-blue);
        height: 2px;
      }

      :deep(.el-tabs__content) {
        padding: 0;
      }
    }
  }

  .tab-body {
    padding: 0;

    .ai-analysis-container {
      display: flex;
      width: 100%;
      background: var(--primary-bg);
      overflow: hidden;
      box-sizing: border-box;
    }

    .ai-analysis-container.embedded {
      background: transparent;
    }

    .main-content-full {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      background: var(--secondary-bg);
      border-radius: 0;
      box-shadow: none;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    }

    .top-index-bar {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 16px;
      background: var(--tertiary-bg);
      border-bottom: 1px solid var(--border-color);
      font-family: "SF Mono", Monaco, Consolas, monospace;

      .indicator-box {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 4px 10px;
        background: var(--secondary-bg);
        border-radius: 6px;
        border: 1px solid var(--border-color);
        min-width: 50px;

        .ind-label { font-size: 9px; color: var(--text-muted); text-transform: uppercase; }
        .ind-value { font-size: 13px; font-weight: 700; color: var(--text-primary); }

        &.fear-greed.extreme-fear .ind-value { color: var(--accent-red); }
        &.fear-greed.fear .ind-value { color: #ea580c; }
        &.fear-greed.neutral .ind-value { color: #ca8a04; }
        &.fear-greed.greed .ind-value { color: #65a30d; }
        &.fear-greed.extreme-greed .ind-value { color: var(--accent-green); }
        &.vix.low .ind-value { color: var(--accent-green); }
        &.vix.medium .ind-value { color: #ca8a04; }
        &.vix.high .ind-value { color: var(--accent-red); }
        &.dxy .ind-value { color: var(--accent-blue); }
      }

      .indices-marquee {
        flex: 1;
        overflow: hidden;
        min-width: 0;

        .marquee-track {
          display: flex;
          gap: 8px;
          animation: marquee 35s linear infinite;
          width: max-content;
          &:hover { animation-play-state: paused; }
        }

        .index-item {
          display: flex;
          align-items: center;
          gap: 4px;
          padding: 4px 8px;
          background: var(--secondary-bg);
          border-radius: 4px;
          border: 1px solid var(--border-color);
          font-size: 11px;
          white-space: nowrap;

          .idx-flag { font-size: 11px; }
          .idx-symbol { color: var(--text-muted); font-weight: 500; }
          .idx-price { color: var(--text-primary); font-weight: 600; }
          .idx-change {
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 4px;
            &.up { color: var(--accent-green); }
            &.down { color: var(--accent-red); }
          }
        }
      }

      @keyframes marquee {
        0% { transform: translateX(0); }
        100% { transform: translateX(-50%); }
      }

      .refresh-btn {
        color: var(--text-muted);
        flex-shrink: 0;
        &:hover { color: var(--text-primary); }
      }
    }

    .main-body {
      flex: 1;
      display: flex;
      gap: 12px;
      padding: 12px;
      overflow: hidden;
      min-height: 0;
    }

    .left-panel {
      width: 280px;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      gap: 10px;
      overflow-y: auto;

      .heatmap-box,
      .calendar-box {
        background: var(--secondary-bg);
        border-radius: 10px;
        padding: 14px;
        border: 1px solid var(--border-color);
        box-shadow: var(--card-shadow);
      }

      .heatmap-box {
        .box-header {
          margin-bottom: 10px;
          .box-title { font-size: 12px; font-weight: 800; color: var(--text-primary); }
        }

        .heatmap-grid {
          display: grid;
          grid-template-columns: repeat(3, 1fr);
          gap: 3px;

          .heat-cell {
            padding: 4px 2px;
            border-radius: 4px;
            text-align: center;
            min-width: 0;

            .heat-name { display: block; font-weight: 600; font-size: 9px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-bottom: 1px; color: var(--text-primary); }
            .heat-price { display: block; font-size: 8px; opacity: 0.8; margin-bottom: 1px; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            .heat-val { font-weight: 700; font-size: 9px; }
          }

          .heatmap-empty {
            grid-column: 1 / -1;
            text-align: center;
            color: var(--text-muted);
            padding: 6px 0;
            font-size: 11px;
          }
        }
      }

      .calendar-box {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
        overflow: hidden;

        .box-header {
          margin-bottom: 10px;
          .box-title { font-size: 12px; font-weight: 800; color: var(--text-primary); }
        }

        .calendar-list {
          flex: 1;
          overflow-y: auto;
          display: flex;
          flex-direction: column;
          gap: 6px;
          font-size: 11px;

          .cal-item {
            display: grid;
            grid-template-columns: 38px 38px 18px 1fr 60px;
            gap: 6px;
            align-items: center;
            padding: 6px 8px;
            border-radius: 8px;
            border: 1px solid var(--border-color);
            background: var(--tertiary-bg);

            .cal-date { font-size: 10px; color: var(--text-secondary); font-weight: 700; }
            .cal-time { font-size: 10px; color: var(--text-muted); font-family: "SF Mono", Monaco, Consolas, monospace; }
            .cal-flag { font-size: 10px; }
            .cal-name { font-size: 10px; color: var(--text-primary); font-weight: 600; }
            .cal-impact {
              font-size: 10px;
              text-align: right;
              font-weight: 700;
              &.bullish { color: var(--accent-green); }
              &.bearish { color: var(--accent-red); }
            }
          }

          .cal-empty {
            text-align: center;
            color: var(--text-muted);
            padding: 12px 0;
          }
        }
      }
    }

    .right-panel {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      gap: 10px;

      .analysis-toolbar {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 12px;
        background: var(--secondary-bg);
        border-radius: 10px;
        border: 1px solid var(--border-color);
        box-shadow: var(--card-shadow);
      }

      .analysis-main {
        flex: 1;
        min-height: 0;
        background: var(--secondary-bg);
        border-radius: 10px;
        border: 1px solid var(--border-color);
        box-shadow: var(--card-shadow);
        overflow: hidden;

        .analysis-placeholder {
          height: 100%;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 24px;
        }

        .placeholder-hero {
          width: 100%;
          max-width: 760px;
          position: relative;
          border-radius: 14px;
          overflow: hidden;
          background: var(--secondary-bg);
        }

        .hero-bg {
          position: absolute;
          inset: 0;
          pointer-events: none;
          opacity: 0.9;

          .hero-bg-circle {
            position: absolute;
            border-radius: 999px;
            filter: blur(0px);
            &.c1 { width: 260px; height: 260px; left: -60px; top: -80px; background: color-mix(in srgb, var(--accent-blue) 10%, transparent); }
            &.c2 { width: 320px; height: 320px; right: -120px; bottom: -140px; background: color-mix(in srgb, #8b5cf6 8%, transparent); }
          }

          .hero-bg-grid {
            position: absolute;
            inset: 0;
            background-image: linear-gradient(color-mix(in srgb, var(--text-muted) 12%, transparent) 1px, transparent 1px), linear-gradient(90deg, color-mix(in srgb, var(--text-muted) 12%, transparent) 1px, transparent 1px);
            background-size: 26px 26px;
            mask-image: radial-gradient(circle at 60% 40%, rgba(0, 0, 0, 1), rgba(0, 0, 0, 0) 70%);
          }
        }

        .hero-body {
          position: relative;
          padding: 34px 26px;
          text-align: center;

          .hero-badge {
            display: inline-block;
            padding: 4px 10px;
            border-radius: 999px;
            font-size: 11px;
            font-weight: 800;
            color: var(--accent-blue);
            background: color-mix(in srgb, var(--accent-blue) 10%, transparent);
            border: 1px solid color-mix(in srgb, var(--accent-blue) 18%, transparent);
          }

          .hero-title {
            margin: 14px 0 0;
            font-size: 22px;
            font-weight: 900;
            color: var(--text-primary);
          }

          .hero-subtitle {
            margin: 10px 0 0;
            font-size: 13px;
            color: var(--text-secondary);
          }

          .hero-stats {
            margin-top: 18px;
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 10px;
            text-align: left;

            .hstat {
              display: flex;
              align-items: flex-start;
              gap: 10px;
              padding: 12px;
              border-radius: 12px;
              border: 1px solid var(--border-color);
              background: var(--tertiary-bg);

              .hstat-icon {
                width: 34px;
                height: 34px;
                display: flex;
                align-items: center;
                justify-content: center;
                border-radius: 10px;
                background: color-mix(in srgb, var(--accent-blue) 10%, transparent);
                border: 1px solid color-mix(in srgb, var(--accent-blue) 16%, transparent);
                font-size: 16px;
              }

              .hstat-body {
                display: flex;
                flex-direction: column;
                gap: 2px;
                min-width: 0;

                .hstat-val { font-weight: 800; font-size: 12px; color: var(--text-primary); }
                .hstat-label { font-size: 11px; color: var(--text-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
              }
            }
          }

          .hero-cta {
            margin-top: 16px;
            display: flex;
            justify-content: center;
            gap: 10px;
          }

          .hero-hint {
            margin-top: 12px;
            font-size: 12px;
            color: var(--text-muted);
          }
        }

        .analysis-loading {
          padding: 24px;
          color: var(--text-secondary);
          font-size: 13px;
        }
      }
    }

    .watchlist-panel {
      width: 280px;
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      background: var(--secondary-bg);
      border-radius: 10px;
      border: 1px solid var(--border-color);
      box-shadow: var(--card-shadow);

      .panel-header {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 10px 12px;
        border-bottom: 1px solid var(--border-color);

        .panel-title {
          font-size: 12px;
          font-weight: 800;
          color: var(--text-primary);
        }

        .panel-count {
          font-size: 10px;
          font-weight: 600;
          color: var(--text-muted);
          background: var(--tertiary-bg);
          border-radius: 8px;
          padding: 0 6px;
          line-height: 18px;
        }

        .panel-actions {
          margin-left: auto;
          display: flex;
          gap: 4px;
        }
      }

      .watchlist-summary {
        display: flex;
        border-bottom: 1px solid var(--border-color);

        .ws-item {
          flex: 1;
          display: flex;
          flex-direction: column;
          align-items: center;
          padding: 8px 4px;
          cursor: default;

          .ws-label {
            font-size: 9px;
            color: var(--text-muted);
            font-weight: 500;
            margin-bottom: 2px;
          }

          .ws-value {
            font-size: 13px;
            font-weight: 800;
            color: var(--text-primary);

            &.ws-tasks {
              cursor: pointer;
              color: var(--accent-blue);
              display: inline-flex;
              align-items: center;
              gap: 2px;
              &:hover { text-decoration: underline; }

              .ws-arrow {
                opacity: 0.6;
                transition: transform 0.2s;
              }
              &:hover .ws-arrow {
                transform: translateX(1px);
                opacity: 1;
              }
            }

            &.up { color: var(--accent-green); }
            &.down { color: var(--accent-red); }
          }
        }
      }

      .batch-bar {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 6px 12px;
        background: var(--tertiary-bg);
        border-bottom: 1px solid var(--border-color);

        .batch-select-all {
          display: flex;
          align-items: center;
          gap: 4px;
          cursor: pointer;
          font-size: 11px;
          color: var(--text-secondary);
          white-space: nowrap;
        }
      }

      .watchlist {
        padding: 6px 12px;
        overflow: auto;
        flex: 1;
        min-height: 0;

        .watch-item {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 7px 0;
          border-bottom: 1px solid var(--border-color);
          cursor: pointer;

          &:last-child { border-bottom: none; }

          .wi-checkbox {
            width: 14px;
            height: 14px;
            cursor: pointer;
            accent-color: var(--accent-blue);
          }

          .wi-info {
            flex: 1;
            min-width: 0;
            overflow: hidden;

            .wi-symbol {
              font-size: 12px;
              font-weight: 800;
              color: var(--text-primary);
              display: flex;
              align-items: center;
              gap: 2px;
            }

            .wi-name {
              font-size: 9px;
              color: var(--text-muted);
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }
          }

          .wi-price {
            font-size: 12px;
            font-weight: 700;
            color: var(--text-primary);
            text-align: right;
            min-width: 56px;
            flex-shrink: 0;
          }

          .wi-change {
            font-size: 11px;
            font-weight: 700;
            text-align: right;
            min-width: 52px;
            flex-shrink: 0;
            &.up { color: var(--accent-green); }
            &.down { color: var(--accent-red); }
          }

          .wi-mini {
            width: 56px;
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: center;
          }

          .wi-hover-actions {
            flex-shrink: 0;
            display: flex;
            align-items: center;
            gap: 2px;

            .wi-icon-btn {
              display: inline-flex;
              align-items: center;
              justify-content: center;
              width: 22px;
              height: 22px;
              border-radius: 4px;
              cursor: pointer;
              color: var(--text-secondary);
              transition: background 0.15s, color 0.15s;

              &:hover {
                background: var(--tertiary-bg);
                color: var(--text-primary);
              }
            }

            .wi-monitor-btn.active {
              color: var(--accent-green);
            }

            .wi-remove-btn:hover {
              color: var(--accent-red);
            }
          }
        }

        .watch-empty {
          padding: 16px 0;
        }
      }

      .bottom-area {
        margin-top: 12px;
        background: var(--secondary-bg);
        border-radius: 10px;
        border: 1px solid var(--border-color);
        overflow: hidden;

        .bottom-area-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 8px 14px;
          border-bottom: 1px solid var(--border-color);

          .bottom-area-title {
            font-size: 11px;
            font-weight: 700;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 0.5px;
          }

          .bottom-area-status {
            font-size: 11px;
            font-weight: 600;
            color: var(--text-muted);

            &.is-running {
              color: var(--accent-blue);
            }

            &.is-idle {
              color: var(--accent-green);
            }
          }
        }

        .bottom-area-body {
          max-height: 120px;
          overflow-y: auto;
          padding: 6px 14px;

          .log-line {
            display: flex;
            gap: 8px;
            padding: 2px 0;
            font-family: "SF Mono", Consolas, monospace;
            font-size: 11px;

            .log-time {
              color: var(--text-muted);
              flex-shrink: 0;
            }

            .log-msg {
              color: var(--text-secondary);
            }

            &.log-empty .log-msg {
              color: var(--text-muted);
              font-style: italic;
            }
          }
        }
      }

      .polymarket-placeholder {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 300px;
      }
    }

    @media (max-width: 768px) {
      padding: 8px;
      min-height: auto;

      .radar-section {
        margin-bottom: 10px;

        .radar-header {
          flex-direction: column;
          align-items: stretch;
          gap: 8px;

          .radar-header-left {
            .radar-title { font-size: 15px; }
            .radar-subtitle { font-size: 11px; }
          }

          .header-actions {
            align-self: flex-end;
          }
        }

        .radar-carousel {
          &::before,
          &::after {
            width: 28px;
          }
        }

        .radar-card {
          width: 160px;
          padding: 6px 8px;

          .rc-head .rc-symbol { font-size: 11px; }
          .rc-metrics .rc-metric { padding: 3px; }
        }
      }

      .tab-body {
        padding: 12px;

        .main-body {
          flex-direction: column;
        }

        .left-panel,
        .watchlist-panel {
          width: 100%;
        }
      }
    }
  }
}
</style>

<style lang="scss">
.add-symbol-body {
  .el-input { margin-bottom: 12px; }
}

.add-symbol-list {
  max-height: 400px;
  overflow-y: auto;

  .add-symbol-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 4px;
    cursor: pointer;
    border-radius: 6px;
    transition: background 0.15s;

    &:hover { background: var(--tertiary-bg); }

    .asi-left {
      display: flex;
      align-items: center;
      gap: 6px;
      min-width: 0;

      svg { flex-shrink: 0; }

      .asi-symbol {
        font-size: 13px;
        font-weight: 700;
        color: var(--text-primary);
      }

      .asi-name {
        font-size: 11px;
        color: var(--text-muted);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }

    .asi-right {
      display: flex;
      align-items: center;
      gap: 8px;

      .asi-price {
        font-size: 12px;
        font-weight: 700;
        color: var(--text-secondary);
      }

      .asi-change {
        font-size: 11px;
        font-weight: 700;
        min-width: 48px;
        text-align: right;

        &.up { color: var(--accent-green); }
        &.down { color: var(--accent-red); }
      }
    }
  }
}

.add-symbol-empty {
  text-align: center;
  color: var(--text-muted);
  padding: 24px 0;
  font-size: 13px;
}

.batch-schedule-info {
  padding: 8px 0;
  font-size: 13px;
  color: var(--text-muted);
  text-align: center;

  strong { color: var(--accent-blue); }
}

.task-drawer-body {
  .task-empty { padding: 40px 0; }

  .task-item {
    padding: 12px;
    margin-bottom: 8px;
    background: var(--tertiary-bg);
    border-radius: 8px;
    border: 1px solid var(--border-color);

    .task-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 6px;

      .task-symbols {
        font-size: 13px;
        font-weight: 700;
        color: var(--text-primary);
        word-break: break-all;
      }
    }

    .task-meta {
      display: flex;
      gap: 12px;
      font-size: 11px;
      color: var(--text-muted);
      margin-bottom: 6px;
    }

    .task-actions {
      display: flex;
      gap: 8px;
    }
  }
}

.edit-symbols {
  font-size: 13px;
  color: var(--text-secondary);
  padding: 4px 0;
  word-break: break-all;
}
</style>
