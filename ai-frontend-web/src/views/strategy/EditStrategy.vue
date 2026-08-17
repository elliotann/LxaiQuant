<template>
  <div class="edit-strategy">
    <div class="page-header">
      <h2>编辑策略</h2>
      <div class="header-actions">
        <el-button @click="backToList">返回列表</el-button>
        <el-button
          @click="saveStrategy"
          :icon="Document"
          size="large"
          type="primary"
          :loading="saving"
        >
          保存策略
        </el-button>
      </div>
    </div>

    <div class="strategy-content">
      <el-form :model="strategyForm" label-width="120px">
        <!-- 基本信息 -->
        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><InfoFilled /></el-icon>
              <span>基本信息</span>
            </div>
          </template>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="策略名称" required>
                <el-input
                  v-model="strategyForm.name"
                  placeholder="请输入策略名称"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="策略类型" required>
                <el-select
                  v-model="strategyForm.type"
                  placeholder="请选择策略类型"
                >
                  <el-option label="趋势跟踪" value="trend" />
                  <el-option label="均值回归" value="mean_reversion" />
                  <el-option label="套利" value="arbitrage" />
                  <el-option label="高频" value="high_frequency" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="策略类">
                <el-input
                  v-model="strategyForm.className"
                  placeholder="请输入策略类名"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="时间框架">
                <el-select
                  v-model="strategyForm.timeframe"
                  placeholder="请选择时间框架"
                >
                  <el-option label="1分钟" value="1m" />
                  <el-option label="3分钟" value="3m" />
                  <el-option label="5分钟" value="5m" />
                  <el-option label="15分钟" value="15m" />
                  <el-option label="30分钟" value="30m" />
                  <el-option label="1小时" value="1h" />
                  <el-option label="4小时" value="4h" />
                  <el-option label="1天" value="1d" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="信号ID">
                <el-select
                  v-model="strategyForm.strategyId"
                  placeholder="请选择信号"
                  clearable
                >
                  <el-option label="BOLL_RSI" value="BOLL_RSI" />
                  <el-option label="MACD" value="MACD" />
                  <el-option label="RANGE_FILTER" value="RANGE_FILTER" />
                  <el-option label="COMBINED" value="COMBINED" />
                  <el-option label="SSL_CHANNEL" value="SSL_CHANNEL" />
                  <el-option
                    label="LOGREG_CHANNEL_TREND"
                    value="LOGREG_CHANNEL_TREND"
                  />
                  <el-option label="FIB_BANDS" value="FIB_BANDS" />
                  <el-option label="SMOOTH" value="SMOOTH" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="策略描述">
            <el-input
              v-model="strategyForm.description"
              type="textarea"
              :rows="3"
              placeholder="请输入策略描述"
            />
          </el-form-item>
        </el-card>

        

        <!-- 入场规则 -->
        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><DataAnalysis /></el-icon>
              <span>入场规则</span>
            </div>
          </template>

          <!-- 做多入场条件 -->
          <el-divider content-position="left">
            做多入场条件
            <el-tag v-if="!entryRules.long.disabled && entryRules.long.conditions.length === 0" type="info" size="small" style="margin-left:8px">无条件，不入场</el-tag>
            <el-tag v-if="entryRules.long.disabled" type="warning" size="small" style="margin-left:8px">已禁用</el-tag>
          </el-divider>

          <el-form-item label=" ">
            <el-checkbox v-model="entryRules.long.disabled">禁用做多</el-checkbox>
          </el-form-item>

          <template v-if="!entryRules.long.disabled">
            <div v-for="(cond, idx) in entryRules.long.conditions" :key="'long-' + idx" style="margin-bottom:10px">
              <el-row :gutter="8" align="middle">
                <el-col :span="7">
                  <el-select v-model="cond.indicator" placeholder="选择指标" style="width:100%" @change="onIndicatorChange(cond)">
                    <el-option v-for="opt in availableIndicators" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </el-col>
                <el-col :span="4">
                  <el-input v-model="cond.period" placeholder="周期" :disabled="!cond.indicator" />
                </el-col>
                <el-col :span="5">
                  <el-select v-model="cond.operator" placeholder="操作符" style="width:100%">
                    <el-option label="大于" value="gt" />
                    <el-option label="小于" value="lt" />
                    <el-option label="等于" value="eq" />
                    <el-option label="上穿" value="cross_up" />
                    <el-option label="下穿" value="cross_down" />
                  </el-select>
                </el-col>
                <el-col :span="5">
                  <el-input v-model="cond.threshold" placeholder="阈值" />
                </el-col>
                <el-col :span="1">
                  <el-button @click="removeCondition('long', idx)" type="danger" circle size="small"><el-icon><Delete /></el-icon></el-button>
                </el-col>
              </el-row>
              <el-row v-if="idx < entryRules.long.conditions.length - 1" :gutter="8" style="margin-top:4px">
                <el-col :span="2" :push="7">
                  <el-select v-model="entryRules.long.conditions[idx + 1].connector" style="width:80px">
                    <el-option label="AND" value="and" />
                    <el-option label="OR" value="or" />
                  </el-select>
                </el-col>
              </el-row>
            </div>
            <el-button @click="addCondition('long')" type="primary" plain size="small"><el-icon><Plus /></el-icon> 添加条件</el-button>
          </template>

          <!-- 做空入场条件 -->
          <el-divider content-position="left">
            做空入场条件
            <el-tag v-if="!entryRules.short.disabled && entryRules.short.conditions.length === 0" type="info" size="small" style="margin-left:8px">无条件，不入场</el-tag>
            <el-tag v-if="entryRules.short.disabled" type="warning" size="small" style="margin-left:8px">已禁用</el-tag>
          </el-divider>

          <el-form-item label=" ">
            <el-checkbox v-model="entryRules.short.disabled">禁用做空</el-checkbox>
          </el-form-item>

          <template v-if="!entryRules.short.disabled">
            <div v-for="(cond, idx) in entryRules.short.conditions" :key="'short-' + idx" style="margin-bottom:10px">
              <el-row :gutter="8" align="middle">
                <el-col :span="7">
                  <el-select v-model="cond.indicator" placeholder="选择指标" style="width:100%" @change="onIndicatorChange(cond)">
                    <el-option v-for="opt in availableIndicators" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </el-col>
                <el-col :span="4">
                  <el-input v-model="cond.period" placeholder="周期" :disabled="!cond.indicator" />
                </el-col>
                <el-col :span="5">
                  <el-select v-model="cond.operator" placeholder="操作符" style="width:100%">
                    <el-option label="大于" value="gt" />
                    <el-option label="小于" value="lt" />
                    <el-option label="等于" value="eq" />
                    <el-option label="上穿" value="cross_up" />
                    <el-option label="下穿" value="cross_down" />
                  </el-select>
                </el-col>
                <el-col :span="5">
                  <el-input v-model="cond.threshold" placeholder="阈值" />
                </el-col>
                <el-col :span="1">
                  <el-button @click="removeCondition('short', idx)" type="danger" circle size="small"><el-icon><Delete /></el-icon></el-button>
                </el-col>
              </el-row>
              <el-row v-if="idx < entryRules.short.conditions.length - 1" :gutter="8" style="margin-top:4px">
                <el-col :span="2" :push="7">
                  <el-select v-model="entryRules.short.conditions[idx + 1].connector" style="width:80px">
                    <el-option label="AND" value="and" />
                    <el-option label="OR" value="or" />
                  </el-select>
                </el-col>
              </el-row>
            </div>
            <el-button @click="addCondition('short')" type="primary" plain size="small"><el-icon><Plus /></el-icon> 添加条件</el-button>
          </template>
        </el-card>

        <!-- 策略代码 -->
        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><Document /></el-icon>
              <span>策略代码</span>
            </div>
          </template>

          <div class="code-editor">
            <el-input
              v-model="strategyForm.code"
              type="textarea"
              :rows="20"
              placeholder="请输入策略代码"
            />
          </div>
        </el-card>

        <!-- 风控设置 -->
        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><WarningFilled /></el-icon>
              <span>风控设置</span>
            </div>
          </template>

          <!-- 仓位控制组 -->
          <el-divider content-position="left">仓位控制</el-divider>

          <p class="section-desc" style="margin: 0 0 16px 0; color: #909399; font-size: 13px;">
            控制单笔开仓大小，支持固定比例和以损定量两种模式
          </p>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="启用仓位管理">
                <el-switch
                  v-model="strategyForm.riskControl.positionManagementEnabled"
                />
                <el-tooltip content="启用后，系统会根据以下规则自动计算仓位">
                  <el-icon
                    style="margin-left: 8px; cursor: help; color: #909399"
                  >
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
          </el-row>

          <template v-if="strategyForm.riskControl.positionManagementEnabled">
            <!-- 仓位模式选择 -->
            <el-row :gutter="20" style="margin-bottom: 12px;">
              <el-col :span="16">
                <el-form-item label="仓位模式">
                  <el-radio-group v-model="strategyForm.riskControl.positionMode">
                    <el-radio value="fixed_ratio">固定比例</el-radio>
                    <el-radio value="risk_based">以损定量</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="8" v-if="strategyForm.riskControl.positionMode === 'fixed_ratio'">
                <el-form-item label="基础仓位">
                  <el-input-number
                    v-model="strategyForm.riskControl.basePositionPct"
                    :min="1"
                    :max="100"
                    :step="1"
                    :precision="0"
                    style="width: 120px"
                  />
                  <span style="margin-left: 8px">%</span>
                </el-form-item>
              </el-col>
              <el-col :span="8" v-if="strategyForm.riskControl.positionMode === 'risk_based'">
                <el-form-item label="单笔风险">
                  <el-input-number
                    v-model="strategyForm.riskControl.singleTradeRiskPct"
                    :min="0.1"
                    :max="5.0"
                    :step="0.1"
                    :precision="1"
                    style="width: 120px"
                  />
                  <span style="margin-left: 8px">%</span>
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 仓位约束（两种模式共用） -->
            <el-row :gutter="20" style="margin-top: 16px;">
              <el-col :span="24">
                <span style="font-weight: 500; margin-right: 16px;">仓位约束（两种模式共用）：</span>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="最大仓位">
                  <el-input-number
                    v-model="strategyForm.riskControl.maxPositionPerTrade"
                    :min="1"
                    :max="100"
                    :step="1"
                    :precision="0"
                    style="width: 120px"
                  />
                  <span style="margin-left: 8px">%</span>
                  <el-tooltip content="硬性上限，最终仓位不超过此比例">
                    <el-icon style="margin-left: 8px; cursor: help; color: #909399">
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="最小仓位">
                  <el-input-number
                    v-model="strategyForm.riskControl.minPosition"
                    :min="0.001"
                    :max="100"
                    :step="0.01"
                    :precision="2"
                    style="width: 120px"
                  />
                  <span style="margin-left: 8px">张</span>
                  <el-tooltip content="硬性下限，单位统一为张">
                    <el-icon style="margin-left: 8px; cursor: help; color: #909399">
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-col>
            </el-row>

            <el-alert type="info" :closable="false" show-icon style="margin-top: 8px;">
              <template #default>
                最终仓位 = min(基础仓位, 最大仓位) 且 ≥ 最小仓位。<br/>
                止损距离优先取【基于结构止盈止损】的日常防线，未启用时取【止损设置】中的固定百分比止损。
              </template>
            </el-alert>
          </template>

          <el-divider content-position="left">加仓设置</el-divider>
          <el-form-item label="是否允许加仓">
            <el-switch v-model="strategyForm.riskControl.allowAddPosition" />
            <el-tooltip
              content="开启后允许同一方向分批入场（同向多笔未平仓订单）"
            >
              <el-icon style="margin-left: 8px; cursor: help; color: #909399">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </el-form-item>

          <template v-if="strategyForm.riskControl.allowAddPosition">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="亏损补仓幅度">
                  <el-input-number
                    v-model="strategyForm.riskControl.addPosOnLossPct"
                    :min="0"
                    :max="100"
                    :step="0.1"
                    :precision="1"
                    style="width: 150px"
                  />
                  <span style="margin-left: 8px">%</span>
                  <el-tooltip
                    content="当亏损达到此幅度时触发补仓（相对于首次开仓价）。0表示不限制幅度。"
                  >
                    <el-icon
                      style="margin-left: 8px; cursor: help; color: #909399"
                    >
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="盈利加仓幅度">
                  <el-input-number
                    v-model="strategyForm.riskControl.addPosOnProfitPct"
                    :min="0"
                    :max="100"
                    :step="0.1"
                    :precision="1"
                    style="width: 150px"
                  />
                  <span style="margin-left: 8px">%</span>
                  <el-tooltip
                    content="当盈利达到此幅度时触发加仓（相对于首次开仓价）。0表示不限制幅度。"
                  >
                    <el-icon
                      style="margin-left: 8px; cursor: help; color: #909399"
                    >
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="亏损补仓间隔">
                  <el-input-number
                    v-model="strategyForm.riskControl.addPosOnLossGapPct"
                    :min="0"
                    :max="100"
                    :step="0.1"
                    :precision="1"
                    style="width: 150px"
                  />
                  <span style="margin-left: 8px">%</span>
                  <el-tooltip
                    content="相对上一次入场价的额外亏损幅度达到此值，才允许再次补仓。0表示不限制间隔。"
                  >
                    <el-icon
                      style="margin-left: 8px; cursor: help; color: #909399"
                    >
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="盈利加仓间隔">
                  <el-input-number
                    v-model="strategyForm.riskControl.addPosOnProfitGapPct"
                    :min="0"
                    :max="100"
                    :step="0.1"
                    :precision="1"
                    style="width: 150px"
                  />
                  <span style="margin-left: 8px">%</span>
                  <el-tooltip
                    content="相对上一次入场价的额外盈利幅度达到此值，才允许再次加仓。0表示不限制间隔。"
                  >
                    <el-icon
                      style="margin-left: 8px; cursor: help; color: #909399"
                    >
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <el-divider content-position="left">📡 信号频率控制</el-divider>
          <el-form-item label="启用同向信号频率限制">
            <el-switch v-model="strategyForm.riskControl.signalFrequencyEnabled" />
            <el-tooltip content="开启后对同方向信号进行频率限制，避免短时间内重复入场" placement="top">
              <el-icon style="margin-left: 8px; cursor: help; color: #909399">
                <QuestionFilled />
              </el-icon>
            </el-tooltip>
          </el-form-item>

          <template v-if="strategyForm.riskControl.signalFrequencyEnabled">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="限制粒度">
                  <el-select
                    v-model="strategyForm.riskControl.signalFrequencyGranularity"
                    style="width: 200px"
                  >
                    <el-option label="3分钟" value="3min" />
                    <el-option label="15分钟（推荐）" value="15min" />
                    <el-option label="1小时" value="1hour" />
                  </el-select>
                  <el-tooltip content="同一方向信号的最小间隔时间" placement="top">
                    <el-icon style="margin-left: 8px; cursor: help; color: #909399">
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="限制模式">
                  <el-select
                    v-model="strategyForm.riskControl.signalFrequencyMode"
                    style="width: 260px"
                  >
                    <el-option label="严格锁定（同向信号只取一次）" value="strict_lock" />
                    <el-option label="结构升级豁免（波次升级时允许突破）" value="structure_upgrade_exempt" />
                    <el-option label="无限制（允许多次同向信号）" value="unlimited" />
                  </el-select>
                  <el-tooltip content="同向信号触发频率限制时的处理策略" placement="top">
                    <el-icon style="margin-left: 8px; cursor: help; color: #909399">
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <!-- 止损设置：固定止损单选，移动止损复选 -->
          <el-divider content-position="left">止损设置</el-divider>
          <!-- 第一行：固定百分比止损 与 ATR 固定止损（单选，二选一） -->
          <el-form-item label="固定止损">
            <el-radio-group
              v-model="stopLossFixedType"
              @change="onStopLossFixedTypeChange"
            >
              <el-radio value="">不启用</el-radio>
              <el-radio value="fixed_percent">固定百分比止损</el-radio>
              <el-radio value="atr_based">ATR 固定止损</el-radio>
            </el-radio-group>
            <template v-if="stopLossFixedType === 'fixed_percent'">
              <el-input-number
                v-model="
                  strategyForm.riskControl.stopLoss.fixed_percent.percent
                "
                :min="0.1"
                :max="100"
                :step="0.1"
                :precision="1"
                size="small"
                style="width: 120px; margin-left: 12px"
              />
              <span style="margin-left: 4px">%</span>
            </template>
            <template v-else-if="stopLossFixedType === 'atr_based'">
              <el-input-number
                v-model="
                  strategyForm.riskControl.stopLoss.atr_based.atrMultiplier
                "
                :min="1"
                :max="5"
                :step="0.1"
                size="small"
                style="width: 90px; margin-left: 12px"
              />
              倍
              <el-input-number
                v-model="strategyForm.riskControl.stopLoss.atr_based.atrPeriod"
                :min="5"
                :max="50"
                size="small"
                style="width: 80px; margin-left: 8px"
              />
              周期
            </template>
          </el-form-item>
          <!-- 第二行：固定百分比移动止损 与 ATR 移动止损（复选，可多选） -->
          <el-form-item label="移动止损">
            <span class="form-hint" style="margin-right: 16px">可多选</span>
            <el-row
              :gutter="24"
              style="
                display: inline-flex;
                flex-wrap: wrap;
                align-items: flex-start;
                gap: 16px 24px;
              "
            >
              <span>
                <el-checkbox
                  v-model="
                    strategyForm.riskControl.stopLoss.fixed_percent_trailing
                      .enabled
                  "
                />
                <span style="margin-right: 6px">固定百分比移动</span>
                <template
                  v-if="
                    strategyForm.riskControl.stopLoss.fixed_percent_trailing
                      .enabled
                  "
                >
                  <el-input-number
                    v-model="
                      strategyForm.riskControl.stopLoss.fixed_percent_trailing
                        .percent
                    "
                    :min="0.1"
                    :max="100"
                    :step="0.1"
                    size="small"
                    style="width: 90px; margin-left: 4px"
                  />%
                  <el-select
                    v-model="
                      strategyForm.riskControl.stopLoss.fixed_percent_trailing
                        .barCount
                    "
                    size="small"
                    style="width: 90px; margin-left: 6px"
                    placeholder="回溯"
                  >
                    <el-option :value="null" label="无限制" />
                    <el-option :value="50" label="50" />
                    <el-option :value="100" label="100" />
                    <el-option :value="200" label="200" />
                  </el-select>
                </template>
              </span>
              <span>
                <el-checkbox
                  v-model="
                    strategyForm.riskControl.stopLoss.atr_trailing.enabled
                  "
                />
                <span style="margin-right: 6px">ATR 移动止损</span>
                <template
                  v-if="strategyForm.riskControl.stopLoss.atr_trailing.enabled"
                >
                  <el-input-number
                    v-model="
                      strategyForm.riskControl.stopLoss.atr_trailing
                        .atrMultiplier
                    "
                    :min="1"
                    :max="5"
                    :step="0.1"
                    size="small"
                    style="width: 80px; margin-left: 4px"
                  />
                  倍
                  <el-input-number
                    v-model="
                      strategyForm.riskControl.stopLoss.atr_trailing.atrPeriod
                    "
                    :min="5"
                    :max="50"
                    size="small"
                    style="width: 70px; margin-left: 4px"
                  />
                  周期
                </template>
              </span>
            </el-row>
          </el-form-item>

          <!-- 止盈设置 -->
          <el-divider content-position="left">止盈设置</el-divider>

          <el-form-item label="启用止盈">
            <el-switch v-model="strategyForm.riskControl.takeProfitEnabled" />
          </el-form-item>

          <template v-if="strategyForm.riskControl.takeProfitEnabled">
            <el-form-item label="止盈类型" required>
              <el-radio-group v-model="strategyForm.riskControl.takeProfitType">
                <el-radio value="fixed_percent">固定百分比止盈</el-radio>
                <el-radio value="atr_based">ATR 固定止盈</el-radio>
              </el-radio-group>
            </el-form-item>

            <template
              v-if="strategyForm.riskControl.takeProfitType === 'fixed_percent'"
            >
              <el-form-item
                label="止盈百分比"
                prop="riskControl.takeProfitPercent"
              >
                <el-input-number
                  v-model="strategyForm.riskControl.takeProfitPercent"
                  :min="0.1"
                  :max="50"
                  :step="0.1"
                  :precision="1"
                  style="width: 200px"
                />
                <span style="margin-left: 8px">%</span>
                <el-tooltip content="达到目标利润百分比时触发止盈">
                  <el-icon
                    style="margin-left: 8px; cursor: help; color: #909399"
                  >
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </el-form-item>
            </template>

            <template
              v-if="strategyForm.riskControl.takeProfitType === 'atr_based'"
            >
              <el-row :gutter="20">
                <el-col :span="12">
                  <el-form-item
                    label="ATR 倍数"
                    prop="riskControl.takeProfitAtrMultiplier"
                  >
                    <el-input-number
                      v-model="strategyForm.riskControl.takeProfitAtrMultiplier"
                      :min="1.0"
                      :max="10.0"
                      :step="0.1"
                      :precision="1"
                      style="width: 200px"
                    />
                    <el-tooltip
                      content="止盈距离 = ATR × 倍数，建议值：2.0-5.0"
                    >
                      <el-icon
                        style="margin-left: 8px; cursor: help; color: #909399"
                      >
                        <QuestionFilled />
                      </el-icon>
                    </el-tooltip>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item
                    label="ATR 计算周期"
                    prop="riskControl.takeProfitAtrPeriod"
                  >
                    <el-input-number
                      v-model="strategyForm.riskControl.takeProfitAtrPeriod"
                      :min="5"
                      :max="50"
                      :step="1"
                      style="width: 200px"
                    />
                    <span style="margin-left: 8px">根K线</span>
                    <el-tooltip content="计算 ATR 指标时使用的周期，通常为 14">
                      <el-icon
                        style="margin-left: 8px; cursor: help; color: #909399"
                      >
                        <QuestionFilled />
                      </el-icon>
                    </el-tooltip>
                  </el-form-item>
                </el-col>
              </el-row>
            </template>

            <!-- 动态风控引擎：移动止损（左）/ 移动止盈（右）双栏镜像布局 -->
            <el-divider content-position="left">⚙️ 动态风控引擎</el-divider>
            <el-row :gutter="20">
              <!-- 左侧：移动止损（结构追踪） -->
              <el-col :span="12">
                <el-card shadow="never">
                  <template #header>
                    <div style="display: flex; align-items: center; justify-content: space-between;">
                      <span style="font-weight: 600;">🛡️ 防守线（移动止损 · 结构追踪）</span>
                      <el-switch v-model="trailingStop.enabled" />
                    </div>
                  </template>

                  <template v-if="trailingStop.enabled">
                  <!-- 移动算法（五选一） -->
                  <div style="font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; padding-bottom: 6px; border-bottom: 1px dashed var(--border-color);">移动算法（五选一）</div>
                  <el-radio-group v-model="trailingStop.algorithm" size="small" style="display: flex; flex-wrap: wrap; gap: 6px 20px; margin-bottom: 8px;">
                    <el-radio value="fixed" disabled>固定%</el-radio>
                    <el-radio value="atr" disabled>ATR</el-radio>
                    <el-radio value="structure">结构</el-radio>
                    <el-radio value="kline" disabled>前N根K线高低点</el-radio>
                    <el-radio value="ma" disabled>均线追踪</el-radio>
                  </el-radio-group>
                  <div style="font-size: 12px; color: #E6A23C; margin-bottom: 12px;">⚠️ 其他算法开发中，暂不可用</div>

                  <!-- 结构参数（当前生效） -->
                  <div style="font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; padding-bottom: 6px; border-bottom: 1px dashed var(--border-color);">结构参数（当前生效）</div>

                  <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 12px;">
                    <span style="font-size: 13px; color: var(--text-secondary);">参考周期：</span>
                    <el-select v-model="trailingStop.period" size="small" style="width: 120px;">
                      <el-option label="5分钟" value="5" />
                      <el-option label="15分钟" value="15" />
                      <el-option label="30分钟" value="30" />
                      <el-option label="1小时" value="60" />
                      <el-option label="4小时" value="240" />
                    </el-select>
                  </div>

                  <!-- 激活时机 -->
                  <div style="font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; padding-bottom: 6px; border-bottom: 1px dashed var(--border-color);">激活时机</div>
                  <el-radio-group v-model="trailingStop.activation" size="small" style="display: flex; flex-direction: column; gap: 6px; align-items: flex-start; margin-bottom: 12px;">
                    <el-radio value="open">开仓即动</el-radio>
                    <el-radio value="break">突破前高/前低后动</el-radio>
                  </el-radio-group>

                  <div style="margin-bottom: 12px;">
                    <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 6px;">结构类型：</div>
                    <div style="display: flex; flex-direction: column; gap: 6px; padding-left: 8px;">
                      <el-checkbox v-model="trailingStop.structureTypes.ob">订单块（OB）</el-checkbox>
                      <el-checkbox v-model="trailingStop.structureTypes.swing">前高/前低</el-checkbox>
                    </div>
                  </div>

                  <div style="margin-bottom: 12px;">
                    <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 6px;">偏移方式：</div>
                    <div style="display: flex; align-items: center; gap: 6px; padding-left: 8px;">
                      <span style="font-size: 13px; color: var(--text-primary);">结构边缘 + 缓冲</span>
                      <el-input-number v-model="trailingStop.offsetBuffer" :min="0" :step="0.01" :precision="2" size="small" style="width: 80px;" />
                      <span style="font-size: 13px; color: var(--text-secondary);">%</span>
                    </div>
                  </div>

                  <div style="margin-bottom: 12px;">
                    <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 6px;">破坏判定：</div>
                    <el-radio-group v-model="trailingStop.breakMode" size="small" style="display: flex; flex-direction: column; gap: 6px; align-items: flex-start;">
                      <el-radio value="wick">影线刺穿即破坏</el-radio>
                      <el-radio value="close">收盘价实体破位才破坏</el-radio>
                    </el-radio-group>
                  </div>

                  </template>
                </el-card>
              </el-col>

              <!-- 右侧：移动止盈（结构追踪） -->
              <el-col :span="12">
                <el-card shadow="never">
                  <template #header>
                    <div style="display: flex; align-items: center; justify-content: space-between;">
                      <span style="font-weight: 600;">🎯 进攻线（移动止盈 · 结构追踪）</span>
                      <el-switch v-model="trailingTakeProfit.enabled" />
                    </div>
                  </template>

                  <template v-if="trailingTakeProfit.enabled">
                  <!-- 移动算法（五选一） -->
                  <div style="font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; padding-bottom: 6px; border-bottom: 1px dashed var(--border-color);">移动算法（五选一）</div>
                  <el-radio-group v-model="trailingTakeProfit.algorithm" size="small" style="display: flex; flex-wrap: wrap; gap: 6px 20px; margin-bottom: 8px;">
                    <el-radio value="fixed" disabled>固定%</el-radio>
                    <el-radio value="atr" disabled>ATR</el-radio>
                    <el-radio value="structure">结构</el-radio>
                    <el-radio value="kline" disabled>前N根K线高低点</el-radio>
                    <el-radio value="ma" disabled>均线追踪</el-radio>
                  </el-radio-group>
                  <div style="font-size: 12px; color: #E6A23C; margin-bottom: 12px;">⚠️ 其他算法开发中，暂不可用</div>

                  <!-- 结构参数（当前生效） -->
                  <div style="font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; padding-bottom: 6px; border-bottom: 1px dashed var(--border-color);">结构参数（当前生效）</div>

                  <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 12px;">
                    <span style="font-size: 13px; color: var(--text-secondary);">参考周期：</span>
                    <el-select v-model="trailingTakeProfit.period" size="small" style="width: 120px;">
                      <el-option label="5分钟" value="5" />
                      <el-option label="15分钟" value="15" />
                      <el-option label="30分钟" value="30" />
                      <el-option label="1小时" value="60" />
                      <el-option label="4小时" value="240" />
                    </el-select>
                  </div>

                  <!-- 激活时机 -->
                  <div style="font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; padding-bottom: 6px; border-bottom: 1px dashed var(--border-color);">激活时机</div>
                  <el-radio-group v-model="trailingTakeProfit.activation" size="small" style="display: flex; flex-direction: column; gap: 6px; align-items: flex-start; margin-bottom: 12px;">
                    <el-radio value="open">开仓即动</el-radio>
                    <el-radio value="break">突破前高/前低后动</el-radio>
                  </el-radio-group>

                  <div style="margin-bottom: 12px;">
                    <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 6px;">结构类型：</div>
                    <div style="display: flex; flex-direction: column; gap: 6px; padding-left: 8px;">
                      <el-checkbox v-model="trailingTakeProfit.structureTypes.ob">订单块（OB）</el-checkbox>
                      <el-checkbox v-model="trailingTakeProfit.structureTypes.swing">前高/前低</el-checkbox>
                    </div>
                  </div>

                  <div style="margin-bottom: 12px;">
                    <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 6px;">偏移方式：</div>
                    <div style="display: flex; align-items: center; gap: 6px; padding-left: 8px;">
                      <span style="font-size: 13px; color: var(--text-primary);">结构边缘 + 缓冲</span>
                      <el-input-number v-model="trailingTakeProfit.offsetBuffer" :min="0" :step="0.01" :precision="2" size="small" style="width: 80px;" />
                      <span style="font-size: 13px; color: var(--text-secondary);">%</span>
                    </div>
                  </div>

                  <div style="margin-bottom: 12px;">
                    <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 6px;">触发条件：</div>
                    <el-radio-group v-model="trailingTakeProfit.triggerMode" size="small" style="display: flex; flex-direction: column; gap: 6px; align-items: flex-start;">
                      <el-radio value="wick">影线刺穿即止盈</el-radio>
                      <el-radio value="close">收盘价突破才止盈</el-radio>
                    </el-radio-group>
                  </div>

                  <!-- 离场方式 -->
                  <div style="font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; padding-bottom: 6px; border-bottom: 1px dashed var(--border-color);">离场方式</div>
                  <el-radio-group v-model="trailingTakeProfit.exitMode" size="small" style="display: flex; flex-direction: column; gap: 6px; align-items: flex-start; margin-bottom: 12px;">
                    <el-radio value="all">全仓离场</el-radio>
                    <el-radio value="half">平50% 剩余追</el-radio>
                  </el-radio-group>

                  <!-- 防噪音过滤 -->
                  <div style="font-size: 13px; font-weight: 600; color: var(--text-primary); margin-bottom: 12px; padding-bottom: 6px; border-bottom: 1px dashed var(--border-color);">防噪音过滤</div>
                  <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap;">
                    <el-checkbox v-model="trailingTakeProfit.minStepEnabled">最小步进</el-checkbox>
                    <el-input-number v-model="trailingTakeProfit.minStep" :min="0" :step="0.01" :precision="2" size="small" style="width: 80px;" />
                    <span style="font-size: 13px; color: var(--text-secondary);">% 低于此不触发</span>
                  </div>
                  </template>
                </el-card>
              </el-col>
            </el-row>

            <!-- 支撑与压力出场设置 (基于 SMC 逻辑) -->
            <el-divider content-position="left">支撑与压力出场设置</el-divider>
            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="启用支撑/压力出场">
                  <el-switch v-model="strategyForm.riskControl.smcExit.enabled" />
                  <el-tooltip content="基于多周期 SMC (Smart Money Concepts) 识别出的支撑压力位动态离场">
                    <el-icon style="margin-left: 8px; cursor: help; color: #909399">
                      <QuestionFilled />
                    </el-icon>
                  </el-tooltip>
                </el-form-item>
              </el-col>
            </el-row>

            <template v-if="strategyForm.riskControl.smcExit.enabled">
              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="主动止盈">
                    <div style="display: flex; flex-direction: column; gap: 10px">
                      <el-checkbox
                        v-model="strategyForm.riskControl.smcExit.activeTakeProfit.enabled"
                        label="按订单块"
                      />
                      <div
                        v-if="strategyForm.riskControl.smcExit.activeTakeProfit.enabled"
                        style="display: flex; flex-direction: column; gap: 8px; padding-left: 18px"
                      >
                        <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
                          <el-checkbox
                            v-model="strategyForm.riskControl.smcExit.activeTakeProfit.ob15m.enabled"
                            label="到达15m对立订单块时平仓："
                          />
                          <el-input-number
                            v-model="strategyForm.riskControl.smcExit.activeTakeProfit.ob15m.closePercent"
                            :min="1"
                            :max="100"
                            :step="1"
                            :disabled="!strategyForm.riskControl.smcExit.activeTakeProfit.ob15m.enabled"
                            size="small"
                            style="width: 110px"
                          />
                          <span>%</span>
                        </div>
                        <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
                          <el-checkbox
                            v-model="strategyForm.riskControl.smcExit.activeTakeProfit.ob1h.enabled"
                            label="到达1h对立订单块时平仓："
                          />
                          <el-input-number
                            v-model="strategyForm.riskControl.smcExit.activeTakeProfit.ob1h.closePercent"
                            :min="1"
                            :max="100"
                            :step="1"
                            :disabled="!strategyForm.riskControl.smcExit.activeTakeProfit.ob1h.enabled"
                            size="small"
                            style="width: 110px"
                          />
                          <span>%</span>
                        </div>
                        <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
                          <el-checkbox
                            v-model="strategyForm.riskControl.smcExit.activeTakeProfit.higher.enabled"
                            label="到达更高时间框架时平仓："
                          />
                          <el-select
                            v-model="strategyForm.riskControl.smcExit.activeTakeProfit.higher.period"
                            :disabled="!strategyForm.riskControl.smcExit.activeTakeProfit.higher.enabled"
                            size="small"
                            style="width: 120px"
                          >
                            <el-option label="4小时" value="240" />
                            <el-option label="1天" value="1440" />
                          </el-select>
                          <el-input-number
                            v-model="strategyForm.riskControl.smcExit.activeTakeProfit.higher.closePercent"
                            :min="1"
                            :max="100"
                            :step="1"
                            :disabled="!strategyForm.riskControl.smcExit.activeTakeProfit.higher.enabled"
                            size="small"
                            style="width: 110px"
                          />
                          <span>%</span>
                        </div>
                      </div>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="被动离场">
                    <div style="display: flex; flex-direction: column; gap: 10px">
                      <el-checkbox
                        v-model="strategyForm.riskControl.smcExit.passiveExit.enabled"
                        label="结构破坏"
                      />
                      <div
                        v-if="strategyForm.riskControl.smcExit.passiveExit.enabled"
                        style="display: flex; flex-direction: column; gap: 8px; padding-left: 18px"
                      >
                        <el-checkbox
                          v-model="strategyForm.riskControl.smcExit.passiveExit.reverseChoch"
                          label="出现反向CHOCH时立即平仓全部"
                        />
                        <el-checkbox
                          v-model="strategyForm.riskControl.smcExit.passiveExit.reverseBos"
                          label="出现反向BOS时立即平仓全部"
                        />
                      </div>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="移动止损">
                    <div style="display: flex; flex-direction: column; gap: 10px">
                      <el-checkbox
                        v-model="strategyForm.riskControl.smcExit.trailingStop.enabled"
                        label="启用"
                      />
                      <div
                        v-if="strategyForm.riskControl.smcExit.trailingStop.enabled"
                        style="display: flex; flex-direction: column; gap: 10px; padding-left: 18px"
                      >
                        <div style="display: flex; flex-direction: column; gap: 8px">
                          <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
                            <el-checkbox
                              v-model="strategyForm.riskControl.smcExit.trailingStop.moveToBreakeven.enabled"
                              label="盈利达到风险倍数后移动止损至成本价"
                            />
                          </div>
                          <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding-left: 18px">
                            <span>触发倍数：</span>
                            <el-input-number
                              v-model="strategyForm.riskControl.smcExit.trailingStop.moveToBreakeven.triggerR"
                              :min="0.1"
                              :max="10"
                              :step="0.1"
                              :precision="1"
                              :disabled="!strategyForm.riskControl.smcExit.trailingStop.moveToBreakeven.enabled"
                              size="small"
                              style="width: 110px"
                            />
                            <span>倍风险</span>
                          </div>
                        </div>

                        <div style="display: flex; flex-direction: column; gap: 8px">
                          <el-checkbox
                            v-model="strategyForm.riskControl.smcExit.trailingStop.trackStructure.enabled"
                            label="跟踪结构低点/高点移动止损"
                          />
                          <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding-left: 18px">
                            <span>跟踪周期：</span>
                            <el-select
                              v-model="strategyForm.riskControl.smcExit.trailingStop.trackStructure.period"
                              :disabled="!strategyForm.riskControl.smcExit.trailingStop.trackStructure.enabled"
                              size="small"
                              style="width: 120px"
                            >
                              <el-option label="15分钟" value="15" />
                              <el-option label="30分钟" value="30" />
                              <el-option label="1小时" value="60" />
                              <el-option label="4小时" value="240" />
                              <el-option label="1天" value="1440" />
                            </el-select>
                            <el-radio-group
                              v-model="strategyForm.riskControl.smcExit.trailingStop.trackStructure.point"
                              :disabled="!strategyForm.riskControl.smcExit.trailingStop.trackStructure.enabled"
                              size="small"
                            >
                              <el-radio value="swing">摆动点</el-radio>
                              <el-radio value="internal">内部结构点</el-radio>
                            </el-radio-group>
                          </div>
                        </div>

                        <!-- SMC结构跟踪移动止损：模式 & 挡位 -->
                        <div style="display: flex; flex-direction: column; gap: 8px; margin-top: 6px">
                          <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
                            <span style="font-size: 13px; color: #606266">模式：</span>
                            <el-radio-group
                              v-model="strategyForm.riskControl.smcExit.trailingStop.mode"
                              size="small"
                            >
                              <el-radio value="AUTO">自动</el-radio>
                              <el-radio value="MANUAL">手动</el-radio>
                            </el-radio-group>
                          </div>
                          <div v-if="strategyForm.riskControl.smcExit.trailingStop.mode === 'MANUAL'"
                               style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding-left: 18px">
                            <span>挡位：</span>
                            <el-select
                              v-model="strategyForm.riskControl.smcExit.trailingStop.gear"
                              size="small"
                              style="width: 140px"
                            >
                              <el-option label="保守(4H摆动点+0.3%)" value="CONSERVATIVE" />
                              <el-option label="中等(1H内部点+0.2%)" value="MODERATE" />
                              <el-option label="激进(15M内部点+0.1%)" value="AGGRESSIVE" />
                            </el-select>
                          </div>
                        </div>
                      </div>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="初始止损偏移">
                    <div style="display: flex; flex-direction: column; gap: 10px">
                      <el-checkbox
                        v-model="strategyForm.riskControl.smcExit.initialStopOffset.enabled"
                        label="启用"
                      />
                      <div
                        v-if="strategyForm.riskControl.smcExit.initialStopOffset.enabled"
                        style="display: flex; flex-direction: column; gap: 10px; padding-left: 18px"
                      >
                        <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
                          <span>方式：</span>
                          <el-radio-group
                            v-model="strategyForm.riskControl.smcExit.initialStopOffset.mode"
                            size="small"
                          >
                            <el-radio value="percent">百分比</el-radio>
                            <el-radio value="points">固定点数</el-radio>
                          </el-radio-group>
                          <template v-if="strategyForm.riskControl.smcExit.initialStopOffset.mode === 'percent'">
                            <el-input-number
                              v-model="strategyForm.riskControl.smcExit.initialStopOffset.percent"
                              :min="0"
                              :max="100"
                              :step="0.01"
                              :precision="2"
                              size="small"
                              style="width: 110px"
                            />
                            <span>%</span>
                          </template>
                          <template v-else>
                            <el-input-number
                              v-model="strategyForm.riskControl.smcExit.initialStopOffset.points"
                              :min="0"
                              :max="1000000"
                              :step="0.001"
                              :precision="3"
                              size="small"
                              style="width: 110px"
                            />
                          </template>
                        </div>
                        <el-checkbox
                          v-model="strategyForm.riskControl.smcExit.initialStopOffset.stopLossObSameAsTarget"
                          label="止损订单块与止盈目标周期相同"
                        />
                        <el-checkbox
                          v-model="strategyForm.riskControl.smcExit.initialStopOffset.fixed15mOrderBlock"
                          label="固定使用15分钟订单块"
                        />
                        <div style="color: #909399; font-size: 12px; line-height: 18px">
                          止损设在顺势订单块外侧 + 偏移，避免毛刺
                        </div>
                      </div>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="20">
                <el-col :span="24">
                  <el-form-item label="参考时间框架">
                    <div style="display: flex; flex-direction: column; gap: 10px">
                      <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
                        <span>止损结构周期：</span>
                        <el-select
                          v-model="strategyForm.riskControl.smcExit.reference.stopStructurePeriod"
                          size="small"
                          style="width: 120px"
                        >
                          <el-option label="15分钟" value="15" />
                          <el-option label="30分钟" value="30" />
                          <el-option label="1小时" value="60" />
                          <el-option label="4小时" value="240" />
                          <el-option label="1天" value="1440" />
                        </el-select>
                      </div>
                      <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap">
                        <span>止盈目标周期：</span>
                        <el-select
                          v-model="strategyForm.riskControl.smcExit.reference.targetPeriod"
                          size="small"
                          style="width: 120px"
                        >
                          <el-option label="15分钟" value="15" />
                          <el-option label="30分钟" value="30" />
                          <el-option label="1小时" value="60" />
                          <el-option label="4小时" value="240" />
                          <el-option label="1天" value="1440" />
                        </el-select>
                      </div>
                    </div>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="20">
                <el-col :span="24">
                  <div style="margin: 0 0 12px 120px; color: #606266; font-size: 12px; line-height: 18px">
                    <div>影响说明：</div>
                    <div>• 止损结构周期 → 被动离场、移动止损（跟踪结构点）</div>
                    <div>• 止盈目标周期 → 主动止盈（1h/更高）、初始止损偏移（若勾选“止损订单块与止盈相同”）</div>
                    <div>• 初始止损偏移的订单块来源可独立选择（与止盈周期相同 或 固定15分钟）</div>
                  </div>
                </el-col>
              </el-row>
            </template>

            <!-- ==================== 基于结构止盈止损（SMC分层出场） ==================== -->
            <el-divider content-position="left">基于结构止盈止损</el-divider>

            <p style="margin: 0 0 16px 0; color: #909399; font-size: 13px;">
              基于SMC结构的出场体系，包含动态止损、分级止盈、移动止损和保本
            </p>

            <el-row :gutter="20" style="margin-bottom: 16px;">
              <el-col :span="8">
                <el-form-item label="启用基于结构止盈止损">
                  <el-switch v-model="strategyForm.riskControl.structureStopProfit.enabled" />
                </el-form-item>
              </el-col>
            </el-row>

            <template v-if="strategyForm.riskControl.structureStopProfit.enabled">
              <!-- 模式选择 -->
              <el-row :gutter="20" style="margin-bottom: 16px;">
                <el-col :span="24">
                  <el-form-item label="模式选择">
                    <el-radio-group v-model="strategyForm.riskControl.structureStopProfit.mode">
                      <el-radio value="manual">手动模式</el-radio>
                      <el-radio value="auto">自动模式</el-radio>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
              </el-row>

              <!-- ==================== 手动模式 ==================== -->
              <template v-if="strategyForm.riskControl.structureStopProfit.mode === 'manual'">
                <!-- 动态止损 -->
                <el-card shadow="never" style="margin-bottom: 16px;">
                  <template #header>
                    <div style="display: flex; align-items: center; gap: 8px;">
                      <b>动态止损</b>
                      <span style="font-size: 13px; color: var(--text-tertiary); font-weight: normal;">基于结构位置决定止损层级</span>
                    </div>
                  </template>
                  <div style="border: 1px solid var(--border-color); border-radius: 4px; margin-bottom: 12px;">
                    <div style="display: flex; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-color); padding: 8px 16px; font-size: 13px; color: var(--text-secondary); font-weight: 500;">
                      <div style="width: 80px;">层级</div>
                      <div style="width: 140px;">周期</div>
                      <div style="width: 130px;">缓冲</div>
                      <div style="flex: 1;">触发条件</div>
                    </div>
                    <div style="display: flex; align-items: center; padding: 10px 16px; border-bottom: 1px solid var(--border-color);">
                      <div style="width: 80px; font-weight: 500; color: var(--text-primary);">第一道</div>
                      <div style="width: 140px;">
                        <el-select v-model="strategyForm.riskControl.structureStopProfit.dynamicStopLoss.dailyPeriod" size="small" style="width: 100%;">
                          <el-option label="5M" value="5" />
                          <el-option label="15M" value="15" />
                          <el-option label="30M" value="30" />
                          <el-option label="1H" value="60" />
                        </el-select>
                      </div>
                      <div style="width: 130px;">
                        <el-input-number v-model="strategyForm.riskControl.structureStopProfit.dynamicStopLoss.dailyBuffer" :min="0.01" :max="1.00" :step="0.01" :precision="2" size="small" style="width: 100%;" controls-position="right" />
                      </div>
                      <div style="flex: 1; color: var(--text-secondary); font-size: 13px;">位置不理想 / 结构老化时使用</div>
                    </div>
                    <div style="display: flex; align-items: center; padding: 10px 16px; border-bottom: 1px solid var(--border-color);">
                      <div style="width: 80px; font-weight: 500; color: var(--text-primary);">第二道</div>
                      <div style="width: 140px;">
                        <el-select v-model="strategyForm.riskControl.structureStopProfit.dynamicStopLoss.bufferPeriod" size="small" style="width: 100%;">
                          <el-option label="15M" value="15" />
                          <el-option label="30M" value="30" />
                          <el-option label="1H" value="60" />
                          <el-option label="2H" value="120" />
                          <el-option label="4H" value="240" />
                        </el-select>
                      </div>
                      <div style="width: 130px;">
                        <el-input-number v-model="strategyForm.riskControl.structureStopProfit.dynamicStopLoss.bufferBuffer" :min="0.01" :max="1.00" :step="0.01" :precision="2" size="small" style="width: 100%;" controls-position="right" />
                      </div>
                      <div style="flex: 1; color: var(--text-secondary); font-size: 13px;">位置理想 + 结构新鲜时使用</div>
                    </div>
                    <div style="display: flex; align-items: center; padding: 10px 16px;">
                      <div style="width: 80px; font-weight: 500; color: var(--text-primary);">第三道</div>
                      <div style="width: 140px;">
                        <el-select v-model="strategyForm.riskControl.structureStopProfit.dynamicStopLoss.ultimatePeriod" size="small" style="width: 100%;">
                          <el-option label="1H" value="60" />
                          <el-option label="2H" value="120" />
                          <el-option label="4H" value="240" />
                          <el-option label="1D" value="1440" />
                        </el-select>
                      </div>
                      <div style="width: 130px;">
                        <el-input-number v-model="strategyForm.riskControl.structureStopProfit.dynamicStopLoss.ultimateBuffer" :min="0.01" :max="1.00" :step="0.01" :precision="2" size="small" style="width: 100%;" controls-position="right" />
                      </div>
                      <div style="flex: 1; color: var(--text-secondary); font-size: 13px;">结构破坏时强制触发</div>
                    </div>
                  </div>
                  <el-form-item label=" ">
                    <el-checkbox v-model="strategyForm.riskControl.structureStopProfit.dynamicStopLoss.autoEnableUltimate">结构破坏自动启用终极防线</el-checkbox>
                  </el-form-item>
                </el-card>

                <!-- 主动止盈 -->
                <el-card shadow="never" style="margin-bottom: 16px;">
                  <template #header><b>主动止盈</b></template>
                  <div style="border: 1px solid var(--border-color); border-radius: 4px; margin-bottom: 16px;">
                    <div style="display: flex; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-color); padding: 8px 16px; font-size: 13px; color: var(--text-secondary); font-weight: 500;">
                      <div style="flex: 1;">止盈目标</div>
                      <div style="width: 160px; text-align: center;">平仓比例</div>
                    </div>
                    <div style="display: flex; align-items: center; padding: 10px 16px; border-bottom: 1px solid var(--border-color);">
                      <div style="flex: 1; color: var(--text-secondary); font-size: 13px;">1H 对立订单块</div>
                      <div style="width: 160px; display: flex; align-items: center; justify-content: center; gap: 4px;">
                        <el-input-number v-model="strategyForm.riskControl.structureStopProfit.takeProfitActive.ob1hClosePct" :min="0" :max="100" :step="5" size="small" style="width: 100px;" controls-position="right" />
                        <span style="font-size: 13px; color: var(--text-secondary);">%</span>
                      </div>
                    </div>
                    <div style="display: flex; align-items: center; padding: 10px 16px; border-bottom: 1px solid var(--border-color);">
                      <div style="flex: 1; color: var(--text-secondary); font-size: 13px;">4H 前高/前低</div>
                      <div style="width: 160px; display: flex; align-items: center; justify-content: center; gap: 4px;">
                        <el-input-number v-model="strategyForm.riskControl.structureStopProfit.takeProfitActive.swingClosePct" :min="0" :max="100" :step="5" size="small" style="width: 100px;" controls-position="right" />
                        <span style="font-size: 13px; color: var(--text-secondary);">%</span>
                      </div>
                    </div>
                    <div style="display: flex; align-items: center; padding: 10px 16px;">
                      <div style="flex: 1; color: var(--text-secondary); font-size: 13px;">1H FVG</div>
                      <div style="width: 160px; display: flex; align-items: center; justify-content: center; gap: 4px;">
                        <el-input-number v-model="strategyForm.riskControl.structureStopProfit.takeProfitActive.fvgClosePct" :min="0" :max="100" :step="5" size="small" style="width: 100px;" controls-position="right" placeholder="选填" />
                        <span style="font-size: 13px; color: var(--text-tertiary);">% <span style="color: var(--text-disabled);">(选填)</span></span>
                      </div>
                    </div>
                  </div>
                  <div style="border: 1px solid var(--border-color); border-radius: 4px;">
                    <div style="display: flex; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-color); padding: 8px 16px; font-size: 13px; color: var(--text-secondary); font-weight: 500;">盈亏比约束</div>
                    <div style="display: flex; align-items: center; padding: 10px 16px; gap: 40px;">
                      <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="font-size: 13px; color: var(--text-secondary);">最小盈亏比</span>
                        <el-input-number v-model="strategyForm.riskControl.structureStopProfit.takeProfitActive.minRR" :min="0.5" :max="10" :step="0.1" :precision="1" size="small" style="width: 100px;" controls-position="right" />
                        <span style="font-size: 13px; color: var(--text-secondary);">: 1</span>
                      </div>
                      <div style="display: flex; align-items: center; gap: 8px;">
                        <span style="font-size: 13px; color: var(--text-secondary);">最大盈亏比</span>
                        <el-input-number v-model="strategyForm.riskControl.structureStopProfit.takeProfitActive.maxRR" :min="0.5" :max="10" :step="0.1" :precision="1" size="small" style="width: 100px;" controls-position="right" />
                        <span style="font-size: 13px; color: var(--text-secondary);">: 1</span>
                      </div>
                    </div>
                  </div>
                </el-card>

                <!-- 移动止损 + 保本 -->
                <el-card shadow="never" style="margin-bottom: 16px;">
                  <template #header><b>移动止损 + 保本</b></template>
                  <el-row :gutter="40">
                    <el-col :span="12">
                      <el-form-item label="移动止损">
                        <el-switch v-model="strategyForm.riskControl.structureStopProfit.trailingProtection.trailingEnabled" />
                      </el-form-item>
                      <template v-if="strategyForm.riskControl.structureStopProfit.trailingProtection.trailingEnabled">
                        <el-form-item label="移动缓冲">
                          <el-input-number v-model="strategyForm.riskControl.structureStopProfit.trailingProtection.trailingBuffer" :min="0.01" :max="1.00" :step="0.01" :precision="2" size="small" style="width: 120px;" />
                          <span style="margin-left: 8px;">%</span>
                        </el-form-item>
                      </template>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="保本止损">
                        <el-switch v-model="strategyForm.riskControl.structureStopProfit.trailingProtection.breakevenEnabled" />
                      </el-form-item>
                      <template v-if="strategyForm.riskControl.structureStopProfit.trailingProtection.breakevenEnabled">
                        <el-form-item label="触发条件"><span style="color: var(--text-secondary);">TP1触发后</span></el-form-item>
                        <el-form-item label="保本缓冲">
                          <el-input-number v-model="strategyForm.riskControl.structureStopProfit.trailingProtection.breakevenBuffer" :min="0.01" :max="0.50" :step="0.01" :precision="2" size="small" style="width: 120px;" />
                          <span style="margin-left: 8px;">%</span>
                        </el-form-item>
                      </template>
                    </el-col>
                  </el-row>
                </el-card>

                <!-- 参考时间框架 -->
                <el-card shadow="never" style="margin-bottom: 16px;">
                  <template #header><b>参考时间框架</b></template>
                  <el-row :gutter="20">
                    <el-col :span="8">
                      <el-form-item label="止损结构周期">
                        <el-select v-model="strategyForm.riskControl.structureStopProfit.reference.stopLossPeriod" style="width: 120px;">
                          <el-option label="5分钟" value="5" />
                          <el-option label="15分钟" value="15" />
                          <el-option label="30分钟" value="30" />
                          <el-option label="1小时" value="60" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="止盈目标周期">
                        <el-select v-model="strategyForm.riskControl.structureStopProfit.reference.takeProfitPeriod" style="width: 120px;">
                          <el-option label="15分钟" value="15" />
                          <el-option label="30分钟" value="30" />
                          <el-option label="1小时" value="60" />
                          <el-option label="2小时" value="120" />
                          <el-option label="4小时" value="240" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-alert type="info" :closable="false" show-icon style="margin-top: 8px;">
                    <template #default>
                      止损结构周期 → 动态止损、移动止损（跟踪结构点）<br/>
                      止盈目标周期 → 主动止盈（1H/更高）
                    </template>
                  </el-alert>
                </el-card>

                <el-alert type="warning" :closable="false" show-icon>
                  <template #default>同一时间只有一道止损生效，根据市场状态自动切换</template>
                </el-alert>
              </template>

              <!-- ==================== 自动模式 ==================== -->
              <template v-else>
                <div style="border: 1px solid var(--border-color); border-radius: 4px; margin-bottom: 16px;">
                  <div style="display: flex; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-color); padding: 8px 16px; font-size: 13px; color: var(--text-secondary); font-weight: 500;">
                    【自动模式】
                  </div>
                  <div style="padding: 16px;">
                    <div style="font-size: 13px; color: var(--text-secondary); margin-bottom: 8px;">
                      系统将根据以下因素自动决策出场策略：
                    </div>
                    <div style="display: flex; flex-direction: column; gap: 4px; padding-left: 16px; margin-bottom: 16px;">
                      <div style="font-size: 13px; color: var(--text-secondary);">├── 4H波次（确认/加速/试盘/赶顶）</div>
                      <div style="font-size: 13px; color: var(--text-secondary);">├── 1H波次（确认/加速/试盘/赶顶）</div>
                      <div style="font-size: 13px; color: var(--text-secondary);">├── 1H位置比（支撑区/中继区/阻力区）</div>
                      <div style="font-size: 13px; color: var(--text-secondary);">└── 综合评分（权重引擎输出）</div>
                    </div>
                    <div style="font-size: 13px; color: var(--text-tertiary); margin-bottom: 16px;">
                      单笔风险已在【仓位控制】中设置，无需重复配置
                    </div>

                    <!-- 决策规则表 -->
                    <div style="font-size: 13px; color: var(--text-secondary); font-weight: 500; margin-bottom: 8px;">决策规则：</div>
                    <div style="border: 1px solid var(--border-color); border-radius: 4px; margin-bottom: 16px; overflow-x: auto;">
                      <div style="display: flex; background: var(--bg-tertiary); border-bottom: 1px solid var(--border-color); padding: 6px 12px; font-size: 12px; color: var(--text-secondary); font-weight: 500; min-width: 600px;">
                        <div style="width: 100px;">开仓质量</div>
                        <div style="width: 80px;">初始止损</div>
                        <div style="width: 80px;">TP1平仓</div>
                        <div style="width: 80px;">TP2平仓</div>
                        <div style="width: 80px;">移动缓冲</div>
                        <div style="width: 80px;">保本</div>
                      </div>
                      <div style="display: flex; align-items: center; padding: 6px 12px; border-bottom: 1px solid var(--border-color); font-size: 12px; color: var(--text-secondary); min-width: 600px;">
                        <div style="width: 100px;">★★★ 黄金</div>
                        <div style="width: 80px;">第二道</div>
                        <div style="width: 80px;">30%</div>
                        <div style="width: 80px;">70%</div>
                        <div style="width: 80px;">0.06%</div>
                        <div style="width: 80px;">启用</div>
                      </div>
                      <div style="display: flex; align-items: center; padding: 6px 12px; border-bottom: 1px solid var(--border-color); font-size: 12px; color: var(--text-secondary); min-width: 600px;">
                        <div style="width: 100px;">★★☆ 优质</div>
                        <div style="width: 80px;">第二道</div>
                        <div style="width: 80px;">50%</div>
                        <div style="width: 80px;">50%</div>
                        <div style="width: 80px;">0.08%</div>
                        <div style="width: 80px;">启用</div>
                      </div>
                      <div style="display: flex; align-items: center; padding: 6px 12px; border-bottom: 1px solid var(--border-color); font-size: 12px; color: var(--text-secondary); min-width: 600px;">
                        <div style="width: 100px;">★☆☆ 普通</div>
                        <div style="width: 80px;">第一道</div>
                        <div style="width: 80px;">70%</div>
                        <div style="width: 80px;">30%</div>
                        <div style="width: 80px;">0.08%</div>
                        <div style="width: 80px;">启用</div>
                      </div>
                      <div style="display: flex; align-items: center; padding: 6px 12px; font-size: 12px; color: var(--text-secondary); min-width: 600px;">
                        <div style="width: 100px;">☆☆☆ 试盘</div>
                        <div style="width: 80px;">第一道收紧</div>
                        <div style="width: 80px;">100%</div>
                        <div style="width: 80px;">0%</div>
                        <div style="width: 80px;">不启用</div>
                        <div style="width: 80px;">启用</div>
                      </div>
                    </div>

                    <div style="margin-bottom: 12px;">
                      <el-checkbox v-model="strategyForm.riskControl.structureStopProfit.dynamicStopLoss.autoEnableUltimate" disabled>
                        结构破坏自动触发终极止损
                      </el-checkbox>
                    </div>
                    <div>
                      <el-checkbox value="true" checked disabled>
                        TP1触发后自动保本
                      </el-checkbox>
                    </div>
                  </div>
                </div>

                <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
                  <template #default>
                    单笔风险由【仓位控制】模块统一管理，避免重复配置
                  </template>
                </el-alert>
              </template>
            </template>

            <el-divider content-position="left">技术止盈</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item>
                  <div
                    style="
                      display: inline-flex;
                      align-items: center;
                      gap: 12px;
                      flex-wrap: wrap;
                    "
                  >
                    <span
                      style="
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                      "
                    >
                      <el-checkbox
                        v-model="
                          strategyForm.riskControl
                            .takeProfitTechnicalMacdEnabled
                        "
                        label="MACD金叉/死叉"
                      />
                      <el-checkbox
                        v-model="
                          strategyForm.riskControl
                            .takeProfitTechnicalMacdMustWin
                        "
                        label="必须盈利"
                        :disabled="
                          !strategyForm.riskControl
                            .takeProfitTechnicalMacdEnabled
                        "
                      />
                    </span>
                    <span
                      style="
                        display: inline-flex;
                        align-items: center;
                        gap: 6px;
                      "
                    >
                      <el-checkbox
                        v-model="
                          strategyForm.riskControl
                            .takeProfitTechnicalPinVolumeEnabled
                        "
                        label="插针放量"
                      />
                      <el-checkbox
                        v-model="
                          strategyForm.riskControl
                            .takeProfitTechnicalPinVolumeMustWin
                        "
                        label="必须盈利"
                        :disabled="
                          !strategyForm.riskControl
                            .takeProfitTechnicalPinVolumeEnabled
                        "
                      />
                    </span>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item>
                  <div
                    style="
                      display: inline-flex;
                      align-items: center;
                      gap: 6px;
                      flex-wrap: wrap;
                    "
                  >
                    <el-checkbox
                      v-model="
                        strategyForm.riskControl.signalReversalExitEnabled
                      "
                      label="反转信号出场"
                    />
                    <el-checkbox
                      v-model="strategyForm.riskControl.signalReversalMustWin"
                      label="必须盈利"
                      :disabled="
                        !strategyForm.riskControl.signalReversalExitEnabled
                      "
                    />
                  </div>
                </el-form-item>
              </el-col>
            </el-row>

            <el-divider content-position="left">时间止盈/止损</el-divider>
            <el-form-item label="时间止盈">
              <el-switch
                v-model="strategyForm.riskControl.timeBasedTakeProfit.enabled"
              />
              <template v-if="strategyForm.riskControl.timeBasedTakeProfit.enabled">
                <el-input-number
                  v-model="strategyForm.riskControl.timeBasedTakeProfit.percent"
                  :min="0.1"
                  :max="100"
                  :step="0.1"
                  :precision="1"
                  size="small"
                  style="width: 120px; margin-left: 12px"
                />
                <span style="margin-left: 4px">%</span>
                <el-checkbox-group
                  v-model="strategyForm.riskControl.timeBasedTakeProfit.days"
                  style="margin-left: 16px"
                >
                  <el-checkbox label="MONDAY">周一</el-checkbox>
                  <el-checkbox label="TUESDAY">周二</el-checkbox>
                  <el-checkbox label="WEDNESDAY">周三</el-checkbox>
                  <el-checkbox label="THURSDAY">周四</el-checkbox>
                  <el-checkbox label="FRIDAY">周五</el-checkbox>
                  <el-checkbox label="SATURDAY">周六</el-checkbox>
                  <el-checkbox label="SUNDAY">周日</el-checkbox>
                </el-checkbox-group>
              </template>
            </el-form-item>

            <el-form-item label="时间止损">
              <el-switch
                v-model="strategyForm.riskControl.timeBasedStopLoss.enabled"
              />
              <template v-if="strategyForm.riskControl.timeBasedStopLoss.enabled">
                <el-input-number
                  v-model="strategyForm.riskControl.timeBasedStopLoss.percent"
                  :min="0.1"
                  :max="100"
                  :step="0.1"
                  :precision="1"
                  size="small"
                  style="width: 120px; margin-left: 12px"
                />
                <span style="margin-left: 4px">%</span>
                <el-checkbox-group
                  v-model="strategyForm.riskControl.timeBasedStopLoss.days"
                  style="margin-left: 16px"
                >
                  <el-checkbox label="MONDAY">周一</el-checkbox>
                  <el-checkbox label="TUESDAY">周二</el-checkbox>
                  <el-checkbox label="WEDNESDAY">周三</el-checkbox>
                  <el-checkbox label="THURSDAY">周四</el-checkbox>
                  <el-checkbox label="FRIDAY">周五</el-checkbox>
                  <el-checkbox label="SATURDAY">周六</el-checkbox>
                  <el-checkbox label="SUNDAY">周日</el-checkbox>
                </el-checkbox-group>
              </template>
            </el-form-item>

            <!-- 分批止盈 -->
            <el-divider content-position="left">分批止盈</el-divider>
            <el-form-item label="启用分批止盈">
              <el-switch
                v-model="strategyForm.riskControl.batchTakeProfitEnabled"
              />
            </el-form-item>
            <template v-if="strategyForm.riskControl.batchTakeProfitEnabled">
              <el-form-item label="分批次数">
                <el-input-number
                  v-model="strategyForm.riskControl.batchTakeProfitCount"
                  :min="2"
                  :max="10"
                  @change="syncBatchTakeProfitPlans"
                />
                <span class="form-hint" style="margin-left: 8px">批（总仓位 100%）</span>
              </el-form-item>
              <el-form-item label="分批明细">
                <el-table
                  :data="strategyForm.riskControl.batchTakeProfitPlans"
                  border
                  size="small"
                  style="max-width: 480px"
                >
                  <el-table-column label="批次" width="60" align="center">
                    <template #default="{ $index }">{{ $index + 1 }}</template>
                  </el-table-column>
                  <el-table-column label="止盈幅度(%)" min-width="120">
                    <template #default="{ row }">
                      <el-input-number
                        v-model="row.profitPercent"
                        :min="0.1"
                        :max="100"
                        :step="0.5"
                        size="small"
                        style="width: 100%"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="仓位(%)" min-width="100">
                    <template #default="{ row }">
                      <el-input-number
                        v-model="row.positionPercent"
                        :min="0"
                        :max="100"
                        :step="5"
                        size="small"
                        style="width: 100%"
                      />
                    </template>
                  </el-table-column>
                </el-table>
                <div class="form-hint" style="margin-top: 4px">
                  仓位合计：{{ batchTakeProfitTotal }}%（需为 100%）
                  <el-tag v-if="batchTakeProfitTotal !== 100" type="warning" size="small" style="margin-left: 8px">未达 100%</el-tag>
                </div>
              </el-form-item>
            </template>
          </template>
        </el-card>

        <!-- 分批止损 -->
        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><WarningFilled /></el-icon>
              <span>分批止损</span>
            </div>
          </template>
          <el-form-item label="启用分批止损">
            <el-switch
              v-model="strategyForm.riskControl.batchStopLossEnabled"
            />
          </el-form-item>
          <template v-if="strategyForm.riskControl.batchStopLossEnabled">
            <el-form-item label="分批次数">
              <el-input-number
                v-model="strategyForm.riskControl.batchStopLossCount"
                :min="2"
                :max="10"
                @change="syncBatchStopLossPlans"
              />
              <span class="form-hint" style="margin-left: 8px">批（总仓位 100%）</span>
            </el-form-item>
            <el-form-item label="分批明细">
              <el-table
                :data="strategyForm.riskControl.batchStopLossPlans"
                border
                size="small"
                style="max-width: 480px"
              >
                <el-table-column label="批次" width="60" align="center">
                  <template #default="{ $index }">{{ $index + 1 }}</template>
                </el-table-column>
                <el-table-column label="止损幅度(%)" min-width="120">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.lossPercent"
                      :min="0.1"
                      :max="100"
                      :step="0.5"
                      size="small"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="仓位(%)" min-width="100">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.positionPercent"
                      :min="0"
                      :max="100"
                      :step="5"
                      size="small"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
              </el-table>
              <div class="form-hint" style="margin-top: 4px">
                仓位合计：{{ batchStopLossTotal }}%（需为 100%）
                <el-tag v-if="batchStopLossTotal !== 100" type="warning" size="small" style="margin-left: 8px">未达 100%</el-tag>
              </div>
            </el-form-item>
          </template>
        </el-card>

        <!-- 分批移动止盈 -->
        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><Top /></el-icon>
              <span>分批移动止盈</span>
            </div>
          </template>
          <el-form-item label="启用分批移动止盈">
            <el-switch
              v-model="strategyForm.riskControl.batchTrailingGainEnabled"
            />
          </el-form-item>
          <template v-if="strategyForm.riskControl.batchTrailingGainEnabled">
            <el-form-item label="分批次数">
              <el-input-number
                v-model="strategyForm.riskControl.batchTrailingGainCount"
                :min="2"
                :max="10"
                @change="syncBatchTrailingGainPlans"
              />
              <span class="form-hint" style="margin-left: 8px">批（总仓位 100%）</span>
            </el-form-item>
            <el-form-item label="分批明细">
              <el-table
                :data="strategyForm.riskControl.batchTrailingGainPlans"
                border
                size="small"
                style="max-width: 480px"
              >
                <el-table-column label="批次" width="60" align="center">
                  <template #default="{ $index }">{{ $index + 1 }}</template>
                </el-table-column>
                <el-table-column label="追踪幅度(%)" min-width="120">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.trailPercent"
                      :min="0.1"
                      :max="100"
                      :step="0.5"
                      size="small"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="仓位(%)" min-width="100">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.positionPercent"
                      :min="0"
                      :max="100"
                      :step="5"
                      size="small"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
              </el-table>
              <div class="form-hint" style="margin-top: 4px">
                仓位合计：{{ batchTrailingGainTotal }}%（需为 100%）
                <el-tag v-if="batchTrailingGainTotal !== 100" type="warning" size="small" style="margin-left: 8px">未达 100%</el-tag>
              </div>
            </el-form-item>
          </template>
        </el-card>

        <!-- 分批移动止损 -->
        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><Bottom /></el-icon>
              <span>分批移动止损</span>
            </div>
          </template>
          <el-form-item label="启用分批移动止损">
            <el-switch
              v-model="strategyForm.riskControl.batchTrailingLossEnabled"
            />
          </el-form-item>
          <template v-if="strategyForm.riskControl.batchTrailingLossEnabled">
            <el-form-item label="分批次数">
              <el-input-number
                v-model="strategyForm.riskControl.batchTrailingLossCount"
                :min="2"
                :max="10"
                @change="syncBatchTrailingLossPlans"
              />
              <span class="form-hint" style="margin-left: 8px">批（总仓位 100%）</span>
            </el-form-item>
            <el-form-item label="分批明细">
              <el-table
                :data="strategyForm.riskControl.batchTrailingLossPlans"
                border
                size="small"
                style="max-width: 480px"
              >
                <el-table-column label="批次" width="60" align="center">
                  <template #default="{ $index }">{{ $index + 1 }}</template>
                </el-table-column>
                <el-table-column label="追踪幅度(%)" min-width="120">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.trailPercent"
                      :min="0.1"
                      :max="100"
                      :step="0.5"
                      size="small"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="仓位(%)" min-width="100">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.positionPercent"
                      :min="0"
                      :max="100"
                      :step="5"
                      size="small"
                      style="width: 100%"
                    />
                  </template>
                </el-table-column>
              </el-table>
              <div class="form-hint" style="margin-top: 4px">
                仓位合计：{{ batchTrailingLossTotal }}%（需为 100%）
                <el-tag v-if="batchTrailingLossTotal !== 100" type="warning" size="small" style="margin-left: 8px">未达 100%</el-tag>
              </div>
            </el-form-item>
          </template>
        </el-card>

        <!-- AI 智能过滤 -->
        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><Setting /></el-icon>
              <span>AI 智能过滤</span>
            </div>
          </template>

          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="启用AI过滤">
                <el-switch v-model="strategyForm.autoSignal.enabled" />
                <el-tooltip content="启用后，系统会在信号生成后调用 AI 进行智能决策过滤，决定是否执行该信号">
                  <el-icon style="margin-left: 8px; cursor: help; color: #909399">
                    <QuestionFilled />
                  </el-icon>
                </el-tooltip>
              </el-form-item>
            </el-col>
          </el-row>

          <template v-if="strategyForm.autoSignal.enabled">
            <el-divider content-position="left">过滤规则</el-divider>
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="客观评分阈值（直接允许）">
                  <el-input-number
                    v-model="strategyForm.autoSignal.allowThreshold"
                    :min="30"
                    :max="100"
                    :step="5"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="客观评分阈值（直接拒绝）">
                  <el-input-number
                    v-model="strategyForm.autoSignal.rejectThreshold"
                    :min="10"
                    :max="80"
                    :step="5"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="强度调整上限">
                  <el-input-number
                    v-model="strategyForm.autoSignal.maxStrength"
                    :min="0.1"
                    :max="2.0"
                    :step="0.1"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <div class="form-hint" style="margin: 0 0 12px 120px; color: #909399; font-size: 12px">
              <div>• 客观评分 &ge; 允许阈值 → 信号直接放行</div>
              <div>• 客观评分 &le; 拒绝阈值 → 信号直接拦截（强度设为 0）</div>
              <div>• 介于两者之间 → 调用 LLM 进行综合决策</div>
              <div>• LLM 返回强度上限不超过 {{ strategyForm.autoSignal.maxStrength }}</div>
            </div>
          </template>
        </el-card>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import {
  Document,
  InfoFilled,
  Setting,
  WarningFilled,
  Plus,
  Delete,
  QuestionFilled,
} from "@element-plus/icons-vue";
import * as strategyApi from "@/api/strategy";

