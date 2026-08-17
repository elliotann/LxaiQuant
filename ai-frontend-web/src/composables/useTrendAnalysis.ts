/**
 * 趋势分析管理 Composable
 * 提供趋势分析数据的获取和应用功能
 */
import { ref } from "vue";
import { getTrendAnalysis } from "@/api/member";

export function useTrendAnalysis(state: any) {
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  /**
   * 获取趋势分析数据
   * 获取主时间框架、15分钟和1小时的时间框架数据
   */
  const fetchTrendAnalysis = async () => {
    isLoading.value = true;
    error.value = null;

    try {
      const symbol = state.selectedSymbol;

      // 获取主时间框架趋势数据
      await fetchTrendDataForInterval(symbol, state.selectedInterval, (data) =>
        applyPrimaryTrendData(data),
      );

      // 获取15分钟时间框架趋势数据
      await fetchTrendDataForInterval(
        symbol,
        "OKXMIN15",
        (data) => apply15mTrendData(data),
        () => reset15mTrendData(),
      );

      // 获取1小时时间框架趋势数据
      await fetchTrendDataForInterval(
        symbol,
        "OKXMIN60",
        (data) => apply1hTrendData(data),
        () => reset1hTrendData(),
      );
    } catch (err: any) {
      error.value = err.message || "获取趋势分析数据失败";
      console.error("获取趋势分析数据出错:", err);
    } finally {
      isLoading.value = false;
    }
  };

  /**
   * 获取指定时间框架的趋势数据
   */
  const fetchTrendDataForInterval = async (
    symbol: string,
    intervalCode: string,
    onSuccess: (data: any) => void,
    onFailure?: () => void,
  ) => {
    const params = { interval: intervalCode, limit: 500 };

    try {
      console.log("📊 获取趋势分析数据:", { symbol, intervalCode, params });
      const response = await getTrendAnalysis(symbol, params);

      console.log("📊 趋势分析API响应:", response);
      if (response && response.success && response.data) {
        console.log("📊 趋势分析数据成功:", response.data);
        onSuccess(response.data);
      } else {
        console.warn("📊 获取趋势分析数据失败:", {
          symbol,
          intervalCode,
          response,
        });
        if (onFailure) onFailure();
      }
    } catch (err: any) {
      console.error("📊 获取趋势分析数据出错:", {
        symbol,
        intervalCode,
        error: err,
      });
      if (onFailure) onFailure();
    }
  };

  /**
   * 应用主时间框架趋势数据
   */
  const applyPrimaryTrendData = (data: any) => {
    state.trendDirection = data.trendDirection || "sideways";
    state.trendStrength = data.trendStrength || "weak";
    state.trendDuration = "实时";
    state.trendPriceChange = "0.00%";

    state.keyResistance = data.bb20_upper || data.bb50_upper || 0;
    state.keySupport = data.bb20_lower || data.bb50_lower || 0;

    state.swingHighs = (data.swingHighs || []).map((item: any) => ({
      price: item.price,
      timestamp: item.timestamp,
    }));

    state.swingLows = (data.swingLows || []).map((item: any) => ({
      price: item.price,
      timestamp: item.timestamp,
    }));

    if (data.bb20_upper && data.bb20_lower) {
      state.rangeUpper = data.bb20_upper;
      state.rangeLower = data.bb20_lower;
      state.rangeCenter =
        data.bb20_middle || (data.bb20_upper + data.bb20_lower) / 2;

      const range = data.bb20_upper - data.bb20_lower;
      const center = state.rangeCenter;
      state.rangePercent =
        center > 0 ? `${((range / center) * 100).toFixed(2)}%` : "0.00%";
      state.showRangeInfo = true;
    } else {
      state.showRangeInfo = false;
    }
  };

  /**
   * 应用15分钟时间框架趋势数据
   */
  const apply15mTrendData = (data: any) => {
    state.trend15mText =
      data.trendDirection === "uptrend"
        ? "上升"
        : data.trendDirection === "downtrend"
          ? "下降"
          : "震荡";
    state.trend15mStrengthText =
      data.trendStrength === "strong"
        ? "强"
        : data.trendStrength === "medium"
          ? "中"
          : "弱";
    state.trend15mDirection = data.trendDirection || "sideways";
    state.trend15mStrength = data.trendStrength || "weak";

    if (data.bb20_upper && data.bb20_lower) {
      state.range15mUpper = data.bb20_upper;
      state.range15mLower = data.bb20_lower;
      state.range15mCenter =
        data.bb20_middle || (data.bb20_upper + data.bb20_lower) / 2;

      const range = data.bb20_upper - data.bb20_lower;
      const center = state.range15mCenter;
      state.range15mPercent =
        center > 0 ? `${((range / center) * 100).toFixed(2)}%` : "0.00%";
      state.show15mRangeInfo = true;
    } else {
      state.show15mRangeInfo = false;
    }

    state.swing15mHighs = (data.swingHighs || []).map((item: any) => ({
      price: item.price,
      timestamp: item.timestamp,
    }));

    state.swing15mLows = (data.swingLows || []).map((item: any) => ({
      price: item.price,
      timestamp: item.timestamp,
    }));
  };

  /**
   * 应用1小时时间框架趋势数据
   */
  const apply1hTrendData = (data: any) => {
    state.trend1hText =
      data.trendDirection === "uptrend"
        ? "上升"
        : data.trendDirection === "downtrend"
          ? "下降"
          : "震荡";
    state.trend1hStrengthText =
      data.trendStrength === "strong"
        ? "强"
        : data.trendStrength === "medium"
          ? "中"
          : "弱";
    state.trend1hDirection = data.trendDirection || "sideways";
    state.trend1hStrength = data.trendStrength || "weak";

    if (data.bb20_upper && data.bb20_lower) {
      state.range1hUpper = data.bb20_upper;
      state.range1hLower = data.bb20_lower;
      state.range1hCenter =
        data.bb20_middle || (data.bb20_upper + data.bb20_lower) / 2;

      const range = data.bb20_upper - data.bb20_lower;
      const center = state.range1hCenter;
      state.range1hPercent =
        center > 0 ? `${((range / center) * 100).toFixed(2)}%` : "0.00%";
      state.show1hRangeInfo = true;
    } else {
      state.show1hRangeInfo = false;
    }

    state.swing1hHighs = (data.swingHighs || []).map((item: any) => ({
      price: item.price,
      timestamp: item.timestamp,
    }));

    state.swing1hLows = (data.swingLows || []).map((item: any) => ({
      price: item.price,
      timestamp: item.timestamp,
    }));
  };

  /**
   * 重置15分钟趋势数据
   */
  const reset15mTrendData = () => {
    state.trend15mText = "震荡";
    state.trend15mStrengthText = "中等";
    state.trend15mDirection = "sideways";
    state.trend15mStrength = "weak";
    state.range15mUpper = 0;
    state.range15mLower = 0;
    state.range15mCenter = 0;
    state.range15mPercent = "0.00%";
    state.show15mRangeInfo = false;
    state.swing15mHighs = [];
    state.swing15mLows = [];
  };

  /**
   * 重置1小时趋势数据
   */
  const reset1hTrendData = () => {
    state.trend1hText = "震荡";
    state.trend1hStrengthText = "中等";
    state.trend1hDirection = "sideways";
    state.trend1hStrength = "weak";
    state.range1hUpper = 0;
    state.range1hLower = 0;
    state.range1hCenter = 0;
    state.range1hPercent = "0.00%";
    state.show1hRangeInfo = false;
    state.swing1hHighs = [];
    state.swing1hLows = [];
  };

  return {
    isLoading,
    error,
    fetchTrendAnalysis,
    fetchTrendDataForInterval,
    applyPrimaryTrendData,
    apply15mTrendData,
    apply1hTrendData,
    reset15mTrendData,
    reset1hTrendData,
  };
}
