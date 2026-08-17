<template>
  <div class="weight-rule-engine">
    <div class="page-header">
      <h2>权重规则引擎</h2>
      <div class="header-actions">
        <el-select v-model="selectedConfigId" placeholder="选择信号配置" style="width:280px" @change="onConfigChange" clearable>
          <el-option v-for="cfg in configs" :key="cfg.id!" :label="cfg.name" :value="cfg.id!" />
        </el-select>
        <el-button :disabled="!selectedConfigId" @click="loadVersions" :loading="restoring">
          版本历史
        </el-button>
        <el-button type="primary" :disabled="!selectedConfigId" @click="saveAllRules" :loading="saving">
          保存全部
        </el-button>
      </div>
    </div>

    <template v-if="selectedConfigId">
      <el-alert :type="engineEnabled ? 'success' : 'info'" :closable="false" show-icon class="engine-status-bar">
        <template #title>
          权重规则引擎 <strong>{{ engineEnabled ? '已启用' : '已关闭' }}</strong>
          <el-switch v-model="engineEnabled" style="margin-left:12px" @change="markDirty" />
        </template>
      </el-alert>

      <div style="margin:12px 0;display:flex;justify-content:flex-end;gap:8px">
        <el-button size="small" @click="scoringDialogVisible = true">
          评分设置
        </el-button>
      </div>

      <el-row :gutter="16" class="main-content">
        <el-col :span="7">
          <el-card class="rule-list-card">
            <div class="panel-header">
              <span class="panel-title">规则列表</span>
              <el-button size="small" type="primary" :disabled="!engineEnabled" @click="addRule" plain>
                + 添加规则
              </el-button>
            </div>
            <div class="rule-items">
              <div
                v-for="(rule, ri) in rules"
                :key="ri"
                class="rule-item"
                :class="{ active: selectedRuleIndex === ri, disabled: !rule.enabled }"
                @click="selectedRuleIndex = ri"
              >
                <div class="ri-header">
                  <span class="ri-name">{{ rule.name || `规则 #${ri + 1}` }}</span>
                  <el-tag :type="rule.type === 'VETO' ? 'danger' : 'primary'" size="small">
                    {{ rule.type }}
                  </el-tag>
                </div>
                <div class="ri-meta">
                  <span>{{ rule.conditions?.length || 0 }} 条件</span>
                  <span v-if="rule.type === 'SCORING'">评分 {{ rule.score }}</span>
                  <span v-else>否决权 {{ rule.vetoWeight }}</span>
                </div>
                <div class="ri-actions">
                  <el-switch v-model="rule.enabled" size="small" @change="markDirty" />
                  <el-button size="small" text type="danger" @click.stop="deleteRule(ri)">删除</el-button>
                </div>
              </div>
              <el-empty v-if="rules.length === 0" description="暂无规则，点击上方添加" :image-size="60" />
            </div>
          </el-card>
        </el-col>

        <el-col :span="17">
          <el-card v-if="selectedRule" class="editor-card">
            <div class="panel-header">
              <span class="panel-title">规则编辑器</span>
            </div>
            <el-form :model="selectedRule" label-width="90px" size="small">
              <el-row :gutter="12">
                <el-col :span="8">
                  <el-form-item label="规则名称">
                    <el-input v-model="selectedRule.name" placeholder="如：EMA趋势过滤" @input="markDirty" />
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="规则类型">
                    <el-select v-model="selectedRule.type" style="width:100%" @change="markDirty">
                      <el-option label="评分 (SCORING)" value="SCORING" />
                      <el-option label="否决 (VETO)" value="VETO" />
                    </el-select>
                    <div style="color:#909399;font-size:12px;line-height:1.4;margin-top:4px;">否决代表满足条件就否决信号</div>
                  </el-form-item>
                </el-col>
                <el-col :span="5" v-if="selectedRule.type === 'SCORING'">
                  <el-form-item label="评分值">
                    <el-input-number v-model="selectedRule.score" :min="-10" :max="10" :step="0.5" controls-position="right" style="width:100%" @change="markDirty" />
                  </el-form-item>
                </el-col>
                <el-col :span="5" v-if="selectedRule.type === 'VETO'">
                  <el-form-item label="否决权重">
                    <el-input-number v-model="selectedRule.vetoWeight" :min="0" :max="1" :step="0.1" controls-position="right" style="width:100%" @change="markDirty" />
                  </el-form-item>
                </el-col>
                <el-col :span="5">
                  <el-form-item label="条件匹配">
                    <el-select v-model="selectedRule.conditionOperator" style="width:100%" @change="markDirty">
                      <el-option label="全部满足 (AND)" value="AND" />
                      <el-option label="任一满足 (OR)" value="OR" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>

              <el-divider content-position="left">条件列表</el-divider>
              <div v-for="(cond, ci) in selectedRule.conditions" :key="ci" class="condition-row">
                <el-row :gutter="8" align="middle">
                  <el-col :span="5">
                    <el-select v-model="cond.indicator" placeholder="指标" style="width:100%" filterable @change="onCondChange(ci)">
                      <el-option v-for="opt in indicatorOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                  </el-col>
                  <el-col :span="2">
                    <el-select v-model="cond.direction" placeholder="方向" style="width:100%" @change="markDirty">
                      <el-option label="双向" value="BOTH" />
                      <el-option label="多头" value="LONG" />
                      <el-option label="空头" value="SHORT" />
                    </el-select>
                  </el-col>
                  <template v-if="cond.indicator === 'SMC_MARKET_TREND'">
                    <el-col :span="8">
                      <el-select v-model="cond.params.categoryValue" placeholder="复合趋势状态" style="width:100%" @change="markDirty">
                        <el-option v-for="o in compositeStateOptions" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-col>
                    <el-col :span="3">
                      <el-select v-model="cond.operator" style="width:100%" @change="markDirty">
                        <el-option label="=" value="EQ" />
                        <el-option label="≠" value="NEQ" />
                      </el-select>
                    </el-col>
                    <el-col :span="1">
                      <el-button size="small" type="danger" text @click="removeCondition(ci)">删除</el-button>
                    </el-col>
                  </template>
                  <template v-else-if="cond.indicator === 'SMC_ALIGNMENT'">
                    <el-col :span="3">
                      <el-select v-model="cond.operator" style="width:100%" @change="markDirty">
                        <el-option label="=" value="EQ" />
                        <el-option label="≠" value="NEQ" />
                      </el-select>
                    </el-col>
                    <el-col :span="8">
                      <el-select v-model="cond.params.categoryValue" placeholder="信号共振" style="width:100%" @change="markDirty">
                        <el-option v-for="o in alignmentOptions" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-col>
                    <el-col :span="1">
                      <el-button size="small" type="danger" text @click="removeCondition(ci)">删除</el-button>
                    </el-col>
                  </template>
                  <template v-else-if="cond.indicator === 'PATTERN_TYPE'">
                    <el-col :span="5">
                      <el-select
                        v-if="cond.operator === 'IN' || cond.operator === 'NOT_IN'"
                        v-model="condParamsArray[ci]"
                        multiple
                        placeholder="选择K线形态"
                        style="width:100%"
                        @change="onPatternMultiChange(ci)"
                      >
                        <el-option v-for="o in patternOptions" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                      <el-select
                        v-else
                        v-model="cond.params.categoryValue"
                        placeholder="选择K线形态"
                        style="width:100%"
                        @change="markDirty"
                      >
                        <el-option v-for="o in patternOptions" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-col>
                    <el-col :span="3">
                      <el-select v-model="cond.operator" style="width:100%" @change="onPatternOperatorChange(ci)">
                        <el-option label="=" value="EQ" />
                        <el-option label="≠" value="NEQ" />
                        <el-option label="包含" value="IN" />
                        <el-option label="不包含" value="NOT_IN" />
                      </el-select>
                    </el-col>
                    <el-col :span="3">
                      <el-select v-model="cond.params.timeframe" placeholder="K线周期" clearable style="width:100%" @change="markDirty">
                        <el-option label="本周期" value="" />
                        <el-option label="15分钟" value="15m" />
                        <el-option label="1小时" value="1H" />
                        <el-option label="4小时" value="4H" />
                      </el-select>
                    </el-col>
                    <el-col :span="1">
                      <el-button size="small" type="danger" text @click="removeCondition(ci)">删除</el-button>
                    </el-col>
                  </template>
                  <template v-else-if="cond.indicator === 'MACD'">
                    <el-col :span="3">
                      <el-select v-model="cond.params.categoryValue" placeholder="选择内容" style="width:100%" @change="onMacdCategoryChange(ci)">
                        <el-option label="DIF(快线)" value="MACD_LINE" />
                        <el-option label="DEA(慢线)" value="MACD_SIGNAL" />
                        <el-option label="柱状图(BAR)" value="MACD_HISTOGRAM" />
                        <el-option label="金叉" value="GOLDEN_CROSS" />
                        <el-option label="死叉" value="DEATH_CROSS" />
                        <el-option label="底背离" value="BULLISH_DIVERGENCE" />
                        <el-option label="顶背离" value="BEARISH_DIVERGENCE" />
                        <el-option label="柱体动能" value="HISTOGRAM_MOMENTUM" />
                      </el-select>
                    </el-col>
                    <el-col :span="2">
                      <el-select v-model="cond.operator" style="width:100%" @change="markDirty">
                        <template v-if="cond.params?.categoryValue === 'HISTOGRAM_MOMENTUM'">
                          <el-option label="= (等于)" value="EQ" />
                          <el-option label="≠ (不等于)" value="NEQ" />
                          <el-option label="包含 (IN)" value="IN" />
                          <el-option label="不包含 (NOT_IN)" value="NOT_IN" />
                        </template>
                        <template v-else>
                          <el-option v-for="opt in operatorOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                        </template>
                      </el-select>
                    </el-col>
                    <el-col :span="3">
                      <template v-if="cond.params?.categoryValue === 'HISTOGRAM_MOMENTUM'">
                        <el-select v-model="cond.value" placeholder="选择动能状态" clearable style="width:100%" @change="markDirty">
                          <el-option label="BULLISH_EXPAND（零轴上放大）" value="BULLISH_EXPAND" />
                          <el-option label="BULLISH_SHRINK（零轴上缩小）" value="BULLISH_SHRINK" />
                          <el-option label="BEARISH_EXPAND（零轴下放大）" value="BEARISH_EXPAND" />
                          <el-option label="BEARISH_SHRINK（零轴下缩小）" value="BEARISH_SHRINK" />
                        </el-select>
                      </template>
                      <el-input v-else v-model="cond.value" type="number" placeholder="阈值" :min="-999999" :max="999999" :step="0.01" style="width:100%" @input="markDirty" />
                    </el-col>
                    <el-col :span="3">
                      <el-select v-model="cond.params.timeframe" placeholder="K线周期" clearable style="width:100%" @change="markDirty">
                        <el-option label="本周期" value="" />
                        <el-option label="15分钟" value="15m" />
                        <el-option label="1小时" value="1H" />
                        <el-option label="4小时" value="4H" />
                      </el-select>
                    </el-col>
                    <el-col :span="1">
                      <el-button size="small" type="danger" text @click="removeCondition(ci)">删除条件</el-button>
                    </el-col>
                  </template>
                  <template v-else>
                    <!-- 运算符 - 动态来自 metadata -->
                    <el-col :span="2">
                      <el-select v-model="cond.operator" placeholder="运算符" style="width:100%" @change="markDirty">
                        <el-option v-for="op in getOperatorsForIndicator(cond.indicator)" :key="op" :label="op" :value="op" />
                      </el-select>
                    </el-col>
                    <!-- 比较值 - 根据 valueType 动态切换控件 -->
                    <template v-if="getValueType(cond.indicator) === 'ENUM'">
                      <el-col :span="3">
                        <el-select v-model="cond.value" placeholder="选择值" filterable style="width:100%" @change="markDirty">
                          <el-option v-for="ev in getEnumValues(cond.indicator)" :key="ev" :label="ev" :value="ev" />
                        </el-select>
                      </el-col>
                    </template>
                    <template v-else-if="getValueType(cond.indicator) === 'BOOLEAN'">
                      <el-col :span="3">
                        <el-select v-model="cond.value" placeholder="选择值" style="width:100%" @change="markDirty">
                          <el-option label="true" value="true" />
                          <el-option label="false" value="false" />
                        </el-select>
                      </el-col>
                    </template>
                    <template v-else-if="getValueType(cond.indicator) === 'STATE'">
                      <el-col :span="3">
                        <el-select v-model="cond.value" placeholder="选择状态" allow-create filterable default-first-option style="width:100%" @change="markDirty">
                          <el-option v-for="ev in getEnumValues(cond.indicator)" :key="ev" :label="ev" :value="ev" />
                        </el-select>
                      </el-col>
                    </template>
                    <template v-else>
                      <!-- IN/NOT_IN 支持逗号分隔多值 -->
                      <el-col v-if="cond.operator === 'IN' || cond.operator === 'NOT_IN'" :span="3">
                        <el-input v-model="cond.value" placeholder="逗号分隔多值" style="width:100%" @input="markDirty" />
                      </el-col>
                      <el-col v-else :span="3">
                        <el-input-number v-model="cond.value" :min="-999999" :max="999999" :step="0.01" controls-position="right" style="width:100%" @change="markDirty" />
                      </el-col>
                    </template>
                    <!-- 参数 - 动态来自 metadata -->
                    <template v-for="param in getParamsForIndicator(cond.indicator)" :key="param.key">
                      <el-col :span="getParamSpan(param)">
                        <!-- K线周期参数：固定渲染为下拉选择，第一项"默认 (信号周期)"空值代表使用信号周期 -->
                        <template v-if="param.key === 'dataPeriod' || param.key === 'timeframe'">
                          <el-select v-model="cond.params[param.key]" placeholder="默认 (信号周期)" clearable style="width:100%" @change="markDirty">
                            <el-option label="默认 (信号周期)" value="" />
                            <el-option v-for="p in KLINE_PERIODS" :key="p" :label="p" :value="p" />
                          </el-select>
                        </template>
                        <template v-else-if="param.type === 'select' || param.options">
                          <el-select v-model="cond.params[param.key]" :placeholder="param.label" clearable style="width:100%" @change="markDirty">
                            <el-option v-for="opt in (param.options || [])" :key="opt" :label="opt" :value="opt" />
                          </el-select>
                        </template>
                        <template v-else-if="param.type === 'STRING'">
                          <el-input v-model="cond.params[param.key]" :placeholder="param.label" clearable size="small" style="width:100%" @change="markDirty" />
                        </template>
                        <template v-else-if="param.type === 'INTEGER' || param.type === 'NUMBER'">
                          <el-input v-model.number="cond.params[param.key]" :placeholder="param.label" type="number" clearable size="small" style="width:100%" @change="markDirty" />
                        </template>
                        <template v-else>
                          <el-input-number v-model="cond.params[param.key]" :min="param.min" :max="param.max" :step="param.step || 1" controls-position="right" style="width:100%" @change="markDirty" />
                        </template>
                      </el-col>
                    </template>
                    <el-col :span="1">
                      <el-button size="small" type="danger" text @click="removeCondition(ci)">删除</el-button>
                    </el-col>
                  </template>
                </el-row>
              </div>
              <el-button size="small" type="primary" text @click="addCondition" style="margin-top:8px">
                <el-icon><Plus /></el-icon> 添加条件
              </el-button>
            </el-form>
          </el-card>

          <el-card v-else class="editor-card">
            <el-empty description="请从左侧选择一条规则进行编辑" :image-size="60" />
          </el-card>

          <el-card class="test-card">
            <div class="panel-header">
              <span class="panel-title">规则测试</span>
            </div>
            <el-form :model="testForm" label-width="90px" size="small">
              <el-row :gutter="16">
                <el-col :span="6">
                  <el-form-item label="方向">
                    <el-select v-model="testForm.direction" style="width:100%">
                      <el-option label="BUY" value="BUY" />
                      <el-option label="SELL" value="SELL" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="交易对">
                    <el-input v-model="testForm.symbol" placeholder="如 BTC-USDT-SWAP" />
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="当前价格">
                    <el-input-number v-model="testForm.currentPrice" :min="0" :step="100" controls-position="right" style="width:100%" />
                  </el-form-item>
                </el-col>
                <el-col :span="6">
                  <el-form-item label="市场趋势">
                    <el-select v-model="testForm.marketTrend" placeholder="选填" clearable style="width:100%">
                      <el-option label="BULLISH" value="BULLISH" />
                      <el-option label="BEARISH" value="BEARISH" />
                      <el-option label="NEUTRAL" value="NEUTRAL" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="SMC上下文">
                <el-button size="small" text type="primary" @click="showSmcContext = !showSmcContext">
                  {{ showSmcContext ? '收起' : '展开' }} SMC 上下文
                </el-button>
              </el-form-item>
              <el-collapse-transition>
                <div v-show="showSmcContext">
                  <el-row :gutter="16">
                    <el-col :span="8" v-for="f in scoreFields" :key="f.key">
                      <el-form-item :label="f.label" size="small">
                        <el-input-number v-model="testForm.context[f.key]" :min="f.min" :max="f.max" :step="f.step || 0.1" :precision="1" size="small" controls-position="right" style="width:100%" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-row :gutter="16">
                    <el-col :span="8" v-for="f in stateFields" :key="f.key">
                      <el-form-item :label="f.label" size="small">
                        <el-select v-model="testForm.context[f.key]" clearable size="small" style="width:100%">
                          <el-option v-for="o in f.options" :key="o.value" :label="o.label" :value="o.value" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </div>
              </el-collapse-transition>
              <el-button type="primary" @click="runTest" :loading="testing" style="width:100%;margin-top:8px">
                {{ testing ? '测试中...' : '开始测试' }}
              </el-button>
            </el-form>

            <template v-if="testResult">
              <el-divider content-position="left">测试结果</el-divider>
              <el-alert
                :title="testResult.vetoed ? '信号被否决' : '信号通过'"
                :type="testResult.vetoed ? 'warning' : 'success'"
                :description="testResult.reason"
                show-icon
                :closable="false"
              />
              <el-descriptions :column="3" border size="small" style="margin-top:12px">
                <el-descriptions-item label="最终权重">
                  <span :class="weightClass(testResult.finalWeight)">{{ testResult.finalWeight.toFixed(1) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="总评分">{{ testResult.totalScore.toFixed(2) }}</el-descriptions-item>
                <el-descriptions-item label="规则数">{{ testResult.traces?.length || 0 }}</el-descriptions-item>
              </el-descriptions>

              <el-table :data="testResult.traces" border size="small" style="margin-top:12px" max-height="280" row-key="ruleName">
                <el-table-column label="规则" prop="ruleName" width="140" />
                <el-table-column label="类型" width="70">
                  <template #default="{ row }">
                    <el-tag :type="row.ruleType === 'VETO' ? 'danger' : 'primary'" size="small">{{ row.ruleType }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="匹配" width="60" align="center">
                  <template #default="{ row }">
                    <el-icon :color="row.matched ? '#67C23A' : '#F56C6C'" size="16">
                      <Check v-if="row.matched" /><Close v-else />
                    </el-icon>
                  </template>
                </el-table-column>
                <el-table-column label="贡献分" width="80" align="right">
                  <template #default="{ row }">{{ row.contribution.toFixed(2) }}</template>
                </el-table-column>
                <el-table-column label="原因" min-width="160" show-overflow-tooltip>
                  <template #default="{ row }">{{ row.reason }}</template>
                </el-table-column>
                <el-table-column label="条件" width="80">
                  <template #default="{ row }">
                    <el-button text size="small" @click="showConditionDetail(row)">查看</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <el-divider content-position="left">指标快照</el-divider>
              <div v-if="snapshotKeys.length > 0" class="snapshot-grid">
                <el-tag v-for="key in snapshotKeys" :key="key" size="small" class="snapshot-tag">
                  {{ key }}: {{ formatSnapshotValue(testResult.indicatorSnapshot[key]) }}
                </el-tag>
              </div>
              <el-empty v-else description="无指标快照数据" :image-size="50" />
            </template>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <el-empty v-else description="请先选择信号配置" :image-size="80" style="margin-top:60px" />
  </div>

  <el-dialog v-model="conditionDetailVisible" title="条件执行详情" width="650px" top="20vh">
    <el-table :data="currentConditionTraces" border size="small" max-height="400">
      <el-table-column label="指标" prop="indicator" width="140" />
      <el-table-column label="运算符" prop="operator" width="70" />
      <el-table-column label="方向" prop="direction" width="60" />
      <el-table-column label="预期值" prop="expectedValue" width="90" />
      <el-table-column label="实际值" width="90">
        <template #default="{ row }">{{ formatActualValue(row.actualValue) }}</template>
      </el-table-column>
      <el-table-column label="匹配" width="60" align="center">
        <template #default="{ row }">
          <el-icon :color="row.matched ? '#67C23A' : '#F56C6C'" size="16">
            <Check v-if="row.matched" /><Close v-else />
          </el-icon>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>

  <el-dialog v-model="versionsVisible" title="版本历史" width="700px" top="15vh">
    <el-table :data="versions" border size="small" max-height="400" empty-text="暂无版本记录">
      <el-table-column label="版本号" prop="version" width="80" align="center" />
      <el-table-column label="状态" prop="status" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="变更说明" prop="remark" min-width="160" show-overflow-tooltip />
      <el-table-column label="创建人" prop="createdBy" width="100" />
      <el-table-column label="创建时间" prop="createTime" width="160" />
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" type="primary" @click="restoreVersion(row.version)" :loading="restoring">
            恢复
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>

  <el-dialog v-model="scoringDialogVisible" title="评分设置" width="500px" top="20vh">
    <el-form label-width="150px" size="small">
      <el-form-item label="VETO规则贡献权重">
        <el-switch v-model="scoringConfig.vetoContributeScore" @change="markDirty" />
        <div style="color:#909399;font-size:12px;margin-left:8px;">VETO规则是否参与权重计算</div>
      </el-form-item>
      <el-form-item label="评分→权重映射模式">
        <el-select v-model="scoringConfig.mappingMode" style="width:100%" @change="markDirty">
          <el-option label="阶梯映射 (STEP)" value="STEP" />
          <el-option label="线性映射 (LINEAR)" value="LINEAR" />
        </el-select>
      </el-form-item>
      <template v-if="scoringConfig.mappingMode === 'LINEAR'">
        <el-form-item label="线性斜率 (slope)">
          <el-input-number v-model="scoringConfig.linearSlope" :min="0" :max="10" :step="0.1" controls-position="right" style="width:100%" @change="markDirty" />
        </el-form-item>
        <el-form-item label="最小权重 (minWeight)">
          <el-input-number v-model="scoringConfig.linearMinWeight" :min="0" :max="10" :step="0.1" controls-position="right" style="width:100%" @change="markDirty" />
        </el-form-item>
        <el-form-item label="最大权重 (maxWeight)">
          <el-input-number v-model="scoringConfig.linearMaxWeight" :min="0" :max="10" :step="0.1" controls-position="right" style="width:100%" @change="markDirty" />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="scoringDialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, inject, watch, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Check, Close } from '@element-plus/icons-vue'
import {
  getSignalServiceConfigs,
  updateSignalServiceConfig,
  testRuleEngine,
  getIndicatorMetadata,
  getWeightRuleVersions,
  restoreWeightRuleVersion,
  updateWeightRules,
  type SignalServiceConfig,
  type WeightRuleConfig,
  type WeightRule,
  type RuleCondition,
  type RuleEvaluationResult,
  type RuleEvaluationTrace,
  type ConditionTrace,
  type IndicatorMetadata,
  type IndicatorParamDef,
  type WeightScoringConfig,
  type WeightRuleVersion,
} from '@/api/priceSignal'

// K线周期选项（用于 dataPeriod/timeframe 参数下拉）
const KLINE_PERIODS = ['1m', '5m', '15m', '30m', '1h', '4h', '1d']

const configs = ref<SignalServiceConfig[]>([])
const selectedConfigId = ref<number | null>(null)

const weightRuleEngineConfigId = inject<Ref<number | null>>('weightRuleEngineConfigId', ref(null))
watch(weightRuleEngineConfigId, (val) => {
  if (val !== null && val !== undefined) {
    selectedConfigId.value = val
  }
}, { immediate: true })

const rules = ref<WeightRule[]>([])
const engineEnabled = ref(false)
const selectedRuleIndex = ref<number | null>(null)
const saving = ref(false)
const dirty = ref(false)

const scoringConfig = ref<WeightScoringConfig>({
  vetoContributeScore: true,
  mappingMode: 'STEP',
  linearSlope: 0.6,
  linearMinWeight: 0.0,
  linearMaxWeight: 2.0,
})
const scoringDialogVisible = ref(false)

watch(selectedRuleIndex, () => {
  syncCondParamsArray()
})

const testing = ref(false)
const testResult = ref<RuleEvaluationResult | null>(null)
const showSmcContext = ref(false)
const conditionDetailVisible = ref(false)
const currentConditionTraces = ref<ConditionTrace[]>([])

const indicatorMetadataList = ref<IndicatorMetadata[]>([])
const versions = ref<WeightRuleVersion[]>([])
const versionsVisible = ref(false)
const restoring = ref(false)

const testForm = reactive({
  direction: 'BUY',
  symbol: '',
  currentPrice: 0,
  marketTrend: '',
  context: {} as Record<string, any>,
})

const selectedRule = computed(() => {
  if (selectedRuleIndex.value === null || selectedRuleIndex.value >= rules.value.length) return null
  return rules.value[selectedRuleIndex.value]
})

const snapshotKeys = computed(() => {
  if (!testResult.value?.indicatorSnapshot) return []
  return Object.keys(testResult.value.indicatorSnapshot)
})

const indicatorOptions = computed(() => {
  return indicatorMetadataList.value.map(m => ({
    label: `${m.name} (${m.id})`,
    value: m.id,
    category: m.category,
  }))
})

function getMetadataForIndicator(indicatorId: string): IndicatorMetadata | undefined {
  return indicatorMetadataList.value?.find(m => m.id === indicatorId)
}

function getValueType(indicatorId: string): string {
  return getMetadataForIndicator(indicatorId)?.valueType || 'NUMERIC'
}

function getOperatorsForIndicator(indicatorId: string): string[] {
  return getMetadataForIndicator(indicatorId)?.operators || ['GT', 'GTE', 'LT', 'LTE', 'EQ', 'NEQ']
}

function getEnumValues(indicatorId: string): string[] {
  return getMetadataForIndicator(indicatorId)?.enumValues || []
}

function getParamsForIndicator(indicatorId: string): IndicatorParamDef[] {
  return getMetadataForIndicator(indicatorId)?.params || []
}

function getParamSpan(param: IndicatorParamDef): number {
  return param.type === 'select' || param.options ? 3 : 2
}

function formatActualValue(val: string | number | null): string {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'string') return val
  return val.toFixed(4)
}

function formatSnapshotValue(val: any): string {
  if (val === null || val === undefined) return '-'
  if (typeof val === 'number') return val.toFixed(4)
  return String(val)
}

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
  { label: '强阳吞没', value: 'BULLISH_ACCUMULATION' },
  { label: '强阴吞没', value: 'BEARISH_ACCUMULATION' },
]

const operatorOptions = [
  { label: '> (大于)', value: 'GT' },
  { label: '>= (大于等于)', value: 'GTE' },
  { label: '< (小于)', value: 'LT' },
  { label: '<= (小于等于)', value: 'LTE' },
  { label: '= (等于)', value: 'EQ' },
  { label: '≠ (不等于)', value: 'NEQ' },
  { label: '上穿 (CROSS_ABOVE)', value: 'CROSS_ABOVE' },
  { label: '下穿 (CROSS_BELOW)', value: 'CROSS_BELOW' },
  { label: '包含 (IN)', value: 'IN' },
  { label: '不包含 (NOT_IN)', value: 'NOT_IN' },
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

const alignmentOptions = [
  { label: '顺势做多', value: '顺势做多' },
  { label: '顺势做空', value: '顺势做空' },
  { label: '逆势', value: '逆势' },
  { label: '方向分歧', value: '方向分歧' },
]

const compositeStateOptions = [
  { label: '强上升·健康', value: 'STRONG_BULLISH_HEALTHY' },
  { label: '强上升·浅回调', value: 'STRONG_BULLISH_SHALLOW_PULLBACK' },
  { label: '强上升·预警回调（1H）', value: 'STRONG_BULLISH_WARNING_1H' },
  { label: '强上升·预警回调（4H内部）', value: 'STRONG_BULLISH_WARNING_4H' },
  { label: '强上升·确认回调', value: 'STRONG_BULLISH_CONFIRMED_PULLBACK' },
  { label: '上升回调·进行中', value: 'BULLISH_PULLBACK_ONGOING' },
  { label: '上升回调·筑底', value: 'BULLISH_PULLBACK_BOTTOMING' },
  { label: '上升回调·失败', value: 'BULLISH_PULLBACK_FAILURE' },
  { label: '上升末端·延续下跌', value: 'BULLISH_ENDING_CONTINUE_DOWN' },
  { label: '上升末端·转势确认', value: 'BULLISH_ENDING_CONFIRM' },
  { label: '强下降·健康', value: 'STRONG_BEARISH_HEALTHY' },
  { label: '强下降·浅反弹', value: 'STRONG_BEARISH_SHALLOW_BOUNCE' },
  { label: '强下降·预警反弹（1H）', value: 'STRONG_BEARISH_WARNING_1H' },
  { label: '强下降·预警反弹（4H内部）', value: 'STRONG_BEARISH_WARNING_4H' },
  { label: '强下降·确认反弹', value: 'STRONG_BEARISH_CONFIRMED_BOUNCE' },
  { label: '下降反弹·进行中', value: 'BEARISH_PULLBACK_ONGOING' },
  { label: '下降反弹·筑顶', value: 'BEARISH_PULLBACK_TOPPING' },
  { label: '下降反弹·失败', value: 'BEARISH_PULLBACK_FAILURE' },
  { label: '下降末端·延续反弹', value: 'BEARISH_ENDING_CONTINUE_UP' },
  { label: '下降末端·转势确认', value: 'BEARISH_ENDING_CONFIRM' },
  { label: '震荡·无方向', value: 'RANGING_NO_DIRECTION' },
  { label: '未知', value: 'UNKNOWN' },
]

function createEmptyCondition(): RuleCondition {
  return {
    indicator: 'CLOSE',
    params: {},
    operator: 'GT',
    value: '0',
    direction: 'BOTH',
  }
}

function createEmptyRule(): WeightRule {
  return {
    name: '',
    type: 'SCORING',
    score: 1,
    vetoWeight: 0.2,
    conditions: [createEmptyCondition()],
    conditionOperator: 'AND',
    enabled: true,
  }
}

// PATTERN_TYPE 多选辅助状态（el-select multiple 需要绑定数组）
const condParamsArray = ref<string[][]>([])

function syncCondParamsArray() {
  const arr: string[][] = []
  if (selectedRule.value?.conditions) {
    selectedRule.value.conditions.forEach((c, i) => {
      if (c.indicator === 'PATTERN_TYPE' && (c.operator === 'IN' || c.operator === 'NOT_IN')) {
        arr[i] = c.params?.categoryValues
          ? c.params.categoryValues.split(',').map((s: string) => s.trim())
          : []
      } else {
        arr[i] = []
      }
    })
  }
  condParamsArray.value = arr
}

function onPatternMultiChange(ci: number) {
  const cond = selectedRule.value?.conditions?.[ci]
  if (!cond) return
  const arr = condParamsArray.value[ci] || []
  cond.params.categoryValues = arr.join(',')
  markDirty()
}

function onPatternOperatorChange(ci: number) {
  const cond = selectedRule.value?.conditions?.[ci]
  if (!cond) return
  if (cond.operator === 'IN' || cond.operator === 'NOT_IN') {
    // 切换到多选模式
    delete cond.params.categoryValue
    cond.params.categoryValues = ''
    condParamsArray.value[ci] = []
  } else {
    // 切换到单选模式
    delete cond.params.categoryValues
    condParamsArray.value[ci] = []
    if (!cond.operator) cond.operator = 'EQ'
  }
  markDirty()
}

function normalizeCondition(cond: any) {
  if (cond.value === undefined || cond.value === null) {
    cond.value = String(cond.expectedValue ?? '0')
  } else {
    cond.value = String(cond.value)
  }
  if (!cond.params) cond.params = {}
  if (!cond.direction) cond.direction = 'BOTH'
  if (cond.indicator === 'SMC_MARKET_TREND') {
    if (!cond.operator) cond.operator = 'EQ'
    if (cond.value === undefined || cond.value === null || cond.value === '' || cond.value === '0') cond.value = '1.0'
  }
  if (cond.indicator === 'PATTERN_TYPE') {
    if (!cond.operator) cond.operator = 'EQ'
    cond.value = '1.0'
  }
  if (cond.indicator === 'MACD') {
    if (!cond.params) cond.params = {}
    if (!cond.params.categoryValue) cond.params.categoryValue = 'MACD_LINE'
    if (!cond.params.fast_period) cond.params.fast_period = '12'
    if (!cond.params.slow_period) cond.params.slow_period = '26'
    if (!cond.params.signal_period) cond.params.signal_period = '9'
    if (!cond.params.timeframe) cond.params.timeframe = ''
    if (!cond.operator) cond.operator = 'GT'
    if (cond.value === undefined || cond.value === null || cond.value === '' || cond.value === '0') cond.value = '0'
  }
  // 通用初始化：从 metadata 初始化参数默认值
  const meta = getMetadataForIndicator(cond.indicator)
  if (meta?.params) {
    for (const p of meta.params) {
      if (cond.params[p.key] === undefined || cond.params[p.key] === null) {
        if (p.defaultValue !== undefined && p.defaultValue !== null) {
          cond.params[p.key] = p.defaultValue
        }
      }
    }
  }
}

function markDirty() {
  dirty.value = true
}

function onCondChange(ci: number) {
  const rule = rules.value[selectedRuleIndex.value!]
  const cond = rule?.conditions?.[ci]
  if (cond) {
    if (!cond.params) cond.params = {}
    if (cond.indicator === 'SMC_MARKET_TREND') {
      if (!cond.operator) cond.operator = 'EQ'
      if (!cond.value || cond.value === '0') cond.value = '1.0'
    }
    if (cond.indicator === 'SMC_ALIGNMENT') {
      if (!cond.operator) cond.operator = 'EQ'
      if (!cond.value || cond.value === '0') cond.value = '1.0'
      if (!cond.params.categoryValue) cond.params.categoryValue = '顺势做多'
    }
    if (cond.indicator === 'PATTERN_TYPE') {
      cond.operator = 'EQ'
      cond.value = '1.0'
      delete cond.params.categoryValues
      syncCondParamsArray()
    }
    if (cond.indicator === 'MACD') {
      if (!cond.params.categoryValue) cond.params.categoryValue = 'MACD_LINE'
      if (!cond.params.fast_period) cond.params.fast_period = '12'
      if (!cond.params.slow_period) cond.params.slow_period = '26'
      if (!cond.params.signal_period) cond.params.signal_period = '9'
      if (!cond.params.timeframe) cond.params.timeframe = ''
      if (!cond.operator) cond.operator = 'GT'
      if (cond.value === undefined || cond.value === null || cond.value === '') cond.value = '0'
    }
    // 通用初始化：从 metadata 初始化参数默认值
    const meta = getMetadataForIndicator(cond.indicator)
    if (meta?.params) {
      for (const p of meta.params) {
        if (cond.params[p.key] === undefined || cond.params[p.key] === null) {
          if (p.defaultValue !== undefined && p.defaultValue !== null) {
            cond.params[p.key] = p.defaultValue
          } else if (p.type === 'INTEGER' || p.type === 'NUMBER') {
            cond.params[p.key] = ''
          }
        }
      }
    }
  }
  markDirty()
}

function onMacdCategoryChange(ci: number) {
  const cond = selectedRule.value?.conditions?.[ci]
  if (!cond) return
  // 布尔类（金叉/死叉/背离）默认 EQ 1，数值类保持当前运算符
  const boolCategories = ['GOLDEN_CROSS', 'DEATH_CROSS', 'BULLISH_DIVERGENCE', 'BEARISH_DIVERGENCE']
  if (boolCategories.includes(cond.params?.categoryValue || '')) {
    if (!cond.operator || cond.operator === 'GT') cond.operator = 'EQ'
    if (cond.value === undefined || cond.value === null || cond.value === '' || cond.value === '0') cond.value = '1'
  }
  // 柱体动能默认 EQ，清空旧值
  if (cond.params?.categoryValue === 'HISTOGRAM_MOMENTUM') {
    cond.operator = 'EQ'
    cond.value = ''
  }
  markDirty()
}

function onConfigChange() {
  selectedRuleIndex.value = null
  testResult.value = null
  loadRulesForCurrentConfig()
}

function loadRulesForCurrentConfig() {
  if (!selectedConfigId.value) {
    rules.value = []
    engineEnabled.value = false
    return
  }
  const cfg = configs.value.find(c => c.id === selectedConfigId.value)
  if (!cfg) {
    return
  }
  if (cfg.weightRules) {
    engineEnabled.value = cfg.weightRules.enabled
    rules.value = JSON.parse(JSON.stringify(cfg.weightRules.rules || []))
    rules.value.forEach(r => {
      if (r.enabled === undefined || r.enabled === null) r.enabled = true
      // 兼容旧数据：conditionMatch → conditionOperator
      if ((r as any).conditionMatch && !r.conditionOperator) {
        r.conditionOperator = (r as any).conditionMatch === 'ALL' ? 'AND' : 'OR'
        delete (r as any).conditionMatch
      }
      r.conditions.forEach(normalizeCondition)
    })
    if (cfg.weightRules.scoringConfig) {
      scoringConfig.value = { ...scoringConfig.value, ...cfg.weightRules.scoringConfig }
    }
    syncCondParamsArray()
  } else {
    engineEnabled.value = false
    rules.value = []
    scoringConfig.value = {
      vetoContributeScore: true,
      mappingMode: 'STEP',
      linearSlope: 0.6,
      linearMinWeight: 0.0,
      linearMaxWeight: 2.0,
    }
  }
  dirty.value = false
}

async function saveAllRules() {
  if (!selectedConfigId.value) return
  const cfg = configs.value.find(c => c.id === selectedConfigId.value)
  if (!cfg) return

  const normalized = JSON.parse(JSON.stringify(rules.value))
  normalized.forEach((r: any) => r.conditions.forEach(normalizeCondition))

  saving.value = true
  try {
    const res = await updateSignalServiceConfig(selectedConfigId.value, {
      ...cfg,
      weightRules: {
        enabled: engineEnabled.value,
        rules: normalized,
        scoringConfig: scoringConfig.value,
      },
    })
    if (res.success) {
      ElMessage.success('权重规则已保存')
      dirty.value = false
      await loadConfigs()
      loadRulesForCurrentConfig()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e: any) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

function addRule() {
  rules.value.push(createEmptyRule())
  selectedRuleIndex.value = rules.value.length - 1
  markDirty()
}

function deleteRule(index: number) {
  rules.value.splice(index, 1)
  if (selectedRuleIndex.value === index) {
    selectedRuleIndex.value = null
  } else if (selectedRuleIndex.value !== null && selectedRuleIndex.value > index) {
    selectedRuleIndex.value--
  }
  markDirty()
}

function addCondition() {
  if (selectedRule.value) {
    selectedRule.value.conditions.push(createEmptyCondition())
    markDirty()
  }
}

function removeCondition(ci: number) {
  if (selectedRule.value) {
    selectedRule.value.conditions.splice(ci, 1)
    markDirty()
  }
}

function weightClass(w: number): string {
  if (w >= 1.5) return 'weight-high'
  if (w >= 1.0) return 'weight-mid'
  return 'weight-low'
}

async function runTest() {
  if (!rules.value.length) {
    ElMessage.warning('请先添加规则')
    return
  }
  const cleanedContext: Record<string, any> = {}
  for (const [k, v] of Object.entries(testForm.context)) {
    if (v !== undefined && v !== null && v !== '') cleanedContext[k] = v
  }

  testing.value = true
  testResult.value = null
  try {
    const res = await testRuleEngine({
      direction: testForm.direction,
      symbol: testForm.symbol || undefined,
      currentPrice: testForm.currentPrice > 0 ? testForm.currentPrice : undefined,
      marketTrend: testForm.marketTrend || undefined,
      weightRules: { enabled: engineEnabled.value, rules: rules.value, scoringConfig: scoringConfig.value },
      context: Object.keys(cleanedContext).length > 0 ? cleanedContext : undefined,
    })
    if (res.success && res.data) {
      testResult.value = res.data
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

async function loadConfigs() {
  const res = await getSignalServiceConfigs()
  if (res.success) {
    configs.value = res.data || []
  }
}

async function loadVersions() {
  if (!selectedConfigId.value) return
  restoring.value = true
  try {
    const res = await getWeightRuleVersions(selectedConfigId.value)
    if (res.success) {
      versions.value = res.data || []
      versionsVisible.value = true
    } else {
      ElMessage.error(res.message || '加载版本历史失败')
    }
  } catch (e: any) {
    ElMessage.error('加载版本历史失败: ' + (e.message || '未知错误'))
  } finally {
    restoring.value = false
  }
}

async function restoreVersion(version: number) {
  if (!selectedConfigId.value) return
  restoring.value = true
  try {
    const res = await restoreWeightRuleVersion(selectedConfigId.value, version)
    if (res.success && res.data) {
      engineEnabled.value = res.data.enabled
      rules.value = JSON.parse(JSON.stringify(res.data.rules || []))
      rules.value.forEach(r => {
        if (r.enabled === undefined || r.enabled === null) r.enabled = true
        r.conditions.forEach(normalizeCondition)
      })
      if (res.data.scoringConfig) {
        scoringConfig.value = { ...scoringConfig.value, ...res.data.scoringConfig }
      }
      dirty.value = true
      versionsVisible.value = false
      ElMessage.success(`已恢复到版本 ${version}，请点击"保存全部"生效`)
    } else {
      ElMessage.error(res.message || '恢复失败')
    }
  } catch (e: any) {
    ElMessage.error('恢复失败: ' + (e.message || '未知错误'))
  } finally {
    restoring.value = false
  }
}

onMounted(async () => {
  await loadConfigs()
  const indicatorsRes = await getIndicatorMetadata()
  if (indicatorsRes.success) {
    indicatorMetadataList.value = indicatorsRes.data || []
  }
  if (selectedConfigId.value) {
    loadRulesForCurrentConfig()
  }
})
</script>

<style scoped>
.weight-rule-engine {
  padding: 20px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.engine-status-bar {
  margin-bottom: 16px;
}
:deep(.engine-status-bar .el-switch) {
  --el-switch-off-color: #7a7a7a;
}
:deep(.engine-status-bar .el-switch__core) {
  border: 1px solid rgba(255,255,255,0.45) !important;
}
:deep(.engine-status-bar .el-switch.is-off .el-switch__action) {
  background-color: #ccc;
}
.main-content {
  min-height: calc(100vh - 180px);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
}

.rule-list-card {
  height: 100%;
}
.rule-items {
  max-height: calc(100vh - 320px);
  overflow-y: auto;
}
.rule-item {
  padding: 10px 12px;
  border: 1px solid var(--border-color, #e4e7ed);
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.rule-item:hover {
  border-color: var(--el-color-primary);
}
.rule-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.rule-item.disabled {
  opacity: 0.55;
}
.ri-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.ri-name {
  font-weight: 500;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  margin-right: 8px;
}
.ri-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: flex;
  gap: 12px;
  margin-bottom: 4px;
}
.ri-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.editor-card {
  margin-bottom: 16px;
}
.test-card {
  margin-bottom: 16px;
}

.condition-row {
  padding: 8px;
  border: 1px solid var(--border-color, #ebeef5);
  border-radius: 4px;
  margin-bottom: 6px;
  background: var(--el-fill-color-light);
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
