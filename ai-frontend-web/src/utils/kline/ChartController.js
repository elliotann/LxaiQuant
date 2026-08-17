/**
 * 图表控制类
 * 负责图表的跳转、显示范围控制等操作
 *
 * 参考文档: https://tradingview.github.io/lightweight-charts/docs/api
 */

import { TimezoneHelper } from "../TimezoneHelper";

export class ChartController {
  constructor(chart, candlestickSeries, dataManager) {
    console.log("🔧 ChartController 构造函数被调用:", {
      chart: !!chart,
      candlestickSeries: !!candlestickSeries,
      dataManager: !!dataManager,
    });

    this.chart = chart;
    this.candlestickSeries = candlestickSeries;
    this.dataManager = dataManager;

    // 闪烁标记相关
    this.flashMarkerTimer = null;
    this.flashMarkerTimeout = null;

    // 可见范围监听
    this.visibleRangeSubscription = null;

    // 记录最近一次断层加载位置，防止同一断层在同一位置反复触发
    this._lastGapLoad = null;

    // 记录最近一次加载的时间戳，防止短时间内重复加载
    this._lastGapLoadTime = 0;

    // 加载状态标志（使用实例变量，避免局部变量导致的问题）
    this._isLoadingGapData = false;

    // 记录最近一次跳转的时间戳，用于防止跳转后立即响应可见范围变化
    this._lastJumpTime = 0;
    // 跳转后的锁定时间（毫秒），在这段时间内不响应可见范围变化导致的自动跳转
    // 关键修复：延长锁定时间到10秒，确保用户有足够时间查看历史数据
    this._jumpLockDuration = 10000; // 10秒

    // 设置可见范围监听
    console.log("🔧 ChartController: 准备调用 setupVisibleRangeListener");
    this.setupVisibleRangeListener();
    console.log("🔧 ChartController: setupVisibleRangeListener 调用完成");
  }

