import { getSmc } from "@/api"
import { LineSeries } from "lightweight-charts"
import type { Time, IChartApi, ISeriesApi, IPrimitivePaneView, IPrimitivePaneRenderer } from "lightweight-charts"
import type { CanvasRenderingTarget2D } from "fancy-canvas"

interface SmcOrderBlock {
  time: number
  high: number
  low: number
  bias: number
}

interface SmcBosChochSignal {
  timestamp: number
  price: number
  type: string
  pivotTimestamp: number
}

interface SmcSwingPoint {
  time: number
  price: number
  label: string
}

interface SmcBarResult {
  timestamp: number
  open: number
  high: number
  low: number
  close: number
  swingOrderBlocks: SmcOrderBlock[]
  internalOrderBlocks: SmcOrderBlock[]
  bullishFairValueGap: boolean
  bearishFairValueGap: boolean
  lastBullishFVGTop?: number
  lastBullishFVGBottom?: number
  lastBearishFVGTop?: number
  lastBearishFVGBottom?: number
  premiumZoneTop?: number
  premiumZoneBottom?: number
  discountZoneTop?: number
  discountZoneBottom?: number
  equilibriumZoneTop?: number
  equilibriumZoneBottom?: number
  equilibriumCenter?: number
  equalHighs: boolean
  equalLows: boolean
  swingBullishBOS?: boolean
  swingBearishBOS?: boolean
  swingBullishCHOCH?: boolean
  swingBearishCHOCH?: boolean
  internalBullishBOS?: boolean
  internalBearishBOS?: boolean
  internalBullishCHOCH?: boolean
  internalBearishCHOCH?: boolean
}

interface ObRect {
  x1: number; x2: number
  y1: number; y2: number
  fillColor: string
  label: string
}

interface FvgRect {
  x1: number; x2: number
  y1: number; y2: number
}

interface BosChochMarker {
  x: number
  endX: number
  y: number
  color: string
  label: string
  isDashed: boolean
}

interface SwingLabelMarker {
  x: number
  y: number
  label: string
  color: string
  above: boolean
}

interface DrawData {
  orderBlocks: ObRect[]
  fvgs: FvgRect[]
  eqhLines: Array<{ x: number; y: number }>
  eqlLines: Array<{ x: number; y: number }>
  bosChochMarkers: BosChochMarker[]
  swingLabels: SwingLabelMarker[]
}

interface ZoneCoords {
  premiumTop: number; premiumBottom: number
  discountTop: number; discountBottom: number
  equilibriumTop: number; equilibriumBottom: number; equilibriumCenter: number
}

const FVG_FILL = "rgba(241,196,15,0.25)"
const EQH_EQL_COLOR = "#8e44ad"

const TF_COLORS: Record<string, { bullFill: string; bearFill: string }> = {
  "15m": { bullFill: "rgba(41,128,185,0.2)", bearFill: "rgba(192,57,43,0.2)" },
  "1h": { bullFill: "rgba(230,126,34,0.2)", bearFill: "rgba(192,57,43,0.2)" },
  "4h": { bullFill: "rgba(142,68,173,0.2)", bearFill: "rgba(192,57,43,0.2)" },
  "1D": { bullFill: "rgba(127,140,141,0.2)", bearFill: "rgba(192,57,43,0.2)" },
}

const TIMEFRAME_SECONDS: Record<string, number> = {
  '15m': 15 * 60,
  '1h': 3600,
  '4h': 4 * 3600,
  '1D': 24 * 3600,
}

class SmcPaneRenderer implements IPrimitivePaneRenderer {
  private _dd: DrawData
  private _zone: ZoneCoords | null

  constructor(dd: DrawData, zone: ZoneCoords | null) {
    this._dd = dd
    this._zone = zone
  }

