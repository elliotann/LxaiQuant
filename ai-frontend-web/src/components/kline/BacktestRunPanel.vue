<template>
  <div class="backtest-sub-panel">
    <div class="backtest-header">
      <div class="backtest-controls">
        <div class="backtest-params">
          <el-form
            :model="backtestParams"
            label-position="top"
            class="backtest-form"
          >
            <el-row :gutter="12" class="backtest-form-row">
              <el-col :span="8">
                <el-form-item label="机器人">
                  <el-select
                    :model-value="backtestParams.botId"
                    @update:model-value="
                      updateBacktestParams('botId', $event || '')
                    "
                    placeholder="请选择机器人"
                    filterable
                    clearable
                  >
                    <el-option
                      v-for="robot in robots"
                      :key="robot.botId || robot.id"
                      :label="
                        robot.botName || robot.name || robot.botId || robot.id
                      "
                      :value="robot.botId || robot.id"
                    />
                  </el-select>
                </el-form-item>
              </el-col>

              <el-col :span="8">
                <el-form-item label="回测类型">
                  <el-select
                    :model-value="backtestParams.backtestType"
                    @update:model-value="
                      updateBacktestParams('backtestType', $event || '')
                    "
                    placeholder="请选择回测类型"
                    clearable
                  >
                    <el-option
                      v-for="type in backtestTypes"
                      :key="type.value"
                      :label="type.label || type.value"
                      :value="type.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>

              <el-col :span="8">
                <el-form-item label="交易对">
                  <el-select
                    :model-value="backtestParams.symbols[0] || ''"
                    @update:model-value="updateSymbolValue"
                    placeholder="请选择交易对"
                    filterable
                    clearable
                  >
                    <el-option
                      v-for="symbol in symbolOptions"
                      :key="symbol.value"
                      :label="symbol.label || symbol.value"
                      :value="symbol.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="12" class="backtest-form-row">
              <el-col :span="8">
                <el-form-item label="时间框架">
                  <el-select
                    :model-value="backtestParams.timeframe"
                    @update:model-value="
                      updateBacktestParams('timeframe', $event || '')
                    "
                    placeholder="请选择时间框架"
                    clearable
                  >
                    <el-option
                      v-for="timeframe in timeframes"
                      :key="timeframe.value"
                      :label="timeframe.label || timeframe.value"
                      :value="timeframe.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="手续费率 (%)">
                  <el-input-number
                    :model-value="backtestParams.commission"
                    @update:model-value="
                      updateBacktestParams('commission', $event ?? undefined)
                    "
                    :min="0"
                    :max="1"
                    :step="0.0001"
                    :disabled="backtestRunning"
                  />
                </el-form-item>
              </el-col>

              <el-col :span="8">
                <el-form-item label="滑点率">
                  <el-input-number
                    :model-value="backtestParams.slippage"
                    @update:model-value="
                      updateBacktestParams('slippage', $event ?? undefined)
                    "
                    :min="0"
                    :max="1"
                    :step="0.001"
                    :disabled="backtestRunning"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="12" class="backtest-form-row">
              <el-col :span="8">
                <el-form-item label="开始时间">
                  <el-date-picker
                    :model-value="backtestParams.startDate"
                    @update:model-value="
                      updateBacktestParams('startDate', $event || '')
                    "
                    type="datetime"
                    placeholder="选择开始时间"
                    format="YYYY-MM-DD HH:mm:ss"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    :disabled="backtestRunning"
                  />
                </el-form-item>
              </el-col>

              <el-col :span="8">
                <el-form-item label="结束时间">
                  <el-date-picker
                    :model-value="backtestParams.endDate"
                    @update:model-value="
                      updateBacktestParams('endDate', $event || '')
                    "
                    type="datetime"
                    placeholder="选择结束时间"
                    format="YYYY-MM-DD HH:mm:ss"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    :disabled="backtestRunning"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8" />
            </el-row>

            <el-row :gutter="12" class="backtest-form-row">
              <el-col :span="8">
                <el-form-item class="backtest-actions">
                  <el-button
                    type="primary"
                    :loading="backtestRunning"
                    :disabled="backtestRunning"
                    @click="$emit('run')"
                  >
                    {{ backtestRunning ? "⏳ 回测中..." : "▶️ 运行回测" }}
                  </el-button>
                  <el-button
                    type="danger"
                    :disabled="!backtestRunning"
                    @click="$emit('stop')"
                  >
                    ⏹️ 停止
                  </el-button>
                </el-form-item>
              </el-col>
              <el-col :span="8" />
              <el-col :span="8" />
            </el-row>
          </el-form>
        </div>
      </div>
    </div>

    <div v-if="backtestRunning" class="backtest-progress">
      <div class="progress-bar">
        <div
          class="progress-fill"
          :style="{ width: backtestProgress + '%' }"
        ></div>
      </div>
      <div class="progress-text">{{ backtestProgress.toFixed(1) }}% 完成</div>
      <div v-if="backtestMessage" class="progress-message">
        {{ backtestMessage }}
      </div>
    </div>

    <div class="backtest-logs">
      <div class="backtest-logs-header">
        <h4>执行日志</h4>
        <el-button size="small" type="danger" plain @click="$emit('clear-logs')"
          >清空日志</el-button
        >
      </div>
      <div class="backtest-logs-container">
        <div
          v-if="!backtestLogs || backtestLogs.length === 0"
          class="backtest-logs-empty"
        >
          暂无日志
        </div>
        <div
          v-else
          v-for="(log, index) in backtestLogs"
          :key="index"
          class="backtest-log-item"
        >
          <span class="backtest-log-time">{{
            formatLogTime(log.timestamp)
          }}</span>
          <span class="backtest-log-level" :class="log.level">{{
            log.level
          }}</span>
          <span class="backtest-log-message">{{ log.message }}</span>
        </div>
      </div>
    </div>

    <div v-if="backtestResults" class="backtest-results">
      <h4>绩效概览</h4>

      <!-- 净值走势图表 -->
      <div class="equity-chart-container">
        <h5>净值走势</h5>
        <div class="equity-chart-wrapper">
          <EquityCurvePanel
            v-if="equityCurvePoints && equityCurvePoints.length"
            :equityPoints="equityCurvePoints"
          />
          <div v-else class="simple-equity-chart">
            <div
              class="equity-line"
              :style="{ width: equityProgress + '%' }"
            ></div>
            <div class="equity-value">
              ${{ backtestResults.finalCapital?.toFixed(2) || "0.00" }}
            </div>
          </div>
          <div class="equity-stats">
            <div class="stat-item">
              <span class="stat-label">起始资金</span>
              <span class="stat-value"
                >${{
                  (
                    backtestResults.initialCapital ??
                    backtestResults.initialAmount ??
                    0
                  ).toFixed(2)
                }}</span
              >
            </div>
            <div class="stat-item">
              <span class="stat-label">最终资金</span>
              <span class="stat-value"
                >${{ backtestResults.finalCapital?.toFixed(2) || "0.00" }}</span
              >
            </div>
            <div class="stat-item">
              <span class="stat-label">收益</span>
              <span
                class="stat-value"
                :class="
                  backtestResults.finalCapital -
                    (backtestResults.initialCapital ??
                      backtestResults.initialAmount ??
                      0) >=
                  0
                    ? 'profit'
                    : 'loss'
                "
              >
                ${{
                  (
                    (backtestResults.finalCapital || 0) -
                    (backtestResults.initialCapital ??
                      backtestResults.initialAmount ??
                      0)
                  ).toFixed(2)
                }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 基础指标 -->
      <div class="metrics-section">
        <h5>基础指标</h5>
        <div class="results-grid">
          <div class="result-item">
            <span class="result-label">总收益率</span>
            <span
              class="result-value"
              :class="
                (backtestResults.totalReturn || 0) >= 0 ? 'profit' : 'loss'
              "
            >
              {{ ((backtestResults.totalReturn || 0) * 100).toFixed(2) }}%
            </span>
          </div>
          <div class="result-item">
            <span class="result-label">年化收益率</span>
            <span
              class="result-value"
              :class="
                (backtestResults.annualReturn || 0) >= 0 ? 'profit' : 'loss'
              "
            >
              {{ ((backtestResults.annualReturn || 0) * 100).toFixed(2) }}%
            </span>
          </div>
          <div class="result-item">
            <span class="result-label">波动率</span>
            <span class="result-value"
              >{{ ((backtestResults.volatility || 0) * 100).toFixed(2) }}%</span
            >
          </div>
          <div class="result-item">
            <span class="result-label">最终资金</span>
            <span class="result-value"
              >${{ backtestResults.finalCapital?.toFixed(2) || "0.00" }}</span
            >
          </div>
        </div>
      </div>

      <!-- 风险指标 -->
      <div class="metrics-section">
        <h5>风险指标</h5>
        <div class="results-grid">
          <div class="result-item">
            <span class="result-label">最大回撤</span>
            <span class="result-value loss"
              >{{
                ((backtestResults.maxDrawdown || 0) * 100).toFixed(2)
              }}%</span
            >
          </div>
          <div class="result-item">
            <span class="result-label">夏普比率</span>
            <span class="result-value">{{
              (backtestResults.sharpeRatio || 0).toFixed(2)
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">索提诺比率</span>
            <span class="result-value">{{
              (backtestResults.sortinoRatio || 0).toFixed(2)
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">卡玛比率</span>
            <span class="result-value">{{
              (backtestResults.calmarRatio || 0).toFixed(2)
            }}</span>
          </div>
        </div>
      </div>

      <!-- 交易指标 -->
      <div class="metrics-section">
        <h5>交易指标</h5>
        <div class="results-grid">
          <div class="result-item">
            <span class="result-label">总交易次数</span>
            <span class="result-value">{{
              backtestResults.totalTrades || 0
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">胜率</span>
            <span class="result-value"
              >{{ ((backtestResults.winRate || 0) * 100).toFixed(2) }}%</span
            >
          </div>
          <div class="result-item">
            <span class="result-label">盈利因子</span>
            <span class="result-value">{{
              (backtestResults.profitFactor || 0).toFixed(2)
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">平均盈利</span>
            <span class="result-value profit"
              >${{ (backtestResults.averageWin || 0).toFixed(2) }}</span
            >
          </div>
          <div class="result-item">
            <span class="result-label">平均亏损</span>
            <span class="result-value loss"
              >${{
                Math.abs(backtestResults.averageLoss || 0).toFixed(2)
              }}</span
            >
          </div>
          <div class="result-item">
            <span class="result-label">最大连续盈利</span>
            <span class="result-value">{{
              backtestResults.maxConsecutiveWins || 0
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">最大连续亏损</span>
            <span class="result-value">{{
              backtestResults.maxConsecutiveLosses || 0
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">平均持仓时间</span>
            <span class="result-value"
              >{{ (backtestResults.avgTradeDuration || 0).toFixed(1) }}天</span
            >
          </div>
        </div>
      </div>

      <!-- 测试概览信息 -->
      <div class="metrics-section">
        <h5>测试概览</h5>
        <div class="results-grid">
          <div class="result-item">
            <span class="result-label">回测ID</span>
            <span class="result-value">{{
              backtestResults.backtestId || "-"
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">交易品种</span>
            <span class="result-value">{{
              backtestResults.symbol || "-"
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">开始时间</span>
            <span class="result-value">{{
              formatDate(backtestResults.startDate)
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">结束时间</span>
            <span class="result-value">{{
              formatDate(backtestResults.endDate)
            }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">执行时间</span>
            <span class="result-value"
              >{{ backtestResults.executionTime || 0 }}ms</span
            >
          </div>
          <div class="result-item">
            <span class="result-label">数据点数</span>
            <span class="result-value">{{
              backtestResults.dataPoints || 0
            }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="!backtestRunning" class="backtest-placeholder">
      <div class="placeholder-text">回测结果将在这里显示</div>
      <div class="placeholder-hint">设置参数并点击运行回测开始测试策略</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import EquityCurvePanel from "@/components/kline/EquityCurvePanel.vue";

interface Props {
  backtestTypes: any[];
  symbolOptions: any[];
  timeframes: any[];
  robots: any[];
  backtestParams: {
    botId: string;
    backtestType: string;
    symbols: string[];
    backtestDays: number;
    timeframe: string;
    startDate: string;
    endDate: string;
    commission: number;
    slippage: number;
  };
  backtestRunning: boolean;
  backtestProgress: number;
  backtestMessage: string;
  backtestResults: any;
  equityProgress: number;
  equityCurvePoints: any[];
  backtestLogs: Array<{ timestamp: number; level: string; message: string }>;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  "update:backtestParams": [params: any];
  run: [];
  stop: [];
  "clear-logs": [];
}>();

const updateBacktestParams = (key: string, value: any) => {
  emit("update:backtestParams", {
    ...props.backtestParams,
    [key]: value,
  });
};

const updateSymbolValue = (value: string | number | null) => {
  const nextValue = value ? String(value) : "";
  updateBacktestParams("symbols", nextValue ? [nextValue] : []);
};

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

const formatLogTime = (timestamp: number) => {
  if (!timestamp) return "-";
  return new Date(timestamp).toLocaleTimeString();
};
</script>

<style scoped>
.backtest-sub-panel {
  padding: 16px 0;
}

.backtest-header {
  margin-bottom: 16px;
}

.backtest-controls {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.backtest-params {
  flex: 1;
}

.backtest-form {
  width: 100%;
}

.backtest-form-row {
  margin-bottom: 12px;
}

.backtest-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.backtest-form :deep(.el-form-item__label) {
  padding-bottom: 2px;
  font-weight: 500;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.backtest-form :deep(.el-form-item__content),
.backtest-form :deep(.el-input),
.backtest-form :deep(.el-select),
.backtest-form :deep(.el-input-number),
.backtest-form :deep(.el-date-editor) {
  width: 100%;
}

.backtest-form :deep(.el-input-number .el-input__wrapper) {
  height: 34px;
}

.backtest-form :deep(.el-input-number .el-input-number__decrease),
.backtest-form :deep(.el-input-number .el-input-number__increase) {
  height: 32px;
  width: 30px;
}

.backtest-form :deep(.el-input-number .el-input__inner) {
  height: 32px;
}

.backtest-form :deep(.el-date-editor .el-input__wrapper) {
  height: 34px;
}

.backtest-progress {
  margin-bottom: 16px;
}

.progress-bar {
  width: 100%;
  height: 8px;
  background-color: var(--bg-tertiary);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}

.progress-fill {
  height: 100%;
  background-color: var(--accent-blue);
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.progress-message {
  font-size: 12px;
  color: var(--text-muted);
}

.backtest-results {
  padding: 16px;
  background-color: var(--bg-secondary);
  border-radius: 8px;
}

.backtest-logs {
  margin-bottom: 16px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: 8px;
  padding: 12px;
}

.backtest-logs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.backtest-logs-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.backtest-logs-container {
  max-height: 220px;
  overflow-y: auto;
  background: var(--bg-tertiary);
  border-radius: 6px;
  padding: 8px;
}

.backtest-logs-empty {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  padding: 12px 0;
}

.backtest-log-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-family: monospace;
  padding: 4px 0;
}

.backtest-log-time {
  min-width: 70px;
  color: var(--text-secondary);
}

.backtest-log-level {
  min-width: 60px;
  text-align: center;
  border-radius: 4px;
  padding: 0 6px;
  text-transform: uppercase;
  font-size: 11px;
}

.backtest-log-level.info {
  background: #d1ecf1;
  color: #0c5460;
}
.backtest-log-level.success {
  background: #d4edda;
  color: #155724;
}
.backtest-log-level.warning {
  background: #fff3cd;
  color: #856404;
}
.backtest-log-level.error {
  background: #f8d7da;
  color: #721c24;
}

.backtest-log-message {
  flex: 1;
  color: var(--text-primary);
  word-break: break-all;
}

.backtest-results h4 {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.backtest-results h5 {
  margin: 16px 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.equity-chart-container {
  margin-bottom: 24px;
}

.equity-chart-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: stretch;
}

.simple-equity-chart {
  flex: 1;
  height: 100px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.equity-line {
  height: 100%;
  background: linear-gradient(to right, var(--accent-blue), var(--accent-green));
  transition: width 0.3s ease;
}

.equity-value {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.equity-stats {
  display: flex;
  flex-direction: row;
  gap: 16px;
  flex-wrap: wrap;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background-color: var(--bg-secondary);
  border-radius: 4px;
  min-width: 150px;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-value.profit {
  color: var(--accent-green);
}

.stat-value.loss {
  color: var(--accent-red);
}

.metrics-section {
  margin-bottom: 24px;
}

.results-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.result-item {
  display: flex;
  flex-direction: column;
  padding: 12px;
  background-color: var(--bg-secondary);
  border-radius: 4px;
  border: 1px solid var(--border-primary);
}

.result-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.result-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.result-value.profit {
  color: var(--accent-green);
}

.result-value.loss {
  color: var(--accent-red);
}

.backtest-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.placeholder-text {
  font-size: 16px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.placeholder-hint {
  font-size: 14px;
  color: var(--text-muted);
}
</style>
