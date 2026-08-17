<template>
  <div class="bottom-panel" :style="{ height: height + 'px' }">
    <div class="bottom-tabs">
      <button
        class="bottom-tab-btn"
        :class="{ active: activeTab === 'backtest' }"
        @click="$emit('update:activeTab', 'backtest')"
      >
        📊 回测
      </button>

      <button
        class="bottom-tab-btn"
        :class="{ active: activeTab === 'positions' }"
        @click="$emit('update:activeTab', 'positions')"
      >
        📊 持仓信息
      </button>

      <button
        class="bottom-tab-btn"
        :class="{ active: activeTab === 'orderEntities' }"
        @click="$emit('update:activeTab', 'orderEntities')"
      >
        📋 历史仓位
      </button>

      <button
        class="bottom-tab-btn"
        :class="{ active: activeTab === 'trades' }"
        @click="$emit('update:activeTab', 'trades')"
      >
        📊 权益曲线
      </button>
    </div>

    <div class="bottom-content">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  height: number;
  activeTab: string;
  backtestSubTab?: string;
  rightPanelCollapsed?: boolean;
}

defineProps<Props>();

defineEmits<{
  "update:activeTab": [tab: string];
  "update:backtestSubTab": [subTab: string];
  "toggle-right-panel": [];
}>();
</script>

<style scoped>
.bottom-panel {
  display: flex;
  flex-direction: column;
  background-color: #ffffff;
  border-top: 1px solid #dee2e6;
  overflow: hidden;
}

.bottom-tabs {
  display: flex;
  border-bottom: 1px solid #dee2e6;
}

.bottom-tab-btn {
  flex: 1;
  padding: 12px 16px;
  border: none;
  background-color: #ffffff;
  color: #787b86;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  letter-spacing: -0.01em;
}

.bottom-tab-btn.active {
  background-color: #ffffff;
  color: #1e222d;
  border-bottom: 2px solid #2962ff;
  font-weight: 700;
}

.bottom-content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
</style>
