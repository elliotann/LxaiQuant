<template>
  <div :class="rootClass">
    <!-- Header -->
    <div class="chart-header">
      <div class="hdr-left">
        <span class="sym-symbol">{{ symName || symbol || '-' }}</span>
        <div class="price-row">
          <span :class="['sym-price', priceClass]">{{ displayPrice }}</span>
        </div>
        <span v-if="displayChange !== null" :class="['sym-change', priceClass]">
          <span class="chg-arrow">{{ priceClass === 'down' ? '▼' : '▲' }}</span>
          {{ displayChangeAbs }} ({{ displayChange }})
        </span>
        <span v-if="hoveredPoint" class="hdr-time">{{ formatTooltipTime(hoveredPoint.time) }}</span>
      </div>
      <div class="hdr-right">
        <div class="hdr-acct" @click="$emit('credential-click')">
          <span class="hdr-acct-label">{{ $t('quick_trade.account') }}</span>
          <span class="hdr-acct-val">{{ credentialLabel || $t('quick_trade.pick_credential') }}</span>
          <van-icon name="arrow" class="hdr-acct-arrow" />
        </div>
        <div class="hdr-bal-row">
          <div class="hdr-bal-item">
            <span class="hdr-bal-label">{{ $t('quick_trade.available') }}</span>
            <span class="hdr-bal-avail">{{ formatNumber(balanceAvailable) }}</span>
          </div>
          <div class="hdr-bal-item">
            <span class="hdr-bal-label">{{ $t('quick_trade.total') }}</span>
            <span class="hdr-bal-total">{{ formatNumber(balanceTotal) }}</span>
          </div>
          <van-button size="mini" plain class="hdr-refresh" @click="$emit('refresh-balance')">
            <van-icon name="replay" />
          </van-button>
        </div>
      </div>
    </div>

    <!-- Timeframe tabs -->
    <div class="tf-tabs">
      <div
        v-for="tf in timeframes"
        :key="tf.value"
        :class="['tf-tab', { active: tf.value === currentTf }]"
        @click="onTfChange(tf.value)"
      >{{ tf.label }}</div>
    </div>

    <!-- Chart -->
    <div ref="chartEl" class="chart-wrap" :style="{ height: `${height}px` }">
      <div ref="lcContainer" class="lc-container"></div>
      <van-loading v-if="loading" color="#7c5cff" size="20" class="chart-loading" />
      <div v-else-if="!candles.length" class="chart-empty">
        <van-icon name="chart-trending-o" />
        <span>{{ emptyText || $t('common.no_data') }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import { createChart, ColorType, CandlestickSeries } from 'lightweight-charts'
import { klineApi } from '@/api'
import { useKlineWebSocket } from '@/composables/useKlineWebSocket.js'

const UP_COLOR = '#33d69f'
const DOWN_COLOR = '#ff5d5d'

export default {
  name: 'KlineChart',
  props: {
    market: { type: String, default: 'Crypto' },
    exchange: { type: String, default: 'GATEIO' },
    symName: { type: String, default: '' },
    symbol: { type: String, default: '' },
    defaultTimeframe: { type: String, default: '15m' },
    height: { type: Number, default: 220 },
    emptyText: { type: String, default: '' },
    noPadding: { type: Boolean, default: false },
    credentialLabel: { type: String, default: '' },
    balanceAvailable: { type: [Number, String], default: null },
    balanceTotal: { type: [Number, String], default: null },
    balanceCurrency: { type: String, default: 'USDT' }
  },
  emits: ['credential-click', 'refresh-balance'],
  setup() {
    return useKlineWebSocket()
  },
  data() {
    return {
      currentTf: this.defaultTimeframe,
      candles: [],
      loading: false,
      chart: null,
      candlestickSeries: null,
      resizeObserver: null,
      hoveredPoint: null,
      isLoadingMore: false,
      unsubVisibleRange: null
    }
  },
  computed: {
    timeframes() {
      return [
        { value: '5m', label: '5m' },
        { value: '15m', label: '15m' },
        { value: '1h', label: '1H' },
        { value: '4h', label: '4H' },
        { value: '1d', label: '1D' }
      ]
    },
    firstPrice() {
      return this.candles.length ? this.candles[0].close : null
    },
    lastPrice() {
      return this.candles.length ? this.candles[this.candles.length - 1].close : null
    },
    changePct() {
      if (this.firstPrice === null || this.lastPrice === null || this.firstPrice === 0) return null
      return ((this.lastPrice - this.firstPrice) / this.firstPrice) * 100
    },
    priceClass() {
      const p = this.hoveredPoint ? this.hoveredPoint.close : this.lastPrice
      const base = this.firstPrice
      if (base === null || p === null) return 'up'
      return p >= base ? 'up' : 'down'
    },
    displayPrice() {
      if (this.hoveredPoint) return this.formatPrice(this.hoveredPoint.close)
      return this.lastPrice !== null ? this.formatPrice(this.lastPrice) : '--'
    },
    displayChange() {
      const base = this.firstPrice
      const target = this.hoveredPoint ? this.hoveredPoint.close : this.lastPrice
      if (base === null || target === null || base === 0) return null
      const pct = ((target - base) / base) * 100
      const sign = pct >= 0 ? '+' : ''
      return `${sign}${pct.toFixed(2)}%`
    },
    displayChangeAbs() {
      const base = this.firstPrice
      const target = this.hoveredPoint ? this.hoveredPoint.close : this.lastPrice
      if (base === null || target === null) return ''
      const diff = target - base
      const sign = diff >= 0 ? '+' : ''
      return `${sign}${this.formatPrice(Math.abs(diff))}`
    },
    rootClass() {
      return { 'kline-chart': true, 'kline-chart--full': this.noPadding }
    },
    effectiveExchange() {
      return this.exchange || 'GATEIO'
    }
  },
  watch: {
    symbol() { this.fetchData(); this.wsSubscribe() },
    market() { this.fetchData() },
    lastKline(val) {
      if (!val || !this.candles.length || !this.candlestickSeries) return
      const last = this.candles[this.candles.length - 1]
      const t = val.time > 1e12 ? Math.floor(val.time / 1000) : val.time
      if (t < last.time) return
      // 构建 lightweight-charts 格式的K线数据
      const bar = { time: t, open: val.open, high: val.high, low: val.low, close: val.close }
      if (t === last.time) {
        // 更新最后一根K线（数据数组 + 图表增量更新）
        last.open = val.open
        last.high = val.high
        last.low = val.low
        last.close = val.close
      } else {
        // 追加新K线
        this.candles.push(bar)
      }
      // 使用 lightweight-charts 的 update() 增量更新，避免全量 setData 导致图表闪烁
      try {
        this.candlestickSeries.update(bar)
      } catch (e) {
        this.updateChart()
      }
    }
  },
  mounted() {
    this.$nextTick(() => {
      this.initChart()
      this.observeResize()
      if (this.symbol) {
        this.fetchData()
        this.wsSubscribe()
      }
      // 监听滚动到左边界，自动加载更多历史K线
      this.setupInfiniteScroll()
    })
  },
  beforeUnmount() {
    this.unsubVisibleRange?.()
    this.resizeObserver?.disconnect()
    this.chart?.remove()
    this.disconnect()
  },
  methods: {
    observeResize() {
      const el = this.$refs.chartEl
      if (!el) return
      this.resizeObserver = new ResizeObserver(() => {
        const w = el.clientWidth
        const h = el.clientHeight
        if (this.chart && w > 0 && h > 0) {
          this.chart.resize(w, h)
        }
      })
      this.resizeObserver.observe(el)
    },
    initChart() {
      const container = this.$refs.lcContainer
      if (!container) return

      this.chart = createChart(container, {
        width: container.clientWidth || 360,
        height: this.height,
        layout: {
          background: { type: ColorType.Solid, color: 'transparent' },
          textColor: '#a0a0b0',
          fontSize: 9,
          fontFamily: '-apple-system, sans-serif'
        },
        grid: {
          vertLines: { color: 'rgba(255,255,255,0.04)' },
          horzLines: { color: 'rgba(255,255,255,0.04)' }
        },
        crosshair: {
          mode: 1,
          vertLine: {
            width: 1,
            color: '#758696',
            style: 2
          },
          horzLine: {
            width: 1,
            color: '#758696',
            style: 2
          }
        },
        rightPriceScale: {
          borderColor: 'rgba(255,255,255,0.06)',
          scaleMargins: { top: 0.05, bottom: 0.05 },
          minimumWidth: 40,
          maximumWidth: 60
        },
        timeScale: {
          borderColor: 'rgba(255,255,255,0.06)',
          timeVisible: true,
          secondsVisible: false
        }
      })

      this.candlestickSeries = this.chart.addSeries(CandlestickSeries, {
        upColor: UP_COLOR,
        downColor: DOWN_COLOR,
        borderUpColor: UP_COLOR,
        borderDownColor: DOWN_COLOR,
        wickUpColor: UP_COLOR,
        wickDownColor: DOWN_COLOR,
        priceFormat: { type: 'price', minMove: 0.01 }
      })

      this.chart.subscribeCrosshairMove((param) => {
        if (param.time) {
          const data = param.seriesData?.get(this.candlestickSeries) || null
          this.hoveredPoint = data
        } else {
          this.hoveredPoint = null
        }
      })
    },
    updateChart() {
      if (!this.candlestickSeries || !this.candles.length) return
      const data = this.candles.map(c => ({
        time: c.time,
        open: c.open,
        high: c.high,
        low: c.low,
        close: c.close
      }))
      this.candlestickSeries.setData(data)
    },
    onTfChange(tf) {
      if (tf === this.currentTf) return
      this.currentTf = tf
      this.hoveredPoint = null
      this.fetchData()
      this.wsSubscribe()
    },
    async fetchData() {
      if (!this.symbol) return
      this.loading = true
      try {
        const normalizedSymbol = this.normalizeKlineSymbol(this.symbol)
        const nowSec = Math.floor(Date.now() / 1000)
        const res = await klineApi.jumpToTime({
          symbol: normalizedSymbol,
          interval: this.currentTf,
          exchange: this.effectiveExchange,
          time: nowSec,
          before: 200,
          after: 200,
          limit: 400
        })
        const list = (res?.data || []).map((k) => {
          // 兼容数组格式 [t,o,h,l,c,v] 和对象格式 {time,open,high,low,close}
          const raw = Array.isArray(k)
            ? { time: Number(k[0]), open: Number(k[1]), high: Number(k[2]), low: Number(k[3]), close: Number(k[4]) }
            : { time: Number(k.time || k.timestamp || k.t || 0), open: Number(k.open ?? k.o ?? 0), high: Number(k.high ?? k.h ?? 0), low: Number(k.low ?? k.l ?? 0), close: Number(k.close ?? k.c ?? 0) }
          const time = Math.floor(raw.time > 1e12 ? raw.time / 1000 : raw.time)
          return { time, open: raw.open, high: raw.high, low: raw.low, close: raw.close }
        }).filter((k) => k.time && Number.isFinite(k.close))
          .sort((a, b) => a.time - b.time)
        this.candles = list
      } catch (e) {
        this.candles = []
      } finally {
        this.loading = false
      }
      this.$nextTick(() => {
        this.updateChart()
        this.chart?.timeScale().fitContent()
        this.setupInfiniteScroll()
      })
    },
    /** 设置可见范围监听，左滑加载更多历史K线 */
    setupInfiniteScroll() {
      this.unsubVisibleRange?.()
      if (!this.chart) return
      const timeScale = this.chart.timeScale()
      this.unsubVisibleRange = timeScale.subscribeVisibleLogicalRangeChange((range) => {
        if (!range || this.isLoadingMore || this.loading) return
        const from = Number(range.from)
        if (Number.isNaN(from) || !this.candles.length) return
        // 当滚动到左侧边界附近（少于20根K线），加载更多历史数据
        if (from < 20) {
          this.loadMoreHistory()
        }
      })
    },
    /** 加载更早的历史K线并拼接到前面 */
    async loadMoreHistory() {
      if (this.isLoadingMore || !this.candles.length) return
      this.isLoadingMore = true
      try {
        const timeScale = this.chart?.timeScale()
        const logicalBefore = timeScale?.getVisibleLogicalRange()
        const firstTime = this.candles[0].time
        const normalizedSymbol = this.normalizeKlineSymbol(this.symbol)
        const res = await klineApi.jumpToTime({
          symbol: normalizedSymbol,
          interval: this.currentTf,
          exchange: this.effectiveExchange,
          time: firstTime,
          before: 200,
          after: 0,
          limit: 200
        })
        const segment = (res?.data || []).map((k) => {
          const raw = Array.isArray(k)
            ? { time: Number(k[0]), open: Number(k[1]), high: Number(k[2]), low: Number(k[3]), close: Number(k[4]) }
            : { time: Number(k.time || k.timestamp || k.t || 0), open: Number(k.open ?? k.o ?? 0), high: Number(k.high ?? k.h ?? 0), low: Number(k.low ?? k.l ?? 0), close: Number(k.close ?? k.c ?? 0) }
          return { time: Math.floor(raw.time > 1e12 ? raw.time / 1000 : raw.time), open: raw.open, high: raw.high, low: raw.low, close: raw.close }
        }).filter((k) => k.time && Number.isFinite(k.close))
          .sort((a, b) => a.time - b.time)
        if (!segment.length) return
        // 去重：排除已存在的时间点
        const existingTimes = new Set(this.candles.map((c) => c.time))
        const newBars = segment.filter((k) => !existingTimes.has(k.time))
        if (!newBars.length) return
        const oldFirstTime = this.candles[0].time
        this.candles = [...newBars, ...this.candles]
        this.candlestickSeries?.setData(this.candles)
        // 调整逻辑范围防止视图跳动
        if (logicalBefore) {
          const newIndexOfOldFirst = this.candles.findIndex((b) => b.time === oldFirstTime)
          if (newIndexOfOldFirst >= 0) {
            timeScale?.setVisibleLogicalRange({
              from: Number(logicalBefore.from) + newIndexOfOldFirst,
              to: Number(logicalBefore.to) + newIndexOfOldFirst
            })
          }
        }
      } finally {
        this.isLoadingMore = false
      }
    },
    wsSubscribe() {
      // 使用与 fetchData 一致的符号规范化，确保 WebSocket 订阅的 symbol 格式正确
      const sym = this.normalizeKlineSymbol(this.symbol) || this.symbol
      this.subscribe(sym, this.currentTf)
    },
    normalizeKlineSymbol(symbol) {
      if (!symbol) return ''
      let s = String(symbol).trim().toUpperCase()
      if (!s.includes('/') && s.includes('-')) return s
      if (s.includes('/')) s = s.replace(/\//g, '-')
      if (s.endsWith('-SWAP') || s.includes('-SWAP')) return s
      return s.includes('-') ? s : `${s}-SWAP`
    },
    formatPrice(value) {
      const num = Number(value)
      if (!Number.isFinite(num)) return '-'
      if (num >= 10000) return num.toLocaleString('en-US', { maximumFractionDigits: 2 })
      if (num >= 1) return num.toFixed(num >= 100 ? 2 : 4)
      if (num >= 0.01) return num.toFixed(4)
      return num.toFixed(6)
    },
    formatTooltipTime(ts) {
      const d = new Date(ts * 1000)
      if (Number.isNaN(d.getTime())) return ''
      const month = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const hh = String(d.getHours()).padStart(2, '0')
      const mm = String(d.getMinutes()).padStart(2, '0')
      return `${month}/${day} ${hh}:${mm}`
    },
    formatNumber(value) {
      const num = Number(value || 0)
      if (!Number.isFinite(num)) return '0.00'
      return num.toFixed(2)
    }
  }
}
</script>

<style scoped>
.kline-chart {
  background: var(--surface-glass);
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 18px 16px 14px;
  color: var(--text);
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 10px;
}
.hdr-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}
.hdr-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}
.hdr-acct {
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}
.hdr-acct-label {
  color: var(--text-3);
  font-weight: 500;
}
.hdr-acct-val {
  color: var(--accent);
  font-weight: 600;
}
.hdr-acct-arrow {
  font-size: 12px;
  color: var(--text-4);
}
.hdr-bal-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hdr-bal-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0;
  min-height: 44px;
  justify-content: space-between;
}
.hdr-bal-label {
  font-size: 10px;
  color: var(--text-3);
  letter-spacing: 0.02em;
}
.hdr-bal-avail {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  color: var(--c-amber);
  line-height: 1.1;
}
.hdr-bal-total {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
}
.hdr-refresh {
  border: none !important;
  padding: 0 !important;
  height: auto !important;
  min-height: unset !important;
  line-height: 1 !important;
  font-size: 15px;
  color: var(--text-3);
  background: none !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.sym-symbol {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-2);
  text-transform: uppercase;
}
.price-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.sym-price {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.01em;
  font-variant-numeric: tabular-nums;
  color: var(--text);
  line-height: 1.1;
  transition: color 0.15s ease;
}
.sym-price.up,
.sym-price.down { color: var(--text); }
.sym-change {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  padding: 2px 0;
}
.sym-change.up { color: var(--up); }
.sym-change.down { color: var(--down); }
.chg-arrow {
  font-size: 9px;
  opacity: 0.85;
}
.hdr-time {
  margin-top: 2px; color: var(--text-2);
  font-weight: 600;
}

.chart-wrap {
  position: relative;
  width: 100%;
}
.lc-container {
  width: 100%;
  height: 100%;
}

.chart-loading,
.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--text-3);
  font-size: 12px;
  flex-direction: column;
}
.chart-empty .van-icon {
  font-size: 28px;
  color: var(--text-4);
}

.tf-tabs {
  margin: 10px 0 12px;
  display: flex;
  background: var(--surface-deep);
  border-radius: 12px;
  padding: 3px;
  gap: 2px;
}
.tf-tab {
  flex: 1;
  text-align: center;
  padding: 7px 0;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-2);
  border-radius: 10px;
  transition: all 0.2s ease;
  letter-spacing: 0.02em;
  user-select: none;
}
.tf-tab.active {
  background: var(--surface-raised);
  color: var(--text);
  box-shadow: var(--shadow-card);
}

/* Full-width mode: remove side padding so chart spans edge to edge */
.kline-chart--full {
  padding-left: 0;
  padding-right: 0;
  border-radius: 0;
  border-left: none;
  border-right: none;
}
.kline-chart--full .chart-header {
  padding-left: 16px;
  padding-right: 16px;
}
.kline-chart--full .tf-tabs {
  margin-left: 16px;
  margin-right: 16px;
}
</style>
