<template>
  <div class="quant-factor-mining">
    <div class="fm-header">
      <div class="header-title">
        <h2><el-icon style="vertical-align: middle; margin-right: 8px"><Cpu /></el-icon>因子自动挖掘</h2>
        <span class="header-subtitle">基于遗传规划的因子自动发现与筛选</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="fm-tabs">
      <!-- ==================== TAB 1: 任务创建 ==================== -->
      <el-tab-pane label="任务创建" name="create">
        <div class="tab-body">
          <el-row :gutter="24">
            <el-col :span="14">
              <el-card shadow="never">
                <template #header><span>终端池选择</span></template>
                <div style="margin-bottom: 12px">
                  <el-select v-model="createForm.symbol" style="width: 140px; margin-right: 8px">
                    <el-option label="BTC/USDT" value="BTCUSDT" />
                    <el-option label="ETH/USDT" value="ETHUSDT" />
                    <el-option label="ETH永续" value="ETH-USDT-SWAP" />
                    <el-option label="SOL/USDT" value="SOLUSDT" />
                  </el-select>
                  <el-select v-model="createForm.interval" style="width: 100px; margin-right: 8px">
                    <el-option label="15m" value="15m" />
                    <el-option label="30m" value="30m" />
                    <el-option label="1H" value="1H" />
                    <el-option label="4H" value="4H" />
                    <el-option label="1D" value="1D" />
                  </el-select>
                  <el-button type="primary" size="default" :loading="loadingTerminalPool" @click="loadTerminalPool">
                    <el-icon><Refresh /></el-icon> 加载终端池
                  </el-button>
                </div>
                <div v-if="terminalPool.length > 0" class="terminal-pool-stats" style="margin-bottom: 8px">
                  <span>共 {{ terminalPool.length }} 个基础特征变体</span>
                  <el-button size="small" text @click="selectAllTerminals" style="margin-left: 8px">全选</el-button>
                  <el-button size="small" text @click="deselectAllTerminals">反选</el-button>
                  <span style="margin-left: 8px; color: var(--el-color-primary)">已选 {{ selectedTerminals.length }} 个</span>
                </div>
                <div v-if="terminalPool.length > 0" class="terminal-pool-grid">
                  <div
                    v-for="t in terminalPool"
                    :key="t.name"
                    class="terminal-item"
                    :class="{ selected: selectedTerminalsSet.has(t.name) }"
                    @click="toggleTerminal(t.name)"
                  >
                    <span class="terminal-name">{{ t.name }}</span>
                  </div>
                </div>
                <el-empty v-else-if="!loadingTerminalPool" description="点击上方按钮加载终端池" />
              </el-card>
            </el-col>

            <el-col :span="10">
              <el-card shadow="never" style="margin-bottom: 16px">
                <template #header><span>GP 算子集</span></template>
                <div class="operator-grid">
                  <div v-for="op in allOperators" :key="op" class="operator-item" :class="{ selected: selectedOperatorsSet.has(op) }" @click="toggleOperator(op)">
                    <span class="operator-symbol">{{ op }}</span>
                  </div>
                </div>
              </el-card>

              <el-card shadow="never">
                <template #header><span>GP 参数配置</span></template>
                <el-form label-width="130px" size="small">
                  <el-row :gutter="12">
                    <el-col :span="12">
                      <el-form-item label="种群大小">
                        <el-input-number v-model="createForm.populationSize" :min="100" :max="2000" :step="100" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="进化代数">
                        <el-input-number v-model="createForm.generations" :min="5" :max="100" :step="5" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-row :gutter="12">
                    <el-col :span="12">
                      <el-form-item label="锦标赛大小">
                        <el-input-number v-model="createForm.tournamentSize" :min="2" :max="20" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="适应度指标">
                        <el-select v-model="createForm.fitnessMetric" style="width: 100%">
                          <el-option label="Rank IC" value="RANK_IC" />
                          <el-option label="夏普比率" value="SHARPE" />
                          <el-option label="IC+夏普组合" value="IC_SHARPE_COMBO" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-row :gutter="12">
                    <el-col :span="12">
                      <el-form-item label="交叉概率">
                        <el-slider v-model="createForm.crossoverProb" :min="0.1" :max="1.0" :step="0.05" show-input style="width: 100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="变异概率">
                        <el-slider v-model="createForm.mutationProb" :min="0.01" :max="0.5" :step="0.01" show-input style="width: 100%" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-row :gutter="12">
                    <el-col :span="12">
                      <el-form-item label="复杂度惩罚">
                        <el-input-number v-model="createForm.parsimonyCoefficient" :min="0" :max="0.01" :step="0.0001" :precision="4" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="回看K线数">
                        <el-input-number v-model="createForm.lookbackBars" :min="200" :max="2000" :step="100" style="width: 100%" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-form-item label="任务名称" style="margin-top: 8px">
                    <el-input v-model="createForm.taskName" placeholder="留空自动生成" />
                  </el-form-item>
                </el-form>
              </el-card>
            </el-col>
          </el-row>

          <div style="margin-top: 16px; text-align: right">
            <el-button type="primary" size="large" :loading="creatingTask" :disabled="!canCreateTask" @click="handleCreateAndStart">
              <el-icon style="margin-right: 4px"><Lightning /></el-icon> 创建并启动挖掘
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- ==================== TAB 2: 任务列表 ==================== -->
      <el-tab-pane label="任务列表" name="tasks">
        <div class="tab-body">
          <div class="task-toolbar">
            <el-button @click="loadTasks">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>

          <el-table :data="taskList" v-loading="tasksLoading" stripe border style="width: 100%">
            <el-table-column prop="taskName" label="任务名称" min-width="140" />
            <el-table-column prop="symbol" label="交易对" width="90" />
            <el-table-column prop="interval" label="周期" width="60" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="160">
              <template #default="{ row }">
                <el-progress
                  v-if="row.status === 'RUNNING'"
                  :percentage="Math.round(row.progress * 100)"
                  :stroke-width="12"
                  style="width: 120px"
                />
                <span v-else-if="row.status === 'DONE'" style="color: var(--el-color-success)">完成</span>
                <span v-else-if="row.status === 'FAILED'" style="color: var(--el-color-danger)">{{ row.errorMsg || '失败' }}</span>
                <span v-else>--</span>
              </template>
            </el-table-column>
            <el-table-column prop="bestFitness" label="最佳适应度" width="110" sortable="custom">
              <template #default="{ row }">{{ row.bestFitness != null ? row.bestFitness.toFixed(4) : '--' }}</template>
            </el-table-column>
            <el-table-column label="最佳表达式" min-width="200">
              <template #default="{ row }">
                <span v-if="row.bestExpression" class="expr-text">{{ row.bestExpression }}</span>
                <span v-else>--</span>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ row.createTime ? dayjs(row.createTime).format('YYYY-MM-DD HH:mm') : '--' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING'" text type="primary" size="small" @click="handleStartTask(row)">
                  <el-icon><VideoPlay /></el-icon> 启动
                </el-button>
                <el-button v-if="row.status === 'RUNNING'" text type="danger" size="small" @click="handleCancelTask(row)">
                  <el-icon><CircleClose /></el-icon> 取消
                </el-button>
                <el-button v-if="row.status === 'DONE'" text type="primary" size="small" @click="handleViewCandidates(row)">
                  <el-icon><DataAnalysis /></el-icon> 候选因子
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 当前运行任务的进度卡片 -->
          <el-card v-if="runningTask" shadow="never" style="margin-top: 16px">
            <template #header>
              <span><el-icon class="icon-loading"><Loading /></el-icon> 正在运行: {{ runningTask.taskName }}</span>
            </template>
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="交易对">{{ runningTask.symbol }}</el-descriptions-item>
              <el-descriptions-item label="周期">{{ runningTask.interval }}</el-descriptions-item>
              <el-descriptions-item label="适应度指标">{{ runningTask.fitnessMetric }}</el-descriptions-item>
              <el-descriptions-item label="种群/代数">{{ runningTask.populationSize }} / {{ runningTask.generations }}</el-descriptions-item>
              <el-descriptions-item label="进度">{{ Math.round(runningTask.progress * 100) }}%</el-descriptions-item>
              <el-descriptions-item label="最佳适应度">{{ runningTask.bestFitness != null ? runningTask.bestFitness.toFixed(4) : '--' }}</el-descriptions-item>
            </el-descriptions>
            <el-progress
              :percentage="Math.round(runningTask.progress * 100)"
              :stroke-width="18"
              style="margin-top: 12px"
            >
              <span>第 {{ Math.round(runningTask.progress * runningTask.generations) }} / {{ runningTask.generations }} 代</span>
            </el-progress>
            <div v-if="runningTask.bestExpression" style="margin-top: 8px; font-size: 13px; color: var(--text-muted)">
              当前最佳: {{ runningTask.bestExpression }}
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- ==================== TAB 3: 候选因子管理 ==================== -->
      <el-tab-pane label="候选因子" name="candidates">
        <div class="tab-body">
          <el-row :gutter="16" style="margin-bottom: 16px">
            <el-col :span="6">
              <el-select v-model="candidateTaskId" placeholder="选择任务" style="width: 100%" @change="loadCandidates">
                <el-option
                  v-for="t in doneTasks"
                  :key="t.id"
                  :label="t.taskName + ' (' + t.symbol + ')'"
                  :value="t.id"
                />
              </el-select>
            </el-col>
            <el-col :span="6">
              <span v-if="candidates.length > 0" style="line-height: 32px; color: var(--text-muted); font-size: 13px">
                共 {{ candidates.length }} 个候选因子，
                <span style="color: var(--el-color-primary)">{{ selectedCandidatesCount }} 个已选中</span>
              </span>
            </el-col>
            <el-col :span="12" style="text-align: right">
              <el-button @click="loadCandidates">
                <el-icon><Refresh /></el-icon> 刷新
              </el-button>
            </el-col>
          </el-row>

          <el-table
            :data="candidates"
            v-loading="candidatesLoading"
            stripe
            border
            style="width: 100%"
            @sort-change="handleCandidateSort"
            :default-sort="{ prop: 'fitness', order: 'descending' }"
          >
            <el-table-column label="排名" type="index" width="55" align="center" />
            <el-table-column label="表达式" min-width="250">
              <template #default="{ row }">
                <span class="expr-text">{{ row.expression }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fitness" label="适应度" width="100" sortable="custom" align="center">
              <template #default="{ row }">{{ row.fitness.toFixed(4) }}</template>
            </el-table-column>
            <el-table-column prop="rankIc" label="Rank IC" width="100" sortable="custom" align="center">
              <template #default="{ row }">{{ row.rankIc != null ? row.rankIc.toFixed(4) : '--' }}</template>
            </el-table-column>
            <el-table-column prop="sharpe" label="夏普" width="90" sortable="custom" align="center">
              <template #default="{ row }">{{ row.sharpe != null ? row.sharpe.toFixed(3) : '--' }}</template>
            </el-table-column>
            <el-table-column prop="turnover" label="换手率" width="90" sortable="custom" align="center">
              <template #default="{ row }">{{ row.turnover != null ? row.turnover.toFixed(3) : '--' }}</template>
            </el-table-column>
            <el-table-column prop="corrWithLabel" label="相关标签" width="100" sortable="custom" align="center">
              <template #default="{ row }">{{ row.corrWithLabel != null ? row.corrWithLabel.toFixed(4) : '--' }}</template>
            </el-table-column>
            <el-table-column prop="treeDepth" label="深度" width="60" sortable="custom" align="center" />
            <el-table-column prop="nodeCount" label="节点数" width="65" sortable="custom" align="center" />
            <el-table-column prop="topRet" label="Top收益" width="90" sortable="custom" align="center">
              <template #default="{ row }">{{ row.topRet != null ? (row.topRet * 100).toFixed(2) + '%' : '--' }}</template>
            </el-table-column>
            <el-table-column label="选中" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.selected" type="success" size="small">是</el-tag>
                <el-tag v-else type="info" size="small">否</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.selected" text type="primary" size="small" @click="handleSelect(row)">
                  <el-icon><Check /></el-icon> 选中
                </el-button>
                <el-button v-else text type="warning" size="small" @click="handleDeselect(row)">
                  <el-icon><Close /></el-icon> 取消
                </el-button>
                <el-button text type="info" size="small" @click="showCandidateDetail(row)">
                  <el-icon><InfoFilled /></el-icon> 详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 已选中的自定义特征列表 -->
          <el-card v-if="selectedCandidates.length > 0" shadow="never" style="margin-top: 16px">
            <template #header>
              <span><el-icon style="color: var(--el-color-success)"><SuccessFilled /></el-icon> 已选自定义特征 ({{ selectedCandidates.length }})</span>
            </template>
            <el-table :data="selectedCandidates" stripe border size="small">
              <el-table-column prop="customFeatureName" label="特征名" width="160" />
              <el-table-column label="表达式" min-width="220">
                <template #default="{ row }">
                  <span class="expr-text">{{ row.expression }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="fitness" label="适应度" width="90" align="center">
                <template #default="{ row }">{{ row.fitness.toFixed(4) }}</template>
              </el-table-column>
              <el-table-column prop="rankIc" label="Rank IC" width="90" align="center">
                <template #default="{ row }">{{ row.rankIc != null ? row.rankIc.toFixed(4) : '--' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button text type="warning" size="small" @click="handleDeselect(row)">
                    <el-icon><Close /></el-icon> 取消
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 选中候选因子弹窗 -->
    <el-dialog v-model="selectDialogVisible" title="选中为自定义特征" width="450px">
      <el-form label-width="100px">
        <el-form-item label="表达式">
          <span class="expr-text">{{ selectDialogCandidate?.expression }}</span>
        </el-form-item>
        <el-form-item label="适应度">{{ selectDialogCandidate?.fitness.toFixed(4) }}</el-form-item>
        <el-form-item label="特征名称">
          <el-input v-model="selectDialogName" placeholder="如: GP_RSI_Momentum" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="selectDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="selectingCandidate" @click="confirmSelect">确认选中</el-button>
      </template>
    </el-dialog>

    <!-- 候选因子详情抽屉 -->
    <el-drawer v-model="detailDrawerVisible" title="候选因子详情" size="450px">
      <template v-if="detailCandidate">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="适应度" :span="2">{{ detailCandidate.fitness.toFixed(4) }}</el-descriptions-item>
          <el-descriptions-item label="Rank IC">{{ detailCandidate.rankIc != null ? detailCandidate.rankIc.toFixed(4) : '--' }}</el-descriptions-item>
          <el-descriptions-item label="夏普比率">{{ detailCandidate.sharpe != null ? detailCandidate.sharpe.toFixed(3) : '--' }}</el-descriptions-item>
          <el-descriptions-item label="换手率">{{ detailCandidate.turnover != null ? detailCandidate.turnover.toFixed(3) : '--' }}</el-descriptions-item>
          <el-descriptions-item label="标签相关">{{ detailCandidate.corrWithLabel != null ? detailCandidate.corrWithLabel.toFixed(4) : '--' }}</el-descriptions-item>
          <el-descriptions-item label="Top收益">{{ detailCandidate.topRet != null ? (detailCandidate.topRet * 100).toFixed(2) + '%' : '--' }}</el-descriptions-item>
          <el-descriptions-item label="树深度">{{ detailCandidate.treeDepth }}</el-descriptions-item>
          <el-descriptions-item label="节点数">{{ detailCandidate.nodeCount }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ detailCandidate.createTime || '--' }}</el-descriptions-item>
        </el-descriptions>

        <el-card shadow="never">
          <template #header><span>表达式</span></template>
          <div class="expr-display">{{ detailCandidate.expression }}</div>
          <div v-if="detailCandidate.expressionLatex" style="margin-top: 8px; font-size: 12px; color: var(--text-muted)">
            LaTeX: {{ detailCandidate.expressionLatex }}
          </div>
        </el-card>

        <div style="margin-top: 16px; text-align: center">
          <el-button
            v-if="!detailCandidate.selected"
            type="primary"
            @click="openSelectFromDetail"
          >
            <el-icon><Check /></el-icon> 选中为自定义特征
          </el-button>
          <el-button
            v-else
            type="warning"
            @click="handleDeselect(detailCandidate)"
          >
            <el-icon><Close /></el-icon> 取消选中
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Cpu, Lightning, Refresh, VideoPlay, CircleClose, DataAnalysis,
  Check, Close, InfoFilled, SuccessFilled, Loading, Search
} from "@element-plus/icons-vue";
import dayjs from "dayjs";
import {
  getTerminalPool,
  createFactorMiningTask,
  startFactorMiningTask,
  getFactorMiningTask,
  listFactorMiningTasks,
  cancelFactorMiningTask,
  listCandidates,
  listSelectedCandidates,
  selectCandidate,
  deselectCandidate,
  TerminalPoolVariant, FactorMiningTaskVO, FactorCandidateVO
} from "@/api/quant-factor-mining";

