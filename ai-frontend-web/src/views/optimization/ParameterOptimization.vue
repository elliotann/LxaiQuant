<template>
  <div class="parameter-optimization">
    <div class="page-header">
      <h2>参数优化</h2>
      <div class="header-actions">
        <el-button
          type="primary"
          @click="runOptimization"
          :loading="isOptimizing"
        >
          <el-icon><VideoPlay /></el-icon>
          {{ isOptimizing ? "优化中..." : "开始优化" }}
        </el-button>
        <el-button @click="saveConfig">
          <el-icon><DocumentAdd /></el-icon>
          保存配置
        </el-button>
      </div>
    </div>

    <div class="optimization-content">
      <el-form
        :model="optimizationForm"
        label-width="120px"
        class="optimization-form"
      >
        <!-- 机器人选择 -->
        <el-form-item label="选择机器人" required>
          <el-select
            v-model="optimizationForm.botId"
            placeholder="请选择机器人"
            style="width: 300px"
            clearable
            :loading="botsLoading"
            filterable
          >
            <el-option
              v-for="bot in bots"
              :key="bot.botId"
              :label="`${bot.botName} (${bot.botId})`"
              :value="bot.botId"
            />
          </el-select>
        </el-form-item>

        

        <!-- 优化算法 -->
        <el-form-item label="优化算法" required>
          <el-select
            v-model="optimizationForm.algorithm"
            placeholder="请选择优化算法"
            style="width: 200px"
          >
            <el-option label="网格搜索" value="grid" />
            <el-option label="遗传算法" value="genetic" />
            <el-option label="贝叶斯优化" value="bayesian" />
            <el-option label="粒子群优化" value="pso" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="目标模式与权重">
          <div class="objective-settings">
            <el-radio-group v-model="optimizationForm.objectiveMode">
              <el-radio value="single">单目标</el-radio>
              <el-radio value="weighted">加权组合</el-radio>
            </el-radio-group>
            <div v-if="optimizationForm.objectiveMode === 'single'" class="objective-single">
              <el-select v-model="optimizationForm.objectiveTarget" placeholder="选择单目标" style="width: 240px">
                <el-option label="总收益" value="TOTAL_RETURN" />
                <el-option label="胜率" value="WIN_RATE" />
                <el-option label="最大回撤(越小越好)" value="-MAX_DRAWDOWN" />
                <el-option label="收益/回撤比" value="RETURN_OVER_MAX_DRAWDOWN" />
              </el-select>
            </div>
            <div v-else class="objective-weighted">
              <div class="weights-row">
                <span>总收益</span>
                <el-input-number v-model="optimizationForm.objectiveWeights.total_return" :min="-10" :max="10" :step="0.1" />
              </div>
              <div class="weights-row">
                <span>胜率</span>
                <el-input-number v-model="optimizationForm.objectiveWeights.win_rate" :min="-10" :max="10" :step="0.1" />
              </div>
              <div class="weights-row">
                <span>最大回撤(负权)</span>
                <el-input-number v-model="optimizationForm.objectiveWeights.max_drawdown" :min="-10" :max="10" :step="0.1" />
              </div>
              <div class="weights-row">
                <span>收益/回撤比</span>
                <el-input-number v-model="optimizationForm.objectiveWeights.return_over_max_drawdown" :min="-10" :max="10" :step="0.1" />
              </div>
              <div class="weights-row">
                <span>盈亏比</span>
                <el-input-number v-model="optimizationForm.objectiveWeights.profit_loss_ratio" :min="-10" :max="10" :step="0.1" />
              </div>
              <div class="weights-row">
                <span>交易次数</span>
                <el-input-number v-model="optimizationForm.objectiveWeights.trades" :min="-10" :max="10" :step="0.1" />
              </div>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="参数范围">
          <div class="parameter-ranges" v-loading="parametersLoading">
            <!-- 当机器人策略为 SSL_CHANNEL 时，展示其信号服务参数定义 -->
            <div v-if="isSslChannelStrategy" class="ssl-channel-params">
              <div class="ssl-params-header">
                SSL_CHANNEL 参数（来自 SslChannelSignService）
              </div>
              <el-table
                :data="sslChannelParamDefs"
                style="width: 100%; margin-bottom: 16px"
                size="small"
              >
                <el-table-column prop="label" label="参数名" min-width="180" />
                <el-table-column prop="type" label="类型" width="120" />
                <el-table-column label="默认值" min-width="160">
                  <template #default="scope">
                    <span>{{ formatDefaultValue(scope.row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column v-if="hasSelectOptions(scope)" label="可选项" min-width="200">
                  <template #default="scope">
                    <span v-if="Array.isArray(scope.row.options) && scope.row.options.length > 0">
                      {{ scope.row.options.map((o:any)=>o.label).join(' / ') }}
                    </span>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div
              v-for="group in parameterGroups"
              :key="group.name"
              class="parameter-group"
            >
              <div v-if="group.name" class="parameter-group-title">
                {{ group.name }}
              </div>
              <div
                v-for="item in group.items"
                :key="item.index"
                class="parameter-item"
              >
                <el-input
                  v-model="item.param.name"
                  placeholder="参数名"
                  style="width: 180px; margin-right: 8px"
                />
                <el-input-number
                  v-model="item.param.min"
                  placeholder="最小值"
                  :min="-1000"
                  :max="1000"
                  style="width: 140px; margin-right: 8px"
                />
                <el-input-number
                  v-model="item.param.max"
                  placeholder="最大值"
                  :min="-1000"
                  :max="1000"
                  style="width: 140px; margin-right: 8px"
                />
                <el-input-number
                  v-model="item.param.step"
                  placeholder="步长"
                  :min="0.001"
                  :max="100"
                  :step="0.001"
                  style="width: 140px; margin-right: 8px"
                />
                <el-select
                  v-model="item.param.values"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  placeholder="离散枚举值，输入后回车"
                  style="width: 320px; margin-right: 8px"
                >
                  <el-option
                    v-for="v in item.param.values || []"
                    :key="v"
                    :label="v"
                    :value="v"
                  />
                </el-select>
                <el-button
                  @click="item.param.values = []"
                  type="warning"
                  circle
                >
                  清
                </el-button>
                <el-button
                  @click="removeParameter(item.index)"
                  type="danger"
                  circle
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
            <el-button @click="addParameter" type="primary" plain>
              <el-icon><Plus /></el-icon>
              添加参数
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="高级设置">
          <el-collapse v-model="advancedSettings">
            <el-collapse-item title="风险控制" name="risk">
              <div class="risk-settings">
                <div class="risk-item">
                  <label>最大仓位</label>
                  <el-input-number
                    v-model="optimizationForm.maxPosition"
                    :min="0"
                    :max="1"
                    :step="0.1"
                    :precision="2"
                    style="width: 120px"
                  />
                  <span class="unit-label">%</span>
                </div>
                <div class="risk-item">
                  <label>止损比例</label>
                  <el-input-number
                    v-model="optimizationForm.stopLoss"
                    :min="0"
                    :max="1"
                    :step="0.01"
                    :precision="2"
                    style="width: 120px"
                  />
                  <span class="unit-label">%</span>
                </div>
                <div class="risk-item">
                  <label>止盈比例</label>
                  <el-input-number
                    v-model="optimizationForm.takeProfit"
                    :min="0"
                    :max="1"
                    :step="0.01"
                    :precision="2"
                    style="width: 120px"
                  />
                  <span class="unit-label">%</span>
                </div>
              </div>
            </el-collapse-item>

            <el-collapse-item title="输出设置" name="output">
              <div class="output-settings">
                <el-checkbox v-model="optimizationForm.saveTrades"
                  >保存交易记录</el-checkbox
                >
                <el-checkbox v-model="optimizationForm.saveChartData"
                  >保存图表数据</el-checkbox
                >
                <el-checkbox v-model="optimizationForm.generateReport"
                  >生成详细报告</el-checkbox
                >
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-form-item>

        <!-- 回测时间范围 -->
        <el-form-item label="回测时间范围" required>
          <div class="date-range">
            <el-date-picker
              v-model="optimizationForm.startDate"
              type="datetime"
              placeholder="开始时间"
              style="width: 200px"
            />
            <span class="date-separator">至</span>
            <el-date-picker
              v-model="optimizationForm.endDate"
              type="datetime"
              placeholder="结束时间"
              style="width: 200px"
            />
          </div>
        </el-form-item>

        <!-- 初始资金 -->
        <el-form-item label="初始资金" required>
          <el-input-number
            v-model="optimizationForm.initialCapital"
            :min="1000"
            :max="10000000"
            :step="1000"
            style="width: 200px"
          />
        </el-form-item>

        <!-- 优化设置 -->
        <el-form-item label="优化设置">
          <el-checkbox-group v-model="optimizationForm.settings">
            <el-checkbox label="parallel">并行计算</el-checkbox>
            <el-checkbox label="earlyStopping">早停机制</el-checkbox>
            <el-checkbox label="crossValidation">交叉验证</el-checkbox>
            <el-checkbox label="saveResults">保存结果</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <!-- 进度显示 -->
        <el-form-item v-if="isOptimizing" label="优化进度">
          <el-progress
            :percentage="optimizationProgress"
            :status="progressStatus"
          />
          <div class="progress-info">
            <span>当前最优: {{ bestResult || "计算中..." }}</span>
            <span>剩余时间: {{ estimatedTime || "计算中..." }}</span>
          </div>
        </el-form-item>
      </el-form>
    </div>

    <!-- 优化结果 -->
    <div v-if="optimizationResults.length > 0" class="results-section">
      <h3>优化结果</h3>
      <el-table :data="optimizationResults" style="width: 100%">
        <el-table-column prop="rank" label="排名" width="80" />
        <el-table-column prop="parameters" label="参数" width="200" />
        <el-table-column prop="objective" label="目标值" width="120" />
        <el-table-column prop="sharpe" label="夏普比率" width="120" />
        <el-table-column prop="profit" label="总收益" width="120" />
        <el-table-column prop="winrate" label="胜率" width="120" />
        <el-table-column prop="maxDrawdown" label="最大回撤" width="120" />
        <el-table-column prop="trades" label="交易次数" width="120" />
        <el-table-column label="操作" width="150">
          <template #default="scope">
            <el-button
              @click="viewResult(scope.row)"
              type="primary"
              size="small"
            >
              查看详情
            </el-button>
            <el-button
              @click="useParameters(scope.row)"
              type="success"
              size="small"
            >
              使用参数
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed } from "vue";
import { ElMessage } from "element-plus";
import { VideoPlay, DocumentAdd, Delete, Plus } from "@element-plus/icons-vue";
import {
  getTradingBots,
  getTradingBotById,
  updateTradingBot,
} from "@/api/robot";
import {
  runParameterRangeOptimization,
  getOptimizationTaskProgress,
  getOptimizationTaskResults,
} from "@/api/backtest";
import { getStrategyById } from "@/api/strategy";
import {
  getSignalServiceDefinitions,
  type SignalServiceDefinition,
  type SignalServiceParamDefinition,
} from "@/api/priceSignal";

interface BotOption {
  botId: string;
  botName: string;
}

interface ParameterRange {
  name: string;
  min: number;
  max: number;
  step: number;
  values?: string[];
  group?: string;
}

interface OptimizationResult {
  rank: number;
  parameters: string;
  objective: number;
  sharpe: number;
  profit: number;
  winrate: number;
  maxDrawdown: number;
  trades: number;
  details?: any;
}

const bots = ref<BotOption[]>([]);
const botsLoading = ref(false);
const isOptimizing = ref(false);
const optimizationProgress = ref(0);
const progressStatus = ref("");
const bestResult = ref("");
const estimatedTime = ref("");
const optimizationResults = ref<OptimizationResult[]>([]);
const advancedSettings = ref(["risk"]);
const signalServiceDefinitions = ref<SignalServiceDefinition[]>([]);
const parametersLoading = ref(false);
const optimizationTaskId = ref("");
const optimizationSocket = ref<WebSocket | null>(null);
const currentStrategyId = ref<string>("");
const isSslChannelStrategy = computed(() => {
  const id = (currentStrategyId.value || "").toLowerCase();
  return id.includes("ssl_channel") || id.includes("ssl");
});

const optimizationForm = reactive({
  botId: "",
  algorithm: "grid",
  objectiveMode: "single",
  objectiveTarget: "TOTAL_RETURN",
  objectiveWeights: {
    total_return: 1,
    win_rate: 0,
    max_drawdown: 0,
    return_over_max_drawdown: 0,
    profit_loss_ratio: 0,
    trades: 0,
  },
  parameters: [
    { name: "fastPeriod", min: 5, max: 20, step: 1 },
    { name: "slowPeriod", min: 20, max: 50, step: 1 },
  ] as ParameterRange[],
  maxPosition: 0.8,
  stopLoss: 0.05,
  takeProfit: 0.1,
  saveTrades: true,
  saveChartData: true,
  generateReport: true,
  startDate: "",
  endDate: "",
  initialCapital: 100000,
  settings: ["parallel", "saveResults"],
});

const addParameter = () => {
  optimizationForm.parameters.push({
    name: "",
    min: 0,
    max: 100,
    step: 1,
    group: "自定义",
  });
};

const removeParameter = (index: number) => {
  optimizationForm.parameters.splice(index, 1);
};

const formatParameters = (parameters: Record<string, string>) => {
  return Object.entries(parameters)
    .map(([key, value]) => `${key}:${value}`)
    .join(", ");
};

const resolveObjectiveTarget = (target: string) => {
  const t = (target || "").toUpperCase();
  if (t === "-MAX_DRAWDOWN") {
    return "MAX_DRAWDOWN";
  }
  if (t === "WIN_RATE") {
    return "WIN_RATE";
  }
  if (t === "RETURN_OVER_MAX_DRAWDOWN") {
    return "RETURN_OVER_MAX_DRAWDOWN";
  }
  return "TOTAL_RETURN";
};

const buildOptimizationResults = (items: any[]) => {
  return items.map((item, index) => {
    const metrics = item?.performanceMetrics || {};
    const initialAmount =
      metrics.initialAmount ?? optimizationForm.initialCapital;
    const totalReturn = metrics.totalReturn ?? 0;
    const profit = totalReturn * initialAmount;
    const winRate = metrics.winRate ?? 0;
    const maxDrawdown = metrics.maxDrawdown ?? 0;
    return {
      rank: index + 1,
      parameters: formatParameters(item?.parameters || {}),
      objective: item?.score ?? 0,
      sharpe: metrics.sharpeRatio ?? 0,
      profit: Math.round(profit),
      winrate: Number((winRate * 100).toFixed(2)),
      maxDrawdown: Number((maxDrawdown * -100).toFixed(2)),
      trades: metrics.totalTrades ?? 0,
      details: item,
    };
  });
};

const closeOptimizationSocket = () => {
  if (optimizationSocket.value) {
    optimizationSocket.value.close();
    optimizationSocket.value = null;
  }
};

const syncOptimizationResults = async (taskId: string) => {
  try {
    const taskResponse = await getBacktestTaskDetail(taskId);
    if (!taskResponse.success || !taskResponse.data) {
      ElMessage.error(taskResponse.message || "获取优化结果失败");
      return;
    }
    const config = taskResponse.data.config || {};
    const optimizationResult = config.optimizationResult || {};
    const results = optimizationResult.results || [];
    optimizationResults.value = buildOptimizationResults(results);
    optimizationProgress.value = 100;
    progressStatus.value = "success";
    estimatedTime.value = "完成";
    if (optimizationResults.value.length > 0) {
      bestResult.value = optimizationResults.value[0].objective.toFixed(4);
    }
    ElMessage.success("参数优化完成！");
  } catch (error: any) {
    ElMessage.error(error.message || "获取优化结果失败");
    progressStatus.value = "exception";
  }
};

const runOptimization = async () => {
  if (!optimizationForm.botId) {
    ElMessage.error("请选择机器人");
    return;
  }

  isOptimizing.value = true;
  optimizationProgress.value = 0;
  progressStatus.value = "";
  bestResult.value = "0.00";
  estimatedTime.value = "执行中...";
  optimizationResults.value = [];
  closeOptimizationSocket();

  try {
    const botResponse = await getTradingBotById(optimizationForm.botId);
    if (!botResponse.success || !botResponse.data) {
      ElMessage.error(botResponse.message || "获取机器人信息失败");
      return;
    }

    const bot = botResponse.data;
    const strategyId = bot.strategyId;
    const coinId = bot.tradingPair;
    if (!strategyId) {
      ElMessage.error("机器人未配置策略");
      return;
    }
    if (!coinId) {
      ElMessage.error("机器人未配置交易对");
      return;
    }

    const ranges = optimizationForm.parameters
      .filter((param) => param.name && param.name.trim().length > 0)
      .map((param) => ({
        name: param.name,
        min: param.min,
        max: param.max,
        step: param.step,
        values: Array.isArray(param.values) ? param.values : undefined,
      }));

    const startTime = optimizationForm.startDate
      ? new Date(optimizationForm.startDate).getTime()
      : undefined;
    const endTime = optimizationForm.endDate
      ? new Date(optimizationForm.endDate).getTime()
      : undefined;

    const response = await runParameterRangeOptimization({
      strategyId,
      coinId,
      startTime,
      endTime,
      initialAmount: optimizationForm.initialCapital,
      objective:
        optimizationForm.objectiveMode === "weighted"
          ? {
              type: "weighted",
              weights: optimizationForm.objectiveWeights,
            }
          : {
              type: "single",
              target: resolveObjectiveTarget(optimizationForm.objectiveTarget),
            },
      ranges,
    });

    if (!response.success) {
      ElMessage.error(
        response.message || response.errorMessage || "参数优化失败",
      );
      progressStatus.value = "exception";
      return;
    }

    const taskId = response.taskId || response.data?.taskId;
    if (!taskId) {
      ElMessage.error("未获取到优化任务ID");
      progressStatus.value = "exception";
      return;
    }
    optimizationTaskId.value = taskId;
    optimizationProgress.value = 5;

    const poll = async () => {
      try {
        const prog = await getOptimizationTaskProgress(taskId);
        if (prog?.success && prog?.data) {
          const p = prog.data.progress ?? 0;
          optimizationProgress.value = p;
          const status = prog.data.status || "RUNNING";
          if (status === "COMPLETED") {
            const res = await getOptimizationTaskResults(taskId, 50);
            const list: any[] = (res?.data?.results) || [];
            optimizationResults.value = buildOptimizationResults(list.map((row:any) => ({
              parameters: JSON.parse(row.paramValues || "{}"),
              score: row.score,
              performanceMetrics: {
                totalReturn: row.totalReturn,
                maxDrawdown: row.maxDrawdown,
                winRate: row.winRate,
                sharpeRatio: row.sharpeRatio,
              },
              details: row,
            })));
            optimizationProgress.value = 100;
            progressStatus.value = "success";
            estimatedTime.value = "完成";
            isOptimizing.value = false;
            if (optimizationResults.value.length > 0) {
              bestResult.value = optimizationResults.value[0].objective.toFixed(4);
            }
          } else if (status === "FAILED") {
            progressStatus.value = "exception";
            estimatedTime.value = "失败";
            isOptimizing.value = false;
          } else {
            setTimeout(poll, 1000);
          }
        } else {
          setTimeout(poll, 1500);
        }
      } catch (e) {
        setTimeout(poll, 2000);
      }
    };
    poll();
  } catch (error) {
    ElMessage.error("优化过程中出现错误");
    progressStatus.value = "exception";
  } finally {
    if (!optimizationTaskId.value) {
      isOptimizing.value = false;
    }
  }
};

const saveConfig = async () => {
  if (!optimizationForm.botId) {
    ElMessage.error("请选择机器人");
    return;
  }
  const payload = {
    optimizationConfig: {
      objective: {
        type: optimizationForm.objectiveMode,
        target: optimizationForm.objectiveTarget,
        weights: optimizationForm.objectiveWeights,
      },
      algorithm: optimizationForm.algorithm,
      parameters: optimizationForm.parameters,
      maxPosition: optimizationForm.maxPosition,
      stopLoss: optimizationForm.stopLoss,
      takeProfit: optimizationForm.takeProfit,
      saveTrades: optimizationForm.saveTrades,
      saveChartData: optimizationForm.saveChartData,
      generateReport: optimizationForm.generateReport,
      startDate: optimizationForm.startDate,
      endDate: optimizationForm.endDate,
      initialCapital: optimizationForm.initialCapital,
      settings: optimizationForm.settings,
    },
  };
  try {
    const response = await updateTradingBot(optimizationForm.botId, {
      configuration: JSON.stringify(payload),
    });
    if (response.success) {
      ElMessage.success("配置已保存");
    } else {
      ElMessage.error(response.message || "配置保存失败");
    }
  } catch (error: any) {
    ElMessage.error(error.message || "配置保存失败");
  }
};

const viewResult = (result: OptimizationResult) => {
  ElMessage.info(`查看参数组合详情: ${result.parameters}`);
};

const useParameters = (result: OptimizationResult) => {
  ElMessage.success(`已应用参数组合: ${result.parameters}`);
};

onMounted(() => {
  loadBots();
  loadSignalServiceDefinitions();

  // 设置默认时间范围
  const now = new Date();
  optimizationForm.endDate = now.toISOString();
  optimizationForm.startDate = new Date(
    now.getTime() - 90 * 24 * 60 * 60 * 1000,
  ).toISOString();
});

const loadBots = async () => {
  botsLoading.value = true;
  try {
    const response = await getTradingBots({
      page: 1,
      limit: 1000,
    });
    if (response.success) {
      bots.value = response.data.records || [];
    } else {
      ElMessage.error(response.message || "加载机器人列表失败");
    }
  } catch (error: any) {
    ElMessage.error(error.message || "加载机器人列表失败");
  } finally {
    botsLoading.value = false;
  }
};

const loadSignalServiceDefinitions = async () => {
  try {
    const response = await getSignalServiceDefinitions();
    if (response.success && response.data) {
      signalServiceDefinitions.value = response.data;
      const hasSsl = signalServiceDefinitions.value.some(
        (d) => (d.key || "").toLowerCase() === "sslchannelsignservice",
      );
      if (!hasSsl) {
        signalServiceDefinitions.value.push({
          key: "SslChannelSignService",
          label: "SSL Channel",
          parameters: [
            { key: "channel1.ma1-length", label: "通道1 MA1周期", type: "number", defaultValue: 100, min: 1, max: 1000, step: 1 },
            { key: "channel1.ma2-length", label: "通道1 MA2周期", type: "number", defaultValue: 100, min: 1, max: 1000, step: 1 },
            { key: "channel2.ma3-length", label: "通道2 MA3周期", type: "number", defaultValue: 20, min: 1, max: 1000, step: 1 },
            { key: "channel2.ma4-length", label: "通道2 MA4周期", type: "number", defaultValue: 20, min: 1, max: 1000, step: 1 },
          ],
        });
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || "加载信号服务参数失败");
  }
};

watch(
  () => optimizationForm.botId,
  async (newBotId) => {
    currentStrategyId.value = "";
    if (!newBotId) return;
    try {
      const botResp = await getTradingBotById(newBotId);
      if (botResp.success && botResp.data) {
        currentStrategyId.value = botResp.data.strategyId || "";
      }
    } catch (e: any) {
      ElMessage.error(e.message || "加载机器人详情失败");
    }
  },
  { immediate: true },
);

const sslChannelParamDefs = computed<SignalServiceParamDefinition[]>(() => {
  const defs = signalServiceDefinitions.value || [];
  const sslDef =
    defs.find((d) => d.key === "SslChannelSignService") ||
    defs.find((d) =>
      (d.label || "").toLowerCase().includes("ssl channel"),
    );
  return sslDef?.parameters || [];
});

const formatDefaultValue = (p: SignalServiceParamDefinition) => {
  if (p.type === "boolean") {
    return p.defaultValue ? "true" : "false";
  }
  return String(p.defaultValue);
};

const hasSelectOptions = (scope: any) => {
  const p = scope.row as SignalServiceParamDefinition;
  return Array.isArray(p.options) && p.options.length > 0;
};

const addOrUpdateSslRanges = () => {
  if (!isSslChannelStrategy.value) return;
  const defs = sslChannelParamDefs.value || [];
  const numericDefs = defs.filter((d) => d.type === "number");
  if (numericDefs.length === 0) return;
  const existingNames = new Set(optimizationForm.parameters.map((p) => p.name));
  numericDefs.forEach((d) => {
    const name = d.key || d.label;
    if (!name) return;
    const def = d as any;
    const dv = Number(def.defaultValue ?? 0);
    const min = typeof def.min === "number" ? def.min : Math.max(1, Math.floor(dv * 0.5));
    const max = typeof def.max === "number" ? def.max : Math.max(min + 1, Math.ceil(dv * 1.5));
    const step =
      typeof def.step === "number"
        ? def.step
        : Number.isInteger(dv) ? 1 : 0.1;
    if (!existingNames.has(name)) {
      optimizationForm.parameters.push({
        name,
        min,
        max,
        step,
        group: "SSL_CHANNEL",
      });
      existingNames.add(name);
    }
  });
};

watch([isSslChannelStrategy, sslChannelParamDefs], () => {
  addOrUpdateSslRanges();
});
const parameterGroups = computed(() => {
  const groups = new Map<
    string,
    { name: string; items: Array<{ param: ParameterRange; index: number }> }
  >();
  optimizationForm.parameters.forEach((param, index) => {
    const name = param.group || "";
    if (!groups.has(name)) {
      groups.set(name, { name, items: [] });
    }
    groups.get(name)!.items.push({ param, index });
  });
  return Array.from(groups.values());
});

const findSignalServiceByKeyOrLabel = (key: string) => {
  const normalizedKey = key.toLowerCase();
  return signalServiceDefinitions.value.find((service) => {
    const serviceKey = service.key?.toLowerCase();
    const serviceLabel = service.label?.toLowerCase();
    return normalizedKey === serviceKey || normalizedKey === serviceLabel;
  });
};

const getStrategyMatchKeys = (strategy: any) => {
  const keys = [
    strategy?.type,
    strategy?.strategyType,
    strategy?.name,
    strategy?.id,
  ];
  return keys.filter((key) => typeof key === "string" && key.trim().length > 0);
};

const isCombinedStrategy = (strategy: any) => {
  const keys = getStrategyMatchKeys(strategy).map((key: string) =>
    key.toLowerCase(),
  );
  return keys.some(
    (key) => key === "combined" || key === "combinedsignservice",
  );
};

const findMatchingSignalService = (strategy: any) => {
  const keys = getStrategyMatchKeys(strategy).map((key: string) =>
    key.toLowerCase(),
  );
  // 额外策略到服务键映射
  const mappedServiceKeys: string[] = [];
  if (keys.some((k) => k.includes("ssl_channel") || k.includes("ssl"))) {
    mappedServiceKeys.push("sslchannelsignservice");
  }
  // RangeFilterDW 策略映射
  if (keys.some((k) => k.includes("range_filter") || k === "range_filter")) {
    mappedServiceKeys.push("rangefilterdwsignservice");
  }
  return signalServiceDefinitions.value.find((service) => {
    const serviceKey = service.key?.toLowerCase();
    const serviceLabel = service.label?.toLowerCase();
    return (
      keys.some((key) => key === serviceKey || key === serviceLabel) ||
      mappedServiceKeys.some((mk) => mk === serviceKey)
    );
  });
};

const buildParameterRanges = (
  definitions: SignalServiceParamDefinition[],
  groupName: string,
) => {
  const numericDefinitions = definitions.filter(
    (item) => item.type === "number",
  );
  const ranges = numericDefinitions.map((item) => {
    const defaultValue =
      typeof item.defaultValue === "number" ? item.defaultValue : 0;
    const min = item.min ?? defaultValue;
    const max = item.max ?? defaultValue;
    const step = item.step ?? 1;
    return {
      name: item.key,
      min,
      max,
      step,
      group: groupName,
    };
  });
  return { ranges, ignored: definitions.length - numericDefinitions.length };
};

const mergeParameterRanges = (
  groups: Array<{ ranges: ParameterRange[]; ignored: number }>,
) => {
  const merged: ParameterRange[] = [];
  const seen = new Set<string>();
  let ignored = 0;
  groups.forEach((group) => {
    ignored += group.ignored;
    group.ranges.forEach((range) => {
      if (seen.has(range.name)) {
        return;
      }
      seen.add(range.name);
      merged.push(range);
    });
  });
  return { ranges: merged, ignored };
};

const parseOptimizationConfig = (configuration: any) => {
  if (!configuration) {
    return null;
  }
  let payload = configuration;
  if (typeof configuration === "string") {
    try {
      payload = JSON.parse(configuration);
    } catch (error) {
      return null;
    }
  }
  if (payload && typeof payload === "object" && payload.optimizationConfig) {
    return payload.optimizationConfig;
  }
  return null;
};

const normalizeParameters = (parameters: any[]) => {
  return parameters
    .filter((param) => param && typeof param.name === "string")
    .map((param) => ({
      name: param.name,
      min: Number(param.min ?? 0),
      max: Number(param.max ?? 0),
      step: Number(param.step ?? 1),
      values: Array.isArray(param.values) ? param.values : undefined,
      group: param.group,
    }));
};

const applyOptimizationConfig = (config: any) => {
  if (!config) {
    return false;
  }
  optimizationForm.algorithm = config.algorithm ?? optimizationForm.algorithm;
  const cfgObjective = config.objective || config.objectiveMode ? {
    type: config.objective?.type || config.objectiveMode,
    target: config.objective?.target || config.objectiveTarget,
    weights: config.objective?.weights || config.objectiveWeights,
  } : null;
  if (cfgObjective && typeof cfgObjective.type === "string") {
    optimizationForm.objectiveMode = cfgObjective.type as any;
  }
  if (cfgObjective && typeof cfgObjective.target === "string") {
    optimizationForm.objectiveTarget = cfgObjective.target as any;
  }
  if (cfgObjective && typeof cfgObjective.weights === "object") {
    optimizationForm.objectiveWeights = {
      total_return: Number(cfgObjective.weights.total_return ?? optimizationForm.objectiveWeights.total_return),
      win_rate: Number(cfgObjective.weights.win_rate ?? optimizationForm.objectiveWeights.win_rate),
      max_drawdown: Number(cfgObjective.weights.max_drawdown ?? optimizationForm.objectiveWeights.max_drawdown),
      return_over_max_drawdown: Number(cfgObjective.weights.return_over_max_drawdown ?? optimizationForm.objectiveWeights.return_over_max_drawdown),
      profit_loss_ratio: Number(cfgObjective.weights.profit_loss_ratio ?? optimizationForm.objectiveWeights.profit_loss_ratio),
      trades: Number(cfgObjective.weights.trades ?? optimizationForm.objectiveWeights.trades),
    };
  }
  optimizationForm.maxPosition =
    config.maxPosition ?? optimizationForm.maxPosition;
  optimizationForm.stopLoss = config.stopLoss ?? optimizationForm.stopLoss;
  optimizationForm.takeProfit =
    config.takeProfit ?? optimizationForm.takeProfit;
  optimizationForm.saveTrades =
    config.saveTrades ?? optimizationForm.saveTrades;
  optimizationForm.saveChartData =
    config.saveChartData ?? optimizationForm.saveChartData;
  optimizationForm.generateReport =
    config.generateReport ?? optimizationForm.generateReport;
  optimizationForm.startDate = config.startDate ?? optimizationForm.startDate;
  optimizationForm.endDate = config.endDate ?? optimizationForm.endDate;
  optimizationForm.initialCapital =
    config.initialCapital ?? optimizationForm.initialCapital;
  optimizationForm.settings = Array.isArray(config.settings)
    ? config.settings
    : optimizationForm.settings;
  if (Array.isArray(config.parameters) && config.parameters.length > 0) {
    optimizationForm.parameters = normalizeParameters(config.parameters);
    return true;
  }
  return false;
};

const syncParametersFromBot = async (botId: string) => {
  if (!botId) {
    return;
  }

  parametersLoading.value = true;
  try {
    const botResponse = await getTradingBotById(botId);
    if (!botResponse.success || !botResponse.data) {
      ElMessage.error(botResponse.message || "获取机器人信息失败");
      return;
    }

    const bot = botResponse.data;
    const savedConfig = parseOptimizationConfig(bot?.configuration);
    const appliedSavedParameters = applyOptimizationConfig(savedConfig);
    if (appliedSavedParameters) {
      return;
    }

    const strategyId = bot.strategyId;
    if (!strategyId) {
      ElMessage.warning("机器人未配置策略");
      return;
    }

    if (signalServiceDefinitions.value.length === 0) {
      await loadSignalServiceDefinitions();
    }

    const strategy = await getStrategyById(strategyId);
    let ranges: ParameterRange[] = [];
    let ignored = 0;
    if (isCombinedStrategy(strategy)) {
      const rangeService = findSignalServiceByKeyOrLabel(
        "rangefilterdwsignservice",
      );
      const bollingerService = findSignalServiceByKeyOrLabel(
        "bollingerrsisignservice",
      );
      const groupResults: Array<{ ranges: ParameterRange[]; ignored: number }> =
        [];
      if (rangeService) {
        groupResults.push(
          buildParameterRanges(
            rangeService.parameters || [],
            rangeService.label || rangeService.key || "RangeFilterDW",
          ),
        );
      }
      if (bollingerService) {
        groupResults.push(
          buildParameterRanges(
            bollingerService.parameters || [],
            bollingerService.label || bollingerService.key || "BollingerRsi",
          ),
        );
      }
      const combined = mergeParameterRanges(groupResults);
      ranges = combined.ranges;
      ignored = combined.ignored;
    } else {
      const matchedService = findMatchingSignalService(strategy);
      if (!matchedService) {
        // SSL_CHANNEL 回退：直接使用注入的 SSL 定义
        if (isSslChannelStrategy.value && sslChannelParamDefs.value.length > 0) {
          const result = buildParameterRanges(
            sslChannelParamDefs.value,
            "SSL_CHANNEL",
          );
          ranges = result.ranges;
          ignored = result.ignored;
        } else {
          ElMessage.warning("未找到与策略匹配的信号服务参数");
          return;
        }
      }
      const result = buildParameterRanges(
        matchedService.parameters || [],
        matchedService.label || matchedService.key || "参数",
      );
      ranges = result.ranges;
      ignored = result.ignored;
    }

    optimizationForm.parameters = ranges;

    if (ignored > 0) {
      ElMessage.info("当前信号服务包含非数值参数，已自动忽略");
    }
  } catch (error: any) {
    ElMessage.error(error.message || "加载策略参数失败");
  } finally {
    parametersLoading.value = false;
  }
};

watch(
  () => optimizationForm.botId,
  (botId) => {
    if (!botId) {
      return;
    }
    syncParametersFromBot(botId);
  },
);
</script>

<style scoped>
.parameter-optimization {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--primary-text);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.optimization-content {
  background: var(--primary-bg);
  border-radius: 8px;
  padding: 24px;
  margin-bottom: 24px;
}

.optimization-form {
  max-width: 800px;
}

.parameter-ranges {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.parameter-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.parameter-group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--secondary-text);
}

.parameter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-settings {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.risk-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-item label {
  min-width: 100px;
  color: var(--text-secondary);
  font-size: var(--font-sm);
  font-weight: var(--font-medium);
}

.output-settings {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.date-range {
  display: flex;
  align-items: center;
  gap: 12px;
}

.date-separator {
  color: var(--secondary-text);
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 14px;
  color: var(--secondary-text);
}

.results-section {
  background: var(--primary-bg);
  border-radius: 8px;
  padding: 24px;
}

.results-section h3 {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--primary-text);
}
</style>
