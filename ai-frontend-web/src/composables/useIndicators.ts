/**
 * 技术指标管理 Composable
 * 提供技术指标的开关和配置管理
 */
import { reactive } from "vue";

export interface IndicatorConfig {
  enabled: boolean;
  [key: string]: any;
}

export function useIndicators() {
  const indicators = reactive<Record<string, IndicatorConfig>>({
    boll: {
      enabled: false,
      period: 20,
      multiplier: 2,
    },
    rangeFilter: {
      enabled: false,
      filterType: "Type 1",
      movementSource: "Close",
      rangeSize: 2.618,
      rangeScale: "Average Change",
      rangePeriod: 14,
      smoothRange: true,
      smoothingPeriod: 27,
      averageFilterChanges: true,
      numberOfChangesToAverage: 2,
      showSignals: true,
    },
    macd: {
      enabled: false,
      fastPeriod: 12,
      slowPeriod: 26,
      signalPeriod: 9,
    },
    rsi: {
      enabled: false,
      period: 14,
      showLevels: true,
      showFill: true,
      showMA: false,
      maType: "SMA",
      maLength: 14,
      showBB: false,
      bbStdDev: 2.0,
    },
    reversalConfirmation: {
      enabled: false,
      trendLookback: 7,
      trendStrength: 0.7,
      minMoveATR: 2.0,
      showBullish: true,
      showBearish: true,
      showReversalCandle: true,
      ema3Length: 3,
      ema5Length: 5,
    },
    trendStrength: {
      enabled: false,
      enableCloud: false,
      period: 20,
      multiplier: 2.5,
      gaugeSize: 25,
      upColor: "#00ffbb",
      downColor: "#ff1100",
      candleColor: false,
    },
    phenom: {
      enabled: false,
    },
    kalman: {
      enabled: false,
      shortLen: 50,
      longLen: 150,
      retestSig: false,
      candleColor: true,
      upperColor: "#13bd6e",
      lowerColor: "#af0d4b",
    },
    apexTrendLiquidity: {
      enabled: false,
    },
    smcLite: {
      enabled: false,
    },
    tsm: {
      enabled: false,
      fastLength: 20,
      slowLength: 50,
      divergenceLength: 14,
      showDivergence: true,
    },
    trendStrengthAfterReversal: {
      enabled: false,
    },
    andeanOscillator: {
      enabled: false,
    },
    multiTimeframeTrend: {
      enabled: false,
      timeframe1: 2,
      timeframe2: 4,
      timeframe3: 8,
    },
  });

  /**
   * 切换指标开关
   */
  const toggleIndicator = (indicatorName: string, enabled: boolean) => {
    if (indicators[indicatorName]) {
      indicators[indicatorName].enabled = enabled;
    }
  };

  /**
   * 更新指标配置
   */
  const updateIndicatorConfig = (
    indicatorName: string,
    config: Partial<IndicatorConfig>,
  ) => {
    if (indicators[indicatorName]) {
      Object.assign(indicators[indicatorName], config);
    }
  };

  return {
    indicators,
    toggleIndicator,
    updateIndicatorConfig,
  };
}
