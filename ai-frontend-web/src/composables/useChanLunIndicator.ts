import { getChanLunData } from "@/api/chanlun"
import type { Time, IChartApi, ISeriesApi, IPrimitivePaneView, IPrimitivePaneRenderer } from "lightweight-charts"
import type { CanvasRenderingTarget2D } from "fancy-canvas"

// ---- 类型定义 ----

interface StdKLine {
  time: string
  open: number
  high: number
  low: number
  close: number
  volume: number
  atr: number
}

interface FenXing {
  index: number
  type: "TOP" | "BOTTOM"
  high: number
  low: number
}

interface Bi {
  start: FenXing
  end: FenXing
  direction: "UP" | "DOWN"
  high: number
  low: number
  klineCount: number
}

interface ZhongShu {
  id: string
  type: string
  startIndex: number
  endIndex: number
  high: number
  low: number
  growthType?: string   // "NEW" / "EXTEND" / "EXPAND"
  gg?: number           // 波动高点
  dd?: number           // 波动低点
}

interface Signal {
  id: string
  type: "BUY" | "SELL"
  level: number
  barIndex: number
  price: number
}

interface ChanLunResult {
  period: string
  klines: StdKLine[]
  fenXings: FenXing[]
  bis: Bi[]
  zhongShus: ZhongShu[]
  signals: Signal[]
}

// ---- 渲染数据结构 ----

interface FenXingDraw {
  x: number
  y: number
  type: "TOP" | "BOTTOM"
}

interface BiDraw {
  x1: number; y1: number
  x2: number; y2: number
  direction: "UP" | "DOWN"
}

interface ZhongShuDraw {
  x1: number; x2: number
  y1: number; y2: number
  growthType?: string
}

interface SignalDraw {
  x: number; y: number
  type: "BUY" | "SELL"
  level: number
}

interface DrawData {
  fenXings: FenXingDraw[]
  bis: BiDraw[]
  zhongShus: ZhongShuDraw[]
  signals: SignalDraw[]
}

// ---- 颜色 ----

const TOP_COLOR = "#F23645"
const BOTTOM_COLOR = "#089981"
const BI_UP_COLOR = "#089981"
const BI_DOWN_COLOR = "#F23645"
const ZS_FILL = "rgba(52, 152, 219, 0.15)"
const ZS_BORDER = "#3498db"
const ZS_EXTEND_FILL = "rgba(155, 89, 182, 0.15)"  // 延伸→紫色
const ZS_EXTEND_BORDER = "#9b59b6"
const ZS_EXPAND_FILL = "rgba(231, 76, 60, 0.15)"   // 扩展→红色
const ZS_EXPAND_BORDER = "#e74c3c"
const BUY_COLOR = "#089981"
const SELL_COLOR = "#F23645"

// ---- Primitive Renderer ----

class ChanLunPaneRenderer implements IPrimitivePaneRenderer {
  private _dd: DrawData

  constructor(dd: DrawData) {
    this._dd = dd
  }

