<template>
  <div
    class="orderEntities-content"
    style="display: flex; flex-direction: column; height: 100%"
  >
    <!-- 订单明细头部工具栏 -->
    <div class="orderEntities-header">
      <div class="orderEntities-title">
        <h4>📋 订单信息</h4>

        <!-- 分页信息和控件 -->
        <div class="orderEntities-pagination-header">
          <div class="pagination-info-section">
            <span class="pagination-header-info">
              <span v-if="loading" class="loading-indicator">(加载中...)</span>
              <span v-else-if="pagination.totalElements > 0">
                第 {{ pagination.page + 1 }} 页，共
                {{ pagination.totalPages }} 页 ({{ pagination.totalElements }}
                条记录)
              </span>
              <span v-else>无数据</span>
            </span>
          </div>

          <div
            v-if="pagination.totalElements > 0"
            class="pagination-controls-inline"
          >
            <button
              class="pagination-btn small"
              :disabled="!pagination.hasPrevious || loading"
              @click="$emit('page-change', pagination.page - 1)"
              title="上一页"
            >
              ◀ 上一页
            </button>

            <div class="page-input-group small">
              <span>跳转到:</span>
              <input
                type="number"
                min="1"
                v-model.number="jumpToPageValue"
                @keyup.enter="handleJumpToPage"
                class="page-input small"
                :disabled="loading"
                :placeholder="`1-${pagination.totalPages || '?'}`"
              />
              <span>页</span>
              <button
                class="jump-btn small"
                @click="handleJumpToPage"
                :disabled="loading || !isValidPageInput"
                :title="
                  !isValidPageInput ? getPageInputError() : '跳转到指定页'
                "
              >
                跳转
              </button>
            </div>

            <button
              class="pagination-btn small"
              :disabled="!pagination.hasNext || loading"
              @click="$emit('page-change', pagination.page + 1)"
              title="下一页"
            >
              下一页 ▶
            </button>
          </div>
        </div>
      </div>

      <div class="orderEntities-actions">
        <!-- 状态筛选控件 -->
        <div class="status-filter-section">
          <label class="filter-label">状态筛选:</label>
          <select
            :model-value="positionStatusFilter"
            @update:model-value="$emit('update:positionStatusFilter', $event)"
            @change="
              $emit(
                'update:positionStatusFilter',
                ($event.target as HTMLSelectElement).value,
              )
            "
            class="status-select"
          >
            <option value="">全部</option>
            <option value="DEAL">持仓中</option>
            <option value="GAIN">止盈</option>
            <option value="LOSS">止损</option>
            <option value="CLOSING">已关闭</option>
          </select>
        </div>

        <button
          class="action-btn refresh-btn"
          :disabled="loading"
          @click="$emit('refresh')"
          title="手动刷新订单数据"
        >
          <span v-if="loading">🔄</span>
          <span v-else>🔄</span>
          {{ loading ? "刷新中..." : "刷新" }}
        </button>

        <div class="auto-refresh-indicator" title="每30秒自动刷新">
          <span class="auto-icon">⏰</span>
          <span class="auto-text">自动刷新</span>
        </div>
      </div>
    </div>

    <!-- 订单树形表格 -->
    <div class="orderEntities-tree" style="flex: 1; overflow-y: auto">
      <div v-if="orders.length === 0" class="orderEntities-placeholder">
        <div class="placeholder-text">暂无订单数据</div>
        <div class="placeholder-hint">订单信息将在这里显示</div>
      </div>

      <div v-else class="orderEntities-tree-container">
        <!-- 表头 -->
        <div class="orderEntities-tree-header">
          <div class="header-cell position-id">仓位ID</div>
          <div class="header-cell symbol">交易对</div>
          <div class="header-cell open-time">开仓时间</div>
          <div class="header-cell close-time">平仓时间</div>
          <div class="header-cell orderEntity-type">方向</div>
          <div class="header-cell quantity">数量</div>
          <div class="header-cell remaining-quantity">剩余数量</div>
          <div class="header-cell price">开仓价</div>
          <div class="header-cell close-price">平仓价</div>
          <div class="header-cell tp-sl">止盈止损</div>
          <div class="header-cell status">状态</div>
          <div class="header-cell close-reason">平仓原因</div>
          <div class="header-cell charge">成本</div>
          <div class="header-cell pnl">盈利</div>
          <div class="header-cell actions">操作</div>
        </div>

        <!-- 订单信息列表 -->
        <div class="orderEntities-tree-body">
          <div
            v-for="(position, positionIndex) in orders"
            :key="'position-' + positionIndex"
            class="position-node"
          >
            <!-- 仓位行 -->
            <div
              class="position-row"
              :class="{ expanded: position.expanded }"
              @click="$emit('toggle-position', position)"
            >
              <div class="row-cell position-id">
                <span
                  class="expand-icon"
                  :class="{ expanded: position.expanded }"
                >
                  {{
                    position.orderCount > 0
                      ? position.expanded
                        ? "▼"
                        : "▶"
                      : "○"
                  }}
                </span>
                {{ position.id }}
              </div>
              <div class="row-cell symbol">{{ position.symbol }}</div>
              <div class="row-cell open-time">
                <button
                  class="time-jump-btn"
                  @click.stop="$emit('jump-to-time', position.openTime)"
                >
                  {{ formatOrderTime(position.openTime) }}
                </button>
              </div>
              <div class="row-cell close-time">
                <button
                  v-if="position.closeTime"
                  class="time-jump-btn"
                  @click.stop="$emit('jump-to-time', position.closeTime)"
                >
                  {{ formatOrderTime(position.closeTime) }}
                </button>
                <span v-else>-</span>
              </div>
              <div class="row-cell orderEntity-type">
                <span
                  class="position-badge"
                  :class="(position.positionType || '').toLowerCase()"
                >
                  {{ formatPositionType(position.positionType) }}
                </span>
              </div>
              <div class="row-cell quantity">
                {{
                  formatPositionQuantity(
                    position.totalQuantity || position.quantity,
                  )
                }}
              </div>
              <div class="row-cell remaining-quantity">
                {{ formatRemainingQuantity(position) }}
              </div>
              <div class="row-cell price">
                {{ position.avgPrice ? formatPrice(position.avgPrice) : "-" }}
              </div>
              <div class="row-cell close-price">
                {{
                  position.closePrice ? formatPrice(position.closePrice) : "-"
                }}
              </div>
              <div class="row-cell tp-sl">
                <template v-if="position.batchExitType && position.batchExitPlans?.length">
                  <BatchExitProgress
                    :batch-exit-type="position.batchExitType"
                    :batch-exit-plans="position.batchExitPlans"
                    :batch-exits="position.batchExits"
                    :show-detail="position.expanded"
                  />
                </template>
                <div
                  v-else-if="position.takeProfitPrice || position.stopLossPrice"
                  class="tp-sl-container"
                >
                  <div
                    v-if="position.takeProfitPrice"
                    class="tp-sl-item tp-profit"
                  >
                    <span class="tp-sl-label">止盈:</span>
                    <span class="tp-sl-value">{{
                      formatPrice(position.takeProfitPrice)
                    }}</span>
                  </div>
                  <div v-if="position.stopLossPrice" class="tp-sl-item tp-loss">
                    <span class="tp-sl-label">止损:</span>
                    <span class="tp-sl-value">{{
                      formatPrice(position.stopLossPrice)
                    }}</span>
                  </div>
                </div>
                <span v-else>—</span>
              </div>
              <div class="row-cell status">
                <span
                  class="status-badge"
                  :class="formatPositionStatusClass(position.status)"
                >
                  {{ formatPositionStatus(position.status) }}
                </span>
              </div>
              <div class="row-cell close-reason">
                {{
                  formatCloseReason(
                    position.closeReason ||
                      position.exitType ||
                      position.closeType,
                  )
                }}
              </div>
              <div class="row-cell charge">
                {{ formatCharge(position.charge) }}
              </div>
              <div
                class="row-cell pnl"
                :class="{
                  'pnl-positive': (position.income || 0) > 0,
                  'pnl-negative': (position.income || 0) < 0,
                }"
              >
                {{ formatPositionPnl(position) }}
              </div>
              <div class="row-cell actions"></div>
            </div>

            <!-- 订单项列表 -->
            <div
              v-if="
                position.expanded &&
                position.orderEntities &&
                position.orderEntities.length > 0
              "
              class="orderEntities-container"
            >
              <div
                v-for="(orderEntity, orderIndex) in position.orderEntities"
                :key="'orderEntity-' + orderIndex"
                class="orderEntity-node"
              >
                <!-- 订单项行 -->
                <div
                  class="orderEntity-row"
                  :class="{ expanded: orderEntity.expanded }"
                  @click.stop="$emit('toggle-order', orderEntity)"
                >
                  <div class="row-cell orderEntity-id">
                    <span
                      class="expand-icon"
                      :class="{ expanded: orderEntity.expanded }"
                    >
                      {{
                        orderEntity.closeOrderCount > 0
                          ? orderEntity.expanded
                            ? "▼"
                            : "▶"
                          : "○"
                      }}
                    </span>
                    {{ orderEntity.id }}
                  </div>
                  <div class="row-cell symbol">
                    {{ formatOrderItemType(orderEntity.orderType) }}
                  </div>
                  <div class="row-cell open-time">
                    {{ formatOrderTime(orderEntity.orderTime) }}
                  </div>
                  <div class="row-cell close-time">
                    {{ formatOrderTime(orderEntity.sellTime) }}
                  </div>
                  <div class="row-cell orderEntity-type">
                    <span
                      class="order-badge"
                      :class="(orderEntity.orderType || '').toLowerCase()"
                    >
                      {{ formatOrderDirection(orderEntity.orderType) }}
                    </span>
                  </div>
                  <div class="row-cell quantity">
                    {{ formatPositionQuantity(orderEntity.quantity) }}
                  </div>
                  <div class="row-cell remaining-quantity">
                    {{ formatRemainingQuantity(orderEntity) }}
                  </div>
                  <div class="row-cell price">
                    {{ formatPrice(orderEntity.price) }}
                  </div>
                  <div class="row-cell close-price">-</div>
                  <div class="row-cell tp-sl">
                    <div
                      v-if="
                        orderEntity.takeProfitPrice || orderEntity.stopLossPrice
                      "
                      class="tp-sl-container"
                    >
                      <div
                        v-if="orderEntity.takeProfitPrice"
                        class="tp-sl-item tp-profit"
                      >
                        <span class="tp-sl-label">止盈价:</span>
                        <span class="tp-sl-value">{{
                          formatPrice(orderEntity.takeProfitPrice)
                        }}</span>
                      </div>
                      <div
                        v-if="orderEntity.stopLossPrice"
                        class="tp-sl-item tp-loss"
                      >
                        <span class="tp-sl-label">止损价:</span>
                        <span class="tp-sl-value">{{
                          formatPrice(orderEntity.stopLossPrice)
                        }}</span>
                      </div>
                    </div>
                    <span v-else>—</span>
                  </div>
                  <div class="row-cell status">
                    <span
                      class="status-badge"
                      :class="formatOrderStatusClass(orderEntity.status)"
                    >
                      {{ formatOrderStatus(orderEntity.status) }}
                    </span>
                  </div>
                  <div class="row-cell close-reason">
                    {{
                      formatCloseReason(
                        orderEntity.closeReason ||
                          orderEntity.exitType ||
                          orderEntity.closeType,
                      )
                    }}
                  </div>
                  <div class="row-cell charge">
                    {{ formatCharge(orderEntity.charge) }}
                  </div>
                  <div
                    class="row-cell pnl"
                    :class="{
                      'pnl-positive': (orderEntity.realizedPnl || 0) > 0,
                      'pnl-negative': (orderEntity.realizedPnl || 0) < 0,
                    }"
                  >
                    {{ formatOrderPnl(orderEntity) }}
                  </div>
                  <div class="row-cell actions">
                    <button
                      class="action-btn"
                      @click.stop="$emit('view-details', orderEntity, position)"
                    >
                      详情
                    </button>
                    <button
                      class="action-btn close-btn"
                      @click.stop="$emit('close-order', orderEntity, position)"
                      :disabled="orderEntity.status !== 'PENDING'"
                    >
                      平仓
                    </button>
                    <button
                      class="action-btn edit-btn"
                      @click.stop="
                        $emit('edit-gain-loss', orderEntity, position)
                      "
                    >
                      修改止盈止损
                    </button>
                  </div>
                </div>

                <!-- 分批出场明细 -->
                <BatchExitDetail
                  v-if="orderEntity.batchExitType && orderEntity.batchExitPlans?.length"
                  :batch-exit-type="orderEntity.batchExitType"
                  :batch-exit-plans="orderEntity.batchExitPlans"
                  :batch-exits="orderEntity.batchExits"
                />

                <!-- 平仓订单列表 -->
                <div
                  v-if="
                    orderEntity.expanded &&
                    orderEntity.closeOrders &&
                    orderEntity.closeOrders.length > 0
                  "
                  class="close-orders-container"
                >
                  <div
                    v-for="(closeOrder, closeIndex) in orderEntity.closeOrders"
                    :key="'close-' + closeIndex"
                    class="close-order-node"
                  >
                    <div class="close-order-row">
                      <div class="row-cell close-order-id">
                        {{ closeOrder.id }}
                      </div>
                      <div class="row-cell symbol">-</div>
                      <div class="row-cell open-time">-</div>
                      <div class="row-cell close-time">
                        <button
                          v-if="closeOrder.createTime"
                          class="time-jump-btn"
                          @click.stop="
                            $emit('jump-to-time', closeOrder.createTime)
                          "
                        >
                          {{ formatOrderTime(closeOrder.createTime) }}
                        </button>
                        <span v-else>-</span>
                      </div>
                      <div class="row-cell close-order-type">-</div>
                      <div class="row-cell quantity">
                        {{ formatPositionQuantity(closeOrder.quantity) }}
                      </div>
                      <div class="row-cell remaining-quantity">-</div>
                      <div class="row-cell price">
                        {{ formatPrice(closeOrder.price) }}
                      </div>
                      <div class="row-cell close-price">-</div>
                      <div class="row-cell tp-sl">-</div>
                      <div class="row-cell status">
                        <span class="status-badge">{{
                          formatOrderStatus(closeOrder.status)
                        }}</span>
                      </div>
                      <div class="row-cell close-reason">
                        {{
                          formatCloseReason(
                            closeOrder.closeReason ||
                              closeOrder.closeType ||
                              closeOrder.closeOrderType,
                          )
                        }}
                      </div>
                      <div class="row-cell charge">
                        {{ formatCharge(closeOrder.charge) }}
                      </div>
                      <div
                        class="row-cell pnl"
                        :class="{
                          'pnl-positive': (closeOrder.realizedPnl || 0) > 0,
                          'pnl-negative': (closeOrder.realizedPnl || 0) < 0,
                        }"
                      >
                        {{ formatOrderPnl(closeOrder) }}
                      </div>
                      <div class="row-cell actions">-</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from "vue";