// 定义 props
const props = defineProps({
  strategyId: {
    type: String,
    default: null,
  },
  strategyData: {
    type: Object,
    default: null,
  },
});

// 定义 emits
const emit = defineEmits(["close", "saved"]);

const router = useRouter();
const route = useRoute();
const saving = ref(false);
const loading = ref(false);

// 固定止损类型（单选）：'' | 'fixed_percent' | 'atr_based'
const stopLossFixedType = ref<"fixed_percent" | "atr_based" | "">("");

function onStopLossFixedTypeChange(val: "" | "fixed_percent" | "atr_based") {
  strategyForm.riskControl.stopLoss.fixed_percent.enabled =
    val === "fixed_percent";
  strategyForm.riskControl.stopLoss.atr_based.enabled = val === "atr_based";
}

const strategyForm = reactive({
  id: "",
  strategyId: "", // 信号ID：BOLL_RSI, MACD, RANGE_FILTER, COMBINED，对应后端 strategyId
  name: "",
  type: "",
  description: "",
  className: "",
  timeframe: "",
  code: "",
  maxPosition: 100,
  stopLoss: 5, // 保留旧字段以兼容
  takeProfit: 10, // 保留旧字段以兼容
  riskControl: {
    positionMode: 'risk_based',
    basePositionPct: 20,
    singleTradeRiskPct: 1.0,
    maxPositionPerTrade: 20,
    minPosition: 0.01,
    positionManagementEnabled: false,
    allowAddPosition: false,
    addPosOnProfitPct: 0,
    addPosOnLossPct: 0,
    addPosOnProfitGapPct: 0,
    addPosOnLossGapPct: 0,
    signalFrequencyEnabled: false,
    signalFrequencyGranularity: '15min',
    signalFrequencyMode: 'structure_upgrade_exempt',
    stopLoss: {
      fixed_percent: { enabled: false, percent: 2.0 },
      fixed_percent_trailing: { enabled: false, percent: 2.0, barCount: null },
      atr_based: { enabled: false, atrMultiplier: 2.0, atrPeriod: 14 },
      atr_trailing: { enabled: true, atrMultiplier: 2.0, atrPeriod: 14 },
    },
    takeProfitEnabled: true,
    takeProfitType: "atr_based",
    takeProfitPercent: 2.0,
    takeProfitAtrMultiplier: 3.0,
    takeProfitAtrPeriod: 14,
    takeProfitTechnicalEnabled: false,
    takeProfitTechnicalMode: "macd_cross",
    takeProfitTechnicalMacdEnabled: false,
    takeProfitTechnicalPinVolumeEnabled: false,
    takeProfitTechnicalMacdMustWin: false,
    takeProfitTechnicalPinVolumeMustWin: false,
    signalReversalExitEnabled: false,
    signalReversalMustWin: false,
    timeBasedTakeProfit: {
      enabled: true,
      percent: 0.5,
      days: ["FRIDAY", "SATURDAY", "SUNDAY"],
    },
    timeBasedStopLoss: {
      enabled: false,
      percent: 0.5,
      days: ["FRIDAY", "SATURDAY", "SUNDAY"],
    },
    // SMC 出场规则 (前端显示为"支撑与压力出场")
    smcExit: {
      enabled: false,
      activeTakeProfit: {
        enabled: true,
        ob15m: { enabled: true, closePercent: 50 },
        ob1h: { enabled: true, closePercent: 100 },
        higher: { enabled: false, period: "240", closePercent: 100 },
      },
      passiveExit: { enabled: true, reverseChoch: true, reverseBos: false },
      trailingStop: {
        enabled: true,
        moveToBreakeven: { enabled: true, triggerR: 1.5 },
        trackStructure: { enabled: true, period: "15", point: "internal" },
        mode: "AUTO",
        gear: "MODERATE",
      },
      initialStopOffset: {
        enabled: true,
        mode: "points",
        percent: 0.05,
        points: 0.01,
        stopLossObSameAsTarget: true,
        fixed15mOrderBlock: false,
      },
      reference: { stopStructurePeriod: "15", targetPeriod: "60" },
    },
    smcUseStructureBreak: true,
    smcUsePremiumDiscountExit: false,
    smcUseTargets: true,
    smcUseTrailingStop: true,
    smcTrailingStopPercent: 0.1,
    smcStopLossOffset: 0.005,
    smcPriceZoneResolution: "15",
    smcStopLossPeriod: "15",
    smcTargetPeriod: "60",
    batchTakeProfitEnabled: false,
    batchTakeProfitCount: 2,
    batchTakeProfitPlans: [
      { profitPercent: 5, positionPercent: 50 },
      { profitPercent: 10, positionPercent: 50 },
    ],
    batchStopLossEnabled: false,
    batchStopLossCount: 3,
    batchStopLossPlans: [
      { lossPercent: 2, positionPercent: 30 },
      { lossPercent: 5, positionPercent: 30 },
      { lossPercent: 8, positionPercent: 40 },
    ],
    batchTrailingGainEnabled: false,
    batchTrailingGainCount: 3,
    batchTrailingGainPlans: [
      { trailPercent: 2, positionPercent: 30 },
      { trailPercent: 4, positionPercent: 30 },
      { trailPercent: 6, positionPercent: 40 },
    ],
    batchTrailingLossEnabled: false,
    batchTrailingLossCount: 3,
    batchTrailingLossPlans: [
      { trailPercent: 2, positionPercent: 30 },
      { trailPercent: 4, positionPercent: 30 },
      { trailPercent: 6, positionPercent: 40 },
    ],
    // 基于结构止盈止损（SMC多层出场体系）
    structureStopProfit: {
      enabled: false,
      mode: 'auto',
      // 动态止损
      dynamicStopLoss: {
        dailyPeriod: '15',
        dailyBuffer: 0.08,
        bufferPeriod: '60',
        bufferBuffer: 0.12,
        ultimatePeriod: '240',
        ultimateBuffer: 0.25,
        autoEnableUltimate: true,
      },
      // 主动止盈
      takeProfitActive: {
        ob1hClosePct: 50,
        swingClosePct: 50,
        fvgClosePct: null,
        minRR: 1.2,
        maxRR: 4.0,
      },
      // 移动止损 + 保本
      trailingProtection: {
        trailingEnabled: true,
        trailingBuffer: 0.08,
        breakevenEnabled: true,
        breakevenBuffer: 0.05,
      },
      // 参考时间框架
      reference: {
        stopLossPeriod: '15',
        takeProfitPeriod: '60',
      },
    },
    // 动态风控引擎（移动止损/移动止盈，结构追踪）
    dynamicRiskEngine: {
      trailingStop: {
        enabled: true,
        algorithm: 'structure',
        period: '15',
        structureTypes: { ob: true, swing: true },
        offsetBuffer: 0.05,
        breakMode: 'wick',
        activation: 'open',
      },
      trailingTakeProfit: {
        enabled: true,
        algorithm: 'structure',
        period: '60',
        structureTypes: { ob: true, swing: true },
        offsetBuffer: 0.05,
        triggerMode: 'wick',
        activation: 'open',
        exitMode: 'all',
        minStepEnabled: true,
        minStep: 0.1,
      },
    },
  },
  autoSignal: {
    enabled: false,
    allowThreshold: 50,
    rejectThreshold: 15,
    maxStrength: 1.5,
  },
});