  /**
   * 设置可见范围变化监听（按照官方示例实现）
   * 参考: https://tradingview.github.io/lightweight-charts/tutorials/demos/infinite-history
   * 官方示例使用 subscribeVisibleLogicalRangeChange 而不是 subscribeVisibleTimeRangeChange
   * 检查逻辑范围（索引）而不是时间范围，更准确可靠
   */
  setupVisibleRangeListener() {
    if (!this.chart) {
      console.warn(
        "⚠️ ChartController.setupVisibleRangeListener: chart 未初始化",
      );
      return;
    }

    const timeScale = this.chart.timeScale();

    if (!timeScale) {
      console.warn(
        "⚠️ ChartController.setupVisibleRangeListener: timeScale 未初始化",
      );
      return;
    }

    console.log(
      "✅ ChartController.setupVisibleRangeListener: 开始设置历史数据加载监听器",
    );

    // 使用官方示例推荐的 subscribeVisibleLogicalRangeChange 方法
    // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#subscribeVisibleLogicalRangeChange
    // 官方示例: https://tradingview.github.io/lightweight-charts/tutorials/demos/infinite-history
    let cleanupTimer = null;
    let isLoading = false; // 防止重复加载

    this.visibleRangeSubscription =
      timeScale.subscribeVisibleLogicalRangeChange(async (logicalRange) => {
        if (!logicalRange) {
          console.log("⚠️ logicalRange 为空，跳过");
          return;
        }

        // 防抖：300ms 后才执行（减少快速拖动时的频繁触发）
        if (cleanupTimer) {
          clearTimeout(cleanupTimer);
        }

        cleanupTimer = setTimeout(async () => {
          if (!this.dataManager) {
            return;
          }

          // 关键修复：如果 DataManager 还在初次加载状态，不响应可见范围变化
          if (this.dataManager.isInitialLoad) {
            console.log("🔒 初次加载中，忽略可见范围变化");
            return;
          }

          // 防止重复加载和快速拖动时的过度处理
          // 关键修复：如果最近刚完成跳转，在锁定时间内不响应可见范围变化
          const timeSinceLastJump = Date.now() - this._lastJumpTime;
          if (
            isLoading ||
            this._isJumping ||
            this._isLoadingGapData ||
            timeSinceLastJump < this._jumpLockDuration
          ) {
            if (timeSinceLastJump < this._jumpLockDuration) {
              console.log("🔒 跳转锁定中，忽略可见范围变化:", {
                距离上次跳转: timeSinceLastJump + "ms",
                锁定时长: this._jumpLockDuration + "ms",
              });
            }
            return;
          }

          // 按照官方示例：当 logicalRange.from < 阈值时，加载更多历史数据
          // 官方示例使用 10 作为阈值，我们使用 30 来更早触发加载
          // 注意：logicalRange.from 是数字类型，表示从数据开始到可见范围开始的索引
          const threshold = 30;

          // 确保 logicalRange.from 和 logicalRange.to 是数字
          const fromIndex =
            typeof logicalRange.from === "number"
              ? logicalRange.from
              : parseFloat(logicalRange.from);
          const toIndex =
            typeof logicalRange.to === "number"
              ? logicalRange.to
              : parseFloat(logicalRange.to);

          // 获取当前数据量
          const cachedData = this.dataManager.getCurrentCache();
          const totalDataCount = cachedData.length;

          // 检查是否需要加载历史数据（向左拖动）
          if (fromIndex < threshold) {
            isLoading = true;

            try {
              // 获取当前缓存数据
              const cachedData = this.dataManager.getCurrentCache();
              if (cachedData.length === 0) {
                isLoading = false;
                return;
              }

              const firstCachedTime = cachedData[0].time;
              const intervalSeconds = this.dataManager.getIntervalSeconds();

              // 计算需要加载的数据量（按照官方示例的方式）
              const numberBarsToLoad = Math.max(threshold - fromIndex, 100); // 至少加载100条

              // 计算加载的时间范围
              const loadTo = firstCachedTime;
              const loadFrom = loadTo - numberBarsToLoad * intervalSeconds;

              // 按照官方示例的方式加载数据
              const newData = await this.dataManager.getBarsByTimeRange(
                loadFrom,
                loadTo,
              );

              // 按照官方示例：使用 setData 更新数据
              // 参考: https://tradingview.github.io/lightweight-charts/tutorials/demos/infinite-history
              if (newData.length > 0) {
                // 获取更新后的缓存数据
                const updatedCache = this.dataManager.getCurrentCache();

                // 按照官方示例：使用 setData 更新图表
                // 官方示例: series.setData(data)
                this.candlestickSeries.setData(updatedCache);
              }
            } catch (error) {
              console.error("❌ 加载历史数据失败:", error);
            } finally {
              isLoading = false;
            }
          }
          // 检查是否需要加载未来数据（向右拖动）或填充数据断层
          else {
            // 获取当前缓存数据（关键修复：在 else 分支中也需要获取 cachedData）
            const cachedData = this.dataManager.getCurrentCache();

            // 获取当前可见范围
            const timeScale = this.chart.timeScale();
            const visibleRange = timeScale.getVisibleRange();

            // 关键修复：检查是否在跳转锁定期间
            const timeSinceLastJump = Date.now() - this._lastJumpTime;
            if (
              !visibleRange ||
              this._isJumping ||
              timeSinceLastJump < this._jumpLockDuration
            ) {
              if (timeSinceLastJump < this._jumpLockDuration) {
                console.log("🔒 跳转锁定中，跳过断层检测:", {
                  距离上次跳转: timeSinceLastJump + "ms",
                  锁定时长: this._jumpLockDuration + "ms",
                });
              }
              return;
            }

            // 关键修复：检查整个数据缓存中是否有数据断层，如果有则加载断层中间的数据
            const intervalSeconds = this.dataManager.getIntervalSeconds();
            const maxGapThreshold = intervalSeconds * 1.2; // 关键修复：降低阈值到1.2个周期，更早检测空隙

            // 确保 cachedData 存在且是数组
            if (
              !cachedData ||
              !Array.isArray(cachedData) ||
              cachedData.length === 0
            ) {
              console.warn("⚠️ 缓存数据为空或无效，跳过断层检测");
              return;
            }

            // 快速检查：如果数据量很少或可见数据充足，跳过断层检测
            const totalDataPoints = cachedData.length;
            if (totalDataPoints < 50) {
              // 数据太少，不需要检测断层
              return;
            }

            // 按时间排序所有数据
            const sortedData = [...cachedData].sort((a, b) => a.time - b.time);

            // 获取可见范围内的数据
            const visibleData = sortedData.filter(
              (item) =>
                item.time >= visibleRange.from && item.time <= visibleRange.to,
            );

            // 如果可见范围内已有足够的数据，减少检测频率
            const visibleDataCount = visibleData.length;
            const expectedVisibleBars = Math.ceil(
              (visibleRange.to - visibleRange.from) / intervalSeconds,
            );
            if (visibleDataCount >= expectedVisibleBars * 0.8) {
              // 可见数据充足，减少不必要的断层检测
              return;
            }

            // 查找数据断层（检查整个数据缓存，而不仅仅是可见范围内的数据）
            let foundGap = false;
            let targetGap = null;

            // 在循环外定义可见范围变量，以便在循环外使用
            const visibleRangeStart = visibleRange.from;
            const visibleRangeEnd = visibleRange.to;

            for (let i = 0; i < sortedData.length - 1; i++) {
              const currentTime = sortedData[i].time;
              const nextTime = sortedData[i + 1].time;
              const gap = nextTime - currentTime;

              // 如果间隔超过阈值，认为是断层
              if (gap > maxGapThreshold) {
                const gapStart = currentTime;
                const gapEnd = nextTime;

                // 如果断层在可见范围内，或者可见范围结束接近断层，则加载断层中间的数据
                const isGapInVisibleRange =
                  gapStart >= visibleRangeStart && gapEnd <= visibleRangeEnd;
                const isVisibleRangeNearGap =
                  (visibleRangeEnd >= gapStart && visibleRangeEnd <= gapEnd) ||
                  (visibleRangeStart >= gapStart &&
                    visibleRangeStart <= gapEnd);

                if (isGapInVisibleRange || isVisibleRangeNearGap) {
                  foundGap = true;
                  targetGap = {
                    start: gapStart,
                    end: gapEnd,
                    gap: gap,
                    missingBars: Math.floor(gap / intervalSeconds),
                  };

                  console.log(
                    "🔍 检测到数据断层，准备加载断层中间的数据（向右拖动）:",
                    {
                      断层开始: new Date(gapStart * 1000).toISOString(),
                      断层结束: new Date(gapEnd * 1000).toISOString(),
                      断层大小: gap,
                      缺失K线数: targetGap.missingBars,
                      可见范围: {
                        开始: new Date(visibleRangeStart * 1000).toISOString(),
                        结束: new Date(visibleRangeEnd * 1000).toISOString(),
                      },
                      断层在可见范围内: isGapInVisibleRange,
                      可见范围接近断层: isVisibleRangeNearGap,
                    },
                  );

                  // 找到第一个相关的断层就处理，避免重复处理
                  break;
                }
              }
            }

            // 如果找到断层，加载断层中间的数据
            if (foundGap && targetGap) {
              // 关键修复：立即设置加载标志，防止重复触发
              if (this._isLoadingGapData) {
                return;
              }

              // 关键日志：检测到数据断层（向右拖动）
              console.log("🔍 [时间轴留空] 检测到数据断层（向右拖动）:", {
                断层开始: new Date(targetGap.start * 1000).toISOString(),
                断层结束: new Date(targetGap.end * 1000).toISOString(),
                缺失K线数: targetGap.missingBars,
                可见范围结束: new Date(visibleRangeEnd * 1000).toISOString(),
              });

              // 关键修复：先计算要加载的范围，然后检查缓存中是否已有数据
              const loadFrom = targetGap.start + intervalSeconds;

              // 限制加载范围：最多加载1000根K线（约50小时），或者加载到可见范围结束，取较小值
              const maxLoadSize = intervalSeconds * 1000; // 最多1000根K线
              const maxLoadToBySize = loadFrom + maxLoadSize; // 从loadFrom开始，最多加载1000根
              const maxLoadToByVisible = visibleRangeEnd; // 或者加载到可见范围结束

              // 取较小值，确保不会加载太多数据
              let finalLoadTo = Math.min(maxLoadToBySize, maxLoadToByVisible);

              // 但不要超过断层结束
              const maxLoadTo = targetGap.end - intervalSeconds;
              finalLoadTo = Math.min(finalLoadTo, maxLoadTo);

              // 确保 finalLoadTo > loadFrom
              if (finalLoadTo <= loadFrom) {
                finalLoadTo = loadFrom + intervalSeconds * 100; // 至少加载100根K线
              }

              // 关键修复：检查缓存中是否已经有数据覆盖了这个范围
              const cachedData = this.dataManager.getCurrentCache();

              // 检查整个要加载的范围是否已经有足够的数据
              const dataInRange = cachedData.filter(
                (item) => item.time >= loadFrom && item.time <= finalLoadTo,
              );

              // 如果已经有数据覆盖了这个范围，检查是否需要继续加载
              if (dataInRange.length > 0) {
                // 找到已加载数据的最大时间
                const maxLoadedTime = Math.max(
                  ...dataInRange.map((item) => item.time),
                );

                // 关键修复：检查可见范围是否完全在已加载数据范围内
                // 如果可见范围结束还在已加载数据范围内（留出200根K线的缓冲），则完全跳过
                const bufferSize = intervalSeconds * 200; // 200根K线的缓冲，更严格
                if (visibleRangeEnd <= maxLoadedTime - bufferSize) {
                  console.log(
                    "⏭️ [时间轴留空] 跳过加载：数据已存在且可见范围未接近边界",
                    {
                      可见范围结束: new Date(
                        visibleRangeEnd * 1000,
                      ).toISOString(),
                      已加载数据最大时间: new Date(
                        maxLoadedTime * 1000,
                      ).toISOString(),
                      缓冲: "200根K线",
                      已加载数据量: dataInRange.length,
                    },
                  );
                  return;
                }

                // 如果可见范围接近边界，需要加载下一段数据
                // 从已加载数据的最大时间开始，继续加载
                const nextLoadFrom = maxLoadedTime + intervalSeconds;
                if (nextLoadFrom >= finalLoadTo) {
                  // 已加载数据已经覆盖了目标范围，跳过
                  return;
                }

                // 调整加载范围，从已加载数据的边界开始
                const adjustedLoadTo = Math.min(
                  nextLoadFrom + maxLoadSize,
                  finalLoadTo,
                  maxLoadTo,
                );

                if (adjustedLoadTo <= nextLoadFrom) {
                  // 没有需要加载的范围，跳过
                  return;
                }

                // 检查下一段是否已经有数据
                const nextDataInRange = cachedData.filter(
                  (item) =>
                    item.time >= nextLoadFrom && item.time <= adjustedLoadTo,
                );

                if (nextDataInRange.length > 0) {
                  console.log("⏭️ [时间轴留空] 跳过加载：下一段数据已存在", {
                    下一段开始: new Date(nextLoadFrom * 1000).toISOString(),
                    下一段结束: new Date(adjustedLoadTo * 1000).toISOString(),
                    已存在数据量: nextDataInRange.length,
                  });
                  return;
                }

                // 更新加载范围，从已加载数据的边界开始加载下一段
                finalLoadTo = adjustedLoadTo;
              }

              // 防抖：如果同一个断层在同一位置已经加载过，并且当前可见范围还在已加载区域内，则跳过
              if (
                this._lastGapLoad &&
                this._lastGapLoad.start === targetGap.start &&
                this._lastGapLoad.end === targetGap.end &&
                this._lastGapLoad.loadedTo
              ) {
                const lastLoadedTo = this._lastGapLoad.loadedTo;
                const bufferSize = intervalSeconds * 200; // 200根K线的缓冲，更严格
                // 只有当可见范围结束接近或超过上次加载的边界时，才继续加载
                if (visibleRangeEnd <= lastLoadedTo - bufferSize) {
                  console.log(
                    "⏭️ [时间轴留空] 跳过加载：同一断层已加载且可见范围未接近边界",
                    {
                      可见范围结束: new Date(
                        visibleRangeEnd * 1000,
                      ).toISOString(),
                      上次加载到: new Date(lastLoadedTo * 1000).toISOString(),
                      缓冲: "200根K线",
                    },
                  );
                  return;
                }
              }

              // 关键修复：防止短时间内重复加载同一范围（2秒内）
              const now = Date.now();
              if (now - this._lastGapLoadTime < 2000) {
                console.log("⏭️ [时间轴留空] 跳过加载：距离上次加载时间太短", {
                  距离上次加载: now - this._lastGapLoadTime + "ms",
                  最小间隔: "2000ms",
                });
                return;
              }

              // 关键修复：立即设置加载标志和时间戳，防止重复触发（使用实例变量）
              this._isLoadingGapData = true;
              this._lastGapLoadTime = now;

              try {
                // 记录本次加载的断层范围，避免重复触发
                this._lastGapLoad = {
                  start: targetGap.start,
                  end: targetGap.end,
                  loadedTo: finalLoadTo,
                };

                // 关键日志：开始加载
                console.log("📥 [时间轴留空] 开始加载断层数据（向右拖动）:", {
                  from: new Date(loadFrom * 1000).toISOString(),
                  to: new Date(finalLoadTo * 1000).toISOString(),
                  缺失K线数: targetGap.missingBars,
                });

                // 加载数据
                const newData = await this.dataManager.getBarsByTimeRange(
                  loadFrom,
                  finalLoadTo,
                );

                if (newData.length > 0) {
                  // 关键日志：加载完成
                  console.log("✅ [时间轴留空] 断层数据加载完成（向右拖动）:", {
                    数据量: newData.length,
                    时间范围: {
                      最早: new Date(newData[0].time * 1000).toISOString(),
                      最晚: new Date(
                        newData[newData.length - 1].time * 1000,
                      ).toISOString(),
                    },
                  });

                  // 关键修复：在更新数据前，先记录已加载的范围，避免立即再次触发
                  const loadedDataMaxTime = Math.max(
                    ...newData.map((item) => item.time),
                  );
                  this._lastGapLoad = {
                    start: targetGap.start,
                    end: targetGap.end,
                    loadedTo: loadedDataMaxTime, // 使用实际加载的数据最大时间，而不是 finalLoadTo
                  };

                  // 更新图表数据（这会触发 subscribeVisibleLogicalRangeChange，但我们已经设置了 _isLoadingGapData = true）
                  const updatedCache = this.dataManager.getCurrentCache();
                  updatedCache.sort((a, b) => a.time - b.time);
                  this.candlestickSeries.setData(updatedCache);

                  // 等待数据更新完成，但减少延迟时间提高响应速度
                  await new Promise((resolve) => setTimeout(resolve, 100));

                  // 关键修复：不再调用 setVisibleRange，让用户自然拖动来触发下一次加载
                  // 这样可以避免立即触发新的检测
                } else {
                  // 没有加载到数据，减少等待时间
                  await new Promise((resolve) => setTimeout(resolve, 50));
                }
              } catch (error) {
                console.error("❌ [时间轴留空] 加载断层数据失败:", error);
              } finally {
                // 延迟重置加载标志，但减少延迟时间提高响应速度
                setTimeout(() => {
                  this._isLoadingGapData = false;
                }, 300); // 减少到300ms，提高响应速度
              }
            }

            // 如果没有发现断层，检查是否需要加载未来数据（接近数据末尾）
            if (
              !foundGap &&
              toIndex > totalDataCount - threshold &&
              totalDataCount > 0
            ) {
              isLoading = true;

              try {
                const lastCachedTime = cachedData[cachedData.length - 1].time;
                const intervalSeconds = this.dataManager.getIntervalSeconds();

                // 计算需要加载的数据量（至少加载100条）
                const numberBarsToLoad = Math.max(
                  threshold - (totalDataCount - toIndex),
                  100,
                );

                // 计算加载的时间范围（从最后一个数据点开始，向右加载）
                const loadFrom = lastCachedTime + intervalSeconds;
                const loadTo = loadFrom + numberBarsToLoad * intervalSeconds;

                // 加载数据
                const newData = await this.dataManager.getBarsByTimeRange(
                  loadFrom,
                  loadTo,
                );

                // 更新图表数据
                if (newData.length > 0) {
                  const updatedCache = this.dataManager.getCurrentCache();
                  // 使用 setData 更新图表
                  this.candlestickSeries.setData(updatedCache);
                }
              } catch (error) {
                console.error("❌ 加载未来数据失败:", error);
              } finally {
                isLoading = false;
              }
            }

            // 关键修复：延迟执行数据清理，避免在加载数据时清理导致跳跃
            // 只在数据量超过阈值且不在加载状态时才清理
            if (
              cachedData &&
              cachedData.length > this.dataManager.maxDataPoints * 1.5
            ) {
              // 延迟清理，避免与数据加载冲突
              setTimeout(() => {
                // 再次检查是否正在加载，避免清理正在使用的数据
                if (this._isLoadingGapData || isLoading) {
                  return;
                }

                // 获取时间范围用于清理
                const timeRange = timeScale.getVisibleRange();
                if (timeRange) {
                  // 执行清理前，先检查数据量是否仍然超过阈值
                  const currentCache = this.dataManager.getCurrentCache();
                  if (
                    currentCache.length >
                    this.dataManager.maxDataPoints * 1.5
                  ) {
                    this.dataManager.applySlidingWindow(timeRange);
                    // 更新图表数据（只保留清理后的数据）
                    const cleanedData = this.dataManager.getCurrentCache();
                    // 关键修复：只有在数据确实被清理了才更新图表，避免不必要的更新
                    if (cleanedData.length < currentCache.length) {
                      this.candlestickSeries.setData(cleanedData);
                    }
                  }
                }
              }, 2000); // 延迟2秒清理，确保数据加载完成
            }
          }
        }, 100); // 减少防抖时间到100ms，提高响应速度
      });

    console.log(
      "✅ 历史数据加载监听器已设置（使用 subscribeVisibleLogicalRangeChange）",
    );
  }

