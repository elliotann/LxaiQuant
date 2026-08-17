/**
 * 订单管理 Composable
 * 提供订单数据的查询、更新和管理功能
 */
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { queryOrders, closeOrderItem, updateOrderItem } from "@/api/tradeOrder";

export function useOrders(state: any) {
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  /**
   * 获取订单详情
   */
  const fetchOrderDetails = async () => {
    state.orderDetailsLoading = true;
    error.value = null;

    try {
      const defaultMemberId = "1665908516499693568";
      const defaultAccountId = "1768185450252304387";
      const memberId =
        state.selectedMemberConfigInfo?.memberId || defaultMemberId;
      const accountId =
        state.selectedMemberConfigInfo?.thirdAccountId || defaultAccountId;

      if (!memberId || !accountId) {
        state.orderDetails = [];
        ElMessage.warning("缺少会员配置，无法加载订单信息");
        return;
      }

      const symbol = state.selectedSymbol;

      // 调用真实API获取订单信息
      const pageSize = 10; // 每页显示10条记录
      const pageNumber = state.ordersPage + 1; // 前端页码从0开始，后端从1开始

      const params: any = {
        memberId,
        accountId,
        symbol,
        pageNum: pageNumber,
        pageSize,
        sortField: "orderTime",
        sortOrder: "desc",
      };

      // 添加状态筛选参数
      if (state.positionStatusFilter) {
        params.status = state.positionStatusFilter;
      }

      const response = await queryOrders(params);

      if (response && response.success && response.data) {
        // 处理OrderManagerController返回的分页数据
        const pageData = response.data;
        state.ordersTotalPages = pageData.pages || 1;
        state.ordersTotalElements = pageData.total || 0;

        // 将OrderVO转换为前端期望的格式
        state.orderDetails = pageData.records.map((order: any) => {
          const closeReason =
            order.exitType ||
            order.closeReason ||
            order.closeType ||
            order.closeOrderType;
          return {
            id: order.id,
            symbol: order.symbol,
            openTime: order.orderTime
              ? new Date(order.orderTime).toISOString()
              : new Date().toISOString(),
            lastUpdateTime: order.updateTime
              ? new Date(order.updateTime).toISOString()
              : new Date().toISOString(),
            closeTime: order.sellTime
              ? new Date(order.sellTime).toISOString()
              : null,
            positionType: order.orderSide,
            quantity: order.amount,
            totalQuantity: order.amount,
            avgPrice: order.buyAvgPrice ?? order.buyPrice ?? order.openPrice,
            closePrice: order.sellPrice,
            status: order.status,
            income: order.income,
            charge: order.charge,
            closeReason,
            orderCount: 1,
            takeProfitPrice: order.gainPrice ?? null,
            stopLossPrice: order.lossPrice ?? null,
            batchExitType: order.batchExitType ?? null,
            currentBatchIndex: order.currentBatchIndex ?? null,
            batchExitPlans: order.batchExitPlans ?? [],
            batchExits: order.batchExits ?? [],
            trailingGainLockedPrice: order.trailingGainLockedPrice ?? null,
            trailingLossLockedPrice: order.trailingLossLockedPrice ?? null,
            expanded: false,
            orderEntities: [
              {
                id: order.id,
                orderType: order.orderSide,
                quantity: order.amount,
                price: order.buyPrice ?? order.openPrice,
                orderTime: order.orderTime,
                sellTime: order.sellTime,
                takeProfitPrice: order.gainPrice ?? null,
                stopLossPrice: order.lossPrice ?? null,
                batchExitType: order.batchExitType ?? null,
                currentBatchIndex: order.currentBatchIndex ?? null,
                batchExitPlans: order.batchExitPlans ?? [],
                batchExits: order.batchExits ?? [],
                trailingGainLockedPrice: order.trailingGainLockedPrice ?? null,
                trailingLossLockedPrice: order.trailingLossLockedPrice ?? null,
                status: order.status,
                realizedPnl: order.income,
                closeReason,
                closeOrderCount: order.sellTime ? 1 : 0,
                expanded: false,
                closeOrders: order.sellTime
                  ? [
                      {
                        id: order.id,
                        quantity: order.amount,
                        price: order.sellPrice,
                        createTime: order.sellTime,
                        status: "CLOSED",
                        realizedPnl: order.income,
                        closeReason: closeReason || "MANUAL_CLOSE",
                      },
                    ]
                  : [],
              },
            ],
          };
        });

        // 更新分页状态
        state.ordersHasPrevious = state.ordersPage > 0;
        state.ordersHasNext = state.ordersPage < state.ordersTotalPages - 1;
      } else {
        // 无数据时清空列表
        state.orderDetails = [];
        state.ordersTotalPages = 0;
        state.ordersTotalElements = 0;
        state.ordersHasPrevious = false;
        state.ordersHasNext = false;
      }
    } catch (err: any) {
      error.value = err.message || "获取订单信息失败";
      console.error("获取订单信息失败:", err);
      state.orderDetails = [];
      state.ordersTotalPages = 0;
      state.ordersTotalElements = 0;
      state.ordersHasPrevious = false;
      state.ordersHasNext = false;
      ElMessage.error("获取订单信息失败");
    } finally {
      state.orderDetailsLoading = false;
    }
  };

  /**
   * 跳转到指定页码
   */
  const goToOrdersPage = (page: number) => {
    if (page >= 0 && page < state.ordersTotalPages) {
      state.ordersPage = page;
      state.ordersHasPrevious = state.ordersPage > 0;
      state.ordersHasNext = state.ordersPage < state.ordersTotalPages - 1;
      fetchOrderDetails();
    }
  };

  /**
   * 跳转到指定页码（通过输入框）
   */
  const jumpToOrdersPage = () => {
    if (state.jumpToPage !== null && state.jumpToPage !== undefined) {
      const page = parseInt(String(state.jumpToPage)) - 1; // 转换为0基索引
      if (page >= 0 && page < state.ordersTotalPages) {
        goToOrdersPage(page);
        state.jumpToPage = null;
      } else {
        ElMessage.warning(`页码必须在 1-${state.ordersTotalPages} 之间`);
      }
    }
  };

  /**
   * 关闭订单项
   */
  const closeOrderItem = async (orderEntity: any, position: any) => {
    try {
      await ElMessageBox.confirm(`确定要平仓订单项吗？`, "确认平仓", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      });

      const defaultMemberId = "1665908516499693568";
      const defaultAccountId = "1768185450252304387";
      const memberId =
        state.selectedMemberConfigInfo?.memberId || defaultMemberId;
      const accountId =
        state.selectedMemberConfigInfo?.thirdAccountId || defaultAccountId;

      const params = {
        id: orderEntity.id,
        memberId,
        accountId,
        symbol: state.selectedSymbol,
      };

      const response = await closeOrderItem(params);
      if (response && response.success) {
        ElMessage.success("平仓成功");
        await fetchOrderDetails();
      } else {
        ElMessage.error("平仓失败：" + (response?.message || "未知错误"));
      }
    } catch (err: any) {
      if (err !== "cancel") {
        console.error("平仓失败:", err);
        ElMessage.error("平仓失败：" + (err.message || "未知错误"));
      }
    }
  };

  /**
   * 显示修改止盈止损模态框
   */
  const showEditGainLossModal = (orderEntity: any, position: any) => {
    console.log("修改止盈止损 - 订单项数据:", orderEntity);

    // 设置表单数据
    state.gainLossForm = {
      id: orderEntity.id,
      orderSn: position ? position.id : "",
      orderItemSn: orderEntity.id,
      orderSideEnum:
        orderEntity.orderType === "BUY" ||
        orderEntity.orderType === "BUY_MARKET"
          ? "BUY"
          : "SELL",
      openPrice: orderEntity.price,
      gainPrice: orderEntity.takeProfitPrice,
      stopLossPrice: orderEntity.stopLossPrice,
    };

    console.log("修改止盈止损 - 表单数据:", state.gainLossForm);

    // 显示模态框
    state.updateGainAndLossModal = true;
  };

  /**
   * 更新订单止盈止损
   */
  const updateOrderGainLoss = async () => {
    try {
      console.log("提交修改止盈止损 - 表单数据:", state.gainLossForm);

      state.gainLossSubmitLoading = true;

      // 准备API参数
      const updateParams = {
        id: state.gainLossForm.id,
        orderSn: state.gainLossForm.orderSn,
        orderItemSn: state.gainLossForm.orderItemSn,
        orderSideEnum: state.gainLossForm.orderSideEnum,
        buyPrice: state.gainLossForm.openPrice,
        gainPrice: state.gainLossForm.gainPrice,
        stopLossPrice: state.gainLossForm.stopLossPrice,
      };

      console.log("提交修改止盈止损 - API参数:", updateParams);

      // 调用API更新订单项
      const response = await updateOrderItem(updateParams);
      state.gainLossSubmitLoading = false;
      state.updateGainAndLossModal = false;
      ElMessage.success("操作成功");
      // 刷新订单详情
      await fetchOrderDetails();
    } catch (err: any) {
      console.error("修改止盈止损失败:", err);
      state.gainLossSubmitLoading = false;
      ElMessage.error("操作失败：" + (err.message || "未知错误"));
    }
  };

  /**
   * 格式化订单时间
   */
  const formatOrderTime = (timeStr: string) => {
    if (!timeStr) return "-";
    try {
      const date = new Date(timeStr);
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
      return timeStr;
    }
  };

  /**
   * 格式化持仓类型
   */
  const formatPositionType = (type: string) => {
    if (type === "BUY" || type === "LONG" || type === "long") return "多头";
    if (type === "SELL" || type === "SHORT" || type === "short") return "空头";
    return type;
  };

  /**
   * 格式化持仓状态
   */
  const formatPositionStatus = (status: string) => {
    const statusMap: Record<string, string> = {
      OPEN: "持仓中",
      CLOSED: "已平仓",
      PARTIAL: "部分平仓",
    };
    return statusMap[status] || status;
  };

  /**
   * 格式化持仓盈亏
   */
  const formatPositionPnl = (pnl: number) => {
    if (pnl >= 0) {
      return `+$${pnl.toFixed(2)}`;
    } else {
      return `-$${Math.abs(pnl).toFixed(2)}`;
    }
  };

  /**
   * 格式化订单方向
   */
  const formatOrderDirection = (direction: string) => {
    if (direction === "BUY" || direction === "BUY_MARKET") return "买入";
    if (direction === "SELL" || direction === "SELL_MARKET") return "卖出";
    return direction;
  };

  /**
   * 格式化订单状态
   */
  const formatOrderStatus = (status: string) => {
    const statusMap: Record<string, string> = {
      OPEN: "持仓中",
      CLOSED: "已平仓",
      CANCELLED: "已取消",
    };
    return statusMap[status] || status;
  };

  /**
   * 格式化订单盈亏
   */
  const formatOrderPnl = (pnl: number) => {
    if (pnl >= 0) {
      return `+$${pnl.toFixed(2)}`;
    } else {
      return `-$${Math.abs(pnl).toFixed(2)}`;
    }
  };

  return {
    isLoading,
    error,
    fetchOrderDetails,
    goToOrdersPage,
    jumpToOrdersPage,
    closeOrderItem,
    showEditGainLossModal,
    updateOrderGainLoss,
    formatOrderTime,
    formatPositionType,
    formatPositionStatus,
    formatPositionPnl,
    formatOrderDirection,
    formatOrderStatus,
    formatOrderPnl,
  };
}
