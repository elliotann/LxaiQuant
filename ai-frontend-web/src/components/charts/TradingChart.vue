<template>
  <div class="trading-chart">
    <!-- 顶部工具栏 -->
    <div class="chart-header">
      <!-- 第一行：交易对选择 + 价格显示 -->
      <div class="header-top">
        <div class="symbol-selector-wrapper">
          <label class="selector-label">交易对:</label>
          <el-select
            v-model="selectedSymbol"
            @change="onSymbolChange"
            placeholder="选择交易对"
            size="small"
            style="width: 180px"
          >
            <el-option-group label="现货交易">
              <el-option label="BTC/USDT" value="BTC/USDT" />
              <el-option label="ETH/USDT" value="ETH/USDT" />
            </el-option-group>
            <el-option-group label="合约交易">
              <el-option label="BTC-USDT-SWAP" value="BTC-USDT-SWAP" />
              <el-option label="ETH-USDT-SWAP" value="ETH-USDT-SWAP" />
            </el-option-group>
          </el-select>

          <!-- 价格显示 -->
          <div class="price-display">
            <span class="price" :class="currentPriceChangeClass">
              ${{ formatPrice(currentPrice || 0) }}
            </span>
            <span class="change" :class="currentPriceChangeClass">
              {{ priceChangePercent }}%
            </span>
          </div>
        </div>
      </div>

      <!-- 第二行：机器人选择 + 会员配置 + 时间周期 + 技术指标 + 时间跳转 -->
      <div class="header-toolbar">
        <!-- 机器人选择 -->
        <div class="robot-selector-wrapper">
          <label class="selector-label">机器人:</label>
          <el-select
            v-model="selectedRobot"
            @change="onRobotChange"
            :disabled="robotsLoading"
            placeholder="请选择机器人"
            size="small"
            style="width: 180px"
          >
            <el-option label="请选择机器人" value="" />
            <el-option
              v-for="robot in robots"
              :key="robot.id"
              :label="robot.name"
              :value="robot.id"
            />
          </el-select>

          <!-- 会员配置选择 -->
          <div class="member-config-selector" v-if="selectedRobot">
            <label class="selector-label">会员配置:</label>
            <el-select
              v-model="selectedMemberConfig"
              @change="onMemberConfigChange"
              :disabled="memberConfigsLoading"
              placeholder="请选择会员配置"
              size="small"
              style="width: 180px"
            >
              <el-option label="请选择会员配置" value="" />
              <el-option
                v-for="config in memberConfigs"
                :key="config.id"
                :label="`${config.memberName || config.memberId} / ${config.apiName || config.memberPlatform}`"
                :value="config.id"
              />
            </el-select>

            <span v-if="memberConfigsLoading" class="loading-text"
              >加载中...</span
            >
            <span
              v-else-if="memberConfigs && memberConfigs.length === 0"
              class="empty-text"
              >暂无会员配置</span
            >
          </div>
        </div>

        <!-- 时间周期选择 -->
        <div class="toolbar-group">
          <span class="toolbar-label">周期:</span>
          <el-button-group size="small">
            <el-button
              v-for="interval in timeIntervals"
              :key="interval.value"
              :type="
                currentTimeframe === interval.value ? 'primary' : 'default'
              "
              @click="changeTimeframe(interval.value)"
            >
              {{ interval.label }}
            </el-button>
          </el-button-group>
        </div>

        <!-- 技术指标选择 -->
        <div class="toolbar-group">
          <span class="toolbar-label">技术指标:</span>
          <el-dropdown
            trigger="click"
            placement="bottom-start"
            @command="onIndicatorSelect"
          >
            <el-button type="default" size="small">
              指标选择
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="boll">BOLL</el-dropdown-item>
                <el-dropdown-item command="macd">MACD</el-dropdown-item>
                <el-dropdown-item command="rsi">RSI</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- 时间跳转 -->
        <div class="toolbar-group">
          <span class="toolbar-label">时间跳转:</span>
          <el-date-picker
            v-model="jumpTime"
            type="datetime"
            placeholder="选择时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            size="small"
            style="width: 180px"
          />
          <el-button
            type="primary"
            size="small"
            :loading="jumpTimeLoading"
            @click="handleTimeJump"
            style="margin-left: 8px"
          >
            跳转
          </el-button>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="chart-content">
      <div class="chart-container" ref="chartContainer">
        <div ref="chartElement" class="chart"></div>

        <!-- 加载状态 -->
        <div v-if="loading" class="chart-loading">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载图表数据中...</p>
        </div>

        <!-- 加载历史数据状态 -->
        <div v-if="isLoadingMoreData" class="chart-loading-history">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载历史数据中...</p>
        </div>

        <!-- 加载最新数据状态 -->
        <div v-if="isLoadingLatestData" class="chart-loading-latest">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p>加载最新数据中...</p>
        </div>
      </div>
    </div>

    <!-- 底部面板分隔符 -->
    <div class="bottom-panel-divider" ref="bottomPanelDivider">
      <div class="divider-handle"></div>
    </div>

    <!-- 底部面板 -->
    <div class="bottom-panel" :style="{ height: bottomPanelHeight + 'px' }">
      <div class="bottom-tabs">
        <button
          class="bottom-tab-btn"
          :class="{ active: bottomActiveTab === 'backtest' }"
          @click="bottomActiveTab = 'backtest'"
        >
          📊 回测
        </button>

        <button
          class="bottom-tab-btn"
          :class="{ active: bottomActiveTab === 'positions' }"
          @click="bottomActiveTab = 'positions'"
        >
          📊 持仓信息
        </button>

        <button
          class="bottom-tab-btn"
          :class="{ active: bottomActiveTab === 'orderEntities' }"
          @click="bottomActiveTab = 'orderEntities'"
        >
          📋 历史仓位
        </button>

        <button
          class="bottom-tab-btn"
          :class="{ active: bottomActiveTab === 'trades' }"
          @click="bottomActiveTab = 'trades'"
        >
          📊 权益曲线
        </button>
      </div>

      <div class="bottom-content">
        <!-- 回测面板 -->
        <div v-show="bottomActiveTab === 'backtest'" class="bottom-tab-panel">
          <div class="backtest-content">
            <!-- 回测子tab导航 -->
            <div class="backtest-sub-tabs">
              <button
                class="backtest-sub-tab-btn"
                :class="{ active: backtestSubTab === 'run' }"
                @click="backtestSubTab = 'run'"
              >
                ▶️ 运行回测
              </button>

              <button
                class="backtest-sub-tab-btn"
                :class="{ active: backtestSubTab === 'backtest-records' }"
                @click="backtestSubTab = 'backtest-records'"
              >
                📊 回测记录
              </button>
            </div>

            <!-- 运行回测面板 -->
            <div v-show="backtestSubTab === 'run'" class="backtest-run-panel">
              <div class="backtest-form">
                <div class="form-row">
                  <label class="form-label">回测周期:</label>
                  <el-select
                    v-model="backtestPeriod"
                    size="small"
                    style="width: 120px"
                  >
                    <el-option label="1天" value="1d" />
                    <el-option label="7天" value="7d" />
                    <el-option label="30天" value="30d" />
                    <el-option label="90天" value="90d" />
                  </el-select>
                </div>

                <div class="form-row">
                  <label class="form-label">初始资金:</label>
                  <el-input-number
                    v-model="initialCapital"
                    :min="100"
                    :max="10000000"
                    size="small"
                    style="width: 120px"
                  />
                </div>

                <div class="form-row">
                  <el-button
                    type="primary"
                    size="small"
                    :loading="isRunningBacktest"
                    @click="runBacktest"
                  >
                    开始回测
                  </el-button>
                </div>
              </div>

              <!-- 回测进度 -->
              <div v-if="backtestProgress > 0" class="backtest-progress">
                <div class="progress-bar">
                  <el-progress
                    :percentage="backtestProgress"
                    :show-text="false"
                  />
                </div>
                <span class="progress-text">{{ backtestProgress }}% 完成</span>
              </div>
            </div>

            <!-- 回测记录面板 -->
            <div
              v-show="backtestSubTab === 'backtest-records'"
              class="backtest-records-panel"
            >
              <div class="records-table">
                <div class="table-header">
                  <span>时间</span>
                  <span>周期</span>
                  <span>初始资金</span>
                  <span>最终资金</span>
                  <span>收益</span>
                  <span>胜率</span>
                  <span>操作</span>
                </div>
                <div
                  v-for="record in backtestRecords"
                  :key="record.id"
                  class="table-row"
                >
                  <span>{{ formatDate(record.createTime) }}</span>
                  <span>{{ record.period }}</span>
                  <span>${{ formatPrice(record.initialCapital) }}</span>
                  <span>${{ formatPrice(record.finalCapital) }}</span>
                  <span
                    :class="
                      record.profit >= 0 ? 'profit-positive' : 'profit-negative'
                    "
                  >
                    {{ record.profit >= 0 ? "+" : ""
                    }}{{ record.profit.toFixed(2) }}%
                  </span>
                  <span>{{ record.winRate.toFixed(2) }}%</span>
                  <span>
                    <el-button
                      size="mini"
                      type="primary"
                      @click="viewBacktestDetail(record)"
                      >详情</el-button
                    >
                    <el-button
                      size="mini"
                      type="danger"
                      @click="deleteBacktestRecord(record)"
                      >删除</el-button
                    >
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 持仓信息面板 -->
        <div v-show="bottomActiveTab === 'positions'" class="bottom-tab-panel">
          <div class="positions-content">
            <div v-if="positions.length === 0" class="empty-state">
              <p>暂无持仓信息</p>
            </div>
            <div v-else class="positions-table">
              <div class="table-header">
                <span>交易对</span>
                <span>方向</span>
                <span>数量</span>
                <span>开仓价</span>
                <span>当前价</span>
                <span>收益</span>
                <span>操作</span>
              </div>
              <div
                v-for="position in positions"
                :key="position.id"
                class="table-row"
              >
                <span>{{ position.symbol }}</span>
                <span
                  :class="position.side === 'long' ? 'side-long' : 'side-short'"
                >
                  {{ position.side === "long" ? "多头" : "空头" }}
                </span>
                <span>{{ position.amount }}</span>
                <span>{{ formatPrice(position.entryPrice) }}</span>
                <span>{{ formatPrice(position.currentPrice) }}</span>
                <span
                  :class="position.pnl >= 0 ? 'pnl-positive' : 'pnl-negative'"
                >
                  {{ position.pnl >= 0 ? "+" : ""
                  }}{{ formatPrice(position.pnl) }}
                </span>
                <span>
                  <el-button
                    size="mini"
                    type="warning"
                    @click="closePosition(position)"
                    >平仓</el-button
                  >
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 订单信息面板 -->
        <div
          v-show="bottomActiveTab === 'orderEntities'"
          class="bottom-tab-panel"
        >
          <div class="orders-content">
            <div v-if="orders.length === 0" class="empty-state">
              <p>暂无订单信息</p>
            </div>
            <div v-else class="orders-table">
              <div class="table-header">
                <span>交易对</span>
                <span>类型</span>
                <span>方向</span>
                <span>数量</span>
                <span>价格</span>
                <span>状态</span>
                <span>时间</span>
                <span>操作</span>
              </div>
              <div v-for="order in orders" :key="order.id" class="table-row">
                <span>{{ order.symbol }}</span>
                <span>{{ order.type }}</span>
                <span :class="order.side === 'buy' ? 'side-buy' : 'side-sell'">
                  {{ order.side === "buy" ? "买入" : "卖出" }}
                </span>
                <span>{{ order.amount }}</span>
                <span>{{ formatPrice(order.price) }}</span>
                <span :class="getOrderStatusClass(order.status)">
                  {{ getOrderStatusText(order.status) }}
                </span>
                <span>{{ formatDate(order.createTime) }}</span>
                <span>
                  <el-button
                    size="mini"
                    type="warning"
                    @click="cancelOrder(order)"
                    v-if="order.status === 'pending'"
                    >取消</el-button
                  >
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 权益曲线面板 -->
        <div v-show="bottomActiveTab === 'trades'" class="bottom-tab-panel">
          <div class="equity-content">
            <div id="equity-chart" class="equity-chart-container"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from "vue";
