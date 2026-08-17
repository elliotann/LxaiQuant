/**
 * 图表交互管理 Composable
 * 处理图表的缩放、拖动、数据预加载等交互逻辑
 */
import { ref, type Ref } from "vue";
import type { IChartApi } from "lightweight-charts";
import { loadKLineData, jumpToTime as apiJumpToTime } from "@/api/kline";
import type { KLineData } from "@/components/KLineChart/KLineChart.types";

export interface DataBoundary {
  startTime: number;
  endTime: number;
  dataCount: number;
}

export interface TimeRange {
  from: number | string;
  to: number | string;
}

export interface JumpRecord {
  targetTime: number;
  timestamp: number;
  visibleRange: TimeRange;
  dataBoundary: DataBoundary;
}

export interface JumpOptions {
  loadCount?: number;
  bufferBefore?: number;
  bufferAfter?: number;
  includeSummary?: boolean;
  lockDuration?: number;
  force?: boolean;
}

export interface JumpResult {
  success: boolean;
  targetTime: number;
  loadedDataCount?: number;
  summary?: any;
  boundary?: any;
  message: string;
}

export interface JumpCache {
  targetTime: number;
  timestamp: number;
  data: any[];
  summary?: any;
  boundary?: any;
}

/**
 * 将时间转换为数字（秒级时间戳）
 */
const toTimestamp = (time: number | string): number => {
  if (typeof time === "number") {
    return time > 1e12 ? Math.floor(time / 1000) : time;
  }
  return parseInt(time);
};