const allOperators = ["+", "-", "*", "/", "sqrt", "log", "abs", "neg", "max", "min", "ts_sum", "ts_mean", "ts_std", "ts_max", "ts_min", "ts_median"];

const activeTab = ref("create");

// ---- Tab 1: 任务创建 ----
const loadingTerminalPool = ref(false);
const terminalPool = ref<TerminalPoolVariant[]>([]);
const selectedTerminals = ref<string[]>([]);
const selectedTerminalsSet = computed(() => new Set(selectedTerminals.value));
const selectedOperators = ref<string[]>(["+", "-", "*", "/", "ts_mean", "ts_std"]);
const selectedOperatorsSet = computed(() => new Set(selectedOperators.value));

const createForm = ref({
  symbol: "BTCUSDT",
  interval: "1H",
  taskName: "",
  populationSize: 500,
  generations: 20,
  tournamentSize: 5,
  crossoverProb: 0.8,
  mutationProb: 0.1,
  parsimonyCoefficient: 0.001,
  fitnessMetric: "RANK_IC",
  lookbackBars: 500
});

const canCreateTask = computed(() => selectedTerminals.value.length >= 3 && selectedOperators.value.length >= 3);

const creatingTask = ref(false);

function toggleTerminal(name: string) {
  const idx = selectedTerminals.value.indexOf(name);
  if (idx >= 0) {
    selectedTerminals.value.splice(idx, 1);
  } else {
    selectedTerminals.value.push(name);
  }
}