import BatchExitProgress from "@/components/kline/BatchExitProgress.vue";
import BatchExitDetail from "@/components/kline/BatchExitDetail.vue";

interface Props {
  orders: any[];
  loading: boolean;
  pagination: {
    page: number;
    totalPages: number;
    totalElements: number;
    hasPrevious: boolean;
    hasNext: boolean;
  };
  positionStatusFilter: string;
  jumpToPage?: number | null;
}

const props = defineProps<Props>();

const jumpToPageValue = ref<number | null>(props.jumpToPage || null);

watch(
  () => props.jumpToPage,
  (newVal) => {
    jumpToPageValue.value = newVal || null;
  },
);

const emit = defineEmits<{
  "update:positionStatusFilter": [filter: string];
  refresh: [];
  "page-change": [page: number];
  "jump-to-page": [page: number];
  "toggle-position": [position: any];
  "toggle-order": [order: any];
  "close-order": [orderEntity: any, position: any];
  "edit-gain-loss": [orderEntity: any, position: any];
  "view-details": [orderEntity: any, position: any];
  "jump-to-time": [time: string | number];
}>();

// 计算属性：检查输入是否有效（允许输入超过总页数，但会提示）
const isValidPageInput = computed(() => {
  const pageValue = jumpToPageValue.value;
  const totalPages = props.pagination.totalPages;

  // 如果没有输入值，按钮禁用
  if (!pageValue || pageValue < 1) {
    return false;
  }

  // 如果总页数无效，按钮禁用
  if (totalPages <= 0) {
    return false;
  }

  // 允许输入超过总页数，但会在点击时提示
  // 这样用户可以输入任何数字，系统会给出友好提示
  return true;
});