  /**
   * 更新图表数据（使用官方推荐的 setData 方法，保持用户视图位置）
   * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesApi#setData
   */
  updateChartData() {
    if (!this.chart || !this.candlestickSeries || !this.dataManager) {
      return;
    }

    const cleanedData = this.dataManager.getCurrentCache();

    if (cleanedData.length === 0) {
      console.warn("⚠️ 清理后的数据为空，跳过更新");
      return;
    }

    // 使用官方推荐的 getVisibleRange() 和 setVisibleRange() 方法
    // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#getVisibleRange
    const timeScale = this.chart.timeScale();
    const currentRange = timeScale.getVisibleRange();

    console.log("📊 更新图表数据:", {
      数据量: cleanedData.length,
      当前视图范围: currentRange
        ? {
            from: new Date(currentRange.from * 1000).toISOString(),
            to: new Date(currentRange.to * 1000).toISOString(),
          }
        : "无",
    });

    // 使用官方推荐的 setData() 方法设置数据
    // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesApi#setData
    this.candlestickSeries.setData(cleanedData);

    // 恢复可见范围（使用官方推荐的 setVisibleRange() 方法）
    // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#setVisibleRange
    if (currentRange) {
      // 确保恢复的范围在数据范围内
      const firstTime = cleanedData[0].time;
      const lastTime = cleanedData[cleanedData.length - 1].time;

      // 调整范围，确保在数据范围内
      const adjustedRange = {
        from: Math.max(currentRange.from, firstTime),
        to: Math.min(currentRange.to, lastTime),
      };

      // 如果调整后的范围无效，使用默认范围
      if (adjustedRange.to <= adjustedRange.from) {
        adjustedRange.from = firstTime;
        adjustedRange.to = Math.min(
          firstTime + (currentRange.to - currentRange.from),
          lastTime,
        );
      }

      // 使用 nextTick 确保数据更新完成后再恢复范围
      setTimeout(() => {
        try {
          timeScale.setVisibleRange(adjustedRange);
          console.log("✅ 视图范围已恢复:", {
            from: new Date(adjustedRange.from * 1000).toISOString(),
            to: new Date(adjustedRange.to * 1000).toISOString(),
          });

          // 确保时间轴可见（使用 applyOptions）
          // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#applyOptions
          try {
            timeScale.applyOptions({
              timeVisible: true,
            });
            console.log("✅ updateChartData 后确保时间轴可见");

            // 延迟再次确保（双重保险）
            setTimeout(() => {
              timeScale.applyOptions({
                timeVisible: true,
              });
              console.log("✅ updateChartData 后再次确保时间轴可见");
            }, 200);
          } catch (applyError) {
            console.warn("⚠️ 应用时间轴选项失败:", applyError);
          }

          // 如果范围太小，时间轴可能不显示，稍微扩大范围
          const finalRange = timeScale.getVisibleRange();
          if (finalRange && finalRange.to - finalRange.from < 60) {
            const expandedRange = {
              from: Math.max(finalRange.from - 300, firstTime),
              to: Math.min(finalRange.to + 300, lastTime),
            };
            if (expandedRange.to > expandedRange.from) {
              timeScale.setVisibleRange(expandedRange);
              console.log("✅ 扩大视图范围以确保时间轴可见");
            }
          }
        } catch (error) {
          console.warn("⚠️ 恢复可见范围失败:", error);
          // 如果恢复失败，尝试使用 fitContent（但只在必要时）
          try {
            timeScale.fitContent();
            console.log("✅ 使用 fitContent 作为后备方案，确保时间轴可见");
          } catch (fitError) {
            console.error("❌ fitContent 也失败:", fitError);
          }
        }
      }, 50); // 稍微增加延迟，确保数据更新完成
    }
  }

