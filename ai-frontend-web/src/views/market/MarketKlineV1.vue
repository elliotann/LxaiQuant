<template>
  <div class="market-kline-wrapper">
    <div class="market-kline-v1">
      <!-- 顶部工具栏 -->
      <div class="top-toolbar">
        <div class="toolbar-left">
          <div class="symbol-section" @click="showSymbolSearch = true">
            <el-icon class="search-icon"><Search /></el-icon>
            <span class="symbol-name">{{ displaySymbol }}</span>
            <el-icon class="add-icon"><Plus /></el-icon>
          </div>
          <!-- 交易对搜索弹窗 -->
          <Teleport to="body">
            <div
              v-if="showSymbolSearch"
              class="symbol-search-overlay"
              @click.self="showSymbolSearch = false"
            >
              <div class="symbol-search-modal" @click.stop>
                <div class="symbol-search-header">
                  <div class="symbol-search-container">
                    <el-icon class="symbol-search-icon"><Search /></el-icon>
                    <input
                      type="text"
                      class="symbol-search-input"
                      placeholder="搜索交易对..."
                      v-model="symbolSearchTerm"
                      ref="symbolSearchInputRef"
                      @keyup.esc="showSymbolSearch = false"
                    />
                    <el-icon
                      v-if="symbolSearchTerm"
                      class="symbol-search-clear"
                      @click="symbolSearchTerm = ''"
                    >
                      <Close />
                    </el-icon>
                  </div>
                  <el-icon
                    class="symbol-search-close"
                    @click="showSymbolSearch = false"
                  >
                    <Close />
                  </el-icon>
                </div>
                <div class="symbol-search-list-header">
                  <span class="symbol-search-col-symbol">交易对</span>
                  <span class="symbol-search-col-exchange">交易所</span>
                  <span class="symbol-search-col-type">类型</span>
                </div>
                <div class="symbol-search-list">
                  <template v-for="(group, exchangeKey) in groupedSymbols" :key="exchangeKey">
                    <div class="symbol-search-group-header">{{ exchangeKey }}</div>
                    <div
                      v-for="symbol in group"
                      :key="symbol.value + '_' + symbol.exchange"
                      class="symbol-search-item"
                      :class="{ active: symbol.value === selectedSymbol && symbol.exchange === selectedExchange }"
                      @click="handleSymbolSelect(symbol.value, symbol.exchange)"
                    >
                      <div class="symbol-search-item-symbol">
                        <span class="symbol-search-base">{{
                          symbol.label.split("/")[0] || symbol.label.split("-")[0]
                        }}</span>
                        <span class="symbol-search-quote"
                          >/{{
                            symbol.label.split("/")[1] ||
                            symbol.label.split("-").slice(1).join("-")
                          }}</span
                        >
                      </div>
                      <div class="symbol-search-item-exchange">{{ symbol.exchange }}</div>
                      <div class="symbol-search-item-type">
                        {{ symbol.type === "spot" ? "现货" : "合约" }}
                      </div>
                      <el-icon
                        v-if="symbol.value === selectedSymbol"
                        class="symbol-search-check"
                      >
                        <Check />
                      </el-icon>
                    </div>
                  </template>
                </div>
              </div>
            </div>
          </Teleport>
          <div class="interval-buttons">
            <button
              v-for="it in topIntervals"
              :key="it.value"
              class="interval-btn"
              :class="{ active: currentInterval === it.value }"
              @click="onIntervalClick(it.value)"
            >
              {{ it.label }}
            </button>
          </div>
          <!-- 指标（模仿 lightweight-charts-ui：按钮 + 下拉） -->
          <div class="indicator-btn-wrap" ref="indicatorBtnRef">
            <el-button
              size="small"
              :type="showIndicatorsDropdown ? 'primary' : undefined"
              text
              class="indicator-trigger-btn"
              @click="showIndicatorsDropdown = !showIndicatorsDropdown"
            >
              <el-icon><TrendCharts /></el-icon>
              <span class="indicator-trigger-text">指标</span>
            </el-button>
            <Teleport to="body">
              <div
                v-show="showIndicatorsDropdown"
                class="indicator-dropdown"
                :style="indicatorDropdownStyle"
                @click.stop
              >
                <div class="indicator-dropdown-section-title">主图</div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.boll }"
                  @click="setIndicator('boll', !indicators.boll)"
                >
                  <el-checkbox
                    :model-value="indicators.boll"
                    @update:model-value="setIndicator('boll', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">BOLL</span>
                </div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.reversal }"
                  @click="setIndicator('reversal', !indicators.reversal)"
                >
                  <el-checkbox
                    :model-value="indicators.reversal"
                    @update:model-value="setIndicator('reversal', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">反转确认</span>
                </div>
                <div
                  v-if="indicators.boll"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label">周期</span>
                  <el-input-number
                    v-model="bollConfig.period"
                    :min="5"
                    :max="200"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateBoll()"
                  />
                  <span class="indicator-param-label">倍数</span>
                  <el-input-number
                    v-model="bollConfig.multiplier"
                    :min="0.5"
                    :max="5"
                    :step="0.1"
                    :precision="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateBoll()"
                  />
                </div>
                <div
                  v-if="indicators.reversal"
                  class="indicator-params-row reversal-params"
                  @click.stop
                >
                  <span class="indicator-param-label">回看</span>
                  <el-input-number
                    v-model="reversalConfig.trendLookback"
                    :min="3"
                    :max="50"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateReversalConfirmation()"
                  />
                  <span class="indicator-param-label">强度</span>
                  <el-input-number
                    v-model="reversalConfig.trendStrength"
                    :min="0.5"
                    :max="1"
                    :step="0.1"
                    :precision="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateReversalConfirmation()"
                  />
                  <span class="indicator-param-label">ATR倍</span>
                  <el-input-number
                    v-model="reversalConfig.minMoveATR"
                    :min="0.5"
                    :max="5"
                    :step="0.1"
                    :precision="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateReversalConfirmation()"
                  />
                  <span class="indicator-param-label">EMA3</span>
                  <el-input-number
                    v-model="reversalConfig.ema3Length"
                    :min="1"
                    :max="20"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateReversalConfirmation()"
                  />
                  <span class="indicator-param-label">EMA5</span>
                  <el-input-number
                    v-model="reversalConfig.ema5Length"
                    :min="1"
                    :max="20"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateReversalConfirmation()"
                  />
                </div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.trendStrength }"
                  @click="
                    setIndicator('trendStrength', !indicators.trendStrength)
                  "
                >
                  <el-checkbox
                    :model-value="indicators.trendStrength"
                    @update:model-value="setIndicator('trendStrength', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">趋势强度信号</span>
                </div>
                <div
                  v-if="indicators.trendStrength"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label">周期</span>
                  <el-input-number
                    v-model="trendStrengthConfig.period"
                    :min="5"
                    :max="200"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateTrendStrengthIndicator()"
                  />
                  <span class="indicator-param-label">倍数</span>
                  <el-input-number
                    v-model="trendStrengthConfig.multiplier"
                    :min="0.5"
                    :max="5"
                    :step="0.1"
                    :precision="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateTrendStrengthIndicator()"
                  />
                </div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.ma }"
                  @click="setIndicator('ma', !indicators.ma)"
                >
                  <el-checkbox
                    :model-value="indicators.ma"
                    @update:model-value="setIndicator('ma', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">均线</span>
                </div>
                <div
                  v-if="indicators.ma"
                  class="indicator-params-row ma-params"
                  @click.stop
                >
                  <span class="indicator-param-label">EMA9</span>
                  <el-checkbox
                    v-model="maConfig.emaLines.ema9.enabled"
                    size="small"
                    @change="updateMA()"
                  />
                  <el-input-number
                    v-model="maConfig.emaLines.ema9.period"
                    :min="1"
                    :max="500"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateMA()"
                  />
                  <span class="indicator-param-label">EMA21</span>
                  <el-checkbox
                    v-model="maConfig.emaLines.ema21.enabled"
                    size="small"
                    @change="updateMA()"
                  />
                  <el-input-number
                    v-model="maConfig.emaLines.ema21.period"
                    :min="1"
                    :max="500"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateMA()"
                  />
                </div>
                <div
                  v-if="indicators.ma"
                  class="indicator-params-row ma-params"
                  @click.stop
                >
                  <span class="indicator-param-label">EMA55</span>
                  <el-checkbox
                    v-model="maConfig.emaLines.ema55.enabled"
                    size="small"
                    @change="updateMA()"
                  />
                  <el-input-number
                    v-model="maConfig.emaLines.ema55.period"
                    :min="1"
                    :max="500"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateMA()"
                  />
                  <span class="indicator-param-label">EMA144</span>
                  <el-checkbox
                    v-model="maConfig.emaLines.ema144.enabled"
                    size="small"
                    @change="updateMA()"
                  />
                  <el-input-number
                    v-model="maConfig.emaLines.ema144.period"
                    :min="1"
                    :max="500"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateMA()"
                  />
                </div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.rangeFilter }"
                  @click="setIndicator('rangeFilter', !indicators.rangeFilter)"
                >
                  <el-checkbox
                    :model-value="indicators.rangeFilter"
                    @update:model-value="setIndicator('rangeFilter', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">Range Filter</span>
                </div>
                <div
                  v-if="indicators.rangeFilter"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label">类型</span>
                  <el-select
                    v-model="rangeFilterConfig.filterType"
                    size="small"
                    class="indicator-param-input"
                    style="width: 72px"
                    @change="updateRangeFilter()"
                  >
                    <el-option value="Type 1" label="Type 1" />
                    <el-option value="Type 2" label="Type 2" />
                  </el-select>
                  <span class="indicator-param-label">范围</span>
                  <el-input-number
                    v-model="rangeFilterConfig.rangeSize"
                    :min="0.0000001"
                    :max="100"
                    :step="0.1"
                    :precision="4"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateRangeFilter()"
                  />
                  <span class="indicator-param-label">周期</span>
                  <el-input-number
                    v-model="rangeFilterConfig.rangePeriod"
                    :min="1"
                    :max="100"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateRangeFilter()"
                  />
                  <el-checkbox
                    v-model="rangeFilterConfig.smoothRange"
                    size="small"
                    @change="updateRangeFilter()"
                    >平滑</el-checkbox
                  >
                  <el-checkbox
                    v-model="rangeFilterConfig.showSignals"
                    size="small"
                    @change="updateRangeFilter()"
                    >信号</el-checkbox
                  >
                </div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.smcLite }"
                  @click="setIndicator('smcLite', !indicators.smcLite)"
                >
                  <el-checkbox
                    :model-value="indicators.smcLite"
                    @update:model-value="setIndicator('smcLite', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">支撑压力</span>
                </div>
                <div
                  v-if="indicators.smcLite"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label" style="min-width:24px">15m</span>
                  <el-radio-group
                    v-model="smcLiteConfig.obModes['15m']"
                    size="small"
                    @change="updateSmcLite()"
                  >
                    <el-radio-button value="">关</el-radio-button>
                    <el-radio-button value="swing">摆动</el-radio-button>
                    <el-radio-button value="internal">内部</el-radio-button>
                    <el-radio-button value="both">全部</el-radio-button>
                  </el-radio-group>
                  <el-switch
                    v-model="smcLiteConfig.bosModes['15m']"
                    size="small"
                    inline-prompt
                    active-text="BOS"
                    inactive-text="BOS"
                    @change="updateSmcLite()"
                    style="margin-left:8px"
                  />
                  <el-switch
                    v-model="smcLiteConfig.chochModes['15m']"
                    size="small"
                    inline-prompt
                    active-text="CHOCH"
                    inactive-text="CHOCH"
                    @change="updateSmcLite()"
                    style="margin-left:4px"
                  />
                </div>
                <div
                  v-if="indicators.smcLite"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label" style="min-width:24px">1h</span>
                  <el-radio-group
                    v-model="smcLiteConfig.obModes['1h']"
                    size="small"
                    @change="updateSmcLite()"
                  >
                    <el-radio-button value="">关</el-radio-button>
                    <el-radio-button value="swing">摆动</el-radio-button>
                    <el-radio-button value="internal">内部</el-radio-button>
                    <el-radio-button value="both">全部</el-radio-button>
                  </el-radio-group>
                  <el-switch
                    v-model="smcLiteConfig.bosModes['1h']"
                    size="small"
                    inline-prompt
                    active-text="BOS"
                    inactive-text="BOS"
                    @change="updateSmcLite()"
                    style="margin-left:8px"
                  />
                  <el-switch
                    v-model="smcLiteConfig.chochModes['1h']"
                    size="small"
                    inline-prompt
                    active-text="CHOCH"
                    inactive-text="CHOCH"
                    @change="updateSmcLite()"
                    style="margin-left:4px"
                  />
                </div>
                <div
                  v-if="indicators.smcLite"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label" style="min-width:24px">4h</span>
                  <el-radio-group
                    v-model="smcLiteConfig.obModes['4h']"
                    size="small"
                    @change="updateSmcLite()"
                  >
                    <el-radio-button value="">关</el-radio-button>
                    <el-radio-button value="swing">摆动</el-radio-button>
                    <el-radio-button value="internal">内部</el-radio-button>
                    <el-radio-button value="both">全部</el-radio-button>
                  </el-radio-group>
                  <el-switch
                    v-model="smcLiteConfig.bosModes['4h']"
                    size="small"
                    inline-prompt
                    active-text="BOS"
                    inactive-text="BOS"
                    @change="updateSmcLite()"
                    style="margin-left:8px"
                  />
                  <el-switch
                    v-model="smcLiteConfig.chochModes['4h']"
                    size="small"
                    inline-prompt
                    active-text="CHOCH"
                    inactive-text="CHOCH"
                    @change="updateSmcLite()"
                    style="margin-left:4px"
                  />
                </div>
                <div
                  v-if="indicators.smcLite"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label" style="min-width:24px">1D</span>
                  <el-radio-group
                    v-model="smcLiteConfig.obModes['1D']"
                    size="small"
                    @change="updateSmcLite()"
                  >
                    <el-radio-button value="">关</el-radio-button>
                    <el-radio-button value="swing">摆动</el-radio-button>
                    <el-radio-button value="internal">内部</el-radio-button>
                    <el-radio-button value="both">全部</el-radio-button>
                  </el-radio-group>
                  <el-switch
                    v-model="smcLiteConfig.bosModes['1D']"
                    size="small"
                    inline-prompt
                    active-text="BOS"
                    inactive-text="BOS"
                    @change="updateSmcLite()"
                    style="margin-left:8px"
                  />
                  <el-switch
                    v-model="smcLiteConfig.chochModes['1D']"
                    size="small"
                    inline-prompt
                    active-text="CHOCH"
                    inactive-text="CHOCH"
                    @change="updateSmcLite()"
                    style="margin-left:4px"
                  />
                </div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.chanlun }"
                  @click="setIndicator('chanlun', !indicators.chanlun)"
                >
                  <el-checkbox
                    :model-value="indicators.chanlun"
                    @update:model-value="setIndicator('chanlun', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">缠论</span>
                </div>
                <!-- 行情看板 -->
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.lingsheAi }"
                  @click="setIndicator('lingsheAi', !indicators.lingsheAi)"
                >
                  <el-checkbox
                    :model-value="indicators.lingsheAi"
                    @update:model-value="setIndicator('lingsheAi', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">行情看板</span>
                </div>
                <!-- 趋势线 -->
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.trendline }"
                  @click="setIndicator('trendline', !indicators.trendline)"
                >
                  <el-checkbox
                    :model-value="indicators.trendline"
                    @update:model-value="setIndicator('trendline', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">趋势线</span>
                </div>
                <div
                  v-if="indicators.trendline"
                  class="indicator-params-row"
                  @click.stop
                >
                  <el-checkbox
                    v-model="trendlineConfig.support"
                    size="small"
                    @change="updateTrendline()"
                  >支撑</el-checkbox>
                  <el-checkbox
                    v-model="trendlineConfig.resistance"
                    size="small"
                    @change="updateTrendline()"
                  >阻力</el-checkbox>
                  <span class="indicator-param-label" style="min-width:24px">范围</span>
                  <el-input-number
                    v-model="trendlineConfig.barCount"
                    :min="10"
                    :max="200"
                    :step="10"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateTrendline()"
                  />
                </div>
                <div class="indicator-dropdown-section-title">副图</div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.macd }"
                  @click="setIndicator('macd', !indicators.macd)"
                >
                  <el-checkbox
                    :model-value="indicators.macd"
                    @update:model-value="setIndicator('macd', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">MACD</span>
                </div>
                <div
                  v-if="indicators.macd"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label">快线</span>
                  <el-input-number
                    v-model="macdConfig.fastPeriod"
                    :min="2"
                    :max="50"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateMACD()"
                  />
                  <span class="indicator-param-label">慢线</span>
                  <el-input-number
                    v-model="macdConfig.slowPeriod"
                    :min="2"
                    :max="100"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateMACD()"
                  />
                  <span class="indicator-param-label">信号</span>
                  <el-input-number
                    v-model="macdConfig.signalPeriod"
                    :min="2"
                    :max="50"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateMACD()"
                  />
                </div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.rsi }"
                  @click="setIndicator('rsi', !indicators.rsi)"
                >
                  <el-checkbox
                    :model-value="indicators.rsi"
                    @update:model-value="setIndicator('rsi', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">RSI</span>
                </div>
                <div
                  v-if="indicators.rsi"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label">周期</span>
                  <el-input-number
                    v-model="rsiConfig.period"
                    :min="5"
                    :max="50"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateRSI()"
                  />
                </div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.tsm }"
                  @click="setIndicator('tsm', !indicators.tsm)"
                >
                  <el-checkbox
                    :model-value="indicators.tsm"
                    @update:model-value="setIndicator('tsm', $event)"
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">趋势强度表</span>
                </div>
                <div
                  v-if="indicators.tsm"
                  class="indicator-params-row"
                  @click.stop
                >
                  <span class="indicator-param-label">周期</span>
                  <el-input-number
                    v-model="tsmConfig.period"
                    :min="5"
                    :max="100"
                    :step="1"
                    size="small"
                    controls-position="right"
                    class="indicator-param-input"
                    @change="updateTSM()"
                  />
                </div>
                <div
                  class="indicator-dropdown-item"
                  :class="{ active: indicators.andeanOscillator }"
                  @click="
                    setIndicator(
                      'andeanOscillator',
                      !indicators.andeanOscillator,
                    )
                  "
                >
                  <el-checkbox
                    :model-value="indicators.andeanOscillator"
                    @update:model-value="
                      setIndicator('andeanOscillator', $event)
                    "
                    @click.stop
                  />
                  <span class="indicator-dropdown-item-text">安第斯振荡器</span>
                </div>
                <div
                  v-if="indicators.andeanOscillator"
                  class="indicator-params-row"
                  @click.stop
                >
                  <el-checkbox
                    v-model="andeanOscillatorConfig.earlySignal"
                    size="small"
                    @change="updateAndeanOscillator()"
                    >早期信号</el-checkbox
                  >
                </div>
              </div>
            </Teleport>
          </div>
          <div class="bot-select" style="margin-left: 12px;">
            <el-select
              v-model="selectedBotId"
              placeholder="选择机器人"
              style="width: 220px"
              filterable
              clearable
            >
              <el-option
                v-for="bot in robots"
                :key="bot.botId || bot.id"
                :label="bot.botName || bot.name || bot.botId || bot.id"
                :value="bot.botId || bot.id"
              />
            </el-select>
          </div>
        </div>
          <div class="toolbar-right">
            <el-button-group>
              <el-button size="small" text>
                <el-icon><Bell /></el-icon>
                警报
              </el-button>
              <el-button size="small" text>
                <el-icon><VideoPlay /></el-icon>
                回放
              </el-button>
            </el-button-group>
          </div>
      </div>
      <!-- 主内容区域 -->
      <div class="mk-main-content">
        <!-- 左侧画线工具 -->
        <div class="left-toolbar">
          <div
            class="tool-item"
            :class="{ active: activeDrawingTool === 'cursor' }"
            @click="onDrawingToolChange('cursor')"
            title="十字光标"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 28 28"
              width="20"
              height="20"
            >
              <g fill="currentColor">
                <path d="M18 15h8v-1h-8z"></path>
                <path d="M14 18v8h1v-8zM14 3v8h1v-8zM3 15h8v-1h-8z"></path>
              </g>
            </svg>
          </div>
          <div
            class="tool-item"
            :class="{ active: activeDrawingTool === 'trendline' }"
            @click="onDrawingToolChange('trendline')"
            title="趋势线"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 28 28"
              width="20"
              height="20"
            >
              <g fill="currentColor" fillRule="nonzero">
                <path d="M7.354 21.354l14-14-.707-.707-14 14z"></path>
                <path
                  d="M22.5 7c.828 0 1.5-.672 1.5-1.5s-.672-1.5-1.5-1.5-1.5.672-1.5 1.5.672 1.5 1.5 1.5zm0 1c-1.381 0-2.5-1.119-2.5-2.5s1.119-2.5 2.5-2.5 2.5 1.119 2.5 2.5-1.119 2.5-2.5 2.5zM5.5 24c.828 0 1.5-.672 1.5-1.5s-.672-1.5-1.5-1.5-1.5.672-1.5 1.5.672 1.5 1.5 1.5zm0 1c-1.381 0-2.5-1.119-2.5-2.5s1.119-2.5 2.5-2.5 2.5 1.119 2.5 2.5-1.119 2.5-2.5 2.5z"
                ></path>
              </g>
            </svg>
          </div>
          <div class="tool-item" title="水平线">
            <el-icon><Minus /></el-icon>
          </div>
          <div class="tool-item" title="垂直线">
            <el-icon><Sort /></el-icon>
          </div>
          <div class="tool-item" title="斐波那契">
            <el-icon><DataLine /></el-icon>
          </div>
          <div class="tool-item" title="文本">
            <el-icon><EditPen /></el-icon>
          </div>
          <div class="tool-item" title="价格范围">
            <el-icon><FullScreen /></el-icon>
          </div>
          <div class="tool-item" title="放大">
            <el-icon><ZoomIn /></el-icon>
          </div>
          <div class="tool-item" title="缩小">
            <el-icon><ZoomOut /></el-icon>
          </div>
          <div class="tool-item" title="磁铁模式">
            <el-icon><View /></el-icon>
          </div>
          <div class="tool-item" title="锁定所有">
            <el-icon><Lock /></el-icon>
          </div>
          <div class="tool-item" title="隐藏所有">
            <el-icon><Hide /></el-icon>
          </div>
          <div
            class="tool-item"
            :class="{ active: selectedTrendLineIndex !== null }"
            @click="deleteSelectedTrendLine"
            title="删除选中的趋势线 (Delete键)"
          >
            <el-icon><Delete /></el-icon>
          </div>
        </div>
        <!-- 中间图表区域 -->
        <div class="chart-wrapper">
          <div
            class="chart-area"
            ref="chartArea"
            :class="{
              'chart-area-suppressed':
                contextMenuVisible || isAnyModalOpen,
            }"
            @contextmenu.prevent="handleContextMenu"
            @mouseleave="hideSignalTooltip"
          >
          <!-- K线信息栏（浮动在K线图左上角） -->
          <div class="kline-header-info" v-if="displayKlineData.time > 0">
            <div class="kline-info-item">
              <span class="label">开盘:</span>
              <span :class="['value', klinePriceColor(displayKlineData)]">{{
                formatPrice(displayKlineData.open)
              }}</span>
            </div>
            <div class="kline-info-item">
              <span class="label">最高:</span>
              <span :class="['value', klinePriceColor(displayKlineData)]">{{
                formatPrice(displayKlineData.high)
              }}</span>
            </div>
            <div class="kline-info-item">
              <span class="label">最低:</span>
              <span :class="['value', klinePriceColor(displayKlineData)]">{{
                formatPrice(displayKlineData.low)
              }}</span>
            </div>
            <div class="kline-info-item">
              <span class="label">收盘:</span>
              <span :class="['value', klinePriceColor(displayKlineData)]">{{
                formatPrice(displayKlineData.close)
              }}</span>
            </div>
            <div class="kline-info-item">
              <span class="label">震幅:</span>
              <span class="value"
                >{{ calculateAmplitude(displayKlineData) }}%</span
              >
            </div>
            <div class="kline-info-item">
              <span class="label">时间:</span>
              <span class="value">{{ formattedKlineTime }}</span>
            </div>
          </div>
            <div
              ref="chartContainer"
              class="chart-container"
              :style="{
                width: containerWidth + 'px',
                height: containerHeight + 'px',
                position: 'relative',
              }"
            >
              <!-- 价格轴标签 - 放在图表容器内部，这样坐标可以直接使用 -->
              <div
                v-if="priceAxisLabel"
                class="price-axis-label"
                :style="{
                  top: priceAxisLabel.top + 'px',
                  backgroundColor: priceAxisLabel.color,
                }"
              >
                {{ priceAxisLabel.price }}
              </div>
              <Teleport to="body">
                <SignalTooltip
                  :visible="signalTooltip.visible"
                  :left="signalTooltip.left"
                  :top="signalTooltip.top"
                  :title="signalTooltip.title"
                  :color="signalTooltip.color"
                  :rows="signalTooltip.rows"
                />
              </Teleport>
            </div>
            <div v-if="loading" class="loading-overlay">
              <div class="loading-spinner"></div>
              <span>加载数据中...</span>
            </div>
            <div v-if="error" class="error-overlay">
              <div class="error-message">{{ error }}</div>
              <button @click="jumpToNow">重试</button>
            </div>
          </div>
          <!-- 右键菜单 -->
          <Teleport to="body">
            <div
              v-if="contextMenuVisible"
              class="chart-context-menu"
              :style="{
                left: contextMenuPosition.x + 'px',
                top: contextMenuPosition.y + 'px',
              }"
              @click.stop
            >
              <div class="context-menu-item" @click="handleOpenOrderFromContextMenu">
                <el-icon><ShoppingCart /></el-icon>
                <span>开单</span>
              </div>
              <div
                v-if="signalTooltip.visible"
                class="context-menu-item"
                @click="handleCopySignalTooltip"
              >
                <el-icon><DocumentCopy /></el-icon>
                <span>复制信号信息</span>
              </div>
              <div class="context-menu-divider"></div>
              <div class="context-menu-item" @click="handleResetZoom">
                <el-icon><RefreshLeft /></el-icon>
                <span>重置缩放</span>
              </div>
              <div class="context-menu-item" @click="handleFitContent">
                <el-icon><FullScreen /></el-icon>
                <span>适应内容</span>
              </div>
              <div class="context-menu-divider"></div>
              <div class="context-menu-item" @click="handleToggleFullscreen">
                <el-icon><FullScreen /></el-icon>
                <span>{{ isFullscreen ? "退出全屏" : "全屏显示" }}</span>
              </div>
            </div>
          </Teleport>
        </div>
        <!-- 右侧面板容器 -->
        <div class="right-panels-container">
          <!-- 开单面板 (独立) -->
          <div v-show="orderPanelVisible" class="slide-panel slide-panel-order">
            <div class="slide-panel-header">
              <span class="slide-panel-title">开单</span>
              <button class="slide-panel-close" @click="orderPanelVisible = false">×</button>
            </div>
            <div class="slide-panel-body">
              <div class="qt-panel-body">
                <div class="qt-section">
                  <div class="qt-label">当前价格</div>
                  <div class="qt-price-row">
                    <span class="qt-current-price">{{ formatPrice(lastPrice) }}</span>
                  </div>
                </div>
                <div class="qt-section">
                  <div class="qt-label">方向</div>
                  <div class="qt-direction-toggle">
                    <div class="qt-dir-btn qt-dir-long" :class="{ active: manualOrderForm.side === 'BUY' }" @click="manualOrderForm.side = 'BUY'">开多</div>
                    <div class="qt-dir-btn qt-dir-short" :class="{ active: manualOrderForm.side === 'SELL' }" @click="manualOrderForm.side = 'SELL'">开空</div>
                  </div>
                </div>
                <div class="qt-section">
                  <div class="qt-label">委托类型</div>
                  <div class="qt-mode-toggle">
                    <span
                      class="qt-mode-toggle-item"
                      :class="{ active: manualOrderForm.orderType === 'MARKET' }"
                      @click="manualOrderForm.orderType = 'MARKET'; manualOrderForm.limitPrice = undefined"
                    >市价</span>
                    <span
                      class="qt-mode-toggle-item"
                      :class="{ active: manualOrderForm.orderType === 'LIMIT' }"
                      @click="manualOrderForm.orderType = 'LIMIT'; if (!Number.isFinite(Number(manualOrderForm.limitPrice))) manualOrderForm.limitPrice = Number(lastPrice) || undefined"
                    >限价</span>
                  </div>
                  <div v-if="manualOrderForm.orderType === 'LIMIT'" style="margin-top: 4px">
                    <el-input-number v-model="manualOrderForm.limitPrice" :min="0" :step="0.01" size="small" controls-position="right" style="width: 100%" />
                  </div>
                </div>
                <div class="qt-section qt-amount-block">
                  <div class="qt-label">{{ isTradfiSymbol ? '手数 (Lots)' : '下单金额 (USDT)' }}</div>
                  <el-input-number v-model="orderAmount" :min="0" :max="isTradfiSymbol ? undefined : orderBalance" :step="isTradfiSymbol ? 0.01 : 1" :precision="isTradfiSymbol ? 2 : 0" size="small" controls-position="right" style="width: 100%" />
                  <div v-if="!isTradfiSymbol" class="qt-quick-amounts">
                    <button class="qt-pct-btn" @click="orderAmount = orderBalance * 0.1">10%</button>
                    <button class="qt-pct-btn" @click="orderAmount = orderBalance * 0.25">25%</button>
                    <button class="qt-pct-btn" @click="orderAmount = orderBalance * 0.5">50%</button>
                    <button class="qt-pct-btn" @click="orderAmount = orderBalance * 0.75">75%</button>
                    <button class="qt-pct-btn" @click="orderAmount = orderBalance * 1">100%</button>
                  </div>
                </div>
                <div v-if="!isTradfiSymbol" class="qt-section qt-card">
                  <div class="qt-section-title-row">
                    <span class="qt-section-title">保证金模式</span>
                  </div>
                  <div class="qt-mode-toggle">
                    <span class="qt-mode-toggle-item" :class="{ active: orderMarginMode === 'cross' }" @click="orderMarginMode = 'cross'">全仓</span>
                    <span class="qt-mode-toggle-item" :class="{ active: orderMarginMode === 'isolated' }" @click="orderMarginMode = 'isolated'">逐仓</span>
                  </div>
                  <div class="qt-section-title-row" style="margin-top: 2px">
                    <span class="qt-section-title">杠杆</span>
                  </div>
                  <div class="qt-leverage-row">
                    <el-slider v-model="orderLeverage" :min="1" :max="125" :step="1" show-input size="small" style="flex:1" />
                  </div>
                </div>
                <div class="qt-section qt-card">
                  <div class="qt-section-title-row">
                    <span class="qt-section-title">止盈 / 止损</span>
                    <span class="qt-optional-tag">可选</span>
                  </div>
                  <div class="qt-tpsl-row">
                    <div class="qt-tpsl-item">
                      <div class="qt-label qt-tp-label">止盈</div>
                      <el-input-number v-model="orderTpPrice" size="small" controls-position="right" :min="0" placeholder="价格" style="width:100%" />
                    </div>
                    <div class="qt-tpsl-item">
                      <div class="qt-label qt-sl-label">止损</div>
                      <el-input-number v-model="orderSlPrice" size="small" controls-position="right" :min="0" placeholder="价格" style="width:100%" />
                    </div>
                  </div>
                </div>
              </div>
              <div class="order-rr-line">
                <span class="order-rr-label">盈亏比 R:R</span>
                <span class="order-rr-value">{{ orderRR ?? "-" }}</span>
              </div>
              <div class="qt-submit-section">
                  <button class="qt-submit-btn" :class="manualOrderForm.side === 'BUY' ? 'qt-btn-long' : 'qt-btn-short'" @click="submitManualOpenOrder" :disabled="manualOrderSubmitting">
                    <span v-if="manualOrderSubmitting">提交中...</span>
                    <span v-else>{{ manualOrderForm.side === 'BUY' ? '开多' : '开空' }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
          <!-- 小灵宝面板 (悬浮弹框) -->
          <XiaoLingBaoDialog
            :visible="aiPanelVisible"
            :symbol="selectedSymbol"
            :bot-id="selectedBotId"
            :interval="currentInterval"
            @close="aiPanelVisible = false"
          />
          <!-- 原有分析/SMC 面板 -->
          <div
            class="right-panel"
            :class="{ collapsed: rightPanelCollapsed }"
            :style="{ width: rightPanelDisplayWidth + 'px' }"
            ref="rightPanelRef"
          >
            <div
              v-if="!rightPanelCollapsed"
              class="right-panel-resizer"
              @mousedown="startRightPanelResize"
              :class="{ resizing: isRightPanelResizing }"
            >
              <div class="resizer-handle-vertical"></div>
            </div>
            <div class="right-panel-content">
              <div v-if="!rightPanelCollapsed" class="right-panel-body">
                <div class="panel-header">
                  <span class="panel-title">{{ rightPanelTitle }}</span>
                </div>
                <div class="main-tabs">
                  <button
                    class="main-tab-btn"
                    :class="{ active: rightMainTab === 'analysis' }"
                    @click="openRightPanelTab('analysis')"
                  >
                    市场分析
                  </button>
                  <button
                    class="main-tab-btn"
                    :class="{ active: rightMainTab === 'smc' }"
                    @click="openRightPanelTab('smc')"
                  >
                    SMC
                  </button>
                </div>
                <!-- 市场分析面板 -->
                <template v-if="rightMainTab === 'analysis'">
                  <div class="analysis-tab-content">
                    <div class="analysis-tab-panel">
                      <div class="trend-state-card">
                        <div class="trend-state-title">
                          LogReg 通道
                          <el-button
                            size="small"
                            text
                            :loading="logRegChannelLoading"
                            @click="loadLogRegChannelIndicator"
                            style="margin-left: 8px"
                          >
                            刷新
                          </el-button>
                        </div>
                        <div v-if="logRegChannelLoading" class="trend-desc-row">
                          <span class="trend-desc-text">加载中...</span>
                        </div>
                        <div v-else-if="logRegChannelError" class="trend-desc-row">
                          <span class="trend-desc-text">{{ logRegChannelError }}</span>
                        </div>
                        <div v-else-if="logRegChannelData" class="logreg-grid">
                          <div class="logreg-card">
                            <div class="logreg-title">斜率 × 100</div>
                            <div class="logreg-value">{{ logRegSlopeX100Text }}</div>
                            <div class="logreg-sub">{{ logRegDirectionLabel }}</div>
                          </div>
                          <div class="logreg-card logreg-card-left">
                            <div class="logreg-title">通道方向</div>
                            <div class="logreg-direction-row">
                              <span
                                class="logreg-dot"
                                :class="logRegDirectionClass"
                              ></span>
                              <span class="logreg-direction">{{
                                logRegDirectionLabel
                              }}</span>
                            </div>
                            <div class="logreg-kv">
                              <div class="logreg-kv-row">
                                <span class="logreg-kv-key">上轨区间：</span>
                                <span class="logreg-kv-val">{{
                                  logRegUpperRangeText
                                }}</span>
                              </div>
                              <div class="logreg-kv-row">
                                <span class="logreg-kv-key">下轨区间：</span>
                                <span class="logreg-kv-val">{{
                                  logRegLowerRangeText
                                }}</span>
                              </div>
                            </div>
                          </div>
                          <div class="logreg-card logreg-card-left">
                            <div class="logreg-title">回归线方向</div>
                            <div class="logreg-direction-row">
                              <span
                                class="logreg-dot"
                                :class="logRegDirectionClass"
                              ></span>
                              <span class="logreg-direction">{{
                                logRegDirectionLabel
                              }}</span>
                            </div>
                            <div class="logreg-kv">
                              <div class="logreg-kv-row">
                                <span class="logreg-kv-key">起点：</span>
                                <span class="logreg-kv-val">{{
                                  logRegMiddleStartText
                                }}</span>
                              </div>
                              <div class="logreg-kv-row">
                                <span class="logreg-kv-key">终点(end)：</span>
                                <span class="logreg-kv-val">{{
                                  logRegMiddleEndText
                                }}</span>
                              </div>
                            </div>
                          </div>
                          <div class="logreg-card">
                            <div class="logreg-title">当前回归值</div>
                            <div class="logreg-sub">（终点 end）</div>
                            <div class="logreg-value">{{ logRegMiddleEndText }}</div>
                          </div>
                          <div class="logreg-card">
                            <div class="logreg-title">通道宽度</div>
                            <div class="logreg-sub">（内/外差）</div>
                            <div class="logreg-kv logreg-kv-center">
                              <div class="logreg-kv-row">
                                <span class="logreg-kv-key">内宽：</span>
                                <span class="logreg-kv-val">{{
                                  logRegInnerWidthText
                                }}</span>
                              </div>
                              <div class="logreg-kv-row">
                                <span class="logreg-kv-key">外宽：</span>
                                <span class="logreg-kv-val">{{
                                  logRegOuterWidthText
                                }}</span>
                              </div>
                            </div>
                          </div>
                          <div class="logreg-card">
                            <div class="logreg-title">价格位置</div>
                            <div class="logreg-sub">（相对通道带）</div>
                            <div class="logreg-value-sm">{{
                              logRegPriceLocationText
                            }}</div>
                            <div class="logreg-check-row">
                              <span
                                class="logreg-check"
                                :class="{ on: logRegIsInChannel }"
                                >✔</span
                              >
                              <span class="logreg-check-text">在通道内</span>
                            </div>
                          </div>
                        </div>
                        <div v-else class="trend-desc-row">
                          <span class="trend-desc-text">暂无数据</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>
                <!-- SMC 面板 -->
                <template v-else>
                  <div class="analysis-tab-content smc-tab-content">
                    <div class="analysis-tab-panel">
                      <div class="trend-state-card">
                        <div class="trend-state-title">Smart Money Concepts</div>
                        <div v-if="smcLoading" class="trend-desc-row">
                          <span class="trend-desc-text">加载中...</span>
                        </div>
                        <div v-else-if="smcError" class="trend-desc-row">
                          <span class="trend-desc-text">{{ smcError }}</span>
                        </div>
                        <template v-else>
                          <div class="level-list" v-if="smcLatest">
                          <div class="level-row">
                            <span class="level-row-label">时间</span>
                            <span class="level-row-value">{{ formatSmcTime(smcLatest.timestamp) }}</span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">内部趋势</span>
                            <span class="level-row-value">{{ smcTrendLabel(smcLatest.internalTrend) }}</span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">摆动趋势</span>
                            <span class="level-row-value">{{ smcTrendLabel(smcLatest.swingTrend) }}</span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">结构信号</span>
                            <span class="level-row-value">
                              <span v-if="smcLatest.internalBullishBOS">内部BOS↑ </span>
                              <span v-if="smcLatest.internalBearishBOS">内部BOS↓ </span>
                              <span v-if="smcLatest.internalBullishCHOCH">内部CHOCH↑ </span>
                              <span v-if="smcLatest.internalBearishCHOCH">内部CHOCH↓ </span>
                              <span v-if="smcLatest.swingBullishBOS">摆动BOS↑ </span>
                              <span v-if="smcLatest.swingBearishBOS">摆动BOS↓ </span>
                              <span v-if="smcLatest.swingBullishCHOCH">摆动CHOCH↑ </span>
                              <span v-if="smcLatest.swingBearishCHOCH">摆动CHOCH↓ </span>
                              <span v-if="!anyStructureSignal(smcLatest)">-</span>
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">订单块突破</span>
                            <span class="level-row-value">
                              <span v-if="smcLatest.internalBullishOrderBlockBreak">内部OB↑ </span>
                              <span v-if="smcLatest.internalBearishOrderBlockBreak">内部OB↓ </span>
                              <span v-if="smcLatest.swingBullishOrderBlockBreak">摆动OB↑ </span>
                              <span v-if="smcLatest.swingBearishOrderBlockBreak">摆动OB↓ </span>
                              <span
                                v-if="
                                  !smcLatest.internalBullishOrderBlockBreak &&
                                  !smcLatest.internalBearishOrderBlockBreak &&
                                  !smcLatest.swingBullishOrderBlockBreak &&
                                  !smcLatest.swingBearishOrderBlockBreak
                                "
                                >-</span
                              >
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">订单块(摆动)</span>
                            <span class="level-row-value level-row-value-multiline">
                              <span v-if="smcLatest.swingOrderBlocks?.length">
                                <span v-if="smcBlocks(smcLatest.swingOrderBlocks, -1).length">
                                  <span class="smc-ob-title">供给</span>
                                  <span
                                    v-for="(ob, idx) in smcBlocks(smcLatest.swingOrderBlocks, -1).slice(0, 3)"
                                    :key="'s-supply-' + idx"
                                    class="smc-ob-range"
                                  >
                                    {{ formatPrice(ob.low) }}~{{ formatPrice(ob.high) }}
                                  </span>
                                </span>
                                <span v-if="smcBlocks(smcLatest.swingOrderBlocks, 1).length">
                                  <span class="smc-ob-title">需求</span>
                                  <span
                                    v-for="(ob, idx) in smcBlocks(smcLatest.swingOrderBlocks, 1).slice(0, 3)"
                                    :key="'s-demand-' + idx"
                                    class="smc-ob-range"
                                  >
                                    {{ formatPrice(ob.low) }}~{{ formatPrice(ob.high) }}
                                  </span>
                                </span>
                              </span>
                              <span v-else>-</span>
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">订单块(内部)</span>
                            <span class="level-row-value level-row-value-multiline">
                              <span v-if="smcLatest.internalOrderBlocks?.length">
                                <span v-if="smcBlocks(smcLatest.internalOrderBlocks, -1).length">
                                  <span class="smc-ob-title">供给</span>
                                  <span
                                    v-for="(ob, idx) in smcBlocks(smcLatest.internalOrderBlocks, -1).slice(0, 3)"
                                    :key="'i-supply-' + idx"
                                    class="smc-ob-range"
                                  >
                                    {{ formatPrice(ob.low) }}~{{ formatPrice(ob.high) }}
                                  </span>
                                </span>
                                <span v-if="smcBlocks(smcLatest.internalOrderBlocks, 1).length">
                                  <span class="smc-ob-title">需求</span>
                                  <span
                                    v-for="(ob, idx) in smcBlocks(smcLatest.internalOrderBlocks, 1).slice(0, 3)"
                                    :key="'i-demand-' + idx"
                                    class="smc-ob-range"
                                  >
                                    {{ formatPrice(ob.low) }}~{{ formatPrice(ob.high) }}
                                  </span>
                                </span>
                              </span>
                              <span v-else>-</span>
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">等高/等低</span>
                            <span class="level-row-value">
                              <span v-if="smcLatest.equalHighs">EQH </span>
                              <span v-if="smcLatest.equalLows">EQL </span>
                              <span v-if="!smcLatest.equalHighs && !smcLatest.equalLows">-</span>
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">公平价值缺口</span>
                            <span class="level-row-value">
                              <span v-if="smcLatest.bullishFairValueGap">看涨FVG </span>
                              <span v-if="smcLatest.bearishFairValueGap">看跌FVG </span>
                              <span v-if="smcLatest.bullishFVGBroken">看涨FVG已破 </span>
                              <span v-if="smcLatest.bearishFVGBroken">看跌FVG已破 </span>
                              <span v-if="!smcLatest.bullishFairValueGap && !smcLatest.bearishFairValueGap && !smcLatest.bullishFVGBroken && !smcLatest.bearishFVGBroken">-</span>
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">FVG区间</span>
                            <span class="level-row-value">
                              <span v-if="hasSmcLevel(smcLatest.lastBullishFVGTop) && hasSmcLevel(smcLatest.lastBullishFVGBottom)">
                                看涨 {{ formatPrice(smcLatest.lastBullishFVGBottom) }} ~ {{ formatPrice(smcLatest.lastBullishFVGTop) }}
                              </span>
                              <span v-else-if="hasSmcLevel(smcLatest.lastBearishFVGTop) && hasSmcLevel(smcLatest.lastBearishFVGBottom)">
                                看跌 {{ formatPrice(smcLatest.lastBearishFVGBottom) }} ~ {{ formatPrice(smcLatest.lastBearishFVGTop) }}
                              </span>
                              <span v-else>-</span>
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">多周期高低</span>
                            <span class="level-row-value">
                              <span v-if="hasSmcLevel(smcLatest.dailyHigh) || hasSmcLevel(smcLatest.dailyLow)">
                                D {{ formatPrice(smcLatest.dailyLow) }} ~ {{ formatPrice(smcLatest.dailyHigh) }}
                              </span>
                              <span v-if="hasSmcLevel(smcLatest.weeklyHigh) || hasSmcLevel(smcLatest.weeklyLow)">
                                W {{ formatPrice(smcLatest.weeklyLow) }} ~ {{ formatPrice(smcLatest.weeklyHigh) }}
                              </span>
                              <span v-if="hasSmcLevel(smcLatest.monthlyHigh) || hasSmcLevel(smcLatest.monthlyLow)">
                                M {{ formatPrice(smcLatest.monthlyLow) }} ~ {{ formatPrice(smcLatest.monthlyHigh) }}
                              </span>
                              <span
                                v-if="
                                  !hasSmcLevel(smcLatest.dailyHigh) &&
                                  !hasSmcLevel(smcLatest.dailyLow) &&
                                  !hasSmcLevel(smcLatest.weeklyHigh) &&
                                  !hasSmcLevel(smcLatest.weeklyLow) &&
                                  !hasSmcLevel(smcLatest.monthlyHigh) &&
                                  !hasSmcLevel(smcLatest.monthlyLow)
                                "
                                >-</span
                              >
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">溢价/折扣区</span>
                            <span class="level-row-value">{{ smcLatest.currentZone ?? "-" }}</span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">溢价区</span>
                            <span class="level-row-value">
                              <span
                                v-if="
                                  hasSmcLevel(smcLatest.premiumZoneBottom) &&
                                  hasSmcLevel(smcLatest.premiumZoneTop)
                                "
                              >
                                {{ formatPrice(smcLatest.premiumZoneBottom) }} ~
                                {{ formatPrice(smcLatest.premiumZoneTop) }}
                              </span>
                              <span v-else>-</span>
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">折扣区</span>
                            <span class="level-row-value">
                              <span
                                v-if="
                                  hasSmcLevel(smcLatest.discountZoneBottom) &&
                                  hasSmcLevel(smcLatest.discountZoneTop)
                                "
                              >
                                {{ formatPrice(smcLatest.discountZoneBottom) }} ~
                                {{ formatPrice(smcLatest.discountZoneTop) }}
                              </span>
                              <span v-else>-</span>
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">均衡区</span>
                            <span class="level-row-value">
                              <span
                                v-if="
                                  hasSmcLevel(smcLatest.equilibriumZoneBottom) &&
                                  hasSmcLevel(smcLatest.equilibriumZoneTop)
                                "
                              >
                                {{ formatPrice(smcLatest.equilibriumZoneBottom) }} ~
                                {{ formatPrice(smcLatest.equilibriumZoneTop) }}
                                ({{ formatPrice(smcLatest.equilibriumCenter) }})
                              </span>
                              <span v-else>-</span>
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">强/弱点</span>
                            <span class="level-row-value">
                              <span v-if="hasSmcLevel(smcLatest.strongHigh)">
                                强高 {{ formatPrice(smcLatest.strongHigh) }}
                              </span>
                              <span v-if="hasSmcLevel(smcLatest.weakHigh)">
                                弱高 {{ formatPrice(smcLatest.weakHigh) }}
                              </span>
                              <span v-if="hasSmcLevel(smcLatest.strongLow)">
                                强低 {{ formatPrice(smcLatest.strongLow) }}
                              </span>
                              <span v-if="hasSmcLevel(smcLatest.weakLow)">
                                弱低 {{ formatPrice(smcLatest.weakLow) }}
                              </span>
                              <span
                                v-if="
                                  !hasSmcLevel(smcLatest.strongHigh) &&
                                  !hasSmcLevel(smcLatest.weakHigh) &&
                                  !hasSmcLevel(smcLatest.strongLow) &&
                                  !hasSmcLevel(smcLatest.weakLow)
                                "
                                >-</span
                              >
                            </span>
                          </div>
                          <div class="level-row">
                            <span class="level-row-label">Trailing</span>
                            <span class="level-row-value">
                              <span v-if="hasSmcLevel(smcLatest.trailingLow) || hasSmcLevel(smcLatest.trailingHigh)">
                                {{ formatPrice(smcLatest.trailingLow) }} ~ {{ formatPrice(smcLatest.trailingHigh) }}
                              </span>
                              <span v-else>-</span>
                            </span>
                          </div>
                        </div>
                        <div v-else class="trend-desc-row">
                          <span class="trend-desc-text">暂无数据</span>
                        </div>
                        </template>
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </div>
          <!-- 右侧图标栏 -->
          <div class="right-iconbar">
            <button
              class="right-icon-btn"
              :class="{ active: !rightPanelCollapsed && rightMainTab === 'analysis' }"
              @click="openRightPanelTab('analysis')"
              title="市场分析"
            >
              <el-icon><TrendCharts /></el-icon>
            </button>
            <button
              class="right-icon-btn"
              :class="{ active: !rightPanelCollapsed && rightMainTab === 'smc' }"
              @click="openRightPanelTab('smc')"
              title="SMC"
            >
              <el-icon><Grid /></el-icon>
            </button>
            <button
              class="right-icon-btn"
              :class="{ active: orderPanelVisible }"
              @click="toggleOrderPanel"
              title="开单"
            >
              <el-icon><ShoppingCart /></el-icon>
            </button>
            <button
              class="right-icon-btn"
              :class="{ active: aiPanelVisible }"
              @click="toggleAiPanel"
              title="小灵宝"
            >
              <el-icon><ChatDotRound /></el-icon>
            </button>
          </div>
        </div>
      </div>
      <!-- 底部工具栏 -->
      <div class="bottom-toolbar">
        <div class="time-jump-section">
          <el-date-picker
            v-model="jumpDateTime"
            type="datetime"
            placeholder="选择时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            :disabled-date="disabledFutureDate"
            size="small"
            style="width: 200px"
          />
          <el-button size="small" type="primary" @click="handleTimeJump">
            跳转
          </el-button>
          <el-button size="small" @click="jumpToNow"> 现在 </el-button>
          <el-button
            size="small"
            :type="isRealtimeMode ? 'primary' : 'default'"
            @click="toggleRealtimeMode"
            title="切换实时模式"
          >
            {{ isRealtimeMode ? "实时" : "历史" }}
          </el-button>
        </div>
        <div class="bottom-right">
          <button class="bottom-btn">auto</button>
          <el-popover placement="top" :width="150" trigger="hover" :disabled="!showApiSignalsOverlay">
            <template #reference>
              <div style="display: inline-flex; gap: 6px; align-items: center">
                <span>信号</span>
                <el-switch v-model="showApiSignalsOverlay" size="small" />
              </div>
            </template>
            <div style="display:flex;align-items:center;justify-content:space-between;gap:10px">
              <span style="font-size:12px;white-space:nowrap">有效信号过滤</span>
              <el-switch v-model="filterValidSignals" size="small" />
            </div>
          </el-popover>
        </div>
      </div>
    
    <!-- 可拖拽分隔条 -->
    <div
      class="panel-resizer"
      @mousedown="startResize"
      :class="{ resizing: isResizing, suppressed: isAnyModalOpen }"
    >
      <div class="resizer-handle"></div>
    </div>
    <!-- 底部Tab面板 -->
    <div
      class="bottom-tab-panel-container"
      :class="{ suppressed: isAnyModalOpen }"
      :style="{ height: `${bottomPanelHeight}px` }"
      ref="bottomPanelRef"
    >
      <!-- Tab导航 -->
      <div class="bottom-tabs-nav">
        <button
          class="bottom-tab-btn"
          :class="{ active: bottomActiveTab === 'backtest' }"
          @click="bottomActiveTab = 'backtest'"
        >
          📊 回测
        </button>
        <button
          class="bottom-tab-btn"
          :class="{ active: bottomActiveTab === 'positions' }"
          @click="bottomActiveTab = 'positions'"
        >
          📌 持仓
        </button>
        <button
          class="bottom-tab-btn"
          :class="{ active: bottomActiveTab === 'orders' }"
          @click="bottomActiveTab = 'orders'"
        >
          📋 历史仓位
        </button>
        <button
          class="bottom-tab-btn"
          :class="{ active: bottomActiveTab === 'profit-report' }"
          @click="bottomActiveTab = 'profit-report'"
        >
          💹 收益统计
        </button>
        <button
          class="bottom-tab-btn"
          :class="{ active: bottomActiveTab === 'trade-logs' }"
          @click="bottomActiveTab = 'trade-logs'"
        >
          📜 交易日志
        </button>
      </div>
      <!-- Tab内容 -->
      <div class="bottom-tab-content">
        <!-- 回测面板 -->
        <div v-show="bottomActiveTab === 'backtest'" class="tab-panel">
          <div class="backtest-sub-tabs">
            <button
              class="sub-tab-btn"
              :class="{ active: backtestSubTab === 'run' }"
              @click="backtestSubTab = 'run'"
            >
              ▶️ 运行回测
            </button>
            <button
              class="sub-tab-btn"
              :class="{ active: backtestSubTab === 'backtest-records' }"
              @click="backtestSubTab = 'backtest-records'"
            >
              📈 回测记录
            </button>
          </div>
          <BacktestRunPanel
            v-show="backtestSubTab === 'run'"
            :backtestTypes="supportedBacktestTypes"
            :symbolOptions="backtestSymbolOptions"
            :timeframes="backtestTimeframes"
            :robots="robots"
            :backtestParams="backtestParams"
            :backtestRunning="backtestRunning"
            :backtestProgress="backtestProgress"
            :backtestMessage="backtestMessage"
            :backtestResults="backtestResults"
            :equityProgress="equityProgress"
            :equityCurvePoints="equityCurvePoints"
            :backtestLogs="backtestLogs"
            @update:backtestParams="backtestParams = $event"
            @run="runBacktest"
            @stop="stopBacktestFn"
            @clear-logs="clearBacktestLogs"
          />
          <BacktestRecordsPanel
            v-show="backtestSubTab === 'backtest-records'"
            :strategies="strategies"
            :selectedStrategy="selectedRecordsStrategy"
            :records="backtestRecords"
            :loading="backtestRecordsLoading"
            @update:selectedStrategy="selectedRecordsStrategy = $event"
            @refresh="loadBacktestRecords"
            @view="viewBacktestRecord"
            @delete="deleteBacktestRecord"
          />
        </div>
        <div v-show="bottomActiveTab === 'positions'" class="tab-panel">
          <div class="orders-header">
            <div style="display: flex; gap: 12px; align-items: center">
              <el-button @click="loadPositionsOrders" :loading="positionsLoading">刷新</el-button>
            </div>
          </div>
          <div v-if="positionsLoading" class="loading">加载中...</div>
          <div v-else-if="positionsOrders.length === 0" class="no-data">暂无持仓订单</div>
          <div v-else class="positions-table-wrap">
            <el-table
              :data="positionsOrders"
              row-key="_rowKey"
              :tree-props="{ children: '_children' }"
              size="small"
              border
              height="100%"
              table-layout="fixed"
              style="width: 100%"
            >
              <el-table-column label="商品" width="120" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{ row.symbol || "-" }}</template>
                  <span v-else-if="row._type === 'item'" style="color:#409eff">开仓记录</span>
                  <span v-else-if="row._type === 'closeItem'" style="color:#e6a23c">平仓明细</span>
                </template>
              </el-table-column>
              <el-table-column label="订单号" width="130" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{
                    row.orderSn || row.id || "-"
                  }}</template>
                  <template v-else-if="row._type === 'closeItem'">{{ row.batchId || row.entrySn || '-' }}</template>
                  <template v-else>{{ row.entrySn || '-' }}</template>
                </template>
              </el-table-column>
              <el-table-column label="买/卖方" width="80">
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <span
                      :class="
                        String(row.orderSide || row.side || '').toUpperCase() === 'BUY' ||
                        String(row.orderSide || row.side || '').toUpperCase() === 'LONG'
                          ? 'order-side long'
                          : 'order-side short'
                      "
                    >
                      {{
                        String(row.orderSide || row.side || "").toUpperCase() === "BUY" ||
                        String(row.orderSide || row.side || "").toUpperCase() === "LONG"
                          ? "做多"
                          : "做空"
                      }}
                    </span>
                  </template>
                  <template v-else-if="row._type === 'closeItem'">
                    <span v-if="row.exitType" style="color:#e6a23c;font-size:12px">{{ row.exitType }}</span>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="开仓时间" width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{ formatOpenTime(row) }}</template>
                  <template v-else-if="row._type === 'item'">{{ formatOpenTime(row) }}</template>
                  <template v-else-if="row._type === 'closeItem'">{{ formatTime(row.exitTime) || formatTime(row.time) }}</template>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="110" align="right">
                <template #default="{ row }">
                  <template v-if="!row._type">
                    {{ formatAmount(row.remainingAmount ?? row.amount ?? row.quantity ?? row.volume) }}({{ formatAmount(row.amount ?? row.quantity ?? row.volume) }})
                  </template>
                  <template v-else-if="row._type === 'item'">{{ formatAmount(row.amount) }}</template>
                  <template v-else-if="row._type === 'closeItem'">{{ formatAmount(row.closedVolume ?? row.amount) }}</template>
                </template>
              </el-table-column>
              <el-table-column label="剩余数量" width="90" align="right">
                <template #default="{ row }">
                  <template v-if="!row._type">{{ formatAmount(row.remainingAmount) }}</template>
                  <template v-else>—</template>
                </template>
              </el-table-column>
              <el-table-column label="平均成交价" width="110" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{ getOrderDisplayAvgPrice(row) }}</template>
                  <template v-else-if="row._type === 'item'">{{ row.buyPrice ? formatPrice(row.buyPrice) : '-' }}</template>
                  <template v-else-if="row._type === 'closeItem'">{{ row.entryPrice ? formatPrice(row.entryPrice) : '-' }}</template>
                </template>
              </el-table-column>
              <el-table-column label="止盈" width="100" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{
                    row.gainPrice ? formatPrice(row.gainPrice) : "—"
                  }}</template>
                  <template v-else-if="row._type === 'item'">{{ row.gainPrice ? formatPrice(row.gainPrice) : "—" }}</template>
                  <template v-else-if="row._type === 'closeItem'">
                    <span :class="(row.income ?? 0) >= 0 ? 'profit-amount positive' : 'profit-amount negative'">
                      {{ (row.income ?? 0) >= 0 ? '+' : '' }}{{ row.income ?? '-' }}
                    </span>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="止损" width="100" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{
                    row.lossPrice ? formatPrice(row.lossPrice) : "—"
                  }}</template>
                  <template v-else-if="row._type === 'item'">{{ row.lossPrice ? formatPrice(row.lossPrice) : "—" }}</template>
                  <template v-else-if="row._type === 'closeItem'">—</template>
                </template>
              </el-table-column>
              <el-table-column label="最新价" width="100" align="right" show-overflow-tooltip>
                <template #default>
                  {{ currentPrice ? formatPrice(currentPrice) : "-" }}
                </template>
              </el-table-column>
              <el-table-column label="杠杆" width="80" align="center" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{ formatLeverage(row) }}</template>
                  <template v-else-if="row._type === 'item'">
                    <span style="color:#909399;font-size:12px">{{ row.status || '-' }}</span>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="260">
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <div class="positions-actions">
                      <el-tooltip content="市价平仓" placement="top">
                        <el-button
                          circle
                          size="small"
                          type="danger"
                          :icon="Close"
                          @click.stop="openCloseDialog(row)"
                        />
                      </el-tooltip>
                      <el-tooltip content="限价/部分平仓" placement="top">
                        <el-button
                          circle
                          size="small"
                          type="primary"
                          :icon="EditPen"
                          @click.stop="openCloseDialog(row, true)"
                        />
                      </el-tooltip>
                      <el-tooltip content="修改止盈止损" placement="top">
                        <el-button
                          circle
                          size="small"
                          type="warning"
                          :icon="Setting"
                          @click.stop="openGainLossDialog(row)"
                        />
                      </el-tooltip>
                      <el-tooltip content="反手" placement="top">
                        <el-button
                          circle
                          size="small"
                          type="success"
                          :icon="RefreshRight"
                          @click.stop="openReverseDialog(row)"
                        />
                      </el-tooltip>
                      <el-tooltip content="详情" placement="top">
                        <el-button
                          circle
                          size="small"
                          type="info"
                          :icon="View"
                          @click.stop="openOrderDetailsDialog(row)"
                        />
                      </el-tooltip>
                    </div>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="市值" width="110" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <span v-if="getPositionMarketValue(row) !== null">
                      {{ (getPositionMarketValue(row) ?? 0).toFixed(2) }}
                    </span>
                    <span v-else>-</span>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="未实现损益" width="120" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <span
                      v-if="getPositionUnrealizedPnl(row) !== null"
                      :class="
                        (getPositionUnrealizedPnl(row) ?? 0) >= 0
                          ? 'profit-amount positive'
                          : 'profit-amount negative'
                      "
                    >
                      {{
                        (getPositionUnrealizedPnl(row) ?? 0) >= 0 ? "+" : ""
                      }}{{ (getPositionUnrealizedPnl(row) ?? 0).toFixed(2) }}
                    </span>
                    <span v-else class="profit-amount neutral">-</span>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
        <el-dialog v-model="closeDialogVisible" title="平仓" width="420px">
          <div>
            <el-form :model="closeForm" label-width="90px">
              <el-form-item label="订单号">
                <span>{{ closeForm.orderSn }}</span>
              </el-form-item>
              <el-form-item label="交易对">
                <span>{{ closeForm.symbol }}</span>
              </el-form-item>
              <el-form-item label="方向">
                <span>{{ closeForm.side === 'LONG' ? '做多' : '做空' }}</span>
              </el-form-item>
              <el-form-item label="类型">
                <el-radio-group v-model="closeForm.orderType">
                  <el-radio value="MARKET">市价</el-radio>
                  <el-radio value="LIMIT">限价</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="数量(张)">
                <el-input-number v-model="closeForm.quantity" :min="1" :step="1" />
              </el-form-item>
              <el-form-item label="限价" v-if="closeForm.orderType === 'LIMIT'">
                <el-input-number v-model="closeForm.limitPrice" :min="0" :step="0.01" />
              </el-form-item>
            </el-form>
          </div>
          <template #footer>
            <el-button @click="closeDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="closeSubmitting" @click="submitManualClose">确认平仓</el-button>
          </template>
        </el-dialog>
        <el-dialog v-model="gainLossDialogVisible" title="修改止盈止损" width="420px">
          <div>
            <el-form :model="gainLossForm" label-width="90px">
              <el-form-item label="订单号">
                <span>{{ gainLossForm.orderSn }}</span>
              </el-form-item>
              <el-form-item label="交易对">
                <span>{{ getSymbolName(gainLossForm.symbol) }}</span>
              </el-form-item>
              <el-form-item label="止盈价">
                <el-input-number
                  v-model="gainLossForm.gainPrice"
                  :min="0"
                  :step="0.01"
                />
              </el-form-item>
              <el-form-item label="止损价">
                <el-input-number
                  v-model="gainLossForm.lossPrice"
                  :min="0"
                  :step="0.01"
                />
              </el-form-item>
            </el-form>
          </div>
          <template #footer>
            <el-button @click="gainLossDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="gainLossSubmitting" @click="submitGainLoss"
              >确认修改</el-button
            >
          </template>
        </el-dialog>
        <el-dialog v-model="reverseDialogVisible" title="反手" width="420px">
          <div>
            <el-form :model="reverseForm" label-width="90px">
              <el-form-item label="订单号">
                <span>{{ reverseForm.orderSn }}</span>
              </el-form-item>
              <el-form-item label="交易对">
                <span>{{ getSymbolName(reverseForm.symbol) }}</span>
              </el-form-item>
              <el-form-item label="类型">
                <el-radio-group v-model="reverseForm.orderType">
                  <el-radio value="MARKET">市价</el-radio>
                  <el-radio value="LIMIT">限价</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="数量(张)">
                <el-input-number v-model="reverseForm.quantity" :min="1" :step="1" />
              </el-form-item>
              <el-form-item label="限价" v-if="reverseForm.orderType === 'LIMIT'">
                <el-input-number v-model="reverseForm.limitPrice" :min="0" :step="0.01" />
              </el-form-item>
            </el-form>
          </div>
          <template #footer>
            <el-button @click="reverseDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="reverseSubmitting" @click="submitReverse"
              >确认反手</el-button
            >
          </template>
        </el-dialog>
        <el-dialog v-model="orderDetailsDialogVisible" title="订单详情" width="1000px" draggable>
          <div v-if="orderDetailsLoading" class="loading">加载中...</div>
          <div v-else-if="orderDetailsOrder" class="order-details">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="订单号">{{
                orderDetailsOrder.orderSn || orderDetailsOrder.id || "-"
              }}</el-descriptions-item>
              <el-descriptions-item label="交易对">{{
                orderDetailsOrder.symbol || "-"
              }}</el-descriptions-item>
              <el-descriptions-item label="交易方向">
                <span
                  :class="
                    orderDetailsOrder.orderSide === 'BUY'
                      ? 'order-side long'
                      : 'order-side short'
                  "
                >
                  {{
                    orderDetailsOrder.orderSide === "BUY"
                      ? "做多"
                      : orderDetailsOrder.orderSide === "SELL"
                        ? "做空"
                        : "-"
                  }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="杠杆">{{
                orderDetailsOrder.leverRate
                  ? orderDetailsOrder.leverRate + "x"
                  : "-"
              }}</el-descriptions-item>
              <el-descriptions-item label="开仓价">{{
                orderDetailsOrder.buyPrice || orderDetailsOrder.openPrice || "-"
              }}</el-descriptions-item>
              <el-descriptions-item label="成交均价">{{
                getOrderDisplayAvgPrice(orderDetailsOrder)
              }}</el-descriptions-item>
              <el-descriptions-item label="数量">{{
                orderDetailsOrder.amount ?? "-"
              }}</el-descriptions-item>
              <el-descriptions-item label="平仓价">{{
                orderDetailsOrder.sellPrice ?? "-"
              }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <span
                  :class="['order-status', orderStatusClass(orderDetailsOrder.status)]"
                >
                  {{ mapOrderStatusLabel(orderDetailsOrder.status) }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="平仓原因">{{
                formatCloseReason(
                  orderDetailsOrder.closeReason ||
                    orderDetailsOrder.closeOrderType ||
                    orderDetailsOrder.exitType ||
                    orderDetailsOrder.closeType,
                )
              }}</el-descriptions-item>
              <el-descriptions-item label="开仓时间">{{
                formatOpenTime(orderDetailsOrder)
              }}</el-descriptions-item>
              <el-descriptions-item label="成交时间">{{
                formatTime(orderDetailsOrder.buyTime)
              }}</el-descriptions-item>
              <el-descriptions-item label="平仓时间">{{
                formatTime(orderDetailsOrder.sellTime)
              }}</el-descriptions-item>
              <el-descriptions-item label="手续费">{{
                formatCharge(orderDetailsOrder.charge)
              }}</el-descriptions-item>
              <el-descriptions-item label="毛利">{{
                formatIncome(orderDetailsOrder.income).text
              }}</el-descriptions-item>
              <el-descriptions-item label="止盈价">{{
                orderDetailsOrder.gainPrice
                  ? formatPrice(orderDetailsOrder.gainPrice)
                  : "—"
              }}</el-descriptions-item>
              <el-descriptions-item label="止损价">{{
                orderDetailsOrder.lossPrice
                  ? formatPrice(orderDetailsOrder.lossPrice)
                  : "—"
              }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="orderDetailsOrder.status === 'PENDING'" style="margin-top: 16px; text-align: right">
              <el-button type="danger" size="small" @click="handleCancelOrder">撤销订单</el-button>
            </div>
            <div class="section-subtitle">开仓记录</div>
            <el-table
              :data="orderDetailsItems"
              size="small"
              border
              style="width: 100%; margin-bottom: 16px"
            >
              <el-table-column prop="entrySn" label="记录号" width="200" />
              <el-table-column prop="orderSideEnum" label="方向" width="100" />
              <el-table-column prop="buyPrice" label="买入价" width="120">
                <template #default="{ row }">{{ row.buyPrice ?? "-" }}</template>
              </el-table-column>
              <el-table-column prop="amount" label="数量" width="120" />
              <el-table-column prop="lossPrice" label="止损价" width="120">
                <template #default="{ row }">{{ row.lossPrice ?? "-" }}</template>
              </el-table-column>
              <el-table-column prop="gainPrice" label="止盈价" width="120">
                <template #default="{ row }">{{ row.gainPrice ?? "-" }}</template>
              </el-table-column>
              <el-table-column label="状态" width="70">
                <template #default="{ row }">{{ mapOrderStatusLabel(row.tradeOrderItemStatus) }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="开仓时间" min-width="160">
                <template #default="{ row }">{{
                  formatOpenTime(row)
                }}</template>
              </el-table-column>
            </el-table>
            <div class="section-subtitle">平仓记录</div>
            <el-table :data="orderDetailsCloses" size="small" border style="width: 100%">
              <el-table-column prop="exitPrice" label="平仓价" width="120" />
              <el-table-column prop="closedVolume" label="平仓数量" width="120" />
              <el-table-column prop="income" label="收益(USDT)" width="140" />
              <el-table-column prop="charge" label="手续费" width="120" />
              <el-table-column prop="exitType" label="出场类型" width="140" />
              <el-table-column prop="exitTime" label="平仓时间" min-width="160">
                <template #default="{ row }">{{ formatTime(row.exitTime) }}</template>
              </el-table-column>
            </el-table>
          </div>
        </el-dialog>
        <!-- 订单信息面板 -->
        <div v-show="bottomActiveTab === 'orders'" class="tab-panel">
          <div class="orders-header">
            <div class="orders-filters">
              <label>订单号:</label>
              <el-input
                v-model="orderSnFilter"
                placeholder="请输入订单号"
                clearable
                style="width: 180px"
                @keyup.enter="loadOrders"
                @clear="onOrderSnFilterChange"
              />
              <label>状态筛选:</label>
              <el-select
                v-model="orderStatusFilter"
                placeholder="全部"
                style="width: 150px"
                @change="loadOrders"
              >
                <el-option label="全部" value="" />
                <el-option label="持仓中" value="DEAL" />
                <el-option label="止盈" value="GAIN" />
                <el-option label="止损" value="LOSS" />
                <el-option label="已关闭" value="CLOSING" />
              </el-select>
              <label style="margin-left: 12px;">平仓日期:</label>
              <el-date-picker
                v-model="orderCloseDateFilter"
                type="date"
                placeholder="选择日期"
                value-format="YYYY-MM-DD"
                clearable
                style="width: 160px"
                @change="onOrderCloseDateChange"
              />
              <label style="margin-left: 12px;">机器人:</label>
              <el-select
                v-model="orderBotIdFilter"
                placeholder="全部机器人"
                clearable
                style="width: 180px"
                @change="loadOrders"
              >
                <el-option
                  v-for="robot in robots"
                  :key="robot.botId || robot.id"
                  :label="robot.botName || robot.name || robot.botId || robot.id"
                  :value="robot.botId || robot.id"
                />
              </el-select>
            </div>
            <div style="display: flex; gap: 12px; align-items: center">
              <el-button @click="loadOrders" :loading="ordersLoading"
                >刷新</el-button
              >
              <div style="display: flex; gap: 6px; align-items: center">
                <span>路径可视化</span>
                <el-switch v-model="showOrderPathOverlay" />
              </div>
              <div style="display: flex; gap: 6px; align-items: center">
              </div>
              <div style="display: flex; gap: 6px; align-items: center">
                <span>仅选中订单</span>
                <el-switch
                  v-model="showOnlySelectedOrder"
                  :disabled="!activeOrderId"
                />
              </div>
            </div>
          </div>
          <div v-if="ordersLoading" class="loading">加载中...</div>
          <div v-else-if="orders.length === 0" class="no-data">暂无订单</div>
          <div v-else class="orders-table-wrap" style="flex: 1; min-height: 0; display: flex; flex-direction: column;">
            <el-table
              :key="'orders-' + orders.length + '-' + ordersPage"
              :data="orders"
              row-key="_rowKey"
              :tree-props="{ children: '_children' }"
              size="small"
              border
              height="100%"
              table-layout="fixed"
              style="width: 100%"
              highlight-current-row
              @row-click="setActiveOrder"
            >
              <el-table-column label="订单号" width="150">
                  <template #default="{ row }">
                    <template v-if="!row._type">{{ row.orderSn || row.id || "-" }}</template>
                    <template v-else-if="row._type === 'closeItem'">{{ row.batchId || row.entrySn || '-' }}</template>
                    <template v-else>{{ row.entrySn || '-' }}</template>
                  </template>
              </el-table-column>
              <el-table-column label="方向" width="50">
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <span :class="row.orderSide === 'BUY' ? 'order-side long' : 'order-side short'">
                      {{ row.orderSide === "BUY" ? "做多" : "做空" }}
                    </span>
                  </template>
                  <span v-else-if="row._type === 'closeItem' && row.exitType" style="color:#e6a23c;font-size:12px">{{ row.exitType }}</span>
                </template>
              </el-table-column>
              <el-table-column label="均价" width="110" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{ getOrderDisplayAvgPrice(row) }}</template>
                  <template v-else-if="row._type === 'item'">{{ row.buyPrice ? formatPrice(row.buyPrice) : '-' }}</template>
                  <template v-else-if="row._type === 'closeItem'">{{ row.entryPrice ? formatPrice(row.entryPrice) : '-' }}</template>
                </template>
              </el-table-column>
              <el-table-column label="开仓价" width="100" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{ row.buyPrice || row.openPrice || "-" }}</template>
                  <template v-else-if="row._type === 'item'">{{ row.buyPrice ? formatPrice(row.buyPrice) : '-' }}</template>
                  <template v-else-if="row._type === 'closeItem'">{{ row.entryPrice ? formatPrice(row.entryPrice) : '-' }}</template>
                </template>
              </el-table-column>
              <el-table-column label="平仓价" width="100" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{ row.sellPrice || "—" }}</template>
                  <template v-else-if="row._type === 'closeItem'">{{ row.exitPrice ? formatPrice(row.exitPrice) : '-' }}</template>
                </template>
              </el-table-column>
              <el-table-column label="止盈" width="90" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{ row.gainPrice ? formatPrice(row.gainPrice) : "—" }}</template>
                  <template v-else-if="row._type === 'item'">{{ row.gainPrice ? formatPrice(row.gainPrice) : "—" }}</template>
                </template>
              </el-table-column>
              <el-table-column label="止损" width="90" align="right" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <span v-if="row.lossPrice" style="color: #f56c6c">{{ formatPrice(row.lossPrice) }}</span>
                    <span v-else>—</span>
                  </template>
                  <template v-else-if="row._type === 'item'">
                    <span v-if="row.lossPrice" style="color: #f56c6c">{{ formatPrice(row.lossPrice) }}</span>
                    <span v-else>—</span>
                  </template>
                  <template v-else-if="row._type === 'closeItem'">—</template>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="70" align="right">
                <template #default="{ row }">
                  <template v-if="!row._type">{{ row.amount ?? "-" }}</template>
                  <template v-else-if="row._type === 'item'">{{ row.amount ?? '-' }}</template>
                  <template v-else-if="row._type === 'closeItem'">{{ row.closedVolume ?? row.amount ?? '-' }}</template>
                </template>
              </el-table-column>
              <el-table-column label="剩余数量" width="70" align="right">
                <template #default="{ row }">
                  <template v-if="!row._type">{{ formatAmount(row.remainingAmount) }}</template>
                  <template v-else>—</template>
                </template>
              </el-table-column>
              <el-table-column label="杠杆" width="65" align="center">
                <template #default="{ row }">
                  <template v-if="!row._type">{{ row.leverRate ? row.leverRate + "x" : "-" }}</template>
                  <template v-else-if="row._type === 'item'">-</template>
                </template>
              </el-table-column>
              <el-table-column label="毛利" width="100" align="right">
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <span :class="formatIncome(row.income).value >= 0 ? 'profit-amount positive' : 'profit-amount negative'">{{ formatIncome(row.income).text }}</span>
                  </template>
                  <template v-else-if="row._type === 'closeItem'">
                    <span :class="(row.income ?? 0) >= 0 ? 'profit-amount positive' : 'profit-amount negative'">{{ (row.income ?? 0).toFixed(2) }}</span>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="成本" width="65" align="right">
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <span v-if="row.charge != null && row.charge !== ''" class="profit-amount negative">-{{ formatCharge(row.charge) }}</span>
                    <span v-else class="profit-amount neutral">—</span>
                  </template>
                  <template v-else-if="row._type === 'closeItem'">
                    <span v-if="row.charge != null" class="profit-amount negative">-{{ formatCharge(row.charge) }}</span>
                    <span v-else class="profit-amount neutral">—</span>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="盈亏(USD)" width="110" align="right">
                <template #default="{ row }">
                  <span :class="getPnlClass(row)">{{ getPnlText(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="收益率" width="90" align="right">
                <template #default="{ row }">
                  <span :class="getPctClass(row)">{{ getPctText(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="65">
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <span :class="['order-status', orderStatusClass(row.status)]">{{ mapOrderStatusLabel(row.status) }}</span>
                  </template>
                  <template v-else-if="row._type === 'item'">
                    <span :class="['order-status', orderStatusClass(row.status || row.tradeOrderItemStatus)]">{{ mapOrderStatusLabel(row.status || row.tradeOrderItemStatus) }}</span>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="开仓时间" width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <button class="time-jump-btn" @click.stop="jumpToOrderTime(row.orderTime)">{{ formatTime(row.orderTime) }}</button>
                  </template>
                  <template v-else-if="row._type === 'item'">
                    <button class="time-jump-btn" @click.stop="jumpToOrderTime(row.orderTime)">{{ formatTime(row.orderTime) || '-' }}</button>
                  </template>
                  <template v-else-if="row._type === 'closeItem'">—</template>
                </template>
              </el-table-column>
              <el-table-column label="平仓时间" width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <button v-if="row.sellTime" class="time-jump-btn" @click.stop="jumpToOrderTime(row.sellTime)">{{ formatTime(row.sellTime) }}</button>
                    <span v-else>—</span>
                  </template>
                  <template v-else-if="row._type === 'item'">{{ formatTime(row.sellTime) || '-' }}</template>
                  <template v-else-if="row._type === 'closeItem'">
                    <button v-if="row.exitTime" class="time-jump-btn" @click.stop="jumpToOrderTime(row.exitTime)">{{ formatTime(row.exitTime) }}</button>
                    <span v-else>—</span>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="持仓时长" width="85" show-overflow-tooltip sortable :sort-method="sortByDuration">
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <span :style="isOver24h(row.orderTime, row.sellTime) ? 'color:#ff1744' : ''">{{ calcDurationHours(row.orderTime, row.sellTime) }}</span>
                  </template>
                  <template v-else>
                    <span>—</span>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="平仓原因" width="120" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="!row._type">{{ formatCloseReason(row.closeReason || row.closeOrderType || row.exitType || row.closeType) }}</template>
                  <template v-else-if="row._type === 'closeItem'">{{ row.closeMethod || '—' }}</template>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70" fixed="right">
                <template #default="{ row }">
                  <template v-if="!row._type">
                    <el-button type="primary" link size="small" @click.stop="openOrderDetailsDialog(row)">详情</el-button>
                  </template>
                  <template v-else>
                    <span></span>
                  </template>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="orders.length > 0" class="orders-pagination">
              <el-button
                @click="goToOrdersPage(ordersPage - 1)"
                :disabled="ordersPage === 0"
                >上一页</el-button
              >
              <span
                >第 {{ ordersPage + 1 }} 页，共 {{ ordersTotalPages }} 页</span
              >
              <div class="page-input-group">
                <span>跳转到</span>
                <el-input-number
                  v-model="ordersPageInput"
                  :min="1"
                  :max="ordersTotalPages"
                  :precision="0"
                  size="small"
                  style="width: 80px"
                  @keyup.enter="jumpToOrdersPageInput"
                />
                <span>页</span>
                <el-button
                  size="small"
                  type="primary"
                  @click="jumpToOrdersPageInput"
                  >跳转</el-button
                >
              </div>
              <el-button
                @click="goToOrdersPage(ordersPage + 1)"
                :disabled="ordersPage >= ordersTotalPages - 1"
                >下一页</el-button
              >
            </div>
          </div>
        </div>
        <div v-show="bottomActiveTab === 'profit-report'" class="tab-panel">
          <div v-if="!profitReportBotId" class="orders-header">
            <div class="orders-filters">
              <label>机器人:</label>
              <el-select
                v-model="profitReportBotId"
                placeholder="请选择机器人"
                style="width: 220px"
              >
                <el-option label="请选择机器人" value="" />
                <el-option
                  v-for="robot in robots"
                  :key="robot.botId || robot.id"
                  :label="robot.botName || robot.name || robot.botId || robot.id"
                  :value="robot.botId || robot.id"
                />
              </el-select>
            </div>
          </div>
          <div v-if="!profitReportBotId" class="no-data">
            请选择机器人后查看收益统计
          </div>
          <BotPerformanceReport
            v-else
            :model-value="true"
            :inline="true"
            :bot="profitReportBot"
            @jump-time="jumpToProfitReportTime"
          >
            <template #toolbar-prefix>
              <el-form-item label="机器人">
                <el-select
                  v-model="profitReportBotId"
                  placeholder="请选择机器人"
                  style="width: 220px"
                >
                  <el-option label="请选择机器人" value="" />
                  <el-option
                    v-for="robot in robots"
                    :key="robot.botId || robot.id"
                    :label="robot.botName || robot.name || robot.botId || robot.id"
                    :value="robot.botId || robot.id"
                  />
                </el-select>
              </el-form-item>
            </template>
          </BotPerformanceReport>
        </div>
        <div v-show="bottomActiveTab === 'trade-logs'" class="tab-panel">
          <div class="log-panel">
            <div class="log-panel-header">
              <span class="log-panel-title">实时交易日志</span>
              <el-button size="small" text @click="clearTradeLogs">清屏</el-button>
            </div>
            <div class="log-panel-body">
              <div v-if="tradeLogs.length === 0" class="log-empty">暂无日志</div>
              <div v-else class="log-list">
                <div v-for="(log, idx) in tradeLogs" :key="idx" class="log-item">
                  <span class="log-time">{{ log.time }}</span>
                  <span class="log-symbol">{{ log.symbol }}</span>
                  <span class="log-side" :class="log.side">{{ log.sideText }}</span>
                  <span class="log-price">{{ log.price }}</span>
                  <span class="log-qty">{{ log.qty }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    </div>
    <LingSheAiPanel
      v-if="indicators.lingsheAi"
      :symbol="selectedSymbol"
      @close="setIndicator('lingsheAi', false)"
    />
</template>
<script setup lang="ts">
import {
  onMounted,
  onBeforeUnmount,
  ref,
  computed,
  nextTick,
  watch,
  inject,
} from "vue";
import {
  createChart,
  CandlestickSeries,
  LineSeries,
  HistogramSeries,
  createSeriesMarkers,
  LineStyle,
  type IChartApi,
  type ISeriesApi,
  type Time,
} from "lightweight-charts";
import {
  jumpToTime,
  loadKLineData,
  loadKLineSignals,
  getSupportedSymbols,
  getSupportedSymbolDetails,
  type KLineSignal,
} from "@/api/kline";
import { TimezoneHelper } from "@/utils/TimezoneHelper";
import {
  calculateMACD,
  calculateBoll,
  emaArray,
  atrArray,
  smaArray,
  stdDevArray,
  maByType,
  rsiArray,
  calculateRangeFilter,
  calculateAndeanOscillator,
} from "@/utils/kline/indicators";
import {
  useKLineWebSocket,
  type KLineUpdateMessage,
} from "@/services/websocket/klineWebSocket";
import BacktestRunPanel from "@/components/kline/BacktestRunPanel.vue";
import BacktestRecordsPanel from "@/components/kline/BacktestRecordsPanel.vue";
import LingSheAiPanel from "@/components/LingSheAiPanel.vue";
import BotPerformanceReport from "@/components/trading/BotPerformanceReport.vue";
import XiaoLingBaoDialog from "@/components/kline/XiaoLingBaoDialog.vue";
import SignalTooltip from "@/components/kline/SignalTooltip.vue";
import type { SignalTooltipRow } from "@/components/kline/SignalTooltip.types";
import {
  getBacktestRecords,
  deleteBacktestRecord as deleteBacktestRecordAPI,
  getTrendAnalysis,
  getElliottWaveAnalysis,
  getLogRegChannelIndicator,
} from "@/api/member";
import { getSmc } from "@/api";
import {
  runAsyncBacktest,
  getBacktestTaskStatus,
  getAsyncBacktestResult,
  stopAsyncBacktest,
  getBacktestTypes,
  getBacktestResults as getBacktestResultsAPI,
  getBacktestReport,
  generateBacktestReport,
} from "@/api/backtest";
import { getTradingBots } from "@/api/robot";
import { getMyPurchases } from "@/api/communityMarket";
import {
  queryOrders,
  manualOpenOrder,
  manualClosePosition,
  manualReversePosition,
  listOrderItems,
  listOrderCloses,
  listOrderCloseItems,
  updateOrderItemGainLoss,
  cancelOrder,
} from "@/api/tradeOrder";
import { getAllStrategyTypes } from "@/api/strategy";
import { ElMessage, ElMessageBox, ElNotification } from "element-plus";
import {
  Search,
  Plus,
  TrendCharts,
  Bell,
  VideoPlay,
  RefreshLeft,
  RefreshRight,
  Document,
  Sunny,
  Setting,
  Grid,
  Menu,
  Pointer,
  Minus,
  Sort,
  DataLine,
  EditPen,
  FullScreen,
  ZoomIn,
  ZoomOut,
  Lock,
  Delete,
  View,
  Hide,
  Close,
  Check,
  DocumentCopy,
  Download,
  ChatDotRound,
  ShoppingCart,
  Promotion,
} from "@element-plus/icons-vue";
import * as echarts from "echarts";
import SockJS from "sockjs-client";
import { Stomp } from "@stomp/stompjs";
import { useAuthStore } from "@/stores/auth";
import { useAppStore } from "@/stores/app";
import { useSmcIndicator } from "@/composables/useSmcIndicator";
import { useChanLunIndicator } from "@/composables/useChanLunIndicator";
import { useOrderPriceLines } from "@/composables/useOrderPriceLines";
interface KLineDto {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
}
const appStore = useAppStore();
const smcIndicatorApi = useSmcIndicator();
const chanLunApi = useChanLunIndicator();
const addAiSignalNotification = inject("addAiSignalNotification") as
  | ((symbol: string, direction: string, strength: number) => void)
  | undefined;
const selectedSymbol = ref("ETH-USDT-SWAP");
const selectedExchange = ref("GATEIO");
const selectedBotId = ref<string>("");
// 可用交易对列表（从 symbols 表动态获取，含 name 用于显示）
interface SymbolInfo {
  symbol: string;
  name: string;
  exchange: string;
}
const symbols = ref<SymbolInfo[]>([]);
const symbolsLoading = ref(false);

const symbolNameMap = computed(() => {
  const map: Record<string, string> = {};
  for (const s of symbols.value) {
    map[s.symbol] = s.name || s.symbol;
  }
  return map;
});

const fetchSymbols = async () => {
  symbolsLoading.value = true;
  try {
    const res = await getSupportedSymbolDetails();
    symbols.value = res?.data || [];
    // 设置默认交易所
    const defaultSymbol = symbols.value.find(s => s.symbol === selectedSymbol.value);
    if (defaultSymbol) {
      selectedExchange.value = defaultSymbol.exchange;
    }
  } catch (e) {
    console.error("获取交易对列表失败:", e);
  } finally {
    symbolsLoading.value = false;
  }
};

const availableSymbols = computed(() => {
  return symbols.value.map(s => ({
    value: s.symbol,
    label: s.name || s.symbol,
    type: s.symbol.endsWith("-SWAP") ? "swap" : "spot",
    exchange: s.exchange
  }));
});

const backtestSymbolOptions = computed(() => {
  return availableSymbols.value
    .filter(s => s.type === "swap" || s.value === "BTC-USDT" || s.value === "ETH-USDT")
    .map(s => ({ label: s.label, value: s.value }));
});

// K线图左上角显示的标的名称
const displaySymbol = computed(() => {
  return symbolNameMap.value[selectedSymbol.value] || selectedSymbol.value;
});

// 根据 symbol code 获取显示用 name
function getSymbolName(code: string | undefined | null): string {
  if (!code) return "-";
  return symbolNameMap.value[code] || code;
}

const closeDialogVisible = ref(false);
const closeSubmitting = ref(false);
const closeForm = reactive<{
  orderSn?: string;
  symbol?: string;
  side: "LONG" | "SHORT";
  quantity?: number;
  orderType: "MARKET" | "LIMIT";
  limitPrice?: number;
}>({
  side: "LONG",
  orderType: "MARKET",
});
const gainLossDialogVisible = ref(false);
const gainLossSubmitting = ref(false);
const gainLossForm = reactive<{
  orderSn?: string;
  symbol?: string;
  gainPrice?: number;
  lossPrice?: number;
}>({});
const reverseDialogVisible = ref(false);
const reverseSubmitting = ref(false);
const reverseForm = reactive<{
  orderSn?: string;
  symbol?: string;
  side: "LONG" | "SHORT";
  quantity?: number;
  orderType: "MARKET" | "LIMIT";
  limitPrice?: number;
}>({
  side: "LONG",
  orderType: "MARKET",
});
const orderDetailsDialogVisible = ref(false);
const orderDetailsLoading = ref(false);
const orderDetailsOrder = ref<any>(null);
const orderDetailsItems = ref<any[]>([]);
const orderDetailsCloses = ref<any[]>([]);
const isAnyModalOpen = computed(
  () =>
    closeDialogVisible.value ||
    gainLossDialogVisible.value ||
    reverseDialogVisible.value ||
    orderDetailsDialogVisible.value,
);
// 交易对搜索相关
const showSymbolSearch = ref(false);
const symbolSearchTerm = ref("");
const symbolSearchInputRef = ref<HTMLInputElement | null>(null);
// 过滤交易对
const filteredSymbols = computed(() => {
  const list = availableSymbols.value;
  if (!symbolSearchTerm.value) {
    return list;
  }
  const term = symbolSearchTerm.value.toLowerCase();
  return list.filter(
    (s) =>
      s.value.toLowerCase().includes(term) ||
      s.label.toLowerCase().includes(term),
  );
});
const groupedSymbols = computed(() => {
  const groups: Record<string, typeof availableSymbols.value[number]> = {};
  for (const symbol of filteredSymbols.value) {
    const key = symbol.exchange || "其他";
    if (!groups[key]) {
      groups[key] = [];
    }
    groups[key].push(symbol);
  }
  return groups;
});
// 监听弹窗打开，自动聚焦输入框
watch(showSymbolSearch, (newVal) => {
  if (newVal) {
    nextTick(() => {
      symbolSearchInputRef.value?.focus();
      symbolSearchTerm.value = "";
    });
  }
});
// 交易对切换处理
const handleSymbolSelect = (newSymbol: string, exchange?: string) => {
  if (newSymbol === selectedSymbol.value && (!exchange || exchange === selectedExchange.value)) {
    showSymbolSearch.value = false;
    return;
  }
  // 取消订阅旧交易对
  const backendInterval = convertIntervalToBackend(currentInterval.value);
  klineWebSocket.unsubscribe(selectedSymbol.value, backendInterval);
  // 更新交易对（优先按 symbol+exchange 精确匹配）
  let selectedItem: (typeof availableSymbols.value)[number] | undefined;
  if (exchange) {
    selectedItem = availableSymbols.value.find(s => s.value === newSymbol && s.exchange === exchange);
  }
  if (!selectedItem) {
    selectedItem = availableSymbols.value.find(s => s.value === newSymbol);
  }
  selectedSymbol.value = newSymbol;
  if (selectedItem) {
    selectedExchange.value = selectedItem.exchange;
  }
  // 关闭弹窗
  showSymbolSearch.value = false;
  // 清空当前数据缓存
  dataCache.value = [];
  if (candleSeries.value) {
    candleSeries.value.setData([]);
  }
  // 重新加载K线数据（跳转到当前时间）
  jumpToNow();
  loadPositionsOrders();
  // 订阅新交易对
  klineWebSocket.subscribe(selectedSymbol.value, backendInterval);
  // 刷新缠论
  if (indicators.value.chanlun) nextTick(() => updateChanLun());
};
// 将前端的时间周期格式转换为后端枚举格式
function convertIntervalToBackend(interval: string): string {
  const intervalMap: Record<string, string> = {
    "1m": "OKXMIN1",
    "3m": "OKXMIN3",
    "5m": "OKXMIN5",
    "15m": "OKXMIN15",
    "30m": "OKXMIN30",
    "1h": "OKXMIN60",
    "4h": "OKX4HOUR",
    "1d": "OKX1D",
  };
  return intervalMap[interval.toLowerCase()] || interval.toUpperCase();
}
/** 将时间戳（秒）对齐到当前周期 K 线的开始时间 */
function alignToCandleStart(timeSec: number, interval: string): number {
  const ms = getIntervalMs(interval);
  const intervalSec = ms / 1000;
  if (intervalSec <= 0) return timeSec;
  return Math.floor(timeSec / intervalSec) * intervalSec;
}

function getIntervalMs(interval: string): number {
  const map: Record<string, number> = {
    "1m": 60 * 1000,
    "3m": 3 * 60 * 1000,
    "5m": 5 * 60 * 1000,
    "15m": 15 * 60 * 1000,
    "30m": 30 * 60 * 1000,
    "1h": 60 * 60 * 1000,
    "4h": 4 * 60 * 60 * 1000,
    "1d": 24 * 60 * 60 * 1000,
  };
  return map[interval.toLowerCase()] || 60 * 1000;
}
const chartArea = ref<HTMLDivElement | null>(null);
const chartContainer = ref<HTMLDivElement | null>(null);
const chart = ref<IChartApi | null>(null);
const candleSeries = ref<ISeriesApi<"Candlestick"> | null>(null);
const orderPriceLinesApi = useOrderPriceLines(candleSeries, chart);
// v5: markers 通过 createSeriesMarkers 插件管理，series 上不再有 markers/setMarkers
const seriesMarkersRef = ref<ReturnType<typeof createSeriesMarkers> | null>(
  null,
);
// 最新价格线
const currentPriceLineRef = ref<any>(null);
// 价格轴标签
const priceAxisLabel = ref<{
  top: number;
  price: string;
  color: string;
} | null>(null);
const signalTooltip = ref<{
  visible: boolean;
  left: number;
  top: number;
  title: string;
  color: string;
  rows: SignalTooltipRow[];
}>({
  visible: false,
  left: 0,
  top: 0,
  title: "",
  color: "#303133",
  rows: [],
});
// RAF 循环 ID（用于更新价格轴标签位置）
let priceAxisLabelRafId: number | null = null;
// MACD 副图系列（新建 pane，见 https://tradingview.github.io/lightweight-charts/docs/5.0/panes）
const macdSeriesRef = ref<{
  macd: ISeriesApi<"Line"> | null;
  signal: ISeriesApi<"Line"> | null;
  histogram: ISeriesApi<"Histogram"> | null;
}>({ macd: null, signal: null, histogram: null });
// BOLL 主图叠加（三条线：中轨、上轨、下轨）
const bollSeriesRef = ref<{
  middle: ISeriesApi<"Line"> | null;
  upper: ISeriesApi<"Line"> | null;
  lower: ISeriesApi<"Line"> | null;
}>({ middle: null, upper: null, lower: null });
// 反转确认：EMA3/EMA5 线 + R/C 标记 + 水平线
const reversalSeriesRef = ref<{
  ema3: ISeriesApi<"Line"> | null;
  ema5: ISeriesApi<"Line"> | null;
  bullishLine: unknown | null;
  bearishLine: unknown | null;
}>({ ema3: null, ema5: null, bullishLine: null, bearishLine: null });
// 趋势强度信号：basis/upper/lower 线（隐藏）+ K线颜色 + ▲▼X 标记
const trendStrengthSeriesRef = ref<{
  basis: ISeriesApi<"Line"> | null;
  upper: ISeriesApi<"Line"> | null;
  lower: ISeriesApi<"Line"> | null;
}>({ basis: null, upper: null, lower: null });
// 均线：EMA9 / EMA21 / EMA55 / EMA144 四条线
const maSeriesRef = ref<{
  ema1: ISeriesApi<"Line"> | null;
  ema2: ISeriesApi<"Line"> | null;
  ema3: ISeriesApi<"Line"> | null;
  ema4: ISeriesApi<"Line"> | null;
}>({ ema1: null, ema2: null, ema3: null, ema4: null });
// 趋势线指标：支撑线 + 阻力线
const trendlineSeriesRef = ref<{
  support: ISeriesApi<"Line"> | null;
  resistance: ISeriesApi<"Line"> | null;
}>({ support: null, resistance: null });

let trendlineRequestId = 0;// Range Filter：滤波器线 + 上下轨 + BUY/SELL 信号（主图 pane 0）
const MAIN_PANE_INDEX = 0;
const rangeFilterSeriesRef = ref<{
  filter: ISeriesApi<"Line"> | null;
  hiBand: ISeriesApi<"Line"> | null;
  loBand: ISeriesApi<"Line"> | null;
}>({ filter: null, hiBand: null, loBand: null });
const rangeFilterMarkersRef = ref<{ time: number; text: string }[]>([]);
const MACD_PANE_INDEX = 1;
/** MACD 副图高度（像素），库内最小 30 */
const MACD_PANE_HEIGHT = 120;
/** RSI 副图 pane 索引与高度 */
const RSI_PANE_INDEX = 2;
const RSI_PANE_HEIGHT = 100;
const rsiSeriesRef = ref<ISeriesApi<"Line"> | null>(null);
const rsiSignalSeriesRef = ref<ISeriesApi<"Line"> | null>(null);
const rsiLevelLinesRef = ref<any[]>([]);
/** 趋势强度表 TSM 副图 */
const TSM_PANE_INDEX = 3;
const TSM_PANE_HEIGHT = 100;
const tsmSeriesRef = ref<{
  trendStrength: ISeriesApi<"Histogram"> | null;
  trendStrengthMA: ISeriesApi<"Line"> | null;
}>({ trendStrength: null, trendStrengthMA: null });
/** 安第斯振荡器副图 */
const ANDEAN_PANE_INDEX = 5;
const ANDEAN_PANE_HEIGHT = 100;
const andeanOscillatorSeriesRef = ref<{
  osc: ISeriesApi<"Histogram"> | null;
  signal: ISeriesApi<"Line"> | null;
  plusLevel: ISeriesApi<"Line"> | null;
  minusLevel: ISeriesApi<"Line"> | null;
  zeroLine: ISeriesApi<"Line"> | null;
}>({
  osc: null,
  signal: null,
  plusLevel: null,
  minusLevel: null,
  zeroLine: null,
});
const andeanOscillatorMarkersRef = ref<
  {
    time: number;
    position: string;
    color: string;
    shape: string;
    text: string;
  }[]
>([]);
// 指标开关与配置
const bollConfig = ref({ enabled: false, period: 20, multiplier: 2 });
const macdConfig = ref({ fastPeriod: 12, slowPeriod: 26, signalPeriod: 9 });
const reversalConfig = ref({
  enabled: false,
  trendLookback: 7,
  trendStrength: 0.7,
  minMoveATR: 2.0,
  showBullish: true,
  showBearish: true,
  showReversalCandle: true,
  ema3Length: 3,
  ema5Length: 5,
});
const trendStrengthConfig = ref({
  enabled: false,
  period: 20,
  multiplier: 2,
  upColor: "#00ffbb",
  downColor: "#ff1100",
  candleColor: true,
});
const maConfig = ref({
  enabled: false,
  emaLines: {
    ema9: { enabled: true, period: 9 },
    ema21: { enabled: true, period: 21 },
    ema55: { enabled: false, period: 55 },
    ema144: { enabled: true, period: 144 },
  },
});
const rangeFilterConfig = ref({
  enabled: false,
  filterType: "Type 1",
  movementSource: "Close",
  rangeSize: 2.618,
  rangeScale: "Average Change",
  rangePeriod: 14,
  smoothRange: true,
  smoothingPeriod: 27,
  averageFilterChanges: true,
  numberOfChangesToAverage: 2,
  showSignals: true,
});
const smcLiteConfig = ref({
  enabled: false,
  bosModes: {
    '15m': true,
    '1h': true,
    '4h': true,
    '1D': true,
  },
  chochModes: {
    '15m': true,
    '1h': true,
    '4h': true,
    '1D': true,
  },
  obModes: {
    '15m': '',
    '1h': 'swing',
    '4h': '',
    '1D': '',
  },
});
/** RSI 副图配置 */
const rsiConfig = ref({
  enabled: false,
  period: 14,
  showLevels: true,
  showSignal: true,
  signalPeriod: 9,
});
/** 趋势强度表 TSM 配置 */
const tsmConfig = ref({
  enabled: false,
  fastLength: 20,
  slowLength: 50,
  divergenceLength: 14,
  showDivergence: false,
});
/** 安第斯振荡器配置 */
const andeanOscillatorConfig = ref({
  enabled: false,
  length: 50,
  sigLength: 9,
  showLevels: true,
  earlySignal: true,
});
/** 趋势线配置（后端 ta4j 计算） */
const trendlineConfig = ref({
  enabled: false,
  support: true,
  resistance: true,
  surroundingBars: 3,
  barCount: 50,
});
const indicators = ref<{
  macd: boolean;
  rsi: boolean;
  boll: boolean;
  reversal: boolean;
  trendStrength: boolean;
  ma: boolean;
  rangeFilter: boolean;
  smcLite: boolean;
  chanlun: boolean;
  lingsheAi: boolean;
  tsm: boolean;
  andeanOscillator: boolean;
  trendline: boolean;
}>({
  macd: false,
  rsi: false,
  boll: false,
  reversal: false,
  trendStrength: false,
  ma: false,
  rangeFilter: false,
  smcLite: false,
  chanlun: false,
  lingsheAi: false,
  tsm: false,
  andeanOscillator: false,
  trendline: false,
});
const showIndicatorsDropdown = ref(false);
const indicatorBtnRef = ref<HTMLElement | null>(null);
const indicatorDropdownStyle = computed(() => {
  if (!indicatorBtnRef.value) return { top: "0", left: "0" };
  const rect = indicatorBtnRef.value.getBoundingClientRect();
  return {
    top: `${rect.bottom + 4}px`,
    left: `${rect.left}px`,
  };
});
/** 清除反转确认：R/C 标记与水平线 */
function clearReversalConfirmationSignals() {
  const markersApi = seriesMarkersRef.value;
  if (markersApi) {
    const current = markersApi.markers() || [];
    const filtered = current.filter(
      (m: any) => m.text !== "R" && m.text !== "C",
    );
    markersApi.setMarkers([...filtered]);
  }
  const cs = candleSeries.value;
  if (cs && reversalSeriesRef.value.bullishLine) {
    cs.removePriceLine(reversalSeriesRef.value.bullishLine as any);
    reversalSeriesRef.value.bullishLine = null;
  }
  if (cs && reversalSeriesRef.value.bearishLine) {
    cs.removePriceLine(reversalSeriesRef.value.bearishLine as any);
    reversalSeriesRef.value.bearishLine = null;
  }
}
/** 反转确认：EMA3/EMA5 线 + R/C 标记 + 最后一根反转水平线 */
function updateReversalConfirmation() {
  if (!chart.value || !candleSeries.value) return;
  const data = dataCache.value;
  const cfg = reversalConfig.value;
  const lookback = cfg.trendLookback;
  if (!indicators.value.reversal) {
    if (reversalSeriesRef.value.ema3) {
      chart.value.removeSeries(reversalSeriesRef.value.ema3);
      chart.value.removeSeries(reversalSeriesRef.value.ema5!);
      reversalSeriesRef.value = {
        ema3: null,
        ema5: null,
        bullishLine: null,
        bearishLine: null,
      };
    }
    clearReversalConfirmationSignals();
    return;
  }
  if (!reversalSeriesRef.value.ema3) {
    reversalSeriesRef.value.ema3 = chart.value.addSeries(LineSeries, {
      color: "#FF6B6B",
      lineWidth: 2,
      title: "EMA3",
      priceLineVisible: false,
      lastValueVisible: true,
    });
    reversalSeriesRef.value.ema5 = chart.value.addSeries(LineSeries, {
      color: "#4ECDC4",
      lineWidth: 1,
      title: "EMA5",
      priceLineVisible: false,
      lastValueVisible: true,
    });
  }
  if (data.length < Math.max(cfg.ema5Length, lookback) + 1) {
    reversalSeriesRef.value.ema3!.setData([]);
    reversalSeriesRef.value.ema5!.setData([]);
    clearReversalConfirmationSignals();
    return;
  }
  const closes = data.map((d) => d.close);
  const ema3Values = emaArray(closes, cfg.ema3Length);
  const ema5Values = emaArray(closes, cfg.ema5Length);
  const atrValues = atrArray(data, 14);
  reversalSeriesRef.value.ema3!.setData(
    data.map((item, i) => ({
      time: item.time,
      value: ema3Values[i] ?? item.close,
    })),
  );
  reversalSeriesRef.value.ema5!.setData(
    data.map((item, i) => ({
      time: item.time,
      value: ema5Values[i] ?? item.close,
    })),
  );
  const markers: any[] = [];
  const reversalCandles = new Map<
    number,
    { close: number; type: "bullish" | "bearish" }
  >();
  for (let i = lookback; i < data.length; i++) {
    const c = data[i];
    const isGreen = c.close > c.open;
    const isRed = c.close < c.open;
    let greenCount = 0,
      redCount = 0;
    for (let j = 1; j <= lookback; j++) {
      if (i - j < 0) break;
      const prev = data[i - j];
      if (prev.close > prev.open) greenCount++;
      else if (prev.close < prev.open) redCount++;
    }
    const priorUptrend = greenCount >= lookback * cfg.trendStrength;
    const priorDowntrend = redCount >= lookback * cfg.trendStrength;
    let highest = c.high,
      lowest = c.low;
    for (let j = 1; j <= lookback; j++) {
      if (i - j < 0) break;
      const prev = data[i - j];
      if (prev.high > highest) highest = prev.high;
      if (prev.low < lowest) lowest = prev.low;
    }
    const priorMove = highest - lowest;
    const atr = atrValues[i];
    if (atr == null) continue;
    const significantUp = priorUptrend && priorMove > atr * cfg.minMoveATR;
    const significantDown = priorDowntrend && priorMove > atr * cfg.minMoveATR;
    const emaBullish = (ema3Values[i] ?? 0) > (ema5Values[i] ?? 0);
    const emaBearish = (ema3Values[i] ?? 0) < (ema5Values[i] ?? 0);
    const bullishReversal = significantDown && isGreen;
    const bearishReversal = significantUp && isRed;
    if (cfg.showReversalCandle) {
      const revUpColor = getCssVar("--mk-color-up") || "#00C853";
      const revDownColor = getCssVar("--mk-color-down") || "#FF1744";
      if (bullishReversal && cfg.showBullish) {
        markers.push({
          time: c.time,
          position: "belowBar",
          color: revUpColor + "99",
          shape: "circle",
          text: "R",
          size: 1,
        });
        reversalCandles.set(c.time, { close: c.close, type: "bullish" });
        drawReversalPriceLine(c.time, c.close, true);
      }
      if (bearishReversal && cfg.showBearish) {
        markers.push({
          time: c.time,
          position: "aboveBar",
          color: revDownColor + "99",
          shape: "circle",
          text: "R",
          size: 1,
        });
        reversalCandles.set(c.time, { close: c.close, type: "bearish" });
        drawReversalPriceLine(c.time, c.close, false);
      }
    }
    if (i > 0) {
      const prevC = data[i - 1];
      const prevRev = reversalCandles.get(prevC.time);
      if (
        prevRev?.type === "bullish" &&
        c.close > prevRev.close &&
        emaBullish &&
        cfg.showBullish
      ) {
        markers.push({
          time: c.time,
          position: "belowBar",
          color: "#00c853",
          shape: "triangleUp",
          text: "C",
          size: 2,
        });
      }
      if (
        prevRev?.type === "bearish" &&
        c.close < prevRev.close &&
        emaBearish &&
        cfg.showBearish
      ) {
        markers.push({
          time: c.time,
          position: "aboveBar",
          color: "#ff1744",
          shape: "triangleDown",
          text: "C",
          size: 2,
        });
      }
    }
  }
  clearReversalConfirmationSignals();
  const markersApi = seriesMarkersRef.value;
  if (markersApi && markers.length > 0) {
    const current = markersApi.markers() || [];
    const other = current.filter((m: any) => m.text !== "R" && m.text !== "C");
    const sorted = [...other, ...markers].sort((a, b) => a.time - b.time);
    markersApi.setMarkers(sorted);
  }
}
function drawReversalPriceLine(
  _time: number,
  price: number,
  isBullish: boolean,
) {
  const cs = candleSeries.value;
  if (!cs) return;
  if (isBullish && reversalSeriesRef.value.bullishLine) {
    cs.removePriceLine(reversalSeriesRef.value.bullishLine as any);
    reversalSeriesRef.value.bullishLine = null;
  }
  if (!isBullish && reversalSeriesRef.value.bearishLine) {
    cs.removePriceLine(reversalSeriesRef.value.bearishLine as any);
    reversalSeriesRef.value.bearishLine = null;
  }
  // v5：系列上 priceLineVisible 为 false 时自定义价格线不绘制，需临时开启
  cs.applyOptions({ priceLineVisible: true });
  const line = cs.createPriceLine({
    price,
    color: isBullish ? "#00c853" : "#ff1744",
    lineWidth: 1,
    lineStyle: LineStyle.Dotted,
    lineVisible: true,
    axisLabelVisible: false,
  });
  if (isBullish) reversalSeriesRef.value.bullishLine = line;
  else reversalSeriesRef.value.bearishLine = line;
}
/** 设置指标开关（复选框或整行点击） */
function setIndicator(
  name:
    | "macd"
    | "rsi"
    | "boll"
    | "reversal"
    | "trendStrength"
    | "ma"
    | "rangeFilter"
    | "smcLite"
    | "lingsheAi"
    | "tsm"
    | "andeanOscillator"
    | "trendline"
    | "chanlun",
  checked: boolean,
) {
  indicators.value = { ...indicators.value, [name]: checked };
  if (name === "trendline") {
    trendlineConfig.value.enabled = checked;
    nextTick(() => updateTrendline());
  } else if (name === "rsi") {
    rsiConfig.value.enabled = checked;
    nextTick(() => updateRSI());
  } else if (name === "boll") {
    bollConfig.value.enabled = checked;
    updateBoll();
  } else if (name === "reversal") {
    reversalConfig.value.enabled = checked;
    updateReversalConfirmation();
  } else if (name === "trendStrength") {
    trendStrengthConfig.value.enabled = checked;
    nextTick(() => updateTrendStrengthIndicator());
  } else if (name === "ma") {
    maConfig.value.enabled = checked;
    nextTick(() => updateMA());
  } else if (name === "rangeFilter") {
    rangeFilterConfig.value.enabled = checked;
    nextTick(() => updateRangeFilter());
  } else if (name === "smcLite") {
    smcLiteConfig.value.enabled = checked;
    nextTick(() => updateSmcLite());
  } else if (name === "chanlun") {
    nextTick(() => updateChanLun());
  } else if (name === "macd") {
    macdConfig.value.enabled = checked;
    nextTick(() => updateMACD());
  } else if (name === "tsm") {
    tsmConfig.value.enabled = checked;
    nextTick(() => updateTSM());
  } else if (name === "andeanOscillator") {
    andeanOscillatorConfig.value.enabled = checked;
    nextTick(() => updateAndeanOscillator());
  } else {
    updateMACD();
  }
}
function toggleIndicator(name: "macd" | "boll") {
  setIndicator(name, !indicators.value[name]);
  showIndicatorsDropdown.value = false;
}
/** RSI 副图：独立 pane，RSI 线 + 信号线(MA) + 可选 30/50/70 水平线 */
function clearRSI() {
  if (rsiSignalSeriesRef.value) {
    chart.value?.removeSeries(rsiSignalSeriesRef.value);
    rsiSignalSeriesRef.value = null;
  }
  if (rsiSeriesRef.value) {
    rsiLevelLinesRef.value.forEach((line: any) =>
      rsiSeriesRef.value?.removePriceLine?.(line),
    );
    rsiLevelLinesRef.value = [];
    chart.value?.removeSeries(rsiSeriesRef.value);
    rsiSeriesRef.value = null;
  }
}
function updateRSI() {
  if (!chart.value || !candleSeries.value) return;
  const cfg = rsiConfig.value;
  if (!indicators.value.rsi) {
    clearRSI();
    return;
  }
  if (!rsiSeriesRef.value) {
    rsiSeriesRef.value = chart.value.addSeries(
      LineSeries,
      {
        color: "#9C27B0",
        lineWidth: 2,
        title: "RSI",
        priceLineVisible: false,
        lastValueVisible: true,
      },
      RSI_PANE_INDEX,
    );
    const rsiPane = rsiSeriesRef.value.getPane();
    rsiPane.setHeight(RSI_PANE_HEIGHT);
    rsiSignalSeriesRef.value = rsiPane.addSeries(LineSeries, {
      color: "#FF9800",
      lineWidth: 1,
      title: "RSI 信号",
      priceLineVisible: false,
      lastValueVisible: true,
    });
  }
  const cache = getRSICache();
  if (!cache) {
    rsiSeriesRef.value.setData([]);
    rsiSignalSeriesRef.value?.setData([]);
    return;
  }
  const visibleRange = chart.value.timeScale().getVisibleRange();
  const vr = visibleRange ? { from: Number(visibleRange.from), to: Number(visibleRange.to) } : null;
  rsiSeriesRef.value.setData(clipToVisibleRange(cache.rsiData, vr));
  if (cfg.showSignal) {
    rsiSignalSeriesRef.value?.setData(clipToVisibleRange(cache.signalData, vr));
  } else {
    rsiSignalSeriesRef.value?.setData([]);
  }
  rsiLevelLinesRef.value.forEach((line: any) =>
    rsiSeriesRef.value?.removePriceLine?.(line),
  );
  rsiLevelLinesRef.value = [];
  rsiSeriesRef.value?.applyOptions({ priceLineVisible: cfg.showLevels });
  if (cfg.showLevels && rsiSeriesRef.value?.createPriceLine) {
    const secondaryColor = getCssVar("--mk-text-secondary") || "#787B86";
    const secondaryColor50 = secondaryColor.startsWith("#")
      ? secondaryColor + "80"
      : secondaryColor;
    for (const level of [70, 50, 30]) {
      const line = rsiSeriesRef.value.createPriceLine({
        price: level,
        color: level === 50 ? secondaryColor50 : secondaryColor,
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        lineVisible: true,
        axisLabelVisible: true,
      });
      rsiLevelLinesRef.value.push(line);
    }
  }
}
/** 趋势强度表 TSM 副图 */
function clearTSM() {
  if (tsmSeriesRef.value.trendStrengthMA) {
    chart.value?.removeSeries(tsmSeriesRef.value.trendStrengthMA);
    tsmSeriesRef.value.trendStrengthMA = null;
  }
  if (tsmSeriesRef.value.trendStrength) {
    chart.value?.removeSeries(tsmSeriesRef.value.trendStrength);
    tsmSeriesRef.value.trendStrength = null;
  }
}
function updateTSM() {
  if (!chart.value || !candleSeries.value) return;
  const data = dataCache.value;
  const cfg = tsmConfig.value;
  if (!indicators.value.tsm) {
    clearTSM();
    return;
  }
  const fastLen = Math.max(1, cfg.fastLength);
  const slowLen = Math.max(1, cfg.slowLength);
  if (data.length < slowLen) {
    clearTSM();
    return;
  }
  const closes = data.map((d) => d.close);
  const fastEMA = emaArray(closes, fastLen);
  const slowEMA = emaArray(closes, slowLen);
  const trendStrengthValues: (number | null)[] = [];
  for (let i = 0; i < data.length; i++) {
    const f = fastEMA[i];
    const s = slowEMA[i];
    if (f == null || s == null) {
      trendStrengthValues.push(null);
      continue;
    }
    const diff = f - s;
    const avg = (f + s) / 2;
    trendStrengthValues.push(avg !== 0 ? (diff / avg) * 100 : 0);
  }
  const trendStrengthMA = emaArray(
    trendStrengthValues.map((v) => v ?? 0) as number[],
    slowLen,
  );
  // 使用 pane.addSeries() 确保同 pane 内直方图与 MA 线共用同一价格轴；用 trendStrength.getPane() 取 pane 避免 panes()[index] 与库内顺序不一致
  if (!tsmSeriesRef.value.trendStrength) {
    tsmSeriesRef.value.trendStrength = chart.value.addSeries(
      HistogramSeries,
      {
        priceLineVisible: false,
        lastValueVisible: true,
      },
      TSM_PANE_INDEX,
    );
    const tsmPane = tsmSeriesRef.value.trendStrength.getPane();
    tsmPane.setHeight(TSM_PANE_HEIGHT);
    tsmSeriesRef.value.trendStrengthMA = tsmPane.addSeries(LineSeries, {
      color: "#FF9800",
      lineWidth: 1,
      priceLineVisible: false,
      lastValueVisible: true,
    });
  }
  const histData = data
    .map((item, i) => ({
      time: item.time,
      value: trendStrengthValues[i],
      color: (trendStrengthValues[i] ?? 0) >= 0 ? "#4CAF50" : "#F44336",
    }))
    .filter((d) => d.value != null) as {
    time: number;
    value: number;
    color: string;
  }[];
  tsmSeriesRef.value.trendStrength!.setData(histData);
  const maData = data
    .map((item, i) => ({ time: item.time, value: trendStrengthMA[i] }))
    .filter((d) => d.value != null) as { time: number; value: number }[];
  tsmSeriesRef.value.trendStrengthMA!.setData(maData);
}
/** 安第斯振荡器副图 */
function hexToRgba(hex: string, opacity: number): string {
  const m = hex.match(/^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i);
  if (!m) return hex;
  const r = parseInt(m[1], 16);
  const g = parseInt(m[2], 16);
  const b = parseInt(m[3], 16);
  return `rgba(${r},${g},${b},${opacity})`;
}
function clearAndeanOscillator() {
  const s = andeanOscillatorSeriesRef.value;
  if (s.zeroLine) {
    chart.value?.removeSeries(s.zeroLine);
    s.zeroLine = null;
  }
  if (s.minusLevel) {
    chart.value?.removeSeries(s.minusLevel);
    s.minusLevel = null;
  }
  if (s.plusLevel) {
    chart.value?.removeSeries(s.plusLevel);
    s.plusLevel = null;
  }
  if (s.signal) {
    chart.value?.removeSeries(s.signal);
    s.signal = null;
  }
  if (s.osc) {
    chart.value?.removeSeries(s.osc);
    s.osc = null;
  }
  andeanOscillatorMarkersRef.value = [];
  const api = seriesMarkersRef.value;
  if (api) {
    const cur = api.markers() ?? [];
    const filtered = cur.filter((m: any) => (m.text !== "多" && m.text !== "空") || m.id === "api-signal");
    api.setMarkers([...filtered].sort((a: any, b: any) => a.time - b.time));
  }
}
function updateAndeanOscillator() {
  if (!chart.value || !candleSeries.value) return;
  const data = dataCache.value;
  const cfg = andeanOscillatorConfig.value;
  if (!indicators.value.andeanOscillator) {
    clearAndeanOscillator();
    return;
  }
  const len = Math.max(1, cfg.length);
  const sigLen = Math.max(1, cfg.sigLength);
  if (data.length < len) {
    clearAndeanOscillator();
    return;
  }
  const result = calculateAndeanOscillator(data, len, sigLen);
  // 未勾选水平线时移除水平线系列，便于再次勾选时补建并正确 setData
  if (!cfg.showLevels) {
    const s = andeanOscillatorSeriesRef.value;
    if (s.plusLevel) {
      chart.value?.removeSeries(s.plusLevel);
      s.plusLevel = null;
    }
    if (s.minusLevel) {
      chart.value?.removeSeries(s.minusLevel);
      s.minusLevel = null;
    }
    if (s.zeroLine) {
      chart.value?.removeSeries(s.zeroLine);
      s.zeroLine = null;
    }
  }
  // 使用 pane.addSeries() 确保同 pane 内所有系列共用同一价格轴；用 osc.getPane() 取 pane 避免 panes()[index] 与库内顺序不一致
  if (!andeanOscillatorSeriesRef.value.osc) {
    andeanOscillatorSeriesRef.value.osc = chart.value.addSeries(
      HistogramSeries,
      {
        priceLineVisible: false,
        lastValueVisible: true,
      },
      ANDEAN_PANE_INDEX,
    );
    const andeanPane = andeanOscillatorSeriesRef.value.osc.getPane();
    andeanPane.setHeight(ANDEAN_PANE_HEIGHT);
    andeanOscillatorSeriesRef.value.signal = andeanPane.addSeries(LineSeries, {
      color: "#FF9800",
      lineWidth: 1,
      priceLineVisible: false,
      lastValueVisible: true,
    });
    if (cfg.showLevels) {
      andeanOscillatorSeriesRef.value.plusLevel = andeanPane.addSeries(
        LineSeries,
        {
          color: "#4CAF50",
          lineWidth: 1,
          lineStyle: LineStyle.Dashed,
          priceLineVisible: false,
          lastValueVisible: true,
        },
      );
      andeanOscillatorSeriesRef.value.minusLevel = andeanPane.addSeries(
        LineSeries,
        {
          color: "#F44336",
          lineWidth: 1,
          lineStyle: LineStyle.Dashed,
          priceLineVisible: false,
          lastValueVisible: true,
        },
      );
      andeanOscillatorSeriesRef.value.zeroLine = andeanPane.addSeries(
        LineSeries,
        {
          color: "#888888",
          lineWidth: 1,
          lineStyle: LineStyle.Dashed,
          priceLineVisible: false,
          lastValueVisible: true,
        },
      );
    }
  }
  const oscData = data
    .map((item, i) => {
      const osc = result.osc[i];
      if (osc == null) return null;
      const plusL = result.plusLevel[i];
      let strength = 0;
      if (plusL != null && plusL > 0)
        strength = Math.min(Math.abs(osc) / (plusL * 2), 1);
      const opacity = 1 - (100 - strength * 100) / 100;
      const color =
        osc >= 0
          ? hexToRgba("#00ff00", opacity)
          : hexToRgba("#ff0000", opacity);
      return { time: item.time, value: osc, color };
    })
    .filter(Boolean) as { time: number; value: number; color: string }[];
  andeanOscillatorSeriesRef.value.osc!.setData(oscData);
  andeanOscillatorSeriesRef.value.signal!.setData(
    data
      .map((item, i) => ({ time: item.time, value: result.signal[i] }))
      .filter((d) => d.value != null) as { time: number; value: number }[],
  );
  // 如果 showLevels 为 true 但水平线系列未创建，需要补建（用 osc.getPane() 保证拿到正确 pane）
  if (
    cfg.showLevels &&
    !andeanOscillatorSeriesRef.value.plusLevel &&
    andeanOscillatorSeriesRef.value.osc
  ) {
    const andeanPane = andeanOscillatorSeriesRef.value.osc.getPane();
    andeanOscillatorSeriesRef.value.plusLevel = andeanPane.addSeries(
      LineSeries,
      {
        color: "#4CAF50",
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        priceLineVisible: false,
        lastValueVisible: true,
      },
    );
    andeanOscillatorSeriesRef.value.minusLevel = andeanPane.addSeries(
      LineSeries,
      {
        color: "#F44336",
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        priceLineVisible: false,
        lastValueVisible: true,
      },
    );
    andeanOscillatorSeriesRef.value.zeroLine = andeanPane.addSeries(
      LineSeries,
      {
        color: "#888888",
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        priceLineVisible: false,
        lastValueVisible: true,
      },
    );
  }
  if (cfg.showLevels && andeanOscillatorSeriesRef.value.plusLevel) {
    andeanOscillatorSeriesRef.value.plusLevel.setData(
      data
        .map((item, i) => ({ time: item.time, value: result.plusLevel[i] }))
        .filter((d) => d.value != null) as { time: number; value: number }[],
    );
    andeanOscillatorSeriesRef.value.minusLevel!.setData(
      data
        .map((item, i) => ({ time: item.time, value: result.minusLevel[i] }))
        .filter((d) => d.value != null) as { time: number; value: number }[],
    );
    andeanOscillatorSeriesRef.value.zeroLine!.setData(
      data.map((item) => ({ time: item.time, value: 0 })),
    );
  }
  const markers: {
    time: number;
    position: string;
    color: string;
    shape: string;
    text: string;
  }[] = [];
  for (let i = 1; i < data.length; i++) {
    const oscPrev = result.osc[i - 1];
    const oscCurr = result.osc[i];
    const sigPrev = result.signal[i - 1];
    const sigCurr = result.signal[i];
    if (
      oscPrev == null ||
      oscCurr == null ||
      sigPrev == null ||
      sigCurr == null
    )
      continue;
    if (cfg.earlySignal) {
      if (oscPrev <= sigPrev && oscCurr > sigCurr) {
        markers.push({
          time: data[i].time,
          position: "belowBar" as const,
          color: "#00ff00",
          shape: "arrowUp" as const,
          text: "多",
        });
      } else if (oscPrev >= sigPrev && oscCurr < sigCurr) {
        markers.push({
          time: data[i].time,
          position: "aboveBar" as const,
          color: "#ff0000",
          shape: "arrowDown" as const,
          text: "空",
        });
      }
    } else {
      if (oscPrev <= 0 && oscCurr > 0) {
        markers.push({
          time: data[i].time,
          position: "belowBar" as const,
          color: "#00ff00",
          shape: "arrowUp" as const,
          text: "多",
        });
      } else if (oscPrev >= 0 && oscCurr < 0) {
        markers.push({
          time: data[i].time,
          position: "aboveBar" as const,
          color: "#ff0000",
          shape: "arrowDown" as const,
          text: "空",
        });
      }
    }
  }
  andeanOscillatorMarkersRef.value = markers;
  const api = seriesMarkersRef.value;
  if (api) {
    const cur = api.markers() ?? [];
    // 只移除安第斯的「多/空」标记（但保留 API 信号），再合并新标记
    const filtered = cur.filter((m: any) => (m.text !== "多" && m.text !== "空") || m.id === "api-signal");
    // 新「多/空」标记放在前面（先渲染=下层），业务信号等保留在后面（后渲染=上层），使技术信号在上
    const merged = [
      ...markers.map((m) => ({ ...m, size: 2 as const })),
      ...filtered,
    ].sort((a: any, b: any) => a.time - b.time);
    api.setMarkers(merged);
  }
}
// ---- 趋势线 ----
function updateTrendline() {
  if (!chart.value) return;
  if (!indicators.value.trendline) {
    clearTrendline();
    return;
  }
  const cfg = trendlineConfig.value;
  if (!cfg.support && !cfg.resistance) {
    clearTrendline();
    return;
  }
  fetchTrendlineData();
}
async function fetchTrendlineData() {
  try {
  const requestId = ++trendlineRequestId;
    const body = {
      symbol: selectedSymbol.value,
      interval: currentInterval.value,
      size: 500,
      indicators: [] as string[],
      params: {
        surroundingBars: trendlineConfig.value.surroundingBars,
        barCount: trendlineConfig.value.barCount,
      },
    };
    if (trendlineConfig.value.support) body.indicators.push("support");
    if (trendlineConfig.value.resistance) body.indicators.push("resistance");
    if (body.indicators.length === 0) {
      clearTrendline();
      return;
    }
    const token = useAuthStore().token;
    const res = await fetch("/api/kline/trendline", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
    });
    const json = await res.json();
    if (!json || !json.success || !json.data) {
      clearTrendline();
      return;
    }
    
  if (requestId !== trendlineRequestId) return;
  renderTrendlines(json.data);
  } catch (e) {
    console.error("趋势线数据获取失败", e);
    clearTrendline();
  }
}
function renderTrendlines(data: any) {
  if (!chart.value) return;
  clearTrendline();
  const sr = data as { support?: any; resistance?: any };
  if (sr.support && sr.support.linePoints && sr.support.linePoints.length === 2) {
    const pts = sr.support.linePoints;
    const lineData = pts.map((p: any) => ({ time: Math.floor(p.time / 1000), value: p.value }));
    trendlineSeriesRef.value.support = chart.value.addSeries(LineSeries, {
      color: "#26A69A",
      lineWidth: 2,
      lineStyle: 2,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
      title: "支撑趋势线",
    });
    trendlineSeriesRef.value.support.setData(lineData);
  }
  if (sr.resistance && sr.resistance.linePoints && sr.resistance.linePoints.length === 2) {
    const pts = sr.resistance.linePoints;
    const lineData = pts.map((p: any) => ({ time: Math.floor(p.time / 1000), value: p.value }));
    trendlineSeriesRef.value.resistance = chart.value.addSeries(LineSeries, {
      color: "#EF5350",
      lineWidth: 2,
      lineStyle: 2,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
      title: "阻力趋势线",
    });
    trendlineSeriesRef.value.resistance.setData(lineData);
  }
}
function clearTrendline() {
  try {
    const s = trendlineSeriesRef.value.support;
    if (s && typeof s.remove === 'function') {
      s.remove();
    }
    trendlineSeriesRef.value.support = null;
    const r = trendlineSeriesRef.value.resistance;
    if (r && typeof r.remove === 'function') {
      r.remove();
    }
    trendlineSeriesRef.value.resistance = null;
  } catch (e) {
    console.error("\u6e05\u9664\u8d8b\u52bf\u7ebf\u5f02\u5e38", e);
  }
}
/**
 * MACD 副图：通过新建 pane（paneIndex=1）添加 MACD 线、信号线、柱状图
 */
function updateMACD() {
  if (!chart.value || !candleSeries.value) return;
  if (indicators.value.macd) {
    if (!macdSeriesRef.value.macd) {
      macdSeriesRef.value.macd = chart.value.addSeries(
        LineSeries,
        {
          color: "#2196F3",
          lineWidth: 2,
          title: "MACD",
          priceLineVisible: false,
          lastValueVisible: true,
        },
        MACD_PANE_INDEX,
      );
      macdSeriesRef.value.signal = chart.value.addSeries(
        LineSeries,
        {
          color: "#FF9800",
          lineWidth: 1,
          title: "Signal",
          priceLineVisible: false,
          lastValueVisible: true,
        },
        MACD_PANE_INDEX,
      );
      macdSeriesRef.value.histogram = chart.value.addSeries(
        HistogramSeries,
        {
          priceLineVisible: false,
          lastValueVisible: false,
        },
        MACD_PANE_INDEX,
      );
      const macdPane = macdSeriesRef.value.macd.getPane();
      macdPane.setHeight(MACD_PANE_HEIGHT);
    }
    const cache = getMACDCache();
    if (cache) {
      const visibleRange = chart.value.timeScale().getVisibleRange();
      const vr = visibleRange ? { from: Number(visibleRange.from), to: Number(visibleRange.to) } : null;
      macdSeriesRef.value.macd!.setData(clipToVisibleRange(cache.lineData, vr));
      macdSeriesRef.value.signal!.setData(clipToVisibleRange(cache.signalData, vr));
      macdSeriesRef.value.histogram!.setData(clipToVisibleRange(cache.histData, vr));
    } else {
      macdSeriesRef.value.macd!.setData([]);
      macdSeriesRef.value.signal!.setData([]);
      macdSeriesRef.value.histogram!.setData([]);
    }
  } else {
    if (macdSeriesRef.value.macd) {
      chart.value.removeSeries(macdSeriesRef.value.macd);
      chart.value.removeSeries(macdSeriesRef.value.signal!);
      chart.value.removeSeries(macdSeriesRef.value.histogram!);
      macdSeriesRef.value = { macd: null, signal: null, histogram: null };
    }
  }
}
/** BOLL 布林带：主图叠加三条线（中轨、上轨、下轨） */
function updateBoll() {
  if (!chart.value || !candleSeries.value) return;
  if (indicators.value.boll) {
    if (!bollSeriesRef.value.middle) {
      bollSeriesRef.value.middle = chart.value.addSeries(LineSeries, {
        color: "#2196F3",
        lineWidth: 2,
        title: "BOLL中轨",
        priceLineVisible: false,
        lastValueVisible: true,
      });
      bollSeriesRef.value.upper = chart.value.addSeries(LineSeries, {
        color: "#FF6B6B",
        lineWidth: 1,
        title: "BOLL上轨",
        priceLineVisible: false,
        lastValueVisible: true,
      });
      bollSeriesRef.value.lower = chart.value.addSeries(LineSeries, {
        color: "#4ECDC4",
        lineWidth: 1,
        title: "BOLL下轨",
        priceLineVisible: false,
        lastValueVisible: true,
      });
    }
    const cache = getBOLLCache();
    if (cache) {
      const visibleRange = chart.value.timeScale().getVisibleRange();
      const vr = visibleRange ? { from: Number(visibleRange.from), to: Number(visibleRange.to) } : null;
      bollSeriesRef.value.middle!.setData(clipToVisibleRange(cache.middleData, vr));
      bollSeriesRef.value.upper!.setData(clipToVisibleRange(cache.upperData, vr));
      bollSeriesRef.value.lower!.setData(clipToVisibleRange(cache.lowerData, vr));
    } else {
      bollSeriesRef.value.middle!.setData([]);
      bollSeriesRef.value.upper!.setData([]);
      bollSeriesRef.value.lower!.setData([]);
    }
  } else {
    if (bollSeriesRef.value.middle) {
      chart.value.removeSeries(bollSeriesRef.value.middle);
      chart.value.removeSeries(bollSeriesRef.value.upper!);
      chart.value.removeSeries(bollSeriesRef.value.lower!);
      bollSeriesRef.value = { middle: null, upper: null, lower: null };
    }
  }
}
/** 判断是否为趋势强度产生的标记（用于从当前 markers 中剔除） */
function isTrendStrengthMarker(m: { text?: string; size?: number }): boolean {
  return (
    m.text === "▲" ||
    m.text === "▼" ||
    (m.text === "X" && (m.size === 0.5 || m.size == null))
  );
}
/** 清除趋势强度信号标记（从 markers 中移除 ▲▼X） */
function clearTrendStrengthSignals() {
  const markersApi = seriesMarkersRef.value;
  if (!markersApi) return;
  const current = markersApi.markers() || [];
  const filtered = current.filter((m: any) => !isTrendStrengthMarker(m));
  if (filtered.length !== current.length) {
    markersApi.setMarkers(
      [...filtered].sort((a: any, b: any) => a.time - b.time),
    );
  }
}
/** 清除趋势强度指标（移除三条线、标记，恢复 K 线默认颜色） */
function clearTrendStrengthIndicator() {
  if (trendStrengthSeriesRef.value.basis) {
    chart.value?.removeSeries(trendStrengthSeriesRef.value.basis);
    chart.value?.removeSeries(trendStrengthSeriesRef.value.upper!);
    chart.value?.removeSeries(trendStrengthSeriesRef.value.lower!);
    trendStrengthSeriesRef.value = { basis: null, upper: null, lower: null };
  }
  clearTrendStrengthSignals();
  if (candleSeries.value && dataCache.value.length > 0) {
    candleSeries.value.setData(
      dataCache.value.map((d) => ({
        time: d.time,
        open: d.open,
        high: d.high,
        low: d.low,
        close: d.close,
      })),
    );
  }
}
/** 更新趋势强度指标：SMA±标准差、三条线（中轨/上轨/下轨）、▲▼X 标记（不修改 K 线数据以免 v5 下消失） */
function updateTrendStrengthIndicator() {
  if (!chart.value || !candleSeries.value) return;
  const data = dataCache.value;
  const cfg = trendStrengthConfig.value;
  if (!indicators.value.trendStrength) {
    clearTrendStrengthIndicator();
    return;
  }
  const period = cfg.period;
  if (data.length < period) {
    return;
  }
  const closes = data.map((d) => d.close);
  const smaValues = smaArray(closes, period);
  const stdDevValues = stdDevArray(data, smaValues, period);
  const trendValues: (number | null)[] = [];
  let lastTrend = 0;
  for (let i = 0; i < data.length; i++) {
    if (smaValues[i] == null || stdDevValues[i] == null) {
      trendValues.push(null);
      continue;
    }
    const basis = smaValues[i]!;
    const upper = basis + stdDevValues[i]!;
    const lower = basis - stdDevValues[i]!;
    const close = data[i].close;
    if (close > basis && close > upper) lastTrend = 1;
    else if (close < basis && close < lower) lastTrend = -1;
    trendValues.push(lastTrend);
  }
  const upper1Values: (number | null)[] = [];
  const lower1Values: (number | null)[] = [];
  const upperValues: (number | null)[] = [];
  const lowerValues: (number | null)[] = [];
  for (let i = 0; i < data.length; i++) {
    if (smaValues[i] == null || stdDevValues[i] == null) {
      upperValues.push(null);
      lowerValues.push(null);
      upper1Values.push(null);
      lower1Values.push(null);
    } else {
      const basis = smaValues[i]!;
      const std = stdDevValues[i]!;
      upperValues.push(basis + std);
      lowerValues.push(basis - std);
      upper1Values.push(basis + std * cfg.multiplier);
      lower1Values.push(basis - std * cfg.multiplier);
    }
  }
  if (!trendStrengthSeriesRef.value.basis) {
    trendStrengthSeriesRef.value.basis = chart.value.addSeries(LineSeries, {
      color: "#787b86",
      lineWidth: 1,
      title: "趋势中轨",
      priceLineVisible: false,
      lastValueVisible: true,
    });
    trendStrengthSeriesRef.value.upper = chart.value.addSeries(LineSeries, {
      color: "#00ffbb",
      lineWidth: 1,
      title: "上轨",
      priceLineVisible: false,
      lastValueVisible: true,
    });
    trendStrengthSeriesRef.value.lower = chart.value.addSeries(LineSeries, {
      color: "#ff1100",
      lineWidth: 1,
      title: "下轨",
      priceLineVisible: false,
      lastValueVisible: true,
    });
  }
  const basisData = data
    .map((item, i) => ({ time: item.time, value: smaValues[i] }))
    .filter((d) => d.value != null) as { time: number; value: number }[];
  const upperData = data
    .map((item, i) => ({ time: item.time, value: upperValues[i] }))
    .filter((d) => d.value != null) as { time: number; value: number }[];
  const lowerData = data
    .map((item, i) => ({ time: item.time, value: lowerValues[i] }))
    .filter((d) => d.value != null) as { time: number; value: number }[];
  trendStrengthSeriesRef.value.basis!.setData(basisData);
  trendStrengthSeriesRef.value.upper!.setData(upperData);
  trendStrengthSeriesRef.value.lower!.setData(lowerData);
  // 不再对 K 线调用 setData，避免 v5 下导致 K 线数据消失；趋势强度仅通过三条线 + ▲▼X 标记展示
  const markers: any[] = [];
  for (let i = 1; i < trendValues.length; i++) {
    if (trendValues[i] == null || trendValues[i - 1] == null) continue;
    const prevTrend = trendValues[i - 1]!;
    const currTrend = trendValues[i]!;
    if (prevTrend <= 0 && currTrend > 0) {
      markers.push({
        time: data[i].time,
        position: "belowBar",
        color: cfg.upColor,
        shape: "circle",
        text: "▲",
        size: 1,
      });
    } else if (prevTrend >= 0 && currTrend < 0) {
      markers.push({
        time: data[i].time,
        position: "aboveBar",
        color: cfg.downColor,
        shape: "circle",
        text: "▼",
        size: 1,
      });
    }
  }
  for (let i = 1; i < data.length; i++) {
    if (
      upper1Values[i] == null ||
      lower1Values[i] == null ||
      upper1Values[i - 1] == null ||
      lower1Values[i - 1] == null
    )
      continue;
    const prevClose = data[i - 1].close;
    const currClose = data[i].close;
    const prevLower1 = lower1Values[i - 1]!;
    const currLower1 = lower1Values[i]!;
    const prevUpper1 = upper1Values[i - 1]!;
    const currUpper1 = upper1Values[i]!;
    if (prevClose <= prevLower1 && currClose > currLower1) {
      markers.push({
        time: data[i].time,
        position: "belowBar",
        color: cfg.upColor,
        shape: "circle",
        text: "X",
        size: 0.5,
      });
    } else if (prevClose >= prevUpper1 && currClose < currUpper1) {
      markers.push({
        time: data[i].time,
        position: "aboveBar",
        color: cfg.downColor,
        shape: "circle",
        text: "X",
        size: 0.5,
      });
    }
  }
  const markersApi = seriesMarkersRef.value;
  if (markersApi) {
    const current = markersApi.markers() || [];
    const other = current.filter((m: any) => !isTrendStrengthMarker(m));
    const merged = [...other, ...markers].sort(
      (a: any, b: any) => a.time - b.time,
    );
    markersApi.setMarkers(merged);
  }
}
/** 根据 chart 可见范围裁剪数据（保留可视区域 ± padding 根 K 线） */
function clipToVisibleRange<T extends { time: number }>(
  data: T[],
  visibleRange: { from: number; to: number } | null,
  padding = 5,
): T[] {
  if (!visibleRange || !data?.length) return data;
  const from = visibleRange.from - padding;
  const to = visibleRange.to + padding;
  return data.filter(p => p.time >= from && p.time <= to);
}
// ---------- 指标计算缓存（避免拖动时重复计算）----------
interface MACacheEntry {
  lineData: { time: number; value: number }[];
  enabled: boolean;
}
interface MACache {
  dataLen: number;
  configSig: string;
  ema9: MACacheEntry | null;
  ema21: MACacheEntry | null;
  ema55: MACacheEntry | null;
  ema144: MACacheEntry | null;
}
let maCache: MACache | null = null;
function maConfigSig(cfg: typeof maConfig.value): string {
  return `${cfg.emaLines.ema9.enabled}|${cfg.emaLines.ema9.period}|${cfg.emaLines.ema21.enabled}|${cfg.emaLines.ema21.period}|${cfg.emaLines.ema55.enabled}|${cfg.emaLines.ema55.period}|${cfg.emaLines.ema144.enabled}|${cfg.emaLines.ema144.period}`;
}
function buildMACache(): void {
  const data = dataCache.value;
  const cfg = maConfig.value;
  if (!data.length) { maCache = null; return; }
  const closes = data.map((d) => d.close);
  const mkEntry = (enabled: boolean, period: number): MACacheEntry | null => {
    if (!enabled || data.length < period) return null;
    const vals = emaArray(closes, period);
    const lineData = data
      .map((item, i) => ({ time: item.time, value: vals[i] }))
      .filter((d) => d.value != null) as { time: number; value: number }[];
    return { lineData, enabled };
  };
  maCache = {
    dataLen: data.length,
    configSig: maConfigSig(cfg),
    ema9: mkEntry(cfg.emaLines.ema9.enabled, cfg.emaLines.ema9.period),
    ema21: mkEntry(cfg.emaLines.ema21.enabled, cfg.emaLines.ema21.period),
    ema55: mkEntry(cfg.emaLines.ema55.enabled, cfg.emaLines.ema55.period),
    ema144: mkEntry(cfg.emaLines.ema144.enabled, cfg.emaLines.ema144.period),
  };
}
function getMACache(): MACache {
  if (!maCache || maCache.dataLen !== dataCache.value.length || maCache.configSig !== maConfigSig(maConfig.value)) {
    buildMACache();
  }
  return maCache!;
}
// ---------- RSI 缓存 ----------
interface RSICache {
  dataLen: number;
  configSig: string;
  rsiData: { time: number; value: number }[];
  signalData: { time: number; value: number }[];
}
let rsiCache: RSICache | null = null;
function rsiConfigSig(cfg: typeof rsiConfig.value): string {
  return `${cfg.period}|${cfg.signalPeriod ?? 9}|${cfg.showSignal}`;
}
function buildRSICache(): void {
  const data = dataCache.value;
  if (!data.length) { rsiCache = null; return; }
  const cfg = rsiConfig.value;
  const period = Math.max(1, cfg.period);
  if (data.length < period + 1) { rsiCache = null; return; }
  const closes = data.map((d) => d.close);
  const rsiValues = rsiArray(closes, period);
  const rsiData = data
    .map((item, i) => ({ time: item.time, value: rsiValues[i] }))
    .filter((d) => d.value != null) as { time: number; value: number }[];
  const sigPeriod = Math.max(1, cfg.signalPeriod ?? 9);
  const signalValues: (number | null)[] = [];
  for (let i = 0; i < data.length; i++) {
    if (i < period - 1) { signalValues.push(null); continue; }
    const start = Math.max(period - 1, i - sigPeriod + 1);
    let sum = 0, count = 0;
    for (let j = start; j <= i; j++) {
      const v = rsiValues[j];
      if (v != null) { sum += v; count++; }
    }
    signalValues.push(count >= sigPeriod ? sum / count : null);
  }
  const signalData = data
    .map((item, i) => ({ time: item.time, value: signalValues[i] }))
    .filter((d) => d.value != null) as { time: number; value: number }[];
  rsiCache = { dataLen: data.length, configSig: rsiConfigSig(cfg), rsiData, signalData };
}
function getRSICache(): RSICache | null {
  const cfg = rsiConfig.value;
  if (!rsiCache || rsiCache.dataLen !== dataCache.value.length || rsiCache.configSig !== rsiConfigSig(cfg)) {
    buildRSICache();
  }
  return rsiCache;
}
// ---------- MACD 缓存 ----------
interface MCDCache {
  dataLen: number;
  configSig: string;
  lineData: { time: number; value: number }[];
  signalData: { time: number; value: number }[];
  histData: { time: number; value: number; color: string }[];
}
let macdCache: MCDCache | null = null;
function macdConfigSig(cfg: typeof macdConfig.value): string {
  return `${cfg.fastPeriod}|${cfg.slowPeriod}|${cfg.signalPeriod}`;
}
function buildMACDCache(): void {
  const data = dataCache.value;
  if (!data.length) { macdCache = null; return; }
  const { fastPeriod, slowPeriod } = macdConfig.value;
  if (data.length < slowPeriod) { macdCache = null; return; }
  const raw = calculateMACD(data, fastPeriod, slowPeriod, macdConfig.value.signalPeriod);
  if (!raw.length) { macdCache = null; return; }
  macdCache = {
    dataLen: data.length,
    configSig: macdConfigSig(macdConfig.value),
    lineData: raw.map((d) => ({ time: d.time, value: d.macd })),
    signalData: raw.map((d) => ({ time: d.time, value: d.signal })),
    histData: raw.map((d) => ({ time: d.time, value: d.histogram, color: d.histogram >= 0 ? "#26a69a" : "#ef5350" })),
  };
}
function getMACDCache(): MCDCache | null {
  if (!macdCache || macdCache.dataLen !== dataCache.value.length || macdCache.configSig !== macdConfigSig(macdConfig.value)) {
    buildMACDCache();
  }
  return macdCache;
}
// ---------- BOLL 缓存 ----------
interface BOLLCache {
  dataLen: number;
  configSig: string;
  middleData: { time: number; value: number }[];
  upperData: { time: number; value: number }[];
  lowerData: { time: number; value: number }[];
}
let bollCache: BOLLCache | null = null;
function bollConfigSig(cfg: typeof bollConfig.value): string {
  return `${cfg.period}|${cfg.multiplier}`;
}
function buildBOLLCache(): void {
  const data = dataCache.value;
  if (!data.length) { bollCache = null; return; }
  const period = bollConfig.value.period;
  if (data.length < period) { bollCache = null; return; }
  const raw = calculateBoll(data, period, bollConfig.value.multiplier);
  if (!raw.length) { bollCache = null; return; }
  bollCache = {
    dataLen: data.length,
    configSig: bollConfigSig(bollConfig.value),
    middleData: raw.map((d) => ({ time: d.time, value: d.middle })),
    upperData: raw.map((d) => ({ time: d.time, value: d.upper })),
    lowerData: raw.map((d) => ({ time: d.time, value: d.lower })),
  };
}
function getBOLLCache(): BOLLCache | null {
  if (!bollCache || bollCache.dataLen !== dataCache.value.length || bollCache.configSig !== bollConfigSig(bollConfig.value)) {
    buildBOLLCache();
  }
  return bollCache;
}
/** 均线：EMA9 / EMA21 / EMA55 / EMA144 四条线 */
function updateMA() {
  if (!chart.value || !candleSeries.value) return;
  if (!indicators.value.ma) {
    maSeriesRef.value.ema1!.setData([]);
    maSeriesRef.value.ema2!.setData([]);
    maSeriesRef.value.ema3!.setData([]);
    maSeriesRef.value.ema4!.setData([]);
    return;
  }
  const cache = getMACache();
  if (!cache) return;
  if (cache.ema9?.enabled && cache.ema9.lineData.length) {
    maSeriesRef.value.ema1!.setData(cache.ema9.lineData);
  } else {
    maSeriesRef.value.ema1!.setData([]);
  }
  if (cache.ema21?.enabled && cache.ema21.lineData.length) {
    maSeriesRef.value.ema2!.setData(cache.ema21.lineData);
  } else {
    maSeriesRef.value.ema2!.setData([]);
  }
  if (cache.ema55?.enabled && cache.ema55.lineData.length) {
    maSeriesRef.value.ema3!.setData(cache.ema55.lineData);
  } else {
    maSeriesRef.value.ema3!.setData([]);
  }
  if (cache.ema144?.enabled && cache.ema144.lineData.length) {
    maSeriesRef.value.ema4!.setData(cache.ema144.lineData);
  } else {
    maSeriesRef.value.ema4!.setData([]);
  }
}
/** Range Filter：滤波器线 + 上下轨 + BUY/SELL 信号 */
function clearRangeFilter() {
  if (rangeFilterSeriesRef.value.filter) {
    chart.value?.removeSeries(rangeFilterSeriesRef.value.filter);
    rangeFilterSeriesRef.value.filter = null;
  }
  if (rangeFilterSeriesRef.value.hiBand) {
    chart.value?.removeSeries(rangeFilterSeriesRef.value.hiBand);
    rangeFilterSeriesRef.value.hiBand = null;
  }
  if (rangeFilterSeriesRef.value.loBand) {
    chart.value?.removeSeries(rangeFilterSeriesRef.value.loBand);
    rangeFilterSeriesRef.value.loBand = null;
  }
  const api = seriesMarkersRef.value;
  if (api && rangeFilterMarkersRef.value.length > 0) {
    const current = api.markers() || [];
    const filtered = current.filter(
      (m: any) =>
        !rangeFilterMarkersRef.value.some(
          (r) => r.time === m.time && r.text === m.text,
        ),
    );
    api.setMarkers([...filtered].sort((a: any, b: any) => a.time - b.time));
  }
  rangeFilterMarkersRef.value = [];
}
function updateRangeFilter() {
  if (!chart.value || !candleSeries.value) return;
  const data = dataCache.value;
  const cfg = rangeFilterConfig.value;
  if (!indicators.value.rangeFilter) {
    clearRangeFilter();
    return;
  }
  if (data.length < 2) {
    clearRangeFilter();
    return;
  }
  const rfData = calculateRangeFilter(data as any, {
    filterType: cfg.filterType,
    movementSource: cfg.movementSource as "Close" | "Wicks",
    rangeSize: cfg.rangeSize,
    rangeScale: cfg.rangeScale as any,
    rangePeriod: cfg.rangePeriod,
    showSignals: cfg.showSignals,
  });
  if (rfData.filter.length === 0) return;
  // 主图 pane 0 上添加/更新三条线，确保显示在 K 线同一层
  if (!rangeFilterSeriesRef.value.filter) {
    rangeFilterSeriesRef.value.filter = chart.value.addSeries(
      LineSeries,
      {
        color: "#2196F3",
        lineWidth: 2,
        title: "Range Filter",
        priceLineVisible: false,
        lastValueVisible: true,
      },
      MAIN_PANE_INDEX,
    );
    rangeFilterSeriesRef.value.hiBand = chart.value.addSeries(
      LineSeries,
      {
        color: "#4CAF50",
        lineWidth: 1,
        lineStyle: 1,
        title: "High Band",
        priceLineVisible: false,
        lastValueVisible: true,
      },
      MAIN_PANE_INDEX,
    );
    rangeFilterSeriesRef.value.loBand = chart.value.addSeries(
      LineSeries,
      {
        color: "#F44336",
        lineWidth: 1,
        lineStyle: 1,
        title: "Low Band",
        priceLineVisible: false,
        lastValueVisible: true,
      },
      MAIN_PANE_INDEX,
    );
  }
  rangeFilterSeriesRef.value.filter!.setData(rfData.filter);
  rangeFilterSeriesRef.value.hiBand!.setData(rfData.hiBand);
  rangeFilterSeriesRef.value.loBand!.setData(rfData.loBand);
  if (cfg.showSignals && rfData.signals.length > 0) {
    const rfMarkers = rfData.signals.map((s) => ({
      time: s.time,
      position: (s.type === "BUY" ? "belowBar" : "aboveBar") as const,
      color: s.type === "BUY" ? "#4CAF50" : "#F44336",
      shape: (s.type === "BUY" ? "arrowUp" : "arrowDown") as const,
      text: s.type,
      size: 2,
    }));
    rangeFilterMarkersRef.value = rfData.signals.map((s) => ({
      time: s.time,
      text: s.type,
    }));
    const api = seriesMarkersRef.value;
    if (api) {
      const current = api.markers() || [];
      const other = current.filter(
        (m: any) =>
          !rangeFilterMarkersRef.value.some(
            (r) => r.time === m.time && r.text === m.text,
          ),
      );
      const merged = [...other, ...rfMarkers].sort(
        (a: any, b: any) => a.time - b.time,
      );
      api.setMarkers(merged);
    }
  }
}
/** SMC 多时间框架覆盖层 */
function updateSmcLite() {
  if (!chart.value) return;
  if (!indicators.value.smcLite || !smcLiteConfig.value.enabled) {
    smcIndicatorApi.destroy(chart.value);
    return;
  }
  const activeTfs: Record<string, boolean> = {};
  for (const [tf, mode] of Object.entries(smcLiteConfig.value.obModes)) {
    activeTfs[tf] = !!mode;
  }
  smcIndicatorApi.update(chart.value, {
    enabled: true,
    timeframes: activeTfs,
    symbol: selectedSymbol.value,
    obTypes: smcLiteConfig.value.obModes,
    showBOS: smcLiteConfig.value.bosModes,
    showCHOCH: smcLiteConfig.value.chochModes,
  });
}
function updateChanLun() {
  if (!chart.value || !candleSeries.value) return;
  if (!indicators.value.chanlun) {
    chanLunApi.destroy();
    return;
  }
  chanLunApi.update(chart.value, candleSeries.value, {
    enabled: true,
    symbol: selectedSymbol.value,
    interval: convertIntervalToBackend(currentInterval.value),
    limit: 500,
  });
}
const containerWidth = ref(1200);
const containerHeight = ref(600);
let resizeObserver: ResizeObserver | null = null;
// 数据缓存 & 无限滚动加载状态
const dataCache = ref<KLineDto[]>([]);
let isLoadingMore = false;
let visibleRangeChangeHandler: ((logicalRange: import("lightweight-charts").LogicalRange | null) => void) | null = null;
let indicatorRangeHandler: (() => void) | null = null;
let indicatorRangeRAF: number | null = null;
let loadByTimeSeqId = 0;
const MIN_BARS_THRESHOLD = 50; // 接近边缘多少根时触发加载
const LOAD_BATCH_SIZE = 300; // 每次加载多少根
const MAX_CACHE_SIZE = 3000; // 最大缓存数据量，超过后清理旧数据
// 闪烁标记定时器
let flashMarkerTimer: number | null = null;
let flashMarkerTimeout: number | null = null;
// 防抖定时器，避免频繁触发加载
let loadDebounceTimer: number | null = null;
// WebSocket 实时更新
const klineWebSocket = useKLineWebSocket();
const isRealtimeMode = ref(true); // 是否处于实时模式（显示最新数据）
// 信号标注相关
const signalsCache = ref<KLineSignal[]>([]);
const isLoadingSignals = ref(false);
let signalLoadDebounceTimer: number | null = null;
const SIGNAL_LOAD_DEBOUNCE = 300; // 信号加载防抖时间（ms）
const topIntervals = [
  { label: "3m", value: "3m" },
  { label: "5m", value: "5m" },
  { label: "15m", value: "15m" },
  { label: "1h", value: "1h" },
  { label: "4h", value: "4h" },
  { label: "D", value: "1d" },
];
const bottomIntervals = [
  { label: "1D", value: "1d" },
  { label: "5D", value: "5d" },
  { label: "1M", value: "1m" },
  { label: "3M", value: "3m" },
  { label: "6M", value: "6m" },
  { label: "YTD", value: "ytd" },
  { label: "1Y", value: "1y" },
  { label: "5Y", value: "5y" },
  { label: "All", value: "all" },
];
const currentInterval = ref("15m");
const currentBottomInterval = ref("all");
// 绘图工具状态
const activeDrawingTool = ref("cursor"); // 默认使用十字光标
// 右键菜单相关
const contextMenuVisible = ref(false);
const contextMenuPosition = ref({ x: 0, y: 0 });
const isFullscreen = ref(false);
let savedBodyCursor: string | null = null;
watch([contextMenuVisible, closeDialogVisible], ([menuVisible, dialogVisible]) => {
  const suppressed = !!menuVisible || !!dialogVisible;
  if (suppressed) {
    if (savedBodyCursor === null) {
      savedBodyCursor = document.body.style.cursor || "";
    }
    document.body.style.cursor = "default";
  } else {
    if (savedBodyCursor !== null) {
      document.body.style.cursor = savedBodyCursor;
      savedBodyCursor = null;
    } else {
      document.body.style.cursor = "";
    }
  }
  if (!chart.value) return;
  chart.value.applyOptions({
    crosshair: {
      mode: 0,
      vertLine: { visible: !suppressed },
      horzLine: { visible: !suppressed },
    },
  });
});
// 趋势线绘制状态
const isDrawingTrendLine = ref(false);
const trendLineStartPoint = ref<{ time: number; price: number } | null>(null);
const trendLineSeries = ref<ISeriesApi<"Line">[]>([]); // 存储所有趋势线
const selectedTrendLineIndex = ref<number | null>(null); // 当前选中的趋势线索引
const lastPrice = ref<number | null>(null);
const prevPrice = ref<number | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
// K线信息显示相关：光标下/最新一根K线的数据
const focusedKlineData = ref<{
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
} | null>(null);
// 时间跳转
const jumpDateTime = ref<string>("");
// 底部Tab相关
const bottomActiveTab = ref<
  "backtest" | "positions" | "orders" | "profit-report" | "trade-logs"
>("backtest");
const backtestSubTab = ref<"run" | "backtest-records">("run");
// 回测相关
const strategies = ref<any[]>([]);
const supportedBacktestTypes = ref<any[]>([]);
const backtestTimeframes = [
  { value: "1m", label: "1分钟" },
  { value: "3m", label: "3分钟" },
  { value: "5m", label: "5分钟" },
  { value: "15m", label: "15分钟" },
  { value: "30m", label: "30分钟" },
  { value: "1h", label: "1小时" },
  { value: "4h", label: "4小时" },
  { value: "1d", label: "1天" },
];
const robots = ref<any[]>([]);
const profitReportBotId = ref("");
const profitReportBot = computed(() => {
  if (!profitReportBotId.value) return null;
  const robot = robots.value.find(
    (item: any) =>
      item?.botId === profitReportBotId.value ||
      item?.id === profitReportBotId.value,
  );
  return {
    botId: robot?.botId || profitReportBotId.value,
    botName: robot?.botName || robot?.name,
    allocatedCapital: robot?.allocatedCapital,
    currentCapital: robot?.currentCapital,
  };
});
const formatDateOnly = (date: Date) => {
  const pad = (value: number) => String(value).padStart(2, "0");
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  return `${year}-${month}-${day} 00:00:00`;
};
const endDateBase = new Date();
endDateBase.setHours(0, 0, 0, 0);
const startDateBase = new Date(
  endDateBase.getTime() - 30 * 24 * 60 * 60 * 1000,
);
const defaultEndDate = formatDateOnly(endDateBase);
const defaultStartDate = formatDateOnly(startDateBase);
const backtestParams = ref({
  botId: "",
  backtestType: "TRADITIONAL_BACKTEST_NEW",
  symbols: [],
  backtestDays: 30,
  timeframe: "3m",
  startDate: defaultStartDate,
  endDate: defaultEndDate,
  commission: 0.045,
  slippage: 0,
});
const backtestRunning = ref(false);
const backtestProgress = ref(0);
const backtestMessage = ref("");
const backtestLogs = ref<
  Array<{ timestamp: number; level: string; message: string }>
>([]);
const backtestResults = ref<any>(null);
const equityProgress = ref(0);
const equityCurvePoints = ref<any[]>([]);
const currentBacktestId = ref<string | null>(null);
const lastBacktestStatus = ref("");
const lastBacktestMessage = ref("");
const lastBacktestProgress = ref(0);
const backtestStompClient = ref<any>(null);
const selectedRecordsStrategy = ref("");
const backtestRecords = ref<any[]>([]);
const backtestRecordsLoading = ref(false);
let backtestPollTimer: number | null = null;
const toNumber = (value: any) => {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : 0;
};
const normalizeBacktestResult = (raw: any) => {
  if (!raw) return null;
  const source = raw.data || raw.result || raw;
  const normalized: any = { ...source };
  if (normalized.finalCapital == null && normalized.finalValue != null) {
    normalized.finalCapital = toNumber(normalized.finalValue);
  } else if (normalized.finalCapital != null) {
    normalized.finalCapital = toNumber(normalized.finalCapital);
  }
  if (normalized.totalReturn != null) {
    normalized.totalReturn = toNumber(normalized.totalReturn);
  }
  if (normalized.maxDrawdown != null) {
    normalized.maxDrawdown = toNumber(normalized.maxDrawdown);
  }
  if (normalized.winRate != null) {
    normalized.winRate = toNumber(normalized.winRate);
  }
  if (normalized.profitFactor != null) {
    normalized.profitFactor = toNumber(normalized.profitFactor);
  }
  if (normalized.sharpeRatio != null) {
    normalized.sharpeRatio = toNumber(normalized.sharpeRatio);
  }
  if (normalized.calmarRatio != null) {
    normalized.calmarRatio = toNumber(normalized.calmarRatio);
  }
  if (normalized.annualReturn != null) {
    normalized.annualReturn = toNumber(normalized.annualReturn);
  } else if (normalized.annualizedReturn != null) {
    normalized.annualReturn = toNumber(normalized.annualizedReturn);
  }
  if (normalized.volatility != null) {
    normalized.volatility = toNumber(normalized.volatility);
  }
  if (normalized.sortinoRatio != null) {
    normalized.sortinoRatio = toNumber(normalized.sortinoRatio);
  }
  if (normalized.averageWin != null) {
    normalized.averageWin = toNumber(normalized.averageWin);
  }
  if (normalized.averageLoss != null) {
    normalized.averageLoss = toNumber(normalized.averageLoss);
  }
  if (normalized.largestWinTrade != null) {
    normalized.largestWinTrade = toNumber(normalized.largestWinTrade);
  }
  if (normalized.largestLossTrade != null) {
    normalized.largestLossTrade = toNumber(normalized.largestLossTrade);
  }
  if (!normalized.backtestId && normalized.taskId) {
    normalized.backtestId = normalized.taskId;
  }
  if (!normalized.symbol && normalized.coinId) {
    normalized.symbol = normalized.coinId;
  }
  if (!normalized.startDate && normalized.startTime) {
    normalized.startDate = normalized.startTime;
  }
  if (!normalized.endDate && normalized.endTime) {
    normalized.endDate = normalized.endTime;
  }
  return normalized;
};
const addBacktestLog = (level: string, message: string) => {
  if (!message) return;
  backtestLogs.value.unshift({
    timestamp: Date.now(),
    level,
    message,
  });
  if (backtestLogs.value.length > 200) {
    backtestLogs.value = backtestLogs.value.slice(0, 200);
  }
};
const clearBacktestLogs = () => {
  backtestLogs.value = [];
};
const parseEquityCurvePoints = (equityCurve: any) => {
  if (!equityCurve) return [];
  if (Array.isArray(equityCurve)) return equityCurve;
  if (typeof equityCurve === "string") {
    try {
      const parsed = JSON.parse(equityCurve);
      return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
      console.error("解析权益曲线数据失败:", error);
      return [];
    }
  }
  return [];
};
const loadBacktestEquityCurve = async (taskId: string) => {
  try {
    const resultResponse: any = await getBacktestResultsAPI(taskId);
    if (resultResponse && resultResponse.success) {
      equityCurvePoints.value = parseEquityCurvePoints(
        resultResponse.equityCurve,
      );
    }
  } catch (error) {
    console.error("获取权益曲线失败:", error);
  }
};
// 订单信息相关
const orders = ref<any[]>([]);
const ordersLoading = ref(false);
const ordersPage = ref(0);
const ordersTotalPages = ref(0);
const orderSnFilter = ref("");
const orderStatusFilter = ref("");
const orderCloseDateFilter = ref<string | null>(null);
const orderBotIdFilter = ref("");
const ordersPageInput = ref<number | null>(null); // 页数输入框的值
const showOrderPathOverlay = ref(false);
const showApiSignalsOverlay = ref(true);
const filterValidSignals = ref(true);
/** 入场价格线显示/隐藏，默认隐藏 */
const showEntryPrice = ref(false);
const showOnlySelectedOrder = ref(false);
const activeOrderId = ref<string | null>(null);
const orderPathMarkersRef = ref<
  Array<{
    time: number;
    position: "aboveBar" | "belowBar" | "inBar";
    color: string;
    shape: "arrowUp" | "arrowDown" | "circle";
    text: string;
    size: 1 | 2;
  }>
>([]);
// 面板拖拽调整高度相关
const bottomPanelHeight = ref(200); // 默认高度200px，上方K线区域更大
const bottomPanelRef = ref<HTMLElement | null>(null);
const isResizing = ref(false);
const minPanelHeight = 60; // 最小高度
const maxPanelHeight = ref(800); // 最大高度，在 onMounted 中动态计算
let resizeStartY = 0;
let resizeStartHeight = 0;
// 右侧面板拖拽调整宽度相关
const rightPanelWidth = ref(320); // 默认宽度320px
const rightPanelCollapsed = ref(true);
const rightPanelCollapsedWidth = 1;
const rightPanelLastWidth = ref(rightPanelWidth.value);
const rightPanelDisplayWidth = computed(() =>
  rightPanelCollapsed.value ? rightPanelCollapsedWidth : rightPanelWidth.value,
);
const rightPanelRef = ref<HTMLElement | null>(null);
const isRightPanelResizing = ref(false);
const minRightPanelWidth = 200; // 最小宽度
const maxRightPanelWidth = 600; // 最大宽度
let rightPanelResizeStartX = 0;
let rightPanelResizeStartWidth = 0;
const rightPanelActiveTab = ref<"trend" | "levels" | "history" | "elliott">(
  "trend",
);
const rightMainTab = ref<"analysis" | "smc">("analysis");
const rightPanelTitle = computed(() => {
  if (rightMainTab.value === "smc") return "SMC";
  return "市场分析";
});
// 交易日志列表
type TradeLog = {
  time: string;
  symbol: string;
  side: "buy" | "sell";
  sideText: string;
  price: string;
  qty: string;
};
const tradeLogs = ref<TradeLog[]>([]);
const trendMiniMode = ref<"support" | "resistance" | "both">("both");
const trendMiniChartRef = ref<HTMLDivElement | null>(null);
let trendMiniChart: echarts.ECharts | null = null;
const trendAnalysisData = ref<any>(null);
const trendAnalysisLoading = ref(false);
const trendAnalysisError = ref<string | null>(null);
const elliottAnalysisData = ref<any>(null);
const elliottAnalysisLoading = ref(false);
const elliottAnalysisError = ref<string | null>(null);
const logRegChannelData = ref<any>(null);
const logRegChannelLoading = ref(false);
const logRegChannelError = ref<string | null>(null);
const logRegSeries = computed(() => {
  const list = logRegChannelData.value?.series;
  return Array.isArray(list) ? list : [];
});
const logRegSlopeX100Text = computed(() => {
  const slope = Number(logRegChannelData.value?.slope);
  if (!Number.isFinite(slope)) return "--";
  return (slope * 100).toFixed(4);
});
const logRegDirectionLabel = computed(() => {
  const slope = Number(logRegChannelData.value?.slope);
  if (!Number.isFinite(slope)) return "-";
  if (slope > 1e-6) return "上升";
  if (slope < -1e-6) return "下降";
  return "震荡";
});
const logRegDirectionClass = computed(() => {
  const slope = Number(logRegChannelData.value?.slope);
  if (!Number.isFinite(slope)) return "";
  if (slope > 1e-6) return "up";
  if (slope < -1e-6) return "down";
  return "flat";
});
const logRegUpperRangeText = computed(() => {
  if (logRegSeries.value.length === 0) return "-- → --";
  const start = Number(logRegSeries.value[0]?.upper);
  const end = Number(logRegSeries.value[logRegSeries.value.length - 1]?.upper);
  return `${formatNumber(start, 2)} → ${formatNumber(end, 2)}`;
});
const logRegLowerRangeText = computed(() => {
  if (logRegSeries.value.length === 0) return "-- → --";
  const start = Number(logRegSeries.value[0]?.lower);
  const end = Number(logRegSeries.value[logRegSeries.value.length - 1]?.lower);
  return `${formatNumber(start, 2)} → ${formatNumber(end, 2)}`;
});
const logRegMiddleStartText = computed(() => {
  if (logRegSeries.value.length === 0) return "--";
  const start = Number(logRegSeries.value[0]?.middle);
  return formatNumber(start, 2);
});
const logRegMiddleEndText = computed(() => {
  const end = Number(logRegChannelData.value?.lastMiddle);
  if (Number.isFinite(end)) return formatNumber(end, 2);
  if (logRegSeries.value.length === 0) return "--";
  const v = Number(logRegSeries.value[logRegSeries.value.length - 1]?.middle);
  return formatNumber(v, 2);
});
const logRegOuterWidthText = computed(() => {
  const upper = Number(logRegChannelData.value?.lastUpper);
  const lower = Number(logRegChannelData.value?.lastLower);
  if (!Number.isFinite(upper) || !Number.isFinite(lower)) return "--";
  return formatNumber(Math.abs(upper - lower), 2);
});
const logRegInnerWidthText = computed(() => {
  const upper = Number(logRegChannelData.value?.lastUpper);
  const middle = Number(logRegChannelData.value?.lastMiddle);
  const lower = Number(logRegChannelData.value?.lastLower);
  if (
    !Number.isFinite(upper) ||
    !Number.isFinite(middle) ||
    !Number.isFinite(lower)
  )
    return "--";
  const inner1 = Math.abs(upper - middle);
  const inner2 = Math.abs(middle - lower);
  return formatNumber(Math.min(inner1, inner2), 2);
});
const logRegIsInChannel = computed(() => {
  const close = Number(logRegChannelData.value?.lastClose);
  const upper = Number(logRegChannelData.value?.lastUpper);
  const lower = Number(logRegChannelData.value?.lastLower);
  if (!Number.isFinite(close) || !Number.isFinite(upper) || !Number.isFinite(lower))
    return false;
  return close >= lower && close <= upper;
});
const logRegPriceLocationText = computed(() => {
  const close = Number(logRegChannelData.value?.lastClose);
  const upper = Number(logRegChannelData.value?.lastUpper);
  const middle = Number(logRegChannelData.value?.lastMiddle);
  const lower = Number(logRegChannelData.value?.lastLower);
  if (
    !Number.isFinite(close) ||
    !Number.isFinite(upper) ||
    !Number.isFinite(middle) ||
    !Number.isFinite(lower)
  )
    return "-";
  if (close > upper) return "上轨外";
  if (close < lower) return "下轨外";
  const width = upper - lower;
  if (!(width > 0)) return "在通道内";
  const nearThreshold = 0.15;
  const relTop = (upper - close) / width;
  const relBottom = (close - lower) / width;
  if (relTop >= 0 && relTop < nearThreshold) return "内上轨附近";
  if (relBottom >= 0 && relBottom < nearThreshold) return "内下轨附近";
  return close >= middle ? "在通道上半区" : "在通道下半区";
});
const smcLoading = ref(false);
const smcError = ref<string | null>(null);
const smcData = ref<any>(null);
const smcLatest = ref<any>(null);
const priceAlertEnabled = ref(false);
const projectionExpanded = ref(false);
const projectionDefaultCount = 3;
const priceChangeClass = computed(() => {
  if (lastPrice.value == null || prevPrice.value == null) return "";
  return lastPrice.value >= prevPrice.value ? "up" : "down";
});
let logsSocket: WebSocket | null = null;
let stompClient: any = null;
let signalStompClient: any = null;
function parseBusinessLogToTradeLog(payload: any): TradeLog | null {
  try {
    // 优先JSON解析；否则兼容“business_log | ts | type | level | traceId | {json}”纯文本
    let data: any;
    if (typeof payload === "string") {
      const text = payload.trim();
      try {
        data = JSON.parse(text);
      } catch {
        if (text.startsWith("business_log")) {
          const jsonStart = text.indexOf("{");
          if (jsonStart !== -1) {
            const jsonStr = text.substring(jsonStart).trim();
            const obj = JSON.parse(jsonStr);
            const tsMatch = text.match(/^business_log\s*\|\s*(\d+)/);
            const ts = tsMatch ? Number(tsMatch[1]) : Date.now();
            data = {
              type: "business_log",
              timestamp: ts,
              data: obj,
            };
          }
        }
      }
    } else {
      data = payload;
    }
    if (!data) return null;
    if (data.type === "connection" || data.type === "ack") return null;
    if (data.type === "business_log") {
      const ts = data.timestamp || Date.now();
      const d = data.data || {};
      const symbol = d.symbol || selectedSymbol.value || "-";
      const side = d.side === "SELL" || d.side === "sell" ? "sell" : "buy";
      const sideText = side === "buy" ? "买入" : "卖出";
      const price =
        d.price != null
          ? Number(d.price).toFixed(4)
          : d.close != null
            ? Number(d.close).toFixed(4)
            : "-";
      const qty =
        d.qty != null
          ? String(d.qty)
          : d.quantity != null
            ? String(d.quantity)
            : "-";
      return {
        time: new Date(ts).toLocaleTimeString(),
        symbol,
        side,
        sideText,
        price,
        qty,
      };
    }
    // 后端可能直接推送简化结构
    if (data.time && data.symbol && (data.side || data.action)) {
      const side = (data.side || data.action || "")
        .toString()
        .toLowerCase()
        .includes("sell")
        ? "sell"
        : "buy";
      const sideText = side === "buy" ? "买入" : "卖出";
      const price = data.price != null ? Number(data.price).toFixed(4) : "-";
      const qty = data.qty != null ? String(data.qty) : "-";
      return {
        time: new Date(data.time).toLocaleTimeString(),
        symbol: data.symbol,
        side,
        sideText,
        price,
        qty,
      };
    }
  } catch (_) {
    // 忽略解析错误
  }
  return null;
}
function connectTradeLogsWebSocket() {
  if (stompClient && stompClient.connected) return;
  // 使用STOMP/SockJS模式，与回测执行日志保持一致
  const socket = new SockJS("/ws");
  stompClient = Stomp.over(socket);
  stompClient.connect(
    {},
    (frame) => {
      console.log("交易日志WebSocket连接成功");
      // 订阅交易日志消息，使用与回测相同的模式
      stompClient.subscribe("/topic/logs", (message) => {
        const entry = parseBusinessLogToTradeLog(message.body);
        if (entry) {
          tradeLogs.value.unshift(entry);
          if (tradeLogs.value.length > 500) {
            tradeLogs.value.splice(500);
          }
        }
      });
    },
    (error) => {
      console.error("交易日志WebSocket连接失败:", error);
    },
  );
}
function disconnectTradeLogsWebSocket() {
  if (stompClient && stompClient.connected) {
    try {
      stompClient.disconnect();
    } catch {}
  }
  stompClient = null;
}
function connectSignalWebSocket() {
  if (signalStompClient && signalStompClient.connected) return;
  const socket = new SockJS("/ws");
  signalStompClient = Stomp.over(socket);
  signalStompClient.connect(
    {},
    () => {
      console.log("AI信号WebSocket连接成功");
      signalStompClient.subscribe("/topic/signals", (message: any) => {
        try {
          const signal = JSON.parse(message.body);
          console.log("收到AI信号:", signal);
          const isLongSignal = signal.direction === "LB" || signal.direction === "LONG";
          const dirText = isLongSignal ? "做多" : "做空";
          ElNotification({
            title: `AI信号 ${signal.symbol}`,
            message: `${dirText} | 强度: ${signal.strength} | 入场: ${signal.entryType}`,
            type: isLongSignal ? "success" : "danger",
            duration: 5000,
          });
          if (addAiSignalNotification) {
            addAiSignalNotification(signal.symbol, signal.direction, signal.strength);
          }
          // 实时推入 K 线图信号标注缓存 — 按选中机器人过滤
          if (signal.symbol === selectedSymbol.value) {
            const robotMatch = !selectedBotId.value || signal.robotId === selectedBotId.value;
            if (robotMatch) {
              const timeInSeconds = signal.timestamp > 1e12
                ? Math.floor(signal.timestamp / 1000)
                : signal.timestamp;
              const wsSignal: KLineSignal = {
                id: -Date.now(),
                time: timeInSeconds,
                signalType: isLongSignal ? "LONG" : "SHORT",
                price: signal.price || null,
                description: isLongSignal ? "多" : "空",
                signalStrength: signal.strength ?? null,
                signalSource: "WEBSOCKET",
                robotId: signal.robotId || null,
                entryType: signal.entryType || null,
                extraParams: signal.timeframe ? JSON.stringify({ timeframe: signal.timeframe }) : null,
                marketTrend: signal.marketTrend || null,
              };
              const cacheKey = `${wsSignal.id}_${wsSignal.time}`;
              const exists = signalsCache.value.some(
                (s) => `${s.id}_${s.time}` === cacheKey,
              );
              if (!exists) {
                signalsCache.value.push(wsSignal);
                signalsCache.value.sort((a, b) => a.time - b.time);
                updateSignalMarkers();
              }
            }
          }
        } catch (e) {
          console.error("解析AI信号消息失败:", e);
        }
      });
    },
    (error: any) => {
      console.error("AI信号WebSocket连接失败:", error);
    },
  );
}
function disconnectSignalWebSocket() {
  if (signalStompClient && signalStompClient.connected) {
    try {
      signalStompClient.disconnect();
    } catch {}
  }
  signalStompClient = null;
}
watch(
  bottomActiveTab,
  (tab) => {
    if (tab === "trade-logs") {
      connectTradeLogsWebSocket();
    } else {
      disconnectTradeLogsWebSocket();
    }
  },
  { immediate: true },
);
const lastPriceDisplay = computed(() => {
  return lastPrice.value != null ? lastPrice.value.toFixed(4) : "--";
});
const changeDisplay = computed(() => {
  if (lastPrice.value == null || prevPrice.value == null) return "--";
  const change = lastPrice.value - prevPrice.value;
  const sign = change >= 0 ? "+" : "";
  return `${sign}${change.toFixed(4)}`;
});
const changePercentDisplay = computed(() => {
  if (
    lastPrice.value == null ||
    prevPrice.value == null ||
    prevPrice.value === 0
  )
    return "--";
  const p = ((lastPrice.value - prevPrice.value) / prevPrice.value) * 100;
  const sign = p >= 0 ? "+" : "";
  return `${sign}${p.toFixed(2)}%`;
});
/**
 * 显示K线数据（计算属性）
 * 如果有焦点数据（光标下的K线），显示焦点数据；否则显示最新K线数据
 */
const displayKlineData = computed(() => {
  if (focusedKlineData.value) {
    return focusedKlineData.value;
  }
  // 如果没有焦点数据，返回最新K线数据
  if (dataCache.value.length > 0) {
    const latest = dataCache.value[dataCache.value.length - 1];
    return {
      time: latest.time,
      open: latest.open,
      high: latest.high,
      low: latest.low,
      close: latest.close,
    };
  }
  // 如果都没有，返回默认值
  return {
    time: 0,
    open: 0,
    high: 0,
    low: 0,
    close: 0,
  };
});
/**
 * 格式化后的时间字符串
 */
const formattedKlineTime = computed(() => {
  const timestamp = displayKlineData.value.time;
  if (!timestamp) return "-";
  return formatTime(timestamp);
});
const currentPriceValue = computed(() => {
  const price = lastPrice.value ?? displayKlineData.value.close;
  return price && price > 0 ? price : 0;
});
const intervalMsValue = computed(() => getIntervalMs(currentInterval.value));
function getNestedValue(source: any, path: string) {
  return path.split(".").reduce((acc, key) => acc?.[key], source);
}
function getTrendNumber(source: any, keys: string[]): number | null {
  for (const key of keys) {
    const raw = key.includes(".") ? getNestedValue(source, key) : source?.[key];
    const value = Number(raw);
    if (Number.isFinite(value)) return value;
  }
  return null;
}
function normalizeTrendDirection(value: string | null | undefined) {
  const raw = String(value || "").toLowerCase();
  if (
    raw.includes("up") ||
    raw.includes("bull") ||
    raw.includes("rise") ||
    raw.includes("上涨") ||
    raw.includes("上升")
  ) {
    return "up";
  }
  if (
    raw.includes("down") ||
    raw.includes("bear") ||
    raw.includes("fall") ||
    raw.includes("下降") ||
    raw.includes("下跌")
  ) {
    return "down";
  }
  return "sideways";
}
function toSmcInterval(iv: string | null | undefined): string {
  const s = String(iv || "");
  if (s.includes("OKX4HOUR") || s.includes("4H")) return "4h";
  if (s.includes("MIN60") || s.includes("60")) return "1h";
  if (s.includes("DAY") || s.includes("1D")) return "1d";
  if (s.includes("MIN15")) return "15m";
  if (s.includes("MIN5")) return "5m";
  if (s.includes("MIN1")) return "1m";
  return "1h";
}
function anyStructureSignal(x: any): boolean {
  if (!x) return false;
  return !!(
    x.internalBullishBOS ||
    x.internalBearishBOS ||
    x.internalBullishCHOCH ||
    x.internalBearishCHOCH ||
    x.swingBullishBOS ||
    x.swingBearishBOS ||
    x.swingBullishCHOCH ||
    x.swingBearishCHOCH
  );
}
function hasSmcLevel(x: any): boolean {
  if (x == null) return false;
  const n = typeof x === "number" ? x : Number(x);
  return Number.isFinite(n);
}
function smcTrendLabel(bias: any): string {
  const n = typeof bias === "number" ? bias : Number(bias);
  if (n === 1) return "看涨";
  if (n === -1) return "看跌";
  if (n === 0) return "中性";
  return "-";
}
function formatSmcTime(ts: any): string {
  const n = typeof ts === "number" ? ts : Number(ts);
  if (!Number.isFinite(n)) return "-";
  return new Date(n).toLocaleString();
}
function smcBlocks(blocks: any, bias: number): any[] {
  if (!Array.isArray(blocks)) return [];
  return blocks.filter((b) => b && Number(b.bias) === bias);
}
function collectSmcLevels(smc: any): { supports: number[]; resistances: number[] } {
  const supports: number[] = [];
  const resistances: number[] = [];
  if (!smc) return { supports, resistances };
  const orderBlockPriority = 1;
  const equilibriumPriority = 2;
  const strongWeakPriority = 3;
  const periodPriority = 4;
  const seenSupports = new Set<number>();
  const seenResistances = new Set<number>();
  function tryAdd(arr: number[], seen: Set<number>, v: number) {
    if (hasSmcLevel(v) && !seen.has(v)) { seen.add(v); arr.push(v); }
  }
  for (const ob of [...(smc.swingOrderBlocks || []), ...(smc.internalOrderBlocks || [])]) {
    tryAdd(supports, seenSupports, ob.low);
    tryAdd(resistances, seenResistances, ob.high);
  }
  tryAdd(supports, seenSupports, smc.strongLow);
  tryAdd(supports, seenSupports, smc.weakLow);
  tryAdd(supports, seenSupports, smc.trailingLow);
  tryAdd(supports, seenSupports, smc.dailyLow);
  tryAdd(supports, seenSupports, smc.weeklyLow);
  tryAdd(supports, seenSupports, smc.monthlyLow);
  tryAdd(supports, seenSupports, smc.equilibriumZoneBottom);
  tryAdd(supports, seenSupports, smc.discountZoneBottom);
  tryAdd(resistances, seenResistances, smc.strongHigh);
  tryAdd(resistances, seenResistances, smc.weakHigh);
  tryAdd(resistances, seenResistances, smc.trailingHigh);
  tryAdd(resistances, seenResistances, smc.dailyHigh);
  tryAdd(resistances, seenResistances, smc.weeklyHigh);
  tryAdd(resistances, seenResistances, smc.monthlyHigh);
  tryAdd(resistances, seenResistances, smc.equilibriumZoneTop);
  tryAdd(resistances, seenResistances, smc.premiumZoneTop);
  if (hasSmcLevel(smc.equilibriumCenter)) {
    tryAdd(supports, seenSupports, smc.equilibriumCenter);
    tryAdd(resistances, seenResistances, smc.equilibriumCenter);
  }
  return { supports, resistances };
}
function findNearestBelow(prices: number[], entry: number): number | null {
  const below = prices.filter(p => p < entry).sort((a, b) => b - a);
  return below.length ? below[0] : null;
}
function findNearestAbove(prices: number[], entry: number): number | null {
  const above = prices.filter(p => p > entry).sort((a, b) => a - b);
  return above.length ? above[0] : null;
}
async function computeSmcTpSl(symbol: string, entryPrice: number, side: "BUY" | "SELL"): Promise<{ tp: number; sl: number } | null> {
  try {
    const now = Date.now();
    const [resp15m, resp1h] = await Promise.all([
      getSmc({ symbol, interval: "15m", from: now - 7 * 24 * 60 * 60 * 1000, to: now }),
      getSmc({ symbol, interval: "1h", from: now - 30 * 24 * 60 * 60 * 1000, to: now }),
    ]);
    const extractLatest = (resp: any): any => {
      const payload = resp?.data ?? resp;
      const arr = payload?.results || payload?.data?.results || [];
      return Array.isArray(arr) && arr.length ? arr[arr.length - 1] : null;
    };
    const smc15m = extractLatest(resp15m);
    const smc1h = extractLatest(resp1h);
    const levels15m = collectSmcLevels(smc15m);
    const levels1h = collectSmcLevels(smc1h);
    if (side === "BUY") {
      const rawSl = findNearestBelow(levels15m.supports, entryPrice) ?? entryPrice * 0.95;
      const tp = findNearestAbove(levels1h.resistances, entryPrice) ?? entryPrice * 1.10;
      const sl = rawSl * 0.99;
      return { tp: Math.round(tp * 100) / 100, sl: Math.round(sl * 100) / 100 };
    } else {
      const rawSl = findNearestAbove(levels15m.resistances, entryPrice) ?? entryPrice * 1.05;
      const tp = findNearestBelow(levels1h.supports, entryPrice) ?? entryPrice * 0.90;
      const sl = rawSl * 1.01;
      return { tp: Math.round(tp * 100) / 100, sl: Math.round(sl * 100) / 100 };
    }
  } catch (e) {
    console.warn("computeSmcTpSl error:", e);
    return null;
  }
}
async function loadSmc() {
  try {
    if (!selectedSymbol.value) return;
    smcLoading.value = true;
    smcError.value = null;
    const interval = toSmcInterval(currentInterval.value);
    const now = Date.now();
    const from =
      interval === "4h"
        ? now - 90 * 24 * 60 * 60 * 1000
        : interval === "1d"
          ? now - 365 * 24 * 60 * 60 * 1000
          : interval === "1h"
            ? now - 30 * 24 * 60 * 60 * 1000
            : now - 7 * 24 * 60 * 60 * 1000;
    const resp: any = await getSmc({
      symbol: selectedSymbol.value,
      interval,
      from,
      to: now,
    });
    const payload = resp?.data ?? resp;
    smcData.value = payload;
    const arr = payload?.results || payload?.data?.results || [];
    smcLatest.value = Array.isArray(arr) && arr.length ? arr[arr.length - 1] : null;
  } catch (e: any) {
    smcError.value = e?.message || "加载失败";
    smcData.value = null;
    smcLatest.value = null;
  } finally {
    smcLoading.value = false;
  }
}
const trendDirection = computed(() => {
  const data = trendAnalysisData.value || {};
  return normalizeTrendDirection(
    data.trendDirection || data.trendState || data.direction || data.trend,
  );
});
const trendStrengthValue = computed(() => {
  const data = trendAnalysisData.value || {};
  const raw =
    data.trendStrength ??
    data.strength ??
    data.trendStrengthScore ??
    data.strengthScore;
  const numeric = Number(raw);
  if (Number.isFinite(numeric)) {
    if (numeric <= 1) return Math.round(numeric * 100);
    return Math.min(100, Math.max(0, Math.round(numeric)));
  }
  if (typeof raw === "string") {
    const key = raw.toLowerCase();
    if (key === "strong") return 80;
    if (key === "medium") return 60;
    if (key === "weak") return 40;
  }
  return 50;
});
const trendStrengthText = computed(() => {
  const data = trendAnalysisData.value || {};
  const raw = data.trendStrength ?? data.strength ?? "";
  if (typeof raw === "string") {
    const key = raw.toLowerCase();
    if (key === "strong") return "强势";
    if (key === "medium") return "中性";
    if (key === "weak") return "偏弱";
  }
  const value = trendStrengthValue.value;
  if (value >= 75) return "强势";
  if (value >= 55) return "中性";
  return "偏弱";
});
const trendStateDisplay = computed(() => {
  const direction = trendDirection.value;
  if (direction === "up") {
    return { label: "上升趋势", icon: "↑", className: "trend-up" };
  }
  if (direction === "down") {
    return { label: "下降趋势", icon: "↓", className: "trend-down" };
  }
  return { label: "震荡整理", icon: "→", className: "trend-sideways" };
});
const supportSlopeValue = computed(() => {
  return getTrendNumber(trendAnalysisData.value, [
    "supportSlope",
    "support_slope",
    "supportLineSlope",
    "support_line_slope",
    "supportLine.slope",
    "supportLine.k",
  ]);
});
const resistanceSlopeValue = computed(() => {
  return getTrendNumber(trendAnalysisData.value, [
    "resistanceSlope",
    "resistance_slope",
    "resistanceLineSlope",
    "resistance_line_slope",
    "resistanceLine.slope",
    "resistanceLine.k",
  ]);
});
const supportSlopePercent = computed(() => {
  if (!currentPriceValue.value || supportSlopeValue.value == null) return null;
  return (
    ((supportSlopeValue.value * intervalMsValue.value) /
      currentPriceValue.value) *
    100
  );
});
const resistanceSlopePercent = computed(() => {
  if (!currentPriceValue.value || resistanceSlopeValue.value == null)
    return null;
  return (
    ((resistanceSlopeValue.value * intervalMsValue.value) /
      currentPriceValue.value) *
    100
  );
});
function formatSlopePercent(value: number | null) {
  if (value == null || !Number.isFinite(value)) return "--";
  const sign = value >= 0 ? "+" : "";
  return `${sign}${value.toFixed(2)}%`;
}
function getSlopeClass(value: number | null) {
  if (value == null || !Number.isFinite(value)) return "slope-flat";
  if (value > 0) return "slope-up";
  if (value < 0) return "slope-down";
  return "slope-flat";
}
const supportSlopeDisplay = computed(() =>
  formatSlopePercent(supportSlopePercent.value),
);
const resistanceSlopeDisplay = computed(() =>
  formatSlopePercent(resistanceSlopePercent.value),
);
const supportSlopeClass = computed(() =>
  getSlopeClass(supportSlopePercent.value),
);
const resistanceSlopeClass = computed(() =>
  getSlopeClass(resistanceSlopePercent.value),
);
const trendScore = computed(() => {
  const base = trendStrengthValue.value;
  const slopeBonus = Math.min(
    20,
    Math.abs(supportSlopePercent.value || 0) +
      Math.abs(resistanceSlopePercent.value || 0),
  );
  return Math.max(0, Math.min(100, Math.round(base + slopeBonus)));
});
const trendDescription = computed(() => {
  if (!trendAnalysisData.value) return "暂无趋势数据";
  const support = supportSlopePercent.value;
  const resistance = resistanceSlopePercent.value;
  const threshold = 0.01;
  const supportDir =
    support == null
      ? "flat"
      : support > threshold
        ? "up"
        : support < -threshold
          ? "down"
          : "flat";
  const resistanceDir =
    resistance == null
      ? "flat"
      : resistance > threshold
        ? "up"
        : resistance < -threshold
          ? "down"
          : "flat";
  if (supportDir === "up" && resistanceDir === "up") {
    return "支撑线和阻力线同步向上，上升动能强劲，多头主导市场。";
  }
  if (supportDir === "down" && resistanceDir === "down") {
    return "支撑线和阻力线同步向下，下降趋势延续，空头力量较强。";
  }
  if (
    (supportDir === "up" && resistanceDir === "down") ||
    (supportDir === "down" && resistanceDir === "up")
  ) {
    return "支撑线向上，阻力线向下，价格波动收窄，可能面临变盘。";
  }
  if (supportDir === "flat" && resistanceDir === "flat") {
    return "支撑线和阻力线接近水平，市场处于横盘整理阶段，等待方向选择。";
  }
  if (supportDir === "flat" && resistanceDir === "up") {
    return "支撑线趋于水平，阻力线抬升，价格仍在上方推进。";
  }
  if (supportDir === "flat" && resistanceDir === "down") {
    return "支撑线趋于水平，阻力线下移，空头压力仍在。";
  }
  if (supportDir === "up" && resistanceDir === "flat") {
    return "支撑线抬升，阻力线趋于水平，市场稳步上移。";
  }
  if (supportDir === "down" && resistanceDir === "flat") {
    return "支撑线下移，阻力线趋于水平，弱势格局未改。";
  }
  return "趋势结构尚不明确，建议结合关键价格区域观察。";
});
const currentSupport = computed(() => {
  return getTrendNumber(trendAnalysisData.value, [
    "keySupport",
    "support",
    "supportPrice",
    "supportLinePrice",
    "supportLine.price",
    "bb20_lower",
    "bb50_lower",
    "rangeLower",
    "range_lower",
  ]);
});
const currentResistance = computed(() => {
  return getTrendNumber(trendAnalysisData.value, [
    "keyResistance",
    "resistance",
    "resistancePrice",
    "resistanceLinePrice",
    "resistanceLine.price",
    "bb20_upper",
    "bb50_upper",
    "rangeUpper",
    "range_upper",
  ]);
});
const supportDistanceText = computed(() => {
  if (!currentPriceValue.value || currentSupport.value == null) return "--";
  const diff = currentPriceValue.value - currentSupport.value;
  const percent = (diff / currentPriceValue.value) * 100;
  const sign = diff >= 0 ? "+" : "";
  return `${sign}${diff.toFixed(2)} (${sign}${percent.toFixed(2)}%)`;
});
const resistanceDistanceText = computed(() => {
  if (!currentPriceValue.value || currentResistance.value == null) return "--";
  const diff = currentResistance.value - currentPriceValue.value;
  const percent = (diff / currentPriceValue.value) * 100;
  const sign = diff >= 0 ? "+" : "";
  return `${sign}${diff.toFixed(2)} (${sign}${percent.toFixed(2)}%)`;
});
const supportDistanceClass = computed(() => {
  if (!currentPriceValue.value || currentSupport.value == null) return "";
  return currentPriceValue.value >= currentSupport.value
    ? "distance-positive"
    : "distance-negative";
});
const resistanceDistanceClass = computed(() => {
  if (!currentPriceValue.value || currentResistance.value == null) return "";
  return currentResistance.value >= currentPriceValue.value
    ? "distance-negative"
    : "distance-positive";
});
const channelWidthDisplay = computed(() => {
  if (currentSupport.value == null || currentResistance.value == null)
    return "--";
  const width = Math.abs(currentResistance.value - currentSupport.value);
  const mid = (currentResistance.value + currentSupport.value) / 2;
  const percent = mid > 0 ? (width / mid) * 100 : 0;
  return `${width.toFixed(2)} (${percent.toFixed(2)}%)`;
});
const futureProjections = computed(() => {
  const steps = [3, 6, 12];
  const supportBase = currentSupport.value;
  const resistanceBase = currentResistance.value;
  const supportSlope = supportSlopeValue.value ?? 0;
  const resistanceSlope = resistanceSlopeValue.value ?? 0;
  const periodMs = intervalMsValue.value;
  return steps.map((step) => ({
    time: step,
    timeLabel: `${step} 根后`,
    support:
      supportBase == null ? null : supportBase + supportSlope * periodMs * step,
    resistance:
      resistanceBase == null
        ? null
        : resistanceBase + resistanceSlope * periodMs * step,
  }));
});
const visibleProjections = computed(() => {
  return projectionExpanded.value
    ? futureProjections.value
    : futureProjections.value.slice(0, projectionDefaultCount);
});
const miniChartRangeLabel = computed(() => {
  const bars = Math.min(dataCache.value.length, 120);
  if (!bars) return "暂无数据";
  return `最近 ${bars} 根K线`;
});
watch(
  [selectedSymbol, currentInterval, rightMainTab, rightPanelCollapsed],
  () => {
    if (rightMainTab.value === "analysis" && !rightPanelCollapsed.value) {
      loadLogRegChannelIndicator();
    }
  },
  { immediate: true },
);
watch(rightPanelWidth, () => {
  if (!rightPanelCollapsed.value) {
    nextTick(() => {
      trendMiniChart?.resize();
    });
  }
});
/**
 * 计算震幅百分比
 */
function calculateAmplitude(kline: {
  high: number;
  low: number;
  open: number;
}) {
  if (!kline || kline.open === 0) return "0.00";
  const amplitude = ((kline.high - kline.low) / kline.open) * 100;
  return amplitude.toFixed(2);
}
/**
 * 格式化价格
 */
function formatPrice(price: number | null | undefined): string {
  if (price == null || isNaN(price)) return "-";
  return price.toFixed(2);
}
function klinePriceColor(kline: { open: number; close: number }): string {
  return kline.close >= kline.open ? "color-up" : "color-down";
}
async function copyPriceValue(value: number | null) {
  if (value == null || !Number.isFinite(value)) return;
  try {
    await navigator.clipboard.writeText(value.toFixed(2));
    ElMessage.success("价格已复制");
  } catch (error) {
    ElMessage.error("复制失败");
  }
}
function buildMiniChartSeries() {
  const bars = dataCache.value.slice(-120);
  if (!bars.length) {
    return { supportSeries: [], resistanceSeries: [] };
  }
  const lastTimeMs = bars[bars.length - 1].time * 1000;
  let supportBase = currentSupport.value;
  let resistanceBase = currentResistance.value;
  if (supportBase == null) {
    supportBase = Math.min(...bars.map((b) => b.low));
  }
  if (resistanceBase == null) {
    resistanceBase = Math.max(...bars.map((b) => b.high));
  }
  const supportSlope = supportSlopeValue.value ?? 0;
  const resistanceSlope = resistanceSlopeValue.value ?? 0;
  const supportSeries = bars.map((bar) => {
    const timeMs = bar.time * 1000;
    const value =
      supportBase == null
        ? null
        : supportBase + supportSlope * (timeMs - lastTimeMs);
    return [timeMs, value];
  });
  const resistanceSeries = bars.map((bar) => {
    const timeMs = bar.time * 1000;
    const value =
      resistanceBase == null
        ? null
        : resistanceBase + resistanceSlope * (timeMs - lastTimeMs);
    return [timeMs, value];
  });
  return { supportSeries, resistanceSeries };
}
function initTrendMiniChart() {
  if (!trendMiniChartRef.value) return;
  if (trendMiniChart) return;
  trendMiniChart = echarts.init(trendMiniChartRef.value);
}
function updateTrendMiniChart() {
  if (!trendMiniChartRef.value) return;
  initTrendMiniChart();
  if (!trendMiniChart) return;
  const { supportSeries, resistanceSeries } = buildMiniChartSeries();
  const values = [...supportSeries, ...resistanceSeries]
    .map((item) => item[1])
    .filter(
      (value): value is number => value != null && Number.isFinite(value),
    );
  const rawMin = values.length ? Math.min(...values) : null;
  const rawMax = values.length ? Math.max(...values) : null;
  const range = rawMin != null && rawMax != null ? rawMax - rawMin : 0;
  const padding =
    range > 0
      ? range * 0.35
      : rawMin != null
        ? Math.max(1, Math.abs(rawMin) * 0.02)
        : 1;
  const yMin = rawMin == null ? null : rawMin - padding;
  const yMax = rawMax == null ? null : rawMax + padding;
  const series: echarts.SeriesOption[] = [];
  if (trendMiniMode.value === "support" || trendMiniMode.value === "both") {
    series.push({
      name: "支撑",
      type: "line",
      smooth: true,
      showSymbol: false,
      data: supportSeries,
      lineStyle: { color: "#13c2c2", width: 2 },
    });
  }
  if (trendMiniMode.value === "resistance" || trendMiniMode.value === "both") {
    series.push({
      name: "阻力",
      type: "line",
      smooth: true,
      showSymbol: false,
      data: resistanceSeries,
      lineStyle: { color: "#fa5252", width: 2 },
    });
  }
  const option: echarts.EChartsOption = {
    backgroundColor: "transparent",
    grid: { left: 6, right: 10, top: 10, bottom: 18 },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "line" },
      formatter: (params: any) => {
        if (!params || !params.length) return "";
        const time = new Date(params[0].value[0]).toLocaleString("zh-CN");
        const lines = params.map(
          (item: any) => `${item.seriesName}: ${formatPrice(item.value[1])}`,
        );
        return `${time}<br/>${lines.join("<br/>")}`;
      },
    },
    xAxis: {
      type: "time",
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
    },
    yAxis: {
      type: "value",
      min: yMin ?? undefined,
      max: yMax ?? undefined,
      axisLabel: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { show: false },
    },
    series,
  };
  trendMiniChart.setOption(option);
  trendMiniChart.resize();
}
async function loadLogRegChannelIndicator() {
  logRegChannelLoading.value = true;
  logRegChannelError.value = null;
  try {
    const params = {
      symbol: selectedSymbol.value,
      interval: convertIntervalToBackend(currentInterval.value),
      length: 200,
      multiplier: 2.0,
      points: 120,
    };
    const response: any = await getLogRegChannelIndicator(params);
    const payload = response?.data ?? response;
    const data = payload?.data ?? payload?.result ?? payload;
    logRegChannelData.value = data || null;
  } catch (error) {
    logRegChannelError.value = "获取LogReg通道失败";
    logRegChannelData.value = null;
  } finally {
    logRegChannelLoading.value = false;
  }
}
async function loadTrendAnalysis() {
  trendAnalysisLoading.value = true;
  trendAnalysisError.value = null;
  try {
    const params = {
      symbol: selectedSymbol.value,
      interval: convertIntervalToBackend(currentInterval.value),
      limit: 500,
    };
    const response: any = await getTrendAnalysis(params);
    const payload = response?.data ?? response;
    const data = payload?.data ?? payload?.result ?? payload;
    trendAnalysisData.value = data || null;
  } catch (error) {
    trendAnalysisError.value = "获取趋势分析失败";
    trendAnalysisData.value = null;
  } finally {
    trendAnalysisLoading.value = false;
    nextTick(() => updateTrendMiniChart());
  }
}
function formatNumber(v: any, digits: number) {
  const n = Number(v);
  if (!Number.isFinite(n)) return "-";
  return n.toFixed(digits);
}
function formatPercent(v: any) {
  const n = Number(v);
  if (!Number.isFinite(n)) return "-";
  return `${n.toFixed(2)}%`;
}
async function loadElliottAnalysis() {
  if (!selectedSymbol.value) return;
  elliottAnalysisLoading.value = true;
  elliottAnalysisError.value = null;
  try {
    const params = {
      symbol: selectedSymbol.value,
      interval: convertIntervalToBackend(currentInterval.value),
      limit: 500,
    };
    const response: any = await getElliottWaveAnalysis(params);
    const payload = response?.data ?? response;
    const data = payload?.data ?? payload?.result ?? payload;
    elliottAnalysisData.value = data || null;
  } catch (error) {
    elliottAnalysisError.value = "获取艾略特波浪分析失败";
    elliottAnalysisData.value = null;
  } finally {
    elliottAnalysisLoading.value = false;
  }
}
/** 格式化成本（手续费等） */
function formatCharge(charge: number | string | null | undefined): string {
  if (charge == null || charge === "") return "-";
  const n = typeof charge === "number" ? charge : Number(charge);
  if (isNaN(n)) return "-";
  return n.toFixed(2);
}
function getCommissionRateForOrder(order: any): number {
  const raw =
    order?.commissionRate ??
    order?.commissionPercent ??
    backtestParams.value.commission ??
    0.045;
  const n = Number(raw);
  return Number.isFinite(n) ? n : 0;
}
function computeOrderCharge(order: any): number | null {
  if (!order) return null;
  const openPrice = Number(order.buyPrice ?? order.openPrice);
  const closePrice = Number(order.sellPrice);
  const amount = Number(order.amount ?? order.quantity ?? order.volume);
  const multiplier = getOrderMultiplier(order);
  const explicit = Number(order.charge);
  const hasExplicit = Number.isFinite(explicit) && explicit >= 0;
  if (
    !Number.isFinite(openPrice) ||
    !Number.isFinite(closePrice) ||
    !Number.isFinite(amount)
  ) {
    return hasExplicit ? explicit : null;
  }
  const rate = getCommissionRateForOrder(order);
  if (!Number.isFinite(rate) || rate <= 0) {
    return hasExplicit ? explicit : null;
  }
  const notionalOpen = openPrice * amount * multiplier;
  const notionalClose = closePrice * amount * multiplier;
  return (notionalOpen + notionalClose) * rate;
}
function formatIncome(income: number | string | null | undefined): {
  text: string;
  value: number;
} {
  if (income == null || income === "") {
    return { text: "-", value: 0 };
  }
  const n = typeof income === "number" ? income : Number(income);
  if (isNaN(n)) {
    return { text: "-", value: 0 };
  }
  const sign = n >= 0 ? "+" : "";
  return { text: `${sign}${n.toFixed(2)}`, value: n };
}
function formatCloseReason(reason?: string | null) {
  if (!reason) return "-";
  const text = String(reason);
  if (/[\u4e00-\u9fa5]/.test(text)) return text;
  const normalized = text.toUpperCase();
  const reasonMap: Record<string, string> = {
    SIGNAL_REVERSAL: "反转信号",
    STOP_LOSS: "固定止损",
    TAKE_PROFIT: "止盈",
    TECHNICAL_INDICATOR: "技术指标触发",
    TIME_LIMIT: "时间限制",
    MANUAL: "手动平仓",
    UNKNOWN: "未知原因",
    MANUAL_CLOSE: "手动平仓",
    AUTO_CLOSE: "自动平仓",
    CLOSE_LONG: "平多",
    CLOSE_SHORT: "平空",
    BUY_GAIN: "多止盈",
    BUY_LOSS: "多止损",
    SELL_GAIN: "空止盈",
    SELL_LOSS: "空止损",
    BATCH_TAKE_PROFIT: "分批止盈",
    BATCH_STOP_LOSS: "分批止损",
    BATCH_TRAILING_GAIN: "分批移动止盈",
    BATCH_TRAILING_LOSS: "分批移动止损",
    TAKE_PROFIT_FIXED: "固定百分比止盈",
    FIXED_TAKE_PROFIT: "固定百分比止盈",
    FIXED_PERCENT_TAKE_PROFIT: "固定百分比止盈",
    TAKE_PROFIT_ATR: "ATR 止盈",
    ATR_TAKE_PROFIT: "ATR 止盈",
    ATR_BASED_TAKE_PROFIT: "ATR 止盈",
    TRAILING_TAKE_PROFIT: "移动止盈",
    MANUAL_TAKE_PROFIT: "手动止盈",
    AUTO_TAKE_PROFIT: "自动止盈",
    MACD_GOLDEN_CROSS: "MACD金叉",
    MACD_DEAD_CROSS: "MACD死叉",
  };
  return reasonMap[normalized] || text;
}
function calcDurationHours(openTime: string | number | null | undefined, closeTime: string | number | null | undefined): string {
  if (!openTime || !closeTime) return "-";
  const toMs = (t: string | number): number => {
    if (typeof t === "string") {
      if (/^\d+$/.test(t)) {
        const n = parseInt(t);
        return n > 10000000000 ? n : n * 1000;
      }
      const d = new Date(t.includes("T") ? t : t.replace(" ", "T"));
      return isNaN(d.getTime()) ? 0 : d.getTime();
    }
    return t > 10000000000 ? t : t * 1000;
  };
  const diffMs = toMs(closeTime) - toMs(openTime);
  if (diffMs <= 0) return "-";
  const hours = diffMs / (1000 * 60 * 60);
  return hours >= 100 ? `${hours.toFixed(0)}h` : `${hours.toFixed(1)}h`;
}
function isOver24h(openTime: string | number | null | undefined, closeTime: string | number | null | undefined): boolean {
  if (!openTime || !closeTime) return false;
  const toMs = (t: string | number): number => {
    if (typeof t === "string") {
      if (/^\d+$/.test(t)) {
        const n = parseInt(t);
        return n > 10000000000 ? n : n * 1000;
      }
      const d = new Date(t.includes("T") ? t : t.replace(" ", "T"));
      return isNaN(d.getTime()) ? 0 : d.getTime();
    }
    return t > 10000000000 ? t : t * 1000;
  };
  return toMs(closeTime) - toMs(openTime) > 24 * 60 * 60 * 1000;
}
function sortByDuration(a: Record<string, any>, b: Record<string, any>): number {
  const ms = (row: Record<string, any>): number => {
    if (!row.orderTime || !row.sellTime) return 0;
    const toMs = (t: string | number): number => {
      if (typeof t === "string") {
        if (/^\d+$/.test(t)) {
          const n = parseInt(t);
          return n > 10000000000 ? n : n * 1000;
        }
        const d = new Date(t.includes("T") ? t : t.replace(" ", "T"));
        return isNaN(d.getTime()) ? 0 : d.getTime();
      }
      return t > 10000000000 ? t : t * 1000;
    };
    return toMs(row.sellTime) - toMs(row.orderTime);
  };
  return ms(a) - ms(b);
}
function mapOrderStatusLabel(status?: string | null): string {
  if (!status) return "-";
  const s = String(status).toUpperCase();
  if (s === "GAIN") return "止盈";
  if (s === "LOSS") return "止损";
  if (s === "DEAL") return "持仓中";
  if (s === "OPEN") return "挂单";
  if (s === "PENDING") return "待成交";
  if (s === "CLOSING") return "已关闭";
  return status;
}
function orderStatusClass(status?: string | null): string {
  if (!status) return "status-neutral";
  const s = String(status).toUpperCase();
  if (s === "GAIN") return "status-gain";
  if (s === "LOSS") return "status-loss";
  return "status-neutral";
}
const symbolMultiplierMap: Record<string, number> = {
  "BTC-USDT-SWAP": 0.01,
  "ETH-USDT-SWAP": 0.1,
};
function getOrderMultiplier(order: any): number {
  const raw =
    order?.contractMultiplier ??
    order?.contractSize ??
    order?.faceValue ??
    order?.contractValue ??
    order?.multiplier ??
    order?.ctVal ??
    order?.ctValue;
  const n = Number(raw);
  if (Number.isFinite(n) && n > 0) return n;
  const symbol = String(order?.symbol || "").toUpperCase();
  if (symbol && symbolMultiplierMap[symbol]) return symbolMultiplierMap[symbol];
  return 1;
}
function calculateOrderPnl(order: any): number | null {
  if (!order) return null;
  const openPrice = Number(order.buyPrice ?? order.openPrice);
  const closePrice = Number(order.sellPrice);
  const amount = Number(order.amount ?? order.quantity ?? order.volume);
  if (
    !Number.isFinite(openPrice) ||
    !Number.isFinite(closePrice) ||
    !Number.isFinite(amount)
  ) {
    return null;
  }
  const multiplier = getOrderMultiplier(order);
  const side = String(order.orderSide || order.side || "").toUpperCase();
  const diff =
    side === "SELL" || side === "SHORT"
      ? openPrice - closePrice
      : closePrice - openPrice;
  const derivedCharge = computeOrderCharge(order);
  const charge = Number.isFinite(derivedCharge as number)
    ? (derivedCharge as number)
    : Number(order.charge ?? 0);
  const pnl =
    diff * amount * multiplier - (Number.isFinite(charge) ? charge : 0);
  return pnl;
}
function getOrderDisplayPnl(order: any): number | null {
  if (!order) return null;
  // DEAL(持仓中)的订单先尝试未实现盈亏，失败则用已实现盈亏
  const status = String(order?.status || "").toUpperCase();
  if (status === "DEAL") {
    const upnl = getPositionUnrealizedPnl(order);
    if (upnl !== null) return upnl;
    // fallback: income - charge
    const gross = toNumber(order.income);
    const fee = toNumber(order.charge);
    if (Number.isFinite(gross) && Number.isFinite(fee)) {
      return gross - fee;
    }
    return null;
  }
  const gross = toNumber(order.income);
  const fee = toNumber(order.charge);
  if (Number.isFinite(gross) && Number.isFinite(fee)) {
    return gross - fee;
  }
  return null;
}
function getOrderDisplayProfitPercent(order: any): number | null {
  const status = String(order?.status || "").toUpperCase();
  if (status === "DEAL") {
    const uPct = getPositionUnrealizedPercent(order);
    if (uPct !== null) return uPct;
    // fallback: order.profitPercent
    const pct = Number(order?.profitPercent);
    return Number.isFinite(pct) ? pct : null;
  }
  const pct = Number(order?.profitPercent);
  return Number.isFinite(pct) ? pct : null;
}
/** 获取盈亏列显示文本 */
function getPnlText(row: any): string {
  if (!row._type) {
    const pnl = getOrderDisplayPnl(row);
    const val = pnl ?? 0;
    return (val >= 0 ? '+' : '') + val.toFixed(2);
  }
  return '—';
}
/** 获取盈亏列CSS类名 */
function getPnlClass(row: any): string {
  if (!row._type) {
    const pnl = getOrderDisplayPnl(row);
    return (pnl ?? 0) >= 0 ? 'profit-amount positive' : 'profit-amount negative';
  }
  return 'profit-amount neutral';
}
/** 获取收益率列显示文本 */
function getPctText(row: any): string {
  if (!row._type) {
    const pct = getOrderDisplayProfitPercent(row);
    if (pct !== null) {
      return (pct >= 0 ? '+' : '') + pct.toFixed(2) + '%';
    }
    return '—';
  }
  if (row._type === 'item') {
    const pct = row.profitPercent;
    if (pct != null) {
      return (Number(pct) >= 0 ? '+' : '') + Number(pct).toFixed(2) + '%';
    }
    return '—';
  }
  return '—';
}
/** 获取收益率列CSS类名 */
function getPctClass(row: any): string {
  if (!row._type) {
    const pct = getOrderDisplayProfitPercent(row);
    const val = pct ?? 0;
    return val >= 0 ? 'profit-percent positive' : 'profit-percent negative';
  }
  if (row._type === 'item') {
    const pct = row.profitPercent;
    const val = Number(pct ?? 0);
    return val >= 0 ? 'profit-percent positive' : 'profit-percent negative';
  }
  return 'profit-percent neutral';
}
function getOrderDisplayAvgPrice(order: any): string {
  if (!order) return "-";
  const candidates = [
    order.buyAvgPrice,
    order.averagePrice,
    order.avgPrice,
    order.avgOpenPrice,
  ];
  for (const c of candidates) {
    const n = Number(c);
    if (Number.isFinite(n) && n > 0) {
      return formatPrice(n);
    }
  }
  return "-";
}
function getOrderAvgOpenPriceNumber(order: any): number | null {
  if (!order) return null;
  const candidates = [
    order.buyAvgPrice,
    order.averagePrice,
    order.avgPrice,
    order.avgOpenPrice,
    order.buyPrice,
    order.openPrice,
  ];
  for (const c of candidates) {
    const n = Number(c);
    if (Number.isFinite(n) && n > 0) return n;
  }
  return null;
}
function getPositionUnrealizedPnl(order: any): number | null {
  const raw =
    order?.upl ??
    order?.unrealizedPnl ??
    order?.unrealizedPnL ??
    order?.unrealized ??
    order?.pnl;
  const rawNumber = Number(raw);
  if (Number.isFinite(rawNumber)) return rawNumber;
  const markPrice = Number(currentPriceValue.value);
  if (!Number.isFinite(markPrice) || markPrice <= 0) return null;
  if (!order) return null;
  const entryPrice = getOrderAvgOpenPriceNumber(order);
  const amount = Number(order.amount ?? order.quantity ?? order.volume);
  if (!Number.isFinite(entryPrice as number) || !Number.isFinite(amount)) {
    return null;
  }
  const multiplier = getOrderMultiplier(order);
  const side = String(order.orderSide || order.side || "").toUpperCase();
  const diff =
    side === "SELL" || side === "SHORT"
      ? (entryPrice as number) - markPrice
      : markPrice - (entryPrice as number);
  return diff * amount * multiplier;
}
function getPositionUnrealizedPercent(order: any): number | null {
  const ratio =
    order?.uplRatio ??
    order?.unrealizedPnlRatio ??
    order?.unrealizedPnLRatio ??
    order?.pnlRatio;
  const ratioNumber = Number(ratio);
  if (Number.isFinite(ratioNumber)) {
    return ratioNumber > 1 ? ratioNumber : ratioNumber * 100;
  }
  const markPrice = Number(currentPriceValue.value);
  if (!Number.isFinite(markPrice) || markPrice <= 0) return null;
  if (!order) return null;
  const entryPrice = getOrderAvgOpenPriceNumber(order);
  const amount = Number(order.amount ?? order.quantity ?? order.volume);
  if (!Number.isFinite(entryPrice as number) || !Number.isFinite(amount)) {
    return null;
  }
  const multiplier = getOrderMultiplier(order);
  const cost = (entryPrice as number) * amount * multiplier;
  if (!Number.isFinite(cost) || cost === 0) return null;
  const pnl = getPositionUnrealizedPnl(order);
  if (!Number.isFinite(pnl as number)) return null;
  return ((pnl as number) / cost) * 100;
}
function getOrderLeverageNumber(order: any): number | null {
  if (!order) return null;
  const raw = order.leverRate ?? order.leverage ?? order.leverageRate;
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : null;
}
function getPositionMarketValue(order: any): number | null {
  const markPrice = Number(currentPriceValue.value);
  if (!Number.isFinite(markPrice) || markPrice <= 0) return null;
  if (!order) return null;
  const amount = Number(order.amount ?? order.quantity ?? order.volume);
  if (!Number.isFinite(amount)) return null;
  const multiplier = getOrderMultiplier(order);
  const value = markPrice * amount * multiplier;
  return Number.isFinite(value) && value > 0 ? value : null;
}
function formatLeverage(order: any): string {
  const leverage = getOrderLeverageNumber(order);
  return leverage ? `${leverage}x` : "-";
}
function formatAmount(value: any): string {
  const n = Number(value);
  if (!Number.isFinite(n)) return "-";
  if (Math.abs(n) >= 1) return n.toFixed(4).replace(/\.?0+$/, "");
  return n.toFixed(8).replace(/\.?0+$/, "");
}
function formatOpenTime(order: any): string {
  if (!order) return "-";
  const value = order.openTime ?? order.buyTime ?? order.orderTime ?? order.createTime;
  if (!value) return "-";
  return formatTime(value);
}
/**
 * 处理十字光标移动事件
 * 按官方文档推荐做法：用 param.time + 本地 dataCache 查找当前 K 线
 * 参考文档: https://tradingview.github.io/lightweight-charts/docs/5.0
 */
function handleCrosshairMove(param: any) {
  if (!param || !dataCache.value.length) {
    hideSignalTooltip();
    return;
  }
  // 鼠标在空白区域，param.time 可能为 undefined，此时保持显示最新一根
  if (param.time == null) {
    hideSignalTooltip();
    return;
  }
  // 我们的 K 线使用的是秒级 UTCTimestamp，param.time 也是同类型
  const crosshairTime =
    typeof param.time === "number"
      ? param.time
      : ((param.time as any).timestamp ?? 0);
  // 在本地缓存中查找对应时间的 K 线
  let bar = dataCache.value.find((b) => b.time === crosshairTime);
  // 如果找不到，尝试找时间最接近的一根（防止存在 1~2 秒误差）
  if (!bar) {
    bar = dataCache.value.reduce(
      (closest, current) => {
        if (!closest) return current;
        const d1 = Math.abs(current.time - crosshairTime);
        const d2 = Math.abs(closest.time - crosshairTime);
        return d1 < d2 ? current : closest;
      },
      dataCache.value[0] as KLineDto | null,
    );
  }
  if (!bar) return;
  updateSignalTooltip(param, bar, crosshairTime);
  // 如果数据没有变化，就不更新，避免多余渲染
  if (
    focusedKlineData.value &&
    focusedKlineData.value.time === bar.time &&
    focusedKlineData.value.close === bar.close
  ) {
    return;
  }
  focusedKlineData.value = {
    time: bar.time,
    open: bar.open,
    high: bar.high,
    low: bar.low,
    close: bar.close,
  };
}
function hideSignalTooltip() {
  if (!signalTooltip.value.visible) return;
  signalTooltip.value.visible = false;
  signalTooltip.value.title = "";
  signalTooltip.value.rows = [];
}
function buildSignalTooltipCopyText(): string {
  const t = signalTooltip.value;
  if (!t.visible) return "";
  const lines: string[] = [];
  const title = String(t.title || "").trim();
  if (title) lines.push(title);
  for (const row of t.rows || []) {
    if (row.kind === "section") {
      const label = String(row.label || "").trim();
      if (label) lines.push(label);
      continue;
    }
    const label = String(row.label || "").trim();
    const value = String(row.value || "").trim();
    if (!label && !value) continue;
    lines.push(`${label}: ${value}`);
  }
  return lines.join("\n").trim();
}
async function copyToClipboard(text: string) {
  const raw = String(text || "");
  const value = raw.trim();
  if (!value) {
    ElMessage.warning("没有可复制的内容");
    return;
  }
  try {
    if (navigator.clipboard && typeof navigator.clipboard.writeText === "function") {
      await navigator.clipboard.writeText(value);
      ElMessage.success("已复制");
      return;
    }
  } catch {}
  const textarea = document.createElement("textarea");
  textarea.value = value;
  textarea.setAttribute("readonly", "true");
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  textarea.style.left = "-9999px";
  textarea.style.top = "-9999px";
  document.body.appendChild(textarea);
  textarea.select();
  textarea.setSelectionRange(0, textarea.value.length);
  let ok = false;
  try {
    ok = document.execCommand("copy");
  } catch {
    ok = false;
  } finally {
    document.body.removeChild(textarea);
  }
  if (ok) {
    ElMessage.success("已复制");
  } else {
    ElMessage.error("复制失败");
  }
}
async function handleCopySignalTooltip() {
  closeContextMenu();
  await copyToClipboard(buildSignalTooltipCopyText());
}
function clamp(n: number, min: number, max: number): number {
  if (n < min) return min;
  if (n > max) return max;
  return n;
}
function formatSignalTypeLabel(code: string): string {
  const c = String(code || "").toUpperCase();
  if (c === "LONG") return "多头开仓";
  if (c === "SHORT") return "空头开仓";
  if (c === "CLOSE_LONG") return "多头平仓";
  if (c === "CLOSE_SHORT") return "空头平仓";
  if (c === "LBAP") return "加多仓";
  if (c === "SBAP") return "加空仓";
  if (c === "LBSP") return "减多仓";
  if (c === "SBSP") return "减空仓";
  return c || "-";
}
function formatSignalDescriptionWeightOnly(description: string): string {
  const text = String(description ?? "");
  const match = text.match(
    /^((?:LB|SB)(?:AP|SP)?|LS|SS)\(\s*([-+]?\d*\.?\d+)(?:\s*,\s*[-+]?\d*\.?\d+)?\s*\)(.*)$/i,
  );
  if (!match) return text;
  const type = String(match[1] || "").toUpperCase();
  const weight = String(match[2] ?? "").trim();
  const tail = String(match[3] ?? "");
  if (!weight) return text;
  return `${type}(${weight})${tail}`;
}
function getMarkerCoordinate(
  marker: any,
  bar: KLineDto,
): { x: number; y: number } | null {
  if (!chart.value || !candleSeries.value) return null;
  const timeScale = chart.value.timeScale();
  const x = timeScale.timeToCoordinate(marker.time as any);
  if (x == null) return null;
  let basePrice = bar.close;
  const position = String(marker.position || "");
  if (position === "aboveBar") {
    basePrice = bar.high;
  } else if (position === "belowBar") {
    basePrice = bar.low;
  }
  const y0 = candleSeries.value.priceToCoordinate(basePrice);
  if (y0 == null) return null;
  let y = y0;
  if (position === "aboveBar") y = y0 - 18;
  if (position === "belowBar") y = y0 + 18;
  return { x, y };
}
function pickHoveredMarker(
  point: { x: number; y: number },
  markers: any[],
  bar: KLineDto,
): any | null {
  let best: any | null = null;
  let bestDist = Number.POSITIVE_INFINITY;
  for (const marker of markers) {
    const coord = getMarkerCoordinate(marker, bar);
    if (!coord) continue;
    const dx = point.x - coord.x;
    const dy = point.y - coord.y;
    const dist = Math.sqrt(dx * dx + dy * dy);
    if (dist < bestDist) {
      best = marker;
      bestDist = dist;
    }
  }
  return bestDist <= 18 ? best : null;
}
let smcPrefetchTimer: number | null = null;
function ensureSmcLoaded() {
  if (smcData.value || smcLoading.value) return;
  if (smcPrefetchTimer) return;
  smcPrefetchTimer = window.setTimeout(() => {
    smcPrefetchTimer = null;
    void loadSmc();
  }, 200);
}
function getSmcIntervalMs(interval: string): number {
  const s = String(interval || "").toLowerCase();
  if (s === "4h") return 4 * 60 * 60 * 1000;
  if (s === "1d") return 24 * 60 * 60 * 1000;
  if (s === "1h") return 60 * 60 * 1000;
  if (s === "15m") return 15 * 60 * 1000;
  if (s === "5m") return 5 * 60 * 1000;
  if (s === "1m") return 60 * 1000;
  return 60 * 60 * 1000;
}
function getSmcResultNearTime(timeSec: number): any | null {
  const payload = smcData.value;
  const arr = payload?.results || payload?.data?.results || [];
  if (!Array.isArray(arr) || arr.length === 0) return null;
  const targetMs = timeSec * 1000;
  let lo = 0;
  let hi = arr.length - 1;
  while (lo < hi) {
    const mid = Math.floor((lo + hi) / 2);
    const t = Number(arr[mid]?.timestamp);
    if (!Number.isFinite(t)) {
      lo = mid + 1;
      continue;
    }
    if (t < targetMs) {
      lo = mid + 1;
    } else {
      hi = mid;
    }
  }
  const candidates: any[] = [];
  if (lo >= 0 && lo < arr.length) candidates.push(arr[lo]);
  if (lo - 1 >= 0 && lo - 1 < arr.length) candidates.push(arr[lo - 1]);
  if (lo + 1 >= 0 && lo + 1 < arr.length) candidates.push(arr[lo + 1]);
  let best: any | null = null;
  let bestAbs = Number.POSITIVE_INFINITY;
  for (const it of candidates) {
    const t = Number(it?.timestamp);
    if (!Number.isFinite(t)) continue;
    const d = Math.abs(t - targetMs);
    if (d < bestAbs) {
      best = it;
      bestAbs = d;
    }
  }
  if (!best) return null;
  const smcInterval = toSmcInterval(currentInterval.value);
  const tol = Math.max(2 * 60 * 1000, getSmcIntervalMs(smcInterval) * 0.8);
  return bestAbs <= tol ? best : null;
}
function buildSmcStructureText(x: any): string {
  if (!x) return "-";
  const parts: string[] = [];
  if (x.internalBullishBOS) parts.push("内部BOS↑");
  if (x.internalBearishBOS) parts.push("内部BOS↓");
  if (x.internalBullishCHOCH) parts.push("内部CHOCH↑");
  if (x.internalBearishCHOCH) parts.push("内部CHOCH↓");
  if (x.swingBullishBOS) parts.push("摆动BOS↑");
  if (x.swingBearishBOS) parts.push("摆动BOS↓");
  if (x.swingBullishCHOCH) parts.push("摆动CHOCH↑");
  if (x.swingBearishCHOCH) parts.push("摆动CHOCH↓");
  return parts.length ? parts.join(" ") : "-";
}
function buildSmcObText(blocks: any, bias: number): string {
  const list = smcBlocks(blocks, bias).slice(0, 2);
  if (!list.length) return "-";
  return list
    .map((ob) => `${formatPrice(ob.low)}~${formatPrice(ob.high)}`)
    .join(", ");
}
function safeParseJsonObject(value: any): any | null {
  if (!value) return null;
  if (typeof value === "object") return value;
  if (typeof value !== "string") return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}
function normalizeSignalSourceText(source: any): string | null {
  if (source == null) return null;
  const raw = String(source).trim();
  if (!raw) return null;
  const cleaned = raw.replace(/w位置/gi, "").trim();
  return cleaned ? cleaned : null;
}
function getSignalSmcFromExtraParams(extraParams: any): { smc15m: any | null; smc1h: any | null; smc4h: any | null } {
  const obj = safeParseJsonObject(extraParams);
  const smcRaw = obj?.smc;
  if (!smcRaw || typeof smcRaw !== "object") return { smc15m: null, smc1h: null, smc4h: null };
  const smcMap: Record<string, any> = {};
  for (const [k, v] of Object.entries(smcRaw)) {
    smcMap[String(k).toLowerCase()] = v;
  }
  const smc15m = smcMap["15m"] ?? null;
  const smc1h = smcMap["1h"] ?? null;
  const smc4h = smcMap["4h"] ?? smcMap["240m"] ?? null;
  return { smc15m, smc1h, smc4h };
}
function pushTooltipSection(rows: SignalTooltipRow[], title: string) {
  rows.push({ key: `${rows.length}_section_${title}`, kind: "section", label: title });
}
function pushTooltipItem(rows: SignalTooltipRow[], label: string, value: string) {
  rows.push({ key: `${rows.length}_item_${label}`, kind: "item", label, value });
}
function getSmcFromMarkers(markers: any[]): { smc15m: any | null; smc1h: any | null; smc4h: any | null } {
  for (const m of markers || []) {
    const meta = (m as any)?.meta;
    const extraParams = meta?.extraParams;
    const { smc15m, smc1h, smc4h } = getSignalSmcFromExtraParams(extraParams);
    if (smc15m || smc1h || smc4h) {
      return { smc15m, smc1h, smc4h };
    }
  }
  return { smc15m: null, smc1h: null, smc4h: null };
}
function appendSmcTooltipRows(rows: SignalTooltipRow[], smc: any) {
  if (!smc) return;
  pushTooltipItem(rows, "时间", formatSmcTime(smc.timestamp));
  pushTooltipItem(rows, "结构", buildSmcStructureText(smc));
  pushTooltipItem(rows, "内部趋势", smcTrendLabel(smc.internalTrend));
  pushTooltipItem(rows, "摆动趋势", smcTrendLabel(smc.swingTrend));
  if (hasSmcLevel(smc.equilibriumZoneBottom) && hasSmcLevel(smc.equilibriumZoneTop)) {
    const center = hasSmcLevel(smc.equilibriumCenter)
      ? `(${formatPrice(smc.equilibriumCenter)})`
      : "";
    pushTooltipItem(
      rows,
      "均衡区",
      `${formatPrice(smc.equilibriumZoneBottom)}~${formatPrice(smc.equilibriumZoneTop)}${center}`,
    );
  }
  pushTooltipItem(rows, "区域", String(smc.currentZone ?? "-"));
  if (hasSmcLevel(smc.premiumZoneBottom) && hasSmcLevel(smc.premiumZoneTop)) {
    pushTooltipItem(
      rows,
      "溢价区",
      `${formatPrice(smc.premiumZoneBottom)}~${formatPrice(smc.premiumZoneTop)}`,
    );
  }
  if (hasSmcLevel(smc.discountZoneBottom) && hasSmcLevel(smc.discountZoneTop)) {
    pushTooltipItem(
      rows,
      "折扣区",
      `${formatPrice(smc.discountZoneBottom)}~${formatPrice(smc.discountZoneTop)}`,
    );
  }
  const swingSupply = buildSmcObText(smc.swingOrderBlocks, -1);
  const swingDemand = buildSmcObText(smc.swingOrderBlocks, 1);
  if (swingSupply !== "-") pushTooltipItem(rows, "摆动供给OB", swingSupply);
  if (swingDemand !== "-") pushTooltipItem(rows, "摆动需求OB", swingDemand);
  const internalSupply = buildSmcObText(smc.internalOrderBlocks, -1);
  const internalDemand = buildSmcObText(smc.internalOrderBlocks, 1);
  if (internalSupply !== "-") pushTooltipItem(rows, "内部供给OB", internalSupply);
  if (internalDemand !== "-") pushTooltipItem(rows, "内部需求OB", internalDemand);
  if (smc.equalHighs || smc.equalLows) {
    pushTooltipItem(
      rows,
      "EQH/EQL",
      `${smc.equalHighs ? "EQH" : ""}${smc.equalHighs && smc.equalLows ? " " : ""}${smc.equalLows ? "EQL" : ""}`,
    );
  }
  const fvgBull =
    hasSmcLevel(smc.lastBullishFVGTop) && hasSmcLevel(smc.lastBullishFVGBottom)
      ? `看涨 ${formatPrice(smc.lastBullishFVGBottom)}~${formatPrice(smc.lastBullishFVGTop)}`
      : null;
  const fvgBear =
    hasSmcLevel(smc.lastBearishFVGTop) && hasSmcLevel(smc.lastBearishFVGBottom)
      ? `看跌 ${formatPrice(smc.lastBearishFVGBottom)}~${formatPrice(smc.lastBearishFVGTop)}`
      : null;
  const fvgText = fvgBull || fvgBear;
  if (fvgText) pushTooltipItem(rows, "FVG", fvgText);
  // 强/弱点
  const strongWeakParts: string[] = [];
  if (hasSmcLevel(smc.strongHigh)) strongWeakParts.push(`强高${formatPrice(smc.strongHigh)}`);
  if (hasSmcLevel(smc.weakHigh)) strongWeakParts.push(`弱高${formatPrice(smc.weakHigh)}`);
  if (hasSmcLevel(smc.strongLow)) strongWeakParts.push(`强低${formatPrice(smc.strongLow)}`);
  if (hasSmcLevel(smc.weakLow)) strongWeakParts.push(`弱低${formatPrice(smc.weakLow)}`);
  if (strongWeakParts.length > 0) {
    pushTooltipItem(rows, "强/弱点", strongWeakParts.join(" "));
  }
  // Trailing
  if (hasSmcLevel(smc.trailingLow) || hasSmcLevel(smc.trailingHigh)) {
    pushTooltipItem(
      rows,
      "Trailing",
      `${formatPrice(smc.trailingLow)} ~ ${formatPrice(smc.trailingHigh)}`,
    );
  }
}
function formatProfitPercent(x: number): string {
  if (!Number.isFinite(x)) return "-";
  const abs = Math.abs(x);
  return abs >= 1 ? x.toFixed(0) : x.toFixed(2);
}
function renderSignalTooltip(
  marker: any,
  crosshairTime: number,
  sameTimeMarkers: any[],
  bar: KLineDto,
  point: { x: number; y: number },
) {
  const meta = (marker as any).meta || {};
  const title = String(meta.title ?? marker.text ?? "信号");
  const color = String(marker.color ?? "#303133");
  const rows: SignalTooltipRow[] = [];
  const timeText = formatTime(crosshairTime);
  pushTooltipSection(rows, "📌 交易基本信息");
  pushTooltipItem(rows, "时间", timeText);
  const metaType = typeof meta.signalType === "string" ? meta.signalType : "";
  if (metaType) {
    pushTooltipItem(rows, "类型", formatSignalTypeLabel(metaType));
  }
  const entryType = meta.entryType;
  if (entryType) {
    pushTooltipItem(rows, "入场类型", entryType === "LIMIT" ? "限价" : "市价");
  }
  const limitPrice = meta.limitPrice;
  if (limitPrice != null && Number.isFinite(Number(limitPrice))) {
    pushTooltipItem(rows, "限价单价格", formatPrice(limitPrice));
  }
  const markerPrice = meta.price != null ? Number(meta.price) : null;
  if (markerPrice != null && Number.isFinite(markerPrice)) {
    pushTooltipItem(rows, "价格", formatPrice(markerPrice));
  } else {
    pushTooltipItem(rows, "价格", formatPrice(bar.open));
  }
  const sourceText = normalizeSignalSourceText(meta.signalSource);
  if (meta.robotId) {
    pushTooltipItem(rows, "机器人", String(meta.robotId));
  }
  if (meta.orderSn) {
    pushTooltipItem(rows, "订单", String(meta.orderSn));
  }
  if (meta.status) {
    pushTooltipItem(rows, "状态", String(meta.status));
  }
  if (meta.signalStrength != null) {
    pushTooltipItem(rows, "信号强度", String(meta.signalStrength));
  }
  if (meta.marketTrend) {
    const trendMap: Record<string, string> = {
      // 上升趋势类
      STRONG_BULLISH_HEALTHY: "强上升·健康",
      STRONG_BULLISH_SHALLOW_PULLBACK: "强上升·浅回调",
      STRONG_BULLISH_WARNING_1H: "强上升·预警回调（1H）",
      STRONG_BULLISH_WARNING_4H: "强上升·预警回调（4H内部）",
      STRONG_BULLISH_CONFIRMED_PULLBACK: "强上升·确认回调",
      BULLISH_PULLBACK_ONGOING: "上升回调·进行中",
      BULLISH_PULLBACK_BOTTOMING: "上升回调·筑底",
      BULLISH_PULLBACK_FAILURE: "上升回调·失败",
      BULLISH_ENDING_CONTINUE_DOWN: "上升末端·延续下跌",
      BULLISH_ENDING_CONFIRM: "上升末端·转势确认",
      // 下降趋势类
      STRONG_BEARISH_HEALTHY: "强下降·健康",
      STRONG_BEARISH_SHALLOW_BOUNCE: "强下降·浅反弹",
      STRONG_BEARISH_WARNING_1H: "强下降·预警反弹（1H）",
      STRONG_BEARISH_WARNING_4H: "强下降·预警反弹（4H内部）",
      STRONG_BEARISH_CONFIRMED_BOUNCE: "强下降·确认反弹",
      BEARISH_PULLBACK_ONGOING: "下降反弹·进行中",
      BEARISH_PULLBACK_TOPPING: "下降反弹·筑顶",
      BEARISH_PULLBACK_FAILURE: "下降反弹·失败",
      BEARISH_ENDING_CONTINUE_UP: "下降末端·延续反弹",
      BEARISH_ENDING_CONFIRM: "下降末端·转势确认",
      // 震荡类
      RANGING_NO_DIRECTION: "震荡·无方向",
      // 未知
      UNKNOWN: "未知",
    };
    pushTooltipItem(rows, "市场趋势", trendMap[meta.marketTrend as string] || String(meta.marketTrend));
  }
  // 从 extraParams 中提取拒绝原因
  let rejectReason: string | null = null;
  if (meta.extraParams) {
    try {
      const parsed = JSON.parse(meta.extraParams as string);
      if (parsed.reason && typeof parsed.reason === "string") {
        rejectReason = parsed.reason;
      }
    } catch (_) {}
  }
  if (rejectReason) {
    pushTooltipItem(rows, "拒绝原因", rejectReason);
  }
  const description = String(meta.description ?? marker.text ?? "");
  if (description) {
    pushTooltipItem(rows, "描述", description);
  }
  let { smc15m, smc1h, smc4h } = getSignalSmcFromExtraParams(meta.extraParams);
  if (!smc15m && !smc1h && !smc4h) {
    const fromOthers = getSmcFromMarkers(sameTimeMarkers);
    smc15m = fromOthers.smc15m;
    smc1h = fromOthers.smc1h;
    smc4h = fromOthers.smc4h;
  }
  const side =
    metaType.toUpperCase() === "LB"
      ? ("long" as const)
      : metaType.toUpperCase() === "SB"
        ? ("short" as const)
        : null;
  let extraParamsCriticalLevels: any[] | null = null;
  if (meta.extraParams) {
    try {
      const parsed = JSON.parse(meta.extraParams as string);
      if (parsed.criticalLevels && Array.isArray(parsed.criticalLevels)) {
        extraParamsCriticalLevels = parsed.criticalLevels;
      }
    } catch (_) {}
  }
  if (extraParamsCriticalLevels && extraParamsCriticalLevels.length > 0) {
    pushTooltipSection(rows, "🎯 关键点位");
    for (const cl of extraParamsCriticalLevels) {
      const price = Number.isFinite(cl.price) ? formatPrice(cl.price) : "";
      const distance = cl.distancePercent != null
        ? `(${cl.distancePercent >= 0 ? "+" : ""}${cl.distancePercent.toFixed(2)}%)`
        : "";
      pushTooltipItem(rows, `${cl.action || cl.type || ""}`, `${price}${distance}`);
    }
  }
  // SMC 看板数据（方向矩阵、周期共振等）
  let smcDashboard: any = null;
  if (meta.extraParams) {
    try {
      const parsed = JSON.parse(meta.extraParams as string);
      if (parsed.smcDashboard && typeof parsed.smcDashboard === "object") {
        smcDashboard = parsed.smcDashboard;
      }
    } catch (_) {}
  }
  if (smcDashboard) {
    pushTooltipSection(rows, "SMC 看板");
    const matrix = smcDashboard.matrix || {};
    const matrixText = Object.entries(matrix)
      .map(([k, v]) => `${k}:${v}`)
      .join("  ");
    if (matrixText) pushTooltipItem(rows, "方向矩阵", matrixText);
    if (smcDashboard.resonance) {
      pushTooltipItem(rows, "周期共振", smcDashboard.resonance);
    }
    if (smcDashboard.alignment) {
      pushTooltipItem(rows, "信号共振", smcDashboard.alignment);
    }
    // 三层评估
    const layers = smcDashboard.layers || {};
    for (const period of ["4H", "1H", "15M"]) {
      const layer = layers[period];
      if (!layer) continue;
      const layerLabel = period === "4H" ? "战略层4H" : period === "1H" ? "战术层1H" : "执行层15M";
      pushTooltipSection(rows, layerLabel);
      if (layer.swingTrend) pushTooltipItem(rows, "趋势", layer.swingTrend);
      if (layer.wavePhase) pushTooltipItem(rows, "波次", layer.wavePhase);
      if (layer.positionRatio != null) pushTooltipItem(rows, "位置比", layer.positionRatio.toFixed(2));
      if (layer.structureAge != null) pushTooltipItem(rows, "年龄", `${layer.structureAge}根`);
      if (layer.flipCount != null) pushTooltipItem(rows, "翻转", String(layer.flipCount));
      const bosParts: string[] = [];
      if (layer.bullishBOS) bosParts.push("多头BOS");
      if (layer.bearishBOS) bosParts.push("空头BOS");
      if (bosParts.length) pushTooltipItem(rows, "BOS", bosParts.join("/"));
    }
  }
  if (smc15m) {
    pushTooltipSection(rows, "🔹 SMC 15分钟 (15m)");
    appendSmcTooltipRows(rows, smc15m);
  }
  if (smc1h) {
    pushTooltipSection(rows, "🔹 SMC 1小时 (1h)");
    appendSmcTooltipRows(rows, smc1h);
  }
  if (smc4h) {
    pushTooltipSection(rows, "🔹 SMC 4小时 (4h)");
    appendSmcTooltipRows(rows, smc4h);
  }
  const maxWidth = 520;
  const maxHeight = 420;
  const padding = 8;
  const rect = chartContainer.value!.getBoundingClientRect();
  const maxLeft = Math.max(padding, window.innerWidth - maxWidth - padding);
  const maxTop = Math.max(padding, window.innerHeight - maxHeight - padding);
  const left = clamp(rect.left + point.x + 12, padding, maxLeft);
  const top = clamp(rect.top + point.y + 12, padding, maxTop);
  signalTooltip.value = {
    visible: true,
    left,
    top,
    title,
    color,
    rows,
  };
}
function updateSignalTooltip(param: any, bar: KLineDto, crosshairTime: number) {
  const markersApi = seriesMarkersRef.value;
  const point = param?.point as { x: number; y: number } | undefined;
  if (!markersApi || !point) {
    hideSignalTooltip();
    return;
  }
  if (!chartContainer.value) {
    hideSignalTooltip();
    return;
  }
  const all = markersApi.markers() || [];
  if (!all.length) {
    hideSignalTooltip();
    return;
  }
  const sameTime = all.filter((m: any) => Number(m?.time) === crosshairTime);

  // 1. 先用像素距离检测鼠标附近是否有任意周期的 marker（支持跨周期悬浮）
  const hovered = pickHoveredMarker(point, all, bar);
  const windowSec = getIntervalMs(currentInterval.value) / 1000;
  if (hovered) {
    // 找到了，但限制时间窗口避免误触，同时如果存在同时间 marker 则优先使用同时间 marker
    const markerTime = Number((hovered as any).time);
    // 使用区间检查 [crosshairTime, crosshairTime + windowSec) 替代对称窗口，
    // 确保信号时间属于当前蜡烛区间，避免跨周期误匹配（如 15min 周期信号 00:51:00 只命中 00:45:00 蜡烛，不命中 01:00:00）
    const withinWindow = markerTime >= crosshairTime && markerTime < crosshairTime + windowSec;
    if (withinWindow || sameTime.length) {
      const target = withinWindow ? hovered : sameTime[0];
      renderSignalTooltip(target, crosshairTime, sameTime, bar, point);
      return;
    }
  }

  // 2. 精确时间匹配兜底
  if (sameTime.length) {
    renderSignalTooltip(sameTime[0], crosshairTime, sameTime, bar, point);
    return;
  }

  // 3. 附近时间窗口内同一机器人跨周期信号匹配
  const candidateMarkers = all.filter((m: any) => {
    const markerTime = Number(m?.time);
    // 使用区间检查 [crosshairTime, crosshairTime + windowSec) 替代对称窗口，
    // 确保信号只命中所属蜡烛区间
    if (!markerTime || markerTime < crosshairTime || markerTime >= crosshairTime + windowSec) return false;
    if (selectedBotId.value) {
      const meta = (m as any).meta || {};
      return meta.robotId === selectedBotId.value;
    }
    return true;
  });
  if (candidateMarkers.length) {
    renderSignalTooltip(candidateMarkers[0], crosshairTime, sameTime, bar, point);
    return;
  }

  hideSignalTooltip();
}
function updateContainerSize() {
  if (!chartArea.value) return;
  const rect = chartArea.value.getBoundingClientRect();
  const w = Math.max(rect.width || 1, 1);
  const h = Math.max(rect.height || 1, 1);
  if (containerWidth.value === w && containerHeight.value === h) return;
  containerWidth.value = w;
  containerHeight.value = h;
  if (chart.value) {
    chart.value.resize(w, h);
  }
}
/**
 * 合并新加载的K线到缓存（按时间排序并去重）
 */
function updateDataCache(newBars: KLineDto[]) {
  if (!newBars || newBars.length === 0) return;
  // 如果缓存为空，直接使用新数据
  if (dataCache.value.length === 0) {
    dataCache.value = [...newBars];
    return;
  }
  const merged = [...dataCache.value, ...newBars];
  // 按时间排序
  merged.sort((a, b) => a.time - b.time);
  // 去重（相同 time 只保留一条，取后面的，即新数据优先）
  const unique: KLineDto[] = [];
  const seen = new Set<number>();
  for (let i = merged.length - 1; i >= 0; i--) {
    const t = merged[i].time;
    if (!seen.has(t)) {
      seen.add(t);
      unique.push(merged[i]);
    }
  }
  // 再次按时间升序
  unique.sort((a, b) => a.time - b.time);
  // 如果缓存数据量超过最大值，清理旧数据（保留可见范围附近的数据）
  if (unique.length > MAX_CACHE_SIZE && chart.value) {
    const timeScale = chart.value.timeScale();
    const visibleRange = timeScale.getVisibleLogicalRange();
    if (visibleRange) {
      const from =
        typeof visibleRange.from === "number"
          ? visibleRange.from
          : parseFloat(String(visibleRange.from));
      const to =
        typeof visibleRange.to === "number"
          ? visibleRange.to
          : parseFloat(String(visibleRange.to));
      // 保留可见范围前后各 MAX_CACHE_SIZE / 2 的数据
      const keepFrom = Math.max(0, Math.floor(from - MAX_CACHE_SIZE / 2));
      const keepTo = Math.min(
        unique.length,
        Math.ceil(to + MAX_CACHE_SIZE / 2),
      );
      dataCache.value = unique.slice(keepFrom, keepTo);
    } else {
      // 如果没有可见范围，保留最新的数据
      dataCache.value = unique.slice(-MAX_CACHE_SIZE);
    }
  } else {
    dataCache.value = unique;
  }
}
/**
 * 设置可见范围变化监听（左右拖动时按需加载更多数据）
 * 参考官方 infinite scroll 示例：subscribeVisibleLogicalRangeChange
 */
function setupVisibleRangeListener() {
  if (!chart.value || !candleSeries.value) return;
  const timeScale = chart.value.timeScale();
  // 取消旧的监听，避免多次切换周期导致监听器堆积
  if (visibleRangeChangeHandler) {
    try {
      timeScale.unsubscribeVisibleLogicalRangeChange(visibleRangeChangeHandler);
    } catch (_) {
      // 忽略取消订阅时的异常
    }
    visibleRangeChangeHandler = null;
  }
  visibleRangeChangeHandler = (logicalRange) => {
    if (!logicalRange || isLoadingMore || loading.value) return;
    const from =
      typeof logicalRange.from === "number"
        ? logicalRange.from
        : parseFloat(String(logicalRange.from));
    const to =
      typeof logicalRange.to === "number"
        ? logicalRange.to
        : parseFloat(String(logicalRange.to));
    const total = dataCache.value.length;
    if (!total || Number.isNaN(from) || Number.isNaN(to)) return;
    // 判断是否接近边界
    // 对于 forward 方向，增加触发范围，确保快速拖动时也能触发加载
    const forwardThreshold = MIN_BARS_THRESHOLD * 2; // forward 方向使用更大的阈值
    const nearLeftBoundary = from < MIN_BARS_THRESHOLD;
    const nearRightBoundary = to > total - forwardThreshold;
    // 如果非常接近边界（< 20根），立即加载，不使用防抖，确保流畅
    const veryCloseToLeft = from < 20;
    const veryCloseToRight = to > total - 20;
    // 如果用户拖动图表查看历史数据（不在最右边），自动关闭实时模式
    if (isRealtimeMode.value && !veryCloseToRight && to < total - 10) {
      isRealtimeMode.value = false;
    }
    // 加载信号标注（根据可见范围）
    void loadSignalsForVisibleRange();
    if (veryCloseToLeft || veryCloseToRight) {
      // 立即加载，不使用防抖
      if (veryCloseToLeft) {
        void loadMoreHistory("backward");
      } else if (veryCloseToRight) {
        void loadMoreHistory("forward");
      }
    } else if (nearLeftBoundary || nearRightBoundary) {
      // 接近边界但不是很近，使用防抖
      // 清除之前的防抖定时器
      if (loadDebounceTimer) {
        clearTimeout(loadDebounceTimer);
        loadDebounceTimer = null;
      }
      // 防抖：延迟50ms执行，减少延迟提高响应速度
      loadDebounceTimer = window.setTimeout(async () => {
        if (nearLeftBoundary) {
          await loadMoreHistory("backward");
        } else if (nearRightBoundary) {
          await loadMoreHistory("forward");
        }
      }, 50);
    }
  };
  timeScale.subscribeVisibleLogicalRangeChange(visibleRangeChangeHandler);
}
/** 监听可视时间范围变化 → 重新裁剪指标数据 */
function initIndicatorRangeSync() {
  if (!chart.value) return;
  const timeScale = chart.value.timeScale();
  // 取消旧的监听
  if (indicatorRangeHandler) {
    try {
      timeScale.unsubscribeVisibleTimeRangeChange(indicatorRangeHandler);
    } catch (_) { }
    indicatorRangeHandler = null;
  }
  if (indicatorRangeRAF !== null) {
    cancelAnimationFrame(indicatorRangeRAF);
    indicatorRangeRAF = null;
  }
  indicatorRangeHandler = () => {
    if (indicatorRangeRAF !== null) return;
    indicatorRangeRAF = window.requestAnimationFrame(() => {
      indicatorRangeRAF = null;
      if (indicators.value.macd) updateMACD();
      if (indicators.value.rsi) updateRSI();
      if (indicators.value.boll) updateBoll();
    });
  };
  timeScale.subscribeVisibleTimeRangeChange(indicatorRangeHandler);
}
/**
 * 按方向加载更多历史数据（用于左右拖动）
 */
/**
 * 按方向加载更多历史数据（用于左右拖动）
 */
async function loadMoreHistory(direction: "backward" | "forward") {
  if (isLoadingMore || !dataCache.value.length || !chart.value) return;
  isLoadingMore = true;
  try {
    const timeScale = chart.value.timeScale();
    const logicalBefore = timeScale.getVisibleLogicalRange();
    const firstTime = dataCache.value[0].time;
    // 对于 forward 方向，使用可见范围的最右端时间作为 anchorTime
    // 这样可以避免被实时更新覆盖，因为实时更新只会更新最后一根K线
    let lastTime: number;
    if (direction === "forward") {
      if (logicalBefore) {
        // 使用可见范围的最右端逻辑索引对应的K线时间
        const visibleTo =
          typeof logicalBefore.to === "number"
            ? logicalBefore.to
            : parseFloat(String(logicalBefore.to));
        const total = dataCache.value.length;
        if (visibleTo < total && visibleTo >= 0) {
          // 使用可见范围最右端的K线时间（向下取整，确保是已存在的K线）
          const visibleIndex = Math.min(Math.floor(visibleTo), total - 1);
          lastTime = dataCache.value[visibleIndex].time;
        } else if (visibleTo >= total) {
          // 如果可见范围超出数据范围，说明需要加载更多数据
          // 使用最后一根K线的时间，但需要确保这个时间不会被实时更新改变
          // 所以立即保存这个时间
          lastTime = dataCache.value[dataCache.value.length - 1].time;
        } else {
          // 异常情况，使用最后一根K线的时间
          lastTime = dataCache.value[dataCache.value.length - 1].time;
        }
      } else {
        // 没有可见范围信息，使用最后一根K线的时间
        lastTime = dataCache.value[dataCache.value.length - 1].time;
      }
    } else {
      // backward 方向，使用第一根K线的时间
      lastTime = dataCache.value[dataCache.value.length - 1].time;
    }
    // 立即保存 anchorTime，避免后续被实时更新改变
    const anchorTime = direction === "backward" ? firstTime : lastTime;
    console.debug("loadMoreHistory: 保存 anchorTime", {
      direction,
      anchorTime,
      isRealtimeMode: isRealtimeMode.value,
    });
    // forward 方向加载更多数据，确保填满右边界
    // 计算当前可见范围，加载足够的数据填满可见范围
    let loadLimit = LOAD_BATCH_SIZE;
    if (direction === "forward" && logicalBefore) {
      const visibleRange =
        (logicalBefore.to as number) - (logicalBefore.from as number);
      // 加载至少2倍的可见范围数据，确保有足够的数据填充右边界
      loadLimit = Math.max(LOAD_BATCH_SIZE, Math.ceil(visibleRange * 2));
    }
    const res = await loadKLineData({
      symbol: selectedSymbol.value,
      interval: convertIntervalToBackend(currentInterval.value),
      exchange: selectedExchange.value,
      direction,
      anchorTime,
      limit: loadLimit,
    });
    // 根据 base.ts 的拦截器，v1 API 返回格式是 { code, message, data }
    // 其中 data 是 KLineLoadResponse，包含 { symbol, interval, direction, data: KLineDataDTO[], hasMore, nextAnchorTime }
    const responseData = res?.data || res;
    const segment: any[] = responseData?.data || []; // responseData.data 是 KLineDataDTO[] 数组
    if (!segment.length) {
      // 没有更多数据，直接返回
      return;
    }
    const newBars: KLineDto[] = segment.map((k) => ({
      time: k.time,
      open: Number(k.open),
      high: Number(k.high),
      low: Number(k.low),
      close: Number(k.close),
    }));
    // 在更新前记录旧的第一根时间，用于计算向左加载时逻辑索引偏移
    const oldFirstTime = dataCache.value[0].time;
    const oldLastTime = dataCache.value[dataCache.value.length - 1].time;
    // 记录更新前的数据量
    const oldDataLength = dataCache.value.length;
    updateDataCache(newBars);
    // 等待数据更新完成
    await nextTick();
    // 更新图表数据
    candleSeries.value!.setData(dataCache.value);
    updateMACD();
    updateBoll();
    updateTrendStrengthIndicator();
    updateMA();
    updateRangeFilter();
    updateRSI();
    updateSmcLite();
    updateReversalConfirmation();
    updateTSM();
    updateAndeanOscillator();
    await nextTick();
    // 加载信号标注
    void loadSignalsForVisibleRange();
    // 如果是向左加载（backward），需要调整可见逻辑范围，避免视图跳动
    if (direction === "backward" && logicalBefore) {
      const newIndexOfOldFirst = dataCache.value.findIndex(
        (b) => b.time === oldFirstTime,
      );
      if (newIndexOfOldFirst >= 0) {
        const addedLeft = newIndexOfOldFirst;
        const adjustedRange = {
          from: (logicalBefore.from as number) + addedLeft,
          to: (logicalBefore.to as number) + addedLeft,
        };
        timeScale.setVisibleLogicalRange(adjustedRange);
      }
    }
    // 如果是向右加载（forward），检查是否需要继续加载更多数据
    if (direction === "forward") {
      await nextTick();
      // 检查是否有更多数据（从响应中获取 hasMore 标志）
      const hasMore = responseData?.hasMore !== false; // 默认为 true，除非明确为 false
      // 如果还有更多数据，继续加载直到填满右边界
      if (hasMore) {
        const currentLogicalRange = timeScale.getVisibleLogicalRange();
        if (currentLogicalRange) {
          const currentTo =
            typeof currentLogicalRange.to === "number"
              ? currentLogicalRange.to
              : parseFloat(String(currentLogicalRange.to));
          const total = dataCache.value.length;
          // 如果加载后仍然接近右边界，继续加载更多数据
          // 使用循环确保加载足够的数据填满右边界
          // 对于 forward 方向，使用更大的阈值，确保快速拖动时也能继续加载
          const forwardThreshold = MIN_BARS_THRESHOLD * 2;
          let continueLoading = currentTo > total - forwardThreshold;
          // 保存初始 anchorTime，避免被实时更新覆盖
          // 使用第一次加载返回的数据中最后一根K线的时间，确保类型正确
          let lastAnchorTime: number;
          if (newBars.length > 0) {
            // 使用第一次加载返回的数据中最后一根K线的时间
            lastAnchorTime = newBars[newBars.length - 1].time;
          } else if (segment.length > 0) {
            // 如果 newBars 为空，使用 segment 中最后一根K线的时间
            const lastSegment = segment[segment.length - 1];
            lastAnchorTime =
              typeof lastSegment.time === "number"
                ? lastSegment.time
                : typeof lastSegment.time === "string"
                  ? parseInt(lastSegment.time, 10)
                  : Number(lastSegment.time);
          } else {
            // 如果都没有，使用 dataCache 中最后一根K线的时间
            lastAnchorTime = dataCache.value[dataCache.value.length - 1].time;
          }
          console.debug("loadMoreHistory: 循环加载初始 anchorTime", {
            lastAnchorTime,
            newBarsLength: newBars.length,
            segmentLength: segment.length,
          });
          let maxIterations = 10; // 增加最大循环次数，确保有足够的数据
          let iteration = 0;
          while (continueLoading && iteration < maxIterations) {
            iteration++;
            const continueRes = await loadKLineData({
              symbol: selectedSymbol.value,
              interval: convertIntervalToBackend(currentInterval.value),
              exchange: selectedExchange.value,
              direction: "forward",
              anchorTime: lastAnchorTime,
              limit: LOAD_BATCH_SIZE,
            });
            const continueResponseData = continueRes?.data || continueRes;
            const continueSegment: any[] = continueResponseData?.data || [];
            const continueHasMore = continueResponseData?.hasMore !== false;
            if (continueSegment.length === 0 || !continueHasMore) {
              // 没有更多数据了，停止加载
              break;
            }
            const continueBars: KLineDto[] = continueSegment.map((k) => ({
              time: k.time,
              open: Number(k.open),
              high: Number(k.high),
              low: Number(k.low),
              close: Number(k.close),
            }));
            updateDataCache(continueBars);
            await nextTick();
            candleSeries.value!.setData(dataCache.value);
            updateMACD();
            updateBoll();
            updateTrendStrengthIndicator();
            updateMA();
            updateRangeFilter();
            updateRSI();
            updateSmcLite();
            updateReversalConfirmation();
            updateTSM();
            updateAndeanOscillator();
            updateMultiTimeframeTrend();
            // 更新 anchorTime：使用加载返回的数据中最后一根K线的时间
            // 而不是从 dataCache 中获取，避免被实时更新覆盖
            if (continueBars.length > 0) {
              const newAnchorTime = continueBars[continueBars.length - 1].time;
              // 确保 anchorTime 是数字类型
              if (typeof newAnchorTime === "number" && !isNaN(newAnchorTime)) {
                lastAnchorTime = newAnchorTime;
                console.debug("loadMoreHistory: 循环中更新 anchorTime", {
                  iteration,
                  lastAnchorTime,
                  continueBarsLength: continueBars.length,
                });
              } else {
                console.error("loadMoreHistory: 无效的 anchorTime", {
                  newAnchorTime,
                  continueBars,
                });
                break; // 如果 anchorTime 无效，停止循环
              }
            } else {
              console.warn("loadMoreHistory: continueBars 为空，停止循环", {
                iteration,
              });
              break;
            }
            await nextTick(); // 等待图表更新
            const newLogicalRange = timeScale.getVisibleLogicalRange();
            if (newLogicalRange) {
              const newTo =
                typeof newLogicalRange.to === "number"
                  ? newLogicalRange.to
                  : parseFloat(String(newLogicalRange.to));
              const newTotal = dataCache.value.length;
              // 如果仍然接近右边界且还有更多数据，继续加载
              // 使用更大的阈值，确保快速拖动时也能继续加载
              const forwardThreshold = MIN_BARS_THRESHOLD * 2;
              continueLoading =
                newTo > newTotal - forwardThreshold && continueHasMore;
            } else {
              continueLoading = false;
            }
          }
          // 加载信号标注
          void loadSignalsForVisibleRange();
        }
      }
    }
  } catch (e) {
    console.error("❌ 加载更多历史数据失败:", e);
  } finally {
    isLoadingMore = false;
  }
}
function getCssVar(name: string): string {
  if (typeof window === "undefined") return "";
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}
function getChartThemeColors() {
  const isDark = document.documentElement.classList.contains("dark");
  const bg = getCssVar("--mk-bg-primary") || (isDark ? "#0b0f14" : "#ffffff");
  const text = getCssVar("--mk-text-primary") || (isDark ? "#f2f4f6" : "#1e222d");
  const border = getCssVar("--mk-border") || (isDark ? "rgba(255,255,255,0.08)" : "#e0e3eb");
  const grid = isDark ? "rgba(255,255,255,0.06)" : "rgba(180,185,195,0.25)";
  return { bg, text, border, grid };
}
function applyChartTheme() {
  if (!chart.value) return;
  const colors = getChartThemeColors();
  chart.value.applyOptions({
    layout: {
      background: { type: "Solid", color: colors.bg },
      textColor: colors.text,
    },
    grid: {
      vertLines: { color: colors.grid },
      horzLines: { color: colors.grid },
    },
    rightPriceScale: { borderColor: colors.bg },
    leftPriceScale: { borderColor: colors.bg },
    timeScale: { borderColor: colors.border },
  });
}
function initChart() {
  if (!chartContainer.value) {
    console.error("图表容器未找到");
    return;
  }
  // 先计算容器尺寸
  updateContainerSize();
  const colors = getChartThemeColors();
  try {
    chart.value = createChart(chartContainer.value, {
      width: containerWidth.value,
      height: containerHeight.value,
      layout: {
        background: { type: "Solid", color: colors.bg },
        textColor: colors.text,
      },
      grid: {
        vertLines: { color: colors.grid },
        horzLines: { color: colors.grid },
      },
      rightPriceScale: {
        borderColor: colors.bg,
        autoScale: true,
        scaleMargins: {
          top: 0.0,
          bottom: 0.12,
        },
        entireTextOnly: false,
        mode: 0,
      },
      timeScale: {
        borderColor: colors.bg,
        timeVisible: true,
        secondsVisible: false,
        rightOffset: 2,
        barSpacing: 6,
        tickMarkFormatter: TimezoneHelper.getTickMarkFormatter(), // UTC+8 时间格式化
      },
      localization: {
        locale: "zh-CN",
        dateFormat: "yyyy-MM-dd",
        timeFormat: "HH:mm:ss",
        timeFormatter: function (time: number) {
          // time 是秒级时间戳（UTC），转换为北京时间显示
          const date = new Date(time * 1000);
          return date
            .toLocaleString("zh-CN", {
              timeZone: "Asia/Shanghai",
              year: "numeric",
              month: "2-digit",
              day: "2-digit",
              hour: "2-digit",
              minute: "2-digit",
              second: "2-digit",
              hour12: false,
            })
            .replace(/\//g, "-");
        },
      },
      crosshair: {
        mode: 0,
      },
    });
    // 监听十字光标移动事件
    chart.value.subscribeCrosshairMove((param) => {
      handleCrosshairMove(param);
    });
    // 监听图表点击事件（用于绘制趋势线）
    chart.value.subscribeClick((param) => {
      handleChartClick(param);
    });
    // v5: addSeries(SeriesType, options[, paneIndex])，主图 pane 0
    candleSeries.value = chart.value.addSeries(CandlestickSeries, {
      upColor: "#00C853",
      downColor: "#FF1744",
      borderVisible: false,
      wickUpColor: "#00C853",
      wickDownColor: "#FF1744",
      priceLineVisible: false,
      lastValueVisible: true, // 启用最新价格显示
    });
    // 预创建均线系列（空数据），避免动态 addSeries 导致画布闪烁
    maSeriesRef.value.ema1 = chart.value.addSeries(LineSeries, {
      color: "#ff6b6b", lineWidth: 1, title: "EMA9",
      priceLineVisible: false, lastValueVisible: true,
    });
    maSeriesRef.value.ema2 = chart.value.addSeries(LineSeries, {
      color: "#4ecdc4", lineWidth: 1, title: "EMA21",
      priceLineVisible: false, lastValueVisible: true,
    });
    maSeriesRef.value.ema3 = chart.value.addSeries(LineSeries, {
      color: "#45b7d1", lineWidth: 1, title: "EMA55",
      priceLineVisible: false, lastValueVisible: true,
    });
    maSeriesRef.value.ema4 = chart.value.addSeries(LineSeries, {
      color: "#f9ca24", lineWidth: 1, title: "EMA144",
      priceLineVisible: false, lastValueVisible: true,
    });
    // v5: markers 由 createSeriesMarkers 插件提供
    seriesMarkersRef.value = createSeriesMarkers(candleSeries.value, []);
    // 拦截 setMarkers，确保订单路径标记在任何 setMarkers 调用后都会合并保留
    if (seriesMarkersRef.value) {
      const originalSetMarkers = seriesMarkersRef.value.setMarkers.bind(
        seriesMarkersRef.value,
      );
      seriesMarkersRef.value.setMarkers = (incoming: any[]) => {
        const valid = (incoming || []).filter((m) => m && m.time != null && !isNaN(Number(m.time)));
        const merged = mergeBaseWithOrderMarkers(valid);
        originalSetMarkers(merged);
      };
    }
    // 创建最新价格线
    updateCurrentPriceLine();
    // 设置 ResizeObserver 监听容器尺寸变化
    if (chartArea.value && window.ResizeObserver) {
      resizeObserver = new ResizeObserver(() => {
        updateContainerSize();
      });
      resizeObserver.observe(chartArea.value);
    }
    // 设置可见范围变化监听（无限滚动加载 + 指标裁剪）
    setupVisibleRangeListener();
    initIndicatorRangeSync();
    if (
      dataCache.value.length >= macdConfig.value.slowPeriod &&
      indicators.value.macd
    ) {
      updateMACD();
    }
    if (
      dataCache.value.length >= bollConfig.value.period &&
      indicators.value.boll
    ) {
      updateBoll();
    }
    if (
      dataCache.value.length >= trendStrengthConfig.value.period &&
      indicators.value.trendStrength
    ) {
      updateTrendStrengthIndicator();
    }
    if (indicators.value.ma) updateMA();
    if (indicators.value.rangeFilter) updateRangeFilter();
    if (indicators.value.rsi) updateRSI();
    if (indicators.value.smcLite) updateSmcLite();
    if (indicators.value.reversal) updateReversalConfirmation();
    if (indicators.value.tsm) updateTSM();
    if (indicators.value.andeanOscillator) updateAndeanOscillator();
    // 加载持仓订单用于图表价格线
    loadPositionsOrders()
  } catch (e) {
    console.error("❌ 图表初始化失败:", e);
    error.value = "图表初始化失败";
  }
}
async function loadByTime(targetTimeSec: number) {
  if (!candleSeries.value) {
    return;
  }
  const mySeq = ++loadByTimeSeqId;
  loading.value = true;
  error.value = null;
  try {
    const res = await jumpToTime({
      symbol: selectedSymbol.value,
      interval: convertIntervalToBackend(currentInterval.value),
      exchange: selectedExchange.value,
      time: targetTimeSec,
      before: 150,
      after: 150,
      limit: 400,
    });
    // 根据 base.ts 的拦截器，v1 API 返回格式是 { code, message, data }
    // res 已经是拦截器返回的 ApiResponse，res.data 是 KLineJumpResponse
    // 如果已触发新周期切换，忽略本次响应
    if (mySeq !== loadByTimeSeqId) return;
    const responseData = res?.data || {};
    const klines: any[] = responseData?.klines || [];
    if (!klines.length) {
      candleSeries.value.setData([]);
      lastPrice.value = null;
      prevPrice.value = null;
      error.value = "暂无数据";
      return;
    }
    const bars: KLineDto[] = klines.map((k) => ({
      time: k.time,
      open: Number(k.open),
      high: Number(k.high),
      low: Number(k.low),
      close: Number(k.close),
    }));
    // 检查返回的数据是否包含目标时间附近的数据
    const targetTime = targetTimeSec;
    const hasTargetTime = bars.some((b) => Math.abs(b.time - targetTime) < 180); // 3分钟内
    // 时间跳转时，先清理之前的闪烁标记
    if (flashMarkerTimer) {
      clearInterval(flashMarkerTimer);
      flashMarkerTimer = null;
    }
    if (flashMarkerTimeout) {
      clearTimeout(flashMarkerTimeout);
      flashMarkerTimeout = null;
    }
    // 判断是否跳转到"现在"（实时模式）
    const currentTime = Math.floor(Date.now() / 1000);
    const timeDiff = Math.abs(currentTime - targetTimeSec);
    // 如果跳转时间与当前时间相差小于1分钟，认为是"现在"
    isRealtimeMode.value = timeDiff < 60;
    // 时间跳转时，清空旧数据缓存，避免数据断层
    // 注意：清空缓存后，拖动加载数据时会从跳转点开始加载，确保数据连续性
    dataCache.value = [];
    // 更新数据缓存（直接设置新数据，不合并）
    dataCache.value = [...bars];
    // 跳转后，立即预加载左右两侧的数据（并行），确保拖动时不会断层
    // 并行预加载 backward + forward
    if (bars.length > 0) {
      const firstTime = bars[0].time;
      const lastTime = bars[bars.length - 1].time;
      const [backwardResult, forwardResult] = await Promise.all([
        (async () => {
          try {
            return await loadKLineData({
              symbol: selectedSymbol.value,
              interval: convertIntervalToBackend(currentInterval.value),
              exchange: selectedExchange.value,
              direction: "backward",
              anchorTime: firstTime,
              limit: LOAD_BATCH_SIZE,
            });
          } catch {
            return null;
          }
        })(),
        (async () => {
          try {
            return await loadKLineData({
              symbol: selectedSymbol.value,
              interval: convertIntervalToBackend(currentInterval.value),
              exchange: selectedExchange.value,
              direction: "forward",
              anchorTime: lastTime,
              limit: LOAD_BATCH_SIZE * 2,
            });
          } catch {
            return null;
          }
        })(),
      ]);
      // 处理 backward 数据
      const backwardRes = backwardResult;
      if (backwardRes) {
        const backwardResponseData = backwardRes?.data || backwardRes;
        const backwardSegment: any[] = backwardResponseData?.data || [];
        if (backwardSegment.length > 0) {
          const backwardBars: KLineDto[] = backwardSegment.map((k) => ({
            time: k.time,
            open: Number(k.open),
            high: Number(k.high),
            low: Number(k.low),
            close: Number(k.close),
          }));
          dataCache.value = [...backwardBars, ...dataCache.value];
        }
      }
      // 处理 forward 数据
      const forwardRes = forwardResult;
      if (forwardRes) {
        const forwardResponseData = forwardRes?.data || forwardRes;
        const forwardSegment: any[] = forwardResponseData?.data || [];
        if (forwardSegment.length > 0) {
          const forwardBars: KLineDto[] = forwardSegment.map((k) => ({
            time: k.time,
            open: Number(k.open),
            high: Number(k.high),
            low: Number(k.low),
            close: Number(k.close),
          }));
          dataCache.value = [...dataCache.value, ...forwardBars];
        }
      }
    }
    // 对合并后的数据进行排序和去重
    dataCache.value.sort((a, b) => a.time - b.time);
    const unique: KLineDto[] = [];
    const seen = new Set<number>();
    for (let i = 0; i < dataCache.value.length; i++) {
      const t = dataCache.value[i].time;
      if (!seen.has(t)) {
        seen.add(t);
        unique.push(dataCache.value[i]);
      }
    }
    dataCache.value = unique;
    // 如果已触发新周期切换，忽略本次响应
    if (mySeq !== loadByTimeSeqId) return;
    // 更新图表数据（使用合并后的完整数据）
    candleSeries.value.setData(dataCache.value);
    updateMACD();
    updateBoll();
    updateTrendStrengthIndicator();
    updateMA();
    updateRangeFilter();
    updateRSI();
    updateSmcLite();
    updateReversalConfirmation();
    updateTSM();
    updateAndeanOscillator();
    // 更新最新价格线
    if (bars.length > 0) {
      lastPrice.value = bars[bars.length - 1].close;
      if (bars.length > 1) {
        prevPrice.value = bars[bars.length - 2].close;
      }
      updateCurrentPriceLine();
    }
    // 数据更新后，重新设置可见范围监听器，确保拖拽加载正常工作
    await nextTick();
    // 如果已触发新周期切换，忽略本次响应
    if (mySeq !== loadByTimeSeqId) return;
    setupVisibleRangeListener();
    initIndicatorRangeSync();
    // 加载信号标注
    void loadSignalsForVisibleRange();
    if (bars.length > 1) {
      prevPrice.value = bars[bars.length - 2].close;
      lastPrice.value = bars[bars.length - 1].close;
      // 设置默认显示最新K线信息
      focusedKlineData.value = {
        time: bars[bars.length - 1].time,
        open: bars[bars.length - 1].open,
        high: bars[bars.length - 1].high,
        low: bars[bars.length - 1].low,
        close: bars[bars.length - 1].close,
      };
      // 更新最新价格线
      updateCurrentPriceLine();
    } else if (bars.length === 1) {
      lastPrice.value = bars[0].close;
      prevPrice.value = bars[0].close;
      focusedKlineData.value = {
        time: bars[0].time,
        open: bars[0].open,
        high: bars[0].high,
        low: bars[0].low,
        close: bars[0].close,
      };
      // 更新最新价格线
      updateCurrentPriceLine();
    }
    // 设置可见范围，让目标K线精确居中，并确保左右都有足够数据
    if (chart.value && dataCache.value.length > 0) {
      await nextTick();
      // 找到最接近目标时间的K线时间
      const nearestKlineTime = findNearestKlineTime(
        targetTimeSec,
        dataCache.value,
      );
      // 找到该K线在数据中的索引位置
      const targetIndex = dataCache.value.findIndex(
        (b) => b.time === nearestKlineTime,
      );
      if (targetIndex >= 0) {
        const timeScale = chart.value.timeScale();
        const totalBars = dataCache.value.length;
        // 计算要显示的K线数量（约300根）
        const visibleBars = Math.min(300, totalBars);
        // 计算目标K线应该处于的屏幕位置（中间）
        const centerOffset = Math.floor(visibleBars / 2);
        // 计算逻辑索引范围，确保目标K线在中间
        let logicalFrom = targetIndex - centerOffset;
        let logicalTo = targetIndex + centerOffset;
        // 检查右侧数据是否充足，如果不足则主动加载
        if (logicalTo >= totalBars - MIN_BARS_THRESHOLD) {
          // 右侧数据不足，先加载更多右侧数据
          const lastTime = dataCache.value[dataCache.value.length - 1].time;
          try {
            const res = await loadKLineData({
              symbol: selectedSymbol.value,
              interval: convertIntervalToBackend(currentInterval.value),
              direction: "forward",
              anchorTime: lastTime,
              limit: LOAD_BATCH_SIZE,
            });
            const payload = res?.data || res;
            const segment: any[] = payload?.data?.data || [];
            if (segment.length > 0) {
              const newBars: KLineDto[] = segment.map((k) => ({
                time: k.time,
                open: Number(k.open),
                high: Number(k.high),
                low: Number(k.low),
                close: Number(k.close),
              }));
              updateDataCache(newBars);
              candleSeries.value.setData(dataCache.value);
              updateMACD();
              updateBoll();
              updateTrendStrengthIndicator();
              updateMA();
              updateReversalConfirmation();
              // 加载信号标注
              void loadSignalsForVisibleRange();
              // 重新计算总数据量
              const newTotalBars = dataCache.value.length;
              // 重新计算逻辑范围
              logicalFrom = targetIndex - centerOffset;
              logicalTo = targetIndex + centerOffset;
              // 确保不超出新的数据范围
              if (logicalTo >= newTotalBars) {
                logicalTo = newTotalBars - 1;
                logicalFrom = Math.max(0, newTotalBars - visibleBars);
              }
            }
          } catch (e) {
            // 加载失败，忽略
          }
        }
        // 如果范围超出数据边界，调整但尽量保持目标K线居中
        const finalTotalBars = dataCache.value.length;
        if (logicalFrom < 0) {
          logicalFrom = 0;
          logicalTo = Math.min(visibleBars, finalTotalBars);
        } else if (logicalTo >= finalTotalBars) {
          logicalTo = finalTotalBars - 1;
          logicalFrom = Math.max(0, finalTotalBars - visibleBars);
        }
        // 使用逻辑索引范围设置可见范围，确保目标K线居中
        await nextTick();
        timeScale.setVisibleLogicalRange({
          from: logicalFrom,
          to: logicalTo,
        });

        // 显示闪烁标记，标记目标时间K线
        await nextTick();
        showFlashMarker(nearestKlineTime, 3000);
        // 加载信号标注
        void loadSignalsForVisibleRange();
      }
    }
  } catch (e: any) {
    console.error("❌ 加载K线失败:", e);
    error.value = e?.message || "加载数据失败";
  } finally {
    loading.value = false;
  }
}
function jumpToNow() {
  const nowSec = Math.floor(Date.now() / 1000);
  isRealtimeMode.value = true;
  void loadByTime(nowSec);
}
// 切换实时模式
function toggleRealtimeMode() {
  isRealtimeMode.value = !isRealtimeMode.value;
  if (isRealtimeMode.value && chart.value) {
    // 切换到实时模式：滚动到最新数据
    chart.value.timeScale().scrollToRealTime();
    // 已切换到实时模式
  } else {
    // 已切换到历史模式
  }
}
/**
 * 处理时间跳转
 */
function handleTimeJump() {
  if (!jumpDateTime.value) {
    return;
  }
  // 用户选择的时间字符串格式：YYYY-MM-DD HH:mm:ss（本地时间，UTC+8）
  // 根据问题描述，后端可能期望的是UTC+8时间戳（即把时间戳当作UTC+8来处理）
  // 所以需要将 "2025-11-01 00:00:00" (UTC+8) 转换为对应的UTC时间戳
  // 这样后端把这个UTC时间戳当作UTC+8时间戳使用时，就是正确的
  // 方法：将UTC+8时间字符串转换为UTC时间戳
  // 例如："2025-11-01 00:00:00" (UTC+8) -> UTC时间戳（对应UTC+8的2025-11-01 00:00:00）
  const utc8Timestamp = TimezoneHelper.utc8ToTimestamp(jumpDateTime.value);
  void loadByTime(utc8Timestamp);
}
/**
 * 禁用未来日期
 */
function disabledFutureDate(time: Date) {
  return time.getTime() > Date.now();
}
// 处理图表点击事件（用于绘制趋势线）
function handleChartClick(param: any) {
  if (
    activeDrawingTool.value !== "trendline" ||
    !chart.value ||
    !candleSeries.value
  ) {
    return;
  }
  // 获取点击位置的时间和价格
  let time: number | null = null;
  let price: number | null = null;
  // 方法1: 从 seriesData 中获取（如果点击在K线上）
  if (param.seriesData && param.seriesData.get(candleSeries.value)) {
    const candleData = param.seriesData.get(candleSeries.value);
    if (
      candleData &&
      candleData.time !== undefined &&
      candleData.close !== undefined
    ) {
      time = candleData.time as number;
      price = candleData.close as number;
    }
  }
  // 方法2: 从 param.time 获取时间，然后从数据中查找对应的价格
  if (time === null && param.time !== undefined) {
    time =
      typeof param.time === "number"
        ? param.time
        : ((param.time as any).timestamp ?? null);
    // 如果获取到了时间，从数据缓存中查找对应的K线数据
    if (time !== null && dataCache.value.length > 0) {
      // 查找最接近的K线
      let bar = dataCache.value.find((b) => b.time === time);
      if (!bar) {
        // 如果找不到精确匹配，找最接近的
        bar = dataCache.value.reduce(
          (closest, current) => {
            if (!closest) return current;
            const d1 = Math.abs(current.time - time!);
            const d2 = Math.abs(closest.time - time!);
            return d1 < d2 ? current : closest;
          },
          dataCache.value[0] as KLineDto | null,
        );
      }
      if (bar) {
        time = bar.time;
        if (price === null) {
          price = bar.close;
        }
      }
    }
  }
  // 方法3: 如果还是没有价格，尝试从 point 坐标转换（使用 timeScale）
  if (time !== null && price === null && param.point) {
    const timeScale = chart.value.timeScale();
    if (timeScale) {
      // 将像素坐标转换为逻辑坐标
      const logical = timeScale.coordinateToLogical(param.point.x);
      if (logical !== null) {
        // 将逻辑坐标转换为时间（如果还没有时间）
        if (time === null) {
          const timeValue = timeScale.logicalToTime(logical);
          if (timeValue !== null) {
            time = timeValue;
          }
        }
        // 如果有了时间，从数据中查找价格
        if (time !== null && dataCache.value.length > 0) {
          let bar = dataCache.value.find((b) => b.time === time);
          if (!bar) {
            bar = dataCache.value.reduce(
              (closest, current) => {
                if (!closest) return current;
                const d1 = Math.abs(current.time - time!);
                const d2 = Math.abs(closest.time - time!);
                return d1 < d2 ? current : closest;
              },
              dataCache.value[0] as KLineDto | null,
            );
          }
          if (bar) {
            price = bar.close;
          }
        }
        // 如果还是没有价格，尝试从 point.y 坐标估算价格
        // 使用价格轴的范围来估算
        if (price === null && dataCache.value.length > 0) {
          const prices = dataCache.value.map((b) => b.close);
          const minPrice = Math.min(...prices);
          const maxPrice = Math.max(...prices);
          const priceRange = maxPrice - minPrice;
          // 获取图表高度（估算）
          const chartHeight = containerHeight.value || 600;
          const yRatio = param.point.y / chartHeight;
          // 估算价格（从下往上，所以是 maxPrice - yRatio * priceRange）
          price = maxPrice - yRatio * priceRange;
        }
      }
    }
  }
  if (time === null || price === null) {
    console.warn("无法获取点击位置的时间和价格", param);
    return;
  }
  if (!isDrawingTrendLine.value || !trendLineStartPoint.value) {
    // 第一次点击：设置起点
    trendLineStartPoint.value = { time, price };
    isDrawingTrendLine.value = true;
    console.log("趋势线起点:", { time, price });
  } else {
    // 第二次点击：绘制趋势线
    const startPoint = trendLineStartPoint.value;
    const endPoint = { time, price };
    // 确保时间顺序是升序的（lightweight-charts 要求数据按时间升序排列）
    let point1 = startPoint;
    let point2 = endPoint;
    // 如果第一个点的时间大于第二个点，交换它们
    if (point1.time > point2.time) {
      point1 = endPoint;
      point2 = startPoint;
    }
    // 创建趋势线数据（两点之间的直线，确保时间升序）
    const trendLineData = [
      { time: point1.time, value: point1.price },
      { time: point2.time, value: point2.price },
    ];
    // 创建新的 LineSeries 来绘制趋势线
    const trendLine = chart.value.addSeries(LineSeries, {
      color: getCssVar("--mk-color-brand") || "#2962FF",
      lineWidth: 2,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false,
    });
    trendLine.setData(trendLineData);
    trendLineSeries.value.push(trendLine);
    console.log("趋势线已绘制:", { start: point1, end: point2 });
    // 重置状态，准备绘制下一条趋势线
    isDrawingTrendLine.value = false;
    trendLineStartPoint.value = null;
  }
}
// 取消绘制趋势线
function cancelTrendLineDrawing() {
  if (isDrawingTrendLine.value) {
    isDrawingTrendLine.value = false;
    trendLineStartPoint.value = null;
    console.log("已取消趋势线绘制");
  }
}
// 删除选中的趋势线
function deleteSelectedTrendLine() {
  if (selectedTrendLineIndex.value !== null && chart.value) {
    const index = selectedTrendLineIndex.value;
    const trendLine = trendLineSeries.value[index];
    if (trendLine) {
      // 从图表中移除趋势线
      chart.value.removeSeries(trendLine);
      // 从数组中移除
      trendLineSeries.value.splice(index, 1);
      // 清除选中状态
      selectedTrendLineIndex.value = null;
      console.log("已删除趋势线:", index);
    }
  } else if (trendLineSeries.value.length > 0) {
    // 如果没有选中，删除最后一条
    const lastIndex = trendLineSeries.value.length - 1;
    const trendLine = trendLineSeries.value[lastIndex];
    if (trendLine && chart.value) {
      chart.value.removeSeries(trendLine);
      trendLineSeries.value.splice(lastIndex, 1);
      console.log("已删除最后一条趋势线");
    }
  }
}
// 删除所有趋势线
function deleteAllTrendLines() {
  if (chart.value && trendLineSeries.value.length > 0) {
    trendLineSeries.value.forEach((trendLine) => {
      chart.value!.removeSeries(trendLine);
    });
    trendLineSeries.value = [];
    selectedTrendLineIndex.value = null;
    console.log("已删除所有趋势线");
  }
}
// 处理键盘事件
function handleKeyDown(event: KeyboardEvent) {
  if (
    signalTooltip.value.visible &&
    (event.ctrlKey || event.metaKey) &&
    String(event.key || "").toLowerCase() === "c"
  ) {
    event.preventDefault();
    void copyToClipboard(buildSignalTooltipCopyText());
    return;
  }
  // ESC键：取消绘制趋势线
  if (event.key === "Escape" || event.keyCode === 27) {
    if (isDrawingTrendLine.value) {
      cancelTrendLineDrawing();
      event.preventDefault();
    } else if (activeDrawingTool.value === "trendline") {
      // 如果正在趋势线模式但没有在绘制，切换到光标模式
      onDrawingToolChange("cursor");
      event.preventDefault();
    }
  }
  // Delete键：删除选中的趋势线
  if (event.key === "Delete" || event.keyCode === 46) {
    if (
      selectedTrendLineIndex.value !== null ||
      trendLineSeries.value.length > 0
    ) {
      deleteSelectedTrendLine();
      event.preventDefault();
    }
  }
}
// 绘图工具切换处理
function onDrawingToolChange(tool: string) {
  activeDrawingTool.value = tool;
  // 重置趋势线绘制状态
  if (tool !== "trendline") {
    isDrawingTrendLine.value = false;
    trendLineStartPoint.value = null;
    selectedTrendLineIndex.value = null;
  }
  if (tool === "cursor") {
    // 确保图表处于正常交互模式
    if (chart.value) {
      chart.value.applyOptions({
        handleScroll: {
          mouseWheel: true,
          pressedMouseMove: true,
          horzTouchDrag: true,
          vertTouchDrag: true,
        },
        handleScale: {
          axisPressedMouseMove: true,
          axisDoubleClickReset: true,
          axisTouchDrag: true,
          mouseWheel: true,
          pinch: true,
        },
      });
    }
  } else if (tool === "trendline") {
    // 趋势线模式：准备绘制
    isDrawingTrendLine.value = false;
    trendLineStartPoint.value = null;
    selectedTrendLineIndex.value = null;
  }
}
function onIntervalClick(value: string) {
  if (value === currentInterval.value) {
    return;
  }
  // 取消订阅旧周期
  const oldBackendInterval = convertIntervalToBackend(currentInterval.value);
  klineWebSocket.unsubscribe(selectedSymbol.value, oldBackendInterval);
  // 更新周期
  currentInterval.value = value;
  // 清空当前数据缓存
  dataCache.value = [];
  if (candleSeries.value) {
    candleSeries.value.setData([]);
  }
  // 重新加载K线数据（跳转到当前时间）
  jumpToNow();
  clearTrendline();
  // 订阅新周期
  const newBackendInterval = convertIntervalToBackend(currentInterval.value);
  klineWebSocket.subscribe(selectedSymbol.value, newBackendInterval);
  // 刷新缠论
  if (indicators.value.chanlun) nextTick(() => updateChanLun());
}
function onBottomIntervalClick(value: string) {
  currentBottomInterval.value = value;
  // TODO: 根据底部周期选择调整图表显示范围
}
/**
 * 查找最接近目标时间的K线时间点
 * @param targetTime - 目标时间戳（UTC，秒）
 * @param bars - K线数据数组
 * @returns 最接近的K线时间戳
 */
function findNearestKlineTime(targetTime: number, bars: KLineDto[]): number {
  if (!bars || bars.length === 0) {
    return targetTime;
  }
  // 如果目标时间在数据范围外，返回边界值
  const firstTime = bars[0].time;
  const lastTime = bars[bars.length - 1].time;
  if (targetTime < firstTime) {
    return firstTime;
  }
  if (targetTime > lastTime) {
    return lastTime;
  }
  // 二分查找最接近的K线时间点
  let left = 0;
  let right = bars.length - 1;
  let closestTime = firstTime;
  let minDiff = Math.abs(targetTime - firstTime);
  while (left <= right) {
    const mid = Math.floor((left + right) / 2);
    const midTime = bars[mid].time;
    const diff = Math.abs(targetTime - midTime);
    // 如果找到精确匹配，直接返回
    if (midTime === targetTime) {
      return targetTime;
    }
    // 更新最接近的时间点
    if (diff < minDiff) {
      minDiff = diff;
      closestTime = midTime;
    }
    // 继续查找
    if (targetTime < midTime) {
      right = mid - 1;
    } else {
      left = mid + 1;
    }
  }
  // 检查左右相邻的时间点，选择更接近的
  const closestIndex = bars.findIndex((item) => item.time === closestTime);
  if (closestIndex >= 0) {
    // 检查前一个时间点
    if (closestIndex > 0) {
      const prevTime = bars[closestIndex - 1].time;
      const prevDiff = Math.abs(targetTime - prevTime);
      if (prevDiff < minDiff) {
        closestTime = prevTime;
      }
    }
    // 检查后一个时间点
    if (closestIndex < bars.length - 1) {
      const nextTime = bars[closestIndex + 1].time;
      const nextDiff = Math.abs(targetTime - nextTime);
      if (nextDiff < minDiff) {
        closestTime = nextTime;
      }
    }
  }
  return closestTime;
}
/**
 * 显示闪烁提示标记（参考 MarketData 的实现）
 * @param targetTime - 目标时间戳（UTC，秒）
 * @param duration - 闪烁持续时间（毫秒），默认3000
 */
function showFlashMarker(targetTime: number, duration = 3000) {
  if (!candleSeries.value) {
    return;
  }
  // 清除之前的闪烁标记
  if (flashMarkerTimer) {
    clearInterval(flashMarkerTimer);
    flashMarkerTimer = null;
  }
  if (flashMarkerTimeout) {
    clearTimeout(flashMarkerTimeout);
    flashMarkerTimeout = null;
  }
  const markersApi = seriesMarkersRef.value;
  if (!markersApi) return;
  const existingMarkers = [...(markersApi.markers() || [])];
  const flashMarker = {
    time: targetTime,
    position: "inBar" as const, // 在K线内部
    color: "#FFD700", // 金色
    shape: "circle" as const,
    text: "目标", // 显示文本
    size: 2, // 稍大一些，更明显
  };
  // 使用官方推荐的 setMarkers() 方法设置标记
  const allMarkers = [...existingMarkers, flashMarker];
  allMarkers.sort((a, b) => a.time - b.time);
  markersApi.setMarkers(allMarkers);
  let flashCount = 0;
  const maxFlashCount = Math.floor(duration / 200);
  flashMarkerTimer = window.setInterval(() => {
    flashCount++;
    const isGold = flashCount % 2 === 0;
    flashMarker.color = isGold ? "#FFD700" : "#FF8C00";
    const currentMarkers = [...(seriesMarkersRef.value?.markers() || [])];
    const updatedMarkers = currentMarkers.map((m) => {
      // 通过时间戳和位置识别闪烁标记
      if (m.time === targetTime && m.position === "inBar") {
        return flashMarker;
      }
      return m;
    });
    // 如果标记不在现有标记中，添加它
    const hasMarker = updatedMarkers.some(
      (m) => m.time === targetTime && m.position === "inBar",
    );
    if (!hasMarker) {
      updatedMarkers.push(flashMarker);
    }
    updatedMarkers.sort((a, b) => a.time - b.time);
    seriesMarkersRef.value?.setMarkers(updatedMarkers);
    // 达到最大闪烁次数，停止
    if (flashCount >= maxFlashCount) {
      if (flashMarkerTimer) {
        clearInterval(flashMarkerTimer);
        flashMarkerTimer = null;
      }
      // 延迟移除标记（让用户看到最后一次闪烁）
      flashMarkerTimeout = window.setTimeout(() => {
        removeFlashMarker(targetTime);
      }, 200);
    }
  }, 200); // 每200ms闪烁一次
}
/**
 * 移除闪烁标记
 * @param targetTime - 目标时间戳（UTC，秒）
 */
function removeFlashMarker(targetTime: number) {
  const markersApi = seriesMarkersRef.value;
  if (!markersApi) return;
  const existingMarkers = markersApi.markers() || [];
  const filteredMarkers = existingMarkers.filter((marker) => {
    return !(
      marker.time === targetTime &&
      marker.position === "inBar" &&
      (marker.color === "#FFD700" || marker.color === "#FF8C00")
    );
  });
  markersApi.setMarkers([...filteredMarkers]);
}
/**
 * 根据可见范围加载信号标注
 */
async function loadSignalsForVisibleRange() {
  if (!chart.value || !candleSeries.value || dataCache.value.length === 0) {
    return;
  }
  if (!showApiSignalsOverlay.value) {
    // 开关关闭时不加载新信号，但保留指标标记与闪烁/订单标记
    updateSignalMarkers();
    return;
  }
  if (isLoadingSignals.value) {
    return;
  }
  // 防抖处理
  if (signalLoadDebounceTimer) {
    clearTimeout(signalLoadDebounceTimer);
  }
  signalLoadDebounceTimer = window.setTimeout(async () => {
    try {
      isLoadingSignals.value = true;
      // 获取可见的逻辑索引范围
      const timeScale = chart.value!.timeScale();
      const visibleLogicalRange = timeScale.getVisibleLogicalRange();
      if (!visibleLogicalRange) {
        return;
      }
      // 将逻辑索引转换为数据索引
      const fromIndex = Math.max(
        0,
        Math.floor(visibleLogicalRange.from as number),
      );
      const toIndex = Math.min(
        dataCache.value.length - 1,
        Math.ceil(visibleLogicalRange.to as number),
      );
      if (fromIndex >= toIndex) {
        return;
      }
      const fromTime = dataCache.value[fromIndex].time;
      const toTime = dataCache.value[toIndex].time;
      // 添加缓冲区（前后各30分钟）
      const bufferTime = 1800; // 30分钟
      const fromTimeWithBuffer = fromTime - bufferTime;
      const toTimeWithBuffer = toTime + bufferTime;
      // 检查缓存中是否已有该时间范围的信号
      const cachedSignals = signalsCache.value.filter(
        (s) => s.time >= fromTimeWithBuffer && s.time <= toTimeWithBuffer,
      );
      // 如果缓存已覆盖该范围，直接使用缓存
      if (cachedSignals.length > 0) {
        const cacheFrom = Math.min(...signalsCache.value.map((s) => s.time));
        const cacheTo = Math.max(...signalsCache.value.map((s) => s.time));
        if (cacheFrom <= fromTimeWithBuffer && cacheTo >= toTimeWithBuffer) {
          updateSignalMarkers();
          return;
        }
      }
      // 加载信号数据
      const selectedBot = robots.value.find(
        (b: any) => (b?.botId || b?.id) === selectedBotId.value,
      );
      const response = await loadKLineSignals({
        symbol: selectedSymbol.value,
        interval: convertIntervalToBackend(currentInterval.value),
        exchange: selectedExchange.value,
        from: fromTimeWithBuffer,
        to: toTimeWithBuffer,
        indicator: selectedBot?.strategyType || undefined,
        robotId: selectedBotId.value || undefined,
        limit: 200,
      });
      const responseData = response?.data || {};
      const rawSignals: any[] = responseData?.signals || [];
      // 确保信号数据包含必要的字段，并处理可能的null值
      // 注意：后端返回的time可能是秒级或毫秒级时间戳，需要统一转换为秒级
      const newSignals: KLineSignal[] = rawSignals.map((s) => {
        // 统一转换为秒级时间戳
        let timeInSeconds = s.time;
        if (timeInSeconds > 1e12) {
          // 如果是毫秒级时间戳（大于1e12），转换为秒级
          timeInSeconds = Math.floor(timeInSeconds / 1000);
        }
        return {
          id: s.id,
          time: timeInSeconds,
          signalType: s.signalType || null, // 可能为null
          description: s.description || "",
          price: s.price || null, // 可能为null
          signalStrength: s.signalStrength || null,
          signalSource: s.signalSource || null,
          robotId: s.robotId || null,
          orderSn: s.orderSn || null,
          status: s.status || null,
          entryType: s.entryType || null,
          limitPrice: s.limitPrice || null,
          extraParams: s.extraParams || null,
          marketTrend: s.marketTrend || null,
        };
      });
      if (newSignals.length > 0) {
        // 合并到缓存中（去重）
        const existingKeys = new Set(
          signalsCache.value.map((s) => `${s.id}_${s.time}`),
        );
        const uniqueNewSignals = newSignals.filter(
          (s) => !existingKeys.has(`${s.id}_${s.time}`),
        );
        signalsCache.value.push(...uniqueNewSignals);
        // 按时间排序
        signalsCache.value.sort((a, b) => a.time - b.time);
        // 限制缓存大小（最多保留1000个信号）
        if (signalsCache.value.length > 1000) {
          signalsCache.value = signalsCache.value.slice(-1000);
        }
      }
      // 更新markers
      updateSignalMarkers();
    } catch (error) {
      console.error("加载信号标注失败:", error);
    } finally {
      isLoadingSignals.value = false;
    }
  }, SIGNAL_LOAD_DEBOUNCE);
}
/**
 * 将信号转换为markers并显示到K线图上
 */
function updateSignalMarkers() {
  if (!candleSeries.value || dataCache.value.length === 0) {
    return;
  }
  // 获取可见的逻辑索引范围
  const timeScale = chart.value!.timeScale();
  const visibleLogicalRange = timeScale.getVisibleLogicalRange();
  if (!visibleLogicalRange) {
    return;
  }
  // 将逻辑索引转换为数据索引
  const fromIndex = Math.max(0, Math.floor(visibleLogicalRange.from as number));
  const toIndex = Math.min(
    dataCache.value.length - 1,
    Math.ceil(visibleLogicalRange.to as number),
  );
  if (fromIndex >= toIndex) {
    return;
  }
  const fromTime = dataCache.value[fromIndex].time;
  const toTime = dataCache.value[toIndex].time;
  // 过滤可见范围内的信号（允许一定的时间容差，因为K线时间可能不完全匹配）
  const timeTolerance = 180; // 3分钟容差
  const visibleSignals = signalsCache.value.filter((s) => {
    // 如果选择了机器人，只显示该机器人的信号
    if (selectedBotId.value && s.robotId !== selectedBotId.value) {
      return false;
    }
    // 有效信号过滤 - 只显示信号强度大于0的信号
    if (filterValidSignals.value && (!s.signalStrength || Number(s.signalStrength) <= 0)) {
      return false;
    }
    return (
      s.time >= fromTime - timeTolerance && s.time <= toTime + timeTolerance
    );
  });
  const markersApi = seriesMarkersRef.value;
  if (!markersApi) return;
  const existingMarkers = markersApi.markers() || [];
  const flashMarkers = existingMarkers.filter(
    (m) => m.color === "#FFD700" || m.color === "#FF8C00",
  );
  // 将信号转换为markers
  const signalMarkers = visibleSignals
    .map((signal) => {
      // 根据信号类型设置颜色和形状
      let color = "#26a69a"; // 默认绿色
      let shape: "circle" | "square" | "arrowUp" | "arrowDown" = "circle";
      let position: "aboveBar" | "belowBar" | "inBar" = "inBar";
      // 信号类型由后端 signal.signalType 提供，如 LONG/SHORT/CLOSE_LONG/CLOSE_SHORT/LBAP/SBAP/LBSP/SBSP
      let signalType = signal.signalType;
      switch (signalType) {
        case "LONG": // 多头开仓
          color = "#00ff00";
          shape = "arrowUp";
          position = "belowBar";
          break;
        case "SHORT": // 空头开仓
          color = "#ff0000";
          shape = "arrowDown";
          position = "aboveBar";
          break;
        case "CLOSE_LONG": // 多头平仓
          color = "#00ff00";
          shape = "square";
          position = "aboveBar";
          break;
        case "CLOSE_SHORT": // 空头平仓
          color = "#ff0000";
          shape = "square";
          position = "belowBar";
          break;
        case "LBAP": // 加多仓
          color = "#00ff00";
          shape = "circle";
          position = "belowBar";
          break;
        case "SBAP": // 加空仓
          color = "#ff0000";
          shape = "circle";
          position = "aboveBar";
          break;
        case "LBSP": // 减多仓
          color = "#00ff00";
          shape = "diamond";
          position = "aboveBar";
          break;
        case "SBSP": // 减空仓
          color = "#ff0000";
          shape = "diamond";
          position = "belowBar";
          break;
        default:
          color = "#787b86";
          shape = "circle";
          position = "inBar";
      }
      // 确保time是数字类型（lightweight-charts要求），并对齐到当前周期K线开始时间
      let markerTime =
        typeof signal.time === "number" ? signal.time : Number(signal.time);
      markerTime = alignToCandleStart(markerTime, currentInterval.value);
      // 如果time无效，跳过这个信号
      if (!markerTime || isNaN(markerTime)) {
        return null;
      }
      const marker = {
        time: markerTime as any, // lightweight-charts接受number类型的时间戳（秒）
        position: position,
        color: color,
        shape: shape,
        text: formatSignalDescriptionWeightOnly(signal.description || signalType || "信号"),
        size: 1,
        id: "api-signal",
        meta: {
          kind: "api-signal",
          title: signalType ? formatSignalTypeLabel(signalType) : "信号",
          description: formatSignalDescriptionWeightOnly(signal.description || ""),
          signalType: signalType || null,
          signalStrength: signal.signalStrength ?? null,
          signalSource: signal.signalSource ?? null,
          robotId: signal.robotId ?? null,
          orderSn: signal.orderSn ?? null,
          status: signal.status ?? null,
          entryType: signal.entryType ?? null,
          limitPrice: signal.limitPrice ?? null,
          price: signal.price ?? null,
          extraParams: signal.extraParams ?? null,
          marketTrend: signal.marketTrend ?? null,
          criticalLevels: signal.criticalLevels ?? null,
        },
      };
      return marker;
    })
    .filter((m) => m !== null) as any[]; // 过滤掉null值
  // 保留指标类标记（安第斯多/空、反转R/C、流动性BUY/SELL、趋势强度▲▼X、RangeFilter 等），再合并 API 信号与闪烁标记
  const isIndicatorMarker = (m: any) =>
    m.id !== "api-signal" &&
    (m.text === "多" ||
    m.text === "空" ||
    m.text === "R" ||
    m.text === "C" ||
    m.text === "BUY" ||
    m.text === "SELL" ||
    isTrendStrengthMarker(m) ||
    rangeFilterMarkersRef.value.some(
      (r) => r.time === m.time && r.text === m.text,
    ));
  const preservedIndicatorMarkers = existingMarkers.filter(isIndicatorMarker);
  const allMarkers = showApiSignalsOverlay.value
    ? [...preservedIndicatorMarkers, ...signalMarkers, ...flashMarkers]
    : [...preservedIndicatorMarkers, ...flashMarkers];
  // 按时间排序；同时间时确保技术指标排在前面，业务信号排在后面
  allMarkers.sort((a, b) => {
    if (a.time !== b.time) return a.time - b.time;
    // 同时间：让技术指标（非api-signal）排在前面，业务信号排在后面
    const aIsApi = a.id === "api-signal";
    const bIsApi = b.id === "api-signal";
    if (aIsApi && !bIsApi) return 1;
    if (!aIsApi && bIsApi) return -1;
    return 0;
  });
  try {
    markersApi.setMarkers(allMarkers);
    if (indicators.value.trendStrength) {
      updateTrendStrengthIndicator();
    }
    if (indicators.value.reversal) {
      updateReversalConfirmation();
    }
  } catch (error) {
    console.error("设置markers失败:", error);
  }
}
// 处理 WebSocket K 线更新
function handleKLineUpdate(message: KLineUpdateMessage) {
  if (!candleSeries.value || !message.data) {
    console.warn("handleKLineUpdate: candleSeries 或 message.data 为空", {
      hasCandleSeries: !!candleSeries.value,
      hasData: !!message.data,
    });
    return;
  }
  // 更新最新K线信息显示
  if (dataCache.value.length > 0 && !focusedKlineData.value) {
    const latest = dataCache.value[dataCache.value.length - 1];
    focusedKlineData.value = {
      time: latest.time,
      open: latest.open,
      high: latest.high,
      low: latest.low,
      close: latest.close,
    };
  }
  // 只处理当前交易对和时间周期的数据
  // 后端推送的 interval 是枚举格式（如 OKXMIN3），需要转换比较
  const backendInterval = convertIntervalToBackend(currentInterval.value);
  if (
    message.symbol !== selectedSymbol.value ||
    message.interval !== backendInterval
  ) {
    console.debug("handleKLineUpdate: 跳过不匹配的消息", {
      messageSymbol: message.symbol,
      selectedSymbol: selectedSymbol.value,
      messageInterval: message.interval,
      backendInterval: backendInterval,
    });
    return;
  }
  const updateData = message.data;
  // 确保 time 是数字类型（秒级时间戳）
  const klineTime =
    typeof updateData.time === "number"
      ? updateData.time
      : typeof updateData.time === "string"
        ? parseInt(updateData.time, 10)
        : Number(updateData.time);
  // 验证 time 是否为有效数字
  if (isNaN(klineTime) || klineTime <= 0) {
    console.error("handleKLineUpdate: 无效的时间戳", updateData.time);
    return;
  }
  console.debug("handleKLineUpdate: 收到K线更新", {
    symbol: message.symbol,
    interval: message.interval,
    time: klineTime,
    open: updateData.open,
    high: updateData.high,
    low: updateData.low,
    close: updateData.close,
  });
  // 转换为 lightweight-charts 格式
  // 确保 time 是数字类型（秒级时间戳）
  const klineBar = {
    time: klineTime as Time, // lightweight-charts 需要数字类型的时间戳
    open: Number(updateData.open),
    high: Number(updateData.high),
    low: Number(updateData.low),
    close: Number(updateData.close),
  };
  // 验证数据有效性
  if (
    isNaN(klineBar.time) ||
    isNaN(klineBar.open) ||
    isNaN(klineBar.high) ||
    isNaN(klineBar.low) ||
    isNaN(klineBar.close)
  ) {
    console.error("handleKLineUpdate: 无效的K线数据", klineBar, updateData);
    return;
  }
  // 如果正在加载历史数据，则不应该更新数据，避免改变 anchorTime
  // 因为这会改变 anchorTime，导致加载历史数据时使用错误的时间点
  if (isLoadingMore) {
    return;
  }

  const lastBar = dataCache.value.length > 0 ? dataCache.value[dataCache.value.length - 1] : null;
  const lastBarTime = lastBar ? (typeof lastBar.time === "number" ? lastBar.time : Number(lastBar.time)) : null;

  if (lastBar && lastBarTime === klineTime) {
    lastBar.open = klineBar.open;
    lastBar.high = klineBar.high;
    lastBar.low = klineBar.low;
    lastBar.close = klineBar.close;
    try {
      candleSeries.value.update(klineBar);
    } catch (error) {
      console.error("更新K线失败:", error, { klineBar, lastBar });
      if (dataCache.value.length > 0) {
        const sortedData = [...dataCache.value].sort((a, b) => {
          const timeA = typeof a.time === "number" ? a.time : Number(a.time);
          const timeB = typeof b.time === "number" ? b.time : Number(b.time);
          return timeA - timeB;
        });
        candleSeries.value.setData(sortedData);
      }
    }
    lastPrice.value = klineBar.close;
    if (dataCache.value.length > 1) {
      prevPrice.value = dataCache.value[dataCache.value.length - 2].close;
    }
    updateCurrentPriceLine();
    const focusedTime = focusedKlineData.value?.time
      ? typeof focusedKlineData.value.time === "number"
        ? focusedKlineData.value.time
        : Number(focusedKlineData.value.time)
      : null;
    if (focusedKlineData.value && focusedTime === klineTime) {
      focusedKlineData.value = {
        time: klineBar.time,
        open: klineBar.open,
        high: klineBar.high,
        low: klineBar.low,
        close: klineBar.close,
      };
    }
    if (dataCache.value.length >= macdConfig.value.slowPeriod && indicators.value.macd) {
      updateMACD();
    }
    if (dataCache.value.length >= bollConfig.value.period && indicators.value.boll) {
      updateBoll();
    }
    if (indicators.value.ma) updateMA();
    if (indicators.value.rangeFilter) updateRangeFilter();
    if (indicators.value.rsi) updateRSI();
    if (indicators.value.smcLite) updateSmcLite();
    if (indicators.value.reversal) updateReversalConfirmation();
    if (indicators.value.tsm) updateTSM();
    if (indicators.value.andeanOscillator) updateAndeanOscillator();
  } else {
    if (lastBar) {
      candleSeries.value.update(lastBar);
    }
    dataCache.value.push(klineBar);
    if (dataCache.value.length > MAX_CACHE_SIZE) {
      dataCache.value = dataCache.value.slice(-MAX_CACHE_SIZE);
    }
    candleSeries.value.update(klineBar);
    prevPrice.value = lastBar ? lastBar.close : klineBar.close;
    lastPrice.value = klineBar.close;
    updateCurrentPriceLine();
    if (dataCache.value.length >= macdConfig.value.slowPeriod && indicators.value.macd) {
      updateMACD();
    }
    if (dataCache.value.length >= bollConfig.value.period && indicators.value.boll) {
      updateBoll();
    }
    if (indicators.value.ma) updateMA();
    if (indicators.value.rangeFilter) updateRangeFilter();
    if (indicators.value.rsi) updateRSI();
    if (indicators.value.smcLite) updateSmcLite();
    if (indicators.value.reversal) updateReversalConfirmation();
    if (indicators.value.tsm) updateTSM();
    if (indicators.value.andeanOscillator) updateAndeanOscillator();
    if (isRealtimeMode.value && chart.value) {
      try {
        chart.value.timeScale().scrollToRealTime();
      } catch (error) {
        const timeScale = chart.value.timeScale();
        const visibleRange = timeScale.getVisibleRange();
        if (visibleRange) {
          const totalBars = dataCache.value.length;
          timeScale.setVisibleLogicalRange({
            from: Math.max(0, totalBars - 300),
            to: totalBars,
          });
        }
      }
    }
  }
}
// 更新最新价格线和价格轴标签
function updateCurrentPriceLine() {
  if (
    !candleSeries.value ||
    lastPrice.value === null ||
    lastPrice.value === undefined
  ) {
    priceAxisLabel.value = null;
    return;
  }
  // 确定价格线颜色（根据涨跌）
  let priceLineColor = getCssVar("--mk-text-secondary") || "var(--text-secondary)"; // 默认灰色
  if (prevPrice.value !== null && lastPrice.value !== null) {
    if (lastPrice.value > prevPrice.value) {
      priceLineColor = getCssVar("--mk-color-up") || "var(--accent-green)"; // 上涨绿色
    } else if (lastPrice.value < prevPrice.value) {
      priceLineColor = getCssVar("--mk-color-down") || "var(--accent-red)"; // 下跌红色
    }
  } else if (dataCache.value.length > 0) {
    // 如果没有前一个价格，使用最新K线的开收盘价判断
    const lastBar = dataCache.value[dataCache.value.length - 1];
    if (lastBar && lastBar.close >= lastBar.open) {
      priceLineColor = getCssVar("--mk-color-up") || "var(--accent-green)";
    } else if (lastBar) {
      priceLineColor = getCssVar("--mk-color-down") || "var(--accent-red)";
    }
  }
  try {
    if (currentPriceLineRef.value) {
      currentPriceLineRef.value.applyOptions({
        price: lastPrice.value,
        color: priceLineColor,
      });
    } else {
      currentPriceLineRef.value = candleSeries.value.createPriceLine({
        price: lastPrice.value,
        color: priceLineColor,
        lineWidth: 1,
        lineStyle: 2,
        axisLabelVisible: false,
      });
    }
    // 更新价格轴标签
    if (priceAxisLabel.value) {
      priceAxisLabel.value.price = formatPrice(lastPrice.value);
      priceAxisLabel.value.color = priceLineColor;
    } else {
      const coordinate = candleSeries.value.priceToCoordinate(lastPrice.value);
      const relativeTop = coordinate !== null ? coordinate + 6 : 0;
      priceAxisLabel.value = {
        top: relativeTop,
        price: formatPrice(lastPrice.value),
        color: priceLineColor,
      };
    }
  } catch (error) {
    console.error("更新价格线失败:", error);
    priceAxisLabel.value = null;
  }
}
// ========== 回测相关函数 ==========
/**
 * 运行回测
 */
async function runBacktest() {
  const symbols =
    backtestParams.value.symbols && backtestParams.value.symbols.length > 0
      ? backtestParams.value.symbols
      : selectedSymbol.value
        ? [selectedSymbol.value]
        : [];
  if (symbols.length === 0) {
    ElMessage.error("请选择交易标的");
    return;
  }
  const hasTimeRange = Boolean(
    backtestParams.value.startDate && backtestParams.value.endDate,
  );
  if (hasTimeRange) {
    const startTime = new Date(backtestParams.value.startDate).getTime();
    const endTime = new Date(backtestParams.value.endDate).getTime();
    if (Number.isNaN(startTime) || Number.isNaN(endTime)) {
      ElMessage.error("时间格式无效");
      return;
    }
    if (startTime >= endTime) {
      ElMessage.error("结束时间必须大于开始时间");
      return;
    }
  } else if (
    !backtestParams.value.backtestDays ||
    backtestParams.value.backtestDays <= 0
  ) {
    ElMessage.error("请设置回测天数或选择时间范围");
    return;
  }
  backtestRunning.value = true;
  backtestProgress.value = 0;
  backtestMessage.value = "";
  backtestLogs.value = [];
  backtestResults.value = null;
  equityProgress.value = 0;
  equityCurvePoints.value = [];
  currentBacktestId.value = null;
  lastBacktestStatus.value = "";
  lastBacktestMessage.value = "";
  lastBacktestProgress.value = 0;
  try {
    const params: any = {
      dataSourceType: "COIN_GECKO",
      coinId: symbols[0],
      days: backtestParams.value.backtestDays,
      isContractTrading: true,
      commissionRate:
        backtestParams.value.commission != null
          ? backtestParams.value.commission
          : 0.045,
      slippageRate:
        backtestParams.value.slippage != null
          ? backtestParams.value.slippage
          : 0.0,
      robotId: backtestParams.value.botId || undefined,
    };
    const selectedBot = robots.value.find(
      (b: any) => (b?.botId || b?.id) === backtestParams.value.botId,
    );
    params.strategyType =
      selectedBot?.strategyType ||
      selectedBot?.strategyName ||
      selectedBot?.strategyCode ||
      undefined;
    if (hasTimeRange) {
      params.startTime = new Date(backtestParams.value.startDate).getTime();
      params.endTime = new Date(backtestParams.value.endDate).getTime();
    }
    const selectedBacktestType =
      backtestParams.value.backtestType || "TRADITIONAL_BACKTEST_NEW";
    params.backtestType = selectedBacktestType;
    const backtestTypeLabel =
      selectedBacktestType === "PAPER_TRADING" ? "模拟实盘" : "V2引擎";
    addBacktestLog(
      "info",
      `=== 开始${backtestTypeLabel}异步回测任务 ===`,
    );
    addBacktestLog("info", "开始创建异步回测任务...");
    const response: any = await runAsyncBacktest(params);
    if (response && response.success && response.taskId) {
      currentBacktestId.value = response.taskId;
      addBacktestLog("info", `异步回测任务已创建，任务ID: ${response.taskId}`);
      pollBacktestProgress();
    } else {
      throw new Error(response?.message || "回测启动失败");
    }
  } catch (error: any) {
    console.error("回测执行失败:", error);
    ElMessage.error("回测执行失败: " + (error.message || "请重试"));
    addBacktestLog("error", `异步请求失败: ${error.message || "请重试"}`);
    backtestRunning.value = false;
    backtestProgress.value = 0;
  }
}
/**
 * 停止回测
 */
async function stopBacktestFn() {
  if (currentBacktestId.value) {
    try {
      await stopAsyncBacktest(currentBacktestId.value);
      addBacktestLog("warning", "已发送停止回测请求");
    } catch (e) {
      console.error("停止回测失败:", e);
    }
  }
  backtestRunning.value = false;
  backtestProgress.value = 0;
  backtestMessage.value = "";
  currentBacktestId.value = null;
  if (backtestPollTimer) {
    clearTimeout(backtestPollTimer);
    backtestPollTimer = null;
  }
}
/**
 * 轮询回测进度
 */
async function pollBacktestProgress() {
  if (!currentBacktestId.value || !backtestRunning.value) {
    return;
  }
  try {
    const response: any = await getBacktestTaskStatus(currentBacktestId.value);
    if (response && response.success) {
      const previousStatus = lastBacktestStatus.value;
      const progressValue = Number(response.progress || 0);
      backtestProgress.value = progressValue;
      backtestMessage.value = response.message || "";
      if (response.status && response.status !== previousStatus) {
        addBacktestLog(
          "info",
          `任务状态更新: ${response.status}${response.message ? ` - ${response.message}` : ""}`,
        );
        lastBacktestStatus.value = response.status;
      }
      if (response.message && response.message !== lastBacktestMessage.value) {
        lastBacktestMessage.value = response.message;
      }
      lastBacktestProgress.value = progressValue;
      if (response.status === "COMPLETED") {
        backtestRunning.value = false;
        backtestProgress.value = 100;
        addBacktestLog("success", "回测任务执行完成");
        await getBacktestResults();
      } else if (response.status === "FAILED") {
        backtestRunning.value = false;
        ElMessage.error(
          "回测执行失败: " + (response.errorMessage || "未知错误"),
        );
        addBacktestLog(
          "error",
          `回测执行失败: ${response.errorMessage || "未知错误"}`,
        );
      } else {
        backtestPollTimer = window.setTimeout(() => {
          pollBacktestProgress();
        }, 3000);
      }
    } else {
      backtestPollTimer = window.setTimeout(() => {
        pollBacktestProgress();
      }, 5000);
    }
  } catch (error) {
    console.error("获取回测进度失败:", error);
    addBacktestLog("error", "获取回测进度失败");
    backtestPollTimer = window.setTimeout(() => {
      pollBacktestProgress();
    }, 5000);
  }
}
/**
 * 获取回测结果
 */
async function getBacktestResults() {
  if (!currentBacktestId.value) {
    return;
  }
  try {
    const response: any = await getAsyncBacktestResult(currentBacktestId.value);
    if (response && response.success) {
      const normalized = normalizeBacktestResult(response);
      backtestResults.value = normalized;
      updateEquityProgress();
      await loadBacktestEquityCurve(currentBacktestId.value);
      ElMessage.success("回测完成");
      const payload = response?.result || response?.data || response;
      const tradeCount =
        payload?.totalTrades ??
        backtestResults.value?.totalTrades ??
        (Array.isArray(payload?.trades) ? payload.trades.length : 0) ??
        (Array.isArray(payload?.tradeRecords) ? payload.tradeRecords.length : 0) ??
        0;
      addBacktestLog(
        "success",
        `回测结果已加载，包含 ${tradeCount} 笔交易记录`,
      );
      await loadBacktestReport(currentBacktestId.value);
    } else {
      throw new Error(response?.message || "获取回测结果失败");
    }
  } catch (error) {
    console.error("获取回测结果失败:", error);
    ElMessage.error(
      "获取回测结果失败: " + ((error as any).message || "请重试"),
    );
    addBacktestLog(
      "error",
      `获取回测结果失败: ${(error as any).message || "请重试"}`,
    );
  }
}
async function loadBacktestReport(taskId: string) {
  try {
    addBacktestLog("info", "正在获取回测报告...");
    const response: any = await getBacktestReport(taskId);
    if (response && response.success && response.report) {
      addBacktestLog("success", "回测报告已加载");
      return;
    }
    addBacktestLog("info", "报告不存在，正在生成新报告...");
    await generateBacktestReportForTask(taskId);
  } catch (error) {
    addBacktestLog(
      "error",
      `获取报告失败: ${(error as any).message || "请重试"}`,
    );
    await generateBacktestReportForTask(taskId);
  }
}
async function generateBacktestReportForTask(taskId: string) {
  try {
    addBacktestLog("info", "正在生成回测报告...");
    const response: any = await generateBacktestReport(taskId);
    if (response && response.success && response.report) {
      addBacktestLog("success", "回测报告生成成功");
    } else {
      addBacktestLog(
        "error",
        `生成报告失败: ${response?.message || "未知错误"}`,
      );
    }
  } catch (error) {
    addBacktestLog(
      "error",
      `生成报告失败: ${(error as any).message || "请重试"}`,
    );
  }
}
const connectBacktestWebSocket = () => {
  const socket = new SockJS("/ws");
  backtestStompClient.value = Stomp.over(socket);
  backtestStompClient.value.connect(
    {},
    () => {
      addBacktestLog("info", "WebSocket连接成功");
      backtestStompClient.value.subscribe(
        "/topic/backtest/*",
        (message: any) => {
          const notification = JSON.parse(message.body);
          handleBacktestWebSocketMessage(notification);
        },
      );
    },
    (error: any) => {
      addBacktestLog("error", `WebSocket连接失败: ${error}`);
    },
  );
};
const disconnectBacktestWebSocket = () => {
  if (backtestStompClient.value) {
    backtestStompClient.value.disconnect();
  }
};
const handleBacktestWebSocketMessage = (notification: any) => {
  const status = notification?.status;
  const message = notification?.message ?? "undefined";
  addBacktestLog("info", `WebSocket通知: ${status} - ${message}`);
  if (
    currentBacktestId.value &&
    notification?.taskId === currentBacktestId.value
  ) {
    backtestMessage.value = notification?.message || "";
    backtestProgress.value = notification?.progress || backtestProgress.value;
  }
};
/**
 * 加载回测记录
 */
async function loadBacktestRecords() {
  backtestRecordsLoading.value = true;
  try {
    const response: any = await getBacktestRecords({
      strategyId: selectedRecordsStrategy.value || undefined,
      page: 1,
      size: 100,
    });
    if (response && response.success && response.data) {
      backtestRecords.value = response.data.records || response.data;
    }
  } catch (error) {
    console.error("加载回测记录失败:", error);
    ElMessage.error("加载回测记录失败");
  } finally {
    backtestRecordsLoading.value = false;
  }
}
/**
 * 查看回测记录
 */
function viewBacktestRecord(record: any) {
  const recordId = record?.backtestId || record?.id;
  if (!recordId) {
    ElMessage.error("回测记录ID无效");
    return;
  }
  currentBacktestId.value = recordId;
  backtestSubTab.value = "run";
  backtestResults.value = normalizeBacktestResult(record);
  updateEquityProgress();
  equityCurvePoints.value = parseEquityCurvePoints(
    record?.equityCurve || record?.equityCurvePoints,
  );
  getBacktestResults();
}
/**
 * 删除回测记录
 */
async function deleteBacktestRecord(recordId: string) {
  try {
    const response: any = await deleteBacktestRecordAPI(recordId);
    if (response && response.success) {
      ElMessage.success("删除成功");
      await loadBacktestRecords();
    } else {
      ElMessage.error(response?.message || "删除失败");
    }
  } catch (error) {
    console.error("删除回测记录失败:", error);
    ElMessage.error("删除失败");
  }
}
/**
 * 加载策略列表
 */
async function loadStrategies() {
  try {
    strategies.value = await getAllStrategyTypes();
  } catch (error) {
    console.error("加载策略列表失败:", error);
  }
}
async function loadBacktestTypes() {
  try {
    const types = await getBacktestTypes();
    supportedBacktestTypes.value = Array.isArray(types) ? types : [];
  } catch (error) {
    console.error("加载回测类型失败:", error);
    supportedBacktestTypes.value = [];
  }
}
async function loadBots() {
  try {
    const [botResponse, purchaseResponse] = await Promise.all([
      getTradingBots({ page: 1, limit: 1000 }),
      getMyPurchases(),
    ]);

    let list: any[] = [];
    if (botResponse && botResponse.success) {
      list = botResponse.data?.records || botResponse.records || [];
    }

    // 合并从市场购买的机器人
    if (purchaseResponse && purchaseResponse.data) {
      const purchased = (Array.isArray(purchaseResponse.data)
        ? purchaseResponse.data
        : []
      )
        .filter((p: any) => p.listingId)
        .map((p: any) => ({
          id: 0,
          botId: `purchased_${p.listingId}`,
          botName: p.botName || "-",
          userId: p.userId || "-",
          accountId: p.authorId || "-",
          strategyId: "-",
          tradingPair: "-",
          allocatedCapital: 0,
          currentCapital: 0,
          status: "PURCHASED",
          startTime: p.purchaseTime || "",
          lastSignalTime: "",
          createdAt: p.purchaseTime || new Date().toISOString(),
          updatedAt: p.purchaseTime || new Date().toISOString(),
        }));
      list = [...purchased, ...list];
    }

    robots.value = list;
  } catch (error) {
    console.error("加载机器人列表失败:", error);
    robots.value = [];
  }
}
function updateEquityProgress() {
  if (!backtestResults.value) {
    equityProgress.value = 0;
    return;
  }
  const totalReturn = Number(backtestResults.value.totalReturn || 0);
  equityProgress.value = Math.max(10, Math.min(100, 50 + totalReturn * 100));
}
// ========== 订单信息相关函数 ==========
/**
 * 加载订单列表
 */
async function loadOrders() {
  ordersLoading.value = true;
  try {
    const params: any = {
      symbol: selectedSymbol.value,
      pageNum: ordersPage.value + 1,
      pageSize: 10,
      sortField: "orderTime",
      sortOrder: "desc",
    };
    if (orderSnFilter.value && orderSnFilter.value.trim()) {
      params.orderSn = orderSnFilter.value.trim();
    }
    if (orderStatusFilter.value) {
      params.status = orderStatusFilter.value;
    }
    if (orderCloseDateFilter.value) {
      params.closeDate = orderCloseDateFilter.value;
    }
    if (orderBotIdFilter.value) {
      params.botId = orderBotIdFilter.value;
    }
    const response = await queryOrders(params);
    if (response && response.success && response.data) {
      const pageData = response.data;
      ordersTotalPages.value = pageData.pages || 1;
      const records = pageData.records || [];
      // 三级树形：订单 → 开仓记录(item) → 平仓明细(closeItem)
      const enrichedOrders = await Promise.all(
        records.map(async (o: any) => {
          const orderSn = o.orderSn;
          if (!orderSn) return { ...o, _children: [], _rowKey: o.id || '' };
          try {
            const [itemsRes, closeItemsRes] = await Promise.all([
              listOrderItems(String(orderSn)),
              listOrderCloseItems(String(orderSn)),
            ]);
            const items: any[] = itemsRes?.success ? itemsRes.data || [] : [];
            const closeItems: any[] = closeItemsRes?.success ? closeItemsRes.data || [] : [];
            // 将平仓明细按 entrySn 分组
            const closeMap: Record<string, any[]> = {};
            for (const ci of closeItems) {
              const sn = ci.entrySn;
              if (sn) {
                if (!closeMap[sn]) closeMap[sn] = [];
                closeMap[sn].push({ ...ci, _type: 'closeItem', _rowKey: orderSn + '_close_' + (ci.batchId || ci.id || sn + '_' + closeMap[sn].length) });
              }
            }
            // 每个开仓记录下挂对应的平仓明细
            const children = items.map((item: any, idx: number) => {
              const itemSn = item.entrySn;
              const itemCloseItems = itemSn ? closeMap[itemSn] : undefined;
              return {
                ...item,
                _type: 'item',
                _rowKey: orderSn + '_item_' + (itemSn || 'i' + idx),
                _children: itemCloseItems || [],
              };
            });
            return { ...o, _children: children, _rowKey: orderSn };
          } catch {
            return { ...o, _children: [], _rowKey: orderSn };
          }
        })
      );
      orders.value = enrichedOrders;
      // 更新订单路径标记
      if (showOrderPathOverlay.value) {
        scheduleOrderOverlayUpdate();
      } else {
        clearOrderPathMarkers();
        clearOrderPathLines();
      }
    }
  } catch (error) {
    console.error("加载订单失败:", error);
    ElMessage.error("加载订单失败");
  } finally {
    ordersLoading.value = false;
  }
}
function onOrderCloseDateChange() {
  ordersPage.value = 0;
  loadOrders();
}
function onOrderSnFilterChange() {
  ordersPage.value = 0;
  loadOrders();
}
const positionsOrders = ref<any[]>([]);
const positionsLoading = ref(false);
async function openOrderDetailsDialog(order: any) {
  const orderSn = order?.orderSn;
  if (!orderSn) {
    ElMessage.warning("未找到订单号");
    return;
  }
  orderDetailsOrder.value = order;
  orderDetailsDialogVisible.value = true;
  orderDetailsLoading.value = true;
  try {
    const [itemsRes, closesRes] = await Promise.all([
      listOrderItems(String(orderSn)),
      listOrderCloses(String(orderSn)),
    ]);
    orderDetailsItems.value =
      itemsRes && (itemsRes as any).success ? (itemsRes as any).data || [] : [];
    orderDetailsCloses.value =
      closesRes && (closesRes as any).success
        ? (closesRes as any).data || []
        : [];
  } catch (e) {
    orderDetailsItems.value = [];
    orderDetailsCloses.value = [];
  } finally {
    orderDetailsLoading.value = false;
  }
}
async function handleCancelOrder() {
  const order = orderDetailsOrder.value;
  if (!order?.id) return;
  try {
    await ElMessageBox.confirm("确认撤销该待成交订单？", "确认", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    const res = await cancelOrder(String(order.id));
    if (res && (res as any).success) {
      ElMessage.success("撤销成功");
      orderDetailsDialogVisible.value = false;
      loadPositionsOrders();
      loadOrders();
    } else {
      ElMessage.error((res as any)?.message || "撤销失败");
    }
  } catch (e) {
    if (e !== "cancel") {
      ElMessage.error("撤销失败");
    }
  }
}
async function loadPositionsOrders() {
  positionsLoading.value = true;
  try {
    const params: any = {
      symbol: selectedSymbol.value,
      pageNum: 1,
      pageSize: 20,
      sortField: "orderTime",
      sortOrder: "desc",
      status: "DEAL",
    };
    const response = await queryOrders(params);
    if (response && response.success && response.data) {
      const pageData = response.data;
      const orders = pageData.records || [];
      // 三级树形：订单 → 开仓记录(item) → 平仓明细(closeItem)
      const enrichedOrders = await Promise.all(
        orders.map(async (o: any) => {
          const orderSn = o.orderSn;
          if (!orderSn) return { ...o, _children: [], _rowKey: o.id || '' };
          try {
            const [itemsRes, closeItemsRes] = await Promise.all([
              listOrderItems(String(orderSn)),
              listOrderCloseItems(String(orderSn)),
            ]);
            const items: any[] = itemsRes?.success ? itemsRes.data || [] : [];
            const closeItems: any[] = closeItemsRes?.success ? closeItemsRes.data || [] : [];
            const closeMap: Record<string, any[]> = {};
            for (const ci of closeItems) {
              const sn = ci.entrySn;
              if (sn) {
                if (!closeMap[sn]) closeMap[sn] = [];
                closeMap[sn].push({ ...ci, _type: 'closeItem', _rowKey: orderSn + '_close_' + (ci.batchId || ci.id || sn + '_' + closeMap[sn].length) });
              }
            }
            const children = items.map((item: any, idx: number) => {
              const itemSn = item.entrySn;
              return {
                ...item,
                _type: 'item',
                _rowKey: orderSn + '_item_' + (itemSn || 'i' + idx),
                _children: itemSn ? closeMap[itemSn] || [] : [],
              };
            });
            return { ...o, _children: children, _rowKey: orderSn };
          } catch {
            return { ...o, _children: [], _rowKey: orderSn };
          }
        })
      );
      positionsOrders.value = enrichedOrders;
    }
  } catch (error) {
    ElMessage.error("加载持仓订单失败");
  } finally {
    positionsLoading.value = false;
  }
}
watch(positionsOrders, (orders) => {
  const openOrders = orders
    .filter((o: any) => String(o.symbol || "") === String(selectedSymbol.value))
    .map((o: any) => {
      let entryTime: number | null = null
      const raw = o.buyTime ?? o.createTime ?? o.orderTime
      if (raw) {
        const t = typeof raw === "number" ? raw : Date.parse(raw)
        entryTime = t > 1000000000000 ? Math.floor(t / 1000) : Math.floor(t)
      }
      return {
        orderSn: o.orderSn || String(o.id),
        entryPrice: Number(o.buyPrice ?? o.openPrice ?? o.buyAvgPrice ?? o.averagePrice ?? 0),
        orderSide: String(o.orderSide || o.side || ""),
        gainPrice: o.gainPrice ? Number(o.gainPrice) : null,
        lossPrice: o.lossPrice ? Number(o.lossPrice) : null,
        batchExitType: o.batchExitType ?? null,
        batchExitPlans: o.batchExitPlans ?? [],
        batchExits: o.batchExits ?? [],
        entryTime,
      }
    })
    .filter((o) => o.entryPrice > 0)
  orderPriceLinesApi.updateOrders(openOrders)
}, { deep: true, immediate: true })
watch(candleSeries, (series) => {
  if (!series || !positionsOrders.value?.length) return
  const openOrders = positionsOrders.value
    .filter((o: any) => String(o.symbol || "") === String(selectedSymbol.value))
    .map((o: any) => {
      let entryTime: number | null = null
      const raw = o.buyTime ?? o.createTime ?? o.orderTime
      if (raw) {
        const t = typeof raw === "number" ? raw : Date.parse(raw)
        entryTime = t > 1000000000000 ? Math.floor(t / 1000) : Math.floor(t)
      }
      return {
        orderSn: o.orderSn || String(o.id),
        entryPrice: Number(o.buyPrice ?? o.openPrice ?? o.buyAvgPrice ?? o.averagePrice ?? 0),
        orderSide: String(o.orderSide || o.side || ""),
        gainPrice: o.gainPrice ? Number(o.gainPrice) : null,
        lossPrice: o.lossPrice ? Number(o.lossPrice) : null,
        batchExitType: o.batchExitType ?? null,
        batchExitPlans: o.batchExitPlans ?? [],
        batchExits: o.batchExits ?? [],
        entryTime,
      }
    })
    .filter((o) => o.entryPrice > 0)
  if (openOrders.length > 0) {
    orderPriceLinesApi.updateOrders(openOrders)
  }
})
// 将订单路径的标记与连线更新合并为一次帧内更新，避免重复重绘造成闪烁
let orderOverlayRaf: number | null = null;
function scheduleOrderOverlayUpdate() {
  if (orderOverlayRaf) {
    cancelAnimationFrame(orderOverlayRaf);
    orderOverlayRaf = null;
  }
  orderOverlayRaf = requestAnimationFrame(() => {
    updateOrderPathMarkers();
    updateOrderPathLines();
    orderOverlayRaf = null;
  });
}
/**
 * 跳转到订单页面
 */
function goToOrdersPage(page: number) {
  if (page < 0 || page >= ordersTotalPages.value) {
    return;
  }
  ordersPage.value = page;
  loadOrders();
}
/**
 * 跳转到输入的页数
 */
function jumpToOrdersPageInput() {
  if (ordersPageInput.value === null || ordersPageInput.value === undefined) {
    ElMessage.warning("请输入页数");
    return;
  }
  const targetPage = ordersPageInput.value;
  // 验证页数范围
  if (targetPage < 1) {
    ElMessage.warning("页数不能小于1");
    ordersPageInput.value = 1;
    return;
  }
  if (targetPage > ordersTotalPages.value) {
    ElMessage.warning(`页数不能大于总页数 ${ordersTotalPages.value}`);
    ordersPageInput.value = ordersTotalPages.value;
    return;
  }
  // 转换为0-based索引并跳转
  goToOrdersPage(targetPage - 1);
  // 清空输入框
  ordersPageInput.value = null;
}
/**
 * 转换各种时间格式为秒级时间戳
 */
function coerceToSeconds(
  orderTime: string | number | Date | null,
): number | null {
  if (!orderTime) return null;
  try {
    if (typeof orderTime === "string") {
      if (orderTime.includes("T") && orderTime.includes("Z")) {
        const d = new Date(orderTime);
        return isNaN(d.getTime()) ? null : Math.floor(d.getTime() / 1000);
      }
      if (/^\d+$/.test(orderTime)) {
        const ms = parseInt(orderTime);
        return Math.floor(ms / 1000);
      }
      const ts = TimezoneHelper.utc8ToTimestamp(orderTime);
      return !ts || Number.isNaN(ts) ? null : ts;
    }
    if (orderTime instanceof Date) {
      return Math.floor(orderTime.getTime() / 1000);
    }
    if (typeof orderTime === "number") {
      return orderTime > 10000000000 ? Math.floor(orderTime / 1000) : orderTime;
    }
    return null;
  } catch {
    return null;
  }
}
/**
 * 清除订单路径标记（移除所有以 [ORD] 开头文本的标记）
 */
function clearOrderPathMarkers() {
  const markersApi = seriesMarkersRef.value;
  if (!markersApi) return;
  const existing = markersApi.markers() || [];
  const filtered = existing.filter(
    (m) => !(typeof m.text === "string" && m.text.startsWith("[ORD]")),
  );
  if (filtered.length !== existing.length) {
    filtered.sort((a, b) => a.time - b.time);
    markersApi.setMarkers(filtered);
  }
}
/**
 * 订单路径连线系列集合（每个订单一条线）
 */
const orderPathLineSeries: Record<string, any> = {};
/**
 * 清除订单路径连线
 */
function clearOrderPathLines() {
  if (!chart.value) return;
  const keys = Object.keys(orderPathLineSeries);
  for (const k of keys) {
    const series = orderPathLineSeries[k];
    if (series) {
      chart.value.removeSeries(series);
    }
    delete orderPathLineSeries[k];
  }
}
/**
 * 根据当前订单绘制开/平仓标记
 */
function updateOrderPathMarkers() {
  const markersApi = seriesMarkersRef.value;
  if (!markersApi || !candleSeries.value) return;
  if (!orders.value || orders.value.length === 0) {
    clearOrderPathMarkers();
    return;
  }
  const orderMarkers: Array<{
    time: number;
    position: "aboveBar" | "belowBar" | "inBar";
    color: string;
    shape:
      | "arrowUp"
      | "arrowDown"
      | "circle"
      | "triangleUp"
      | "triangleDown"
      | "square";
    text: string;
    size: 1 | 2 | 3;
  }> = [];
  const iterOrders =
    showOnlySelectedOrder.value && activeOrderId.value
      ? orders.value.filter(
          (o) =>
            String(o.id ?? `${o.orderSn ?? ""}-${o.orderTime ?? ""}`) ===
            activeOrderId.value,
        )
      : orders.value;
  for (const o of iterOrders) {
    const side = (o.orderSide || o.side || "").toUpperCase();
    const openTs = coerceToSeconds(
      o.orderTime || o.buyTime || o.createTime || null,
    );
    const closeTs = coerceToSeconds(o.sellTime || o.closeTime || null);
    const openPrice = o.buyPrice ?? o.openPrice;
    const closePrice = o.sellPrice ?? o.closePrice;
    const isSelected =
      !!activeOrderId.value &&
      String(o.id ?? `${o.orderSn ?? ""}-${o.orderTime ?? ""}`) ===
        activeOrderId.value;
    if (openTs && typeof openTs === "number") {
      orderMarkers.push({
        time: openTs,
        position: side === "BUY" ? "belowBar" : "aboveBar",
        color: side === "BUY" ? "#26a69a" : "#ef5350",
        shape: side === "BUY" ? "arrowUp" : "arrowDown",
        text: "[ORD] 开",
        size: isSelected ? 3 : 2,
      });
    }
    if (closeTs && typeof closeTs === "number") {
      const income = Number(
        o.income ??
          (side === "BUY"
            ? (closePrice ?? 0) - (openPrice ?? 0)
            : (openPrice ?? 0) - (closePrice ?? 0)),
      );
      const profitable = income >= 0;
      orderMarkers.push({
        time: closeTs,
        position: "inBar",
        color: profitable ? "#67c23a" : "#f56c6c",
        shape: profitable ? "triangleUp" : "triangleDown",
        text: profitable ? "[ORD] 盈" : "[ORD] 亏",
        size: isSelected ? 3 : 2,
      });
    }
  }
  orderPathMarkersRef.value = orderMarkers;
  const base = markersApi.markers() || [];
  markersApi.setMarkers(base);
}
/**
 * 根据当前订单绘制开/平仓连线（两点直线）
 */
function updateOrderPathLines() {
  if (!chart.value || !candleSeries.value) return;
  // 当关闭可视化时，直接清除
  if (!showOrderPathOverlay.value) {
    clearOrderPathLines();
    return;
  }
  // 仅处理当前页订单，避免过多 series
  const maxSeries = 12;
  let count = 0;
  const activeKeys: Set<string> = new Set();
  const iterOrders =
    showOnlySelectedOrder.value && activeOrderId.value
      ? orders.value.filter(
          (o) =>
            String(o.id ?? `${o.orderSn ?? ""}-${o.orderTime ?? ""}`) ===
            activeOrderId.value,
        )
      : orders.value;
  for (const o of iterOrders) {
    if (count >= maxSeries) break;
    const id = String(o.id ?? `${o.orderSn ?? ""}-${o.orderTime ?? ""}`);
    const side = (o.orderSide || o.side || "").toUpperCase();
    const openTs = coerceToSeconds(
      o.orderTime || o.buyTime || o.createTime || null,
    );
    const closeTs = coerceToSeconds(o.sellTime || o.closeTime || null);
    const openPrice = Number(o.buyPrice ?? o.openPrice);
    const closePrice = Number(o.sellPrice ?? o.closePrice);
    if (!openTs || !closeTs || isNaN(openPrice) || isNaN(closePrice)) {
      continue;
    }
    activeKeys.add(id);
    const p1 = {
      time: Math.min(openTs, closeTs),
      value: openTs <= closeTs ? openPrice : closePrice,
    };
    const p2 = {
      time: Math.max(openTs, closeTs),
      value: openTs <= closeTs ? closePrice : openPrice,
    };
    const income = Number(
      o.income ??
        (side === "BUY" ? closePrice - openPrice : openPrice - closePrice),
    );
    let series = orderPathLineSeries[id];
    if (!series) {
      series = chart.value.addSeries(LineSeries, {
        color: side === "BUY" ? "#26a69a" : "#ef5350",
        lineWidth: activeOrderId.value === id ? 2 : 1,
        lineStyle: income >= 0 ? 0 : 2,
        priceLineVisible: false,
        lastValueVisible: false,
        crosshairMarkerVisible: false,
      });
      orderPathLineSeries[id] = series;
    } else {
      series.applyOptions({
        lineStyle: income >= 0 ? 0 : 2,
        lineWidth: activeOrderId.value === id ? 2 : 1,
      });
    }
    // 设置数据（两点直线）
    series.setData([p1, p2]);
    count++;
  }
  // 处理非活动线：仅选中模式下不移除，改为隐藏；正常模式下移除避免堆积
  const allKeys = Object.keys(orderPathLineSeries);
  if (showOnlySelectedOrder.value && activeOrderId.value) {
    for (const key of allKeys) {
      const series = orderPathLineSeries[key];
      if (series) {
        const visible = activeKeys.has(key);
        series.applyOptions({ visible });
      }
    }
  } else if (chart.value) {
    for (const key of allKeys) {
      if (!activeKeys.has(key)) {
        chart.value.removeSeries(orderPathLineSeries[key]);
        delete orderPathLineSeries[key];
      }
    }
  }
}
watch(showOrderPathOverlay, (val) => {
  if (val) {
    scheduleOrderOverlayUpdate();
  } else {
    clearOrderPathMarkers();
    clearOrderPathLines();
  }
});
/**
 * 合并基础标记与订单路径标记
 */
function mergeBaseWithOrderMarkers(baseMarkers: any[]) {
  const baseWithoutOrder = (baseMarkers || []).filter(
    (m) => !(typeof m.text === "string" && m.text.startsWith("[ORD]")),
  );
  const orderMarkers = (showOrderPathOverlay.value ? orderPathMarkersRef.value : []);
  const merged = [
    ...baseWithoutOrder,
    ...orderMarkers,
  ];
  const valid = merged.filter((m) => m && m.time != null && !isNaN(Number(m.time)));
  valid.sort((a, b) => a.time - b.time);
  return valid;
}
watch(showApiSignalsOverlay, (val) => {
  // 直接更新一次，以应用开关状态
  updateSignalMarkers();
});
watch(filterValidSignals, () => {
  updateSignalMarkers();
});
watch(selectedBotId, () => {
  signalsCache.value = [];
  void loadSignalsForVisibleRange();
});
watch(showOnlySelectedOrder, () => {
  if (showOrderPathOverlay.value) {
    scheduleOrderOverlayUpdate();
  }
});
watch(activeOrderId, () => {
  if (showOnlySelectedOrder.value) {
    scheduleOrderOverlayUpdate();
  }
});
/**
 * 选择当前订单以单独显示路径
 */
function setActiveOrder(order: any) {
  const id = String(
    order.id ?? `${order.orderSn ?? ""}-${order.orderTime ?? ""}`,
  );
  activeOrderId.value = id;
  if (showOrderPathOverlay.value && showOnlySelectedOrder.value) {
    updateOrderPathMarkers();
    updateOrderPathLines();
  }
}
/**
 * 格式化时间
 */
function formatTime(time: string | number | null) {
  if (!time) return "-";
  const toDateTimeString = (date: Date) => {
    const formatter = new Intl.DateTimeFormat("sv-SE", {
      timeZone: "Asia/Shanghai",
      hour12: false,
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
    return formatter.format(date).replace("T", " ");
  };
  const parseDateTime = (value: string) => {
    const normalized = value.replace("T", " ").trim();
    const match = normalized.match(
      /^(\d{4})[/-](\d{1,2})[/-](\d{1,2})(?:\s+(\d{1,2})(?::(\d{1,2})(?::(\d{1,2}))?)?)?$/,
    );
    if (match) {
      const year = Number(match[1]);
      const month = Number(match[2]) - 1;
      const day = Number(match[3]);
      const hour = Number(match[4] ?? 0);
      const minute = Number(match[5] ?? 0);
      const second = Number(match[6] ?? 0);
      const date = new Date(year, month, day, hour, minute, second);
      return isNaN(date.getTime()) ? null : date;
    }
    const date = new Date(value);
    return isNaN(date.getTime()) ? null : date;
  };
  if (typeof time === "string") {
    const parsed = parseDateTime(time);
    if (!parsed) return time;
    return toDateTimeString(parsed);
  }
  const timestamp = time > 10000000000 ? time : time * 1000;
  const date = new Date(timestamp);
  if (isNaN(date.getTime())) return String(time);
  return toDateTimeString(date);
}
/**
 * 跳转到订单时间
 * @param orderTime - 订单时间（字符串、Date对象或时间戳）
 */
async function jumpToOrderTime(orderTime: string | number | Date | null) {
  if (!orderTime) {
    ElMessage.warning("订单时间无效");
    return;
  }
  try {
    let timestamp: number;
    // 处理不同格式的时间输入
    if (typeof orderTime === "string") {
      // 检查是否是ISO 8601格式的UTC时间字符串（如 '2024-12-31T18:03:00.000Z'）
      if (orderTime.includes("T") && orderTime.includes("Z")) {
        // ISO 8601 UTC时间字符串，直接转换为时间戳
        const date = new Date(orderTime);
        if (!isNaN(date.getTime())) {
          timestamp = Math.floor(date.getTime() / 1000);
        } else {
          throw new Error(`无效的ISO时间字符串: ${orderTime}`);
        }
      } else if (orderTime.match(/^\d+$/)) {
        // 如果是纯数字，当作毫秒级时间戳处理
        timestamp = Math.floor(parseInt(orderTime) / 1000); // 毫秒转秒
      } else {
        // 其他格式的日期字符串，当作UTC+8时间处理
        timestamp = TimezoneHelper.utc8ToTimestamp(orderTime);
      }
    } else if (orderTime instanceof Date) {
      // 如果是Date对象，直接转换为时间戳
      timestamp = Math.floor(orderTime.getTime() / 1000);
    } else if (typeof orderTime === "number") {
      // 如果是数字，判断是秒级还是毫秒级时间戳
      if (orderTime > 10000000000) {
        // 毫秒级时间戳
        timestamp = Math.floor(orderTime / 1000);
      } else {
        // 秒级时间戳
        timestamp = orderTime;
      }
    } else {
      ElMessage.error("不支持的时间格式");
      return;
    }
    // 验证时间戳有效性
    if (!timestamp || isNaN(timestamp) || timestamp <= 0) {
      ElMessage.error("无效的时间戳");
      return;
    }
    // 验证时间不是未来时间（允许1小时误差）
    const now = Math.floor(Date.now() / 1000);
    const timeDiff = timestamp - now;
    const oneHour = 3600;
    if (timeDiff > oneHour) {
      const daysDiff = Math.floor(timeDiff / (24 * 3600));
      ElMessage.warning(
        `订单时间异常（未来时间，相差${daysDiff}天），无法跳转`,
      );
      return;
    }
    // 验证时间不是太早（超过1年的数据可能不存在）
    const oneYearAgo = now - 365 * 24 * 3600;
    if (timestamp < oneYearAgo) {
      ElMessage.warning("订单时间过久（超过1年），可能无法加载数据");
    }
    // 执行时间跳转
    await loadByTime(timestamp);
    ElMessage.success("已跳转到订单时间");
  } catch (error) {
    console.error("订单时间跳转失败:", error);
    let errorMessage = "跳转失败";
    if (error && typeof error === "object" && "message" in error) {
      errorMessage = `跳转失败: ${(error as Error).message}`;
    } else if (typeof error === "string") {
      errorMessage = `跳转失败: ${error}`;
    }
    ElMessage.error(errorMessage);
  }
}
async function jumpToProfitReportTime(payload: {
  periodKey: string;
  periodType: "day" | "month";
}) {
  if (!payload?.periodKey) {
    ElMessage.warning("时间无效");
    return;
  }
  try {
    let timeStr = "";
    if (payload.periodType === "month") {
      if (!/^\d{4}-\d{2}$/.test(payload.periodKey)) {
        ElMessage.warning("月份格式无效");
        return;
      }
      timeStr = `${payload.periodKey}-01 00:00:00`;
    } else {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(payload.periodKey)) {
        ElMessage.warning("日期格式无效");
        return;
      }
      timeStr = `${payload.periodKey} 00:00:00`;
    }
    const timestamp = TimezoneHelper.utc8ToTimestamp(timeStr);
    if (!timestamp || Number.isNaN(timestamp) || timestamp <= 0) {
      ElMessage.error("无效的时间戳");
      return;
    }
    await loadByTime(timestamp);
    ElMessage.success("已跳转到收益统计时间");
  } catch (error) {
    console.error("收益统计时间跳转失败:", error);
    let errorMessage = "跳转失败";
    if (error && typeof error === "object" && "message" in error) {
      errorMessage = `跳转失败: ${(error as Error).message}`;
    } else if (typeof error === "string") {
      errorMessage = `跳转失败: ${error}`;
    }
    ElMessage.error(errorMessage);
  }
}
// ========== 面板拖拽调整高度相关函数 ==========
/**
 * 动态计算底部面板最大高度（基于当前窗口高度）
 */
function updateMaxPanelHeight() {
  const wrapper = document.querySelector('.market-kline-wrapper');
  if (!wrapper) return;
  const wrapperRect = wrapper.getBoundingClientRect();
  maxPanelHeight.value = Math.max(minPanelHeight, wrapperRect.height - 120);
}
/**
 * 开始拖拽调整面板高度（上下方向）
 */
function startResize(e: MouseEvent) {
  e.preventDefault();
  e.stopPropagation();
  isResizing.value = true;
  resizeStartY = e.clientY;
  resizeStartHeight = bottomPanelHeight.value;
  // 使用 capture 模式确保事件能正确捕获
  document.addEventListener("mousemove", handleResize, {
    passive: false,
    capture: true,
  });
  document.addEventListener("mouseup", stopResize, {
    passive: false,
    capture: true,
  });
  document.body.style.cursor = "row-resize";
  document.body.style.userSelect = "none";
}
/**
 * 处理拖拽调整（上下方向）
 */
function handleResize(e: MouseEvent) {
  if (!isResizing.value) return;
  e.preventDefault();
  e.stopPropagation();
  const deltaY = resizeStartY - e.clientY;
  const newHeight = resizeStartHeight + deltaY;
  const clampedHeight = Math.max(
    minPanelHeight,
    Math.min(maxPanelHeight.value, newHeight),
  );
  bottomPanelHeight.value = clampedHeight;
  requestAnimationFrame(() => updateContainerSize());
}
/**
 * 停止拖拽调整（上下方向）
 */
function stopResize(e?: MouseEvent) {
  if (e) {
    e.preventDefault();
    e.stopPropagation();
  }
  // 必须先设置标志，防止handleResize继续执行
  isResizing.value = false;
  // 移除事件监听器时，必须使用与添加时相同的选项
  document.removeEventListener("mousemove", handleResize, {
    capture: true,
  } as any);
  document.removeEventListener("mouseup", stopResize, { capture: true } as any);
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
}
// ========== 右侧面板拖拽调整宽度相关函数 ==========
/**
 * 开始拖拽调整右侧面板宽度
 */
function startRightPanelResize(e: MouseEvent) {
  e.preventDefault();
  e.stopPropagation();
  if (rightPanelCollapsed.value) return;
  isRightPanelResizing.value = true;
  rightPanelResizeStartX = e.clientX;
  rightPanelResizeStartWidth = rightPanelWidth.value;
  // 使用 capture 模式确保事件能正确捕获
  document.addEventListener("mousemove", handleRightPanelResize, {
    passive: false,
    capture: true,
  });
  document.addEventListener("mouseup", stopRightPanelResize, {
    passive: false,
    capture: true,
  });
  document.body.style.cursor = "col-resize";
  document.body.style.userSelect = "none";
}
/**
 * 处理右侧面板拖拽调整
 */
function handleRightPanelResize(e: MouseEvent) {
  if (!isRightPanelResizing.value) return;
  e.preventDefault();
  e.stopPropagation();
  const deltaX = rightPanelResizeStartX - e.clientX; // 向左拖拽为正，向右拖拽为负
  const newWidth = rightPanelResizeStartWidth + deltaX;
  // 限制在最小和最大宽度之间
  const clampedWidth = Math.max(
    minRightPanelWidth,
    Math.min(maxRightPanelWidth, newWidth),
  );
  // 直接更新值
  rightPanelWidth.value = clampedWidth;
  rightPanelLastWidth.value = clampedWidth;
  // 同时直接设置 DOM 元素的宽度，确保实时更新
  if (rightPanelRef.value) {
    rightPanelRef.value.style.width = `${clampedWidth}px`;
  }
  // 更新图表尺寸
  requestAnimationFrame(() => {
    updateContainerSize();
  });
}
/**
 * 停止右侧面板拖拽调整
 */
function stopRightPanelResize(e?: MouseEvent) {
  if (e) {
    e.preventDefault();
    e.stopPropagation();
  }
  // 必须先设置标志，防止handleRightPanelResize继续执行
  isRightPanelResizing.value = false;
  // 移除事件监听器时，必须使用与添加时相同的选项
  document.removeEventListener("mousemove", handleRightPanelResize, {
    capture: true,
  } as any);
  document.removeEventListener("mouseup", stopRightPanelResize, {
    capture: true,
  } as any);
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
}
function toggleRightPanel() {
  if (rightPanelCollapsed.value) {
    rightPanelCollapsed.value = false;
    const restoredWidth = rightPanelLastWidth.value || rightPanelWidth.value;
    rightPanelWidth.value = Math.max(
      minRightPanelWidth,
      Math.min(maxRightPanelWidth, restoredWidth),
    );
  } else {
    rightPanelLastWidth.value = rightPanelWidth.value;
    rightPanelCollapsed.value = true;
  }
  nextTick(() => {
    updateContainerSize();
  });
}
function openRightPanelTab(tab: "analysis" | "smc") {
  if (!rightPanelCollapsed.value && rightMainTab.value === tab) {
    toggleRightPanel();
    return;
  }
  if (rightPanelCollapsed.value) {
    toggleRightPanel();
  }
  rightMainTab.value = tab;
  if (tab === "smc") loadSmc();
}
// 清屏交易日志
function clearTradeLogs() {
  tradeLogs.value = [];
}
// 点击外部关闭指标下拉（模仿 lightweight-charts-ui）
function onDocumentClickForIndicator(e: MouseEvent) {
  const target = e.target as Node;
  if (indicatorBtnRef.value?.contains(target)) return;
  const dropdown = document.querySelector(".indicator-dropdown");
  if (dropdown?.contains(target)) return;
  showIndicatorsDropdown.value = false;
}
// 右键菜单处理函数
function handleContextMenu(event: MouseEvent) {
  event.preventDefault();
  event.stopPropagation();
  const approxWidth = 180;
  const approxHeight = signalTooltip.value.visible ? 260 : 220;
  const maxX = Math.max(0, window.innerWidth - approxWidth);
  const maxY = Math.max(0, window.innerHeight - approxHeight);
  const x = Math.min(Math.max(0, event.clientX), maxX);
  const y = Math.min(Math.max(0, event.clientY), maxY);
  contextMenuPosition.value = { x, y };
  contextMenuVisible.value = true;
  if (chart.value && chartArea.value) {
    const chartRect = chartArea.value.getBoundingClientRect();
    const chartX = event.clientX - chartRect.left;
    const timeFromCoord = chart.value.timeScale().coordinateToTime(chartX);
    if (timeFromCoord) {
      const bar = dataCache.value.find(b => b.time === timeFromCoord);
      if (bar) {
        focusedKlineData.value = { time: bar.time, open: bar.open, high: bar.high, low: bar.low, close: bar.close };
      }
    }
  }
}
// 关闭右键菜单
function closeContextMenu(event?: Event) {
  // 如果点击的是右键菜单本身，不关闭
  if (event && (event.target as HTMLElement).closest(".chart-context-menu")) {
    return;
  }
  contextMenuVisible.value = false;
}
const handleDocumentClick = (e: Event) => {
  closeContextMenu(e);
};
const handleDocumentContextMenu = (e: Event) => {
  // 如果右键的是图表区域，不关闭菜单（让 handleContextMenu 处理）
  if ((e.target as HTMLElement).closest(".chart-area")) {
    return;
  }
  closeContextMenu(e);
};
const manualOrderForm = reactive({
  side: "BUY" as "BUY" | "SELL",
  orderType: "MARKET" as "MARKET" | "LIMIT",
  limitPrice: undefined as number | undefined,
});
const manualOrderSubmitting = ref(false);
const orderPanelVisible = ref(false);
const aiPanelVisible = ref(false);
function toggleOrderPanel() {
  orderPanelVisible.value = !orderPanelVisible.value;
  if (aiPanelVisible.value) aiPanelVisible.value = false;
}
function toggleAiPanel() {
  aiPanelVisible.value = !aiPanelVisible.value;
  if (orderPanelVisible.value) orderPanelVisible.value = false;
}
const orderAmount = ref(10);
const orderBalance = computed(() => {
  const bot = robots.value.find((b: any) => (b.botId || b.id) === selectedBotId.value);
  return bot?.currentCapital ?? 0;
});
/** TradFi品种检测：纯字母交易对，如 EURUSD、XAGUSD */
const isTradfiSymbol = computed(() => /^[A-Za-z]{4,12}$/.test(selectedSymbol.value));
const orderTradeMode = ref<"swap" | "spot">("swap");
const orderLeverage = ref(100);
const orderMarginMode = ref<"cross" | "isolated">("cross");
const orderTpPrice = ref<number | undefined>(undefined);
const orderSlPrice = ref<number | undefined>(undefined);
const manualOrderContextTime = ref<number | null>(null);
const manualOrderContextPrice = ref<number | null>(null);
const manualOrderContextKlineTime = ref<string | null>(null);
const orderRR = computed(() => {
  const isMarket = manualOrderForm.orderType === "MARKET";
  const entry = isMarket && dataCache.value.length > 0
    ? dataCache.value[dataCache.value.length - 1].close
    : manualOrderContextPrice.value;
  const tp = orderTpPrice.value;
  const sl = orderSlPrice.value;
  if (!entry || tp === undefined || sl === undefined) return null;
  const side = manualOrderForm.side === "BUY" ? 1 : -1;
  const tpDiff = side * (tp - entry);
  const slDiff = side * (entry - sl);
  if (tpDiff <= 0 || slDiff <= 0) return null;
  return (tpDiff / slDiff).toFixed(2);
});
async function handleOpenOrderFromContextMenu() {
  closeContextMenu();
  const time =
    focusedKlineData.value?.time ??
    (dataCache.value.length
      ? dataCache.value[dataCache.value.length - 1].time
      : undefined);
  const price =
    focusedKlineData.value?.close ??
    (dataCache.value.length
      ? dataCache.value[dataCache.value.length - 1].close
      : undefined);
  manualOrderContextTime.value = typeof time === "number" ? time : null;
  manualOrderContextPrice.value = typeof price === "number" ? price : null;
  manualOrderContextKlineTime.value =
    typeof time === "number" ? formatTime(time * 1000) : null;
  manualOrderForm.side = "BUY";
  manualOrderForm.orderType = "MARKET";
  manualOrderForm.limitPrice =
    typeof price === "number" ? Number(price) : undefined;
  if (typeof price === "number" && selectedSymbol.value) {
    const levels = await computeSmcTpSl(selectedSymbol.value, price, "BUY");
    if (levels) {
      orderTpPrice.value = levels.tp;
      orderSlPrice.value = levels.sl;
    }
  }
  if (!orderPanelVisible.value) toggleOrderPanel();
}
function appendManualOrderMarker(side: "BUY" | "SELL") {
  const api = seriesMarkersRef.value;
  const time = manualOrderContextTime.value;
  if (!api || typeof time !== "number") return;
  const marker =
    side === "BUY"
      ? {
          time,
          position: "belowBar" as const,
          color: "#00c853",
          shape: "arrowUp" as const,
          text: "多",
          size: 2 as const,
        }
      : {
          time,
          position: "aboveBar" as const,
          color: "#ff1744",
          shape: "arrowDown" as const,
          text: "空",
          size: 2 as const,
        };
  const current = api.markers() || [];
  const updated = [...current, marker].sort((a: any, b: any) => a.time - b.time);
  api.setMarkers(updated);
}
async function submitManualOpenOrder() {
  if (manualOrderSubmitting.value) return;
  try {
    if (!selectedBotId.value) {
      ElMessage.warning("请选择机器人");
      return;
    }
    const bot = robots.value.find((b: any) => (b.botId || b.id) === selectedBotId.value);
    const accountId = bot?.accountId;
    if (!accountId) {
      ElMessage.warning("机器人未绑定账户，无法执行下单");
      return;
    }
    if (!Number.isFinite(Number(orderAmount.value)) || Number(orderAmount.value) <= 0) {
      ElMessage.warning(isTradfiSymbol.value ? "请输入有效的手数" : "请输入有效的下单金额");
      return;
    }
    // TradFi 不下单金额/资金校验（手数模式）
    if (!isTradfiSymbol.value && Number(orderAmount.value) > Number(orderBalance.value || 0)) {
      ElMessage.warning("下单金额超过机器人当前资金");
      return;
    }
    manualOrderSubmitting.value = true;
    const limitPrice =
      manualOrderForm.orderType === "LIMIT"
        ? Number(manualOrderForm.limitPrice)
        : undefined;
    if (manualOrderForm.orderType === "LIMIT" && !Number.isFinite(limitPrice)) {
      ElMessage.warning("请输入有效的限价");
      return;
    }
    const openResp = await manualOpenOrder({
      accountId,
      robotId: selectedBotId.value,
      symbol: selectedSymbol.value,
      side: manualOrderForm.side,
      orderType: manualOrderForm.orderType,
      quantity: Number(orderAmount.value),
      limitPrice: limitPrice,
      leverage: (!isTradfiSymbol.value && orderTradeMode.value === 'swap') ? orderLeverage.value : undefined,
      exchange: bot?.exchange,
      metadata: {
        source: "MarketKlineV1",
        klineTime: manualOrderContextKlineTime.value,
        klinePrice: manualOrderContextPrice.value,
        tradeMode: isTradfiSymbol.value ? 'tradfi' : orderTradeMode.value,
        marginMode: orderMarginMode.value,
        amount: orderAmount.value,
        tpPrice: orderTpPrice.value,
        slPrice: orderSlPrice.value,
      },
    });
    if (!openResp?.success) {
      throw new Error(openResp?.message || "开单失败");
    }
    appendManualOrderMarker(manualOrderForm.side);
    ElMessage.success("开单请求已发送");
  } catch (e: any) {
    ElMessage.error(e?.message || "开单失败");
  } finally {
    manualOrderSubmitting.value = false;
  }
}
// 手工平仓（市价）
async function handleManualClose(order: any) {
  try {
    const accountId =
      order?.accountId ||
      robots.value.find((b: any) => (b.botId || b.id) === selectedBotId.value)
        ?.accountId;
    if (!accountId) {
      ElMessage.warning("未找到账户ID，无法平仓");
      return;
    }
    const side =
      order?.orderSide === "BUY" ? ("LONG" as const) : ("SHORT" as const);
    const resp = await manualClosePosition({
      accountId,
      robotId: selectedBotId.value,
      symbol: order?.symbol || selectedSymbol.value,
      side,
      // 不传quantity表示全平（由后端决定），如需部分平仓可传入数字
    });
    if (!resp?.success) {
      throw new Error(resp?.message || "平仓失败");
    }
    ElMessage.success("已触发市价平仓");
    await loadPositionsOrders();
  } catch (e: any) {
    ElMessage.error(e?.message || "平仓失败");
  }
}

function openCloseDialog(order: any, isLimit = false) {
  closeForm.orderSn = order?.orderSn || order?.id;
  closeForm.symbol = order?.symbol || selectedSymbol.value;
  closeForm.side = order?.orderSide === "BUY" ? "LONG" : "SHORT";
  closeForm.quantity = undefined;
  closeForm.orderType = isLimit ? "LIMIT" : "MARKET";
  closeForm.limitPrice = undefined;
  closeDialogVisible.value = true;
}
function openGainLossDialog(order: any) {
  gainLossForm.orderSn = order?.orderSn || order?.id;
  gainLossForm.symbol = order?.symbol || selectedSymbol.value;
  gainLossForm.gainPrice =
    typeof order?.gainPrice === "number"
      ? order.gainPrice
      : order?.gainPrice
        ? Number(order.gainPrice)
        : undefined;
  gainLossForm.lossPrice =
    typeof order?.lossPrice === "number"
      ? order.lossPrice
      : order?.lossPrice
        ? Number(order.lossPrice)
        : undefined;
  gainLossDialogVisible.value = true;
}
async function submitGainLoss() {
  try {
    gainLossSubmitting.value = true;
    const bot = robots.value.find(
      (b: any) => (b.botId || b.id) === selectedBotId.value,
    );
    const accountId = bot?.accountId;
    if (!accountId) {
      ElMessage.warning("请选择绑定账户的机器人");
      return;
    }
    const orderSn = gainLossForm.orderSn;
    if (!orderSn) {
      ElMessage.warning("未找到订单号");
      return;
    }
    if (
      typeof gainLossForm.gainPrice === "number" &&
      gainLossForm.gainPrice <= 0
    ) {
      ElMessage.warning("止盈价需大于0或留空");
      return;
    }
    if (
      typeof gainLossForm.lossPrice === "number" &&
      gainLossForm.lossPrice <= 0
    ) {
      ElMessage.warning("止损价需大于0或留空");
      return;
    }
    const itemsRes = await listOrderItems(String(orderSn));
    const items =
      itemsRes && (itemsRes as any).success ? (itemsRes as any).data || [] : [];
    const firstItem = Array.isArray(items) ? items[0] : null;
    const itemId = firstItem?.id ?? firstItem?.entrySn ?? firstItem?.itemId;
    if (!itemId) {
      ElMessage.warning("未找到订单项ID");
      return;
    }
    const payload: any = {
      gainPrice:
        typeof gainLossForm.gainPrice === "number"
          ? gainLossForm.gainPrice
          : null,
      lossPrice:
        typeof gainLossForm.lossPrice === "number"
          ? gainLossForm.lossPrice
          : null,
    };
    const resp = await updateOrderItemGainLoss(String(itemId), payload);
    if (!resp?.success) {
      throw new Error(resp?.message || "修改失败");
    }
    ElMessage.success("止盈止损已提交修改");
    gainLossDialogVisible.value = false;
    await loadPositionsOrders();
  } catch (e: any) {
    ElMessage.error(e?.message || "修改失败");
  } finally {
    gainLossSubmitting.value = false;
  }
}
function openReverseDialog(order: any) {
  reverseForm.orderSn = order?.orderSn || order?.id;
  reverseForm.symbol = order?.symbol || selectedSymbol.value;
  reverseForm.side = order?.orderSide === "BUY" ? "LONG" : "SHORT";
  reverseForm.quantity = undefined;
  reverseForm.orderType = "MARKET";
  reverseForm.limitPrice = undefined;
  reverseDialogVisible.value = true;
}
async function submitReverse() {
  try {
    reverseSubmitting.value = true;
    const bot = robots.value.find(
      (b: any) => (b.botId || b.id) === selectedBotId.value,
    );
    const accountId = bot?.accountId;
    if (!accountId) {
      ElMessage.warning("请选择绑定账户的机器人");
      return;
    }
    if (reverseForm.orderType === "LIMIT") {
      if (
        !reverseForm.limitPrice ||
        typeof reverseForm.limitPrice !== "number" ||
        reverseForm.limitPrice <= 0
      ) {
        ElMessage.warning("请输入有效的限价");
        return;
      }
    }
    const fromSide = reverseForm.side;
    const toSide = fromSide === "LONG" ? "SHORT" : "LONG";
    const payload: any = {
      accountId,
      robotId: selectedBotId.value,
      symbol: reverseForm.symbol || selectedSymbol.value,
      fromSide,
      toSide,
      requestId: `${Date.now()}`,
      channel: "MarketKlineV1",
    };
    if (typeof reverseForm.quantity === "number" && reverseForm.quantity > 0) {
      payload.quantity = Math.floor(reverseForm.quantity);
    }
    if (reverseForm.orderType === "LIMIT") {
      payload.orderType = "LIMIT";
      payload.limitPrice = reverseForm.limitPrice;
    } else {
      payload.orderType = "MARKET";
    }
    const resp = await manualReversePosition(payload);
    if (!resp?.success) {
      throw new Error(resp?.message || "反手失败");
    }
    ElMessage.success("反手请求已提交");
    reverseDialogVisible.value = false;
    await loadPositionsOrders();
  } catch (e: any) {
    ElMessage.error(e?.message || "反手失败");
  } finally {
    reverseSubmitting.value = false;
  }
}
async function submitManualClose() {
  try {
    closeSubmitting.value = true;
    const bot = robots.value.find(
      (b: any) => (b.botId || b.id) === selectedBotId.value,
    );
    const accountId = bot?.accountId;
    if (!accountId) {
      ElMessage.warning("请选择绑定账户的机器人");
      return;
    }
    if (closeForm.orderType === "LIMIT") {
      if (
        !closeForm.limitPrice ||
        typeof closeForm.limitPrice !== "number" ||
        closeForm.limitPrice <= 0
      ) {
        ElMessage.warning("请输入有效的限价");
        return;
      }
    }
    const payload: any = {
      accountId,
      robotId: selectedBotId.value,
      symbol: closeForm.symbol || selectedSymbol.value,
      side: closeForm.side,
      requestId: `${Date.now()}`,
      channel: "MarketKlineV1",
    };
    if (typeof closeForm.quantity === "number" && closeForm.quantity > 0) {
      payload.quantity = Math.floor(closeForm.quantity);
    }
    if (closeForm.orderType === "LIMIT") {
      payload.orderType = "LIMIT";
      payload.limitPrice = closeForm.limitPrice;
    } else {
      payload.orderType = "MARKET";
    }
    const resp = await manualClosePosition(payload);
    if (!resp?.success) {
      throw new Error(resp?.message || "平仓失败");
    }
    ElMessage.success("平仓请求已提交");
    closeDialogVisible.value = false;
    await loadPositionsOrders();
  } catch (e: any) {
    ElMessage.error(e?.message || "平仓失败");
  } finally {
    closeSubmitting.value = false;
  }
}
// 重置缩放
function handleResetZoom() {
  if (!chart.value || !dataCache.value.length) return;
  const timeScale = chart.value.timeScale();
  const totalBars = dataCache.value.length;
  const visibleBars = Math.min(300, totalBars);
  if (totalBars > 0) {
    const firstTime = dataCache.value[0].time;
    const lastTime = dataCache.value[totalBars - 1].time;
    // 计算要显示的时间范围
    const timeRange = lastTime - firstTime;
    const visibleTimeRange = (timeRange * visibleBars) / totalBars;
    // 设置可见范围（显示最后 visibleBars 根K线）
    const startTime = lastTime - visibleTimeRange;
    timeScale.setVisibleRange({
      from: startTime as Time,
      to: lastTime as Time,
    });
  }
  closeContextMenu();
}
// 适应内容（显示所有数据）
function handleFitContent() {
  if (!chart.value || !dataCache.value.length) return;
  const timeScale = chart.value.timeScale();
  const totalBars = dataCache.value.length;
  if (totalBars > 0) {
    const firstTime = dataCache.value[0].time;
    const lastTime = dataCache.value[totalBars - 1].time;
    timeScale.setVisibleRange({
      from: firstTime as Time,
      to: lastTime as Time,
    });
  }
  closeContextMenu();
}
// 切换全屏
function handleToggleFullscreen() {
  if (!chartArea.value) return;
  if (!isFullscreen.value) {
    // 进入全屏
    if (chartArea.value.requestFullscreen) {
      chartArea.value.requestFullscreen();
    } else if ((chartArea.value as any).webkitRequestFullscreen) {
      (chartArea.value as any).webkitRequestFullscreen();
    } else if ((chartArea.value as any).mozRequestFullScreen) {
      (chartArea.value as any).mozRequestFullScreen();
    } else if ((chartArea.value as any).msRequestFullscreen) {
      (chartArea.value as any).msRequestFullscreen();
    }
  } else {
    // 退出全屏
    if (document.exitFullscreen) {
      document.exitFullscreen();
    } else if ((document as any).webkitExitFullscreen) {
      (document as any).webkitExitFullscreen();
    } else if ((document as any).mozCancelFullScreen) {
      (document as any).mozCancelFullScreen();
    } else if ((document as any).msExitFullscreen) {
      (document as any).msExitFullscreen();
    }
  }
  closeContextMenu();
}
// 监听全屏状态变化
function handleFullscreenChange() {
  isFullscreen.value = !!(
    document.fullscreenElement ||
    (document as any).webkitFullscreenElement ||
    (document as any).mozFullScreenElement ||
    (document as any).msFullscreenElement
  );
}
onMounted(async () => {
  updateMaxPanelHeight();
  window.addEventListener('resize', updateMaxPanelHeight);
  await fetchSymbols();
  await nextTick();
  initChart();
  if (chart.value && candleSeries.value) {
    jumpToNow();
  }
  nextTick(() => updateTrendMiniChart());
  watch(() => appStore.isDarkMode, () => {
    applyChartTheme();
  });
  document.addEventListener("mousedown", onDocumentClickForIndicator);
  // 添加键盘事件监听（用于取消绘制和删除趋势线）
  window.addEventListener("keydown", handleKeyDown);
  // 添加全屏状态监听
  document.addEventListener("fullscreenchange", handleFullscreenChange);
  document.addEventListener("webkitfullscreenchange", handleFullscreenChange);
  document.addEventListener("mozfullscreenchange", handleFullscreenChange);
  document.addEventListener("MSFullscreenChange", handleFullscreenChange);
  // 添加点击外部关闭右键菜单的监听
  document.addEventListener("click", handleDocumentClick);
  document.addEventListener("contextmenu", handleDocumentContextMenu);
  // 连接 WebSocket 并订阅 K 线数据
  try {
    // 注册 K 线更新处理器（在连接前注册）
    klineWebSocket.on("kline_update", handleKLineUpdate);
    // 先订阅（会存储在 subscriptions 中，连接成功后会重新订阅）
    const backendInterval = convertIntervalToBackend(currentInterval.value);
    klineWebSocket.subscribe(selectedSymbol.value, backendInterval);
    // 然后连接（连接成功后会重新订阅）
    klineWebSocket.connect();
  } catch (error) {
    // WebSocket 连接失败，将使用 REST API 模式
    console.warn("WebSocket 连接失败:", error);
  }
  // 连接 AI 信号 WebSocket（独立连接，不受底部标签页切换影响）
  connectSignalWebSocket();
  // 加载策略列表（用于回测）
  loadStrategies();
  loadBacktestTypes();
  loadBots();
  connectBacktestWebSocket();
  await loadBots();
  if (robots.value && robots.value.length > 0) {
    selectedBotId.value = robots.value[0].botId || robots.value[0].id;
  }
  // 如果切换到订单信息tab，加载订单
  if (bottomActiveTab.value === "orders") {
    loadOrders();
  } else if (bottomActiveTab.value === "positions") {
    loadPositionsOrders();
  }
  // 监听tab切换
  watch(bottomActiveTab, (newTab) => {
    if (newTab === "orders") {
      ordersPage.value = 0;
      loadOrders();
    } else if (newTab === "positions") {
      loadPositionsOrders();
    } else if (
      newTab === "backtest" &&
      backtestSubTab.value === "backtest-records"
    ) {
      loadBacktestRecords();
    }
  });
  watch(backtestSubTab, (newSubTab) => {
    if (newSubTab === "backtest-records") {
      loadBacktestRecords();
    }
  });
  watch(() => manualOrderForm.side, (newSide) => {
    if (!selectedSymbol.value) return;
    const entryPrice = dataCache.value.length > 0
      ? dataCache.value[dataCache.value.length - 1].close
      : manualOrderContextPrice.value;
    if (entryPrice == null) return;
    computeSmcTpSl(selectedSymbol.value, entryPrice, newSide).then((levels) => {
      if (levels) {
        orderTpPrice.value = levels.tp;
        orderSlPrice.value = levels.sl;
      }
    });
  });
  // 监听底部面板高度变化，更新图表尺寸
  watch(bottomPanelHeight, () => {
    // 使用 requestAnimationFrame 确保在布局更新后计算尺寸
    requestAnimationFrame(() => {
      updateContainerSize();
    });
  });
  // 监听窗口大小变化
  window.addEventListener("resize", handleWindowResize);
  // 启动 RAF 循环更新价格轴标签位置（参考 lightweight-charts-ui-main）
  const updatePriceAxisLabelPosition = () => {
    if (
      candleSeries.value &&
      lastPrice.value !== null &&
      chartContainer.value &&
      priceAxisLabel.value
    ) {
      try {
        const coordinate = candleSeries.value.priceToCoordinate(
          lastPrice.value,
        );
        if (coordinate !== null) {
          // 由于标签现在放在图表容器内部，坐标可以直接使用，但需要加上 padding top
          const chartPaddingTop = 10; // 图表配置中的 padding.top
          // 微调偏移量，确保标签在价格线中间（与 updateCurrentPriceLine 中的值保持一致）
          const fineTuneOffset = 6; // 标签在价格线上方，向下调整
          const relativeTop = coordinate + chartPaddingTop + fineTuneOffset;
          priceAxisLabel.value.top = relativeTop;
        }
      } catch (error) {
        // 忽略错误
      }
    }
    priceAxisLabelRafId = requestAnimationFrame(updatePriceAxisLabelPosition);
  };
  priceAxisLabelRafId = requestAnimationFrame(updatePriceAxisLabelPosition);
});
// 窗口resize处理函数
function handleWindowResize() {
  updateContainerSize();
  if (!rightPanelCollapsed.value) {
    trendMiniChart?.resize();
  }
}
onBeforeUnmount(() => {
  // 移除键盘事件监听
  window.removeEventListener("keydown", handleKeyDown);
  // 移除全屏状态监听
  document.removeEventListener("fullscreenchange", handleFullscreenChange);
  document.removeEventListener(
    "webkitfullscreenchange",
    handleFullscreenChange,
  );
  document.removeEventListener("mozfullscreenchange", handleFullscreenChange);
  document.removeEventListener("MSFullscreenChange", handleFullscreenChange);
  // 移除右键菜单监听
  document.removeEventListener("click", handleDocumentClick);
  document.removeEventListener("contextmenu", handleDocumentContextMenu);
  // 停止 RAF 循环
  if (priceAxisLabelRafId !== null) {
    cancelAnimationFrame(priceAxisLabelRafId);
    priceAxisLabelRafId = null;
  }
  // 清理窗口resize监听器
  window.removeEventListener("resize", handleWindowResize);
  window.removeEventListener("resize", updateMaxPanelHeight);
  // 清理拖拽事件监听器
  if (isResizing.value) {
    stopResize();
  }
  // 清理右侧面板拖拽事件监听器
  if (isRightPanelResizing.value) {
    stopRightPanelResize();
  }
  // 清理回测轮询定时器
  if (backtestPollTimer) {
    clearTimeout(backtestPollTimer);
    backtestPollTimer = null;
  }
  // 清理闪烁标记定时器
  if (flashMarkerTimer) {
    clearInterval(flashMarkerTimer);
    flashMarkerTimer = null;
  }
  if (flashMarkerTimeout) {
    clearTimeout(flashMarkerTimeout);
    flashMarkerTimeout = null;
  }
  // 清理防抖定时器
  if (loadDebounceTimer) {
    clearTimeout(loadDebounceTimer);
    loadDebounceTimer = null;
  }
  // 清理信号加载定时器
  if (signalLoadDebounceTimer) {
    clearTimeout(signalLoadDebounceTimer);
    signalLoadDebounceTimer = null;
  }
  // 取消 WebSocket 订阅并断开连接
  try {
    const backendInterval = convertIntervalToBackend(currentInterval.value);
    klineWebSocket.unsubscribe(selectedSymbol.value, backendInterval);
    klineWebSocket.off("kline_update");
    klineWebSocket.disconnect();
  } catch (error) {
    // WebSocket 断开连接失败
  }
  disconnectBacktestWebSocket();
  disconnectTradeLogsWebSocket();
  if (resizeObserver) {
    resizeObserver.disconnect();
    resizeObserver = null;
  }
  document.removeEventListener("mousedown", onDocumentClickForIndicator);
  if (seriesMarkersRef.value) {
    try {
      seriesMarkersRef.value.detach();
    } catch (_) {}
    seriesMarkersRef.value = null;
  }
  if (trendMiniChart) {
    trendMiniChart.dispose();
    trendMiniChart = null;
  }
  if (chart.value) {
    if (macdSeriesRef.value.macd) {
      try {
        chart.value.removeSeries(macdSeriesRef.value.macd);
      } catch (_) {}
      try {
        macdSeriesRef.value.signal &&
          chart.value.removeSeries(macdSeriesRef.value.signal);
      } catch (_) {}
      try {
        macdSeriesRef.value.histogram &&
          chart.value.removeSeries(macdSeriesRef.value.histogram);
      } catch (_) {}
      macdSeriesRef.value = { macd: null, signal: null, histogram: null };
    }
    if (rsiSeriesRef.value || rsiSignalSeriesRef.value) {
      try {
        rsiSignalSeriesRef.value &&
          chart.value.removeSeries(rsiSignalSeriesRef.value);
      } catch (_) {}
      try {
        rsiSeriesRef.value && chart.value.removeSeries(rsiSeriesRef.value);
      } catch (_) {}
      rsiSignalSeriesRef.value = null;
      rsiSeriesRef.value = null;
      rsiLevelLinesRef.value = [];
    }
    try {
      clearTSM();
    } catch (_) {}
    try {
      clearAndeanOscillator();
    } catch (_) {}
    try {
      clearMultiTimeframeTrend();
    } catch (_) {}
    try {
      smcIndicatorApi.destroy(chart.value);
    } catch (_) {}
    chart.value.remove();
    chart.value = null;
    candleSeries.value = null;
  }
});
</script>
<style scoped lang="scss">
/* 外层包装容器 */
.market-kline-wrapper {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100%;
  overflow: hidden;
  position: relative;
}
/* 确保没有白色背景 */
.market-kline-v1 {
  display: flex;
  flex-direction: column;
  flex: 1 1 0%;
  min-height: 0;
  width: 100%;
  background: var(--mk-bg-primary) !important;
  color: var(--mk-text-primary);
  overflow: hidden;
  margin: 0;
  padding: 0;
}
/* 确保所有子元素也是深色背景 */
.market-kline-v1 * {
  box-sizing: border-box;
}
/* 覆盖可能的父容器白色背景 */
:deep(.tab-content) {
  background: var(--mk-bg-primary) !important;
  height: 100%;
  border: none !important;
}
:deep(.el-scrollbar__wrap) {
  background: var(--mk-bg-primary) !important;
}
/* 确保 tab-content 也是浅色背景 */
:deep(.tab-content) {
  background: var(--mk-bg-primary) !important;
}
/* 顶部工具栏 */
.top-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 16px;
  background: var(--mk-bg-secondary);
  border-bottom: 1px solid var(--mk-border);
  flex-shrink: 0;
  height: var(--mk-height-topbar);
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 24px;
}
.symbol-section {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--mk-radius-sm);
  cursor: pointer;
  transition: background 0.2s;
}
.symbol-section:hover {
  background: var(--mk-bg-hover);
}
.search-icon,
.add-icon {
  font-size: var(--mk-font-lg);
  color: var(--mk-text-secondary);
  cursor: pointer;
}
.symbol-name {
  font-weight: 500;
  font-size: var(--mk-font-base);
  color: var(--mk-text-primary);
}
/* 交易对搜索弹窗样式 */
.symbol-search-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
}
.symbol-search-modal {
  width: 600px;
  height: 500px;
  background-color: var(--mk-bg-primary);
  border-radius: var(--mk-radius-lg);
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}
.symbol-search-header {
  padding: 16px;
  border-bottom: 1px solid var(--mk-border);
  display: flex;
  align-items: center;
  gap: 12px;
}
.symbol-search-container {
  flex: 1;
  display: flex;
  align-items: center;
  background-color: var(--mk-bg-secondary);
  border-radius: var(--mk-radius-sm);
  padding: 0 12px;
  height: 40px;
  border: 1px solid transparent;
  transition: border-color 0.2s;
}
.symbol-search-container:focus-within {
  border-color: var(--mk-color-brand);
  background-color: var(--mk-bg-primary);
}
.symbol-search-icon {
  font-size: var(--mk-font-lg);
  color: var(--mk-text-secondary);
  margin-right: 8px;
}
.symbol-search-input {
  flex: 1;
  background: transparent;
  border: none;
  color: var(--mk-text-primary);
  font-size: var(--mk-font-lg);
  outline: none;
}
.symbol-search-input::placeholder {
  color: var(--mk-text-tertiary);
}
.symbol-search-clear {
  font-size: var(--mk-font-lg);
  color: var(--mk-text-secondary);
  cursor: pointer;
  margin-left: 8px;
}
.symbol-search-clear:hover {
  color: var(--mk-text-primary);
}
.symbol-search-close {
  font-size: var(--mk-font-xxl);
  color: var(--mk-text-secondary);
  cursor: pointer;
  padding: 4px;
}
.symbol-search-close:hover {
  color: var(--mk-text-primary);
}
.symbol-search-list-header {
  display: flex;
  padding: 8px 16px;
  border-bottom: 1px solid var(--mk-border);
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-sm);
}
.symbol-search-col-symbol {
  flex: 1;
}
.symbol-search-col-exchange {
  width: 80px;
  text-align: center;
}
.symbol-search-col-type {
  width: 80px;
  text-align: right;
}
.symbol-search-list {
  flex: 1;
  overflow-y: auto;
}
.symbol-search-item {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--mk-border);
  transition: background-color 0.2s;
}
.symbol-search-item:hover {
  background-color: var(--mk-bg-secondary);
}
.symbol-search-item.active {
  background-color: var(--mk-bg-hover);
}
.symbol-search-item-symbol {
  flex: 1;
  font-weight: 600;
  color: var(--mk-text-primary);
}
.symbol-search-base {
  color: var(--mk-text-primary);
}
.symbol-search-quote {
  color: var(--mk-text-secondary);
  font-size: 0.9em;
  margin-left: 4px;
}
.symbol-search-item-exchange {
  width: 80px;
  text-align: center;
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-sm);
}
.symbol-search-group-header {
  padding: 6px 16px;
  background-color: var(--mk-bg-tertiary);
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-sm);
  font-weight: 600;
  border-bottom: 1px solid var(--mk-border);
}
.symbol-search-item-type {
  width: 80px;
  text-align: right;
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-md);
}
.symbol-search-check {
  width: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--mk-color-brand);
  margin-left: 8px;
  font-size: var(--mk-font-lg);
}
.interval-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
}
.time-jump-section {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 16px;
}
.interval-btn {
  padding: 4px 12px;
  border-radius: var(--mk-radius-sm);
  border: none;
  background: transparent;
  color: var(--mk-text-tertiary);
  font-size: var(--mk-font-md);
  cursor: pointer;
  transition: all 0.2s;
}
.interval-btn:hover:not(:disabled) {
  background: var(--mk-bg-hover);
  color: var(--mk-color-brand);
}
.interval-btn.active {
  background: var(--mk-color-brand);
  color: #fff;
}
.interval-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
/* 绘图工具栏样式 */
.drawing-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 16px;
  padding: 4px;
  border-left: 1px solid var(--mk-border);
  padding-left: 16px;
}
.tool-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--mk-border-input);
  border-radius: var(--mk-radius-sm);
  background-color: var(--mk-bg-primary);
  cursor: pointer;
  transition: all 0.2s;
  color: var(--mk-text-tertiary);
}
.tool-btn:hover {
  background-color: var(--mk-bg-secondary);
  border-color: var(--mk-text-tertiary);
  color: var(--mk-color-brand);
}
.tool-btn.active {
  background-color: var(--mk-bg-hover);
  border-color: var(--mk-color-brand);
  color: var(--mk-color-brand);
}
.tool-btn svg {
  width: 20px;
  height: 20px;
}
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 指标按钮与下拉（模仿 lightweight-charts-ui Topbar.indicatorBtn / indicatorDropdown） */
.indicator-btn-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.indicator-trigger-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 8px;
  border-radius: var(--mk-radius-sm);
  color: var(--mk-text-tertiary);
  font-size: var(--mk-font-base);
  font-weight: 500;
}
.indicator-trigger-btn:hover {
  background: var(--mk-bg-hover);
  color: var(--mk-color-brand);
}
.indicator-trigger-text {
  margin-left: 2px;
}
.indicator-dropdown {
  position: fixed;
  z-index: 2000;
  min-width: 260px;
  padding: 6px 0;
  background: var(--mk-bg-primary);
  border: 1px solid var(--mk-border);
  border-radius: var(--mk-radius-sm);
  box-shadow: var(--mk-shadow-dropdown);
}
.indicator-dropdown-section-title {
  padding: 6px 16px 4px;
  font-size: var(--mk-font-sm);
  color: var(--mk-text-secondary);
  font-weight: 600;
}
.indicator-params-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 6px 16px 10px;
  background: var(--mk-bg-tertiary);
  border-bottom: 1px solid var(--mk-border-light);
}
.indicator-params-row .indicator-param-label {
  font-size: var(--mk-font-sm);
  color: var(--mk-text-tertiary);
  min-width: 28px;
}
.indicator-params-row .indicator-param-input {
  width: 72px;
}
.indicator-params-row .indicator-param-input :deep(.el-input-number) {
  width: 72px;
}
.indicator-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  cursor: pointer;
  color: var(--mk-text-primary);
  font-size: var(--mk-font-md);
  background: var(--mk-bg-primary);
}
.indicator-dropdown-item .el-checkbox {
  margin-right: 0;
  height: auto;
}
.indicator-dropdown-item .el-checkbox :deep(.el-checkbox__label) {
  padding-left: 0;
  display: none;
}
.indicator-dropdown-item-text {
  flex: 1;
}
.indicator-dropdown-item:hover {
  background: var(--mk-bg-secondary);
}
.indicator-dropdown-item.active {
  color: var(--mk-color-brand);
  background: rgba(64, 158, 255, 0.06);
}
/* 主内容区域 - 使用独立类名避免与父容器冲突 */
.mk-main-content {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--mk-bg-primary);
  border: none;
  align-items: stretch;
}
/* 左侧工具栏 */
.left-toolbar {
  width: var(--mk-width-left-toolbar);
  background: var(--mk-bg-secondary);
  border-right: 1px solid var(--mk-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
  gap: 4px;
  flex: 0 0 auto;
  align-self: stretch;
}
.tool-item {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--mk-radius-sm);
  cursor: pointer;
  color: var(--mk-text-tertiary);
  transition: all 0.2s;
}
.tool-item:hover {
  background: var(--mk-bg-hover);
  color: var(--mk-color-brand);
}
.tool-item.active {
  background: var(--mk-color-brand);
  color: var(--mk-bg-primary);
}
.tool-item.active:hover {
  background: #66b1ff;
  color: var(--mk-bg-primary);
}
/* 图表区域 */
.chart-wrapper {
  flex: 1 1 0%;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--mk-bg-primary) !important;
  width: 100%;
  align-self: stretch;
}
.chart-area {
  flex: 1;
  position: relative;
  min-height: 0;
  overflow: hidden;
  width: 100%;
  height: 100%;
  background: var(--mk-bg-primary) !important;
  padding-bottom: 0; /* 确保没有额外的底部 padding */
}
.chart-area.chart-area-suppressed {
  pointer-events: none;
}
.chart-container {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  padding: 0;
  border: none;
  background: var(--mk-bg-primary);
  border-radius: 0;
}
.chart-area :deep(.chart-container)::before {
  content: none;
  display: none;
}
/* 右侧面板容器（flex 布局容纳多个滑出面板 + 分析面板 + 图标栏） */
.right-panels-container {
  display: flex;
  flex-direction: row;
  flex: 0 0 auto;
  min-height: 0;
  overflow: hidden;
  align-self: stretch;
}
.slide-panel {
  background: var(--mk-bg-secondary);
  border-left: 1px solid var(--mk-border);
  display: flex;
  flex-direction: column;
  width: 320px;
  flex-shrink: 0;
  overflow: hidden;
}
.slide-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--mk-space-4) var(--mk-space-8);
  background: var(--mk-bg-primary);
  border-bottom: 1px solid var(--mk-border);
}
.slide-panel-title {
  font-size: var(--mk-font-md);
  font-weight: 600;
  color: var(--mk-text-primary);
}
.slide-panel-close {
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  font-size: var(--mk-font-base);
  color: var(--mk-text-secondary);
  cursor: pointer;
  border-radius: var(--mk-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}
.slide-panel-close:hover {
  background: var(--mk-bg-tertiary);
  color: var(--mk-text-tertiary);
}
.slide-panel-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.slide-panel-clear {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: var(--mk-font-base);
  color: var(--mk-text-secondary);
  cursor: pointer;
  border-radius: var(--mk-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}
.slide-panel-clear:hover {
  background: var(--mk-bg-tertiary);
  color: var(--mk-text-tertiary);
}
.slide-panel-body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
/* 右侧面板 */
.right-panel {
  position: relative;
  background: var(--mk-bg-secondary);
  border-left: 1px solid var(--mk-border);
  display: flex;
  flex-direction: row;
  flex: 0 0 auto;
  min-height: 0;
  overflow: hidden;
  align-self: stretch;
}
/* 右侧面板左侧拖拽分隔条 */
.right-panel-resizer {
  width: 5px;
  border-left: 1px solid var(--mk-border);
  border-right: 1px solid var(--mk-border);
  cursor: col-resize;
  position: relative;
  user-select: none;
  transition: border-color 0.2s;
  flex-shrink: 0;
  z-index: 10;
}
.right-panel-resizer.resizing {
  border-color: var(--mk-color-brand);
}
.resizer-handle-vertical {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  height: 40px;
  width: 2px;
  background: var(--mk-text-tertiary);
  border-radius: 1px;
  transition: background 0.2s;
}
.right-panel-resizer:hover .resizer-handle-vertical {
  background: var(--mk-text-primary);
}
.right-panel-resizer.resizing .resizer-handle-vertical {
  background: #ffffff;
}
/* 右侧面板内容区域 */
.right-panel-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
.analysis-tabs {
  display: flex;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--mk-border);
  background: var(--mk-bg-secondary);
}
.analysis-tab-btn {
  flex: 1;
  border: 1px solid var(--mk-border-input);
  background: var(--mk-bg-primary);
  border-radius: var(--mk-radius-md);
  font-size: var(--mk-font-sm);
  padding: 6px 4px;
  color: var(--mk-text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
}
.analysis-tab-btn.active {
  border-color: var(--mk-color-brand);
  color: var(--mk-color-brand);
  background: rgba(64, 158, 255, 0.08);
}
.analysis-tab-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px;
}
.smc-tab-content {
  padding-bottom: 72px;
}
.analysis-tab-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.trend-state-card {
  background: var(--mk-bg-primary);
  border-radius: var(--mk-radius-xl);
  padding: 12px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.trend-state-title {
  font-size: var(--mk-font-md);
  font-weight: 600;
  color: #1f2937;
}
.trend-state-row {
  display: flex;
  align-items: center;
}
.trend-state-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: var(--mk-font-md);
  font-weight: 500;
}
.trend-state-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}
.trend-up {
  color: var(--mk-color-up);
  background: rgba(34, 197, 94, 0.12);
}
.trend-down {
  color: var(--mk-color-down);
  background: rgba(239, 68, 68, 0.12);
}
.trend-sideways {
  color: #64748b;
  background: rgba(148, 163, 184, 0.18);
}
.trend-slope-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.trend-slope-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: var(--mk-font-sm);
}
.trend-slope-label {
  color: #94a3b8;
}
.trend-slope-value {
  font-size: var(--mk-font-md);
  font-weight: 600;
}
.trend-slope-unit {
  font-size: var(--mk-font-sm);
  color: #94a3b8;
}
.slope-up {
  color: var(--mk-color-up);
}
.slope-down {
  color: var(--mk-color-down);
}
.slope-flat {
  color: #64748b;
}
.trend-desc-row {
  display: flex;
  gap: 6px;
  font-size: var(--mk-font-sm);
  color: #475569;
  line-height: 1.5;
}
.trend-desc-label {
  color: #94a3b8;
  flex-shrink: 0;
}
.trend-desc-text {
  flex: 1;
}
.trend-score {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.trend-score-header {
  font-size: var(--mk-font-sm);
  color: #64748b;
}
.trend-score-bar {
  height: 6px;
  border-radius: 999px;
  background: #e2e8f0;
  overflow: hidden;
}
.trend-score-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #60a5fa, #4f46e5);
}
.trend-score-value {
  font-size: var(--mk-font-sm);
  color: #475569;
}
.levels-card {
  background: var(--mk-bg-primary);
  border-radius: var(--mk-radius-xl);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.06);
}
.levels-title {
  font-size: var(--mk-font-md);
  font-weight: 600;
  color: #1f2937;
}
.level-list {
  display: flex;
  flex-direction: column;
  gap: var(--mk-space-8);
}
.level-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: var(--mk-font-sm);
  color: #475569;
}
.level-row-label {
  color: #94a3b8;
  flex-shrink: 0;
}
.level-row-value {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #111827;
}
.level-row-value-multiline {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  text-align: right;
}
.smc-ob-title {
  display: block;
}
.smc-ob-range {
  display: block;
  font-weight: 600;
}
.level-distance-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: var(--mk-font-sm);
  color: #475569;
  padding-left: 52px;
}
.level-distance-label {
  color: #94a3b8;
  flex-shrink: 0;
}
.level-distance-value {
  font-weight: 600;
}
.copy-btn {
  border: none;
  background: rgba(148, 163, 184, 0.16);
  color: #64748b;
  width: 26px;
  height: 26px;
  border-radius: var(--mk-radius-md);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.copy-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.level-distance {
  font-size: var(--mk-font-sm);
}
.distance-positive {
  color: var(--mk-color-up);
}
.distance-negative {
  color: var(--mk-color-down);
}
.future-projections {
  background: var(--mk-bg-secondary);
  border-radius: var(--mk-radius-lg);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.future-header {
  font-size: var(--mk-font-sm);
  color: #64748b;
}
.future-table {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.future-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  font-size: var(--mk-font-sm);
  color: #475569;
}
.future-header-row {
  font-weight: 600;
  color: #1f2937;
}
.future-support {
  color: var(--mk-color-up);
}
.future-resistance {
  color: var(--mk-color-down);
}
.future-more {
  border: none;
  background: rgba(59, 130, 246, 0.1);
  color: #2563eb;
  border-radius: var(--mk-radius-md);
  padding: 6px 8px;
  font-size: var(--mk-font-sm);
  cursor: pointer;
  align-self: flex-start;
}
.price-alert {
  background: var(--mk-bg-primary);
  border-radius: var(--mk-radius-xl);
  padding: 8px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--mk-font-sm);
  color: #475569;
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.06);
}
.mini-chart-toolbar {
  display: flex;
  gap: 6px;
}
.mini-chart-btn {
  flex: 1;
  border: 1px solid var(--mk-border-input);
  background: var(--mk-bg-primary);
  border-radius: var(--mk-radius-md);
  font-size: var(--mk-font-sm);
  padding: 6px 4px;
  color: var(--mk-text-tertiary);
  cursor: pointer;
  transition: all 0.2s;
}
.mini-chart-btn.active {
  border-color: var(--mk-color-brand);
  color: var(--mk-color-brand);
  background: rgba(64, 158, 255, 0.08);
}
.mini-chart {
  height: 180px;
  border-radius: var(--mk-radius-xl);
  background: var(--mk-bg-primary);
  box-shadow: 0 4px 10px rgba(15, 23, 42, 0.06);
}
.mini-chart-note {
  font-size: var(--mk-font-sm);
  color: #64748b;
  text-align: right;
}
.panel-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--mk-border);
  background: var(--mk-bg-secondary);
}
.panel-title {
  font-weight: 500;
  font-size: var(--mk-font-base);
  color: var(--mk-text-primary);
}
.panel-actions {
  display: flex;
  align-items: center;
  gap: var(--mk-space-8);
}
.collapse-toggle-btn {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: var(--mk-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.collapse-toggle-btn:hover {
  color: var(--mk-color-brand);
}
.right-panel.collapsed {
  overflow: hidden;
  min-width: 0;
}
.right-panel-content {
  height: 100%;
}
.right-panel-body {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.right-iconbar {
  width: 40px;
  background: var(--mk-bg-primary);
  border-left: 1px solid var(--mk-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
  gap: 8px;
  flex-shrink: 0;
}
.right-icon-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--mk-radius-lg);
  border: 1px solid transparent;
  background: transparent;
  color: var(--mk-text-tertiary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.right-icon-btn :deep(.el-icon) {
  font-size: var(--mk-font-xxxl);
}
.right-icon-btn:hover {
  background: var(--mk-bg-secondary);
}
.right-icon-btn.active {
  background: rgba(64, 158, 255, 0.2);
  border-color: rgba(64, 158, 255, 0.35);
  color: var(--mk-color-brand);
}
.qt-panel-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--mk-space-4) var(--mk-space-8);
  background: var(--mk-bg-tertiary);
}
.qt-section {
  background: var(--mk-bg-primary);
  border-radius: var(--mk-radius-lg);
  padding: var(--mk-space-4) var(--mk-space-8);
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.qt-label {
  font-size: var(--mk-font-xs);
  color: #8c8c8c;
  font-weight: 500;
}
.qt-price-row {
  display: flex;
  align-items: center;
}
.qt-current-price {
  font-size: var(--mk-font-xl);
  font-weight: 700;
  color: #0f172a;
  letter-spacing: 0.02em;
}
.qt-direction-toggle {
  display: flex;
  gap: 6px;
}
.qt-dir-btn {
  flex: 1;
  padding: 4px 8px;
  text-align: center;
  border-radius: var(--mk-radius-sm);
  font-weight: 600;
  font-size: var(--mk-font-sm);
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  user-select: none;
}
.qt-dir-long {
  color: var(--mk-color-up);
  background: rgba(82, 196, 26, 0.06);
  border-color: rgba(82, 196, 26, 0.2);
}
.qt-dir-long.active {
  background: var(--mk-color-up);
  color: var(--mk-text-inverse);
  border-color: var(--mk-color-up);
  box-shadow: 0 2px 6px rgba(82, 196, 26, 0.25);
}
.qt-dir-long:hover:not(.active) {
  border-color: var(--mk-color-up);
}
.qt-dir-short {
  color: var(--mk-color-down);
  background: rgba(245, 34, 45, 0.06);
  border-color: rgba(245, 34, 45, 0.2);
}
.qt-dir-short.active {
  background: var(--mk-color-down);
  color: var(--mk-text-inverse);
  border-color: var(--mk-color-down);
  box-shadow: 0 2px 6px rgba(245, 34, 45, 0.25);
}
.qt-dir-short:hover:not(.active) {
  border-color: var(--mk-color-down);
}
.qt-submit-section {
  padding: 2px 0;
}
.qt-submit-btn {
  width: 100%;
  height: 32px;
  border: none;
  border-radius: var(--mk-radius-md);
  font-size: var(--mk-font-sm);
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s;
  color: var(--mk-text-inverse);
}
.qt-submit-btn.qt-btn-long {
  background: var(--mk-color-up);
  box-shadow: 0 2px 6px rgba(82, 196, 26, 0.25);
}
.qt-submit-btn.qt-btn-long:hover {
  background: #73d13d;
}
.qt-submit-btn.qt-btn-short {
  background: var(--mk-color-down);
  box-shadow: 0 2px 6px rgba(245, 34, 45, 0.25);
}
.qt-submit-btn.qt-btn-short:hover {
  background: #ff4d4f;
}
.qt-amount-block {
  gap: 6px;
}
.qt-quick-amounts {
  display: flex;
  gap: 4px;
}
.qt-pct-btn {
  flex: 1;
  height: 24px;
  border: 1px solid var(--mk-border-input);
  border-radius: var(--mk-radius-sm);
  background: var(--mk-bg-primary);
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-xs);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.qt-pct-btn:hover:not(:disabled) {
  border-color: var(--mk-color-brand);
  color: var(--mk-color-brand);
}
.qt-pct-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.qt-card {
  gap: var(--mk-space-8);
}
.qt-section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.qt-section-title {
  font-size: var(--mk-font-sm);
  font-weight: 600;
  color: #262626;
}
.qt-optional-tag {
  font-size: var(--mk-font-xs);
  color: #8c8c8c;
}
.qt-mode-toggle {
  display: flex;
  border-radius: var(--mk-radius-sm);
  overflow: hidden;
  border: 1px solid var(--mk-border-input);
}
.qt-mode-toggle-item {
  padding: 1px 8px;
  font-size: var(--mk-font-xs);
  cursor: pointer;
  background: var(--mk-bg-primary);
  color: var(--mk-text-secondary);
  transition: all 0.2s;
  user-select: none;
}
.qt-mode-toggle-item.active {
  background: var(--mk-color-brand);
  color: var(--mk-text-inverse);
}
.qt-leverage-row {
  display: flex;
  align-items: center;
  gap: 2px;
}
.qt-label-spaced {
  margin-top: 2px;
}
.qt-hint-text {
  font-size: var(--mk-font-xs);
  color: #8c8c8c;
  line-height: 1.4;
}
.qt-spot-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #8c8c8c;
}
.qt-tpsl-row {
  display: flex;
  gap: 6px;
}
.qt-tpsl-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.qt-tp-label {
  color: var(--mk-color-up) !important;
}
.qt-sl-label {
  color: var(--mk-color-down) !important;
}
/* 盈亏比行 */
.order-rr-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px;
  font-size: var(--mk-font-xs);
}
.order-rr-label {
  color: #8c8c8c;
}
.order-rr-value {
  font-weight: 600;
  color: var(--mk-text-primary);
}
/* 开单面板内紧凑的表单控件 */
.slide-panel-order .el-input-number--small {
  width: 100%;
}
.slide-panel-order .el-input-number--small .el-input-number__increase,
.slide-panel-order .el-input-number--small .el-input-number__decrease {
  width: 20px;
}
.slide-panel-order .el-input-number--small .el-input__wrapper {
  padding-left: 4px;
  padding-right: 4px;
}
.slide-panel-order .el-input-number--small .el-input__inner {
  height: 28px;
  font-size: var(--mk-font-sm);
}
.slide-panel-order .el-slider--small {
  height: 4px;
}
.slide-panel-order .el-slider__runway {
  height: 4px;
}
.slide-panel-order .el-slider__bar {
  height: 4px;
}
.slide-panel-order .el-slider__button-wrapper {
  top: -13px;
}
.slide-panel-order .el-slider .el-input-number--small .el-input__inner {
  height: 24px;
}

.menu-icon {
  font-size: var(--mk-font-lg);
  color: var(--mk-text-secondary);
  cursor: pointer;
}
.watchlist {
  flex: 1;
  overflow-y: auto;
}
.watchlist-header {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 8px;
  padding: 8px 16px;
  font-size: var(--mk-font-sm);
  color: var(--mk-text-secondary);
  border-bottom: 1px solid var(--mk-border);
  background: var(--mk-bg-tertiary);
}
.watchlist-item {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 8px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid var(--mk-border-light);
}
.watchlist-item:hover {
  background: var(--mk-bg-secondary);
}
.watchlist-item .symbol {
  font-weight: 500;
  font-size: var(--mk-font-md);
  color: var(--mk-text-primary);
}
.watchlist-item .price {
  font-size: var(--mk-font-md);
  text-align: right;
}
.watchlist-item .change {
  font-size: var(--mk-font-md);
  text-align: right;
}
.watchlist-item .change-pct {
  font-size: var(--mk-font-md);
  text-align: right;
}
.up {
  color: #26a69a;
}
.down {
  color: #ef5350;
}
/* 底部工具栏 */
.bottom-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--mk-bg-secondary);
  border-top: 1px solid var(--mk-border);
  flex-shrink: 0;
  height: var(--mk-height-bottombar);
}
.bottom-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.timezone {
  font-size: var(--mk-font-sm);
  color: var(--mk-text-secondary);
  cursor: pointer;
}
.bottom-separator {
  width: 1px;
  height: 16px;
  background-color: var(--mk-border);
  flex-shrink: 0;
}
.bottom-btn {
  padding: 2px 8px;
  border: none;
  background: transparent;
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-sm);
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: color 0.15s;
}
.bottom-btn:hover {
  color: var(--mk-color-brand);
}
.loading-overlay,
.error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--mk-bg-secondary);
  z-index: 10;
  gap: 12px;
}
.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid rgba(38, 166, 154, 0.2);
  border-top-color: #26a69a;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.error-message {
  color: #ef5350;
  font-size: var(--mk-font-base);
  margin-bottom: 0;
}
.error-overlay button {
  padding: 6px 16px;
  border-radius: var(--mk-radius-sm);
  border: none;
  background: var(--mk-color-brand);
  color: var(--mk-bg-primary);
  font-size: var(--mk-font-md);
  cursor: pointer;
}
.error-overlay button:hover {
  background: #66b1ff;
}
/* 可拖拽分隔条（上下方向，用于K线图和底部tab之间） */
.panel-resizer {
  height: 5px;
  border-top: 1px solid var(--mk-border);
  border-bottom: 1px solid var(--mk-border);
  cursor: row-resize;
  position: relative;
  user-select: none;
  transition: border-color 0.2s;
  flex-shrink: 0;
  z-index: 10;
}
.panel-resizer.suppressed {
  opacity: 0;
  pointer-events: none;
}
.panel-resizer.resizing {
  border-color: var(--mk-color-brand);
}
.resizer-handle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 40px;
  height: 2px;
  background: var(--mk-text-tertiary);
  border-radius: 1px;
  transition: background 0.2s;
}
.panel-resizer:hover .resizer-handle {
  background: var(--mk-text-primary);
}
.panel-resizer.resizing .resizer-handle {
  background: #ffffff;
}
/* ========== 底部Tab面板样式 ========== */
/* 底部Tab面板容器 */
.bottom-tab-panel-container {
  display: flex;
  flex-direction: column;
  background: var(--mk-bg-primary);
  border-top: 1px solid var(--mk-border);
  flex: 0 0 auto;
  overflow: hidden;
  transition: none;
  will-change: height;
  position: relative;
  z-index: 1;
}
.bottom-tab-panel-container.suppressed {
  border-top-color: transparent;
}
/* Tab导航 */
.bottom-tabs-nav {
  display: flex;
  border-bottom: 1px solid var(--mk-border);
  background: var(--mk-bg-secondary);
  flex-shrink: 0;
}
.bottom-tab-btn {
  flex: 1;
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: var(--mk-text-tertiary);
  font-size: var(--mk-font-md);
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: color 0.15s;
  letter-spacing: -0.01em;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.bottom-tab-btn:hover {
  color: var(--mk-color-brand);
  background: var(--mk-bg-hover);
}
.bottom-tab-btn.active {
  color: var(--mk-color-brand);
  border-bottom-color: var(--mk-color-brand);
}
/* Tab内容 */
.bottom-tab-content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  background: var(--mk-bg-primary);
}
.section-subtitle {
  font-size: var(--mk-font-md);
  font-weight: 600;
  color: var(--mk-text-secondary);
  padding: 8px 0 4px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--mk-border);
}
.tab-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}
/* 回测子tab */
.backtest-sub-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--mk-border);
  padding-bottom: 8px;
  flex-shrink: 0;
}
.sub-tab-btn {
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: var(--mk-text-tertiary);
  font-size: var(--mk-font-base);
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
  border-radius: var(--mk-radius-sm);
}
.sub-tab-btn:hover {
  background: var(--mk-bg-hover);
  color: var(--mk-color-brand);
}
.sub-tab-btn.active {
  color: var(--mk-color-brand);
  background: var(--mk-bg-hover);
  font-weight: 600;
}
/* 回测运行面板 */
.backtest-run-panel {
  flex: 1;
  overflow-y: auto;
}
.backtest-form {
  margin-bottom: 24px;
  padding: 20px;
  background: var(--mk-bg-secondary);
  border: 1px solid var(--mk-border);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.form-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.form-row:last-of-type {
  margin-bottom: 0;
}
.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-item label {
  font-size: var(--mk-font-md);
  font-weight: 500;
  color: #374151;
  letter-spacing: 0.025em;
}
.form-control {
  width: 100%;
}
.form-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--mk-border);
}
.action-btn {
  min-width: 120px;
  font-weight: 500;
  transition: all 0.2s ease;
}
.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--mk-shadow-dropdown);
}
/* 响应式设计 */
@media (max-width: 768px) {
  .backtest-form {
    padding: 16px;
  }
  .form-row {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .form-actions {
    flex-direction: column;
    align-items: stretch;
  }
  .action-btn {
    width: 100%;
  }
}
.backtest-progress {
  margin: 20px 0;
}
.progress-message {
  margin-top: 8px;
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-sm);
}
.backtest-results {
  margin-top: 20px;
  padding: 16px;
  background: var(--mk-bg-secondary);
  border-radius: var(--mk-radius-sm);
  border: 1px solid var(--mk-border);
}
.backtest-results h4 {
  margin: 0 0 16px 0;
  color: var(--mk-text-primary);
  font-size: var(--mk-font-lg);
  font-weight: 700;
}
.results-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.result-item {
  display: flex;
  justify-content: space-between;
  padding: 8px;
  background: var(--mk-bg-primary);
  border-radius: var(--mk-radius-sm);
  border: 1px solid var(--mk-border);
}
.result-item label {
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-base);
}
.result-item span {
  color: var(--mk-text-primary);
  font-weight: 600;
}
/* 回测记录面板 */
.backtest-records-panel {
  flex: 1;
  overflow-y: auto;
}
.records-header {
  margin-bottom: 16px;
}
.records-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.record-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: var(--mk-bg-secondary);
  border-radius: var(--mk-radius-sm);
  border: 1px solid var(--mk-border);
}
.record-info {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: var(--mk-text-primary);
  font-size: var(--mk-font-base);
}
.record-actions {
  display: flex;
  gap: 8px;
}
/* 订单信息面板 */
.orders-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.orders-filters {
  display: flex;
  align-items: center;
  gap: 12px;
}
.orders-filters label {
  color: var(--mk-text-primary);
  font-size: var(--mk-font-base);
}

.positions-table-wrap,
.orders-table-wrap {
  flex: 1;
  min-height: 0;
}
.positions-table-wrap :deep(.el-table .cell),
.orders-table-wrap :deep(.el-table .cell) {
  padding: 0 6px;
  white-space: nowrap;
}
.positions-table-wrap :deep(.el-table__header .cell),
.orders-table-wrap :deep(.el-table__header .cell) {
  white-space: nowrap;
}
html.dark .positions-table-wrap :deep(.el-table),
html.dark .positions-table-wrap :deep(.el-table__body),
html.dark .positions-table-wrap :deep(.el-table__body tr),
html.dark .positions-table-wrap :deep(.el-table__body td),
html.dark .positions-table-wrap :deep(.el-table__body th),
html.dark .orders-table-wrap :deep(.el-table),
html.dark .orders-table-wrap :deep(.el-table__body),
html.dark .orders-table-wrap :deep(.el-table__body tr),
html.dark .orders-table-wrap :deep(.el-table__body td),
html.dark .orders-table-wrap :deep(.el-table__body th) {
  background-color: var(--mk-bg-hover);
}
html.dark .positions-table-wrap :deep(.el-table--border .el-table__inner-wrapper),
html.dark .positions-table-wrap :deep(.el-table--border .el-table__body-wrapper),
html.dark .orders-table-wrap :deep(.el-table--border .el-table__inner-wrapper),
html.dark .orders-table-wrap :deep(.el-table--border .el-table__body-wrapper) {
  border-color: var(--mk-border);
}
html.dark .positions-table-wrap :deep(.el-table__body tr:hover > td),
html.dark .orders-table-wrap :deep(.el-table__body tr:hover > td) {
  background-color: var(--mk-bg-tertiary);
}
html.dark .positions-table-wrap :deep(.el-table__body tr.el-table__row--striped td),
html.dark .orders-table-wrap :deep(.el-table__body tr.el-table__row--striped td) {
  background-color: var(--mk-bg-secondary);
}
.positions-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.profit-amount {
  font-weight: 500;
}
.profit-amount.positive {
  color: #00c853;
}
.profit-amount.negative {
  color: #ff1744;
}
.profit-percent {
  font-weight: 500;
  margin-left: 4px;
}
.profit-percent.positive {
  color: #00c853;
}
.profit-percent.negative {
  color: #ff1744;
}
.profit-percent.neutral {
  color: var(--mk-text-secondary);
}
.order-side {
  font-weight: 500;
}
.order-side.long {
  color: #00c853;
}
.order-side.short {
  color: #ff1744;
}
.order-status {
  font-weight: 500;
}
.order-status.status-gain {
  color: #00c853;
}
.order-status.status-loss {
  color: #ff1744;
}
.order-status.status-neutral {
  color: var(--mk-text-primary);
}
.orders-pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--mk-border);
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-base);
  flex-wrap: wrap;
}
.page-input-group {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--mk-font-base);
  color: var(--mk-text-primary);
}
.loading,
.no-data {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px;
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-base);
}
/* 时间跳转按钮样式 */
.time-jump-btn {
  background: none;
  border: none;
  color: var(--mk-color-brand);
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
  font-size: inherit;
  transition: color 0.2s;
}
.time-jump-btn:hover {
  color: #66b1ff;
}
.time-jump-btn:active {
  color: #337ecc;
}
/* K线信息栏（浮动在K线图左上角，类似 TradingView 十字光标浮层） */
.kline-header-info {
  position: absolute;
  top: 4px;
  left: 4px;
  z-index: 5;
  background: transparent;
  border: none;
  border-radius: 4px;
  padding: 3px 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  font-size: 11px;
  flex-shrink: 0;
  pointer-events: none;
  color: var(--mk-text-primary);
  font-family: "Consolas", "Monaco", "Courier New", monospace;
}
.kline-info-item {
  display: flex;
  align-items: center;
  font-family: "Consolas", "Monaco", "Courier New", monospace;
  gap: 4px;
}
.kline-info-item .label {
  color: var(--mk-text-tertiary);
  font-weight: 400;
  font-size: 11px;
}
.kline-info-item .value {
  font-weight: 600;
  font-size: 11px;
}
.kline-info-item .value.color-up {
  color: var(--mk-color-up);
}
.kline-info-item .value.color-down {
  color: var(--mk-color-down);
}
/* 右键菜单样式 */
.chart-context-menu {
  position: fixed;
  background: var(--mk-bg-primary);
  border: 1px solid var(--mk-border);
  border-radius: var(--mk-radius-sm);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  z-index: 10000;
  min-width: 160px;
  padding: 4px 0;
  font-size: var(--mk-font-base);
}
.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  cursor: pointer;
  color: var(--mk-text-tertiary);
  transition: background-color 0.2s;
}
.context-menu-item:hover {
  background-color: var(--mk-bg-secondary);
  color: var(--mk-color-brand);
}
.context-menu-item .el-icon {
  font-size: var(--mk-font-lg);
}
.context-menu-divider {
  height: 1px;
  background-color: var(--mk-border);
  margin: 4px 0;
}
/* 价格轴标签样式（参考 lightweight-charts-ui-main） */
.price-axis-label {
  position: absolute;
  right: 0;
  transform: translateY(-50%);
  z-index: 20;
  pointer-events: none;
  padding: 3px 6px;
  font-size: var(--mk-font-xs);
  font-family:
    -apple-system, BlinkMacSystemFont, "Trebuchet MS", Roboto, Ubuntu,
    sans-serif;
  font-weight: 600;
  color: white;
  border-radius: 2px;
  line-height: 1.2;
  min-width: 55px;
  text-align: center;
}
/* 主标签 */
.main-tabs {
  display: flex;
  border-bottom: 1px solid var(--mk-border);
  margin: 8px 12px 0;
}
.main-tab-btn {
  flex: 1;
  padding: 6px 0;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: var(--mk-font-base);
  color: var(--mk-text-tertiary);
  transition: all 0.2s;
  position: relative;
}
.main-tab-btn:hover {
  color: var(--mk-color-brand);
}
.main-tab-btn.active {
  color: var(--mk-color-brand);
  font-weight: 500;
}
.main-tab-btn.active::after {
  content: "";
  position: absolute;
  bottom: -1px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--mk-color-brand);
}
/* 交易日志面板 */
.log-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.log-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--mk-border);
}
.log-panel-title {
  font-size: var(--mk-font-base);
  font-weight: 500;
  color: var(--mk-text-primary);
}
.log-panel-body {
  flex: 1;
  overflow: auto;
  padding: 8px 12px;
}
.logreg-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.logreg-card {
  border: 1px dashed var(--mk-text-tertiary);
  border-radius: var(--mk-radius-sm);
  padding: 10px 10px;
  min-height: 92px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  background: var(--mk-bg-primary);
}
.logreg-card-left {
  align-items: flex-start;
  text-align: left;
}
.logreg-title {
  font-size: var(--mk-font-md);
  color: var(--mk-text-primary);
  font-weight: 600;
  line-height: 18px;
}
.logreg-value {
  margin-top: 6px;
  font-size: var(--mk-font-lg);
  font-weight: 700;
  color: var(--mk-text-primary);
  font-family: "Consolas", "Monaco", "Courier New", monospace;
}
.logreg-value-sm {
  margin-top: 6px;
  font-size: var(--mk-font-md);
  font-weight: 600;
  color: var(--mk-text-primary);
}
.logreg-sub {
  margin-top: 6px;
  font-size: var(--mk-font-sm);
  color: var(--mk-text-tertiary);
}
.logreg-direction-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.logreg-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--mk-text-secondary);
}
.logreg-dot.up {
  background: var(--mk-color-up);
}
.logreg-dot.down {
  background: var(--mk-color-down);
}
.logreg-dot.flat {
  background: #e6a23c;
}
.logreg-direction {
  font-size: var(--mk-font-md);
  font-weight: 600;
  color: var(--mk-text-primary);
}
.logreg-kv {
  margin-top: 8px;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.logreg-kv-center {
  align-items: center;
}
.logreg-kv-row {
  display: flex;
  gap: 6px;
  font-size: var(--mk-font-sm);
  line-height: 16px;
}
.logreg-kv-key {
  color: var(--mk-text-tertiary);
  white-space: nowrap;
}
.logreg-kv-val {
  color: var(--mk-text-primary);
  font-family: "Consolas", "Monaco", "Courier New", monospace;
}
.logreg-check-row {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--mk-font-sm);
  color: var(--mk-text-primary);
}
.logreg-check {
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--mk-border-input);
  border-radius: 3px;
  color: transparent;
  background: var(--mk-bg-primary);
  font-size: var(--mk-font-sm);
  line-height: 12px;
}
.logreg-check.on {
  border-color: var(--mk-color-up);
  color: var(--mk-color-up);
}
.log-empty {
  text-align: center;
  color: var(--mk-text-secondary);
  font-size: var(--mk-font-md);
  padding: 24px 0;
}
.log-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.log-item {
  display: grid;
  grid-template-columns: 60px 1fr 40px 80px 60px;
  gap: 6px;
  font-size: var(--mk-font-sm);
  padding: 4px 0;
  border-bottom: 1px dashed var(--mk-border);
  align-items: center;
}
.log-time {
  color: var(--mk-text-secondary);
}
.log-symbol {
  color: var(--mk-text-primary);
  font-weight: 500;
}
.log-side {
  text-align: center;
  font-weight: 500;
}
.log-side.buy {
  color: var(--mk-color-up);
}
.log-side.sell {
  color: var(--mk-color-down);
}
.log-price {
  text-align: right;
  font-family: monospace;
  color: var(--mk-text-primary);
}
.log-qty {
  text-align: right;
  font-family: monospace;
  color: var(--mk-text-secondary);
}
</style>
<!-- 非 scoped 样式：确保 Teleport 到 body 的元素在深色主题下有正确背景 -->
<style lang="scss">
/* ===== CSS Design Tokens (global) ===== */
:root {
  --mk-color-brand: #2962FF;
  --mk-color-brand-light: rgba(41, 98, 255, 0.08);
  --mk-color-brand-hover: #1e53e5;
  --mk-color-up: #089981;
  --mk-color-down: #F23645;
  --mk-bg-primary: #ffffff;
  --mk-bg-secondary: #f8f9fa;
  --mk-bg-tertiary: #f0f2f5;
  --mk-bg-hover: #ecf5ff;
  --mk-bg-card: #ffffff;
  --mk-bg-modal: #ffffff;
  --mk-bg-dropdown: #ffffff;
  --mk-text-primary: #1e222d;
  --mk-text-secondary: #787B86;
  --mk-text-tertiary: #9B9EAB;
  --mk-text-inverse: #ffffff;
  --mk-border: #e0e3eb;
  --mk-border-light: #f0f2f5;
  --mk-border-input: #dcdfe6;
  --mk-font-xs: 11px;
  --mk-font-sm: 12px;
  --mk-font-md: 13px;
  --mk-font-base: 14px;
  --mk-font-lg: 16px;
  --mk-font-xl: 18px;
  --mk-font-xxl: 20px;
  --mk-font-xxxl: 22px;
  --mk-space-2: 2px;
  --mk-space-4: 4px;
  --mk-space-8: 8px;
  --mk-space-12: 12px;
  --mk-space-16: 16px;
  --mk-height-topbar: 42px;
  --mk-height-bottombar: 32px;
  --mk-width-left-toolbar: 40px;
  --mk-width-right-panel: 320px;
  --mk-radius-sm: 4px;
  --mk-radius-md: 6px;
  --mk-radius-lg: 8px;
  --mk-radius-xl: 10px;
  --mk-shadow-card: 0 4px 12px rgba(15, 23, 42, 0.08);
  --mk-shadow-dropdown: 0 4px 12px rgba(0, 0, 0, 0.12);
}
html.dark {
  --mk-color-brand: var(--accent-blue);
  --mk-color-brand-light: rgba(255, 107, 92, 0.15);
  --mk-color-brand-hover: var(--accent-blue-dark);
  --mk-color-up: var(--accent-green);
  --mk-color-down: var(--accent-red);
  --mk-bg-primary: var(--primary-bg);
  --mk-bg-secondary: var(--secondary-bg);
  --mk-bg-tertiary: var(--tertiary-bg);
  --mk-bg-hover: var(--hover-bg);
  --mk-bg-card: var(--secondary-bg);
  --mk-bg-modal: var(--secondary-bg);
  --mk-bg-dropdown: var(--secondary-bg);
  --mk-text-primary: var(--text-primary);
  --mk-text-secondary: var(--text-secondary);
  --mk-text-tertiary: var(--text-muted);
  --mk-text-inverse: #ffffff;
  --mk-border: var(--border-color);
  --mk-border-light: var(--border-secondary);
  --mk-border-input: var(--input-border);
  --mk-font-xs: 11px;
  --mk-font-sm: 12px;
  --mk-font-md: 13px;
  --mk-font-base: 14px;
  --mk-font-lg: 16px;
  --mk-font-xl: 18px;
  --mk-font-xxl: 20px;
  --mk-font-xxxl: 22px;
  --mk-space-2: 2px;
  --mk-space-4: 4px;
  --mk-space-8: 8px;
  --mk-space-12: 12px;
  --mk-space-16: 16px;
  --mk-height-topbar: 42px;
  --mk-height-bottombar: 32px;
  --mk-width-left-toolbar: 40px;
  --mk-width-right-panel: 320px;
  --mk-radius-sm: 4px;
  --mk-radius-md: 6px;
  --mk-radius-lg: 8px;
  --mk-radius-xl: 10px;
  --mk-shadow-card: var(--card-shadow);
  --mk-shadow-dropdown: var(--shadow-premium);
}
html.dark .chart-context-menu {
  background: var(--mk-bg-primary, var(--primary-bg));
  border-color: var(--mk-border, var(--border-color));
}
html.dark .context-menu-item {
  color: var(--mk-text-tertiary, var(--text-muted));
  border-bottom: 1px solid var(--mk-bg-secondary, var(--secondary-bg));
}
html.dark .context-menu-item:last-child {
  border-bottom: none;
}
html.dark .context-menu-item:hover {
  background-color: var(--mk-bg-hover, var(--hover-bg));
  color: var(--mk-text-primary, var(--text-primary));
}
/* 开单面板暗色主题适配 */
html.dark .qt-current-price {
  color: var(--mk-text-primary, #e5e7eb);
}
html.dark .qt-section-title {
  color: var(--mk-text-primary, #e5e7eb);
}
.interval-btn.active {
  background: var(--mk-color-brand, var(--accent-blue)) !important;
  color: #fff !important;
}
</style>
