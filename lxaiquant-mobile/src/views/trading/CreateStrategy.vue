<template>
  <div class="create-page">
    <van-nav-bar
      title="创建策略"
      left-arrow
      @click-left="$router.back()"
      :border="false"
    />
    
    <div class="content">
      <van-form @submit="onSubmit">
        <van-cell-group inset>
          <van-field
            v-model="form.name"
            label="策略名称"
            placeholder="请输入策略名称"
            required
            :rules="[{ required: true, message: '请输入策略名称' }]"
          />
        </van-cell-group>
        
        <div class="section-title">交易配置</div>
        <van-cell-group inset>
          <van-field
            v-model="form.symbol"
            label="交易标的"
            placeholder="如 EURUSD, BTCUSDT"
            required
          />
          <van-field
            v-model="form.timeframe"
            is-link
            readonly
            label="周期"
            placeholder="请选择"
            @click="showTimeframePicker = true"
          />
          <van-field
            v-model="form.broker"
            is-link
            readonly
            label="券商"
            placeholder="请选择"
            @click="showBrokerPicker = true"
          />
        </van-cell-group>
        
        <div class="submit-btn">
          <van-button block type="primary" native-type="submit" :loading="submitting">
            创建策略
          </van-button>
        </div>
      </van-form>
    </div>
    
    <!-- 周期选择器 -->
    <van-popup v-model:show="showTimeframePicker" position="bottom" round>
      <van-picker
        :columns="timeframes"
        @confirm="onTimeframeConfirm"
        @cancel="showTimeframePicker = false"
      />
    </van-popup>
    
    <!-- 券商选择器 -->
    <van-popup v-model:show="showBrokerPicker" position="bottom" round>
      <van-picker
        :columns="brokers"
        @confirm="onBrokerConfirm"
        @cancel="showBrokerPicker = false"
      />
    </van-popup>
  </div>
</template>

<script>
import { showToast } from 'vant'
import { strategyApi } from '@/api'

export default {
  name: 'CreateStrategy',
  
  data() {
    return {
      form: {
        name: '',
        symbol: '',
        timeframe: '',
        broker: '',
        indicatorId: null,
        indicatorName: ''
      },
      showTimeframePicker: false,
      showBrokerPicker: false,
      submitting: false,
      timeframes: ['M1', 'M5', 'M15', 'M30', 'H1', 'H4', 'D1', 'W1'],
      brokers: ['MT5', 'IBKR', 'Binance', 'OKX']
    }
  },
  
  methods: {
    onTimeframeConfirm({ selectedValues }) {
      this.form.timeframe = selectedValues[0]
      this.showTimeframePicker = false
    },
    
    onBrokerConfirm({ selectedValues }) {
      this.form.broker = selectedValues[0]
      this.showBrokerPicker = false
    },
    
    async onSubmit() {
      if (!this.form.name || !this.form.symbol) {
        showToast({ message: '请填写必填项', type: 'fail' })
        return
      }
      
      this.submitting = true
      
      try {
        const res = await strategyApi.create({
          name: this.form.name,
          trading_config: {
            symbol: this.form.symbol,
            timeframe: this.form.timeframe,
            broker: this.form.broker
          },
          indicator_id: this.form.indicatorId
        })
        
        if (res.code === 1) {
          showToast({ message: '创建成功', type: 'success' })
          this.$router.back()
        } else {
          showToast({ message: res.msg || '创建失败', type: 'fail' })
        }
      } catch (err) {
        showToast({ message: '创建失败', type: 'fail' })
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.create-page {
  min-height: 100vh;
  background: transparent;
}

.create-page :deep(.van-nav-bar) { background: transparent; }
.create-page :deep(.van-nav-bar__title),
.create-page :deep(.van-nav-bar__arrow),
.create-page :deep(.van-nav-bar .van-icon) { color: var(--text); }

.content { padding: 16px; }

.section-title {
  font-size: 13px;
  color: var(--text-2);
  margin: 16px 0 8px 4px;
}

.create-page :deep(.van-cell-group--inset) {
  margin: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
}

.create-page :deep(.van-cell) { background: transparent; }
.create-page :deep(.van-field__label) { color: var(--text-2); }
.create-page :deep(.van-field__control) { color: var(--text); }
.create-page :deep(.van-field__control::placeholder) { color: var(--text-3); }

.submit-btn { margin-top: 32px; }

.submit-btn :deep(.van-button) {
  height: 48px;
  border-radius: 12px;
  background: var(--accent);
  color: var(--text-on-accent);
  border: none;
}
</style>
