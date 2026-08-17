<template>
  <div class="batch-exit-detail">
    <div class="detail-header">
      <span class="detail-title">分批出场明细</span>
      <span class="detail-type">{{ batchTypeLabel }}</span>
    </div>
    <div class="detail-table">
      <div class="table-header">
        <span class="col-index">档位</span>
        <span class="col-status">状态</span>
        <span class="col-target">比例</span>
        <span class="col-trigger">触发价</span>
        <span class="col-volume">已平量</span>
        <span class="col-price">成交均价</span>
        <span class="col-income">盈亏</span>
      </div>
      <div class="table-body">
        <div
          v-for="(plan, i) in batchExitPlans"
          :key="i"
          class="table-row"
          :class="{ triggered: i < triggeredCount, active: i === triggeredCount }"
        >
          <span class="col-index">档{{ i + 1 }}</span>
          <span class="col-status">
            <span v-if="i < triggeredCount" class="status-triggered">已触发</span>
            <span v-else-if="i === triggeredCount" class="status-active">进行中</span>
            <span v-else class="status-pending">待触发</span>
          </span>
          <span class="col-target">{{ plan.ratio ? (plan.ratio * 100).toFixed(1) + '%' : (plan.percent || 0) + '%' }}</span>
          <span class="col-trigger">{{ batchExits[i] ? formatPrice(batchExits[i].triggerPrice) : '-' }}</span>
          <span class="col-volume">{{ batchExits[i] ? formatQuantity(batchExits[i].closeVolume) : '-' }}</span>
          <span class="col-price">{{ batchExits[i] ? formatPrice(batchExits[i].closeAvgPrice) : '-' }}</span>
          <span class="col-income" :class="incomeClass(batchExits[i]?.income)">
            {{ batchExits[i] ? formatIncome(batchExits[i].income) : '-' }}
          </span>
        </div>
      </div>
    </div>
    <div v-if="batchExitPlans.length === 0" class="empty-hint">无分批出场配置</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  batchExitType?: string | null
  currentBatchIndex?: number | null
  batchExitPlans?: any[]
  batchExits?: any[]
}>()

const triggeredCount = computed(() => props.batchExits?.length || 0)

const batchTypeLabel = computed(() => {
  const map: Record<string, string> = {
    BATCH_TAKE_PROFIT: '分批止盈',
    BATCH_STOP_LOSS: '分批止损',
    BATCH_TRAILING_GAIN: '分批移动止盈',
    BATCH_TRAILING_LOSS: '分批移动止损',
  }
  return props.batchExitType ? map[props.batchExitType] || props.batchExitType : ''
})

const formatPrice = (v: any) => v != null ? Number(v).toFixed(4) : '-'
const formatQuantity = (v: any) => v != null ? Number(v).toFixed(4) : '-'
const formatIncome = (v: any) => v != null ? (Number(v) >= 0 ? '+' : '') + Number(v).toFixed(2) : '-'
const incomeClass = (v: any) => v != null ? (Number(v) >= 0 ? 'income-positive' : 'income-negative') : ''
</script>

<style scoped>
.batch-exit-detail {
  background: #f9f9f9;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 8px;
  margin: 4px 0;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.detail-title {
  font-size: 12px;
  font-weight: 600;
  color: #333;
}
.detail-type {
  font-size: 11px;
  color: #999;
  background: #eef2f7;
  padding: 0 6px;
  border-radius: 3px;
  line-height: 18px;
}
.detail-table {
  font-size: 11px;
}
.table-header {
  display: flex;
  background: #eef2f7;
  border-radius: 3px;
  padding: 4px 6px;
  font-weight: 600;
  color: #666;
  gap: 4px;
}
.table-body {
  margin-top: 2px;
}
.table-row {
  display: flex;
  padding: 3px 6px;
  border-bottom: 1px solid #f0f0f0;
  gap: 4px;
}
.table-row:last-child {
  border-bottom: none;
}
.table-row.triggered {
  background: rgba(82, 196, 26, 0.04);
}
.table-row.active {
  background: rgba(250, 173, 20, 0.06);
}
.col-index { width: 30px; flex-shrink: 0; }
.col-status { width: 44px; flex-shrink: 0; }
.col-target { width: 48px; flex-shrink: 0; text-align: right; }
.col-trigger { flex: 1; text-align: right; font-family: monospace; }
.col-volume { flex: 1; text-align: right; font-family: monospace; }
.col-price { flex: 1; text-align: right; font-family: monospace; }
.col-income { width: 70px; flex-shrink: 0; text-align: right; font-family: monospace; }

.status-triggered { color: #52c41a; font-weight: 500; }
.status-active { color: #faad14; font-weight: 500; }
.status-pending { color: #bbb; }

.income-positive { color: #52c41a; }
.income-negative { color: #f5222d; }

.empty-hint {
  text-align: center;
  color: #bbb;
  font-size: 11px;
  padding: 12px 0;
}
</style>