  /**
   * 跳转到指定时间（使用官方推荐的方法）
   * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#setVisibleRange
   * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#scrollToPosition
   *
   * @param {number} targetTime - 目标时间戳（UTC，秒）
   * @param {object} options - 跳转选项
   * @param {string} options.position - 目标时间在视图中的位置：'start' | 'center' | 'end'，默认'center'
   * @param {boolean} options.animated - 是否使用动画，默认true
   * @param {boolean} options.ensureData - 是否确保数据已加载，默认true
   * @param {boolean} options.showFlashMarker - 是否显示闪烁提示，默认true
   * @param {number} options.flashDuration - 闪烁持续时间（毫秒），默认3000
   * @returns {Promise<boolean>} 是否成功
   */
  async jumpToTime(targetTime, options = {}) {
    const {
      position = "center",
      animated = true,
      ensureData = true,
      showFlashMarker = true,
      flashDuration = 3000,
    } = options;

    // 设置跳转标志
    this._isJumping = true;

    try {
      // 1. 获取当前数据
      let seriesData = this.candlestickSeries.data();

      // 2. 如果需要，加载数据
      if (ensureData && this.dataManager) {
        // 关键修复：不仅检查时间范围，还要检查目标时间点是否真的有数据
        const hasTargetTimeData =
          seriesData &&
          seriesData.length > 0 &&
          seriesData.some((item) => {
            const interval = this.dataManager.getIntervalSeconds();
            return Math.abs(item.time - targetTime) <= interval / 2;
          });

        const needsLoad =
          !seriesData ||
          seriesData.length === 0 ||
          targetTime < seriesData[0].time ||
          targetTime > seriesData[seriesData.length - 1].time ||
          !hasTargetTimeData;

        if (needsLoad) {
          console.log("📥 需要加载数据:", {
            目标时间: new Date(targetTime * 1000).toISOString(),
            当前数据范围:
              seriesData && seriesData.length > 0
                ? {
                    from: new Date(seriesData[0].time * 1000).toISOString(),
                    to: new Date(
                      seriesData[seriesData.length - 1].time * 1000,
                    ).toISOString(),
                  }
                : "无数据",
            目标时间是否有数据: hasTargetTimeData,
          });

          // 强制加载目标时间点的数据
          await this.dataManager.ensureDataForTime(targetTime, 2000); // 增大缓冲区，确保跨月跳转有足够数据

          // 等待数据加载完成，减少延迟提高响应速度
          await new Promise((resolve) => setTimeout(resolve, 100));

          // 获取完整缓存数据
          const cachedData = this.dataManager.getCurrentCache();

          if (cachedData && cachedData.length > 0) {
            // 确保数据按时间排序
            cachedData.sort((a, b) => a.time - b.time);

            // 更新图表数据
            this.candlestickSeries.setData(cachedData);

            // 关键修复：重新从图表获取数据，确保使用最新数据
            await new Promise((resolve) => setTimeout(resolve, 100));
            seriesData = this.candlestickSeries.data();

            console.log("✅ 数据加载完成:", {
              数据量: seriesData.length,
              数据范围: {
                from: new Date(seriesData[0].time * 1000).toISOString(),
                to: new Date(
                  seriesData[seriesData.length - 1].time * 1000,
                ).toISOString(),
              },
              目标时间: new Date(targetTime * 1000).toISOString(),
            });
          }
        }
      }

      // 3. 检查数据
      if (!seriesData || seriesData.length === 0) {
        console.error("❌ 数据为空");
        this._isJumping = false;
        return false;
      }

      // 4. 查找最接近的K线时间
      const adjustedTargetTime = this.findNearestKlineTime(
        targetTime,
        seriesData,
      );
      const firstTime = seriesData[0].time;
      const lastTime = seriesData[seriesData.length - 1].time;

      // 5. 计算显示范围（确保目标时间在正中央）
      const interval = this.dataManager
        ? this.dataManager.getIntervalSeconds()
        : 300;
      const defaultBars = 150; // 默认显示150根K线
      const rangeLength = interval * defaultBars;

      // 计算理想范围（目标时间居中）
      let idealFrom = adjustedTargetTime - rangeLength / 2;
      let idealTo = adjustedTargetTime + rangeLength / 2;

      // 如果理想范围超出数据边界，调整但尽量保持目标时间居中
      if (idealFrom < firstTime) {
        // 目标时间靠近数据开始，调整范围
        const availableRange = lastTime - firstTime;
        const halfRange = Math.min(rangeLength / 2, availableRange / 2);

        if (adjustedTargetTime - firstTime < halfRange) {
          // 目标时间太靠近开始，使用最小范围
          idealFrom = firstTime;
          idealTo = Math.min(firstTime + rangeLength, lastTime);
        } else {
          // 可以保持目标时间居中
          idealFrom = Math.max(adjustedTargetTime - halfRange, firstTime);
          idealTo = Math.min(adjustedTargetTime + halfRange, lastTime);
        }
      } else if (idealTo > lastTime) {
        // 目标时间靠近数据结束，调整范围
        const availableRange = lastTime - firstTime;
        const halfRange = Math.min(rangeLength / 2, availableRange / 2);

        if (lastTime - adjustedTargetTime < halfRange) {
          // 目标时间太靠近结束，使用最小范围
          idealTo = lastTime;
          idealFrom = Math.max(lastTime - rangeLength, firstTime);
        } else {
          // 可以保持目标时间居中
          idealFrom = Math.max(adjustedTargetTime - halfRange, firstTime);
          idealTo = Math.min(adjustedTargetTime + halfRange, lastTime);
        }
      }

      const displayRange = {
        from: idealFrom,
        to: idealTo,
      };

      // 确保范围有效
      if (displayRange.to <= displayRange.from) {
        displayRange.from = firstTime;
        displayRange.to = Math.min(firstTime + rangeLength, lastTime);
      }

      // 计算目标时间在显示范围中的位置（用于验证是否居中）
      const rangeCenter = (displayRange.from + displayRange.to) / 2;
      const centerOffset = adjustedTargetTime - rangeCenter;
      const centerOffsetPercent =
        (centerOffset / (displayRange.to - displayRange.from)) * 100;

      console.log("📐 显示范围计算:", {
        from: new Date(displayRange.from * 1000).toISOString(),
        to: new Date(displayRange.to * 1000).toISOString(),
        目标时间: new Date(adjustedTargetTime * 1000).toISOString(),
        范围中心: new Date(rangeCenter * 1000).toISOString(),
        居中偏移: centerOffsetPercent.toFixed(2) + "%",
      });

      // 6. 执行跳转
      const timeScale = this.chart.timeScale();
      const priceScale = this.chart.priceScale("right");

      await new Promise((resolve) => setTimeout(resolve, 100));

      try {
        timeScale.setVisibleRange(displayRange);

        // 关键修复：强制调整价格轴，确保K线在可见范围内
        // 计算显示范围内的K线价格范围
        const visibleData = seriesData.filter(
          (item) =>
            item.time >= displayRange.from && item.time <= displayRange.to,
        );

        if (visibleData.length > 0) {
          // 计算价格范围
          const prices = visibleData
            .map((d) => [d.high, d.low, d.open, d.close])
            .flat();
          const minPrice = Math.min(...prices);
          const maxPrice = Math.max(...prices);
          const priceRange = maxPrice - minPrice;

          // 添加10%的边距
          const margin = priceRange * 0.1;
          const adjustedMinPrice = Math.max(0, minPrice - margin); // 确保不为负数
          const adjustedMaxPrice = maxPrice + margin;

          // 等待图表渲染完成后再调整价格轴
          await new Promise((resolve) => setTimeout(resolve, 200));

          // 强制调整价格轴范围，确保K线在可见范围内
          try {
            if (priceScale) {
              // 关键修复：强制触发价格轴重新计算
              // 方法：先禁用 autoScale，然后重新启用，这会触发根据可见数据重新计算价格范围

              // 步骤1：禁用 autoScale
              priceScale.applyOptions({
                autoScale: false,
              });

              await new Promise((resolve) => setTimeout(resolve, 50));

              // 步骤2：重新启用 autoScale，这会根据当前可见范围内的数据自动调整价格轴
              priceScale.applyOptions({
                autoScale: true,
                autoScaleAnimationEnabled: false, // 禁用动画，立即调整
                scaleMargins: {
                  top: 0.1,
                  bottom: 0.1,
                },
              });

              // 步骤3：等待价格轴调整完成
              await new Promise((resolve) => setTimeout(resolve, 150));
            }
          } catch (priceError) {
            // 如果失败，尝试备用方法：通过临时修改数据来触发重新计算
            try {
              if (priceScale) {
                priceScale.applyOptions({
                  autoScale: true,
                });
              }
            } catch (fallbackError) {
              // 忽略错误
            }
          }
        }

        // 关键修复：多次验证和微调，确保目标时间精确居中
        for (let attempt = 0; attempt < 3; attempt++) {
          // 等待渲染完成
          await new Promise((resolve) => setTimeout(resolve, 150));

          const actualRange = timeScale.getVisibleRange();
          if (!actualRange) {
            break;
          }

          const actualCenter = (actualRange.from + actualRange.to) / 2;
          const actualOffset = adjustedTargetTime - actualCenter;
          const actualOffsetPercent =
            (actualOffset / (actualRange.to - actualRange.from)) * 100;

          // 如果偏移超过2%，进行微调
          if (Math.abs(actualOffsetPercent) > 2) {
            const adjustedRange = {
              from: actualRange.from - actualOffset,
              to: actualRange.to - actualOffset,
            };

            // 确保调整后的范围在数据范围内
            if (
              adjustedRange.from >= firstTime &&
              adjustedRange.to <= lastTime
            ) {
              timeScale.setVisibleRange(adjustedRange);
            } else {
              break;
            }
          } else {
            break;
          }
        }
      } catch (error) {
        console.error("❌ setVisibleRange 失败:", error);
        try {
          timeScale.fitContent();
        } catch (e) {
          console.error("❌ fitContent 也失败:", e);
        }
      }

      // 7. 显示闪烁提示
      if (showFlashMarker) {
        this.showFlashMarker(adjustedTargetTime, flashDuration);
      }

      // 8. 检查并预加载右侧数据（关键修复：确保时间轴显示完整范围）
      // 如果跳转到较早时间点，右侧有数据断层，自动加载一部分数据，让时间轴能够显示完整范围
      // 延迟执行，确保图表已渲染完成
      setTimeout(async () => {
        await this.preloadRightSideData(displayRange, seriesData);
      }, 300);

      // 关键修复：记录跳转时间，用于防止跳转后立即响应可见范围变化
      this._lastJumpTime = Date.now();
      this._isJumping = false;

      console.log("✅ 时间跳转完成，已设置锁定时间:", {
        跳转时间: new Date(adjustedTargetTime * 1000).toISOString(),
        锁定时长: this._jumpLockDuration + "ms",
      });

      return true;
    } catch (error) {
      console.error("❌ 时间跳转失败:", error);
      // 即使失败也记录跳转时间，防止后续的可见范围变化触发跳转
      this._lastJumpTime = Date.now();
      this._isJumping = false;
      return false;
    }
  }