  draw(target: CanvasRenderingTarget2D): void {
    target.useMediaCoordinateSpace(({ context: ctx }) => {
      const d = this._dd

      // 1. 中枢矩形（按growthType区分颜色与标签）
      for (const zs of d.zhongShus) {
        const gt = zs.growthType || "NEW"
        const fill = gt === "EXTEND" ? ZS_EXTEND_FILL : gt === "EXPAND" ? ZS_EXPAND_FILL : ZS_FILL
        const border = gt === "EXTEND" ? ZS_EXTEND_BORDER : gt === "EXPAND" ? ZS_EXPAND_BORDER : ZS_BORDER
        ctx.fillStyle = fill
        ctx.strokeStyle = border
        ctx.lineWidth = 1
        ctx.setLineDash([])
        ctx.fillRect(zs.x1, zs.y1, zs.x2 - zs.x1, zs.y2 - zs.y1)
        ctx.strokeRect(zs.x1, zs.y1, zs.x2 - zs.x1, zs.y2 - zs.y1)
      }

      // 2. 笔连线
      for (const bi of d.bis) {
        ctx.strokeStyle = bi.direction === "UP" ? BI_UP_COLOR : BI_DOWN_COLOR
        ctx.lineWidth = 2
        ctx.setLineDash([])
        ctx.beginPath()
        ctx.moveTo(bi.x1, bi.y1)
        ctx.lineTo(bi.x2, bi.y2)
        ctx.stroke()
      }

      // 3. 分型标记
      for (const fx of d.fenXings) {
        ctx.strokeStyle = fx.type === "TOP" ? TOP_COLOR : BOTTOM_COLOR
        ctx.fillStyle = fx.type === "TOP" ? TOP_COLOR : BOTTOM_COLOR
        ctx.lineWidth = 2

        if (fx.type === "TOP") {
          // ▼ 向下指（顶分型如屋檐/天花板）
          ctx.beginPath()
          ctx.moveTo(fx.x, fx.y + 8)
          ctx.lineTo(fx.x - 6, fx.y - 4)
          ctx.lineTo(fx.x + 6, fx.y - 4)
          ctx.closePath()
          ctx.fill()
        } else {
          // ▲ 向上指（底分型如地板/支撑）
          ctx.beginPath()
          ctx.moveTo(fx.x, fx.y - 8)
          ctx.lineTo(fx.x - 6, fx.y + 4)
          ctx.lineTo(fx.x + 6, fx.y + 4)
          ctx.closePath()
          ctx.fill()
        }
      }

      // 4. 买卖点信号
      for (const sg of d.signals) {
        const isBuy = sg.type === "BUY"
        ctx.fillStyle = isBuy ? BUY_COLOR : SELL_COLOR
        ctx.strokeStyle = isBuy ? BUY_COLOR : SELL_COLOR
        ctx.lineWidth = 2

        // 圆形标记
        ctx.beginPath()
        ctx.arc(sg.x, sg.y, 6, 0, Math.PI * 2)
        ctx.fill()

        // 外圈
        ctx.fillStyle = isBuy ? "rgba(8,153,129,0.2)" : "rgba(242,54,69,0.2)"
        ctx.beginPath()
        ctx.arc(sg.x, sg.y, 12, 0, Math.PI * 2)
        ctx.fill()

        // 标签
        const label = isBuy ? `B${sg.level}` : `S${sg.level}`
        ctx.fillStyle = "#fff"
        ctx.font = "bold 10px sans-serif"
        ctx.textAlign = "center"
        ctx.textBaseline = "middle"
        ctx.fillText(label, sg.x, sg.y)

        // 文字说明
        ctx.fillStyle = isBuy ? BUY_COLOR : SELL_COLOR
        ctx.font = "11px sans-serif"
        ctx.textBaseline = "bottom"
        const desc = isBuy
          ? sg.level === 1 ? "一买" : sg.level === 2 ? "二买" : "三买"
          : sg.level === 1 ? "一卖" : sg.level === 2 ? "二卖" : "三卖"
        ctx.fillText(desc, sg.x, sg.y - 16)
      }
    })
  }
}

// ---- Primitive Pane View ----

class ChanLunPaneView implements IPrimitivePaneView {
  private _dd: DrawData

  constructor(dd: DrawData) {
    this._dd = dd
  }

  renderer(): IPrimitivePaneRenderer | null {
    return new ChanLunPaneRenderer(this._dd)
  }
}

// ---- Primitive (attached to series) ----

class ChanLunPrimitive {
  private _chart: IChartApi | null = null
  private _series: ISeriesApi<"Candlestick"> | null = null
  private _requestUpdate: (() => void) | null = null
  private _data: ChanLunResult | null = null
  private _dd: DrawData = { fenXings: [], bis: [], zhongShus: [], signals: [] }
  // 缓存：K线索引 → Unix时间戳(秒)，避免每帧重复 new Date()
  private _tsMap: number[] | null = null

  attached(param: { chart: IChartApi; series: ISeriesApi<"Candlestick">; requestUpdate: () => void }): void {
    this._chart = param.chart
    this._series = param.series
    this._requestUpdate = param.requestUpdate
  }

  detached(): void {
    this._chart = null
    this._series = null
    this._requestUpdate = null
  }

  setData(data: ChanLunResult): void {
    this._data = data
    // 数据到达时一次性预计算时间戳映射，后续渲染不再重复 new Date()
    this._tsMap = data.klines.map(k => new Date(k.time).getTime() / 1000)
    this._requestUpdate?.()
  }

  clear(): void {
    this._data = null
    this._tsMap = null
    this._dd = { fenXings: [], bis: [], zhongShus: [], signals: [] }
    this._requestUpdate?.()
  }

