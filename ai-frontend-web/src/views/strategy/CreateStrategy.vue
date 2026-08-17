<template>
  <div class="create-strategy">
    <div class="page-header">
      <h2>{{ isFromTemplate ? "从模板创建策略" : "创建策略" }}</h2>
      <div class="header-actions">
        <el-button @click="handleBack">返回列表</el-button>
        <el-button
          @click="createStrategy"
          :icon="Document"
          size="large"
          type="primary"
          :loading="saving"
        >
          创建策略
        </el-button>
      </div>
    </div>

    <div class="strategy-content">
      <el-form :model="strategyForm" label-width="120px">
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

        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><Setting /></el-icon>
              <span>策略参数</span>
            </div>
          </template>

          <div class="parameters-list">
            <div
              v-for="(param, index) in strategyForm.parameters"
              :key="index"
              class="parameter-item"
            >
              <el-row :gutter="8">
                <el-col :span="5">
                  <el-form-item label="参数名">
                    <el-input v-model="param.name" placeholder="参数名" />
                  </el-form-item>
                </el-col>
                <el-col :span="4">
                  <el-form-item label="类型">
                    <el-select
                      v-model="param.type"
                      placeholder="类型"
                      style="width: 100%"
                    >
                      <el-option label="数字" value="number" />
                      <el-option label="字符串" value="string" />
                      <el-option label="布尔" value="boolean" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="4">
                  <el-form-item label="默认值">
                    <el-input
                      v-model="param.defaultValue"
                      placeholder="默认值"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="5">
                  <el-form-item label="最小值">
                    <el-input v-model="param.min" placeholder="最小值" />
                  </el-form-item>
                </el-col>
                <el-col :span="5">
                  <el-form-item label="最大值">
                    <el-input v-model="param.max" placeholder="最大值" />
                  </el-form-item>
                </el-col>
                <el-col :span="1">
                  <el-form-item label=" ">
                    <el-button
                      @click="removeParameter(index)"
                      type="danger"
                      circle
                    >
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </el-form-item>
                </el-col>
              </el-row>
            </div>

            <el-button @click="addParameter" type="primary" plain>
              <el-icon><Plus /></el-icon>
              添加参数
            </el-button>
          </div>
        </el-card>

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

        <el-card class="form-section">
          <template #header>
            <div class="card-header">
              <el-icon><WarningFilled /></el-icon>
              <span>风控设置</span>
            </div>
          </template>

          <el-divider content-position="left">仓位控制</el-divider>

          <p style="margin: 0 0 16px 0; color: #909399; font-size: 13px;">
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
                止损距离由【基于结构止盈止损】的日常防线决定。
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

            <el-divider content-position="left">止损设置</el-divider>
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
                  />
                  %
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

            <el-divider content-position="left">时间止盈/止损</el-divider>
            <el-form-item label="时间止盈">
              <el-switch
                v-model="strategyForm.riskControl.timeBasedTakeProfit.enabled"
              />
              <template v-if="strategyForm.riskControl.timeBasedTakeProfit.enabled">
                <el-input-number
                  v-model="
                    strategyForm.riskControl.timeBasedTakeProfit.percent
                  "
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
                  <el-tag
                    v-if="batchTakeProfitTotal !== 100"
                    type="warning"
                    size="small"
                    style="margin-left: 8px"
                    >未达 100%</el-tag
                  >
                </div>
              </el-form-item>
            </template>
          </template>
        </el-card>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from "vue";

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

interface Props {
  template?: any;
  isFromTemplate?: boolean;
  onBackToList?: () => void;
  onCreateSuccess?: () => void;
}

const props = withDefaults(defineProps<Props>(), {
  template: undefined,
  isFromTemplate: false,
});

const emit = defineEmits<{
  (e: "back-to-list"): void;
  (e: "create-success"): void;
}>();


const saving = ref(false);
const stopLossFixedType = ref<"fixed_percent" | "atr_based" | "">("");

function onStopLossFixedTypeChange(val: "" | "fixed_percent" | "atr_based") {
  strategyForm.riskControl.stopLoss.fixed_percent.enabled =
    val === "fixed_percent";
  strategyForm.riskControl.stopLoss.atr_based.enabled = val === "atr_based";
}