import { createChart, ColorType } from "lightweight-charts";
import { Loading, ArrowDown } from "@element-plus/icons-vue";
import { getKlineChartData } from "@/api/trading";
import { getAllRobotList } from "@/api/robot";
import { ElMessage } from "element-plus";

// Props定义
defineProps<{
  height?: number;
}>();

// 响应式数据
const loading = ref(false);
const currentTimeframe = ref("1h");
const selectedSymbol = ref("ETH-USDT-SWAP");
const currentPrice = ref(0);
const priceChangePercent = ref("0.00");

// 时间间隔配置
const timeIntervals = [
  { value: "1m", label: "1分钟" },
  { value: "3m", label: "3分钟" },
  { value: "5m", label: "5分钟" },
  { value: "15m", label: "15分钟" },
  { value: "30m", label: "30分钟" },
  { value: "1h", label: "1小时" },
  { value: "4h", label: "4小时" },
  { value: "1d", label: "1天" },
];

// 底部面板相关数据
const bottomActiveTab = ref("trades"); // 默认显示权益曲线tab
const backtestSubTab = ref("run"); // 默认显示运行回测
const bottomPanelHeight = ref(300); // 默认高度
const bottomPanelDivider = ref(null);

// 回测相关数据
const backtestPeriod = ref("30d");
const initialCapital = ref(10000);
const isRunningBacktest = ref(false);
const backtestProgress = ref(0);
const backtestRecords = ref([]);