  updateAllViews(): void {
    if (!this._chart || !this._series) return
    if (!this._data || !this._tsMap) {
      this._dd = { fenXings: [], bis: [], zhongShus: [], signals: [] }
      return
    }

    const chart = this._chart
    const series = this._series
    const data = this._data
    const tsMap = this._tsMap

    // 获取当前可视时间范围，仅处理可见区域内的元素以减少坐标转换调用
    // visibleRange.from/to 可能是 string（ISO日期）或 number（Unix秒），需要兼容处理
    const visibleRange = chart.timeScale().getVisibleRange()
    let visibleStart = -Infinity
    let visibleEnd = Infinity
    if (visibleRange) {
      visibleStart = typeof visibleRange.from === 'number' ? visibleRange.from : new Date(visibleRange.from).getTime() / 1000
      visibleEnd = typeof visibleRange.to === 'number' ? visibleRange.to : new Date(visibleRange.to).getTime() / 1000
    }

    const fxDraw: FenXingDraw[] = []
    const biDraw: BiDraw[] = []
    const zsDraw: ZhongShuDraw[] = []
    const sgDraw: SignalDraw[] = []

    // ---- 分型 ----
    for (const fx of data.fenXings) {
      const ts = tsMap[fx.index]
      if (ts == null || ts < visibleStart || ts > visibleEnd) continue
      const x = chart.timeScale().timeToCoordinate(ts as Time)
      const y = series.priceToCoordinate(fx.type === "TOP" ? fx.high : fx.low)
      if (x == null || y == null) continue
      fxDraw.push({ x, y, type: fx.type })
    }

    // ---- 笔 ----
    for (const bi of data.bis) {
      const ts1 = tsMap[bi.start.index]
      const ts2 = tsMap[bi.end.index]
      if (ts1 == null || ts2 == null) continue
      // 整笔完全在可视区域之外则跳过（笔跨过可见边界时仍需绘制）
      if ((ts1 < visibleStart && ts2 < visibleStart) || (ts1 > visibleEnd && ts2 > visibleEnd)) continue
      const x1 = chart.timeScale().timeToCoordinate(ts1 as Time)
      const y1 = series.priceToCoordinate(bi.direction === "UP" ? bi.start.low : bi.start.high)
      const x2 = chart.timeScale().timeToCoordinate(ts2 as Time)
      const y2 = series.priceToCoordinate(bi.direction === "UP" ? bi.end.high : bi.end.low)
      if (x1 == null || y1 == null || x2 == null || y2 == null) continue
      biDraw.push({ x1, y1, x2, y2, direction: bi.direction })
    }

    // ---- 中枢 ----
    for (const zs of data.zhongShus) {
      const ts1 = tsMap[zs.startIndex]
      const ts2 = tsMap[zs.endIndex]
      if (ts1 == null || ts2 == null) continue
      if ((ts1 < visibleStart && ts2 < visibleStart) || (ts1 > visibleEnd && ts2 > visibleEnd)) continue
      const x1 = chart.timeScale().timeToCoordinate(ts1 as Time)
      const x2 = chart.timeScale().timeToCoordinate(ts2 as Time)
      const y1 = series.priceToCoordinate(zs.high)
      const y2 = series.priceToCoordinate(zs.low)
      if (x1 == null || x2 == null || y1 == null || y2 == null) continue
      zsDraw.push({ x1, x2, y1: Math.min(y1, y2), y2: Math.max(y1, y2), growthType: zs.growthType })
    }

    // ---- 买卖点 ----
    for (const sg of data.signals) {
      const ts = tsMap[sg.barIndex]
      if (ts == null || ts < visibleStart || ts > visibleEnd) continue
      const x = chart.timeScale().timeToCoordinate(ts as Time)
      const y = series.priceToCoordinate(sg.price)
      if (x == null || y == null) continue
      sgDraw.push({ x, y, type: sg.type, level: sg.level })
    }

    this._dd = { fenXings: fxDraw, bis: biDraw, zhongShus: zsDraw, signals: sgDraw }
  }

  paneViews(): readonly IPrimitivePaneView[] {
    return [new ChanLunPaneView(this._dd)]
  }
}

// ---- Composable ----

export interface ChanLunConfig {
  enabled: boolean
  symbol: string
  interval: string
  limit?: number
}

export function useChanLunIndicator() {
  let _primitive: ChanLunPrimitive | null = null
  let _series: ISeriesApi<"Candlestick"> | null = null
  let _lastSymbol = ""
  let _lastInterval = ""

  async function fetchData(symbol: string, interval: string, limit: number): Promise<ChanLunResult | null> {
    try {
      const res = await getChanLunData({ symbol, interval, limit })
      if (res.success && res.data) {
        return res.data as ChanLunResult
      }
      return null
    } catch (e) {
      console.warn("[ChanLun] fetch error:", e)
      return null
    }
  }

  function update(chart: IChartApi, series: ISeriesApi<"Candlestick">, config: ChanLunConfig): void {
    if (!config.enabled) {
      return
    }

    const changed = config.symbol !== _lastSymbol || config.interval !== _lastInterval
    _lastSymbol = config.symbol
    _lastInterval = config.interval
    _series = series

    // 首次创建 primitive 并 attach 到已有 candleSeries
    if (!_primitive) {
      _primitive = new ChanLunPrimitive()
      series.attachPrimitive(_primitive as any)
    }

    // symbol 或 interval 变化时重新拉取数据
    if (changed) {
      fetchData(config.symbol, config.interval, config.limit ?? 500).then(data => {
        if (data) _primitive!.setData(data)
      })
    }
  }

  function destroy(): void {
    if (_primitive) {
      _primitive.clear()
    }
    _primitive = null
    _series = null
    _lastSymbol = ""
    _lastInterval = ""
  }

  return { update, destroy }
}
