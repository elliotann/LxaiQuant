<template>
  <div v-if="inline" class="bot-performance-report-inline">
    <div class="report-toolbar">
      <el-form inline>
        <slot name="toolbar-prefix" />
        <el-form-item label="统计维度">
          <el-radio-group v-model="periodType" @change="loadReport">
            <el-radio-button value="day">按日</el-radio-button>
            <el-radio-button value="month">按月</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="quickSelectYear(1)"
            >最近1年</el-button
          >
          <el-button size="small" @click="quickSelectYear(2)"
            >最近2年</el-button
          >
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            :type="periodType === 'month' ? 'monthrange' : 'daterange'"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            :shortcuts="dateShortcuts"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadReport"
            >查询</el-button
          >
        </el-form-item>
      </el-form>
    </div>

    <div v-if="summary" class="summary-cards">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">订单数</div>
            <div class="summary-value">{{ summary.orderCount }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">总收益</div>
            <div
              class="summary-value"
              :class="(summary.totalIncome ?? 0) >= 0 ? 'profit' : 'loss'"
            >
              {{ formatMoney(summary.totalIncome) }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">总成本(手续费)</div>
            <div class="summary-value">
              {{ formatMoney(summary.totalCharge) }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">净利润</div>
            <div
              class="summary-value"
              :class="(summary.netProfit ?? 0) >= 0 ? 'profit' : 'loss'"
            >
              {{ formatMoney(summary.netProfit) }}
            </div>
            <div class="summary-sub">
              <div>成功率：{{ formatPercent(summarySuccessRate) }}</div>
              <div>总收益率：{{ formatPercent(summaryTotalReturn) }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <div v-show="tableData.length > 0" class="report-chart-wrap">
      <div ref="chartRef" class="report-chart"></div>
      <div class="report-subcharts">
        <div ref="equityChartRef" class="report-subchart"></div>
        <div ref="drawdownChartRef" class="report-subchart"></div>
      </div>
    </div>
    <div v-if="tableData.length === 0 && !loading" class="report-empty">
      暂无数据，请选择时间范围后查询
    </div>
  </div>

  <el-dialog
    v-else
    v-model="visible"
    :title="`订单收益报表 - ${bot?.botName || bot?.botId || '机器人'}`"
    width="1400px"
    destroy-on-close
    class="bot-performance-report-dialog"
    @close="handleClose"
  >
    <div class="report-toolbar">
      <el-form inline>
        <slot name="toolbar-prefix" />
        <el-form-item label="统计维度">
          <el-radio-group v-model="periodType" @change="loadReport">
            <el-radio-button value="day">按日</el-radio-button>
            <el-radio-button value="month">按月</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="quickSelectYear(1)"
            >最近1年</el-button
          >
          <el-button size="small" @click="quickSelectYear(2)"
            >最近2年</el-button
          >
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            :type="periodType === 'month' ? 'monthrange' : 'daterange'"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            :shortcuts="dateShortcuts"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadReport"
            >查询</el-button
          >
        </el-form-item>
      </el-form>
    </div>

    <div v-if="summary" class="summary-cards">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">订单数</div>
            <div class="summary-value">{{ summary.orderCount }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">总收益</div>
            <div
              class="summary-value"
              :class="(summary.totalIncome ?? 0) >= 0 ? 'profit' : 'loss'"
            >
              {{ formatMoney(summary.totalIncome) }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">总成本(手续费)</div>
            <div class="summary-value">
              {{ formatMoney(summary.totalCharge) }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="summary-card">
            <div class="summary-label">净利润</div>
            <div
              class="summary-value"
              :class="(summary.netProfit ?? 0) >= 0 ? 'profit' : 'loss'"
            >
              {{ formatMoney(summary.netProfit) }}
            </div>
            <div class="summary-sub">
              <div>成功率：{{ formatPercent(summarySuccessRate) }}</div>
              <div>总收益率：{{ formatPercent(summaryTotalReturn) }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <div v-show="tableData.length > 0" class="report-chart-wrap">
      <div ref="chartRef" class="report-chart"></div>
      <div class="report-subcharts">
        <div ref="equityChartRef" class="report-subchart"></div>
        <div ref="drawdownChartRef" class="report-subchart"></div>
      </div>
    </div>
    <div v-if="tableData.length === 0 && !loading" class="report-empty">
      暂无数据，请选择时间范围后查询
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { getRobotOrderReport } from "@/api/tradeOrder";

const props = defineProps<{
  modelValue: boolean;
  bot: {
    botId: string;
    botName?: string;
    allocatedCapital?: number;
    currentCapital?: number;
  } | null;
  inline?: boolean;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: boolean): void;
  (e: "jump-time", v: { periodKey: string; periodType: "day" | "month" }): void;
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
});
const inline = computed(() => props.inline === true);
const isOpen = computed(() => (inline.value ? true : props.modelValue));

const periodType = ref<"day" | "month">("day");
const dateRange = ref<[string, string] | null>(null);
const loading = ref(false);
const tableData = ref<
  {
    periodKey: string;
    orderCount: number;
    totalIncome: number;
    totalCharge: number;
    netProfit: number;
    takeProfitAmount?: number;
    stopLossAmount?: number;
  }[]
>([]);
const summary = ref<{
  orderCount: number;
  totalIncome: number;
  totalCharge: number;
  netProfit: number;
  profitOrderCount?: number;
} | null>(null);
/** 后端返回的权益曲线数据 */
const equityCurveData = ref<
  { date: string; equity: number; drawdown: number | null; actualDate?: string }[]
>([]);
const chartRef = ref<HTMLDivElement | null>(null);
const equityChartRef = ref<HTMLDivElement | null>(null);
const drawdownChartRef = ref<HTMLDivElement | null>(null);
let chartInstance: echarts.ECharts | null = null;
let equityChartInstance: echarts.ECharts | null = null;
let drawdownChartInstance: echarts.ECharts | null = null;

const dateShortcuts = [
  {
    text: "最近7天",
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - 6);
      return [start, end] as [Date, Date];
    },
  },
  {
    text: "最近30天",
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - 29);
      return [start, end] as [Date, Date];
    },
  },
  {
    text: "最近3个月",
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setMonth(start.getMonth() - 2);
      return [start, end] as [Date, Date];
    },
  },
  {
    text: "今年",
    value: () => {
      const end = new Date();
      const start = new Date(end.getFullYear(), 0, 1);
      return [start, end] as [Date, Date];
    },
  },
  {
    text: "最近1年",
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setFullYear(start.getFullYear() - 1);
      return [start, end] as [Date, Date];
    },
  },
  {
    text: "最近2年",
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setFullYear(start.getFullYear() - 2);
      return [start, end] as [Date, Date];
    },
  },
];

