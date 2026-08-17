<template>
  <el-dropdown
    trigger="click"
    placement="bottom-start"
    @command="onIndicatorSelect"
  >
    <el-button type="default">
      指标选择
      <el-icon class="el-icon--right">
        <ArrowDown />
      </el-icon>
    </el-button>
    <template #dropdown>
      <el-dropdown-menu class="indicator-dropdown-menu">
        <div class="indicator-menu-content">
          <!-- 主图指标 -->
          <div class="indicator-menu-section">
            <div class="indicator-menu-section-title">主图指标</div>

            <!-- BOLL -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="bollConfig.enabled"
                @change="handleToggle('boll', $event)"
              >
                BOLL
              </el-checkbox>
            </div>

            <!-- 反转确认 -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="reversalConfirmationConfig.enabled"
                @change="handleToggle('reversalConfirmation', $event)"
              >
                反转确认
              </el-checkbox>
            </div>

            <!-- 趋势强度信号 -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="trendStrengthConfig.enabled"
                @change="handleToggle('trendStrength', $event)"
              >
                趋势强度信号
              </el-checkbox>
            </div>

            <!-- 均线 -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="phenomConfig.enabled"
                @change="handleToggle('phenom', $event)"
              >
                均线
              </el-checkbox>
            </div>
            <!-- 均线子选项 -->
            <div v-if="phenomConfig.enabled" class="indicator-submenu">
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="phenomConfig.emaLines.ema9.enabled"
                  size="small"
                  @change="handlePhenomEmaChange('ema9', $event)"
                />
                <span>EMA9</span>
                <el-input-number
                  :model-value="phenomConfig.emaLines.ema9.period"
                  size="small"
                  :min="1"
                  :max="500"
                  style="width: 60px"
                  @change="handlePhenomEmaPeriodChange('ema9', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="phenomConfig.emaLines.ema21.enabled"
                  size="small"
                  @change="handlePhenomEmaChange('ema21', $event)"
                />
                <span>EMA21</span>
                <el-input-number
                  :model-value="phenomConfig.emaLines.ema21.period"
                  size="small"
                  :min="1"
                  :max="500"
                  style="width: 60px"
                  @change="handlePhenomEmaPeriodChange('ema21', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="phenomConfig.emaLines.ema55.enabled"
                  size="small"
                  @change="handlePhenomEmaChange('ema55', $event)"
                />
                <span>EMA55</span>
                <el-input-number
                  :model-value="phenomConfig.emaLines.ema55.period"
                  size="small"
                  :min="1"
                  :max="500"
                  style="width: 60px"
                  @change="handlePhenomEmaPeriodChange('ema55', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="phenomConfig.emaLines.ema144.enabled"
                  size="small"
                  @change="handlePhenomEmaChange('ema144', $event)"
                />
                <span>EMA144</span>
                <el-input-number
                  :model-value="phenomConfig.emaLines.ema144.period"
                  size="small"
                  :min="1"
                  :max="500"
                  style="width: 60px"
                  @change="handlePhenomEmaPeriodChange('ema144', $event)"
                />
              </div>
            </div>

            <!-- 超级趋势 -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="kalmanConfig.enabled"
                @change="handleToggle('kalman', $event)"
              >
                超级趋势
              </el-checkbox>
            </div>
            <!-- 超级趋势子选项 -->
            <div v-if="kalmanConfig.enabled" class="indicator-submenu">
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="kalmanConfig.retestSig"
                  size="small"
                  @change="handleKalmanConfigChange('retestSig', $event)"
                />
                <span>重测信号</span>
              </div>
              <div class="indicator-submenu-item">
                <span>短期长度</span>
                <el-input-number
                  :model-value="kalmanConfig.shortLen"
                  size="small"
                  :min="1"
                  :max="500"
                  style="width: 60px"
                  @change="handleKalmanConfigChange('shortLen', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <span>长期长度</span>
                <el-input-number
                  :model-value="kalmanConfig.longLen"
                  size="small"
                  :min="1"
                  :max="500"
                  style="width: 60px"
                  @change="handleKalmanConfigChange('longLen', $event)"
                />
              </div>
            </div>

            <!-- 流动性指标 -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="apexTrendLiquidityConfig.enabled"
                @change="handleToggle('apexTrendLiquidity', $event)"
              >
                流动性指标
              </el-checkbox>
            </div>
            <!-- 流动性指标子选项 -->
            <div
              v-if="apexTrendLiquidityConfig.enabled"
              class="indicator-submenu"
            >
              <div class="indicator-submenu-item">
                <span>趋势算法</span>
                <el-select
                  :model-value="apexTrendLiquidityConfig.maType"
                  size="small"
                  style="width: 80px"
                  @change="handleApexConfigChange('maType', $event)"
                >
                  <el-option value="EMA" label="EMA"></el-option>
                  <el-option value="SMA" label="SMA"></el-option>
                  <el-option value="HMA" label="HMA"></el-option>
                  <el-option value="RMA" label="RMA"></el-option>
                </el-select>
              </div>
              <div class="indicator-submenu-item">
                <span>趋势长度</span>
                <el-input-number
                  :model-value="apexTrendLiquidityConfig.mainLength"
                  size="small"
                  :min="10"
                  :max="200"
                  style="width: 60px"
                  @change="handleApexConfigChange('mainLength', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <span>波动倍数</span>
                <el-input-number
                  :model-value="apexTrendLiquidityConfig.volatilityMultiplier"
                  size="small"
                  :min="0.1"
                  :max="5.0"
                  :step="0.1"
                  style="width: 60px"
                  @change="
                    handleApexConfigChange('volatilityMultiplier', $event)
                  "
                />
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="apexTrendLiquidityConfig.showLiquidity"
                  size="small"
                  @change="handleApexConfigChange('showLiquidity', $event)"
                />
                <span>显示流动性</span>
              </div>
              <div class="indicator-submenu-item">
                <span>枢轴回溯</span>
                <el-input-number
                  :model-value="apexTrendLiquidityConfig.pivotLookback"
                  size="small"
                  :min="5"
                  :max="50"
                  style="width: 60px"
                  @change="handleApexConfigChange('pivotLookback', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <span>区域扩展</span>
                <el-input-number
                  :model-value="apexTrendLiquidityConfig.zoneExtension"
                  size="small"
                  :min="1"
                  :max="20"
                  style="width: 60px"
                  @change="handleApexConfigChange('zoneExtension', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="apexTrendLiquidityConfig.useVolumeFilter"
                  size="small"
                  @change="handleApexConfigChange('useVolumeFilter', $event)"
                />
                <span>成交量过滤</span>
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="apexTrendLiquidityConfig.useRsiFilter"
                  size="small"
                  @change="handleApexConfigChange('useRsiFilter', $event)"
                />
                <span>RSI过滤</span>
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="apexTrendLiquidityConfig.colorCandles"
                  size="small"
                  @change="handleApexConfigChange('colorCandles', $event)"
                />
                <span>着色K线</span>
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="apexTrendLiquidityConfig.showHud"
                  size="small"
                  @change="handleApexConfigChange('showHud', $event)"
                />
                <span>显示HUD</span>
              </div>
            </div>

            <!-- Range Filter -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="rangeFilterConfig.enabled"
                @change="handleToggle('rangeFilter', $event)"
              >
                <span>Range Filter</span>
              </el-checkbox>
            </div>
            <!-- Range Filter子选项 -->
            <div v-if="rangeFilterConfig.enabled" class="indicator-submenu">
              <div class="indicator-submenu-item">
                <span>过滤类型</span>
                <el-select
                  :model-value="rangeFilterConfig.filterType"
                  size="small"
                  style="width: 80px"
                  @change="handleRangeFilterConfigChange('filterType', $event)"
                >
                  <el-option value="Type 1" label="Type 1"></el-option>
                  <el-option value="Type 2" label="Type 2"></el-option>
                </el-select>
              </div>
              <div class="indicator-submenu-item">
                <span>移动源</span>
                <el-select
                  :model-value="rangeFilterConfig.movementSource"
                  size="small"
                  style="width: 80px"
                  @change="
                    handleRangeFilterConfigChange('movementSource', $event)
                  "
                >
                  <el-option value="Close" label="Close"></el-option>
                  <el-option value="Wicks" label="Wicks"></el-option>
                </el-select>
              </div>
              <div class="indicator-submenu-item">
                <span>范围大小</span>
                <el-input-number
                  :model-value="rangeFilterConfig.rangeSize"
                  size="small"
                  :min="0.0000001"
                  :step="0.001"
                  style="width: 80px"
                  @change="handleRangeFilterConfigChange('rangeSize', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <span>范围刻度</span>
                <el-select
                  :model-value="rangeFilterConfig.rangeScale"
                  size="small"
                  style="width: 100px"
                  @change="handleRangeFilterConfigChange('rangeScale', $event)"
                >
                  <el-option
                    value="Average Change"
                    label="平均变化"
                  ></el-option>
                  <el-option value="ATR" label="ATR"></el-option>
                  <el-option
                    value="Standard Deviation"
                    label="标准差"
                  ></el-option>
                  <el-option value="Points" label="点数"></el-option>
                  <el-option value="% of Price" label="价格百分比"></el-option>
                </el-select>
              </div>
              <div class="indicator-submenu-item">
                <span>范围周期</span>
                <el-input-number
                  :model-value="rangeFilterConfig.rangePeriod"
                  size="small"
                  :min="1"
                  :max="100"
                  style="width: 80px"
                  @change="handleRangeFilterConfigChange('rangePeriod', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="rangeFilterConfig.smoothRange"
                  size="small"
                  @change="handleRangeFilterConfigChange('smoothRange', $event)"
                />
                <span>平滑范围</span>
              </div>
              <div class="indicator-submenu-item">
                <span>平滑周期</span>
                <el-input-number
                  :model-value="rangeFilterConfig.smoothingPeriod"
                  size="small"
                  :min="1"
                  :max="100"
                  style="width: 80px"
                  @change="
                    handleRangeFilterConfigChange('smoothingPeriod', $event)
                  "
                />
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="rangeFilterConfig.averageFilterChanges"
                  size="small"
                  @change="
                    handleRangeFilterConfigChange(
                      'averageFilterChanges',
                      $event,
                    )
                  "
                />
                <span>平均过滤变化</span>
              </div>
              <div class="indicator-submenu-item">
                <span>平均样本数</span>
                <el-input-number
                  :model-value="rangeFilterConfig.numberOfChangesToAverage"
                  size="small"
                  :min="1"
                  :max="20"
                  style="width: 80px"
                  @change="
                    handleRangeFilterConfigChange(
                      'numberOfChangesToAverage',
                      $event,
                    )
                  "
                />
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="rangeFilterConfig.showSignals"
                  size="small"
                  @change="handleRangeFilterConfigChange('showSignals', $event)"
                />
                <span>显示信号</span>
              </div>
            </div>

            <!-- SMC Lite -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="smcLiteConfig.enabled"
                @change="handleToggle('smcLite', $event)"
              >
                <span>SMC Lite</span>
              </el-checkbox>
            </div>
            <!-- SMC Lite子选项 -->
            <div v-if="smcLiteConfig.enabled" class="indicator-submenu">
              <div class="indicator-submenu-item">
                <span>摆动长度</span>
                <el-input-number
                  :model-value="smcLiteConfig.swingLength"
                  size="small"
                  :min="3"
                  :max="50"
                  style="width: 60px"
                  @change="handleSmcLiteConfigChange('swingLength', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <span>历史保留</span>
                <el-input-number
                  :model-value="smcLiteConfig.historyToKeep"
                  size="small"
                  :min="5"
                  :max="100"
                  style="width: 60px"
                  @change="handleSmcLiteConfigChange('historyToKeep', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <span>框体宽度</span>
                <el-input-number
                  :model-value="smcLiteConfig.boxWidth"
                  size="small"
                  :min="0.5"
                  :max="10.0"
                  :step="0.1"
                  style="width: 60px"
                  @change="handleSmcLiteConfigChange('boxWidth', $event)"
                />
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="smcLiteConfig.showZigZag"
                  size="small"
                  @change="handleSmcLiteConfigChange('showZigZag', $event)"
                >
                  <span>Zig Zag线</span>
                </el-checkbox>
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="smcLiteConfig.showPriceActionLabels"
                  size="small"
                  @change="
                    handleSmcLiteConfigChange('showPriceActionLabels', $event)
                  "
                >
                  <span>价格标签</span>
                </el-checkbox>
              </div>
            </div>
          </div>

          <!-- 副图指标 -->
          <div class="indicator-menu-section">
            <div class="indicator-menu-section-title">副图指标</div>

            <!-- MACD -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="macdConfig.enabled"
                @change="handleToggle('macd', $event)"
              >
                <span>MACD</span>
              </el-checkbox>
            </div>

            <!-- RSI -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="rsiConfig.enabled"
                @change="handleToggle('rsi', $event)"
              >
                <span>RSI</span>
              </el-checkbox>
            </div>
            <!-- RSI子选项 -->
            <div v-if="rsiConfig.enabled" class="indicator-submenu">
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="rsiConfig.showLevels"
                  size="small"
                  @change="handleRsiConfigChange('showLevels', $event)"
                >
                  <span>水平线</span>
                </el-checkbox>
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="rsiConfig.showMA"
                  size="small"
                  @change="handleRsiConfigChange('showMA', $event)"
                >
                  <span>移动平均</span>
                </el-checkbox>
              </div>
              <div class="indicator-submenu-item">
                <el-checkbox
                  :model-value="rsiConfig.showBB"
                  size="small"
                  @change="handleRsiConfigChange('showBB', $event)"
                >
                  <span>布林带</span>
                </el-checkbox>
              </div>
            </div>

            <!-- 趋势强度表 -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="tsmConfig.enabled"
                @change="handleToggle('tsm', $event)"
              >
                <span>趋势强度表</span>
              </el-checkbox>
            </div>

            <!-- 反转后趋势强度 -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="trendStrengthAfterReversalConfig.enabled"
                @change="handleToggle('trendStrengthAfterReversal', $event)"
              >
                <span>反转后趋势强度</span>
              </el-checkbox>
            </div>

            <!-- 安第斯振荡器 -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="andeanOscillatorConfig.enabled"
                @change="handleToggle('andeanOscillator', $event)"
              >
                <span>安第斯振荡器</span>
              </el-checkbox>
            </div>

            <!-- 多时间框架趋势 -->
            <div class="indicator-menu-item">
              <el-checkbox
                :model-value="multiTimeframeTrendConfig.enabled"
                @change="handleToggle('multiTimeframeTrend', $event)"
              >
                <span>多时间框架趋势</span>
              </el-checkbox>
            </div>
          </div>
        </div>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { ArrowDown } from "@element-plus/icons-vue";

