<template>
  <div class="backtest-sub-panel">
    <div class="backtest-records-content">
      <div class="backtest-records-header">
        <h4>📈 回测记录</h4>
        <div class="backtest-records-controls">
          <select
            :model-value="selectedStrategy"
            @update:model-value="$emit('update:selectedStrategy', $event)"
            @change="
              $emit(
                'update:selectedStrategy',
                ($event.target as HTMLSelectElement).value,
              )
            "
            class="strategy-filter"
          >
            <option value="">全部策略</option>
            <option
              v-for="strategy in strategies"
              :key="strategy.id || strategy.value"
              :value="strategy.id || strategy.value"
            >
              {{
                strategy.name || strategy.label || strategy.id || strategy.value
              }}
            </option>
          </select>
          <button
            @click="$emit('refresh')"
            :disabled="loading"
            class="refresh-btn"
          >
            {{ loading ? "加载中..." : "刷新" }}
          </button>
        </div>
      </div>

      <div v-if="loading" class="backtest-loading">
        <div class="loading-spinner-modern"></div>
        <span>加载回测记录中...</span>
      </div>

      <div v-else-if="records.length === 0" class="backtest-no-data">
        <div class="no-data-icon">📊</div>
        <div class="no-data-text">暂无回测记录</div>
        <div class="no-data-subtext">运行回测后，历史记录将显示在这里</div>
      </div>

      <div v-else class="backtest-records-list">
        <div class="records-table">
          <div class="table-header">
            <span class="col-report-name">报告名称</span>
            <span class="col-strategy">策略</span>
            <span class="col-date">测试日期</span>
            <span class="col-return">总收益率</span>
            <span class="col-drawdown">最大回撤</span>
            <span class="col-sharpe">夏普比率</span>
            <span class="col-actions">操作</span>
          </div>

          <div v-for="record in records" :key="record.id" class="table-row">
            <span class="col-report-name">{{
              record.reportName || record.name || "-"
            }}</span>
            <span class="col-strategy">{{
              record.strategyName || record.strategy || "-"
            }}</span>
            <span class="col-date">{{
              formatDate(record.createdAt || record.createTime)
            }}</span>
            <span
              class="col-return"
              :class="(record.totalReturn || 0) >= 0 ? 'positive' : 'negative'"
            >
              {{ ((record.totalReturn || 0) * 100).toFixed(2) }}%
            </span>
            <span class="col-drawdown negative">
              {{ ((record.maxDrawdown || 0) * 100).toFixed(2) }}%
            </span>
            <span
              class="col-sharpe"
              :class="(record.sharpeRatio || 0) >= 0 ? 'positive' : 'negative'"
            >
              {{ (record.sharpeRatio || 0).toFixed(2) }}
            </span>
            <span class="col-actions">
              <button
                @click="$emit('view', record)"
                class="action-btn view-btn"
              >
                查看
              </button>
              <button
                @click="$emit('delete', record.id || record.backtestId)"
                class="action-btn delete-btn"
              >
                删除
              </button>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  strategies: any[];
  selectedStrategy: string;
  records: any[];
  loading: boolean;
}

defineProps<Props>();

defineEmits<{
  "update:selectedStrategy": [strategy: string];
  refresh: [];
  view: [record: any];
  delete: [recordId: string];
}>();

const pad2 = (value: number) => String(value).padStart(2, "0");

const toDateTimeString = (date: Date) => {
  const year = date.getFullYear();
  const month = pad2(date.getMonth() + 1);
  const day = pad2(date.getDate());
  const hour = pad2(date.getHours());
  const minute = pad2(date.getMinutes());
  const second = pad2(date.getSeconds());
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
};

const parseDateTime = (value: string | Date) => {
  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value;
  }
  if (!value) return null;
  const normalized = value.replace("T", " ").trim();
  const match = normalized.match(
    /^(\d{4})[/-](\d{1,2})[/-](\d{1,2})(?:\s+(\d{1,2})(?::(\d{1,2})(?::(\d{1,2}))?)?)?$/,
  );
  if (match) {
    const year = Number(match[1]);
    const month = Number(match[2]) - 1;
    const day = Number(match[3]);
    const hour = Number(match[4] ?? 0);
    const minute = Number(match[5] ?? 0);
    const second = Number(match[6] ?? 0);
    const date = new Date(year, month, day, hour, minute, second);
    return isNaN(date.getTime()) ? null : date;
  }
  const date = new Date(value);
  return isNaN(date.getTime()) ? null : date;
};

const formatDate = (date: string | Date) => {
  if (!date) return "-";
  const parsed = parseDateTime(date);
  if (!parsed) return String(date);
  return toDateTimeString(parsed);
};
</script>

<style scoped>
.backtest-sub-panel {
  padding: 16px 0;
}

.backtest-records-content {
  height: 100%;
}

.backtest-records-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.backtest-records-header h4 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.backtest-records-controls {
  display: flex;
  gap: 12px;
  align-items: center;
}

.strategy-filter {
  padding: 8px 12px;
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  font-size: 14px;
  min-width: 150px;
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}

.refresh-btn {
  padding: 8px 16px;
  border: 1px solid var(--border-primary);
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.refresh-btn:hover:not(:disabled) {
  background-color: var(--bg-tertiary);
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.backtest-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  gap: 16px;
}

.loading-spinner-modern {
  width: 40px;
  height: 40px;
  border: 4px solid var(--border-primary);
  border-top-color: var(--accent-blue);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.backtest-no-data {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.no-data-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.no-data-text {
  font-size: 16px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.no-data-subtext {
  font-size: 14px;
  color: var(--text-muted);
}

.backtest-records-list {
  overflow-y: auto;
}

.records-table {
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  overflow: hidden;
}

.table-header {
  display: grid;
  grid-template-columns: 2fr 1.5fr 1.5fr 1fr 1fr 1fr 1.5fr;
  gap: 12px;
  padding: 12px;
  background-color: var(--bg-tertiary);
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-primary);
}

.table-row {
  display: grid;
  grid-template-columns: 2fr 1.5fr 1.5fr 1fr 1fr 1fr 1.5fr;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid var(--border-primary);
  font-size: 14px;
  color: var(--text-primary);
  transition: background-color 0.2s;
}

.table-row:hover {
  background-color: var(--bg-tertiary);
}

.table-row:last-child {
  border-bottom: none;
}

.col-report-name,
.col-strategy,
.col-date,
.col-return,
.col-drawdown,
.col-sharpe,
.col-actions {
  display: flex;
  align-items: center;
}

.col-return.positive,
.col-sharpe.positive {
  color: var(--accent-green);
}

.col-return.negative,
.col-drawdown.negative,
.col-sharpe.negative {
  color: var(--accent-red);
}

.col-actions {
  gap: 8px;
}

.action-btn {
  padding: 4px 12px;
  border: 1px solid var(--border-primary);
  background-color: var(--bg-secondary);
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn.view-btn {
  color: var(--accent-blue);
}

.action-btn.view-btn:hover {
  background-color: var(--bg-tertiary);
  border-color: var(--accent-blue);
}

.action-btn.delete-btn {
  color: var(--accent-red);
}

.action-btn.delete-btn:hover {
  background-color: var(--bg-tertiary);
  border-color: var(--accent-red);
}
</style>