// 动态风控引擎快捷引用（响应式代理，便于模板绑定）
const trailingStop = strategyForm.riskControl.dynamicRiskEngine.trailingStop;
const trailingTakeProfit = strategyForm.riskControl.dynamicRiskEngine.trailingTakeProfit;

const availableIndicators = [
  { value: 'signal', label: '信号入场' },
  { value: 'rsi', label: 'RSI' },
  { value: 'macd', label: 'MACD' },
  { value: 'ma', label: '移动平均线' },
  { value: 'bollinger', label: '布林带' },
  { value: 'stoch', label: '随机指标' },
  { value: 'volume', label: '成交量' },
  { value: 'atr', label: 'ATR' },
];

const defaultCondition = () => ({
  indicator: '',
  period: '',
  operator: 'gt',
  threshold: '',
  connector: 'and',
});

const entryRules = reactive({
  long: {
    disabled: false,
    conditions: [],
  },
  short: {
    disabled: false,
    conditions: [],
  },
});

function onIndicatorChange(cond: any) {
  if (cond.indicator === 'signal') {
    cond.period = '';
    cond.operator = '';
    cond.threshold = '';
  } else if (cond.indicator === 'rsi') cond.period = '14';
  else if (cond.indicator === 'macd') cond.period = '12,26,9';
  else if (cond.indicator === 'ma') cond.period = '20';
  else if (cond.indicator === 'bollinger') cond.period = '20,2';
  else if (cond.indicator === 'stoch') cond.period = '14,3,3';
  else if (cond.indicator === 'volume') cond.period = '20';
  else if (cond.indicator === 'atr') cond.period = '14';
}