interface Props {
  bollConfig: any;
  rangeFilterConfig: any;
  macdConfig: any;
  rsiConfig: any;
  reversalConfirmationConfig: any;
  trendStrengthConfig: any;
  phenomConfig: any;
  kalmanConfig: any;
  apexTrendLiquidityConfig: any;
  smcLiteConfig: any;
  tsmConfig: any;
  trendStrengthAfterReversalConfig: any;
  andeanOscillatorConfig: any;
  multiTimeframeTrendConfig: any;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  "toggle-boll": [enabled: boolean];
  "toggle-range-filter": [enabled: boolean];
  "toggle-macd": [enabled: boolean];
  "toggle-rsi": [enabled: boolean];
  "toggle-reversal-confirmation": [enabled: boolean];
  "toggle-trend-strength": [enabled: boolean];
  "toggle-phenom": [enabled: boolean];
  "toggle-kalman": [enabled: boolean];
  "toggle-apex-trend-liquidity": [enabled: boolean];
  "toggle-smc-lite": [enabled: boolean];
  "toggle-tsm": [enabled: boolean];
  "toggle-trend-strength-after-reversal": [enabled: boolean];
  "toggle-andean-oscillator": [enabled: boolean];
  "toggle-multi-timeframe-trend": [enabled: boolean];
  "update-phenom-ema": [emaName: string, enabled: boolean];
  "update-phenom-ema-period": [emaName: string, period: number];
  "update-kalman-config": [key: string, value: any];
  "update-apex-config": [key: string, value: any];
  "update-range-filter-config": [key: string, value: any];
  "update-smc-lite-config": [key: string, value: any];
  "update-rsi-config": [key: string, value: any];
}>();

