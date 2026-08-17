/**
 * 技术指标计算
 * 用于市场行情 K 线图：MACD 副图（panes）
 */

export interface OHLCItem {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
}

export interface LineIndicatorPoint {
  time: number;
  value: number;
}

/** MACD 单点 */
export interface MACDPoint {
  time: number;
  macd: number;
  signal: number;
  histogram: number;
}

/** BOLL 单点（布林带：中轨、上轨、下轨） */
export interface BOLLPoint {
  time: number;
  middle: number;
  upper: number;
  lower: number;
}

/**
 * BOLL 布林带：中轨 = SMA(close, period)，上下轨 = 中轨 ± multiplier * stdDev(close, period)
 * 返回从 period-1 起的数据点
 */
export function calculateBoll(
  data: OHLCItem[],
  period = 20,
  multiplier = 2,
): BOLLPoint[] {
  if (!Array.isArray(data) || data.length < period) return [];
  const result: BOLLPoint[] = [];
  for (let i = period - 1; i < data.length; i++) {
    let sum = 0;
    for (let j = i - period + 1; j <= i; j++) {
      sum += data[j].close;
    }
    const ma = sum / period;
    let variance = 0;
    for (let j = i - period + 1; j <= i; j++) {
      variance += Math.pow(data[j].close - ma, 2);
    }
    const stdDev = Math.sqrt(variance / period);
    result.push({
      time: data[i].time,
      middle: ma,
      upper: ma + multiplier * stdDev,
      lower: ma - multiplier * stdDev,
    });
  }
  return result;
}

/**
 * 卡尔曼滤波（超级趋势用）kalman_filter(src, length, R=0.01, Q=0.1)
 * 返回与 data 同长的估计值数组
 */
export function kalmanFilter(data: OHLCItem[], len: number): number[] {
  if (!data?.length) return [];
  const R = 0.01;
  const Q = 0.1;
  const result: number[] = [];
  let estimate: number | null = null;
  let errorEst = 1.0;
  const errorMeas = R * len;
  let kalmanGain = 0.0;

  for (let i = 0; i < data.length; i++) {
    const src = data[i].close;
    if (estimate === null) {
      estimate = i > 0 ? data[i - 1].close : src;
    }
    const prediction = estimate;
    kalmanGain = errorEst / (errorEst + errorMeas);
    estimate = prediction + kalmanGain * (src - prediction);
    errorEst = (1 - kalmanGain) * errorEst + Q / len;
    result.push(estimate);
  }
  return result;
}

/**
 * 对数值数组计算 EMA，返回与输入同长的数组，首项为 price[0]
 * 用于反转确认等需要逐点 EMA 的指标
 */
export function emaArray(values: number[], period: number): (number | null)[] {
  if (!values?.length || period <= 0) return [];
  const result: (number | null)[] = [];
  const k = 2 / (period + 1);
  for (let i = 0; i < values.length; i++) {
    const v = values[i];
    if (typeof v !== "number" || isNaN(v)) {
      result[i] = null;
    } else if (i === 0) {
      result[i] = v;
    } else {
      const prev = result[i - 1];
      if (prev == null) result[i] = v;
      else result[i] = v * k + prev * (1 - k);
    }
  }
  return result;
}

/**
 * 计算 ATR(period)，返回与 data 同长数组，前 period 项为 null
 */
export function atrArray(data: OHLCItem[], period: number): (number | null)[] {
  if (!data?.length || data.length < period + 1) return [];
  const tr: number[] = [];
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
  const atr: (number | null)[] = [];
  for (let i = 0; i < period; i++) atr.push(null);
  let sum = 0;
  for (let i = 0; i < period; i++) sum += tr[i];
  atr.push(sum / period);
  const mult = 1 / period;
  for (let i = period + 1; i < data.length; i++) {
    atr.push(atr[i - 1]! * (1 - mult) + tr[i - 1] * mult);
  }
  return atr;
}

/**
 * 简单移动平均 SMA(values, period)，返回与输入同长数组，前 period-1 项为 null
 */
