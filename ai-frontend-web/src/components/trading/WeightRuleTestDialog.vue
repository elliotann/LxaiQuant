<template>
  <el-dialog
    :model-value="visible"
    title="权重规则测试"
    width="800px"
    top="5vh"
    @close="handleClose"
    class="weight-rule-test-dialog"
  >
    <el-form :model="form" label-width="110px" size="small">
      <el-row :gutter="16">
        <el-col :span="8">
          <el-form-item label="方向">
            <el-select v-model="form.direction" placeholder="选择方向">
              <el-option label="BUY" value="BUY" />
              <el-option label="SELL" value="SELL" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="交易对">
            <el-input v-model="form.symbol" placeholder="如 BTC-USDT-SWAP" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="当前价格">
            <el-input-number v-model="form.currentPrice" :min="0" :step="100" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="市场趋势">
        <el-select v-model="form.marketTrend" placeholder="选填" clearable>
          <el-option label="BULLISH" value="BULLISH" />
          <el-option label="BEARISH" value="BEARISH" />
          <el-option label="NEUTRAL" value="NEUTRAL" />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">SMC 上下文（选填）</el-divider>
      <el-collapse v-model="activeContextSections">
        <el-collapse-item title="SMC 评分字段" name="scores">
          <el-row :gutter="16">
            <el-col :span="8" v-for="f in scoreFields" :key="f.key">
              <el-form-item :label="f.label">
                <el-input-number
                  v-model="form.context[f.key]"
                  :min="f.min" :max="f.max" :step="f.step || 0.1"
                  :precision="1"
                  size="small"
                  controls-position="right"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-collapse-item>
        <el-collapse-item title="SMC 状态字段" name="states">
          <el-row :gutter="16">
            <el-col :span="8" v-for="f in stateFields" :key="f.key">
              <el-form-item :label="f.label">
                <el-select v-model="form.context[f.key]" clearable size="small">
                  <el-option v-for="o in f.options" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-collapse-item>
        <el-collapse-item title="K线形态" name="patterns">
          <el-form-item label="检测到的形态">
            <el-select
              v-model="form.detectedPatterns"
              multiple
              placeholder="选择检测到的K线形态（可多选）"
              style="width:100%"
              size="small"
            >
              <el-option v-for="o in patternOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-collapse-item>
      </el-collapse>
    </el-form>

    <el-button type="primary" @click="runTest" :loading="testing" style="width:100%;margin-bottom:16px">
      {{ testing ? '测试中...' : '开始测试' }}
    </el-button>

    <template v-if="result">
      <el-alert
        :title="result.vetoed ? '信号被否决' : '信号通过'"
        :type="result.vetoed ? 'warning' : 'success'"
        :description="result.reason"
        show-icon
        :closable="false"
      />
      <el-descriptions :column="3" border size="small" style="margin-top:12px">
        <el-descriptions-item label="最终权重">
          <span :class="weightClass(result.finalWeight)">{{ result.finalWeight.toFixed(1) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="总评分">{{ result.totalScore.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="规则数">{{ result.traces?.length || 0 }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="result.traces" border size="small" style="margin-top:12px" max-height="320" row-key="ruleName">
        <el-table-column label="规则" prop="ruleName" width="140" />
        <el-table-column label="类型" width="70">
          <template #default="{ row }">
            <el-tag :type="row.ruleType === 'VETO' ? 'danger' : 'primary'" size="small">
              {{ row.ruleType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="匹配" width="60" align="center">
          <template #default="{ row }">
            <el-icon :color="row.matched ? '#67C23A' : '#F56C6C'" size="16">
              <Check v-if="row.matched" />
              <Close v-else />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column label="贡献分" width="80" align="right">
          <template #default="{ row }">{{ row.contribution.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason }}</template>
        </el-table-column>
        <el-table-column label="条件详情" width="120">
          <template #default="{ row }">
            <el-button text size="small" @click="showConditionDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">指标快照</el-divider>
      <div v-if="snapshotKeys.length > 0" class="snapshot-grid">
        <el-tag
          v-for="key in snapshotKeys"
          :key="key"
          size="small"
          class="snapshot-tag"
        >
          {{ key }}: {{ result.indicatorSnapshot[key]?.toFixed(4) }}
        </el-tag>
      </div>
      <el-empty v-else description="无指标快照数据" :image-size="60" />
    </template>
  </el-dialog>

  <el-dialog
    v-model="conditionDetailVisible"
    title="条件执行详情"
    width="650px"
    top="20vh"
  >
    <el-table :data="currentConditionTraces" border size="small" max-height="400">
      <el-table-column label="指标" prop="indicator" width="140" />
      <el-table-column label="运算符" prop="operator" width="70" />
      <el-table-column label="方向" prop="direction" width="60" />
      <el-table-column label="预期值" prop="expectedValue" width="90" />
      <el-table-column label="实际值" width="90">
        <template #default="{ row }">
          {{ row.actualValue !== null ? row.actualValue.toFixed(4) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="匹配" width="60" align="center">
        <template #default="{ row }">
          <el-icon :color="row.matched ? '#67C23A' : '#F56C6C'" size="16">
            <Check v-if="row.matched" />
            <Close v-else />
          </el-icon>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { Check, Close } from '@element-plus/icons-vue'
import { testRuleEngine, type RuleEvaluationResult, type RuleEvaluationTrace, type ConditionTrace } from '@/api/priceSignal'
import type { WeightRuleConfig } from '@/api/priceSignal'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  visible: boolean
  weightRules: WeightRuleConfig | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const testing = ref(false)
const result = ref<RuleEvaluationResult | null>(null)
const conditionDetailVisible = ref(false)
const currentConditionTraces = ref<ConditionTrace[]>([])
const activeContextSections = ref<string[]>([])

const patternOptions = [
  { label: '看涨孕线', value: 'BULLISH_HARAMI' },
  { label: '看跌孕线', value: 'BEARISH_HARAMI' },
  { label: '看涨吞没', value: 'BULLISH_ENGULFING' },
  { label: '看跌吞没', value: 'BEARISH_ENGULFING' },
  { label: '看涨锤子线', value: 'BULLISH_PIN_BAR' },
  { label: '看跌流星线', value: 'BEARISH_PIN_BAR' },
  { label: '十字星', value: 'DOJI' },
  { label: '晨星', value: 'MORNING_STAR' },
  { label: '黄昏星', value: 'EVENING_STAR' },
  { label: '三白兵', value: 'THREE_WHITE_SOLDIERS' },
  { label: '三只乌鸦', value: 'THREE_BLACK_CROWS' },
]

const scoreFields = [
  { key: 'smcTrendScore', label: '趋势评分', min: 0, max: 5 },
  { key: 'smcPositionScore', label: '位置评分', min: 0, max: 5 },
  { key: 'smcNetRR', label: '盈亏比', min: 0, max: 10, step: 0.1 },
  { key: 'smcPositionScore15m', label: '15m位置', min: 0, max: 5 },
  { key: 'smcRiskPercent', label: '风险%', min: 0, max: 10, step: 0.1 },
]

const stateFields = [
  {
    key: 'smcInsideOB', label: '在OB内',
    options: [{ label: '否', value: 0 }, { label: '是', value: 1 }],
  },
  {
    key: 'smcDirectionAligned', label: '方向一致',
    options: [{ label: '否', value: 0 }, { label: '是', value: 1 }],
  },
  {
    key: 'smcAlignment', label: '信号共振',
    options: [
      { label: '顺势做多', value: '顺势做多' },
      { label: '顺势做空', value: '顺势做空' },
      { label: '逆势', value: '逆势' },
      { label: '方向分歧', value: '方向分歧' },
    ],
  },
  {
    key: 'smcInSupplyZone', label: '在供应区',
    options: [{ label: '否', value: 0 }, { label: '是', value: 1 }],
  },
  {
    key: 'smcInDemandZone', label: '在需求区',
    options: [{ label: '否', value: 0 }, { label: '是', value: 1 }],
  },
  {
    key: 'swingBreakout', label: '摆动突破',
    options: [{ label: '否', value: 0 }, { label: '是', value: 1 }],
  },
  {
    key: 'weekday', label: '星期',
    options: [
      { label: '周一', value: 1 }, { label: '周二', value: 2 }, { label: '周三', value: 3 },
      { label: '周四', value: 4 }, { label: '周五', value: 5 }, { label: '周六', value: 6 },
      { label: '周日', value: 7 },
    ],
  },
  {
    key: 'swingRanging', label: '摆动盘整',
    options: [{ label: '否', value: false }, { label: '是', value: true }],
  },
  {
    key: 'smcObRanging', label: 'OB盘整',
    options: [{ label: '否', value: false }, { label: '是', value: true }],
  },
]

const form = reactive({
  direction: 'BUY',
  symbol: '',
  currentPrice: 0,
  marketTrend: '',
  detectedPatterns: [] as string[],
  context: {} as Record<string, any>,
})

const snapshotKeys = computed(() => {
  if (!result.value?.indicatorSnapshot) return []
  return Object.keys(result.value.indicatorSnapshot)
})

watch(() => props.visible, (v) => {
  if (v) {
    form.direction = 'BUY'
    form.symbol = ''
    form.currentPrice = 0
    form.marketTrend = ''
    form.detectedPatterns = []
    form.context = {}
    result.value = null
    activeContextSections.value = []
  }
})

function weightClass(w: number): string {
  if (w >= 1.5) return 'weight-high'
  if (w >= 1.0) return 'weight-mid'
  return 'weight-low'
}

async function runTest() {
  if (!props.weightRules) {
    ElMessage.warning('未配置权重规则，请先保存规则再测试')
    return
  }
  testing.value = true
  result.value = null
  try {
    const cleanedContext: Record<string, any> = {}
    for (const [k, v] of Object.entries(form.context)) {
      if (v !== undefined && v !== null && v !== '') {
        cleanedContext[k] = v
      }
    }
    if (form.detectedPatterns.length > 0) {
      cleanedContext.detectedPatterns = form.detectedPatterns.join(',')
    }

    const res = await testRuleEngine({
      direction: form.direction,
      symbol: form.symbol || undefined,
      currentPrice: form.currentPrice > 0 ? form.currentPrice : undefined,
      marketTrend: form.marketTrend || undefined,
      weightRules: props.weightRules,
      context: Object.keys(cleanedContext).length > 0 ? cleanedContext : undefined,
    })
    if (res.success && res.data) {
      result.value = res.data
    } else {
      ElMessage.error(res.message || '测试失败')
    }
  } catch (e: any) {
    ElMessage.error('请求失败: ' + (e.message || '未知错误'))
  } finally {
    testing.value = false
  }
}

function showConditionDetail(trace: RuleEvaluationTrace) {
  currentConditionTraces.value = trace.conditionResults || []
  conditionDetailVisible.value = true
}

function handleClose() {
  emit('close')
}
</script>

<style scoped>
.weight-rule-test-dialog :deep(.el-dialog__body) {
  padding-top: 16px;
}
.snapshot-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.snapshot-tag {
  font-family: 'Courier New', monospace;
}
.weight-high { color: #67C23A; font-weight: bold; }
.weight-mid { color: #E6A23C; font-weight: bold; }
.weight-low { color: #909399; }
</style>
