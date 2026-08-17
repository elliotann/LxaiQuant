<template>
  <div class="orders-management">
    <div class="page-header">
      <h2>订单管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="refreshOrders">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button @click="exportOrders">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </div>
    </div>

    <!-- 筛选区域 -->
    <div class="filter-section">
      <el-form :model="filterForm" inline class="filter-form">
        <el-form-item label="交易对">
          <el-select
            v-model="filterForm.symbol"
            placeholder="选择交易对"
            style="width: 180px"
            clearable
          >
            <el-option label="ETH永续" value="ETH-USDT-SWAP" />
            <el-option label="BTC永续" value="BTC-USDT-SWAP" />
            <el-option label="BTC/USDT" value="BTC-USDT" />
            <el-option label="ETH/USDT" value="ETH-USDT" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单号">
          <el-input
            v-model="filterForm.orderSn"
            placeholder="输入订单号"
            style="width: 220px"
            clearable
          />
        </el-form-item>
        <el-form-item label="订单状态">
          <el-select
            v-model="filterForm.status"
            placeholder="选择状态"
            style="width: 120px"
            clearable
          >
            <el-option label="全部" value="" />
            <el-option label="待成交" value="PENDING" />
            <el-option label="持仓中" value="DEAL" />
            <el-option label="平仓中" value="CLOSING" />
            <el-option label="已平仓" value="CLOSE" />
            <el-option label="已取消" value="CANCEL" />
            <el-option label="已止损" value="LOSS" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单类型">
          <el-select
            v-model="filterForm.type"
            placeholder="选择类型"
            style="width: 120px"
            clearable
          >
            <el-option label="全部" value="" />
            <el-option label="限价单" value="limit" />
            <el-option label="市价单" value="market" />
            <el-option label="止损单" value="stop" />
            <el-option label="止盈单" value="take_profit" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 订单列表 -->
    <div class="orders-table">
      <el-table :data="filteredOrders" style="width: 100%" v-loading="loading">
        <el-table-column prop="orderSn" label="订单号" width="180" />
        <el-table-column prop="symbol" label="交易对" width="100" />
        <el-table-column prop="type" label="类型" width="80">
          <template #default="scope">
            <el-tag :type="getOrderTypeTag(scope.row.type)" size="small">
              {{ getOrderTypeText(scope.row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="side" label="方向" width="80">
          <template #default="scope">
            <span
              :class="scope.row.side === 'buy' ? 'buy-color' : 'sell-color'"
            >
              {{ scope.row.side === "buy" ? "买入" : "卖出" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="averagePrice" label="均价" width="100">
          <template #default="scope">
            {{ scope.row.averagePrice != null ? scope.row.averagePrice.toFixed(4) : "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="price" label="开仓价" width="100">
          <template #default="scope">
            {{
              scope.row.type === "market" ? "市价" : scope.row.price.toFixed(4)
            }}
          </template>
        </el-table-column>
        <el-table-column prop="closePrice" label="平仓价" width="100">
          <template #default="scope">
            {{ scope.row.closePrice ? scope.row.closePrice.toFixed(4) : "-" }}
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column prop="executedQuantity" label="已成交" width="100" />
        <el-table-column prop="profit" label="盈利" width="120">
          <template #default="scope">
            <span
              :style="{
                color:
                  scope.row.profit != null && scope.row.profit > 0
                    ? '#67c23a'
                    : scope.row.profit != null && scope.row.profit < 0
                      ? '#f56c6c'
                      : '',
              }"
            >
              {{ scope.row.profit != null ? scope.row.profit.toFixed(4) : "-" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="profitPercent" label="收益率(%)" width="120">
          <template #default="scope">
            <span
              :style="{
                color:
                  scope.row.status !== 'DEAL' &&
                  scope.row.profitPercent != null &&
                  scope.row.profitPercent > 0
                    ? '#67c23a'
                    : scope.row.status !== 'DEAL' &&
                        scope.row.profitPercent != null &&
                        scope.row.profitPercent < 0
                      ? '#f56c6c'
                      : '',
              }"
            >
              {{
                scope.row.status !== "DEAL" && scope.row.profitPercent != null
                  ? scope.row.profitPercent.toFixed(2)
                  : "-"
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getOrderStatusTag(scope.row.status)" size="small">
              {{ getOrderStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openTime" label="开仓时间" width="160">
          <template #default="scope">
            {{ formatTime(scope.row.openTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="closeTime" label="平仓时间" width="160">
          <template #default="scope">
            {{ formatTime(scope.row.closeTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="closeReason" label="平仓原因" width="160">
          <template #default="scope">
            {{ getCloseReasonText(scope.row.closeReason) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button
              v-if="
                scope.row.status === 'pending' ||
                scope.row.status === 'PENDING' ||
                scope.row.status === 'partially_filled'
              "
              @click="cancelOrder(scope.row)"
              type="danger"
              size="small"
            >
              取消
            </el-button>
            <el-button
              @click="viewOrderDetails(scope.row)"
              type="primary"
              size="small"
            >
              详情
            </el-button>
            <el-button @click="copyOrder(scope.row)" type="info" size="small">
              复制
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[15, 20, 50, 100]"
        :total="totalOrders"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="detailsDialogVisible" title="订单详情" width="1000px">
      <div v-if="selectedOrder" class="order-details">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{
            selectedOrder.orderSn
          }}</el-descriptions-item>
          <el-descriptions-item label="交易对">{{
            selectedOrder.symbol
          }}</el-descriptions-item>
          <el-descriptions-item label="订单类型">{{
            getOrderTypeText(selectedOrder.type)
          }}</el-descriptions-item>
          <el-descriptions-item label="交易方向">
            <span
              :class="selectedOrder.side === 'buy' ? 'buy-color' : 'sell-color'"
            >
              {{ selectedOrder.side === "buy" ? "买入" : "卖出" }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="开仓价">{{
            selectedOrder.price
          }}</el-descriptions-item>
          <el-descriptions-item label="平仓价">{{
            selectedOrder.closePrice ? selectedOrder.closePrice.toFixed(4) : "-"
          }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{
            selectedOrder.quantity
          }}</el-descriptions-item>
          <el-descriptions-item label="已成交数量">{{
            selectedOrder.executedQuantity
          }}</el-descriptions-item>
          <el-descriptions-item label="成交均价">{{
            selectedOrder.averagePrice || "-"
          }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getOrderStatusTag(selectedOrder.status)">
              {{ getOrderStatusText(selectedOrder.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开仓时间">{{
            formatTime(selectedOrder.openTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="平仓时间">{{
            formatTime(selectedOrder.closeTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="手续费">{{
            selectedOrder.fee || "-"
          }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{
            selectedOrder.note || "-"
          }}</el-descriptions-item>
        </el-descriptions>

        <h4 style="margin-top: 16px">订单项</h4>
        <el-table
          :data="orderItems"
          size="small"
          border
          style="width: 100%; margin-bottom: 16px"
        >
          <el-table-column prop="entrySn" label="订单项号" width="200" />
          <el-table-column prop="orderSideEnum" label="方向" width="100" />
          <el-table-column prop="buyPrice" label="买入价" width="120">
            <template #default="{ row }">{{ row.buyPrice ?? "-" }}</template>
          </el-table-column>
          <el-table-column prop="amount" label="数量" width="120" />
          <el-table-column prop="lossPrice" label="止损价" width="120">
            <template #default="{ row }">{{ row.lossPrice ?? "-" }}</template>
          </el-table-column>
          <el-table-column prop="gainPrice" label="止盈价" width="120">
            <template #default="{ row }">{{ row.gainPrice ?? "-" }}</template>
          </el-table-column>
          <el-table-column
            prop="tradeOrderItemStatus"
            label="状态"
            width="120"
          />
          <el-table-column prop="createTime" label="创建时间" min-width="160">
            <template #default="{ row }">{{
              formatTime(row.createTime)
            }}</template>
          </el-table-column>
        </el-table>

        <h4>平仓记录</h4>
        <el-table :data="orderCloses" size="small" border style="width: 100%">
          <el-table-column prop="sellPrice" label="平仓价" width="120" />
          <el-table-column prop="sellVolume" label="平仓数量" width="120" />
          <el-table-column prop="income" label="收益(USDT)" width="140" />
          <el-table-column prop="charge" label="手续费" width="120" />
          <el-table-column prop="exitType" label="出场类型" width="140" />
          <el-table-column prop="sellTime" label="平仓时间" min-width="160">
            <template #default="{ row }">{{
              formatTime(row.sellTime)
            }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Refresh, Download } from "@element-plus/icons-vue";
import { queryOrders, listOrderItems, listOrderCloses } from "@/api/tradeOrder";

const props = defineProps<{
  initialOrderSn?: string;
}>();

interface Order {
  id: string;
  orderSn: string;
  symbol: string;
  type: "limit" | "market" | "stop" | "take_profit";
  side: "buy" | "sell";
  price: number;
  closePrice?: number;
  quantity: number;
  executedQuantity: number;
  averagePrice?: number;
  status:
    | "pending"
    | "partially_filled"
    | "filled"
    | "canceled"
    | "rejected"
    | "DEAL"
    | "CLOSING"
    | "CLOSE"
    | "LOSS"
    | "PENDING"
    | "CANCEL";
  openTime?: string;
  closeTime?: string;
  fee?: number;
  note?: string;
  profit?: number;
  profitPercent?: number;
  closeReason?: string;
}

const loading = ref(false);
const orders = ref<Order[]>([]);
const currentPage = ref(1);
const pageSize = ref(15);
const totalOrders = ref(0);
const detailsDialogVisible = ref(false);
const selectedOrder = ref<Order | null>(null);
const lastAutoOpenOrderSn = ref<string | null>(null);

const filterForm = reactive({
  symbol: "",
  status: "",
  type: "",
  orderSn: "",
  dateRange: [] as [Date, Date] | [],
});

// 表格数据：支持本地按“订单类型”筛选（后端目前未提供 type 参数）
const filteredOrders = computed(() => {
  if (!filterForm.type) return orders.value;
  return orders.value.filter((o) => o.type === filterForm.type);
});

const getOrderTypeTag = (type: string) => {
  const tagMap: Record<string, string> = {
    limit: "",
    market: "success",
    stop: "warning",
    take_profit: "info",
  };
  return tagMap[type] || "";
};

const getOrderTypeText = (type: string) => {
  const textMap: Record<string, string> = {
    limit: "限价单",
    market: "市价单",
    stop: "止损单",
    take_profit: "止盈单",
  };
  return textMap[type] || type;
};

const getOrderStatusTag = (status: string) => {
  const tagMap: Record<string, string> = {
    pending: "warning",
    PENDING: "warning",
    partially_filled: "info",
    filled: "success",
    DEAL: "success", // 持仓中
    CLOSING: "info", // 平仓中
    CLOSE: "", // 已平仓
    canceled: "danger",
    CANCEL: "danger",
    rejected: "danger",
    LOSS: "danger", // 已止损
  };
  return tagMap[status] || "";
};

// 映射后端状态到前端状态（保持后端状态值，因为前端已支持）
const mapBackendStatusToFrontend = (
  backendStatus: string | null | undefined,
): string => {
  if (!backendStatus) {
    return "pending";
  }
  // 后端状态直接使用（DEAL, PENDING, CLOSING, CLOSE, CANCEL, LOSS等）
  return backendStatus;
};

const getOrderStatusText = (status: string) => {
  const textMap: Record<string, string> = {
    pending: "待成交",
    PENDING: "待成交",
    partially_filled: "部分成交",
    filled: "完全成交",
    DEAL: "持仓中",
    CLOSING: "平仓中",
    CLOSE: "已平仓",
    canceled: "已取消",
    CANCEL: "已取消",
    rejected: "已拒绝",
    LOSS: "已止损",
  };
  return textMap[status] || status;
};

// 平仓原因中文映射（ExitType 或 closeOrderType）
const getCloseReasonText = (reason?: string) => {
  if (!reason) return "-";
  const map: Record<string, string> = {
    TAKE_PROFIT: "止盈",
    STOP_LOSS: "止损",
    FIXED_PERCENT_TAKE_PROFIT: "固定比例止盈",
    FIXED_PERCENT_STOP_LOSS: "固定比例止损",
    TRAILING_STOP_LOSS: "移动止损",
    TRAILING_STOP_GAIN: "移动止盈",
    MACD_GOLDEN_CROSS: "MACD 金叉出场",
    MACD_DEAD_CROSS: "MACD 死叉出场",
    MANUAL_CLOSE: "手动平仓",
    BATCH_TAKE_PROFIT: "分批止盈",
    BATCH_STOP_LOSS: "分批止损",
    BATCH_TRAILING_GAIN: "分批移动止盈",
    BATCH_TRAILING_LOSS: "分批移动止损",
  };
  return map[reason] || reason;
};

// 格式化时间显示
const formatTime = (time: string | Date | undefined): string => {
  if (!time) return "-";
  try {
    const date = typeof time === "string" ? new Date(time) : time;
    if (isNaN(date.getTime())) return "-";
    return date.toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  } catch (e) {
    return "-";
  }
};

// 从后端分页获取订单列表
const fetchOrders = async () => {
  loading.value = true;
  try {
    const params: any = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      sortField: "orderTime",
      sortOrder: "desc",
    };

    if (filterForm.symbol) {
      params.symbol = filterForm.symbol;
    }
    if (filterForm.orderSn) {
      params.orderSn = filterForm.orderSn;
    }
    if (filterForm.status) {
      // 后端使用大写状态值（DEAL, PENDING, CLOSING, CLOSE, CANCEL, LOSS等）
      params.status = filterForm.status;
    }
    if (filterForm.dateRange && filterForm.dateRange.length === 2) {
      const [start, end] = filterForm.dateRange;
      params.startTime = start.getTime();
      params.endTime = end.getTime();
    }

    const res = await queryOrders(params);
    if (res && res.success && res.data) {
      const page = res.data;
      totalOrders.value = page.total || 0;
      orders.value = (page.records || []).map(
        (o: any): Order => ({
          id: o.id,
          orderSn: o.orderSn,
          symbol: o.symbol,
          type: (o.priceType ? String(o.priceType).toLowerCase() : "limit") as any,
          side: o.orderSide === "BUY" ? "buy" : "sell",
          price: o.openPrice || o.buyPrice || 0,
          closePrice: o.sellPrice ? Number(o.sellPrice) : undefined,
          quantity: o.amount || 0,
          executedQuantity: o.volume || 0,
          averagePrice: o.buyAvgPrice != null ? Number(o.buyAvgPrice) : undefined,
          status: mapBackendStatusToFrontend(o.status), // 映射后端状态到前端状态
          openTime: o.buyTime || o.orderTime || o.createTime || undefined,
          closeTime: o.sellTime || undefined,
          fee: o.charge || 0,
          note: "",
          profit: o.income != null ? Number(o.income) : undefined,
          profitPercent:
            o.profitPercent != null ? Number(o.profitPercent) : undefined,
          closeReason: o.closeReason,
        }),
      );
    } else {
      totalOrders.value = 0;
      orders.value = [];
    }
  } catch (error) {
    console.error("加载订单失败", error);
    ElMessage.error("加载订单失败");
    totalOrders.value = 0;
    orders.value = [];
  } finally {
    loading.value = false;
  }
};

const applyFilter = () => {
  currentPage.value = 1;
  fetchOrders();
};

const resetFilter = () => {
  filterForm.symbol = "";
  filterForm.status = "";
  filterForm.type = "";
  filterForm.orderSn = "";
  filterForm.dateRange = [];
  currentPage.value = 1;
  fetchOrders();
  ElMessage.info("筛选条件已重置");
};

const cancelOrder = async (order: Order) => {
  try {
    await ElMessageBox.confirm("确定要取消这个订单吗？", "确认取消", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500));

    const index = orders.value.findIndex((o) => o.id === order.id);
    if (index > -1) {
      orders.value[index].status = "canceled";
      // 取消订单时不需要更新平仓时间
    }

    ElMessage.success("订单已取消");
  } catch (error) {
    // 用户取消操作
  }
};

const orderItems = ref<any[]>([]);
const orderCloses = ref<any[]>([]);

const viewOrderDetails = async (order: Order) => {
  selectedOrder.value = order;
  detailsDialogVisible.value = true;
  try {
    const [itemsRes, closesRes] = await Promise.all([
      listOrderItems(order.orderSn),
      listOrderCloses(order.orderSn),
    ]);
    orderItems.value = itemsRes && itemsRes.success ? itemsRes.data || [] : [];
    orderCloses.value =
      closesRes && closesRes.success ? closesRes.data || [] : [];
  } catch (e) {
    orderItems.value = [];
    orderCloses.value = [];
  }
};

const copyOrder = (order: Order) => {
  ElMessage.success(`已复制订单 ${order.id}`);
};

const exportOrders = () => {
  ElMessage.success("订单数据导出中...");
};

const handleSizeChange = (val: number) => {
  pageSize.value = val;
  currentPage.value = 1;
  fetchOrders();
};

const handleCurrentChange = (val: number) => {
  currentPage.value = val;
  fetchOrders();
};

onMounted(() => {
  fetchOrders();
});

watch(
  () => props.initialOrderSn,
  async (sn) => {
    if (!sn) return;
    if (lastAutoOpenOrderSn.value === sn) return;
    lastAutoOpenOrderSn.value = sn;
    filterForm.orderSn = sn;
    currentPage.value = 1;
    await fetchOrders();
    const target = orders.value.find((o) => o.orderSn === sn);
    if (target) {
      await viewOrderDetails(target);
    }
  },
  { immediate: true },
);
</script>

<style scoped>
.orders-management {
  padding: 20px;
  padding-bottom: 80px; /* 为底部状态栏留出空间（状态栏高度35px + 分页组件高度 + 额外安全边距） */
  min-height: calc(
    100vh - 60px - 35px
  ); /* 减去顶部状态栏(60px)和底部状态栏(35px)的高度 */
  box-sizing: border-box;
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

.filter-section {
  background: var(--primary-bg);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 24px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.orders-table {
  background: var(--primary-bg);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 24px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 50px; /* 为底部状态栏留出空间 */
  padding-bottom: 20px; /* 额外的底部内边距 */
}

.buy-color {
  color: #67c23a;
  font-weight: 600;
}

.sell-color {
  color: #f56c6c;
  font-weight: 600;
}

.order-details {
  padding: 16px 0;
}
</style>