  draw(target: CanvasRenderingTarget2D): void {
    target.useMediaCoordinateSpace(({ context: ctx }) => {
      const d = this._dd
      for (const ob of d.orderBlocks) {
        ctx.fillStyle = ob.fillColor
        ctx.fillRect(ob.x1, ob.y1, ob.x2 - ob.x1, ob.y2 - ob.y1)
        const labelW = ctx.measureText(ob.label).width + 8
        const labelH = 16
        const lx = ob.x1 + 2
        const ly = ob.y1 + 2
        ctx.fillStyle = "rgba(0,0,0,0.55)"
        ctx.fillRect(lx, ly, labelW, labelH)
        ctx.fillStyle = "#fff"
        ctx.font = "11px sans-serif"
        ctx.textAlign = "left"
        ctx.textBaseline = "middle"
        ctx.fillText(ob.label, lx + 4, ly + labelH / 2)
      }
      ctx.textBaseline = "alphabetic"
      for (const fvg of d.fvgs) {
        ctx.fillStyle = FVG_FILL
        ctx.fillRect(fvg.x1, fvg.y1, fvg.x2 - fvg.x1, fvg.y2 - fvg.y1)
      }
      if (this._zone) {
        const z = this._zone
        ctx.fillStyle = "rgba(231,76,60,0.08)"
        ctx.fillRect(0, z.premiumTop, ctx.canvas.width, z.premiumBottom - z.premiumTop)
        ctx.fillStyle = "rgba(241,196,15,0.08)"
        ctx.fillRect(0, z.equilibriumTop, ctx.canvas.width, z.equilibriumBottom - z.equilibriumTop)
        ctx.fillStyle = "rgba(46,204,113,0.08)"
        ctx.fillRect(0, z.discountTop, ctx.canvas.width, z.discountBottom - z.discountTop)
        ctx.strokeStyle = "#f39c12"
        ctx.lineWidth = 0.5
        ctx.beginPath()
        ctx.moveTo(0, z.equilibriumTop)
        ctx.lineTo(ctx.canvas.width, z.equilibriumTop)
        ctx.moveTo(0, z.equilibriumBottom)
        ctx.lineTo(ctx.canvas.width, z.equilibriumBottom)
        ctx.stroke()
        ctx.font = "11px sans-serif"
        ctx.textAlign = "left"
        ctx.fillStyle = "#e74c3c"
        ctx.fillText("Premium", 4, z.premiumTop + 14)
        ctx.fillStyle = "#f39c12"
        ctx.fillText("Equilibrium", 4, z.equilibriumTop + 14)
        ctx.fillStyle = "#2ecc71"
        ctx.fillText("Discount", 4, z.discountBottom - 4)
      }
      ctx.strokeStyle = EQH_EQL_COLOR
      ctx.lineWidth = 1
      ctx.setLineDash([4, 4])
      for (const eqh of d.eqhLines) {
        ctx.beginPath()
        ctx.moveTo(eqh.x - 8, eqh.y)
        ctx.lineTo(eqh.x + 8, eqh.y)
        ctx.stroke()
      }
      for (const eql of d.eqlLines) {
        ctx.beginPath()
        ctx.moveTo(eql.x - 8, eql.y)
        ctx.lineTo(eql.x + 8, eql.y)
        ctx.stroke()
      }
      ctx.setLineDash([])

      for (const m of d.bosChochMarkers) {
        ctx.strokeStyle = m.color
        ctx.lineWidth = 1.5
        if (m.isDashed) {
          ctx.setLineDash([6, 4])
        }
        ctx.beginPath()
        ctx.moveTo(m.x, m.y)
        ctx.lineTo(m.endX, m.y)
        ctx.stroke()
        if (m.isDashed) {
          ctx.setLineDash([])
        }
        const midX = (m.x + m.endX) / 2
        const labelW = ctx.measureText(m.label).width + 8
        const labelH = 16
        const lx = midX - labelW / 2
        const ly = m.y - labelH - 6
        ctx.fillStyle = "rgba(0,0,0,0.55)"
        ctx.fillRect(lx, ly, labelW, labelH)
        ctx.fillStyle = m.color
        ctx.font = "11px sans-serif"
        ctx.textAlign = "center"
        ctx.textBaseline = "middle"
        ctx.fillText(m.label, midX, ly + labelH / 2)
      }

      // 绘制摆动点标签（HH/HL/LL/LH）
      for (const sl of d.swingLabels) {
        const labelW = ctx.measureText(sl.label).width + 8
        const labelH = 16
        const lx = sl.x - labelW / 2
        const ly = sl.above ? sl.y - labelH - 4 : sl.y + 4
        ctx.fillStyle = "rgba(0,0,0,0.6)"
        ctx.fillRect(lx, ly, labelW, labelH)
        ctx.fillStyle = sl.color
        ctx.font = "bold 11px sans-serif"
        ctx.textAlign = "center"
        ctx.textBaseline = "middle"
        ctx.fillText(sl.label, sl.x, ly + labelH / 2)
      }
    })
  }
}