function toggleOperator(op: string) {
  const idx = selectedOperators.value.indexOf(op);
  if (idx >= 0) {
    selectedOperators.value.splice(idx, 1);
  } else {
    selectedOperators.value.push(op);
  }
}

function selectAllTerminals() {
  selectedTerminals.value = terminalPool.value.map(t => t.name);
}

function deselectAllTerminals() {
  selectedTerminals.value = [];
}

async function loadTerminalPool() {
  loadingTerminalPool.value = true;
  try {
    const res = await getTerminalPool(createForm.value.symbol, createForm.value.interval);
    const data = (res as any).data || res;
    terminalPool.value = data.variants || [];
  } catch {
    ElMessage.error("加载终端池失败");
  } finally {
    loadingTerminalPool.value = false;
  }
}

async function handleCreateAndStart() {
  if (!canCreateTask.value) {
    ElMessage.warning("请至少选择3个终端特征和3个算子");
    return;
  }
  creatingTask.value = true;
  try {
    const req = {
      taskName: createForm.value.taskName || `GP挖掘_${createForm.value.symbol}_${dayjs().format('MMDDHHmm')}`,
      symbol: createForm.value.symbol,
      interval: createForm.value.interval,
      operatorSet: selectedOperators.value,
      terminalSet: selectedTerminals.value,
      populationSize: createForm.value.populationSize,
      generations: createForm.value.generations,
      tournamentSize: createForm.value.tournamentSize,
      crossoverProb: createForm.value.crossoverProb,
      mutationProb: createForm.value.mutationProb,
      parsimonyCoefficient: createForm.value.parsimonyCoefficient,
      fitnessMetric: createForm.value.fitnessMetric,
      lookbackBars: createForm.value.lookbackBars
    };
    const res = await createFactorMiningTask(req);
    const task = (res as any).data || res;
    ElMessage.success("任务创建成功");
    await startFactorMiningTask(task.id || task.taskId);
    ElMessage.info("挖掘任务已启动");
    activeTab.value = "tasks";
    loadTasks();
  } catch (e: any) {
    ElMessage.error(e.message || "创建任务失败");
  } finally {
    creatingTask.value = false;
  }
}

