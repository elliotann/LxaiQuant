/**
 * 持仓管理 Composable
 * 提供持仓数据的查询和管理功能
 */
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { getPositionTree } from "@/api/tradeOrder";

export function usePositions(state: any) {
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  /**
   * 获取持仓信息
   */
  const fetchPositions = async () => {
    if (state.positionsLoading) return;

    state.positionsLoading = true;
    isLoading.value = true;
    error.value = null;

    try {
      // 使用默认的会员ID和账户ID
      const defaultMemberId = "1665908516499693568";
      const defaultAccountId = "1768185450252304387";

      const memberId =
        state.selectedMemberConfigInfo?.memberId || defaultMemberId;
      const accountId =
        state.selectedMemberConfigInfo?.thirdAccountId || defaultAccountId;

      if (!memberId || !accountId) {
        ElMessage.warning("缺少会员或API账号信息，无法获取持仓");
        state.positions = [];
        return;
      }

      // 调用真实API获取订单信息
      const response = await getPositionTree({
        memberId,
        accountId,
        symbol: state.selectedSymbol,
      });

      if (response.success && response.result) {
        // 转换数据格式，适配底部面板的显示
        state.positions = (response.result || []).map((p: any) => ({
          posId: p.posId || p.id,
          instId: p.instId || p.symbol,
          posSide: p.posSide || (p.side === "LONG" ? "long" : "short"),
          pos: p.pos || p.quantity || p.sz,
          sz: p.sz || p.pos || p.quantity,
          upl: p.upl || p.unrealizedPnl || p.pnl,
          unrealizedPnl: p.unrealizedPnl || p.upl || p.pnl,
          uplRatio:
            p.uplRatio ||
            (p.unrealizedPnl && p.avgPrice
              ? p.unrealizedPnl / (p.avgPrice * (p.pos || p.quantity))
              : 0),
        }));
      } else {
        state.positions = [];
        console.warn("获取持仓信息失败:", response);
      }
    } catch (err: any) {
      error.value = err.message || "获取持仓信息失败";
      console.error("获取持仓信息出错:", err);
      state.positions = [];
      ElMessage.error("获取持仓信息失败");
    } finally {
      state.positionsLoading = false;
      isLoading.value = false;
    }
  };

  /**
   * 关闭持仓
   */
  const closePosition = async (position: any) => {
    // 持仓平仓逻辑
    console.log("关闭持仓:", position);
    ElMessage.info("持仓平仓功能开发中");
  };

  /**
   * 格式化PnL显示
   */
  const formatPnL = (pnl: number, ratio?: number) => {
    const pnlValue = parseFloat(String(pnl || 0));
    const ratioValue = ratio ? parseFloat(String(ratio)) : 0;

    if (pnlValue >= 0) {
      return `+$${pnlValue.toFixed(2)}${ratio ? ` (+${(ratioValue * 100).toFixed(2)}%)` : ""}`;
    } else {
      return `-$${Math.abs(pnlValue).toFixed(2)}${ratio ? ` (${(ratioValue * 100).toFixed(2)}%)` : ""}`;
    }
  };

  return {
    isLoading,
    error,
    fetchPositions,
    closePosition,
    formatPnL,
  };
}