function addCondition(direction: 'long' | 'short') {
  (entryRules[direction].conditions as any[]).push(defaultCondition());
}

function removeCondition(direction: 'long' | 'short', index: number) {
  (entryRules[direction].conditions as any[]).splice(index, 1);
}

// 分批止盈：根据分批次数同步明细行，并尽量保持总仓位 100%
function syncBatchTakeProfitPlans() {
  const n = Math.max(
    2,
    Math.min(10, strategyForm.riskControl.batchTakeProfitCount),
  );
  const plans = strategyForm.riskControl.batchTakeProfitPlans;
  if (plans.length === n) return;
  if (plans.length < n) {
    const per = Math.floor(100 / n);
    while (plans.length < n) {
      plans.push({ profitPercent: 5 + plans.length * 2, positionPercent: per });
    }
    plans[plans.length - 1].positionPercent = 100 - per * (n - 1);
  } else {
    strategyForm.riskControl.batchTakeProfitPlans = plans.slice(0, n);
  }
}

// 分批止盈仓位合计（用于校验 100%）
const batchTakeProfitTotal = computed(() => {
  const plans = strategyForm.riskControl.batchTakeProfitPlans || [];
  return plans.reduce((sum, p) => sum + (Number(p.positionPercent) || 0), 0);
});