function formatMoney(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return "-";
  return v >= 0 ? `+${v.toFixed(2)}` : v.toFixed(2);
}

function formatPercent(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return "-";
  return `${v.toFixed(2)}%`;
}

function toNum(v: unknown): number {
  if (v == null) return 0;
  if (typeof v === "number" && !Number.isNaN(v)) return v;
  const n = Number(v);
  return Number.isNaN(n) ? 0 : n;
}

// 汇总成功率 = 盈利订单数 / 总订单数 * 100
const summarySuccessRate = computed<number | null>(() => {
  const s = summary.value;
  if (!s) return null;
  const totalCount = toNum(s.orderCount);
  const profitCount = toNum((s as any).profitOrderCount);
  if (totalCount <= 0) return 0;

  const rate = (profitCount / totalCount) * 100;
  if (!Number.isFinite(rate)) return 0;
  return rate;
});

const summaryTotalReturn = computed<number | null>(() => {
  const s = summary.value;
  if (!s) return null;
  const base = toNum(props.bot?.currentCapital);
  if (base <= 0) return 0;
  const rate = (toNum(s.netProfit) / base) * 100;
  if (!Number.isFinite(rate)) return 0;
  return rate;
});

function loadReport() {
  if (!props.bot?.botId) {
    ElMessage.warning("请选择机器人");
    return;
  }
  const [startStr, endStr] = dateRange.value || [];
  if (!startStr || !endStr) {
    ElMessage.warning("请选择时间范围");
    return;
  }

  loading.value = true;
  tableData.value = [];
  summary.value = null;
  equityCurveData.value = [];
  const startTime = new Date(startStr).getTime();
  const endTime = new Date(endStr).getTime() + 86400000 - 1;

  getRobotOrderReport({
    robotId: props.bot.botId,
    startTime,
    endTime,
    granularity: periodType.value,
  })
    .then((res: any) => {
      if (!res?.success || !res?.data) {
        tableData.value = [];
        summary.value = null;
        return;
      }
      const data = res.data;
      const items = data.items || [];
      tableData.value = items.map((r: any) => ({
        periodKey: r.periodKey,
        orderCount: toNum(r.orderCount),
        totalIncome: toNum(r.totalIncome),
        totalCharge: toNum(r.totalCharge),
        netProfit: toNum(r.netProfit),
        takeProfitAmount: toNum((r as any).takeProfitAmount),
        stopLossAmount: toNum((r as any).stopLossAmount),
      }));
      summary.value = {
        orderCount: toNum(data.orderCount),
        totalIncome: toNum(data.totalIncome),
        totalCharge: toNum(data.totalCharge),
        netProfit: toNum(data.netProfit),
        // 盈利订单数（后端新增字段，旧版本没有时自动视为 0）
        profitOrderCount: toNum((data as any).profitOrderCount),
      };
      // 保存后端返回的权益曲线数据
      const ec = (data as any).equityCurve;
      equityCurveData.value = Array.isArray(ec) ? ec.map((p: any) => ({
        date: p.date,
        equity: toNum(p.equity),
        drawdown: p.drawdown != null ? toNum(p.drawdown) : null,
        actualDate: p.actualDate,
      })) : [];
      nextTick(() => updateChart());
    })
    .catch((e) => {
      console.error(e);
      ElMessage.error("加载报表失败");
    })
    .finally(() => {
      loading.value = false;
    });
}

