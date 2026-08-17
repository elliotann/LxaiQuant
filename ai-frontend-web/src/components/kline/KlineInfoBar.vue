<template>
  <div class="kline-info-bar" v-if="klineData">
    <div class="info-item">
      <span class="label">时间:</span>
      <span class="value">{{ formattedTime }}</span>
    </div>
    <div class="info-item">
      <span class="label">开盘:</span>
      <span class="value">{{ formatPrice(klineData.open) }}</span>
    </div>
    <div class="info-item">
      <span class="label">最高:</span>
      <span class="value up">{{ formatPrice(klineData.high) }}</span>
    </div>
    <div class="info-item">
      <span class="label">最低:</span>
      <span class="value down">{{ formatPrice(klineData.low) }}</span>
    </div>
    <div class="info-item">
      <span class="label">收盘:</span>
      <span class="value" :class="getClosePriceClass()">{{
        formatPrice(klineData.close)
      }}</span>
    </div>
    <div class="info-item">
      <span class="label">涨跌:</span>
      <span class="value" :class="getChangeClass()">{{
        calculateChange()
      }}</span>
    </div>
    <div class="info-item">
      <span class="label">涨跌幅:</span>
      <span class="value" :class="getChangePercentClass()">{{
        calculateChangePercent()
      }}</span>
    </div>
    <div class="info-item">
      <span class="label">振幅:</span>
      <span class="value">{{ calculateAmplitude() }}%</span>
    </div>
  </div>
  <div class="kline-info-bar empty" v-else>
    <span class="empty-text">暂无K线数据</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

interface KlineData {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume?: number;
}

interface Props {
  klineData?: KlineData | null;
  formattedTime?: string;
}

const props = withDefaults(defineProps<Props>(), {
  klineData: null,
  formattedTime: "-",
});

const formatPrice = (price: number | undefined): string => {
  if (price === undefined || price === null) return "-";
  return parseFloat(String(price)).toFixed(4);
};

const calculateChange = (): string => {
  if (!props.klineData || !props.klineData.open || !props.klineData.close) {
    return "-";
  }
  const change = props.klineData.close - props.klineData.open;
  return change >= 0 ? `+${change.toFixed(4)}` : change.toFixed(4);
};

const calculateChangePercent = (): string => {
  if (!props.klineData || !props.klineData.open || !props.klineData.close) {
    return "-";
  }
  if (props.klineData.open === 0) return "0.00%";
  const percent =
    ((props.klineData.close - props.klineData.open) / props.klineData.open) *
    100;
  return percent >= 0 ? `+${percent.toFixed(2)}%` : `${percent.toFixed(2)}%`;
};

const calculateAmplitude = (): string => {
  if (
    !props.klineData ||
    !props.klineData.open ||
    !props.klineData.high ||
    !props.klineData.low
  ) {
    return "0.00";
  }
  if (props.klineData.open === 0) return "0.00";
  const amplitude =
    ((props.klineData.high - props.klineData.low) / props.klineData.open) * 100;
  return amplitude.toFixed(2);
};

const getClosePriceClass = (): string => {
  if (!props.klineData || !props.klineData.open || !props.klineData.close) {
    return "";
  }
  return props.klineData.close >= props.klineData.open ? "up" : "down";
};

const getChangeClass = (): string => {
  if (!props.klineData || !props.klineData.open || !props.klineData.close) {
    return "";
  }
  return props.klineData.close >= props.klineData.open ? "up" : "down";
};

const getChangePercentClass = (): string => {
  if (!props.klineData || !props.klineData.open || !props.klineData.close) {
    return "";
  }
  return props.klineData.close >= props.klineData.open ? "up" : "down";
};
</script>

<style scoped>
.kline-info-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 8px 16px;
  background: #f8f9fa;
  border-bottom: 1px solid #e0e3eb;
  font-size: 13px;
  flex-wrap: wrap;
  min-height: 40px;
}

.kline-info-bar.empty {
  justify-content: center;
  color: #999;
}

.empty-text {
  font-size: 13px;
  color: #999;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.label {
  color: #666;
  font-weight: 500;
}

.value {
  color: #131722;
  font-weight: 600;
}

.value.up {
  color: #26a69a;
}

.value.down {
  color: #ef5350;
}

@media (max-width: 768px) {
  .kline-info-bar {
    gap: 16px;
    font-size: 12px;
  }
}
</style>
