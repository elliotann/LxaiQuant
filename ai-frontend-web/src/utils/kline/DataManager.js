/**
 * K线数据管理类
 * 负责K线数据的加载、缓存和管理
 *
 * 参考文档: https://tradingview.github.io/lightweight-charts/docs/api
 */

import { getklineData, getUnshiftData } from "@/api/member";
import { TimezoneHelper } from "@/utils/TimezoneHelper";

export class DataManager {
  constructor(options = {}) {
    this.symbol = options.symbol || "";
    this.interval = options.interval || "3m"; // 默认使用3分钟，与测试工具保持一致
    this.memberId = options.memberId || "";
    this.accountId = options.accountId || "";
    this.memberPlatform = options.memberPlatform || "OKX";
    this.indicatorType = options.indicatorType || "";
    this.autoLoadGaps = options.autoLoadGaps !== false; // 默认启用自动加载空隙功能

    // 数据缓存
    this.dataCache = []; // 已加载的数据
    this.loadingRanges = new Set(); // 正在加载的时间范围

    // 性能控制参数
    this.maxDataPoints = options.maxDataPoints || 3000; // 最大数据点数量
    this.bufferRatio = options.bufferRatio || 2.0; // 缓冲区倍数
    this.cleanupThreshold = options.cleanupThreshold || 4000; // 清理阈值

    // 加载控制
    this.loadingQueue = []; // 加载队列
    this.maxConcurrentLoads = 2; // 最大并发加载数
    this.currentLoads = 0; // 当前加载数
    this.loadDebounceTime = 300; // 防抖时间（毫秒）
    this.loadTimer = null;

    // 初次加载标志：用于区分初次加载和后续的历史数据加载
    this.isInitialLoad = true; // 默认为true，初次加载完成后设置为false
  }

  /**
   * 获取周期代码（转换为后端枚举值）
   */
  getIntervalCode(interval) {
    const intervalMap = {
      "1m": "OKXMIN1",
      "3m": "OKXMIN3",
      "5m": "OKXMIN5",
      "15m": "OKXMIN15",
      "30m": "OKXMIN30",
      "1H": "OKXMIN60", // 1小时 = 60分钟
      "4h": "OKX4HOUR", // 4小时
      "1d": "OKX1D", // 1天
      "1w": "WEEK1", // 1周
    };
    return intervalMap[interval] || "OKXMIN5"; // 默认返回 5分钟
  }

  /**
   * 获取周期对应的秒数
   */
  getIntervalSeconds() {
    const map = {
      "1m": 60,
      "3m": 180,
      "5m": 300,
      "15m": 900,
      "30m": 1800,
      "1H": 3600,
      "4h": 14400,
      "1d": 86400,
    };
    return map[this.interval] || 300;
  }

