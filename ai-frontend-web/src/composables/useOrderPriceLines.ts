import type { ISeriesApi, IChartApi, Time } from "lightweight-charts"
import { LineSeries } from "lightweight-charts"
import type { Ref } from "vue"
import { ref } from "vue"

export interface BatchExitPlan {
  batchIndex?: number
  percent?: number
  ratio?: number
}

export interface BatchExitRecord {
  batchIndex?: number
  triggerPrice?: number
  closeVolume?: number
  closeAvgPrice?: number
  income?: number
  closeTime?: number
}

export interface OrderEntry {
  orderSn: string
  entryPrice: number
  orderSide: string
  gainPrice?: number | null
  lossPrice?: number | null
  entryTime?: number | null
  batchExitType?: string | null
  batchExitPlans?: BatchExitPlan[]
  batchExits?: BatchExitRecord[]
}

export function useOrderPriceLines(
  seriesRef: Ref<ISeriesApi<"Candlestick"> | null>,
  chartRef: Ref<IChartApi | null>,
) {
  const seriesMap = new Map<string, ISeriesApi<"Line">[]>()
  const isSetup = ref(false)
  /** 缓存的最新订单数据，供切换显示/隐藏时重新渲染 */
  let lastOrders: OrderEntry[] = []

  function setLineData(series: ISeriesApi<"Line">, entryTime: number | null | undefined, price: number) {
    if (entryTime) {
      const SECONDS_10_YEARS = 10 * 365 * 24 * 3600
      series.setData([
        { time: Math.floor(entryTime) as Time, value: price },
        { time: Math.floor(entryTime + SECONDS_10_YEARS) as Time, value: price },
      ])
    }
  }

  function createLines(entry: OrderEntry, chart: IChartApi): ISeriesApi<"Line">[] {
    const seriesList: ISeriesApi<"Line">[] = []
    const baseOpts = { priceLineVisible: false }

    const entrySeries = chart.addSeries(LineSeries, {
      color: "#888888",
      lineStyle: 0,
      lineWidth: 1,
      title: `入场 ${entry.entryPrice}`,
      ...baseOpts,
    })
    setLineData(entrySeries, entry.entryTime, entry.entryPrice)
    seriesList.push(entrySeries)

    const hasBatchExits = entry.batchExitType && entry.batchExits && entry.batchExits.length > 0
    const hasBatchPlans = entry.batchExitType && entry.batchExitPlans && entry.batchExitPlans.length > 0

    if (hasBatchExits) {
      for (const record of entry.batchExits) {
        if (record.triggerPrice != null && record.triggerPrice > 0) {
          const label = `${batchTypeLabel(entry.batchExitType)} 档${record.batchIndex ?? ''}`
          const batchSeries = chart.addSeries(LineSeries, {
            color: "#26a69a",
            lineStyle: 2,
            lineWidth: 1,
            title: `${label} ${record.triggerPrice}`,
            ...baseOpts,
          })
          setLineData(batchSeries, entry.entryTime, record.triggerPrice)
          seriesList.push(batchSeries)
        }
      }
    } else if (entry.gainPrice != null && entry.gainPrice > 0) {
      const gainSeries = chart.addSeries(LineSeries, {
        color: "#26a69a",
        lineStyle: 0,
        lineWidth: 1,
        title: `止盈 ${entry.gainPrice}`,
        ...baseOpts,
      })
      setLineData(gainSeries, entry.entryTime, entry.gainPrice)
      seriesList.push(gainSeries)
    }

    if (entry.lossPrice != null && entry.lossPrice > 0 && !hasBatchPlans) {
      const lossSeries = chart.addSeries(LineSeries, {
        color: "#ef5350",
        lineStyle: 0,
        lineWidth: 1,
        title: `止损 ${entry.lossPrice}`,
        ...baseOpts,
      })
      setLineData(lossSeries, entry.entryTime, entry.lossPrice)
      seriesList.push(lossSeries)
    }

    return seriesList
  }

  function batchTypeLabel(type?: string | null): string {
    const map: Record<string, string> = {
      BATCH_TAKE_PROFIT: "分批止盈",
      BATCH_STOP_LOSS: "分批止损",
      BATCH_TRAILING_GAIN: "分批移动止盈",
      BATCH_TRAILING_LOSS: "分批移动止损",
    }
    return type ? map[type] || type : ""
  }

  function clearAll(chart: IChartApi) {
    for (const seriesList of seriesMap.values()) {
      seriesList.forEach((s) => chart.removeSeries(s))
    }
    seriesMap.clear()
  }

  function updateOrders(orders: OrderEntry[]) {
    const series = seriesRef.value
    const chart = chartRef.value
    if (!series || !chart) return

    lastOrders = orders
    clearAll(chart)

    for (const order of orders) {
      const lines = createLines(order, chart)
      seriesMap.set(order.orderSn, lines)
    }

    isSetup.value = true
  }

  /** 显示/隐藏入场价格线，隐藏时清除线条，显示时用缓存数据重新绘制 */
  function setVisible(visible: boolean) {
    const chart = chartRef.value
    if (!chart) return
    if (visible) {
      if (lastOrders.length > 0) {
        updateOrders(lastOrders)
      }
    } else {
      clearAll(chart)
      isSetup.value = false
    }
  }

  function destroy() {
    const chart = chartRef.value
    if (chart) {
      clearAll(chart)
    }
    seriesMap.clear()
  }

  return {
    updateOrders,
    setVisible,
    destroy,
    isSetup,
  }
}