  /**
   * 显示闪烁提示标记（使用官方推荐的 setMarkers() 方法）
   * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesMarkersPluginApi#setMarkers
   *
   * @param {number} targetTime - 目标时间戳（UTC，秒）
   * @param {number} duration - 闪烁持续时间（毫秒）
   */
  showFlashMarker(targetTime, duration = 3000) {
    if (!this.candlestickSeries) {
      return;
    }

    // 清除之前的闪烁标记
    if (this.flashMarkerTimer) {
      clearInterval(this.flashMarkerTimer);
      this.flashMarkerTimer = null;
    }
    if (this.flashMarkerTimeout) {
      clearTimeout(this.flashMarkerTimeout);
      this.flashMarkerTimeout = null;
    }

    // 获取现有的 markers（保留其他标记，如信号标记）
    const existingMarkers = this.candlestickSeries.markers() || [];

    // 创建闪烁标记
    const flashMarker = {
      time: targetTime,
      position: "inBar", // 在K线内部
      color: "#FFD700", // 金色
      shape: "circle",
      text: "目标", // 可选：显示文本
      size: 2, // 稍大一些，更明显
    };

    // 使用官方推荐的 setMarkers() 方法设置标记
    // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ISeriesMarkersPluginApi#setMarkers
    const allMarkers = [...existingMarkers, flashMarker];

    // 确保markers按照时间升序排列（TradingView要求）
    allMarkers.sort((a, b) => a.time - b.time);

    this.candlestickSeries.setMarkers(allMarkers);

    // 闪烁动画：定期切换颜色
    let flashCount = 0;
    const maxFlashCount = Math.floor(duration / 200); // 每200ms闪烁一次

    this.flashMarkerTimer = setInterval(() => {
      flashCount++;

      // 切换颜色：金色 <-> 橙色
      const isGold = flashCount % 2 === 0;
      flashMarker.color = isGold ? "#FFD700" : "#FF8C00";

      // 更新标记
      const currentMarkers = this.candlestickSeries.markers() || [];
      const updatedMarkers = currentMarkers.map((m) => {
        // 通过时间戳和位置识别闪烁标记
        if (m.time === targetTime && m.position === "inBar") {
          return flashMarker;
        }
        return m;
      });

      // 如果标记不在现有标记中，添加它
      const hasMarker = updatedMarkers.some(
        (m) => m.time === targetTime && m.position === "inBar",
      );
      if (!hasMarker) {
        updatedMarkers.push(flashMarker);
      }

      this.candlestickSeries.setMarkers(updatedMarkers);

      // 达到最大闪烁次数，停止
      if (flashCount >= maxFlashCount) {
        clearInterval(this.flashMarkerTimer);
        this.flashMarkerTimer = null;

        // 延迟移除标记（让用户看到最后一次闪烁）
        this.flashMarkerTimeout = setTimeout(() => {
          this.removeFlashMarker(targetTime);
        }, 200);
      }
    }, 200); // 每200ms闪烁一次
  }

