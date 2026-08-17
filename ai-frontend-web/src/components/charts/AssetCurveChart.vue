<template>
  <div class="dashboard-card">
    <div class="dashboard-header">
      <h3 class="card-title"><el-icon><TrendCharts /></el-icon> 资产曲线</h3>
      <div class="header-controls">
        <el-radio-group v-model="selectedPeriod" size="small">
          <el-radio-button label="1d">1日</el-radio-button>
          <el-radio-button label="1w">1周</el-radio-button>
          <el-radio-button label="1m">1月</el-radio-button>
          <el-radio-button label="3m">3月</el-radio-button>
          <el-radio-button label="1y">1年</el-radio-button>
        </el-radio-group>
        <el-button size="small" text @click="fetchData">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>
    <div class="chart-area">
      <div v-loading="loading" element-loading-text="加载中..." class="chart-wrapper">
        <div ref="chartRef" class="chart-container"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue";
import * as echarts from "echarts";
import { getTradingEquityCurve } from "@/api/trading";
import { TrendCharts, Refresh } from "@element-plus/icons-vue";

interface EquityPoint {
  date: string;
  totalEquity: number;
}

const chartRef = ref<HTMLElement>();
let chart: echarts.ECharts | null = null;
const loading = ref(false);
const chartData = ref<EquityPoint[]>([]);
const selectedPeriod = ref("1m");

function getDateRange(period: string): { startDate: string; endDate: string } {
  const end = new Date();
  const endDate = end.toISOString().slice(0, 10);
  const start = new Date();

  switch (period) {
    case "1d":
      start.setDate(end.getDate() - 1);
      break;
    case "1w":
      start.setDate(end.getDate() - 7);
      break;
    case "1m":
      start.setMonth(end.getMonth() - 1);
      break;
    case "3m":
      start.setMonth(end.getMonth() - 3);
      break;
    case "1y":
      start.setFullYear(end.getFullYear() - 1);
      break;
    default:
      start.setMonth(end.getMonth() - 1);
  }

  return { startDate: start.toISOString().slice(0, 10), endDate };
}

async function fetchData() {
  loading.value = true;
  try {
    const { startDate, endDate } = getDateRange(selectedPeriod.value);
    const res = await getTradingEquityCurve({ startDate, endDate });
    if (res?.success && Array.isArray(res.data)) {
      chartData.value = res.data.map((p: any) => ({
        date: p.date,
        totalEquity: Number(p.totalEquity),
      }));
    }
  } catch {
    chartData.value = [];
  } finally {
    loading.value = false;
    updateChart();
  }
}

const initChart = () => {
  if (!chartRef.value) return;
  chart = echarts.init(chartRef.value);
  fetchData();
};