const strategyForm = reactive({
  strategyId: "",
  name: "",
  type: "",
  description: "",
  className: "",
  timeframe: "",
  parameters: [
    { name: "", type: "number", defaultValue: "", min: "", max: "" },
  ],
  code: "",
  maxPosition: 100,
  stopLoss: 5,
  takeProfit: 10,
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
    batchTakeProfitEnabled: false,
    batchTakeProfitCount: 2,
    batchTakeProfitPlans: [
      { profitPercent: 5, positionPercent: 50 },
      { profitPercent: 10, positionPercent: 50 },
    ],
    // 基于结构止盈止损（SMC多层出场体系）
    structureStopProfit: {
      enabled: false,
      mode: 'auto',
      dynamicStopLoss: {
        dailyPeriod: '15',
        dailyBuffer: 0.08,
        bufferPeriod: '60',
        bufferBuffer: 0.12,
        ultimatePeriod: '240',
        ultimateBuffer: 0.25,
        autoEnableUltimate: true,
      },
      takeProfitActive: {
        ob1hClosePct: 50,
        swingClosePct: 50,
        fvgClosePct: null,
        minRR: 1.2,
        maxRR: 4.0,
      },
      trailingProtection: {
        trailingEnabled: true,
        trailingBuffer: 0.08,
        breakevenEnabled: true,
        breakevenBuffer: 0.05,
      },
      reference: {
        stopLossPeriod: '15',
        takeProfitPeriod: '60',
      },
    },
  },
});

const addParameter = () => {
  strategyForm.parameters.push({
    name: "",
    type: "number",
    defaultValue: "",
    min: "",
    max: "",
  });
};

const removeParameter = (index: number) => {
  strategyForm.parameters.splice(index, 1);
};

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

const batchTakeProfitTotal = computed(() => {
  const plans = strategyForm.riskControl.batchTakeProfitPlans || [];
  return plans.reduce((sum, p) => sum + (Number(p.positionPercent) || 0), 0);
});