class SmcPaneView implements IPrimitivePaneView {
  private _dd: DrawData
  private _zone: ZoneCoords | null

  constructor(dd: DrawData, zone: ZoneCoords | null) {
    this._dd = dd
    this._zone = zone
  }

  renderer(): IPrimitivePaneRenderer | null {
    return new SmcPaneRenderer(this._dd, this._zone)
  }
}

class SmcPrimitive {
  private _chart: IChartApi | null = null
  private _series: ISeriesApi<"Line"> | null = null
  private _requestUpdate: (() => void) | null = null
  private _data: SmcBarResult[] = []
  private _bosChochSignals: SmcBosChochSignal[] = []
  private _swingPoints: SmcSwingPoint[] = []
  private _colors: { bullFill: string; bearFill: string }
  private _barSec: number
  private _period: string
  private _obMode: 'swing' | 'internal' | 'both' = 'swing'
  private _showBOS: boolean = true
  private _showCHOCH: boolean = true
  private _dd: DrawData = { orderBlocks: [], fvgs: [], eqhLines: [], eqlLines: [], bosChochMarkers: [], swingLabels: [] }
  private _zone: ZoneCoords | null = null

  constructor(colors: { bullFill: string; bearFill: string }, barSec: number, period: string) {
    this._colors = colors
    this._barSec = barSec
    this._period = period
  }

  setObMode(mode: 'swing' | 'internal' | 'both'): void {
    if (this._obMode !== mode) {
      this._obMode = mode
      this._requestUpdate?.()
    }
  }

  setShowBOS(show: boolean): void {
    if (this._showBOS !== show) {
      this._showBOS = show
      this._requestUpdate?.()
    }
  }

  setShowCHOCH(show: boolean): void {
    if (this._showCHOCH !== show) {
      this._showCHOCH = show
      this._requestUpdate?.()
    }
  }

  attached(param: { chart: IChartApi; series: ISeriesApi<"Line">; requestUpdate: () => void }): void {
    this._chart = param.chart
    this._series = param.series
    this._requestUpdate = param.requestUpdate
  }

  detached(): void {
    this._chart = null
    this._series = null
    this._requestUpdate = null
  }

  setData(bars: SmcBarResult[], signals?: SmcBosChochSignal[], swingPoints?: SmcSwingPoint[]): void {
    this._data = bars
    if (signals) this._bosChochSignals = signals
    if (swingPoints) this._swingPoints = swingPoints
    this._requestUpdate?.()
  }