// 分批止损：根据分批次数同步明细行
function syncBatchStopLossPlans() {
  const n = Math.max(2, Math.min(10, strategyForm.riskControl.batchStopLossCount));
  const plans = strategyForm.riskControl.batchStopLossPlans;
  if (plans.length === n) return;
  if (plans.length < n) {
    const per = Math.floor(100 / n);
    while (plans.length < n) {
      plans.push({ lossPercent: 2 + plans.length * 3, positionPercent: per });
    }
    plans[plans.length - 1].positionPercent = 100 - per * (n - 1);
  } else {
    strategyForm.riskControl.batchStopLossPlans = plans.slice(0, n);
  }
}

// 分批止损仓位合计
const batchStopLossTotal = computed(() => {
  const plans = strategyForm.riskControl.batchStopLossPlans || [];
  return plans.reduce((sum, p) => sum + (Number(p.positionPercent) || 0), 0);
});

// 分批移动止盈：根据分批次数同步明细行
function syncBatchTrailingGainPlans() {
  const n = Math.max(2, Math.min(10, strategyForm.riskControl.batchTrailingGainCount));
  const plans = strategyForm.riskControl.batchTrailingGainPlans;
  if (plans.length === n) return;
  if (plans.length < n) {
    const per = Math.floor(100 / n);
    while (plans.length < n) {
      plans.push({ trailPercent: 2 + plans.length * 2, positionPercent: per });
    }
    plans[plans.length - 1].positionPercent = 100 - per * (n - 1);
  } else {
    strategyForm.riskControl.batchTrailingGainPlans = plans.slice(0, n);
  }
}