// 持仓和订单数据
const positions = ref([]);
const orders = ref([]);

// 权益曲线图表实例
let equityChart = null;

// 机器人相关
const selectedRobot = ref("");
const robots = ref([]);
const robotsLoading = ref(false);

// 会员配置相关
const memberConfigs = ref([]);
const memberConfigsLoading = ref(false);
const selectedMemberConfig = ref("");

// 时间跳转相关
const jumpTime = ref("");
const jumpTimeLoading = ref(false);

// 图表相关
let chart: any = null;
let candlestickSeries: any = null;
let chartElement = ref<HTMLElement>();
let chartContainer = ref<HTMLElement>();

// K线数据
const chartData = ref<any[]>([]);

// 无限加载相关
const earliestDataTime = ref<number | null>(null);
const latestDataTime = ref<number | null>(null);
const isLoadingMoreData = ref(false);
const isLoadingLatestData = ref(false);
const hasMoreData = ref(true);

// 计算属性
const currentPriceChangeClass = computed(() => {
  const percent = parseFloat(priceChangePercent.value);
  return percent > 0 ? "positive" : percent < 0 ? "negative" : "neutral";
});

// 时间格式映射
const mapTimeframeToEnum = (timeframe: string): string => {
  const mapping: Record<string, string> = {
    "1m": "OKXMIN1",
    "3m": "OKXMIN3",
    "5m": "OKXMIN5",
    "15m": "OKXMIN15",
    "30m": "OKXMIN30",
    "1h": "OKXMIN60",
    "4h": "OKX4HOUR",
    "1d": "OKX1D",
  };
  return mapping[timeframe] || "OKXMIN60";
};

// 格式化价格
const formatPrice = (price: number): string => {
  return price.toFixed(price >= 1 ? 2 : 6);
};

// 获取K线数据
const fetchKlineData = async (params: {
  symbol: string;
  interval: string;
  limit: number;
  startTime?: number;
  endTime?: number;
}) => {
  try {
    const response = await getKlineChartData({
      symbol: params.symbol.includes("-SWAP")
        ? params.symbol
        : params.symbol.replace("/", "-"),
      interval: params.interval,
      limit: params.limit,
      startTime: params.startTime,
      endTime: params.endTime,
    });
    return response;
  } catch (error) {
    console.error("获取K线数据失败:", error);
    ElMessage.error("获取K线数据失败");
    throw error;
  }
};

// 初始化图表
const initChart = async () => {
  if (!chartElement.value) return;

  loading.value = true;

  try {
    // 创建图表
    chart = createChart(chartElement.value, {
      width: chartElement.value.clientWidth,
      height: chartElement.value.clientHeight,
      layout: {
        background: { type: ColorType.Solid, color: "#ffffff" },
        textColor: "#333333",
      },
      grid: {
        vertLines: { color: "#e0e3eb" },
        horzLines: { color: "#e0e3eb" },
      },
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
      },
      localization: {
        timeFormatter: (timestamp) => {
          // 格式化K线时间（UTC+8，显示日期和时间），与KlineNew.vue保持一致
          if (!timestamp) return "-";

          // 时间戳是 UTC 时间戳（秒级），使用 toLocaleString 转换为北京时间
          const utcDate = new Date(timestamp * 1000);
          return utcDate.toLocaleString("zh-CN", {
            timeZone: "Asia/Shanghai",
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
          });
        },
      },
    });

    // 创建K线系列
    candlestickSeries = chart.addCandlestickSeries({
      upColor: "#26a69a",
      downColor: "#ef5350",
      borderVisible: false,
      wickUpColor: "#26a69a",
      wickDownColor: "#ef5350",
    });

    // 获取初始数据
    await loadInitialData();

    // 自适应内容
    chart.timeScale().fitContent();

    // 监听时间范围变化，用于拖动加载更多数据
    chart.timeScale().subscribeVisibleTimeRangeChange((timeRange) => {
      if (timeRange) {
        const from = timeRange.from * 1000; // 转换为毫秒
        const to = timeRange.to * 1000; // 转换为毫秒

        // 获取数据的时间范围
        let earliestData, latestData;
        if (earliestDataTime.value && latestDataTime.value) {
          earliestData = earliestDataTime.value * 1000;
          latestData = latestDataTime.value * 1000;
        }

        onTimeRangeChange({ from, to, earliestData, latestData });
      }
    });
  } catch (error) {
    console.error("初始化图表失败:", error);
    ElMessage.error("初始化图表失败");
  } finally {
    loading.value = false;
  }
};

