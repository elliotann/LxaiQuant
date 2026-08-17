/**
 * 与后端 OrderAction 枚举一致，用于信号方向等字段的展示与筛选。
 */
export const ORDER_ACTION_LABELS: Record<string, string> = {
  OPEN_LONG: "开多",
  OPEN_SHORT: "开空",
  CLOSE_LONG: "平多",
  CLOSE_SHORT: "平空",
  // 新版技术信号方向（数据库落库值）
  LONG: "多",
  SHORT: "空",
  CALLBACK_LONG: "回调做多",
  CALLBACK_SHORT: "反弹做空",
  // 历史兼容（旧版技术信号方向）
  LB: "开多",
  SB: "开空",
  SS: "平多",
  BS: "平空",
  SL: "平多",
  LBAP: "加多仓",
  LBSP: "减多仓",
  SBAP: "加空仓",
  SBSP: "减空仓",
  BUY_GAIN: "多止盈",
  BUY_LOSS: "多止损",
  SELL_GAIN: "空止盈",
  SELL_LOSS: "空止损",
  ADJUST_LEVERAGE: "调整杠杆",
  CANCEL_ORDER: "取消订单",
  CLOSE: "关单",
};

/** 信号管理筛选：仅业务常用的开仓/平仓/调仓/止盈止损 */
export const SIGNAL_DIRECTION_FILTER_OPTIONS: { value: string; label: string }[] =
  [
    { value: "OPEN_LONG", label: "开多" },
    { value: "OPEN_SHORT", label: "开空" },
    { value: "CLOSE_LONG", label: "平多" },
    { value: "CLOSE_SHORT", label: "平空" },
    { value: "LBAP", label: "加多仓" },
    { value: "LBSP", label: "减多仓" },
    { value: "SBAP", label: "加空仓" },
    { value: "SBSP", label: "减空仓" },
    { value: "BUY_GAIN", label: "多止盈" },
    { value: "BUY_LOSS", label: "多止损" },
    { value: "SELL_GAIN", label: "空止盈" },
    { value: "SELL_LOSS", label: "空止损" },
  ];

/** 历史技术方向字段兼容（库中若仍为旧值仍可读） */
const LEGACY_TECH_DIRECTION_LABELS: Record<string, string> = {
  STRONG_BULLISH: "强多",
  BULLISH: "偏多",
  NEUTRAL: "中性",
  BEARISH: "偏空",
  STRONG_BEARISH: "强空",
};

export function formatSignalDirectionLabel(
  raw: string | undefined | null,
): string {
  if (raw == null || raw === "") return "-";
  return (
    ORDER_ACTION_LABELS[raw] ??
    LEGACY_TECH_DIRECTION_LABELS[raw] ??
    raw
  );
}

export function getSignalDirectionTagType(
  direction: string | undefined | null,
): "success" | "warning" | "danger" | "info" {
  if (!direction) return "info";
  switch (direction) {
    case "LONG":
    case "CALLBACK_LONG":
    case "OPEN_LONG":
    case "LB":
    case "LBAP":
    case "BUY_GAIN":
    case "STRONG_BULLISH":
    case "BULLISH":
      return "success";
    case "SHORT":
    case "CALLBACK_SHORT":
    case "OPEN_SHORT":
    case "SB":
    case "SBAP":
    case "SELL_GAIN":
    case "BEARISH":
    case "STRONG_BEARISH":
      return "danger";
    case "CLOSE_LONG":
    case "CLOSE_SHORT":
    case "SL":
    case "SS":
    case "LBSP":
    case "BUY_LOSS":
    case "BS":
    case "SBSP":
    case "SELL_LOSS":
      return "warning";
    default:
      return "info";
  }
}