const onIndicatorSelect = (command: string) => {
  // 处理下拉菜单命令（如果需要）
};

const handleToggle = (indicatorName: string, enabled: boolean) => {
  const eventMap: Record<string, string> = {
    boll: "toggle-boll",
    rangeFilter: "toggle-range-filter",
    macd: "toggle-macd",
    rsi: "toggle-rsi",
    reversalConfirmation: "toggle-reversal-confirmation",
    trendStrength: "toggle-trend-strength",
    phenom: "toggle-phenom",
    kalman: "toggle-kalman",
    apexTrendLiquidity: "toggle-apex-trend-liquidity",
    smcLite: "toggle-smc-lite",
    tsm: "toggle-tsm",
    trendStrengthAfterReversal: "toggle-trend-strength-after-reversal",
    andeanOscillator: "toggle-andean-oscillator",
    multiTimeframeTrend: "toggle-multi-timeframe-trend",
  };

  const eventName = eventMap[indicatorName];
  if (eventName) {
    emit(eventName as any, enabled);
  }
};

const handlePhenomEmaChange = (emaName: string, enabled: boolean) => {
  emit("update-phenom-ema", emaName, enabled);
};

const handlePhenomEmaPeriodChange = (emaName: string, period: number) => {
  emit("update-phenom-ema-period", emaName, period);
};