// ---- Tab 2: 任务列表 ----
const tasksLoading = ref(false);
const taskList = ref<FactorMiningTaskVO[]>([]);
const runningTask = ref<FactorMiningTaskVO | null>(null);
let pollTimer: number | null = null;

function statusTag(s: string) {
  if (s === 'DONE') return 'success';
  if (s === 'RUNNING') return 'warning';
  if (s === 'FAILED') return 'danger';
  if (s === 'PENDING') return 'info';
  return 'info';
}

function statusText(s: string) {
  if (s === 'DONE') return '完成';
  if (s === 'RUNNING') return '运行中';
  if (s === 'FAILED') return '失败';
  if (s === 'PENDING') return '待启动';
  return s;
}

async function loadTasks() {
  tasksLoading.value = true;
  try {
    const res = await listFactorMiningTasks(20);
    taskList.value = Array.isArray(res) ? res : (res as any).data || [];
    const run = taskList.value.find(t => t.status === 'RUNNING');
    runningTask.value = run || null;
    if (run) {
      startPolling(run.id);
    }
  } catch {
    taskList.value = [];
  } finally {
    tasksLoading.value = false;
  }
}

function startPolling(taskId: string) {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = window.setInterval(async () => {
    try {
      const res = await getFactorMiningTask(taskId);
      const updated = (res as any).data || res;
      const idx = taskList.value.findIndex(t => t.id === taskId);
      if (idx >= 0) {
        taskList.value[idx] = updated;
      }
      if (updated.status === 'RUNNING') {
        runningTask.value = updated;
      } else {
        runningTask.value = null;
        if (pollTimer) {
          clearInterval(pollTimer);
          pollTimer = null;
        }
        if (updated.status === 'DONE') {
          ElMessage.success('挖掘任务完成');
        } else if (updated.status === 'FAILED') {
          ElMessage.error('挖掘任务失败: ' + (updated.errorMsg || ''));
        }
      }
    } catch {
      if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
      }
    }
  }, 2000);
}