export function smaArray(values: number[], period: number): (number | null)[] {
  if (!values?.length || period <= 0) return [];
  const result: (number | null)[] = [];
  for (let i = 0; i < values.length; i++) {
    if (i < period - 1) {
      result.push(null);
    } else {
      let sum = 0;
      for (let j = i - period + 1; j <= i; j++) {
        const v = values[j];
        if (typeof v === "number" && !isNaN(v)) sum += v;
      }
      result.push(sum / period);
    }
  }
  return result;
}

/**
 * 加权移动平均 WMA(values, period)，权重 1,2,...,period，返回与输入同长，前 period-1 项为 null
 */
export function wmaArray(values: number[], period: number): (number | null)[] {
  if (!values?.length || period <= 0) return [];
  const result: (number | null)[] = [];
  let weightSum = 0;
  for (let w = 1; w <= period; w++) weightSum += w;
  for (let i = 0; i < values.length; i++) {
    if (i < period - 1) {
      result.push(null);
    } else {
      let weightedSum = 0;
      for (let j = 0; j < period; j++) {
        const v = values[i - j];
        if (typeof v === "number" && !isNaN(v)) weightedSum += v * (period - j);
      }
      result.push(weightedSum / weightSum);
    }
  }
  return result;
}

/**
 * Hull 移动平均 HMA(values, period)：2*WMA(n/2) - WMA(n)，再对结果做 WMA(sqrt(n))
 */
export function hmaArray(values: number[], period: number): (number | null)[] {
  if (!values?.length || period <= 0) return [];
  const half = Math.floor(period / 2);
  const sqrtPeriod = Math.max(1, Math.round(Math.sqrt(period)));
  if (half < 1) return [];
  const wma1 = wmaArray(values, half);
  const wma2 = wmaArray(values, period);
  const diff: (number | null)[] = [];
  for (let i = 0; i < values.length; i++) {
    const w1 = wma1[i];
    const w2 = wma2[i];
    if (w1 != null && w2 != null) diff.push(2 * w1 - w2);
    else diff.push(null);
  }
  const numericDiff = diff.map((d) => d ?? 0);
  const wma3 = wmaArray(numericDiff, sqrtPeriod);
  return wma3;
}

/**
 * 按类型计算移动平均（流动性指标等用）
 * RMA 使用 SMA 近似（原 LightweightChart 中 RMA 为滑动平均）
 */
export function maByType(
  type: "SMA" | "EMA" | "HMA" | "RMA",
  values: number[],
  period: number,
): (number | null)[] {
  if (!values?.length || period <= 0) return [];
  switch (type) {
    case "SMA":
      return smaArray(values, period);
    case "EMA":
      return emaArray(values, period);
    case "HMA":
      return hmaArray(values, period);
    case "RMA":
      return smaArray(values, period);
    default:
      return emaArray(values, period);
  }
}

/**
 * 标准差 stdDev(close, period)，需配合 smaValues 使用，返回与 data 同长，前 period-1 项为 null
 */
export function stdDevArray(
  data: OHLCItem[],
  smaValues: (number | null)[],
  period: number,
): (number | null)[] {
  if (!data?.length || !smaValues?.length || data.length < period) return [];
  const result: (number | null)[] = [];
  for (let i = 0; i < period - 1; i++) result.push(null);
  for (let i = period - 1; i < data.length; i++) {
    if (smaValues[i] == null) {
      result.push(null);
      continue;
    }
    let variance = 0;
    for (let j = i - period + 1; j <= i; j++) {
      variance += Math.pow(data[j].close - smaValues[i]!, 2);
    }
    result.push(Math.sqrt(variance / period));
  }
  return result;
}

/**
 * 对数值数组计算 EMA（用于 MACD 内部）
 */
function emaValues(values: number[], period: number): number[] {
  if (!Array.isArray(values) || values.length < period || period <= 0)
    return [];
  const k = 2 / (period + 1);
  const result: number[] = [];
  let sum = 0;
  for (let i = 0; i < period; i++) {
    sum += values[i];
  }
  let prevEma = sum / period;
  for (let i = 0; i < period - 1; i++) result.push(0);
  result.push(prevEma);
  for (let i = period; i < values.length; i++) {
    const ema = (values[i] - prevEma) * k + prevEma;
    result.push(ema);
    prevEma = ema;
  }
  return result;
}

