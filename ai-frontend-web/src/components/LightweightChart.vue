<template>
  <div class="lightweight-chart-container">
    <div
      ref="chartContainer"
      :style="{ width: width + 'px', height: height + 'px' }"
    ></div>
  </div>
</template>

<script>
import { createChart } from "lightweight-charts";
import { TimezoneHelper } from "@/utils/TimezoneHelper";
import { ChartController } from "@/utils/kline/ChartController";

export default {
  name: "LightweightChart",
  props: {
    width: {
      type: Number,
      default: 800,
    },
    height: {
      type: Number,
      default: 400,
    },
    data: {
      type: Array,
      default: () => [],
    },
    options: {
      type: Object,
      default: () => ({}),
    },
    signals: {
      type: Array,
      default: () => [],
    },
    boll: {
      type: Object,
      default: () => ({
        enabled: false,
        period: 20,
        multiplier: 2,
      }),
    },
    macd: {
      type: Object,
      default: () => ({
        enabled: false,
        fastPeriod: 12,
        slowPeriod: 26,
        signalPeriod: 9,
        showDivergence: false,
        showMomentum: false,
      }),
    },
    rsi: {
      type: Object,
      default: () => ({
        enabled: false,
        period: 14,
        showLevels: true, // 显示水平线
        showFill: true, // 显示背景填充
        showMA: false, // 显示移动平均线
        maType: "SMA", // MA类型: SMA, EMA, RMA
        maLength: 14, // MA周期
        showBB: false, // 显示布林带
        bbStdDev: 2.0, // 布林带标准差
      }),
    },
    kalman: {
      type: Object,
      default: () => ({
        enabled: false,
        shortLen: 50,
        longLen: 150,
        retestSig: false,
        candleColor: true,
        upperColor: "#13bd6e",
        lowerColor: "#af0d4b",
      }),
    },
    phenom: {
      type: Object,
      default: () => ({
        enabled: false,
        showColor: true,
        emaLines: {
          ema9: {
            enabled: true,
            period: 9,
          },
          ema21: {
            enabled: true,
            period: 21,
          },
          ema55: {
            enabled: false,
            period: 55,
          },
          ema144: {
            enabled: true,
            period: 144,
          },
        },
        showStop: false,
        atrPeriod: 10,
        multiplier: 2.0,
        lastBar: 4,
      }),
    },
    logRegChannel: {
      type: Object,
      default: () => ({
        enabled: false,
        length: 100,
        channelWidth: 1.5,
        channelLen: 100,
        midDisp: true,
        fillBand: true,
        col1: "#21dfac",
        col2: "#df216d",
      }),
    },
    trendStrength: {
      type: Object,
      default: () => ({
        enabled: false,
        enableCloud: false, // 默认不启用云（已移除填充）
        period: 20,
        multiplier: 2.5,
        gaugeSize: 25,
        upColor: "#00ffbb",
        downColor: "#ff1100",
        candleColor: true, // 启用K线颜色（按原始脚本：barcolor）
      }),
    },
    reversalConfirmation: {
      type: Object,
      default: () => ({
        enabled: false,
        trendLookback: 7,
        trendStrength: 0.7,
        minMoveATR: 2.0,
        showBullish: true,
        showBearish: true,
        showReversalCandle: true,
        ema3Length: 3,
        ema5Length: 5,
      }),
    },
    tsm: {
      type: Object,
      default: () => ({
        enabled: false,
        fastLength: 20,
        slowLength: 50,
        divergenceLength: 14,
        showDivergence: true,
      }),
    },
    trendStrengthAfterReversal: {
      type: Object,
      default: () => ({
        enabled: false,
      }),
    },
    andeanOscillator: {
      type: Object,
      default: () => ({
        enabled: false,
        length: 50,
        sigLength: 9,
        showLevels: true,
        earlySignal: true,
      }),
    },
    multiTimeframeTrend: {
      type: Object,
      default: () => ({
        enabled: false,
        timeframe1: 2, // 第一个更高时间框架倍数
        timeframe2: 4, // 第二个更高时间框架倍数
        timeframe3: 8, // 第三个更高时间框架倍数
      }),
    },
    apexTrendLiquidity: {
      type: Object,
      default: () => ({
        enabled: false,
        // 趋势引擎设置
        maType: "HMA",
        mainLength: 55,
        volatilityMultiplier: 1.5,
        source: "close",
        // 流动性设置
        showLiquidity: true,
        pivotLookback: 10,
        zoneExtension: 5,
        // 过滤器设置
        useVolumeFilter: true,
        useRsiFilter: false,
        // 可视化设置
        bullishColor: "#00E676",
        bearishColor: "#FF1744",
        neutralColor: "#78909C",
        textColor: "#FFFFFF",
        colorCandles: true,
        // HUD设置
        showHud: true,
        hudPosition: "Top Right",
        hudSize: "Small",
      }),
    },
    smcLite: {
      type: Object,
      default: () => ({
        enabled: false,
        swingLength: 10,
        historyToKeep: 20,
        boxWidth: 2.5,
        showZigZag: false,
        showPriceActionLabels: false,
        supplyColor: "rgba(237, 237, 237, 0.7)",
        supplyOutlineColor: "rgba(255, 255, 255, 0.75)",
        demandColor: "rgba(0, 255, 255, 0.7)",
        demandOutlineColor: "rgba(255, 255, 255, 0.75)",
        bosLabelColor: "#ffffff",
        poiLabelColor: "#ffffff",
        swingTypeColor: "#000000",
        zigZagColor: "rgba(0, 0, 0, 0)",
      }),
    },
    rangeFilter: {
      type: Object,
      default: () => ({
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
      }),
    },
    dataManager: {
      type: Object,
      default: null,
    },
  },
  data() {
    return {
      chart: null,
      candlestickSeries: null,
      chartController: null, // 图表控制器
      signalMarkers: [], // 存储信号标注
      lastUpdateTime: 0, // 最后更新时间
      updateThrottle: 1500, // 更新节流时间（毫秒），略小于刷新间隔
      latestPriceLine: null, // 最新价格线（用于在价格轴上显示标签）
      crosshairSubscription: null, // crosshair事件订阅句柄
      timeScaleSubscription: null, // 时间刻度变化事件订阅句柄
      signalUpdateTimer: null, // 信号更新定时器
      dataUpdateTimer: null, // 数据更新定时器（用于防抖）
      indicatorUpdateTimer: null, // 🎯 性能优化：指标更新定时器（用于防抖）
      indicatorCache: new Map(), // 🎯 性能优化：指标计算结果缓存
      isApiLoading: false, // API调用状态标志，防止无限循环
      crosshairDebounceTimer: null, // crosshair事件防抖定时器
      handleContextMenu: null, // 右键菜单事件处理函数
      handleClick: null, // 左键点击事件处理函数
      _hasInitialized: false, // 标记是否已初始化（用于 fitContent）
      _rawKlineData: [], // 保存原始K线数据（包含 multiTimeframeTrend 等字段）
      bollSeries: {
        middle: null, // 中轨
        upper: null, // 上轨
        lower: null, // 下轨
      },
      macdPane: null, // MACD面板
      macdSeries: {
        macd: null, // MACD线
        signal: null, // 信号线
        histogram: null, // 柱状图
      },
      macdDivergenceMarkers: [], // MACD背离标记
      macdMomentumMarkers: [], // MACD背驰标记
      rsiSeries: null, // RSI指标
      rsiLevelSeries: [], // RSI水平线
      rsiMASeries: null, // RSI移动平均线
      rsiBBSeries: [], // RSI布林带
      // 超级趋势
      kalmanSeries: {
        short: null, // 短期卡尔曼线
        long: null, // 长期卡尔曼线
        fill: null, // 填充区域
      },
      kalmanElements: {
        labels: [], // 标签
        boxes: [], // 盒子（AreaSeries数组）
        markers: [], // 标记
        priceLines: [], // 价格线（用于模拟盒子）
      },
      phenomSeries: {
        ema1: null, // EMA9
        ema2: null, // EMA21
        ema3: null, // EMA144
        ema4: null, // EMA55
        stopLong: null, // 多头止损线
        stopShort: null, // 空头止损线
      },
      // 流动性指标
      apexTrendLiquiditySeries: {
        upper: null, // 上轨
        lower: null, // 下轨
        fill: null, // 填充区域
        signals: [], // 信号标记
      },
      apexTrendLiquidityElements: {
        supplyZones: [], // 供应区
        demandZones: [], // 需求区
        hudTable: null, // HUD仪表板
      },
      // 对数回归通道
      logRegChannelSeries: {
        base: null, // 中线（虚线）
        up: null, // 上通道线（细线）
        up1: null, // 上通道线（粗线）
        lw: null, // 下通道线（细线）
        lw1: null, // 下通道线（粗线）
        fillUpper: null, // 上填充区域
        fillLower: null, // 下填充区域
        logReg: null, // 对数回归线
      },
      logRegChannelElements: {
        labels: [], // 标签
        markers: [], // 信号标记
      },
      // 趋势强度信号
      trendStrengthSeries: {
        basis: null, // 基准线（SMA）
        upper: null, // 上轨
        lower: null, // 下轨
        upper1: null, // TP上轨
        lower1: null, // TP下轨
        fill: null, // 填充区域
      },
      trendStrengthElements: {
        markers: [], // 信号标记
      },
      // 反转确认指标
      reversalConfirmationSeries: {
        ema3: null, // EMA3线
        ema5: null, // EMA5线
        bullishLine: null, // 看涨反转线
        bearishLine: null, // 看跌反转线
      },
      reversalConfirmationElements: {
        markers: [], // 信号标记
      },
      // 趋势强度表指标
      tsmSeries: {
        trendStrength: null, // 趋势强度直方图
        trendStrengthMA: null, // 趋势强度MA线
      },
      tsmElements: {
        markers: [], // 背离标记
      },
      // 反转后趋势强度指标
      trendStrengthAfterReversalSeries: {
        value: null, // 反转后趋势强度柱状图
      },
      // 安第斯振荡器指标
      andeanOscillatorSeries: {
        osc: null, // 振荡器直方图
        signal: null, // 信号线
        plusLevel: null, // +1σ水平线
        minusLevel: null, // -1σ水平线
        zeroLine: null, // 零线
      },
      andeanOscillatorElements: {
        markers: [], // 信号标记
      },
      // 多时间框架趋势强度指标
      multiTimeframeTrendSeries: {
        dot1: null, // 第一个时间框架圆点系列
        dot2: null, // 第二个时间框架圆点系列
        dot3: null, // 第三个时间框架圆点系列
      },
      multiTimeframeTrendElements: {
        markers: [], // 三个圆点标记
      },
      // SMC Lite
      smcLiteElements: {
        supplyBoxes: [], // 供应区框（使用Area Series）
        demandBoxes: [], // 需求区框（使用Area Series）
        supplyPoi: [], // 供应区POI标签（价格线）
        demandPoi: [], // 需求区POI标签（价格线）
        supplyBos: [], // 供应区BOS（价格线）
        demandBos: [], // 需求区BOS（价格线）
        zigZagLine: null, // Zig Zag线
        priceActionLabels: [], // 价格行为标签
      },
      // Smart Money Concepts
      smartMoneyElements: {
        structures: [], // 结构线条和标签
        orderBlocks: [], // 订单块
        fairValueGaps: [], // 公允价值缺口
        swingPoints: [], // 摆动点
      },
      // Range Filter
      rangeFilterSeries: {
        filter: null, // 过滤器线
        hiBand: null, // 上轨
        loBand: null, // 下轨
      },
      rangeFilterElements: {
        markers: [], // 买卖信号标记
        fill: null, // 填充区域
      },
    };
  },
  mounted() {
    // 组件挂载后初始化图表
    this.initChart();
  },
  beforeUnmount() {
    // 清理资源（严格按照官方文档）
    // 清理图表控制器
    if (this.chartController) {
      this.chartController.destroy();
      this.chartController = null;
    }

    // 使用官方推荐的 remove() 方法清理图表实例
    // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/IChartApi#remove
    if (this.chart) {
      // 使用官方推荐的方法移除事件监听器（返回的函数用于取消订阅）
      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/IChartApi#subscribeCrosshairMove
      if (this.crosshairSubscription) {
        this.crosshairSubscription(); // 调用返回的函数取消订阅
        this.crosshairSubscription = null;
      }

      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#subscribeVisibleTimeRangeChange
      if (this.timeScaleSubscription) {
        this.timeScaleSubscription(); // 调用返回的函数取消订阅
        this.timeScaleSubscription = null;
      }

      // 清理信号更新定时器
      if (this.signalUpdateTimer) {
        clearTimeout(this.signalUpdateTimer);
        this.signalUpdateTimer = null;
      }
      // 清理数据更新定时器
      if (this.dataUpdateTimer) {
        clearTimeout(this.dataUpdateTimer);
        this.dataUpdateTimer = null;
      }
      // 🎯 性能优化：清理指标更新定时器
      if (this.indicatorUpdateTimer) {
        clearTimeout(this.indicatorUpdateTimer);
        this.indicatorUpdateTimer = null;
      }

      // 清理crosshair防抖定时器
      if (this.crosshairDebounceTimer) {
        clearTimeout(this.crosshairDebounceTimer);
        this.crosshairDebounceTimer = null;
      }

      // 移除右键菜单和左键点击事件监听器
      const chartContainer = this.$refs.chartContainer;
      if (chartContainer) {
        chartContainer.removeEventListener(
          "contextmenu",
          this.handleContextMenu,
          true,
        ); // 移除捕获模式监听器
        chartContainer.removeEventListener("mousedown", this.handleClick);
      }

      // 使用官方推荐的 remove() 方法清理图表实例
      // 清理最新价格线
      if (this.latestPriceLine && this.candlestickSeries) {
        try {
          this.candlestickSeries.removePriceLine(this.latestPriceLine);
        } catch (error) {
          console.warn("清理最新价格线失败:", error);
        }
        this.latestPriceLine = null;
      }

      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/IChartApi#remove
      this.chart.remove();
    }
  },
  watch: {
    data: {
      handler(newData) {
        // 🚨 关键修复：如果正在加载数据（API调用中），跳过自动更新，避免拖动时跳动
        if (this.isApiLoading) {
          console.log(
            "⏭️ LightweightChart watch data 跳过：isApiLoading=true，避免拖动时跳动",
          );
          return;
        }

        // 🎯 性能优化：防抖处理数据更新，避免频繁渲染
        if (this.dataUpdateTimer) {
          clearTimeout(this.dataUpdateTimer);
        }

        this.dataUpdateTimer = setTimeout(() => {
          console.log("📊 LightweightChart watch data 触发:", {
            数据量: newData ? newData.length : 0,
            数据示例:
              newData && newData.length > 0 ? newData.slice(0, 3) : "无数据",
          });
          // 数据更新时保持当前视图位置，避免加载历史数据后跳转
          this.updateData(newData, true);
        }, 150); // 150ms防抖，减少频繁更新
      },
      deep: true,
      immediate: false, // 不在初始化时立即执行，避免重复调用
    },
    signals: {
      handler(newSignals) {
        // 🎯 性能优化：大幅增加防抖时间，避免拖动时频繁更新信号
        if (this.signalUpdateTimer) {
          clearTimeout(this.signalUpdateTimer);
        }
        this.signalUpdateTimer = setTimeout(() => {
          // 🎯 性能优化：使用 requestIdleCallback 在浏览器空闲时更新信号
          if (window.requestIdleCallback) {
            window.requestIdleCallback(
              () => {
                this.updateSignals(newSignals);
              },
              { timeout: 1000 },
            );
          } else {
            setTimeout(() => {
              this.updateSignals(newSignals);
            }, 500);
          }
        }, 1000); // 🎯 性能优化：增加防抖时间到1000ms，减少拖动时的频繁更新
      },
      deep: true,
    },
    boll: {
      handler(newBoll, oldBoll) {
        // 🎯 性能优化：浅比较，只有配置真正变化时才更新
        if (this.hasIndicatorConfigChanged(newBoll, oldBoll)) {
          this.debouncedUpdateIndicator("boll", () =>
            this.updateBollIndicator(newBoll),
          );
        }
      },
      deep: false, // 🎯 性能优化：改为浅监听，减少性能开销
    },
    macd: {
      handler(newMacd, oldMacd) {
        if (this.hasIndicatorConfigChanged(newMacd, oldMacd)) {
          this.debouncedUpdateIndicator("macd", () =>
            this.updateMacdIndicator(newMacd),
          );
        }
      },
      deep: false,
    },
    rsi: {
      handler(newRsi, oldRsi) {
        if (this.hasIndicatorConfigChanged(newRsi, oldRsi)) {
          this.debouncedUpdateIndicator("rsi", () =>
            this.updateRsiIndicator(newRsi),
          );
        }
      },
      deep: false,
    },
    kalman: {
      handler(newKalman, oldKalman) {
        if (this.hasIndicatorConfigChanged(newKalman, oldKalman)) {
          this.debouncedUpdateIndicator("kalman", () =>
            this.updateKalmanIndicator(newKalman),
          );
        }
      },
      deep: false, // 🎯 性能优化：改为浅监听
    },
    phenom: {
      handler(newPhenom, oldPhenom) {
        if (this.hasIndicatorConfigChanged(newPhenom, oldPhenom)) {
          this.debouncedUpdateIndicator("phenom", () =>
            this.updatePhenomIndicator(newPhenom),
          );
        }
      },
      deep: false,
    },
    apexTrendLiquidity: {
      handler(newApexTrendLiquidity, oldApexTrendLiquidity) {
        if (
          this.hasIndicatorConfigChanged(
            newApexTrendLiquidity,
            oldApexTrendLiquidity,
          )
        ) {
          this.debouncedUpdateIndicator("apexTrendLiquidity", () =>
            this.updateApexTrendLiquidityIndicator(newApexTrendLiquidity),
          );
        }
      },
      deep: false,
    },
    logRegChannel: {
      handler(newLogRegChannel, oldLogRegChannel) {
        if (
          this.hasIndicatorConfigChanged(newLogRegChannel, oldLogRegChannel)
        ) {
          this.debouncedUpdateIndicator("logRegChannel", () =>
            this.updateLogRegChannelIndicator(newLogRegChannel),
          );
        }
      },
      deep: false,
    },
    trendStrength: {
      handler(newTrendStrength, oldTrendStrength) {
        if (
          this.hasIndicatorConfigChanged(newTrendStrength, oldTrendStrength)
        ) {
          this.debouncedUpdateIndicator("trendStrength", () =>
            this.updateTrendStrengthIndicator(newTrendStrength),
          );
        }
      },
      deep: false,
    },
    reversalConfirmation: {
      handler(newReversalConfirmation, oldReversalConfirmation) {
        if (
          this.hasIndicatorConfigChanged(
            newReversalConfirmation,
            oldReversalConfirmation,
          )
        ) {
          this.debouncedUpdateIndicator("reversalConfirmation", () =>
            this.updateReversalConfirmationIndicator(newReversalConfirmation),
          );
        }
      },
      deep: false,
    },
    tsm: {
      handler(newTsm, oldTsm) {
        if (this.hasIndicatorConfigChanged(newTsm, oldTsm)) {
          this.debouncedUpdateIndicator("tsm", () =>
            this.updateTsmIndicator(newTsm),
          );
        }
      },
      deep: false,
    },
    trendStrengthAfterReversal: {
      handler(newTrendStrengthAfterReversal, oldTrendStrengthAfterReversal) {
        if (
          this.hasIndicatorConfigChanged(
            newTrendStrengthAfterReversal,
            oldTrendStrengthAfterReversal,
          )
        ) {
          this.debouncedUpdateIndicator("trendStrengthAfterReversal", () =>
            this.updateTrendStrengthAfterReversalIndicator(
              newTrendStrengthAfterReversal,
            ),
          );
        }
      },
      deep: false,
    },
    andeanOscillator: {
      handler(newAndeanOscillator, oldAndeanOscillator) {
        if (
          this.hasIndicatorConfigChanged(
            newAndeanOscillator,
            oldAndeanOscillator,
          )
        ) {
          this.debouncedUpdateIndicator("andeanOscillator", () =>
            this.updateAndeanOscillatorIndicator(newAndeanOscillator),
          );
        }
      },
      deep: false,
    },
    multiTimeframeTrend: {
      handler(newMultiTimeframeTrend, oldMultiTimeframeTrend) {
        if (
          this.hasIndicatorConfigChanged(
            newMultiTimeframeTrend,
            oldMultiTimeframeTrend,
          )
        ) {
          this.debouncedUpdateIndicator("multiTimeframeTrend", () =>
            this.updateMultiTimeframeTrendIndicator(newMultiTimeframeTrend),
          );
        }
      },
      deep: false,
    },
    smcLite: {
      handler(newSmcLite, oldSmcLite) {
        if (this.hasIndicatorConfigChanged(newSmcLite, oldSmcLite)) {
          this.debouncedUpdateIndicator("smcLite", () =>
            this.updateSmcLiteIndicator(newSmcLite),
          );
        }
      },
      deep: false,
    },
    rangeFilter: {
      handler(newRangeFilter, oldRangeFilter) {
        if (this.hasIndicatorConfigChanged(newRangeFilter, oldRangeFilter)) {
          this.debouncedUpdateIndicator("rangeFilter", () =>
            this.updateRangeFilterIndicator(newRangeFilter),
          );
        }
      },
      deep: false,
    },
    dataManager: {
      handler(newDataManager) {
        console.log("🔄 LightweightChart watch dataManager 触发:", {
          hasDataManager: !!newDataManager,
          dataManagerType: newDataManager ? typeof newDataManager : "null",
        });

        // 如果之前没有dataManager，现在有了，重新初始化ChartController
        if (newDataManager && !this.chartController) {
          this.initChartController();
        }
      },
      immediate: false,
    },
  },
  methods: {
    /**
     * 初始化图表控制器
     */
    initChartController() {
      console.log(
        "🔧 LightweightChart.initChartController: 准备创建 ChartController",
        {
          hasDataManager: !!this.dataManager,
          hasChart: !!this.chart,
          hasCandlestickSeries: !!this.candlestickSeries,
        },
      );

      if (this.dataManager && this.chart && this.candlestickSeries) {
        try {
          this.chartController = new ChartController(
            this.chart,
            this.candlestickSeries,
            this.dataManager,
          );
          console.log(
            "✅ LightweightChart.initChartController: ChartController 创建成功",
            {
              hasController: !!this.chartController,
            },
          );
        } catch (error) {
          console.error(
            "❌ LightweightChart.initChartController: ChartController 创建失败",
            error,
          );
        }
      } else {
        console.warn(
          "⚠️ LightweightChart.initChartController: 缺少必要条件，无法创建 ChartController",
          {
            hasDataManager: !!this.dataManager,
            hasChart: !!this.chart,
            hasCandlestickSeries: !!this.candlestickSeries,
          },
        );
      }
    },

    /**
     * 初始化图表（严格按照官方文档实现）
     * 参考: https://tradingview.github.io/lightweight-charts/docs/api/functions/createChart
     */
    initChart() {
      if (!this.$refs.chartContainer) {
        console.error("无法找到图表容器元素");
        return;
      }

      // 使用官方推荐的 createChart() 方法创建图表实例
      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/functions/createChart
      // 注意：确保容器有足够高度以显示时间轴（时间轴需要额外空间）
      const chartHeight = Math.max(this.height, 300); // 最小高度300px，确保时间轴有空间
      this.chart = createChart(this.$refs.chartContainer, {
        width: this.width,
        height: chartHeight,

        // 布局配置（TradingView 原生风格）
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/LayoutOptions
        layout: {
          background: {
            type: "solid",
            color: "#FFFFFF", // 白色背景
          },
          textColor: "#131722", // 深灰色文字（TradingView 默认）
          fontSize: 12,
          fontFamily:
            '-apple-system, BlinkMacSystemFont, "Trebuchet MS", Roboto, Ubuntu, sans-serif',
        },

        // 网格配置（TradingView 原生风格）
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/GridOptions
        grid: {
          vertLines: {
            color: "#E0E3EB", // 浅灰色竖线
            style: 0, // 实线
            visible: true,
          },
          horzLines: {
            color: "#E0E3EB", // 浅灰色横线
            style: 0, // 实线
            visible: true,
          },
        },

        // 价格刻度配置（TradingView 原生风格）
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/PriceScaleOptions
        rightPriceScale: {
          borderColor: "#D1D4DC", // 边框颜色
          scaleMargins: {
            top: 0.1,
            bottom: this.macd.enabled ? 0.3 : 0.1,
          },
          autoScale: true,
          entireTextOnly: false, // 允许显示部分文本，确保价格标签可见
          mode: 0, // PriceScaleMode.Normal，确保价格轴正常显示
          // 注意：TradingView Lightweight Charts 的十字线水平线标签会在鼠标离开时隐藏
          // 这是默认行为，无法通过配置禁用。如果需要固定布局，可以考虑：
          // 1. 禁用十字线的水平线（但会失去价格显示功能）
          // 2. 使用自定义价格线替代（createPriceLine with axisLabelVisible: true）
        },

        // 时间刻度配置（严格按照官方文档）
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/TimeScaleOptions
        timeScale: {
          borderColor: "#D1D4DC",
          timeVisible: true, // 显示时间轴（官方推荐，必须为 true）
          secondsVisible: false,
          rightOffset: 0,
          barSpacing: 6, // K线间距
          minBarSpacing: 0.5, // 最小间距
          invertScale: false, // 确保时间轴不反转（最新时间在右边，旧时间在左边）
          // 使用官方推荐的 tickMarkFormatter 自定义时间格式化（UTC+8）
          // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/TimeScaleOptions#tickMarkFormatter
          tickMarkFormatter: TimezoneHelper.getTickMarkFormatter(),
          // 确保时间轴始终可见
          visible: true,
        },

        // 本地化配置（严格按照官方文档）
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/LocalizationOptions
        // 注意：尝试使用自定义格式化函数来控制十字线悬浮提示的时间显示
        localization: {
          locale: "zh-CN",
          dateFormat: "yyyy-MM-dd",
          timeFormat: "HH:mm:ss",
          // 尝试自定义格式化函数
          timeFormatter: function (time) {
            // time 是秒级时间戳，由于我们已经将数据时间戳加上了8小时，这里已经是 UTC+8 时间戳
            // 直接格式化为完整的日期时间格式
            const date = new Date(time * 1000);
            const fullDateTime = date
              .toLocaleString("zh-CN", {
                timeZone: "Asia/Shanghai",
                year: "numeric",
                month: "2-digit",
                day: "2-digit",
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit",
                hour12: false,
              })
              .replace(/\//g, "-");
            return fullDateTime;
          },
          // 或者尝试 priceFormatter，如果需要的话
          priceFormatter: function (price) {
            return parseFloat(price).toFixed(2);
          },
        },

        // 十字线配置（严格按照官方文档）
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/CrosshairOptions
        crosshair: {
          mode: 0, // CrosshairMode.Normal（官方枚举值）
          vertLine: {
            color: "#758696",
            width: 1,
            style: 3, // LineStyle.Dashed（官方枚举值）
            labelVisible: true, // 显示垂直线的时间标签
            labelBackgroundColor: "#131722",
          },
          horzLine: {
            color: "#758696",
            width: 1,
            style: 3, // LineStyle.Dashed（官方枚举值）
            labelVisible: true, // 显示水平线的时间标签
            labelBackgroundColor: "#131722",
            // 注意：TradingView Lightweight Charts 的十字线水平线标签会在鼠标离开图表区域时自动隐藏
            // 这是库的默认行为，无法通过配置禁用。如果这导致布局抖动，可以考虑：
            // 1. 使用固定高度的价格轴容器（通过CSS）
            // 2. 或者接受这个行为（大多数图表库都是这样设计的）
          },
          // 尝试自定义十字线的时间格式化
          // 注意：这个配置可能不被支持，但可以尝试
          timeFormatter: function (time) {
            const date = new Date(time * 1000);
            return date
              .toLocaleString("zh-CN", {
                timeZone: "Asia/Shanghai",
                year: "numeric",
                month: "2-digit",
                day: "2-digit",
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit",
                hour12: false,
              })
              .replace(/\//g, "-");
          },
        },

        // 水印（隐藏）
        watermark: {
          visible: false,
        },

        // 允许合并用户自定义选项
        ...this.options,
      });

      // 使用官方推荐的 addCandlestickSeries() 方法创建K线序列
      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/IChartApi#addCandlestickSeries
      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/CandlestickStyleOptions
      this.candlestickSeries = this.chart.addCandlestickSeries({
        upColor: "#00C853", // 默认上升K线颜色
        downColor: "#FF1744", // 默认下降K线颜色
        borderVisible: false,
        wickUpColor: "#00C853", // 上影线：绿色
        wickDownColor: "#FF1744", // 下影线：红色
        priceFormat: {
          type: "price",
          precision: 2,
          minMove: 0.01,
        },
      });

      // 创建图表控制器（用于管理图表交互和数据加载）
      this.initChartController();

      // 使用官方推荐的 subscribeCrosshairMove() 方法监听十字线移动
      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/IChartApi#subscribeCrosshairMove

      // 初始化MACD面板（如果启用）
      if (this.macd.enabled) {
        this.initMacdPane();
      }

      // 初始化RSI系列（如果启用）
      if (this.rsi.enabled) {
        this.initRsiSeries(this.rsi);
      }

      // 使用官方推荐的 subscribeCrosshairMove() 方法监听十字线移动
      // 简化实现：父组件负责去抖和显示逻辑，保证事件一定发出去
      this.crosshairSubscription = this.chart.subscribeCrosshairMove(
        (param) => {
          if (!param || !param.time || !param.seriesData) {
            // 鼠标离开图表区域，告知父组件恢复到最新K线
            this.$emit("crosshair-move", null);
            return;
          }

          // 优先从蜡烛图系列获取数据，如果拿不到则从所有 seriesData 中兜底取一条
          let candleData = param.seriesData.get(this.candlestickSeries);
          if (!candleData) {
            for (const value of param.seriesData.values()) {
              candleData = value;
              break;
            }
          }
          if (!candleData) return;

          const dataToEmit = {
            time: param.time, // 轻量图返回的 UTC 秒级时间戳
            open: candleData.open,
            high: candleData.high,
            low: candleData.low,
            close: candleData.close,
          };

          this.$emit("crosshair-move", dataToEmit);
        },
      );

      // 使用官方推荐的 subscribeVisibleTimeRangeChange() 方法监听时间刻度变化
      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#subscribeVisibleTimeRangeChange
      // 添加防抖和条件检查，避免在鼠标移动时频繁触发
      let lastTimeRange = null;
      this.timeScaleSubscription = this.chart
        .timeScale()
        .subscribeVisibleTimeRangeChange((timeRange) => {
          // 只在时间范围真正变化时才更新信号标注和指标
          // 避免鼠标移动时频繁触发导致抖动
          if (!timeRange || !lastTimeRange) {
            lastTimeRange = timeRange;
            return;
          }

          // 🎯 性能优化：检查时间范围是否真的发生了变化（允许小误差）
          const fromDiff = Math.abs(
            (timeRange.from || 0) - (lastTimeRange.from || 0),
          );
          const toDiff = Math.abs(
            (timeRange.to || 0) - (lastTimeRange.to || 0),
          );
          const timeDiff = fromDiff + toDiff;

          // 🎯 性能优化：大幅增加阈值，只有明显的时间范围变化才触发更新
          // 从1秒增加到60秒，避免拖动时的频繁处理
          if (timeDiff < 60.0) {
            return;
          }

          console.log("📊 时间范围变化，触发指标重新计算:", {
            from: timeRange.from,
            to: timeRange.to,
            fromDiff: fromDiff,
            toDiff: toDiff,
            timeDiff: timeDiff,
            lastFrom: lastTimeRange.from,
            lastTo: lastTimeRange.to,
          });

          lastTimeRange = { ...timeRange }; // 深拷贝，避免引用问题

          // 🎯 性能优化：大幅增加防抖时间，只在拖动完全停止后才发射事件
          // 完全禁用拖动时的指标计算，避免卡顿
          if (this.signalUpdateTimer) {
            clearTimeout(this.signalUpdateTimer);
          }
          this.signalUpdateTimer = setTimeout(() => {
            // 🚨 修复：检查是否应该发射时间范围变化事件，避免API调用循环
            // 如果当前有API调用正在进行，跳过发射事件
            if (this.isApiLoading) {
              console.log("⏭️ 跳过时间范围变化事件发射，API调用正在进行中");
              return;
            }

            // 🎯 性能优化：使用 requestIdleCallback 在浏览器空闲时发射事件
            // 完全禁用拖动时的指标计算，避免卡顿
            const emitTimeRangeChange = () => {
              if (timeRange.from && timeRange.to) {
                this.$emit("time-range-change", {
                  from: timeRange.from,
                  to: timeRange.to,
                });
              }

              // 🎯 性能优化：完全禁用拖动时的指标计算，避免卡顿
              // 指标计算已经在其他地方处理，这里不需要重复计算
              // this.recalculateAllIndicators() // 已禁用，避免拖动时卡顿
            };

            // 🎯 性能优化：使用更长的延迟，确保拖动完全停止后才执行
            if (window.requestIdleCallback) {
              window.requestIdleCallback(emitTimeRangeChange, {
                timeout: 2000,
              });
            } else {
              // 降级方案：使用 setTimeout，延迟更长时间
              setTimeout(emitTimeRangeChange, 500);
            }
          }, 2000); // 🎯 性能优化：大幅增加防抖时间到2000ms，确保拖动完全停止后才触发
        });

      // 监听右键菜单事件 - 在DOM元素上直接监听
      const chartContainer = this.$refs.chartContainer;
      console.log("初始化右键菜单事件监听器，chartContainer:", chartContainer);
      if (chartContainer) {
        console.log("找到chartContainer，开始设置右键菜单事件监听器");
        this.handleContextMenu = (event) => {
          console.log("DOM右键菜单事件被触发:", event);
          console.log("右键菜单事件监听器已设置，准备发射事件");
          console.log("事件目标:", event.target);
          console.log("事件类型:", event.type);
          event.preventDefault();
          console.log("默认行为已阻止");

          // 获取点击位置的坐标（相对于图表容器）
          const rect = chartContainer.getBoundingClientRect();
          const x = event.clientX - rect.left;
          const y = event.clientY - rect.top;

          console.log("点击坐标:", {
            x,
            y,
            clientX: event.clientX,
            clientY: event.clientY,
          });

          // 尝试获取点击位置的时间和价格
          let clickedTime = null;
          let clickedPrice = null;

          try {
            if (this.chart) {
              const timeScale = this.chart.timeScale();
              const priceScale = this.chart.priceScale();

              clickedTime = timeScale.coordinateToTime(x);
              clickedPrice = priceScale.coordinateToPrice(y);

              console.log("计算得到的时间和价格:", {
                time: clickedTime,
                price: clickedPrice,
              });
            }
          } catch (error) {
            console.warn("获取时间和价格失败:", error);
            clickedTime = Date.now() / 1000;
            clickedPrice = 0;
          }

          // 发射右键菜单事件给父组件
          console.log("发射右键菜单事件给父组件:", {
            time: clickedTime,
            price: clickedPrice,
            point: { x, y },
          });
          this.$emit("context-menu", {
            time: clickedTime,
            price: clickedPrice,
            point: { x, y },
            event: event,
          });
          console.log("右键菜单事件已发射");
        };

        // 监听左键点击事件，用于隐藏右键菜单
        this.handleClick = (event) => {
          console.log("DOM左键点击事件被触发:", event);
          // 检查是否是左键点击 (event.button === 0)
          if (event.button === 0) {
            // 发射左键点击事件给父组件，用于隐藏右键菜单
            this.$emit("chart-click", {
              point: { x: event.clientX, y: event.clientY },
              event: event,
            });
          }
        };

        chartContainer.addEventListener(
          "contextmenu",
          this.handleContextMenu,
          true,
        ); // 使用捕获模式
        chartContainer.addEventListener("mousedown", this.handleClick);
        console.log("右键菜单和左键点击事件监听器已添加到DOM元素");
      } else {
        console.error("无法找到图表容器元素，无法绑定右键菜单");
      }

      // 初始化BOLL指标系列
      this.initBollSeries();

      // 设置数据
      if (this.data && this.data.length > 0) {
        this.updateData(this.data);

        // 初始化时调用 fitContent 显示时间轴和数据
        this.$nextTick(() => {
          try {
            const timeScale = this.chart.timeScale();
            // 先确保时间轴可见
            timeScale.applyOptions({
              timeVisible: true,
            });
            // 然后调用 fitContent
            timeScale.fitContent();
            console.log("✅ 初始化时调用 fitContent 显示时间轴");

            // 再次确保时间轴可见（双重保险）
            setTimeout(() => {
              timeScale.applyOptions({
                timeVisible: true,
              });
              console.log("✅ 再次确保时间轴可见");
            }, 100);
          } catch (error) {
            console.warn("初始化 fitContent 失败:", error);
          }
        });
      }
    },

    updateData(data, preserveView = false) {
      console.log("📊 LightweightChart.updateData 被调用:", {
        有candlestickSeries: !!this.candlestickSeries,
        数据量: data ? data.length : 0,
        数据示例: data && data.length > 0 ? data.slice(0, 3) : "无数据",
        有ChartController: !!this.chartController,
      });

      // 如果 ChartController 还未创建，尝试创建（延迟创建的情况）
      if (
        !this.chartController &&
        this.dataManager &&
        this.chart &&
        this.candlestickSeries
      ) {
        console.log("⚠️ updateData: ChartController 未创建，尝试创建");
        try {
          this.chartController = new ChartController(
            this.chart,
            this.candlestickSeries,
            this.dataManager,
          );
          console.log("✅ updateData: ChartController 创建成功");
        } catch (error) {
          console.error("❌ updateData: ChartController 创建失败", error);
        }
      }

      // 关键修复：如果使用 dataManager，优先使用 dataManager 的完整数据
      // 避免用更少的数据覆盖更多的数据（比如跳转时加载了1300条，但 chartData 只有201条）
      if (this.dataManager && this.candlestickSeries) {
        const currentChartData = this.candlestickSeries.data();
        const managerCache = this.dataManager.getCurrentCache();

        // 如果 dataManager 有更多数据，使用 dataManager 的数据
        if (managerCache && managerCache.length > 0) {
          const newDataCount = data ? data.length : 0;
          const managerDataCount = managerCache.length;
          const currentDataCount = currentChartData
            ? currentChartData.length
            : 0;

          console.log("📊 数据量比较:", {
            新数据量: newDataCount,
            管理器缓存数据量: managerDataCount,
            当前图表数据量: currentDataCount,
          });

          // 如果 dataManager 的数据明显更多，或者新数据明显更少，使用 dataManager 的数据
          if (
            managerDataCount > newDataCount * 1.5 ||
            (newDataCount < currentDataCount * 0.5 &&
              managerDataCount > currentDataCount)
          ) {
            console.log("✅ 使用 dataManager 的完整数据，避免数据丢失");
            data = managerCache;
          }
        }
      }

      if (this.candlestickSeries && data && data.length > 0) {
        // 关键修复：保存原始数据（包含 multiTimeframeTrend 等字段）
        // 因为 lightweight-charts 的 candlestickSeries 只接受 OHLC，会丢失其他字段
        this._rawKlineData = data.filter((item) => {
          // 只保存有效的K线数据
          return (
            item &&
            item.time != null &&
            item.open != null &&
            item.high != null &&
            item.low != null &&
            item.close != null &&
            !isNaN(item.open) &&
            !isNaN(item.high) &&
            !isNaN(item.low) &&
            !isNaN(item.close) &&
            isFinite(item.open) &&
            isFinite(item.high) &&
            isFinite(item.low) &&
            isFinite(item.close)
          );
        });

        // 将数据转换为lightweight-charts格式，并过滤掉包含 null 值的数据点
        // 注意：lightweight-charts 的十字线悬浮提示会将时间戳视为 UTC
        // 我们使用原始 UTC 时间戳，让 TimezoneHelper 处理时区转换
        const formattedData = data
          .map((item) => {
            // 检查所有必需字段是否有效
            if (
              item.time == null ||
              item.open == null ||
              item.high == null ||
              item.low == null ||
              item.close == null
            ) {
              return null;
            }
            // 检查数值是否有效（不是 NaN 或 Infinity）
            if (
              isNaN(item.open) ||
              isNaN(item.high) ||
              isNaN(item.low) ||
              isNaN(item.close) ||
              !isFinite(item.open) ||
              !isFinite(item.high) ||
              !isFinite(item.low) ||
              !isFinite(item.close)
            ) {
              return null;
            }
            // 使用原始 UTC 时间戳，让图表库和 TimezoneHelper 处理时区转换
            return {
              time: item.time, // 使用原始 UTC 时间戳
              open: item.open,
              high: item.high,
              low: item.low,
              close: item.close,
            };
          })
          .filter((item) => item !== null); // 过滤掉 null 值

        // 🔥 关键修复：确保数据按时间升序排列
        // 检查数据是否已排序
        let isSorted = true;
        for (let i = 1; i < formattedData.length; i++) {
          if (formattedData[i].time < formattedData[i - 1].time) {
            isSorted = false;
            console.warn(
              `⚠️ 数据未按时间升序排列，索引 ${i}: time=${formattedData[i].time}, prev time=${formattedData[i - 1].time}`,
            );
            break;
          }
        }

        // 如果数据未排序，进行排序
        if (!isSorted) {
          console.warn("⚠️ 检测到数据未排序，正在修复...");
          formattedData.sort((a, b) => a.time - b.time);
          console.log("✅ 数据已重新排序");
        }

        console.log("📊 转换后的数据格式:", {
          数据量: formattedData.length,
          原始数据量: data.length,
          过滤掉的数据: data.length - formattedData.length,
          数据示例: formattedData.slice(0, 3),
          已排序: isSorted || "已修复",
        });

        // 如果没有有效数据，不更新图表
        if (formattedData.length === 0) {
          console.warn("⚠️ 没有有效的K线数据，跳过更新");
          return;
        }

        // 检查数据连续性和跳空
        if (formattedData.length > 1) {
          let timeGaps = 0;
          let priceGaps = 0;
          const interval = 300; // 5分钟间隔

          for (let i = 1; i < formattedData.length; i++) {
            const prev = formattedData[i - 1];
            const curr = formattedData[i];

            // 再次验证排序（双重检查）
            if (curr.time < prev.time) {
              console.error(
                `❌ 数据排序验证失败，索引 ${i}: time=${curr.time}, prev time=${prev.time}`,
              );
              // 如果仍然乱序，重新排序并警告
              formattedData.sort((a, b) => a.time - b.time);
              console.warn("⚠️ 数据在验证时发现乱序，已重新排序");
              break; // 重新排序后需要重新开始检查
            }

            // 检查时间连续性
            const expectedNext = prev.time + interval;
            if (curr.time > expectedNext + 60) {
              timeGaps++;
            }

            // 检查价格跳空（开盘价与前收盘价差距超过一定比例）
            const priceDiff = Math.abs(curr.open - prev.close);
            const avgPrice = (prev.close + curr.open) / 2;
            const gapPercent = (priceDiff / avgPrice) * 100;

            if (gapPercent > 2) {
              // 超过2%的跳空
              priceGaps++;
            }
          }

          console.log(
            `数据检查: 时间断层=${timeGaps}个, 价格跳空=${priceGaps}个`,
          );
        }

        // 检查数据是否真正发生变化，避免不必要的重新渲染
        const currentData = this.candlestickSeries.data() || [];
        const dataChanged = this.hasDataChanged(currentData, formattedData);

        if (!dataChanged) {
          console.log("📊 数据未发生变化，跳过重新设置");
          return;
        }

        console.log("LightweightChart设置数据:", {
          count: formattedData.length,
          timeRange:
            formattedData.length > 0
              ? {
                  first: new Date(
                    formattedData[0].time * 1000,
                  ).toLocaleString(),
                  last: new Date(
                    formattedData[formattedData.length - 1].time * 1000,
                  ).toLocaleString(),
                }
              : "无数据",
          sample: formattedData.slice(0, 3).map((d) => ({
            time: d.time,
            timeFormatted: new Date(d.time * 1000).toLocaleString(),
            ohlc: [d.open, d.high, d.low, d.close],
          })),
        });

        // 关键优化：如果 preserveView=true，在设置数据前保存视图范围，设置后立即恢复
        let savedVisibleRange = null;
        if (preserveView && this.chart) {
          try {
            const timeScale = this.chart.timeScale();
            savedVisibleRange = timeScale.getVisibleRange();
            console.log(
              "📌 保存视图范围（preserveView=true）:",
              savedVisibleRange,
            );
          } catch (error) {
            console.warn("⚠️ 保存视图范围失败:", error);
          }
        }

        this.candlestickSeries.setData(formattedData);

        // 更新最新价格标签（如果有数据）
        // 使用 $nextTick 确保图表已更新后再创建价格线
        if (formattedData && formattedData.length > 0) {
          const latestKline = formattedData[formattedData.length - 1];
          this.$nextTick(() => {
            this.updateLatestPriceLabel(latestKline.close);
          });
        }

        // 如果 preserveView=true，使用 requestAnimationFrame 在下一帧恢复视图范围
        // 确保在浏览器重绘之前恢复，避免闪烁和缩放
        if (preserveView && savedVisibleRange && this.chart) {
          // 立即尝试恢复一次（同步）
          try {
            const timeScale = this.chart.timeScale();
            timeScale.setVisibleRange(savedVisibleRange);
          } catch (error) {
            console.warn("⚠️ 同步恢复视图范围失败:", error);
          }

          // 使用 requestAnimationFrame 在下一帧再次恢复，确保视图范围被正确设置
          requestAnimationFrame(() => {
            try {
              const timeScale = this.chart.timeScale();
              timeScale.setVisibleRange(savedVisibleRange);
              console.log("✅ 已恢复视图范围（preserveView=true）");
            } catch (error) {
              console.warn("⚠️ requestAnimationFrame 恢复视图范围失败:", error);
            }
          });
        }

        // 关键修复：检查是否正在进行时间跳转，如果是，不要调用 fitContent
        // 避免覆盖跳转时设置的可见范围
        const isJumping =
          this.chartController && this.chartController._isJumping;

        // 数据更新后，手动调整价格轴范围（因为禁用了 autoScale）
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesApi#priceScale
        this.$nextTick(() => {
          try {
            const timeScale = this.chart.timeScale();

            // 如果正在进行时间跳转，只确保时间轴可见，不调用 fitContent
            if (isJumping) {
              console.log(
                "⏸️ 检测到时间跳转进行中，跳过 fitContent，保持当前视图范围",
              );
              timeScale.applyOptions({
                timeVisible: true,
              });
            } else {
              // 使用 applyOptions 确保时间轴可见
              // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#applyOptions
              timeScale.applyOptions({
                timeVisible: true,
              });

              // 根据 preserveView 参数决定是否调用 fitContent
              // preserveView = true 时保持当前视图位置（用于加载历史数据）
              // preserveView = false 时调用 fitContent 调整显示范围
              if (!preserveView) {
                // 延迟调用 fitContent，确保数据已经渲染完成
                // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/IChartApi#timeScale
                setTimeout(() => {
                  try {
                    // 使用 fitContent 自动调整图表显示范围
                    // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/IChartApi#timeScale
                    this.chart.timeScale().fitContent();
                    console.log("📊 updateData: 调用 fitContent 调整显示范围");
                  } catch (e) {
                    console.warn("fitContent 调用失败:", e);
                  }
                }, 100);
              } else {
                console.log(
                  "📊 updateData: preserveView=true，跳过 fitContent，保持当前视图",
                );
              }
            }
          } catch (e) {
            console.warn("价格轴调整失败:", e);
          }
        });
      }
    },

    /**
     * 检查数据是否发生变化，避免不必要的重新渲染
     */
    hasDataChanged(currentData, newData) {
      // 如果数据长度不同，肯定发生了变化
      if (currentData.length !== newData.length) {
        return true;
      }

      // 如果都没有数据，不算变化
      if (currentData.length === 0 && newData.length === 0) {
        return false;
      }

      // 检查前几个数据点是否相同（通常变化发生在最新数据）
      const checkCount = Math.min(5, currentData.length, newData.length);

      for (let i = 0; i < checkCount; i++) {
        const current = currentData[i];
        const newItem = newData[i];

        if (!current || !newItem) {
          return true;
        }

        // 比较关键字段
        if (
          current.time !== newItem.time ||
          current.open !== newItem.open ||
          current.high !== newItem.high ||
          current.low !== newItem.low ||
          current.close !== newItem.close
        ) {
          return true;
        }
      }

      // 检查最后几个数据点（新数据通常添加到末尾）
      const startIndex = Math.max(0, currentData.length - checkCount);
      for (let i = startIndex; i < currentData.length; i++) {
        const current = currentData[i];
        const newItem = newData[i];

        if (!current || !newItem) {
          return true;
        }

        // 比较关键字段
        if (
          current.time !== newItem.time ||
          current.open !== newItem.open ||
          current.high !== newItem.high ||
          current.low !== newItem.low ||
          current.close !== newItem.close
        ) {
          return true;
        }
      }

      return false;
    },

    /**
     * 检查标记是否发生变化，避免不必要的重新渲染
     */
    hasMarkersChanged(currentMarkers, newMarkers) {
      // 如果标记数量不同，肯定发生了变化
      if (currentMarkers.length !== newMarkers.length) {
        return true;
      }

      // 如果都没有标记，不算变化
      if (currentMarkers.length === 0 && newMarkers.length === 0) {
        return false;
      }

      // 检查所有标记是否相同
      for (let i = 0; i < currentMarkers.length; i++) {
        const current = currentMarkers[i];
        const newMarker = newMarkers[i];

        if (!current || !newMarker) {
          return true;
        }

        // 比较关键字段
        if (
          current.time !== newMarker.time ||
          current.position !== newMarker.position ||
          current.color !== newMarker.color ||
          current.shape !== newMarker.shape ||
          current.text !== newMarker.text ||
          current.size !== newMarker.size
        ) {
          return true;
        }
      }

      return false;
    },

    /**
     * 🎯 性能优化：防抖更新指标
     */
    debouncedUpdateIndicator(indicatorName, updateFn) {
      if (this.indicatorUpdateTimer) {
        clearTimeout(this.indicatorUpdateTimer);
      }

      this.indicatorUpdateTimer = setTimeout(() => {
        // 使用 requestIdleCallback 在浏览器空闲时更新
        if (window.requestIdleCallback) {
          window.requestIdleCallback(updateFn, { timeout: 500 });
        } else {
          setTimeout(updateFn, 100);
        }
      }, 300); // 300ms防抖，减少频繁更新
    },

    /**
     * 🎯 性能优化：检查指标配置是否真正变化
     */
    hasIndicatorConfigChanged(newConfig, oldConfig) {
      if (!newConfig && !oldConfig) return false;
      if (!newConfig || !oldConfig) return true;

      // 只比较关键配置项，避免深度比较
      return (
        newConfig.enabled !== oldConfig.enabled ||
        JSON.stringify(newConfig) !== JSON.stringify(oldConfig)
      );
    },

    /**
     * 重新计算所有已启用的指标（用于拖动图表时更新指标）
     * 🎯 性能优化：使用 requestIdleCallback 延迟计算
     */
    recalculateAllIndicators() {
      if (!this.candlestickSeries) {
        return;
      }

      // 获取当前图表中的所有K线数据
      const allKlineData = this.candlestickSeries.data();
      if (!allKlineData || allKlineData.length === 0) {
        return;
      }

      // 🎯 性能优化：使用 requestIdleCallback 延迟计算，避免阻塞主线程
      const calculateIndicators = () => {
        console.log("🔄 重新计算所有指标，数据量:", allKlineData.length);

        // 重新计算所有已启用的指标
        if (this.boll && this.boll.enabled) {
          this.updateBollIndicator(this.boll);
        }

        if (this.macd && this.macd.enabled) {
          this.updateMacdData(this.macd);
        }

        if (this.kalman && this.kalman.enabled) {
          // 使用图表中的实际数据重新计算超级趋势指标
          this.updateKalmanIndicator(this.kalman, allKlineData);
        }

        if (this.phenom && this.phenom.enabled) {
          this.updatePhenomIndicator(this.phenom);
        }

        if (this.apexTrendLiquidity && this.apexTrendLiquidity.enabled) {
          this.updateApexTrendLiquidityIndicator(this.apexTrendLiquidity);
        }

        if (this.logRegChannel && this.logRegChannel.enabled) {
          this.updateLogRegChannelIndicator(this.logRegChannel);
        }

        if (this.trendStrength && this.trendStrength.enabled) {
          this.updateTrendStrengthIndicator(this.trendStrength);
        }

        if (this.reversalConfirmation && this.reversalConfirmation.enabled) {
          this.updateReversalConfirmationIndicator(this.reversalConfirmation);
        }

        if (this.tsm && this.tsm.enabled) {
          this.updateTsmIndicator(this.tsm);
        }

        if (
          this.trendStrengthAfterReversal &&
          this.trendStrengthAfterReversal.enabled
        ) {
          this.updateTrendStrengthAfterReversalIndicator(
            this.trendStrengthAfterReversal,
          );
        }

        if (this.andeanOscillator && this.andeanOscillator.enabled) {
          this.updateAndeanOscillatorIndicator(this.andeanOscillator);
        }

        if (this.multiTimeframeTrend && this.multiTimeframeTrend.enabled) {
          this.updateMultiTimeframeTrendIndicator(this.multiTimeframeTrend);
        }

        if (this.smcLite && this.smcLite.enabled) {
          this.updateSmcLiteIndicator(this.smcLite);
        }

        if (this.rangeFilter && this.rangeFilter.enabled) {
          this.updateRangeFilterIndicator(this.rangeFilter);
        }
      };

      if (window.requestIdleCallback) {
        window.requestIdleCallback(calculateIndicators, { timeout: 1000 });
      } else {
        setTimeout(calculateIndicators, 200);
      }
    },

    // 添加标记线
    addMarker(marker) {
      if (this.candlestickSeries) {
        this.candlestickSeries.setMarkers([marker]);
      }
    },

    // 添加指标线
    addLineSeries(data, options = {}) {
      const lineSeries = this.chart.addLineSeries({
        color: "#2196F3",
        lineWidth: 2,
        ...options,
      });

      const formattedData = data.map((item) => ({
        time: item.time,
        value: item.value,
      }));

      lineSeries.setData(formattedData);
      return lineSeries;
    },

    // 调整图表大小
    resize(width, height) {
      if (this.chart) {
        this.chart.applyOptions({
          width: width || this.width,
          height: height || this.height,
        });
      }
    },

    /**
     * 跳转到指定时间
     * 使用 ChartController 实现，支持闪烁提示
     * @param {number} targetTime - 目标时间戳（UTC，秒）
     * @param {object} options - 跳转选项
     * @param {string} options.position - 目标时间在视图中的位置：'start' | 'center' | 'end'，默认'center'
     * @param {boolean} options.showFlashMarker - 是否显示闪烁提示，默认true
     * @param {number} options.flashDuration - 闪烁持续时间（毫秒），默认3000
     * @returns {Promise<boolean>} 是否成功
     */
    async jumpToTime(targetTime, options = {}) {
      // 如果 ChartController 已创建，使用它
      if (this.chartController) {
        return await this.chartController.jumpToTime(targetTime, options);
      }

      // 否则使用简化版本（向后兼容）
      if (!this.chart) {
        console.warn("图表未初始化，无法跳转");
        return false;
      }

      try {
        const timeScale = this.chart.timeScale();
        const seriesData = this.candlestickSeries
          ? this.candlestickSeries.data()
          : [];

        if (!seriesData || seriesData.length === 0) {
          return false;
        }

        const firstTime = seriesData[0].time;
        const lastTime = seriesData[seriesData.length - 1].time;

        if (targetTime < firstTime || targetTime > lastTime) {
          console.warn("目标时间不在数据范围内");
          return false;
        }

        // 计算显示范围（目标时间居中）
        const interval = 300; // 默认5分钟
        const rangeLength = interval * 150;
        const displayRange = {
          from: Math.max(targetTime - rangeLength / 2, firstTime),
          to: Math.min(targetTime + rangeLength / 2, lastTime),
        };

        // 设置可见范围
        timeScale.setVisibleRange(displayRange);

        return true;
      } catch (error) {
        console.error("时间跳转失败:", error);
        return false;
      }
    },

    /**
     * 获取图表控制器
     * @returns {ChartController|null}
     */
    getController() {
      return this.chartController;
    },

    /**
     * 获取当前可见的时间范围
     * @returns {object|null} 包含from和to的时间范围对象
     */
    getVisibleTimeRange() {
      if (!this.chart) {
        return null;
      }

      try {
        const timeScale = this.chart.timeScale();
        return timeScale.getVisibleRange();
      } catch (error) {
        console.error("获取可见时间范围失败:", error);
        return null;
      }
    },

    /**
     * 设置可见的时间范围
     * @param {object} range - 包含from和to的时间范围对象
     * @returns {boolean} 是否成功
     */
    setVisibleTimeRange(range) {
      if (!this.chart || !range) {
        return false;
      }

      try {
        const timeScale = this.chart.timeScale();
        timeScale.setVisibleRange(range);
        return true;
      } catch (error) {
        console.error("设置可见时间范围失败:", error);
        return false;
      }
    },

    /**
     * 更新最新K线数据（保持用户视图位置，不自动跳转）
     * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesApi#update
     */
    updateLatestKLine(newKLineData) {
      if (!this.chart || !this.candlestickSeries) return;

      // 节流检查：避免过于频繁的更新
      // 关键修复：只有在数据完全相同且更新过于频繁时才跳过
      const now = Date.now();
      const timeSinceLastUpdate = now - this.lastUpdateTime;

      // 获取当前图表数据，检查新数据是否与最后一条完全相同
      const currentData = this.candlestickSeries.data();
      if (currentData && currentData.length > 0) {
        const lastData = currentData[currentData.length - 1];
        const isDataIdentical =
          lastData &&
          lastData.time === newKLineData.time &&
          lastData.open === newKLineData.open &&
          lastData.high === newKLineData.high &&
          lastData.low === newKLineData.low &&
          lastData.close === newKLineData.close;

        // 如果数据完全相同且更新过于频繁，才跳过
        if (isDataIdentical && timeSinceLastUpdate < this.updateThrottle) {
          console.log(
            `跳过更新：数据完全相同且更新过于频繁（距离上次更新 ${timeSinceLastUpdate}ms）`,
          );
          return;
        }
      } else if (timeSinceLastUpdate < this.updateThrottle) {
        // 如果没有数据，但更新过于频繁，也跳过
        console.log(
          `跳过更新：更新过于频繁（距离上次更新 ${timeSinceLastUpdate}ms）`,
        );
        return;
      }

      this.lastUpdateTime = now;

      console.log("更新最新K线数据:", newKLineData);

      // 获取当前可见范围，用于保持用户视图位置
      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#getVisibleRange
      const timeScale = this.chart.timeScale();
      const currentVisibleRange = timeScale.getVisibleRange();

      // 关键修复：检查是否在跳转锁定期间，如果是则不自动滚动
      const chartController = this.getController();
      const timeSinceLastJump = chartController
        ? Date.now() - chartController._lastJumpTime
        : Infinity;
      const isInJumpLock =
        chartController &&
        timeSinceLastJump < chartController._jumpLockDuration;

      // 判断是否在查看最新数据（允许60秒误差）
      // 关键修复：如果在跳转锁定期间，即使时间接近最新，也不认为是"查看最新"
      const isViewingLatest =
        !isInJumpLock &&
        currentVisibleRange &&
        currentVisibleRange.to >= newKLineData.time - 60;

      // 注意：currentData 已在节流检查部分获取，这里直接使用

      if (currentData && currentData.length > 0) {
        // 检查并清理现有数据中的 null 值
        const validData = currentData.filter(
          (d) =>
            d != null &&
            d.time != null &&
            d.open != null &&
            d.high != null &&
            d.low != null &&
            d.close != null &&
            !isNaN(d.open) &&
            !isNaN(d.high) &&
            !isNaN(d.low) &&
            !isNaN(d.close) &&
            isFinite(d.open) &&
            isFinite(d.high) &&
            isFinite(d.low) &&
            isFinite(d.close),
        );

        // 如果发现无效数据，重新设置数据
        if (validData.length !== currentData.length) {
          console.warn(
            `⚠️ 检测到现有数据中包含 ${currentData.length - validData.length} 个无效数据点，正在清理...`,
          );
          if (validData.length > 0) {
            this.candlestickSeries.setData(validData);
          } else {
            console.error("❌ 清理后没有有效数据，无法继续更新");
            return;
          }
        }

        const lastIndex = validData.length - 1;
        const lastDataPoint = validData[lastIndex];

        // 验证 lastDataPoint 的有效性
        if (
          !lastDataPoint ||
          lastDataPoint.time == null ||
          lastDataPoint.open == null ||
          lastDataPoint.high == null ||
          lastDataPoint.low == null ||
          lastDataPoint.close == null
        ) {
          console.warn("⚠️ 最后一条K线数据无效，跳过更新:", lastDataPoint);
          return;
        }

        // 检查是否需要更新数据
        let needsUpdate = false;

        if (newKLineData.time === lastDataPoint.time) {
          // 同一时间点：更新最后一条K线
          // 关键修复：即使价格相同也要更新，因为可能有其他变化（如成交量等）
          // 或者至少允许在节流时间外强制更新
          if (
            newKLineData.close !== lastDataPoint.close ||
            newKLineData.high !== lastDataPoint.high ||
            newKLineData.low !== lastDataPoint.low ||
            newKLineData.open !== lastDataPoint.open
          ) {
            needsUpdate = true;
            console.log("更新最后一条K线数据（价格有变化）");
          } else {
            // 即使价格相同，如果距离上次更新超过节流时间，也允许更新（确保实时性）
            const timeSinceLastUpdate = Date.now() - this.lastUpdateTime;
            if (timeSinceLastUpdate >= this.updateThrottle) {
              needsUpdate = true;
              console.log("更新最后一条K线数据（价格相同但需要刷新）");
            } else {
              console.log("跳过更新：价格相同且更新过于频繁");
            }
          }
        } else if (newKLineData.time > lastDataPoint.time) {
          // 新时间点：添加新K线
          // 检查是否有价格跳空
          const priceDiff = Math.abs(newKLineData.open - lastDataPoint.close);
          const avgPrice = (lastDataPoint.close + newKLineData.open) / 2;
          const gapPercent = (priceDiff / avgPrice) * 100;

          if (gapPercent > 2) {
            console.warn(
              `实时更新检测到价格跳空: 前收=${lastDataPoint.close}, 新开=${newKLineData.open}, 差距=${gapPercent.toFixed(2)}%`,
            );
          }

          // 对齐新K线时间戳到正确的间隔位置
          const alignedTime = this.alignKLineTimestamp(
            newKLineData.time,
            lastDataPoint.time,
          );
          if (alignedTime !== newKLineData.time) {
            console.log(
              `时间戳对齐: 原始=${newKLineData.time}, 对齐后=${alignedTime}`,
            );
            newKLineData.time = alignedTime;
          }

          needsUpdate = true;
          console.log("添加新的K线数据点");
        }

        // 使用官方推荐的 update() 方法进行实时更新
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesApi#update
        if (needsUpdate) {
          // 关键修复：保存当前视图范围（如果用户不在查看最新数据，或者在跳转锁定期间，需要恢复）
          // 如果在跳转锁定期间，强制保持视图位置
          const shouldPreserveView =
            isInJumpLock || (currentVisibleRange && !isViewingLatest);

          // 验证数据有效性，确保没有 null 值
          if (
            newKLineData.time == null ||
            newKLineData.open == null ||
            newKLineData.high == null ||
            newKLineData.low == null ||
            newKLineData.close == null
          ) {
            console.warn("⚠️ 新K线数据包含 null 值，跳过更新:", newKLineData);
            return;
          }

          // 检查数值是否有效（不是 NaN 或 Infinity）
          if (
            isNaN(newKLineData.open) ||
            isNaN(newKLineData.high) ||
            isNaN(newKLineData.low) ||
            isNaN(newKLineData.close) ||
            !isFinite(newKLineData.open) ||
            !isFinite(newKLineData.high) ||
            !isFinite(newKLineData.low) ||
            !isFinite(newKLineData.close)
          ) {
            console.warn("⚠️ 新K线数据包含无效数值，跳过更新:", newKLineData);
            return;
          }

          try {
            // 关键修复：如果在跳转锁定期间，使用 setData 而不是 update，避免自动滚动
            // 因为 update() 方法在添加新数据时可能会触发自动滚动到最新位置
            if (isInJumpLock) {
              // 在跳转锁定期间，使用 setData 更新数据，避免自动滚动
              const allData = [...validData];
              if (newKLineData.time === lastDataPoint.time) {
                // 更新最后一条
                allData[allData.length - 1] = {
                  time: newKLineData.time,
                  open: newKLineData.open,
                  high: newKLineData.high,
                  low: newKLineData.low,
                  close: newKLineData.close,
                };
                console.log(
                  "✅ [跳转锁定] 使用 setData() 更新最新K线（相同时间），避免自动滚动",
                );
              } else if (newKLineData.time > lastDataPoint.time) {
                // 添加新数据
                allData.push({
                  time: newKLineData.time,
                  open: newKLineData.open,
                  high: newKLineData.high,
                  low: newKLineData.low,
                  close: newKLineData.close,
                });
                console.log(
                  "✅ [跳转锁定] 使用 setData() 添加新K线（新时间），避免自动滚动",
                );
              }
              this.candlestickSeries.setData(allData);
            } else {
              // 不在跳转锁定期间，使用 update() 方法（更高效）
              // 按照官方示例，直接使用 update() 方法
              // update() 会自动判断：如果时间相同则更新最后一条K线，如果时间不同则添加新K线
              // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesApi#update
              this.candlestickSeries.update({
                time: newKLineData.time,
                open: newKLineData.open,
                high: newKLineData.high,
                low: newKLineData.low,
                close: newKLineData.close,
              });

              if (newKLineData.time === lastDataPoint.time) {
                console.log("✅ 使用 update() 方法更新最新K线（相同时间）");
              } else if (newKLineData.time > lastDataPoint.time) {
                console.log("✅ 使用 update() 方法添加新K线（新时间）");
              }
            }

            // 更新最新价格标签（在右侧价格轴上显示）
            // 使用 $nextTick 确保图表已更新后再创建价格线
            this.$nextTick(() => {
              this.updateLatestPriceLabel(newKLineData.close);
            });
          } catch (error) {
            console.error("❌ 更新K线数据失败:", error, {
              newKLineData,
              lastDataPoint,
              currentDataLength: validData.length,
            });
            // 如果更新失败，尝试重新设置所有数据
            try {
              const allData = [...validData];
              if (newKLineData.time === lastDataPoint.time) {
                // 更新最后一条
                allData[allData.length - 1] = {
                  time: newKLineData.time,
                  open: newKLineData.open,
                  high: newKLineData.high,
                  low: newKLineData.low,
                  close: newKLineData.close,
                };
              } else if (newKLineData.time > lastDataPoint.time) {
                // 添加新数据
                allData.push({
                  time: newKLineData.time,
                  open: newKLineData.open,
                  high: newKLineData.high,
                  low: newKLineData.low,
                  close: newKLineData.close,
                });
              }
              this.candlestickSeries.setData(allData);
              console.log("✅ 使用 setData() 方法恢复数据");

              // 更新最新价格标签
              this.$nextTick(() => {
                this.updateLatestPriceLabel(newKLineData.close);
              });
            } catch (setDataError) {
              console.error("❌ 使用 setData() 恢复数据也失败:", setDataError);
            }
          }

          // 关键修复：如果用户在跳转锁定期间，或者不在查看最新数据，恢复之前的视图范围（保持用户拖拽位置）
          // 使用多次恢复，确保视图位置不被改变
          if (shouldPreserveView || isInJumpLock) {
            // 立即恢复一次
            try {
              if (currentVisibleRange) {
                timeScale.setVisibleRange(currentVisibleRange);
                if (isInJumpLock) {
                  console.log("✅ [跳转锁定] 立即恢复视图位置，不自动跳转");
                } else {
                  console.log("✅ 立即恢复视图位置，不自动跳转");
                }
              }
            } catch (error) {
              console.warn("立即恢复视图范围失败:", error);
            }

            // 在 nextTick 中再次恢复，确保视图位置不被后续更新改变
            this.$nextTick(() => {
              try {
                if (currentVisibleRange) {
                  timeScale.setVisibleRange(currentVisibleRange);
                  if (isInJumpLock) {
                    console.log(
                      "✅ [跳转锁定] nextTick 中恢复视图位置，不自动跳转",
                    );
                  } else {
                    console.log("✅ nextTick 中恢复视图位置，不自动跳转");
                  }
                }
              } catch (error) {
                console.warn("nextTick 恢复视图范围失败:", error);
              }
            });

            // 关键修复：延迟再次恢复，防止 update() 方法触发自动滚动
            setTimeout(() => {
              try {
                if (currentVisibleRange) {
                  const currentRange = timeScale.getVisibleRange();
                  // 检查当前视图范围是否被改变
                  if (
                    currentRange &&
                    (Math.abs(currentRange.from - currentVisibleRange.from) >
                      60 ||
                      Math.abs(currentRange.to - currentVisibleRange.to) > 60)
                  ) {
                    timeScale.setVisibleRange(currentVisibleRange);
                    if (isInJumpLock) {
                      console.log(
                        "✅ [跳转锁定] 延迟恢复视图位置（检测到视图被改变），不自动跳转",
                      );
                    } else {
                      console.log(
                        "✅ 延迟恢复视图位置（检测到视图被改变），不自动跳转",
                      );
                    }
                  }
                }
              } catch (error) {
                console.warn("延迟恢复视图范围失败:", error);
              }
            }, 100); // 延迟100ms恢复

            // 关键修复：在跳转锁定期间，持续监控并恢复视图位置
            if (isInJumpLock) {
              let restoreCount = 0;
              const maxRestoreCount = 20; // 最多恢复20次（2秒内，每100ms一次）
              const restoreInterval = setInterval(() => {
                restoreCount++;
                if (restoreCount > maxRestoreCount) {
                  clearInterval(restoreInterval);
                  console.log("✅ [跳转锁定] 视图位置恢复监控结束");
                  return;
                }

                try {
                  if (currentVisibleRange) {
                    const currentRange = timeScale.getVisibleRange();
                    // 检查当前视图范围是否被改变
                    if (
                      currentRange &&
                      (Math.abs(currentRange.from - currentVisibleRange.from) >
                        60 ||
                        Math.abs(currentRange.to - currentVisibleRange.to) > 60)
                    ) {
                      timeScale.setVisibleRange(currentVisibleRange);
                      console.log(
                        `✅ [跳转锁定] 第${restoreCount}次恢复视图位置（检测到视图被改变），不自动跳转`,
                      );
                    }
                  }
                } catch (error) {
                  console.warn(`第${restoreCount}次恢复视图范围失败:`, error);
                }
              }, 100); // 每100ms检查一次
            }
          }

          // 实时K线更新后，更新BOLL指标
          if (this.boll && this.boll.enabled) {
            console.log("实时K线更新，重新计算BOLL指标");
            this.updateBollIndicator(this.boll);
          }
        } else {
          console.log("数据无变化，跳过更新");
        }
      } else {
        // 没有现有数据，使用官方推荐的 setData() 方法设置初始数据
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesApi#setData
        this.candlestickSeries.setData([
          {
            time: newKLineData.time,
            open: newKLineData.open,
            high: newKLineData.high,
            low: newKLineData.low,
            close: newKLineData.close,
          },
        ]);
        console.log("✅ 使用官方 setData() 方法设置初始K线数据");

        // 初始化最新价格标签
        this.$nextTick(() => {
          this.updateLatestPriceLabel(newKLineData.close);
        });
      }
    },

    /**
     * 更新最新价格标签（在右侧价格轴上显示）
     * @param {number} price - 最新价格
     */
    updateLatestPriceLabel(price) {
      if (
        !this.candlestickSeries ||
        price == null ||
        isNaN(price) ||
        !isFinite(price)
      ) {
        return;
      }

      try {
        // 如果已存在价格线，先移除
        if (this.latestPriceLine) {
          try {
            this.candlestickSeries.removePriceLine(this.latestPriceLine);
          } catch (e) {
            // 忽略移除失败的错误
          }
          this.latestPriceLine = null;
        }

        // 创建新的价格线，显示在右侧价格轴上
        // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/PriceLineOptions
        // 注意：axisLabelVisible 用于在价格轴上显示标签，title 用于在价格线上显示标签
        // 为了只在价格轴上显示标签，我们需要：
        // 1. 设置 axisLabelVisible: true（在价格轴上显示标签）
        // 2. 不设置 title（避免在价格线上显示标签）
        // 3. 可以设置 lineVisible: false 来隐藏价格线本身（可选）
        const priceLineOptions = {
          price: price,
          color: "#758696", // 与十字线颜色一致
          lineWidth: 1,
          lineStyle: 3, // LineStyle.Dashed
          lineVisible: true, // 保留价格线（虚线），但标签显示在价格轴上
          // 不设置 title，这样标签就不会显示在价格线上
          // 使用 axisLabelVisible 在价格轴上显示标签
          axisLabelVisible: true,
          axisLabelColor: "#758696",
          axisLabelTextColor: "#ffffff",
          axisLabelBackgroundColor: "#131722",
        };

        console.log("🔍 创建价格线，参数:", priceLineOptions);
        this.latestPriceLine =
          this.candlestickSeries.createPriceLine(priceLineOptions);
        console.log("✅ 价格线创建成功:", this.latestPriceLine, "价格:", price);

        // 验证价格线是否创建成功
        if (!this.latestPriceLine) {
          console.error("❌ 价格线创建失败，返回值为 null");
        } else {
          // 尝试获取价格线的属性，验证是否创建成功
          console.log("🔍 价格线对象详情:", {
            hasPriceLine: !!this.latestPriceLine,
            priceLineType: typeof this.latestPriceLine,
          });
        }
      } catch (error) {
        console.error("❌ 更新最新价格标签失败:", error, { price });
      }
    },

    /**
     * 更新信号标注（使用官方推荐的 setMarkers() 方法）
     * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesMarkersPluginApi#setMarkers
     * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/SeriesMarker
     *
     * @param {Array} signals - 信号数据数组
     */
    updateSignals(signals) {
      if (!this.chart || !this.candlestickSeries) {
        return;
      }

      const formatSignalLabelWeightOnly = (input) => {
        const text = String(input ?? "");
        const match = text.match(
          /^(LB|SB|LS|SS)\(\s*([-+]?\d*\.?\d+)(?:\s*,\s*[-+]?\d*\.?\d+)?\s*\)(.*)$/i,
        );
        if (!match) return text;
        const type = String(match[1] || "").toUpperCase();
        const weight = String(match[2] ?? "").trim();
        const tail = String(match[3] ?? "");
        if (!weight) return text;
        return `${type}(${weight})${tail}`;
      };

      // 🎯 性能优化：限制信号数量，避免渲染过多导致卡顿
      // 根据可见范围动态限制信号数量，提升拖动性能
      const maxSignalsToRender = 200; // 最多渲染200个信号（从500减少到200，提升性能）
      let signalsToRender = signals || [];
      if (signalsToRender.length > maxSignalsToRender) {
        // 如果信号过多，按时间均匀采样，而不是简单截取前N个
        const step = Math.ceil(signalsToRender.length / maxSignalsToRender);
        signalsToRender = signalsToRender
          .filter((_, index) => index % step === 0)
          .slice(0, maxSignalsToRender);
        console.warn(
          `⚠️ 性能优化：信号数量过多(${signals.length})，已限制为${maxSignalsToRender}个以提升渲染性能`,
        );
      }

      if (!signalsToRender || signalsToRender.length === 0) {
        // 没有信号时，只清除信号标记，保留其他标记（如闪烁标记）
        const currentMarkers = this.candlestickSeries.markers() || [];
        const nonSignalMarkers = currentMarkers.filter((m) => {
          // 保留非信号标记（通过颜色判断，信号标记是红色、绿色或黄色）
          // 信号标记颜色：红色(#ff062b-开空/平空)、绿色(#089981-开多)、黄色(#ffd700-平多)、蓝色(#2196F3-默认)
          return !(
            m.color === "#ff062b" ||
            m.color === "#089981" ||
            m.color === "#ffd700" ||
            m.color === "#2196F3"
          );
        });
        this.candlestickSeries.setMarkers(nonSignalMarkers);
        this.signalMarkers = [];
        return;
      }

      // 获取K线数据的时间范围
      const data = this.candlestickSeries.data();
      if (!data || data.length === 0) {
        return;
      }

      const firstDataTime = data[0].time;
      const lastDataTime = data[data.length - 1].time;

      // 🎯 性能优化：预先计算时间范围，避免在循环中重复计算
      const timeTolerance = 10800; // 3小时 = 10800秒
      const minTime = firstDataTime - timeTolerance;
      const maxTime = lastDataTime + timeTolerance;

      // 🎯 性能优化：使用对象映射替代多个if-else，提升性能
      const signalConfigMap = {
        SB: { color: "#ff062b", position: "aboveBar", shape: "circle" },
        LB: { color: "#089981", position: "belowBar", shape: "circle" },
        LS: {
          color: "#00ffbb",
          position: "belowBar",
          shape: "square",
          text: "X 平多",
        },
        SS: {
          color: "#ff062b",
          position: "aboveBar",
          shape: "square",
          text: "X 平空",
        },
      };

      // 创建信号标注
      let signalMarkers = [];
      for (let i = 0; i < signalsToRender.length; i++) {
        const signal = signalsToRender[i];

        // 处理时间戳格式 - 使用 TimezoneHelper
        let timeValue = TimezoneHelper.normalizeTimestamp(signal.timestamp);

        // 检查时间戳是否有效
        if (!timeValue || timeValue <= 0 || isNaN(timeValue)) {
          continue;
        }

        // 🎯 性能优化：快速范围检查
        if (timeValue < minTime || timeValue > maxTime) {
          continue;
        }

        // 🎯 性能优化：使用映射表获取配置，避免多个if-else
        const config = signalConfigMap[signal.signal] || {
          color: "#2196F3",
          position: "inBar",
          shape: "circle",
        };

        const markerTextRaw = config.text || signal.lable || signal.signal;
        const markerText = formatSignalLabelWeightOnly(markerTextRaw);

        signalMarkers.push({
          id: `signal_${signal.id}_${timeValue}`, // 唯一标识符
          time: timeValue, // UTC 时间戳（秒）
          position: config.position,
          color: config.color,
          shape: config.shape,
          text: markerText,
          size: 1,
        });
      }

      // 🎯 性能优化：只在有多个标记时才排序和去重
      if (signalMarkers.length > 1) {
        // 按照时间从小到大排序，满足lightweight-charts对markers顺序的要求
        signalMarkers.sort((a, b) => a.time - b.time);

        // 🎯 性能优化：使用 Set 进行去重（比 Map 更轻量）
        const seen = new Set();
        signalMarkers = signalMarkers.filter((m) => {
          // 对于平仓信号（LS/SS），使用更严格的去重：时间+位置+颜色
          // 对于开仓信号（LB/SB），允许同一时间点有多个（虽然不太可能）
          const isCloseSignal =
            m.text && (m.text.includes("平多") || m.text.includes("平空"));
          const key = isCloseSignal
            ? `${m.time}-${m.position}-${m.color}` // 平仓信号：时间+位置+颜色去重
            : `${m.time}-${m.position}-${m.text}`; // 开仓信号：时间+位置+文本去重

          if (seen.has(key)) {
            return false;
          }
          seen.add(key);
          return true;
        });
      }

      // 获取现有标记（保留非信号标记，如闪烁标记）
      const currentMarkers = this.candlestickSeries.markers() || [];
      const nonSignalMarkers = currentMarkers.filter((m) => {
        // 保留非信号标记（通过颜色判断，信号标记是红色、绿色或亮绿色）
        // 信号标记颜色：红色(#ff062b-开空/平空)、绿色(#089981-开多)、亮绿色(#00ffbb-平多)、蓝色(#2196F3-默认)
        return !(
          m.color === "#ff062b" ||
          m.color === "#089981" ||
          m.color === "#00ffbb" ||
          m.color === "#2196F3"
        );
      });

      // 🎯 性能优化：只在有多个标记时才排序
      let allMarkers = [...nonSignalMarkers, ...signalMarkers];
      if (allMarkers.length > 1) {
        allMarkers.sort((a, b) => a.time - b.time);
      }

      // 检查标记是否发生变化，避免不必要的重新渲染
      if (!this.hasMarkersChanged(currentMarkers, allMarkers)) {
        return;
      }

      // 使用官方推荐的 setMarkers() 方法设置标记
      // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesMarkersPluginApi#setMarkers
      this.candlestickSeries.setMarkers(allMarkers);
      this.signalMarkers = signalMarkers;

      if (signalMarkers.length === 0 && signals && signals.length > 0) {
        console.warn("⚠️ [信号更新] 没有有效的信号标记被创建，可能的原因：");
        console.warn("1. 信号时间戳不在K线数据范围内");
        console.warn("2. 信号数据格式不正确");
        console.warn("3. 信号时间戳格式错误");
        console.warn("原始信号数据示例:", signals.slice(0, 5));
        console.warn("K线数据时间范围:", {
          first: data[0]
            ? new Date(data[0].time * 1000).toLocaleString()
            : "无",
          last: data[data.length - 1]
            ? new Date(data[data.length - 1].time * 1000).toLocaleString()
            : "无",
          firstTimestamp: data[0]?.time,
          lastTimestamp: data[data.length - 1]?.time,
        });
      }
    },

    // 初始化BOLL指标系列
    initBollSeries() {
      if (!this.chart) return;

      // 创建中轨线
      this.bollSeries.middle = this.chart.addLineSeries({
        color: "#2196F3",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      // 创建上轨线
      this.bollSeries.upper = this.chart.addLineSeries({
        color: "#FF6B6B",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      // 创建下轨线
      this.bollSeries.lower = this.chart.addLineSeries({
        color: "#4ECDC4",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });
    },

    // 计算BOLL指标
    calculateBoll(data, period = 20, multiplier = 2) {
      if (!data || data.length < period) return [];

      const result = [];

      for (let i = period - 1; i < data.length; i++) {
        // 计算移动平均线（中轨）
        let sum = 0;
        for (let j = i - period + 1; j <= i; j++) {
          sum += data[j].close;
        }
        const ma = sum / period;

        // 计算标准差
        let variance = 0;
        for (let j = i - period + 1; j <= i; j++) {
          variance += Math.pow(data[j].close - ma, 2);
        }
        const stdDev = Math.sqrt(variance / period);

        // 计算上下轨
        const upper = ma + multiplier * stdDev;
        const lower = ma - multiplier * stdDev;

        result.push({
          time: data[i].time,
          middle: ma,
          upper: upper,
          lower: lower,
        });
      }

      return result;
    },

    // 更新BOLL指标
    updateBollIndicator(bollConfig) {
      if (!this.chart || !this.candlestickSeries) return;

      // 使用图表中的所有K线数据，而不是只使用this.data（可能只包含最新数据）
      const allKlineData = this.candlestickSeries.data();
      if (bollConfig.enabled && allKlineData && allKlineData.length > 0) {
        console.log(
          "📊 使用图表中的所有K线数据计算BOLL，数据量:",
          allKlineData.length,
        );
        // 计算BOLL数据
        const bollData = this.calculateBoll(
          allKlineData,
          bollConfig.period,
          bollConfig.multiplier,
        );

        if (bollData.length > 0) {
          // 更新中轨
          if (this.bollSeries.middle) {
            this.bollSeries.middle.setData(
              bollData.map((d) => ({ time: d.time, value: d.middle })),
            );
            this.bollSeries.middle.applyOptions({
              lineWidth: 0,
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }

          // 更新上轨
          if (this.bollSeries.upper) {
            this.bollSeries.upper.setData(
              bollData.map((d) => ({ time: d.time, value: d.upper })),
            );
            this.bollSeries.upper.applyOptions({
              lineWidth: 0,
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }

          // 更新下轨
          if (this.bollSeries.lower) {
            this.bollSeries.lower.setData(
              bollData.map((d) => ({ time: d.time, value: d.lower })),
            );
            this.bollSeries.lower.applyOptions({
              lineWidth: 0,
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }
        }
      } else {
        // 隐藏BOLL指标
        if (this.bollSeries.middle) {
          this.bollSeries.middle.setData([]);
        }
        if (this.bollSeries.upper) {
          this.bollSeries.upper.setData([]);
        }
        if (this.bollSeries.lower) {
          this.bollSeries.lower.setData([]);
        }
      }
    },

    // 初始化MACD面板
    initMacdPane() {
      if (!this.chart) {
        console.log("Chart not available for MACD pane creation");
        return;
      }

      // 直接使用主图表方案，因为Lightweight Charts 4.2.0可能不支持addPane
      console.log("Using main chart for MACD series");
      this.initMacdSeriesOnMainChart();
    },

    /**
     * 统一管理副图布局，防止重叠
     * 根据启用的副图数量动态分配垂直空间
     */
    updateSubChartLayout() {
      if (!this.chart) {
        console.warn("⚠️ updateSubChartLayout: 图表不存在，跳过布局更新");
        return;
      }

      try {
        // 定义副图列表（按显示顺序）
        const subCharts = [
          { id: "macd", enabled: this.macd && this.macd.enabled },
          { id: "rsi", enabled: this.rsi && this.rsi.enabled },
          { id: "tsm", enabled: this.tsm && this.tsm.enabled },
          {
            id: "trendStrengthAfterReversal",
            enabled:
              this.trendStrengthAfterReversal &&
              this.trendStrengthAfterReversal.enabled,
          },
          {
            id: "andeanOscillator",
            enabled: this.andeanOscillator && this.andeanOscillator.enabled,
          },
          {
            id: "multiTimeframeTrend",
            enabled:
              this.multiTimeframeTrend && this.multiTimeframeTrend.enabled,
          },
        ];

        // 过滤出启用的副图
        const enabledSubCharts = subCharts.filter((sc) => sc.enabled);
        const subChartCount = enabledSubCharts.length;

        // 计算主图底部边距（根据副图数量）
        // scaleMargins.bottom 值越大，主图占用空间越小，副图占用空间越大
        // 主图应该占据大部分空间，副图占据小部分空间，形成上下分离的结构
        // 1个副图：主图占75%，副图占25%
        // 2个副图：主图占70%，每个副图占15%
        // 3个副图：主图占65%，每个副图占11.67%
        // 4个副图：主图占60%，每个副图占10%
        let mainChartBottom = 0.1;
        if (subChartCount === 1) {
          mainChartBottom = 0.25; // 主图占75%，副图占25%
        } else if (subChartCount === 2) {
          mainChartBottom = 0.3; // 主图占70%，每个副图占15%
        } else if (subChartCount === 3) {
          mainChartBottom = 0.35; // 主图占65%，每个副图占11.67%
        } else if (subChartCount >= 4) {
          mainChartBottom = 0.4; // 主图占60%，每个副图占10%
        }

        // 更新主图价格刻度
        const priceScale = this.chart.priceScale("right");
        if (priceScale) {
          priceScale.applyOptions({
            scaleMargins: {
              top: 0.1,
              bottom: mainChartBottom,
            },
          });
        } else {
          console.warn(
            "⚠️ updateSubChartLayout: 价格刻度不存在，跳过scaleMargins设置",
          );
        }

        // 计算每个副图的位置
        // 主图的 scaleMargins.bottom = mainChartBottom，主图实际占用：0 到 (1 - mainChartBottom)
        // 副图应该从主图结束位置开始，即从 (1 - mainChartBottom) 开始
        const mainChartEnd = 1 - mainChartBottom; // 主图结束位置（例如：mainChartBottom=0.25时，主图结束于0.75）

        // 为副图整体预留一小段到底部的间距，避免最后一个副图贴着整个图表底部（时间轴）
        // 这里适当减小 padding，让副图整体稍微靠下，但不贴底
        const bottomPadding = subChartCount > 0 ? 0.03 : 0;
        const subTopStart = mainChartEnd;
        const subBottomEnd = 1 - bottomPadding;
        const subChartArea = Math.max(0, subBottomEnd - subTopStart); // 真正用于副图的总高度
        const subChartHeight =
          subChartCount > 0 ? subChartArea / subChartCount : 0;

        // 更新每个副图的价格刻度
        enabledSubCharts.forEach((subChart, index) => {
          try {
            // 🔥 关键修复：检查对应的系列是否存在，如果不存在则跳过
            let seriesExists = false;
            if (
              subChart.id === "macd" &&
              this.macdSeries &&
              this.macdSeries.macd
            ) {
              seriesExists = true;
            } else if (subChart.id === "rsi" && this.rsiSeries) {
              seriesExists = true;
            } else if (
              subChart.id === "tsm" &&
              this.tsmSeries &&
              this.tsmSeries.trendStrength
            ) {
              seriesExists = true;
            } else if (
              subChart.id === "trendStrengthAfterReversal" &&
              this.trendStrengthAfterReversalSeries &&
              this.trendStrengthAfterReversalSeries.value
            ) {
              seriesExists = true;
            } else if (
              subChart.id === "andeanOscillator" &&
              this.andeanOscillatorSeries &&
              this.andeanOscillatorSeries.osc
            ) {
              seriesExists = true;
            } else if (
              subChart.id === "multiTimeframeTrend" &&
              this.multiTimeframeTrendSeries &&
              this.multiTimeframeTrendSeries.value
            ) {
              seriesExists = true;
            }

            if (!seriesExists) {
              console.debug(
                `⚠️ updateSubChartLayout: 副图 ${subChart.id} 的系列尚未创建，跳过价格刻度更新`,
              );
              return;
            }

            const priceScale = this.chart.priceScale(subChart.id);
            if (priceScale) {
              // 计算该副图的顶部和底部位置
              // 副图从主图结束位置开始：subTopStart
              // 每个副图的高度：subChartHeight
              // top: 主图结束位置 + (index * 副图高度)
              // bottom: 1 - (top + 副图高度)
              const top = subTopStart + index * subChartHeight;
              const bottom = 1 - (top + subChartHeight);

              priceScale.applyOptions({
                scaleMargins: {
                  top: top,
                  bottom: bottom,
                },
              });
            } else {
              console.warn(
                `⚠️ updateSubChartLayout: 副图 ${subChart.id} 的价格刻度不存在`,
              );
            }
          } catch (error) {
            console.error(
              `❌ updateSubChartLayout: 更新副图 ${subChart.id} 价格刻度失败:`,
              error,
            );
          }
        });

        // 如果某个副图被禁用，确保其价格刻度也被重置
        subCharts.forEach((subChart) => {
          if (!subChart.enabled) {
            try {
              const priceScale = this.chart.priceScale(subChart.id);
              if (priceScale) {
                // 重置为默认值（虽然可能不存在，但确保不会影响布局）
                priceScale.applyOptions({
                  scaleMargins: {
                    top: 0.1,
                    bottom: 0.1,
                  },
                });
              }
            } catch (error) {
              // 忽略错误，被禁用的副图价格刻度可能不存在
              console.debug(
                `副图 ${subChart.id} 价格刻度重置跳过:`,
                error.message,
              );
            }
          }
        });
      } catch (error) {
        console.error("❌ 更新图表配置失败:", error);
        // 不抛出错误，避免中断其他操作
      }
    },

    // 回退方案：在主图表上添加MACD系列
    initMacdSeriesOnMainChart() {
      console.log("Creating MACD series on main chart");

      // 创建MACD线
      this.macdSeries.macd = this.chart.addLineSeries({
        color: "#2196F3",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceScaleId: "macd",
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });
      console.log("MACD line series created");

      // 创建信号线
      this.macdSeries.signal = this.chart.addLineSeries({
        color: "#FF9800",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceScaleId: "macd",
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });
      console.log("Signal line series created");

      // 创建柱状图
      this.macdSeries.histogram = this.chart.addHistogramSeries({
        color: "#4CAF50",
        title: "", // 隐藏价格轴标签
        priceScaleId: "macd",
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });
      console.log("Histogram series created");

      // 配置MACD价格刻度 - 在创建系列后配置
      this.chart.priceScale("macd").applyOptions({
        autoScale: true,
        position: "right",
        borderVisible: true,
        borderColor: "#cccccc",
        // scaleMargins 由 updateSubChartLayout() 统一管理
      });

      // 统一更新副图布局
      this.updateSubChartLayout();

      console.log(
        "MACD series created on main chart with separate price scale",
      );
    },

    // 计算MACD指标
    calculateMacd(data, fastPeriod = 12, slowPeriod = 26, signalPeriod = 9) {
      if (!data || data.length < slowPeriod) return [];

      const closes = data.map((d) => d.close);
      const emaFast = this.calculateEMA(closes, fastPeriod);
      const emaSlow = this.calculateEMA(closes, slowPeriod);

      const macdLine = [];
      for (let i = 0; i < closes.length; i++) {
        if (i >= slowPeriod - 1) {
          macdLine.push(emaFast[i] - emaSlow[i]);
        } else {
          macdLine.push(0);
        }
      }

      const signalLine = this.calculateEMA(macdLine, signalPeriod);
      const histogram = [];

      for (let i = 0; i < macdLine.length; i++) {
        histogram.push(macdLine[i] - signalLine[i]);
      }

      const result = [];
      for (let i = 0; i < data.length; i++) {
        if (i >= slowPeriod - 1) {
          result.push({
            time: data[i].time,
            macd: macdLine[i],
            signal: signalLine[i],
            histogram: histogram[i],
          });
        }
      }

      return result;
    },

    // 计算EMA
    calculateEMA(data, period) {
      if (!data || !Array.isArray(data) || data.length === 0) return [];
      if (typeof period !== "number" || period <= 0) return [];

      const ema = [];
      const multiplier = 2 / (period + 1);

      for (let i = 0; i < data.length; i++) {
        const value = data[i];
        if (typeof value !== "number" || isNaN(value)) {
          ema[i] = null;
        } else if (i === 0) {
          ema[i] = value;
        } else {
          const prevEma = ema[i - 1];
          if (prevEma === null) {
            ema[i] = value;
          } else {
            ema[i] = value * multiplier + prevEma * (1 - multiplier);
          }
        }
      }

      return ema;
    },

    // 更新MACD指标
    updateMacdIndicator(macdConfig) {
      console.log("updateMacdIndicator called:", macdConfig);
      if (!this.chart) {
        console.log("Chart not initialized, skipping MACD update");
        return;
      }

      if (macdConfig.enabled) {
        console.log("MACD enabled, initializing pane and calculating data");

        if (!this.macdPane && !this.macdSeries.macd) {
          console.log("Creating MACD pane");
          this.initMacdPane();
        } else if (this.macdSeries.macd) {
          console.log("MACD series already exists, using existing series");
        }

        // 统一更新副图布局
        this.updateSubChartLayout();

        // 使用图表中的所有K线数据，而不是只使用this.data（可能只包含最新数据）
        const allKlineData = this.candlestickSeries
          ? this.candlestickSeries.data()
          : null;
        if (allKlineData && allKlineData.length > 0) {
          console.log(
            "📊 使用图表中的所有K线数据计算MACD，数据量:",
            allKlineData.length,
          );
          const macdData = this.calculateMacd(
            allKlineData,
            macdConfig.fastPeriod,
            macdConfig.slowPeriod,
            macdConfig.signalPeriod,
          );

          console.log("MACD data calculated:", macdData.length, "points");
          if (macdData.length > 0) {
            // 更新MACD线
            if (this.macdSeries.macd) {
              console.log("Updating MACD line with", macdData.length, "points");
              this.macdSeries.macd.setData(
                macdData.map((d) => ({
                  time: d.time,
                  value: d.macd,
                })),
              );
              this.macdSeries.macd.applyOptions({
                lineWidth: 0,
                title: "",
                priceLineVisible: false,
                lastValueVisible: true,
              });
            }

            // 更新信号线
            if (this.macdSeries.signal) {
              console.log(
                "Updating signal line with",
                macdData.length,
                "points",
              );
              this.macdSeries.signal.setData(
                macdData.map((d) => ({
                  time: d.time,
                  value: d.signal,
                })),
              );
              this.macdSeries.signal.applyOptions({
                lineWidth: 0,
                title: "",
                priceLineVisible: false,
                lastValueVisible: true,
              });
            }

            // 更新柱状图
            if (this.macdSeries.histogram) {
              console.log("Updating histogram with", macdData.length, "points");
              this.macdSeries.histogram.setData(
                macdData.map((d) => ({
                  time: d.time,
                  value: d.histogram,
                  color: d.histogram >= 0 ? "#4CAF50" : "#F44336",
                })),
              );
              this.macdSeries.histogram.applyOptions({
                title: "",
                priceLineVisible: false,
                lastValueVisible: true,
              });
            }

            // 统一更新副图布局
            this.updateSubChartLayout();
          } else {
            console.log("No MACD data calculated");
          }
        } else {
          console.log("No chart data available for MACD calculation");
        }
      } else {
        console.log("MACD disabled, removing pane and series");

        // 先移除MACD系列
        if (this.macdPane) {
          this.chart.removePane(this.macdPane);
          this.macdPane = null;
        }

        if (this.macdSeries.macd) {
          this.chart.removeSeries(this.macdSeries.macd);
        }
        if (this.macdSeries.signal) {
          this.chart.removeSeries(this.macdSeries.signal);
        }
        if (this.macdSeries.histogram) {
          this.chart.removeSeries(this.macdSeries.histogram);
        }

        this.macdSeries = {
          macd: null,
          signal: null,
          histogram: null,
        };

        // 统一更新副图布局
        this.updateSubChartLayout();
      }
    },

    // 只更新MACD数据，不调整布局
    updateMacdData(macdConfig) {
      if (!this.chart || !this.macdSeries.macd || !this.candlestickSeries) {
        return;
      }

      // 使用图表中的所有K线数据，而不是只使用this.data（可能只包含最新数据）
      const allKlineData = this.candlestickSeries.data();
      if (allKlineData && allKlineData.length > 0) {
        console.log(
          "📊 使用图表中的所有K线数据计算MACD，数据量:",
          allKlineData.length,
        );
        const macdData = this.calculateMacd(
          allKlineData,
          macdConfig.fastPeriod,
          macdConfig.slowPeriod,
          macdConfig.signalPeriod,
        );

        if (macdData.length > 0) {
          // 只更新数据，不调整价格刻度
          if (this.macdSeries.macd) {
            this.macdSeries.macd.setData(
              macdData.map((d) => ({
                time: d.time,
                value: d.macd,
              })),
            );
            this.macdSeries.macd.applyOptions({
              lineWidth: 0,
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }

          if (this.macdSeries.signal) {
            this.macdSeries.signal.setData(
              macdData.map((d) => ({
                time: d.time,
                value: d.signal,
              })),
            );
            this.macdSeries.signal.applyOptions({
              lineWidth: 0,
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }

          if (this.macdSeries.histogram) {
            this.macdSeries.histogram.setData(
              macdData.map((d) => ({
                time: d.time,
                value: d.histogram,
                color: d.histogram >= 0 ? "#4CAF50" : "#F44336",
              })),
            );
            this.macdSeries.histogram.applyOptions({
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }
        }
      }
    },

    // Smart Money Concepts 相关方法
    updateSmartMoneyConcepts(results) {
      if (!this.chart || !this.candlestickSeries) {
        console.warn("Smart Money Concepts: 图表或K线系列未初始化");
        return;
      }

      console.log("updateSmartMoneyConcepts 被调用, results:", results);

      // 清除之前的元素
      this.clearSmartMoneyConcepts();

      if (!results) {
        console.warn("Smart Money Concepts: 没有结果数据");
        return;
      }

      // 收集所有标记
      const allMarkers = [];

      // 绘制结构（BOS/CHoCH）
      if (results.structures && results.structures.length > 0) {
        console.log("绘制", results.structures.length, "个结构");
        results.structures.forEach((structure, idx) => {
          const markers = this.drawStructure(structure);
          if (markers && markers.length > 0) {
            allMarkers.push(...markers);
          }
        });
      } else {
        console.warn("Smart Money Concepts: 没有结构数据");
      }

      // 绘制订单块
      if (results.orderBlocks && results.orderBlocks.length > 0) {
        console.log("绘制", results.orderBlocks.length, "个订单块");
        results.orderBlocks.forEach((ob) => {
          const marker = this.drawOrderBlock(ob);
          if (marker) {
            allMarkers.push(marker);
          }
        });
      }

      // 绘制公允价值缺口
      if (results.fairValueGaps && results.fairValueGaps.length > 0) {
        console.log("绘制", results.fairValueGaps.length, "个公允价值缺口");
        results.fairValueGaps.forEach((fvg) => {
          this.drawFairValueGap(fvg);
        });
      }

      // 一次性设置所有标记
      if (allMarkers.length > 0) {
        // 🔥 关键修复：确保 markers 按时间升序排列
        allMarkers.sort((a, b) => a.time - b.time);

        // 获取现有的信号标记
        const existingMarkers = this.signalMarkers || [];
        // 合并并排序所有 markers
        const mergedMarkers = [...existingMarkers, ...allMarkers].sort(
          (a, b) => a.time - b.time,
        );

        this.candlestickSeries.setMarkers(mergedMarkers);
        console.log("设置了", allMarkers.length, "个标记");
      } else {
        console.warn("Smart Money Concepts: 没有标记需要设置");
      }
    },

    // 绘制结构（BOS/CHoCH）
    drawStructure(structure) {
      if (
        !this.chart ||
        !structure.level ||
        structure.level === null ||
        !structure.time
      ) {
        console.warn("绘制结构失败: 缺少必要数据", structure);
        return [];
      }

      try {
        console.log("绘制结构:", structure);

        if (!this.candlestickSeries) {
          console.error("绘制结构失败: candlestickSeries 未初始化");
          return [];
        }

        // 获取当前数据以确定线条的结束时间
        const currentData = this.candlestickSeries.data();
        if (!currentData || currentData.length === 0) {
          console.warn("绘制结构失败: 没有K线数据");
          return [];
        }

        // 确定线条的起止时间
        // 起点：swing point 的时间
        const startTime = structure.time;
        // 终点：突破点的时间（如果有），否则使用最后一个K线的时间
        let endTime = structure.currentTime;
        if (!endTime) {
          // 如果没有突破点，对于参考点（SH/SL），延伸到最后一个K线
          endTime = currentData[currentData.length - 1].time;
        }

        // 确保时间在数据范围内
        const dataTimes = currentData.map((d) => d.time);
        const lastTime = dataTimes[dataTimes.length - 1];

        // 调整开始时间
        let adjustedStartTime = startTime;
        if (!dataTimes.includes(startTime)) {
          // 找到最接近的开始时间
          const closestStartTime =
            dataTimes.find((t) => t >= startTime) || dataTimes[0];
          adjustedStartTime = closestStartTime || startTime;
        }

        // 调整结束时间
        let adjustedEndTime = endTime;
        if (!dataTimes.includes(endTime)) {
          if (endTime <= lastTime) {
            // 找到最接近的结束时间
            const closestEndTime =
              dataTimes.find((t) => t >= endTime) || lastTime;
            adjustedEndTime = closestEndTime || lastTime;
          } else {
            adjustedEndTime = lastTime;
          }
        }

        // 确保结束时间不早于开始时间
        if (adjustedEndTime < adjustedStartTime) {
          adjustedEndTime = adjustedStartTime;
        }

        // 使用 line 系列创建从起点到终点的水平线
        // CHoCH 使用虚线，BOS 使用实线
        const isCHoCH = structure.tag === "CHoCH";
        const lineStyle = isCHoCH || structure.type === "internal" ? 2 : 0; // 0 = solid, 2 = dashed

        const lineSeries = this.chart.addLineSeries({
          color: structure.color || "#089981",
          lineWidth: structure.type === "internal" ? 1 : 2,
          lineStyle: lineStyle,
          priceLineVisible: false,
          lastValueVisible: false,
          title: structure.tag || "",
        });

        // 设置线条数据：两个点（起点和终点）
        lineSeries.setData([
          { time: adjustedStartTime, value: structure.level },
          { time: adjustedEndTime, value: structure.level },
        ]);

        // 创建标签（使用标记）- 在突破点或参考点显示
        const markers = [];
        // 找到标记点的位置（突破点或参考点）
        let markerTime = structure.currentTime || structure.time;

        // 确保时间在数据范围内
        if (!dataTimes.includes(markerTime)) {
          if (structure.time && dataTimes.includes(structure.time)) {
            markerTime = structure.time;
          } else if (markerTime <= lastTime) {
            // 时间在范围内，但可能不是精确匹配，使用最接近的时间
            const closestTime =
              dataTimes.find((t) => t >= markerTime) || lastTime;
            markerTime = closestTime;
          } else {
            markerTime = lastTime;
          }
        }

        const position =
          structure.direction === "bullish"
            ? "belowBar"
            : structure.direction === "bearish"
              ? "aboveBar"
              : structure.direction === "reference"
                ? structure.tag === "SH"
                  ? "aboveBar"
                  : "belowBar"
                : "belowBar";

        markers.push({
          time: markerTime,
          position: position,
          color: structure.color || "#089981",
          shape:
            structure.direction === "bullish"
              ? "arrowUp"
              : structure.direction === "bearish"
                ? "arrowDown"
                : "circle",
          text: structure.tag || "",
          size: 1,
        });

        this.smartMoneyElements.structures.push({ line: lineSeries, markers });
        console.log("结构绘制成功:", {
          level: structure.level,
          tag: structure.tag,
          startTime: adjustedStartTime,
          endTime: adjustedEndTime,
          markers: markers.length,
        });
        return markers;
      } catch (error) {
        console.error("绘制结构失败:", error, structure);
        return [];
      }
    },

    // 绘制订单块
    drawOrderBlock(orderBlock) {
      if (
        !this.chart ||
        !orderBlock.barTime ||
        !orderBlock.barHigh ||
        !orderBlock.barLow
      ) {
        return null;
      }

      try {
        // 获取当前数据的最后时间，用于延伸订单块
        const currentData = this.candlestickSeries.data();
        if (!currentData || currentData.length === 0) {
          return null;
        }

        if (!this.candlestickSeries) {
          console.error("绘制订单块失败: candlestickSeries 未初始化");
          return null;
        }

        // 创建订单块矩形（使用两个价格线）- 使用 series 的 createPriceLine 方法
        const topLine = this.candlestickSeries.createPriceLine({
          price: orderBlock.barHigh,
          color: orderBlock.bias === 1 ? "#3179f5" : "#f77c80",
          lineWidth: 1,
          lineStyle: 0,
          axisLabelVisible: false,
        });

        const bottomLine = this.candlestickSeries.createPriceLine({
          price: orderBlock.barLow,
          color: orderBlock.bias === 1 ? "#3179f5" : "#f77c80",
          lineWidth: 1,
          lineStyle: 0,
          axisLabelVisible: false,
        });

        // 创建标记
        const marker = {
          time: orderBlock.barTime,
          position: orderBlock.bias === 1 ? "belowBar" : "aboveBar",
          color: orderBlock.bias === 1 ? "#3179f5" : "#f77c80",
          shape: "square",
          text: "OB",
          size: 1,
        };

        this.smartMoneyElements.orderBlocks.push({
          topLine,
          bottomLine,
          marker,
        });
        return marker;
      } catch (error) {
        console.error("绘制订单块失败:", error);
        return null;
      }
    },

    // 绘制公允价值缺口
    drawFairValueGap(fvg) {
      if (!this.chart || !fvg.top || !fvg.bottom || !fvg.time) {
        return;
      }

      try {
        // 获取当前数据的最后时间
        const currentData = this.candlestickSeries.data();
        if (!currentData || currentData.length === 0) {
          return;
        }
        const lastTime = currentData[currentData.length - 1].time;

        if (!this.candlestickSeries) {
          console.error("绘制公允价值缺口失败: candlestickSeries 未初始化");
          return;
        }

        // 创建公允价值缺口的上下边界线 - 使用 series 的 createPriceLine 方法
        const topLine = this.candlestickSeries.createPriceLine({
          price: fvg.top,
          color: fvg.type === "bullish" ? "#00ff68" : "#ff0008",
          lineWidth: 1,
          lineStyle: 0,
          axisLabelVisible: false,
        });

        const bottomLine = this.candlestickSeries.createPriceLine({
          price: fvg.bottom,
          color: fvg.type === "bullish" ? "#00ff68" : "#ff0008",
          lineWidth: 1,
          lineStyle: 0,
          axisLabelVisible: false,
        });

        this.smartMoneyElements.fairValueGaps.push({ topLine, bottomLine });
      } catch (error) {
        console.error("绘制公允价值缺口失败:", error);
      }
    },

    // 清除 Smart Money Concepts 显示
    clearSmartMoneyConcepts() {
      if (!this.chart) {
        return;
      }

      // 清除结构线条（现在是 line series）
      this.smartMoneyElements.structures.forEach((element) => {
        try {
          if (element.line) {
            // line 是 line series，使用 removeSeries 移除
            this.chart.removeSeries(element.line);
          }
        } catch (error) {
          console.error("清除结构线条失败:", error);
        }
      });

      // 清除订单块
      this.smartMoneyElements.orderBlocks.forEach((element) => {
        try {
          if (element.topLine) {
            this.candlestickSeries.removePriceLine(element.topLine);
          }
          if (element.bottomLine) {
            this.candlestickSeries.removePriceLine(element.bottomLine);
          }
        } catch (error) {
          console.error("清除订单块失败:", error);
        }
      });

      // 清除公允价值缺口
      this.smartMoneyElements.fairValueGaps.forEach((element) => {
        try {
          if (element.topLine) {
            this.candlestickSeries.removePriceLine(element.topLine);
          }
          if (element.bottomLine) {
            this.candlestickSeries.removePriceLine(element.bottomLine);
          }
        } catch (error) {
          console.error("清除公允价值缺口失败:", error);
        }
      });

      // 重置数组
      this.smartMoneyElements = {
        structures: [],
        orderBlocks: [],
        fairValueGaps: [],
        swingPoints: [],
      };
    },

    // ==================== 辅助方法 ====================

    /**
     * 计算简单移动平均线 (SMA)
     */
    calculateSMA(data, period) {
      if (!data || !Array.isArray(data) || data.length < period) return [];

      const sma = [];
      for (let i = 0; i < data.length; i++) {
        if (i < period - 1) {
          sma.push(null);
        } else {
          let sum = 0;
          for (let j = i - period + 1; j <= i; j++) {
            const value = data[j];
            if (typeof value === "number" && !isNaN(value)) {
              sum += value;
            }
          }
          sma.push(sum / period);
        }
      }
      return sma;
    },

    /**
     * 计算平均真实波幅 (ATR)
     */
    calculateATR(data, period) {
      if (!data || data.length < period + 1) return [];

      const tr = [];
      for (let i = 1; i < data.length; i++) {
        const high = data[i].high;
        const low = data[i].low;
        const prevClose = data[i - 1].close;
        tr.push(
          Math.max(
            high - low,
            Math.abs(high - prevClose),
            Math.abs(low - prevClose),
          ),
        );
      }

      const atr = [];
      // 填充初始值
      for (let i = 0; i < period; i++) {
        atr.push(null);
      }

      // 计算第一个ATR（使用前period个TR的简单平均）
      let sum = 0;
      for (let i = 0; i < period; i++) {
        sum += tr[i];
      }
      atr[period] = sum / period;

      // 使用EMA方式计算后续ATR
      const multiplier = 1 / period;
      for (let i = period + 1; i < data.length; i++) {
        atr.push(atr[i - 1] * (1 - multiplier) + tr[i - 1] * multiplier);
      }

      return atr;
    },

    /**
     * 计算相对强弱指标 (RSI)
     */
    calculateRSI(data, period) {
      if (!data || data.length < period + 1) return [];

      const changes = [];
      for (let i = 1; i < data.length; i++) {
        changes.push(data[i].close - data[i - 1].close);
      }

      const rsi = [];
      for (let i = 0; i < period; i++) {
        rsi.push(null);
      }

      // 计算初始平均涨幅和跌幅
      let avgGain = 0;
      let avgLoss = 0;
      for (let i = 0; i < period; i++) {
        if (changes[i] > 0) {
          avgGain += changes[i];
        } else {
          avgLoss += Math.abs(changes[i]);
        }
      }
      avgGain /= period;
      avgLoss /= period;

      // 计算第一个RSI
      if (avgLoss === 0) {
        rsi.push(100);
      } else {
        const rs = avgGain / avgLoss;
        rsi.push(100 - 100 / (1 + rs));
      }

      // 使用EMA方式计算后续RSI
      const multiplier = 1 / period;
      for (let i = period; i < changes.length; i++) {
        const change = changes[i];
        avgGain =
          avgGain * (1 - multiplier) + (change > 0 ? change : 0) * multiplier;
        avgLoss =
          avgLoss * (1 - multiplier) +
          (change < 0 ? Math.abs(change) : 0) * multiplier;

        if (avgLoss === 0) {
          rsi.push(100);
        } else {
          const rs = avgGain / avgLoss;
          rsi.push(100 - 100 / (1 + rs));
        }
      }

      return rsi;
    },

    /**
     * 计算标准差
     */
    calculateStdDev(data, smaValues, period) {
      if (!data || !smaValues || data.length < period) return [];

      const stdDev = [];
      for (let i = 0; i < period - 1; i++) {
        stdDev.push(null);
      }

      for (let i = period - 1; i < data.length; i++) {
        if (smaValues[i] === null || smaValues[i] === undefined) {
          stdDev.push(null);
          continue;
        }

        let variance = 0;
        for (let j = i - period + 1; j <= i; j++) {
          variance += Math.pow(data[j].close - smaValues[i], 2);
        }
        stdDev.push(Math.sqrt(variance / period));
      }

      return stdDev;
    },

    /**
     * 颜色转换：十六进制转RGBA
     */
    hexToRgba(hex, alpha = 1) {
      const r = parseInt(hex.slice(1, 3), 16);
      const g = parseInt(hex.slice(3, 5), 16);
      const b = parseInt(hex.slice(5, 7), 16);
      return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    },

    hexToRgb(hex) {
      const r = parseInt(hex.slice(1, 3), 16);
      const g = parseInt(hex.slice(3, 5), 16);
      const b = parseInt(hex.slice(5, 7), 16);
      return { r, g, b };
    },

    /**
     * 卡尔曼滤波
     */
    kalmanFilter(data, len) {
      if (!data || data.length === 0) return [];

      // 按原始脚本：kalman_filter(src, length, R = 0.01, Q = 0.1)
      const R = 0.01;
      const Q = 0.1;

      const result = [];
      let estimate = null; // var float estimate = na
      let error_est = 1.0; // var float error_est = 1.0
      const error_meas = R * len; // var float error_meas = R * (length)
      let kalman_gain = 0.0; // var float kalman_gain = 0.0
      let prediction = null; // var float prediction = na

      for (let i = 0; i < data.length; i++) {
        const src = data[i].close;

        // Initialize the estimate with the first value of the source
        // if na(estimate) estimate := src[1]
        if (estimate === null) {
          if (i > 0) {
            estimate = data[i - 1].close; // src[1]
          } else {
            estimate = src;
          }
        }

        // Prediction step
        prediction = estimate;

        // Update Kalman gain
        kalman_gain = error_est / (error_est + error_meas);

        // Update estimate with measurement correction
        estimate = prediction + kalman_gain * (src - prediction);

        // Update error estimates
        // error_est := (1 - kalman_gain) * error_est + Q / (length)
        error_est = (1 - kalman_gain) * error_est + Q / len;

        result.push(estimate);
      }

      return result;
    },

    // ==================== 超级趋势 (Kalman Trend) ====================

    /**
     * 初始化超级趋势系列
     */
    initKalmanSeries() {
      if (!this.chart) return;

      this.kalmanSeries.short = this.chart.addLineSeries({
        color: "#13bd6e",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.kalmanSeries.long = this.chart.addLineSeries({
        color: "#af0d4b",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      // 填充区域已移除
    },

    /**
     * 更新超级趋势指标
     * @param {Object} config - 配置对象
     * @param {Array} customData - 可选的自定义数据，如果不提供则使用 this.data
     */
    updateKalmanIndicator(config, customData = null) {
      if (!this.chart || !this.candlestickSeries) return;

      if (config.enabled) {
        if (!this.kalmanSeries.short) {
          this.initKalmanSeries();
        }

        // 优先使用传入的自定义数据，如果没有则使用 this.data
        // 这样在拖动图表时可以使用图表中的实际数据重新计算
        let allKlineData = customData;
        if (!allKlineData || allKlineData.length === 0) {
          allKlineData = this.data;
        }
        if (!allKlineData || allKlineData.length === 0) return;

        // 计算卡尔曼滤波
        const shortKalman = this.kalmanFilter(allKlineData, config.shortLen);
        const longKalman = this.kalmanFilter(allKlineData, config.longLen);

        console.log("超级趋势计算完成:", {
          shortLen: config.shortLen || 50,
          longLen: config.longLen || 150,
          shortKalmanLength: shortKalman.length,
          longKalmanLength: longKalman.length,
          dataLength: allKlineData.length,
          shortLast3: shortKalman.slice(-3),
          longLast3: longKalman.slice(-3),
          shortSample: shortKalman.slice(0, 5),
          longSample: longKalman.slice(0, 5),
        });

        // 检查最后几个值的趋势判断
        if (shortKalman.length >= 2 && longKalman.length >= 2) {
          const lastTrend =
            shortKalman[shortKalman.length - 1] >
            longKalman[longKalman.length - 1];
          const prevTrend =
            shortKalman[shortKalman.length - 2] >
            longKalman[longKalman.length - 2];
          console.log("趋势判断:", {
            lastTrend: lastTrend ? "上升" : "下降",
            prevTrend: prevTrend ? "上升" : "下降",
            trendChanged: lastTrend !== prevTrend,
            shortVal: shortKalman[shortKalman.length - 1],
            longVal: longKalman[longKalman.length - 1],
          });
        }

        // 更新短期线（按原始脚本：trend_col1 = short_kalman > short_kalman[2] ? upper_col : lower_col）
        const upColor = config.upperColor || "#13bd6e";
        const downColor = config.lowerColor || "#af0d4b";

        // 计算短期线的颜色（根据短期卡尔曼是否上升）
        let shortColor = upColor;
        if (shortKalman.length >= 3) {
          const lastShort = shortKalman[shortKalman.length - 1];
          const prevShort2 = shortKalman[shortKalman.length - 3];
          shortColor = lastShort > prevShort2 ? upColor : downColor;
        }

        const shortData = allKlineData
          .map((item, i) => ({
            time: item.time,
            value: shortKalman[i] || null,
          }))
          .filter((d) => d.value !== null);

        if (shortData.length > 0) {
          this.kalmanSeries.short.applyOptions({
            color: shortColor,
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
          this.kalmanSeries.short.setData(shortData);
          console.log("短期线已更新:", shortData.length, "个数据点");
        } else {
          console.warn("短期线数据为空");
        }

        // 更新长期线（按原始脚本：p2 = plot(long_kalman, "Long Kalman", linewidth = 2, color = trend_col)）
        // trend_col = trend_up ? upper_col : lower_col
        // trend_up = short_kalman > long_kalman
        let longColor = upColor;
        if (shortKalman.length > 0 && longKalman.length > 0) {
          // 根据最新的趋势判断颜色
          const lastShort = shortKalman[shortKalman.length - 1];
          const lastLong = longKalman[longKalman.length - 1];
          const trendUp = lastShort > lastLong;
          longColor = trendUp ? upColor : downColor;

          console.log("长期线颜色判断:", {
            lastShort: lastShort,
            lastLong: lastLong,
            trendUp: trendUp,
            color: longColor,
          });
        }

        const longData = allKlineData
          .map((item, i) => ({
            time: item.time,
            value: longKalman[i] || null,
          }))
          .filter((d) => d.value !== null);

        if (longData.length > 0) {
          this.kalmanSeries.long.applyOptions({ color: longColor });
          this.kalmanSeries.long.setData(longData);
          console.log(
            "长期线已更新:",
            longData.length,
            "个数据点，颜色:",
            longColor,
          );
        } else {
          console.warn("长期线数据为空");
        }

        // 填充区域已移除

        // 根据趋势动能调整蜡烛颜色（传递当前图表数据，避免使用过时的 this.data）
        if (config.candleColor) {
          this.updateKalmanCandleColors(
            config,
            allKlineData,
            shortKalman,
            longKalman,
          );
        }

        // 计算趋势和绘制标签、盒子
        this.updateKalmanTrendElements(
          config,
          allKlineData,
          shortKalman,
          longKalman,
        );
      } else {
        this.clearKalmanIndicator();
      }
    },

    /**
     * 根据趋势动能调整蜡烛颜色（按"以前的"实现）
     * 只处理趋势减弱的情况（设置为灰色），其他情况保持默认颜色
     * 优化：避免频繁重绘导致K线闪烁，只在颜色真正变化时才更新
     */
    updateKalmanCandleColors(config, allKlineData, shortKalman, longKalman) {
      if (!this.candlestickSeries || !allKlineData || allKlineData.length === 0)
        return;
      if (shortKalman.length < 2 || longKalman.length < 2) return;

      // 获取当前K线数据，用于比较是否需要更新
      const currentData = this.candlestickSeries.data();
      const currentColorMap = new Map();
      if (currentData && currentData.length > 0) {
        currentData.forEach((candle) => {
          if (candle.color) {
            currentColorMap.set(candle.time, candle.color);
          }
        });
      }

      // 创建颜色映射表，用于更新特定K线颜色（趋势减弱时）
      // 只设置灰色，其他颜色保持默认不变
      const colorMap = new Map();
      let hasColorChange = false;

      for (let i = 1; i < shortKalman.length && i < allKlineData.length; i++) {
        const shortVal = shortKalman[i];
        const prevShortVal = shortKalman[i - 1];
        const longVal = longKalman[i];

        // 判断趋势：短期线 < 长期线 表示下降趋势
        const isDowntrend = shortVal < longVal;

        // 在下降趋势中，如果快速卡尔曼线向上移动（当前值 > 前一个值），表明下跌动能减弱
        if (isDowntrend && shortVal > prevShortVal) {
          const candle = allKlineData[i];
          if (candle && candle.time) {
            const targetColor = "#808080"; // 灰色
            const currentColor = currentColorMap.get(candle.time);

            // 只有颜色真正变化时才标记需要更新
            if (currentColor !== targetColor) {
              colorMap.set(candle.time, targetColor);
              hasColorChange = true;
            }
          }
        } else {
          // 如果不需要设置灰色，检查是否需要清除之前的灰色
          const candle = allKlineData[i];
          if (candle && candle.time) {
            const currentColor = currentColorMap.get(candle.time);
            if (currentColor === "#808080") {
              // 之前是灰色，现在不需要了，需要清除（设置为null表示使用默认颜色）
              colorMap.set(candle.time, null);
              hasColorChange = true;
            }
          }
        }
      }

      // 如果没有颜色变化，直接返回，避免不必要的重绘
      if (!hasColorChange || colorMap.size === 0) {
        return;
      }

      // 只更新需要改变颜色的K线，使用 update 方法而不是 setData，避免重绘所有K线
      const updates = [];
      colorMap.forEach((color, time) => {
        const candle = allKlineData.find((c) => c.time === time);
        if (candle) {
          const updateData = {
            time: candle.time,
            open: candle.open,
            high: candle.high,
            low: candle.low,
            close: candle.close,
          };

          // 如果 color 为 null，表示清除自定义颜色，使用默认颜色
          // 在 Lightweight Charts 中，不设置 color 属性即可使用默认颜色
          if (color !== null) {
            updateData.color = color;
          }

          updates.push(updateData);
        }
      });

      // 批量更新K线，只更新需要改变颜色的部分，避免重绘整个数据集
      if (updates.length > 0) {
        // 使用 update 方法更新单个K线，而不是 setData 重绘所有K线
        updates.forEach((update) => {
          try {
            this.candlestickSeries.update(update);
          } catch (e) {
            // 如果 update 失败（可能K线不存在），忽略错误
            console.debug("更新K线颜色失败:", e);
          }
        });

        console.log("Kalman蜡烛颜色更新:", {
          updatedCandles: updates.length,
          totalCandles: allKlineData.length,
        });
      }
    },

    /**
     * 更新 Kalman 趋势元素（标签、盒子等）（按原始 Pine Script 逻辑）
     */
    updateKalmanTrendElements(config, allKlineData, shortKalman, longKalman) {
      if (!this.chart || !allKlineData || allKlineData.length === 0) return;
      if (!this.candlestickSeries) return;
      if (shortKalman.length === 0 || longKalman.length === 0) return;

      // 清除旧元素
      this.clearKalmanElements();

      // 计算 ATR（用于 box）- 按原始脚本：float atr = ta.atr(200) * 0.5
      const atrPeriod = 200;
      const atrValues = this.calculateATR(allKlineData, atrPeriod);
      // 使用最新的 ATR 值
      const atr =
        (atrValues && atrValues.length > 0
          ? atrValues[atrValues.length - 1]
          : 0) * 0.5;

      let trendUp = false;
      let prevTrendUp = false;
      const markers = [];
      const priceLines = [];

      // 记录 box 信息（模拟 Pine Script 的 box）
      let lowerBox = null; // { top: number, bottom: number, startTime: number }
      let upperBox = null; // { top: number, bottom: number, startTime: number }

      for (let i = 1; i < shortKalman.length && i < allKlineData.length; i++) {
        const shortVal = shortKalman[i];
        const longVal = longKalman[i];
        const prevShortVal = shortKalman[i - 1];
        const prevLongVal = longKalman[i - 1];

        trendUp = shortVal > longVal;
        prevTrendUp = prevShortVal > prevLongVal;

        const candle = allKlineData[i];
        const prevCandle = allKlineData[i - 1];
        const time = candle.time;
        const prevTime = prevCandle.time;

        // 趋势从下降转为上升（按原始脚本：if trend_up and not trend_up[1]）
        if (trendUp && !prevTrendUp) {
          // 添加标签：label.new(bar_index, short_kalman, "🡹\n" + price)
          // 注意：原始脚本中标签位置在 short_kalman 值的位置，但 Lightweight Charts 标记只能显示在 K 线位置
          const priceText = candle.close
            ? String(Math.round(candle.close * 10) / 10)
            : "";
          console.log("趋势变化：下降→上升", {
            time: time,
            shortVal: shortVal,
            longVal: longVal,
            prevShortVal: prevShortVal,
            prevLongVal: prevLongVal,
            close: candle.close,
          });
          markers.push({
            time: time,
            position: "belowBar",
            color: config.upperColor || "#13bd6e",
            shape: "arrowUp",
            text: "↑\n" + priceText,
            size: 1,
          });

          // 清除旧的 lower_box（如果有）
          if (lowerBox && lowerBox.priceLines) {
            lowerBox.priceLines.forEach((pl) => {
              try {
                this.candlestickSeries.removePriceLine(pl);
              } catch (e) {}
            });
          }

          // 创建新的 lower_box（按原始脚本：box.new(bar_index, low+atr, bar_index, low, ...)）
          lowerBox = {
            top: candle.low + atr,
            bottom: candle.low,
            startTime: time,
          };

          // 只在启用重测信号时创建价格线来显示 box
          if (config.retestSig) {
            const boxTopLine = this.candlestickSeries.createPriceLine({
              price: lowerBox.top,
              color: this.hexToRgba(config.upperColor || "#13bd6e", 0.6),
              lineWidth: 1,
              lineStyle: 0,
              axisLabelVisible: false,
            });
            const boxBottomLine = this.candlestickSeries.createPriceLine({
              price: lowerBox.bottom,
              color: this.hexToRgba(config.upperColor || "#13bd6e", 0.6),
              lineWidth: 1,
              lineStyle: 0,
              axisLabelVisible: false,
            });
            lowerBox.priceLines = [boxTopLine, boxBottomLine];
            priceLines.push(boxTopLine, boxBottomLine);
          }
        }

        // 扩展 lower_box（按原始脚本：if not ta.change(trend_up) lower_box.set_right(bar_index)）
        // 如果趋势没有改变（即趋势持续），扩展box
        if (lowerBox && trendUp && prevTrendUp) {
          // 更新 box 的结束时间
          lowerBox.endTime = time;

          // 如果启用了重测信号，需要更新价格线（重新创建以反映扩展）
          if (config.retestSig && lowerBox.priceLines) {
            // 移除旧的价格线
            lowerBox.priceLines.forEach((pl) => {
              try {
                this.candlestickSeries.removePriceLine(pl);
              } catch (e) {}
            });

            // 重新创建价格线（从startTime到endTime）
            const boxTopLine = this.candlestickSeries.createPriceLine({
              price: lowerBox.top,
              color: this.hexToRgba(config.upperColor || "#13bd6e", 0.6),
              lineWidth: 1,
              lineStyle: 0,
              axisLabelVisible: false,
            });
            const boxBottomLine = this.candlestickSeries.createPriceLine({
              price: lowerBox.bottom,
              color: this.hexToRgba(config.upperColor || "#13bd6e", 0.6),
              lineWidth: 1,
              lineStyle: 0,
              axisLabelVisible: false,
            });
            lowerBox.priceLines = [boxTopLine, boxBottomLine];

            // 更新priceLines数组（移除旧的，添加新的）
            const oldPriceLines = priceLines.filter(
              (pl) => !lowerBox.priceLines || !lowerBox.priceLines.includes(pl),
            );
            priceLines.length = 0;
            priceLines.push(...oldPriceLines, boxTopLine, boxBottomLine);
          }
        }

        // 趋势从上升转为下降（按原始脚本：if trend_up[1] and not trend_up）
        if (!trendUp && prevTrendUp) {
          // 添加标签：label.new(bar_index, short_kalman, price + "\n🢃")
          // 注意：原始脚本中标签位置在 short_kalman 值的位置，但 Lightweight Charts 标记只能显示在 K 线位置
          const priceText = candle.close
            ? String(Math.round(candle.close * 10) / 10)
            : "";
          console.log("趋势变化：上升→下降", {
            time: time,
            shortVal: shortVal,
            longVal: longVal,
            prevShortVal: prevShortVal,
            prevLongVal: prevLongVal,
            close: candle.close,
          });
          markers.push({
            time: time,
            position: "aboveBar",
            color: config.lowerColor || "#af0d4b",
            shape: "arrowDown",
            text: priceText + "\n↓",
            size: 1,
          });

          // 清除旧的 upper_box（如果有）
          if (upperBox && upperBox.priceLines) {
            upperBox.priceLines.forEach((pl) => {
              try {
                this.candlestickSeries.removePriceLine(pl);
              } catch (e) {}
            });
          }

          // 创建新的 upper_box（按原始脚本：box.new(bar_index, high, bar_index, high-atr, ...)）
          upperBox = {
            top: candle.high,
            bottom: candle.high - atr,
            startTime: time,
          };

          // 只在启用重测信号时创建价格线来显示 box
          if (config.retestSig) {
            const boxTopLine = this.candlestickSeries.createPriceLine({
              price: upperBox.top,
              color: this.hexToRgba(config.lowerColor || "#af0d4b", 0.6),
              lineWidth: 1,
              lineStyle: 0,
              axisLabelVisible: false,
            });
            const boxBottomLine = this.candlestickSeries.createPriceLine({
              price: upperBox.bottom,
              color: this.hexToRgba(config.lowerColor || "#af0d4b", 0.6),
              lineWidth: 1,
              lineStyle: 0,
              axisLabelVisible: false,
            });
            upperBox.priceLines = [boxTopLine, boxBottomLine];
            priceLines.push(boxTopLine, boxBottomLine);
          }
        }

        // 扩展 upper_box（按原始脚本：if not ta.change(trend_up) upper_box.set_right(bar_index)）
        // 如果趋势没有改变（即趋势持续），扩展box
        if (upperBox && !trendUp && !prevTrendUp) {
          // 更新 box 的结束时间
          upperBox.endTime = time;

          // 如果启用了重测信号，需要更新价格线（重新创建以反映扩展）
          if (config.retestSig && upperBox.priceLines) {
            // 移除旧的价格线
            upperBox.priceLines.forEach((pl) => {
              try {
                this.candlestickSeries.removePriceLine(pl);
              } catch (e) {}
            });

            // 重新创建价格线（从startTime到endTime）
            const boxTopLine = this.candlestickSeries.createPriceLine({
              price: upperBox.top,
              color: this.hexToRgba(config.lowerColor || "#af0d4b", 0.6),
              lineWidth: 1,
              lineStyle: 0,
              axisLabelVisible: false,
            });
            const boxBottomLine = this.candlestickSeries.createPriceLine({
              price: upperBox.bottom,
              color: this.hexToRgba(config.lowerColor || "#af0d4b", 0.6),
              lineWidth: 1,
              lineStyle: 0,
              axisLabelVisible: false,
            });
            upperBox.priceLines = [boxTopLine, boxBottomLine];

            // 更新priceLines数组（移除旧的，添加新的）
            const oldPriceLines = priceLines.filter(
              (pl) => !upperBox.priceLines || !upperBox.priceLines.includes(pl),
            );
            priceLines.length = 0;
            priceLines.push(...oldPriceLines, boxTopLine, boxBottomLine);
          }
        }

        // 重测信号（按原始脚本：if retest_sig ...）
        if (config.retestSig) {
          // 检查重测 upper_box（按原始脚本：if high < upper_box.get_bottom() and high[1]>= upper_box.get_bottom()）
          // 注意：只有在下降趋势中才检查重测upper_box
          if (upperBox && !trendUp && i > 0) {
            if (
              candle.high < upperBox.bottom &&
              prevCandle.high >= upperBox.bottom
            ) {
              // label.new(bar_index-1, high[1], "x", ...)
              console.log("重测upper_box信号:", {
                time: prevTime,
                candleHigh: candle.high,
                prevCandleHigh: prevCandle.high,
                upperBoxBottom: upperBox.bottom,
              });
              markers.push({
                time: prevTime,
                position: "aboveBar",
                color: config.lowerColor || "#af0d4b",
                shape: "circle",
                text: "x",
                size: 1,
              });
            }
          }

          // 检查重测 lower_box（按原始脚本：if low > lower_box.get_top() and low[1]<= lower_box.get_top()）
          // 注意：只有在上升趋势中才检查重测lower_box
          if (lowerBox && trendUp && i > 0) {
            if (candle.low > lowerBox.top && prevCandle.low <= lowerBox.top) {
              // label.new(bar_index-1, low[1], "+", ...)
              console.log("重测lower_box信号:", {
                time: prevTime,
                candleLow: candle.low,
                prevCandleLow: prevCandle.low,
                lowerBoxTop: lowerBox.top,
              });
              markers.push({
                time: prevTime,
                position: "belowBar",
                color: config.upperColor || "#13bd6e",
                shape: "circle",
                text: "+",
                size: 1,
              });
            }
          }
        }
      }

      // 保存价格线引用（用于清除）
      this.kalmanElements.priceLines = priceLines;

      // 一次性设置所有标记
      // 先获取当前所有标记，移除旧的超级趋势标记，然后添加新标记
      const currentMarkers = this.candlestickSeries.markers() || [];

      // 移除旧的超级趋势标记
      let filteredMarkers = currentMarkers;
      if (
        this.kalmanElements.markers &&
        this.kalmanElements.markers.length > 0
      ) {
        // 如果有保存的标记，通过时间和文本匹配移除
        filteredMarkers = currentMarkers.filter((m) => {
          return !this.kalmanElements.markers.some(
            (km) => km.time === m.time && km.text === m.text,
          );
        });
      } else {
        // 如果没有保存的标记，通过特征识别移除
        filteredMarkers = currentMarkers.filter((m) => {
          // 排除超级趋势相关的标记
          if (
            m.text &&
            (m.text.includes("↑") ||
              m.text.includes("↓") ||
              m.text === "x" ||
              m.text === "+")
          ) {
            if (
              m.shape === "arrowUp" ||
              m.shape === "arrowDown" ||
              (m.shape === "circle" && (m.text === "x" || m.text === "+"))
            ) {
              return false; // 移除旧的超级趋势标记
            }
          }
          return true;
        });
      }

      // 添加新标记
      if (markers.length > 0) {
        // 🔥 关键修复：确保 markers 按时间升序排列
        markers.sort((a, b) => a.time - b.time);
        const allMarkers = [...filteredMarkers, ...markers].sort(
          (a, b) => a.time - b.time,
        );

        this.candlestickSeries.setMarkers(allMarkers);
        this.kalmanElements.markers = markers;
        console.log(
          "超级趋势标记已添加:",
          markers.length,
          "个标记",
          markers.map((m) => ({
            time: m.time,
            text: m.text,
            shape: m.shape,
            position: m.position,
            color: m.color,
          })),
        );
      } else {
        // 即使没有新标记，也要清除旧的标记
        this.candlestickSeries.setMarkers(filteredMarkers);
        this.kalmanElements.markers = [];
        console.log(
          "超级趋势：未检测到趋势变化，已清除旧标记。数据长度:",
          allKlineData.length,
          "shortKalman长度:",
          shortKalman.length,
          "longKalman长度:",
          longKalman.length,
        );
      }
    },

    /**
     * 清除 Kalman 元素（按原始脚本逻辑）
     */
    clearKalmanElements() {
      // 清除价格线（模拟 box）
      if (
        this.candlestickSeries &&
        this.kalmanElements.priceLines &&
        this.kalmanElements.priceLines.length > 0
      ) {
        this.kalmanElements.priceLines.forEach((priceLine) => {
          try {
            this.candlestickSeries.removePriceLine(priceLine);
          } catch (e) {
            console.warn("移除价格线失败:", e);
          }
        });
      }

      // 清除 Kalman 标记（保留其他信号标记）
      if (this.candlestickSeries) {
        // 获取当前所有标记
        const currentMarkers = this.candlestickSeries.markers() || [];

        // 如果有保存的 Kalman 标记，移除它们
        if (
          this.kalmanElements.markers &&
          this.kalmanElements.markers.length > 0
        ) {
          // 移除 Kalman 标记（通过时间和文本匹配）
          const filteredMarkers = currentMarkers.filter((m) => {
            // 检查是否是 Kalman 标记
            const isKalmanMarker = this.kalmanElements.markers.some((km) => {
              // 匹配时间和文本，或者匹配特定的形状和文本组合
              return (
                (km.time === m.time && km.text === m.text) ||
                (km.time === m.time &&
                  ((km.text && km.text.includes("↑")) ||
                    (km.text && km.text.includes("↓")) ||
                    km.text === "x" ||
                    km.text === "+"))
              );
            });
            return !isKalmanMarker;
          });

          this.candlestickSeries.setMarkers(filteredMarkers);
          console.log("清除超级趋势标记:", {
            清除前: currentMarkers.length,
            清除后: filteredMarkers.length,
            清除数量: currentMarkers.length - filteredMarkers.length,
          });
        } else {
          // 如果没有保存的标记，尝试通过特征识别并清除
          // 超级趋势标记的特征：包含 '↑'、'↓'、'x'、'+' 文本，或者特定的形状
          const filteredMarkers = currentMarkers.filter((m) => {
            // 排除超级趋势相关的标记
            if (
              m.text &&
              (m.text.includes("↑") ||
                m.text.includes("↓") ||
                m.text === "x" ||
                m.text === "+")
            ) {
              // 检查是否是箭头形状（趋势变化标记）或圆形（重测信号标记）
              if (
                m.shape === "arrowUp" ||
                m.shape === "arrowDown" ||
                (m.shape === "circle" && (m.text === "x" || m.text === "+"))
              ) {
                return false; // 移除这个标记
              }
            }
            return true; // 保留其他标记
          });

          if (filteredMarkers.length !== currentMarkers.length) {
            this.candlestickSeries.setMarkers(filteredMarkers);
            console.log("通过特征清除超级趋势标记:", {
              清除前: currentMarkers.length,
              清除后: filteredMarkers.length,
              清除数量: currentMarkers.length - filteredMarkers.length,
            });
          }
        }
      }

      // 重置元素
      this.kalmanElements = {
        labels: [],
        boxes: [],
        markers: [],
        priceLines: [],
      };
    },

    /**
     * 清除超级趋势指标
     */
    clearKalmanIndicator() {
      // 清除指标线数据
      if (this.kalmanSeries.short) {
        this.kalmanSeries.short.setData([]);
      }
      if (this.kalmanSeries.long) {
        this.kalmanSeries.long.setData([]);
      }

      // 清除所有元素（标记、价格线等）
      this.clearKalmanElements();

      // 恢复默认K线颜色（当Kalman指标关闭时）
      if (this.candlestickSeries) {
        this.candlestickSeries.applyOptions({
          upColor: "#00C853", // 默认上升K线颜色
          downColor: "#FF1744", // 默认下降K线颜色
          wickUpColor: "#00C853", // 默认上升K线影线颜色
          wickDownColor: "#FF1744", // 默认下降K线影线颜色
        });
        // 重新设置数据以清除自定义颜色
        const currentData = this.candlestickSeries.data();
        if (currentData && currentData.length > 0) {
          const defaultData = currentData.map((candle) => ({
            time: candle.time,
            open: candle.open,
            high: candle.high,
            low: candle.low,
            close: candle.close,
            // 不设置 color 属性，恢复默认颜色
          }));
          this.candlestickSeries.setData(defaultData);
        }
      }
    },

    // ==================== SMC Lite 指标 ====================

    /**
     * 更新 SMC Lite 指标
     */
    updateSmcLiteIndicator(config) {
      if (!this.chart || !this.candlestickSeries) {
        return;
      }

      if (!config.enabled) {
        this.clearSmcLiteIndicator();
        return;
      }

      // 先清除所有旧的BOS和POI，避免重复
      this.smcLiteElements.supplyBos.forEach((bos) => {
        try {
          if (bos) {
            this.chart.removeSeries(bos);
          }
        } catch (error) {
          console.error("清除旧供应BOS失败:", error);
        }
      });
      this.smcLiteElements.demandBos.forEach((bos) => {
        try {
          if (bos) {
            this.chart.removeSeries(bos);
          }
        } catch (error) {
          console.error("清除旧需求BOS失败:", error);
        }
      });
      this.smcLiteElements.supplyPoi.forEach((poi) => {
        try {
          if (poi) {
            this.chart.removeSeries(poi);
          }
        } catch (error) {
          console.error("清除旧供应POI失败:", error);
        }
      });
      this.smcLiteElements.demandPoi.forEach((poi) => {
        try {
          if (poi) {
            this.chart.removeSeries(poi);
          }
        } catch (error) {
          console.error("清除旧需求POI失败:", error);
        }
      });

      // 清空数组
      this.smcLiteElements.supplyBos = [];
      this.smcLiteElements.demandBos = [];
      this.smcLiteElements.supplyPoi = [];
      this.smcLiteElements.demandPoi = [];

      // 获取所有K线数据
      const allKlineData = this.candlestickSeries.data();
      if (!allKlineData || allKlineData.length === 0) {
        return;
      }

      console.log("更新 SMC Lite 指标，数据量:", allKlineData.length);

      // 清除之前的元素
      this.clearSmcLiteIndicator();

      // 计算 ATR
      const atrPeriod = 50;
      const atrValues = this.calculateATR(allKlineData, atrPeriod);
      const atr =
        atrValues && atrValues.length > 0 ? atrValues[atrValues.length - 1] : 0;

      // 检测摆动高点和低点
      const swingLength = config.swingLength || 10;
      const swingHighs = [];
      const swingLows = [];
      const swingHighIndices = [];
      const swingLowIndices = [];

      // 检测摆动高点（pivot high）
      for (let i = swingLength; i < allKlineData.length - swingLength; i++) {
        let isPivotHigh = true;
        for (let j = i - swingLength; j < i; j++) {
          if (allKlineData[j].high >= allKlineData[i].high) {
            isPivotHigh = false;
            break;
          }
        }
        for (let j = i + 1; j <= i + swingLength; j++) {
          if (allKlineData[j].high >= allKlineData[i].high) {
            isPivotHigh = false;
            break;
          }
        }
        if (isPivotHigh) {
          swingHighs.push(allKlineData[i].high);
          swingHighIndices.push(i); // 记录摆动点的索引
        }
      }

      // 检测摆动低点（pivot low）
      for (let i = swingLength; i < allKlineData.length - swingLength; i++) {
        let isPivotLow = true;
        for (let j = i - swingLength; j < i; j++) {
          if (allKlineData[j].low <= allKlineData[i].low) {
            isPivotLow = false;
            break;
          }
        }
        for (let j = i + 1; j <= i + swingLength; j++) {
          if (allKlineData[j].low <= allKlineData[i].low) {
            isPivotLow = false;
            break;
          }
        }
        if (isPivotLow) {
          swingLows.push(allKlineData[i].low);
          swingLowIndices.push(i); // 记录摆动点的索引（不是 i - swingLength）
        }
      }

      // 管理供应区和需求区
      const historyToKeep = config.historyToKeep || 20;
      const boxWidth = config.boxWidth || 2.5;
      const atrBuffer = atr * (boxWidth / 10);

      console.log("SMC Lite 检测结果:", {
        摆动高点数量: swingHighs.length,
        摆动低点数量: swingLows.length,
        ATR: atr,
        ATR缓冲: atrBuffer,
        数据长度: allKlineData.length,
        摆动高点索引示例: swingHighIndices.slice(0, 3),
        摆动低点索引示例: swingLowIndices.slice(0, 3),
      });

      if (swingHighs.length === 0 && swingLows.length === 0) {
        console.warn(
          "SMC Lite: 未检测到任何摆动点，可能需要更多数据或调整 swingLength",
        );
        return;
      }

      // 处理供应区（从摆动高点）
      const supplyBoxes = [];
      const supplyPoi = [];
      const supplyPoiList = []; // 临时存储所有POI信息（价格、series）
      for (
        let i = Math.max(0, swingHighs.length - historyToKeep);
        i < swingHighs.length;
        i++
      ) {
        const swingHigh = swingHighs[i];
        const swingIndex = swingHighIndices[i];
        const boxTop = swingHigh;
        const boxBottom = boxTop - atrBuffer;
        const poi = (boxTop + boxBottom) / 2;
        const startTime = allKlineData[swingIndex].time;
        const endTime = allKlineData[allKlineData.length - 1].time;

        // 检查是否重叠
        let okayToDraw = true;
        for (let j = 0; j < supplyBoxes.length; j++) {
          const existingPoi = supplyBoxes[j].poi;
          const atrThreshold = atr * 2;
          if (Math.abs(poi - existingPoi) < atrThreshold) {
            okayToDraw = false;
            break;
          }
        }

        if (okayToDraw) {
          // 检查是否被突破（如果被突破，颜色互换）
          let isBroken = false;
          for (let j = swingIndex; j < allKlineData.length; j++) {
            if (allKlineData[j].close >= boxTop) {
              isBroken = true;
              // 供应区被突破，应该变成需求区（颜色变成青色）
              break;
            }
          }

          // 无论是否被突破，都创建box，但颜色根据是否被突破来决定
          // 如果被突破，使用需求区的颜色（青色），否则使用供应区的颜色（灰色）
          const supplyPriceLineColor = isBroken ? "#00C8C8" : "#808080"; // 被突破时用青色，否则用灰色
          const topLine = this.candlestickSeries.createPriceLine({
            price: boxTop,
            color: supplyPriceLineColor,
            lineWidth: 2,
            lineStyle: 0,
            axisLabelVisible: false,
          });

          const bottomLine = this.candlestickSeries.createPriceLine({
            price: boxBottom,
            color: supplyPriceLineColor,
            lineWidth: 2,
            lineStyle: 0,
            axisLabelVisible: false,
          });

          // 创建框的数据点（从摆动点开始到当前）
          // 只创建两个数据点：开始点（摆动点）和结束点（当前 K 线），而不是所有中间点
          // 这样可以避免边界线延伸到图表最右边
          const boxTopData = [
            {
              time: allKlineData[swingIndex].time,
              value: boxTop,
            },
            {
              time: allKlineData[allKlineData.length - 1].time,
              value: boxTop,
            },
          ];
          const boxBottomData = [
            {
              time: allKlineData[swingIndex].time,
              value: boxBottom,
            },
            {
              time: allKlineData[allKlineData.length - 1].time,
              value: boxBottom,
            },
          ];

          // 注意：由于 Lightweight Charts 的限制，Area Series 总是从 0 开始填充
          // 无法直接创建从 boxBottom 到 boxTop 的填充区域
          // 因此，我们只使用边界线来标识 box 的范围，不填充区域
          // 这样至少 box 的边界是清晰的，用户可以清楚地看到 box 的范围

          const supplyAreaTop = null;
          const supplyAreaBottom = null;
          const supplyHistogram = null;
          const supplyPriceScaleId = null;

          // 使用两个 Line Series 绘制上下边界（加粗，确保可见）
          // 如果被突破，使用需求区的颜色（青色），否则使用供应区的颜色（灰色）
          const supplyLineColor = isBroken ? "#00C8C8" : "#808080"; // 被突破时用青色，否则用灰色
          const topLineSeries = this.chart.addLineSeries({
            color: supplyLineColor,
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
          });
          topLineSeries.setData(boxTopData);

          const bottomLineSeries = this.chart.addLineSeries({
            color: supplyLineColor,
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
          });
          bottomLineSeries.setData(boxBottomData);

          console.log("✅ 创建供应区边界线:", {
            topLineSeries: !!topLineSeries,
            bottomLineSeries: !!bottomLineSeries,
            topDataPoints: boxTopData.length,
            bottomDataPoints: boxBottomData.length,
            boxTop,
            boxBottom,
            boxHeight: boxTop - boxBottom,
            color: supplyLineColor,
          });

          // 创建POI标签（使用Line Series代替Price Line，确保在K线下方）
          // 如果被突破，使用需求区的颜色（青色），否则使用供应区的颜色（灰色）
          const poiLineColor = isBroken ? "#00C8C8" : "#808080"; // 被突破时用青色，否则用灰色
          const poiLineSeries = this.chart.addLineSeries({
            color: poiLineColor,
            lineWidth: 1,
            lineStyle: 2, // dashed
            priceLineVisible: false,
            lastValueVisible: false,
          });
          poiLineSeries.setData([
            {
              time: allKlineData[swingIndex].time,
              value: poi,
            },
            {
              time: allKlineData[allKlineData.length - 1].time,
              value: poi,
            },
          ]);
          // 为了兼容性，仍然创建一个priceLine对象（但不使用）
          const poiLine = null;

          supplyBoxes.push({
            areaTop: supplyAreaTop,
            areaBottom: supplyAreaBottom,
            topLineSeries: topLineSeries,
            bottomLineSeries: bottomLineSeries,
            topLine: topLine,
            bottomLine: bottomLine,
            poi: poi,
            top: boxTop,
            bottom: boxBottom,
            startTime: startTime,
          });
          this.smcLiteElements.supplyBoxes.push({
            areaTop: supplyAreaTop,
            areaBottom: supplyAreaBottom,
            topLineSeries: topLineSeries,
            bottomLineSeries: bottomLineSeries,
            topLine: topLine,
            bottomLine: bottomLine,
            poi: poiLineSeries,
          });

          console.log("✅ 创建供应区 box（仅边界线）:", {
            swingIndex,
            boxTop,
            boxBottom,
            boxHeight: boxTop - boxBottom,
            dataPoints: boxTopData.length,
            topLineSeries: !!topLineSeries,
            bottomLineSeries: !!bottomLineSeries,
            topLine: !!topLine,
            bottomLine: !!bottomLine,
          });

          // 临时存储POI信息，稍后按价格排序并只保留最大的2条
          supplyPoiList.push({
            price: poi,
            series: poiLineSeries,
          });
        }
      }

      // 对供应区POI按价格从高到低排序，只保留最大的2条
      supplyPoiList.sort((a, b) => b.price - a.price);
      const top2SupplyPoi = supplyPoiList.slice(0, 2);
      top2SupplyPoi.forEach((item) => {
        this.smcLiteElements.supplyPoi.push(item.series);
      });
      // 删除多余的POI线条
      supplyPoiList.slice(2).forEach((item) => {
        try {
          this.chart.removeSeries(item.series);
        } catch (error) {
          console.error("删除多余供应区POI失败:", error);
        }
      });

      // 暂时不处理供应区BOS，稍后合并所有BOS一起处理

      // 处理需求区（从摆动低点）
      const demandBoxes = [];
      const demandPoi = [];
      const demandPoiList = []; // 临时存储所有POI信息（价格、series）
      for (
        let i = Math.max(0, swingLows.length - historyToKeep);
        i < swingLows.length;
        i++
      ) {
        const swingLow = swingLows[i];
        const swingIndex = swingLowIndices[i];
        const boxBottom = swingLow;
        const boxTop = boxBottom + atrBuffer;
        const poi = (boxTop + boxBottom) / 2;
        const startTime = allKlineData[swingIndex].time;
        const endTime = allKlineData[allKlineData.length - 1].time;

        // 检查是否重叠
        let okayToDraw = true;
        for (let j = 0; j < demandBoxes.length; j++) {
          const existingPoi = demandBoxes[j].poi;
          const atrThreshold = atr * 2;
          if (Math.abs(poi - existingPoi) < atrThreshold) {
            okayToDraw = false;
            break;
          }
        }

        if (okayToDraw) {
          // 检查是否被突破（如果被突破，颜色互换）
          let isBroken = false;
          for (let j = swingIndex; j < allKlineData.length; j++) {
            if (allKlineData[j].close <= boxBottom) {
              isBroken = true;
              // 需求区被突破，应该变成供应区（颜色变成灰色）
              break;
            }
          }

          // 无论是否被突破，都创建box，但颜色根据是否被突破来决定
          // 如果被突破，使用供应区的颜色（灰色），否则使用需求区的颜色（青色）
          const demandPriceLineColor = isBroken ? "#808080" : "#00C8C8"; // 被突破时用灰色，否则用青色
          const topLine = this.candlestickSeries.createPriceLine({
            price: boxTop,
            color: demandPriceLineColor,
            lineWidth: 2,
            lineStyle: 0,
            axisLabelVisible: false,
          });

          const bottomLine = this.candlestickSeries.createPriceLine({
            price: boxBottom,
            color: demandPriceLineColor,
            lineWidth: 2,
            lineStyle: 0,
            axisLabelVisible: false,
          });

          // 创建框的数据点（从摆动点开始到当前）
          // 只创建两个数据点：开始点（摆动点）和结束点（当前 K 线），而不是所有中间点
          // 这样可以避免边界线延伸到图表最右边
          const boxTopData = [
            {
              time: allKlineData[swingIndex].time,
              value: boxTop,
            },
            {
              time: allKlineData[allKlineData.length - 1].time,
              value: boxTop,
            },
          ];
          const boxBottomData = [
            {
              time: allKlineData[swingIndex].time,
              value: boxBottom,
            },
            {
              time: allKlineData[allKlineData.length - 1].time,
              value: boxBottom,
            },
          ];

          // 注意：由于 Lightweight Charts 的限制，Area Series 总是从 0 开始填充
          // 无法直接创建从 boxBottom 到 boxTop 的填充区域
          // 因此，我们只使用边界线来标识 box 的范围，不填充区域
          // 这样至少 box 的边界是清晰的，用户可以清楚地看到 box 的范围

          const demandAreaTop = null;
          const demandAreaBottom = null;
          const demandHistogram = null;
          const demandPriceScaleId = null;

          // 使用两个 Line Series 绘制上下边界（加粗，确保可见）
          // 如果被突破，使用供应区的颜色（灰色），否则使用需求区的颜色（青色）
          const demandLineColor = isBroken ? "#808080" : "#00C8C8"; // 被突破时用灰色，否则用青色
          const topLineSeries = this.chart.addLineSeries({
            color: demandLineColor,
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
          });
          topLineSeries.setData(boxTopData);

          const bottomLineSeries = this.chart.addLineSeries({
            color: demandLineColor,
            lineWidth: 2,
            priceLineVisible: false,
            lastValueVisible: false,
          });
          bottomLineSeries.setData(boxBottomData);

          console.log("✅ 创建需求区边界线:", {
            topLineSeries: !!topLineSeries,
            bottomLineSeries: !!bottomLineSeries,
            topDataPoints: boxTopData.length,
            bottomDataPoints: boxBottomData.length,
            boxTop,
            boxBottom,
            boxHeight: boxTop - boxBottom,
            color: demandLineColor,
          });

          // 创建POI标签（使用Line Series代替Price Line，确保在K线下方）
          // 如果被突破，使用供应区的颜色（灰色），否则使用需求区的颜色（青色）
          const poiLineColor = isBroken ? "#808080" : "#00C8C8"; // 被突破时用灰色，否则用青色
          const poiLineSeries = this.chart.addLineSeries({
            color: poiLineColor,
            lineWidth: 1,
            lineStyle: 2, // dashed
            priceLineVisible: false,
            lastValueVisible: false,
          });
          poiLineSeries.setData([
            {
              time: allKlineData[swingIndex].time,
              value: poi,
            },
            {
              time: allKlineData[allKlineData.length - 1].time,
              value: poi,
            },
          ]);
          // 为了兼容性，仍然创建一个priceLine对象（但不使用）
          const poiLine = null;

          demandBoxes.push({
            areaTop: demandAreaTop,
            areaBottom: demandAreaBottom,
            topLineSeries: topLineSeries,
            bottomLineSeries: bottomLineSeries,
            topLine: topLine,
            bottomLine: bottomLine,
            poi: poi,
            top: boxTop,
            bottom: boxBottom,
            startTime: startTime,
          });
          this.smcLiteElements.demandBoxes.push({
            areaTop: demandAreaTop,
            areaBottom: demandAreaBottom,
            topLineSeries: topLineSeries,
            bottomLineSeries: bottomLineSeries,
            topLine: topLine,
            bottomLine: bottomLine,
            poi: poiLineSeries,
          });

          // 临时存储POI信息，稍后按价格排序并只保留最小的2条
          demandPoiList.push({
            price: poi,
            series: poiLineSeries,
          });
        }
      }

      // 对需求区POI按价格从低到高排序，只保留最小的2条
      demandPoiList.sort((a, b) => a.price - b.price);
      const top2DemandPoi = demandPoiList.slice(0, 2);
      top2DemandPoi.forEach((item) => {
        this.smcLiteElements.demandPoi.push(item.series);
      });
      // 删除多余的POI线条
      demandPoiList.slice(2).forEach((item) => {
        try {
          this.chart.removeSeries(item.series);
        } catch (error) {
          console.error("删除多余需求区POI失败:", error);
        }
      });

      // 不再创建BOS标记，所以不需要处理BOS列表

      // 绘制价格行为标签
      if (config.showPriceActionLabels) {
        this.drawPriceActionLabels(
          swingHighs,
          swingLows,
          swingHighIndices,
          swingLowIndices,
          allKlineData,
          config,
        );
      }

      // 绘制Zig Zag线
      if (config.showZigZag) {
        this.drawZigZagLine(swingLength, allKlineData, config);
      }
    },

    /**
     * 绘制价格行为标签（HH, LH, HL, LL）
     */
    drawPriceActionLabels(
      swingHighs,
      swingLows,
      swingHighIndices,
      swingLowIndices,
      allKlineData,
      config,
    ) {
      const labels = [];

      // 处理摆动高点标签
      for (let i = 0; i < swingHighs.length; i++) {
        const label =
          i === 0 ? "HH" : swingHighs[i] >= swingHighs[i - 1] ? "HH" : "LH";
        labels.push({
          time: allKlineData[swingHighIndices[i]].time,
          position: "aboveBar",
          color: config.swingTypeColor || "#000000",
          shape: "circle",
          text: label,
          size: 0,
        });
      }

      // 处理摆动低点标签
      for (let i = 0; i < swingLows.length; i++) {
        const label =
          i === 0 ? "LL" : swingLows[i] <= swingLows[i - 1] ? "LL" : "HL";
        labels.push({
          time: allKlineData[swingLowIndices[i]].time,
          position: "belowBar",
          color: config.swingTypeColor || "#000000",
          shape: "circle",
          text: label,
          size: 0,
        });
      }

      this.smcLiteElements.priceActionLabels = labels;
      const currentMarkers = this.candlestickSeries.markers() || [];
      // 🔥 关键修复：确保 markers 按时间升序排列
      labels.sort((a, b) => a.time - b.time);
      const allMarkers = [...currentMarkers, ...labels].sort(
        (a, b) => a.time - b.time,
      );

      this.candlestickSeries.setMarkers(allMarkers);
    },

    /**
     * 绘制Zig Zag线
     */
    drawZigZagLine(swingLength, allKlineData, config) {
      if (!this.chart) return;

      // 简化的Zig Zag实现 - 基于价格百分比变化
      const zigZagPercent = 0.05; // 5%的价格变化触发转向
      let direction = 0; // 0: 未确定, 1: 上涨, -1: 下跌
      let lastPivotPrice = allKlineData[0].high;
      let lastPivotTime = allKlineData[0].time;
      const zigZagPoints = [];

      // 添加起始点
      zigZagPoints.push({ time: lastPivotTime, value: lastPivotPrice });

      for (let i = 1; i < allKlineData.length; i++) {
        const currentHigh = allKlineData[i].high;
        const currentLow = allKlineData[i].low;
        const currentTime = allKlineData[i].time;

        if (direction >= 0) {
          // 当前是上涨趋势或未确定
          // 检查是否形成新的高点
          if (currentHigh > lastPivotPrice * (1 + zigZagPercent)) {
            lastPivotPrice = currentHigh;
            lastPivotTime = currentTime;
            direction = 1;
            zigZagPoints.push({ time: lastPivotTime, value: lastPivotPrice });
          }
          // 检查是否转为下跌
          else if (currentLow < lastPivotPrice * (1 - zigZagPercent)) {
            lastPivotPrice = currentLow;
            lastPivotTime = currentTime;
            direction = -1;
            zigZagPoints.push({ time: lastPivotTime, value: lastPivotPrice });
          }
        } else {
          // 当前是下跌趋势
          // 检查是否形成新的低点
          if (currentLow < lastPivotPrice * (1 - zigZagPercent)) {
            lastPivotPrice = currentLow;
            lastPivotTime = currentTime;
            direction = -1;
            zigZagPoints.push({ time: lastPivotTime, value: lastPivotPrice });
          }
          // 检查是否转为上涨
          else if (currentHigh > lastPivotPrice * (1 + zigZagPercent)) {
            lastPivotPrice = currentHigh;
            lastPivotTime = currentTime;
            direction = 1;
            zigZagPoints.push({ time: lastPivotTime, value: lastPivotPrice });
          }
        }
      }

      if (zigZagPoints.length > 0 && config.showZigZag) {
        const zigZagSeries = this.chart.addLineSeries({
          color: config.zigZagColor || "#FF6B35",
          lineWidth: 2,
          priceLineVisible: false,
          lastValueVisible: false,
        });
        zigZagSeries.setData(zigZagPoints);
        this.smcLiteElements.zigZagLine = zigZagSeries;
      }
    },

    /**
     * 清除 SMC Lite 指标
     */
    clearSmcLiteIndicator() {
      if (!this.chart || !this.candlestickSeries) return;

      // 辅助函数：安全地移除价格线
      const safeRemovePriceLine = (priceLine) => {
        if (!priceLine) return;
        try {
          // 检查是否是有效的价格线对象（有 _internal_priceLine 方法或类似属性）
          if (typeof priceLine === "object" && priceLine !== null) {
            // 尝试移除价格线
            this.candlestickSeries.removePriceLine(priceLine);
          }
        } catch (error) {
          // 忽略移除失败的错误，可能价格线已经被移除或无效
          console.debug("移除价格线失败（已忽略）:", error.message);
        }
      };

      // 清除供应区框
      this.smcLiteElements.supplyBoxes.forEach((element) => {
        try {
          if (element.areaTop) {
            try {
              this.chart.removeSeries(element.areaTop);
            } catch (e) {
              console.debug("移除供应区顶部系列失败（已忽略）:", e.message);
            }
          }
          if (element.areaBottom) {
            try {
              this.chart.removeSeries(element.areaBottom);
            } catch (e) {
              console.debug("移除供应区底部系列失败（已忽略）:", e.message);
            }
          }
          if (element.topLineSeries) {
            try {
              this.chart.removeSeries(element.topLineSeries);
            } catch (e) {
              console.debug("移除供应区顶部线系列失败（已忽略）:", e.message);
            }
          }
          if (element.bottomLineSeries) {
            try {
              this.chart.removeSeries(element.bottomLineSeries);
            } catch (e) {
              console.debug("移除供应区底部线系列失败（已忽略）:", e.message);
            }
          }
          safeRemovePriceLine(element.topLine);
          safeRemovePriceLine(element.bottomLine);
          safeRemovePriceLine(element.poi);
        } catch (error) {
          console.error("清除供应区失败:", error);
        }
      });

      // 清除需求区框
      this.smcLiteElements.demandBoxes.forEach((element) => {
        try {
          if (element.areaTop) {
            try {
              this.chart.removeSeries(element.areaTop);
            } catch (e) {
              console.debug("移除需求区顶部系列失败（已忽略）:", e.message);
            }
          }
          if (element.areaBottom) {
            try {
              this.chart.removeSeries(element.areaBottom);
            } catch (e) {
              console.debug("移除需求区底部系列失败（已忽略）:", e.message);
            }
          }
          if (element.topLineSeries) {
            try {
              this.chart.removeSeries(element.topLineSeries);
            } catch (e) {
              console.debug("移除需求区顶部线系列失败（已忽略）:", e.message);
            }
          }
          if (element.bottomLineSeries) {
            try {
              this.chart.removeSeries(element.bottomLineSeries);
            } catch (e) {
              console.debug("移除需求区底部线系列失败（已忽略）:", e.message);
            }
          }
          safeRemovePriceLine(element.topLine);
          safeRemovePriceLine(element.bottomLine);
          safeRemovePriceLine(element.poi);
        } catch (error) {
          console.error("清除需求区失败:", error);
        }
      });

      // 清除BOS
      this.smcLiteElements.supplyBos.forEach((bos) => {
        safeRemovePriceLine(bos);
      });

      this.smcLiteElements.demandBos.forEach((bos) => {
        safeRemovePriceLine(bos);
      });

      // 清除Zig Zag线
      if (this.smcLiteElements.zigZagLine) {
        try {
          this.chart.removeSeries(this.smcLiteElements.zigZagLine);
        } catch (error) {
          console.error("清除Zig Zag线失败:", error);
        }
      }

      // 清除价格行为标签
      if (this.smcLiteElements.priceActionLabels.length > 0) {
        const currentMarkers = this.candlestickSeries.markers() || [];
        const filteredMarkers = currentMarkers.filter((m) => {
          return !this.smcLiteElements.priceActionLabels.some(
            (label) => label.time === m.time && label.text === m.text,
          );
        });
        this.candlestickSeries.setMarkers(filteredMarkers);
      }

      // 重置数组
      this.smcLiteElements = {
        supplyBoxes: [],
        demandBoxes: [],
        supplyPoi: [],
        demandPoi: [],
        supplyBos: [],
        demandBos: [],
        zigZagLine: null,
        priceActionLabels: [],
      };
    },

    // ==================== Range Filter 指标 ====================

    /**
     * 更新 Range Filter 指标
     */
    updateRangeFilterIndicator(config) {
      if (!this.chart || !this.candlestickSeries) {
        return;
      }

      if (!config.enabled) {
        this.clearRangeFilterIndicator();
        return;
      }

      // 获取所有K线数据
      const allKlineData = this.candlestickSeries.data();
      if (!allKlineData || allKlineData.length === 0) {
        return;
      }

      console.log("更新 Range Filter 指标，数据量:", allKlineData.length);
      console.log(
        "前5条K线数据样本:",
        allKlineData.slice(0, 5).map((k) => ({
          time: new Date(k.time * 1000).toLocaleString(),
          open: k.open,
          high: k.high,
          low: k.low,
          close: k.close,
        })),
      );

      // 清除之前的元素
      this.clearRangeFilterIndicator();

      // 计算Range Filter数据
      const rangeFilterData = this.calculateRangeFilter(allKlineData, config);

      // 创建过滤器线
      if (rangeFilterData.filter.length > 0) {
        const filterSeries = this.chart.addLineSeries({
          color: "#2196F3",
          lineWidth: 2,
          title: "Range Filter",
        });
        filterSeries.setData(rangeFilterData.filter);
        this.rangeFilterSeries.filter = filterSeries;
      }

      // 创建上轨线
      if (rangeFilterData.hiBand.length > 0) {
        const hiBandSeries = this.chart.addLineSeries({
          color: "#4CAF50",
          lineWidth: 1,
          lineStyle: 1, // 虚线
          title: "High Band",
        });
        hiBandSeries.setData(rangeFilterData.hiBand);
        this.rangeFilterSeries.hiBand = hiBandSeries;
      }

      // 创建下轨线
      if (rangeFilterData.loBand.length > 0) {
        const loBandSeries = this.chart.addLineSeries({
          color: "#F44336",
          lineWidth: 1,
          lineStyle: 1, // 虚线
          title: "Low Band",
        });
        loBandSeries.setData(rangeFilterData.loBand);
        this.rangeFilterSeries.loBand = loBandSeries;
      }

      // 移除填充区域，用户不需要填充颜色

      // 添加买卖信号标记
      if (config.showSignals && rangeFilterData.signals.length > 0) {
        const markers = rangeFilterData.signals.map((signal) => ({
          time: signal.time,
          position: signal.type === "BUY" ? "belowBar" : "aboveBar",
          color: signal.type === "BUY" ? "#4CAF50" : "#F44336",
          shape: signal.type === "BUY" ? "arrowUp" : "arrowDown",
          text: signal.type,
          size: 2,
        }));

        // 获取现有标记并合并
        const existingMarkers = this.candlestickSeries.markers() || [];
        const newMarkers = [...existingMarkers, ...markers];
        this.candlestickSeries.setMarkers(newMarkers);

        // 保存标记引用以便后续清除
        this.rangeFilterElements.markers = markers;
      }
    },

    /**
     * 计算Range Filter指标数据
     */
    calculateRangeFilter(klineData, config) {
      const result = {
        filter: [],
        hiBand: [],
        loBand: [],
        signals: [],
      };

      if (!klineData || klineData.length < 2) {
        return result;
      }

      console.log("开始计算Range Filter，K线数据量:", klineData.length);

      // 初始化数组用于存储计算结果
      const rfilt = [klineData[0].close, klineData[0].close]; // Range Filter数组
      let condIni = 0; // 信号初始化状态
      let signalCount = 0; // 信号计数器

      for (let i = 0; i < klineData.length; i++) {
        const current = klineData[i];
        const h =
          config.movementSource === "Wicks" ? current.high : current.close;
        const l =
          config.movementSource === "Wicks" ? current.low : current.close;

        // 计算范围大小
        const range = this.calculateRangeSizeForFilter(klineData, i, config);

        // 计算Range Filter
        const rfResult = this.calculateRangeFilterValue(
          h,
          l,
          range,
          rfilt,
          i,
          config,
        );
        rfilt.push(rfResult.filt);

        // 构建结果数据
        result.filter.push({
          time: current.time,
          value: rfResult.filt,
        });

        result.hiBand.push({
          time: current.time,
          value: rfResult.filt + range,
        });

        result.loBand.push({
          time: current.time,
          value: rfResult.filt - range,
        });

        // 检测买卖信号
        if (i >= 1) {
          const prevFilter = result.filter[i - 1].value;
          const currFilter = result.filter[i].value;
          const prevClose = klineData[i - 1].close;

          const upward = currFilter > prevFilter ? 1 : 0;
          const downward = currFilter < prevFilter ? 1 : 0;

          // Trading Conditions Logic (基于Pine Script原文)
          // longCond = close > filt and upward > 0
          const longCond = current.close > currFilter && upward > 0;

          // shortCond = close < filt and downward > 0
          const shortCond = current.close < currFilter && downward > 0;

          const newCondIni = longCond ? 1 : shortCond ? -1 : condIni;

          const longCondition = longCond && condIni === -1;
          const shortCondition = shortCond && condIni === 1;

          // 调试信息 - 只在过滤器变化时输出
          if (currFilter !== prevFilter) {
            console.log(
              `Range Filter过滤器变化 [${i}]: prev=${prevFilter.toFixed(2)}, curr=${currFilter.toFixed(2)}, close=${current.close.toFixed(2)}, upward=${upward}, downward=${downward}`,
            );
          }

          if (longCondition) {
            console.log(
              `🎯 Range Filter BUY信号 at ${new Date(current.time * 1000).toLocaleString()}: close=${current.close}, filter=${currFilter}, upward=${upward}, prevCondIni=${condIni}`,
            );
            result.signals.push({
              time: current.time,
              type: "BUY",
            });
          } else if (shortCondition) {
            console.log(
              `🎯 Range Filter SELL信号 at ${new Date(current.time * 1000).toLocaleString()}: close=${current.close}, filter=${currFilter}, downward=${downward}, prevCondIni=${condIni}`,
            );
            result.signals.push({
              time: current.time,
              type: "SELL",
            });
          }

          condIni = newCondIni;
        }
      }

      console.log(
        `Range Filter计算完成: BUY=${result.signals.filter((s) => s.type === "BUY").length}, SELL=${result.signals.filter((s) => s.type === "SELL").length}`,
      );

      return result;
    },

    /**
     * 计算范围大小（用于Range Filter）
     */
    calculateRangeSizeForFilter(klineData, index, config) {
      const { rangeScale, rangeSize, rangePeriod } = config;

      if (index < rangePeriod) {
        return rangeSize;
      }

      switch (rangeScale) {
        case "Average Change":
          let sum = 0;
          const start = Math.max(0, index - rangePeriod + 1);
          for (let i = start; i <= index; i++) {
            if (i > 0) {
              sum += Math.abs(klineData[i].close - klineData[i - 1].close);
            }
          }
          return (sum / Math.max(1, index - start + 1)) * rangeSize;

        case "ATR":
          return this.calculateATR(klineData, index, rangePeriod) * rangeSize;

        case "Standard Deviation":
          return (
            this.calculateStdDev(klineData, index, rangePeriod) * rangeSize
          );

        case "% of Price":
          return (klineData[index].close * rangeSize) / 100;

        case "Points":
          return rangeSize;

        default:
          return rangeSize;
      }
    },

    /**
     * 计算Range Filter值
     */
    calculateRangeFilterValue(h, l, range, rfilt, index, config) {
      const currentFilt = rfilt[rfilt.length - 1];
      let newFilt = currentFilt;

      if (config.filterType === "Type 1") {
        if (h - range > currentFilt) {
          newFilt = h - range;
        }
        if (l + range < currentFilt) {
          newFilt = l + range;
        }
      } else {
        // Type 2
        if (h >= currentFilt + range) {
          newFilt =
            currentFilt + Math.floor(Math.abs(h - currentFilt) / range) * range;
        }
        if (l <= currentFilt - range) {
          newFilt =
            currentFilt - Math.floor(Math.abs(l - currentFilt) / range) * range;
        }
      }

      return { filt: newFilt };
    },

    /**
     * 清除 Range Filter 指标
     */
    clearRangeFilterIndicator() {
      if (!this.chart) return;

      // 清除过滤器线
      if (this.rangeFilterSeries.filter) {
        try {
          this.chart.removeSeries(this.rangeFilterSeries.filter);
        } catch (error) {
          console.error("清除Range Filter过滤器线失败:", error);
        }
      }

      // 清除上轨线
      if (this.rangeFilterSeries.hiBand) {
        try {
          this.chart.removeSeries(this.rangeFilterSeries.hiBand);
        } catch (error) {
          console.error("清除Range Filter上轨线失败:", error);
        }
      }

      // 清除下轨线
      if (this.rangeFilterSeries.loBand) {
        try {
          this.chart.removeSeries(this.rangeFilterSeries.loBand);
        } catch (error) {
          console.error("清除Range Filter下轨线失败:", error);
        }
      }

      // 填充区域已移除，不需要清除

      // 清除信号标记
      if (this.rangeFilterElements.markers.length > 0) {
        const currentMarkers = this.candlestickSeries.markers() || [];
        const filteredMarkers = currentMarkers.filter(
          (m) =>
            !this.rangeFilterElements.markers.some(
              (marker) => marker.time === m.time && marker.text === m.text,
            ),
        );
        this.candlestickSeries.setMarkers(filteredMarkers);
      }

      // 重置数据结构
      this.rangeFilterSeries = {
        filter: null,
        hiBand: null,
        loBand: null,
      };
      this.rangeFilterElements = {
        markers: [],
      };
    },

    // ==================== Phenom 指标 ====================

    /**
     * 初始化Phenom系列
     */
    initPhenomSeries() {
      if (!this.chart) return;

      // 初始化所有均线系列
      this.phenomSeries.ema1 = this.chart.addLineSeries({
        color: "#ff6b6b",
        lineWidth: 1,
        lineStyle: 0,
        title: "EMA9",
        priceLineVisible: false,
        lastValueVisible: true,
        priceFormat: {
          type: "price",
          precision: 2,
          minMove: 0.01,
        },
      });
      this.phenomSeries.ema2 = this.chart.addLineSeries({
        color: "#4ecdc4",
        lineWidth: 1,
        lineStyle: 0,
        title: "EMA21",
        priceLineVisible: false,
        lastValueVisible: true,
        priceFormat: {
          type: "price",
          precision: 2,
          minMove: 0.01,
        },
      });
      this.phenomSeries.ema3 = this.chart.addLineSeries({
        color: "#45b7d1",
        lineWidth: 1,
        lineStyle: 0,
        title: "EMA55",
        priceLineVisible: false,
        lastValueVisible: true,
        priceFormat: {
          type: "price",
          precision: 2,
          minMove: 0.01,
        },
      });
      this.phenomSeries.ema4 = this.chart.addLineSeries({
        color: "#f9ca24",
        lineWidth: 1,
        lineStyle: 0,
        title: "EMA144",
        priceLineVisible: false,
        lastValueVisible: true,
        priceFormat: {
          type: "price",
          precision: 2,
          minMove: 0.01,
        },
      });

      if (this.phenom.showStop) {
        this.phenomSeries.stopLong = this.chart.addLineSeries({
          color: "#00ff00",
          lineWidth: 1,
          lineStyle: 2,
          title: "多头止损",
        });
        this.phenomSeries.stopShort = this.chart.addLineSeries({
          color: "#ff0000",
          lineWidth: 1,
          lineStyle: 2,
          title: "空头止损",
        });
      }
    },

    /**
     * 更新流动性指标
     */
    updateApexTrendLiquidityIndicator(config) {
      if (!this.chart || !this.candlestickSeries) return;

      if (config.enabled) {
        // 初始化系列（如果需要）
        if (!this.apexTrendLiquiditySeries.upper) {
          this.initApexTrendLiquiditySeries();
        }

        // 优先使用当前图表中的所有K线数据
        let allKlineData = this.candlestickSeries.data();
        if (!allKlineData || allKlineData.length === 0) {
          allKlineData = this.data;
        }
        if (!allKlineData || allKlineData.length === 0) return;

        // 计算趋势云
        this.updateTrendCloud(config, allKlineData);

        // 计算流动性区域
        if (config.showLiquidity) {
          this.updateLiquidityZones(config, allKlineData);
        }

        // 更新信号标记
        this.updateLiquiditySignals(config, allKlineData);

        // 应用信号标记到图表
        this.applyLiquiditySignalsToChart();

        // 更新HUD仪表板
        if (config.showHud) {
          this.updateLiquidityHud(config, allKlineData);
        }

        // 着色K线
        if (config.colorCandles) {
          this.updateCandleColors(config, allKlineData);
        }
      } else {
        // 清理所有系列
        this.cleanupApexTrendLiquiditySeries();
      }
    },

    /**
     * 初始化流动性指标系列
     */
    initApexTrendLiquiditySeries() {
      if (!this.chart) return;

      // 创建趋势通道线系列
      this.apexTrendLiquiditySeries.upper = this.chart.addLineSeries({
        color: "#78909C", // 默认中性颜色
        lineWidth: 2,
        lineStyle: 0,
        title: "趋势上轨",
        priceLineVisible: false,
        lastValueVisible: true,
      });

      this.apexTrendLiquiditySeries.lower = this.chart.addLineSeries({
        color: "#78909C", // 默认中性颜色
        lineWidth: 2,
        lineStyle: 0,
        title: "趋势下轨",
        priceLineVisible: false,
        lastValueVisible: true,
      });

      // 不创建填充区域，只保留线条
    },

    /**
     * 清理流动性指标系列
     */
    cleanupApexTrendLiquiditySeries() {
      // 清理系列
      if (this.apexTrendLiquiditySeries.upper) {
        this.chart.removeSeries(this.apexTrendLiquiditySeries.upper);
        this.apexTrendLiquiditySeries.upper = null;
      }
      if (this.apexTrendLiquiditySeries.lower) {
        this.chart.removeSeries(this.apexTrendLiquiditySeries.lower);
        this.apexTrendLiquiditySeries.lower = null;
      }

      // 清理元素
      this.cleanupLiquidityZones();
      this.cleanupLiquidityHud();
      this.cleanupLiquiditySignals();
    },

    /**
     * 更新趋势云
     */
    updateTrendCloud(config, klineData) {
      if (!klineData || !Array.isArray(klineData) || klineData.length === 0) {
        console.warn("updateTrendCloud: 无效的K线数据");
        return;
      }

      const closes = klineData.map((d) => d.close);
      if (!closes || !Array.isArray(closes) || closes.length === 0) {
        console.warn("updateTrendCloud: 无法提取收盘价数据");
        return;
      }

      // 计算基准MA
      const baseline = this.calculateMA(
        config.maType,
        closes,
        config.mainLength,
      );
      if (!baseline || !Array.isArray(baseline) || baseline.length === 0) {
        console.warn("updateTrendCloud: MA计算失败", {
          maType: config.maType,
          mainLength: config.mainLength,
        });
        return;
      }

      // 添加HMA调试信息
      if (config.maType === "HMA") {
        const smaCompare = this.calculateMA("SMA", closes, config.mainLength);
        console.log("HMA计算详情:", {
          period: config.mainLength,
          halfPeriod: Math.floor(config.mainLength / 2),
          sqrtPeriod: Math.max(1, Math.round(Math.sqrt(config.mainLength))),
          baselineLength: baseline.length,
          firstValidIndex: baseline.findIndex((v) => v !== null),
          lastHMAValues: baseline.slice(-3),
          lastSMAValues: smaCompare.slice(-3),
          hasValidValues: baseline.some((v) => v !== null && !isNaN(v)),
        });
      }

      // 计算ATR
      const atr = this.calculateATR(klineData, config.mainLength);
      if (!atr || !Array.isArray(atr) || atr.length === 0) {
        console.warn("updateTrendCloud: ATR计算失败");
        return;
      }

      // 计算上下轨
      const upper = baseline.map((base, i) =>
        base && atr[i] ? base + atr[i] * config.volatilityMultiplier : null,
      );
      const lower = baseline.map((base, i) =>
        base && atr[i] ? base - atr[i] * config.volatilityMultiplier : null,
      );

      // 计算活跃趋势（简化版本：基于最近的价格位置）
      let activeTrend = 0;
      const recentCount = 5; // 检查最近5个K线

      if (klineData.length >= recentCount) {
        let bullishSignals = 0;
        let bearishSignals = 0;
        let neutralSignals = 0;
        let details = [];

        for (
          let i = klineData.length - recentCount;
          i < klineData.length;
          i++
        ) {
          const close = closes[i];
          const base = baseline[i];
          const atrVal = atr[i];

          if (base && atrVal) {
            const upperBound = base + atrVal * config.volatilityMultiplier;
            const lowerBound = base - atrVal * config.volatilityMultiplier;

            let signal = "neutral";
            let position = (close - base) / atrVal; // 标准化位置

            // 更宽松的判断：相对于基准线的标准化位置
            if (position > 0.5) {
              // 在基准线上方0.5个ATR以上
              bullishSignals++;
              signal = "bullish";
            } else if (position < -0.5) {
              // 在基准线下方0.5个ATR以上
              bearishSignals++;
              signal = "bearish";
            } else {
              neutralSignals++;
            }

            details.push({
              index: i,
              close: close.toFixed(2),
              base: base.toFixed(2),
              atr: atrVal.toFixed(4),
              position: position.toFixed(2),
              upper: upperBound.toFixed(2),
              lower: lowerBound.toFixed(2),
              signal,
            });
          }
        }

        // 临时强制设置为中性灰色
        activeTrend = 0;

        console.log("流动性指标: 通道颜色计算详情", {
          recentCount,
          bullishSignals,
          bearishSignals,
          neutralSignals,
          activeTrend,
          channelColor:
            activeTrend === 1
              ? config.bullishColor
              : activeTrend === -1
                ? config.bearishColor
                : config.neutralColor,
          volatilityMultiplier: config.volatilityMultiplier,
          details: details.slice(-3), // 只显示最后3个
        });
      }

      // 根据活跃趋势设置通道线颜色
      const channelColor =
        activeTrend === 1
          ? config.bullishColor
          : activeTrend === -1
            ? config.bearishColor
            : config.neutralColor;

      // 更新上轨线
      if (this.apexTrendLiquiditySeries.upper) {
        const upperData = klineData
          .map((item, i) => ({
            time: item.time,
            value: upper[i],
          }))
          .filter((d) => d.value !== null);

        this.apexTrendLiquiditySeries.upper.setData(upperData);
        this.apexTrendLiquiditySeries.upper.applyOptions({
          color: channelColor,
          lineWidth: 2, // 稍微加粗
          lineStyle: activeTrend === 0 ? 1 : 0, // 中性时使用虚线
        });
      }

      // 更新下轨线
      if (this.apexTrendLiquiditySeries.lower) {
        const lowerData = klineData
          .map((item, i) => ({
            time: item.time,
            value: lower[i],
          }))
          .filter((d) => d.value !== null);

        this.apexTrendLiquiditySeries.lower.setData(lowerData);
        this.apexTrendLiquiditySeries.lower.applyOptions({
          color: channelColor,
          lineWidth: 2, // 稍微加粗
          lineStyle: activeTrend === 0 ? 1 : 0, // 中性时使用虚线
        });
      }

      // 移除填充区域（如果存在）
      if (this.apexTrendLiquiditySeries.fill) {
        this.apexTrendLiquiditySeries.fill.setData([]);
      }
    },

    /**
     * 更新流动性区域
     */
    updateLiquidityZones(config, klineData) {
      // 清理现有的流动性区域
      this.cleanupLiquidityZones();

      if (!config.showLiquidity) {
        return; // 如果不显示流动性区域，直接返回
      }

      // 检测枢轴点
      const pivots = this.detectPivots(klineData, config.pivotLookback);

      // 只显示最近的3个供应和需求区域，避免界面过于杂乱
      const maxZones = 3;

      // 创建供应区域（最近的几个）
      const recentSupply = pivots.supply.slice(-maxZones);
      recentSupply.forEach((pivot) => {
        this.createSupplyZone(pivot, config, klineData);
      });

      // 创建需求区域（最近的几个）
      const recentDemand = pivots.demand.slice(-maxZones);
      recentDemand.forEach((pivot) => {
        this.createDemandZone(pivot, config, klineData);
      });

      console.log(
        `流动性指标: 显示 ${recentSupply.length} 个供应区域, ${recentDemand.length} 个需求区域`,
      );
    },

    /**
     * 检测枢轴点
     */
    detectPivots(klineData, lookback) {
      const supply = [];
      const demand = [];

      for (let i = lookback; i < klineData.length - lookback; i++) {
        const current = klineData[i];

        // 检测供应区域（阻力）
        let isSupply = true;
        for (let j = i - lookback; j <= i + lookback; j++) {
          if (j !== i && klineData[j].high >= current.high) {
            isSupply = false;
            break;
          }
        }
        if (isSupply) {
          supply.push({ index: i, price: current.high, data: current });
        }

        // 检测需求区域（支撑）
        let isDemand = true;
        for (let j = i - lookback; j <= i + lookback; j++) {
          if (j !== i && klineData[j].low <= current.low) {
            isDemand = false;
            break;
          }
        }
        if (isDemand) {
          demand.push({ index: i, price: current.low, data: current });
        }
      }

      return { supply, demand };
    },

    /**
     * 创建供应区域
     */
    createSupplyZone(pivot, config, klineData) {
      // 暂时禁用供应区域的价格线显示
      // 这里简化实现，实际应该创建box系列
      // 由于Lightweight Charts没有原生box支持，我们使用价格线来模拟
      const priceLine = {
        price: pivot.price,
        color: config.bearishColor,
        lineWidth: 1,
        lineStyle: 2, // 虚线
        axisLabelVisible: false,
        title: `供应 ${pivot.price.toFixed(2)}`,
      };

      // 存储供后续清理
      this.apexTrendLiquidityElements.supplyZones.push(priceLine);

      // 暂时注释掉价格线创建，不显示虚线
      // if (this.candlestickSeries) {
      //   this.candlestickSeries.createPriceLine(priceLine)
      // }
    },

    /**
     * 创建需求区域
     */
    createDemandZone(pivot, config, klineData) {
      const priceLine = {
        price: pivot.price,
        color: config.bullishColor,
        lineWidth: 1,
        lineStyle: 2, // 虚线
        axisLabelVisible: false,
        title: `需求 ${pivot.price.toFixed(2)}`,
      };

      this.apexTrendLiquidityElements.demandZones.push(priceLine);

      // 暂时注释掉价格线创建，不显示虚线
      // if (this.candlestickSeries) {
      //   this.candlestickSeries.createPriceLine(priceLine)
      // }
    },

    /**
     * 更新信号标记
     */
    updateLiquiditySignals(config, klineData) {
      // 清理现有信号
      this.cleanupLiquiditySignals();

      console.log("流动性指标: 开始生成信号，数据长度:", klineData.length);

      // 计算趋势状态
      const trendStates = this.calculateTrendStates(config, klineData);
      console.log(
        "流动性指标: 趋势状态计算完成，状态数量:",
        trendStates.length,
      );

      let buySignals = 0;
      let sellSignals = 0;

      // 生成买卖信号 (按照TradingView Pine Script逻辑)
      for (let i = 1; i < klineData.length; i++) {
        const current = trendStates[i];
        const previous = trendStates[i - 1];

        // 买入信号：当前是多头趋势，且前一根K线不是多头趋势 (trend == 1 and trend[1] != 1)
        if (current.trend === 1 && previous.trend !== 1) {
          console.log(
            `流动性指标: 检测到买入信号机会 at index ${i}, trend: ${previous.trend} -> ${current.trend}`,
          );
          console.log(
            `流动性指标: 检查过滤条件 - useVolumeFilter: ${config.useVolumeFilter}, useRsiFilter: ${config.useRsiFilter}`,
          );

          const filterResult = this.checkSignalFilters(
            config,
            klineData,
            i,
            "buy",
          );
          console.log(`流动性指标: 过滤结果: ${filterResult}`);

          if (filterResult) {
            const marker = {
              time:
                typeof klineData[i].time === "string"
                  ? new Date(klineData[i].time).getTime() / 1000
                  : klineData[i].time,
              position: "belowBar",
              color: config.bullishColor,
              shape: "arrowUp",
              text: "BUY",
              size: 2,
            };
            this.apexTrendLiquidityElements.signals.push(marker);
            buySignals++;
            console.log(`流动性指标: 买入信号生成 at ${klineData[i].time}`);
          } else {
            console.log(`流动性指标: 买入信号被过滤 at index ${i}`);
          }
        }

        // 卖出信号：当前是空头趋势，且前一根K线不是空头趋势 (trend == -1 and trend[1] != -1)
        if (current.trend === -1 && previous.trend !== -1) {
          console.log(
            `流动性指标: 检测到卖出信号机会 at index ${i}, trend: ${previous.trend} -> ${current.trend}`,
          );
          console.log(
            `流动性指标: 检查过滤条件 - useVolumeFilter: ${config.useVolumeFilter}, useRsiFilter: ${config.useRsiFilter}`,
          );

          const filterResult = this.checkSignalFilters(
            config,
            klineData,
            i,
            "sell",
          );
          console.log(`流动性指标: 过滤结果: ${filterResult}`);

          if (filterResult) {
            const marker = {
              time:
                typeof klineData[i].time === "string"
                  ? new Date(klineData[i].time).getTime() / 1000
                  : klineData[i].time,
              position: "aboveBar",
              color: config.bearishColor,
              shape: "arrowDown",
              text: "SELL",
              size: 2,
            };
            this.apexTrendLiquidityElements.signals.push(marker);
            sellSignals++;
            console.log(`流动性指标: 卖出信号生成 at ${klineData[i].time}`);
          } else {
            console.log(`流动性指标: 卖出信号被过滤 at index ${i}`);
          }
        }
      }

      console.log(
        `流动性指标: 信号生成完成 - 买入: ${buySignals}, 卖出: ${sellSignals}, 总信号: ${this.apexTrendLiquidityElements.signals.length}`,
      );
    },

    /**
     * 计算趋势状态（用于线条变色）
     */

    /**
     * 计算趋势状态
     */
    /**
     * 对齐K线时间戳到正确的间隔位置
     * @param {number} newTime - 新K线时间戳
     * @param {number} lastTime - 上一根K线时间戳
     * @returns {number} 对齐后的时间戳
     */
    alignKLineTimestamp(newTime, lastTime) {
      // 获取当前K线间隔（秒）
      const intervalSeconds = this.getCurrentIntervalSeconds();

      if (!intervalSeconds) {
        console.warn("无法获取K线间隔，使用原始时间戳");
        return newTime;
      }

      // 计算从上一根K线开始的下一个正确时间戳
      const expectedNextTime = lastTime + intervalSeconds;

      // 如果新时间戳在期望时间的±30秒范围内，认为是同一周期的更新
      const tolerance = 30; // 30秒容差
      if (Math.abs(newTime - expectedNextTime) <= tolerance) {
        return expectedNextTime;
      }

      // 如果差异较大，可能是新周期开始，对齐到最近的间隔
      const timeDiff = newTime - lastTime;
      const intervalsPassed = Math.round(timeDiff / intervalSeconds);
      const alignedTime = lastTime + intervalsPassed * intervalSeconds;

      console.log(
        `时间戳对齐: 原始=${newTime}, 上一根=${lastTime}, 间隔=${intervalSeconds}s, 对齐后=${alignedTime}`,
      );

      return alignedTime;
    },

    /**
     * 获取当前K线间隔（秒）
     * @returns {number} 间隔秒数
     */
    getCurrentIntervalSeconds() {
      // 从props或其他地方获取当前K线间隔
      // 这里需要根据实际的数据源来确定间隔

      // 可以通过检查现有K线数据的时间间隔来推断
      if (this.candlestickSeries) {
        const data = this.candlestickSeries.data();
        if (data && data.length >= 2) {
          // 计算最后两根K线的时间差
          const timeDiff =
            data[data.length - 1].time - data[data.length - 2].time;
          return timeDiff;
        }
      }

      // 默认返回5分钟（300秒）
      return 300;
    },

    calculateTrendStates(config, klineData) {
      console.log("流动性指标: 开始计算趋势状态，数据长度:", klineData.length);
      console.log(
        "流动性指标: 配置参数 - maType:",
        config.maType,
        "mainLength:",
        config.mainLength,
        "volatilityMultiplier:",
        config.volatilityMultiplier,
      );

      const closes = klineData.map((d) => d.close);
      console.log(
        "流动性指标: 收盘价数据长度:",
        closes.length,
        "最后几个收盘价:",
        closes.slice(-5),
      );

      const baseline = this.calculateMA(
        config.maType,
        closes,
        config.mainLength,
      );
      console.log(
        "流动性指标: 基准线计算完成，长度:",
        baseline.length,
        "最后几个基准值:",
        baseline.slice(-5),
      );

      const atr = this.calculateATR(klineData, config.mainLength);
      console.log(
        "流动性指标: ATR计算完成，长度:",
        atr.length,
        "最后几个ATR值:",
        atr.slice(-5),
      );

      const states = [];
      let bullishCount = 0;
      let bearishCount = 0;
      let neutralCount = 0;

      // 按照TradingView Pine Script逻辑：保持趋势状态直到被突破
      let currentTrend = 0; // 初始趋势状态

      for (let i = 0; i < klineData.length; i++) {
        const upper = baseline[i] + (atr[i] || 0) * config.volatilityMultiplier;
        const lower = baseline[i] - (atr[i] || 0) * config.volatilityMultiplier;
        const close = closes[i];

        // 按照Pine Script逻辑：trend := trend (保持之前的趋势)
        let trend = currentTrend;

        if (close > upper) {
          trend = 1; // 突破上轨，建立多头趋势
          if (currentTrend !== 1) bullishCount++;
        } else if (close < lower) {
          trend = -1; // 跌破下轨，建立空头趋势
          if (currentTrend !== -1) bearishCount++;
        } else {
          // 价格在中性区域，保持当前趋势
          neutralCount++;
        }

        currentTrend = trend; // 更新当前趋势状态

        states.push({ trend, upper, lower, baseline: baseline[i] });

        // 只记录最后几个的详细信息
        if (i >= klineData.length - 3) {
          console.log(
            `流动性指标: K线${i} - 收盘价:${close}, 基准:${baseline[i]?.toFixed(2)}, 上轨:${upper?.toFixed(2)}, 下轨:${lower?.toFixed(2)}, 趋势:${trend}`,
          );
        }
      }

      console.log(
        `流动性指标: 趋势状态统计 - 多头: ${bullishCount}, 空头: ${bearishCount}, 中性: ${neutralCount}`,
      );
      console.log("流动性指标: 趋势状态变化检查...");

      // 检查趋势变化
      let trendChanges = 0;
      for (let i = 1; i < states.length; i++) {
        if (states[i].trend !== states[i - 1].trend) {
          trendChanges++;
          console.log(
            `流动性指标: 趋势变化 at ${i}: ${states[i - 1].trend} -> ${states[i].trend}`,
          );
        }
      }
      console.log(`流动性指标: 总趋势变化次数: ${trendChanges}`);

      return states;
    },

    /**
     * 检查信号过滤条件
     */
    checkSignalFilters(config, klineData, index, signalType) {
      const current = klineData[index];

      // 成交量过滤
      if (config.useVolumeFilter) {
        const lookback = Math.min(20, index);
        const volumes = klineData
          .slice(Math.max(0, index - lookback), index)
          .map((d) => d.volume || 0);
        const avgVolume =
          volumes.reduce((sum, vol) => sum + vol, 0) / volumes.length;

        console.log(
          `流动性指标: 成交量过滤 - 当前成交量:${current.volume}, 平均成交量:${avgVolume.toFixed(2)}, 条件:${current.volume > avgVolume}`,
        );

        if (current.volume <= avgVolume) {
          console.log("流动性指标: 成交量过滤失败");
          return false;
        }
      }

      // RSI过滤
      if (config.useRsiFilter) {
        const rsiValues = this.calculateRSI(klineData.slice(0, index + 1), 14);
        const rsi = rsiValues[rsiValues.length - 1]; // 取最新RSI值

        console.log(
          `流动性指标: RSI过滤 - 信号:${signalType}, RSI值:${rsi?.toFixed(2)}, 买入条件:${rsi < 70}, 卖出条件:${rsi > 30}`,
        );

        if (signalType === "buy" && rsi >= 70) {
          console.log("流动性指标: RSI买入过滤失败");
          return false; // 买入需要RSI < 70
        }
        if (signalType === "sell" && rsi <= 30) {
          console.log("流动性指标: RSI卖出过滤失败");
          return false; // 卖出需要RSI > 30
        }
      }

      console.log("流动性指标: 所有过滤条件通过");
      return true;
    },

    /**
     * 更新HUD仪表板
     */
    updateLiquidityHud(config, klineData) {
      // 清理现有HUD
      this.cleanupLiquidityHud();

      if (!this.chart) return;

      // 这里简化实现，实际应该创建table系列来显示HUD
      // 由于复杂性，这里暂时跳过完整实现
    },

    /**
     * 更新K线颜色
     */
    updateCandleColors(config, klineData) {
      if (!this.candlestickSeries) return;

      const trendStates = this.calculateTrendStates(config, klineData);

      // 为每根K线设置颜色
      klineData.forEach((candle, i) => {
        let color;
        if (trendStates[i].trend === 1) {
          color = config.bullishColor;
        } else if (trendStates[i].trend === -1) {
          color = config.bearishColor;
        } else {
          color = config.neutralColor;
        }

        // 这里需要更新K线的颜色，实际实现可能需要自定义渲染
      });
    },

    /**
     * 清理流动性区域
     */
    cleanupLiquidityZones() {
      // 清理供应区域
      this.apexTrendLiquidityElements.supplyZones.forEach((zone) => {
        if (this.candlestickSeries) {
          // 移除价格线
        }
      });
      this.apexTrendLiquidityElements.supplyZones = [];

      // 清理需求区域
      this.apexTrendLiquidityElements.demandZones.forEach((zone) => {
        if (this.candlestickSeries) {
          // 移除价格线
        }
      });
      this.apexTrendLiquidityElements.demandZones = [];
    },

    /**
     * 清理HUD仪表板
     */
    cleanupLiquidityHud() {
      if (this.apexTrendLiquidityElements.hudTable) {
        // 移除HUD table
        this.apexTrendLiquidityElements.hudTable = null;
      }
    },

    /**
     * 应用信号标记到图表
     */
    applyLiquiditySignalsToChart() {
      console.log(
        "流动性指标: 应用信号到图表，信号数量:",
        this.apexTrendLiquidityElements.signals.length,
      );
      if (this.apexTrendLiquidityElements.signals.length > 0) {
        console.log(
          "流动性指标: 信号详情:",
          this.apexTrendLiquidityElements.signals.slice(0, 3),
        );
      }

      if (
        this.candlestickSeries &&
        this.apexTrendLiquidityElements.signals.length > 0
      ) {
        try {
          // 🔥 关键修复：确保 markers 按时间升序排列
          const sortedSignals = [
            ...this.apexTrendLiquidityElements.signals,
          ].sort((a, b) => a.time - b.time);
          this.candlestickSeries.setMarkers(sortedSignals);
          console.log("流动性指标: 信号成功应用到图表");
        } catch (error) {
          console.error("流动性指标: 应用信号失败:", error);
        }
      } else {
        console.log(
          "流动性指标: 跳过应用信号 - candlestickSeries:",
          !!this.candlestickSeries,
          "信号数量:",
          this.apexTrendLiquidityElements.signals.length,
        );
      }
    },

    /**
     * 清理信号标记
     */
    cleanupLiquiditySignals() {
      // 清除图表上的标记
      if (this.candlestickSeries) {
        this.candlestickSeries.setMarkers([]);
      }
      this.apexTrendLiquidityElements.signals = [];
    },

    /**
     * 计算移动平均线
     */
    calculateMA(type, data, period) {
      // 数据验证
      if (!data || !Array.isArray(data) || data.length === 0) {
        console.warn("calculateMA: 无效的数据参数", {
          type,
          dataLength: data ? data.length : "null",
          period,
        });
        return [];
      }

      if (typeof period !== "number" || period <= 0) {
        console.warn("calculateMA: 无效的周期参数", { period });
        return [];
      }

      switch (type) {
        case "SMA":
          return this.calculateSMA(data, period);
        case "EMA":
          return this.calculateEMA(data, period);
        case "HMA":
          return this.calculateHMA(data, period);
        case "RMA":
          return this.calculateRMA(data, period);
        default:
          return this.calculateEMA(data, period);
      }
    },

    /**
     * 计算加权移动平均线 (WMA)
     */
    calculateWMA(data, period) {
      if (!data || !Array.isArray(data) || data.length < period) return [];

      const result = [];
      const weights = [];

      // 计算权重 (1, 2, 3, ..., period)
      let weightSum = 0;
      for (let i = 1; i <= period; i++) {
        weights.push(i);
        weightSum += i;
      }

      for (let i = 0; i < data.length; i++) {
        if (i < period - 1) {
          result.push(null);
        } else {
          let weightedSum = 0;
          for (let j = 0; j < period; j++) {
            const value = data[i - j];
            if (typeof value === "number" && !isNaN(value)) {
              weightedSum += value * weights[period - 1 - j];
            }
          }
          result.push(weightedSum / weightSum);
        }
      }

      return result;
    },

    /**
     * 计算Hull移动平均线
     */
    calculateHMA(data, period) {
      if (!data || !Array.isArray(data) || data.length < period) return [];

      const halfPeriod = Math.floor(period / 2);
      const sqrtPeriod = Math.max(1, Math.round(Math.sqrt(period))); // 确保至少为1

      if (halfPeriod < 1) return [];

      console.log(
        `HMA参数: period=${period}, halfPeriod=${halfPeriod}, sqrtPeriod=${sqrtPeriod}`,
      );

      const wma1 = this.calculateWMA(data, halfPeriod);
      const wma2 = this.calculateWMA(data, period);
      if (!wma1 || !wma2 || wma1.length !== wma2.length) return [];

      const diff = wma1.map((w1, i) => {
        const w2 = wma2[i];
        if (w1 === null || w2 === null) return null;
        // 标准HMA公式：2 * WMA(n/2) - WMA(n)
        return 2 * w1 - w2;
      });
      const wma3 = this.calculateWMA(diff, sqrtPeriod);

      return wma3;
    },

    /**
     * 计算修正移动平均线
     */
    calculateRMA(data, period) {
      if (!data || !Array.isArray(data) || data.length < period) return [];

      const result = [];
      let sum = 0;
      let validCount = 0;

      for (let i = 0; i < data.length; i++) {
        const value = data[i];
        if (typeof value === "number" && !isNaN(value)) {
          sum += value;
          validCount++;
        }

        if (i >= period - 1) {
          result.push(validCount > 0 ? sum / validCount : null);
          // 移除最旧的值
          const oldestValue = data[i - period + 1];
          if (typeof oldestValue === "number" && !isNaN(oldestValue)) {
            sum -= oldestValue;
            validCount--;
          }
        } else {
          result.push(null);
        }
      }

      return result;
    },

    /**
     * 更新Phenom指标（均线）
     */
    updatePhenomIndicator(config) {
      if (!this.chart || !this.candlestickSeries) return;

      if (config.enabled) {
        // 初始化系列（如果需要）
        if (!this.phenomSeries.ema1) {
          this.initPhenomSeries();
        }

        // 优先使用当前图表中的所有K线数据（拖动时会自动更新）
        // 如果图表数据为空，则使用上层传入的原始数据
        let allKlineData = this.candlestickSeries
          ? this.candlestickSeries.data()
          : null;
        if (
          !allKlineData ||
          !Array.isArray(allKlineData) ||
          allKlineData.length === 0
        ) {
          allKlineData = this.data;
        }
        if (
          !allKlineData ||
          !Array.isArray(allKlineData) ||
          allKlineData.length === 0
        ) {
          console.warn("流动性指标: 无可用K线数据");
          return;
        }

        const closes = allKlineData.map((d) => d.close);

        // EMA9
        if (config.emaLines.ema9.enabled) {
          const ema9 = this.calculateEMA(closes, config.emaLines.ema9.period);
          if (this.phenomSeries.ema1) {
            this.phenomSeries.ema1.setData(
              allKlineData
                .map((item, i) => ({
                  time: item.time,
                  value: ema9[i],
                }))
                .filter((d) => d.value !== null),
            );
            this.phenomSeries.ema1.applyOptions({
              lineWidth: 0,
              color: "#FF6B6B",
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }
        } else if (this.phenomSeries.ema1) {
          // 如果不启用，清空数据
          this.phenomSeries.ema1.setData([]);
        }

        // EMA21
        if (config.emaLines.ema21.enabled) {
          const ema21 = this.calculateEMA(closes, config.emaLines.ema21.period);
          if (this.phenomSeries.ema2) {
            this.phenomSeries.ema2.setData(
              allKlineData
                .map((item, i) => ({
                  time: item.time,
                  value: ema21[i],
                }))
                .filter((d) => d.value !== null),
            );
            this.phenomSeries.ema2.applyOptions({
              lineWidth: 0,
              color: "#1677ff",
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }
        } else if (this.phenomSeries.ema2) {
          this.phenomSeries.ema2.setData([]);
        }

        // EMA55
        if (config.emaLines.ema55.enabled) {
          const ema55 = this.calculateEMA(closes, config.emaLines.ema55.period);
          if (this.phenomSeries.ema3) {
            this.phenomSeries.ema3.setData(
              allKlineData
                .map((item, i) => ({
                  time: item.time,
                  value: ema55[i],
                }))
                .filter((d) => d.value !== null),
            );
            this.phenomSeries.ema3.applyOptions({
              lineWidth: 0,
              color: "#ff0000",
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }
        } else if (this.phenomSeries.ema3) {
          this.phenomSeries.ema3.setData([]);
        }

        // EMA144
        if (config.emaLines.ema144.enabled) {
          const ema144 = this.calculateEMA(
            closes,
            config.emaLines.ema144.period,
          );
          if (this.phenomSeries.ema4) {
            this.phenomSeries.ema4.setData(
              allKlineData
                .map((item, i) => ({
                  time: item.time,
                  value: ema144[i],
                }))
                .filter((d) => d.value !== null),
            );
            this.phenomSeries.ema4.applyOptions({
              lineWidth: 0,
              color: "#9b59b6",
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }
        } else if (this.phenomSeries.ema4) {
          this.phenomSeries.ema4.setData([]);
        }

        // 计算止损线
        if (config.showStop) {
          const atrValues = this.calculateATR(allKlineData, config.atrPeriod);
          const stopLong = [];
          const stopShort = [];

          for (let i = 0; i < allKlineData.length; i++) {
            if (i < config.lastBar || !atrValues[i]) {
              stopLong.push(null);
              stopShort.push(null);
              continue;
            }

            // 找到最近lastBar根K线的最低价和最高价
            let minLow = allKlineData[i].low;
            let maxHigh = allKlineData[i].high;
            for (let j = Math.max(0, i - config.lastBar + 1); j < i; j++) {
              minLow = Math.min(minLow, allKlineData[j].low);
              maxHigh = Math.max(maxHigh, allKlineData[j].high);
            }

            stopLong.push(minLow - atrValues[i] * config.multiplier);
            stopShort.push(maxHigh + atrValues[i] * config.multiplier);
          }

          if (this.phenomSeries.stopLong) {
            this.phenomSeries.stopLong.setData(
              allKlineData
                .map((item, i) => ({
                  time: item.time,
                  value: stopLong[i],
                }))
                .filter((d) => d.value !== null),
            );
          }
          if (this.phenomSeries.stopShort) {
            this.phenomSeries.stopShort.setData(
              allKlineData
                .map((item, i) => ({
                  time: item.time,
                  value: stopShort[i],
                }))
                .filter((d) => d.value !== null),
            );
          }
        }
      } else {
        this.clearPhenomIndicator();
      }
    },

    /**
     * 清除Phenom指标
     */
    clearPhenomIndicator() {
      if (this.phenomSeries.ema1) {
        this.phenomSeries.ema1.setData([]);
      }
      if (this.phenomSeries.ema2) {
        this.phenomSeries.ema2.setData([]);
      }
      if (this.phenomSeries.ema3) {
        this.phenomSeries.ema3.setData([]);
      }
      if (this.phenomSeries.ema4) {
        this.phenomSeries.ema4.setData([]);
      }
      if (this.phenomSeries.stopLong) {
        this.phenomSeries.stopLong.setData([]);
      }
      if (this.phenomSeries.stopShort) {
        this.phenomSeries.stopShort.setData([]);
      }
    },

    // ==================== 对数回归通道 ====================

    /**
     * 计算对数回归
     */
    calculateLogRegression(data, length) {
      if (!data || data.length < length) return [];

      const result = [];
      for (let i = 0; i < length - 1; i++) {
        result.push(null);
      }

      for (let i = length - 1; i < data.length; i++) {
        const slice = data.slice(i - length + 1, i + 1);
        const n = slice.length;

        let sumX = 0;
        let sumY = 0;
        let sumXY = 0;
        let sumX2 = 0;

        for (let j = 0; j < n; j++) {
          const x = j;
          const y = Math.log(slice[j].close);
          sumX += x;
          sumY += y;
          sumXY += x * y;
          sumX2 += x * x;
        }

        const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        const intercept = (sumY - slope * sumX) / n;

        const centerValue = Math.exp(intercept + slope * (n - 1));
        result.push(centerValue);
      }

      return result;
    },

    /**
     * 初始化对数回归通道系列
     */
    initLogRegChannelSeries() {
      if (!this.chart) return;

      this.logRegChannelSeries.base = this.chart.addLineSeries({
        color: "#888888",
        lineWidth: 0, // 隐藏线条
        lineStyle: 2,
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.logRegChannelSeries.up = this.chart.addLineSeries({
        color: "#21dfac",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.logRegChannelSeries.up1 = this.chart.addLineSeries({
        color: "#21dfac",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.logRegChannelSeries.lw = this.chart.addLineSeries({
        color: "#df216d",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.logRegChannelSeries.lw1 = this.chart.addLineSeries({
        color: "#df216d",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      if (this.logRegChannel.fillBand) {
        this.logRegChannelSeries.fillUpper = this.chart.addAreaSeries({
          lineColor: "#21dfac",
          topColor: this.hexToRgba("#21dfac", 0.1),
          bottomColor: this.hexToRgba("#21dfac", 0.1),
          title: "上填充",
        });

        this.logRegChannelSeries.fillLower = this.chart.addAreaSeries({
          lineColor: "#df216d",
          topColor: this.hexToRgba("#df216d", 0.1),
          bottomColor: this.hexToRgba("#df216d", 0.1),
          title: "下填充",
        });
      }

      this.logRegChannelSeries.logReg = this.chart.addLineSeries({
        color: "#888888",
        lineWidth: 0, // 隐藏线条
        lineStyle: 2,
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });
    },

    /**
     * 更新对数回归通道指标
     */
    updateLogRegChannelIndicator(config) {
      if (!this.chart || !this.candlestickSeries) return;

      if (config.enabled) {
        if (!this.logRegChannelSeries.base) {
          this.initLogRegChannelSeries();
        }

        const allKlineData = this.candlestickSeries.data();
        if (!allKlineData || allKlineData.length === 0) return;

        // 计算对数回归
        const logRegValues = this.calculateLogRegression(
          allKlineData,
          config.length,
        );

        // 计算通道
        const closes = allKlineData.map((d) => d.close);
        const channelData = [];

        for (let i = 0; i < allKlineData.length; i++) {
          if (i < config.channelLen - 1 || !logRegValues[i]) {
            channelData.push({
              time: allKlineData[i].time,
              upper: null,
              lower: null,
              mid: null,
            });
            continue;
          }

          const slice = closes.slice(i - config.channelLen + 1, i + 1);
          const mean = slice.reduce((a, b) => a + b, 0) / slice.length;
          let variance = 0;
          for (let j = 0; j < slice.length; j++) {
            variance += Math.pow(slice[j] - mean, 2);
          }
          const stdDev = Math.sqrt(variance / slice.length);

          const mid = logRegValues[i];
          const upper = mid + stdDev * config.channelWidth;
          const lower = mid - stdDev * config.channelWidth;

          channelData.push({ time: allKlineData[i].time, upper, lower, mid });
        }

        // 更新系列
        if (config.midDisp && this.logRegChannelSeries.base) {
          this.logRegChannelSeries.base.setData(
            channelData
              .map((d) => ({
                time: d.time,
                value: d.mid,
              }))
              .filter((d) => d.value !== null),
          );
          this.logRegChannelSeries.base.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        if (this.logRegChannelSeries.up) {
          this.logRegChannelSeries.up.setData(
            channelData
              .map((d) => ({
                time: d.time,
                value: d.upper,
              }))
              .filter((d) => d.value !== null),
          );
          this.logRegChannelSeries.up.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        if (this.logRegChannelSeries.up1) {
          this.logRegChannelSeries.up1.setData(
            channelData
              .map((d) => ({
                time: d.time,
                value: d.upper,
              }))
              .filter((d) => d.value !== null),
          );
          this.logRegChannelSeries.up1.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        if (this.logRegChannelSeries.lw) {
          this.logRegChannelSeries.lw.setData(
            channelData
              .map((d) => ({
                time: d.time,
                value: d.lower,
              }))
              .filter((d) => d.value !== null),
          );
          this.logRegChannelSeries.lw.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        if (this.logRegChannelSeries.lw1) {
          this.logRegChannelSeries.lw1.setData(
            channelData
              .map((d) => ({
                time: d.time,
                value: d.lower,
              }))
              .filter((d) => d.value !== null),
          );
          this.logRegChannelSeries.lw1.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        if (config.fillBand) {
          if (this.logRegChannelSeries.fillUpper) {
            this.logRegChannelSeries.fillUpper.setData(
              channelData
                .map((d) => ({
                  time: d.time,
                  value: d.upper,
                }))
                .filter((d) => d.value !== null),
            );
          }

          if (this.logRegChannelSeries.fillLower) {
            this.logRegChannelSeries.fillLower.setData(
              channelData
                .map((d) => ({
                  time: d.time,
                  value: d.lower,
                }))
                .filter((d) => d.value !== null),
            );
          }
        }

        if (this.logRegChannelSeries.logReg) {
          this.logRegChannelSeries.logReg.setData(
            allKlineData
              .map((item, i) => ({
                time: item.time,
                value: logRegValues[i],
              }))
              .filter((d) => d.value !== null),
          );
          this.logRegChannelSeries.logReg.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }
      } else {
        this.clearLogRegChannelIndicator();
      }
    },

    /**
     * 清除对数回归通道指标
     */
    clearLogRegChannelIndicator() {
      if (this.logRegChannelSeries.base) {
        this.logRegChannelSeries.base.setData([]);
      }
      if (this.logRegChannelSeries.up) {
        this.logRegChannelSeries.up.setData([]);
      }
      if (this.logRegChannelSeries.up1) {
        this.logRegChannelSeries.up1.setData([]);
      }
      if (this.logRegChannelSeries.lw) {
        this.logRegChannelSeries.lw.setData([]);
      }
      if (this.logRegChannelSeries.lw1) {
        this.logRegChannelSeries.lw1.setData([]);
      }
      if (this.logRegChannelSeries.fillUpper) {
        this.logRegChannelSeries.fillUpper.setData([]);
      }
      if (this.logRegChannelSeries.fillLower) {
        this.logRegChannelSeries.fillLower.setData([]);
      }
      if (this.logRegChannelSeries.logReg) {
        this.logRegChannelSeries.logReg.setData([]);
      }
    },

    // ==================== 趋势强度指标 ====================

    /**
     * 初始化趋势强度系列
     */
    initTrendStrengthSeries() {
      if (!this.chart) return;

      this.trendStrengthSeries.basis = this.chart.addLineSeries({
        color: "#888888",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.trendStrengthSeries.upper = this.chart.addLineSeries({
        color: "#00ffbb",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.trendStrengthSeries.lower = this.chart.addLineSeries({
        color: "#ff1100",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      // TP上下轨线条已移除，只保留X信号标记
    },

    /**
     * 更新趋势强度指标
     */
    updateTrendStrengthIndicator(config) {
      if (!this.chart || !this.candlestickSeries) return;

      if (config.enabled) {
        if (!this.trendStrengthSeries.basis) {
          this.initTrendStrengthSeries();
        }

        const allKlineData = this.candlestickSeries.data();
        if (!allKlineData || allKlineData.length === 0) return;

        // 计算SMA和标准差（按原始脚本逻辑）
        const closes = allKlineData.map((d) => d.close);
        const smaValues = this.calculateSMA(closes, config.period);
        const stdDevValues = this.calculateStdDev(
          allKlineData,
          smaValues,
          config.period,
        );

        // 计算趋势值（按原始脚本逻辑：trend = 1 当 close > basis AND close > upper；trend = -1 当 close < basis AND close < lower）
        const trendValues = [];
        let lastTrend = 0;
        for (let i = 0; i < allKlineData.length; i++) {
          if (smaValues[i] === null || stdDevValues[i] === null) {
            trendValues.push(null);
            continue;
          }

          const basis = smaValues[i];
          const upper = basis + stdDevValues[i]; // 1倍标准差
          const lower = basis - stdDevValues[i]; // 1倍标准差
          const close = allKlineData[i].close;

          // 按原始脚本逻辑：trend = 1 当 close > basis AND close > upper
          if (close > basis && close > upper) {
            lastTrend = 1;
          }
          // trend = -1 当 close < basis AND close < lower
          else if (close < basis && close < lower) {
            lastTrend = -1;
          }
          // 否则保持之前的trend值

          trendValues.push(lastTrend);
        }

        // 更新系列
        const basisData = allKlineData
          .map((item, i) => ({
            time: item.time,
            value: smaValues[i],
          }))
          .filter((d) => d.value !== null);

        if (this.trendStrengthSeries.basis) {
          this.trendStrengthSeries.basis.setData(basisData);
          this.trendStrengthSeries.basis.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        // 计算上下轨（按原始脚本逻辑）
        const upperData = [];
        const lowerData = [];
        const upper1Data = [];
        const lower1Data = [];

        for (let i = 0; i < allKlineData.length; i++) {
          if (smaValues[i] === null || stdDevValues[i] === null) {
            continue;
          }

          const basis = smaValues[i];
          const upper = basis + stdDevValues[i]; // 1倍标准差（用于趋势判断和K线颜色）
          const lower = basis - stdDevValues[i]; // 1倍标准差
          const upper1 = basis + stdDevValues[i] * config.multiplier; // mult倍标准差（用于TP信号）
          const lower1 = basis - stdDevValues[i] * config.multiplier; // mult倍标准差（用于TP信号）

          upperData.push({ time: allKlineData[i].time, value: upper });
          lowerData.push({ time: allKlineData[i].time, value: lower });
          upper1Data.push({ time: allKlineData[i].time, value: upper1 });
          lower1Data.push({ time: allKlineData[i].time, value: lower1 });
        }

        if (this.trendStrengthSeries.upper) {
          this.trendStrengthSeries.upper.setData(upperData);
          this.trendStrengthSeries.upper.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }
        if (this.trendStrengthSeries.lower) {
          this.trendStrengthSeries.lower.setData(lowerData);
          this.trendStrengthSeries.lower.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }
        // TP上下轨线条已移除，但保留upper1和lower1的计算用于信号检测

        // 更新K线颜色和信号（传递upper和lower数据，确保数组索引对齐）
        const upperValues = [];
        const lowerValues = [];
        const upper1Values = [];
        const lower1Values = [];

        for (let i = 0; i < allKlineData.length; i++) {
          if (smaValues[i] === null || stdDevValues[i] === null) {
            upperValues.push(null);
            lowerValues.push(null);
            upper1Values.push(null);
            lower1Values.push(null);
          } else {
            const basis = smaValues[i];
            upperValues.push(basis + stdDevValues[i]);
            lowerValues.push(basis - stdDevValues[i]);
            upper1Values.push(basis + stdDevValues[i] * config.multiplier);
            lower1Values.push(basis - stdDevValues[i] * config.multiplier);
          }
        }

        this.updateTrendStrengthCandleColors(
          config,
          allKlineData,
          upperValues,
          lowerValues,
          config.upColor,
          config.downColor,
        );
        this.updateTrendStrengthSignals(
          trendValues,
          allKlineData,
          upper1Values,
          lower1Values,
          config.upColor,
          config.downColor,
        );
      } else {
        this.clearTrendStrengthIndicator();
      }
    },

    /**
     * 更新趋势强度K线颜色（按原始脚本逻辑：barcolor(src > upper ? upColor : src < lower ? downColor : chart.fg_color)）
     */
    updateTrendStrengthCandleColors(
      config,
      allKlineData,
      upperValues,
      lowerValues,
      upColor,
      downColor,
    ) {
      if (!config.candleColor || !this.candlestickSeries) return;

      // 按原始脚本：barcolor(src > upper ? upColor : src < lower ? downColor : chart.fg_color)
      // 在 Lightweight Charts 中，需要通过 setData 更新K线数据并设置 color 属性
      const updatedData = allKlineData.map((candle, i) => {
        const candleData = {
          time: candle.time,
          open: candle.open,
          high: candle.high,
          low: candle.low,
          close: candle.close,
        };

        // 如果 upper 和 lower 值有效，根据原始脚本逻辑设置颜色
        if (upperValues[i] !== null && lowerValues[i] !== null) {
          const src = candle.close;
          const upper = upperValues[i];
          const lower = lowerValues[i];

          // 按原始脚本：src > upper ? upColor : src < lower ? downColor : chart.fg_color
          if (src > upper) {
            // 使用 upColor（亮绿色 #00ffbb）
            candleData.color = upColor || "#00ffbb";
          } else if (src < lower) {
            // 使用 downColor（亮红色 #ff1100）
            candleData.color = downColor || "#ff1100";
          }
          // 否则使用默认颜色（不设置 color 属性）
        }

        return candleData;
      });

      // 更新K线数据（这会应用新的颜色）
      this.candlestickSeries.setData(updatedData);
    },

    /**
     * 更新趋势强度信号（按原始脚本逻辑）
     */
    updateTrendStrengthSignals(
      trendValues,
      allKlineData,
      upper1Values,
      lower1Values,
      upColor,
      downColor,
    ) {
      if (!this.candlestickSeries) return;

      // 清除之前的标记
      this.clearTrendStrengthSignals();

      if (!allKlineData || allKlineData.length === 0) return;

      // 🔥 关键修复：验证数据是否按时间升序排列
      let isSorted = true;
      for (let i = 1; i < allKlineData.length; i++) {
        if (allKlineData[i].time < allKlineData[i - 1].time) {
          isSorted = false;
          console.error(
            `❌ [updateTrendStrengthSignals] 数据未按时间升序排列，索引 ${i}: time=${allKlineData[i].time}, prev time=${allKlineData[i - 1].time}`,
          );
          break;
        }
      }

      // 如果数据未排序，进行排序
      if (!isSorted) {
        console.warn(
          "⚠️ [updateTrendStrengthSignals] 检测到数据未排序，正在修复...",
        );
        allKlineData = [...allKlineData].sort((a, b) => a.time - b.time);
        console.log("✅ [updateTrendStrengthSignals] 数据已重新排序");
      }

      const markers = [];

      // 1. 检测趋势变化信号（按原始脚本：crossover(trend, 0) 和 crossunder(trend, 0)）
      for (let i = 1; i < trendValues.length; i++) {
        if (trendValues[i] === null || trendValues[i - 1] === null) continue;

        const prevTrend = trendValues[i - 1];
        const currTrend = trendValues[i];

        // crossover(trend, 0): 从非正数（<=0）变为正数（>0），即从-1或0变为1
        if (prevTrend <= 0 && currTrend > 0) {
          markers.push({
            time: allKlineData[i].time,
            position: "belowBar",
            color: upColor,
            shape: "circle",
            text: "▲",
            size: 1,
          });
        }
        // crossunder(trend, 0): 从非负数（>=0）变为负数（<0），即从1或0变为-1
        else if (prevTrend >= 0 && currTrend < 0) {
          markers.push({
            time: allKlineData[i].time,
            position: "aboveBar",
            color: downColor,
            shape: "circle",
            text: "▼",
            size: 1,
          });
        }
      }

      // 2. 检测TP信号（按原始脚本：crossover(src, lower1) 和 crossunder(src, upper1)）
      for (let i = 1; i < allKlineData.length; i++) {
        if (
          upper1Values[i] === null ||
          lower1Values[i] === null ||
          upper1Values[i - 1] === null ||
          lower1Values[i - 1] === null
        )
          continue;

        const prevClose = allKlineData[i - 1].close;
        const currClose = allKlineData[i].close;
        const prevLower1 = lower1Values[i - 1];
        const currLower1 = lower1Values[i];
        const prevUpper1 = upper1Values[i - 1];
        const currUpper1 = upper1Values[i];

        // crossover(src, lower1): 价格向上突破lower1（做空TP）
        if (prevClose <= prevLower1 && currClose > currLower1) {
          markers.push({
            time: allKlineData[i].time,
            position: "belowBar",
            color: upColor,
            shape: "circle",
            text: "X",
            size: 0.5,
          });
        }
        // crossunder(src, upper1): 价格向下突破upper1（做多TP）
        else if (prevClose >= prevUpper1 && currClose < currUpper1) {
          markers.push({
            time: allKlineData[i].time,
            position: "aboveBar",
            color: downColor,
            shape: "circle",
            text: "X",
            size: 0.5,
          });
        }
      }

      // 添加标记
      if (markers.length > 0) {
        // 🔥 关键修复：确保 markers 按时间升序排列
        markers.sort((a, b) => a.time - b.time);

        const currentMarkers = this.candlestickSeries.markers() || [];
        // 合并所有 markers 并排序
        const allMarkers = [...currentMarkers, ...markers].sort(
          (a, b) => a.time - b.time,
        );

        this.candlestickSeries.setMarkers(allMarkers);
        this.trendStrengthElements.markers = markers;
      }
    },

    /**
     * 清除趋势强度信号
     */
    clearTrendStrengthSignals() {
      if (!this.candlestickSeries) return;

      const currentMarkers = this.candlestickSeries.markers() || [];
      const filteredMarkers = currentMarkers.filter((m) => {
        return !this.trendStrengthElements.markers.some(
          (tm) => tm.time === m.time,
        );
      });
      this.candlestickSeries.setMarkers(filteredMarkers);
      this.trendStrengthElements.markers = [];
    },

    /**
     * 清除趋势强度指标
     */
    clearTrendStrengthIndicator() {
      if (this.trendStrengthSeries.basis) {
        this.trendStrengthSeries.basis.setData([]);
      }
      if (this.trendStrengthSeries.upper) {
        this.trendStrengthSeries.upper.setData([]);
      }
      if (this.trendStrengthSeries.lower) {
        this.trendStrengthSeries.lower.setData([]);
      }
      // TP上下轨线条已移除
      this.clearTrendStrengthSignals();
    },

    // ==================== 反转确认指标 ====================

    /**
     * 初始化反转确认系列
     */
    initReversalConfirmationSeries() {
      if (!this.chart) return;

      this.reversalConfirmationSeries.ema3 = this.chart.addLineSeries({
        color: "#FF6B6B",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.reversalConfirmationSeries.ema5 = this.chart.addLineSeries({
        color: "#4ECDC4",
        lineWidth: 0, // 隐藏线条
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });
    },

    /**
     * 更新反转确认指标
     */
    updateReversalConfirmationIndicator(config) {
      if (!this.chart || !this.candlestickSeries) return;

      if (config.enabled) {
        if (!this.reversalConfirmationSeries.ema3) {
          this.initReversalConfirmationSeries();
        }

        const allKlineData = this.candlestickSeries.data();
        if (!allKlineData || allKlineData.length === 0) return;

        // 计算EMA
        const closes = allKlineData.map((d) => d.close);
        const ema3Values = this.calculateEMA(closes, config.ema3Length);
        const ema5Values = this.calculateEMA(closes, config.ema5Length);

        // 更新EMA线
        if (this.reversalConfirmationSeries.ema3) {
          this.reversalConfirmationSeries.ema3.setData(
            allKlineData.map((item, i) => ({
              time: item.time,
              value: ema3Values[i],
            })),
          );
          this.reversalConfirmationSeries.ema3.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        if (this.reversalConfirmationSeries.ema5) {
          this.reversalConfirmationSeries.ema5.setData(
            allKlineData.map((item, i) => ({
              time: item.time,
              value: ema5Values[i],
            })),
          );
          this.reversalConfirmationSeries.ema5.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        // 计算ATR
        const atrValues = this.calculateATR(allKlineData, 14);

        // 更新信号
        this.updateReversalConfirmationSignals(
          config,
          ema3Values,
          ema5Values,
          atrValues,
        );
      } else {
        this.clearReversalConfirmationIndicator();
      }
    },

    /**
     * 更新反转确认信号
     * 按照原始Pine Script逻辑实现
     */
    updateReversalConfirmationSignals(
      config,
      ema3Values,
      ema5Values,
      atrValues,
    ) {
      if (!this.candlestickSeries) return;

      // 清除之前的标记和线条
      this.clearReversalConfirmationSignals();

      let allKlineData = this.candlestickSeries.data();
      if (!allKlineData || allKlineData.length === 0) return;

      // 🔥 关键修复：验证数据是否按时间升序排列
      let isSorted = true;
      for (let i = 1; i < allKlineData.length; i++) {
        if (allKlineData[i].time < allKlineData[i - 1].time) {
          isSorted = false;
          console.error(
            `❌ [updateReversalConfirmationSignals] 数据未按时间升序排列，索引 ${i}: time=${allKlineData[i].time}, prev time=${allKlineData[i - 1].time}`,
          );
          break;
        }
      }

      // 如果数据未排序，进行排序
      if (!isSorted) {
        console.warn(
          "⚠️ [updateReversalConfirmationSignals] 检测到数据未排序，正在修复...",
        );
        allKlineData = [...allKlineData].sort((a, b) => a.time - b.time);
        console.log("✅ [updateReversalConfirmationSignals] 数据已重新排序");
      }

      const markers = [];
      // 存储反转蜡烛信息：{close: 收盘价, type: 'bullish'|'bearish'}
      const reversalCandles = new Map();

      // 从trendLookback开始遍历，因为需要足够的历史数据
      for (let i = config.trendLookback; i < allKlineData.length; i++) {
        if (!ema3Values[i] || !ema5Values[i] || !atrValues[i]) continue;

        const currentCandle = allKlineData[i];
        const isGreenCandle = currentCandle.close > currentCandle.open;
        const isRedCandle = currentCandle.close < currentCandle.open;

        // 统计过去trendLookback根K线的红绿蜡烛数量
        let greenCount = 0;
        let redCount = 0;

        for (let j = 1; j <= config.trendLookback; j++) {
          if (i - j < 0) break;
          const candle = allKlineData[i - j];
          if (candle.close > candle.open) {
            greenCount++;
          } else if (candle.close < candle.open) {
            redCount++;
          }
        }

        // 判断是否有前期趋势
        const priorUptrend =
          greenCount >= config.trendLookback * config.trendStrength;
        const priorDowntrend =
          redCount >= config.trendLookback * config.trendStrength;

        // 计算过去trendLookback根K线的移动幅度
        let highestHigh = currentCandle.high;
        let lowestLow = currentCandle.low;

        for (let j = 1; j <= config.trendLookback; j++) {
          if (i - j < 0) break;
          const candle = allKlineData[i - j];
          if (candle.high > highestHigh) highestHigh = candle.high;
          if (candle.low < lowestLow) lowestLow = candle.low;
        }

        const priorMove = highestHigh - lowestLow;
        const atr = atrValues[i];

        // 判断移动是否足够显著
        const significantUpMove =
          priorUptrend && priorMove > atr * config.minMoveATR;
        const significantDownMove =
          priorDowntrend && priorMove > atr * config.minMoveATR;

        // EMA条件
        const emaBullish = ema3Values[i] > ema5Values[i];
        const emaBearish = ema3Values[i] < ema5Values[i];

        // 检测看涨反转蜡烛 (R)
        // 条件：前期有显著下跌趋势，且当前是绿色蜡烛
        const bullishReversalCandle = significantDownMove && isGreenCandle;

        // 检测看跌反转蜡烛 (R)
        // 条件：前期有显著上涨趋势，且当前是红色蜡烛
        const bearishReversalCandle = significantUpMove && isRedCandle;

        // 绘制反转蜡烛标记
        if (config.showReversalCandle) {
          if (bullishReversalCandle && config.showBullish) {
            markers.push({
              time: currentCandle.time,
              position: "belowBar",
              color: "rgba(0, 200, 83, 0.6)", // #00c853 with 40% transparency (60% opacity)
              shape: "circle",
              text: "R",
              size: 1,
            });
            // 存储反转蜡烛信息
            reversalCandles.set(currentCandle.time, {
              close: currentCandle.close,
              type: "bullish",
            });

            // 绘制反转线
            this.drawReversalLine(
              currentCandle.time,
              currentCandle.close,
              true,
            );
          }

          if (bearishReversalCandle && config.showBearish) {
            markers.push({
              time: currentCandle.time,
              position: "aboveBar",
              color: "rgba(255, 23, 68, 0.6)", // #ff1744 with 40% transparency (60% opacity)
              shape: "circle",
              text: "R",
              size: 1,
            });
            // 存储反转蜡烛信息
            reversalCandles.set(currentCandle.time, {
              close: currentCandle.close,
              type: "bearish",
            });

            // 绘制反转线
            this.drawReversalLine(
              currentCandle.time,
              currentCandle.close,
              false,
            );
          }
        }

        // 检测确认蜡烛 (C)
        // 看涨确认：前一根是看涨反转蜡烛，且当前收盘价 > 反转蜡烛收盘价，且EMA3 > EMA5
        // 看跌确认：前一根是看跌反转蜡烛，且当前收盘价 < 反转蜡烛收盘价，且EMA3 < EMA5
        if (i > 0) {
          const prevCandle = allKlineData[i - 1];
          const prevReversalInfo = reversalCandles.get(prevCandle.time);

          if (prevReversalInfo) {
            // 看涨确认：前一根是看涨反转蜡烛
            if (prevReversalInfo.type === "bullish") {
              const bullishConfirmation =
                currentCandle.close > prevReversalInfo.close && emaBullish;

              if (bullishConfirmation && config.showBullish) {
                markers.push({
                  time: currentCandle.time,
                  position: "belowBar",
                  color: "#00c853",
                  shape: "triangleUp",
                  text: "C",
                  size: 2,
                });
              }
            }

            // 看跌确认：前一根是看跌反转蜡烛
            if (prevReversalInfo.type === "bearish") {
              const bearishConfirmation =
                currentCandle.close < prevReversalInfo.close && emaBearish;

              if (bearishConfirmation && config.showBearish) {
                markers.push({
                  time: currentCandle.time,
                  position: "aboveBar",
                  color: "#ff1744",
                  shape: "triangleDown",
                  text: "C",
                  size: 2,
                });
              }
            }
          }
        }
      }

      // 添加标记
      if (markers.length > 0) {
        // 🔥 关键修复：确保 markers 按时间升序排列
        markers.sort((a, b) => a.time - b.time);

        const currentMarkers = this.candlestickSeries.markers() || [];
        // 合并所有 markers 并排序
        const allMarkers = [...currentMarkers, ...markers].sort(
          (a, b) => a.time - b.time,
        );

        this.candlestickSeries.setMarkers(allMarkers);
        this.reversalConfirmationElements.markers = markers;
      }
    },

    /**
     * 绘制反转线
     */
    drawReversalLine(time, price, isBullish) {
      if (!this.candlestickSeries) return;

      // 清除之前的同类型线条
      if (isBullish && this.reversalConfirmationSeries.bullishLine) {
        this.candlestickSeries.removePriceLine(
          this.reversalConfirmationSeries.bullishLine,
        );
      }
      if (!isBullish && this.reversalConfirmationSeries.bearishLine) {
        this.candlestickSeries.removePriceLine(
          this.reversalConfirmationSeries.bearishLine,
        );
      }

      // 创建水平线（虚线）
      const line = this.candlestickSeries.createPriceLine({
        price: price,
        color: isBullish ? "#00c853" : "#ff1744",
        lineWidth: 1,
        lineStyle: 1, // dashed
        axisLabelVisible: false,
      });

      if (isBullish) {
        this.reversalConfirmationSeries.bullishLine = line;
      } else {
        this.reversalConfirmationSeries.bearishLine = line;
      }
    },

    /**
     * 清除反转确认信号
     */
    clearReversalConfirmationSignals() {
      if (!this.candlestickSeries) return;

      const currentMarkers = this.candlestickSeries.markers() || [];
      const filteredMarkers = currentMarkers.filter((m) => {
        return !(m.text === "R" || m.text === "C");
      });
      this.candlestickSeries.setMarkers(filteredMarkers);
      this.reversalConfirmationElements.markers = [];

      // 清除反转线
      if (this.reversalConfirmationSeries.bullishLine) {
        this.candlestickSeries.removePriceLine(
          this.reversalConfirmationSeries.bullishLine,
        );
        this.reversalConfirmationSeries.bullishLine = null;
      }
      if (this.reversalConfirmationSeries.bearishLine) {
        this.candlestickSeries.removePriceLine(
          this.reversalConfirmationSeries.bearishLine,
        );
        this.reversalConfirmationSeries.bearishLine = null;
      }
    },

    /**
     * 清除反转确认指标
     */
    clearReversalConfirmationIndicator() {
      if (this.reversalConfirmationSeries.ema3) {
        this.reversalConfirmationSeries.ema3.setData([]);
      }
      if (this.reversalConfirmationSeries.ema5) {
        this.reversalConfirmationSeries.ema5.setData([]);
      }
      this.clearReversalConfirmationSignals();
    },

    // ==================== 趋势强度表 (TSM) ====================

    /**
     * 初始化TSM系列
     */
    initTsmSeries() {
      if (!this.chart) return;

      this.tsmSeries.trendStrength = this.chart.addHistogramSeries({
        color: "#4CAF50",
        priceScaleId: "tsm",
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.tsmSeries.trendStrengthMA = this.chart.addLineSeries({
        color: "#FF9800",
        lineWidth: 0, // 隐藏线条
        priceScaleId: "tsm",
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      // 配置TSM价格刻度
      this.chart.priceScale("tsm").applyOptions({
        autoScale: true,
        position: "right",
        // scaleMargins 由 updateSubChartLayout() 统一管理
      });

      // 统一更新副图布局
      this.updateSubChartLayout();
    },

    /**
     * 更新TSM指标
     */
    updateTsmIndicator(config) {
      if (!this.chart || !this.candlestickSeries) return;

      // 统一更新副图布局
      this.updateSubChartLayout();

      if (config.enabled) {
        if (!this.tsmSeries.trendStrength) {
          this.initTsmSeries();
        }

        const allKlineData = this.candlestickSeries.data();
        if (!allKlineData || allKlineData.length === 0) return;

        // 计算EMA
        const closes = allKlineData.map((d) => d.close);
        const fastEMA = this.calculateEMA(closes, config.fastLength);
        const slowEMA = this.calculateEMA(closes, config.slowLength);

        // 计算趋势强度
        const trendStrengthValues = [];
        for (let i = 0; i < allKlineData.length; i++) {
          if (!fastEMA[i] || !slowEMA[i]) {
            trendStrengthValues.push(null);
            continue;
          }

          const diff = fastEMA[i] - slowEMA[i];
          const avg = (fastEMA[i] + slowEMA[i]) / 2;
          trendStrengthValues.push(avg !== 0 ? (diff / avg) * 100 : 0);
        }

        // 计算趋势强度MA
        const trendStrengthMA = this.calculateEMA(
          trendStrengthValues,
          config.slowLength,
        );

        // 更新系列
        if (this.tsmSeries.trendStrength) {
          this.tsmSeries.trendStrength.setData(
            allKlineData
              .map((item, i) => ({
                time: item.time,
                value: trendStrengthValues[i],
                color: trendStrengthValues[i] >= 0 ? "#4CAF50" : "#F44336",
              }))
              .filter((d) => d.value !== null),
          );
          this.tsmSeries.trendStrength.applyOptions({
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        if (this.tsmSeries.trendStrengthMA) {
          this.tsmSeries.trendStrengthMA.setData(
            allKlineData
              .map((item, i) => ({
                time: item.time,
                value: trendStrengthMA[i],
              }))
              .filter((d) => d.value !== null),
          );
          this.tsmSeries.trendStrengthMA.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        // 检测背离
        if (config.showDivergence) {
          this.detectTsmDivergence(config, trendStrengthValues);
        }
      } else {
        this.clearTsmIndicator();
      }
    },

    /**
     * 检测TSM背离
     */
    detectTsmDivergence(config, trendStrengthValues) {
      if (!this.candlestickSeries) return;

      // 清除之前的标记
      this.clearTsmDivergenceMarkers();

      const allKlineData = this.candlestickSeries.data();
      if (!allKlineData || allKlineData.length === 0) return;

      // 计算RSI
      const rsiValues = this.calculateRSI(
        allKlineData,
        config.divergenceLength,
      );

      const markers = [];

      // 检测背离
      for (let i = config.divergenceLength; i < allKlineData.length - 1; i++) {
        if (!trendStrengthValues[i] || !rsiValues[i]) continue;

        // 看涨背离：价格创新低，但TSM和RSI没有创新低
        if (i >= config.divergenceLength) {
          let priceLow = allKlineData[i].low;
          let priceLowIdx = i;
          let tsmLow = trendStrengthValues[i];
          let rsiLow = rsiValues[i];

          for (let j = i - config.divergenceLength; j < i; j++) {
            if (allKlineData[j].low < priceLow) {
              priceLow = allKlineData[j].low;
              priceLowIdx = j;
            }
            if (trendStrengthValues[j] && trendStrengthValues[j] < tsmLow) {
              tsmLow = trendStrengthValues[j];
            }
            if (rsiValues[j] && rsiValues[j] < rsiLow) {
              rsiLow = rsiValues[j];
            }
          }

          if (
            allKlineData[i].low < priceLow &&
            trendStrengthValues[i] > tsmLow &&
            rsiValues[i] > rsiLow
          ) {
            markers.push({
              time: allKlineData[i].time,
              position: "belowBar",
              color: "#089981",
              shape: "triangleUp",
              text: "看涨背离",
              size: 1,
            });
          }
        }

        // 看跌背离：价格创新高，但TSM和RSI没有创新高
        if (i >= config.divergenceLength) {
          let priceHigh = allKlineData[i].high;
          let priceHighIdx = i;
          let tsmHigh = trendStrengthValues[i];
          let rsiHigh = rsiValues[i];

          for (let j = i - config.divergenceLength; j < i; j++) {
            if (allKlineData[j].high > priceHigh) {
              priceHigh = allKlineData[j].high;
              priceHighIdx = j;
            }
            if (trendStrengthValues[j] && trendStrengthValues[j] > tsmHigh) {
              tsmHigh = trendStrengthValues[j];
            }
            if (rsiValues[j] && rsiValues[j] > rsiHigh) {
              rsiHigh = rsiValues[j];
            }
          }

          if (
            allKlineData[i].high > priceHigh &&
            trendStrengthValues[i] < tsmHigh &&
            rsiValues[i] < rsiHigh
          ) {
            markers.push({
              time: allKlineData[i].time,
              position: "aboveBar",
              color: "#ff062b",
              shape: "triangleDown",
              text: "看跌背离",
              size: 1,
            });
          }
        }
      }

      // 添加标记
      if (markers.length > 0) {
        const currentMarkers = this.candlestickSeries.markers() || [];
        this.candlestickSeries.setMarkers([...currentMarkers, ...markers]);
        this.tsmElements.markers = markers;
      }
    },

    /**
     * 清除TSM背离标记
     */
    clearTsmDivergenceMarkers() {
      if (!this.candlestickSeries) return;

      const currentMarkers = this.candlestickSeries.markers() || [];
      const filteredMarkers = currentMarkers.filter((m) => {
        return !(m.text === "看涨背离" || m.text === "看跌背离");
      });
      this.candlestickSeries.setMarkers(filteredMarkers);
      this.tsmElements.markers = [];
    },

    /**
     * 清除TSM指标
     */
    clearTsmIndicator() {
      if (this.tsmSeries.trendStrength) {
        this.tsmSeries.trendStrength.setData([]);
      }
      if (this.tsmSeries.trendStrengthMA) {
        this.tsmSeries.trendStrengthMA.setData([]);
      }
      this.clearTsmDivergenceMarkers();

      // 统一更新副图布局
      this.updateSubChartLayout();
    },

    // ==================== 反转后趋势强度指标 ====================

    /**
     * 计算反转后趋势强度（按原始 Pine Script 逻辑）
     */
    calculateTrendStrengthAfterReversal(data) {
      if (!data || data.length < 3) return [];

      // 辅助函数：判断 bar2 是否在 bar1 内部
      const insideBar = (bar1High, bar1Low, bar2High, bar2Low) => {
        return bar2High >= bar1High && bar2Low <= bar1Low;
      };

      // 辅助函数：判断 bar2 是否高于 bar1（higher high, higher low）
      const hhhl = (bar1High, bar1Low, bar2High, bar2Low) => {
        return bar2High > bar1High && bar2Low > bar1Low;
      };

      // 辅助函数：判断 bar2 是否低于 bar1（lower low, lower high）
      const lllh = (bar1High, bar1Low, bar2High, bar2Low) => {
        return bar2High < bar1High && bar2Low < bar1Low;
      };

      const values = [];
      const highCount = true; // 固定值
      let value = 0;
      let beatPriceUp = null;
      let beatPriceDown = null;

      for (let i = 0; i < data.length; i++) {
        // 初始化前几个值
        if (i < 2) {
          values.push(0);
          continue;
        }

        // 找到 i1, i2, i3（按原始脚本的 while 循环逻辑）
        let i1 = i - 2;
        let i2 = i - 1;
        let i3 = i;

        // 第一个 while 循环：跳过内部K线
        while (
          i2 > 0 &&
          insideBar(data[i2].high, data[i2].low, data[i3].high, data[i3].low)
        ) {
          i1 = i1 > 0 ? i1 - 1 : 0;
          i2 = i2 - 1;
          if (i2 < 0) break;
        }

        // 第二个 while 循环：继续跳过内部K线
        while (
          i1 > 0 &&
          insideBar(data[i1].high, data[i1].low, data[i2].high, data[i2].low)
        ) {
          i1 = i1 - 1;
          if (i1 < 0) break;
        }

        // 确保索引有效
        if (
          i1 < 0 ||
          i2 < 0 ||
          i3 < 0 ||
          i1 >= data.length ||
          i2 >= data.length ||
          i3 >= data.length
        ) {
          values.push(value);
          continue;
        }

        const bar1 = data[i1];
        const bar2 = data[i2];
        const bar3 = data[i3];

        // 检测反转（按原始脚本）
        // reversalUp := high[i1] > high[i2] and low[i1] > low[i2] and low[i3] > low[i2]
        const reversalUp =
          bar1.high > bar2.high && bar1.low > bar2.low && bar3.low > bar2.low;

        // reversalDown := low[i1] < low[i2] and high[i1] < high[i2] and high[i3] < high[i2]
        const reversalDown =
          bar1.low < bar2.low && bar1.high < bar2.high && bar3.high < bar2.high;

        // 检测吞没形态（按原始脚本）
        // upEngulfing := reversalUp and close[i3] > high[i1]
        const upEngulfing = reversalUp && bar3.close > bar1.high;

        // downEngulfing := reversalDown and close[i3] < low[i1]
        const downEngulfing = reversalDown && bar3.close < bar1.low;

        // 更新 beatPrice（按原始脚本逻辑）
        // beatPriceUp := beatPriceUp[1]
        // beatPriceDown := beatPriceDown[1]
        const prevBeatPriceUp = beatPriceUp;
        const prevBeatPriceDown = beatPriceDown;

        // if (not upEngulfing and not downEngulfing) or highCount
        if ((!upEngulfing && !downEngulfing) || highCount) {
          if (reversalUp) {
            beatPriceUp = bar1.high;
          }
          if (reversalDown) {
            beatPriceDown = bar1.low;
          }
        }

        // if not highCount
        if (!highCount) {
          if (upEngulfing) {
            beatPriceUp = null;
          }
          if (downEngulfing) {
            beatPriceDown = null;
          }
        }

        // 更新 value（按原始脚本逻辑）
        // value := value[1]
        const prevValue = value;

        // if not reversalUp and not reversalDown
        if (!reversalUp && !reversalDown) {
          // if close > beatPriceUp
          if (bar3.close > prevBeatPriceUp) {
            value = prevValue + 1;
            if (!highCount) {
              beatPriceUp = null;
            }
          }
          // if close < beatPriceDown
          if (bar3.close < prevBeatPriceDown) {
            value = prevValue - 1;
            if (!highCount) {
              beatPriceDown = null;
            }
          }
        }

        // if upEngulfing
        if (upEngulfing) {
          if (prevValue > 0) {
            value = prevValue + 1;
          } else {
            value = 1;
          }
        }

        // if downEngulfing
        if (downEngulfing) {
          if (prevValue < 0) {
            value = prevValue - 1;
          } else {
            value = -1;
          }
        }

        values.push(value);
      }

      return values;
    },

    /**
     * 初始化反转后趋势强度系列
     */
    initTrendStrengthAfterReversalSeries() {
      if (!this.chart) return;

      this.trendStrengthAfterReversalSeries.value =
        this.chart.addHistogramSeries({
          color: "#2196F3",
          priceScaleId: "trendStrengthAfterReversal",
          title: "", // 隐藏价格轴标签
          priceLineVisible: false, // 隐藏价格线
          lastValueVisible: true, // 显示最后一个值（带颜色）
        });

      // 配置价格刻度
      this.chart.priceScale("trendStrengthAfterReversal").applyOptions({
        autoScale: true,
        position: "right",
        // scaleMargins 由 updateSubChartLayout() 统一管理
      });

      // 统一更新副图布局
      this.updateSubChartLayout();
    },

    /**
     * 更新反转后趋势强度指标
     */
    updateTrendStrengthAfterReversalIndicator(config) {
      if (!this.chart || !this.candlestickSeries) return;

      // 统一更新副图布局
      this.updateSubChartLayout();

      if (config.enabled) {
        if (!this.trendStrengthAfterReversalSeries.value) {
          this.initTrendStrengthAfterReversalSeries();
        }

        const allKlineData = this.candlestickSeries.data();
        if (!allKlineData || allKlineData.length === 0) return;

        // 计算值
        const values = this.calculateTrendStrengthAfterReversal(allKlineData);

        // 更新系列
        if (this.trendStrengthAfterReversalSeries.value) {
          // 按原始脚本：col := value > 0 ? bullishLine : value < 0 ? color.red : color.gray
          this.trendStrengthAfterReversalSeries.value.setData(
            allKlineData
              .map((item, i) => {
                const val = values[i];
                let color = "#888888"; // gray
                if (val > 0) {
                  color = "#4CAF50"; // green (bullishLine)
                } else if (val < 0) {
                  color = "#F44336"; // red
                }
                return {
                  time: item.time,
                  value: val,
                  color: color,
                };
              })
              .filter((d) => d.value !== null),
          );
        }
      } else {
        this.clearTrendStrengthAfterReversalIndicator();
      }
    },

    /**
     * 清除反转后趋势强度指标
     */
    clearTrendStrengthAfterReversalIndicator() {
      if (this.trendStrengthAfterReversalSeries.value) {
        this.trendStrengthAfterReversalSeries.value.setData([]);
      }

      // 统一更新副图布局
      this.updateSubChartLayout();
    },

    // ==================== 安第斯振荡器指标 ====================

    /**
     * 计算安第斯振荡器（按原始 Pine Script 逻辑）
     */
    calculateAndeanOscillator(data, length, sigLength) {
      if (!data || data.length === 0)
        return {
          osc: [],
          signal: [],
          plusLevel: [],
          minusLevel: [],
        };

      // 按原始脚本：var float alpha = 2 / (length + 1)
      const alpha = 2 / (length + 1);

      // 初始化变量（按原始脚本：var float up1 = 0, var float up2 = 0, var float dn1 = 0, var float dn2 = 0）
      let up1 = 0;
      let up2 = 0;
      let dn1 = 0;
      let dn2 = 0;

      const osc = [];
      const signal = [];
      const plusLevel = [];
      const minusLevel = [];

      // 计算包络线和振荡器（按原始脚本逻辑）
      for (let i = 0; i < data.length; i++) {
        const C = data[i].close;
        const O = data[i].open;

        // 按原始脚本计算 up1, up2, dn1, dn2
        // up1 := nz(math.max(C, O, up1[1] - (up1[1] - C) * alpha), C)
        // up2 := nz(math.max(C * C, O * O, up2[1] - (up2[1] - C * C) * alpha), C * C)
        // dn1 := nz(math.min(C, O, dn1[1] + (C - dn1[1]) * alpha), C)
        // dn2 := nz(math.min(C * C, O * O, dn2[1] + (C * C - dn2[1]) * alpha), C * C)

        if (i === 0) {
          up1 = C;
          up2 = C * C;
          dn1 = C;
          dn2 = C * C;
        } else {
          const prevUp1 = up1;
          const prevUp2 = up2;
          const prevDn1 = dn1;
          const prevDn2 = dn2;

          // up1 = max(C, O, up1[1] - (up1[1] - C) * alpha)
          up1 = Math.max(C, O, prevUp1 - (prevUp1 - C) * alpha);
          // up2 = max(C*C, O*O, up2[1] - (up2[1] - C*C) * alpha)
          up2 = Math.max(C * C, O * O, prevUp2 - (prevUp2 - C * C) * alpha);
          // dn1 = min(C, O, dn1[1] + (C - dn1[1]) * alpha)
          dn1 = Math.min(C, O, prevDn1 + (C - prevDn1) * alpha);
          // dn2 = min(C*C, O*O, dn2[1] + (C*C - dn2[1]) * alpha)
          dn2 = Math.min(C * C, O * O, prevDn2 + (C * C - prevDn2) * alpha);
        }

        // 计算 bull 和 bear（按原始脚本）
        // bull = math.sqrt(dn2 - dn1 * dn1)
        // bear = math.sqrt(up2 - up1 * up1)
        const bull = Math.sqrt(Math.max(0, dn2 - dn1 * dn1));
        const bear = Math.sqrt(Math.max(0, up2 - up1 * up1));

        // 计算振荡器（按原始脚本：osc = bull - bear）
        osc.push(bull - bear);
      }

      // 计算信号线（按原始脚本：signal = ta.ema(osc, sig_length)）
      const signalValues = this.calculateEMA(osc, sigLength);

      // 计算标准差水平（按原始脚本：osc_stdev = ta.stdev(osc, length)）
      for (let i = 0; i < data.length; i++) {
        if (i < length) {
          signal.push(null);
          plusLevel.push(null);
          minusLevel.push(null);
          continue;
        }

        // 计算标准差
        const slice = osc.slice(i - length + 1, i + 1);
        const mean = slice.reduce((a, b) => a + b, 0) / slice.length;
        let variance = 0;
        for (let j = 0; j < slice.length; j++) {
          variance += Math.pow(slice[j] - mean, 2);
        }
        const stdDev = Math.sqrt(variance / slice.length);

        // plus_lvl = osc_stdev, minus_lvl = -osc_stdev
        plusLevel.push(stdDev);
        minusLevel.push(-stdDev);
        signal.push(signalValues[i]);
      }

      return { osc, signal, plusLevel, minusLevel };
    },

    /**
     * 初始化安第斯振荡器系列
     */
    initAndeanOscillatorSeries() {
      if (!this.chart) return;

      this.andeanOscillatorSeries.osc = this.chart.addHistogramSeries({
        color: "#2196F3",
        priceScaleId: "andeanOscillator",
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      this.andeanOscillatorSeries.signal = this.chart.addLineSeries({
        color: "#FF9800",
        lineWidth: 0, // 隐藏线条
        priceScaleId: "andeanOscillator",
        title: "", // 隐藏价格轴标签
        priceLineVisible: false, // 隐藏价格线
        lastValueVisible: true, // 显示最后一个值（带颜色）
      });

      if (this.andeanOscillator.showLevels) {
        this.andeanOscillatorSeries.plusLevel = this.chart.addLineSeries({
          color: "#4CAF50",
          lineWidth: 0, // 隐藏线条
          lineStyle: 2,
          priceScaleId: "andeanOscillator",
          title: "", // 隐藏价格轴标签
          priceLineVisible: false, // 隐藏价格线
          lastValueVisible: true, // 显示最后一个值（带颜色）
        });

        this.andeanOscillatorSeries.minusLevel = this.chart.addLineSeries({
          color: "#F44336",
          lineWidth: 0, // 隐藏线条
          lineStyle: 2,
          priceScaleId: "andeanOscillator",
          title: "", // 隐藏价格轴标签
          priceLineVisible: false, // 隐藏价格线
          lastValueVisible: true, // 显示最后一个值（带颜色）
        });

        this.andeanOscillatorSeries.zeroLine = this.chart.addLineSeries({
          color: "#888888",
          lineWidth: 0, // 隐藏线条
          lineStyle: 2,
          priceScaleId: "andeanOscillator",
          title: "", // 隐藏价格轴标签
          priceLineVisible: false, // 隐藏价格线
          lastValueVisible: true, // 显示最后一个值（带颜色）
        });
      }

      // 配置价格刻度（副图应该显示在独立区域）
      this.chart.priceScale("andeanOscillator").applyOptions({
        autoScale: true,
        position: "right",
        // scaleMargins 由 updateSubChartLayout() 统一管理
      });

      // 统一更新副图布局
      this.updateSubChartLayout();
    },

    /**
     * 更新安第斯振荡器指标
     */
    updateAndeanOscillatorIndicator(config) {
      if (!this.chart || !this.candlestickSeries) return;

      // 统一更新副图布局
      this.updateSubChartLayout();

      if (config.enabled) {
        if (!this.andeanOscillatorSeries.osc) {
          this.initAndeanOscillatorSeries();
        }

        const allKlineData = this.candlestickSeries.data();
        if (!allKlineData || allKlineData.length === 0) return;

        // 计算振荡器
        const result = this.calculateAndeanOscillator(
          allKlineData,
          config.length,
          config.sigLength,
        );

        // 更新系列（按原始脚本：plot(osc, style=histogram, color=osc >= 0 ? color_up : color_dn)）
        // strength = math.min(math.abs(osc) / (osc_stdev * 2), 1.0)
        // color_up = color.new(color.lime, 100 - strength * 100)
        // color_dn = color.new(color.red, 100 - strength * 100)
        if (this.andeanOscillatorSeries.osc) {
          const oscData = allKlineData
            .map((item, i) => {
              const osc = result.osc[i];
              if (osc === null || osc === undefined) return null;

              // 计算强度（按原始脚本）
              const oscStdev = result.plusLevel[i]; // plusLevel = osc_stdev
              let strength = 0;
              if (oscStdev !== null && oscStdev !== undefined && oscStdev > 0) {
                strength = Math.min(Math.abs(osc) / (oscStdev * 2), 1.0);
              }

              // 计算颜色透明度（按原始脚本：color.new(color.lime, 100 - strength * 100)）
              // transparency = 100 - strength * 100，然后转换为 opacity
              const transparency = 100 - strength * 100;
              const opacity = 1 - transparency / 100;

              // 根据 osc 的正负和强度设置颜色（按原始脚本：color.lime 和 color.red）
              let color = "#00ff00"; // lime (bright green)
              if (osc < 0) {
                color = "#ff0000"; // red
              }

              // 转换为 rgba 格式以支持透明度
              const rgbaColor = this.hexToRgba(color, opacity);

              return {
                time: item.time,
                value: osc,
                color: rgbaColor,
              };
            })
            .filter((d) => d !== null);

          this.andeanOscillatorSeries.osc.setData(oscData);
          this.andeanOscillatorSeries.osc.applyOptions({
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        if (this.andeanOscillatorSeries.signal) {
          this.andeanOscillatorSeries.signal.setData(
            allKlineData
              .map((item, i) => ({
                time: item.time,
                value: result.signal[i],
              }))
              .filter((d) => d.value !== null),
          );
          this.andeanOscillatorSeries.signal.applyOptions({
            lineWidth: 0,
            title: "",
            priceLineVisible: false,
            lastValueVisible: true,
          });
        }

        if (config.showLevels) {
          if (this.andeanOscillatorSeries.plusLevel) {
            this.andeanOscillatorSeries.plusLevel.setData(
              allKlineData
                .map((item, i) => ({
                  time: item.time,
                  value: result.plusLevel[i],
                }))
                .filter((d) => d.value !== null),
            );
            this.andeanOscillatorSeries.plusLevel.applyOptions({
              lineWidth: 0,
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }

          if (this.andeanOscillatorSeries.minusLevel) {
            this.andeanOscillatorSeries.minusLevel.setData(
              allKlineData
                .map((item, i) => ({
                  time: item.time,
                  value: result.minusLevel[i],
                }))
                .filter((d) => d.value !== null),
            );
            this.andeanOscillatorSeries.minusLevel.applyOptions({
              lineWidth: 0,
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }

          if (this.andeanOscillatorSeries.zeroLine) {
            this.andeanOscillatorSeries.zeroLine.setData(
              allKlineData.map((item, i) => ({
                time: item.time,
                value: 0,
              })),
            );
            this.andeanOscillatorSeries.zeroLine.applyOptions({
              lineWidth: 0,
              title: "",
              priceLineVisible: false,
              lastValueVisible: true,
            });
          }
        }

        // 检测信号
        if (config.earlySignal) {
          this.detectAndeanOscillatorSignals(result, true);
        } else {
          this.detectAndeanOscillatorSignals(result, false);
        }
      } else {
        this.clearAndeanOscillatorIndicator();
      }
    },

    /**
     * 检测安第斯振荡器信号
     */
    detectAndeanOscillatorSignals(result, earlySignal) {
      if (!this.candlestickSeries) return;

      // 清除之前的标记
      this.clearAndeanOscillatorSignals();

      const allKlineData = this.candlestickSeries.data();
      if (!allKlineData || allKlineData.length === 0) return;

      const markers = [];

      for (let i = 1; i < allKlineData.length; i++) {
        // 检查数据有效性
        if (
          result.osc[i] === null ||
          result.osc[i] === undefined ||
          result.signal[i] === null ||
          result.signal[i] === undefined ||
          result.osc[i - 1] === null ||
          result.osc[i - 1] === undefined ||
          result.signal[i - 1] === null ||
          result.signal[i - 1] === undefined
        ) {
          continue;
        }

        if (earlySignal) {
          // 早期信号：振荡器与信号线交叉（按原始脚本：ta.crossover 和 ta.crossunder）
          // early_cross_up = ta.crossover(osc, signal)  // osc[1] <= signal[1] and osc > signal
          // early_cross_dn = ta.crossunder(osc, signal) // osc[1] >= signal[1] and osc < signal
          const oscPrev = result.osc[i - 1];
          const oscCurr = result.osc[i];
          const signalPrev = result.signal[i - 1];
          const signalCurr = result.signal[i];

          // 检测向上交叉：osc 从下方穿越 signal 到上方
          const earlyCrossUp = oscPrev <= signalPrev && oscCurr > signalCurr;
          // 检测向下交叉：osc 从上方穿越 signal 到下方
          const earlyCrossDn = oscPrev >= signalPrev && oscCurr < signalCurr;

          if (earlyCrossUp) {
            // 按原始脚本：plotshape(long_signal, location=location.bottom, style=shape.triangleup, color=color.lime)
            markers.push({
              time: allKlineData[i].time,
              position: "belowBar",
              color: "#00ff00", // color.lime
              shape: "triangleUp",
              text: "多", // 看涨信号
              size: 2,
            });
          } else if (earlyCrossDn) {
            // 按原始脚本：plotshape(short_signal, location=location.top, style=shape.triangledown, color=color.red)
            markers.push({
              time: allKlineData[i].time,
              position: "aboveBar",
              color: "#ff0000", // color.red
              shape: "triangleDown",
              text: "空", // 看跌信号
              size: 2,
            });
          }
        } else {
          // 标准信号：振荡器穿越零线（按原始脚本：ta.crossover 和 ta.crossunder）
          // zero_cross_up = ta.crossover(osc, 0)  // osc[1] <= 0 and osc > 0
          // zero_cross_dn = ta.crossunder(osc, 0) // osc[1] >= 0 and osc < 0
          const oscPrev = result.osc[i - 1];
          const oscCurr = result.osc[i];

          // 检测向上穿越零线
          const zeroCrossUp = oscPrev <= 0 && oscCurr > 0;
          // 检测向下穿越零线
          const zeroCrossDn = oscPrev >= 0 && oscCurr < 0;

          if (zeroCrossUp) {
            // 按原始脚本：plotshape(long_signal, location=location.bottom, style=shape.triangleup, color=color.lime)
            markers.push({
              time: allKlineData[i].time,
              position: "belowBar",
              color: "#00ff00", // color.lime
              shape: "triangleUp",
              text: "多", // 看涨信号
              size: 2,
            });
          } else if (zeroCrossDn) {
            // 按原始脚本：plotshape(short_signal, location=location.top, style=shape.triangledown, color=color.red)
            markers.push({
              time: allKlineData[i].time,
              position: "aboveBar",
              color: "#ff0000", // color.red
              shape: "triangleDown",
              text: "空", // 看跌信号
              size: 2,
            });
          }
        }
      }

      // 添加标记
      if (markers.length > 0) {
        const currentMarkers = this.candlestickSeries.markers() || [];
        // 先清除旧的安第斯振荡器标记
        const filteredMarkers = currentMarkers.filter((m) => {
          // 排除之前保存的安第斯振荡器标记
          return !this.andeanOscillatorElements.markers.some(
            (am) => am.time === m.time,
          );
        });
        this.candlestickSeries.setMarkers([...filteredMarkers, ...markers]);
        this.andeanOscillatorElements.markers = markers;
      }
    },

    /**
     * 清除安第斯振荡器信号
     */
    clearAndeanOscillatorSignals() {
      if (!this.candlestickSeries) return;

      // 清除安第斯振荡器标记（通过形状和时间判断）
      if (
        this.andeanOscillatorElements.markers &&
        this.andeanOscillatorElements.markers.length > 0
      ) {
        const currentMarkers = this.candlestickSeries.markers() || [];
        const filteredMarkers = currentMarkers.filter((m) => {
          // 排除安第斯振荡器的标记（通过时间和形状判断）
          return !this.andeanOscillatorElements.markers.some(
            (am) =>
              am.time === m.time &&
              (m.shape === "triangleUp" || m.shape === "triangleDown"),
          );
        });
        this.candlestickSeries.setMarkers(filteredMarkers);
      }
      this.andeanOscillatorElements.markers = [];
    },

    /**
     * 清除安第斯振荡器指标
     */
    clearAndeanOscillatorIndicator() {
      if (this.andeanOscillatorSeries.osc) {
        this.andeanOscillatorSeries.osc.setData([]);
      }
      if (this.andeanOscillatorSeries.signal) {
        this.andeanOscillatorSeries.signal.setData([]);
      }
      if (this.andeanOscillatorSeries.plusLevel) {
        this.andeanOscillatorSeries.plusLevel.setData([]);
      }
      if (this.andeanOscillatorSeries.minusLevel) {
        this.andeanOscillatorSeries.minusLevel.setData([]);
      }
      if (this.andeanOscillatorSeries.zeroLine) {
        this.andeanOscillatorSeries.zeroLine.setData([]);
      }
      this.clearAndeanOscillatorSignals();

      // 统一更新副图布局
      this.updateSubChartLayout();
    },

    // ==================== 多时间框架趋势强度指标 ====================

    /**
     * 计算多时间框架趋势强度
     * 返回每根K线相对于三个更高时间框架的趋势强度值（-1到1之间）
     */
    calculateMultiTimeframeTrend(data, tf1, tf2, tf3) {
      if (!data || data.length === 0) return [];

      const result = [];

      for (let i = 0; i < data.length; i++) {
        const currentCandle = data[i];
        const strengths = [];

        // 计算相对于三个更高时间框架的趋势强度
        const timeframes = [tf1, tf2, tf3];

        for (const tf of timeframes) {
          // 找到对应的更高时间框架K线（向前查找）
          let higherTfIndex = i - tf;
          if (higherTfIndex < 0) higherTfIndex = 0;

          const higherTfCandle = data[higherTfIndex];
          if (!higherTfCandle) {
            strengths.push(0);
            continue;
          }

          // 计算当前价格相对于更高时间框架K线的位置
          // 如果当前收盘价 > 更高时间框架收盘价，趋势强度为正（看涨）
          // 强度 = (当前收盘价 - 更高时间框架收盘价) / 更高时间框架K线范围
          const higherTfRange = higherTfCandle.high - higherTfCandle.low;
          if (higherTfRange === 0) {
            strengths.push(0);
            continue;
          }

          // 计算强度：当前收盘价相对于更高时间框架K线的位置
          // 如果当前收盘价在更高时间框架K线的上半部分，强度为正
          // 如果在下半部分，强度为负
          const position =
            (currentCandle.close - higherTfCandle.low) / higherTfRange;
          // 将位置转换为强度：-1（最低）到 1（最高）
          const strength = (position - 0.5) * 2; // 范围：-1 到 1

          strengths.push(strength);
        }

        result.push({
          time: currentCandle.time,
          strengths: strengths, // [strength1, strength2, strength3]
        });
      }

      return result;
    },

    /**
     * 初始化多时间框架趋势强度系列
     */
    initMultiTimeframeTrendSeries() {
      if (!this.chart) return;

      // 创建三个线系列，每个系列显示圆点（在副图中，使用不同的值来区分位置）
      // 使用 LineSeries，设置 lineWidth: 0 和 pointMarkersVisible: true 来只显示圆点，不显示线
      if (!this.multiTimeframeTrendSeries.dot1) {
        this.multiTimeframeTrendSeries.dot1 = this.chart.addLineSeries({
          color: "#888888",
          lineWidth: 0, // 不显示线，只显示点
          lineStyle: 0, // 实线（虽然不显示，但需要设置）
          priceScaleId: "multiTimeframeTrend",
          priceLineVisible: false,
          lastValueVisible: false,
          title: "TF1",
          pointMarkersVisible: true, // 显示点标记
          pointMarkersRadius: 3, // 圆点大小
        });
      }
      if (!this.multiTimeframeTrendSeries.dot2) {
        this.multiTimeframeTrendSeries.dot2 = this.chart.addLineSeries({
          color: "#888888",
          lineWidth: 0, // 不显示线，只显示点
          lineStyle: 0, // 实线（虽然不显示，但需要设置）
          priceScaleId: "multiTimeframeTrend",
          priceLineVisible: false,
          lastValueVisible: false,
          title: "TF2",
          pointMarkersVisible: true, // 显示点标记
          pointMarkersRadius: 3, // 圆点大小
        });
      }
      if (!this.multiTimeframeTrendSeries.dot3) {
        this.multiTimeframeTrendSeries.dot3 = this.chart.addLineSeries({
          color: "#888888",
          lineWidth: 0, // 不显示线，只显示点
          lineStyle: 0, // 实线（虽然不显示，但需要设置）
          priceScaleId: "multiTimeframeTrend",
          priceLineVisible: false,
          lastValueVisible: false,
          title: "TF3",
          pointMarkersVisible: true, // 显示点标记
          pointMarkersRadius: 3, // 圆点大小
        });
      }

      // 配置价格刻度
      this.chart.priceScale("multiTimeframeTrend").applyOptions({
        autoScale: true,
        position: "right",
        // scaleMargins 由 updateSubChartLayout() 统一管理
      });

      // 统一更新副图布局
      this.updateSubChartLayout();
    },

    /**
     * 更新多时间框架趋势强度指标
     * @param {Object} config - 配置对象
     * @param {Array} [externalData] - 外部传入的数据（可选，如果传入则优先使用）
     */
    updateMultiTimeframeTrendIndicator(config, externalData) {
      if (!this.chart || !this.candlestickSeries) return;

      // 统一更新副图布局
      this.updateSubChartLayout();

      if (config.enabled) {
        if (!this.multiTimeframeTrendSeries.dot1) {
          this.initMultiTimeframeTrendSeries();
        }

        // ========= 关键修复：优先使用外部传入的数据，否则从 this.data（props）读取 =========
        // candlestickSeries.data() 只包含 OHLC，没有 multiTimeframeTrend 字段
        // _rawKlineData 可能保存的是旧数据（更新趋势数据之前的数据）
        // externalData 是 KlineNew.vue 直接传入的最新 chartData，确保拿到最新的包含 multiTimeframeTrend 的数据
        const allKlineData = externalData || this.data || [];

        console.log("🔍 [多时间框架趋势] 数据来源:", {
          使用外部数据: !!externalData,
          外部数据长度: externalData ? externalData.length : 0,
          thisData长度: this.data ? this.data.length : 0,
          最终使用数据长度: allKlineData.length,
        });

        // 详细检查传入的外部数据
        if (externalData && externalData.length > 0) {
          const externalWithTrend = externalData.filter(
            (k) => k.multiTimeframeTrend,
          );
          console.log("🔍 [多时间框架趋势] 外部数据详情:", {
            总条数: externalData.length,
            有趋势数据的条数: externalWithTrend.length,
            前3条: externalData.slice(0, 3).map((k) => ({
              time: k.time,
              hasMultiTimeframeTrend: !!k.multiTimeframeTrend,
              multiTimeframeTrend: k.multiTimeframeTrend,
            })),
            有趋势数据的前3条: externalWithTrend.slice(0, 3).map((k) => ({
              time: k.time,
              trend: k.multiTimeframeTrend,
            })),
          });
        }

        console.log("🔍 [多时间框架趋势] 开始检查数据:", {
          thisData长度: allKlineData.length,
          thisData前3条: allKlineData.slice(0, 3).map((k) => ({
            time: k.time,
            hasMultiTimeframeTrend: !!k.multiTimeframeTrend,
            multiTimeframeTrend: k.multiTimeframeTrend,
          })),
          _rawKlineData长度: this._rawKlineData ? this._rawKlineData.length : 0,
        });

        if (!allKlineData || allKlineData.length === 0) {
          console.warn("⚠️ 多时间框架趋势：this.data 为空，无法读取趋势数据");
          return;
        }

        // 统计有多少条K线有趋势数据
        const klinesWithTrend = allKlineData.filter(
          (k) => k.multiTimeframeTrend != null,
        );
        console.log("🔍 [多时间框架趋势] 趋势数据统计:", {
          总K线数: allKlineData.length,
          有趋势数据的K线数: klinesWithTrend.length,
          有趋势数据的K线示例: klinesWithTrend.slice(0, 5).map((k) => ({
            time: k.time,
            trend: k.multiTimeframeTrend,
          })),
        });

        // 按 time 建立映射，确保能匹配到趋势数据
        const chartDataMap = new Map();
        const candlestickData = this.candlestickSeries.data();
        if (candlestickData && candlestickData.length > 0) {
          candlestickData.forEach((c) => {
            chartDataMap.set(c.time, c);
          });
        }

        console.log("🔍 [多时间框架趋势] 数据检查完成:", {
          thisDataLength: allKlineData.length,
          candlestickDataLength: candlestickData ? candlestickData.length : 0,
          firstKlineTrend: allKlineData[0]?.multiTimeframeTrend,
          lastKlineTrend:
            allKlineData[allKlineData.length - 1]?.multiTimeframeTrend,
        });

        // ========= 使用后端返回的多时间框架趋势数据 =========
        // 约定：每根 K 线数据上挂载一个 multiTimeframeTrend 字段：
        // {
        //   time: xxx,
        //   multiTimeframeTrend: {
        //     // 趋势方向（必填，决定上/下/无趋势）
        //     // 支持：-1/0/1 或 'DOWN'/'NONE'/'UP' 等
        //     trend1, trend2, trend3,
        //     // 可选：强度（0~1 或 -1~1），决定颜色和上下偏移幅度
        //     strength1, strength2, strength3
        //   }
        // }
        const trendData = allKlineData.map((candle, index) => {
          const t = candle.multiTimeframeTrend || {};

          // 调试日志：打印所有有趋势数据的K线
          if (t && (t.trend1 != null || t.trend2 != null || t.trend3 != null)) {
            console.log(`🔍 [多时间框架趋势] K线[${index}] 有趋势数据:`, {
              time: candle.time,
              multiTimeframeTrend: t,
              trend1: t.trend1,
              trend2: t.trend2,
              trend3: t.trend3,
              strength1: t.strength1,
              strength2: t.strength2,
              strength3: t.strength3,
            });
          }

          // 将后端的趋势方向统一转换为 -1 / 0 / 1
          const normalizeTrendSign = (raw) => {
            if (raw === null || raw === undefined) return 0;
            // 数字或数字字符串（例如 -1 / "1" / "0"）
            if (
              typeof raw === "number" ||
              (typeof raw === "string" && !isNaN(raw))
            ) {
              const v = typeof raw === "number" ? raw : parseFloat(raw);
              if (v > 0) return 1;
              if (v < 0) return -1;
              return 0;
            }
            // 方向字符串（例如 "UP" / "DOWN" / "LONG" / "SHORT" / "BULL" / "BEAR"）
            if (typeof raw === "string") {
              const v = raw.toUpperCase();
              if (v === "UP" || v === "LONG" || v === "BULL") return 1;
              if (v === "DOWN" || v === "SHORT" || v === "BEAR") return -1;
              return 0;
            }
            return 0;
          };

          // 将后端的强度归一化到 [0, 1]，如果没有提供则默认为 1
          const normalizeStrength = (raw) => {
            if (raw === null || raw === undefined) return 1;
            if (typeof raw === "number") {
              const v = Math.abs(raw);
              if (v === 0) return 0;
              if (v > 1) return 1;
              return v;
            }
            return 1;
          };

          const trendSigns = [
            normalizeTrendSign(t.trend1),
            normalizeTrendSign(t.trend2),
            normalizeTrendSign(t.trend3),
          ];

          const strengthsRaw = [
            normalizeStrength(t.strength1),
            normalizeStrength(t.strength2),
            normalizeStrength(t.strength3),
          ];

          // 最终用于画图的强度：方向 * 绝对强度
          const strengths = trendSigns.map(
            (sign, idx) => sign * strengthsRaw[idx],
          );

          // 调试日志：打印所有有趋势数据的K线的转换结果
          if (t && (t.trend1 != null || t.trend2 != null || t.trend3 != null)) {
            console.log(`🔍 [多时间框架趋势] K线[${index}]趋势转换结果:`, {
              time: candle.time,
              原始trend: {
                trend1: t.trend1,
                trend2: t.trend2,
                trend3: t.trend3,
              },
              原始strength: {
                strength1: t.strength1,
                strength2: t.strength2,
                strength3: t.strength3,
              },
              trendSigns,
              strengthsRaw,
              strengths,
              finalColors: strengths.map((s) => {
                if (s > 0) return "绿色";
                if (s < 0) return "红色";
                return "灰色";
              }),
            });
          }

          return {
            time: candle.time,
            strengths,
          };
        });

        // 为副图准备数据：三个圆点系列
        const dot1Data = [];
        const dot2Data = [];
        const dot3Data = [];

        // 三个圆点的基础垂直位置（在副图中，使用不同的基础值来区分三个周期）
        const basePositions = [1.4, 1.6, 1.8];

        for (let i = 0; i < trendData.length; i++) {
          const trend = trendData[i];
          const strengths = trend.strengths;

          // 为每个时间框架创建一个圆点数据点
          for (let j = 0; j < 3; j++) {
            const strength = strengths[j];
            if (strength === undefined || strength === null) continue;

            // 根据强度确定颜色和亮度
            // > 0: 绿色（看涨），< 0: 红色（看跌），= 0: 灰色（无趋势）
            // 强度绝对值越大，颜色越亮
            const absStrength = Math.abs(strength);
            const isBullish = strength > 0;
            const isBearish = strength < 0;

            // 计算颜色亮度（0-255）
            // 强度为1时，亮度为255（最亮）
            // 强度为0时，亮度为160（中等偏亮）
            const brightness = Math.round(160 + absStrength * 95);

            let color;
            if (isBullish) {
              // 绿色：RGB(0, brightness, 0)
              color = `rgb(0, ${brightness}, 0)`;
            } else if (isBearish) {
              // 红色：RGB(brightness, 0, 0)
              color = `rgb(${brightness}, 0, 0)`;
            } else {
              // strength === 0：灰色，表示无趋势
              color = "rgb(150, 150, 150)";
            }

            // 计算圆点在副图中的位置
            // 同一周期（同一行）的所有圆圈都固定在同一位置（basePositions[j]）
            // 不再根据趋势方向上下偏移，只通过颜色来区分趋势方向
            const dotValue = basePositions[j];

            const dotData = {
              time: trend.time,
              value: dotValue, // 值很小，显示为小圆点
              color: color, // 设置点的颜色
            };

            // 调试日志：打印前几条的颜色计算结果
            if (i < 3 && j === 0) {
              console.log(`🔍 [多时间框架趋势] 画图数据[${i}][周期${j + 1}]:`, {
                time: trend.time,
                strength,
                absStrength,
                isBullish,
                isBearish,
                brightness,
                color,
                dotValue,
              });
            }

            if (j === 0) {
              dot1Data.push(dotData);
            } else if (j === 1) {
              dot2Data.push(dotData);
            } else {
              dot3Data.push(dotData);
            }
          }
        }

        // 更新副图系列数据（LineSeries 显示为圆点）
        // 注意：LineSeries 不支持每个数据点单独设置颜色，只能设置整个系列的颜色
        // 我们为每个数据点动态更新系列颜色，使其显示正确的颜色
        // 由于无法为每个点单独设置颜色，我们使用一个策略：
        // 为每个时间点创建三个数据点，分别属于三个系列，每个系列只显示一个圆点

        // 为每个系列准备数据，移除 color 属性（LineSeries 不支持）
        const dot1DataClean = dot1Data.map((d) => ({
          time: d.time,
          value: d.value,
        }));
        const dot2DataClean = dot2Data.map((d) => ({
          time: d.time,
          value: d.value,
        }));
        const dot3DataClean = dot3Data.map((d) => ({
          time: d.time,
          value: d.value,
        }));

        // 更新数据
        if (this.multiTimeframeTrendSeries.dot1 && dot1DataClean.length > 0) {
          this.multiTimeframeTrendSeries.dot1.setData(dot1DataClean);
        }
        if (this.multiTimeframeTrendSeries.dot2 && dot2DataClean.length > 0) {
          this.multiTimeframeTrendSeries.dot2.setData(dot2DataClean);
        }
        if (this.multiTimeframeTrendSeries.dot3 && dot3DataClean.length > 0) {
          this.multiTimeframeTrendSeries.dot3.setData(dot3DataClean);
        }

        // 调试日志：打印最终要设置的颜色
        console.log("🔍 [多时间框架趋势] 最终画图数据统计:", {
          dot1Data条数: dot1Data.length,
          dot2Data条数: dot2Data.length,
          dot3Data条数: dot3Data.length,
          dot1Data前3条颜色: dot1Data.slice(0, 3).map((d) => d.color),
          dot2Data前3条颜色: dot2Data.slice(0, 3).map((d) => d.color),
          dot3Data前3条颜色: dot3Data.slice(0, 3).map((d) => d.color),
          dot1Data最后1条: dot1Data[dot1Data.length - 1],
          dot2Data最后1条: dot2Data[dot2Data.length - 1],
          dot3Data最后1条: dot3Data[dot3Data.length - 1],
        });

        // 根据每个数据点的强度动态更新系列颜色
        // 由于无法为每个点单独设置颜色，我们找到有趋势数据的最后一条K线的颜色来设置
        // 这样可以确保即使最后一条K线没有趋势数据，也能显示正确的颜色

        // 辅助函数：找到有趋势数据的最后一条K线（颜色不是灰色）
        const findLastNonGrayDot = (dotDataArray) => {
          for (let i = dotDataArray.length - 1; i >= 0; i--) {
            const dot = dotDataArray[i];
            if (dot && dot.color && dot.color !== "rgb(150, 150, 150)") {
              return dot;
            }
          }
          return null;
        };

        if (dot1Data.length > 0) {
          // 优先使用有趋势数据的最后一条K线的颜色，如果没有则使用最后一条
          const targetDot1 =
            findLastNonGrayDot(dot1Data) || dot1Data[dot1Data.length - 1];
          console.log("🔍 [多时间框架趋势] 设置周期1颜色:", {
            使用数据索引: dot1Data.indexOf(targetDot1),
            颜色: targetDot1.color,
            时间: targetDot1.time,
          });
          if (targetDot1 && targetDot1.color) {
            this.multiTimeframeTrendSeries.dot1.applyOptions({
              color: targetDot1.color,
            });
          }
        }
        if (dot2Data.length > 0) {
          const targetDot2 =
            findLastNonGrayDot(dot2Data) || dot2Data[dot2Data.length - 1];
          console.log("🔍 [多时间框架趋势] 设置周期2颜色:", {
            使用数据索引: dot2Data.indexOf(targetDot2),
            颜色: targetDot2.color,
            时间: targetDot2.time,
          });
          if (targetDot2 && targetDot2.color) {
            this.multiTimeframeTrendSeries.dot2.applyOptions({
              color: targetDot2.color,
            });
          }
        }
        if (dot3Data.length > 0) {
          const targetDot3 =
            findLastNonGrayDot(dot3Data) || dot3Data[dot3Data.length - 1];
          console.log("🔍 [多时间框架趋势] 设置周期3颜色:", {
            使用数据索引: dot3Data.indexOf(targetDot3),
            颜色: targetDot3.color,
            时间: targetDot3.time,
          });
          if (targetDot3 && targetDot3.color) {
            this.multiTimeframeTrendSeries.dot3.applyOptions({
              color: targetDot3.color,
            });
          }
        }
      } else {
        this.clearMultiTimeframeTrendIndicator();
      }
    },

    /**
     * 清除多时间框架趋势强度指标
     */
    clearMultiTimeframeTrendIndicator() {
      if (this.multiTimeframeTrendSeries.dot1) {
        this.multiTimeframeTrendSeries.dot1.setData([]);
      }
      if (this.multiTimeframeTrendSeries.dot2) {
        this.multiTimeframeTrendSeries.dot2.setData([]);
      }
      if (this.multiTimeframeTrendSeries.dot3) {
        this.multiTimeframeTrendSeries.dot3.setData([]);
      }

      // 统一更新副图布局
      this.updateSubChartLayout();
    },

    // ==================== RSI指标 ====================

    /**
     * 初始化RSI系列
     */
    initRsiSeries(config) {
      console.log("📊 初始化RSI系列");

      // 创建RSI线
      this.rsiSeries = this.chart.addLineSeries({
        color: "#7E57C2", // 紫色（与Pine Script一致）
        lineWidth: 2,
        title: "RSI",
        priceScaleId: "rsi",
        priceLineVisible: true,
        lastValueVisible: true,
        crosshairMarkerVisible: true,
        priceFormat: {
          type: "price",
          precision: 2,
          minMove: 0.01,
        },
      });

      // 设置价格刻度
      const priceScale = this.rsiSeries.priceScale();
      priceScale.applyOptions({
        scaleMargins: {
          top: 0.1,
          bottom: 0.1,
        },
        autoScale: true,
        entireTextOnly: false,
        invertScale: false,
        alignLabels: true,
        borderVisible: true,
        borderColor: "#cccccc",
        textColor: "#666666",
      });

      // 初始化水平线
      if (config.showLevels) {
        this.initRsiLevels(config);
      }

      // 初始化移动平均线
      if (config.showMA) {
        this.initRsiMA(config);
      }

      // 初始化布林带
      if (config.showBB) {
        this.initRsiBB(config);
      }

      // 统一更新副图布局
      this.updateSubChartLayout();

      console.log("✅ RSI系列初始化完成");
    },

    /**
     * 初始化RSI水平线
     */
    initRsiLevels(config) {
      // RSI水平线：30, 50, 70
      const levels = [
        { value: 70, color: "#787B86", title: "RSI Upper Band" },
        {
          value: 50,
          color: "rgba(120, 123, 134, 0.5)",
          title: "RSI Middle Band",
        },
        { value: 30, color: "#787B86", title: "RSI Lower Band" },
      ];

      levels.forEach((level) => {
        const line = this.chart.addLineSeries({
          color: level.color,
          lineWidth: 1,
          title: level.title,
          priceScaleId: "rsi",
          priceLineVisible: false,
          lastValueVisible: false,
          crosshairMarkerVisible: false,
        });

        // 设置水平线
        const data = [];
        // 创建水平线数据（在整个时间范围内）
        if (this.data && this.data.length > 0) {
          data.push({ time: this.data[0].time, value: level.value });
          data.push({
            time: this.data[this.data.length - 1].time,
            value: level.value,
          });
        }

        line.setData(data);
        this.rsiLevelSeries.push(line);
      });
    },

    /**
     * 初始化RSI移动平均线
     */
    initRsiMA(config) {
      this.rsiMASeries = this.chart.addLineSeries({
        color: "#FFFF00", // 黄色
        lineWidth: 1,
        title: "RSI MA",
        priceScaleId: "rsi",
        priceLineVisible: false,
        lastValueVisible: true,
        crosshairMarkerVisible: true,
      });
    },

    /**
     * 初始化RSI布林带
     */
    initRsiBB(config) {
      // 上轨
      const bbUpper = this.chart.addLineSeries({
        color: "#00FF00", // 绿色
        lineWidth: 1,
        title: "BB Upper",
        priceScaleId: "rsi",
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: true,
      });

      // 下轨
      const bbLower = this.chart.addLineSeries({
        color: "#00FF00", // 绿色
        lineWidth: 1,
        title: "BB Lower",
        priceScaleId: "rsi",
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: true,
      });

      this.rsiBBSeries = [bbUpper, bbLower];
    },

    /**
     * 计算RSI指标 - 使用正确的RMA算法（Pine Script标准实现）
     */
    calculateRSI(data, period = 14) {
      if (!data || data.length < period + 1) return [];

      const closes = data.map((d) => d.close);
      const rsiValues = [];

      // 计算价格变化
      const changes = [];
      for (let i = 1; i < closes.length; i++) {
        changes.push(closes[i] - closes[i - 1]);
      }

      // 计算上涨和下跌
      const gains = [];
      const losses = [];
      for (let i = 0; i < changes.length; i++) {
        const change = changes[i];
        gains.push(change > 0 ? change : 0);
        losses.push(change < 0 ? -change : 0);
      }

      // 计算RMA（Relative Moving Average）
      const gainRMA = this.calculateRMA(gains, period);
      const lossRMA = this.calculateRMA(losses, period);

      for (let i = 0; i < closes.length; i++) {
        if (i < period) {
          rsiValues.push({
            time: data[i].time,
            value: null,
          });
          continue;
        }

        const gain = gainRMA[i - period];
        const loss = lossRMA[i - period];

        let rsi;
        if (loss === 0) {
          rsi = 100;
        } else if (gain === 0) {
          rsi = 0;
        } else {
          const rs = gain / loss;
          rsi = 100 - 100 / (1 + rs);
        }

        rsiValues.push({
          time: data[i].time,
          value: rsi,
        });
      }

      return rsiValues;
    },

    /**
     * 计算RMA (Relative Moving Average) - Pine Script的RMA实现
     */
    calculateRMA(data, period) {
      const rma = [];
      let sum = 0;

      for (let i = 0; i < data.length; i++) {
        if (i < period - 1) {
          sum += data[i];
          rma.push(sum / (i + 1)); // 初始阶段使用SMA
        } else if (i === period - 1) {
          sum += data[i];
          rma.push(sum / period); // 第一个RMA值
        } else {
          // RMA = (前一个RMA * (period-1) + 当前值) / period
          const prevRMA = rma[rma.length - 1];
          const currentRMA = (prevRMA * (period - 1) + data[i]) / period;
          rma.push(currentRMA);
        }
      }

      return rma;
    },

    /**
     * 更新RSI指标
     */
    updateRsiIndicator(config) {
      if (!this.chart || !this.candlestickSeries) return;

      // 统一更新副图布局
      this.updateSubChartLayout();

      if (config.enabled) {
        // 初始化RSI系列（如果还没有）
        if (!this.rsiSeries) {
          this.initRsiSeries(config);
        }

        // 获取图表数据
        const allKlineData = this.candlestickSeries.data();
        if (!allKlineData || allKlineData.length === 0) return;

        // 计算RSI
        const rsiData = this.calculateRSI(allKlineData, config.period);

        console.log("📊 更新RSI指标:", {
          周期: config.period,
          数据点数: rsiData.length,
          最后值: rsiData[rsiData.length - 1]?.value,
          显示水平线: config.showLevels,
          显示MA: config.showMA,
          显示BB: config.showBB,
        });

        // 更新RSI主线
        if (this.rsiSeries && rsiData.length > 0) {
          // 🔥 关键修复：过滤掉 null 值，lightweight-charts 要求数据必须是数字
          const validRsiData = rsiData.filter((item) => {
            return (
              item != null &&
              item.value != null &&
              !isNaN(item.value) &&
              isFinite(item.value)
            );
          });

          if (validRsiData.length > 0) {
            this.rsiSeries.setData(validRsiData);
          } else {
            console.warn("⚠️ RSI数据无效，所有数据点都包含null或无效值");
          }
        }

        // 更新水平线
        this.updateRsiLevels(config, allKlineData);

        // 更新移动平均线
        this.updateRsiMA(config, rsiData);

        // 更新布林带
        this.updateRsiBB(config, rsiData);
      } else {
        // 移除所有RSI相关系列
        this.removeAllRsiSeries();

        // 统一更新副图布局
        this.updateSubChartLayout();
      }
    },

    /**
     * 更新RSI水平线
     */
    updateRsiLevels(config, klineData) {
      if (!config.showLevels) {
        // 移除水平线
        this.rsiLevelSeries.forEach((line) => {
          if (line) this.chart.removeSeries(line);
        });
        this.rsiLevelSeries = [];
        return;
      }

      // 确保水平线已创建
      if (this.rsiLevelSeries.length === 0) {
        this.initRsiLevels(config);
      }

      // 更新水平线数据
      const levels = [70, 50, 30];
      levels.forEach((level, index) => {
        if (this.rsiLevelSeries[index] && klineData.length > 0) {
          const data = [
            { time: klineData[0].time, value: level },
            { time: klineData[klineData.length - 1].time, value: level },
          ];
          this.rsiLevelSeries[index].setData(data);
        }
      });
    },

    /**
     * 更新RSI移动平均线
     */
    updateRsiMA(config, rsiData) {
      if (!config.showMA) {
        // 移除MA线
        if (this.rsiMASeries) {
          this.chart.removeSeries(this.rsiMASeries);
          this.rsiMASeries = null;
        }
        return;
      }

      // 确保MA线已创建
      if (!this.rsiMASeries) {
        this.initRsiMA(config);
      }

      // 计算RSI的MA
      const rsiValues = rsiData
        .map((d) => d.value)
        .filter((v) => v !== null && !isNaN(v) && isFinite(v));
      if (rsiValues.length >= config.maLength) {
        const maData = this.calculateMovingAverage(
          rsiValues,
          config.maLength,
          config.maType,
        );

        // 对齐时间 - 🔥 关键修复：只添加有效的数字数据点，过滤掉 null 值
        const maSeriesData = [];
        let maIndex = 0;
        rsiData.forEach((rsiPoint, index) => {
          // 只添加有效的 RSI 值和对应的 MA 值
          if (
            rsiPoint.value !== null &&
            !isNaN(rsiPoint.value) &&
            isFinite(rsiPoint.value) &&
            maIndex < maData.length
          ) {
            const maValue = maData[maIndex];
            // 确保 MA 值也是有效的数字
            if (maValue !== null && !isNaN(maValue) && isFinite(maValue)) {
              maSeriesData.push({
                time: rsiPoint.time,
                value: maValue,
              });
            }
            maIndex++;
          }
          // 不再添加 null 值的数据点
        });

        // 🔥 关键修复：确保数据有效后再设置
        if (maSeriesData.length > 0) {
          this.rsiMASeries.setData(maSeriesData);
        } else {
          console.warn("⚠️ RSI MA数据无效，所有数据点都包含null或无效值");
        }
      }
    },

    /**
     * 更新RSI布林带
     */
    updateRsiBB(config, rsiData) {
      if (!config.showBB) {
        // 移除布林带
        this.rsiBBSeries.forEach((line) => {
          if (line) this.chart.removeSeries(line);
        });
        this.rsiBBSeries = [];
        return;
      }

      // 确保布林带已创建
      if (this.rsiBBSeries.length === 0) {
        this.initRsiBB(config);
      }

      // 计算RSI的布林带
      const rsiValues = rsiData.map((d) => d.value).filter((v) => v !== null);
      if (rsiValues.length >= config.maLength) {
        const bbData = this.calculateBollingerBands(
          rsiValues,
          config.maLength,
          config.bbStdDev,
        );

        // 对齐时间
        const bbUpperData = [];
        const bbLowerData = [];
        let bbIndex = 0;

        rsiData.forEach((rsiPoint, index) => {
          // 🔥 关键修复：只添加有效的数字数据点，过滤掉 null 值
          if (
            rsiPoint.value !== null &&
            !isNaN(rsiPoint.value) &&
            isFinite(rsiPoint.value) &&
            bbIndex < bbData.upper.length
          ) {
            const upperValue = bbData.upper[bbIndex];
            const lowerValue = bbData.lower[bbIndex];

            // 确保布林带值也是有效的数字
            if (
              upperValue !== null &&
              !isNaN(upperValue) &&
              isFinite(upperValue) &&
              lowerValue !== null &&
              !isNaN(lowerValue) &&
              isFinite(lowerValue)
            ) {
              bbUpperData.push({
                time: rsiPoint.time,
                value: upperValue,
              });
              bbLowerData.push({
                time: rsiPoint.time,
                value: lowerValue,
              });
            }
            bbIndex++;
          }
          // 不再添加 null 值的数据点
        });

        // 🔥 关键修复：确保数据有效后再设置
        if (bbUpperData.length > 0 && this.rsiBBSeries[0]) {
          this.rsiBBSeries[0].setData(bbUpperData);
        }
        if (bbLowerData.length > 0 && this.rsiBBSeries[1]) {
          this.rsiBBSeries[1].setData(bbLowerData);
        }
      }
    },

    /**
     * 计算移动平均线 (RSI指标使用)
     */
    calculateMovingAverage(data, period, type = "SMA") {
      // 数据验证
      if (!data || !Array.isArray(data) || data.length === 0) {
        console.warn("calculateMA (RSI): 无效的数据参数", {
          type,
          dataLength: data ? data.length : "null",
          period,
        });
        return [];
      }

      if (typeof period !== "number" || period <= 0) {
        console.warn("calculateMA (RSI): 无效的周期参数", { period });
        return [];
      }

      const result = [];

      for (let i = 0; i < data.length; i++) {
        if (i < period - 1) {
          result.push(null);
          continue;
        }

        let value;
        try {
          switch (type) {
            case "SMA":
              const sliceData = data.slice(i - period + 1, i + 1);
              if (!sliceData || sliceData.length === 0) {
                value = null;
              } else {
                const sum = sliceData.reduce((acc, val) => {
                  const num = typeof val === "number" && !isNaN(val) ? val : 0;
                  return acc + num;
                }, 0);
                value = sum / sliceData.length;
              }
              break;
            case "EMA":
              const emaData = data.slice(0, i + 1);
              if (emaData && emaData.length > 0) {
                const emaResult = this.calculateEMA(emaData, period);
                value = emaResult && emaResult.length > i ? emaResult[i] : null;
              } else {
                value = null;
              }
              break;
            case "RMA":
              const rmaData = data.slice(0, i + 1);
              if (rmaData && rmaData.length >= period) {
                const rmaResult = this.calculateRMA(rmaData, period);
                const rmaIndex = i - period + 1;
                value =
                  rmaResult && rmaResult.length > rmaIndex
                    ? rmaResult[rmaIndex]
                    : null;
              } else {
                value = null;
              }
              break;
            default:
              const defaultSliceData = data.slice(i - period + 1, i + 1);
              if (!defaultSliceData || defaultSliceData.length === 0) {
                value = null;
              } else {
                const defaultSum = defaultSliceData.reduce((acc, val) => {
                  const num = typeof val === "number" && !isNaN(val) ? val : 0;
                  return acc + num;
                }, 0);
                value = defaultSum / defaultSliceData.length;
              }
          }
        } catch (error) {
          console.warn("calculateMA (RSI): 计算错误", {
            type,
            i,
            period,
            error: error.message,
          });
          value = null;
        }

        result.push(value);
      }

      return result;
    },

    /**
     * 计算布林带
     */
    calculateBollingerBands(data, period, stdDev = 2) {
      const sma = this.calculateMovingAverage(data, period, "SMA");
      const upper = [];
      const lower = [];

      for (let i = 0; i < data.length; i++) {
        if (i < period - 1) {
          upper.push(null);
          lower.push(null);
          continue;
        }

        const slice = data.slice(i - period + 1, i + 1);
        const mean = sma[i];
        const variance =
          slice.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) / period;
        const std = Math.sqrt(variance);

        upper.push(mean + stdDev * std);
        lower.push(mean - stdDev * std);
      }

      return { upper, lower, sma };
    },

    /**
     * 移除所有RSI相关系列
     */
    removeAllRsiSeries() {
      // 移除主RSI线
      if (this.rsiSeries) {
        this.chart.removeSeries(this.rsiSeries);
        this.rsiSeries = null;
      }

      // 移除水平线
      this.rsiLevelSeries.forEach((line) => {
        if (line) this.chart.removeSeries(line);
      });
      this.rsiLevelSeries = [];

      // 移除MA线
      if (this.rsiMASeries) {
        this.chart.removeSeries(this.rsiMASeries);
        this.rsiMASeries = null;
      }

      // 移除布林带
      this.rsiBBSeries.forEach((line) => {
        if (line) this.chart.removeSeries(line);
      });
      this.rsiBBSeries = [];
    },
  },
};
</script>

<style scoped>
.lightweight-chart-container {
  border: 1px solid #e1ecf2;
  border-radius: 4px;
  overflow: hidden; /* 改为 hidden，防止内容溢出到右侧面板 */
  position: relative;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

/* 确保图表容器有足够空间显示时间轴 */
.lightweight-chart-container > div {
  overflow: visible;
}

/* 固定价格轴标签区域，防止布局变化 */
/* TradingView Lightweight Charts 会在价格轴右侧渲染标签，我们需要确保这个区域始终存在 */
.lightweight-chart-container canvas {
  /* 确保画布不会因为标签显示/隐藏而改变尺寸 */
  display: block;
}
</style>