function handleClose() {
  tableData.value = [];
  summary.value = null;
  window.removeEventListener("resize", onChartResize);
  disposeCharts();
}

function disposeCharts() {
  if (chartInstance) {
    chartInstance.dispose();
    chartInstance = null;
  }
  if (equityChartInstance) {
    equityChartInstance.dispose();
    equityChartInstance = null;
  }
  if (drawdownChartInstance) {
    drawdownChartInstance.dispose();
    drawdownChartInstance = null;
  }
}

function updateChart() {
  if (!chartRef.value || !tableData.value.length) {
    disposeCharts();
    return;
  }
  disposeCharts();
  chartInstance = echarts.init(chartRef.value);
  const rows = [...tableData.value].reverse();
  const xData = rows.map((r) => r.periodKey);
  const netProfitData = rows.map((r) => r.netProfit);
  const orderCountData = rows.map((r) => r.orderCount);
  // 收益率% = 净利润/总成本*100（总成本为0时按总收益绝对值算，避免除零）
  const barColors = netProfitData.map((v) => (v >= 0 ? "#67c23a" : "#f56c6c"));

  // 根据数据量动态设置 dataZoom 初始范围
  // 如果数据点超过30个，默认只显示最近30个，否则显示全部
  const dataLength = xData.length;
  const maxDisplayCount = 30;
  let dataZoomStart = 0;
  let dataZoomEnd = 100;
  if (dataLength > maxDisplayCount) {
    // 计算显示最近30个数据点的百分比范围
    dataZoomStart = ((dataLength - maxDisplayCount) / dataLength) * 100;
    dataZoomEnd = 100;
  }

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (items: any) => {
        if (!items?.length) return "";
        const periodKey = items[0].axisValue;
        const row = rows.find((r) => r.periodKey === periodKey);
        let returnRate = "";
        if (row) {
          const base = toNum(props.bot?.currentCapital);
          if (base > 0) returnRate = ((row.netProfit / base) * 100).toFixed(2);
          else returnRate = "0";
        }
        // 格式化日期，添加星期信息
        const formattedDate = formatDateWithWeekday(periodKey);
        let s = formattedDate + "<br/>";
        items.forEach((it: any) => {
          s += `${it.marker} ${it.seriesName}: ${it.value}<br/>`;
        });
        if (returnRate !== "") s += `收益率: ${returnRate}%<br/>`;
        if (row) {
          const tp = toNum((row as any).takeProfitAmount);
          const sl = toNum((row as any).stopLossAmount);
          if (tp > 0) s += `盈利金额: ${formatMoney(tp)}<br/>`;
          if (sl > 0) s += `亏损金额: ${(-sl).toFixed(2)}<br/>`;
        }
        return s;
      },
    },
    legend: {
      data: ["净利润", "订单数"],
      top: 0,
    },
    grid: { left: 50, right: 50, top: 40, bottom: 100, containLabel: true },
    dataZoom: [
      {
        type: "slider",
        show: true,
        xAxisIndex: [0],
        start: dataZoomStart,
        end: dataZoomEnd,
        bottom: 20,
        height: 30,
        handleIcon:
          "M10.7,11.9v-1.3H9.3v1.3c-4.9,0.3-8.8,4.4-8.8,9.4c0,5,3.9,9.1,8.8,9.4v1.3h1.3v-1.3c4.9-0.3,8.8-4.4,8.8-9.4C19.5,16.3,15.6,12.2,10.7,11.9z M13.3,24.4H6.7V23.1h6.6V24.4z M13.3,19.6H6.7v-1.4h6.6V19.6z",
        handleSize: "80%",
        handleStyle: {
          color: "#fff",
          shadowBlur: 3,
          shadowColor: "rgba(0, 0, 0, 0.6)",
          shadowOffsetX: 2,
          shadowOffsetY: 2,
        },
        textStyle: {
          color: "#999",
        },
        borderColor: "#ccc",
      },
      {
        type: "inside",
        xAxisIndex: [0],
        start: dataZoomStart,
        end: dataZoomEnd,
      },
    ],
    xAxis: {
      type: "category",
      data: xData,
      axisLabel: { rotate: periodType.value === "day" ? 45 : 0 },
    },
    yAxis: [
      { type: "value", name: "净利润", axisLabel: { formatter: "{value}" } },
      {
        type: "value",
        name: "订单数",
        position: "right",
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: "净利润",
        type: "bar",
        data: netProfitData.map((v, i) => ({
          value: v,
          itemStyle: { color: barColors[i] },
        })),
        barMaxWidth: 36,
      },
      {
        name: "订单数",
        type: "line",
        yAxisIndex: 1,
        data: orderCountData,
        symbol: "circle",
        symbolSize: 6,
      },
    ],
  };
  chartInstance.setOption(option);
  chartInstance.off("click");
  chartInstance.on("click", (params: any) => {
    if (params?.seriesType !== "bar") return;
    const periodKey = String(params?.name || params?.axisValue || "");
    if (!periodKey) return;
    emit("jump-time", { periodKey, periodType: periodType.value });
  });
  window.removeEventListener("resize", onChartResize);
  window.addEventListener("resize", onChartResize);

  // 使用后端返回的权益曲线数据（含回撤），替代旧的反推逻辑
  const equityPoints: Array<[number, number]> = [];
  const drawdownPoints: Array<[number, number]> = [];
  if (equityCurveData.value.length > 0) {
    equityCurveData.value.forEach((p) => {
      const time = new Date(p.date + "T00:00:00").getTime();
      if (!Number.isFinite(time)) return;
      equityPoints.push([time, p.equity]);
      if (p.drawdown != null) {
        drawdownPoints.push([time, p.drawdown]);
      }
    });
  }
  if (equityChartRef.value) {
    equityChartInstance = echarts.init(equityChartRef.value);
    const equityOption: echarts.EChartsOption = {
      title: {
        text: "权益曲线",
        left: "center",
        textStyle: { fontSize: 14, fontWeight: 600 },
      },
      tooltip: {
        trigger: "axis",
        axisPointer: { type: "cross" },
        formatter: (params: any) => {
          if (!params || params.length === 0) return "";
          const p = params[0];
          const ts = p.value[0];
          // 查找对应数据点，获取 actualDate（月粒度用）
          const matched = equityCurveData.value.find((d) => {
            const t = new Date(d.date + "T00:00:00").getTime();
            return t === ts;
          });
          const label = matched?.actualDate
            ? `${matched.date} (${matched.actualDate})`
            : new Date(ts).toLocaleDateString("zh-CN");
          return `${label}<br/>权益: $${Number(p.value[1]).toFixed(2)}`;
        },
      },
      grid: { left: 50, right: 30, top: 40, bottom: 40, containLabel: true },
      xAxis: {
        type: "time",
        axisLabel: { color: "#666" },
        axisLine: { lineStyle: { color: "#dee2e6" } },
      },
      yAxis: {
        type: "value",
        axisLabel: {
          color: "#666",
          formatter: (v: number) => `$${Number(v).toFixed(2)}`,
        },
        splitLine: { show: false },
      },
      series: [
        {
          name: "权益",
          type: "line",
          smooth: true,
          symbol: "none",
          lineStyle: { color: "#2962ff", width: 2 },
          areaStyle: {
            color: {
              type: "linear",
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: "rgba(41, 98, 255, 0.3)" },
                { offset: 1, color: "rgba(41, 98, 255, 0.05)" },
              ],
            },
          },
          data: equityPoints,
        },
      ],
    };
    equityChartInstance.setOption(equityOption);
  }

  if (drawdownChartRef.value) {
    drawdownChartInstance = echarts.init(drawdownChartRef.value);
    const drawdownOption: echarts.EChartsOption = {
      title: {
        text: "回撤曲线",
        left: "center",
        textStyle: { fontSize: 14, fontWeight: 600 },
      },
      tooltip: {
        trigger: "axis",
        axisPointer: { type: "cross" },
        formatter: (params: any) => {
          if (!params || params.length === 0) return "";
          const p = params[0];
          const ts = p.value[0];
          // 查找对应数据点，获取 actualDate（月粒度用）
          const matched = equityCurveData.value.find((d) => {
            const t = new Date(d.date + "T00:00:00").getTime();
            return t === ts;
          });
          const label = matched?.actualDate
            ? `${matched.date} (${matched.actualDate})`
            : new Date(ts).toLocaleDateString("zh-CN");
          return `${label}<br/>回撤: ${Number(p.value[1]).toFixed(2)}%`;
        },
      },
      grid: { left: 50, right: 30, top: 40, bottom: 40, containLabel: true },
      xAxis: {
        type: "time",
        axisLabel: { color: "#666" },
        axisLine: { lineStyle: { color: "#dee2e6" } },
      },
      yAxis: {
        type: "value",
        axisLabel: { color: "#666", formatter: "{value}%" },
        splitLine: { show: false },
      },
      series: [
        {
          name: "回撤",
          type: "line",
          smooth: true,
          symbol: "none",
          lineStyle: { color: "#f56c6c", width: 2 },
          areaStyle: { color: "rgba(245, 108, 108, 0.15)" },
          data: drawdownPoints,
        },
      ],
    };
    drawdownChartInstance.setOption(drawdownOption);
  }
}