// 分批移动止盈仓位合计
const batchTrailingGainTotal = computed(() => {
  const plans = strategyForm.riskControl.batchTrailingGainPlans || [];
  return plans.reduce((sum, p) => sum + (Number(p.positionPercent) || 0), 0);
});

// 分批移动止损：根据分批次数同步明细行
function syncBatchTrailingLossPlans() {
  const n = Math.max(2, Math.min(10, strategyForm.riskControl.batchTrailingLossCount));
  const plans = strategyForm.riskControl.batchTrailingLossPlans;
  if (plans.length === n) return;
  if (plans.length < n) {
    const per = Math.floor(100 / n);
    while (plans.length < n) {
      plans.push({ trailPercent: 2 + plans.length * 2, positionPercent: per });
    }
    plans[plans.length - 1].positionPercent = 100 - per * (n - 1);
  } else {
    strategyForm.riskControl.batchTrailingLossPlans = plans.slice(0, n);
  }
}

// 分批移动止损仓位合计
const batchTrailingLossTotal = computed(() => {
  const plans = strategyForm.riskControl.batchTrailingLossPlans || [];
  return plans.reduce((sum, p) => sum + (Number(p.positionPercent) || 0), 0);
});

const backToList = () => {
  emit("close");
};

// 填充表单数据
const fillFormFromData = (strategy: any) => {
  if (!strategy) return;

  console.log("🔥 fillFormFromData: 原始策略数据:", strategy);
  console.log(
    "🔥 fillFormFromData: className字段值:",
    strategy?.className,
    "class_name字段值:",
    strategy?.class_name,
  );

  strategyForm.id = strategy.id || strategy.strategyId || "";
  strategyForm.strategyId = strategy.strategyId || "";
  strategyForm.name = strategy.name || "";
  strategyForm.type = strategy.type || strategy.strategyType || "";
  strategyForm.description = strategy.description || "";
  strategyForm.className = strategy.className || strategy.class_name || "";
  strategyForm.code = strategy.code || strategy.codeContent || "";
  strategyForm.timeframe =
    strategy.timeFrame ||
    strategy.timeframe ||
    strategy.interval ||
    strategy.config?.timeframe ||
    strategyForm.timeframe;

  console.log(
    "🔥 fillFormFromData: 填充后的className:",
    strategyForm.className,
  );

  // 其他字段映射
  if (strategy.stopLoss !== undefined) {
    strategyForm.stopLoss = strategy.stopLoss;
  }
  if (strategy.takeProfit !== undefined) {
    strategyForm.takeProfit = strategy.takeProfit;
  }

  // 风控配置映射
  if (strategy.riskControl) {
    try {
      const riskControl =
        typeof strategy.riskControl === "string"
          ? JSON.parse(strategy.riskControl)
          : strategy.riskControl;

      // 仓位控制参数
      if (riskControl.maxPosition !== undefined) {
        strategyForm.maxPosition = riskControl.maxPosition;
      }
      if (riskControl.maxPositionPerTrade !== undefined) {
        strategyForm.riskControl.maxPositionPerTrade =
          riskControl.maxPositionPerTrade;
      }
      if (riskControl.minPosition !== undefined) {
        strategyForm.riskControl.minPosition = riskControl.minPosition;
      }
      if (riskControl.positionManagementEnabled !== undefined) {
        strategyForm.riskControl.positionManagementEnabled =
          riskControl.positionManagementEnabled;
      }
      if (riskControl.positionMode !== undefined) {
        strategyForm.riskControl.positionMode = riskControl.positionMode;
      }
      if (riskControl.basePositionPct !== undefined) {
        strategyForm.riskControl.basePositionPct = riskControl.basePositionPct;
      }
      if (riskControl.singleTradeRiskPct !== undefined) {
        strategyForm.riskControl.singleTradeRiskPct = riskControl.singleTradeRiskPct;
      }
      if (riskControl.allowAddPosition !== undefined) {
        strategyForm.riskControl.allowAddPosition =
          !!riskControl.allowAddPosition;
      }
      if (riskControl.addPosOnProfitPct !== undefined) {
        strategyForm.riskControl.addPosOnProfitPct = riskControl.addPosOnProfitPct;
      }
      if (riskControl.addPosOnLossPct !== undefined) {
        strategyForm.riskControl.addPosOnLossPct = riskControl.addPosOnLossPct;
      }
      if (riskControl.addPosOnProfitGapPct !== undefined) {
        strategyForm.riskControl.addPosOnProfitGapPct = riskControl.addPosOnProfitGapPct;
      }
      if (riskControl.addPosOnLossGapPct !== undefined) {
        strategyForm.riskControl.addPosOnLossGapPct = riskControl.addPosOnLossGapPct;
      }
      if (riskControl.signalFrequencyEnabled !== undefined) {
        strategyForm.riskControl.signalFrequencyEnabled =
          !!riskControl.signalFrequencyEnabled;
      }
      if (riskControl.signalFrequencyGranularity !== undefined) {
        strategyForm.riskControl.signalFrequencyGranularity = riskControl.signalFrequencyGranularity;
      }
      if (riskControl.signalFrequencyMode !== undefined) {
        strategyForm.riskControl.signalFrequencyMode = riskControl.signalFrequencyMode;
      }

      // 止损设置（多选结构或旧单选结构）
      if (riskControl.stopLoss) {
        const sl = riskControl.stopLoss;
        if (sl.fixed_percent) {
          strategyForm.riskControl.stopLoss.fixed_percent = {
            ...strategyForm.riskControl.stopLoss.fixed_percent,
            ...sl.fixed_percent,
          };
        }
        if (sl.fixed_percent_trailing) {
          strategyForm.riskControl.stopLoss.fixed_percent_trailing = {
            ...strategyForm.riskControl.stopLoss.fixed_percent_trailing,
            ...sl.fixed_percent_trailing,
          };
        }
        if (sl.atr_based) {
          strategyForm.riskControl.stopLoss.atr_based = {
            ...strategyForm.riskControl.stopLoss.atr_based,
            ...sl.atr_based,
          };
        }
        if (sl.atr_trailing) {
          strategyForm.riskControl.stopLoss.atr_trailing = {
            ...strategyForm.riskControl.stopLoss.atr_trailing,
            ...sl.atr_trailing,
          };
        }
        if (sl.type && !sl.fixed_percent && !sl.atr_based && !sl.atr_trailing) {
          const t = sl.type;
          if (t === "fixed_percent") {
            strategyForm.riskControl.stopLoss.fixed_percent.enabled = true;
            if (sl.percent !== undefined)
              strategyForm.riskControl.stopLoss.fixed_percent.percent =
                sl.percent;
          } else if (t === "fixed_percent_trailing") {
            strategyForm.riskControl.stopLoss.fixed_percent_trailing.enabled = true;
            if (sl.percent !== undefined)
              strategyForm.riskControl.stopLoss.fixed_percent_trailing.percent =
                sl.percent;
            if (sl.barCount !== undefined)
              strategyForm.riskControl.stopLoss.fixed_percent_trailing.barCount =
                sl.barCount;
          } else if (t === "atr_based") {
            strategyForm.riskControl.stopLoss.atr_based.enabled = true;
            if (sl.atrMultiplier !== undefined)
              strategyForm.riskControl.stopLoss.atr_based.atrMultiplier =
                sl.atrMultiplier;
            if (sl.atrPeriod !== undefined)
              strategyForm.riskControl.stopLoss.atr_based.atrPeriod =
                sl.atrPeriod;
          } else if (t === "atr_trailing") {
            strategyForm.riskControl.stopLoss.atr_trailing.enabled = true;
            if (sl.atrMultiplier !== undefined)
              strategyForm.riskControl.stopLoss.atr_trailing.atrMultiplier =
                sl.atrMultiplier;
            if (sl.atrPeriod !== undefined)
              strategyForm.riskControl.stopLoss.atr_trailing.atrPeriod =
                sl.atrPeriod;
          }
        }
        // 同步固定止损单选显示
        if (strategyForm.riskControl.stopLoss.fixed_percent.enabled)
          stopLossFixedType.value = "fixed_percent";
        else if (strategyForm.riskControl.stopLoss.atr_based.enabled)
          stopLossFixedType.value = "atr_based";
        else stopLossFixedType.value = "";
      }
      if (riskControl.takeProfit) {
        const tp = riskControl.takeProfit;
        strategyForm.riskControl.takeProfitEnabled = tp.enabled !== false;
        if (tp.atrMultiplier !== undefined)
          strategyForm.riskControl.takeProfitAtrMultiplier = tp.atrMultiplier;
        if (tp.atrPeriod !== undefined)
          strategyForm.riskControl.takeProfitAtrPeriod = tp.atrPeriod;
        if (tp.percent !== undefined)
          strategyForm.riskControl.takeProfitPercent = tp.percent;
        if (tp.type) strategyForm.riskControl.takeProfitType = tp.type;

        if (tp.fixed_percent?.enabled) {
          strategyForm.riskControl.takeProfitType = "fixed_percent";
          if (tp.fixed_percent.percent !== undefined)
            strategyForm.riskControl.takeProfitPercent =
              tp.fixed_percent.percent;
        } else if (tp.atr_based?.enabled) {
          strategyForm.riskControl.takeProfitType = "atr_based";
          if (tp.atr_based.atrMultiplier !== undefined)
            strategyForm.riskControl.takeProfitAtrMultiplier =
              tp.atr_based.atrMultiplier;
          if (tp.atr_based.atrPeriod !== undefined)
            strategyForm.riskControl.takeProfitAtrPeriod =
              tp.atr_based.atrPeriod;
        }
        if (tp.smcStopLossPeriod !== undefined)
          strategyForm.riskControl.smcStopLossPeriod = String(tp.smcStopLossPeriod);
        if (tp.smcTargetPeriod !== undefined)
          strategyForm.riskControl.smcTargetPeriod = String(tp.smcTargetPeriod);

        if (tp.technical) {
          if (tp.technical.enabled !== undefined)
            strategyForm.riskControl.takeProfitTechnicalEnabled =
              !!tp.technical.enabled;
          if (tp.technical.mode !== undefined) {
            const mode = tp.technical.mode;
            if (mode === "macd_cross") {
              strategyForm.riskControl.takeProfitTechnicalMacdEnabled = true;
              strategyForm.riskControl.takeProfitTechnicalPinVolumeEnabled = false;
              strategyForm.riskControl.takeProfitTechnicalMode = "macd_cross";
            } else if (mode === "pin_volume") {
              strategyForm.riskControl.takeProfitTechnicalPinVolumeEnabled = true;
              strategyForm.riskControl.takeProfitTechnicalMacdEnabled = false;
              strategyForm.riskControl.takeProfitTechnicalMode = "pin_volume";
            } else if (mode === "signal_reversal") {
              strategyForm.riskControl.signalReversalExitEnabled = true;
            }
          }
          if (Array.isArray(tp.technical.modes)) {
            strategyForm.riskControl.takeProfitTechnicalMacdEnabled =
              tp.technical.modes.includes("macd_cross");
            strategyForm.riskControl.takeProfitTechnicalPinVolumeEnabled =
              tp.technical.modes.includes("pin_volume");
          }
          if (tp.technical.macdEnabled !== undefined) {
            strategyForm.riskControl.takeProfitTechnicalMacdEnabled =
              !!tp.technical.macdEnabled;
          }
          if (tp.technical.pinVolumeEnabled !== undefined) {
            strategyForm.riskControl.takeProfitTechnicalPinVolumeEnabled =
              !!tp.technical.pinVolumeEnabled;
          }
          if (tp.technical.mustWin !== undefined)
            strategyForm.riskControl.takeProfitTechnicalMustWin =
              !!tp.technical.mustWin;
          if (tp.technical.mustWinMap) {
            const m = tp.technical.mustWinMap || {};
            if (m.macd_cross !== undefined)
              strategyForm.riskControl.takeProfitTechnicalMacdMustWin =
                !!m.macd_cross;
            if (m.pin_volume !== undefined)
              strategyForm.riskControl.takeProfitTechnicalPinVolumeMustWin =
                !!m.pin_volume;
          }
        }
        if (tp.technicalEnabled !== undefined)
          strategyForm.riskControl.takeProfitTechnicalEnabled =
            !!tp.technicalEnabled;
        if (tp.technicalMode !== undefined) {
          const mode = tp.technicalMode;
          if (mode === "macd_cross") {
            strategyForm.riskControl.takeProfitTechnicalMacdEnabled = true;
            strategyForm.riskControl.takeProfitTechnicalPinVolumeEnabled = false;
            strategyForm.riskControl.takeProfitTechnicalMode = "macd_cross";
          } else if (mode === "pin_volume") {
            strategyForm.riskControl.takeProfitTechnicalPinVolumeEnabled = true;
            strategyForm.riskControl.takeProfitTechnicalMacdEnabled = false;
            strategyForm.riskControl.takeProfitTechnicalMode = "pin_volume";
          } else if (mode === "signal_reversal") {
            strategyForm.riskControl.signalReversalExitEnabled = true;
          }
        }
        if (tp.technicalMustWin !== undefined) {
          if (strategyForm.riskControl.takeProfitTechnicalMacdEnabled) {
            strategyForm.riskControl.takeProfitTechnicalMacdMustWin =
              !!tp.technicalMustWin;
          }
          if (strategyForm.riskControl.takeProfitTechnicalPinVolumeEnabled) {
            strategyForm.riskControl.takeProfitTechnicalPinVolumeMustWin =
              !!tp.technicalMustWin;
          }
        }
        if (tp.technicalMacdEnabled !== undefined)
          strategyForm.riskControl.takeProfitTechnicalMacdEnabled =
            !!tp.technicalMacdEnabled;
        if (tp.technicalPinVolumeEnabled !== undefined)
          strategyForm.riskControl.takeProfitTechnicalPinVolumeEnabled =
            !!tp.technicalPinVolumeEnabled;
        if (tp.macdMustWin !== undefined)
          strategyForm.riskControl.takeProfitTechnicalMacdMustWin =
            !!tp.macdMustWin;
        if (tp.pinVolumeMustWin !== undefined)
          strategyForm.riskControl.takeProfitTechnicalPinVolumeMustWin =
            !!tp.pinVolumeMustWin;
        if (tp.type === "technical")
          strategyForm.riskControl.takeProfitTechnicalEnabled = true;
      }
      if (riskControl.signalReversalExitEnabled !== undefined)
        strategyForm.riskControl.signalReversalExitEnabled =
          !!riskControl.signalReversalExitEnabled;
      if (riskControl.signalReversalMustWin !== undefined)
        strategyForm.riskControl.signalReversalMustWin =
          !!riskControl.signalReversalMustWin;
      if (riskControl.batchTakeProfitEnabled !== undefined)
        strategyForm.riskControl.batchTakeProfitEnabled =
          riskControl.batchTakeProfitEnabled;
      if (riskControl.batchTakeProfitCount !== undefined)
        strategyForm.riskControl.batchTakeProfitCount =
          riskControl.batchTakeProfitCount;
      if (
        riskControl.batchTakeProfitPlans &&
        Array.isArray(riskControl.batchTakeProfitPlans)
      ) {
        strategyForm.riskControl.batchTakeProfitPlans =
          riskControl.batchTakeProfitPlans.map((p: any) => ({
            profitPercent: p.profitPercent ?? 5,
            positionPercent: p.positionPercent ?? 50,
          }));
      }
      if (riskControl.timeBasedTakeProfit) {
        strategyForm.riskControl.timeBasedTakeProfit = {
          ...strategyForm.riskControl.timeBasedTakeProfit,
          ...riskControl.timeBasedTakeProfit,
        };
      }
      if (riskControl.timeBasedStopLoss) {
        strategyForm.riskControl.timeBasedStopLoss = {
          ...strategyForm.riskControl.timeBasedStopLoss,
          ...riskControl.timeBasedStopLoss,
        };
      }
      // SMC 配置回显
      if (riskControl.batchStopLossEnabled !== undefined)
        strategyForm.riskControl.batchStopLossEnabled = riskControl.batchStopLossEnabled;
      if (riskControl.batchStopLossCount !== undefined)
        strategyForm.riskControl.batchStopLossCount = riskControl.batchStopLossCount;
      if (riskControl.batchStopLossPlans && Array.isArray(riskControl.batchStopLossPlans)) {
        strategyForm.riskControl.batchStopLossPlans = riskControl.batchStopLossPlans.map((p: any) => ({
          lossPercent: p.lossPercent ?? 2,
          positionPercent: p.positionPercent ?? 50,
        }));
      }
      if (riskControl.batchTrailingGainEnabled !== undefined)
        strategyForm.riskControl.batchTrailingGainEnabled = riskControl.batchTrailingGainEnabled;
      if (riskControl.batchTrailingGainCount !== undefined)
        strategyForm.riskControl.batchTrailingGainCount = riskControl.batchTrailingGainCount;
      if (riskControl.batchTrailingGainPlans && Array.isArray(riskControl.batchTrailingGainPlans)) {
        strategyForm.riskControl.batchTrailingGainPlans = riskControl.batchTrailingGainPlans.map((p: any) => ({
          trailPercent: p.trailPercent ?? 2,
          positionPercent: p.positionPercent ?? 50,
        }));
      }
      if (riskControl.batchTrailingLossEnabled !== undefined)
        strategyForm.riskControl.batchTrailingLossEnabled = riskControl.batchTrailingLossEnabled;
      if (riskControl.batchTrailingLossCount !== undefined)
        strategyForm.riskControl.batchTrailingLossCount = riskControl.batchTrailingLossCount;
      if (riskControl.batchTrailingLossPlans && Array.isArray(riskControl.batchTrailingLossPlans)) {
        strategyForm.riskControl.batchTrailingLossPlans = riskControl.batchTrailingLossPlans.map((p: any) => ({
          trailPercent: p.trailPercent ?? 2,
          positionPercent: p.positionPercent ?? 50,
        }));
      }
      if (riskControl.smcUseStructureBreak !== undefined) {
        strategyForm.riskControl.smcUseStructureBreak = !!riskControl.smcUseStructureBreak;
      }
      if (riskControl.smcUsePremiumDiscountExit !== undefined) {
        strategyForm.riskControl.smcUsePremiumDiscountExit = !!riskControl.smcUsePremiumDiscountExit;
      }
      if (riskControl.smcUseTargets !== undefined) {
        strategyForm.riskControl.smcUseTargets = !!riskControl.smcUseTargets;
      }
      if (riskControl.smcUseTrailingStop !== undefined) {
        strategyForm.riskControl.smcUseTrailingStop = !!riskControl.smcUseTrailingStop;
      }
      if (riskControl.smcTrailingStopMode !== undefined) {
        strategyForm.riskControl.smcExit.trailingStop.mode = String(riskControl.smcTrailingStopMode);
      }
      if (riskControl.smcTrailingStopGear !== undefined) {
        strategyForm.riskControl.smcExit.trailingStop.gear = String(riskControl.smcTrailingStopGear);
      }
      if (riskControl.smcTrailingStopPercent !== undefined) {
        strategyForm.riskControl.smcTrailingStopPercent = riskControl.smcTrailingStopPercent;
      }
      if (riskControl.smcStopLossOffset !== undefined) {
        strategyForm.riskControl.smcStopLossOffset = riskControl.smcStopLossOffset;
      }
      if (riskControl.smcPriceZoneResolution !== undefined) {
        strategyForm.riskControl.smcPriceZoneResolution = String(riskControl.smcPriceZoneResolution);
      }
      if (riskControl.smcStopLossPeriod !== undefined) {
        strategyForm.riskControl.smcStopLossPeriod = String(riskControl.smcStopLossPeriod);
      }
      if (riskControl.smcTargetPeriod !== undefined) {
        strategyForm.riskControl.smcTargetPeriod = String(riskControl.smcTargetPeriod);
      }
      if (riskControl.smcExit && typeof riskControl.smcExit === "object") {
        const smc = riskControl.smcExit;
        if (smc.enabled !== undefined)
          strategyForm.riskControl.smcExit.enabled = !!smc.enabled;
        if (smc.activeTakeProfit) {
          strategyForm.riskControl.smcExit.activeTakeProfit = {
            ...strategyForm.riskControl.smcExit.activeTakeProfit,
            ...smc.activeTakeProfit,
            ob15m: {
              ...strategyForm.riskControl.smcExit.activeTakeProfit.ob15m,
              ...(smc.activeTakeProfit.ob15m || {}),
            },
            ob1h: {
              ...strategyForm.riskControl.smcExit.activeTakeProfit.ob1h,
              ...(smc.activeTakeProfit.ob1h || {}),
            },
            higher: {
              ...strategyForm.riskControl.smcExit.activeTakeProfit.higher,
              ...(smc.activeTakeProfit.higher || {}),
            },
          };
        }
        if (smc.passiveExit) {
          strategyForm.riskControl.smcExit.passiveExit = {
            ...strategyForm.riskControl.smcExit.passiveExit,
            ...smc.passiveExit,
          };
        }
        if (smc.trailingStop) {
          strategyForm.riskControl.smcExit.trailingStop = {
            ...strategyForm.riskControl.smcExit.trailingStop,
            ...smc.trailingStop,
            moveToBreakeven: {
              ...strategyForm.riskControl.smcExit.trailingStop.moveToBreakeven,
              ...(smc.trailingStop.moveToBreakeven || {}),
            },
            trackStructure: {
              ...strategyForm.riskControl.smcExit.trailingStop.trackStructure,
              ...(smc.trailingStop.trackStructure || {}),
            },
          };
        }
        if (smc.initialStopOffset) {
          strategyForm.riskControl.smcExit.initialStopOffset = {
            ...strategyForm.riskControl.smcExit.initialStopOffset,
            ...smc.initialStopOffset,
          };
        }
        if (smc.reference) {
          strategyForm.riskControl.smcExit.reference = {
            ...strategyForm.riskControl.smcExit.reference,
            ...smc.reference,
          };
        }
      } else {
        strategyForm.riskControl.smcExit.reference.targetPeriod =
          String(strategyForm.riskControl.smcTargetPeriod || "60");
        strategyForm.riskControl.smcExit.reference.stopStructurePeriod =
          String(strategyForm.riskControl.smcStopLossPeriod || "15");
      }
      // 基于结构止盈止损填充
      if (riskControl.structureStopProfit && typeof riskControl.structureStopProfit === "object") {
        const ssp = riskControl.structureStopProfit;
        if (ssp.enabled !== undefined)
          strategyForm.riskControl.structureStopProfit.enabled = !!ssp.enabled;
        if (ssp.mode !== undefined)
          strategyForm.riskControl.structureStopProfit.mode = ssp.mode;
        if (ssp.dynamicStopLoss) {
          strategyForm.riskControl.structureStopProfit.dynamicStopLoss = {
            ...strategyForm.riskControl.structureStopProfit.dynamicStopLoss,
            ...ssp.dynamicStopLoss,
          };
        }
        if (ssp.takeProfitActive) {
          strategyForm.riskControl.structureStopProfit.takeProfitActive = {
            ...strategyForm.riskControl.structureStopProfit.takeProfitActive,
            ...ssp.takeProfitActive,
          };
        }
        if (ssp.trailingProtection) {
          strategyForm.riskControl.structureStopProfit.trailingProtection = {
            ...strategyForm.riskControl.structureStopProfit.trailingProtection,
            ...ssp.trailingProtection,
          };
        }
        if (ssp.reference) {
          strategyForm.riskControl.structureStopProfit.reference = {
            ...strategyForm.riskControl.structureStopProfit.reference,
            ...ssp.reference,
          };
        }
      }
      // 动态风控引擎（移动止损/移动止盈）回显：优先读独立参数组 strategy.dynamicRiskEngine，兜底 riskControl.dynamicRiskEngine
      let dreSource = strategy.dynamicRiskEngine;
      if (dreSource && typeof dreSource === "string") {
        try {
          dreSource = JSON.parse(dreSource);
        } catch (e) {
          dreSource = null;
        }
      }
      if (
        (!dreSource || typeof dreSource !== "object") &&
        riskControl.dynamicRiskEngine &&
        typeof riskControl.dynamicRiskEngine === "object"
      ) {
        dreSource = riskControl.dynamicRiskEngine;
      }
      if (dreSource && typeof dreSource === "object") {
        const dre = dreSource;
        if (dre.trailingStop && typeof dre.trailingStop === "object") {
          const ts = dre.trailingStop;
          const target = strategyForm.riskControl.dynamicRiskEngine.trailingStop;
          if (ts.enabled !== undefined) target.enabled = !!ts.enabled;
          if (ts.algorithm !== undefined) target.algorithm = ts.algorithm;
          if (ts.period !== undefined) target.period = String(ts.period);
          if (ts.structureTypes) target.structureTypes = { ...target.structureTypes, ...ts.structureTypes };
          if (ts.offsetBuffer !== undefined) target.offsetBuffer = ts.offsetBuffer;
          if (ts.breakMode !== undefined) target.breakMode = ts.breakMode;
          if (ts.activation !== undefined) target.activation = ts.activation;
        }
        if (dre.trailingTakeProfit && typeof dre.trailingTakeProfit === "object") {
          const tp = dre.trailingTakeProfit;
          const target = strategyForm.riskControl.dynamicRiskEngine.trailingTakeProfit;
          if (tp.enabled !== undefined) target.enabled = !!tp.enabled;
          if (tp.algorithm !== undefined) target.algorithm = tp.algorithm;
          if (tp.period !== undefined) target.period = String(tp.period);
          if (tp.structureTypes) target.structureTypes = { ...target.structureTypes, ...tp.structureTypes };
          if (tp.offsetBuffer !== undefined) target.offsetBuffer = tp.offsetBuffer;
          if (tp.triggerMode !== undefined) target.triggerMode = tp.triggerMode;
          if (tp.activation !== undefined) target.activation = tp.activation;
          if (tp.exitMode !== undefined) target.exitMode = tp.exitMode;
          if (tp.minStepEnabled !== undefined) target.minStepEnabled = !!tp.minStepEnabled;
          if (tp.minStep !== undefined) target.minStep = tp.minStep;
        }
      }
    } catch (e) {
      console.warn("解析风控配置失败", e);
    }
  }
  // 追加：从后端返回的 positionRisk 组读取（如果存在）
  if (strategy.positionRisk && typeof strategy.positionRisk === "object") {
    const pr = strategy.positionRisk;
    if (pr.maxPosition !== undefined) strategyForm.maxPosition = pr.maxPosition;
    if (pr.maxPositionPerTrade !== undefined)
      strategyForm.riskControl.maxPositionPerTrade = pr.maxPositionPerTrade;
    if (pr.minPosition !== undefined)
      strategyForm.riskControl.minPosition = pr.minPosition;
    if (pr.positionManagementEnabled !== undefined)
      strategyForm.riskControl.positionManagementEnabled =
        !!pr.positionManagementEnabled;
    if (pr.allowAddPosition !== undefined)
      strategyForm.riskControl.allowAddPosition = !!pr.allowAddPosition;
    if (pr.addPosOnProfitPct !== undefined)
      strategyForm.riskControl.addPosOnProfitPct = pr.addPosOnProfitPct;
    if (pr.addPosOnLossPct !== undefined)
      strategyForm.riskControl.addPosOnLossPct = pr.addPosOnLossPct;
    if (pr.addPosOnProfitGapPct !== undefined)
      strategyForm.riskControl.addPosOnProfitGapPct = pr.addPosOnProfitGapPct;
    if (pr.addPosOnLossGapPct !== undefined)
      strategyForm.riskControl.addPosOnLossGapPct = pr.addPosOnLossGapPct;
    if (pr.signalFrequencyEnabled !== undefined)
      strategyForm.riskControl.signalFrequencyEnabled = !!pr.signalFrequencyEnabled;
    if (pr.signalFrequencyGranularity !== undefined)
      strategyForm.riskControl.signalFrequencyGranularity = pr.signalFrequencyGranularity;
    if (pr.signalFrequencyMode !== undefined)
      strategyForm.riskControl.signalFrequencyMode = pr.signalFrequencyMode;
  }

  // AI 智能过滤配置
  if (strategy.autoSignal) {
    try {
      const autoSignal =
        typeof strategy.autoSignal === "string"
          ? JSON.parse(strategy.autoSignal)
          : strategy.autoSignal;
      strategyForm.autoSignal = {
        ...strategyForm.autoSignal,
        ...autoSignal,
      };
    } catch (e) {
      console.warn("解析 autoSignal 配置失败", e);
    }
  }

  // 入场规则回显
  if (strategy.entryRules) {
    try {
      const rules = typeof strategy.entryRules === 'string'
        ? JSON.parse(strategy.entryRules)
        : strategy.entryRules;
      if (rules.long) {
        entryRules.long.disabled = rules.long.disabled || false;
        entryRules.long.conditions = Array.isArray(rules.long.conditions) ? rules.long.conditions : [];
      }
      if (rules.short) {
        entryRules.short.disabled = rules.short.disabled || false;
        entryRules.short.conditions = Array.isArray(rules.short.conditions) ? rules.short.conditions : [];
      }
    } catch (e) {
      console.warn("解析入场规则失败", e);
    }
  }
};

