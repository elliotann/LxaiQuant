<template>
  <div class="lingsheai-panel">
    <div class="panel-container">
      <!-- ── Header ── -->
      <div class="panel-header">
        <span class="header-icon">🔍</span>
        <span class="header-title">SMC 行情看板 · {{ symbol }}</span>
        <span class="header-time">{{ displayTime }}</span>
        <span class="close-btn" @click="$emit('close')">✕</span>
      </div>

      <!-- ── 主趋势状态 ── -->
      <div class="section">
        <div class="section-title">主趋势状态</div>
        <div class="trend-bar-row">
          <div class="trend-bar-track">
            <div class="trend-bar-fill" :style="{ width: trendBarWidth }" :class="trendStateClass"></div>
          </div>
          <span class="trend-bar-label" :class="trendStateClass">{{ compositeStateText }}</span>
        </div>
        <div class="trend-suggestion">{{ trendSuggestion }}</div>
      </div>

      <!-- ── 方向矩阵 ── -->
      <div class="section">
        <div class="section-title">【方向矩阵】</div>
        <div class="matrix-row" v-if="matrix.length">
          <div class="matrix-col" v-for="item in matrix" :key="item.period">
            <div class="matrix-period">{{ item.period }}</div>
            <div class="matrix-bar" :class="directionClass(item.direction)">
              <div class="matrix-fill" :style="{ width: '80%' }"></div>
            </div>
            <div class="matrix-direction" :class="directionClass(item.direction)">{{ item.direction || '--' }}</div>
          </div>
        </div>
        <div class="matrix-footer">
          <span>周期共振 <span class="resonance-val" :class="resonanceClass">{{ core?.institutionResonance || '--' }}</span></span>
          <span>市场状态 <span class="market-val">{{ core?.marketGenre || '--' }}</span></span>
        </div>
      </div>

      <!-- ── 评估三层次 ── -->
      <div class="section">
        <div class="section-title">【评估三层次】</div>
        <div class="three-col-box">
          <!-- 战略层 4H -->
          <div class="layer-col">
            <div class="layer-header">战略层 (4H)</div>
            <div class="layer-row"><span class="layer-key">趋势</span><span class="layer-bar"><span class="bar-fill" :style="{ width: '80%' }" :class="trend4hCls"></span></span><span class="layer-val" :class="trend4hCls">{{ trend4hLabel }}</span></div>
            <div class="layer-row"><span class="layer-key">波次</span><span class="layer-val">{{ evaluate.wavePhase4h }}({{ evaluate.waveIndex4h }})</span></div>
            <div class="layer-row"><span class="layer-key">位置比</span><span class="layer-val">{{ pos4hText }}<span class="layer-hint" v-if="pos4hHint"> ({{ pos4hHint }})</span></span></div>
            <div class="layer-row"><span class="layer-key">翻转</span><span class="layer-val">{{ evaluate.flipCount4h }}次{{ flipSmoothLabel }}</span></div>
            <div class="layer-row"><span class="layer-key">年龄</span><span class="layer-val">{{ evaluate.structureAge4h }}根</span></div>
          </div>
          <!-- 战术层 1H -->
          <div class="layer-col">
            <div class="layer-header">战术层 (1H)</div>
            <div class="layer-row"><span class="layer-key">方向</span><span class="layer-bar"><span class="bar-fill" :style="{ width: '80%' }" :class="trend1hCls"></span></span><span class="layer-val" :class="trend1hCls">{{ trend1hLabel }}</span></div>
            <div class="layer-row"><span class="layer-key">波次</span><span class="layer-val">{{ evaluate.wavePhase1h }}({{ evaluate.waveIndex1h }})</span></div>
            <div class="layer-row"><span class="layer-key">位置比</span><span class="layer-val">{{ pos1hText }}<span class="layer-hint" v-if="pos1hHint"> ({{ pos1hHint }})</span></span></div>
            <div class="layer-row"><span class="layer-key">年龄</span><span class="layer-val">{{ evaluate.structureAge1h }}根</span></div>
            <div class="layer-row"><span class="layer-key">BOS</span><span class="layer-val">{{ bos1hLabel }}</span></div>
          </div>
          <!-- 执行层 15M -->
          <div class="layer-col">
            <div class="layer-header">执行层 (15M)</div>
            <div class="layer-row"><span class="layer-key">波次</span><span class="layer-val">{{ evaluate.wavePhase15m || '--' }}({{ evaluate.waveIndex15m }})</span></div>
            <div class="layer-row"><span class="layer-key">位置比</span><span class="layer-val">{{ pos15mText }}</span></div>
            <div class="layer-row"><span class="layer-key">Internal</span><span class="layer-bar"><span class="bar-fill" :style="{ width: '75%' }" :class="trend15mCls"></span></span><span class="layer-val" :class="trend15mCls">{{ internalTrendLabel }}</span></div>
            <div class="layer-row"><span class="layer-key">年龄</span><span class="layer-val">{{ evaluate.structureAge15m }}根</span></div>
            <div class="layer-row"><span class="layer-key">BOS</span><span class="layer-val">{{ bos15mLabel }}</span></div>
          </div>
        </div>
      </div>

      <!-- ── 综合评估 + 执行参数 ── -->
      <div class="section">
        <div class="two-col-section">
          <div class="assess-box">
            <div class="inner-box-title">【综合评估】</div>
            <div class="assess-row"><span class="assess-key">评分</span><span class="assess-stars">{{ scoreStars }}</span><span class="assess-val">{{ scoreFormatted }}</span></div>
            <div class="assess-row"><span class="assess-key">建议乘数</span><span class="assess-val" :class="multiplierCls">{{ evaluate.suggestedMultiplier != null ? evaluate.suggestedMultiplier.toFixed(1) : '--' }}<span class="assess-hint" v-if="multiplierHint"> ({{ multiplierHint }})</span></span></div>
            <div class="assess-row"><span class="assess-key">盈亏比</span><span class="assess-val" :class="rrClass">{{ evaluate.riskRewardRatio != null ? evaluate.riskRewardRatio.toFixed(2) + ':1' : '--' }}</span></div>
            <div class="assess-row"><span class="assess-key">风险比例</span><span class="assess-val">{{ evaluate.riskPercent != null ? evaluate.riskPercent.toFixed(1) + '%' : '--' }}</span></div>
            <div class="assess-row"><span class="assess-key">混沌特例</span><span class="assess-val" :class="chaosCls">{{ chaosText }}</span></div>
            <div class="assess-row"><span class="assess-key">阶段说明</span><span class="assess-val">{{ evaluate.phaseDescription || '--' }}</span></div>
          </div>
          <div class="exec-box">
            <div class="inner-box-title">【执行参数】</div>
            <div class="exec-row" v-for="(level, idx) in execLevels" :key="idx">
              <span class="exec-action" :class="execActionClass(level)">{{ getExecActionLabel(level) }}</span>
              <span class="exec-price">{{ formatPrice(level.price) }}</span>
              <span class="exec-dist" :class="distClass(level)">{{ level.distancePercent != null ? (level.distancePercent >= 0 ? '+' : '') + level.distancePercent.toFixed(1) + '%' : '--' }}</span>
              <span class="exec-meta">{{ level.period }} {{ formatType(level.type) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getMultiPeriod } from '@/api/smc'

interface MatrixItem { period: string; direction: string }
interface CoreData {
  institutionResonance: string
  marketGenre: string
  compositeState?: string
  trendState?: string
  updateTime: string
}
interface CriticalLevel {
  type: string; side: string; price: number; high: number; low: number
  period: string; action: string; priority: number; distancePercent?: number
}
interface EvaluateData {
  riskRewardRatio?: number; riskPercent?: number
  wavePhase4h?: string; waveIndex4h?: number; flipCount4h?: number
  wavePhase1h?: string; waveIndex1h?: number
  wavePhase15m?: string; waveIndex15m?: number
  chaosException?: boolean; chaosForcedMultiplier?: number
  positionRatio4h?: number; positionRatio1h?: number; positionRatio15m?: number
  structureAge1h?: number; structureAge15m?: number; structureAge4h?: number
  swingTrend4h?: number; swingTrend1h?: number; swingTrend15m?: number
  swingBullishBOS1h?: boolean; swingBearishBOS1h?: boolean
  swingBullishBOS15m?: boolean; swingBearishBOS15m?: boolean
  compositeScore?: number; suggestedMultiplier?: number; phaseDescription?: string
  timestamp?: number
}
interface MultiPeriodResponse {
  symbol: string; matrix: MatrixItem[]; core: CoreData; criticalLevels: CriticalLevel[]
  structureData?: EvaluateData
}

const props = defineProps<{ symbol: string }>()
const emit = defineEmits<{ close: [] }>()

const data = ref<MultiPeriodResponse | null>(null)
const evaluate = ref<EvaluateData | null>(null)
let timer: ReturnType<typeof setInterval> | null = null

const matrix = computed(() => data.value?.matrix || [])
const core = computed(() => data.value?.core || null)
const criticalLevels = computed(() => data.value?.criticalLevels || [])

// ── Time ──
const displayTime = computed(() => {
  const ts = evaluate.value?.timestamp
  return ts ? new Date(ts).toLocaleString('zh-CN', { hour12: false }) : (data.value?.core?.updateTime || '')
})

// ── Trend state ──
const compositeStateText = computed(() => {
  const cs = core.value?.compositeState
  const ts = core.value?.trendState
  return (cs && cs !== '') ? cs : (ts || '--')
})

const stateMap: Record<string, { cls: string, suggestion: string, bar: string }> = {
  '强上升·健康':          { cls: 'trend-strong-up', suggestion: '持仓多单，移动止损', bar: '80%' },
  '强上升·浅回调':        { cls: 'trend-strong-up', suggestion: '多单持有，关注浅回调', bar: '75%' },
  '强上升·预警回调(1H)': { cls: 'trend-up-pullback', suggestion: '1H出现预警，关注是否转回调', bar: '65%' },
  '强上升·预警回调(4H)': { cls: 'trend-up-pullback', suggestion: '4H内部预警，警惕深度回调', bar: '60%' },
  '强上升·确认回调':      { cls: 'trend-up-pullback', suggestion: '确认回调，观望', bar: '50%' },
  '上升回调·进行中':      { cls: 'trend-up-pullback', suggestion: '回调进行中，观望', bar: '45%' },
  '上升回调·筑底':        { cls: 'trend-up-pullback', suggestion: '回调筑底，关注做多机会', bar: '40%' },
  '上升回调·失败':        { cls: 'trend-up-end', suggestion: '回调失败，趋势转空', bar: '30%' },
  '上升末端·延续下跌':    { cls: 'trend-up-end', suggestion: '多单离场，不开新多', bar: '25%' },
  '上升末端·转势确认':    { cls: 'trend-up-end', suggestion: '转势确认，考虑做空', bar: '20%' },
  '强下降·健康':          { cls: 'trend-strong-down', suggestion: '持仓空单，移动止损', bar: '80%' },
  '强下降·浅反弹':        { cls: 'trend-strong-down', suggestion: '空单持有，关注浅反弹', bar: '75%' },
  '强下降·预警反弹(1H)': { cls: 'trend-down-bounce', suggestion: '1H出现预警，关注是否转反弹', bar: '65%' },
  '强下降·预警反弹(4H)': { cls: 'trend-down-bounce', suggestion: '4H内部预警，警惕深度反弹', bar: '60%' },
  '强下降·确认反弹':      { cls: 'trend-down-bounce', suggestion: '确认反弹，观望', bar: '50%' },
  '下降反弹·进行中':      { cls: 'trend-down-bounce', suggestion: '反弹进行中，观望', bar: '45%' },
  '下降反弹·筑顶':        { cls: 'trend-down-bounce', suggestion: '反弹筑顶，关注做空机会', bar: '40%' },
  '下降反弹·失败':        { cls: 'trend-down-end', suggestion: '反弹失败，趋势转多', bar: '30%' },
  '下降末端·延续反弹':    { cls: 'trend-down-end', suggestion: '空单离场，不开新空', bar: '25%' },
  '下降末端·转势确认':    { cls: 'trend-down-end', suggestion: '转势确认，考虑做多', bar: '20%' },
  '完全震荡':              { cls: 'trend-neutral', suggestion: '停止趋势策略', bar: '50%' },
}
const legacyStateMap: Record<string, { cls: string, suggestion: string, bar: string }> = {
  '强上升':   { cls: 'trend-strong-up', suggestion: '持仓多单，移动止损', bar: '80%' },
  '上升回调': { cls: 'trend-up-pullback', suggestion: '观望，等待回调结束做多', bar: '50%' },
  '上升末端': { cls: 'trend-up-end', suggestion: '多单离场，不开新多', bar: '25%' },
  '强下降':   { cls: 'trend-strong-down', suggestion: '持仓空单，移动止损', bar: '80%' },
  '下降反弹': { cls: 'trend-down-bounce', suggestion: '观望，等待反弹结束做空', bar: '50%' },
  '下降末端': { cls: 'trend-down-end', suggestion: '空单离场，不开新空', bar: '25%' },
  '完全震荡': { cls: 'trend-neutral', suggestion: '停止趋势策略', bar: '50%' },
}

const displayState = computed(() => {
  const key = core.value?.compositeState || core.value?.trendState || ''
  return stateMap[key] || legacyStateMap[key] || { cls: '', suggestion: '', bar: '50%' }
})

const trendStateClass = computed(() => displayState.value.cls)
const trendSuggestion = computed(() => displayState.value.suggestion)
const trendBarWidth = computed(() => displayState.value.bar)

// ── Direction helpers ──
function directionClass(dir: string) {
  if (dir === '多头') return 'dir-bullish'
  if (dir === '空头') return 'dir-bearish'
  return 'dir-neutral'
}

function dirLabel(v: number | undefined | null): string {
  if (v == null) return '--'
  if (v > 0) return '多头'
  if (v < 0) return '空头'
  return '震荡'
}

const resonanceClass = computed(() => {
  const v = core.value?.institutionResonance
  if (v === '多方共振') return 'resonance-bullish'
  if (v === '空方共振') return 'resonance-bearish'
  return ''
})

// ── 评估三层次 ──
const trend4hLabel = computed(() => dirLabel(evaluate.value?.swingTrend4h))
const trend4hCls = computed(() => {
  const v = evaluate.value?.swingTrend4h
  if (v == null || v === 0) return ''
  return v > 0 ? 'dir-bullish' : 'dir-bearish'
})

const trend1hLabel = computed(() => dirLabel(evaluate.value?.swingTrend1h))
const trend1hCls = computed(() => {
  const v = evaluate.value?.swingTrend1h
  if (v == null || v === 0) return ''
  return v > 0 ? 'dir-bullish' : 'dir-bearish'
})

const trend15mLabel = computed(() => dirLabel(evaluate.value?.swingTrend15m))
const trend15mCls = computed(() => {
  const v = evaluate.value?.swingTrend15m
  if (v == null || v === 0) return ''
  return v > 0 ? 'dir-bullish' : 'dir-bearish'
})

const internalTrendLabel = computed(() => {
  const v = evaluate.value?.swingTrend15m
  if (v == null) return '--'
  return (v > 0 ? '多头' : '空头') + '共振'
})

const flipSmoothLabel = computed(() => {
  const fc = evaluate.value?.flipCount4h
  if (fc == null) return ''
  return fc <= 1 ? ' (流畅)' : ''
})

const pos4hText = computed(() => {
  const v = evaluate.value?.positionRatio4h
  if (v == null || isNaN(v)) return '--'
  return v.toFixed(2)
})
const pos4hHint = computed(() => {
  const v = evaluate.value?.positionRatio4h
  if (v == null || isNaN(v)) return ''
  if (v >= 0.8) return '阻力/支撑'
  if (v >= 0.65) return '阻力'
  if (v <= 0.2) return '支撑'
  return ''
})

const pos1hText = computed(() => {
  const v = evaluate.value?.positionRatio1h
  if (v == null || isNaN(v)) return '--'
  return v.toFixed(2)
})
const pos1hHint = computed(() => {
  const v = evaluate.value?.positionRatio1h
  if (v == null || isNaN(v)) return ''
  if (v >= 0.8) return '阻力/支撑'
  if (v >= 0.65) return '阻力'
  if (v <= 0.2) return '支撑'
  return ''
})

const pos15mText = computed(() => {
  const v = evaluate.value?.positionRatio15m
  if (v == null || isNaN(v)) return '--'
  return v.toFixed(2)
})

const bos1hLabel = computed(() => {
  const e = evaluate.value
  if (e?.swingBearishBOS1h) return '↓ (有)'
  if (e?.swingBullishBOS1h) return '↑ (有)'
  return '无'
})

const bos15mLabel = computed(() => {
  const e = evaluate.value
  if (e?.swingBearishBOS15m) return '↓ (有)'
  if (e?.swingBullishBOS15m) return '↑ (有)'
  return '无'
})

// ── 综合评估 ──
const scoreFormatted = computed(() => {
  const s = evaluate.value?.compositeScore
  return s != null ? s.toFixed(1) : '--'
})

const scoreStars = computed(() => {
  const s = evaluate.value?.compositeScore
  if (s == null) return ''
  const full = Math.min(5, Math.max(0, Math.floor(s)))
  return '★'.repeat(full) + '☆'.repeat(5 - full)
})

const multiplierCls = computed(() => {
  const m = evaluate.value?.suggestedMultiplier
  if (m == null) return ''
  if (m >= 1.5) return 'mult-high'
  if (m >= 1.0) return 'mult-mid'
  return 'mult-low'
})

const multiplierHint = computed(() => {
  const m = evaluate.value?.suggestedMultiplier
  if (m == null) return ''
  if (m >= 2.0) return '重仓'
  if (m >= 1.5) return '偏重'
  if (m >= 1.0) return '正常'
  if (m >= 0.5) return '轻仓'
  return '不交易'
})

const rrClass = computed(() => {
  const rr = evaluate.value?.riskRewardRatio
  if (rr == null) return ''
  if (rr >= 2) return 'rr-good'
  if (rr >= 1) return 'rr-ok'
  return 'rr-bad'
})

const chaosText = computed(() => {
  const e = evaluate.value
  if (e?.chaosException == null) return '--'
  return e.chaosException
    ? `是 (x${e.chaosForcedMultiplier != null ? e.chaosForcedMultiplier.toFixed(1) : '?'})`
    : '未触发'
})
const chaosCls = computed(() => {
  return evaluate.value?.chaosException ? 'chaos-yes' : 'chaos-no'
})

// ── 执行参数 ──
const execLevels = computed(() => {
  const levels = criticalLevels.value
  // Filter to entry/sl/tp levels
  return levels.filter(l => l.action !== 'none')
})

function getExecActionLabel(level: CriticalLevel) {
  const act = level.action
  if (act === '入场') return `入场${level.side === '看跌' ? '做空' : '做多'}`
  if (act === '止损') return '止损'
  return `止盈${level.priority}`
}

function execActionClass(level: CriticalLevel) {
  const act = level.action
  if (act === '入场') return level.side === '看跌' ? 'exec-entry-bear' : 'exec-entry-bull'
  if (act === '止损') return 'exec-sl'
  return 'exec-tp'
}

function distClass(level: CriticalLevel) {
  if (level.distancePercent == null) return ''
  if (level.side === '看跌') return level.distancePercent >= 0 ? 'dist-pos' : 'dist-neg'
  return level.distancePercent >= 0 ? 'dist-pos' : 'dist-neg'
}

function formatType(type: string) {
  if (type === '看涨订单块' || type === '看跌订单块') return 'OB'
  if (type.includes('止损')) return '止损位'
  if (type === '公允价值缺口' || type === '看涨公允价值缺口' || type === '看跌公允价值缺口') return 'FVG'
  if (type === '流动性池') return 'EQH/EQL'
  return type
}

function formatPrice(val: number | undefined | null) {
  if (val == null) return '--'
  return val.toFixed(4)
}

// ── Data fetching ──
async function fetchData() {
  try {
    const mRes = await getMultiPeriod(props.symbol)
    data.value = mRes.data
    evaluate.value = mRes.data?.structureData || null
  } catch (e) {
    console.error('[行情看板] 获取数据失败', e)
  }
}

onMounted(() => {
  fetchData()
  timer = setInterval(fetchData, 30_000)
})

onUnmounted(() => {
  if (timer) { clearInterval(timer); timer = null }
})
</script>

<style scoped>
/* ── Container ── */
.lingsheai-panel {
  position: fixed;
  top: 150px;
  right: 30px;
  z-index: 1000;
  width: 720px;
  font-size: 12px;
  font-family: 'Consolas', 'Courier New', monospace;
  user-select: none;
  line-height: 1.5;
}
.panel-container {
  background: rgba(22, 22, 32, 0.97);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  color: #d0d0d0;
  padding: 10px 12px;
}

/* ── Header ── */
.panel-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: 6px;
}
.header-icon { font-size: 14px; }
.header-title { font-weight: 700; font-size: 13px; color: #a78bfa; flex: 1; }
.header-time { font-size: 11px; color: #777; }
.close-btn { cursor: pointer; color: #777; font-size: 13px; padding: 0 4px; }
.close-btn:hover { color: #ff6b6b; }

/* ── Section ── */
.section { margin-bottom: 8px; }
.section-title { font-weight: 600; font-size: 12px; color: #999; margin-bottom: 4px; }

/* ── Main Trend ── */
.trend-bar-row { display: flex; align-items: center; gap: 8px; margin: 4px 0; }
.trend-bar-track {
  flex: 1; height: 14px; background: rgba(255,255,255,0.06);
  border-radius: 3px; overflow: hidden; position: relative;
}
.trend-bar-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
.trend-bar-fill.trend-strong-up { background: linear-gradient(90deg, #22c55e, #4ade80); }
.trend-bar-fill.trend-up-pullback { background: linear-gradient(90deg, #eab308, #facc15); }
.trend-bar-fill.trend-up-end { background: linear-gradient(90deg, #f97316, #fb923c); }
.trend-bar-fill.trend-strong-down { background: linear-gradient(90deg, #ef4444, #f87171); }
.trend-bar-fill.trend-down-bounce { background: linear-gradient(90deg, #eab308, #facc15); }
.trend-bar-fill.trend-down-end { background: linear-gradient(90deg, #22c55e, #4ade80); }
.trend-bar-fill.trend-neutral { background: linear-gradient(90deg, #6b7280, #9ca3af); }

.trend-bar-label { font-weight: 600; font-size: 12px; white-space: nowrap; min-width: 140px; }
.trend-bar-label.trend-strong-up { color: #4ade80; }
.trend-bar-label.trend-up-pullback { color: #eab308; }
.trend-bar-label.trend-up-end { color: #fb923c; }
.trend-bar-label.trend-strong-down { color: #f87171; }
.trend-bar-label.trend-down-bounce { color: #eab308; }
.trend-bar-label.trend-down-end { color: #4ade80; }
.trend-bar-label.trend-neutral { color: #9ca3af; }

.trend-suggestion { font-size: 11px; color: #888; padding-left: 2px; }

/* ── Matrix ── */
.matrix-row { display: flex; gap: 12px; margin: 4px 0; }
.matrix-col { flex: 1; text-align: center; }
.matrix-period { font-size: 11px; color: #888; margin-bottom: 2px; }
.matrix-bar { height: 10px; border-radius: 2px; overflow: hidden; background: rgba(255,255,255,0.06); margin-bottom: 2px; }
.matrix-bar.dir-bullish .matrix-fill { height: 100%; background: #22c55e; }
.matrix-bar.dir-bearish .matrix-fill { height: 100%; background: #ef4444; }
.matrix-bar.dir-neutral .matrix-fill { height: 100%; background: #6b7280; }
.matrix-direction { font-size: 11px; font-weight: 500; }
.matrix-direction.dir-bullish { color: #4ade80; }
.matrix-direction.dir-bearish { color: #f87171; }
.matrix-direction.dir-neutral { color: #9ca3af; }

.matrix-footer { display: flex; gap: 16px; font-size: 11px; color: #888; margin-top: 2px; }
.resonance-val { font-weight: 600; }
.resonance-val.resonance-bullish { color: #4ade80; }
.resonance-val.resonance-bearish { color: #f87171; }
.market-val { color: #aaa; font-weight: 500; }

/* ── Three Column Assessment ── */
.three-col-box {
  display: flex;
  gap: 0;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 4px;
  overflow: hidden;
}
.layer-col { flex: 1; padding: 6px 8px; }
.layer-col + .layer-col { border-left: 1px solid rgba(255,255,255,0.08); }
.layer-header { font-weight: 600; font-size: 11px; color: #a78bfa; margin-bottom: 4px; padding-bottom: 2px; border-bottom: 1px solid rgba(255,255,255,0.06); }
.layer-row { display: flex; align-items: center; gap: 4px; padding: 2px 0; font-size: 11px; }
.layer-key { color: #888; min-width: 44px; }
.layer-val { color: #ddd; font-weight: 500; }
.layer-hint { color: #888; font-weight: 400; font-size: 10px; }
.layer-bar { flex: 1; height: 8px; background: rgba(255,255,255,0.06); border-radius: 2px; overflow: hidden; max-width: 60px; }
.layer-bar .bar-fill { height: 100%; border-radius: 2px; }
.layer-bar .bar-fill.dir-bullish { background: #22c55e; }
.layer-bar .bar-fill.dir-bearish { background: #ef4444; }
.dir-bullish { color: #4ade80; }
.dir-bearish { color: #f87171; }

/* ── Two Column ── */
.two-col-section { display: flex; gap: 10px; }
.assess-box, .exec-box {
  flex: 1;
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 4px;
  padding: 6px 8px;
}
.inner-box-title { font-weight: 600; font-size: 11px; color: #a78bfa; margin-bottom: 4px; }
.assess-row, .exec-row { display: flex; align-items: center; gap: 4px; padding: 2px 0; font-size: 11px; }
.assess-key, .exec-action { color: #888; min-width: 56px; }
.assess-val { color: #ddd; font-weight: 500; }
.assess-stars { color: #eab308; font-size: 10px; letter-spacing: 1px; margin-right: 2px; }
.assess-hint { color: #888; font-weight: 400; font-size: 10px; }

.mult-high { color: #ef4444; font-weight: 600; }
.mult-mid { color: #eab308; font-weight: 500; }
.mult-low { color: #9ca3af; }

.rr-good { color: #4ade80; }
.rr-ok { color: #eab308; }
.rr-bad { color: #f87171; }
.chaos-yes { color: #f87171; }
.chaos-no { color: #4ade80; }

.exec-row { justify-content: space-between; }
.exec-action { min-width: 64px; font-weight: 500; }
.exec-action.exec-entry-bull { color: #4ade80; }
.exec-action.exec-entry-bear { color: #f87171; }
.exec-action.exec-sl { color: #f97316; }
.exec-action.exec-tp { color: #38bdf8; }
.exec-price { color: #ddd; font-weight: 500; }
.exec-meta { color: #888; font-size: 10px; }

.exec-dist { min-width: 56px; text-align: right; font-size: 10px; }

.dist-pos { color: #4ade80; }
.dist-neg { color: #f87171; }
</style>