/**
 * MACD：快线 EMA(12) - 慢线 EMA(26)，信号线 EMA(MACD, 9)，柱状图 = MACD - 信号线
 * 返回从 slowPeriod-1 起的数据点
 */
export function calculateMACD(
  data: OHLCItem[],
  fastPeriod = 12,
  slowPeriod = 26,
  signalPeriod = 9,
): MACDPoint[] {
  if (!Array.isArray(data) || data.length < slowPeriod) return [];
  const closes = data.map((d) => d.close);
  const emaFast = emaValues(closes, fastPeriod);
  const emaSlow = emaValues(closes, slowPeriod);

  const macdLine: number[] = [];
  for (let i = 0; i < closes.length; i++) {
    if (i >= slowPeriod - 1) {
      macdLine.push(emaFast[i] - emaSlow[i]);
    } else {
      macdLine.push(0);
    }
  }

  const signalLine = emaValues(macdLine, signalPeriod);
  const result: MACDPoint[] = [];

  for (let i = slowPeriod - 1; i < data.length; i++) {
    const hist = macdLine[i] - signalLine[i];
    result.push({
      time: data[i].time,
      macd: macdLine[i],
      signal: signalLine[i],
      histogram: hist,
    });
  }
  return result;
}

/**
 * RSI(period)：相对强弱指标，用于流动性指标信号过滤等
 * 返回与 closes 同长，前 period 项为 null
 */
export function rsiArray(closes: number[], period = 14): (number | null)[] {
  if (!closes?.length || closes.length < period + 1 || period <= 0) return [];
  const result: (number | null)[] = [];
  for (let i = 0; i < period; i++) result.push(null);
  let avgGain = 0;
  let avgLoss = 0;
  for (let i = 1; i <= period; i++) {
    const change = closes[i] - closes[i - 1];
    if (change > 0) avgGain += change;
    else avgLoss -= change;
  }
  avgGain /= period;
  avgLoss /= period;
  for (let i = period; i < closes.length; i++) {
    const change = i > 0 ? closes[i] - closes[i - 1] : 0;
    const gain = change > 0 ? change : 0;
    const loss = change < 0 ? -change : 0;
    avgGain = (avgGain * (period - 1) + gain) / period;
    avgLoss = (avgLoss * (period - 1) + loss) / period;
    const rs = avgLoss === 0 ? (avgGain > 0 ? Infinity : 0) : avgGain / avgLoss;
    const rsi = avgLoss === 0 ? 100 : 100 - 100 / (1 + rs);
    result.push(rsi);
  }
  return result;
}

/** Range Filter 配置（与旧版 LightweightChart 对齐） */
export interface RangeFilterConfig {
  filterType: "Type 1" | "Type 2";
  movementSource: "Close" | "Wicks";
  rangeSize: number;
  rangeScale:
    | "Average Change"
    | "ATR"
    | "Standard Deviation"
    | "% of Price"
    | "Points";
  rangePeriod: number;
  showSignals?: boolean;
}

export interface RangeFilterResult {
  filter: { time: number; value: number }[];
  hiBand: { time: number; value: number }[];
  loBand: { time: number; value: number }[];
  signals: { time: number; type: "BUY" | "SELL" }[];
}

/**
 * 单点 Range Filter 值计算（Type 1 / Type 2）
 */
function rangeFilterValue(
  h: number,
  l: number,
  range: number,
  currentFilt: number,
  filterType: "Type 1" | "Type 2",
): number {
  let newFilt = currentFilt;
  if (filterType === "Type 1") {
    if (h - range > currentFilt) newFilt = h - range;
    if (l + range < newFilt) newFilt = l + range;
  } else {
    if (h >= currentFilt + range) {
      newFilt =
        currentFilt + Math.floor(Math.abs(h - currentFilt) / range) * range;
    }
    if (l <= newFilt - range) {
      newFilt = newFilt - Math.floor(Math.abs(newFilt - l) / range) * range;
    }
  }
  return newFilt;
}