// 加载策略详情
const loadStrategy = async () => {
  // 优先使用 props 中的 strategyId，其次使用 route.params.id
  const strategyId = props.strategyId || route.params.id;

  // 即使通过 props 传递了策略数据，也优先从 API 获取完整数据
  // 因为列表数据可能不完整（缺少 className 等字段），且需要获取最新数据
  if (props.strategyData && !strategyId) {
    // 如果没有 strategyId，只能使用 props 数据（降级方案）
    console.log(
      "🔥 EditStrategy: 使用 props 传入的策略数据（无ID，无法调用API）:",
      props.strategyData,
    );
    fillFormFromData(props.strategyData);
    return;
  }

  if (!strategyId) {
    ElMessage.error("策略ID不存在");
    emit("close");
    return;
  }

  loading.value = true;
  try {
    console.log("🔥 EditStrategy: 开始加载策略, id:", strategyId);
    const response = await strategyApi.getStrategyById(strategyId);
    console.log("🔥 EditStrategy: API响应:", response);
    console.log("🔥 EditStrategy: 响应类型:", typeof response);
    console.log(
      "🔥 EditStrategy: 响应键:",
      response && typeof response === "object" ? Object.keys(response) : "N/A",
    );

    // 处理响应数据（后端返回 ApiResponse 格式）
    // 根据base.ts拦截器，如果success=true，会返回data.data或data
    // 根据strategy.ts的getStrategyById处理，已经做了数据提取
    let strategy = response;

    // 如果响应有data字段（说明是ApiResponse格式，但拦截器可能已经提取）
    if (response && typeof response === "object") {
      if (
        response.data &&
        typeof response.data === "object" &&
        !response.id &&
        !response.strategyId
      ) {
        // 如果response有data字段，但本身不是策略对象（没有id或strategyId），则从data提取
        strategy = response.data;
        console.log("🔥 EditStrategy: 从response.data提取策略数据");
      } else {
        // response本身就是策略数据（拦截器已经提取）
        strategy = response;
        console.log("🔥 EditStrategy: 直接使用response作为策略数据");
      }
    }

    console.log("🔥 EditStrategy: 最终使用的策略数据:", strategy);
    console.log("🔥 EditStrategy: 策略数据字段:", {
      id: strategy?.id,
      strategyId: strategy?.strategyId,
      name: strategy?.name,
      type: strategy?.type,
      strategyType: strategy?.strategyType,
      description: strategy?.description,
      className: strategy?.className,
      code: strategy?.code,
      codeContent: strategy?.codeContent,
    });

    if (!strategy) {
      throw new Error("策略数据为空");
    }

    // 填充表单数据
    fillFormFromData(strategy);

    console.log("🔥 EditStrategy: 填充后的表单数据:", {
      id: strategyForm.id,
      name: strategyForm.name,
      type: strategyForm.type,
      description: strategyForm.description,
      className: strategyForm.className,
      code: strategyForm.code ? strategyForm.code.substring(0, 50) + "..." : "",
    });
  } catch (error) {
    console.error("加载策略失败", error);
    ElMessage.error("加载策略失败: " + (error.message || "未知错误"));
    emit("close");
  } finally {
    loading.value = false;
  }
};

