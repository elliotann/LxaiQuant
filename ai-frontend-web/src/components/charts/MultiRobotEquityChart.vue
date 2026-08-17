<template>
  <div class="dashboard-card">
    <!-- 标题 -->
    <div class="dashboard-header">
      <div class="header-left">
        <span class="header-icon">📊</span>
        <h3>多机器人量化对比仪表盘</h3>
      </div>
      <div class="header-right">
        <el-button size="small" :icon="Refresh" text @click="refreshData">刷新数据</el-button>
      </div>
    </div>

    <!-- 指标卡片区 -->
    <div class="metrics-row">
      <div class="metric-card" v-for="m in metricCards" :key="m.label">
        <div class="metric-label">{{ m.label }}</div>
        <div class="metric-value" :class="m.colorClass">{{ m.value }}</div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="selectedRobots"
          multiple
          placeholder="选择机器人（最多3个）"
          :max="3"
          filterable
          collapse-tags
          collapse-tags-tooltip
          style="width: 260px"
          @change="onRobotChange"
        >
          <el-option
            v-for="bot in availableBots"
            :key="bot.botId"
            :label="bot.botName"
            :value="bot.botId"
          />
        </el-select>

        <el-radio-group v-model="alignType" size="small" @change="fetchChartData">
          <el-radio-button value="absolute">原始权益</el-radio-button>
          <el-radio-button value="normalized">归一化净值</el-radio-button>
        </el-radio-group>
      </div>

      <div class="toolbar-right">
        <el-radio-group v-model="quickRange" size="small" @change="onQuickRangeChange">
          <el-radio-button value="1w">1周</el-radio-button>
          <el-radio-button value="1m">1月</el-radio-button>
          <el-radio-button value="3m">3月</el-radio-button>
          <el-radio-button value="1y">1年</el-radio-button>
        </el-radio-group>

        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="small"
          style="width: 220px"
          @change="onDateRangeChange"
        />

        <el-button type="primary" size="small" :icon="Search" @click="fetchChartData">查询</el-button>
      </div>
    </div>

    <!-- 图表区 -->
    <div class="chart-area">
      <div ref="chartRef" class="chart-container" />
      <div v-if="!loading && chartData.length === 0" class="chart-empty">
        <el-empty description="请选择机器人进行对比" />
      </div>
    </div>

    <!-- 详细绩效表格 -->
    <div class="table-section">
      <div class="section-title">绩效对比详情</div>
      <el-table :data="performanceData" stripe size="small" style="width: 100%">
        <el-table-column prop="robotName" label="机器人" min-width="130" />
        <el-table-column prop="totalReturn" label="总收益" width="100" align="right">
          <template #default="{ row }">
            <span :class="row.totalReturn >= 0 ? 'text-up' : 'text-down'">{{ row.totalReturn }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="annualReturn" label="年化收益" width="110" align="right">
          <template #default="{ row }">
            <span :class="row.annualReturn >= 0 ? 'text-up' : 'text-down'">{{ row.annualReturn }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="maxDrawdown" label="最大回撤" width="110" align="right">
          <template #default="{ row }">
            <span class="text-down">{{ row.maxDrawdown }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sharpRatio" label="夏普比率" width="110" align="right">
          <template #default="{ row }">
            <span :class="row.sharpRatio >= 0 ? 'text-up' : 'text-down'">{{ row.sharpRatio }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="calmarRatio" label="卡玛比率" width="110" align="right">
          <template #default="{ row }">
            <span :class="row.calmarRatio >= 0 ? 'text-up' : 'text-down'">{{ row.calmarRatio }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 策略说明 -->
    <div class="strategy-notes">
      <div class="notes-header">
        <span class="notes-icon">💡</span>
        <span>策略说明</span>
      </div>
      <div class="notes-body">
        <div class="note-item" v-for="bot in selectedRobotDetails" :key="bot.botId">
          <span class="note-dot" :style="{ background: getRobotColor(bot.botId) }" />
          <span class="note-name">{{ bot.botName }}</span>
          <span class="note-desc">{{ bot.description || '暂无策略说明' }}</span>
        </div>
        <div v-if="selectedRobotDetails.length === 0" class="note-empty">请选择机器人以查看策略说明</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from "vue";
import * as echarts from "echarts";
import { Search, Refresh } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import { getRobotEquityCompare, getAllRobotList } from "@/api/robot";

const CHART_COLORS = ["#5470c6", "#91cc75", "#fac858", "#ee6666"];
const COLORS_UP = "#22c55e";
const COLORS_DOWN = "#ef4444";

interface RobotOption {
  botId: string;
  botName: string;
  description?: string;
}

interface CompareData {
  robotId: string;
  robotName: string;
  dates: string[];
  equities: number[];
  navs: number[];
}

interface PerformanceRow {
  robotName: string;
  totalReturn: string;
  annualReturn: string;
  maxDrawdown: string;
  sharpRatio: string;
  calmarRatio: string;
}

interface MetricCard {
  label: string;
  value: string;
  colorClass: string;
}

// 工具函数 - 计算日收益率数组
function calcDailyReturns(values: number[]): number[] {
  const returns: number[] = [];
  for (let i = 1; i < values.length; i++) {
    if (values[i - 1] !== 0) {
      returns.push((values[i] - values[i - 1]) / values[i - 1]);
    }
  }
  return returns;
}

// 工具函数 - 均值
function mean(arr: number[]): number {
  if (arr.length === 0) return 0;
  return arr.reduce((a, b) => a + b, 0) / arr.length;
}

// 工具函数 - 标准差
function std(arr: number[]): number {
  if (arr.length < 2) return 0;
  const m = mean(arr);
  const squaredDiffs = arr.map(v => (v - m) ** 2);
  return Math.sqrt(squaredDiffs.reduce((a, b) => a + b, 0) / (arr.length - 1));
}

// 工具函数 - 最大回撤
function calcMaxDrawdown(values: number[]): number {
  let peak = values[0];
  let maxDd = 0;
  for (const v of values) {
    if (v > peak) peak = v;
    const dd = (peak - v) / peak;
    if (dd > maxDd) maxDd = dd;
  }
  return maxDd;
}

const chartRef = ref<HTMLElement | null>(null);
let chartInstance: echarts.ECharts | null = null;

const loading = ref(false);
const availableBots = ref<RobotOption[]>([]);
const selectedRobots = ref<string[]>([]);
const alignType = ref("absolute");
const quickRange = ref("1m");
const dateRange = ref<[Date, Date]>([new Date(Date.now() - 30 * 86400000), new Date()]);

const chartData = ref<CompareData[]>([]);

// 计算绩效数据
const performanceData = computed<PerformanceRow[]>(() => {
  return chartData.value.map(d => {
    const series = d.equities && d.equities.length > 0 ? d.equities : d.navs || [];
    if (series.length < 2) {
      return {
        robotName: d.robotName,
        totalReturn: "--",
        annualReturn: "--",
        maxDrawdown: "--",
        sharpRatio: "--",
        calmarRatio: "--",
      };
    }

    const first = series[0];
    const last = series[series.length - 1];
    const totalReturn = first !== 0 ? ((last - first) / first) * 100 : 0;

    const days = series.length - 1;
    const annualReturn = days > 0 ? (Math.pow(last / first, 252 / days) - 1) * 100 : 0;

    const maxDd = calcMaxDrawdown(series) * 100;

    const dailyReturns = calcDailyReturns(series);
    const meanRet = mean(dailyReturns);
    const stdRet = std(dailyReturns);
    const sharpRatio = stdRet !== 0 ? (meanRet / stdRet) * Math.sqrt(252) : 0;

    const calmarRatio = maxDd !== 0 ? annualReturn / Math.abs(maxDd) : 0;

    return {
      robotName: d.robotName,
      totalReturn: totalReturn.toFixed(2) + "%",
      annualReturn: annualReturn.toFixed(2) + "%",
      maxDrawdown: (-Math.abs(maxDd)).toFixed(2) + "%",
      sharpRatio: sharpRatio.toFixed(2),
      calmarRatio: calmarRatio.toFixed(2),
    };
  });
});

// 指标卡片（展示第一个选中机器人的数据）
const metricCards = computed<MetricCard[]>(() => {
  const row = performanceData.value[0];
  if (!row || row.totalReturn === "--") {
    return [
      { label: "总收益", value: "--", colorClass: "" },
      { label: "年化收益", value: "--", colorClass: "" },
      { label: "最大回撤", value: "--", colorClass: "" },
      { label: "夏普比率", value: "--", colorClass: "" },
      { label: "卡玛比率", value: "--", colorClass: "" },
    ];
  }
  return [
    { label: "总收益", value: row.totalReturn, colorClass: row.totalReturn.startsWith("-") ? "metric-down" : "metric-up" },
    { label: "年化收益", value: row.annualReturn, colorClass: row.annualReturn.startsWith("-") ? "metric-down" : "metric-up" },
    { label: "最大回撤", value: row.maxDrawdown, colorClass: "metric-down" },
    { label: "夏普比率", value: row.sharpRatio, colorClass: "metric-up" },
    { label: "卡玛比率", value: row.calmarRatio, colorClass: "metric-up" },
  ];
});

// 已选机器人的详情（用于策略说明）
const selectedRobotDetails = computed(() => {
  return availableBots.value.filter(b => selectedRobots.value.includes(b.botId));
});

function getRobotColor(robotId: string): string {
  const idx = selectedRobots.value.indexOf(robotId);
  return idx >= 0 ? CHART_COLORS[idx % CHART_COLORS.length] : CHART_COLORS[0];
}

// 获取可用机器人列表
async function fetchAvailableBots() {
  try {
    const resp = await getAllRobotList();
    const result = resp && typeof resp === "object" && "data" in resp ? (resp as any).data : resp;
    if (Array.isArray(result)) {
      availableBots.value = result.map((b: any) => ({
        botId: b.botId || b.id,
        botName: b.botName || "未知机器人",
        description: b.description || b.strategyDesc || "",
      }));
      if (availableBots.value.length > 0 && selectedRobots.value.length === 0) {
        selectedRobots.value = [availableBots.value[0].botId];
      }
    }
  } catch (e) {
    console.error("获取机器人列表失败", e);
  }
}

// 快捷日期选择
function onQuickRangeChange(val: string) {
  const now = new Date();
  let start: Date;
  switch (val) {
    case "1w":
      start = new Date(now.getTime() - 7 * 86400000);
      break;
    case "1m":
      start = new Date(now.getTime() - 30 * 86400000);
      break;
    case "3m":
      start = new Date(now.getTime() - 90 * 86400000);
      break;
    case "1y":
      start = new Date(now.getTime() - 365 * 86400000);
      break;
    default:
      start = new Date(now.getTime() - 30 * 86400000);
  }
  dateRange.value = [start, now];
  fetchChartData();
}

function onDateRangeChange() {
  quickRange.value = "";
}

// 机器人切换
function onRobotChange() {
  if (selectedRobots.value.length > 0) {
    fetchChartData();
  }
}

// 获取图表数据
async function fetchChartData() {
  if (selectedRobots.value.length === 0) return;

  loading.value = true;
  try {
    const [startDate, endDate] = dateRange.value;
    const fmtStart = formatDate(startDate);
    const fmtEnd = formatDate(endDate);
    const resp = await getRobotEquityCompare({
      robotIds: selectedRobots.value,
      startDate: fmtStart,
      endDate: fmtEnd,
      alignType: alignType.value,
    });
    chartData.value = resp?.data || [];

    // 确保所有机器人都返回了数据
    const returnedIds = chartData.value.map(d => d.robotId);
    for (const id of selectedRobots.value) {
      if (!returnedIds.includes(id)) {
        chartData.value.push({
          robotId: id,
          robotName: availableBots.value.find(b => b.botId === id)?.botName || id,
          dates: [],
          equities: [],
          navs: [],
        });
      }
    }

    await nextTick();
    renderChart();
  } catch (e) {
    console.error("获取权益数据失败", e);
    ElMessage.error("获取权益数据失败");
  } finally {
    loading.value = false;
  }
}

// 渲染 ECharts
function renderChart() {
  if (!chartRef.value) return;

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);
  }

  const series: echarts.SeriesOption[] = chartData.value.map((d, i) => {
    const data = alignType.value === "absolute" ? d.equities || [] : d.navs || [];
    const color = CHART_COLORS[i % CHART_COLORS.length];
    return {
      name: d.robotName,
      type: "line",
      data,
      smooth: true,
      symbol: "none",
      lineStyle: { width: 2, color },
      itemStyle: { color },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: color + "33" },
          { offset: 1, color: color + "05" },
        ]),
      },
    };
  });

  const dates = chartData.value.length > 0 ? chartData.value[0].dates : [];

  chartInstance.setOption({
    tooltip: {
      trigger: "axis",
      backgroundColor: "rgba(15, 23, 42, 0.9)",
      borderColor: "rgba(255, 255, 255, 0.1)",
      borderWidth: 1,
      textStyle: { color: "#e2e8f0", fontSize: 12 },
      formatter(params: any) {
        let tip = `<div style="font-weight:600;margin-bottom:4px">${params[0].axisValue}</div>`;
        for (const p of params) {
          const color = p.color as string;
          const val = p.value != null ? Number(p.value).toFixed(2) : "--";
          tip += `<div style="display:flex;justify-content:space-between;gap:16px">
            <span><span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${color};margin-right:6px"></span>${p.seriesName}</span>
            <span style="font-weight:600">${val}</span>
          </div>`;
        }
        return tip;
      },
    },
    legend: {
      data: chartData.value.map(d => d.robotName),
      bottom: 0,
      textStyle: { color: "#94a3b8", fontSize: 12 },
    },
    grid: {
      left: 50,
      right: 20,
      top: 20,
      bottom: 36,
    },
    xAxis: {
      type: "category",
      data: dates,
      axisLine: { lineStyle: { color: "rgba(255,255,255,0.1)" } },
      axisLabel: {
        color: "#94a3b8",
        fontSize: 11,
        formatter: (v: string) => v.slice(5),
      },
      splitLine: { show: false },
    },
    yAxis: {
      type: "value",
      splitLine: { lineStyle: { color: "rgba(255,255,255,0.06)", type: "dashed" } },
      axisLabel: {
        color: "#94a3b8",
        fontSize: 11,
        formatter: (v: number) => {
          if (alignType.value === "normalized") return v.toFixed(2);
          return v >= 10000 ? (v / 10000).toFixed(1) + "w" : v.toFixed(1);
        },
      },
    },
    series,
  });

  chartInstance?.setOption(series.length > 0 ? {} : { series: [] });
}

