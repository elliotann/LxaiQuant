<template>
  <div class="batch-exit-progress" @click.stop>
    <div class="batch-progress-bar" :title="progressTitle">
      <div
        v-for="(plan, i) in batchExitPlans"
        :key="i"
        class="batch-segment"
        :class="segmentClass(i)"
        :style="segmentStyle(plan.ratio || plan.percent)"
      >
        <span v-if="batchExitPlans.length <= 5" class="segment-index">{{ i + 1 }}</span>
      </div>
    </div>
    <div class="batch-summary">
      <span class="batch-count">{{ triggeredCount }}/{{ batchExitPlans.length }}</span>
      <span v-if="batchExitType" class="batch-type-label">{{ batchTypeLabel }}</span>
    </div>
    <div v-if="showDetail" class="batch-detail-tooltip">
      <div v-for="(plan, i) in batchExitPlans" :key="i" class="detail-row" :class="{ triggered: i < triggeredCount }">
        <span class="detail-index">档{{ i + 1 }}</span>
        <span class="detail-target">{{ plan.ratio ? (plan.ratio * 100).toFixed(1) + '%' : (plan.percent || 0) + '%' }}</span>
        <span class="detail-status">{{ i < triggeredCount ? '已触发' : '待触发' }}</span>
        <span v-if="i < triggeredCount && batchExits[i]" class="detail-price">
          @{{ formatPrice(batchExits[i].triggerPrice) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  batchExitType?: string | null
  currentBatchIndex?: number | null
  batchExitPlans?: any[]
  batchExits?: any[]
  showDetail?: boolean
}>(), {
  batchExitType: null,
  currentBatchIndex: null,
  batchExitPlans: () => [],
  batchExits: () => [],
  showDetail: false,
})

const triggeredCount = computed(() => props.batchExits.length)

const progressTitle = computed(() => {
  const total = props.batchExitPlans.length
  const done = triggeredCount.value
  return `分批出场 ${done}/${total}`
})

const batchTypeLabel = computed(() => {
  const map: Record<string, string> = {
    BATCH_TAKE_PROFIT: '分批止盈',
    BATCH_STOP_LOSS: '分批止损',
    BATCH_TRAILING_GAIN: '分批移动止盈',
    BATCH_TRAILING_LOSS: '分批移动止损',
  }
  return props.batchExitType ? map[props.batchExitType] || props.batchExitType : ''
})

const segmentClass = (i: number) => ({
  triggered: i < triggeredCount.value,
  active: i === triggeredCount.value,
  pending: i > triggeredCount.value,
})

const segmentStyle = (ratio?: number | string) => {
  const r = ratio ? Number(ratio) : undefined
  return r ? { flex: r } : {}
}

const formatPrice = (price: any) => {
  if (price == null) return '-'
  return Number(price).toFixed(4)
}
</script>

<style scoped>
.batch-exit-progress {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}
.batch-progress-bar {
  display: flex;
  width: 64px;
  height: 12px;
  border-radius: 3px;
  overflow: hidden;
  background: #e8e8e8;
  border: 1px solid #d0d0d0;
}
.batch-segment {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  min-width: 6px;
}
.batch-segment.triggered {
  background: #52c41a;
}
.batch-segment.active {
  background: #faad14;
}
.batch-segment.pending {
  background: #e8e8e8;
}
.batch-segment.triggered + .batch-segment {
  border-left: 1px solid rgba(255,255,255,0.4);
}
.segment-index {
  font-size: 9px;
  color: #fff;
  line-height: 1;
  font-weight: 600;
}
.batch-segment.pending .segment-index {
  color: #999;
}
.batch-summary {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.batch-count {
  font-size: 11px;
  font-weight: 600;
  color: #333;
}
.batch-type-label {
  font-size: 10px;
  color: #999;
}
.batch-detail-tooltip {
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 100;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  padding: 6px 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  white-space: nowrap;
  margin-top: 4px;
}
.detail-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  padding: 2px 0;
  color: #999;
}
.detail-row.triggered {
  color: #333;
}
.detail-index {
  min-width: 18px;
  font-weight: 600;
}
.detail-target {
  min-width: 38px;
  text-align: right;
}
.detail-status {
  min-width: 34px;
  color: #999;
}
.detail-row.triggered .detail-status {
  color: #52c41a;
}
.detail-price {
  color: #666;
  font-family: monospace;
}
</style>