const handleKalmanConfigChange = (key: string, value: any) => {
  emit("update-kalman-config", key, value);
};

const handleApexConfigChange = (key: string, value: any) => {
  emit("update-apex-config", key, value);
};

const handleRangeFilterConfigChange = (key: string, value: any) => {
  emit("update-range-filter-config", key, value);
};

const handleSmcLiteConfigChange = (key: string, value: any) => {
  emit("update-smc-lite-config", key, value);
};

const handleRsiConfigChange = (key: string, value: any) => {
  emit("update-rsi-config", key, value);
};
</script>

<style scoped>
.indicator-dropdown-menu {
  max-width: 400px;
  max-height: 600px;
  overflow-y: auto;
}

.indicator-menu-content {
  padding: 8px;
}

.indicator-menu-section {
  margin-bottom: 16px;
}

.indicator-menu-section:last-child {
  margin-bottom: 0;
}

.indicator-menu-section-title {
  font-weight: 600;
  font-size: 14px;
  color: #131722;
  padding: 8px 12px;
  background: #f8f9fa;
  border-radius: 4px;
  margin-bottom: 8px;
}

.indicator-menu-item {
  padding: 6px 12px;
  display: flex;
  align-items: center;
}

.indicator-submenu {
  margin-left: 24px;
  margin-top: 4px;
  padding-left: 12px;
  border-left: 2px solid #e0e3eb;
}

.indicator-submenu-item {
  padding: 4px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.indicator-submenu-item span {
  min-width: 80px;
  color: #666;
}
</style>