// 图表resize函数
const resizeChart = () => {
  if (chart && chartElement.value) {
    const newWidth = chartElement.value.clientWidth;
    const newHeight = chartElement.value.clientHeight;
    chart.resize(newWidth, newHeight);
    console.log("图表已resize:", newWidth, "x", newHeight);
  }
};

// 时间范围变化防抖定时器
let signalLoadTimer = null;

// 处理时间范围变化事件（拖动图表时触发）
const onTimeRangeChange = async (timeRange) => {
  // 防抖处理，避免频繁加载
  if (signalLoadTimer) {
    clearTimeout(signalLoadTimer);
  }

  signalLoadTimer = setTimeout(async () => {
    try {
      // 检查是否需要加载更多K线数据
      await loadMoreKlineDataIfNeeded(timeRange);
    } catch (error) {
      console.error("处理时间范围变化时出错:", error);
    }
  }, 500); // 500ms 防抖
};

// 根据时间范围检查是否需要加载更多K线数据
const loadMoreKlineDataIfNeeded = async (timeRange) => {
  if (!timeRange || !earliestDataTime.value || !latestDataTime.value) {
    return;
  }

  // 如果已经有加载任务在进行中，跳过
  if (isLoadingMoreData.value || isLoadingLatestData.value) {
    console.log("已有加载任务进行中，跳过本次请求");
    return;
  }

  const { from, to } = timeRange;
  // timeRange.from/to 是毫秒级，数据时间是秒级，统一转换为秒进行比较
  const currentDataStartTime = earliestDataTime.value;
  const currentDataEndTime = latestDataTime.value;
  const requestFromSeconds = Math.floor(from / 1000);
  const requestToSeconds = Math.floor(to / 1000);

  // 添加缓冲区，避免频繁加载（提前10个K线就开始加载）
  const bufferKlines = 10;
  const bufferSeconds = bufferKlines * getIntervalSeconds();
  const needLoadLeft =
    requestFromSeconds < currentDataStartTime + bufferSeconds;
  const needLoadRight = requestToSeconds > currentDataEndTime - bufferSeconds;

  console.log("检查K线数据加载需求:", {
    请求范围秒: { from: requestFromSeconds, to: requestToSeconds },
    当前数据范围秒: { start: currentDataStartTime, end: currentDataEndTime },
    缓冲秒数: bufferSeconds,
    需要向前加载: needLoadLeft,
    需要向后加载: needLoadRight,
  });

  // 根据拖动方向只加载一个方向的数据，避免同时加载两个方向
  if (needLoadLeft && needLoadRight) {
    // 如果两个条件都满足，优先判断用户的拖动意图
    // 计算哪个方向的缺口更大
    const leftGap = currentDataStartTime + bufferSeconds - requestFromSeconds;
    const rightGap = requestToSeconds - (currentDataEndTime - bufferSeconds);

    console.log("两个方向都需要加载，比较缺口大小:", { leftGap, rightGap });

    if (leftGap > rightGap) {
      // 左边缺口更大，优先加载历史数据
      console.log("优先加载历史数据（左边缺口更大）");
      await loadKlineDataByDirection("LEFT", from);
    } else {
      // 右边缺口更大，优先加载最新数据
      console.log("优先加载最新数据（右边缺口更大）");
      await loadKlineDataByDirection("RIGHT", to);
    }
  } else if (needLoadLeft) {
    // 只加载历史数据
    console.log("只加载历史数据");
    await loadKlineDataByDirection("LEFT", from);
  } else if (needLoadRight) {
    // 只加载最新数据
    console.log("只加载最新数据");
    await loadKlineDataByDirection("RIGHT", to);
  } else {
    console.log("当前数据范围足够，无需加载更多数据");
  }
};

// 按方向加载K线数据
const loadKlineDataByDirection = async (direction, timestamp) => {
  try {
    // 设置加载状态
    if (direction === "LEFT") {
      isLoadingMoreData.value = true;
    } else if (direction === "RIGHT") {
      isLoadingLatestData.value = true;
    }
    const params = {
      symbol: selectedSymbol.value,
      interval: mapTimeframeToEnum(currentTimeframe.value),
      limit: 200,
      direction: direction, // 'LEFT' 或 'RIGHT'
    };

    // 根据方向设置不同的时间参数
    if (direction === "LEFT") {
      // 向左加载：加载timestamp之前的数据
      params.endTime = Math.floor(timestamp / 1000) - getIntervalSeconds(); // 多加载一些缓冲
      params.limit = 200;
    } else if (direction === "RIGHT") {
      // 向右加载：加载timestamp之后的数据
      params.startTime = Math.floor(timestamp / 1000) + getIntervalSeconds(); // 多加载一些缓冲
      params.limit = 200;
    }

    console.log(`${direction}方向加载K线数据:`, params);

    const response = await getKlineChartData(params);

    if (response && response.data && Array.isArray(response.data)) {
      const newData = response.data;

      if (newData.length > 0) {
        console.log(`成功加载 ${direction} 方向 ${newData.length} 条K线数据`);

        // 将新数据合并到现有数据中，并确保时间连续性
        mergeKlineData(newData, direction);

        // 数据已经在mergeKlineData中更新，这里不需要额外更新
      } else {
        console.log(`${direction} 方向没有更多数据`);
      }
    } else {
      console.warn(`${direction} 方向加载K线数据失败:`, response);
    }
  } catch (error) {
    console.error(`${direction} 方向加载K线数据出错:`, error);
  } finally {
    // 重置加载状态
    if (direction === "LEFT") {
      isLoadingMoreData.value = false;
    } else if (direction === "RIGHT") {
      isLoadingLatestData.value = false;
    }
  }
};