// 监听 props 变化，如果通过抽屉打开并传入数据，立即填充
watch(
  () => props.strategyData,
  (newData) => {
    if (newData) {
      fillFormFromData(newData);
    }
  },
  { immediate: true },
);

watch(
  () => strategyForm.riskControl.takeProfitTechnicalEnabled,
  (val) => {
    if (!val) {
      strategyForm.riskControl.takeProfitTechnicalMustWin = false;
    }
  },
);

watch(
  () => strategyForm.riskControl.signalReversalExitEnabled,
  (val) => {
    if (!val) {
      strategyForm.riskControl.signalReversalMustWin = false;
    }
  },
);

const saveStrategy = async () => {
  if (!strategyForm.name) {
    ElMessage.error("请输入策略名称");
    return;
  }

  if (!strategyForm.id) {
    ElMessage.error("策略ID不存在");
    return;
  }

  saving.value = true;
  try {
    const rc = strategyForm.riskControl;
    // 出场规则：止损（多选）+ 止盈 + 分批止盈
    const exitRules: any = {
      stopLoss: {
        fixed_percent: rc.stopLoss.fixed_percent,
        fixed_percent_trailing: rc.stopLoss.fixed_percent_trailing,
        atr_based: rc.stopLoss.atr_based,
        atr_trailing: rc.stopLoss.atr_trailing,
      },
      takeProfit: {
        enabled: rc.takeProfitEnabled,
        type: rc.takeProfitType || "atr_based",
        percent: rc.takeProfitPercent,
        atrMultiplier: rc.takeProfitAtrMultiplier,
        atrPeriod: rc.takeProfitAtrPeriod,
        technicalEnabled:
          rc.takeProfitTechnicalMacdEnabled ||
          rc.takeProfitTechnicalPinVolumeEnabled,
        technicalMode: rc.takeProfitTechnicalMacdEnabled
          ? "macd_cross"
          : rc.takeProfitTechnicalPinVolumeEnabled
            ? "pin_volume"
            : undefined,
        technicalMustWin: rc.takeProfitTechnicalMustWin,
        technical: {
          enabled:
            rc.takeProfitTechnicalMacdEnabled ||
            rc.takeProfitTechnicalPinVolumeEnabled,
          mode: rc.takeProfitTechnicalMacdEnabled
            ? "macd_cross"
            : rc.takeProfitTechnicalPinVolumeEnabled
              ? "pin_volume"
              : undefined,
          modes: [
            ...(rc.takeProfitTechnicalMacdEnabled ? ["macd_cross"] : []),
            ...(rc.takeProfitTechnicalPinVolumeEnabled ? ["pin_volume"] : []),
          ],
          macdEnabled: !!rc.takeProfitTechnicalMacdEnabled,
          pinVolumeEnabled: !!rc.takeProfitTechnicalPinVolumeEnabled,
          mustWin: rc.takeProfitTechnicalMacdEnabled
            ? !!rc.takeProfitTechnicalMacdMustWin
            : rc.takeProfitTechnicalPinVolumeEnabled
              ? !!rc.takeProfitTechnicalPinVolumeMustWin
              : false,
          mustWinMap: {
            macd_cross: !!rc.takeProfitTechnicalMacdMustWin,
            pin_volume: !!rc.takeProfitTechnicalPinVolumeMustWin,
          },
        },
      },
      signalReversalExitEnabled: rc.signalReversalExitEnabled,
      signalReversalMustWin: rc.signalReversalMustWin,
      timeBasedTakeProfit: rc.timeBasedTakeProfit,
      timeBasedStopLoss: rc.timeBasedStopLoss,
      // SMC 配置保存
      smcExit: rc.smcExit,
      // 基于结构止盈止损保存
      structureStopProfit: rc.structureStopProfit,
      batchTakeProfitEnabled: rc.batchTakeProfitEnabled,
      batchTakeProfitCount: rc.batchTakeProfitCount,
      batchTakeProfitPlans: (rc.batchTakeProfitPlans || []).map((p: any) => ({
        profitPercent: p.profitPercent,
        positionPercent: p.positionPercent,
      })),
      batchStopLossEnabled: rc.batchStopLossEnabled,
      batchStopLossCount: rc.batchStopLossCount,
      batchStopLossPlans: (rc.batchStopLossPlans || []).map((p: any) => ({
        lossPercent: p.lossPercent,
        positionPercent: p.positionPercent,
      })),
      batchTrailingGainEnabled: rc.batchTrailingGainEnabled,
      batchTrailingGainCount: rc.batchTrailingGainCount,
      batchTrailingGainPlans: (rc.batchTrailingGainPlans || []).map((p: any) => ({
        trailPercent: p.trailPercent,
        positionPercent: p.positionPercent,
      })),
      batchTrailingLossEnabled: rc.batchTrailingLossEnabled,
      batchTrailingLossCount: rc.batchTrailingLossCount,
      batchTrailingLossPlans: (rc.batchTrailingLossPlans || []).map((p: any) => ({
        trailPercent: p.trailPercent,
        positionPercent: p.positionPercent,
      })),
    };
    if (!rc.takeProfitEnabled) {
      exitRules.takeProfit.type = undefined;
      exitRules.takeProfit.percent = undefined;
      exitRules.takeProfit.atrMultiplier = undefined;
      exitRules.takeProfit.atrPeriod = undefined;
      exitRules.takeProfit.technicalEnabled = false;
      exitRules.takeProfit.technicalMode = undefined;
      exitRules.takeProfit.technicalMustWin = false;
      exitRules.takeProfit.technical = {
        enabled: false,
        mode: undefined,
        mustWin: false,
      };
    } else if (rc.takeProfitType === "fixed_percent") {
      exitRules.takeProfit.atrMultiplier = undefined;
      exitRules.takeProfit.atrPeriod = undefined;
    } else {
      exitRules.takeProfit.percent = undefined;
    }
    // 仓位风险管理
    const positionRisk: any = {
      maxPosition: strategyForm.maxPosition,
      maxPositionPerTrade: rc.maxPositionPerTrade,
      minPosition: rc.minPosition,
      positionManagementEnabled: rc.positionManagementEnabled,
      positionMode: rc.positionMode,
      basePositionPct: rc.basePositionPct,
      singleTradeRiskPct: rc.singleTradeRiskPct,
      allowAddPosition: !!rc.allowAddPosition,
      addPosOnProfitPct: rc.addPosOnProfitPct,
      addPosOnLossPct: rc.addPosOnLossPct,
      addPosOnProfitGapPct: rc.addPosOnProfitGapPct,
      addPosOnLossGapPct: rc.addPosOnLossGapPct,
      signalFrequencyEnabled: !!rc.signalFrequencyEnabled,
      signalFrequencyGranularity: rc.signalFrequencyGranularity || '15min',
      signalFrequencyMode: rc.signalFrequencyMode || 'structure_upgrade_exempt',
    };
    // 构建更新数据：分别传 positionRisk、exitRules，后端存为两组参数
    const updateData: any = {
      name: strategyForm.name,
      strategyId: strategyForm.strategyId,
      strategyType: strategyForm.type,
      description: strategyForm.description,
      className: strategyForm.className,
      timeFrame: strategyForm.timeframe,
      codeContent: strategyForm.code,
      positionRisk: JSON.stringify(positionRisk),
      exitRules: JSON.stringify(exitRules),
      // 动态风控引擎（移动止损/移动止盈）独立参数组
      dynamicRiskEngine: JSON.stringify(rc.dynamicRiskEngine),
      autoSignal: JSON.stringify(strategyForm.autoSignal),
      entryRules: JSON.stringify({
        long: { disabled: entryRules.long.disabled, conditions: entryRules.long.conditions },
        short: { disabled: entryRules.short.disabled, conditions: entryRules.short.conditions },
      }),
    };

    const response = await strategyApi.updateStrategy(
      strategyForm.id,
      updateData,
    );

    if (response.success !== false) {
      ElMessage.success("策略保存成功");
      emit("saved");
    } else {
      throw new Error(response.message || "保存失败");
    }
  } catch (error) {
    console.error("保存策略失败", error);
    ElMessage.error("保存失败: " + (error.message || "未知错误"));
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadStrategy();
});
</script>

<style scoped>
.edit-strategy {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--primary-text);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.strategy-content {
  max-width: 1200px;
  margin: 0 auto;
}

.form-section {
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}


.code-editor {
  margin-top: 16px;
}

.code-editor :deep(.el-textarea__inner) {
  font-family: "Monaco", "Menlo", "Ubuntu Mono", monospace;
  font-size: 14px;
  line-height: 1.5;
  background: #1e1e1e;
  color: #d4d4d4;
  border: 1px solid #3e3e3e;
}

.code-editor :deep(.el-textarea__inner)::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.code-editor :deep(.el-textarea__inner)::-webkit-scrollbar-track {
  background: #1e1e1e;
}

.code-editor :deep(.el-textarea__inner)::-webkit-scrollbar-thumb {
  background: #4e4e4e;
  border-radius: 4px;
}

.code-editor :deep(.el-textarea__inner)::-webkit-scrollbar-thumb:hover {
  background: #6e6e6e;
}

.form-hint {
  color: #909399;
  font-size: 12px;
}

.mode-card {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px 16px;
}

.mode-card-header {
  margin-bottom: 12px;
}
</style>