  /**
   * 获取指定时间范围的数据
   * @param {number} from - 开始时间（UTC时间戳，秒）
   * @param {number} to - 结束时间（UTC时间戳，秒）
   * @returns {Promise<Array>} K线数据数组
   */
  async getBarsByTimeRange(from, to, forceLoad = false) {
    try {
      // 关键修复：如果还在初次加载状态，且不是强制加载，直接返回空数组
      // 避免在初始化时触发历史数据加载
      if (this.isInitialLoad && !forceLoad) {
        console.log("🔒 初次加载中，跳过 getBarsByTimeRange 请求:", {
          from: new Date(from * 1000).toISOString(),
          to: new Date(to * 1000).toISOString(),
        });
        return [];
      }

      // 1. 检查缓存中是否已有数据（除非强制加载）
      if (!forceLoad) {
        const cachedData = this.getCachedData(from, to);

        if (this.isRangeFullyCached(from, to, cachedData)) {
          return cachedData;
        }
      }

      // 2. 计算需要加载的时间范围
      const cachedData = this.getCachedData(from, to);
      const missingRanges = forceLoad
        ? [{ from, to }] // 强制加载时，加载整个范围
        : this.calculateMissingRanges(from, to, cachedData);

      if (missingRanges.length === 0) {
        return cachedData;
      }

      // 3. 并发加载缺失的数据
      console.log("📡 开始加载缺失数据:", {
        缺失范围数量: missingRanges.length,
        缺失范围详情: missingRanges.map((range) => ({
          from: range.from,
          to: range.to,
          fromUTC: new Date(range.from * 1000).toISOString(),
          toUTC: new Date(range.to * 1000).toISOString(),
        })),
      });

      const loadPromises = missingRanges.map((range) =>
        this.loadFromBackend(range.from, range.to),
      );

      const newData = await Promise.all(loadPromises);

      console.log("📦 后端数据加载结果:", {
        加载任务数量: loadPromises.length,
        返回数据数组数量: newData.length,
        每个数组的数据量: newData.map((arr) => (arr ? arr.length : 0)),
        总数据量: newData.flat().length,
        第一个数据样例:
          newData.flat().length > 0
            ? {
                time: newData.flat()[0].time,
                UTC时间: new Date(newData.flat()[0].time * 1000).toISOString(),
                北京时间: new Date(
                  newData.flat()[0].time * 1000,
                ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
              }
            : "无数据",
      });

      // 4. 合并数据并更新缓存
      const mergedData = this.mergeData(cachedData, newData.flat());

      console.log("🔀 数据合并结果:", {
        原始缓存数据量: cachedData.length,
        新增数据量: newData.flat().length,
        合并后数据量: mergedData.length,
        合并后时间范围:
          mergedData.length > 0
            ? {
                最早: new Date(mergedData[0].time * 1000).toISOString(),
                最晚: new Date(
                  mergedData[mergedData.length - 1].time * 1000,
                ).toISOString(),
              }
            : "无数据",
      });

      console.log("📥 DataManager 数据加载完成:", {
        加载的原始数据量: newData.flat().length,
        合并后的数据量: mergedData.length,
        加载数据时间范围:
          mergedData.length > 0
            ? {
                最早: new Date(mergedData[0].time * 1000).toISOString(),
                最晚: new Date(
                  mergedData[mergedData.length - 1].time * 1000,
                ).toISOString(),
                最早北京时间: new Date(
                  mergedData[0].time * 1000,
                ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
                最晚北京时间: new Date(
                  mergedData[mergedData.length - 1].time * 1000,
                ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
              }
            : "无数据",
        样本数据: mergedData.slice(0, 3).map((item) => ({
          time: item.time,
          timeISO: new Date(item.time * 1000).toISOString(),
          timeBeijing: new Date(item.time * 1000).toLocaleString("zh-CN", {
            timeZone: "Asia/Shanghai",
          }),
          open: item.open,
          high: item.high,
          low: item.low,
          close: item.close,
        })),
      });

      this.updateCache(mergedData);

      // 关键修复：检查合并后的数据是否有空隙，如果有则自动加载
      const finalData = this.getCurrentCache();
      if (finalData.length > 1) {
        const interval = this.getIntervalSeconds();
        const sortedData = [...finalData].sort((a, b) => a.time - b.time);
        const gapsToLoad = [];

        // 检查请求范围内的空隙
        for (let i = 0; i < sortedData.length - 1; i++) {
          const currentTime = sortedData[i].time;
          const nextTime = sortedData[i + 1].time;
          const gap = nextTime - currentTime;

          // 如果间隔超过1.5个周期，且在请求范围内，则标记需要加载
          if (gap > interval * 1.5) {
            const gapStart = currentTime + interval;
            const gapEnd = nextTime - interval;

            if (gapStart <= gapEnd && gapStart <= to && gapEnd >= from) {
              gapsToLoad.push({ from: gapStart, to: gapEnd });
            }
          }
        }

        // 如果启用了自动加载空隙功能，则异步加载空隙数据
        if (this.autoLoadGaps && gapsToLoad.length > 0) {
          console.log(`🔍 检测到 ${gapsToLoad.length} 个数据空隙，自动加载...`);

          // 异步加载空隙数据
          Promise.all(
            gapsToLoad.map((gap) => this.loadFromBackend(gap.from, gap.to)),
          )
            .then((gapData) => {
              if (gapData.flat().length > 0) {
                console.log(
                  `✅ 自动加载空隙数据完成，加载了 ${gapData.flat().length} 根K线`,
                );
                // 更新缓存
                this.updateCache(gapData.flat());
              }
            })
            .catch((error) => {
              console.error("❌ 自动加载空隙数据失败:", error);
            });
        } else if (!this.autoLoadGaps && gapsToLoad.length > 0) {
          console.log(
            `⏭️ 检测到 ${gapsToLoad.length} 个数据空隙，但自动加载功能已禁用`,
          );
        }
      }

      // 关键日志：只记录时间轴留空相关的加载
      if (missingRanges.length > 0) {
        console.log("📥 [时间轴留空] DataManager 加载数据:", {
          加载范围数量: missingRanges.length,
          总数据量: mergedData.length,
          时间范围: {
            最早:
              mergedData.length > 0
                ? new Date(mergedData[0].time * 1000).toISOString()
                : "无",
            最晚:
              mergedData.length > 0
                ? new Date(
                    mergedData[mergedData.length - 1].time * 1000,
                  ).toISOString()
                : "无",
          },
        });
      }

      return mergedData;
    } catch (error) {
      console.error(
        "❌ [时间轴留空] DataManager.getBarsByTimeRange 出错:",
        error,
      );
      throw error;
    }
  }

  /**
   * 从后端加载数据
   * @param {number} from - 开始时间（UTC时间戳，秒）
   * @param {number} to - 结束时间（UTC时间戳，秒）
   * @returns {Promise<Array>} K线数据数组
   */
  async loadFromBackend(from, to) {
    const rangeKey = `${from}-${to}`;

    // 不输出详细日志，只保留关键的时间轴留空相关日志

    // 防止重复加载
    if (this.loadingRanges.has(rangeKey)) {
      console.log("DataManager.loadFromBackend 正在加载中，跳过");
      return [];
    }

    this.loadingRanges.add(rangeKey);

    try {
      const intervalCode = this.getIntervalCode(this.interval);
      const currentTime = Math.floor(Date.now() / 1000);

      // 不输出详细参数日志

      // 计算需要加载的数据量
      const timeRange = to - from;
      const intervalSeconds = this.getIntervalSeconds();
      const estimatedBars = Math.ceil(timeRange / intervalSeconds);

      // 限制单次加载数量
      const maxBars = Math.min(Math.max(estimatedBars, 200), 2000);

      let allData = [];

      // 如果目标时间范围较大，需要分批加载
      if (estimatedBars > 1000) {
        // 分批加载
        const batchSize = 1000;
        const batchCount = Math.ceil(estimatedBars / batchSize);

        for (let i = 0; i < batchCount; i++) {
          const batchFrom = from + i * batchSize * intervalSeconds;
          const batchTo = Math.min(
            from + (i + 1) * batchSize * intervalSeconds,
            to,
          );

          const batchData = await this.loadSingleBatch(
            batchFrom,
            batchTo,
            intervalCode,
            currentTime,
          );
          allData.push(...batchData);
        }
      } else {
        // 单次加载
        allData = await this.loadSingleBatch(
          from,
          to,
          intervalCode,
          currentTime,
        );
      }

      // 按时间排序并去重
      allData.sort((a, b) => a.time - b.time);
      const uniqueData = [];
      const seenTimes = new Set();
      for (const item of allData) {
        if (!seenTimes.has(item.time)) {
          seenTimes.add(item.time);
          uniqueData.push(item);
        }
      }

      // 过滤时间范围（数据已经在 loadSingleBatch 中转换为 UTC 时间戳）
      let filteredData = uniqueData.filter(
        (item) => item.time >= from && item.time <= to,
      );

      // 如果过滤后数据量为0，但原始数据不为空，输出调试信息并尝试放宽条件
      if (filteredData.length === 0 && uniqueData.length > 0) {
        // 检查时间范围差异
        const dataTimeRange = {
          earliest: uniqueData[0].time,
          latest: uniqueData[uniqueData.length - 1].time,
        };
        const earliestDiff = Math.abs(dataTimeRange.earliest - from);
        const latestDiff = Math.abs(dataTimeRange.latest - to);
        const maxDiff = Math.max(earliestDiff, latestDiff);

        // 无论时间范围是否匹配，都使用所有返回的数据
        // 因为后端可能返回了其他时间范围的数据，但至少是有数据的
        filteredData = uniqueData;
      }

      return filteredData;
    } catch (error) {
      console.error("❌ [时间轴留空] DataManager.loadFromBackend 出错:", error);
      return [];
    } finally {
      this.loadingRanges.delete(rangeKey);
    }
  }

  /**
   * 加载单批数据
   */
  async loadSingleBatch(from, to, intervalCode, currentTime) {
    // 关键修复：在方法开始处再次检查 isInitialLoad，防止在延迟设置标志期间调用
    if (this.isInitialLoad) {
      console.warn(
        "⚠️ [防护] loadSingleBatch 检测到 isInitialLoad 仍为 true，强制使用 getklineData",
      );
      const params = {
        memberId: this.memberId || "",
        thirdAccountId: this.accountId || "",
        symbol: this.symbol,
        interval: intervalCode,
        dataInterval: this.interval,
        memberPlatform: this.memberPlatform,
        ...(this.indicatorType ? { indicatorType: this.indicatorType } : {}),
        pageNumber: 1,
        pageSize: 200,
        size: 200,
      };
      console.log("📡 [防护] 使用 getklineData 替代 getUnshiftData:", {
        时间范围: {
          from: new Date(from * 1000).toISOString(),
          to: new Date(to * 1000).toISOString(),
        },
      });
      const response = await getklineData(params);
      // 使用相同的数据格式化逻辑
      if (
        response &&
        response.success &&
        (response.result?.klineDatas || response.data?.klineDatas)
      ) {
        const klineDatas =
          response.result?.klineDatas || response.data?.klineDatas;
        return klineDatas.map((item) => {
          let timeValue = item.id;
          if (timeValue > 1e12) {
            timeValue = Math.floor(timeValue / 1000);
          }
          return {
            time: timeValue,
            open: parseFloat(item.openPrice || item.open || 0),
            high: parseFloat(item.highPrice || item.high || 0),
            low: parseFloat(item.lowPrice || item.low || 0),
            close: parseFloat(item.closePrice || item.close || 0),
          };
        });
      }
      return [];
    }
    const params = {
      memberId: this.memberId || "", // 允许为空
      thirdAccountId: this.accountId || "", // 允许为空
      symbol: this.symbol,
      interval: intervalCode,
      dataInterval: this.interval,
      memberPlatform: this.memberPlatform,
      // indicatorType 可以为空，如果为空则不传这个参数
      ...(this.indicatorType ? { indicatorType: this.indicatorType } : {}),
      pageNumber: 1,
      pageSize: 1000,
      size: 1000,
    };

    // 不输出详细请求参数日志

    // 对于历史数据，优先使用 getUnshiftData
    // 对于最新数据，使用 getklineData
    // 关键修复：初次加载时，强制使用 getklineData，不使用 getUnshiftData
    let response;
    try {
      // 判断是获取最新数据还是历史数据
      const isRecentData = to >= currentTime - 300; // 5分钟内认为是"最新"

      // 关键修复：初次加载时，强制使用 getklineData，不使用 getUnshiftData
      // 初次加载时，无论时间范围如何，都使用 getklineData
      if (this.isInitialLoad) {
        // 初次加载时，强制使用 getklineData
        const latestParams = {
          ...params,
          size: Math.min(params.size, 200), // 初次加载限制200条
        };
        console.log("📡 [初次加载] 使用 getklineData 加载数据:", {
          是否初次加载: this.isInitialLoad,
          时间范围: {
            from: new Date(from * 1000).toISOString(),
            to: new Date(to * 1000).toISOString(),
            from北京时间: new Date(from * 1000).toLocaleString("zh-CN", {
              timeZone: "Asia/Shanghai",
            }),
            to北京时间: new Date(to * 1000).toLocaleString("zh-CN", {
              timeZone: "Asia/Shanghai",
            }),
          },
          当前时间: new Date(currentTime * 1000).toISOString(),
          当前北京时间: new Date(currentTime * 1000).toLocaleString("zh-CN", {
            timeZone: "Asia/Shanghai",
          }),
        });
        response = await getklineData(latestParams);

        // 注意：不在这里设置 isInitialLoad = false
        // 因为初次加载可能通过 updateCache 完成，那里会延迟设置标志
        // 如果在这里设置，可能会在 updateCache 之前就允许历史数据加载
        console.log(
          "✅ [初次加载] getklineData 调用完成，isInitialLoad 标志将在 updateCache 中延迟设置",
        );
      } else if (isRecentData) {
        // 非初次加载，但获取最新数据时，使用 getklineData
        const latestParams = {
          ...params,
          size: Math.min(params.size, 200), // 最新数据限制200条
        };
        console.log("📡 [最新数据] 使用 getklineData 加载数据:", {
          是否最新数据: isRecentData,
          时间范围: {
            from: new Date(from * 1000).toISOString(),
            to: new Date(to * 1000).toISOString(),
          },
        });
        response = await getklineData(latestParams);
      } else {
        // 非初次加载且不是最新数据时，使用getUnshiftData获取历史数据
        // 关键修复：再次检查 isInitialLoad，防止在延迟设置标志期间调用
        if (this.isInitialLoad) {
          console.warn(
            "⚠️ [防护] 检测到 isInitialLoad 仍为 true，强制使用 getklineData 而不是 getUnshiftData",
          );
          const latestParams = {
            ...params,
            size: Math.min(params.size, 200),
          };
          response = await getklineData(latestParams);
        } else {
          const historyParams = {
            ...params,
            to: to, // 秒级时间戳
            direction: "LEFT", // 往左/往前加载历史数据
            toTime: false,
          };
          // 如果 indicatorType 为空，尝试使用默认值（参考 KlineNew.vue）
          if (!historyParams.indicatorType && this.symbol.includes("ETH")) {
            historyParams.indicatorType =
              "OKX-ETH-USDT202405121789571050845601792";
          }

          // 关键修复：添加调用栈追踪，帮助定位调用来源
          console.trace(
            "📡 [历史数据] 使用 getUnshiftData 加载历史数据 - 调用栈:",
          );
          console.log("📡 [历史数据] 使用 getUnshiftData 加载历史数据:", {
            时间范围: {
              from: new Date(from * 1000).toISOString(),
              to: new Date(to * 1000).toISOString(),
              from北京时间: new Date(from * 1000).toLocaleString("zh-CN", {
                timeZone: "Asia/Shanghai",
              }),
              to北京时间: new Date(to * 1000).toLocaleString("zh-CN", {
                timeZone: "Asia/Shanghai",
              }),
            },
            当前时间: new Date(currentTime * 1000).toISOString(),
            当前北京时间: new Date(currentTime * 1000).toLocaleString("zh-CN", {
              timeZone: "Asia/Shanghai",
            }),
            时间差: currentTime - to,
            时间差小时: Math.floor((currentTime - to) / 3600),
            isInitialLoad: this.isInitialLoad,
          });
          response = await getUnshiftData(historyParams);
        }
      }

      // 如果返回成功但没有数据，尝试使用 getUnshiftData
      if (
        response?.success &&
        (!response?.result?.klineDatas ||
          response.result.klineDatas.length === 0)
      ) {
        // 如果使用 getklineData 失败，尝试使用 getUnshiftData
        if (to >= currentTime - 300 && !response?.result?.klineDatas?.length) {
          const retryParams = {
            ...params,
            to: to, // 保持秒级时间戳，不要乘以1000
            direction: "LEFT",
            memberId: "", // 尝试使用空的 memberId
            thirdAccountId: "", // 尝试使用空的 accountId
          };
          // 如果 indicatorType 为空，尝试使用默认值
          if (!retryParams.indicatorType && this.symbol.includes("ETH")) {
            retryParams.indicatorType =
              "OKX-ETH-USDT202405121789571050845601792";
          }
          const retryResponse = await getUnshiftData(retryParams);
          console.log("getUnshiftData 重试请求响应:", {
            success: retryResponse?.success,
            hasKlineDatas: !!retryResponse?.result?.klineDatas,
            klineDatasCount: retryResponse?.result?.klineDatas?.length || 0,
          });
          if (
            retryResponse?.success &&
            retryResponse?.result?.klineDatas?.length > 0
          ) {
            console.log("使用 getUnshiftData 成功获取到数据");
            response = retryResponse;
          }
        }
      }
    } catch (error) {
      console.error("DataManager.loadSingleBatch API调用失败:", error);
      throw error;
    }

    console.log("🔍 loadSingleBatch 响应检查:", {
      response存在: !!response,
      response结构: response ? Object.keys(response) : "无响应",
      response值: response,
      success值: response?.success,
      result存在: !!response?.result,
      result结构: response?.result ? Object.keys(response?.result) : "无result",
      klineDatas存在: !!response?.result?.klineDatas,
      klineDatas类型: response?.result?.klineDatas
        ? typeof response.result.klineDatas
        : "未知",
      klineDatas长度: response?.result?.klineDatas?.length || 0,
    });

    // 支持两种响应结构：
    // 1. { success: true, result: { klineDatas: [...] } } - KlineDataController
    // 2. { success: true, data: { klineDatas: [...] } } - MemberController
    const hasValidResponse =
      response &&
      response.success &&
      ((response.result && response.result.klineDatas) || // KlineDataController格式
        (response.data && response.data.klineDatas)); // MemberController格式

    console.log("🔍 响应格式检查:", {
      原始响应结构: response,
      KlineDataController格式: !!response?.result?.klineDatas,
      MemberController格式: !!response?.data?.klineDatas,
      使用格式: response?.result?.klineDatas
        ? "KlineDataController"
        : "MemberController",
    });

    if (hasValidResponse) {
      // 根据响应格式获取数据
      const klineDatas =
        response.result?.klineDatas || response.data?.klineDatas;

      console.log("🔍 后端返回的原始数据检查:", {
        返回数据量: klineDatas.length,
        样本原始数据: klineDatas.slice(0, 3).map((item) => ({
          id: item.id,
          id毫秒级UTC时间: new Date(item.id).toISOString(),
          id秒级UTC时间: new Date(item.id / 1000).toISOString(),
          openPrice: item.openPrice,
          highPrice: item.highPrice,
          lowPrice: item.lowPrice,
          closePrice: item.closePrice,
        })),
        id字段范围: {
          最小id: Math.min(...klineDatas.map((item) => item.id)),
          最大id: Math.max(...klineDatas.map((item) => item.id)),
          最小id对应的UTC时间: new Date(
            Math.min(...klineDatas.map((item) => item.id)),
          ).toISOString(),
          最大id对应的UTC时间: new Date(
            Math.max(...klineDatas.map((item) => item.id)),
          ).toISOString(),
        },
      });

      // 转换数据格式
      const formattedData = klineDatas.map((item, index) => {
        let timeValue = item.id;

        // 关键修复：数据库中的 id 字段是毫秒级时间戳
        // 需要先转换为秒级时间戳
        if (timeValue > 1e12) {
          timeValue = Math.floor(timeValue / 1000);
        }

        // 只打印前3个数据的转换过程
        if (index < 3) {
          console.log("🔄 单个数据转换:", {
            索引: index,
            原始id: item.id,
            原始idUTC时间: new Date(item.id).toISOString(),
            转换后timeValue: timeValue,
            转换后UTC时间: new Date(timeValue * 1000).toISOString(),
            转换后北京时间: new Date(timeValue * 1000).toLocaleString("zh-CN", {
              timeZone: "Asia/Shanghai",
            }),
          });
        }

        // 关键修复：从数据库查询结果看，id 存储的是 UTC 时间戳（毫秒）
        // FROM_UNIXTIME(id/1000) 显示的是 UTC 时间
        // 例如：id=1754006400000 (毫秒) = 1754006400 (秒) = 2025-08-01 08:00:00 UTC
        // 这说明 id 是 UTC 时间戳，不是 UTC+8 时间戳
        // 所以不需要减去8小时，直接使用即可
        const utcTimeValue = timeValue;

        return {
          time: utcTimeValue, // UTC时间戳（秒）
          open: parseFloat(item.openPrice || item.open || 0),
          high: parseFloat(item.highPrice || item.high || 0),
          low: parseFloat(item.lowPrice || item.low || 0),
          close: parseFloat(item.closePrice || item.close || 0),
        };
      });

      console.log("✅ 数据转换完成:", {
        原始数据量: klineDatas.length,
        转换后数据量: formattedData.length,
        转换后时间范围:
          formattedData.length > 0
            ? {
                第一条: {
                  time: formattedData[0].time,
                  UTC时间: new Date(formattedData[0].time * 1000).toISOString(),
                  北京时间: new Date(
                    formattedData[0].time * 1000,
                  ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
                },
                最后一条: {
                  time: formattedData[formattedData.length - 1].time,
                  UTC时间: new Date(
                    formattedData[formattedData.length - 1].time * 1000,
                  ).toISOString(),
                  北京时间: new Date(
                    formattedData[formattedData.length - 1].time * 1000,
                  ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
                },
              }
            : "无数据",
      });

      console.log("✅ 数据转换完成:", {
        原始数据量: klineDatas.length,
        转换后数据量: formattedData.length,
        转换后时间范围:
          formattedData.length > 0
            ? {
                最早时间戳: formattedData[0].time,
                最晚时间戳: formattedData[formattedData.length - 1].time,
                最早UTC时间: new Date(
                  formattedData[0].time * 1000,
                ).toISOString(),
                最晚UTC时间: new Date(
                  formattedData[formattedData.length - 1].time * 1000,
                ).toISOString(),
                最早北京时间: new Date(
                  formattedData[0].time * 1000,
                ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
                最晚北京时间: new Date(
                  formattedData[formattedData.length - 1].time * 1000,
                ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
              }
            : "无数据",
        样本转换数据: formattedData.slice(0, 3).map((item) => ({
          time: item.time,
          timeUTC: new Date(item.time * 1000).toISOString(),
          timeBeijing: new Date(item.time * 1000).toLocaleString("zh-CN", {
            timeZone: "Asia/Shanghai",
          }),
          open: item.open,
          high: item.high,
          low: item.low,
          close: item.close,
        })),
      });

      return formattedData;
    }

    return [];
  }

  /**
   * 检查并加载目标时间的数据
   * @param {number} targetTime - 目标时间戳（UTC，秒）
   * @param {number} bufferBars - 缓冲区K线数量
   * @returns {Promise<void>}
   */
  async ensureDataForTime(targetTime, bufferBars = 200) {
    // 关键修复：如果还在初次加载状态，直接抛出错误，避免调用 getUnshiftData
    if (this.isInitialLoad) {
      const errorMsg =
        "初次加载中，无法确保历史时间点的数据，请等待初次加载完成";
      console.warn("⚠️ DataManager.ensureDataForTime:", errorMsg, {
        目标时间: new Date(targetTime * 1000).toISOString(),
        是否初次加载: this.isInitialLoad,
      });
      throw new Error(errorMsg);
    }

    // 提前验证目标时间有效性
    const now = Math.floor(Date.now() / 1000);
    const timeDiff = targetTime - now;
    const oneHour = 3600;
    const oneYear = 365 * 24 * 3600;

    // 检查是否是未来时间
    if (timeDiff > oneHour) {
      const daysDiff = Math.floor(timeDiff / (24 * 3600));
      const errorMsg = `目标时间是未来时间（相差${daysDiff}天），无法加载数据`;
      console.error("❌ DataManager.ensureDataForTime:", errorMsg, {
        目标时间: new Date(targetTime * 1000).toISOString(),
        目标时间北京时间: new Date(targetTime * 1000).toLocaleString("zh-CN", {
          timeZone: "Asia/Shanghai",
        }),
        当前时间: new Date(now * 1000).toISOString(),
        当前时间北京时间: new Date(now * 1000).toLocaleString("zh-CN", {
          timeZone: "Asia/Shanghai",
        }),
        时间差: timeDiff,
        时间差天数: daysDiff,
      });
      throw new Error(errorMsg);
    }

    // 检查是否是过久的历史时间（超过1年）
    // 关键修复：对于过久的时间，仍然尝试加载，只给出警告，不阻止
    if (timeDiff < -oneYear) {
      const daysDiff = Math.floor(Math.abs(timeDiff) / (24 * 3600));
      console.warn(
        "⚠️ DataManager.ensureDataForTime: 目标时间过久，但仍会尝试加载",
        {
          目标时间: new Date(targetTime * 1000).toISOString(),
          目标时间北京时间: new Date(targetTime * 1000).toLocaleString(
            "zh-CN",
            { timeZone: "Asia/Shanghai" },
          ),
          当前时间: new Date(now * 1000).toISOString(),
          当前时间北京时间: new Date(now * 1000).toLocaleString("zh-CN", {
            timeZone: "Asia/Shanghai",
          }),
          时间差天数: daysDiff,
        },
      );
      // 对于过久的时间，仍然尝试加载，但给出警告
    }

    const interval = this.getIntervalSeconds();
    const bufferTime = interval * bufferBars;

    const range = {
      from: targetTime - bufferTime,
      to: targetTime + bufferTime,
    };

    console.log("DataManager.ensureDataForTime 开始:", {
      目标时间: new Date(targetTime * 1000).toISOString(),
      目标时间北京时间: new Date(targetTime * 1000).toLocaleString("zh-CN", {
        timeZone: "Asia/Shanghai",
      }),
      时间范围: {
        from: new Date(range.from * 1000).toISOString(),
        to: new Date(range.to * 1000).toISOString(),
      },
      缓冲区条数: bufferBars,
    });

    // 关键修复：先检查目标时间点附近是否有数据（允许一定误差）
    const cachedData = this.getCachedData(range.from, range.to);
    // 允许半间隔的误差来匹配最近的K线
    const hasTargetTimeData = cachedData.some(
      (item) => Math.abs(item.time - targetTime) <= interval / 2,
    );

    console.log("DataManager.ensureDataForTime 检查:", {
      缓存数据量: cachedData.length,
      总缓存大小: this.dataCache.length,
      缓存时间范围:
        this.dataCache.length > 0
          ? {
              最早: new Date(this.dataCache[0].time * 1000).toISOString(),
              最晚: new Date(
                this.dataCache[this.dataCache.length - 1].time * 1000,
              ).toISOString(),
              最早北京时间: new Date(
                this.dataCache[0].time * 1000,
              ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
              最晚北京时间: new Date(
                this.dataCache[this.dataCache.length - 1].time * 1000,
              ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
            }
          : "无数据",
      目标时间是否有数据: hasTargetTimeData,
      目标时间: new Date(targetTime * 1000).toISOString(),
      目标时间北京时间: new Date(targetTime * 1000).toLocaleString("zh-CN", {
        timeZone: "Asia/Shanghai",
      }),
    });

    // 如果目标时间点没有数据，或者缓存数据不足，强制加载
    if (!hasTargetTimeData || cachedData.length < bufferBars * 0.5) {
      console.log(
        "DataManager.ensureDataForTime 需要加载数据（目标时间点无数据或缓存不足）",
      );
      // 强制加载，忽略缓存检查
      await this.getBarsByTimeRange(range.from, range.to, true);

      // 打印初始加载的返回结果时间范围
      const initialLoadedData = this.getCachedData(range.from, range.to);
      console.log("📊 初始加载的返回结果时间范围:", {
        返回数据量: initialLoadedData.length,
        时间范围:
          initialLoadedData.length > 0
            ? {
                第一条时间戳: initialLoadedData[0].time,
                最后一条时间戳:
                  initialLoadedData[initialLoadedData.length - 1].time,
                第一条UTC时间: new Date(
                  initialLoadedData[0].time * 1000,
                ).toISOString(),
                最后一条UTC时间: new Date(
                  initialLoadedData[initialLoadedData.length - 1].time * 1000,
                ).toISOString(),
                第一条北京时间: new Date(
                  initialLoadedData[0].time * 1000,
                ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
                最后一条北京时间: new Date(
                  initialLoadedData[initialLoadedData.length - 1].time * 1000,
                ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
              }
            : "无数据",
        目标时间: new Date(targetTime * 1000).toISOString(),
        目标时间戳: targetTime,
      }); // 添加 forceLoad 参数
    } else {
      console.log("DataManager.ensureDataForTime 使用缓存数据");
    }

    // 验证目标时间点附近是否有数据
    let finalData = this.getCachedData(range.from, range.to);
    let hasFinalData = finalData.some(
      (item) => Math.abs(item.time - targetTime) <= interval / 2,
    );

    console.log("🔍 验证加载后的数据:", {
      验证范围: {
        from: new Date(range.from * 1000).toISOString(),
        to: new Date(range.to * 1000).toISOString(),
      },
      验证数据量: finalData.length,
      目标时间: new Date(targetTime * 1000).toISOString(),
      目标时间戳: targetTime,
      允许误差: interval / 2,
      误差范围: [targetTime - interval / 2, targetTime + interval / 2],
      是否有目标数据: hasFinalData,
      缓存总数据量: this.dataCache.length,
      缓存时间范围:
        this.dataCache.length > 0
          ? {
              最早时间戳: this.dataCache[0].time,
              最晚时间戳: this.dataCache[this.dataCache.length - 1].time,
              最早时间: new Date(this.dataCache[0].time * 1000).toISOString(),
              最晚时间: new Date(
                this.dataCache[this.dataCache.length - 1].time * 1000,
              ).toISOString(),
            }
          : "无缓存数据",
    });

    // 如果没有找到目标数据，打印前几个和后几个数据的时间戳进行调试
    if (!hasFinalData && finalData.length > 0) {
      const firstFew = finalData.slice(0, 5).map((item) => ({
        time: item.time,
        timeISO: new Date(item.time * 1000).toISOString(),
        diff: Math.abs(item.time - targetTime),
      }));
      const lastFew = finalData.slice(-5).map((item) => ({
        time: item.time,
        timeISO: new Date(item.time * 1000).toISOString(),
        diff: Math.abs(item.time - targetTime),
      }));

      console.log("🔍 详细数据检查:", {
        前5个数据点: firstFew,
        后5个数据点: lastFew,
        目标时间戳: targetTime,
        时间戳差异分析: `目标时间戳 ${targetTime} 与数据时间戳的差异`,
      });
    }

    // 如果目标时间点没有数据，尝试扩大范围并多次重试
    if (!hasFinalData) {
      console.warn(
        "⚠️ DataManager.ensureDataForTime: 加载后目标时间点仍无数据，尝试扩大范围",
      );

      // 多次尝试扩大范围
      for (let retry = 1; retry <= 3; retry++) {
        const expandMultiplier = retry * 2; // 2倍、4倍、6倍
        const expandedRange = {
          from: targetTime - bufferTime * expandMultiplier,
          to: targetTime + bufferTime * expandMultiplier,
        };

        console.log(`🔧 第 ${retry} 次扩大范围计算详情:`, {
          扩大倍数: expandMultiplier,
          原始目标时间戳: targetTime,
          原始缓冲时间: bufferTime,
          计算公式: `targetTime ± bufferTime × expandMultiplier`,
          扩大后from计算: `${targetTime} - ${bufferTime} × ${expandMultiplier} = ${expandedRange.from}`,
          扩大后to计算: `${targetTime} + ${bufferTime} × ${expandMultiplier} = ${expandedRange.to}`,
          时间范围跨度秒: expandedRange.to - expandedRange.from,
          时间范围跨度小时: (expandedRange.to - expandedRange.from) / 3600,
        });

        console.log(`🔄 尝试 ${retry}/3: 扩大范围到 ${expandMultiplier} 倍`, {
          from: new Date(expandedRange.from * 1000).toISOString(),
          to: new Date(expandedRange.to * 1000).toISOString(),
          to参数值: expandedRange.to, // 添加to参数值
          目标时间: new Date(targetTime * 1000).toISOString(),
        });

        // 强制加载扩大范围的数据
        console.log(`📡 第 ${retry} 次扩大范围开始调用getBarsByTimeRange:`, {
          from: expandedRange.from,
          to: expandedRange.to,
          forceLoad: true,
        });

        await this.getBarsByTimeRange(
          expandedRange.from,
          expandedRange.to,
          true,
        );

        // 等待数据加载完成
        await new Promise((resolve) => setTimeout(resolve, 300));

        console.log(
          `✅ 第 ${retry} 次扩大范围getBarsByTimeRange调用完成，检查缓存更新:`,
          {
            调用后缓存总数据量: this.dataCache.length,
            缓存时间范围:
              this.dataCache.length > 0
                ? {
                    最早: new Date(this.dataCache[0].time * 1000).toISOString(),
                    最晚: new Date(
                      this.dataCache[this.dataCache.length - 1].time * 1000,
                    ).toISOString(),
                  }
                : "无缓存数据",
          },
        );

        // 重新检查缓存中是否有目标时间点附近的数据
        console.log(`🔍 第 ${retry} 次扩大范围前缓存状态:`, {
          缓存总数据量: this.dataCache.length,
          扩大范围from: expandedRange.from,
          扩大范围to: expandedRange.to,
          缓存时间范围:
            this.dataCache.length > 0
              ? {
                  最早: new Date(this.dataCache[0].time * 1000).toISOString(),
                  最晚: new Date(
                    this.dataCache[this.dataCache.length - 1].time * 1000,
                  ).toISOString(),
                }
              : "无缓存数据",
        });

        finalData = this.getCachedData(expandedRange.from, expandedRange.to);

        console.log(`📊 第 ${retry} 次扩大范围后的getCachedData结果:`, {
          扩大范围: `${expandMultiplier}倍`,
          查询范围from: expandedRange.from,
          查询范围to: expandedRange.to,
          返回数据量: finalData.length,
          查询结果是否为空: finalData.length === 0,
        });

        // 打印扩大范围后的返回结果时间范围
        console.log(`📊 第 ${retry} 次扩大范围后的返回结果时间范围:`, {
          扩大范围: `${expandMultiplier}倍`,
          返回数据量: finalData.length,
          时间范围:
            finalData.length > 0
              ? {
                  第一条时间戳: finalData[0].time,
                  最后一条时间戳: finalData[finalData.length - 1].time,
                  第一条UTC时间: new Date(
                    finalData[0].time * 1000,
                  ).toISOString(),
                  最后一条UTC时间: new Date(
                    finalData[finalData.length - 1].time * 1000,
                  ).toISOString(),
                  第一条北京时间: new Date(
                    finalData[0].time * 1000,
                  ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
                  最后一条北京时间: new Date(
                    finalData[finalData.length - 1].time * 1000,
                  ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
                }
              : "无数据",
          目标时间: new Date(targetTime * 1000).toISOString(),
          目标时间戳: targetTime,
          目标时间北京时间: new Date(targetTime * 1000).toLocaleString(
            "zh-CN",
            { timeZone: "Asia/Shanghai" },
          ),
        });

        // 如果返回数据量为0，说明后端没有该时间范围的数据
        if (finalData.length === 0 && retry === 3) {
          // 最后一次尝试仍然没有数据，提前结束
          console.warn(
            "⚠️ 多次尝试后后端仍返回0条数据，可能该时间范围的数据不存在",
          );
          break;
        }

        hasFinalData = finalData.some(
          (item) => Math.abs(item.time - targetTime) <= interval / 2,
        );

        if (hasFinalData) {
          console.log(`✅ 第 ${retry} 次扩大范围后找到目标时间点数据`);
          break;
        } else {
          console.warn(`⚠️ 第 ${retry} 次扩大范围后仍未找到目标时间点数据`);

          // 检查是否至少加载了一些数据
          if (finalData.length > 0) {
            const dataTimeRange = {
              earliest: finalData[0].time,
              latest: finalData[finalData.length - 1].time,
            };
            console.log("📊 已加载的数据时间范围:", {
              最早: new Date(dataTimeRange.earliest * 1000).toISOString(),
              最晚: new Date(dataTimeRange.latest * 1000).toISOString(),
              目标时间: new Date(targetTime * 1000).toISOString(),
              目标时间在范围内:
                targetTime >= dataTimeRange.earliest &&
                targetTime <= dataTimeRange.latest,
            });

            // 如果目标时间在已加载的数据范围内，但时间戳不完全匹配
            // 可能是时间戳格式问题，尝试查找最接近的时间点
            if (
              targetTime >= dataTimeRange.earliest &&
              targetTime <= dataTimeRange.latest
            ) {
              // 🎯 修复：使用外部已定义的 interval 变量，避免重复定义
              const tolerance = interval / 2; // 允许半个周期的时间误差
              const closestData = finalData.find(
                (item) => Math.abs(item.time - targetTime) <= tolerance,
              );

              if (closestData) {
                console.log(
                  "✅ 找到最接近目标时间的K线数据（时间戳不完全匹配）",
                  {
                    目标时间: new Date(targetTime * 1000).toISOString(),
                    最接近时间: new Date(closestData.time * 1000).toISOString(),
                    时间差秒: Math.abs(closestData.time - targetTime),
                  },
                );
                // 虽然时间戳不完全匹配，但找到了最接近的数据，可以继续
                break;
              }
            }
          }
        }
      }

      // 最终验证 - 允许时间戳近似匹配
      // 🎯 修复：使用外部已定义的 interval 变量，避免重复定义
      finalData = this.getCurrentCache();
      hasFinalData = finalData.some(
        (item) => Math.abs(item.time - targetTime) <= interval / 2,
      );

      if (!hasFinalData) {
        // 检查目标时间是否是未来时间
        const now = Math.floor(Date.now() / 1000);
        const timeDiff = targetTime - now;
        const oneHour = 3600;
        const oneYear = 365 * 24 * 3600;

        let errorMsg = "多次尝试后仍无法获取目标时间点附近的数据";
        let errorType = "UNKNOWN";

        if (timeDiff > oneHour) {
          const daysDiff = Math.floor(timeDiff / (24 * 3600));
          errorMsg = `目标时间是未来时间（相差${daysDiff}天），无法加载数据`;
          errorType = "FUTURE_TIME";
        } else if (finalData.length === 0) {
          // 如果多次尝试后仍然没有加载到任何数据
          const daysDiff = Math.floor(Math.abs(timeDiff) / (24 * 3600));
          if (timeDiff < -oneYear) {
            // 时间过久且没有数据
            errorMsg = `目标时间过久（${daysDiff}天前），且未找到可用数据，数据可能已过期`;
            errorType = "TOO_OLD_NO_DATA";
          } else {
            errorMsg = `目标时间点附近没有可用数据（可能数据不存在或已过期）`;
            errorType = "NO_DATA";
          }
        } else {
          // 有数据但时间点不匹配
          const daysDiff = Math.floor(Math.abs(timeDiff) / (24 * 3600));
          errorMsg = `目标时间点附近没有匹配的K线数据（相差${daysDiff}天）`;
          errorType = "NO_MATCH";
        }

        console.error("❌ DataManager.ensureDataForTime:", errorMsg, {
          错误类型: errorType,
          目标时间: new Date(targetTime * 1000).toISOString(),
          目标时间北京时间: new Date(targetTime * 1000).toLocaleString(
            "zh-CN",
            { timeZone: "Asia/Shanghai" },
          ),
          当前时间: new Date(now * 1000).toISOString(),
          当前时间北京时间: new Date(now * 1000).toLocaleString("zh-CN", {
            timeZone: "Asia/Shanghai",
          }),
          时间差:
            timeDiff > 0
              ? `未来${Math.floor(timeDiff / 3600)}小时`
              : `过去${Math.floor(Math.abs(timeDiff) / 3600)}小时`,
          时间差天数: Math.floor(Math.abs(timeDiff) / (24 * 3600)),
          缓存数据量: finalData.length,
          缓存时间范围:
            finalData.length > 0
              ? {
                  最早: new Date(finalData[0].time * 1000).toISOString(),
                  最晚: new Date(
                    finalData[finalData.length - 1].time * 1000,
                  ).toISOString(),
                  最早北京时间: new Date(
                    finalData[0].time * 1000,
                  ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
                  最晚北京时间: new Date(
                    finalData[finalData.length - 1].time * 1000,
                  ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
                }
              : "无数据",
          建议:
            errorType === "TOO_OLD_NO_DATA"
              ? "该时间点的数据可能已过期，请选择更近的时间"
              : errorType === "NO_DATA"
                ? "请检查数据源是否包含该时间范围的数据"
                : errorType === "FUTURE_TIME"
                  ? "无法加载未来时间的数据"
                  : "请尝试选择其他时间点",
        });

        // 创建一个包含错误类型的错误对象
        const error = new Error(errorMsg);
        error.errorType = errorType;
        error.targetTime = targetTime;
        error.timeDiff = timeDiff;
        throw error;
      } else {
        console.log("✅ DataManager.ensureDataForTime: 成功获取目标时间点数据");
      }
    }
  }

  /**
   * 获取缓存中的数据
   * @param {number} from - 开始时间
   * @param {number} to - 结束时间
   * @returns {Array} 缓存的数据
   */
  getCachedData(from, to) {
    return this.dataCache.filter(
      (item) => item.time >= from && item.time <= to,
    );
  }

  /**
   * 检查时间范围是否完全在缓存中
   */
  isRangeFullyCached(from, to, cachedData) {
    if (!cachedData || cachedData.length === 0) {
      return false;
    }

    // 检查缓存数据是否覆盖了请求的范围
    const cachedTimes = cachedData.map((d) => d.time).sort((a, b) => a - b);
    const firstCached = cachedTimes[0];
    const lastCached = cachedTimes[cachedTimes.length - 1];

    return firstCached <= from && lastCached >= to;
  }

  /**
   * 计算缺失的时间范围
   */
  calculateMissingRanges(from, to, cachedData) {
    if (!cachedData || cachedData.length === 0) {
      return [{ from, to }];
    }

    const interval = this.getIntervalSeconds();
    const cachedTimes = cachedData.map((d) => d.time).sort((a, b) => a - b);
    const missingRanges = [];

    // 检查开始部分
    if (from < cachedTimes[0]) {
      missingRanges.push({ from, to: Math.min(to, cachedTimes[0] - interval) });
    }

    // 关键修复：检查中间的空隙，使用间隔来判断是否连续
    for (let i = 0; i < cachedTimes.length - 1; i++) {
      const currentTime = cachedTimes[i];
      const nextTime = cachedTimes[i + 1];
      const gap = nextTime - currentTime;

      // 如果间隔超过1个周期，说明中间有缺失的K线
      if (gap > interval * 1.5) {
        // 计算缺失范围的开始和结束
        const gapStart = currentTime + interval;
        const gapEnd = nextTime - interval;

        // 只加载在请求范围内的空隙
        if (gapStart <= gapEnd && gapStart <= to && gapEnd >= from) {
          missingRanges.push({
            from: Math.max(gapStart, from),
            to: Math.min(gapEnd, to),
          });

          console.log(
            `🔍 检测到数据空隙: ${new Date(gapStart * 1000).toISOString()} -> ${new Date(gapEnd * 1000).toISOString()}, 间隔: ${gap}秒 (${gap / interval}个周期)`,
          );
        }
      }
    }

    // 检查结束部分
    if (to > cachedTimes[cachedTimes.length - 1]) {
      missingRanges.push({
        from: Math.max(from, cachedTimes[cachedTimes.length - 1] + interval),
        to,
      });
    }

    return missingRanges;
  }

  /**
   * 合并数据
   */
  mergeData(existingData, newData) {
    console.log("🔀 mergeData 开始合并:", {
      existingData长度: existingData.length,
      newData长度: newData.length,
      existingData时间范围:
        existingData.length > 0
          ? {
              最早: new Date(existingData[0].time * 1000).toISOString(),
              最晚: new Date(
                existingData[existingData.length - 1].time * 1000,
              ).toISOString(),
            }
          : "无数据",
      newData时间范围:
        newData.length > 0
          ? {
              最早: new Date(newData[0].time * 1000).toISOString(),
              最晚: new Date(
                newData[newData.length - 1].time * 1000,
              ).toISOString(),
            }
          : "无数据",
    });

    // 合并数据
    const allData = [...existingData, ...newData];

    // 按时间排序
    allData.sort((a, b) => a.time - b.time);

    // 去重（相同时间的K线只保留一个，保留最新的数据）
    const uniqueData = [];
    const seenTimes = new Map(); // 使用 Map 来保留最新的数据

    for (const item of allData) {
      if (!seenTimes.has(item.time)) {
        seenTimes.set(item.time, item);
      } else {
        // 如果已存在，比较时间戳，保留更新的（通常新数据更准确）
        const existing = seenTimes.get(item.time);
        // 这里假设新数据更准确，直接替换
        seenTimes.set(item.time, item);
      }
    }

    // 转换为数组并重新排序
    const result = Array.from(seenTimes.values()).sort(
      (a, b) => a.time - b.time,
    );

    console.log("🔀 mergeData 合并完成:", {
      合并前总数据量: existingData.length + newData.length,
      合并后数据量: result.length,
      去重数量: existingData.length + newData.length - result.length,
      合并结果时间范围:
        result.length > 0
          ? {
              最早: new Date(result[0].time * 1000).toISOString(),
              最晚: new Date(
                result[result.length - 1].time * 1000,
              ).toISOString(),
              最早北京时间: new Date(result[0].time * 1000).toLocaleString(
                "zh-CN",
                { timeZone: "Asia/Shanghai" },
              ),
              最晚北京时间: new Date(
                result[result.length - 1].time * 1000,
              ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
            }
          : "无数据",
    });

    // 关键修复：检查合并后的数据是否有空隙
    if (result.length > 1) {
      const interval = this.getIntervalSeconds();
      const gaps = [];

      for (let i = 0; i < result.length - 1; i++) {
        const gap = result[i + 1].time - result[i].time;
        if (gap > interval * 1.5) {
          gaps.push({
            from: result[i].time,
            to: result[i + 1].time,
            gap: gap,
            missingBars: Math.floor(gap / interval) - 1,
          });
        }
      }

      if (gaps.length > 0) {
        console.warn(
          `⚠️ 数据合并后检测到 ${gaps.length} 个空隙:`,
          gaps.map((g) => ({
            from: new Date(g.from * 1000).toISOString(),
            to: new Date(g.to * 1000).toISOString(),
            缺失K线数: g.missingBars,
          })),
        );
      }
    }

    return result;
  }

  /**
   * 更新缓存
   */
  /**
   * 更新缓存（用于外部直接设置数据，如初次加载）
   * @param {Array} data - K线数据数组
   * @param {boolean} isInitial - 是否是初次加载
   */
  updateCache(data, isInitial = false) {
    // 关键修复：确保新数据正确合并到缓存
    const mergedData = this.mergeData(this.dataCache, data);

    // 关键修复：检查合并后的数据是否连续，如果有空隙则记录警告
    if (mergedData.length > 1) {
      const sortedData = [...mergedData].sort((a, b) => a.time - b.time);
      const interval = this.getIntervalSeconds();

      for (let i = 0; i < sortedData.length - 1; i++) {
        const gap = sortedData[i + 1].time - sortedData[i].time;
        // 如果间隔超过2个周期，记录警告（但不阻止更新）
        if (gap > interval * 2) {
          console.warn(
            `⚠️ 数据更新后检测到空隙: ${new Date(sortedData[i].time * 1000).toISOString()} -> ${new Date(sortedData[i + 1].time * 1000).toISOString()}, 间隔: ${gap}秒`,
          );
        }
      }
    }

    this.dataCache = mergedData;

    // 如果是初次加载，延迟设置标志，确保图表完全渲染完成后再允许历史数据加载
    if (isInitial && this.isInitialLoad) {
      // 延迟2秒设置标志，确保初次加载完全完成，避免可见范围监听器触发历史数据加载
      setTimeout(() => {
        if (this.isInitialLoad) {
          this.isInitialLoad = false;
          console.log(
            "✅ 初次数据已通过 updateCache 设置，延迟2秒后 isInitialLoad 已设置为 false",
          );
        }
      }, 2000); // 延迟2秒，与 MarketData.vue 中的延迟时间保持一致
    }

    console.log("✅ DataManager.updateCache: 缓存已更新", {
      更新前数据量: this.dataCache.length - data.length,
      新增数据量: data.length,
      更新后总数据量: this.dataCache.length,
      缓存时间范围:
        this.dataCache.length > 0
          ? {
              最早: new Date(this.dataCache[0].time * 1000).toISOString(),
              最晚: new Date(
                this.dataCache[this.dataCache.length - 1].time * 1000,
              ).toISOString(),
              最早北京时间: new Date(
                this.dataCache[0].time * 1000,
              ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
              最晚北京时间: new Date(
                this.dataCache[this.dataCache.length - 1].time * 1000,
              ).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }),
            }
          : "无数据",
    });

    // 🎯 性能优化：如果数据量超过阈值，自动清理数据
    if (this.dataCache.length > this.cleanupThreshold) {
      console.warn(
        `⚠️ 数据量超过阈值 (${this.dataCache.length} > ${this.cleanupThreshold})，开始自动清理`,
      );

      // 自动清理：保留最新的数据，清理旧数据
      // 保留最近 maxDataPoints 条数据
      if (this.dataCache.length > this.maxDataPoints) {
        // 按时间排序
        const sortedData = [...this.dataCache].sort((a, b) => a.time - b.time);

        // 保留最新的 maxDataPoints 条数据
        const beforeCount = sortedData.length;
        this.dataCache = sortedData.slice(-this.maxDataPoints);
        const afterCount = this.dataCache.length;
        const removedCount = beforeCount - afterCount;

        console.log(
          `✅ 数据自动清理完成: 清理前 ${beforeCount} 条，清理后 ${afterCount} 条，清理了 ${removedCount} 条`,
        );
      }
    }
  }

  /**
   * 滑动窗口数据管理
   * 只保留可见范围 + 缓冲区范围的数据
   * @param {object} visibleRange - 可见时间范围 { from: number, to: number }
   */
  applySlidingWindow(visibleRange) {
    if (!visibleRange || !this.dataCache || this.dataCache.length === 0) {
      return;
    }

    const currentDataCount = this.dataCache.length;

    // 如果数据量在合理范围内，不需要清理
    if (currentDataCount <= this.maxDataPoints) {
      return;
    }

    // 计算需要保留的时间范围
    const rangeLength = visibleRange.to - visibleRange.from;
    const bufferTime = rangeLength * this.bufferRatio;

    const keepRange = {
      from: visibleRange.from - bufferTime / 2,
      to: visibleRange.to + bufferTime / 2,
    };

    // 关键修复：获取最新的数据时间，确保不清理最新的实时数据
    const latestDataTime = Math.max(...this.dataCache.map((item) => item.time));
    const currentTime = Math.floor(Date.now() / 1000);
    const interval = this.getIntervalSeconds();

    // 如果最新数据是最近3个周期内的，扩大保留范围，确保不清理实时数据
    if (latestDataTime >= currentTime - interval * 3) {
      // 至少保留到最新数据之后1个周期
      keepRange.to = Math.max(keepRange.to, latestDataTime + interval);
    }

    // 过滤数据：只保留范围内的数据
    const beforeCount = this.dataCache.length;
    const beforeData = [...this.dataCache];
    this.dataCache = this.dataCache.filter((item) => {
      return item.time >= keepRange.from && item.time <= keepRange.to;
    });

    const afterCount = this.dataCache.length;
    const removedCount = beforeCount - afterCount;

    if (removedCount > 0) {
      // 关键修复：检查清理后的数据是否连续
      const sortedData = [...this.dataCache].sort((a, b) => a.time - b.time);
      let hasGap = false;

      for (let i = 0; i < sortedData.length - 1; i++) {
        const gap = sortedData[i + 1].time - sortedData[i].time;
        if (gap > interval * 2) {
          hasGap = true;
          console.warn(
            `⚠️ 数据清理后检测到空隙: ${new Date(sortedData[i].time * 1000).toISOString()} -> ${new Date(sortedData[i + 1].time * 1000).toISOString()}, 间隔: ${gap}秒`,
          );
        }
      }

      console.log(
        `数据清理: 保留 ${afterCount} 条，清理 ${removedCount} 条`,
        hasGap ? "(检测到空隙)" : "(数据连续)",
      );
    }
  }

  /**
   * 获取当前缓存的所有数据
   */
  getCurrentCache() {
    return [...this.dataCache];
  }

  /**
   * 清除缓存
   */
  clearCache() {
    this.dataCache = [];
  }

  /**
   * 更新配置
   */
  updateConfig(options) {
    if (options.symbol !== undefined) this.symbol = options.symbol;
    if (options.interval !== undefined) this.interval = options.interval;
    if (options.memberId !== undefined) this.memberId = options.memberId;
    if (options.accountId !== undefined) this.accountId = options.accountId;
    if (options.memberPlatform !== undefined)
      this.memberPlatform = options.memberPlatform;
    if (options.indicatorType !== undefined)
      this.indicatorType = options.indicatorType;

    // 切换配置时清除缓存
    this.clearCache();
  }
}
