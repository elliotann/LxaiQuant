<template>
  <div class="kline-chart-container">
    <!-- 工具栏 -->
    <div class="chart-toolbar" v-if="showToolbar">
      <el-space :size="20" wrap>
        <el-button-group>
          <el-button
            v-for="period in timePeriods"
            :key="period.value"
            :type="activePeriod === period.value ? 'primary' : 'default'"
            @click="changeTimePeriod(period.value)"
            size="small"
          >
            {{ period.label }}
          </el-button>
        </el-button-group>

        <el-select
          v-model="selectedIndicator"
          placeholder="添加技术指标"
          style="width: 180px"
          size="small"
          @change="handleAddIndicator"
        >
          <el-option
            v-for="indicator in indicatorOptions"
            :key="indicator.value"
            :label="indicator.label"
            :value="indicator.value"
          />
        </el-select>

        <el-button
          type="danger"
          plain
          size="small"
          @click="handleClearIndicators"
          :disabled="activeIndicators.length === 0"
        >
          清除指标
        </el-button>

        <el-switch
          v-model="showVolume"
          active-text="成交量"
          inactive-text="隐藏成交量"
          size="small"
        />

        <!-- 时间跳转 -->
        <el-date-picker
          v-model="jumpDateTime"
          type="datetime"
          placeholder="选择跳转时间"
          :disabled-date="disabledFutureDate"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          size="small"
          style="width: 200px"
        />
        <el-button
          type="primary"
          size="small"
          :loading="isJumping"
          @click="handleTimeJump"
          :disabled="!jumpDateTime"
        >
          <el-icon><Position /></el-icon>
          跳转
        </el-button>
        <el-button
          type="info"
          size="small"
          @click="jumpToCurrentTime"
          :disabled="isJumping"
        >
          返回最新
        </el-button>
      </el-space>

      <div class="chart-info">
        <span v-if="lastPrice" class="price-display">
          最新价:
          <span :class="priceChangeClass">{{ lastPrice.toFixed(2) }}</span>
          <span :class="priceChangeClass">
            ({{ priceChange >= 0 ? "+" : "" }}{{ priceChange.toFixed(2) }})
          </span>
        </span>
        <el-tag
          :type="chartMode === 'realtime' ? 'success' : 'info'"
          size="small"
          style="margin-left: 10px"
        >
          {{ chartMode === "realtime" ? "实时模式" : "历史模式" }}
        </el-tag>
      </div>
    </div>

    <!-- 图表容器 -->
    <div class="chart-wrapper">
      <div
        ref="chartContainer"
        class="chart-container"
        :style="{
          width: containerWidth + 'px',
          height: containerHeight + 'px',
        }"
      ></div>

      <!-- 加载状态 -->
      <div v-if="loading" class="chart-loading">
        <el-icon class="is-loading">
          <Loading />
        </el-icon>
        <span>加载数据中...</span>
      </div>

      <!-- 错误状态 -->
      <div v-if="error" class="chart-error">
        <el-alert :title="error" type="error" show-icon :closable="false" />
        <el-button @click="handleRetry">重试</el-button>
      </div>
    </div>

    <!-- 指标面板 -->
    <div
      class="indicator-panel"
      v-if="activeIndicators.length > 0 && showToolbar"
    >
      <div class="panel-title">当前指标</div>
      <div class="indicator-list">
        <div
          v-for="indicator in activeIndicators"
          :key="indicator.id"
          class="indicator-item"
        >
          <span>{{ indicator.name }}</span>
          <el-tag :type="getIndicatorTagType(indicator.type)" size="small">
            {{
              indicator.period
                ? `周期 ${indicator.period}`
                : indicator.type.toUpperCase()
            }}
          </el-tag>
          <el-button
            type="text"
            size="small"
            @click="removeIndicator(indicator.id)"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, computed, nextTick } from "vue";
import { createChart, ColorType } from "lightweight-charts";
import {
  ElButton,
  ElButtonGroup,
  ElSelect,
  ElOption,
  ElSwitch,
  ElSpace,
  ElAlert,
  ElTag,
  ElIcon,
  ElDatePicker,
  ElMessage,
} from "element-plus";
import { Loading, Close, Position } from "@element-plus/icons-vue";
import type {
  KLineData,
  ChartConfig,
  ChartTools,
  IndicatorConfig,
} from "./KLineChart.types";
import { jumpToTime } from "@/api/kline";