// 刷新数据
async function refreshData() {
  await fetchAvailableBots();
  await fetchChartData();
  ElMessage.success("数据已刷新");
}

function formatDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

// 窗口自适应
function handleResize() {
  chartInstance?.resize();
}

onMounted(async () => {
  await fetchAvailableBots();
  await fetchChartData();
  window.addEventListener("resize", handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleResize);
  chartInstance?.dispose();
  chartInstance = null;
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

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-primary);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-left h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-icon {
  font-size: 18px;
}

/* 指标卡片区 */
.metrics-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-primary);
}

.metric-card {
  background: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: 6px;
  padding: 14px 16px;
  text-align: center;
  transition: border-color 0.2s;
}

.metric-card:hover {
  border-color: var(--border-glow-primary);
}

.metric-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.metric-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  font-variant-numeric: tabular-nums;
}

.metric-value.metric-up {
  color: var(--positive-color);
}

.metric-value.metric-down {
  color: var(--negative-color);
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-primary);
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* 图表区 */
.chart-area {
  position: relative;
  min-height: 320px;
  border-bottom: 1px solid var(--border-primary);
  overflow: hidden;
}

.chart-container {
  width: 100%;
  height: 320px;
  padding: 16px 20px 40px;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 表格区 */
.table-section {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-primary);
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 12px;
}

