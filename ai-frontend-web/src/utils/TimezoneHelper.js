/**
 * 时区处理工具类
 * 用于处理 UTC+8（北京时间）与 UTC 时间戳之间的转换
 *
 * 参考文档: https://tradingview.github.io/lightweight-charts/docs/api
 */

export class TimezoneHelper {
  /**
   * 将 UTC+8 时间字符串转换为 UTC 时间戳（秒）
   * @param {string} utc8TimeString - UTC+8 时间字符串，格式：'2025-01-01 00:00:00'
   * @returns {number} UTC 时间戳（秒）
   * @throws {Error} 如果时间字符串格式无效
   */
  static utc8ToTimestamp(utc8TimeString) {
    if (!utc8TimeString || typeof utc8TimeString !== "string") {
      throw new Error(`无效的时间字符串: ${utc8TimeString}`);
    }

    // 将 '2025-01-01 00:00:00' 转换为 ISO 格式 '2025-01-01T00:00:00+08:00'
    const isoString = utc8TimeString.replace(" ", "T") + "+08:00";
    const date = new Date(isoString);

    if (isNaN(date.getTime())) {
      throw new Error(`无效的时间字符串: ${utc8TimeString}`);
    }

    // 返回 UTC 时间戳（秒）
    return Math.floor(date.getTime() / 1000);
  }

  /**
   * 将 UTC 时间戳转换为 UTC+8 时间字符串
   * @param {number} timestamp - UTC 时间戳（秒）
   * @returns {string} UTC+8 时间字符串，格式：'2025-01-01 00:00:00'
   */
  static timestampToUTC8(timestamp) {
    if (!timestamp || typeof timestamp !== "number") {
      return "";
    }

    const date = new Date(timestamp * 1000);

    // 转换为 UTC+8 时间
    const utc8String = date.toLocaleString("zh-CN", {
      timeZone: "Asia/Shanghai",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });

    // 将 '/' 替换为 '-'，统一格式
    return utc8String.replace(/\//g, "-");
  }

  /**
   * 获取时间轴的格式化函数（用于 tickMarkFormatter）
   * 参考: https://tradingview.github.io/lightweight-charts/docs/api/interfaces/TimeScaleOptions#tickmarkformatter
   *
   * 注意：后端返回的K线时间戳已经是 UTC+8（北京时间）的时间戳
   * 需要减去8小时（28800秒）得到UTC时间戳，然后用UTC方法格式化显示为北京时间
   *
   * @returns {function} tickMarkFormatter 函数
   */
  static getTickMarkFormatter() {
    return (time, tickMarkType, locale) => {
      // time: 时间戳（秒级）
      // time 是 UTC 时间戳，将其转换为北京时间进行格式化
      const date = new Date(time * 1000);

      // 时间轴只显示时分（不显示日期）
      // 使用北京时区格式化
      const beijingTime = date.toLocaleString("zh-CN", {
        timeZone: "Asia/Shanghai",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      });

      return beijingTime;
    };
  }

  /**
   * 规范化时间戳（毫秒转秒）
   * @param {number} timestamp - 时间戳（可能是毫秒或秒）
   * @returns {number} 秒级时间戳
   */
  static normalizeTimestamp(timestamp) {
    if (!timestamp || typeof timestamp !== "number") {
      return 0;
    }
    // 如果是毫秒级（> 1e12），转换为秒级
    return timestamp > 1e12 ? Math.floor(timestamp / 1000) : timestamp;
  }

  /**
   * 格式化时间戳为可读字符串（UTC+8）
   * @param {number} timestamp - UTC 时间戳（秒）
   * @param {object} options - 格式化选项
   * @returns {string} 格式化后的时间字符串
   */
  static formatTimestamp(timestamp, options = {}) {
    const {
      includeSeconds = false,
      includeDate = true,
      includeTime = true,
    } = options;

    const date = new Date(timestamp * 1000);
    const formatOptions = {
      timeZone: "Asia/Shanghai",
    };

    if (includeDate) {
      formatOptions.year = "numeric";
      formatOptions.month = "2-digit";
      formatOptions.day = "2-digit";
    }

    if (includeTime) {
      formatOptions.hour = "2-digit";
      formatOptions.minute = "2-digit";
      if (includeSeconds) {
        formatOptions.second = "2-digit";
      }
    }

    return date.toLocaleString("zh-CN", formatOptions).replace(/\//g, "-");
  }
}