// 智能合并K线数据，避免断层
const mergeKlineData = (newData, direction) => {
  if (!newData || newData.length === 0) {
    return;
  }

  // 创建以时间为key的Map，用于去重和排序
  const dataMap = new Map();

  // 将现有数据添加到Map中
  chartData.value.forEach((item) => {
    dataMap.set(item.time, item);
  });

  // 将新数据添加到Map中（自动去重）
  newData.forEach((item) => {
    // 确保数据格式正确
    const formattedItem = {
      time: Math.floor(item.timestamp / 1000),
      open: parseFloat(item.open || item.o),
      high: parseFloat(item.high || item.h),
      low: parseFloat(item.low || item.l),
      close: parseFloat(item.close || item.c),
      volume: parseFloat(item.volume || item.v || item.vol || 0),
    };
    dataMap.set(formattedItem.time, formattedItem);
  });

  // 将Map转换为数组并按时间排序
  const mergedData = Array.from(dataMap.values()).sort(
    (a, b) => a.time - b.time,
  );

  // 检查数据连续性
  checkDataContinuity(mergedData, direction);

  // 更新chartData和图表
  chartData.value = mergedData;
  candlestickSeries.setData(mergedData);

  // 更新时间范围
  if (mergedData.length > 0) {
    earliestDataTime.value = mergedData[0].time;
    latestDataTime.value = mergedData[mergedData.length - 1].time;
  }

  console.log(
    `K线数据合并完成 - 方向: ${direction}, 新增: ${newData.length}, 总数: ${mergedData.length}`,
  );
};

// 检查K线数据的连续性
const checkDataContinuity = (data, direction) => {
  if (data.length < 2) {
    return;
  }

  const intervalSeconds = getIntervalSeconds();
  let gapCount = 0;
  let totalGapSeconds = 0;

  for (let i = 1; i < data.length; i++) {
    const currentTime = data[i].time;
    const prevTime = data[i - 1].time;
    const expectedTime = prevTime + intervalSeconds;
    const gap = currentTime - expectedTime;

    if (gap > intervalSeconds) {
      gapCount++;
      totalGapSeconds += gap;
      console.warn(
        `发现K线断层 - 位置: ${i}, 预期时间: ${expectedTime}, 实际时间: ${currentTime}, 缺口: ${gap}秒`,
      );
    }
  }

  if (gapCount > 0) {
    console.warn(
      `数据连续性检查完成 - 方向: ${direction}, 断层数量: ${gapCount}, 总缺口时间: ${totalGapSeconds}秒`,
    );
  } else {
    console.log(`数据连续性检查通过 - 方向: ${direction}, 无断层`);
  }
};

// 获取当前时间间隔的秒数
const getIntervalSeconds = () => {
  const interval = mapTimeframeToEnum(currentTimeframe.value) || "OKXMIN3";
  const intervalMap = {
    OKXMIN1: 60,
    OKXMIN3: 180,
    OKXMIN5: 300,
    OKXMIN15: 900,
    OKXMIN30: 1800,
    OKXMIN60: 3600,
    OKX4HOUR: 14400,
    OKX1D: 86400,
  };
  return intervalMap[interval] || 180; // 默认3分钟
};

// 加载初始数据
const loadInitialData = async () => {
  try {
    console.log("🔄 开始加载K线数据:", {
      symbol: selectedSymbol.value,
      interval: mapTimeframeToEnum(currentTimeframe.value),
      limit: 100,
    });

    // 清空现有数据，确保每次都是全新的
    chartData.value = [];
    earliestDataTime.value = null;
    latestDataTime.value = null;

    const response = await fetchKlineData({
      symbol: selectedSymbol.value,
      interval: mapTimeframeToEnum(currentTimeframe.value),
      limit: 100,
    });

    console.log("📡 API响应:", response);

    let klineData = [];
    if (response && response.data) {
      if (Array.isArray(response.data)) {
        klineData = response.data;
      } else if (response.data.data && Array.isArray(response.data.data)) {
        klineData = response.data.data;
      }
    }

    console.log("📊 解析后的K线数据:", {
      length: klineData.length,
      firstItem: klineData[0],
      lastItem: klineData[klineData.length - 1],
      isEmpty: klineData.length === 0,
    });

    if (klineData.length > 0) {
      // 转换数据格式
      const formattedData = klineData.map((item: any) => ({
        time: Math.floor(item.timestamp / 1000),
        open: parseFloat(item.open || item.o),
        high: parseFloat(item.high || item.h),
        low: parseFloat(item.low || item.l),
        close: parseFloat(item.close || item.c),
        volume: parseFloat(item.volume || item.v || item.vol || 0),
      }));

      // 更新数据
      chartData.value = formattedData;

      // 设置图表数据
      candlestickSeries.setData(formattedData);

      // 记录时间范围
      earliestDataTime.value = formattedData[0].time;
      latestDataTime.value = formattedData[formattedData.length - 1].time;

      // 更新价格显示
      const lastData = formattedData[formattedData.length - 1];
      currentPrice.value = lastData.close;
      priceChangePercent.value = "0.00"; // 这里可以计算涨跌幅

      console.log("✅ 成功加载并设置K线数据到图表");
    } else {
      console.warn("⚠️ API返回的K线数据为空，图表将显示为空");
      console.log("API响应详情:", {
        response,
        responseData: response?.data,
        isArray: Array.isArray(response?.data),
        hasNestedData:
          response?.data?.data && Array.isArray(response.data.data),
      });

      // 清空图表数据
      chartData.value = [];
      if (candlestickSeries) {
        candlestickSeries.setData([]);
      }

      ElMessage.warning("未获取到K线数据，请检查API配置或网络连接");
    }
  } catch (error) {
    console.error("❌ 加载初始数据失败:", error);
    console.error("错误详情:", {
      message: error.message,
      stack: error.stack,
    });

    // 发生错误时也清空数据
    chartData.value = [];
    if (candlestickSeries) {
      candlestickSeries.setData([]);
    }

    ElMessage.error("加载图表数据失败，请检查网络连接");
  }
};