async function handleStartTask(task: FactorMiningTaskVO) {
  try {
    await startFactorMiningTask(task.id);
    ElMessage.info("任务已启动");
    loadTasks();
  } catch {
    ElMessage.error("启动失败");
  }
}

async function handleCancelTask(task: FactorMiningTaskVO) {
  try {
    await cancelFactorMiningTask(task.id);
    ElMessage.info("任务已取消");
    loadTasks();
  } catch {
    ElMessage.error("取消失败");
  }
}

function handleViewCandidates(task: FactorMiningTaskVO) {
  candidateTaskId.value = task.id;
  activeTab.value = "candidates";
  loadCandidates();
}

// ---- Tab 3: 候选因子 ----
const candidatesLoading = ref(false);
const candidateTaskId = ref<string>("");
const candidates = ref<FactorCandidateVO[]>([]);
const selectedCandidates = ref<FactorCandidateVO[]>([]);

const doneTasks = computed(() => taskList.value.filter(t => t.status === 'DONE'));
const selectedCandidatesCount = computed(() => candidates.value.filter(c => c.selected).length);

async function loadCandidates() {
  if (!candidateTaskId.value) return;
  candidatesLoading.value = true;
  try {
    const res = await listCandidates(candidateTaskId.value);
    candidates.value = Array.isArray(res) ? res : (res as any).data || [];
  } catch {
    candidates.value = [];
  } finally {
    candidatesLoading.value = false;
  }
}

