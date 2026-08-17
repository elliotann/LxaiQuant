<template>
  <div class="quant-ml">
    <div class="ml-header">
      <div class="header-title">
        <h2><el-icon style="vertical-align: middle; margin-right: 8px"><Cpu /></el-icon>机器学习实验室</h2>
        <span class="header-subtitle">基于随机森林的方向预测模型训练与推理</span>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="ml-tabs">
      <!-- 模型训练 -->
      <el-tab-pane label="模型训练" name="train">
        <div class="tab-body">
          <el-card shadow="never" class="train-card">
            <template #header>
              <span>训练配置</span>
            </template>
            <el-form :model="trainForm" label-width="80px" size="default">
              <el-row :gutter="24">
                <el-col :span="8">
                  <el-form-item label="交易对">
                    <el-select v-model="trainForm.symbol" style="width: 100%">
                      <el-option label="BTC/USDT" value="BTCUSDT" />
                      <el-option label="ETH/USDT" value="ETHUSDT" />
                      <el-option label="ETH永续" value="ETH-USDT-SWAP" />
                      <el-option label="SOL/USDT" value="SOLUSDT" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="模型类型">
                    <el-select v-model="trainForm.modelType" style="width: 100%">
                      <el-option label="方向预测" value="DIRECTION" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="24">
                <el-col :span="24">
                  <el-button type="primary" :loading="trainingNow" @click="handleTrain">
                    <el-icon style="margin-right: 4px"><Lightning /></el-icon>开始训练
                  </el-button>
                  <el-button :loading="trainingAsyncNow" @click="handleTrainAsync" style="margin-left: 12px">
                    <el-icon style="margin-right: 4px"><Warning /></el-icon>异步训练
                  </el-button>
                </el-col>
              </el-row>
            </el-form>
          </el-card>

          <el-collapse v-model="trainAdvancedOpen" style="margin-top: 12px">
            <el-collapse-item title="高级参数" name="advanced">
              <el-form :model="trainForm" label-width="120px" size="small">
                <el-row :gutter="24">
                  <el-col :span="8">
                    <el-form-item label="决策树数量">
                      <el-input-number v-model="trainForm.numTrees" :min="10" :max="500" :step="10" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="最大深度">
                      <el-input-number v-model="trainForm.maxDepth" :min="2" :max="30" :step="1" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="最小样本数">
                      <el-input-number v-model="trainForm.minSamples" :min="1" :max="50" :step="1" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </el-collapse-item>
          </el-collapse>

          <el-card v-if="lastTrainingJob" shadow="never" class="result-card" style="margin-top: 16px">
            <template #header>
              <span>
                <el-icon v-if="lastTrainingJob.status === 'SUCCESS'" class="icon-success"><SuccessFilled /></el-icon>
                <el-icon v-else-if="lastTrainingJob.status === 'FAILED'" class="icon-danger"><CircleCloseFilled /></el-icon>
                <el-icon v-else class="icon-loading"><Loading /></el-icon>
                训练结果
              </span>
            </template>
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="交易对">{{ lastTrainingJob.symbol }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="lastTrainingJob.status === 'SUCCESS' ? 'success' : lastTrainingJob.status === 'FAILED' ? 'danger' : 'warning'" size="small">
                  {{ lastTrainingJob.status }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="准确率">
                {{ lastTrainingJob.accuracy ? (lastTrainingJob.accuracy * 100).toFixed(2) + '%' : '--' }}
              </el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ lastTrainingJob.startTime || '--' }}</el-descriptions-item>
              <el-descriptions-item label="结束时间">{{ lastTrainingJob.endTime || '--' }}</el-descriptions-item>
              <el-descriptions-item v-if="lastTrainingJob.errorMsg" label="错误信息" :span="3">
                <span style="color: var(--accent-red)">{{ lastTrainingJob.errorMsg }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <el-card v-if="trainingProgress" shadow="never" style="margin-top: 16px">
            <template #header><span><el-icon class="icon-loading"><Loading /></el-icon> 训练进度</span></template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="任务ID">{{ trainingProgress.jobId }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="trainingProgress.status === 'SUCCESS' ? 'success' : trainingProgress.status === 'FAILED' ? 'danger' : 'warning'" size="small">
                  {{ trainingProgress.status }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
            <el-progress :percentage="Math.round(trainingProgress.progressPct * 100)" :status="trainingProgress.status === 'SUCCESS' ? 'success' : trainingProgress.status === 'FAILED' ? 'exception' : ''" :stroke-width="16" style="margin-top: 12px">
              <span>{{ trainingProgress.completedTrees }} / {{ trainingProgress.totalTrees }} 棵树</span>
            </el-progress>
            <div v-if="trainingProgress.currentAccuracy" style="margin-top: 8px; font-size: 13px; color: var(--text-muted)">
              当前准确率: {{ (trainingProgress.currentAccuracy * 100).toFixed(2) }}%
            </div>
            <div v-if="trainingProgress.errorMsg" style="margin-top: 8px; color: var(--accent-red); font-size: 13px">
              {{ trainingProgress.errorMsg }}
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <!-- 模型管理 -->
      <el-tab-pane label="模型管理" name="models">
        <div class="tab-body">
          <div class="model-toolbar">
            <el-select v-model="modelFilter.symbol" placeholder="选择交易对" style="width: 160px" @change="loadModels">
              <el-option label="全部" value="" />
              <el-option label="BTC/USDT" value="BTCUSDT" />
              <el-option label="ETH/USDT" value="ETHUSDT" />
              <el-option label="ETH永续" value="ETH-USDT-SWAP" />
              <el-option label="SOL/USDT" value="SOLUSDT" />
            </el-select>
            <el-button text @click="loadModels">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>

          <div v-if="modelList.length > 0" style="margin-bottom: 12px; font-size: 12px; color: var(--text-muted)">
            共 {{ modelList.length }} 个版本，最大保留 {{ modelList.length }} / 5 个版本
          </div>
          <el-table :data="modelList" v-loading="modelsLoading" stripe border style="width: 100%">
            <el-table-column prop="symbol" label="交易对" width="120" />
            <el-table-column prop="modelType" label="类型" width="120" />
            <el-table-column prop="version" label="版本" width="80">
              <template #default="{ row }">
                <el-tag :type="row.isActive ? 'primary' : 'info'" size="small">v{{ row.version }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="accuracy" label="准确率" width="100">
              <template #default="{ row }">
                {{ row.accuracy ? (row.accuracy * 100).toFixed(2) + '%' : '--' }}
              </template>
            </el-table-column>
            <el-table-column label="F1 Score" width="100">
              <template #default="{ row }">
                {{ row.f1Score ? (row.f1Score * 100).toFixed(2) + '%' : '--' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
                  {{ row.isActive ? '活跃' : '未激活' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="训练时间" width="170">
              <template #default="{ row }">
                {{ row.trainedAt ? dayjs(row.trainedAt).format('YYYY-MM-DD HH:mm') : '--' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.isActive" text type="primary" size="small" @click="handleActivate(row.id)">
                  <el-icon><Check /></el-icon> 激活
                </el-button>
                <el-button text type="primary" size="small" @click="showModelDetail(row)">
                  <el-icon><InfoFilled /></el-icon> 详情
                </el-button>
                <el-button text type="danger" size="small" @click="handleDelete(row.id)">
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- 在线预测 -->
      <el-tab-pane label="在线预测" name="predict">
        <div class="tab-body">
          <el-row :gutter="24">
            <el-col :span="8">
              <el-card shadow="never">
                <template #header><span>预测配置</span></template>
                <el-form :model="predictForm" label-width="80px">
                  <el-form-item label="交易对">
                    <el-select v-model="predictForm.symbol" style="width: 100%" @change="loadPredictModels">
                      <el-option label="BTC/USDT" value="BTCUSDT" />
                      <el-option label="ETH/USDT" value="ETHUSDT" />
                      <el-option label="ETH永续" value="ETH-USDT-SWAP" />
                      <el-option label="SOL/USDT" value="SOLUSDT" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="模型版本">
                    <el-select v-model="predictForm.modelId" style="width: 100%" placeholder="默认活跃模型">
                      <el-option label="默认活跃模型" value="" />
                      <el-option v-for="m in predictModelList" :key="m.id" :label="'v' + m.version + ' (' + (m.accuracy ? (m.accuracy * 100).toFixed(1) + '%)' : '--')" :value="m.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" :loading="predicting" @click="handlePredict" style="width: 100%">
                      <el-icon><TrendCharts /></el-icon> 预测方向
                    </el-button>
                  </el-form-item>
                  <el-form-item>
                    <el-button :loading="volatilityLoading" @click="handlePredictVolatility" style="width: 100%">
                      <el-icon><DataAnalysis /></el-icon> 波动率预测
                    </el-button>
                  </el-form-item>
                  <el-form-item>
                    <el-button :loading="marketStateLoading" @click="handleMarketState" style="width: 100%">
                      <el-icon><DataAnalysis /></el-icon> 市场状态
                    </el-button>
                  </el-form-item>
                  <el-form-item>
                    <el-button :loading="featuresLoading" @click="loadFeatures" style="width: 100%">
                      <el-icon><DataAnalysis /></el-icon> 查看特征
                    </el-button>
                  </el-form-item>
                </el-form>
              </el-card>
            </el-col>
            <el-col :span="16">
              <el-card v-if="predictionResult" shadow="never">
                <template #header><span><el-icon><TrendCharts /></el-icon> 方向预测结果</span></template>
                <el-row :gutter="16">
                  <el-col :span="8">
                    <div class="predict-metric">
                      <div class="metric-label">预测方向</div>
                      <div class="metric-value" :class="predictionResult.direction === 'UP' ? 'up' : 'down'">
                        <el-icon v-if="predictionResult.direction === 'UP'" :size="32"><Top /></el-icon>
                        <el-icon v-else-if="predictionResult.direction === 'DOWN'" :size="32"><Bottom /></el-icon>
                        <div>{{ predictionResult.direction === 'UP' ? '上涨' : predictionResult.direction === 'DOWN' ? '下跌' : '--' }}</div>
                      </div>
                    </div>
                  </el-col>
                  <el-col :span="8">
                    <div class="predict-metric">
                      <div class="metric-label">置信度</div>
                      <div class="metric-value" style="color: var(--accent-blue)">
                {{ (predictionResult.confidence * 100).toFixed(1) }}%
              </div>
                    </div>
                  </el-col>
                  <el-col :span="8">
                    <div class="predict-metric">
                      <div class="metric-label">上涨概率</div>
                      <div class="metric-value up">{{ (predictionResult.probabilityUp * 100).toFixed(1) }}%</div>
                    </div>
                  </el-col>
                </el-row>
                <el-row :gutter="16" style="margin-top: 8px">
                  <el-col :span="24">
                    <div class="probability-bar">
                      <div class="prob-label">下跌 <span style="color:var(--accent-red)">{{ ((1 - predictionResult.probabilityUp) * 100).toFixed(1) }}%</span></div>
                      <div class="prob-track">
                        <div class="prob-fill" :style="{ width: (predictionResult.probabilityUp * 100).toFixed(1) + '%' }"></div>
                      </div>
                      <div class="prob-label" style="text-align:right">上涨 <span style="color:var(--accent-green)">{{ (predictionResult.probabilityUp * 100).toFixed(1) }}%</span></div>
                    </div>
                  </el-col>
                </el-row>
                <el-row :gutter="16" style="margin-top: 16px">
                  <el-col :span="24">
                    <div ref="predictGaugeRef" style="height: 180px"></div>
                  </el-col>
                </el-row>
                <div v-if="predictionResult.message" style="text-align: center; color: var(--text-muted); font-size: 13px; margin-top: 8px;">
                  {{ predictionResult.message }}
                </div>
              </el-card>

              <el-card v-if="volatilityResult" shadow="never" style="margin-top: 16px">
                <template #header><span><el-icon><DataAnalysis /></el-icon> 波动率预测</span></template>
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="年化波动率">
                    {{ (volatilityResult.predictedVolatility * 100).toFixed(2) }}%
                  </el-descriptions-item>
                  <el-descriptions-item label="ATR">{{ Number(volatilityResult.atr).toFixed(6) }}</el-descriptions-item>
                  <el-descriptions-item label="波动率制度">
                    <el-tag :type="volatilityResult.regime === 'HIGH' ? 'danger' : volatilityResult.regime === 'MEDIUM' ? 'warning' : 'success'" size="small">
                      {{ volatilityResult.regime === 'HIGH' ? '高波动' : volatilityResult.regime === 'MEDIUM' ? '中波动' : '低波动' }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="建仓建议">
                    {{ volatilityResult.suggestion }}
                  </el-descriptions-item>
                </el-descriptions>
              </el-card>

              <el-card v-if="marketStateResult" shadow="never" style="margin-top: 16px">
                <template #header><span><el-icon><DataAnalysis /></el-icon> 市场状态</span></template>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="当前状态标签">{{ marketStateResult.regime }}</el-descriptions-item>
                  <el-descriptions-item label="状态描述">{{ marketStateResult.description }}</el-descriptions-item>
                  <el-descriptions-item label="聚类数量">
                    <el-tag v-for="c in marketStateResult.clusters" :key="c.clusterId" style="margin-right: 4px" size="small">
                      {{ c.label }} ({{ c.count }}次, 平均收益{{ (c.avgReturn * 100).toFixed(2) }}%)
                    </el-tag>
                  </el-descriptions-item>
                </el-descriptions>
              </el-card>

              <el-card v-if="currentFeatures" shadow="never" style="margin-top: 16px">
                <template #header><span>当前特征值</span></template>
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item v-for="(val, key) in currentFeatures" :key="key" :label="key">
                    <span :class="val >= 0 ? 'up' : 'down'">{{ Number(val).toFixed(4) }}</span>
                  </el-descriptions-item>
                </el-descriptions>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <!-- 特征分析 -->
      <el-tab-pane label="特征分析" name="features">
        <div class="tab-body">
          <el-row :gutter="24">
            <el-col :span="6">
              <el-select v-model="featureForm.symbol" style="width: 100%" @change="loadFeatureTimeSeriesIfReady">
                <el-option label="BTC/USDT" value="BTCUSDT" />
                <el-option label="ETH/USDT" value="ETHUSDT" />
                <el-option label="ETH永续" value="ETH-USDT-SWAP" />
                <el-option label="SOL/USDT" value="SOLUSDT" />
              </el-select>
            </el-col>
            <el-col :span="12">
              <el-checkbox-group v-model="featureForm.selectedFeatures">
                <el-checkbox v-for="f in allFeatureNames" :key="f" :label="f" size="small" />
              </el-checkbox-group>
            </el-col>
            <el-col :span="6" style="text-align: right">
              <el-button type="primary" :loading="featureTimeSeriesLoading" @click="loadFeatureTimeSeries">
                <el-icon><Refresh /></el-icon> 加载时序
              </el-button>
            </el-col>
          </el-row>

          <el-card shadow="never" style="margin-top: 16px">
            <template #header><span>多特征时序曲线</span></template>
            <el-alert v-if="!featureTimeSeriesData || featureTimeSeriesData.length === 0" title="选择交易对和特征后点击“加载时序”" type="info" show-icon :closable="false" />
            <div ref="featureTimeSeriesRef" style="height: 450px" v-show="featureTimeSeriesData && featureTimeSeriesData.length > 0"></div>
          </el-card>

          <div v-if="currentFeatures" class="features-grid" style="margin-top: 16px">
            <el-row :gutter="[16, 16]">
              <el-col v-for="(val, key) in currentFeatures" :key="key" :span="8">
                <el-card shadow="never" class="feature-card" :class="val >= 0 ? 'feature-up' : 'feature-down'">
                  <div class="feature-name">{{ key }}</div>
                  <div class="feature-value" :class="val >= 0 ? 'up' : 'down'">{{ Number(val).toFixed(4) }}</div>
                  <div class="feature-bar-bg">
                    <div class="feature-bar-fill" :style="{ width: Math.min(Math.abs(val) * 100, 100) + '%', background: val >= 0 ? 'var(--accent-green)' : 'var(--accent-red)' }"></div>
                  </div>
                </el-card>
              </el-col>
            </el-row>
          </div>
          <div v-else-if="!featuresLoading" class="empty-state">
            <el-empty description="暂无特征数据">
              <el-button type="primary" @click="loadFeatures">加载特征</el-button>
            </el-empty>
          </div>
        </div>
      </el-tab-pane>
      <!-- 特征自动搜索 -->
      <el-tab-pane label="特征自动搜索" name="auto-search">
        <div class="tab-body">

          <!-- 交易对 -->
          <el-select v-model="autoSearchSymbol" style="width: 200px; margin-bottom: 16px">
            <el-option label="BTC/USDT" value="BTCUSDT" />
            <el-option label="ETH/USDT" value="ETHUSDT" />
            <el-option label="ETH永续" value="ETH-USDT-SWAP" />
            <el-option label="SOL/USDT" value="SOLUSDT" />
          </el-select>

          <!-- 特征池配置 -->
          <el-collapse v-model="autoSearchCollapse">
            <el-collapse-item title="特征池配置" name="featurePool">
              <div style="margin-bottom: 8px">
                <el-button size="small" @click="selectAllFeatures">全选</el-button>
                <el-button size="small" @click="deselectAllFeatures">反选</el-button>
                <el-button size="small" @click="resetFeaturePool">重置</el-button>
              </div>
              <div v-for="def in featurePoolDefs" :key="def.name" class="feature-pool-row">
                <el-checkbox v-model="def.checked" :label="def.name" size="small" style="width: 100px" />
                <el-checkbox-group v-if="def.checked" v-model="def.selectedParams" size="small" style="display: inline-flex; flex-wrap: wrap; gap: 4px">
                  <el-checkbox v-for="p in def.paramOptions" :key="JSON.stringify(p)" :label="p" size="small" border>
                    {{ Array.isArray(p) ? p.join(',') : p }}
                  </el-checkbox>
                </el-checkbox-group>
                <span v-else style="color: var(--text-muted); font-size: 12px; margin-left: 8px">未选择</span>
              </div>
            </el-collapse-item>

            <el-collapse-item title="搜索与模型参数" name="searchParams">
              <el-form label-width="160px" size="small">
                <el-row :gutter="24">
                  <el-col :span="8">
                    <el-form-item label="最大组合数">
                      <el-input-number v-model="autoSearchMaxCombos" :min="100" :max="2000" :step="100" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="每组合特征数(最少)">
                      <el-input-number v-model="autoSearchMinFeatures" :min="2" :max="6" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="每组合特征数(最多)">
                      <el-input-number v-model="autoSearchMaxFeatures" :min="2" :max="10" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-divider content-position="left">随机森林超参数</el-divider>
                <el-row :gutter="24">
                  <el-col :span="8">
                    <el-form-item label="树数量">
                      <el-input-number v-model="autoSearchNumTrees" :min="100" :max="1000" :step="100" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="最大深度">
                      <el-input-number v-model="autoSearchMaxDepth" :min="2" :max="20" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="叶节点最小样本">
                      <el-input-number v-model="autoSearchMinSamples" :min="5" :max="100" :step="5" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-divider content-position="left">评分权重</el-divider>
                <el-row :gutter="24">
                  <el-col :span="8">
                    <el-form-item label="F1 权重">
                      <el-slider v-model="autoSearchWeightF1" :min="0" :max="1" :step="0.05" show-input style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="精确率 权重">
                      <el-slider v-model="autoSearchWeightPrecision" :min="0" :max="1" :step="0.05" show-input style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="信号数 权重">
                      <el-slider v-model="autoSearchWeightSignal" :min="0" :max="1" :step="0.05" show-input style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <div style="text-align: right; font-size: 12px; color: var(--text-muted); margin-top: 4px">
                  权重总和: {{ (autoSearchWeightF1 + autoSearchWeightPrecision + autoSearchWeightSignal).toFixed(2) }}
                  <el-tag v-if="Math.abs(autoSearchWeightF1 + autoSearchWeightPrecision + autoSearchWeightSignal - 1) > 0.01" type="warning" size="small" style="margin-left: 8px">总和应等于1</el-tag>
                  <el-tag v-else type="success" size="small" style="margin-left: 8px">已平衡</el-tag>
                </div>
                <el-divider content-position="left">数据划分</el-divider>
                <el-row :gutter="24">
                  <el-col :span="12">
                    <el-form-item label="总K线数量">
                      <el-input-number v-model="autoSearchTotalBars" :min="500" :max="5000" :step="500" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="测试集比例">
                      <el-slider v-model="autoSearchTestRatio" :min="0.1" :max="0.4" :step="0.05" show-input style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-divider content-position="left">阈值扫描</el-divider>
                <el-row :gutter="24">
                  <el-col :span="8">
                    <el-form-item label="起始">
                      <el-slider v-model="autoSearchThresholdStart" :min="0.3" :max="0.7" :step="0.05" show-input style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="结束">
                      <el-slider v-model="autoSearchThresholdEnd" :min="0.6" :max="0.95" :step="0.05" show-input style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="步长">
                      <el-slider v-model="autoSearchThresholdStep" :min="0.01" :max="0.1" :step="0.01" show-input style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
            </el-collapse-item>
          </el-collapse>

          <!-- 操作按钮 -->
          <div style="margin: 16px 0">
            <el-button
              type="primary"
              size="default"
              :loading="autoSearchRunning"
              :disabled="autoSearchRunning || !canStartAutoSearch"
              @click="handleStartAutoSearch"
            >
              <el-icon style="margin-right: 4px"><Search /></el-icon>
              {{ autoSearchRunning ? '搜索中...' : '开始搜索' }}
            </el-button>
            <el-button
              size="default"
              :disabled="!autoSearchRunning"
              @click="handleStopAutoSearch"
              style="margin-left: 12px"
            >
              <el-icon style="margin-right: 4px"><CircleClose /></el-icon>
              停止搜索
            </el-button>
          </div>

          <!-- 进度区域 -->
          <el-card v-if="autoSearchRunning || autoSearchDone" shadow="never" style="margin-bottom: 16px">
            <template #header>
              <span>
                <el-icon v-if="autoSearchRunning" class="icon-loading"><Loading /></el-icon>
                <el-icon v-else-if="autoSearchDone" class="icon-success"><SuccessFilled /></el-icon>
                {{ autoSearchRunning ? '搜索中...' : '搜索完成' }}
              </span>
            </template>
            <el-progress
              :percentage="autoSearchProgressPct"
              :status="autoSearchDone ? 'success' : ''"
              :stroke-width="16"
              style="margin-bottom: 8px"
            >
              <span>{{ autoSearchCompleted }} / {{ autoSearchTotal }} 组合</span>
            </el-progress>
            <div v-if="autoSearchBestSoFar && !autoSearchDone" style="font-size: 13px; color: var(--text-muted)">
              当前最佳: F1 {{ (autoSearchBestSoFar.f1 * 100).toFixed(1) }}% |
              精确率 {{ (autoSearchBestSoFar.precision * 100).toFixed(1) }}% |
              特征: {{ autoSearchBestSoFar.features?.join(', ') }}
            </div>
            <div v-if="autoSearchError" style="margin-top: 8px; color: var(--accent-red); font-size: 13px">
              {{ autoSearchError }}
            </div>
          </el-card>

          <!-- 结果表格 -->
          <div v-if="autoSearchResults.length > 0">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px">
              <span style="font-size: 14px; font-weight: 500">搜索结果 Top-{{ autoSearchResults.length }}</span>
              <el-button size="small" @click="exportAutoSearchCSV">
                <el-icon><Download /></el-icon> 导出 CSV
              </el-button>
            </div>
            <el-table
              :data="autoSearchResults"
              stripe
              border
              style="width: 100%"
              @sort-change="handleAutoSearchSort"
              :default-sort="{ prop: 'score', order: 'descending' }"
              :row-class-name="autoSearchRowClass"
            >
              <el-table-column label="排名" type="index" width="60" align="center" />
              <el-table-column label="特征组合" min-width="280">
                <template #default="{ row }">
                  <el-tag
                    v-for="f in row.features"
                    :key="f"
                    size="small"
                    style="margin-right: 4px; margin-bottom: 2px"
                  >
                    {{ f }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="featureCount" label="特征数" width="70" sortable="custom" align="center" />
              <el-table-column prop="bestThreshold" label="最佳阈值" width="90" sortable="custom" align="center">
                <template #default="{ row }">{{ row.bestThreshold.toFixed(2) }}</template>
              </el-table-column>
              <el-table-column prop="precision" label="精确率" width="90" sortable="custom" align="center">
                <template #default="{ row }">{{ (row.precision * 100).toFixed(1) }}%</template>
              </el-table-column>
              <el-table-column prop="recall" label="召回率" width="90" sortable="custom" align="center">
                <template #default="{ row }">{{ (row.recall * 100).toFixed(1) }}%</template>
              </el-table-column>
              <el-table-column prop="f1" label="F1" width="80" sortable="custom" align="center">
                <template #default="{ row }">{{ (row.f1 * 100).toFixed(1) }}%</template>
              </el-table-column>
              <el-table-column prop="score" label="得分" width="80" sortable="custom" align="center">
                <template #default="{ row }">{{ row.score.toFixed(4) }}</template>
              </el-table-column>
              <el-table-column prop="signalCount" label="信号数" width="70" sortable="custom" align="center" />
              <el-table-column label="操作" width="140" fixed="right" align="center">
                <template #default="{ row }">
                  <el-button text type="primary" size="small" @click="showAutoSearchDetail(row)">
                    详情
                  </el-button>
                  <el-button text type="primary" size="small" @click="handleApplyFeatureCombo(row)">
                    应用
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 模型详情弹窗 -->

    <!-- 特征搜索详情抽屉 -->
    <el-drawer v-model="autoSearchDetailVisible" title="组合详情" size="500px" @closed="onAutoSearchDetailClosed">
      <template v-if="autoSearchDetailData">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="最佳阈值" :span="1">{{ autoSearchDetailData.bestThreshold.toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="信号数" :span="1">{{ autoSearchDetailData.signalCount }}</el-descriptions-item>
          <el-descriptions-item label="精确率">{{ (autoSearchDetailData.precision * 100).toFixed(1) }}%</el-descriptions-item>
          <el-descriptions-item label="召回率">{{ (autoSearchDetailData.recall * 100).toFixed(1) }}%</el-descriptions-item>
          <el-descriptions-item label="F1" :span="2">{{ (autoSearchDetailData.f1 * 100).toFixed(1) }}%</el-descriptions-item>
        </el-descriptions>

        <el-card shadow="never" class="chart-card" style="margin-bottom: 16px">
          <template #header><span>特征组合</span></template>
          <div>
            <el-tag v-for="f in autoSearchDetailData.features" :key="f" size="default" style="margin-right: 6px; margin-bottom: 4px">
              {{ f }}
            </el-tag>
          </div>
        </el-card>

        <el-card shadow="never" class="chart-card" style="margin-bottom: 16px">
          <template #header><span>混淆矩阵</span></template>
          <div ref="autoSearchCmRef" style="height: 220px"></div>
        </el-card>

        <el-card shadow="never" class="chart-card">
          <template #header><span>特征重要性</span></template>
          <div v-if="autoSearchDetailData.featureImportance && Object.keys(autoSearchDetailData.featureImportance).length > 0" ref="autoSearchFiRef" style="height: 220px"></div>
          <el-empty v-else description="暂无特征重要性数据" />
        </el-card>
      </template>
    </el-drawer>
    <el-dialog v-model="detailVisible" title="模型详情" width="800px" @closed="onDetailClosed">
      <template v-if="detailModel">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 20px">
          <el-descriptions-item label="交易对" :span="1">{{ detailModel.symbol }}</el-descriptions-item>
          <el-descriptions-item label="模型类型" :span="1">{{ detailModel.modelType }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detailModel.version }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailModel.isActive ? 'success' : 'info'" size="small">
              {{ detailModel.isActive ? '活跃' : '未激活' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="准确率">{{ detailModel.accuracy ? (detailModel.accuracy * 100).toFixed(2) + '%' : '--' }}</el-descriptions-item>
          <el-descriptions-item label="F1 Score">{{ detailModel.f1Score ? (detailModel.f1Score * 100).toFixed(2) + '%' : '--' }}</el-descriptions-item>
          <el-descriptions-item label="精确率">{{ detailModel.precision ? (detailModel.precision * 100).toFixed(2) + '%' : '--' }}</el-descriptions-item>
          <el-descriptions-item label="召回率">{{ detailModel.recall ? (detailModel.recall * 100).toFixed(2) + '%' : '--' }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ detailModel.fileSize ? (detailModel.fileSize / 1024).toFixed(1) + ' KB' : '--' }}</el-descriptions-item>
          <el-descriptions-item label="训练耗时">{{ detailModel.trainingDurationMs ? (detailModel.trainingDurationMs / 1000).toFixed(1) + 's' : '--' }}</el-descriptions-item>
          <el-descriptions-item label="训练时间">{{ detailModel.trainedAt ? dayjs(detailModel.trainedAt).format('YYYY-MM-DD HH:mm') : '--' }}</el-descriptions-item>
        </el-descriptions>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-card shadow="never" class="chart-card">
              <template #header><span>特征重要性</span></template>
              <div ref="featureImportanceRef" style="height: 260px" v-loading="detailChartsLoading"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never" class="chart-card">
              <template #header><span>混淆矩阵</span></template>
              <div ref="confusionMatrixRef" style="height: 260px" v-loading="detailChartsLoading"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" style="margin-top: 16px">
          <el-col :span="24">
            <el-card shadow="never" class="chart-card">
              <template #header><span>准确率趋势</span></template>
              <div ref="accuracyTrendRef" style="height: 220px" v-loading="detailChartsLoading"></div>
            </el-card>
          </el-col>
        </el-row>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Cpu, Lightning, Warning, TrendCharts, DataAnalysis,
  SuccessFilled, CircleCloseFilled, Loading,
  Refresh, Check, InfoFilled, Delete,
  Top, Bottom,
  Search, CircleClose, Download
} from "@element-plus/icons-vue";
import * as echarts from "echarts";
import dayjs from "dayjs";
import {
  trainModel, trainModelAsync, predictDirection, getTrainingProgress,
  listModels, activateModel, deleteModel, getCurrentFeatures,
  getFeatureImportance, getConfusionMatrix, getAccuracyTrend,
  predictVolatility, getMarketState, getFeatureTimeSeries,
  startAutoSearch, getAutoSearchProgress, stopAutoSearch,
  applyFeatureCombination, getFeaturePoolDefs
} from "@/api/quant-ml";

interface TrainingJob {
  symbol?: string;
  status?: string;
  accuracy?: number;
  startTime?: string;
  endTime?: string;
  errorMsg?: string;
}

interface MlModelRecord {
  id: string;
  symbol: string;
  modelType: string;
  version: string;
  accuracy?: number;
  f1Score?: number;
  precision?: number;
  recall?: number;
  isActive: boolean;
  trainedAt?: string;
  fileSize?: number;
  md5Checksum?: string;
  trainingDurationMs?: number;
}

interface PredictionResult {
  direction?: string;
  confidence: number;
  probabilityUp: number;
  message?: string;
}

interface VolatilityPrediction {
  predictedVolatility: number;
  regime: string;
  atr: number;
  suggestion?: string;
}

interface ClusterInfo {
  clusterId: number;
  label: string;
  description: string;
  count: number;
  avgReturn: number;
  avgRange: number;
}

interface MarketStateCluster {
  regime: string;
  description: string;
  clusters: ClusterInfo[];
  currentCluster: number;
}

interface FeaturePoolDef {
  name: string;
  label: string;
  checked: boolean;
  paramOptions: any[];
  selectedParams: any[];
}

interface AutoSearchCombo {
  features: string[];
  featureCount: number;
  bestThreshold: number;
  precision: number;
  recall: number;
  f1: number;
  score: number;
  signalCount: number;
  confusionMatrix?: {
    tp: number; fp: number; fn: number; tn: number;
  };
  featureImportance?: Record<string, number>;
}

interface TrainingProgressInfo {
  jobId: string;
  totalTrees: number;
  completedTrees: number;
  currentAccuracy: number;
  status: string;
  errorMsg: string;
  progressPct: number;
}

interface Features {
  [key: string]: number;
}

const isDarkTheme = computed(() => document.documentElement.classList.contains("dark"));

const cssVar = (name: string, fallback: string) =>
  getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;

const hexToRgba = (hex: string, alpha: number) => {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${alpha})`;
};

const activeTab = ref("train");

const trainingNow = ref(false);
const trainingAsyncNow = ref(false);
const lastTrainingJob = ref<TrainingJob | null>(null);
const trainingProgress = ref<TrainingProgressInfo | null>(null);
const trainAdvancedOpen = ref<string[]>([]);
const trainForm = ref({ symbol: "BTCUSDT", modelType: "DIRECTION", numTrees: 100, maxDepth: 10, minSamples: 5 });

const handleTrain = async () => {
  trainingNow.value = true;
  lastTrainingJob.value = null;
  try {
    const res = await trainModel(trainForm.value.symbol, trainForm.value.modelType);
    lastTrainingJob.value = res.data || res;
    ElMessage.success("训练完成");
  } catch {
    ElMessage.error("训练失败");
  } finally {
    trainingNow.value = false;
  }
};

const handleTrainAsync = async () => {
  trainingAsyncNow.value = true;
  try {
    const res = await trainModelAsync(trainForm.value.symbol);
    const data = res.data || res;
    ElMessage.info("异步训练已启动");
    const jobId = data?.jobId || (typeof data === 'object' && data !== null ? (data as any).jobId : null);
    if (jobId) {
      startProgressPolling(jobId);
    }
  } catch {
    ElMessage.error("启动训练失败");
  } finally {
    trainingAsyncNow.value = false;
  }
};

const startProgressPolling = (jobId: string) => {
  const pollInterval = 2000;
  const timer = setInterval(async () => {
    try {
      const res = await getTrainingProgress(jobId);
      const data = res.data || res;
      trainingProgress.value = data as TrainingProgressInfo;
      if (data.status === 'SUCCESS' || data.status === 'FAILED') {
        clearInterval(timer);
        if (data.status === 'SUCCESS') {
          ElMessage.success('训练完成');
        } else {
          ElMessage.error('训练失败: ' + (data.errorMsg || '未知错误'));
        }
        loadModels();
      }
    } catch {
      clearInterval(timer);
    }
  }, pollInterval);
};

const modelList = ref<MlModelRecord[]>([]);
const modelsLoading = ref(false);
const modelFilter = ref({ symbol: "" });

const loadModels = async () => {
  modelsLoading.value = true;
  try {
    const symbol = modelFilter.value.symbol || "BTCUSDT";
    const res = await listModels(symbol);
    modelList.value = Array.isArray(res) ? res : (res.data || []);
  } catch {
    modelList.value = [];
  } finally {
    modelsLoading.value = false;
  }
};

const handleActivate = async (id: string) => {
  try {
    await activateModel(id);
    ElMessage.success("模型已激活");
    loadModels();
  } catch {
    ElMessage.error("激活失败");
  }
};

const handleDelete = async (id: string) => {
  try {
    await ElMessageBox.confirm("确认删除该模型？", "提示", { confirmButtonText: "确认", cancelButtonText: "取消", type: "warning" });
    await deleteModel(id);
    ElMessage.success("已删除");
    loadModels();
  } catch {
    // cancelled
  }
};

const detailVisible = ref(false);
const detailModel = ref<MlModelRecord | null>(null);
const detailChartsLoading = ref(false);

const featureImportanceRef = ref<HTMLElement | null>(null);
const confusionMatrixRef = ref<HTMLElement | null>(null);
let featureImportanceChart: echarts.ECharts | null = null;
let confusionMatrixChart: echarts.ECharts | null = null;

const showModelDetail = async (row: MlModelRecord) => {
  detailModel.value = row;
  detailVisible.value = true;
  detailChartsLoading.value = true;

  await nextTick();

  try {
    const [fiRes, cmRes, atRes] = await Promise.all([
      getFeatureImportance(row.id).catch(() => null),
      getConfusionMatrix(row.id).catch(() => null),
      getAccuracyTrend(row.id).catch(() => null)
    ]);

    const featureData = fiRes?.data || fiRes;
    const matrixData = cmRes?.data || cmRes;
    const accuracyData = atRes?.data || atRes;

    await nextTick();
    if (featureImportanceRef.value) {
      renderFeatureImportance(featureData);
    }
    if (confusionMatrixRef.value) {
      renderConfusionMatrix(matrixData);
    }
    if (accuracyTrendRef.value) {
      renderAccuracyTrend(accuracyData);
    }
  } finally {
    detailChartsLoading.value = false;
  }
};

function renderFeatureImportance(data: Record<string, number> | null) {
  if (featureImportanceChart) featureImportanceChart.dispose();
  if (!featureImportanceRef.value) return;
  featureImportanceChart = echarts.init(featureImportanceRef.value, isDarkTheme.value ? 'dark' : undefined);

  if (!data || Object.keys(data).length === 0) {
    featureImportanceChart.setOption({
      title: { text: "暂无数据", left: "center", top: "center", textStyle: { fontSize: 14, color: cssVar('--text-muted', '#909399') } }
    });
    return;
  }

  const sorted = Object.entries(data).sort((a, b) => b[1] - a[1]);
  const names = sorted.map(([k]) => k);
  const values = sorted.map(([, v]) => Number(v.toFixed(4)));

  featureImportanceChart.setOption({
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: any) => `${params[0].name}: ${(params[0].value * 100).toFixed(2)}%`
    },
    grid: { left: "3%", right: "10%", bottom: "10%", top: "10%", containLabel: true },
    xAxis: {
      type: "value",
      axisLabel: { formatter: (v: number) => (v * 100).toFixed(0) + "%" }
    },
    yAxis: {
      type: "category",
      data: names.reverse(),
      axisLabel: { fontSize: 12 }
    },
    series: [{
      type: "bar",
      data: values.reverse().map((v, i) => ({
        value: v,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: cssVar('--accent-blue', '#409eff') },
            { offset: 1, color: "#79bbff" }
          ])
        }
      })),
      barWidth: "60%",
      label: {
        show: true,
        position: "right",
        formatter: (p: any) => (p.value * 100).toFixed(1) + "%",
        fontSize: 11,
        color: cssVar('--text-muted', '#909399')
      }
    }]
  });
}

function renderConfusionMatrix(data: Record<string, number> | null) {
  if (confusionMatrixChart) confusionMatrixChart.dispose();
  if (!confusionMatrixRef.value) return;
  confusionMatrixChart = echarts.init(confusionMatrixRef.value, isDarkTheme.value ? 'dark' : undefined);

  if (!data || Object.keys(data).length === 0) {
    confusionMatrixChart.setOption({
      title: { text: "暂无数据", left: "center", top: "center", textStyle: { fontSize: 14, color: cssVar('--text-muted', '#909399') } }
    });
    return;
  }

  const tp = data.tp ?? 0;
  const fp = data.fp ?? 0;
  const fn = data.fn ?? 0;
  const tn = data.tn ?? 0;
  const total = tp + fp + fn + tn || 1;

  const matrixData = [
    { value: tp, name: "TP\n正确预测上涨" },
    { value: fp, name: "FP\n误报上涨" },
    { value: fn, name: "FN\n漏报上涨" },
    { value: tn, name: "TN\n正确预测下跌" }
  ];

  confusionMatrixChart.setOption({
    tooltip: {
      formatter: (params: any) => {
        const d = params.data;
        return `${d.name}<br/>数量: ${d.value}<br/>占比: ${((d.value / total) * 100).toFixed(1)}%`;
      }
    },
    series: [{
      type: "pie",
      radius: ["40%", "70%"],
      center: ["50%", "55%"],
      avoidLabelOverlap: true,
      itemStyle: {
        borderRadius: 6,
        borderColor: "#fff",
        borderWidth: 2
      },
      label: {
        show: true,
        formatter: (p: any) => `${p.name}\n${p.percent.toFixed(1)}%`,
        fontSize: 11
      },
      emphasis: {
        label: { show: true, fontSize: 14, fontWeight: "bold" }
      },
      data: matrixData,
      color: [cssVar('--accent-green', '#67c23a'), cssVar('--accent-orange', '#e6a23c'), cssVar('--accent-red', '#f56c6c'), cssVar('--accent-blue', '#409eff')]
    }]
  });
}

function onDetailClosed() {
  if (featureImportanceChart) { featureImportanceChart.dispose(); featureImportanceChart = null; }
  if (confusionMatrixChart) { confusionMatrixChart.dispose(); confusionMatrixChart = null; }
  if (accuracyTrendChart) { accuracyTrendChart.dispose(); accuracyTrendChart = null; }
}

const predictForm = ref({ symbol: "BTCUSDT" });
const predicting = ref(false);
const predictionResult = ref<PredictionResult | null>(null);

const predictGaugeRef = ref<HTMLElement | null>(null);
let predictGaugeChart: echarts.ECharts | null = null;

const handlePredict = async () => {
  predicting.value = true;
  predictionResult.value = null;
  try {
    const res = await predictDirection(predictForm.value.symbol);
    predictionResult.value = res.data || res;
    await nextTick();
    if (predictGaugeRef.value) {
      renderPredictGauge(predictionResult.value);
    }
  } catch {
    ElMessage.error("预测失败");
  } finally {
    predicting.value = false;
  }
};

function renderPredictGauge(result: PredictionResult) {
  if (predictGaugeChart) predictGaugeChart.dispose();
  if (!predictGaugeRef.value) return;
  predictGaugeChart = echarts.init(predictGaugeRef.value, isDarkTheme.value ? 'dark' : undefined);

  const probUp = Math.round(result.probabilityUp * 100);
  const probDown = 100 - probUp;

  predictGaugeChart.setOption({
    tooltip: { formatter: () => `上涨概率: ${probUp}%<br/>下跌概率: ${probDown}%` },
    series: [
      {
        type: "gauge",
        startAngle: 180,
        endAngle: 0,
        min: 0,
        max: 100,
        splitNumber: 5,
        progress: {
          show: true,
          width: 18,
          itemStyle: {
            color: {
              type: "linear",
              x: 0, y: 0, x2: 1, y2: 0,
              colorStops: [
                { offset: 0, color: cssVar('--accent-red', '#f56c6c') },
                { offset: 0.5, color: cssVar('--accent-orange', '#e6a23c') },
                { offset: 1, color: cssVar('--accent-green', '#67c23a') }
              ]
            }
          }
        },
        axisLine: { lineStyle: { width: 18 } },
        axisTick: { show: false },
        splitLine: { length: 10, lineStyle: { width: 2, color: cssVar('--text-muted', '#999') } },
        axisLabel: {
          distance: 20,
          formatter: (v: number) => v + "%"
        },
        pointer: { show: false },
        detail: {
          valueAnimation: true,
          formatter: (v: number) => `上涨 ${v}%`,
          color: probUp >= 50 ? cssVar('--accent-green', '#67c23a') : cssVar('--accent-red', '#f56c6c'),
          fontSize: 18,
          fontWeight: 700,
          offsetCenter: [0, "20%"]
        },
        data: [{ value: probUp, name: "" }]
      }
    ]
  });
}

watch(predictGaugeRef, () => {
  if (predictionResult.value && predictGaugeRef.value) {
    renderPredictGauge(predictionResult.value);
  }
});

const featuresLoading = ref(false);
const currentFeatures = ref<Features | null>(null);

const loadFeatures = async () => {
  const symbol = activeTab.value === "features" ? featureForm.value.symbol : predictForm.value.symbol;
  featuresLoading.value = true;
  try {
    const res = await getCurrentFeatures(symbol);
    currentFeatures.value = res.data || res;
  } catch {
    currentFeatures.value = null;
  } finally {
    featuresLoading.value = false;
  }
};

const volatilityResult = ref<VolatilityPrediction | null>(null);
const volatilityLoading = ref(false);

const handlePredictVolatility = async () => {
  volatilityLoading.value = true;
  volatilityResult.value = null;
  try {
    const res = await predictVolatility(predictForm.value.symbol);
    volatilityResult.value = res.data || res;
  } catch {
    ElMessage.error("波动率预测失败");
  } finally {
    volatilityLoading.value = false;
  }
};

const marketStateResult = ref<MarketStateCluster | null>(null);
const marketStateLoading = ref(false);

const handleMarketState = async () => {
  marketStateLoading.value = true;
  marketStateResult.value = null;
  try {
    const res = await getMarketState(predictForm.value.symbol);
    marketStateResult.value = res.data || res;
  } catch {
    ElMessage.error("获取市场状态失败");
  } finally {
    marketStateLoading.value = false;
  }
};

const predictModelList = ref<MlModelRecord[]>([]);
predictForm.value = { symbol: "BTCUSDT", modelId: "" };

const loadPredictModels = async () => {
  try {
    const res = await listModels(predictForm.value.symbol);
    predictModelList.value = Array.isArray(res) ? res : (res.data || []);
  } catch {
    predictModelList.value = [];
  }
};

const allFeatureNames = ["RSI", "MACD", "MACD_Signal", "Bollinger_%B", "PriceChange_%", "ATR", "VolumeRatio", "EMADiff"];
const featureForm = ref({ symbol: "BTCUSDT", selectedFeatures: ["RSI", "MACD", "ATR"] });
const featureTimeSeriesRef = ref<HTMLElement | null>(null);
const featureTimeSeriesData = ref<any[] | null>(null);
const featureTimeSeriesLoading = ref(false);
let featureTimeSeriesChart: echarts.ECharts | null = null;

const loadFeatureTimeSeriesIfReady = () => {
  if (featureTimeSeriesData.value && featureTimeSeriesData.value.length > 0) {
    loadFeatureTimeSeries();
  }
};

const loadFeatureTimeSeries = async () => {
  featureTimeSeriesLoading.value = true;
  try {
    const res = await getFeatureTimeSeries(featureForm.value.symbol, 100);
    featureTimeSeriesData.value = res.data || res;
    await nextTick();
    if (featureTimeSeriesRef.value) {
      renderFeatureTimeSeries();
    }
  } catch {
    ElMessage.error("加载时序数据失败");
  } finally {
    featureTimeSeriesLoading.value = false;
  }
};

function renderFeatureTimeSeries() {
  if (featureTimeSeriesChart) featureTimeSeriesChart.dispose();
  if (!featureTimeSeriesRef.value || !featureTimeSeriesData.value) return;
  featureTimeSeriesChart = echarts.init(featureTimeSeriesRef.value, isDarkTheme.value ? 'dark' : undefined);

  const selectedSet = new Set(featureForm.value.selectedFeatures);
  const series: echarts.EChartsOption["series"] = [];
  const featureColorMap: Record<string, string> = {
    "RSI": "#f56c6c", "MACD": "#409eff", "MACD_Signal": "#79bbff",
    "Bollinger_%B": "#e6a23c", "PriceChange_%": "#67c23a",
    "ATR": "#9c27b0", "VolumeRatio": "#ff9800", "EMADiff": "#009688"
  };

  const timestamps = featureTimeSeriesData.value.map((d: any) =>
    dayjs(d.timestamp).format("MM-DD HH:mm")
  );

  for (const [key, color] of Object.entries(featureColorMap)) {
    if (!selectedSet.has(key)) continue;
    const values = featureTimeSeriesData.value.map((d: any) => d[key] ?? null);
    series.push({
      name: key,
      type: "line",
      data: values,
      smooth: true,
      symbol: "none",
      lineStyle: { width: 2, color },
      itemStyle: { color }
    });
  }

  featureTimeSeriesChart.setOption({
    tooltip: {
      trigger: "axis",
      formatter: (params: any) => {
        let html = `<div>${params[0].axisValue}</div>`;
        for (const p of params) {
          html += `<div style="display:flex;align-items:center;gap:4px">
            <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${p.color}"></span>
            ${p.seriesName}: ${Number(p.value).toFixed(4)}
          </div>`;
        }
        return html;
      }
    },
    legend: {
      type: "scroll",
      top: 0,
      textStyle: { fontSize: 11 }
    },
    grid: { left: "3%", right: "4%", bottom: "3%", top: "14%", containLabel: true },
    xAxis: {
      type: "category",
      data: timestamps,
      axisLabel: { rotate: 45, fontSize: 10, interval: "auto" }
    },
    yAxis: { type: "value", splitLine: { lineStyle: { type: "dashed" } } },
    dataZoom: [
      { type: "inside", start: 0, end: 100 },
      { type: "slider", start: 0, end: 100, height: 20, bottom: 0 }
    ],
    series
  });
}

const accuracyTrendRef = ref<HTMLElement | null>(null);
let accuracyTrendChart: echarts.ECharts | null = null;

function renderAccuracyTrend(data: any[] | null) {
  if (accuracyTrendChart) accuracyTrendChart.dispose();
  if (!accuracyTrendRef.value) return;
  accuracyTrendChart = echarts.init(accuracyTrendRef.value, isDarkTheme.value ? 'dark' : undefined);

  if (!data || data.length === 0) {
    accuracyTrendChart.setOption({
      title: { text: "暂无数据", left: "center", top: "center", textStyle: { fontSize: 14, color: cssVar('--text-muted', '#909399') } }
    });
    return;
  }

  const dates = data.map((d: any) => d.date);
  const accuracies = data.map((d: any) => (d.accuracy * 100).toFixed(1));
  const green = cssVar('--accent-green', '#67c23a');

  accuracyTrendChart.setOption({
    tooltip: {
      trigger: "axis",
      formatter: (params: any) => `${params[0].axisValue}<br/>准确率: ${params[0].value}%`
    },
    grid: { left: "3%", right: "4%", bottom: "3%", top: "10%", containLabel: true },
    xAxis: {
      type: "category",
      data: dates,
      axisLabel: { rotate: 30, fontSize: 10 }
    },
    yAxis: {
      type: "value",
      min: 0,
      max: 100,
      axisLabel: { formatter: (v: number) => v + "%" }
    },
    series: [{
      type: "line",
      data: accuracies,
      smooth: true,
      symbol: "circle",
      symbolSize: 6,
      lineStyle: { width: 2, color: green },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: hexToRgba(green, 0.3) },
          { offset: 1, color: hexToRgba(green, 0.05) }
        ])
      },
      markLine: {
        data: [{ type: "average", name: "平均值" }],
        lineStyle: { color: cssVar('--text-muted', '#909399'), type: "dashed" },
        label: { formatter: "平均: {c}%" }
      }
    }]
  });
}

// ---- 特征自动搜索逻辑 ----

const autoSearchSymbol = ref("BTCUSDT");
const autoSearchCollapse = ref(["featurePool", "searchParams"]);
const autoSearchRunning = ref(false);
const autoSearchDone = ref(false);
const autoSearchSearchId = ref("");
const autoSearchCompleted = ref(0);
const autoSearchTotal = ref(0);
const autoSearchProgressPct = computed(() =>
  autoSearchTotal.value > 0 ? Math.round((autoSearchCompleted.value / autoSearchTotal.value) * 100) : 0
);
const autoSearchBestSoFar = ref<AutoSearchCombo | null>(null);
const autoSearchError = ref("");
const autoSearchResults = ref<AutoSearchCombo[]>([]);
const autoSearchPollTimer = ref<ReturnType<typeof setInterval> | null>(null);

const autoSearchMaxCombos = ref(500);
const autoSearchMinFeatures = ref(2);
const autoSearchMaxFeatures = ref(6);
const autoSearchNumTrees = ref(500);
const autoSearchMaxDepth = ref(4);
const autoSearchMinSamples = ref(40);
const autoSearchWeightF1 = ref(0.5);
const autoSearchWeightPrecision = ref(0.3);
const autoSearchWeightSignal = ref(0.2);
const autoSearchTotalBars = ref(2000);
const autoSearchTestRatio = ref(0.2);
const autoSearchThresholdStart = ref(0.5);
const autoSearchThresholdEnd = ref(0.9);
const autoSearchThresholdStep = ref(0.05);

const canStartAutoSearch = computed(() =>
  featurePoolDefs.value.some(d => d.checked && d.selectedParams.length > 0)
);

const defaultFeaturePoolDefs: FeaturePoolDef[] = [
  { name: "RSI", label: "RSI", checked: true, paramOptions: [7, 14, 21, 28], selectedParams: [14] },
  { name: "MACD", label: "MACD", checked: true, paramOptions: [[5,13,5], [12,26,9], [19,39,13]], selectedParams: [[12,26,9]] },
  { name: "EMADiff", label: "EMADiff", checked: true, paramOptions: [[5,20], [10,30], [20,60]], selectedParams: [[5,20], [10,30]] },
  { name: "SMA", label: "SMA", checked: false, paramOptions: [5, 10, 20, 60], selectedParams: [] },
  { name: "ATR", label: "ATR", checked: false, paramOptions: [7, 14, 21], selectedParams: [] },
  { name: "Bollinger_%B", label: "Bollinger_%B", checked: false, paramOptions: [10], selectedParams: [] },
  { name: "VolumeRatio", label: "VolumeRatio", checked: false, paramOptions: [5, 10], selectedParams: [] },
  { name: "LinearRegSlope", label: "LinearRegSlope", checked: false, paramOptions: [5, 10, 20], selectedParams: [] },
];
const featurePoolDefs = ref<FeaturePoolDef[]>(JSON.parse(JSON.stringify(defaultFeaturePoolDefs)));

function selectAllFeatures() {
  featurePoolDefs.value.forEach(d => {
    d.checked = true;
    d.selectedParams = [...d.paramOptions];
  });
}

function deselectAllFeatures() {
  featurePoolDefs.value.forEach(d => { d.checked = false; d.selectedParams = []; });
}

function resetFeaturePool() {
  featurePoolDefs.value = JSON.parse(JSON.stringify(defaultFeaturePoolDefs));
}

async function handleStartAutoSearch() {
  const pool: Record<string, any[]> = {};
  for (const def of featurePoolDefs.value) {
    if (def.checked && def.selectedParams.length > 0) {
      pool[def.name] = def.selectedParams;
    }
  }

  const req = {
    symbol: autoSearchSymbol.value,
    maxCombinations: autoSearchMaxCombos.value,
    minFeatures: autoSearchMinFeatures.value,
    maxFeatures: autoSearchMaxFeatures.value,
    featurePool: pool,
    modelParams: {
      numTrees: autoSearchNumTrees.value,
      maxDepth: autoSearchMaxDepth.value,
      minSamples: autoSearchMinSamples.value,
    },
    weights: {
      f1: autoSearchWeightF1.value,
      precision: autoSearchWeightPrecision.value,
      signalCount: autoSearchWeightSignal.value,
    },
    dataSplit: {
      totalBars: autoSearchTotalBars.value,
      testRatio: autoSearchTestRatio.value,
    },
    thresholdScan: {
      start: autoSearchThresholdStart.value,
      end: autoSearchThresholdEnd.value,
      step: autoSearchThresholdStep.value,
    },
  };

  autoSearchRunning.value = true;
  autoSearchDone.value = false;
  autoSearchCompleted.value = 0;
  autoSearchTotal.value = 0;
  autoSearchBestSoFar.value = null;
  autoSearchError.value = "";
  autoSearchResults.value = [];

  try {
    const res = await startAutoSearch(req);
    const data = res.data || res;
    autoSearchSearchId.value = data.searchId || data;
    autoSearchTotal.value = data.totalCombinations || autoSearchMaxCombos.value;
    startAutoSearchPolling();
  } catch (e: any) {
    autoSearchRunning.value = false;
    autoSearchError.value = e?.message || "启动搜索失败";
  }
}

function startAutoSearchPolling() {
  stopAutoSearchPolling();
  autoSearchPollTimer.value = setInterval(async () => {
    try {
      const res = await getAutoSearchProgress(autoSearchSearchId.value);
      const data = res.data || res;
      autoSearchCompleted.value = data.completedCombinations || 0;
      autoSearchTotal.value = data.totalCombinations || autoSearchTotal.value;
      if (data.bestSoFar) {
        autoSearchBestSoFar.value = typeof data.bestSoFar === 'string' ? JSON.parse(data.bestSoFar) : data.bestSoFar;
      }
      if (data.status === "DONE" || data.status === "FAILED") {
        stopAutoSearchPolling();
        autoSearchRunning.value = false;
        autoSearchDone.value = true;
        if (data.status === "FAILED") {
          autoSearchError.value = data.errorMsg || "搜索失败";
        }
        if (data.finalTop20) {
          const top20 = typeof data.finalTop20 === 'string' ? JSON.parse(data.finalTop20) : data.finalTop20;
          autoSearchResults.value = Array.isArray(top20) ? top20 : [];
        }
      }
    } catch {
      // ignore polling errors
    }
  }, 2000);
}

function stopAutoSearchPolling() {
  if (autoSearchPollTimer.value) {
    clearInterval(autoSearchPollTimer.value);
    autoSearchPollTimer.value = null;
  }
}

async function handleStopAutoSearch() {
  if (!autoSearchSearchId.value) return;
  try {
    await stopAutoSearch(autoSearchSearchId.value);
    ElMessage.info("已发送停止指令");
  } catch {
    ElMessage.error("停止搜索失败");
  }
}

function handleAutoSearchSort({ prop, order }: { prop?: string; order?: string }) {
  if (!prop || !order || autoSearchResults.value.length === 0) return;
  const sorted = [...autoSearchResults.value];
  sorted.sort((a: any, b: any) => {
    const va = a[prop] ?? 0;
    const vb = b[prop] ?? 0;
    return order === 'ascending' ? va - vb : vb - va;
  });
  autoSearchResults.value = sorted;
}

function autoSearchRowClass({ row }: { row: AutoSearchCombo }) {
  return row.score >= 0.6 ? 'score-highlight-row' : '';
}

function exportAutoSearchCSV() {
  const headers = ["排名", "特征组合", "特征数", "最佳阈值", "精确率", "召回率", "F1", "得分", "信号数"];
  const rows = autoSearchResults.value.map((r, i) => [
    i + 1,
    `"${r.features.join(' ')}"`,
    r.featureCount,
    r.bestThreshold.toFixed(2),
    (r.precision * 100).toFixed(1) + '%',
    (r.recall * 100).toFixed(1) + '%',
    (r.f1 * 100).toFixed(1) + '%',
    r.score.toFixed(4),
    r.signalCount
  ]);
  const csv = [headers.join(','), ...rows.map(r => r.join(','))].join('\n');
  const blob = new Blob(["\uFEFF" + csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `auto-search-${autoSearchSymbol.value}-${dayjs().format('YYYYMMDD-HHmmss')}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

const autoSearchDetailVisible = ref(false);
const autoSearchDetailData = ref<AutoSearchCombo | null>(null);
const autoSearchCmRef = ref<HTMLElement | null>(null);
const autoSearchFiRef = ref<HTMLElement | null>(null);
let autoSearchCmChart: echarts.ECharts | null = null;
let autoSearchFiChart: echarts.ECharts | null = null;

function showAutoSearchDetail(row: AutoSearchCombo) {
  autoSearchDetailData.value = row;
  autoSearchDetailVisible.value = true;
  nextTick(() => {
    if (autoSearchCmRef.value) renderAutoSearchCmChart();
    if (autoSearchFiRef.value) renderAutoSearchFiChart();
  });
}

function renderAutoSearchCmChart() {
  if (autoSearchCmChart) autoSearchCmChart.dispose();
  if (!autoSearchCmRef.value) return;
  autoSearchCmChart = echarts.init(autoSearchCmRef.value, isDarkTheme.value ? 'dark' : undefined);

  const cm = autoSearchDetailData.value?.confusionMatrix;
  if (!cm) {
    autoSearchCmChart.setOption({
      title: { text: "暂无数据", left: "center", top: "center", textStyle: { fontSize: 14, color: cssVar('--text-muted', '#909399') } }
    });
    return;
  }

  const total = cm.tp + cm.fp + cm.fn + cm.tn || 1;
  autoSearchCmChart.setOption({
    tooltip: {
      formatter: (params: any) => {
        const d = params.data;
        return `${d.name}<br/>数量: ${d.value}<br/>占比: ${((d.value / total) * 100).toFixed(1)}%`;
      }
    },
    series: [{
      type: "pie",
      radius: ["40%", "70%"],
      center: ["50%", "55%"],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: "#fff", borderWidth: 2 },
      label: {
        show: true,
        formatter: (p: any) => `${p.name}\n${p.percent.toFixed(1)}%`,
        fontSize: 11
      },
      data: [
        { value: cm.tp, name: "TP 正确预测上涨" },
        { value: cm.fp, name: "FP 误报上涨" },
        { value: cm.fn, name: "FN 漏报上涨" },
        { value: cm.tn, name: "TN 正确预测下跌" }
      ],
      color: ['#67c23a', '#e6a23c', '#f56c6c', '#409eff']
    }]
  });
}

function renderAutoSearchFiChart() {
  if (autoSearchFiChart) autoSearchFiChart.dispose();
  if (!autoSearchFiRef.value) return;
  autoSearchFiChart = echarts.init(autoSearchFiRef.value, isDarkTheme.value ? 'dark' : undefined);

  const fi = autoSearchDetailData.value?.featureImportance;
  if (!fi || Object.keys(fi).length === 0) {
    autoSearchFiChart.setOption({
      title: { text: "暂无数据", left: "center", top: "center", textStyle: { fontSize: 14, color: cssVar('--text-muted', '#909399') } }
    });
    return;
  }

  const sorted = Object.entries(fi).sort((a, b) => b[1] - a[1]);
  autoSearchFiChart.setOption({
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: any) => `${params[0].name}: ${(params[0].value * 100).toFixed(2)}%`
    },
    grid: { left: "3%", right: "10%", bottom: "10%", top: "10%", containLabel: true },
    xAxis: { type: "value", axisLabel: { formatter: (v: number) => (v * 100).toFixed(0) + "%" } },
    yAxis: { type: "category", data: sorted.map(([k]) => k).reverse(), axisLabel: { fontSize: 12 } },
    series: [{
      type: "bar",
      data: sorted.reverse().map(([, v]) => v),
      barWidth: "60%",
      label: {
        show: true,
        position: "right",
        formatter: (p: any) => (p.value * 100).toFixed(1) + "%",
        fontSize: 11,
        color: cssVar('--text-muted', '#909399')
      },
      itemStyle: { color: cssVar('--accent-blue', '#409eff') }
    }]
  });
}

function onAutoSearchDetailClosed() {
  if (autoSearchCmChart) { autoSearchCmChart.dispose(); autoSearchCmChart = null; }
  if (autoSearchFiChart) { autoSearchFiChart.dispose(); autoSearchFiChart = null; }
}

async function handleApplyFeatureCombo(row: AutoSearchCombo) {
  try {
    await ElMessageBox.confirm(
      `确认应用特征组合「${row.features.join(', ')}」?\n下次训练将使用此特征组合。`,
      "应用确认",
      { confirmButtonText: "确认", cancelButtonText: "取消", type: "info" }
    );
    await applyFeatureCombination({ features: row.features, symbol: autoSearchSymbol.value });
    ElMessage.success("已应用，下次训练将使用此特征组合");
  } catch {
    // cancelled
  }
}

onUnmounted(() => {
  stopAutoSearchPolling();
});

loadModels();
</script>

<style scoped>
.quant-ml {
  padding: 24px;
  height: 100%;
}

.ml-header {
  margin-bottom: 24px;
}

.header-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--primary-text, #303133);
}

.header-subtitle {
  color: var(--secondary-text, #909399);
  font-size: 13px;
  margin-top: 4px;
  display: block;
}

.icon-success { color: var(--accent-green); }
.icon-danger { color: var(--accent-red); }
.icon-loading { color: var(--accent-blue); }

.ml-tabs {
  min-height: calc(100vh - 180px);
}

.tab-body {
  min-height: 400px;
}

.model-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.predict-metric {
  text-align: center;
  padding: 8px;
}

.metric-label {
  font-size: 13px;
  color: var(--secondary-text, #909399);
  margin-bottom: 4px;
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
}

.metric-value.up, .up { color: var(--accent-green); }
.metric-value.down, .down { color: var(--accent-red); }

.probability-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
}

.prob-label {
  font-size: 13px;
  color: var(--secondary-text, #909399);
  white-space: nowrap;
  min-width: 90px;
}

.prob-track {
  flex: 1;
  height: 10px;
  background: var(--tertiary-bg);
  border-radius: 5px;
  overflow: hidden;
  position: relative;
}

.prob-track::before {
  content: "";
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--border-color);
  transform: translateX(-50%);
}

.prob-fill {
  height: 100%;
  border-radius: 5px;
  background: linear-gradient(90deg, var(--accent-red) 0%, var(--accent-orange) 50%, var(--accent-green) 100%);
  transition: width 0.5s ease;
}

.feature-controls {
  margin-bottom: 24px;
}

.features-grid {
  margin-top: 8px;
}

.feature-card {
  text-align: center;
  border-left: 3px solid var(--border-color);
}

.feature-card.feature-up { border-left-color: var(--accent-green); }
.feature-card.feature-down { border-left-color: var(--accent-red); }

.feature-name {
  font-size: 13px;
  color: var(--secondary-text, #909399);
  margin-bottom: 4px;
}

.feature-value {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 8px;
}

.feature-bar-bg {
  height: 4px;
  background: var(--border-color, #f0f0f0);
  border-radius: 2px;
  overflow: hidden;
}

.feature-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
}

.chart-card {
  border: 1px solid var(--border-color, #ebeef5);
}

.chart-card:deep(.el-card__header) {
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
}

.feature-pool-row {
  display: flex;
  align-items: flex-start;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.feature-pool-row:last-child {
  border-bottom: none;
}
:deep(.score-highlight-row > td) {
  background-color: rgba(103, 194, 58, 0.08) !important;
}
.icon-loading {
  animation: rotating 1.4s linear infinite;
  margin-right: 4px;
}
.icon-success {
  color: #67c23a;
  margin-right: 4px;
}
@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
