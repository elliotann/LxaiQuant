<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    title="AI 智能创建机器人"
    :width="580"
    :close-on-click-modal="false"
    destroy-on-close
    top="5vh"
  >
    <div class="ai-dialog-body">
      <div v-if="!result && !loading" class="input-section">
        <div class="market-type-selector">
          <span class="selector-label">交易类型</span>
          <el-radio-group v-model="marketType" size="small">
            <el-radio-button value="spot">现货</el-radio-button>
            <el-radio-button value="swap">合约/永续</el-radio-button>
          </el-radio-group>
        </div>
        <el-input
          v-model="userInput"
          type="textarea"
          :rows="4"
          placeholder="描述你的交易需求，例如：我想做 BTC 的趋势跟踪策略，4小时周期，风险中等"
          :disabled="loading"
        />
        <div class="quick-prompts">
          <div class="prompt-label">快速输入</div>
          <div class="prompt-chips">
            <el-tag
              v-for="(item, idx) in quickPrompts"
              :key="idx"
              class="prompt-chip"
              size="small"
              @click="userInput = item"
            >{{ item }}</el-tag>
          </div>
        </div>
        <div class="input-footer">
          <span class="input-hint">描述你的交易需求和风险偏好</span>
          <el-button type="primary" :loading="loading" :disabled="!userInput.trim()" @click="handleGenerate">
            AI 分析生成
          </el-button>
        </div>
      </div>

      <div v-if="loading" class="loading-state">
        <el-progress type="circle" :percentage="50" :width="60" :stroke-width="4" status="warning" />
        <p>AI 正在分析你的需求，生成最优配置...</p>
      </div>

      <div v-if="result && !loading" class="result-section">
        <el-alert title="AI 推荐方案已就绪" type="success" :closable="false" show-icon class="result-header" />

        <el-card class="result-card" shadow="never">
          <div class="result-row type-row">
            <span class="result-label">推荐策略</span>
            <el-tag type="warning" effect="dark">{{ result.botType }}</el-tag>
          </div>
          <div class="result-reason">
            <el-icon><Lightning /></el-icon>
            <span>{{ result.reason }}</span>
          </div>
        </el-card>

        <el-card class="result-card" shadow="never">
          <template #header>
            <span>基础配置</span>
          </template>
          <div class="result-grid">
            <div class="result-item">
              <span class="item-label">交易对</span>
              <span class="item-value">{{ result.baseConfig.symbol }}</span>
            </div>
            <div class="result-item">
              <span class="item-label">时间周期</span>
              <span class="item-value">{{ result.baseConfig.timeframe }}</span>
            </div>
            <div class="result-item">
              <span class="item-label">市场类型</span>
              <span class="item-value">{{ result.baseConfig.marketType === 'swap' ? '合约/永续' : '现货' }}</span>
            </div>
            <div v-if="result.baseConfig.marketType === 'swap'" class="result-item">
              <span class="item-label">杠杆</span>
              <span class="item-value">{{ result.baseConfig.leverage }}x</span>
            </div>
            <div class="result-item">
              <span class="item-label">初始资金</span>
              <span class="item-value">${{ result.baseConfig.initialCapital }}</span>
            </div>
          </div>
        </el-card>

        <el-card v-if="strategyParamsList.length" class="result-card" shadow="never">
          <template #header>
            <span>策略参数</span>
          </template>
          <div class="result-grid">
            <div v-for="(val, key) in result.strategyParams" :key="key" class="result-item">
              <span class="item-label">{{ key }}</span>
              <span class="item-value">{{ val }}</span>
            </div>
          </div>
        </el-card>

        <el-card v-if="result.riskConfig" class="result-card" shadow="never">
          <template #header>
            <span>风控配置</span>
          </template>
          <div class="result-grid">
            <div class="result-item">
              <span class="item-label">最大回撤</span>
              <span class="item-value">{{ result.riskConfig.maxDrawdownPct }}%</span>
            </div>
            <div class="result-item">
              <span class="item-label">单笔最大仓位</span>
              <span class="item-value">{{ result.riskConfig.maxPositionPct }}%</span>
            </div>
            <div class="result-item">
              <span class="item-label">每日亏损限制</span>
              <span class="item-value">{{ result.riskConfig.dailyLossLimitPct }}%</span>
            </div>
          </div>
        </el-card>

        <el-form :model="confirmForm" class="confirm-form" label-width="80px">
          <el-form-item label="机器人名称" required>
            <el-input v-model="confirmForm.botName" placeholder="给机器人起个名字" />
          </el-form-item>
          <el-form-item label="交易所账户" required>
            <el-select v-model="selectedAccountId" placeholder="选择交易所账户" style="width:100%">
              <el-option
                v-for="acc in accounts"
                :key="acc.accountId || acc.id"
                :label="acc.name || acc.accountName || acc.accountId || acc.id"
                :value="acc.accountId || acc.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="confirmForm.remark" type="textarea" :rows="2" placeholder="可选备注" />
          </el-form-item>
        </el-form>
      </div>

      <div v-if="errorMsg && !loading" class="error-state">
        <el-alert title="AI 生成失败" :description="errorMsg" type="error" show-icon />
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button v-if="result && !loading" type="primary" :loading="confirming" @click="handleConfirm">
        确认创建
      </el-button>
      <el-button v-if="!result && !loading && !errorMsg" type="primary" :disabled="!userInput.trim()" @click="handleGenerate">
        生成
      </el-button>
      <el-button v-if="errorMsg && !loading" @click="handleReset">
        重新输入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Lightning } from '@element-plus/icons-vue'
