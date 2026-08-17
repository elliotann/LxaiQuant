<template>
  <div class="trading-bots">
    <div class="page-header">
      <h2>交易机器人管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="createBot">
          <el-icon><Plus /></el-icon>
          创建机器人
        </el-button>
        <el-button type="success" @click="openAiDialog">
          <el-icon><Lightning /></el-icon>
          AI 智能创建
        </el-button>
        <el-button @click="refreshBots">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 筛选区域 -->
    <div class="filter-section">
      <el-form :model="filterForm" inline class="filter-form">
        <el-form-item label="机器人名称">
          <el-input
            v-model="filterForm.botName"
            placeholder="输入机器人名称"
            style="width: 150px"
            clearable
          />
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input
            v-model="filterForm.userId"
            placeholder="输入用户ID"
            style="width: 120px"
            clearable
          />
        </el-form-item>
        <el-form-item label="交易所">
          <el-select
            v-model="filterForm.exchange"
            placeholder="选择交易所"
            style="width: 120px"
            clearable
          >
            <el-option label="全部" value="" />
            <el-option label="BINANCE" value="BINANCE" />
            <el-option label="OKX" value="OKX" />
            <el-option label="BYBIT" value="BYBIT" />
            <el-option label="GATEIO" value="GATEIO" />
            <el-option label="HUOBI" value="HUOBI" />
            <el-option label="COINBASE" value="COINBASE" />
          </el-select>
        </el-form-item>
        <el-form-item label="交易对">
          <el-select
            v-model="filterForm.tradingPair"
            placeholder="选择交易对"
            style="width: 150px"
            clearable
            filterable
          >
            <el-option label="BTC永续" value="BTC-USDT-SWAP" />
            <el-option label="ETH永续" value="ETH-USDT-SWAP" />
            <el-option label="BNB永续" value="BNB-USDT-SWAP" />
            <el-option label="ADA永续" value="ADA-USDT-SWAP" />
            <el-option label="LTC永续" value="LTC-USDT-SWAP" />
            <el-option label="DOT永续" value="DOT-USDT-SWAP" />
            <el-option label="LINK永续" value="LINK-USDT-SWAP" />
            <el-option label="XRP永续" value="XRP-USDT-SWAP" />
            <el-option label="SOL永续" value="SOL-USDT-SWAP" />
            <el-option label="DOGE永续" value="DOGE-USDT-SWAP" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="filterForm.status"
            placeholder="选择状态"
            style="width: 120px"
            clearable
          >
            <el-option label="全部" value="" />
            <el-option label="已创建" value="CREATED" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已暂停" value="PAUSED" />
            <el-option label="已停止" value="STOPPED" />
            <el-option label="错误状态" value="ERROR" />
            <el-option label="已购买" value="PURCHASED" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略ID">
          <el-input
            v-model="filterForm.strategyId"
            placeholder="输入策略ID"
            style="width: 120px"
            clearable
          />
        </el-form-item>
        <el-form-item label="账户ID">
          <el-input
            v-model="filterForm.accountId"
            placeholder="输入账户ID"
            style="width: 120px"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计信息 -->
    <div class="stats-section">
      <el-row :gutter="20">
        <el-col :span="4">
          <div class="stat-card">
            <div class="stat-number">{{ stats.total }}</div>
            <div class="stat-label">总机器人</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card running">
            <div class="stat-number">{{ stats.running }}</div>
            <div class="stat-label">运行中</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card paused">
            <div class="stat-number">{{ stats.paused }}</div>
            <div class="stat-label">已暂停</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card stopped">
            <div class="stat-number">{{ stats.stopped }}</div>
            <div class="stat-label">已停止</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card created">
            <div class="stat-number">{{ stats.created }}</div>
            <div class="stat-label">已创建</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-card error">
            <div class="stat-number">{{ stats.error }}</div>
            <div class="stat-label">错误状态</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 机器人列表 -->
    <div class="bots-table">
      <el-table
        :data="bots"
        style="width: 100%"
        v-loading="loading"
        :default-sort="{ prop: 'createdAt', order: 'descending' }"
      >
        <el-table-column prop="botId" label="机器人ID" width="180" fixed>
          <template #default="scope">
            <el-link type="primary" @click="viewBotDetail(scope.row)">{{
              scope.row.botId
            }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="botName" label="机器人名称" width="150" />
        <el-table-column prop="userId" label="用户ID" width="120" />
        <el-table-column prop="accountId" label="账户ID" width="120" />
        <el-table-column prop="exchange" label="交易所" width="100">
          <template #default="scope">
            <el-tag size="small" type="info">{{ scope.row.exchange || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="strategyId" label="策略ID" width="120" />
        <el-table-column prop="tradingPair" label="交易对" width="100" />
        <el-table-column prop="direction" label="做单方向" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.direction" size="small" :type="scope.row.direction === 'BOTH' ? 'info' : scope.row.direction === 'LONG' ? 'success' : 'danger'">
              {{ scope.row.direction === 'BOTH' ? '双向' : scope.row.direction === 'LONG' ? '只做多' : '只做空' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="资金" width="200">
          <template #default="scope">
            <div class="capital-info">
              <div>分配: {{ formatCurrency(scope.row.allocatedCapital) }}</div>
              <div>当前: {{ formatCurrency(scope.row.currentCapital) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusTag(scope.row.status)" size="small">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="启动时间" width="160">
          <template #default="scope">
            {{ scope.row.startTime ? formatDate(scope.row.startTime) : "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="lastSignalTime" label="最后信号" width="160">
          <template #default="scope">
            {{
              scope.row.lastSignalTime
                ? formatDate(scope.row.lastSignalTime)
                : "-"
            }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="scope">
            {{ formatDate(scope.row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="scope">
            <el-button-group v-if="scope.row.status !== 'PURCHASED'" size="small">
              <el-button
                v-if="
                  scope.row.status === 'CREATED' ||
                  scope.row.status === 'STOPPED' ||
                  scope.row.status === 'PAUSED'
                "
                type="success"
                @click="startBot(scope.row)"
              >
                启动
              </el-button>
              <el-button
                v-if="scope.row.status === 'RUNNING'"
                type="warning"
                @click="pauseBot(scope.row)"
              >
                暂停
              </el-button>
              <el-button
                v-if="
                  scope.row.status === 'RUNNING' ||
                  scope.row.status === 'PAUSED'
                "
                type="danger"
                @click="stopBot(scope.row)"
              >
                停止
              </el-button>
              <el-button
                v-if="scope.row.status === 'PAUSED'"
                type="info"
                @click="resumeBot(scope.row)"
              >
                恢复
              </el-button>
              <el-button type="primary" @click="editBot(scope.row)">
                编辑
              </el-button>
              <el-dropdown v-if="scope.row.status !== 'PURCHASED'" @command="handleCommand">
                <el-button size="small">
                  更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      :command="{ action: 'view', bot: scope.row }"
                    >
                      查看详情
                    </el-dropdown-item>
                    <el-dropdown-item
                      :command="{ action: 'logs', bot: scope.row }"
                    >
                      查看日志
                    </el-dropdown-item>
                    <el-dropdown-item
                      :command="{ action: 'performance', bot: scope.row }"
                    >
                      性能分析
                    </el-dropdown-item>
                    <el-dropdown-item
                      :command="{ action: 'delete', bot: scope.row }"
                      :disabled="scope.row.status === 'RUNNING'"
                      class="danger-item"
                    >
                      删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </el-button-group>
            <span v-else style="color: #909399; font-size: 12px;">已购买，请创建交易机器人后使用</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 15, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 编辑机器人对话框 -->
    <EditTradingBot
      v-model="editDialogVisible"
      :bot-id="currentEditBotId"
      @success="handleEditSuccess"
    />

    <!-- 机器人订单收益报表 -->
    <BotPerformanceReport
      v-model="performanceReportVisible"
      :bot="performanceReportBot"
    />

    <!-- AI智能创建机器人对话框 -->
    <AiBotDialog
      v-model:visible="aiDialogVisible"
      @created="handleAiCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh, ArrowDown, Lightning } from "@element-plus/icons-vue";
import * as tradingBotApi from "@/api/robot";
import { getMyPurchases } from "@/api/communityMarket";
import EditTradingBot from "@/components/trading/EditTradingBot.vue";
import BotPerformanceReport from "@/components/trading/BotPerformanceReport.vue";
import AiBotDialog from "@/components/trading/AiBotDialog.vue";

// 接口定义
interface TradingBot {
  id: number;
  botId: string;
  botName: string;
  userId: string;
  accountId: string;
  exchange: string;
  strategyId: string;
  direction: string;
  tradingPair: string;
  allocatedCapital: number;
  currentCapital: number;
  status: string;
  startTime?: string;
  lastSignalTime?: string;
  createdAt: string;
  updatedAt: string;
}

interface Pagination {
  current: number;
  size: number;
  total: number;
}

interface FilterForm {
  botName: string;
  userId: string;
  exchange: string;
  tradingPair: string;
  status: string;
  strategyId: string;
  accountId: string;
}

interface Stats {
  total: number;
  running: number;
  paused: number;
  stopped: number;
  created: number;
  error: number;
}

// 响应式数据
const loading = ref(false);
const bots = ref<TradingBot[]>([]);
const editDialogVisible = ref(false);
const currentEditBotId = ref<string>("");
const aiDialogVisible = ref(false);
const performanceReportVisible = ref(false);
const performanceReportBot = ref<{
  botId: string;
  botName?: string;
  allocatedCapital?: number;
} | null>(null);
const stats = reactive<Stats>({
  total: 0,
  running: 0,
  paused: 0,
  stopped: 0,
  created: 0,
  error: 0,
});

const pagination = reactive<Pagination>({
  current: 1,
  size: 15,
  total: 0,
});

const filterForm = reactive<FilterForm>({
  botName: "",
  userId: "",
  exchange: "",
  tradingPair: "",
  status: "",
  strategyId: "",
  accountId: "",
});

// 生命周期
onMounted(() => {
  loadBots();
  loadStats();
});

// 方法
const loadBots = async () => {
  loading.value = true;
  try {
    const [botResponse, purchaseResponse] = await Promise.all([
      tradingBotApi.getTradingBots({
        page: pagination.current,
        limit: pagination.size,
        ...filterForm,
      }),
      getMyPurchases(),
    ]);

    if (botResponse.success) {
      bots.value = botResponse.data.records;
      pagination.total = botResponse.data.total;
    } else {
      ElMessage.error(botResponse.message || "获取机器人列表失败");
    }

    // 合并从市场购买的机器人
    if (purchaseResponse && purchaseResponse.data) {
      const purchased = (Array.isArray(purchaseResponse.data)
        ? purchaseResponse.data
        : []
      )
        .filter((p: any) => p.listingId)
        .map((p: any) => ({
          id: 0,
          botId: `purchased_${p.listingId}`,
          botName: p.botName || p.botNameZh || "-",
          userId: p.userId || "-",
          accountId: p.exchangeName || "-",
          strategyId: "-",
          tradingPair: p.symbol || "-",
          allocatedCapital: 0,
          currentCapital: 0,
          status: "PURCHASED",
          startTime: p.purchaseTime || "",
          lastSignalTime: "",
          createdAt: p.purchaseTime || new Date().toISOString(),
          updatedAt: p.purchaseTime || new Date().toISOString(),
        }));
      // 已购买的排在最前面
      bots.value = [...purchased, ...bots.value];
      pagination.total += purchased.length;
    }
  } catch (error) {
    console.error("Load bots error:", error);
    ElMessage.error("获取机器人列表失败");
  } finally {
    loading.value = false;
  }
};

const loadStats = async () => {
  try {
    const response = await tradingBotApi.getBotStatusStats();

    if (response.success) {
      Object.assign(stats, response.data);
    }
  } catch (error) {
    console.error("Load stats error:", error);
  }
};

const refreshBots = () => {
  loadBots();
  loadStats();
};

const applyFilter = () => {
  pagination.current = 1;
  loadBots();
};

const resetFilter = () => {
  Object.assign(filterForm, {
    botName: "",
    userId: "",
    exchange: "",
    tradingPair: "",
    status: "",
    strategyId: "",
    accountId: "",
  });
  pagination.current = 1;
  loadBots();
};

const handleSizeChange = (size: number) => {
  pagination.size = size;
  pagination.current = 1;
  loadBots();
};

const handleCurrentChange = (current: number) => {
  pagination.current = current;
  loadBots();
};

const getStatusTag = (status: string) => {
  switch (status) {
    case "RUNNING":
      return "success";
    case "PAUSED":
      return "warning";
    case "STOPPED":
      return "danger";
    case "ERROR":
      return "danger";
    case "PURCHASED":
      return "primary";
    default:
      return "info";
  }
};

const getStatusText = (status: string) => {
  switch (status) {
    case "CREATED":
      return "已创建";
    case "RUNNING":
      return "运行中";
    case "PAUSED":
      return "已暂停";
    case "STOPPED":
      return "已停止";
    case "ERROR":
      return "错误状态";
    case "PURCHASED":
      return "已购买";
    default:
      return status;
  }
};

const formatCurrency = (value: number) => {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "USD",
  }).format(value);
};

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString("zh-CN");
};

// 操作方法
const createBot = () => {
  currentEditBotId.value = "";
  editDialogVisible.value = true;
};

const openAiDialog = () => {
  aiDialogVisible.value = true;
};

const handleAiCreated = (data: { botName: string; botId?: string }) => {
  ElMessage.success(`AI 创建机器人成功: ${data.botName}`)
  refreshBots()
};

const viewBotDetail = (bot: TradingBot) => {
  ElMessage.info(`查看机器人详情: ${bot.botName}`);
};

const editBot = (bot: TradingBot) => {
  currentEditBotId.value = bot.botId;
  editDialogVisible.value = true;
};

const handleEditSuccess = () => {
  refreshBots();
};

const startBot = async (bot: TradingBot) => {
  try {
    // 只更新机器人状态
    const response = await tradingBotApi.startBot(bot.botId);
    if (response.success) {
      ElMessage.success("机器人启动成功");
      refreshBots();
    } else {
      ElMessage.error(response.message || "启动失败");
    }
  } catch (error) {
    console.error("Start bot error:", error);
    ElMessage.error("启动机器人失败");
  }
};

const stopBot = async (bot: TradingBot) => {
  try {
    const response = await tradingBotApi.stopBot(bot.botId);
    if (response.success) {
      ElMessage.success("机器人停止成功");
      refreshBots();
    } else {
      ElMessage.error(response.message || "停止失败");
    }
  } catch (error) {
    console.error("Stop bot error:", error);
    ElMessage.error("停止机器人失败");
  }
};

const pauseBot = async (bot: TradingBot) => {
  try {
    const response = await tradingBotApi.pauseBot(bot.botId);
    if (response.success) {
      ElMessage.success("机器人暂停成功");
      refreshBots();
    } else {
      ElMessage.error(response.message || "暂停失败");
    }
  } catch (error) {
    console.error("Pause bot error:", error);
    ElMessage.error("暂停机器人失败");
  }
};

const resumeBot = async (bot: TradingBot) => {
  try {
    const response = await tradingBotApi.resumeBot(bot.botId);
    if (response.success) {
      ElMessage.success("机器人恢复成功");
      refreshBots();
    } else {
      ElMessage.error(response.message || "恢复失败");
    }
  } catch (error) {
    console.error("Resume bot error:", error);
    ElMessage.error("恢复机器人失败");
  }
};

const handleCommand = async (command: { action: string; bot: TradingBot }) => {
  const { action, bot } = command;

  switch (action) {
    case "view":
      viewBotDetail(bot);
      break;
    case "logs":
      ElMessage.info("查看日志功能开发中");
      break;
    case "performance":
      performanceReportBot.value = {
        botId: bot.botId,
        botName: bot.botName,
        allocatedCapital: bot.allocatedCapital,
        currentCapital: bot.currentCapital,
      };
      performanceReportVisible.value = true;
      break;
    case "delete":
      try {
        await ElMessageBox.confirm(
          `确定要删除机器人 "${bot.botName}" 吗？此操作不可恢复。`,
          "确认删除",
          {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            type: "warning",
          },
        );

        const response = await tradingBotApi.deleteTradingBot(bot.botId);
        if (response.success) {
          ElMessage.success("机器人删除成功");
          refreshBots();
        } else {
          ElMessage.error(response.message || "删除失败");
        }
      } catch (error) {
        if (error !== "cancel") {
          console.error("Delete bot error:", error);
          ElMessage.error("删除机器人失败");
        }
      }
      break;
  }
};
</script>

<style scoped>
.trading-bots {
  padding: 20px;
  background: var(--primary-bg);
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: var(--primary-text);
  font-size: 24px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.filter-section {
  background: var(--secondary-bg);
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  border: 1px solid var(--border-color);
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.stats-section {
  margin-bottom: 20px;
}

.stat-card {
  background: var(--secondary-bg);
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  border: 1px solid var(--border-color);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.stat-card.running {
  border-left: 4px solid #67c23a;
}

.stat-card.paused {
  border-left: 4px solid #e6a23c;
}

.stat-card.stopped {
  border-left: 4px solid #f56c6c;
}

.stat-card.created {
  border-left: 4px solid #409eff;
}

.stat-card.error {
  border-left: 4px solid #f56c6c;
}

.stat-number {
  font-size: 24px;
  font-weight: bold;
  color: var(--primary-text);
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: var(--muted-text);
}

.bots-table {
  background: var(--secondary-bg);
  border-radius: 8px;
  border: 1px solid var(--border-color);
  overflow: hidden;
}

.capital-info {
  font-size: 12px;
  line-height: 1.4;
}

.capital-info div:first-child {
  color: var(--primary-text);
}

.capital-info div:last-child {
  color: var(--muted-text);
}

.pagination {
  padding: 20px;
  text-align: center;
  border-top: 1px solid var(--border-color);
}

.el-dropdown-menu .danger-item {
  color: #f56c6c;
}

.el-dropdown-menu .danger-item:hover {
  background-color: #fef0f0;
  color: #f56c6c;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .filter-form {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-form .el-form-item {
    margin-right: 0;
    margin-bottom: 16px;
  }

  .stats-section .el-col {
    margin-bottom: 16px;
  }
}
</style>
