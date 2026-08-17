<template>
  <div class="positions-content">
    <div class="positions-header">
      <span>持仓信息</span>
      <button @click="$emit('refresh')" :disabled="loading" class="refresh-btn">
        {{ loading ? "加载中..." : "刷新" }}
      </button>
    </div>

    <!-- 持仓列表内容 -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <span>加载持仓信息中...</span>
    </div>
    <div v-else-if="positions.length === 0" class="no-data">
      <span>暂无持仓</span>
    </div>
    <div v-else class="positions-list">
      <div
        v-for="p in positions"
        :key="p.posId || p.instId + '-' + p.posSide"
        class="position-item"
      >
        <div class="position-info">
          <span class="symbol">{{ p.instId || p.symbol }}</span>
          <span
            class="side"
            :class="
              (p.posSide || p.side) === 'long' ||
              (p.posSide || p.side) === 'LONG'
                ? 'long'
                : 'short'
            "
          >
            {{
              (p.posSide || p.side) === "long" ||
              (p.posSide || p.side) === "LONG"
                ? "多头"
                : "空头"
            }}
          </span>
          <span class="quantity">{{
            parseFloat(p.pos || p.sz || p.quantity || 0).toFixed(4)
          }}</span>
          <span
            class="pnl"
            :class="
              parseFloat(p.upl || p.unrealizedPnl || p.pnl || 0) >= 0
                ? 'profit'
                : 'loss'
            "
          >
            {{ formatPnL(p.upl || p.unrealizedPnl || p.pnl || 0, p.uplRatio) }}
          </span>
        </div>
        <div class="position-actions">
          <button
            @click="$emit('close-position', p)"
            class="action-btn close-btn"
            :disabled="!p.pos || parseFloat(p.pos) <= 0"
          >
            市场全平
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  positions: any[];
  loading: boolean;
}

defineProps<Props>();

defineEmits<{
  refresh: [];
  "close-position": [position: any];
}>();

const formatPnL = (pnl: number, ratio?: number) => {
  const pnlValue = parseFloat(String(pnl || 0));
  const ratioValue = ratio ? parseFloat(String(ratio)) : 0;

  if (pnlValue >= 0) {
    return `+$${pnlValue.toFixed(2)}${ratio ? ` (+${(ratioValue * 100).toFixed(2)}%)` : ""}`;
  } else {
    return `-$${Math.abs(pnlValue).toFixed(2)}${ratio ? ` (${(ratioValue * 100).toFixed(2)}%)` : ""}`;
  }
};
</script>

<style scoped>
.positions-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.positions-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 600;
  color: #1e222d;
}

.refresh-btn {
  padding: 6px 12px;
  border: 1px solid #dee2e6;
  background-color: #ffffff;
  color: #1e222d;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.refresh-btn:hover:not(:disabled) {
  background-color: #f8f9fa;
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  gap: 16px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #e0e0e0;
  border-top-color: #2962ff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.no-data {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #999;
  font-size: 14px;
}

.positions-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.position-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background-color: #f8f9fa;
  border-radius: 4px;
  border: 1px solid #dee2e6;
}

.position-info {
  display: flex;
  gap: 16px;
  align-items: center;
  flex: 1;
}

.symbol {
  font-weight: 600;
  color: #1e222d;
  min-width: 100px;
}

.side {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.side.long {
  background-color: #e8f5e9;
  color: #00c853;
}

.side.short {
  background-color: #ffebee;
  color: #f44336;
}

.quantity {
  color: #666;
  min-width: 80px;
}

.pnl {
  font-weight: 600;
  min-width: 120px;
}

.pnl.profit {
  color: #00c853;
}

.pnl.loss {
  color: #f44336;
}

.position-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 6px 12px;
  border: 1px solid #dee2e6;
  background-color: #ffffff;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn.close-btn {
  color: #f44336;
}

.action-btn.close-btn:hover:not(:disabled) {
  background-color: #ffebee;
  border-color: #f44336;
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
