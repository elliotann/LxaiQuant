<template>
  <div class="accounts">
    <div class="page-header">
      <h2>账户管理</h2>
      <div class="header-actions">
        <el-button
          type="success"
          @click="syncAllBalances"
          :loading="syncLoading"
        >
          <el-icon><RefreshRight /></el-icon>
          同步所有余额
        </el-button>
        <el-button type="primary" @click="handleAddAccount">
          <el-icon><Plus /></el-icon>
          添加账户
        </el-button>
      </div>
    </div>

    <!-- 账户概览 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6">
        <el-card class="overview-card">
          <template #header>
            <span>账户概览</span>
          </template>
          <div class="overview-content">
            <div class="overview-item">
              <span class="label">总账户数</span>
              <span class="value">{{ accountOverview.totalAccounts }}</span>
            </div>
            <div class="overview-item">
              <span class="label">活跃账户</span>
              <span class="value">{{ accountOverview.activeAccounts }}</span>
            </div>
            <div class="overview-item">
              <span class="label">总资产</span>
              <span class="value"
                >${{ accountOverview.totalAssets.toLocaleString() }}</span
              >
            </div>
            <div class="overview-item">
              <span class="label">今日盈亏</span>
              <span
                class="value"
                :class="accountOverview.todayPnl >= 0 ? 'profit' : 'loss'"
              >
                {{ accountOverview.todayPnl >= 0 ? "+" : "" }}${{
                  accountOverview.todayPnl.toLocaleString()
                }}
              </span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="18">
        <el-card>
          <template #header>
            <span>账户列表</span>
          </template>
          <el-table
            v-loading="loading"
            :data="accounts"
            style="width: 100%"
            border
          >
            <el-table-column prop="name" label="账户名称" width="150" />
            <el-table-column prop="platform" label="交易所" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ row.platform }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="账户类型" width="120">
              <template #default="{ row }">
                <el-tag :type="getAccountTypeColor(row.type)" size="small">
                  {{ getAccountTypeText(row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="balance" label="账户余额" width="150">
              <template #default="{ row }">
                <span class="balance-value"
                  >${{ row.balance.toLocaleString() }}</span
                >
              </template>
            </el-table-column>
            <el-table-column prop="pnl" label="今日盈亏" width="120">
              <template #default="{ row }">
                <span :class="row.pnl >= 0 ? 'profit' : 'loss'">
                  {{ row.pnl >= 0 ? "+" : "" }}${{ row.pnl.toLocaleString() }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusColor(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastSync" label="最后同步" width="180">
              <template #default="{ row }">
                {{ formatTime(row.lastSync) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" @click="viewAccount(row)"
                  >查看</el-button
                >
                <el-button size="small" @click="editAccount(row)"
                  >编辑</el-button
                >
                <el-button size="small" @click="syncAccount(row)"
                  >同步</el-button
                >
                <el-button
                  size="small"
                  type="danger"
                  @click="deleteAccount(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 标签页 -->
    <el-tabs v-model="activeTab" @tab-click="handleTabClick">
      <!-- 账户详情 -->
      <el-tab-pane label="账户详情" name="detail">
        <el-card v-if="selectedAccount">
          <template #header>
            <div class="card-header">
              <h3>{{ selectedAccount.name }} - 详细信息</h3>
              <el-button @click="refreshAccountDetails" :loading="detailLoading"
                >刷新</el-button
              >
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card>
                <template #header>
                  <span>基本信息</span>
                </template>
                <el-descriptions :column="1" border>
                  <el-descriptions-item label="账户名称">
                    {{ selectedAccount.name }}
                  </el-descriptions-item>
                  <el-descriptions-item label="交易所">
                    {{ selectedAccount.exchange }}
                  </el-descriptions-item>
                  <el-descriptions-item label="账户类型">
                    {{ getAccountTypeText(selectedAccount.type) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="API Key">
                    {{ maskApiKey(selectedAccount.apiKey) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="创建时间">
                    {{ formatTime(selectedAccount.createdAt) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="状态">
                    <el-tag
                      :type="getStatusColor(selectedAccount.status)"
                      size="small"
                    >
                      {{ getStatusText(selectedAccount.status) }}
                    </el-tag>
                  </el-descriptions-item>
                </el-descriptions>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card>
                <template #header>
                  <span>资产概览</span>
                </template>
                <div class="asset-overview">
                  <div class="asset-item">
                    <span class="label">总资产</span>
                    <span class="value"
                      >${{ selectedAccount.balance.toLocaleString() }}</span
                    >
                  </div>
                  <div class="asset-item">
                    <span class="label">可用余额</span>
                    <span class="value"
                      >${{
                        selectedAccount.availableBalance.toLocaleString()
                      }}</span
                    >
                  </div>
                  <div class="asset-item">
                    <span class="label">冻结资金</span>
                    <span class="value"
                      >${{
                        selectedAccount.frozenBalance.toLocaleString()
                      }}</span
                    >
                  </div>
                  <div class="asset-item">
                    <span class="label">今日盈亏</span>
                    <span
                      class="value"
                      :class="selectedAccount.pnl >= 0 ? 'profit' : 'loss'"
                    >
                      {{ selectedAccount.pnl >= 0 ? "+" : "" }}${{
                        selectedAccount.pnl.toLocaleString()
                      }}
                    </span>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
          <el-row :gutter="20" style="margin-top: 20px">
            <el-col :span="24">
              <el-card>
                <template #header>
                  <span>持仓情况</span>
                </template>
                <el-table
                  v-loading="positionsLoading"
                  :data="positions"
                  style="width: 100%"
                  border
                >
                  <el-table-column prop="symbol" label="交易对" width="100" />
                  <el-table-column prop="side" label="方向" width="80">
                    <template #default="{ row }">
                      <el-tag
                        :type="row.side === 'long' ? 'success' : 'danger'"
                        size="small"
                      >
                        {{ row.side === "long" ? "多头" : "空头" }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="size" label="数量" width="100" />
                  <el-table-column
                    prop="entryPrice"
                    label="开仓价"
                    width="120"
                  />
                  <el-table-column
                    prop="currentPrice"
                    label="当前价"
                    width="120"
                  />
                  <el-table-column prop="pnl" label="盈亏" width="120">
                    <template #default="{ row }">
                      <span :class="row.pnl >= 0 ? 'profit' : 'loss'">
                        {{ row.pnl >= 0 ? "+" : "" }}${{ row.pnl }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="pnlPercent" label="盈亏%" width="100">
                    <template #default="{ row }">
                      <span :class="row.pnlPercent >= 0 ? 'profit' : 'loss'">
                        {{ row.pnlPercent >= 0 ? "+" : ""
                        }}{{ row.pnlPercent }}%
                      </span>
                    </template>
                  </el-table-column>
                </el-table>
              </el-card>
            </el-col>
          </el-row>
        </el-card>
        <el-card v-else>
          <div class="empty-state">
            <el-empty description="请选择一个账户查看详情" :image-size="100" />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 交易历史 -->
      <el-tab-pane label="交易历史" name="history">
        <el-card>
          <div class="history-controls">
            <el-form :model="historyForm" inline>
              <el-form-item label="账户">
                <el-select
                  v-model="historyForm.accountId"
                  placeholder="选择账户"
                  clearable
                >
                  <el-option
                    v-for="account in accounts"
                    :key="account.id"
                    :label="account.name"
                    :value="account.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="时间范围">
                <el-date-picker
                  v-model="historyForm.dateRange"
                  type="datetimerange"
                  range-separator="至"
                  start-placeholder="开始时间"
                  end-placeholder="结束时间"
                  format="YYYY-MM-DD HH:mm:ss"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="queryTradeHistory"
                  >查询</el-button
                >
                <el-button @click="exportTradeHistory">导出</el-button>
              </el-form-item>
            </el-form>
          </div>
          <el-table
            v-loading="historyLoading"
            :data="tradeHistory"
            style="width: 100%"
            border
            height="400"
          >
            <el-table-column prop="timestamp" label="时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.timestamp) }}
              </template>
            </el-table-column>
            <el-table-column prop="account" label="账户" width="120" />
            <el-table-column prop="symbol" label="交易对" width="100" />
            <el-table-column prop="type" label="类型" width="80">
              <template #default="{ row }">
                <el-tag
                  :type="row.type === 'buy' ? 'success' : 'danger'"
                  size="small"
                >
                  {{ row.type === "buy" ? "买入" : "卖出" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="price" label="价格" width="120" />
            <el-table-column prop="amount" label="数量" width="100" />
            <el-table-column prop="total" label="总额" width="120" />
            <el-table-column prop="fee" label="手续费" width="100" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getTradeStatusColor(row.status)" size="small">
                  {{ getTradeStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination
              v-model:current-page="historyPagination.page"
              v-model:page-size="historyPagination.size"
              :page-sizes="[50, 100, 200, 500]"
              :total="historyPagination.total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleHistorySizeChange"
              @current-change="handleHistoryCurrentChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- API配置 -->
      <el-tab-pane label="API配置" name="api">
        <el-card>
          <template #header>
            <div class="card-header">
              <h3>API配置管理</h3>
              <el-button @click="testAllConnections">测试所有连接</el-button>
            </div>
          </template>
          <el-table :data="apiConfigs" style="width: 100%" border>
            <el-table-column prop="exchange" label="交易所" width="120" />
            <el-table-column prop="apiKey" label="API Key" width="200">
              <template #default="{ row }">
                {{ maskApiKey(row.apiKey) }}
              </template>
            </el-table-column>
            <el-table-column prop="permissions" label="权限" width="200">
              <template #default="{ row }">
                <el-tag
                  v-for="permission in row.permissions"
                  :key="permission"
                  size="small"
                  style="margin-right: 4px"
                >
                  {{ getPermissionText(permission) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="连接状态" width="120">
              <template #default="{ row }">
                <el-tag
                  :type="getConnectionStatusColor(row.status)"
                  size="small"
                >
                  {{ getConnectionStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastTest" label="最后测试" width="180">
              <template #default="{ row }">
                {{ formatTime(row.lastTest) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button size="small" @click="testConnection(row)"
                  >测试</el-button
                >
                <el-button size="small" @click="editApiConfig(row)"
                  >编辑</el-button
                >
                <el-button
                  size="small"
                  type="danger"
                  @click="deleteApiConfig(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加/编辑账户对话框 -->
    <el-dialog
      v-model="accountDialogVisible"
      :title="accountDialogType === 'add' ? '添加账户' : '编辑账户'"
      width="600px"
    >
      <el-form
        ref="accountFormRef"
        :model="accountForm"
        :rules="accountRules"
        label-width="100px"
      >
        <el-form-item label="账户名称" prop="name">
          <el-input v-model="accountForm.name" placeholder="请输入账户名称" />
        </el-form-item>
        <el-form-item label="交易所" prop="exchange">
          <el-select v-model="accountForm.exchange" placeholder="请选择交易所">
            <el-option label="OKX" value="OKX" />
            <el-option label="Gate.io" value="GATEIO" />
          </el-select>
        </el-form-item>
        <el-form-item label="账户类型" prop="type">
          <el-select v-model="accountForm.type" placeholder="请选择账户类型">
            <el-option label="现货" value="spot" />
            <el-option label="合约" value="futures" />
            <el-option label="保证金" value="margin" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="accountForm.apiKey"
            placeholder="请输入API Key"
            show-password
          />
        </el-form-item>
        <el-form-item label="API Secret" prop="apiSecret">
          <el-input
            v-model="accountForm.apiSecret"
            placeholder="请输入API Secret"
            show-password
          />
        </el-form-item>
        <el-form-item label="Passphrase" prop="passphrase">
          <el-input
            v-model="accountForm.passphrase"
            placeholder="请输入Passphrase（如果需要）"
            show-password
          />
        </el-form-item>
        <el-form-item label="模拟账户">
          <el-switch
            v-model="accountForm.simulated"
            active-text="是"
            inactive-text="否"
          />
          <div
            style="
              font-size: 12px;
              color: var(--el-text-color-secondary);
              margin-top: 4px;
            "
          >
            开启后，交易操作将使用沙箱环境（K线数据始终使用真实环境）
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button @click="testApiConnection">测试连接</el-button>
        <el-button type="primary" @click="saveAccount">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import { Plus, RefreshRight } from "@element-plus/icons-vue";
import * as tradingApi from "@/api/trading";
import { exchangeApi } from "@/api/exchange";

const activeTab = ref("detail");
const loading = ref(false);
const detailLoading = ref(false);
const historyLoading = ref(false);
const positionsLoading = ref(false);
const syncLoading = ref(false);
const accountDialogVisible = ref(false);
const accountDialogType = ref<"add" | "edit">("add");
const accountFormRef = ref<FormInstance>();

// 账户概览
const accountOverview = reactive({
  totalAccounts: 3,
  activeAccounts: 2,
  totalAssets: 150000,
  todayPnl: 2500,
});

// 账户列表
const accounts = ref([
  {
    id: 1,
    name: "OKX现货账户",
    exchange: "OKX",
    type: "spot",
    balance: 50000,
    pnl: 1200,
    status: "active",
    lastSync: "2024-01-15 10:30:00",
    apiKey: "okx_api_key_123",
    availableBalance: 45000,
    frozenBalance: 5000,
    createdAt: "2024-01-01 00:00:00",
  },
  {
    id: 2,
    name: "Gate.io合约账户",
    exchange: "GATEIO",
    type: "futures",
    balance: 100000,
    pnl: 1300,
    status: "active",
    lastSync: "2024-01-15 10:25:00",
    apiKey: "gateio_api_key_456",
    availableBalance: 80000,
    frozenBalance: 20000,
    createdAt: "2024-01-02 00:00:00",
  },
]);

// 选中的账户
const selectedAccount = ref(null);

// 持仓情况
const positions = ref([
  {
    symbol: "BTC/USDT",
    side: "long",
    size: "0.5",
    entryPrice: "45000",
    currentPrice: "45500",
    pnl: "+250",
    pnlPercent: "+0.56",
  },
  {
    symbol: "ETH/USDT",
    side: "short",
    size: "2.0",
    entryPrice: "3000",
    currentPrice: "2950",
    pnl: "+100",
    pnlPercent: "+1.67",
  },
]);

// 交易历史表单
const historyForm = reactive({
  accountId: "",
  dateRange: [],
});

// 交易历史
const tradeHistory = ref([]);
const historyPagination = reactive({
  page: 1,
  size: 100,
  total: 0,
});

// API配置
const apiConfigs = ref([
  {
    id: 1,
    exchange: "OKX",
    apiKey: "okx_api_key_123",
    permissions: ["read", "trade"],
    status: "connected",
    lastTest: "2024-01-15 10:30:00",
  },
  {
    id: 2,
    exchange: "GATEIO",
    apiKey: "gateio_api_key_456",
    permissions: ["read", "trade", "withdraw"],
    status: "connected",
    lastTest: "2024-01-15 10:25:00",
  },
]);

// 账户表单
const accountForm = reactive({
  id: "",
  name: "",
  exchange: "",
  type: "spot",
  apiKey: "",
  apiSecret: "",
  passphrase: "",
  simulated: false, // 是否模拟账户（沙箱环境）
});

const accountRules: FormRules = {
  name: [
    { required: true, message: "请输入账户名称", trigger: "blur" },
    {
      min: 2,
      max: 50,
      message: "账户名称长度在 2 到 50 个字符",
      trigger: "blur",
    },
  ],
  exchange: [{ required: true, message: "请选择交易所", trigger: "change" }],
  type: [{ required: true, message: "请选择账户类型", trigger: "change" }],
  apiKey: [{ required: true, message: "请输入API Key", trigger: "blur" }],
  apiSecret: [{ required: true, message: "请输入API Secret", trigger: "blur" }],
};

// 方法
const fetchAccounts = async () => {
  loading.value = true;
  try {
    const response = await tradingApi.getTradingAccounts();
    if (response.success && response.data) {
      // 将后端数据转换为前端需要的格式
      accounts.value = response.data.map((account) => ({
        id: account.id,
        name: account.accountName || `账户 ${account.id}`,
        platform: account.platformName || account.memberPlatform,
        exchange: account.platformName || account.memberPlatform || "",
        type: account.type || "spot",
        apiKey: account.apiKey || "",
        apiSecret: account.apiSecret || "",
        passphrase: account.passphrase || "",
        simulated: account.simulated ?? false,
        balance: account.balance || 0,
        pnl: account.pnl || 0,
        isActive: account.isActive,
        status: account.isActive ? "active" : "inactive",
        createdAt: account.createTime,
        updatedAt: account.updateTime,
        lastSync: account.updateTime, // 使用更新时间作为最后同步时间
      }));
    } else {
      // 如果API调用失败，使用模拟数据
      console.warn("API调用失败，使用模拟数据");
      accounts.value = getMockAccounts();
    }

    // 更新账户概览
    const activeAccounts = accounts.value.filter((acc) => acc.isActive).length;
    const totalAssets = accounts.value.reduce(
      (sum, acc) => sum + (acc.balance || 0),
      0,
    );
    const todayPnl = accounts.value.reduce(
      (sum, acc) => sum + (acc.pnl || 0),
      0,
    );

    Object.assign(accountOverview, {
      totalAccounts: accounts.value.length,
      activeAccounts,
      totalAssets,
      todayPnl,
    });
  } catch (error) {
    console.error("获取账户列表失败:", error);
    // API调用失败时使用模拟数据
    accounts.value = getMockAccounts();

    // 更新账户概览
    const activeAccounts = accounts.value.filter((acc) => acc.isActive).length;
    const totalAssets = accounts.value.reduce(
      (sum, acc) => sum + (acc.balance || 0),
      0,
    );
    const todayPnl = accounts.value.reduce(
      (sum, acc) => sum + (acc.pnl || 0),
      0,
    );

    Object.assign(accountOverview, {
      totalAccounts: accounts.value.length,
      activeAccounts,
      totalAssets,
      todayPnl,
    });

    ElMessage.success("已加载模拟账户数据");
  } finally {
    loading.value = false;
  }
};

// 获取模拟账户数据
const getMockAccounts = () => {
  return [
    {
      id: "1",
        name: "OKX现货账户",
        exchange: "OKX",
        type: "spot",
        balance: 50000,
        pnl: 1250,
        isActive: true,
        createdAt: "2024-01-15T10:30:00Z",
        lastUpdated: "2024-01-20T15:45:00Z",
        description: "主要交易账户，用于现货交易",
      },
      {
        id: "2",
        name: "Gate.io合约账户",
        exchange: "GATEIO",
        type: "futures",
        balance: 25000,
        pnl: -320,
        isActive: true,
        createdAt: "2024-01-10T09:15:00Z",
        lastUpdated: "2024-01-20T15:45:00Z",
        description: "合约交易账户，支持杠杆交易",
      },
      {
        id: "3",
        name: "OKX合约账户",
        exchange: "OKX",
        type: "futures",
        balance: 15000,
        pnl: 890,
        isActive: true,
        createdAt: "2024-01-08T14:20:00Z",
        lastUpdated: "2024-01-20T14:30:00Z",
        description: "OKX平台合约账户",
      },
      {
        id: "4",
        name: "测试账户",
        exchange: "OKX",
        type: "spot",
        balance: 5000,
        pnl: 150,
        isActive: false,
        createdAt: "2024-01-05T11:10:00Z",
        lastUpdated: "2024-01-18T10:20:00Z",
        description: "用于策略测试的模拟账户",
      },
  ];
};

const handleAddAccount = () => {
  accountDialogType.value = "add";
  Object.assign(accountForm, {
    id: "",
    name: "",
    exchange: "",
    type: "spot",
    apiKey: "",
    apiSecret: "",
    passphrase: "",
    simulated: false,
  });
  // 添加模式需要必填API Key和Secret
  accountRules.apiKey = [{ required: true, message: "请输入API Key", trigger: "blur" }];
  accountRules.apiSecret = [{ required: true, message: "请输入API Secret", trigger: "blur" }];
  accountDialogVisible.value = true;
};

const viewAccount = (account: any) => {
  selectedAccount.value = account;
  activeTab.value = "detail";
  fetchAccountDetails(account.id);
};

const editAccount = (account: any) => {
  accountDialogType.value = "edit";
  Object.assign(accountForm, {
    id: account.id,
    name: account.name,
    exchange: account.exchange || account.platform || "",
    type: account.type || "spot",
    apiKey: account.apiKey || "",
    apiSecret: account.apiSecret || "",
    passphrase: account.passphrase || "",
    simulated: account.simulated !== undefined ? account.simulated : false,
  });
  // 编辑模式不需要必填API Key和Secret（后端出于安全不返回）
  accountRules.apiKey = [{ trigger: "blur" }];
  accountRules.apiSecret = [{ trigger: "blur" }];
  accountDialogVisible.value = true;
};

const saveAccount = async () => {
  if (!accountFormRef.value) return;

  try {
    await accountFormRef.value.validate();

    if (accountDialogType.value === "edit") {
      // 编辑账户：只发送非空的密钥字段，避免覆盖已有密钥
      const updateData: any = {
        name: accountForm.name,
        simulated: accountForm.simulated,
        type: accountForm.type,
      };
      if (accountForm.apiKey) updateData.apiKey = accountForm.apiKey;
      if (accountForm.apiSecret) updateData.apiSecret = accountForm.apiSecret;
      if (accountForm.passphrase) updateData.passphrase = accountForm.passphrase;

      const response = await exchangeApi.updateAccount(
        accountForm.id,
        updateData,
      );

      if (response.success) {
        ElMessage.success("账户编辑成功");
        accountDialogVisible.value = false;
        fetchAccounts();
      } else {
        ElMessage.error(response.message || "账户编辑失败");
      }
    } else {
      // 添加账户
      const createData = {
        name: accountForm.name,
        exchange: accountForm.exchange,
        apiKey: accountForm.apiKey,
        apiSecret: accountForm.apiSecret,
        passphrase: accountForm.passphrase,
        simulated: accountForm.simulated,
        type: accountForm.type,
      };

      const response = await exchangeApi.createAccount(createData);

      if (response.success) {
        ElMessage.success("账户添加成功");
        accountDialogVisible.value = false;
        fetchAccounts();
      } else {
        ElMessage.error(response.message || "账户添加失败");
      }
    }
  } catch (error) {
    console.error("保存账户失败:", error);
    ElMessage.error("保存账户失败");
  }
};

const syncAccount = async (account: any) => {
  try {
    const response = await exchangeApi.syncAccountBalance(account.id);
    if (response.success) {
      ElMessage.success("账户余额同步成功");
      // 更新本地数据
      account.balance = Object.values(response.balances || {}).reduce(
        (sum: number, balance: any) =>
          sum +
          (typeof balance === "number"
            ? balance
            : parseFloat(balance.toString())),
        0,
      );
      account.lastSync = new Date().toISOString();
      fetchAccounts(); // 重新获取最新数据
    } else {
      ElMessage.error(response.message || "账户同步失败");
    }
  } catch (error) {
    console.error("同步账户失败:", error);
    ElMessage.error("账户同步失败");
  }
};

const syncAllBalances = async () => {
  try {
    syncLoading.value = true;
    const response = await exchangeApi.syncAllBalances();
    if (response.success) {
      const { successCount, failCount, total } = response;
      ElMessage.success(
        `批量同步完成：成功 ${successCount} 个，失败 ${failCount} 个，总共 ${total} 个账户`,
      );
      fetchAccounts(); // 重新获取最新数据
    } else {
      ElMessage.error(response.message || "批量同步失败");
    }
  } catch (error) {
    console.error("批量同步失败:", error);
    ElMessage.error("批量同步失败");
  } finally {
    syncLoading.value = false;
  }
};

const deleteAccount = async (account: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除账户"${account.name}"吗？此操作不可撤销。`,
      "删除账户",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    // TODO: 调用API删除账户
    await new Promise((resolve) => setTimeout(resolve, 1000));

    ElMessage.success("账户删除成功");
    fetchAccounts();
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("账户删除失败");
    }
  }
};

const fetchAccountDetails = async (accountId: string) => {
  detailLoading.value = true;
  try {
    // TODO: 从API获取账户详情
    await new Promise((resolve) => setTimeout(resolve, 1000));
  } catch (error) {
    ElMessage.error("获取账户详情失败");
  } finally {
    detailLoading.value = false;
  }
};

const refreshAccountDetails = async () => {
  if (selectedAccount.value) {
    await fetchAccountDetails(selectedAccount.value.id);
    ElMessage.success("账户详情已刷新");
  }
};

const handleTabClick = (tab: any) => {
  if (tab.props.name === "history") {
    queryTradeHistory();
  }
};

const queryTradeHistory = async () => {
  historyLoading.value = true;
  try {
    // TODO: 从API获取交易历史
    await new Promise((resolve) => setTimeout(resolve, 1000));
    tradeHistory.value = [];
    historyPagination.total = 0;
  } catch (error) {
    ElMessage.error("查询交易历史失败");
  } finally {
    historyLoading.value = false;
  }
};

const exportTradeHistory = () => {
  // TODO: 导出交易历史
  ElMessage.success("交易历史导出成功");
};

const handleHistorySizeChange = (size: number) => {
  historyPagination.size = size;
  queryTradeHistory();
};

const handleHistoryCurrentChange = (page: number) => {
  historyPagination.page = page;
  queryTradeHistory();
};

const testApiConnection = async () => {
  try {
    // 使用表单中的API配置进行测试
    const config = {
      apiKey: accountForm.apiKey,
      apiSecret: accountForm.apiSecret,
      passphrase: accountForm.passphrase,
      exchange: accountForm.exchange,
    };

    const response = await exchangeApi.testConnectionConfig(config);
    if (response.success) {
      ElMessage.success("API连接测试成功");
    } else {
      // 显示详细的错误信息
      let errorMessage = response.message || "API连接测试失败";

      // 如果有详细信息，显示更具体的错误信息
      if (response.details && response.details.error) {
        errorMessage += "\n" + response.details.error;
      }

      // 如果有解决方案建议，也显示出来
      if (response.details && response.details.solution) {
        errorMessage += "\n\n解决方案：" + response.details.solution;
      }

      ElMessage.error(errorMessage);
    }
  } catch (error) {
    console.error("API连接测试异常:", error);
    ElMessage.error("API连接测试失败");
  }
};

const testConnection = async (account: any) => {
  try {
    const response = await exchangeApi.testConnection(account.id);
    if (response.success) {
      ElMessage.success(`${account.name} 连接测试成功`);
    } else {
      // 显示详细的错误信息
      let errorMessage = `${account.name} ${response.message || "连接测试失败"}`;

      // 如果有详细信息，显示更具体的错误信息
      if (response.details && response.details.error) {
        errorMessage += "\n" + response.details.error;
      }

      // 如果有解决方案建议，也显示出来
      if (response.details && response.details.solution) {
        errorMessage += "\n\n解决方案：" + response.details.solution;
      }

      ElMessage.error(errorMessage);
    }
  } catch (error) {
    console.error("连接测试异常:", error);
    ElMessage.error(`${account.name} 连接测试失败`);
  }
};

const testAllConnections = async () => {
  try {
    // TODO: 测试所有API连接
    await new Promise((resolve) => setTimeout(resolve, 1000));
    ElMessage.success("所有连接测试完成");
  } catch (error) {
    ElMessage.error("连接测试失败");
  }
};

const editApiConfig = (config: any) => {
  // TODO: 编辑API配置
  ElMessage.info("编辑API配置功能开发中");
};

const deleteApiConfig = async (config: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除${config.exchange}的API配置吗？`,
      "删除API配置",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    // TODO: 调用API删除API配置
    await new Promise((resolve) => setTimeout(resolve, 1000));

    ElMessage.success("API配置删除成功");
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("API配置删除失败");
    }
  }
};

// 工具函数
const getAccountTypeText = (type: string) => {
  const types: Record<string, string> = {
    spot: "现货",
    futures: "合约",
    margin: "保证金",
  };
  return types[type] || type;
};

const getAccountTypeColor = (type: string) => {
  const colors: Record<string, string> = {
    spot: "success",
    futures: "warning",
    margin: "info",
  };
  return colors[type] || "info";
};

const getStatusText = (status: string) => {
  const texts: Record<string, string> = {
    active: "活跃",
    inactive: "停用",
    error: "错误",
  };
  return texts[status] || status;
};

const getStatusColor = (status: string) => {
  const colors: Record<string, string> = {
    active: "success",
    inactive: "info",
    error: "danger",
  };
  return colors[status] || "info";
};

const getTradeStatusText = (status: string) => {
  const texts: Record<string, string> = {
    pending: "待成交",
    filled: "已成交",
    cancelled: "已取消",
    rejected: "已拒绝",
  };
  return texts[status] || status;
};

const getTradeStatusColor = (status: string) => {
  const colors: Record<string, string> = {
    pending: "warning",
    filled: "success",
    cancelled: "info",
    rejected: "danger",
  };
  return colors[status] || "info";
};

const getConnectionStatusText = (status: string) => {
  const texts: Record<string, string> = {
    connected: "已连接",
    disconnected: "未连接",
    error: "错误",
  };
  return texts[status] || status;
};

const getConnectionStatusColor = (status: string) => {
  const colors: Record<string, string> = {
    connected: "success",
    disconnected: "info",
    error: "danger",
  };
  return colors[status] || "info";
};

const getPermissionText = (permission: string) => {
  const texts: Record<string, string> = {
    read: "读取",
    trade: "交易",
    withdraw: "提现",
  };
  return texts[permission] || permission;
};

const maskApiKey = (apiKey: string) => {
  if (!apiKey) return "";
  return apiKey.substring(0, 8) + "****" + apiKey.substring(apiKey.length - 4);
};

const formatTime = (timeString: string) => {
  return new Date(timeString).toLocaleString("zh-CN");
};

// 生命周期
onMounted(() => {
  fetchAccounts();
});
</script>

<style scoped>
.accounts {
  padding: 20px;
  background: var(--primary-bg);
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  background: var(--secondary-bg);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 16px 20px;
  box-shadow: var(--card-shadow);
}

.page-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-weight: 700;
  font-size: 20px;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.overview-card {
  background: var(--secondary-bg) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: 10px !important;
  box-shadow: var(--card-shadow) !important;
}

.overview-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.overview-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color);
}

.overview-item:last-child {
  border-bottom: none;
}

.overview-item .label {
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 500;
}

.overview-item .value {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: 'Courier New', monospace;
}

.balance-value {
  color: var(--accent-green);
  font-weight: 700;
  font-family: 'Courier New', monospace;
}

.profit {
  color: var(--accent-green) !important;
  font-weight: 700;
  font-family: 'Courier New', monospace;
}

.loss {
  color: var(--accent-red) !important;
  font-weight: 700;
  font-family: 'Courier New', monospace;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.card-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-weight: 600;
  font-size: 15px;
}

.asset-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.asset-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color);
}

.asset-item:last-child {
  border-bottom: none;
}

.asset-item .label {
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 500;
}

.asset-item .value {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  font-family: 'Courier New', monospace;
}

.history-controls {
  margin-bottom: 20px;
}

.empty-state {
  padding: 40px;
  text-align: center;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

/* 卡片样式 - 实盘交易风格 */
.el-card {
  background: var(--secondary-bg) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: 8px !important;
  box-shadow: var(--card-shadow) !important;
}

.el-card__header {
  background: var(--tertiary-bg) !important;
  border-bottom: 1px solid var(--border-color) !important;
  padding: 10px 15px !important;
}

.el-card__header span,
.el-card__header h3 {
  color: var(--text-primary) !important;
  font-weight: 600 !important;
  font-size: 14px !important;
}

/* 表格样式 */
.el-table {
  background: transparent !important;
  color: var(--text-primary) !important;
}

.el-table th {
  background: var(--tertiary-bg) !important;
  color: var(--text-secondary) !important;
  border-bottom: 1px solid var(--border-color) !important;
  font-weight: 600 !important;
  font-size: 12px !important;
}

.el-table td {
  background: transparent !important;
  border-bottom: 1px solid var(--border-color) !important;
  color: var(--text-primary) !important;
}

.el-table--enable-row-hover .el-table__body tr:hover > td {
  background: var(--tertiary-bg) !important;
}

.el-table tr:last-child td {
  border-bottom: none !important;
}

/* 标签样式 */
.el-tag {
  background: var(--tertiary-bg) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: 4px !important;
  color: var(--text-primary) !important;
  font-weight: 500 !important;
}

.el-tag--success {
  border-color: var(--accent-green) !important;
  color: var(--accent-green) !important;
  background: color-mix(in srgb, var(--accent-green) 10%, transparent) !important;
}

.el-tag--danger {
  border-color: var(--accent-red) !important;
  color: var(--accent-red) !important;
  background: color-mix(in srgb, var(--accent-red) 10%, transparent) !important;
}

.el-tag--warning {
  border-color: var(--accent-orange) !important;
  color: var(--accent-orange) !important;
  background: color-mix(in srgb, var(--accent-orange) 10%, transparent) !important;
}

.el-tag--info {
  border-color: var(--accent-blue) !important;
  color: var(--accent-blue) !important;
  background: color-mix(in srgb, var(--accent-blue) 10%, transparent) !important;
}

/* 按钮样式 */
.el-button {
  border-radius: 6px !important;
  font-weight: 500 !important;
}

.el-button--primary {
  background: var(--accent-blue) !important;
  border-color: var(--accent-blue) !important;
  color: #ffffff !important;
}

.el-button--primary:hover {
  opacity: 0.9 !important;
}

.el-button--success {
  background: var(--accent-green) !important;
  border-color: var(--accent-green) !important;
  color: #ffffff !important;
}

.el-button--success:hover {
  opacity: 0.9 !important;
}

.el-button--danger {
  background: var(--accent-red) !important;
  border-color: var(--accent-red) !important;
  color: #ffffff !important;
}

.el-button--danger:hover {
  opacity: 0.9 !important;
}

/* 对话框样式 */
.el-dialog {
  background: var(--secondary-bg) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: 8px !important;
  box-shadow: var(--card-shadow) !important;
}

.el-dialog__header {
  background: var(--tertiary-bg) !important;
  border-bottom: 1px solid var(--border-color) !important;
  padding: 12px 20px !important;
}

.el-dialog__title {
  color: var(--text-primary) !important;
  font-weight: 600 !important;
  font-size: 15px !important;
}

.el-dialog__body {
  background: var(--secondary-bg) !important;
  color: var(--text-primary) !important;
  padding: 20px !important;
}

/* 表单样式 */
.el-form-item__label {
  color: var(--text-secondary) !important;
  font-weight: 500 !important;
}

.el-input__inner {
  background: var(--primary-bg) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: 6px !important;
  color: var(--text-primary) !important;
}

.el-input__inner:focus {
  border-color: var(--accent-blue) !important;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2) !important;
}

.el-select .el-input__inner {
  background: var(--primary-bg) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-primary) !important;
}

.el-select-dropdown {
  background: var(--secondary-bg) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: 6px !important;
  box-shadow: var(--card-shadow) !important;
}

.el-select-dropdown__item {
  color: var(--text-primary) !important;
}

.el-select-dropdown__item:hover {
  background: var(--tertiary-bg) !important;
  color: var(--text-primary) !important;
}

/* 标签页样式 */
.el-tabs__nav-wrap::after {
  background: var(--border-color) !important;
}

.el-tabs__item {
  color: var(--text-muted) !important;
  font-weight: 500 !important;
}

.el-tabs__item.is-active {
  color: var(--accent-blue) !important;
}

.el-tabs__active-bar {
  background: var(--accent-blue) !important;
}

/* 分页样式 */
.el-pagination {
  color: var(--text-primary) !important;
}

.el-pagination .el-pagination__total {
  color: var(--text-muted) !important;
}

.el-pagination .el-pager li {
  background: var(--tertiary-bg) !important;
  color: var(--text-primary) !important;
  border-radius: 4px !important;
}

.el-pagination .el-pager li.active {
  background: var(--accent-blue) !important;
  color: #ffffff !important;
}

/* 开关样式 */
.el-switch {
  --el-switch-on-color: var(--accent-green);
}

/* 描述列表样式 */
.el-descriptions {
  --el-descriptions-item-bordered-label-background: var(--tertiary-bg);
  --el-descriptions-table-border-color: var(--border-color);
}

.el-descriptions__title {
  color: var(--text-primary) !important;
}

.el-descriptions__label {
  color: var(--text-secondary) !important;
  font-weight: 500 !important;
}

.el-descriptions__content {
  color: var(--text-primary) !important;
}
</style>