// 切换时间周期
const changeTimeframe = async () => {
  if (chart && candlestickSeries) {
    await loadInitialData();
    chart.timeScale().fitContent();
  }
};

// 切换交易对
const onSymbolChange = async () => {
  if (chart && candlestickSeries) {
    await loadInitialData();
    chart.timeScale().fitContent();
  }
};

// 机器人选择
const onRobotChange = async () => {
  // 清空会员配置选择
  selectedMemberConfig.value = "";
  memberConfigs.value = [];

  if (selectedRobot.value) {
    // 加载会员配置
    await loadMemberConfigs();
  }
};

// 会员配置选择
const onMemberConfigChange = () => {
  // 这里可以添加会员配置切换的逻辑
};

// 技术指标选择
const onIndicatorSelect = (command: string) => {
  console.log("选择技术指标:", command);
  // 这里可以添加技术指标切换的逻辑
};

// 底部面板相关方法
const watchBottomActiveTab = () => {
  // 当切换到持仓信息tab时，加载持仓数据
  if (bottomActiveTab.value === "positions") {
    loadPositions();
  }
  // 当切换到订单信息tab时，加载订单数据
  else if (bottomActiveTab.value === "orderEntities") {
    loadOrders();
  }
  // 当切换到权益曲线tab时初始化图表
  else if (bottomActiveTab.value === "trades") {
    initEquityChart();
  }
  // 当切换到回测记录tab时，自动加载回测记录
  else if (bottomActiveTab.value === "backtest") {
    loadBacktestRecords();
  }
};

// 运行回测
const runBacktest = async () => {
  if (!selectedRobot.value || !selectedMemberConfig.value) {
    ElMessage.warning("请先选择机器人和会员配置");
    return;
  }

  isRunningBacktest.value = true;
  backtestProgress.value = 0;

  try {
    // 这里调用回测API
    const response = await runBacktestAPI({
      robotId: selectedRobot.value,
      memberConfigId: selectedMemberConfig.value,
      symbol: selectedSymbol.value,
      period: backtestPeriod.value,
      initialCapital: initialCapital.value,
    });

    // 模拟进度更新
    const progressInterval = setInterval(() => {
      backtestProgress.value += Math.random() * 20;
      if (backtestProgress.value >= 100) {
        backtestProgress.value = 100;
        clearInterval(progressInterval);
        ElMessage.success("回测完成");
        loadBacktestRecords(); // 重新加载回测记录
      }
    }, 1000);
  } catch (error) {
    console.error("回测失败:", error);
    ElMessage.error("回测失败");
  } finally {
    isRunningBacktest.value = false;
  }
};

// 加载回测记录
const loadBacktestRecords = async () => {
  try {
    const response = await getBacktestRecordsAPI();
    backtestRecords.value = response.data || [];
  } catch (error) {
    console.error("加载回测记录失败:", error);
  }
};

// 查看回测详情
const viewBacktestDetail = (record: any) => {
  // 这里可以打开回测详情弹窗
  console.log("查看回测详情:", record);
};

// 删除回测记录
const deleteBacktestRecord = async (record: any) => {
  try {
    await ElMessageBox.confirm("确定要删除这条回测记录吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    await deleteBacktestRecordAPI(record.id);
    ElMessage.success("删除成功");
    loadBacktestRecords(); // 重新加载回测记录
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除回测记录失败:", error);
      ElMessage.error("删除失败");
    }
  }
};

// 加载持仓信息
const loadPositions = async () => {
  try {
    const response = await getPositionTreeAPI();
    positions.value = response.data || [];
  } catch (error) {
    console.error("加载持仓信息失败:", error);
  }
};