/**
 * Range Filter 指标：滤波器线 + 上下轨 + BUY/SELL 信号
 */
export function calculateRangeFilter(
  data: OHLCItem[],
  config: RangeFilterConfig,
): RangeFilterResult {
  const result: RangeFilterResult = {
    filter: [],
    hiBand: [],
    loBand: [],
    signals: [],
  };
  if (!data?.length || data.length < 2) return result;

  const closes = data.map((d) => d.close);
  const atrArr = atrArray(data, config.rangePeriod);
  const smaArr = smaArray(closes, config.rangePeriod);
  const stdArr = stdDevArray(data, smaArr, config.rangePeriod);

  const getRange = (index: number): number => {
    if (index < config.rangePeriod) return config.rangeSize;
    switch (config.rangeScale) {
      case "Average Change": {
        const start = Math.max(0, index - config.rangePeriod + 1);
        let sum = 0;
        for (let i = start; i <= index; i++) {
          if (i > 0) sum += Math.abs(data[i].close - data[i - 1].close);
        }
        return (sum / Math.max(1, index - start + 1)) * config.rangeSize;
      }
      case "ATR": {
        const a = atrArr[index];
        return a != null ? a * config.rangeSize : config.rangeSize;
      }
      case "Standard Deviation": {
        const s = stdArr[index];
        return s != null ? s * config.rangeSize : config.rangeSize;
      }
      case "% of Price":
        return data[index].close * (config.rangeSize / 100);
      case "Points":
      default:
        return config.rangeSize;
    }
  };

  let filt = data[0].close;
  let condIni = 0;

  for (let i = 0; i < data.length; i++) {
    const current = data[i];
    const h = config.movementSource === "Wicks" ? current.high : current.close;
    const l = config.movementSource === "Wicks" ? current.low : current.close;
    const range = getRange(i);
    filt = rangeFilterValue(h, l, range, filt, config.filterType);

    result.filter.push({ time: current.time, value: filt });
    result.hiBand.push({ time: current.time, value: filt + range });
    result.loBand.push({ time: current.time, value: filt - range });

    if (i >= 1) {
      const prevFilter = result.filter[i - 1].value;
      const currFilter = filt;
      const upward = currFilter > prevFilter ? 1 : 0;
      const downward = currFilter < prevFilter ? 1 : 0;
      const longCond = current.close > currFilter && upward > 0;
      const shortCond = current.close < currFilter && downward > 0;
      const newCondIni = longCond ? 1 : shortCond ? -1 : condIni;
      const longCondition = longCond && condIni === -1;
      const shortCondition = shortCond && condIni === 1;
      if (longCondition)
        result.signals.push({ time: current.time, type: "BUY" });
      else if (shortCondition)
        result.signals.push({ time: current.time, type: "SELL" });
      condIni = newCondIni;
    }
  }
  return result;
}

/**
 * 反转后趋势强度（按原始 Pine Script 逻辑）
 * 返回与 data 同长的 value 数组
 */