.text-up {
  color: var(--positive-color);
  font-weight: 600;
}

.text-down {
  color: var(--negative-color);
  font-weight: 600;
}

/* 策略说明 */
.strategy-notes {
  padding: 16px 20px;
}

.notes-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.notes-icon {
  font-size: 14px;
}

.notes-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.note-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}

.note-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.note-name {
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
}

.note-desc {
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-empty {
  font-size: 13px;
  color: var(--text-muted);
}

/* Element 样式覆盖 */
:deep(.el-select .el-select__tags-text) {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.el-radio-group) {
  background: transparent;
}

:deep(.el-radio-button__inner) {
  background: var(--surface-elevated);
  border-color: var(--border-primary);
  color: var(--text-secondary);
  font-size: 12px;
  padding: 4px 10px;
}

:deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--btn-primary);
  border-color: var(--btn-primary);
  color: white;
}

:deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: transparent;
  --el-table-border-color: var(--border-primary);
  --el-table-text-color: var(--text-primary);
  --el-table-header-text-color: var(--text-secondary);
}

:deep(.el-table th.el-table__cell) {
  background-color: transparent;
  font-size: 12px;
}

:deep(.el-table__row) {
  font-size: 13px;
}

:deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background-color: rgba(255, 255, 255, 0.02);
}

/* 响应式 */
@media (max-width: 900px) {
  .metrics-row {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 600px) {
  .metrics-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-left,
  .toolbar-right {
    flex-wrap: wrap;
  }
}
</style>