const populateFormFromTemplate = (template: any) => {
  if (template.name) strategyForm.name = template.name;
  if (template.description) strategyForm.description = template.description;
  if (template.category) strategyForm.type = template.category;
  if (template.className || template.class_name)
    strategyForm.className = template.className || template.class_name;
  if (template.strategyId) strategyForm.strategyId = template.strategyId;
  if (template.language)
    strategyForm.code =
      template.code ||
      `// ${template.name} 策略代码\n// 语言: ${template.language}`;

  const timeframe =
    template.config?.timeframe ||
    template.parameters?.timeframe ||
    template.timeFrame ||
    template.timeframe;
  if (timeframe) strategyForm.timeframe = timeframe;

  if (template.defaultParameters) {
    try {
      const params =
        typeof template.defaultParameters === "string"
          ? JSON.parse(template.defaultParameters)
          : template.defaultParameters;
      if (Array.isArray(params)) {
        strategyForm.parameters =
          params.length > 0
            ? params
            : [
                {
                  name: "",
                  type: "number",
                  defaultValue: "",
                  min: "",
                  max: "",
                },
              ];
      }
    } catch (e) {
      console.warn("解析参数失败", e);
    }
  }

  if (template.riskControl) {
    try {
      const riskControl =
        typeof template.riskControl === "string"
          ? JSON.parse(template.riskControl)
          : template.riskControl;
      if (riskControl.maxPosition !== undefined)
        strategyForm.maxPosition = riskControl.maxPosition;
      if (riskControl.maxPositionPerTrade !== undefined)
        strategyForm.riskControl.maxPositionPerTrade =
          riskControl.maxPositionPerTrade;
      if (riskControl.minPosition !== undefined)
        strategyForm.riskControl.minPosition = riskControl.minPosition;
      if (riskControl.positionManagementEnabled !== undefined)
        strategyForm.riskControl.positionManagementEnabled =
          riskControl.positionManagementEnabled;
      if (riskControl.positionMode !== undefined)
        strategyForm.riskControl.positionMode = riskControl.positionMode;
      if (riskControl.basePositionPct !== undefined)
        strategyForm.riskControl.basePositionPct = riskControl.basePositionPct;
      if (riskControl.singleTradeRiskPct !== undefined)
        strategyForm.riskControl.singleTradeRiskPct = riskControl.singleTradeRiskPct;
      if (riskControl.allowAddPosition !== undefined)
        strategyForm.riskControl.allowAddPosition =
          riskControl.allowAddPosition;
      if (riskControl.addPosOnProfitPct !== undefined)
        strategyForm.riskControl.addPosOnProfitPct =
          riskControl.addPosOnProfitPct;
      if (riskControl.addPosOnLossPct !== undefined)
        strategyForm.riskControl.addPosOnLossPct =
          riskControl.addPosOnLossPct;
      if (riskControl.addPosOnProfitGapPct !== undefined)
        strategyForm.riskControl.addPosOnProfitGapPct =
          riskControl.addPosOnProfitGapPct;
      if (riskControl.addPosOnLossGapPct !== undefined)
        strategyForm.riskControl.addPosOnLossGapPct =
          riskControl.addPosOnLossGapPct;
      if (riskControl.signalFrequencyEnabled !== undefined)
        strategyForm.riskControl.signalFrequencyEnabled =
          !!riskControl.signalFrequencyEnabled;
      if (riskControl.signalFrequencyGranularity !== undefined)
        strategyForm.riskControl.signalFrequencyGranularity =
          riskControl.signalFrequencyGranularity;
      if (riskControl.signalFrequencyMode !== undefined)
        strategyForm.riskControl.signalFrequencyMode =
          riskControl.signalFrequencyMode;
      if (riskControl.stopLoss) {
        const sl = riskControl.stopLoss;
        if (sl.fixed_percent)
          strategyForm.riskControl.stopLoss.fixed_percent = {
            ...strategyForm.riskControl.stopLoss.fixed_percent,
            ...sl.fixed_percent,
          };
        if (sl.fixed_percent_trailing)
          strategyForm.riskControl.stopLoss.fixed_percent_trailing = {
            ...strategyForm.riskControl.stopLoss.fixed_percent_trailing,
            ...sl.fixed_percent_trailing,
          };
        if (sl.atr_based)
          strategyForm.riskControl.stopLoss.atr_based = {
            ...strategyForm.riskControl.stopLoss.atr_based,
            ...sl.atr_based,
          };
        if (sl.atr_trailing)
          strategyForm.riskControl.stopLoss.atr_trailing = {
            ...strategyForm.riskControl.stopLoss.atr_trailing,
            ...sl.atr_trailing,
          };
      }
      if (riskControl.takeProfitEnabled !== undefined)
        strategyForm.riskControl.takeProfitEnabled =
          riskControl.takeProfitEnabled;
      if (riskControl.takeProfitType)
        strategyForm.riskControl.takeProfitType = riskControl.takeProfitType;
      if (riskControl.takeProfitPercent !== undefined)
        strategyForm.riskControl.takeProfitPercent =
          riskControl.takeProfitPercent;
      if (riskControl.takeProfitAtrMultiplier !== undefined)
        strategyForm.riskControl.takeProfitAtrMultiplier =
          riskControl.takeProfitAtrMultiplier;
      if (riskControl.takeProfitAtrPeriod !== undefined)
        strategyForm.riskControl.takeProfitAtrPeriod =
          riskControl.takeProfitAtrPeriod;
      if (riskControl.batchTakeProfitEnabled !== undefined)
        strategyForm.riskControl.batchTakeProfitEnabled =
          riskControl.batchTakeProfitEnabled;
      if (riskControl.batchTakeProfitCount !== undefined)
        strategyForm.riskControl.batchTakeProfitCount =
          riskControl.batchTakeProfitCount;
      if (Array.isArray(riskControl.batchTakeProfitPlans))
        strategyForm.riskControl.batchTakeProfitPlans =
          riskControl.batchTakeProfitPlans;
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
    } catch (e) {
      console.warn("解析风控配置失败", e);
    }
  }
};

watch(
  () => props.template,
  (newTemplate) => {
    if (newTemplate) {
      populateFormFromTemplate(newTemplate);
    }
  },
  { immediate: true },
);

const createStrategy = async () => {
  if (!strategyForm.name) {
    ElMessage.error("请输入策略名称");
    return;
  }

  saving.value = true;
  try {
    const rc = strategyForm.riskControl;
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
      },
      timeBasedTakeProfit: rc.timeBasedTakeProfit,
      timeBasedStopLoss: rc.timeBasedStopLoss,
      batchTakeProfitEnabled: rc.batchTakeProfitEnabled,
      batchTakeProfitCount: rc.batchTakeProfitCount,
      batchTakeProfitPlans: (rc.batchTakeProfitPlans || []).map((p: any) => ({
        profitPercent: p.profitPercent,
        positionPercent: p.positionPercent,
      })),
    };
    if (!rc.takeProfitEnabled) {
      exitRules.takeProfit.percent = undefined;
      exitRules.takeProfit.atrMultiplier = undefined;
      exitRules.takeProfit.atrPeriod = undefined;
    } else if (rc.takeProfitType === "fixed_percent") {
      exitRules.takeProfit.atrMultiplier = undefined;
      exitRules.takeProfit.atrPeriod = undefined;
    } else {
      exitRules.takeProfit.percent = undefined;
    }
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
    const riskControlMerged = { ...positionRisk, ...exitRules };
    const createData: any = {
      name: strategyForm.name,
      strategyId: strategyForm.strategyId,
      strategyType: strategyForm.type,
      description: strategyForm.description,
      className: strategyForm.className,
      timeFrame: strategyForm.timeframe,
      codeContent: strategyForm.code,
      defaultParameters: JSON.stringify(strategyForm.parameters),
      riskControl: JSON.stringify(riskControlMerged),
      positionRisk: JSON.stringify(positionRisk),
      exitRules: JSON.stringify(exitRules),
    };

    const response = await strategyApi.createStrategy(createData);

    if (response.success !== false) {
      ElMessage.success("策略创建成功");
      emit("create-success");
      handleBack();
    } else {
      throw new Error(response.message || "创建失败");
    }
  } catch (error: any) {
    console.error("创建策略失败", error);
    ElMessage.error("创建失败: " + (error.message || "未知错误"));
  } finally {
    saving.value = false;
  }
};