export function calculateTrendStrengthAfterReversal(
  data: OHLCItem[],
): number[] {
  if (!data?.length || data.length < 3) return [];

  const insideBar = (h1: number, l1: number, h2: number, l2: number) =>
    h2 >= h1 && l2 <= l1;
  const values: number[] = [];
  let value = 0;
  let beatPriceUp: number | null = null;
  let beatPriceDown: number | null = null;
  const highCount = true;

  for (let i = 0; i < data.length; i++) {
    if (i < 2) {
      values.push(0);
      continue;
    }
    let i1 = i - 2;
    let i2 = i - 1;
    const i3 = i;
    const d = data;
    while (i2 > 0 && insideBar(d[i2].high, d[i2].low, d[i3].high, d[i3].low)) {
      i1 = i1 > 0 ? i1 - 1 : 0;
      i2 -= 1;
      if (i2 < 0) break;
    }
    while (i1 > 0 && insideBar(d[i1].high, d[i1].low, d[i2].high, d[i2].low)) {
      i1 -= 1;
      if (i1 < 0) break;
    }
    if (
      i1 < 0 ||
      i2 < 0 ||
      i3 < 0 ||
      i1 >= d.length ||
      i2 >= d.length ||
      i3 >= d.length
    ) {
      values.push(value);
      continue;
    }
    const b1 = d[i1];
    const b2 = d[i2];
    const b3 = d[i3];
    const reversalUp = b1.high > b2.high && b1.low > b2.low && b3.low > b2.low;
    const reversalDown =
      b1.low < b2.low && b1.high < b2.high && b3.high < b2.high;
    const upEngulfing = reversalUp && b3.close > b1.high;
    const downEngulfing = reversalDown && b3.close < b1.low;
    const prevBeatPriceUp = beatPriceUp;
    const prevBeatPriceDown = beatPriceDown;
    if ((!upEngulfing && !downEngulfing) || highCount) {
      if (reversalUp) beatPriceUp = b1.high;
      if (reversalDown) beatPriceDown = b1.low;
    }
    if (!highCount) {
      if (upEngulfing) beatPriceUp = null;
      if (downEngulfing) beatPriceDown = null;
    }
    const prevValue = value;
    if (!reversalUp && !reversalDown) {
      if (b3.close > (prevBeatPriceUp ?? -Infinity)) {
        value = prevValue + 1;
        if (!highCount) beatPriceUp = null;
      }
      if (b3.close < (prevBeatPriceDown ?? Infinity)) {
        value = prevValue - 1;
        if (!highCount) beatPriceDown = null;
      }
    }
    if (upEngulfing) value = prevValue > 0 ? prevValue + 1 : 1;
    if (downEngulfing) value = prevValue < 0 ? prevValue - 1 : -1;
    values.push(value);
  }
  return values;
}

/**
 * 安第斯振荡器：osc = bull - bear，bull/bear 由包络线计算
 * 返回 osc 数组及 signal(EMA(osc))、plusLevel/minusLevel(±stdDev)
 */
export function calculateAndeanOscillator(
  data: OHLCItem[],
  length: number,
  sigLength: number,
): {
  osc: number[];
  signal: (number | null)[];
  plusLevel: (number | null)[];
  minusLevel: (number | null)[];
} {
  if (!data?.length)
    return { osc: [], signal: [], plusLevel: [], minusLevel: [] };
  const alpha = 2 / (length + 1);
  let up1 = 0;
  let up2 = 0;
  let dn1 = 0;
  let dn2 = 0;
  const osc: number[] = [];
  for (let i = 0; i < data.length; i++) {
    const C = data[i].close;
    const O = data[i].open;
    if (i === 0) {
      up1 = C;
      up2 = C * C;
      dn1 = C;
      dn2 = C * C;
    } else {
      up1 = Math.max(C, O, up1 - (up1 - C) * alpha);
      up2 = Math.max(C * C, O * O, up2 - (up2 - C * C) * alpha);
      dn1 = Math.min(C, O, dn1 + (C - dn1) * alpha);
      dn2 = Math.min(C * C, O * O, dn2 + (C * C - dn2) * alpha);
    }
    const bull = Math.sqrt(Math.max(0, dn2 - dn1 * dn1));
    const bear = Math.sqrt(Math.max(0, up2 - up1 * up1));
    osc.push(bull - bear);
  }
  const signal = emaArray(osc, sigLength);
  const plusLevel: (number | null)[] = [];
  const minusLevel: (number | null)[] = [];
  for (let i = 0; i < data.length; i++) {
    if (i < length) {
      plusLevel.push(null);
      minusLevel.push(null);
      continue;
    }
    const slice = osc.slice(i - length + 1, i + 1);
    const mean = slice.reduce((a, b) => a + b, 0) / slice.length;
    let variance = 0;
    for (let j = 0; j < slice.length; j++)
      variance += Math.pow(slice[j] - mean, 2);
    const stdDev = Math.sqrt(variance / slice.length);
    plusLevel.push(stdDev);
    minusLevel.push(-stdDev);
  }
  return { osc, signal, plusLevel, minusLevel };
}
