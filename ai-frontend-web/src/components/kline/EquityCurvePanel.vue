<template>
  <div class="pnl-stats-content">
    <div
      ref="chartContainer"
      class="pnl-chart-container"
      style="width: 100%; height: 350px"
    ></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from "vue";
import * as echarts from "echarts";

interface Props {
  equityPoints: any[];
  loading?: boolean;
}

const props = defineProps<Props>();

const chartContainer = ref<HTMLDivElement | null>(null);
let pnlChart: echarts.ECharts | null = null;

const initPnlChart = () => {
  if (!chartContainer.value) return;

  // 如果图表已存在，先销毁
  if (pnlChart) {
    pnlChart.dispose();
    pnlChart = null;
  }

  // 创建新的图表实例
  pnlChart = echarts.init(chartContainer.value);

  // 设置初始配置
  const option: echarts.EChartsOption = {
    title: {
      text: "权益曲线",
      left: "center",
      textStyle: {
        fontSize: 16,
        fontWeight: 600,
        color: "#1e222d",
      },
    },
    tooltip: {
      trigger: "axis",
      axisPointer: {
        type: "cross",
      },
      formatter: (params: any) => {
        if (!params || params.length === 0) return "";
        const param = params[0];
        const date = new Date(param.value[0]);
        return `
          <div style="padding: 8px;">
            <div style="margin-bottom: 4px; font-weight: 600;">
              ${date.toLocaleString("zh-CN")}
            </div>
            <div>
              权益: <span style="color: #2962ff; font-weight: 600;">$${param.value[1].toFixed(2)}</span>
            </div>
          </div>
        `;
      },
    },
    grid: {
      left: "3%",
      right: "4%",
      bottom: "3%",
      containLabel: true,
    },
    xAxis: {
      type: "time",
      boundaryGap: false,
      axisLine: {
        lineStyle: {
          color: "#dee2e6",
        },
      },
      axisLabel: {
        color: "#666",
        formatter: (value: any) => {
          const date = new Date(value);
          return date.toLocaleDateString("zh-CN", {
            month: "2-digit",
            day: "2-digit",
          });
        },
      },
    },
    yAxis: {
      type: "value",
      axisLine: {
        lineStyle: {
          color: "#dee2e6",
        },
      },
      axisLabel: {
        color: "#666",
        formatter: (value: number) => {
          return `$${value.toFixed(2)}`;
        },
      },
      splitLine: {
        lineStyle: {
          color: "#f0f0f0",
        },
      },
    },
    series: [
      {
        name: "权益",
        type: "line",
        smooth: true,
        symbol: "none",
        lineStyle: {
          color: "#2962ff",
          width: 2,
        },
        areaStyle: {
          color: {
            type: "linear",
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              {
                offset: 0,
                color: "rgba(41, 98, 255, 0.3)",
              },
              {
                offset: 1,
                color: "rgba(41, 98, 255, 0.05)",
              },
            ],
          },
        },
        data: [],
      },
    ],
  };

  pnlChart.setOption(option);

  // 监听窗口大小变化
  window.addEventListener("resize", handleResize);
};

const renderPnlChart = () => {
  if (!pnlChart) return;

  // 转换数据格式
  const data = props.equityPoints.map((point: any) => {
    const timestamp = point.timestamp || point.time || point.createTime;
    const value = point.equity || point.value || point.balance || 0;

    // 处理时间戳（可能是秒或毫秒）
    let time: number;
    if (typeof timestamp === "string") {
      time = new Date(timestamp).getTime();
    } else if (timestamp > 1e12) {
      // 毫秒时间戳
      time = timestamp;
    } else {
      // 秒时间戳
      time = timestamp * 1000;
    }

    return [time, parseFloat(String(value))];
  });

  // 更新图表数据
  pnlChart.setOption({
    series: [
      {
        data,
      },
    ],
  });
};

const handleResize = () => {
  if (pnlChart) {
    pnlChart.resize();
  }
};

watch(
  () => props.equityPoints,
  () => {
    if (pnlChart) {
      renderPnlChart();
    }
  },
  { deep: true },
);

onMounted(async () => {
  await nextTick();
  initPnlChart();
  if (props.equityPoints && props.equityPoints.length > 0) {
    renderPnlChart();
  }
});

onBeforeUnmount(() => {
  if (pnlChart) {
    window.removeEventListener("resize", handleResize);
    pnlChart.dispose();
    pnlChart = null;
  }
});

defineExpose({
  initPnlChart,
  renderPnlChart,
});
</script>

<style scoped>
.pnl-stats-content {
  width: 100%;
  padding: 16px;
}

.pnl-chart-container {
  width: 100%;
  min-height: 350px;
}
</style>