const handleBack = () => {
  if (props.onBackToList) {
    props.onBackToList();
  } else {
    emit("back-to-list");
  }
};
</script>

<style scoped>
.create-strategy {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
  background: var(--bg-secondary);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px 20px;
  background: var(--surface-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
}

.page-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-2xl);
  font-weight: var(--font-semibold);
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

.parameters-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.parameter-item {
  margin-bottom: 16px;
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
}

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

:deep(.el-card) {
  background: var(--surface-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

:deep(.el-card__body) {
  padding: 24px;
}

:deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-weight: var(--font-medium);
  font-size: var(--font-sm);
}

:deep(.el-input__inner) {
  background: var(--input-bg);
  border: none;
  color: var(--input-text);
  border-radius: var(--radius-md);
  font-size: var(--font-base);
}

:deep(.el-input__inner:focus) {
  box-shadow: 0 0 0 2px var(--glow-primary);
}

:deep(.el-input__inner::placeholder) {
  color: var(--input-placeholder);
}

:deep(.el-textarea__inner) {
  background: var(--input-bg);
  border: none;
  color: var(--input-text);
  border-radius: var(--radius-md);
  font-size: var(--font-base);
}

:deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px var(--glow-primary);
}

:deep(.el-select) {
  width: 100%;
}

:deep(.el-select .el-input__inner) {
  cursor: pointer;
}

:deep(.el-input-number) {
  width: 100%;
}

:deep(.el-input-number .el-input__inner) {
  text-align: left;
}

.code-editor {
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--bg-primary);
  width: 100%;
  max-width: 100%;
  min-height: 400px;
  margin-bottom: 20px;
}

.code-editor :deep(.el-textarea__inner) {
  border: none;
  background: transparent;
  font-family: "Monaco", "Menlo", "Ubuntu Mono", "Consolas", monospace;
  font-size: 13px;
  line-height: 1.5;
  resize: none;
  color: var(--text-primary);
  width: 100% !important;
  min-height: 380px !important;
  padding: 16px !important;
}

.code-editor :deep(.el-textarea__inner):focus {
  box-shadow: none;
}

/* 按钮样式 */
:deep(.el-button) {
  border-radius: var(--radius-md);
  font-weight: var(--font-medium);
  transition: all var(--transition-normal) var(--ease-out);
}

:deep(.el-button--primary) {
  background: var(--btn-primary);
  border-color: var(--btn-primary);
  color: white;
}

:deep(.el-button--primary:hover) {
  background: var(--btn-primary-hover);
  border-color: var(--btn-primary-hover);
  box-shadow: var(--glow-primary);
}

:deep(.el-button--default) {
  background: var(--btn-secondary);
  border-color: var(--border-primary);
  color: var(--text-secondary);
}

:deep(.el-button--default:hover) {
  background: var(--bg-hover);
  border-color: var(--border-secondary);
  color: var(--text-primary);
}

:deep(.el-button--danger) {
  background: var(--market-down);
  border-color: var(--market-down);
  color: white;
}

:deep(.el-button--danger:hover) {
  background: #e02e24;
  border-color: #e02e24;
  box-shadow: var(--glow-danger);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .create-strategy {
    padding: 12px;
  }

  .page-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
    padding: 12px 16px;
  }

  .page-header h2 {
    font-size: var(--font-xl);
  }

  :deep(.el-card__body) {
    padding: 16px;
  }

  :deep(.el-form-item__label) {
    float: none;
    display: block;
    text-align: left;
    margin-bottom: 8px;
  }

  :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }
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