// 平仓
const closePosition = async (position: any) => {
  try {
    await ElMessageBox.confirm("确定要平仓吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    await closeOrderItemAPI(position.id);
    ElMessage.success("平仓成功");
    loadPositions(); // 重新加载持仓信息
  } catch (error) {
    if (error !== "cancel") {
      console.error("平仓失败:", error);
      ElMessage.error("平仓失败");
    }
  }
};

// 加载订单信息
const loadOrders = async () => {
  try {
    // 这里需要根据实际情况调用相应的API
    orders.value = []; // 暂时清空
  } catch (error) {
    console.error("加载订单信息失败:", error);
  }
};

// 取消订单
const cancelOrder = async (order: any) => {
  try {
    // 这里需要调用取消订单的API
    console.log("取消订单:", order);
  } catch (error) {
    console.error("取消订单失败:", error);
  }
};

// 初始化权益曲线图表
const initEquityChart = () => {
  if (equityChart) {
    equityChart.dispose();
  }

  const chartDom = document.getElementById("equity-chart");
  if (chartDom) {
    equityChart = echarts.init(chartDom);
    const option = {
      title: {
        text: "权益曲线",
      },
      tooltip: {
        trigger: "axis",
      },
      xAxis: {
        type: "category",
        data: [
          "2024-01-01",
          "2024-01-02",
          "2024-01-03",
          "2024-01-04",
          "2024-01-05",
        ],
      },
      yAxis: {
        type: "value",
      },
      series: [
        {
          name: "权益",
          type: "line",
          data: [10000, 10200, 10100, 10300, 10500],
          smooth: true,
        },
      ],
    };
    equityChart.setOption(option);
  }
};

// 获取订单状态文本
const getOrderStatusText = (status: string) => {
  const statusMap = {
    pending: "待执行",
    filled: "已成交",
    cancelled: "已取消",
    rejected: "已拒绝",
  };
  return statusMap[status] || status;
};

// 获取订单状态样式类
const getOrderStatusClass = (status: string) => {
  const classMap = {
    pending: "status-pending",
    filled: "status-filled",
    cancelled: "status-cancelled",
    rejected: "status-rejected",
  };
  return classMap[status] || "";
};

// 格式化日期
const formatDate = (timestamp: number) => {
  return new Date(timestamp).toLocaleString("zh-CN");
};

// 时间跳转
const handleTimeJump = async () => {
  if (!jumpTime.value) {
    ElMessage.warning("请选择跳转时间");
    return;
  }

  jumpTimeLoading.value = true;
  try {
    // 将时间字符串转换为时间戳
    const timestamp = new Date(jumpTime.value).getTime() / 1000;

    // 重新加载数据，设置startTime为跳转时间
    const response = await fetchKlineData({
      symbol: selectedSymbol.value,
      interval: mapTimeframeToEnum(currentTimeframe.value),
      limit: 100,
      startTime: timestamp * 1000, // 转换为毫秒
    });

    let klineData = [];
    if (response && response.data) {
      if (Array.isArray(response.data)) {
        klineData = response.data;
      } else if (response.data.data && Array.isArray(response.data.data)) {
        klineData = response.data.data;
      }
    }

    if (klineData.length > 0) {
      const formattedData = klineData.map((item: any) => ({
        time: Math.floor(item.timestamp / 1000),
        open: parseFloat(item.open || item.o),
        high: parseFloat(item.high || item.h),
        low: parseFloat(item.low || item.l),
        close: parseFloat(item.close || item.c),
        volume: parseFloat(item.volume || item.v || item.vol || 0),
      }));

      candlestickSeries.setData(formattedData);
      earliestDataTime.value = formattedData[0].time;
      latestDataTime.value = formattedData[formattedData.length - 1].time;

      chart.timeScale().fitContent();
      ElMessage.success("时间跳转成功");
    }
  } catch (error) {
    console.error("时间跳转失败:", error);
    ElMessage.error("时间跳转失败");
  } finally {
    jumpTimeLoading.value = false;
  }
};

// 加载机器人列表
const loadRobots = async () => {
  robotsLoading.value = true;
  try {
    const response = await getAllRobotList();
    robots.value = response || [];
  } catch (error) {
    console.error("加载机器人列表失败:", error);
    robots.value = [];
  } finally {
    robotsLoading.value = false;
  }
};

// 加载会员配置列表（暂时留空）
const loadMemberConfigs = async () => {
  if (!selectedRobot.value) return;

  memberConfigsLoading.value = true;
  try {
    // 这里应该调用API获取会员配置列表
    // memberConfigs.value = await getMemberConfigs(selectedRobot.value)
  } catch (error) {
    console.error("加载会员配置失败:", error);
  } finally {
    memberConfigsLoading.value = false;
  }
};

// 底部面板拖拽调整大小
const initBottomPanelDrag = () => {
  const divider = bottomPanelDivider.value;
  if (!divider) return;

  let isDragging = false;
  let startY = 0;
  let startHeight = 0;

  const onMouseDown = (e: MouseEvent) => {
    isDragging = true;
    startY = e.clientY;
    startHeight = bottomPanelHeight.value;
    document.addEventListener("mousemove", onMouseMove);
    document.addEventListener("mouseup", onMouseUp);
    document.body.style.cursor = "ns-resize";
    document.body.style.userSelect = "none";
  };

  const onMouseMove = (e: MouseEvent) => {
    if (!isDragging) return;

    const deltaY = startY - e.clientY;
    let newHeight = startHeight + deltaY;

    // 限制最小和最大高度
    newHeight = Math.max(100, Math.min(600, newHeight));
    bottomPanelHeight.value = newHeight;

    // 保存到本地存储
    localStorage.setItem("bottomPanelHeight", newHeight.toString());

    // 触发图表resize以适应新的高度
    nextTick(() => {
      resizeChart();
    });
  };

  const onMouseUp = () => {
    isDragging = false;
    document.removeEventListener("mousemove", onMouseMove);
    document.removeEventListener("mouseup", onMouseUp);
    document.body.style.cursor = "";
    document.body.style.userSelect = "";
  };

  divider.addEventListener("mousedown", onMouseDown);
};

// 监听底部tab变化
watch(bottomActiveTab, watchBottomActiveTab);

// 从本地存储加载底部面板高度
const savedHeight = localStorage.getItem("bottomPanelHeight");
if (savedHeight) {
  bottomPanelHeight.value = parseInt(savedHeight);
}

// 生命周期
onMounted(async () => {
  await nextTick();

  // 加载机器人列表
  await loadRobots();

  setTimeout(() => {
    initChart();
  }, 100);

  // 初始化底部面板拖拽功能
  setTimeout(() => {
    initBottomPanelDrag();
  }, 200);

  // 根据默认tab初始化相应内容
  // 如果默认tab是权益曲线，初始化图表
  if (bottomActiveTab.value === "trades") {
    setTimeout(() => {
      initEquityChart();
    }, 300);
  }
  // 如果默认tab是持仓信息，加载持仓数据
  else if (bottomActiveTab.value === "positions") {
    loadPositions();
  }
  // 如果默认tab是订单信息，加载订单详情
  else if (bottomActiveTab.value === "orderEntities") {
    loadOrders();
  }
  // 如果默认tab是回测，加载回测记录
  else if (bottomActiveTab.value === "backtest") {
    loadBacktestRecords();
  }

  // 监听窗口大小变化
  const handleResize = () => {
    resizeChart();
    if (equityChart) {
      equityChart.resize();
    }
  };

  window.addEventListener("resize", handleResize);

  onUnmounted(() => {
    window.removeEventListener("resize", handleResize);
    if (chart) {
      chart.remove();
    }
    if (equityChart) {
      equityChart.dispose();
    }
  });
});

// 暴露方法
defineExpose({
  initChart,
  changeTimeframe,
});
</script>

<style scoped>
.trading-chart {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

.chart-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e0e3eb;
  background: #ffffff;
}

.header-top {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 12px;
}

.header-toolbar {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.symbol-selector-wrapper,
.robot-selector-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.selector-label {
  font-size: 14px;
  color: #131722;
  font-weight: 500;
}

.price-display {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.price {
  font-size: 18px;
  font-weight: 600;
  color: #131722;
}

.change {
  font-size: 14px;
  font-weight: 500;
}

.change.positive {
  color: #26a69a;
}

.change.negative {
  color: #ef5350;
}

.change.neutral {
  color: #666666;
}

.toolbar-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-label {
  font-size: 14px;
  color: #131722;
  font-weight: 500;
}

.member-config-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 16px;
}

.chart-content {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.chart-container {
  width: 100%;
  height: 100%;
  position: relative;
}

.chart {
  width: 100%;
  height: 100%;
}

.chart-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--text-primary, #333333);
  z-index: 10;
}

.chart-loading-history {
  position: absolute;
  top: 20px;
  left: 20px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 8px 12px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  z-index: 10;
}

.chart-loading-latest {
  position: absolute;
  top: 20px;
  right: 20px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 8px 12px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  z-index: 10;
}

.loading-icon {
  font-size: 20px;
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

/* 底部面板样式 */
.bottom-panel-divider {
  height: 8px;
  background: #f5f5f5;
  border-top: 1px solid #e0e3eb;
  border-bottom: 1px solid #e0e3eb;
  cursor: ns-resize;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bottom-panel-divider:hover {
  background: #e8eaec;
}

.bottom-panel-divider:hover .divider-handle {
  background: #2962ff;
}

.divider-handle {
  width: 40px;
  height: 2px;
  background: #bbdefb;
  border-radius: 1px;
  transition: background-color 0.2s;
}

.bottom-panel {
  height: 300px;
  border-top: 1px solid #e0e3eb;
  background: #ffffff;
  display: flex;
  flex-direction: column;
}

.bottom-tabs {
  display: flex;
  border-bottom: 1px solid #dee2e6;
  background: #f8f9fa;
}

.bottom-tab-btn {
  flex: 1;
  padding: 12px 16px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: #6c757d;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.bottom-tab-btn:hover {
  background: #e9ecef;
  color: #495057;
}

.bottom-tab-btn.active {
  color: #2962ff;
  border-bottom-color: #2962ff;
  background: #ffffff;
}

.bottom-content {
  flex: 1;
  overflow: hidden;
}

.bottom-tab-panel {
  height: 100%;
  padding: 16px;
  overflow-y: auto;
}

/* 回测面板样式 */
.backtest-content {
  height: 100%;
}

.backtest-sub-tabs {
  display: flex;
  margin-bottom: 16px;
  border-bottom: 1px solid #dee2e6;
  padding-bottom: 8px;
}

.backtest-sub-tab-btn {
  padding: 8px 16px;
  margin-right: 8px;
  border: 1px solid #dee2e6;
  background: #ffffff;
  color: #6c757d;
  cursor: pointer;
  border-radius: 4px;
  font-size: 13px;
}

.backtest-sub-tab-btn:hover {
  background: #f8f9fa;
}

.backtest-sub-tab-btn.active {
  background: #2962ff;
  color: #ffffff;
  border-color: #2962ff;
}

.backtest-form {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: #495057;
  min-width: 60px;
}

.backtest-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.progress-bar {
  flex: 1;
  max-width: 300px;
}

.progress-text {
  font-size: 14px;
  color: #6c757d;
}

/* 表格样式 */
.records-table,
.positions-table,
.orders-table {
  width: 100%;
}

.table-header {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid #dee2e6;
  font-weight: 600;
  color: #495057;
  font-size: 14px;
}

.table-header span {
  flex: 1;
  text-align: center;
}

.table-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid #f1f3f4;
  font-size: 13px;
  color: #6c757d;
}

.table-row:hover {
  background: #f8f9fa;
}

.table-row span {
  flex: 1;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 状态样式 */
.profit-positive {
  color: #28a745;
}

.profit-negative {
  color: #dc3545;
}

.pnl-positive {
  color: #28a745;
}

.pnl-negative {
  color: #dc3545;
}

.side-long {
  color: #28a745;
}

.side-short {
  color: #dc3545;
}

.side-buy {
  color: #28a745;
}

.side-sell {
  color: #dc3545;
}

.status-pending {
  color: #ffc107;
}

.status-filled {
  color: #28a745;
}

.status-cancelled {
  color: #6c757d;
}

.status-rejected {
  color: #dc3545;
}

/* 权益曲线样式 */
.equity-content {
  height: 100%;
}

.equity-chart-container {
  width: 100%;
  height: 100%;
}

/* 空状态样式 */
.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
  color: #6c757d;
}

.empty-state p {
  font-size: 16px;
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .price-info {
    margin-left: 0;
    margin-top: 8px;
  }

  .bottom-tab-btn {
    padding: 8px 12px;
    font-size: 13px;
  }

  .table-header span,
  .table-row span {
    font-size: 12px;
    min-width: 60px;
  }
}
</style>