  updateAllViews(): void {
    if (!this._chart || !this._series) return
    const chart = this._chart
    const series = this._series

    const obResult: ObRect[] = []
    const fvgResult: FvgRect[] = []
    const eqhLines: Array<{ x: number; y: number }> = []
    const eqlLines: Array<{ x: number; y: number }> = []
    const bosChochResult: BosChochMarker[] = []
    let zoneResult: ZoneCoords | null = null
    let rightEdgeX = 0
    try { rightEdgeX = chart.timeScale().width() } catch { }

    for (const bar of this._data) {
      const timeSec = bar.timestamp / 1000
      const barEndSec = timeSec + this._barSec
      const x = chart.timeScale().timeToCoordinate(timeSec as Time)
      const xR = chart.timeScale().timeToCoordinate(barEndSec as Time)
      if (x == null) continue
      const x2 = xR ?? x + 12
      const blocks: SmcOrderBlock[] = []
      if (this._obMode === 'swing' || this._obMode === 'both') {
        blocks.push(...(bar.swingOrderBlocks || []))
      }
      if (this._obMode === 'internal' || this._obMode === 'both') {
        blocks.push(...(bar.internalOrderBlocks || []))
      }
      if (blocks.length > 0) {
        for (const ob of blocks) {
          const obSec = ob.time / 1000
          const obX = chart.timeScale().timeToCoordinate(obSec as Time)
          if (obX == null) continue
          const y1 = series.priceToCoordinate(ob.high)
          const y2 = series.priceToCoordinate(ob.low)
          if (y1 == null || y2 == null) continue
          obResult.push({
            x1: obX, x2: rightEdgeX,
            y1: Math.min(y1, y2),
            y2: Math.max(y1, y2),
            fillColor: ob.bias > 0 ? this._colors.bullFill : this._colors.bearFill,
            label: `${this._period} ${ob.bias > 0 ? '支撑' : '压力'}`,
          })
        }
      }
      if (bar.bullishFairValueGap && bar.lastBullishFVGTop != null && bar.lastBullishFVGBottom != null) {
        const yT = series.priceToCoordinate(bar.lastBullishFVGTop)
        const yB = series.priceToCoordinate(bar.lastBullishFVGBottom)
        if (yT != null && yB != null) {
          fvgResult.push({ x1: x, x2, y1: Math.min(yT, yB), y2: Math.max(yT, yB) })
        }
      }
      if (bar.bearishFairValueGap && bar.lastBearishFVGTop != null && bar.lastBearishFVGBottom != null) {
        const yT = series.priceToCoordinate(bar.lastBearishFVGTop)
        const yB = series.priceToCoordinate(bar.lastBearishFVGBottom)
        if (yT != null && yB != null) {
          fvgResult.push({ x1: x, x2, y1: Math.min(yT, yB), y2: Math.max(yT, yB) })
        }
      }
      if (bar.equalHighs) {
        const y = series.priceToCoordinate(bar.high)
        if (y != null) eqhLines.push({ x, y })
      }
      if (bar.equalLows) {
        const y = series.priceToCoordinate(bar.low)
        if (y != null) eqlLines.push({ x, y })
      }
    }
    if (this._showBOS) {
      for (const signal of this._bosChochSignals) {
        if (!signal.type.includes('BOS')) continue
        const pivotSec = signal.pivotTimestamp / 1000
        const pivotX = chart.timeScale().timeToCoordinate(pivotSec as Time)
        if (pivotX == null) continue
        const sigSec = signal.timestamp / 1000
        const sigX = chart.timeScale().timeToCoordinate(sigSec as Time)
        if (sigX == null) continue
        const sigY = series.priceToCoordinate(signal.price)
        if (sigY == null) continue
        const isBullish = signal.type.includes('Bullish')
        bosChochResult.push({
          x: pivotX,
          endX: sigX,
          y: sigY,
          color: isBullish ? '#089981' : '#F23645',
          label: `${this._period}${isBullish ? '多延续' : '空延续'}`,
          isDashed: !signal.type.startsWith('swing'),
        })
      }
    }
    if (this._showCHOCH) {
      for (const signal of this._bosChochSignals) {
        if (!signal.type.includes('CHOCH')) continue
        const pivotSec = signal.pivotTimestamp / 1000
        const pivotX = chart.timeScale().timeToCoordinate(pivotSec as Time)
        if (pivotX == null) continue
        const sigSec = signal.timestamp / 1000
        const sigX = chart.timeScale().timeToCoordinate(sigSec as Time)
        if (sigX == null) continue
        const sigY = series.priceToCoordinate(signal.price)
        if (sigY == null) continue
        const isBullish = signal.type.includes('Bullish')
        bosChochResult.push({
          x: pivotX,
          endX: sigX,
          y: sigY,
          color: isBullish ? '#089981' : '#F23645',
          label: `${this._period}${isBullish ? '反转多' : '反转空'}`,
          isDashed: !signal.type.startsWith('swing'),
        })
      }
    }
    // 绘制摆动点标签（HH/HL/LL/LH）
    const swingLabelResult: SwingLabelMarker[] = []
    for (const sp of this._swingPoints) {
      const spSec = sp.time / 1000
      const spX = chart.timeScale().timeToCoordinate(spSec as Time)
      if (spX == null) continue
      const spY = series.priceToCoordinate(sp.price)
      if (spY == null) continue
      const isHighLabel = sp.label === 'HH' || sp.label === 'LH'
      swingLabelResult.push({
        x: spX,
        y: spY,
        label: sp.label,
        color: isHighLabel ? '#F23645' : '#089981',
        above: isHighLabel, // HH/LH 标签在价格上方，LL/HL 标签在价格下方
      })
    }
    this._dd = { orderBlocks: obResult, fvgs: fvgResult, eqhLines, eqlLines, bosChochMarkers: bosChochResult, swingLabels: swingLabelResult }

    if (this._data.length > 0) {
      const last = this._data[this._data.length - 1]
      if (last.premiumZoneTop != null && last.equilibriumZoneTop != null) {
        const pTop = series.priceToCoordinate(last.premiumZoneTop)
        const pBot = series.priceToCoordinate(last.premiumZoneBottom!)
        const dTop = series.priceToCoordinate(last.discountZoneTop!)
        const dBot = series.priceToCoordinate(last.discountZoneBottom!)
        const eqTop = series.priceToCoordinate(last.equilibriumZoneTop)
        const eqBot = series.priceToCoordinate(last.equilibriumZoneBottom!)
        const eqCtr = series.priceToCoordinate(last.equilibriumCenter!)
        if (pTop != null && pBot != null && dTop != null && dBot != null && eqTop != null && eqBot != null) {
          zoneResult = {
            premiumTop: Math.min(pTop, pBot), premiumBottom: Math.max(pTop, pBot),
            discountTop: Math.min(dTop, dBot), discountBottom: Math.max(dTop, dBot),
            equilibriumTop: Math.min(eqTop, eqBot), equilibriumBottom: Math.max(eqTop, eqBot),
            equilibriumCenter: eqCtr ?? (Math.min(eqTop, eqBot) + Math.max(eqTop, eqBot)) / 2,
          }
        }
      }
    }
    this._zone = zoneResult
  }