function onChartResize() {
  chartInstance?.resize();
  equityChartInstance?.resize();
  drawdownChartInstance?.resize();
}

onBeforeUnmount(() => {
  window.removeEventListener("resize", onChartResize);
  disposeCharts();
});

function toDateString(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function quickSelectYear(n: number) {
  const end = new Date();
  const start = new Date();
  start.setFullYear(start.getFullYear() - n);
  dateRange.value = [toDateString(start), toDateString(end)];
  loadReport();
}

// 格式化日期，添加星期信息（仅对日期格式 YYYY-MM-DD 添加星期，月份格式 YYYY-MM 不添加）
function formatDateWithWeekday(dateStr: string): string {
  if (!dateStr) return dateStr;

  // 判断是否为日期格式（YYYY-MM-DD），而不是月份格式（YYYY-MM）
  const datePattern = /^\d{4}-\d{2}-\d{2}$/;
  if (!datePattern.test(dateStr)) {
    // 不是日期格式，直接返回（可能是月份格式或其他格式）
    return dateStr;
  }

  try {
    const date = new Date(dateStr + "T00:00:00"); // 添加时间部分，避免时区问题
    if (isNaN(date.getTime())) return dateStr;

    const weekdays = [
      "星期日",
      "星期一",
      "星期二",
      "星期三",
      "星期四",
      "星期五",
      "星期六",
    ];
    const weekday = weekdays[date.getDay()];
    return `${dateStr} ${weekday}`;
  } catch (e) {
    return dateStr;
  }
}

watch(
  () => [isOpen.value, props.bot] as const,
  ([open, b]) => {
    if (open && b) {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - 29);
      dateRange.value = [toDateString(start), toDateString(end)];
      periodType.value = "day";
      loadReport();
    }
  },
  { immediate: true },
);
</script>

<style scoped>
.bot-performance-report-dialog .report-toolbar,
.bot-performance-report-inline .report-toolbar {
  margin-bottom: 16px;
}
.bot-performance-report-dialog .summary-cards,
.bot-performance-report-inline .summary-cards {
  margin-bottom: 16px;
}
.bot-performance-report-inline {
  width: 100%;
}
.summary-card {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 12px 16px;
  text-align: center;
}
.summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.summary-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.summary-value.profit {
  color: var(--el-color-success);
}
.summary-value.loss {
  color: var(--el-color-danger);
}
.summary-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.report-chart-wrap {
  margin-bottom: 16px;
}
.report-chart {
  width: 100%;
  height: 500px;
}
.report-subcharts {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}
.report-subchart {
  width: 100%;
  height: 320px;
}
.report-empty {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
</style>