  /**
   * 预加载右侧数据（确保时间轴显示完整范围）
   * 当跳转到较早时间点后，如果右侧有数据断层，自动加载一部分数据，让时间轴能够显示完整范围
   *
   * @param {object} visibleRange - 可见时间范围 {from, to}
   * @param {Array} currentData - 当前数据
   */
  async preloadRightSideData(visibleRange, currentData) {
    if (!this.dataManager || !this.candlestickSeries) {
      return;
    }

    try {
      // 获取当前所有数据（包括缓存）
      const allData = this.dataManager.getCurrentCache();
      if (!allData || allData.length === 0) {
        return;
      }

      // 按时间排序
      allData.sort((a, b) => a.time - b.time);

      const intervalSeconds = this.dataManager.getIntervalSeconds();
      const maxGapThreshold = intervalSeconds * 2;

      // 查找数据中的断层（关键：查找可见范围内或接近可见范围的断层）
      let foundGap = false;
      let targetGap = null;

      const visibleRangeStart = visibleRange.from;
      const visibleRangeEnd = visibleRange.to;

      for (let i = 0; i < allData.length - 1; i++) {
        const currentTime = allData[i].time;
        const nextTime = allData[i + 1].time;
        const gap = nextTime - currentTime;

        // 如果间隔超过阈值，认为是断层
        if (gap > maxGapThreshold) {
          const gapStart = currentTime;
          const gapEnd = nextTime;

          // 关键修复：如果断层在可见范围内，或者可见范围结束接近断层开始，则预加载
          // 这样当跳转到06-01时，如果06-02后面直接是12-14，就会检测到断层并预加载
          const isGapInVisibleRange =
            gapStart >= visibleRangeStart && gapEnd <= visibleRangeEnd;
          const isVisibleRangeNearGapStart =
            visibleRangeEnd >= gapStart && visibleRangeEnd <= gapEnd;
          const isVisibleRangeBeforeGap =
            visibleRangeEnd < gapStart &&
            gapStart - visibleRangeEnd < intervalSeconds * 100;

          if (
            isGapInVisibleRange ||
            isVisibleRangeNearGapStart ||
            isVisibleRangeBeforeGap
          ) {
            foundGap = true;
            targetGap = {
              start: gapStart,
              end: gapEnd,
              gap: gap,
              missingBars: Math.floor(gap / intervalSeconds),
            };

            // 关键日志：检测到数据断层
            console.log("🔍 [时间轴留空] 检测到数据断层:", {
              断层开始: new Date(gapStart * 1000).toISOString(),
              断层结束: new Date(gapEnd * 1000).toISOString(),
              缺失K线数: targetGap.missingBars,
              可见范围结束: new Date(visibleRangeEnd * 1000).toISOString(),
            });

            // 找到第一个相关的断层就处理
            break;
          }
        }
      }

      // 如果找到断层，预加载一部分数据
      if (foundGap && targetGap) {
        // 关键修复：预加载策略
        // 1. 至少加载到可见范围结束，确保时间轴能够显示完整范围
        // 2. 如果断层很长，加载一部分数据填充断层，让时间轴显示完整范围
        const loadFrom = targetGap.start + intervalSeconds;

        // 计算需要加载的范围：
        // - 至少加载到可见范围结束（visibleRangeEnd）
        // - 如果断层很长，加载一部分数据（比如加载到断层中间，或者加载300根K线）
        // - 但不要超过断层结束
        const minLoadTo = visibleRangeEnd; // 至少加载到可见范围结束
        const maxLoadTo = targetGap.end - intervalSeconds; // 不超过断层结束

        // 关键修复：只加载到可见范围结束，不要加载整个断层
        // 这样时间轴就能显示完整范围，当用户向右拖动时，再继续加载更多数据
        // 如果断层很长，只加载一部分数据（比如加载到可见范围结束，或者加载300根K线）
        let finalLoadTo;
        if (targetGap.end > visibleRangeEnd) {
          // 断层延伸到可见范围外，只加载到可见范围结束
          // 不要加载整个断层，避免一次性加载太多数据
          finalLoadTo = Math.min(minLoadTo, maxLoadTo);

          // 关键修复：限制加载范围，最多加载1000根K线（约50小时）
          const maxLoadSize = intervalSeconds * 1000;
          const maxLoadToBySize = loadFrom + maxLoadSize;
          finalLoadTo = Math.min(finalLoadTo, maxLoadToBySize);
        } else {
          // 断层在可见范围内，加载到可见范围结束
          finalLoadTo = Math.min(minLoadTo, maxLoadTo);
        }

        // 确保范围有效
        if (finalLoadTo > loadFrom) {
          // 关键日志：开始预加载
          console.log("📥 [时间轴留空] 开始预加载断层数据:", {
            from: new Date(loadFrom * 1000).toISOString(),
            to: new Date(finalLoadTo * 1000).toISOString(),
            缺失K线数: targetGap.missingBars,
          });

          // 加载数据（只加载一部分，不全部填充）
          const newData = await this.dataManager.getBarsByTimeRange(
            loadFrom,
            finalLoadTo,
          );

          if (newData.length > 0) {
            // 关键日志：预加载完成
            console.log("✅ [时间轴留空] 预加载完成:", {
              数据量: newData.length,
              时间范围: {
                最早: new Date(newData[0].time * 1000).toISOString(),
                最晚: new Date(
                  newData[newData.length - 1].time * 1000,
                ).toISOString(),
              },
            });

            // 更新图表数据
            const updatedCache = this.dataManager.getCurrentCache();
            updatedCache.sort((a, b) => a.time - b.time);
            this.candlestickSeries.setData(updatedCache);

            // 关键修复：确保可见范围包含预加载的数据，让时间轴显示完整范围
            // 等待数据更新完成
            await new Promise((resolve) => setTimeout(resolve, 200));

            // 获取更新后的时间范围（添加检查，确保 chart 存在）
            if (this.chart) {
              try {
                const timeScale = this.chart.timeScale();
                const currentVisibleRange = timeScale.getVisibleRange();

                if (currentVisibleRange && updatedCache.length > 0) {
                  // 确保可见范围包含预加载的数据
                  const updatedLastTime =
                    updatedCache[updatedCache.length - 1].time;
                  // 扩展可见范围到预加载的数据结束，但不超过原始可见范围结束
                  const expandedRange = {
                    from: currentVisibleRange.from,
                    to: Math.max(
                      currentVisibleRange.to,
                      Math.min(updatedLastTime, visibleRangeEnd),
                    ),
                  };

                  timeScale.setVisibleRange(expandedRange);
                  // 关键日志：已扩展可见范围
                  console.log("✅ [时间轴留空] 已扩展可见范围:", {
                    from: new Date(expandedRange.from * 1000).toISOString(),
                    to: new Date(expandedRange.to * 1000).toISOString(),
                  });
                }
              } catch (error) {
                // 忽略错误
              }
            }
          }
        }
      }
    } catch (error) {
      console.error("❌ 预加载右侧数据失败:", error);
    }
  }