async function loadSelectedCandidates() {
  try {
    const res = await listSelectedCandidates();
    selectedCandidates.value = Array.isArray(res) ? res : (res as any).data || [];
  } catch {
    selectedCandidates.value = [];
  }
}

// ---- 选中/取消 ----
const selectDialogVisible = ref(false);
const selectDialogCandidate = ref<FactorCandidateVO | null>(null);
const selectDialogName = ref("");
const selectingCandidate = ref(false);

function handleSelect(candidate: FactorCandidateVO) {
  selectDialogCandidate.value = candidate;
  selectDialogName.value = `GP_${candidate.expression?.substring(0, 12).replace(/[^a-zA-Z0-9_]/g, '_')}`;
  selectDialogVisible.value = true;
}

async function confirmSelect() {
  if (!selectDialogCandidate.value || !selectDialogName.value.trim()) {
    ElMessage.warning("请输入特征名称");
    return;
  }
  selectingCandidate.value = true;
  try {
    await selectCandidate(selectDialogCandidate.value.id, selectDialogName.value.trim());
    ElMessage.success("已选中为自定义特征");
    selectDialogVisible.value = false;
    loadCandidates();
    loadSelectedCandidates();
  } catch {
    ElMessage.error("操作失败");
  } finally {
    selectingCandidate.value = false;
  }
}