// Props
interface Props {
  data?: KLineData[];
  config?: ChartConfig;
  autoResize?: boolean;
  showToolbar?: boolean;
  width?: number;
  height?: number;
  symbol?: string;
  interval?: string;
}

const props = withDefaults(defineProps<Props>(), {
  data: () => [],
  config: () => ({}),
  autoResize: true,
  showToolbar: true,
  width: 1200,
  height: 600,
  symbol: "ETH-USDT-SWAP",
  interval: "OKXMIN3",
});

// Emits
const emit = defineEmits<{
  "chart-ready": [tools: ChartTools];
  "time-period-change": [period: string];
  "indicator-added": [indicator: IndicatorConfig];
  "indicator-removed": [id: string];
  "price-update": [price: number, change: number];
  "time-jump-start": [{ targetTime: number }];
  "time-jump": [
    data:
      | number
      | {
          targetTime: number;
          dataRange?: { startTime: number; endTime: number; dataCount: number };
          hasMoreBefore?: boolean;
          hasMoreAfter?: boolean;
        },
  ];
  "mode-change": [mode: "realtime" | "historical"];
}>();

// Refs
const chartContainer = ref<HTMLElement | null>(null);
const chartTools = ref<ChartTools | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const containerWidth = ref(props.width);
const containerHeight = ref(props.height);
const lastPrice = ref<number | null>(null);
const priceChange = ref(0);

// 时间周期选项
const timePeriods = [
  { label: "1分", value: "1min" },
  { label: "5分", value: "5min" },
  { label: "15分", value: "15min" },
  { label: "30分", value: "30min" },
  { label: "1小时", value: "1h" },
  { label: "4小时", value: "4h" },
  { label: "日线", value: "1d" },
  { label: "周线", value: "1w" },
];
const activePeriod = ref("1h");

// 指标选项
const indicatorOptions = [
  { label: "SMA (简单移动平均线)", value: "sma" },
  { label: "EMA (指数移动平均线)", value: "ema" },
  { label: "RSI (相对强弱指数)", value: "rsi" },
  { label: "MACD (异同移动平均线)", value: "macd" },
  { label: "BOLL (布林带)", value: "boll" },
];
const selectedIndicator = ref("");
const activeIndicators = ref<
  Array<{ id: string; name: string; type: string; period?: number }>
>([]);
const showVolume = ref(true);

// 时间跳转相关
const jumpDateTime = ref("");
const isJumping = ref(false);
const chartMode = ref<"realtime" | "historical">("realtime");

// 监听props变化，更新symbol和interval
watch(
  () => props.symbol,
  (newVal) => {
    if (newVal) {
      // symbol变化时的处理
    }
  },
);

watch(
  () => props.interval,
  (newVal) => {
    if (newVal) {
      // interval变化时的处理
    }
  },
);

// 价格变化样式
const priceChangeClass = computed(() => {
  if (priceChange.value > 0) return "price-up";
  if (priceChange.value < 0) return "price-down";
  return "price-neutral";
});