  paneViews(): readonly IPrimitivePaneView[] {
    return [new SmcPaneView(this._dd, this._zone)]
  }
}

export interface SmcIndicatorConfig {
  enabled: boolean
  timeframes: Record<string, boolean>
  symbol: string
  obTypes: Record<string, 'swing' | 'internal' | 'both'>
  showBOS?: Record<string, boolean>
  showCHOCH?: Record<string, boolean>
}

interface SmcTimeframeState {
  series: ISeriesApi<"Line"> | null
  primitive: SmcPrimitive | null
  enabled: boolean
  data: SmcBarResult[]
  signals: SmcBosChochSignal[]
  swingPoints: SmcSwingPoint[]
}

export function useSmcIndicator() {
  const stateMap = new Map<string, SmcTimeframeState>()
  let _lastSymbol = ''

  function getState(tf: string): SmcTimeframeState {
    let s = stateMap.get(tf)
    if (!s) {
      s = { series: null, primitive: null, enabled: false, data: [], signals: [], swingPoints: [] }
      stateMap.set(tf, s)
    }
    return s
  }

  function calcSmcFrom(_chart: IChartApi): number {
    return Date.now() - 86400000
  }

  async function fetchData(symbol: string, interval: string, fromMs: number): Promise<{ bars: SmcBarResult[]; signals: SmcBosChochSignal[]; swingPoints: SmcSwingPoint[] }> {
    try {
      const now = Date.now()
      const res = await getSmc({ symbol, interval, from: fromMs, to: now })
      const bars = (res.data?.results ?? []).slice(-2)
      const signals = res.data?.bosChochSignals ?? []
      const swingPoints = res.data?.swingPoints ?? []
      console.log(`[SMC] fetchData success: symbol=${symbol}, interval=${interval}, count=${res.data?.results?.length ?? 0}, kept=${bars.length}, signals=${signals.length}, swingPoints=${swingPoints.length}`)
      return { bars, signals, swingPoints }
    } catch (e) {
      console.warn(`[SMC] fetchData error: symbol=${symbol}, interval=${interval}`, e)
      return { bars: [], signals: [], swingPoints: [] }
    }
  }

  function update(chart: IChartApi, config: SmcIndicatorConfig): void {
    console.log(`[SMC] update() called: enabled=${config.enabled}, symbol=${config.symbol}, timeframes=`, JSON.stringify(config.timeframes))
    const symbolChanged = config.symbol !== _lastSymbol
    _lastSymbol = config.symbol

    const activeTfs = new Set(
      Object.entries(config.timeframes).filter(([, v]) => v).map(([k]) => k)
    )
    for (const [tf, state] of stateMap.entries()) {
      if (!activeTfs.has(tf) && state.series) {
        try { chart.removeSeries(state.series) } catch { }
        state.series = null
        state.primitive = null
        state.enabled = false
        state.data = []
      }
    }
    if (!config.enabled) return
    const fromMs = calcSmcFrom(chart)
    for (const tf of activeTfs) {
      const state = getState(tf)
      if (state.series) {
        state.primitive?.setObMode(config.obTypes[tf] || 'swing')
        state.primitive?.setShowBOS(config.showBOS?.[tf] ?? true)
        state.primitive?.setShowCHOCH(config.showCHOCH?.[tf] ?? true)
        if (symbolChanged) {
          fetchData(config.symbol, tf, fromMs).then(({ bars, signals, swingPoints }) => {
            console.log(`[SMC] data updated for ${config.symbol} ${tf}: ${bars.length} bars`)
            state.data = bars
            state.signals = signals
            state.swingPoints = swingPoints
            state.primitive?.setData(bars, signals, swingPoints)
            if (bars.length > 0) {
              state.series!.setData(bars.map(b => ({ time: (b.timestamp / 1000) as Time, value: b.close })))
            }
          })
        } else {
          state.primitive?.setData(state.data, state.signals, state.swingPoints)
        }
        continue
      }
      const colors = TF_COLORS[tf] ?? TF_COLORS["1h"]
      const barSec = TIMEFRAME_SECONDS[tf] ?? 3600
      const primitive = new SmcPrimitive(colors, barSec, tf)
      let series: ISeriesApi<"Line"> | null = null
      try {
        series = chart.addSeries(LineSeries, {
          color: "transparent",
          priceLineVisible: false,
          lastValueVisible: false,
          priceScaleId: "right",
        })
      } catch (e) {
        console.warn(`[SMC] addSeries(LineSeries) failed for ${tf}:`, e)
        continue
      }
      series.attachPrimitive(primitive as any)
      state.series = series
      state.primitive = primitive
      state.enabled = true
      primitive.setObMode(config.obTypes[tf] || 'swing')
      primitive.setShowBOS(config.showBOS?.[tf] ?? true)
      primitive.setShowCHOCH(config.showCHOCH?.[tf] ?? true)
      fetchData(config.symbol, tf, fromMs).then(({ bars, signals, swingPoints }) => {
        console.log(`[SMC] initial data loaded for ${config.symbol} ${tf}: ${bars.length} bars, signals=${signals.length}`)
        state.data = bars
        state.signals = signals
        state.swingPoints = swingPoints
        primitive.setData(bars, signals, swingPoints)
        if (bars.length > 0) {
          series!.setData(bars.map(b => ({ time: (b.timestamp / 1000) as Time, value: b.close })))
        }
      })
    }
  }

  function destroy(chart: IChartApi): void {
    for (const [, state] of stateMap.entries()) {
      if (state.series) {
        try { chart.removeSeries(state.series) } catch { }
      }
    }
    stateMap.clear()
  }

  return { update, destroy }
}