async function handleDeselect(candidate: FactorCandidateVO) {
  try {
    await deselectCandidate(candidate.id);
    ElMessage.success("已取消选中");
    loadCandidates();
    loadSelectedCandidates();
  } catch {
    ElMessage.error("操作失败");
  }
}

function openSelectFromDetail() {
  detailDrawerVisible.value = false;
  if (detailCandidate.value) {
    handleSelect(detailCandidate.value);
  }
}

// ---- 详情 ----
const detailDrawerVisible = ref(false);
const detailCandidate = ref<FactorCandidateVO | null>(null);

function showCandidateDetail(candidate: FactorCandidateVO) {
  detailCandidate.value = candidate;
  detailDrawerVisible.value = true;
}

// ---- 排序 ----
let candidateSortOrder: string | null = null;

function handleCandidateSort(sort: { prop: string; order: string }) {
  const prop = sort.prop as keyof FactorCandidateVO;
  if (!prop || !sort.order) return;
  const dir = sort.order === 'ascending' ? 1 : -1;
  candidates.value.sort((a: any, b: any) => {
    const va = a[prop] ?? 0;
    const vb = b[prop] ?? 0;
    return (va - vb) * dir;
  });
}

// ---- 生命周期 ----
onMounted(async () => {
  await loadTasks();
  await loadSelectedCandidates();
});

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
});
</script>

<style scoped>
.quant-factor-mining {
  padding: 0;
  height: 100%;
}

.fm-header {
  margin-bottom: 16px;
}

.header-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
}

.header-subtitle {
  font-size: 13px;
  color: var(--text-muted, #909399);
  margin-top: 4px;
  display: block;
}

.tab-body {
  min-height: 300px;
}

.terminal-pool-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 400px;
  overflow-y: auto;
}

.terminal-item {
  padding: 4px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
  user-select: none;
}

.terminal-item:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.terminal-item.selected {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.terminal-name {
  white-space: nowrap;
}

.operator-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.operator-item {
  padding: 4px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  font-family: monospace;
  transition: all 0.2s;
  user-select: none;
}

.operator-item:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.operator-item.selected {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.operator-symbol {
  font-weight: 500;
}

.task-toolbar {
  margin-bottom: 12px;
}

.expr-text {
  font-family: "Courier New", Courier, monospace;
  font-size: 12px;
  color: var(--el-color-primary-dark-2, #2c3e50);
  word-break: break-all;
}

.expr-display {
  font-family: "Courier New", Courier, monospace;
  font-size: 14px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  word-break: break-all;
  line-height: 1.6;
}

.icon-loading {
  animation: rotating 1.5s linear infinite;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