  /**
   * 检查并填充数据断层
   * 当跳转到较早时间点后，检查可见范围内是否有数据断层，如果有则自动加载缺失的数据
   *
   * @param {object} visibleRange - 可见时间范围 {from, to}
   * @param {Array} currentData - 当前数据
   */
  async checkAndFillDataGaps(visibleRange, currentData) {
    if (!this.dataManager || !this.candlestickSeries) {
      return;
    }

    try {
      console.log("🔍 开始检查数据断层:", {
        可见范围: {
          from: new Date(visibleRange.from * 1000).toISOString(),
          to: new Date(visibleRange.to * 1000).toISOString(),
        },
        当前数据量: currentData.length,
      });

      // 获取当前所有数据（包括缓存）
      const allData = this.dataManager.getCurrentCache();
      if (allData.length === 0) {
        console.warn("⚠️ 无数据，跳过断层检查");
        return;
      }

      // 按时间排序
      allData.sort((a, b) => a.time - b.time);

      const intervalSeconds = this.dataManager.getIntervalSeconds();
      const maxGapThreshold = intervalSeconds * 2; // 如果间隔超过2个周期，认为是断层

      // 检查可见范围内的数据连续性
      const visibleData = allData.filter(
        (item) =>
          item.time >= visibleRange.from && item.time <= visibleRange.to,
      );

      if (visibleData.length === 0) {
        console.warn("⚠️ 可见范围内无数据，跳过断层检查");
        return;
      }

      // 查找数据断层
      const gaps = [];
      for (let i = 0; i < visibleData.length - 1; i++) {
        const currentTime = visibleData[i].time;
        const nextTime = visibleData[i + 1].time;
        const gap = nextTime - currentTime;

        // 如果间隔超过阈值，认为是断层
        if (gap > maxGapThreshold) {
          gaps.push({
            from: currentTime,
            to: nextTime,
            gapSize: gap,
            missingBars: Math.floor(gap / intervalSeconds) - 1,
          });
        }
      }

      // 检查可见范围右侧是否有数据断层（延伸到可见范围外）
      const lastVisibleTime = visibleData[visibleData.length - 1].time;
      const lastDataTime = allData[allData.length - 1].time;

      // 如果可见范围右侧还有数据，但中间有断层
      if (lastVisibleTime < visibleRange.to && lastDataTime > visibleRange.to) {
        // 检查从可见范围结束到实际数据结束之间是否有断层
        const dataAfterVisible = allData.filter(
          (item) => item.time > visibleRange.to,
        );
        if (dataAfterVisible.length > 0) {
          const firstAfterVisible = dataAfterVisible[0].time;
          const gap = firstAfterVisible - lastVisibleTime;
          if (gap > maxGapThreshold) {
            gaps.push({
              from: lastVisibleTime,
              to: firstAfterVisible,
              gapSize: gap,
              missingBars: Math.floor(gap / intervalSeconds) - 1,
              extendsBeyondVisible: true,
            });
          }
        }
      }

      // 关键修复：如果可见范围右侧没有数据，但应该有时间范围内的数据，尝试加载
      // 这种情况发生在跳转到较早时间点后，右侧数据还没有加载
      if (lastVisibleTime < visibleRange.to) {
        // 检查从最后一个可见数据到可见范围结束之间是否有数据需要加载
        const expectedNextTime = lastVisibleTime + intervalSeconds;
        if (expectedNextTime < visibleRange.to) {
          // 检查是否有数据覆盖这个范围
          const hasDataInRange = allData.some(
            (item) =>
              item.time >= expectedNextTime && item.time <= visibleRange.to,
          );

          if (!hasDataInRange) {
            // 没有数据，需要加载
            console.log("📥 检测到可见范围右侧缺少数据，准备加载:", {
              从: new Date(expectedNextTime * 1000).toISOString(),
              到: new Date(visibleRange.to * 1000).toISOString(),
              最后一个可见数据: new Date(lastVisibleTime * 1000).toISOString(),
            });

            // 添加到加载队列
            gaps.push({
              from: lastVisibleTime,
              to: visibleRange.to,
              gapSize: visibleRange.to - lastVisibleTime,
              missingBars: Math.floor(
                (visibleRange.to - lastVisibleTime) / intervalSeconds,
              ),
              needsLoad: true,
            });
          }
        }
      }

      if (gaps.length === 0) {
        console.log("✅ 未发现数据断层");
        return;
      }

      console.log(
        "⚠️ 发现数据断层:",
        gaps.map((gap) => ({
          从: new Date(gap.from * 1000).toISOString(),
          到: new Date(gap.to * 1000).toISOString(),
          断层大小: gap.gapSize,
          缺失K线数: gap.missingBars,
        })),
      );

      // 加载缺失的数据
      for (const gap of gaps) {
        try {
          // 计算需要加载的时间范围（稍微扩大一点，确保覆盖）
          const loadFrom = gap.from + intervalSeconds;
          const loadTo = gap.to - intervalSeconds;

          // 如果范围太小，跳过
          if (loadTo <= loadFrom) {
            continue;
          }

          console.log("📥 开始加载断层数据:", {
            from: new Date(loadFrom * 1000).toISOString(),
            to: new Date(loadTo * 1000).toISOString(),
            缺失K线数: gap.missingBars,
          });

          // 加载数据
          const newData = await this.dataManager.getBarsByTimeRange(
            loadFrom,
            loadTo,
          );

          if (newData.length > 0) {
            console.log("✅ 断层数据加载完成:", {
              数据量: newData.length,
              时间范围: {
                最早: new Date(newData[0].time * 1000).toISOString(),
                最晚: new Date(
                  newData[newData.length - 1].time * 1000,
                ).toISOString(),
              },
            });

            // 更新图表数据
            const updatedCache = this.dataManager.getCurrentCache();
            updatedCache.sort((a, b) => a.time - b.time);
            this.candlestickSeries.setData(updatedCache);

            console.log("✅ 图表数据已更新，填充断层:", {
              更新前数据量: allData.length,
              更新后数据量: updatedCache.length,
              新增数据量: updatedCache.length - allData.length,
            });
          } else {
            console.warn("⚠️ 断层数据加载返回空，可能该时间段无数据");
          }
        } catch (error) {
          console.error("❌ 加载断层数据失败:", error);
        }
      }

      // 如果填充了断层，重新检查是否还有断层
      if (gaps.length > 0) {
        // 等待数据更新完成
        await new Promise((resolve) => setTimeout(resolve, 300));

        // 递归检查（最多检查2次，避免无限循环）
        const updatedData = this.dataManager.getCurrentCache();
        if (updatedData.length > allData.length) {
          console.log("🔄 数据已更新，再次检查断层...");
          // 不递归，避免无限循环，只检查一次
        }
      }
    } catch (error) {
      console.error("❌ 检查数据断层失败:", error);
    }
  }