const updateChart = () => {
  if (!chart) return;

  const data = chartData.value.map((p) => [new Date(p.date).getTime(), p.totalEquity]);

  const max = data.length > 0 ? Math.max(...data.map((d) => d[1])) : 0;
  const min = data.length > 0 ? Math.min(...data.map((d) => d[1])) : 0;

  const option = {
    backgroundColor: "transparent",
    grid: {
      left: "8%",
      right: "5%",
      top: "8%",
      bottom: "12%",
      containLabel: true,
    },
    xAxis: {
      type: "time",
      axisLine: { lineStyle: { color: "#404040", width: 1 } },
      axisLabel: { color: "#e0e0e0", fontSize: 11 },
      splitLine: {
        show: true,
        lineStyle: { color: "#2a2a2a", opacity: 0.6, type: "solid", width: 1 },
      },
    },
    yAxis: {
      type: "value",
      min: min > 0 ? min * 0.98 : undefined,
      max: max * 1.02,
      axisLine: { lineStyle: { color: "#404040", width: 1 } },
      axisLabel: {
        color: "#ffffff",
        fontSize: 12,
        fontWeight: 600,
        formatter: (value: number) => "$" + (value / 1000).toFixed(0) + "K",
      },
      splitLine: {
        show: true,
        lineStyle: { color: "#2a2a2a", opacity: 0.6, type: "solid", width: 1 },
      },
    },
    series: [
      {
        type: "line",
        data: data,
        smooth: true,
        showSymbol: false,
        emphasis: { scale: true, focus: "series" },
        markPoint: {
          data: [
            { type: "max", name: "最高值" },
            { type: "min", name: "最低值" },
          ],
          symbol: "circle",
          symbolSize: 8,
          itemStyle: {
            color: "#00d4aa",
            borderColor: "#ffffff",
            borderWidth: 2,
            shadowColor: "#00d4aa",
            shadowBlur: 8,
          },
          label: {
            color: "#ffffff",
            fontSize: 11,
            fontWeight: 600,
            backgroundColor: "rgba(0, 0, 0, 0.85)",
            padding: [4, 8],
            borderRadius: 4,
            borderColor: "#00d4aa",
            borderWidth: 1,
          },
        },
        lineStyle: {
          color: "#00d4aa",
          width: 3,
          shadowColor: "#00d4aa",
          shadowBlur: 10,
          shadowOffsetY: 2,
        },
        areaStyle: {
          color: {
            type: "linear",
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: "rgba(0, 212, 170, 0.7)" },
              { offset: 0.3, color: "rgba(0, 212, 170, 0.4)" },
              { offset: 0.7, color: "rgba(0, 212, 170, 0.15)" },
              { offset: 1, color: "rgba(0, 212, 170, 0.02)" },
            ],
          },
        },
      },
    ],
    tooltip: {
      trigger: "axis",
      backgroundColor: "rgba(0, 0, 0, 0.92)",
      borderColor: "#00d4aa",
      borderWidth: 1.5,
      textStyle: { color: "#ffffff", fontSize: 13, fontWeight: 500 },
      extraCssText:
        "box-shadow: 0 8px 24px rgba(0, 212, 170, 0.3); border-radius: 8px;",
      formatter: (params: any) => {
        const p = params[0];
        const value = p.data[1];
        const date = new Date(p.data[0]);
        const idx = p.dataIndex;

        let change = 0;
        let changePercent = 0;
        if (idx > 0 && idx < data.length) {
          const prev = data[idx - 1][1];
          change = value - prev;
          changePercent = prev !== 0 ? (change / prev) * 100 : 0;
        }

        const c = change >= 0 ? "#00d4aa" : "#ff3b30";
        const s = change >= 0 ? "+" : "";

        return `
          <div style="padding: 12px; min-width: 200px;">
            <div style="margin-bottom: 8px; font-weight: 600; font-size: 14px; color: #00d4aa; text-align: center;">
              ${date.toLocaleDateString("zh-CN", {
                year: "numeric", month: "short", day: "numeric",
              })}
            </div>
            <div style="margin-bottom: 6px; font-size: 16px; font-weight: 700; text-align: center;">
              总资产: <span style="color: #ffffff;">$${value.toLocaleString("zh-CN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}</span>
            </div>
            ${change !== 0 ? `
              <div style="font-size: 12px; text-align: center; padding-top: 6px; border-top: 1px solid #333;">
                变化: <span style="color: ${c}; font-weight: 700;">
                  ${s}$${Math.abs(change).toLocaleString("zh-CN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })} (${s}${changePercent.toFixed(2)}%)
                </span>
              </div>
            ` : ""}
          </div>
        `;
      },
    },
  };

  chart.setOption(option, true);
};

watch(selectedPeriod, () => {
  if (chart) fetchData();
});

onMounted(() => {
  initChart();
  window.addEventListener("resize", () => chart?.resize());
});

onBeforeUnmount(() => {
  if (chart) {
    chart.dispose();
    chart = null;
  }
});
</script>

<style scoped>
.dashboard-card {
  background: var(--surface-elevated);
  border: 1px solid var(--border-primary);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.dashboard-card:hover {
  border-color: var(--border-glow-primary);
  box-shadow: var(--glow-primary);
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-primary);
}

.card-title {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart-area {
  position: relative;
  min-height: 260px;
  overflow: hidden;
  flex: 1;
}

.chart-wrapper {
  width: 100%;
  height: 100%;
}

.chart-container {
  width: 100%;
  height: 260px;
  padding: 12px 16px 20px;
}
</style>