import { aiGenerateStrategy, aiConfirmStrategy } from '@/api/strategy'
import { exchangeApi } from '@/api/exchange'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'created': [data: { botName: string; botId?: string }]
}>()

const authStore = useAuthStore()
const userInput = ref('')
const marketType = ref('spot')
const loading = ref(false)
const confirming = ref(false)
const result = ref<any>(null)
const errorMsg = ref('')
const accounts = ref<any[]>([])
const selectedAccountId = ref('')
const confirmForm = ref({
  botName: '',
  remark: ''
})

const quickPrompts = [
  '根据BTC最近行情帮我做趋势跟踪策略，4h周期',
  '分析ETH走势，帮我设置网格交易策略',
  'SOL最近强势，帮我做突破交易策略',
  'BTC回调中，帮我做均值回归策略'
]

const strategyParamsList = computed(() => {
  if (!result.value?.strategyParams) return []
  return Object.keys(result.value.strategyParams)
})

watch(() => props.visible, async (v) => {
  if (v) {
    userInput.value = ''
    marketType.value = 'spot'
    result.value = null
    errorMsg.value = ''
    loading.value = false
    confirming.value = false
    confirmForm.value = { botName: '', remark: '' }
    selectedAccountId.value = ''
    try {
      const res = await exchangeApi.getAccounts()
      accounts.value = (res as any)?.data || []
      if (accounts.value.length === 1) {
        selectedAccountId.value = accounts.value[0].accountId || accounts.value[0].id
      }
    } catch {
      accounts.value = []
    }
  }
})

async function handleGenerate() {
  if (!userInput.value.trim()) return
  loading.value = true
  errorMsg.value = ''
  result.value = null

  try {
    const res = await aiGenerateStrategy({
      prompt: userInput.value.trim(),
      intent: 'bot_recommend',
      marketType: marketType.value
    })
    if (res.success !== false && res.data) {
      result.value = res.data
      confirmForm.value.botName = `AI-${res.data.botType}-${res.data.baseConfig?.symbol || ''}`
    } else {
      errorMsg.value = res.message || '生成失败，请重试'
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '网络错误，请检查连接后重试'
  } finally {
    loading.value = false
  }
}

async function handleConfirm() {
  if (!confirmForm.value.botName.trim()) {
    errorMsg.value = '请输入机器人名称'
    return
  }
  if (!selectedAccountId.value) {
    errorMsg.value = '请选择交易所账户'
    return
  }
  confirming.value = true
  errorMsg.value = ''

  try {
    const res = await aiConfirmStrategy({
      recommendation: result.value,
      botName: confirmForm.value.botName.trim(),
      remark: confirmForm.value.remark.trim() || undefined,
      userId: authStore.user?.id || '',
      accountId: selectedAccountId.value
    })
    if (res.success !== false && res.data) {
      emit('created', res.data)
      emit('update:visible', false)
    } else {
      errorMsg.value = res.message || '创建失败，请重试'
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '网络错误，请检查连接后重试'
  } finally {
    confirming.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
}

function handleReset() {
  result.value = null
  errorMsg.value = ''
  userInput.value = ''
}
</script>

<style scoped>
.ai-dialog-body {
  min-height: 200px;
}

.input-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.market-type-selector {
  display: flex;
  align-items: center;
  gap: 12px;
}

.selector-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.quick-prompts {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.prompt-label {
  font-size: 12px;
  color: #909399;
}

.prompt-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.prompt-chip {
  cursor: pointer;
}

.input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.input-hint {
  font-size: 12px;
  color: #909399;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
  gap: 16px;
  color: #909399;
  font-size: 14px;
}

.result-header {
  margin-bottom: 16px;
}

.result-card {
  margin-bottom: 12px;
}

.result-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-reason {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.result-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.result-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.item-label {
  font-size: 12px;
  color: #909399;
}

.item-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.confirm-form {
  margin-top: 16px;
}

.error-state {
  padding: 24px 0;
}
</style>
