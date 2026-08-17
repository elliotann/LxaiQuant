<template>
  <div class="price-signal-management">
    <div class="page-header">
      <h2>信号管理</h2>
    </div>

    <el-card>
      <!-- 查询表单 -->
      <el-form :model="queryForm" :inline="true" class="query-form">
        <el-form-item label="交易对">
          <el-select
            v-model="queryForm.symbol"
            placeholder="请选择交易对"
            clearable
            style="width: 200px"
            :loading="symbolsLoading"
          >
            <el-option
              v-for="item in symbolOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="信号类型">
          <el-select
            v-model="queryForm.signalType"
            placeholder="请选择信号类型"
            clearable
            style="width: 200px"
          >
            <el-option label="MACD" value="MACD" />
            <el-option label="布林+RSI" value="BOLL_RSI" />
            <el-option label="斐波那契通道" value="FIB_BANDS" />
            <el-option label="区间滤波器" value="RANGE_FILTER" />
            <el-option label="对数回归通道" value="LOGREG_CHANNEL_TREND" />
            <el-option label="SSL通道" value="SSL_CHANNEL" />
            <el-option label="组合信号" value="COMBINED" />
            <el-option label="平滑信号" value="SMOOTH" />
            <el-option label="AI趋势" value="AI_TREND" />
            <el-option label="AI网格" value="AI_GRID" />
            <el-option label="AI均值回归" value="AI_MEAN_REVERSION" />
            <el-option label="AI突破" value="AI_BREAKOUT" />
            <el-option label="AI剥头皮" value="AI_SCALPING" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="queryForm.startTime"
            type="datetime"
            placeholder="选择开始时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="queryForm.endTime"
            type="datetime"
            placeholder="选择结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="时间周期">
          <el-select
            v-model="queryForm.interval"
            placeholder="请选择时间周期"
            clearable
            style="width: 150px"
          >
            <el-option label="1分钟" value="OKXMIN1" />
            <el-option label="3分钟" value="OKXMIN3" />
            <el-option label="5分钟" value="OKXMIN5" />
            <el-option label="15分钟" value="OKXMIN15" />
            <el-option label="30分钟" value="OKXMIN30" />
            <el-option label="1小时" value="OKXMIN60" />
            <el-option label="4小时" value="OKXHOUR4" />
            <el-option label="1天" value="OKXDAY1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery" :loading="loading">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button
            type="success"
            @click="handleGenerateHistorySignals"
            :loading="generating"
            :disabled="!queryForm.signalType"
          >
            <el-icon><Loading v-if="generating" /><VideoPlay v-else /></el-icon>
            {{ generating ? "生成中..." : "生成历史信号" }}
          </el-button>
          <el-button
            type="danger"
            @click="handleClearSignals"
            :loading="clearing"
            :disabled="!queryForm.signalType"
          >
            <el-icon><Delete /></el-icon>
            {{ clearing ? "清除中..." : "清除信号" }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
        :default-sort="{ prop: 'klineTime', order: 'descending' }"
      >
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="symbol" label="交易对" min-width="150" />
        <el-table-column prop="indicator" label="信号类型" min-width="120" />
        <el-table-column prop="strategyName" label="信号名称" min-width="140" />
        <el-table-column
          prop="technicalDirection"
          label="信号方向"
          min-width="130"
        >
          <template #default="{ row }">
            <el-tag
              :type="getSignalDirectionTagType(row.technicalDirection)"
              size="small"
            >
              {{ formatSignalDirectionLabel(row.technicalDirection) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="signalStrength" label="信号强度" min-width="110">
          <template #default="{ row }">
            {{ row.signalStrength ? row.signalStrength.toFixed(4) : "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="entryType" label="入场类型" min-width="100">
          <template #default="{ row }">
            <el-tag
              v-if="row.entryType"
              :type="row.entryType === 'LIMIT' ? 'warning' : 'info'"
              size="small"
            >
              {{ row.entryType === 'LIMIT' ? '限价' : '市价' }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="limitPrice" label="限价单价格" min-width="120">
          <template #default="{ row }">
            {{ row.limitPrice || "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="klineTime" label="K线时间" min-width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.klineTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 底部占位，确保内容不被状态栏遮挡 -->
    <div class="bottom-spacer"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Loading, VideoPlay } from "@element-plus/icons-vue";
import {
  getTechnicalSignals,
  generateHistorySignals,
  clearSignalsByIndicator,
  type TechnicalSignal,
  type TechnicalSignalQueryParams,
  type GenerateHistorySignalsRequest,
} from "@/api/priceSignal";
import { getSupportedSymbolDetails } from "@/api/kline";
import {
  formatSignalDirectionLabel,
  getSignalDirectionTagType,
} from "@/utils/orderActionLabels";

// 查询表单
const queryForm = reactive<
  TechnicalSignalQueryParams & { interval?: string; signalType?: string }
>({
  symbol: "ETH-USDT-SWAP",
  signalType: "",
  startTime: "",
  endTime: "",
  interval: "OKXMIN3",
  pageNum: 1,
  pageSize: 15,
});

// 交易对选项（从API动态加载）
const symbolOptions = ref<{ label: string; value: string }[]>([]);
const symbolsLoading = ref(false);

const loadSymbolOptions = async () => {
  symbolsLoading.value = true;
  try {
    const res = await getSupportedSymbolDetails();
    const data = res?.data || [];
    symbolOptions.value = data.map((s: any) => ({
      label: s.name || s.symbol,
      value: s.symbol,
    }));
  } catch (error) {
    console.error("加载交易对列表失败:", error);
    symbolOptions.value = [];
  } finally {
    symbolsLoading.value = false;
  }
};

// 表格数据
const tableData = ref<TechnicalSignal[]>([]);
const loading = ref(false);

// 分页信息
const pagination = reactive({
  pageNum: 1,
  pageSize: 15,
  total: 0,
  pages: 0,
});

const generating = ref(false);
const clearing = ref(false);

// 查询数据
const handleQuery = async () => {
  loading.value = true;
  try {
    const params: TechnicalSignalQueryParams = {
      ...queryForm,
      indicator: queryForm.signalType,
      timeframe: queryForm.interval,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    };
    // 移除signalType和interval，因为已经映射到indicator和timeframe了
    delete (params as any).signalType;
    delete (params as any).interval;
    const response = await getTechnicalSignals(params);
    if (response.success) {
      tableData.value = response.data || [];
      pagination.total = response.total || 0;
      pagination.pages = response.pages || 0;
    } else {
      ElMessage.error(response.message || "查询失败");
    }
  } catch (error: any) {
    ElMessage.error(error.message || "查询失败");
  } finally {
    loading.value = false;
  }
};

// 重置查询
const handleReset = () => {
  queryForm.symbol = "ETH-USDT-SWAP";
  queryForm.signalType = "";
  queryForm.startTime = "";
  queryForm.endTime = "";
  queryForm.interval = "OKXMIN3";
  pagination.pageNum = 1;
  handleQuery();
};

// 分页大小变化
const handleSizeChange = (size: number) => {
  pagination.pageSize = size;
  pagination.pageNum = 1;
  handleQuery();
};

// 当前页变化
const handleCurrentChange = (page: number) => {
  pagination.pageNum = page;
  handleQuery();
};

// 生成历史信号（使用查询条件）
const handleGenerateHistorySignals = async () => {
  if (!queryForm.symbol) {
    ElMessage.warning("请输入交易对");
    return;
  }
  if (!queryForm.interval) {
    ElMessage.warning("请选择时间周期");
    return;
  }
  if (!queryForm.signalType) {
    ElMessage.warning("请选择信号类型");
    return;
  }
  if (!queryForm.startTime) {
    ElMessage.warning("请选择开始时间");
    return;
  }

  try {
    await ElMessageBox.confirm(
      "历史信号生成可能需要较长时间，是否继续？",
      "确认生成",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    generating.value = true;

    // 将开始时间转换为时间戳（秒）
    const startTimeStamp = Math.floor(
      new Date(queryForm.startTime).getTime() / 1000,
    );

    const request: GenerateHistorySignalsRequest = {
      symbol: queryForm.symbol,
      interval: queryForm.interval,
      strategyType: queryForm.signalType,
      startTime: startTimeStamp,
    };

    const response = await generateHistorySignals(request);
    if (response.success) {
      ElMessage.success(
        `历史信号生成完成！共生成 ${response.signalCount || 0} 个信号`,
      );
      // 生成成功后刷新列表
      handleQuery();
    } else {
      ElMessage.error(response.message || "生成失败");
    }
  } catch (error: any) {
    if (error !== "cancel") {
      ElMessage.error(error.message || "生成失败");
    }
  } finally {
    generating.value = false;
  }
};

// 清除信号
const handleClearSignals = async () => {
  if (!queryForm.signalType) {
    ElMessage.warning("请先选择信号类型");
    return;
  }

  try {
    await ElMessageBox.confirm(
      `确定要清除信号类型 "${queryForm.signalType}" 的所有技术信号吗？此操作不可恢复！`,
      "确认清除",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    clearing.value = true;
    const response = await clearSignalsByIndicator(queryForm.signalType);

    if (response.success) {
      ElMessage.success(
        `清除成功！共清除 ${response.deletedCount || 0} 条技术信号`,
      );
      // 清除成功后刷新列表
      handleQuery();
    } else {
      ElMessage.error(response.message || "清除失败");
    }
  } catch (error: any) {
    if (error !== "cancel") {
      ElMessage.error(error.message || "清除失败");
    }
  } finally {
    clearing.value = false;
  }
};

// 格式化日期时间
const formatDateTime = (dateTime: string | undefined) => {
  if (!dateTime) return "-";
  try {
    return new Date(dateTime).toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  } catch {
    return dateTime;
  }
};

// 初始化
onMounted(() => {
  loadSymbolOptions();
  handleQuery();
});
</script>

<style scoped>
.price-signal-management {
  padding: 20px;
  padding-bottom: 120px; /* 避免被底部状态栏遮挡（状态栏高度35px + 安全间距） */
  min-height: calc(100vh - 200px);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.query-form {
  margin-bottom: 20px;
}

.pagination-container {
  margin-top: 20px;
  margin-bottom: 20px;
  display: flex;
  justify-content: flex-end;
}

.bottom-spacer {
  height: 120px;
  flex-shrink: 0;
}
</style>