// 获取输入错误提示
const getPageInputError = () => {
  const pageValue = jumpToPageValue.value;
  const totalPages = props.pagination.totalPages;

  if (!pageValue || pageValue < 1) {
    return "页码必须大于等于1";
  }

  if (totalPages <= 0) {
    return "总页数无效";
  }

  if (pageValue > totalPages) {
    return `将跳转到最后一页（第 ${totalPages} 页）`;
  }

  return "";
};

const handleJumpToPage = () => {
  const pageValue = jumpToPageValue.value;
  const totalPages = props.pagination.totalPages;

  console.log("🔍 OrderEntitiesPanel.handleJumpToPage:", {
    输入值: pageValue,
    总页数: totalPages,
    输入值类型: typeof pageValue,
    是否有效: pageValue && pageValue >= 1 && pageValue <= totalPages,
  });

  // 验证输入值
  if (!pageValue || pageValue < 1) {
    console.warn("⚠️ 页码无效: 页码必须大于等于1", { 输入值: pageValue });
    // 可以在这里显示提示消息
    return;
  }

  if (totalPages <= 0) {
    console.warn("⚠️ 总页数无效:", { 总页数: totalPages });
    return;
  }

  // 如果输入值大于总页数，自动跳转到最后一页并提示
  if (pageValue > totalPages) {
    console.warn("⚠️ 页码超出范围，自动跳转到最后一页:", {
      输入值: pageValue,
      总页数: totalPages,
      将跳转到: totalPages,
    });
    // 触发跳转到最后一页
    emit("jump-to-page", totalPages);
    // 清空输入框
    jumpToPageValue.value = null;
    return;
  }

  // 触发跳转事件
  console.log("✅ 触发跳转事件:", { 页码: pageValue });
  emit("jump-to-page", pageValue);

  // 清空输入框
  jumpToPageValue.value = null;
};