export function useChartInteractions(
  chart: Ref<IChartApi | null>,
  symbol: Ref<string>,
  interval: Ref<string>,
  dataBoundary: Ref<DataBoundary>,
  onDataLoaded?: (
    data: KLineData[],
    direction: "forward" | "backward" | "jump",
  ) => void,
  onJumpComplete?: (result: JumpResult) => void,
) {
  const isDragging = ref(false);
  const isZooming = ref(false);
  const scrollDirection = ref<"left" | "right" | "none">("none");
  const lastVisibleRange = ref<TimeRange | null>(null);
  const chartMode = ref<"realtime" | "historical">("realtime");
  const isLoading = ref({ backward: false, forward: false, jump: false });
  const isJumping = ref(false); // 标记是否正在跳转或刚完成跳转
  const jumpLockUntil = ref(0); // 跳转锁定时间（时间戳）

  // 跳转历史记录
  const jumpHistory = ref<JumpRecord[]>([]);
  const maxJumpHistory = 10;

  // 跳转位置缓存
  const jumpPositionCache = ref<Map<string, JumpCache>>(new Map());

  // 防抖相关
  let scrollDebounceTimer: number | null = null;
  const lastLoadTime = { backward: 0, forward: 0 }; // 记录上次加载时间，避免频繁请求
  const LOAD_DEBOUNCE_DELAY = 500; // 防抖延迟（毫秒）
  const MIN_LOAD_INTERVAL = 1000; // 最小加载间隔（毫秒）

  // 跳转锁定配置
  const JUMP_LOCK_DURATION = 30000; // 跳转锁定持续时间（毫秒）

  /**
   * 初始化交互监听
   */
  const setupChartInteractions = () => {
    if (!chart.value) return;

    const timeScale = chart.value.timeScale();

    // 监听可见范围变化
    timeScale.subscribeVisibleTimeRangeChange((timeRange: any) => {
      // 转换 lightweight-charts 的 TimeRange 到我们的 TimeRange
      if (timeRange) {
        handleVisibleRangeChange({
          from:
            typeof timeRange.from === "number"
              ? timeRange.from
              : typeof timeRange.from === "string"
                ? parseInt(timeRange.from)
                : (timeRange.from as any),
          to:
            typeof timeRange.to === "number"
              ? timeRange.to
              : typeof timeRange.to === "string"
                ? parseInt(timeRange.to)
                : (timeRange.to as any),
        });
      } else {
        handleVisibleRangeChange(null);
      }
    });

    // 监听十字光标
    chart.value.subscribeCrosshairMove(handleCrosshairMove);

    // 监听图表点击（用于时间跳转）
    chart.value.subscribeClick(handleChartClick);
  };

  /**
   * 处理图表点击事件（用于时间跳转）
   */
  const handleChartClick = (param: any) => {
    if (!param.time || !param.point) return;

    // 可以在这里实现点击图表跳转到该时间点的功能
    // 例如：双击跳转、右键菜单跳转等
    console.log("图表点击时间点:", param.time);

    // 如果启用了点击跳转功能
    // advancedJumpToTime(toTimestamp(param.time))
  };

  /**
   * 处理可见范围变化
   */
  const handleVisibleRangeChange = (newRange: TimeRange | null) => {
    if (!newRange || !lastVisibleRange.value) {
      lastVisibleRange.value = newRange;
      return;
    }

    const oldRange = lastVisibleRange.value;
    const now = Date.now();
    const isInJumpLock = isJumping.value || now < jumpLockUntil.value;

    // 清除之前的防抖定时器
    if (scrollDebounceTimer) {
      clearTimeout(scrollDebounceTimer);
      scrollDebounceTimer = null;
    }

    // 判断是拖动还是缩放
    if (isScrolling(oldRange, newRange)) {
      // 拖动：使用防抖，避免频繁触发
      scrollDebounceTimer = window.setTimeout(() => {
        handleChartScroll(oldRange, newRange, isInJumpLock);
        scrollDebounceTimer = null;
      }, LOAD_DEBOUNCE_DELAY);
    } else if (isZoomingAction(oldRange, newRange)) {
      // 缩放：允许在跳转锁定期间也处理
      handleChartZoom(oldRange, newRange);
    }

    lastVisibleRange.value = newRange;

    // 只有在非拖动状态下才检查预加载（避免与拖动时的加载冲突）
    // 并且不在跳转锁定期间（避免与跳转时的预加载冲突）
    const checkNow = Date.now();
    const isInJumpLockCheck = isJumping.value || checkNow < jumpLockUntil.value;
    if (!isDragging.value && !isInJumpLockCheck) {
      checkPreloadNeeded(newRange);
    } else if (isInJumpLockCheck) {
      console.log(
        `[可见范围变化] 跳转锁定中，跳过预加载检查。isJumping=${isJumping.value}, jumpLockUntil=${new Date(jumpLockUntil.value).toLocaleTimeString()}`,
      );
    }
  };

  /**
   * 处理图表滚动
   */
  const handleChartScroll = (
    oldRange: TimeRange,
    newRange: TimeRange,
    isInJumpLock: boolean = false,
  ) => {
    isDragging.value = true;

    // 计算滚动方向
    const oldFrom = toTimestamp(oldRange.from);
    const oldTo = toTimestamp(oldRange.to);
    const newFrom = toTimestamp(newRange.from);
    const newTo = toTimestamp(newRange.to);
    const oldCenter = (oldFrom + oldTo) / 2;
    const newCenter = (newFrom + newTo) / 2;

    if (newCenter < oldCenter) {
      // 向左拖动（向后加载更早的数据）
      scrollDirection.value = "left";
      evaluateBackwardLoad(newRange);
    } else {
      // 向右拖动（向前加载更新的数据）
      scrollDirection.value = "right";
      // 如果不在跳转锁定期间，或者用户主动拖动，允许向前加载
      // 但在跳转锁定期间，只允许加载历史数据，不允许跳回最新数据
      evaluateForwardLoad(newRange, isInJumpLock);
    }

    // 重置拖动状态
    setTimeout(() => {
      isDragging.value = false;
    }, 100);
  };

  /**
   * 处理图表缩放
   */
  const handleChartZoom = (_oldRange: TimeRange, newRange: TimeRange) => {
    isZooming.value = true;

    // 缩放后检查是否需要加载数据
    checkPreloadNeeded(newRange);

    setTimeout(() => {
      isZooming.value = false;
    }, 100);
  };

  /**
   * 处理十字光标移动
   */
  const handleCrosshairMove = (_param: any) => {
    // 可以在这里添加十字光标移动时的处理逻辑
    // 例如：显示详细信息、高亮等
  };

  /**
   * 评估是否需要向后加载数据
   */
  const evaluateBackwardLoad = async (range: TimeRange) => {
    if (isLoading.value.backward) {
      console.log("向后加载正在进行中，跳过");
      return;
    }

    // 检查距离上次加载的时间间隔
    const now = Date.now();
    if (now - lastLoadTime.backward < MIN_LOAD_INTERVAL) {
      console.log(
        `向后加载过于频繁，距离上次加载仅 ${now - lastLoadTime.backward}ms，跳过`,
      );
      return;
    }

    const rangeFrom = toTimestamp(range.from);
    const rangeTo = toTimestamp(range.to);
    const visibleDuration = rangeTo - rangeFrom;
    const loadThreshold = visibleDuration * 0.3; // 30%可见范围作为阈值

    // 确保数据边界已初始化
    if (dataBoundary.value.startTime === 0) {
      console.log("数据边界未初始化，跳过向后加载");
      return;
    }

    const distanceToBoundary = rangeFrom - dataBoundary.value.startTime;
    console.log(
      `评估向后加载: distanceToBoundary=${distanceToBoundary}, loadThreshold=${loadThreshold}, dataBoundary.startTime=${dataBoundary.value.startTime}`,
    );

    // 使用数据边界的开始时间作为 anchorTime，而不是可见范围的开始时间
    if (distanceToBoundary < loadThreshold && distanceToBoundary > 0) {
      console.log(
        `触发向后加载数据: anchorTime=${dataBoundary.value.startTime} (${new Date(dataBoundary.value.startTime * 1000).toLocaleString("zh-CN")})`,
      );
      isLoading.value.backward = true;
      lastLoadTime.backward = now;
      try {
        await loadMoreData("backward", dataBoundary.value.startTime);
      } finally {
        isLoading.value.backward = false;
      }
    }
  };

  /**
   * 评估是否需要向前加载数据
   * @param range 可见时间范围
   * @param isInJumpLock 是否在跳转锁定期间（如果是，则只加载历史数据，不跳回最新）
   */
  const evaluateForwardLoad = async (
    range: TimeRange,
    isInJumpLock: boolean = false,
  ) => {
    if (isLoading.value.forward) {
      console.log("向前加载正在进行中，跳过");
      return;
    }

    // 检查距离上次加载的时间间隔
    const now = Date.now();
    if (now - lastLoadTime.forward < MIN_LOAD_INTERVAL) {
      console.log(
        `向前加载过于频繁，距离上次加载仅 ${now - lastLoadTime.forward}ms，跳过`,
      );
      return;
    }

    const rangeFrom = toTimestamp(range.from);
    const rangeTo = toTimestamp(range.to);
    const visibleDuration = rangeTo - rangeFrom;
    const loadThreshold = visibleDuration * 0.3;

    // 确保数据边界已初始化
    if (dataBoundary.value.endTime === 0) {
      console.log("数据边界未初始化，跳过向前加载");
      return;
    }

    const currentTime = Math.floor(Date.now() / 1000);
    const distanceToBoundary = dataBoundary.value.endTime - rangeTo;
    const isViewingRecent = currentTime - rangeTo < 300;

    // 添加详细的调试日志
    console.log(
      `[evaluateForwardLoad] 可见范围: [${rangeFrom} (${new Date(rangeFrom * 1000).toLocaleString("zh-CN")}), ${rangeTo} (${new Date(rangeTo * 1000).toLocaleString("zh-CN")})]`,
    );
    console.log(
      `[evaluateForwardLoad] 数据边界: [${dataBoundary.value.startTime} (${new Date(dataBoundary.value.startTime * 1000).toLocaleString("zh-CN")}), ${dataBoundary.value.endTime} (${new Date(dataBoundary.value.endTime * 1000).toLocaleString("zh-CN")})]`,
    );
    console.log(
      `[evaluateForwardLoad] distanceToBoundary=${distanceToBoundary}, loadThreshold=${loadThreshold}, isViewingRecent=${isViewingRecent}`,
    );

    // 如果处于历史模式，或者在跳转锁定期间，只加载历史数据，不跳回最新，也不切换到实时模式
    if (chartMode.value === "historical" || isInJumpLock) {
      console.log(
        `评估向前加载（历史模式${isInJumpLock ? "，跳转锁定中" : ""}）: distanceToBoundary=${distanceToBoundary}, loadThreshold=${loadThreshold}, isViewingRecent=${isViewingRecent}, dataBoundary.endTime=${dataBoundary.value.endTime}`,
      );

      // 在跳转锁定期间，检查数据边界是否合理
      // 如果数据边界的 endTime 明显大于当前时间（比如超过1年），说明数据边界可能没有正确更新
      if (isInJumpLock) {
        const currentTime = Math.floor(Date.now() / 1000);
        const oneYearInSeconds = 31536000;
        if (dataBoundary.value.endTime > currentTime + oneYearInSeconds) {
          console.warn(
            `[evaluateForwardLoad] ⚠️ 跳转锁定中，但数据边界异常（endTime=${dataBoundary.value.endTime} 明显大于当前时间），跳过加载。可能数据边界未正确更新。`,
          );
          return;
        }
      }

      // 在历史模式或跳转锁定期间，即使接近当前时间，也只加载历史数据，不跳回最新
      // 使用数据边界的结束时间作为 anchorTime，而不是可见范围的结束时间
      if (distanceToBoundary < loadThreshold && distanceToBoundary > 0) {
        console.log(
          `触发向前加载数据（历史模式，不跳回最新）: anchorTime=${dataBoundary.value.endTime} (${new Date(dataBoundary.value.endTime * 1000).toLocaleString("zh-CN")})`,
        );
        isLoading.value.forward = true;
        lastLoadTime.forward = now;
        try {
          await loadMoreData("forward", dataBoundary.value.endTime);
        } finally {
          isLoading.value.forward = false;
        }
      } else if (isViewingRecent && isInJumpLock) {
        // 在跳转锁定期间，即使接近当前时间，也不切换到实时模式
        console.log("跳转锁定中，接近当前时间但不切换到实时模式");
      }
      return;
    }

    // 实时模式下的逻辑（只有在非历史模式且非跳转锁定期间才执行）
    if (isViewingRecent) {
      // 如果接近当前时间，切换到实时模式
      chartMode.value = "realtime";
      console.log("切换到实时模式");
    } else {
      // 加载更多历史数据
      // 使用数据边界的结束时间作为 anchorTime，而不是可见范围的结束时间
      console.log(
        `评估向前加载: distanceToBoundary=${distanceToBoundary}, loadThreshold=${loadThreshold}, dataBoundary.endTime=${dataBoundary.value.endTime}`,
      );

      if (distanceToBoundary < loadThreshold && distanceToBoundary > 0) {
        console.log(
          `触发向前加载数据: anchorTime=${dataBoundary.value.endTime} (${new Date(dataBoundary.value.endTime * 1000).toLocaleString("zh-CN")})`,
        );
        isLoading.value.forward = true;
        lastLoadTime.forward = now;
        try {
          await loadMoreData("forward", dataBoundary.value.endTime);
        } finally {
          isLoading.value.forward = false;
        }
      }
    }
  };

  /**
   * 加载更多数据
   */
  const loadMoreData = async (
    direction: "forward" | "backward",
    anchorTime: number,
  ) => {
    try {
      // 确保 anchorTime 是秒级时间戳
      const anchorTimeSeconds =
        typeof anchorTime === "number"
          ? anchorTime > 1e12
            ? Math.floor(anchorTime / 1000)
            : anchorTime
          : anchorTime;

      console.log(
        `[loadMoreData] 开始加载数据: direction=${direction}, anchorTime=${anchorTimeSeconds} (${new Date(anchorTimeSeconds * 1000).toLocaleString("zh-CN")})`,
      );
      console.log(
        `[loadMoreData] 当前数据边界: [${dataBoundary.value.startTime}, ${dataBoundary.value.endTime}]`,
      );

      const response = await loadKLineData({
        symbol: symbol.value,
        interval: interval.value,
        direction,
        anchorTime: anchorTimeSeconds,
        limit: 200, // 增加每次加载的数量
      });

      if (
        response &&
        (response as any).code === 200 &&
        (response as any).data
      ) {
        const newData = (response as any).data.data.map((item: any) => ({
          time: item.time,
          open: parseFloat(item.open),
          high: parseFloat(item.high),
          low: parseFloat(item.low),
          close: parseFloat(item.close),
          volume: parseFloat(item.volume || 0),
        }));

        console.log(`加载到 ${newData.length} 条新数据`);

        // 更新数据边界
        if (newData.length > 0) {
          const times = newData.map((k: KLineData) => {
            const t = typeof k.time === "string" ? parseInt(k.time) : k.time;
            return t;
          });

          if (direction === "backward") {
            // 向后加载：更新开始时间（取更早的时间）
            const minTime = Math.min(...times);
            if (
              dataBoundary.value.startTime === 0 ||
              minTime < dataBoundary.value.startTime
            ) {
              dataBoundary.value.startTime = minTime;
            }
          } else {
            // 向前加载：更新结束时间（取更晚的时间）
            const maxTime = Math.max(...times);
            if (maxTime > dataBoundary.value.endTime) {
              dataBoundary.value.endTime = maxTime;
            }
          }
          dataBoundary.value.dataCount += newData.length;
        }

        // 通知数据加载完成
        if (onDataLoaded) {
          onDataLoaded(newData, direction);
        }

        return response.data.hasMore || false;
      } else {
        console.warn("加载数据响应异常:", response);
      }
    } catch (error) {
      console.error("加载数据失败:", error);
    }

    return false;
  };

  /**
   * 检查是否需要预加载
   */
  const checkPreloadNeeded = (range: TimeRange) => {
    // 如果正在加载数据，跳过预加载（避免冲突）
    if (
      isLoading.value.backward ||
      isLoading.value.forward ||
      isLoading.value.jump
    ) {
      return;
    }

    // 如果正在跳转或刚完成跳转，跳过预加载（避免与跳转时的预加载冲突）
    const now = Date.now();
    if (isJumping.value || now < jumpLockUntil.value) {
      console.log(
        "[预加载检查] 跳转锁定中，跳过自动预加载（已由 preloadAroundJump 处理）",
      );
      return;
    }

    // 确保数据边界已初始化
    if (
      dataBoundary.value.startTime === 0 ||
      dataBoundary.value.endTime === 0
    ) {
      console.log("[预加载检查] 数据边界未初始化，跳过");
      return;
    }

    const rangeFrom = toTimestamp(range.from);
    const rangeTo = toTimestamp(range.to);

    // 验证可见范围是否合理（避免使用错误的时间值）
    const currentTime = Math.floor(Date.now() / 1000);
    const oneYearAgo = currentTime - 31536000; // 1年前
    const oneYearLater = currentTime + 31536000; // 1年后

    if (
      rangeFrom < oneYearAgo ||
      rangeFrom > oneYearLater ||
      rangeTo < oneYearAgo ||
      rangeTo > oneYearLater
    ) {
      console.warn(
        `[预加载检查] 可见范围异常，跳过预加载: from=${rangeFrom}, to=${rangeTo}`,
      );
      return;
    }

    const bufferSeconds = (rangeTo - rangeFrom) * 2;

    const shouldPreloadBackward =
      rangeFrom - bufferSeconds < dataBoundary.value.startTime;
    const shouldPreloadForward =
      rangeTo + bufferSeconds > dataBoundary.value.endTime;

    if (shouldPreloadBackward || shouldPreloadForward) {
      console.log(
        `[预加载检查] 触发预加载: shouldPreloadBackward=${shouldPreloadBackward}, shouldPreloadForward=${shouldPreloadForward}`,
      );
      // 触发预加载
      preloadData(range, bufferSeconds);
    }
  };

  /**
   * 预加载数据
   */
  const preloadData = async (range: TimeRange, _bufferSeconds: number) => {
    // 如果正在跳转或刚完成跳转，跳过预加载（避免与跳转时的预加载冲突）
    const now = Date.now();
    if (isJumping.value || now < jumpLockUntil.value) {
      console.log("[preloadData] 跳转锁定中，跳过预加载");
      return;
    }

    const preloadPromises = [];

    const rangeFrom = toTimestamp(range.from);
    const rangeTo = toTimestamp(range.to);
    const bufferSeconds = (rangeTo - rangeFrom) * 2;

    // 验证时间范围是否合理
    const currentTime = Math.floor(Date.now() / 1000);
    const oneYearAgo = currentTime - 31536000;
    const oneYearLater = currentTime + 31536000;

    if (
      rangeFrom < oneYearAgo ||
      rangeFrom > oneYearLater ||
      rangeTo < oneYearAgo ||
      rangeTo > oneYearLater
    ) {
      console.warn(
        `[preloadData] 可见范围异常，跳过预加载: from=${rangeFrom}, to=${rangeTo}`,
      );
      return;
    }

    console.log(
      `[preloadData] 预加载数据: range=[${rangeFrom}, ${rangeTo}], bufferSeconds=${bufferSeconds}`,
    );

    if (rangeFrom - bufferSeconds < dataBoundary.value.startTime) {
      const anchorTime = rangeFrom - bufferSeconds;
      console.log(`[preloadData] 向后预加载: anchorTime=${anchorTime}`);
      preloadPromises.push(
        loadKLineData({
          symbol: symbol.value,
          interval: interval.value,
          direction: "backward",
          anchorTime: anchorTime,
          limit: 50,
        }),
      );
    }

    if (rangeTo + bufferSeconds > dataBoundary.value.endTime) {
      const anchorTime = rangeTo + bufferSeconds;
      console.log(`[preloadData] 向前预加载: anchorTime=${anchorTime}`);
      preloadPromises.push(
        loadKLineData({
          symbol: symbol.value,
          interval: interval.value,
          direction: "forward",
          anchorTime: anchorTime,
          limit: 50,
        }),
      );
    }

    if (preloadPromises.length > 0) {
      await Promise.allSettled(preloadPromises);
    }
  };

  /**
   * 工具函数：判断是否为滚动
   */
  const isScrolling = (oldRange: TimeRange, newRange: TimeRange): boolean => {
    const oldFrom = toTimestamp(oldRange.from);
    const oldTo = toTimestamp(oldRange.to);
    const newFrom = toTimestamp(newRange.from);
    const newTo = toTimestamp(newRange.to);
    const oldDuration = oldTo - oldFrom;
    const newDuration = newTo - newFrom;
    const durationDiff = Math.abs(oldDuration - newDuration) / oldDuration;

    return durationDiff < 0.05; // 5%以内的变化认为是滚动
  };

  /**
   * 工具函数：判断是否为缩放
   */
  const isZoomingAction = (
    oldRange: TimeRange,
    newRange: TimeRange,
  ): boolean => {
    const oldFrom = toTimestamp(oldRange.from);
    const oldTo = toTimestamp(oldRange.to);
    const newFrom = toTimestamp(newRange.from);
    const newTo = toTimestamp(newRange.to);
    const oldCenter = (oldFrom + oldTo) / 2;
    const newCenter = (newFrom + newTo) / 2;
    const oldDuration = oldTo - oldFrom;
    const newDuration = newTo - newFrom;
    const centerDiff = Math.abs(oldCenter - newCenter) / oldDuration;
    const durationDiff = Math.abs(oldDuration - newDuration) / oldDuration;

    return centerDiff < 0.1 && durationDiff > 0.1;
  };

  /**
   * 跳转到指定时间（简单版本，仅调整可视范围）
   */
  const jumpToTime = (targetTime: number) => {
    if (!chart.value) return;

    const timeScale = chart.value.timeScale();
    const visibleRange = timeScale.getVisibleRange();

    if (visibleRange) {
      const from =
        typeof visibleRange.from === "number"
          ? visibleRange.from
          : parseInt(visibleRange.from as any);
      const to =
        typeof visibleRange.to === "number"
          ? visibleRange.to
          : parseInt(visibleRange.to as any);
      const duration = to - from;
      timeScale.setVisibleRange({
        from: (targetTime - duration / 2) as any,
        to: (targetTime + duration / 2) as any,
      });
    }
  };

  /**
   * 高级时间跳转（调用后端接口获取数据）
   */
  const advancedJumpToTime = async (
    targetTime: number,
    options: JumpOptions = {},
  ): Promise<JumpResult> => {
    if (!chart.value) {
      return {
        success: false,
        message: "图表未初始化",
        targetTime,
      };
    }

    const now = Date.now();
    if (now < jumpLockUntil.value && options.force !== true) {
      return {
        success: false,
        message: "跳转锁定中，请稍后再试",
        targetTime,
      };
    }

    // 立即设置跳转状态，防止 checkPreloadNeeded 在跳转过程中被触发
    isLoading.value.jump = true;
    isJumping.value = true;
    // 立即设置跳转锁定时间，防止在跳转过程中触发其他预加载逻辑
    const lockDuration = options.lockDuration || JUMP_LOCK_DURATION;
    jumpLockUntil.value = Date.now() + lockDuration;
    console.log(
      `🔒 [时间跳转] 立即设置跳转锁定: ${lockDuration}ms，直到 ${new Date(jumpLockUntil.value).toLocaleTimeString()}`,
    );

    try {
      console.log(
        `[时间跳转] 开始时间跳转: targetTime=${targetTime} (${new Date(targetTime * 1000).toLocaleString("zh-CN")}), symbol=${symbol.value}, interval=${interval.value}`,
      );

      // 1. 调用后端跳转接口
      const response = await apiJumpToTime({
        symbol: symbol.value,
        interval: interval.value,
        time: targetTime,
        before: options.bufferBefore || 100,
        after: options.bufferAfter || 100,
        limit: options.loadCount || 200,
      });

      if ((response as any).code !== 200 || !(response as any).data) {
        throw new Error((response as any).message || "跳转失败");
      }

      const jumpData = (response as any).data;

      // 2. 转换数据格式
      const klineData = jumpData.klines.map((item: any) => ({
        time: item.time,
        open: parseFloat(item.open),
        high: parseFloat(item.high),
        low: parseFloat(item.low),
        close: parseFloat(item.close),
        volume: parseFloat(item.volume || 0),
      }));

      // 3. 保存跳转前的状态（用于回退）
      const timeScale = chart.value.timeScale();
      const currentVisibleRange = timeScale.getVisibleRange();

      if (currentVisibleRange) {
        addJumpHistory({
          targetTime: jumpData.targetTime,
          timestamp: Date.now(),
          visibleRange: {
            from:
              typeof currentVisibleRange.from === "number"
                ? currentVisibleRange.from
                : parseInt(currentVisibleRange.from as any),
            to:
              typeof currentVisibleRange.to === "number"
                ? currentVisibleRange.to
                : parseInt(currentVisibleRange.to as any),
          },
          dataBoundary: { ...dataBoundary.value },
        });
      }

      // 4. 更新数据边界
      if (klineData.length > 0) {
        const times = klineData.map((k: KLineData) => {
          const t = typeof k.time === "string" ? parseInt(k.time) : k.time;
          return t;
        });

        const minTime = Math.min(...times);
        const maxTime = Math.max(...times);

        console.log(
          `[时间跳转] 更新数据边界: [${minTime}, ${maxTime}] (${new Date(minTime * 1000).toLocaleString("zh-CN")} ~ ${new Date(maxTime * 1000).toLocaleString("zh-CN")}), 数据条数=${klineData.length}`,
        );

        dataBoundary.value.startTime = minTime;
        dataBoundary.value.endTime = maxTime;
        dataBoundary.value.dataCount = klineData.length;
      } else {
        console.warn("[时间跳转] 跳转返回的数据为空，无法更新数据边界");
      }

      // 5. 通知数据加载完成
      if (onDataLoaded) {
        onDataLoaded(klineData, "jump");
      }

      // 6. 跳转锁定已在开始时设置，这里确保锁定时间正确（通常不需要重复设置）
      // 如果之前没有设置，则设置（但通常已经在开始时设置了）
      if (jumpLockUntil.value === 0) {
        const lockDuration = options.lockDuration || JUMP_LOCK_DURATION;
        jumpLockUntil.value = Date.now() + lockDuration;
        console.log(`🔒 [时间跳转] 补充设置跳转锁定: ${lockDuration}ms`);
      }

      // 7. 切换到历史模式
      chartMode.value = "historical";

      // 8. 缓存跳转位置数据
      cacheJumpPosition(jumpData);

      // 9. 预加载周围数据（在设置可见范围之前，使用更新后的数据边界）
      // 注意：必须在数据边界更新后调用，确保使用正确的边界值
      console.log(
        `[时间跳转] 准备预加载周围数据: centerTime=${targetTime} (${new Date(targetTime * 1000).toLocaleString("zh-CN")}), 数据边界=[${dataBoundary.value.startTime}, ${dataBoundary.value.endTime}]`,
      );
      // 使用 await 确保预加载完成后再设置可见范围，避免触发 checkPreloadNeeded
      await preloadAroundJump(targetTime);

      // 10. 设置可见范围（以目标时间点为中心）
      // 注意：在预加载之后设置，避免触发 checkPreloadNeeded
      const optimalRange = calculateOptimalVisibleRange(targetTime);
      console.log(
        `设置可见范围: from=${optimalRange.from}, to=${optimalRange.to}`,
      );
      timeScale.setVisibleRange({
        from: optimalRange.from as any,
        to: optimalRange.to as any,
      });

      const result: JumpResult = {
        success: true,
        targetTime,
        loadedDataCount: klineData.length,
        summary: jumpData.summary,
        boundary: jumpData.boundary,
        message: "跳转成功",
      };

      if (onJumpComplete) {
        onJumpComplete(result);
      }

      return result;
    } catch (error: any) {
      console.error("时间跳转失败:", error);

      const result: JumpResult = {
        success: false,
        targetTime,
        message: error.message || "跳转失败",
      };

      return result;
    } finally {
      isLoading.value.jump = false;
      // 注意：isJumping 状态在锁定时间结束后会自动重置
    }
  };

  /**
   * 回退到上一次跳转位置
   */
  const jumpBack = async (): Promise<JumpResult | null> => {
    if (jumpHistory.value.length <= 1) {
      console.warn("没有可回退的跳转记录");
      return null;
    }

    // 移除当前记录
    const currentJump = jumpHistory.value.pop();
    if (!currentJump) return null;

    // 获取上一次记录
    const lastJump = jumpHistory.value[jumpHistory.value.length - 1];
    if (!lastJump) return null;

    // 执行跳转回退
    return await advancedJumpToTime(lastJump.targetTime, {
      force: true,
      lockDuration: JUMP_LOCK_DURATION,
    });
  };

  /**
   * 设置跳转锁定（防止跳转后自动跳回最新数据）
   * @param duration 锁定持续时间（毫秒），默认30秒
   */
  const setJumpLock = (duration: number = JUMP_LOCK_DURATION) => {
    isJumping.value = true;
    jumpLockUntil.value = Date.now() + duration;
    console.log(
      `🔒 设置跳转锁定: ${duration}ms，直到 ${new Date(jumpLockUntil.value).toLocaleTimeString()}`,
    );

    // 在锁定时间结束后重置
    setTimeout(() => {
      isJumping.value = false;
      console.log("🔓 跳转锁定已解除");

      // 检查当前是否还在查看历史数据
      if (chart.value) {
        const timeScale = chart.value.timeScale();
        const visibleRange = timeScale.getVisibleRange();

        if (visibleRange) {
          const rangeTo =
            typeof visibleRange.to === "number"
              ? visibleRange.to
              : parseInt(visibleRange.to as any);
          const currentTime = Math.floor(Date.now() / 1000);
          const isViewingRecent = currentTime - rangeTo < 300;

          // 如果用户正在查看近期数据，自动切换到实时模式
          if (isViewingRecent && chartMode.value === "historical") {
            chartMode.value = "realtime";
            console.log("自动切换到实时模式");
          }
        }
      }
    }, duration);
  };

  /**
   * 强制解除跳转锁定
   */
  const clearJumpLock = () => {
    isJumping.value = false;
    jumpLockUntil.value = 0;
    console.log("🔓 强制解除跳转锁定");
  };

  /**
   * 计算最佳可见范围
   */
  const calculateOptimalVisibleRange = (targetTime: number): TimeRange => {
    // 根据时间间隔计算最佳可见范围
    const intervalSeconds = getIntervalSeconds(interval.value);
    const optimalBarCount = getOptimalBarCount(interval.value);
    const visibleDuration = optimalBarCount * intervalSeconds;

    return {
      from: targetTime - visibleDuration / 2,
      to: targetTime + visibleDuration / 2,
    };
  };

  /**
   * 获取时间间隔秒数
   */
  const getIntervalSeconds = (intervalStr: string): number => {
    const intervalMap: Record<string, number> = {
      OKXMIN1: 60,
      OKXMIN3: 180,
      OKXMIN5: 300,
      OKXMIN15: 900,
      OKXMIN30: 1800,
      OKXMIN60: 3600,
      OKX4HOUR: 14400,
      OKX1D: 86400,
      "1m": 60,
      "5m": 300,
      "15m": 900,
      "30m": 1800,
      "1h": 3600,
      "4h": 14400,
      "1d": 86400,
      "1w": 604800,
    };

    return intervalMap[intervalStr] || 3600;
  };

  /**
   * 获取最佳显示的K线数量
   */
  const getOptimalBarCount = (intervalStr: string): number => {
    const optimalCounts: Record<string, number> = {
      OKXMIN1: 200,
      OKXMIN3: 150,
      OKXMIN5: 150,
      OKXMIN15: 100,
      OKXMIN30: 80,
      OKXMIN60: 60,
      OKX4HOUR: 40,
      OKX1D: 30,
      "1m": 200,
      "5m": 150,
      "15m": 100,
      "30m": 80,
      "1h": 60,
      "4h": 40,
      "1d": 30,
      "1w": 20,
    };

    return optimalCounts[intervalStr] || 60;
  };

  /**
   * 预加载跳转位置周围的数据
   */
  const preloadAroundJump = async (centerTime: number) => {
    // 确保数据边界已初始化
    if (
      dataBoundary.value.startTime === 0 ||
      dataBoundary.value.endTime === 0
    ) {
      console.warn("[预加载] 数据边界未初始化，跳过预加载");
      return;
    }

    // 检查数据边界，避免重复加载已存在的数据
    const rangeFrom = toTimestamp(dataBoundary.value.startTime);
    const rangeTo = toTimestamp(dataBoundary.value.endTime);

    console.log(
      `[预加载] 跳转中心时间: ${centerTime} (${new Date(centerTime * 1000).toLocaleString("zh-CN")})`,
    );
    console.log(
      `[预加载] 数据边界: [${rangeFrom}, ${rangeTo}] (${new Date(rangeFrom * 1000).toLocaleString("zh-CN")} ~ ${new Date(rangeTo * 1000).toLocaleString("zh-CN")})`,
    );

    // 计算预加载的时间范围（跳转位置前后2小时）
    const preloadBeforeTime = centerTime - 7200; // 2小时前
    const preloadAfterTime = centerTime + 7200; // 2小时后

    console.log(
      `[预加载] 预加载目标范围: [${preloadBeforeTime}, ${preloadAfterTime}]`,
    );

    const preloadPromises = [];

    // 预加载之前的数据：如果预加载范围在数据边界之前，则加载
    if (preloadBeforeTime < rangeFrom) {
      // 使用跳转中心时间作为 anchorTime（向后加载更早的数据）
      // backward: 从 centerTime 向前查找更早的数据
      const anchorTime = centerTime;
      console.log(
        `[预加载] ✅ 需要加载之前的数据: anchorTime=${anchorTime} (${new Date(anchorTime * 1000).toLocaleString("zh-CN")}), 目标时间=${preloadBeforeTime} (${new Date(preloadBeforeTime * 1000).toLocaleString("zh-CN")})`,
      );
      preloadPromises.push(
        loadKLineData({
          symbol: symbol.value,
          interval: interval.value,
          direction: "backward",
          anchorTime: anchorTime, // 从跳转中心时间向前查找更早的数据
          limit: 50,
        })
          .then((response) => {
            console.log(`[预加载] backward 请求完成: anchorTime=${anchorTime}`);
            return response;
          })
          .catch((error) => {
            console.error(
              `[预加载] backward 请求失败: anchorTime=${anchorTime}`,
              error,
            );
            throw error;
          }),
      );
    } else {
      console.log(
        `[预加载] ⏭️ 之前的数据已存在，跳过: preloadBeforeTime=${preloadBeforeTime} >= rangeFrom=${rangeFrom}`,
      );
    }

    // 预加载之后的数据：如果预加载范围在数据边界之后，则加载
    if (preloadAfterTime > rangeTo) {
      // 使用跳转中心时间作为 anchorTime（向前加载更新的数据）
      // forward: 从 centerTime 向后查找更新的数据
      const anchorTime = centerTime;
      console.log(
        `[预加载] ✅ 需要加载之后的数据: anchorTime=${anchorTime} (${new Date(anchorTime * 1000).toLocaleString("zh-CN")}), 目标时间=${preloadAfterTime} (${new Date(preloadAfterTime * 1000).toLocaleString("zh-CN")})`,
      );
      preloadPromises.push(
        loadKLineData({
          symbol: symbol.value,
          interval: interval.value,
          direction: "forward",
          anchorTime: anchorTime, // 从跳转中心时间向后查找更新的数据
          limit: 50,
        })
          .then((response) => {
            console.log(`[预加载] forward 请求完成: anchorTime=${anchorTime}`);
            return response;
          })
          .catch((error) => {
            console.error(
              `[预加载] forward 请求失败: anchorTime=${anchorTime}`,
              error,
            );
            throw error;
          }),
      );
    } else {
      console.log(
        `[预加载] ⏭️ 之后的数据已存在，跳过: preloadAfterTime=${preloadAfterTime} <= rangeTo=${rangeTo}`,
      );
    }

    // 如果没有需要预加载的数据，直接返回
    if (preloadPromises.length === 0) {
      console.log("[预加载] 跳转位置周围的数据已完整，无需预加载");
      return;
    }

    console.log(`[预加载] 开始预加载 ${preloadPromises.length} 个方向的数据`);

    // 并行预加载，不阻塞UI
    Promise.allSettled(preloadPromises).then((results) => {
      results.forEach((result, index) => {
        if (result.status === "fulfilled") {
          console.log(`[预加载] ${index === 0 ? "前" : "后"}数据预加载成功`);
        } else {
          console.warn(
            `[预加载] ${index === 0 ? "前" : "后"}数据预加载失败:`,
            result.reason,
          );
        }
      });
    });
  };

  /**
   * 添加跳转历史记录
   */
  const addJumpHistory = (record: JumpRecord) => {
    jumpHistory.value.push(record);

    // 限制历史记录数量
    if (jumpHistory.value.length > maxJumpHistory) {
      jumpHistory.value.shift();
    }
  };

  /**
   * 缓存跳转位置数据
   */
  const cacheJumpPosition = (jumpData: any) => {
    const cacheKey = `${symbol.value}_${interval.value}_${jumpData.targetTime}`;
    const cacheValue: JumpCache = {
      targetTime: jumpData.targetTime,
      timestamp: Date.now(),
      data: jumpData.klines,
      summary: jumpData.summary,
      boundary: jumpData.boundary,
    };

    jumpPositionCache.value.set(cacheKey, cacheValue);

    // 清理过期缓存（超过1小时）
    const oneHourAgo = Date.now() - 3600000;
    for (const [key, value] of jumpPositionCache.value.entries()) {
      if (value.timestamp < oneHourAgo) {
        jumpPositionCache.value.delete(key);
      }
    }
  };

  /**
   * 从缓存获取跳转数据（预留功能，可用于优化重复跳转）
   */
  const getCachedJumpData = (targetTime: number): JumpCache | null => {
    const cacheKey = `${symbol.value}_${interval.value}_${targetTime}`;
    return jumpPositionCache.value.get(cacheKey) || null;
  };

  // 导出 getCachedJumpData 供外部使用（如果需要）
  // 目前未在 return 中导出，但保留函数以备将来使用

  /**
   * 设置图表模式
   */
  const setChartMode = (mode: "realtime" | "historical") => {
    chartMode.value = mode;

    // 如果切换到实时模式，解除跳转锁定
    if (mode === "realtime") {
      clearJumpLock();
    }
  };

  /**
   * 检查当前是否在跳转锁定期间
   */
  const isInJumpLock = (): boolean => {
    return isJumping.value || Date.now() < jumpLockUntil.value;
  };

  /**
   * 获取跳转历史
   */
  const getJumpHistory = () => {
    return [...jumpHistory.value];
  };

  /**
   * 清除跳转历史
   */
  const clearJumpHistory = () => {
    jumpHistory.value = [];
  };

  /**
   * 获取缓存统计信息
   */
  const getCacheStats = () => {
    return {
      cacheSize: jumpPositionCache.value.size,
      jumpHistoryCount: jumpHistory.value.length,
    };
  };

  return {
    setupChartInteractions,
    isDragging,
    isZooming,
    scrollDirection,
    chartMode,
    isLoading,
    jumpToTime,
    advancedJumpToTime,
    jumpBack,
    setJumpLock,
    clearJumpLock,
    setChartMode,
    isInJumpLock,
    getJumpHistory,
    clearJumpHistory,
    getCacheStats,
    getCachedJumpData,
    lastVisibleRange,
  };
}