// 初始化图表
const initChart = () => {
  if (!chartContainer.value) return;

  try {
    // 设置容器尺寸
    if (props.autoResize) {
      const parentElement = chartContainer.value.parentElement;
      containerWidth.value = parentElement?.clientWidth || props.width;
      // 为时间轴和状态栏留出底部空间（约50px）
      const availableHeight = parentElement?.clientHeight || props.height;
      containerHeight.value = Math.max(availableHeight - 50, 400); // 至少400px高度
    } else {
      containerWidth.value = props.width;
      containerHeight.value = props.height;
    }

    // 创建图表实例
    const chart = createChart(chartContainer.value, {
      width: containerWidth.value,
      height: containerHeight.value,
      layout: {
        background: { type: ColorType.Solid, color: "#1a1d29" },
        textColor: "#d1d4dc",
        fontSize: 12,
        fontFamily: "Arial, sans-serif",
        padding: {
          top: 10,
          bottom: 40, // 为时间轴留出更多底部空间，确保完整显示
          left: 10,
          right: 10,
        },
        padding: {
          top: 10,
          bottom: 30, // 为时间轴留出底部空间
          left: 10,
          right: 10,
        },
      },
      grid: {
        vertLines: { color: "#2B2B43" },
        horzLines: { color: "#2B2B43" },
      },
      crosshair: {
        mode: 1, // Normal mode
        vertLine: {
          width: 1,
          color: "#758696",
          style: 3, // Dashed
        },
        horzLine: {
          width: 1,
          color: "#758696",
          style: 3, // Dashed
        },
      },
      priceScale: {
        borderColor: "#2B2B43",
        scaleMargins: {
          top: 0.1,
          bottom: 0.2,
        },
      },
      timeScale: {
        borderColor: "#2B2B43",
        timeVisible: true,
        secondsVisible: false,
        rightOffset: 10,
        barSpacing: 6,
      },
      watermark: {
        visible: true,
        fontSize: 48,
        horzAlign: "center",
        vertAlign: "center",
        color: "rgba(255, 255, 255, 0.05)",
        text: "K线图表",
      },
      ...props.config,
    });

    // 创建K线系列
    const candlestickSeries = chart.addCandlestickSeries({
      upColor: "#26a69a",
      downColor: "#ef5350",
      borderVisible: false,
      wickUpColor: "#26a69a",
      wickDownColor: "#ef5350",
      priceFormat: {
        type: "price",
        precision: 2,
        minMove: 0.01,
      },
    });

    // 创建成交量系列
    const volumeSeries = chart.addHistogramSeries({
      color: "#26a69a",
      priceFormat: {
        type: "volume",
      },
      priceScaleId: "volume",
      scaleMargins: {
        top: 0.8,
        bottom: 0,
      },
    });

    // 设置价格刻度
    chart.priceScale("volume").applyOptions({
      scaleMargins: {
        top: 0.8,
        bottom: 0,
      },
    });

    // 存储图表工具
    chartTools.value = {
      chart,
      candlestickSeries,
      volumeSeries,
      indicators: new Map(),
      resize: () => {
        if (chartContainer.value && props.autoResize) {
          containerWidth.value =
            chartContainer.value.parentElement?.clientWidth || props.width;
          containerHeight.value =
            chartContainer.value.parentElement?.clientHeight || props.height;
          chart.applyOptions({
            width: containerWidth.value,
            height: containerHeight.value,
          });
        }
      },
      update: (data: KLineData[]) => {
        candlestickSeries.setData(data);

        // 更新成交量数据
        if (showVolume.value) {
          const volumeData = data.map((item) => ({
            time: item.time,
            value: item.volume || 0,
            color: item.close >= item.open ? "#26a69a" : "#ef5350",
          }));
          volumeSeries.setData(volumeData);
        }

        // 更新最新价格
        if (data.length > 0) {
          const latest = data[data.length - 1];
          const previous = data.length > 1 ? data[data.length - 2] : latest;

          lastPrice.value = latest.close;
          priceChange.value = parseFloat(
            (latest.close - previous.close).toFixed(2),
          );

          emit("price-update", latest.close, priceChange.value);
        }

        // 更新所有指标
        updateIndicators(data);
      },
      addIndicator: (config: IndicatorConfig) => {
        const id = `indicator_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        addIndicator(config, id);
        return id;
      },
      removeIndicator: (id: string) => {
        removeIndicator(id);
      },
    };

    // 加载数据
    if (props.data && props.data.length > 0) {
      chartTools.value.update(props.data);
    }

    // 订阅十字光标移动事件
    chart.subscribeCrosshairMove((param) => {
      // 可以在这里添加十字光标移动时的处理逻辑
    });

    // 图表准备就绪
    emit("chart-ready", chartTools.value);
  } catch (err) {
    console.error("Error initializing chart:", err);
    error.value = "初始化图表失败";
  }
};

// 添加技术指标
const addIndicator = (config: IndicatorConfig, id: string) => {
  if (!chartTools.value) return;

  const { chart, candlestickSeries } = chartTools.value;

  try {
    let indicatorSeries;

    switch (config.type) {
      case "sma":
        indicatorSeries = chart.addLineSeries({
          color: config.color || "#FF6B6B",
          lineWidth: config.lineWidth || 2,
          title: `SMA(${config.period || 20})`,
        });

        // 计算SMA数据
        const smaData = calculateSMA(props.data, config.period || 20);
        indicatorSeries.setData(smaData);
        break;

      case "ema":
        indicatorSeries = chart.addLineSeries({
          color: config.color || "#4ECDC4",
          lineWidth: config.lineWidth || 2,
          title: `EMA(${config.period || 20})`,
        });

        // 计算EMA数据
        const emaData = calculateEMA(props.data, config.period || 20);
        indicatorSeries.setData(emaData);
        break;

      case "volume":
        // 成交量已经在主图表中显示
        showVolume.value = true;
        return;

      default:
        console.warn(`Unsupported indicator type: ${config.type}`);
        return;
    }

    // 存储指标
    chartTools.value.indicators.set(id, indicatorSeries);

    // 添加到活动指标列表
    const indicatorName =
      config.type.toUpperCase() + (config.period ? `(${config.period})` : "");
    activeIndicators.value.push({
      id,
      name: indicatorName,
      type: config.type,
      period: config.period,
    });

    emit("indicator-added", config);
  } catch (err) {
    console.error("Error adding indicator:", err);
  }
};

// 移除技术指标
const removeIndicator = (id: string) => {
  if (!chartTools.value) return;

  const indicator = chartTools.value.indicators.get(id);
  if (indicator) {
    chartTools.value.chart.removeSeries(indicator);
    chartTools.value.indicators.delete(id);

    // 从活动指标列表移除
    const index = activeIndicators.value.findIndex((item) => item.id === id);
    if (index !== -1) {
      activeIndicators.value.splice(index, 1);
    }

    emit("indicator-removed", id);
  }
};

// 计算SMA
const calculateSMA = (data: KLineData[], period: number) => {
  const result: { time: string | number; value: number }[] = [];

  for (let i = period - 1; i < data.length; i++) {
    let sum = 0;
    for (let j = 0; j < period; j++) {
      sum += data[i - j].close;
    }
    const sma = sum / period;

    result.push({
      time: data[i].time,
      value: parseFloat(sma.toFixed(2)),
    });
  }

  return result;
};

// 计算EMA
const calculateEMA = (data: KLineData[], period: number) => {
  const result: { time: string | number; value: number }[] = [];
  const multiplier = 2 / (period + 1);

  // 计算第一个SMA作为EMA的起点
  let ema = 0;
  for (let i = 0; i < period && i < data.length; i++) {
    ema += data[i].close;
  }
  ema = ema / Math.min(period, data.length);

  // 第一个EMA点
  if (data.length >= period) {
    result.push({
      time: data[period - 1].time,
      value: parseFloat(ema.toFixed(2)),
    });
  }

  // 计算后续的EMA
  for (let i = period; i < data.length; i++) {
    ema = (data[i].close - ema) * multiplier + ema;

    result.push({
      time: data[i].time,
      value: parseFloat(ema.toFixed(2)),
    });
  }

  return result;
};

// 更新所有指标
const updateIndicators = (data: KLineData[]) => {
  if (!chartTools.value) return;

  for (const [id, series] of chartTools.value.indicators.entries()) {
    const indicatorInfo = activeIndicators.value.find((item) => item.id === id);
    if (!indicatorInfo) continue;

    let indicatorData;

    switch (indicatorInfo.type) {
      case "sma":
        indicatorData = calculateSMA(data, indicatorInfo.period || 20);
        break;
      case "ema":
        indicatorData = calculateEMA(data, indicatorInfo.period || 20);
        break;
      default:
        continue;
    }

    if (indicatorData.length > 0) {
      series.setData(indicatorData);
    }
  }
};

// 切换时间周期
const changeTimePeriod = (period: string) => {
  activePeriod.value = period;
  emit("time-period-change", period);
};

// 添加指标处理
const handleAddIndicator = () => {
  if (!selectedIndicator.value) return;

  const config: IndicatorConfig = {
    type: selectedIndicator.value as any,
  };

  // 为某些指标设置默认周期
  if (["sma", "ema", "rsi"].includes(selectedIndicator.value)) {
    config.period = 20;
  }

  chartTools.value?.addIndicator(config);
  selectedIndicator.value = "";
};

// 清除所有指标
const handleClearIndicators = () => {
  if (!chartTools.value) return;

  for (const id of chartTools.value.indicators.keys()) {
    removeIndicator(id);
  }

  activeIndicators.value = [];
};

// 切换成交量显示
watch(showVolume, (show) => {
  if (!chartTools.value || !props.data.length) return;

  if (show) {
    const volumeData = props.data.map((item) => ({
      time: item.time,
      value: item.volume || 0,
      color: item.close >= item.open ? "#26a69a" : "#ef5350",
    }));
    chartTools.value.volumeSeries.setData(volumeData);
  } else {
    chartTools.value.volumeSeries.setData([]);
  }
});

// 重试
const handleRetry = () => {
  error.value = null;
  initChart();
};

// 禁止选择未来日期
const disabledFutureDate = (time: Date) => {
  return time.getTime() > Date.now();
};

// 处理时间跳转
const handleTimeJump = async () => {
  if (!jumpDateTime.value) {
    ElMessage.warning("请选择跳转时间");
    return;
  }

  try {
    isJumping.value = true;
    loading.value = true;
    error.value = null;

    // 转换为时间戳（秒）
    const targetTime = Math.floor(
      new Date(jumpDateTime.value).getTime() / 1000,
    );
    const currentTime = Math.floor(Date.now() / 1000);

    // 检查是否跳转到未来
    if (targetTime > currentTime) {
      ElMessage.warning("不能跳转到未来时间");
      isJumping.value = false;
      loading.value = false;
      return;
    }

    // 在调用API之前，先通知父组件设置跳转锁定，防止在更新图表数据时触发 checkPreloadNeeded
    emit("time-jump-start", { targetTime });

    // 调用跳转API
    console.log(
      `[KLineChart] 准备调用跳转API: targetTime=${targetTime} (${new Date(targetTime * 1000).toLocaleString("zh-CN")})`,
    );
    const response = await jumpToTime({
      symbol: props.symbol,
      interval: props.interval,
      time: targetTime,
      before: 100, // 跳转前加载100条
      after: 100, // 跳转后加载100条
      limit: 200,
    });

    console.log(
      `[KLineChart] 跳转API响应: code=${response?.code}, hasData=${!!response?.data}, klinesCount=${response?.data?.klines?.length || 0}`,
    );

    if (response && response.code === 200 && response.data) {
      const klines = response.data.klines || [];

      console.log(
        `[KLineChart] 跳转响应: targetTime=${targetTime} (${new Date(targetTime * 1000).toLocaleString("zh-CN")}), klines数量=${klines.length}`,
      );

      if (klines.length === 0) {
        ElMessage.warning("该时间点没有数据");
        isJumping.value = false;
        loading.value = false;
        return;
      }

      // 转换数据格式
      const chartData = klines.map((item) => ({
        time: item.time,
        open: item.open,
        high: item.high,
        low: item.low,
        close: item.close,
        volume: item.volume || 0,
      }));

      // 检查数据时间范围
      if (chartData.length > 0) {
        const times = chartData.map((item) => {
          const t =
            typeof item.time === "string" ? parseInt(item.time) : item.time;
          return t;
        });
        const minTime = Math.min(...times);
        const maxTime = Math.max(...times);
        console.log(
          `[KLineChart] 跳转返回的数据时间范围: [${minTime} (${new Date(minTime * 1000).toLocaleString("zh-CN")}), ${maxTime} (${new Date(maxTime * 1000).toLocaleString("zh-CN")})]`,
        );
        console.log(
          `[KLineChart] 跳转目标时间: ${targetTime} (${new Date(targetTime * 1000).toLocaleString("zh-CN")})`,
        );
      }

      // 更新图表数据
      if (chartTools.value) {
        chartTools.value.update(chartData);

        // 设置可见范围，以跳转时间点为中心
        if (chartTools.value.chart && chartData.length > 0) {
          const timeScale = chartTools.value.chart.timeScale();
          const firstTime =
            typeof chartData[0].time === "string"
              ? parseInt(chartData[0].time)
              : chartData[0].time;
          const lastTime =
            typeof chartData[chartData.length - 1].time === "string"
              ? parseInt(chartData[chartData.length - 1].time)
              : chartData[chartData.length - 1].time;
          const visibleDuration = lastTime - firstTime;
          const centerTime = targetTime;

          // 确保可见范围在数据范围内
          const visibleFrom = Math.max(
            firstTime,
            centerTime - visibleDuration / 2,
          );
          const visibleTo = Math.min(
            lastTime,
            centerTime + visibleDuration / 2,
          );

          timeScale.setVisibleRange({
            from: visibleFrom,
            to: visibleTo,
          });
        }
      }

      // 判断是否切换到历史模式
      const timeDiff = currentTime - targetTime;
      if (timeDiff > 300) {
        // 5分钟以上，切换到历史模式
        chartMode.value = "historical";
        ElMessage.success(
          `已跳转到 ${new Date(targetTime * 1000).toLocaleString("zh-CN")}，已切换到历史模式`,
        );
      } else {
        chartMode.value = "realtime";
        ElMessage.success(
          `已跳转到 ${new Date(targetTime * 1000).toLocaleString("zh-CN")}，保持实时模式`,
        );
      }

      // 更新数据边界（从跳转响应中获取）
      if (chartData.length > 0) {
        const times = chartData.map((item) => {
          const t =
            typeof item.time === "string" ? parseInt(item.time) : item.time;
          return t;
        });

        const startTime = Math.min(...times);
        const endTime = Math.max(...times);

        console.log(
          `[KLineChart] 计算数据边界: startTime=${startTime} (${new Date(startTime * 1000).toLocaleString("zh-CN")}), endTime=${endTime} (${new Date(endTime * 1000).toLocaleString("zh-CN")}), dataCount=${chartData.length}`,
        );

        // 通过事件传递数据边界信息
        emit("time-jump", {
          targetTime,
          dataRange: {
            startTime,
            endTime,
            dataCount: chartData.length,
          },
          hasMoreBefore: response.data.hasMoreBefore,
          hasMoreAfter: response.data.hasMoreAfter,
        });
      } else {
        emit("time-jump", targetTime);
      }
      emit("mode-change", chartMode.value);
    } else {
      throw new Error(response?.message || "跳转失败");
    }
  } catch (err: any) {
    error.value = "时间跳转失败: " + (err.message || "未知错误");
    console.error("时间跳转失败:", err);
    ElMessage.error(error.value);
  } finally {
    isJumping.value = false;
    loading.value = false;
  }
};

// 跳转到最新时间
const jumpToCurrentTime = async () => {
  const currentTime = Math.floor(Date.now() / 1000);
  jumpDateTime.value = new Date(currentTime * 1000)
    .toISOString()
    .slice(0, 19)
    .replace("T", " ");
  await handleTimeJump();
  chartMode.value = "realtime";
  ElMessage.success("已返回最新时间");
};

// 监听数据变化
watch(
  () => props.data,
  (newData) => {
    if (chartTools.value && newData.length > 0) {
      chartTools.value.update(newData);
    }
  },
  { deep: true },
);

// 监听窗口大小变化
const handleResize = () => {
  if (chartTools.value && props.autoResize) {
    chartTools.value.resize();
  }
};

// 生命周期
onMounted(() => {
  nextTick(() => {
    initChart();

    if (props.autoResize) {
      window.addEventListener("resize", handleResize);
    }
  });
});

onUnmounted(() => {
  if (chartTools.value) {
    chartTools.value.chart.remove();
  }

  if (props.autoResize) {
    window.removeEventListener("resize", handleResize);
  }
});

// 获取指标标签类型
const getIndicatorTagType = (type: string) => {
  const typeMap: Record<string, string> = {
    sma: "primary",
    ema: "success",
    rsi: "warning",
    macd: "info",
    boll: "danger",
  };
  return typeMap[type] || "info";
};

// 暴露方法给父组件
defineExpose({
  getChartTools: () => chartTools.value,
  addIndicator,
  removeIndicator,
  updateData: (data: KLineData[]) => {
    if (chartTools.value) {
      chartTools.value.update(data);
    }
  },
});
</script>

<style scoped>
.kline-chart-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  background: #1a1d29;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
}

.chart-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: #2b2b43;
  border-bottom: 1px solid #3a3a5a;
}

.chart-info {
  display: flex;
  align-items: center;
}

.price-display {
  font-size: 16px;
  font-weight: 600;
}

.price-up {
  color: #26a69a;
}

.price-down {
  color: #ef5350;
}

.price-neutral {
  color: #d1d4dc;
}

.chart-wrapper {
  position: relative;
  flex: 1;
  min-height: 400px;
  padding-bottom: 60px; /* 为底部时间轴和状态栏留出足够空间（时间轴约30px + 状态栏35px + 安全间距） */
  box-sizing: border-box;
  overflow: visible; /* 确保时间轴可以完整显示 */
}

.chart-container {
  width: 100%;
  height: 100%;
}

.chart-loading,
.chart-error {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: rgba(26, 29, 41, 0.9);
  z-index: 10;
}

.chart-loading .el-icon {
  font-size: 40px;
  margin-bottom: 10px;
  color: #409eff;
}

.chart-error {
  padding: 20px;
}

.chart-error .el-alert {
  margin-bottom: 20px;
  max-width: 400px;
}

.indicator-panel {
  padding: 12px 20px;
  background: #2b2b43;
  border-top: 1px solid #3a3a5a;
}

.panel-title {
  font-size: 14px;
  color: #8a8a9e;
  margin-bottom: 8px;
}

.indicator-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.indicator-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #3a3a5a;
  border-radius: 4px;
  font-size: 13px;
}
</style>