const formatOrderTime = (timeStr: string | number) => {
  if (!timeStr) return "-";
  try {
    const date = new Date(
      typeof timeStr === "string" ? timeStr : timeStr * 1000,
    );
    return date.toLocaleString("zh-CN", {
      timeZone: "Asia/Shanghai",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
  } catch {
    return String(timeStr);
  }
};

const formatPositionType = (type: string) => {
  if (type === "BUY" || type === "LONG" || type === "long") return "多头";
  if (type === "SELL" || type === "SHORT" || type === "short") return "空头";
  return type;
};

const formatPositionQuantity = (quantity: number | string) => {
  if (!quantity && quantity !== 0) return "-";
  return parseFloat(String(quantity)).toFixed(4);
};

const formatRemainingQuantity = (entity: any) => {
  const remaining = entity.remainingAmount ?? entity.remainingQuantity;
  if (remaining !== null && remaining !== undefined) {
    return formatPositionQuantity(remaining);
  }
  // 如果没有 remainingAmount，尝试用 totalQuantity/quantity 减去 closedVolume
  const total = entity.totalQuantity ?? entity.quantity ?? entity.amount;
  const closed = entity.closedVolume ?? entity.closeQuantity ?? 0;
  if (total) {
    const r = parseFloat(String(total)) - parseFloat(String(closed));
    return r > 0 ? r.toFixed(4) : "-";
  }
  return "-";
};

const formatPrice = (price: number | string) => {
  if (!price && price !== 0) return "-";
  return parseFloat(String(price)).toFixed(4);
};

const formatPositionStatus = (status: string) => {
  const statusMap: Record<string, string> = {
    OPEN: "持仓中",
    CLOSED: "已平仓",
    PARTIAL: "部分平仓",
    DEAL: "持仓中",
    GAIN: "止盈",
    LOSS: "止损",
    CLOSING: "已关闭",
  };
  return statusMap[status] || status;
};

const formatPositionStatusClass = (status: string) => {
  if (status === "OPEN" || status === "DEAL") return "status-open";
  if (status === "CLOSED" || status === "CLOSING") return "status-closed";
  if (status === "GAIN") return "status-gain";
  if (status === "LOSS") return "status-loss";
  return "";
};

const formatOrderItemType = (orderType: string) => {
  if (orderType === "BUY" || orderType === "BUY_MARKET") return "买入";
  if (orderType === "SELL" || orderType === "SELL_MARKET") return "卖出";
  return orderType;
};

const formatOrderDirection = (direction: string) => {
  if (direction === "BUY" || direction === "BUY_MARKET") return "买入";
  if (direction === "SELL" || direction === "SELL_MARKET") return "卖出";
  return direction;
};

const formatOrderStatus = (status: string) => {
  const statusMap: Record<string, string> = {
    OPEN: "持仓中",
    CLOSED: "已平仓",
    CANCELLED: "已取消",
    PENDING: "待处理",
  };
  return statusMap[status] || status;
};

const formatOrderStatusClass = (status: string) => {
  if (status === "OPEN" || status === "PENDING") return "status-open";
  if (status === "CLOSED") return "status-closed";
  if (status === "CANCELLED") return "status-cancelled";
  return "";
};

const formatCloseReason = (reason: string | null | undefined) => {
  if (!reason) return "-";
  const text = String(reason);
  if (/[\u4e00-\u9fa5]/.test(text)) return text;
  const normalized = text.toUpperCase();
  const reasonMap: Record<string, string> = {
    STOP_LOSS: "固定止损",
    TAKE_PROFIT: "止盈",
    TECHNICAL_INDICATOR: "技术指标",
    SIGNAL_REVERSAL: "反转信号",
    TIME_LIMIT: "时间限制",
    MANUAL: "手动平仓",
    UNKNOWN: "未知原因",
    MANUAL_CLOSE: "手动平仓",
    AUTO_CLOSE: "自动平仓",
    CLOSE_LONG: "平多",
    CLOSE_SHORT: "平空",
    BUY_GAIN: "多止盈",
    BUY_LOSS: "多止损",
    SELL_GAIN: "空止盈",
    SELL_LOSS: "空止损",
    BATCH_TAKE_PROFIT: "分批止盈",
    BATCH_STOP_LOSS: "分批止损",
    BATCH_TRAILING_GAIN: "分批移动止盈",
    BATCH_TRAILING_LOSS: "分批移动止损",
    TAKE_PROFIT_FIXED: "固定百分比止盈",
    FIXED_TAKE_PROFIT: "固定百分比止盈",
    FIXED_PERCENT_TAKE_PROFIT: "固定百分比止盈",
    TAKE_PROFIT_ATR: "ATR 止盈",
    ATR_TAKE_PROFIT: "ATR 止盈",
    ATR_BASED_TAKE_PROFIT: "ATR 止盈",
    TRAILING_TAKE_PROFIT: "移动止盈",
    MANUAL_TAKE_PROFIT: "手动止盈",
    AUTO_TAKE_PROFIT: "自动止盈",
  };
  return reasonMap[normalized] || text;
};

const formatPositionPnl = (position: any) => {
  const income = position.income;
  alert("here")
  if (income == null || income === "") return "-";
  const num = typeof income === "number" ? income : Number(income);
  if (isNaN(num)) return "-";
  if (num >= 0) {
    return `+$${num.toFixed(2)}`;
  } else {
    return `-$${Math.abs(num).toFixed(2)}`;
  }
};

const formatOrderPnl = (order: any) => {
  const pnl = order.realizedPnl || 0;
  if (pnl >= 0) {
    return `+$${pnl.toFixed(2)}`;
  } else {
    return `-$${Math.abs(pnl).toFixed(2)}`;
  }
};

/** 格式化成本（手续费） */
const formatCharge = (charge: number | string | null | undefined): string => {
  if (charge == null || charge === "") return "-";
  const n = typeof charge === "number" ? charge : Number(charge);
  if (isNaN(n)) return "-";
  return `$${n.toFixed(2)}`;
};
</script>

<style scoped>
.orderEntities-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.orderEntities-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #dee2e6;
}

.orderEntities-title {
  flex: 1;
}

.orderEntities-title h4 {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
  color: #1e222d;
}

.orderEntities-pagination-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.pagination-info-section {
  font-size: 14px;
  color: #666;
}

.loading-indicator {
  color: #2962ff;
}

.pagination-controls-inline {
  display: flex;
  gap: 8px;
  align-items: center;
}

.pagination-btn {
  padding: 6px 12px;
  border: 1px solid #dee2e6;
  background-color: #ffffff;
  color: #1e222d;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.pagination-btn:hover:not(:disabled) {
  background-color: #f8f9fa;
}

.pagination-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.page-input-group {
  display: flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
}

.page-input {
  width: 60px;
  padding: 4px 8px;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 12px;
  text-align: center;
}

.jump-btn {
  padding: 4px 8px;
  border: 1px solid #dee2e6;
  background-color: #ffffff;
  color: #1e222d;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.jump-btn:hover:not(:disabled) {
  background-color: #f8f9fa;
}

.jump-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.orderEntities-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.status-filter-section {
  display: flex;
  gap: 8px;
  align-items: center;
}

.filter-label {
  font-size: 14px;
  color: #666;
}

.status-select {
  padding: 6px 12px;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 14px;
  min-width: 120px;
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

.auto-refresh-indicator {
  display: flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: #999;
}

.orderEntities-tree {
  flex: 1;
  overflow-y: auto;
}

.orderEntities-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.placeholder-text {
  font-size: 16px;
  color: #666;
  margin-bottom: 8px;
}

.placeholder-hint {
  font-size: 14px;
  color: #999;
}

.orderEntities-tree-container {
  border: 1px solid #dee2e6;
  border-radius: 4px;
  overflow: hidden;
}

.orderEntities-tree-header {
  display: grid;
  grid-template-columns: 120px 100px 150px 150px 80px 100px 100px 100px 100px 120px 80px 140px 80px 100px 150px;
  gap: 8px;
  padding: 12px;
  background-color: #f8f9fa;
  font-weight: 600;
  font-size: 13px;
  color: #1e222d;
  border-bottom: 1px solid #dee2e6;
}

.orderEntities-tree-body {
  background-color: #ffffff;
}

.position-node {
  border-bottom: 1px solid #f0f0f0;
}

.position-row {
  display: grid;
  grid-template-columns: 120px 100px 150px 150px 80px 100px 100px 100px 100px 120px 80px 140px 80px 100px 150px;
  gap: 8px;
  padding: 12px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.position-row:hover {
  background-color: #f8f9fa;
}

.position-row.expanded {
  background-color: #f0f7ff;
}

.row-cell {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #1e222d;
}

.expand-icon {
  margin-right: 4px;
  font-size: 12px;
  color: #666;
  transition: transform 0.2s;
}

.expand-icon.expanded {
  transform: rotate(90deg);
}

.time-jump-btn {
  padding: 2px 6px;
  border: 1px solid #dee2e6;
  background-color: #ffffff;
  color: #2962ff;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.time-jump-btn:hover {
  background-color: #e3f2fd;
  border-color: #2962ff;
}

.position-badge,
.order-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.position-badge.buy,
.position-badge.long {
  background-color: #e8f5e9;
  color: #00c853;
}

.position-badge.sell,
.position-badge.short {
  background-color: #ffebee;
  color: #f44336;
}

.tp-sl-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tp-sl-item {
  font-size: 11px;
}

.tp-sl-label {
  color: #666;
  margin-right: 4px;
}

.tp-sl-value {
  font-weight: 600;
}

.tp-sl-item.tp-profit .tp-sl-value {
  color: #00c853;
}

.tp-sl-item.tp-loss .tp-sl-value {
  color: #f44336;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge.status-open {
  background-color: #e3f2fd;
  color: #2962ff;
}

.status-badge.status-closed {
  background-color: #f5f5f5;
  color: #666;
}

.status-badge.status-gain {
  background-color: #e8f5e9;
  color: #00c853;
}

.status-badge.status-loss {
  background-color: #ffebee;
  color: #f44336;
}

.pnl-positive {
  color: #00c853;
  font-weight: 600;
}

.pnl-negative {
  color: #f44336;
  font-weight: 600;
}

.orderEntities-container {
  background-color: #fafafa;
  border-left: 2px solid #2962ff;
  margin-left: 20px;
}

.orderEntity-node {
  border-bottom: 1px solid #f0f0f0;
}

.orderEntity-row {
  display: grid;
  grid-template-columns: 120px 100px 150px 150px 80px 100px 100px 100px 100px 120px 80px 140px 80px 100px 150px;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.orderEntity-row:hover {
  background-color: #f0f0f0;
}

.close-orders-container {
  background-color: #f5f5f5;
  border-left: 2px solid #999;
  margin-left: 40px;
}

.close-order-row {
  display: grid;
  grid-template-columns: 120px 100px 150px 150px 80px 100px 100px 100px 100px 120px 80px 140px 80px 100px 150px;
  gap: 8px;
  padding: 8px 12px;
  font-size: 12px;
  color: #666;
}

.action-btn {
  padding: 4px 8px;
  border: 1px solid #dee2e6;
  background-color: #ffffff;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  margin-right: 4px;
}

.action-btn.close-btn {
  color: #f44336;
}

.action-btn.close-btn:hover:not(:disabled) {
  background-color: #ffebee;
  border-color: #f44336;
}

.action-btn.edit-btn {
  color: #2962ff;
}

.action-btn.edit-btn:hover {
  background-color: #e3f2fd;
  border-color: #2962ff;
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