  /**
   * 移除闪烁标记
   * @param {number} targetTime - 目标时间戳（UTC，秒）
   */
  removeFlashMarker(targetTime) {
    if (!this.candlestickSeries) {
      return;
    }

    // 获取现有标记
    const existingMarkers = this.candlestickSeries.markers() || [];

    // 过滤掉闪烁标记（通过时间戳、位置和颜色判断）
    const filteredMarkers = existingMarkers.filter((marker) => {
      // 移除目标时间的 inBar 位置的标记，且颜色是闪烁颜色
      return !(
        marker.time === targetTime &&
        marker.position === "inBar" &&
        (marker.color === "#FFD700" || marker.color === "#FF8C00")
      );
    });

    this.candlestickSeries.setMarkers(filteredMarkers);
  }

  /**
   * 计算显示范围（确保目标时间居中）
   * @param {number} targetTime - 目标时间戳（UTC，秒）
   * @param {string} position - 位置：'start' | 'center' | 'end'，但跳转时强制使用'center'
   * @param {Array} seriesData - K线数据
   * @returns {object} 包含 from 和 to 的时间范围
   */
  calculateDisplayRange(targetTime, position, seriesData) {
    const interval = this.dataManager
      ? this.dataManager.getIntervalSeconds()
      : 300;
    const defaultBars = 150; // 默认显示150根K线
    const rangeLength = interval * defaultBars;

    const firstTime = seriesData[0].time;
    const lastTime = seriesData[seriesData.length - 1].time;

    // 跳转时强制居中显示
    let from, to;

    if (position === "start") {
      from = targetTime;
      to = Math.min(targetTime + rangeLength, lastTime);
    } else if (position === "end") {
      from = Math.max(targetTime - rangeLength, firstTime);
      to = targetTime;
    } else {
      // center（默认，跳转时使用）
      // 计算理想范围（目标时间居中）
      from = Math.max(targetTime - rangeLength / 2, firstTime);
      to = Math.min(targetTime + rangeLength / 2, lastTime);

      // 如果理想范围超出边界，调整但尽量保持目标时间居中
      if (from < firstTime) {
        // 目标时间靠近数据开始
        const availableRange = lastTime - firstTime;
        const halfRange = Math.min(rangeLength / 2, availableRange / 2);

        if (targetTime - firstTime < halfRange) {
          // 目标时间太靠近开始，使用最小范围
          from = firstTime;
          to = Math.min(firstTime + rangeLength, lastTime);
        } else {
          // 可以保持目标时间居中
          from = Math.max(targetTime - halfRange, firstTime);
          to = Math.min(targetTime + halfRange, lastTime);
        }
      } else if (to > lastTime) {
        // 目标时间靠近数据结束
        const availableRange = lastTime - firstTime;
        const halfRange = Math.min(rangeLength / 2, availableRange / 2);

        if (lastTime - targetTime < halfRange) {
          // 目标时间太靠近结束，使用最小范围
          from = Math.max(lastTime - rangeLength, firstTime);
          to = lastTime;
        } else {
          // 可以保持目标时间居中
          from = Math.max(targetTime - halfRange, firstTime);
          to = Math.min(targetTime + halfRange, lastTime);
        }
      }
    }

    // 确保范围有效
    if (to <= from) {
      to = Math.min(from + rangeLength, lastTime);
    }

    return { from, to };
  }

  /**
   * 查找最接近目标时间的K线时间点
   * 如果目标时间没有对应的K线（比如周末、非交易时间），返回最接近的K线时间
   * @param {number} targetTime - 目标时间戳（UTC，秒）
   * @param {Array} seriesData - K线数据数组
   * @returns {number} 最接近的K线时间戳
   */
  findNearestKlineTime(targetTime, seriesData) {
    if (!seriesData || seriesData.length === 0) {
      return targetTime;
    }

    // 如果目标时间在数据范围外，返回边界值
    const firstTime = seriesData[0].time;
    const lastTime = seriesData[seriesData.length - 1].time;

    if (targetTime < firstTime) {
      return firstTime;
    }
    if (targetTime > lastTime) {
      return lastTime;
    }

    // 二分查找最接近的K线时间点
    let left = 0;
    let right = seriesData.length - 1;
    let closestTime = firstTime;
    let minDiff = Math.abs(targetTime - firstTime);

    while (left <= right) {
      const mid = Math.floor((left + right) / 2);
      const midTime = seriesData[mid].time;
      const diff = Math.abs(targetTime - midTime);

      // 如果找到精确匹配，直接返回
      if (midTime === targetTime) {
        return targetTime;
      }

      // 更新最接近的时间点
      if (diff < minDiff) {
        minDiff = diff;
        closestTime = midTime;
      }

      // 继续查找
      if (targetTime < midTime) {
        right = mid - 1;
      } else {
        left = mid + 1;
      }
    }

    // 检查左右相邻的时间点，选择更接近的
    const closestIndex = seriesData.findIndex(
      (item) => item.time === closestTime,
    );
    if (closestIndex >= 0) {
      // 检查前一个时间点
      if (closestIndex > 0) {
        const prevTime = seriesData[closestIndex - 1].time;
        const prevDiff = Math.abs(targetTime - prevTime);
        if (prevDiff < minDiff) {
          closestTime = prevTime;
        }
      }

      // 检查后一个时间点
      if (closestIndex < seriesData.length - 1) {
        const nextTime = seriesData[closestIndex + 1].time;
        const nextDiff = Math.abs(targetTime - nextTime);
        if (nextDiff < minDiff) {
          closestTime = nextTime;
        }
      }
    }

    return closestTime;
  }

  /**
   * 等待数据更新
   * @param {number} timeout - 超时时间（毫秒）
   * @returns {Promise<void>}
   */
  waitForDataUpdate(timeout = 3000) {
    return new Promise((resolve, reject) => {
      const startTime = Date.now();
      const checkInterval = setInterval(() => {
        const data = this.candlestickSeries.data();
        if (data && data.length > 0) {
          clearInterval(checkInterval);
          resolve();
        } else if (Date.now() - startTime > timeout) {
          clearInterval(checkInterval);
          reject(new Error("数据更新超时"));
        }
      }, 50);
    });
  }

  /**
   * 跳转到最新数据
   * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#scrollToRealTime
   *
   * @returns {Promise<void>}
   */
  async jumpToLatest() {
    // 1. 加载最新数据（如果需要）
    if (this.dataManager) {
      const currentTime = Math.floor(Date.now() / 1000);
      await this.dataManager.ensureDataForTime(currentTime);
      await this.waitForDataUpdate();
    }

    // 2. 使用官方推荐的 scrollToRealTime() 方法滚动到实时数据
    // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#scrollToRealTime
    const timeScale = this.chart.timeScale();
    timeScale.scrollToRealTime();
  }

  /**
   * 获取当前可见的时间范围
   * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#getVisibleRange
   *
   * @returns {object|null} 包含 from 和 to 的时间范围对象
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
  }

  /**
   * 清理资源
   */
  destroy() {
    // 清理闪烁标记定时器
    if (this.flashMarkerTimer) {
      clearInterval(this.flashMarkerTimer);
      this.flashMarkerTimer = null;
    }
    if (this.flashMarkerTimeout) {
      clearTimeout(this.flashMarkerTimeout);
      this.flashMarkerTimeout = null;
    }

    // 取消可见范围监听（调用返回的函数取消订阅，官方推荐方法）
    // 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/ITimeScaleApi#subscribeVisibleTimeRangeChange
    if (this.visibleRangeSubscription) {
      this.visibleRangeSubscription(); // 调用返回的函数取消订阅
      this.visibleRangeSubscription = null;
    }
  }
}
