<template>
  <div class="real-time-trading">
    <!-- 顶部状态栏 - 按最新图示修正排版 -->
    <div class="trading-header-v3">
      <div class="header-left-box">
        <div class="logo-area-v3">
          <div class="logo-svg-v3">
            <!-- 猞猁 (Lynx) 形象 LOGO - 实时交易适配版 -->
            <svg width="28" height="28" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="32" height="32" rx="8" fill="url(#lynx-grad-v3)" />
              <path d="M16 6L10 14V24L16 28L22 24V14L16 6Z" fill="white" fill-opacity="0.1"/>
              <path d="M10 14L7 4L13 11" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M22 14L25 4L19 11" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M12 18H14M18 18H20" stroke="#00D2FF" stroke-width="2" stroke-linecap="round"/>
              <path d="M16 22V28M10 24H22" stroke="white" stroke-width="1" stroke-opacity="0.5"/>
              <defs>
                <linearGradient id="lynx-grad-v3" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
                  <stop stop-color="#409EFF" />
                  <stop offset="1" stop-color="#00D2FF" />
                </linearGradient>
              </defs>
            </svg>
          </div>
          <span class="logo-text-v3">LYNXAI</span>
          <span class="version-text-v3"></span>
        </div>
        <div class="status-line-v3">
          <span class="status-dot-v3"></span>
          <span class="status-text-v3">实时交易 | 多交易所统一交易界面</span>
          <span class="tick-text-v3"></span>
        </div>
      </div>
      
      <div class="header-right-v3">
        <div class="controls-row-v3">
          <el-button class="settings-gear-v3" text @click="openAgentConfig">
            <el-icon><Setting /></el-icon>
          </el-button>
          <div class="tag-group-v3">
            <span class="custom-tag-v3 green"><span class="t-icon">💰</span>实盘</span>
            <span class="custom-tag-v3 blue"><span class="t-icon">📊</span>加密货币, 美股</span>
            <span class="custom-tag-v3 purple"><span class="t-icon">🏘️🌟🌙</span>21 模型 (3)</span>
            <span class="custom-tag-v3 teal"><span class="t-icon">🔗</span>交易所</span>
          </div>
          <div class="stats-row-v3">
            <div class="stat-item-v3">
              <span class="s-label">交易</span>
              <span class="s-value">{{ stats.trades }}</span>
            </div>
            <div class="stat-item-v3">
              <span class="s-label">账户</span>
              <span class="s-value">${{ stats.account }}</span>
            </div>
            <div class="stat-item-v3 highlight">
              <span class="s-label">实盘盈亏</span>
              <span class="s-value">${{ stats.pnl }}</span>
            </div>
            <div class="stat-item-v3 target">
              <div class="target-top">
                <span class="s-label"><span class="t-icon">🎯</span>日目标</span>
                <span class="s-value">${{ stats.targetCurrent }} / ${{ stats.targetTotal }}</span>
              </div>
              <div class="target-bar-v3">
                <div class="bar-fill" :style="{ width: (stats.targetCurrent / stats.targetTotal * 100) + '%' }"></div>
              </div>
              <div class="target-bot">
                {{ selectedBotId ? ('机器人: ' + ((selectedBot && (selectedBot.botName || selectedBot.botId || selectedBot.id)) || selectedBotId)) : '账户模式' }}
                <span v-if="botDailyTargetLabel"> · {{ botDailyTargetLabel }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 跑马灯 Ticker -->
    <div class="trading-ticker">
      <div class="ticker-content">
        <div v-for="item in tickerItems" :key="item.symbol" class="ticker-item">
          <span class="ticker-symbol">{{ item.displaySymbol }}</span>
          <span class="ticker-price">${{ item.price }}</span>
          <span class="ticker-change" :class="item.change >= 0 ? 'up' : 'down'">
            <el-icon v-if="item.change >= 0"><CaretTop /></el-icon>
            <el-icon v-else><CaretBottom /></el-icon>
            {{ Math.abs(item.change) }}%
          </span>
        </div>
      </div>
    </div>

    <el-row :gutter="15" class="main-layout">
      <!-- 左侧面板: 市场分析 + 活跃策略 -->
      <el-col :span="6">
        <!-- 市场分析 -->
        <el-card class="market-analyze-card glass-effect dark-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Monitor /></el-icon>
                市场分析
              </span>
              <div class="header-ops">
                <el-button size="small" class="op-btn">ENGINE READY</el-button>
                <el-select v-model="marketAnalysisInterval" size="small" style="width: 88px">
                  <el-option label="1m" value="1m" />
                  <el-option label="3m" value="3m" />
                  <el-option label="5m" value="5m" />
                  <el-option label="15m" value="15m" />
                  <el-option label="1h" value="1h" />
                </el-select>
                <el-button size="small" class="op-btn" @click="scanMarketAnalysis">扫描</el-button>
              </div>
            </div>
          </template>
          <div class="analyze-body">
            <div class="sentiment-section">
              <div class="fear-greed-gauge">
                <!-- 这里可以用一个简单的仪表盘 -->
                <div class="gauge-value" :style="{ color: sentimentColor }">{{ sentimentScore }}</div>
                <div class="gauge-label">{{ sentimentLabel }}</div>
              </div>
              <div class="gauge-track">
                <div class="gauge-pointer" :style="{ left: sentimentScore + '%' }"></div>
              </div>
              <div v-if="marketAnalysis" class="analysis-mini">
                <div class="analysis-mini-row">
                  <span class="analysis-mini-label">标的</span>
                  <span class="analysis-mini-value">{{ marketAnalysis.symbol }}</span>
                </div>
                <div class="analysis-mini-row">
                  <span class="analysis-mini-label">周期</span>
                  <span class="analysis-mini-value">{{ marketAnalysisInterval }}</span>
                </div>
                <div class="analysis-mini-row" v-if="marketAnalysis.supports && marketAnalysis.supports.length">
                  <span class="analysis-mini-label">支撑</span>
                  <span class="analysis-mini-value">{{ marketAnalysis.supports.join(" / ") }}</span>
                </div>
                <div class="analysis-mini-row" v-if="marketAnalysis.resistances && marketAnalysis.resistances.length">
                  <span class="analysis-mini-label">压力</span>
                  <span class="analysis-mini-value">{{ marketAnalysis.resistances.join(" / ") }}</span>
                </div>
                <div class="analysis-mini-tags" v-if="marketAnalysis.tags && marketAnalysis.tags.length">
                  <el-tag v-for="t in marketAnalysis.tags" :key="t" size="small" effect="dark" class="analysis-tag">{{ t }}</el-tag>
                </div>
              </div>
              <div v-else class="analysis-empty">
                暂无市场分析数据，点击“扫描”获取
              </div>

              <div class="analysis-ai">
                <div class="analysis-ai-title">
                  <span>AI 研报总结</span>
                  <el-tag v-if="aiMarketSummaryLoading" size="small" effect="dark">生成中</el-tag>
                </div>
                <div v-if="aiMarketSummaryError" class="analysis-ai-error">{{ aiMarketSummaryError }}</div>
                <div v-else-if="aiMarketSummaryText" class="analysis-ai-body markdown-body" v-html="renderMarkdown(aiMarketSummaryText)"></div>
                <div v-else class="analysis-ai-empty">点击“扫描”后生成</div>
              </div>
            </div>
            
            <div class="symbol-signals">
              <div class="signal-header">自选币信号</div>
              <el-empty v-if="!symbolSignals || symbolSignals.length === 0" description="暂无信号（请先导入/采集行情数据）" />
              <template v-else>
                <div v-for="s in symbolSignals" :key="s.symbol" class="signal-item" @click="onWatchSymbolClick(s.symbol)">
                  <span class="s-dot" :class="s.trend"></span>
                  <span class="s-name">{{ (s.symbol.split('-')[0] || s.symbol) }}</span>
                  <span class="s-price">${{ s.price }}</span>
                  <span class="s-label" :class="s.trend">{{ s.label }}</span>
                  <div class="s-bar-wrap">
                    <div class="s-bar" :style="{ width: s.strength + '%', backgroundColor: s.trend === 'Bullish' ? '#00ff00' : (s.trend === 'Bearish' ? '#ff4d4d' : '#808080') }"></div>
                  </div>
                  <span v-if="s.change != null" class="s-change" :class="s.change < 0 ? 'neg' : 'pos'">
                    {{ s.change >= 0 ? '+' : '' }}{{ s.change.toFixed(2) }}%
                  </span>
                </div>
              </template>
            </div>
          </div>
        </el-card>

        <el-card class="strategy-list-card glass-effect dark-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Operation /></el-icon>
                活动机器人
              </span>
              <div class="header-ops">
                <el-button size="small" class="op-btn" @click="loadBots">刷新</el-button>
              </div>
            </div>
          </template>
          <el-empty v-if="activeBotCards.length === 0" description="暂无活动机器人" />
          <div v-else class="bot-list">
            <div v-for="b in activeBotCards" :key="b.botKey" class="bot-item">
              <div class="bot-top">
                <div class="bot-name">
                  <el-tag v-if="b.isPinned" size="small" effect="dark" type="success">当前</el-tag>
                  <span class="bot-name-text">{{ b.name }}</span>
                </div>
                <el-tag size="small" effect="dark" :type="b.statusType">{{ b.statusText }}</el-tag>
              </div>
              <div class="bot-meta">
                <span class="bot-pair">{{ b.tradingPair || "-" }}</span>
                <span class="bot-last">最近：{{ b.lastActionText }}</span>
              </div>
              <div class="bot-pnl">
                <span class="bot-pnl-label">今日PnL</span>
                <span class="bot-pnl-value" :class="b.dailyPnl < 0 ? 'neg' : 'pos'">{{ b.dailyPnlText }}</span>
                <span class="bot-target">{{ b.targetText }}</span>
              </div>
              <div class="bot-progress">
                <el-progress :percentage="b.targetPercent" :stroke-width="8" :show-text="false" />
              </div>
              <div class="bot-actions">
                <el-button size="small" @click="enterBot(b.raw)">进入</el-button>
                <el-button size="small" :type="b.toggleBtnType" :disabled="b.toggleDisabled" @click="toggleBot(b.raw)">
                  {{ b.toggleBtnText }}
                </el-button>
                <el-button size="small" @click="openBotLogs(b.raw)">日志</el-button>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 中间面板: 智能体通讯 (AI Chat) -->
      <el-col :span="12">
        <el-card class="ai-communication-card glass-effect dark-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><ChatDotRound /></el-icon>
                小灵宝
              </span>
              <el-tag size="small" type="success" effect="dark">SECURE</el-tag>
            </div>
          </template>
          <div class="chat-container-v2">
            <div ref="chatHistoryEl" class="chat-history" @scroll="onChatHistoryScroll">
              <div class="history-divider">历史会话恢复</div>
              <div v-for="(msg, idx) in chatMessages" :key="msg.id" :class="['chat-msg-v2', msg.role]">
                <div class="msg-role-label">
                  <img v-if="msg.role === 'assistant'" :src="xiaolingbaoLogo" class="msg-avatar" />
                  <span v-else class="msg-avatar user-avatar">我</span>
                  <span class="msg-role-text">{{ roleLabel(msg.role) }}</span>
                </div>
                <div class="msg-content-box">
                  <div v-if="msg.type === 'strategy'" class="strategy-msg">
                    <div class="strategy-header">AI STRATEGY</div>
                    <div class="strategy-content markdown-body" v-html="renderMarkdown(renderChatMessageContent(msg))"></div>
                  </div>
                  <div v-else class="text-msg">
                    <div v-if="msg.role === 'assistant' && toolPreviewFor(msg.id)" class="tool-preview-card">
                      <div class="tool-preview-title">工具结果</div>
                      <div class="tool-preview-row">
                        <span class="tool-preview-label">previewId</span>
                        <span class="tool-preview-value">{{ toolPreviewFor(msg.id)?.previewId }}</span>
                      </div>
                      <div v-if="toolPreviewFor(msg.id)?.planUuid" class="tool-preview-row">
                        <span class="tool-preview-label">planUuid</span>
                        <span class="tool-preview-value">{{ toolPreviewFor(msg.id)?.planUuid }}</span>
                      </div>
                      <div v-if="toolPreviewFor(msg.id)?.message" class="tool-preview-row">
                        <span class="tool-preview-label">message</span>
                        <span class="tool-preview-value">{{ toolPreviewFor(msg.id)?.message }}</span>
                      </div>
                      <div v-if="(toolPreviewFor(msg.id)?.warnings || []).length" class="tool-preview-warnings">
                        <div class="tool-preview-warn-title">warnings</div>
                        <div v-for="(w, idx) in toolPreviewFor(msg.id)?.warnings" :key="idx" class="tool-preview-warn-item">
                          {{ w }}
                        </div>
                      </div>
                      <div class="tool-preview-actions">
                        <el-button
                          type="primary"
                          size="small"
                          :disabled="isChatting || (toolPreviewFor(msg.id)?.tool === 'quant_trade_plan_confirm' && !toolPreviewFor(msg.id)?.planUuid)"
                          @click="confirmToolPreview(toolPreviewFor(msg.id)!)"
                        >
                          Confirm
                        </el-button>
                      </div>
                    </div>
                    <div
                      v-if="msg.role === 'assistant' && (tradePlanDraftsFor(msg.id).length || isLiveAdviceAssistantMessage(msg, idx))"
                      class="assistant-ops"
                    >
                      <el-button
                        v-if="tradePlanDraftsFor(msg.id).length === 1"
                        size="small"
                        :disabled="isChatting"
                        @click="openCreateTradePlanFromMessage(msg, 0)"
                      >
                        生成交易计划
                      </el-button>
                      <el-button
                        v-else-if="isLiveAdviceAssistantMessage(msg, idx)"
                        size="small"
                        :disabled="isChatting"
                        @click="openCreateTradePlanFromAssistantText(msg)"
                      >
                        新建交易计划
                      </el-button>
                      <el-dropdown
                        v-else
                        trigger="click"
                        @command="(cmd) => onTradePlanDraftCommand(msg, cmd)"
                      >
                        <el-button size="small" :disabled="isChatting">
                          计划草案({{ tradePlanDraftsFor(msg.id).length }})
                          <el-icon class="el-icon--right"><CaretBottom /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item
                              v-for="(d, idx) in tradePlanDraftsFor(msg.id)"
                              :key="idx"
                              :command="{ type: 'single', idx }"
                            >
                              {{ tradePlanDraftLabel(d, idx) }}
                            </el-dropdown-item>
                            <el-dropdown-item divided :command="{ type: 'batch' }">
                              批量生成全部
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                    <div class="markdown-body" v-html="renderMarkdown(renderChatMessageContent(msg))"></div>
                  </div>
                </div>
              </div>
            </div>
            <div class="chat-input-shell">
              <div class="chat-input-top">
                <input type="text" v-model="userInput" @keyup.enter="sendMessage" :placeholder="chatInputPlaceholder" />
                <el-button v-if="isChatting" size="small" class="stop-btn" @click="stopChat">Stop</el-button>
                <div class="send-btn" :class="{ disabled: isChatting }" @click="sendMessage">
                  <el-icon class="send-btn-icon"><Promotion /></el-icon>
                </div>
              </div>
              <div class="biz-pill-bar">
                <span
                  v-for="p in bizPills"
                  :key="p.key"
                  class="biz-pill"
                  :class="{ active: activeBizPill === p.key, 'advice-pill': p.key === 'live_advice', disabled: isChatting }"
                  @click="onBizPillClick(p.key)"
                >
                  {{ p.label }}
                </span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧面板: AI 指挥官 + 交易计划 + 事件日志 -->
      <el-col :span="6">
        <!-- AI 指挥官 -->
        <el-card class="commander-card glass-effect dark-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Cpu /></el-icon>
                AI 指挥官
              </span>
              <div class="commander-status">
                <span class="status-dot" :class="commanderOnline ? 'online' : 'offline'"></span>
                {{ commanderOnline ? "活跃" : "未启动" }}
              </div>
            </div>
          </template>
          <div class="commander-body">
            <div class="progress-section">
              <div class="progress-labels">
                <span>{{ commanderTargetLabel }}</span>
                <span>{{ commanderTodayLabel }}</span>
                <span>{{ commanderPercentLabel }}</span>
              </div>
              <el-progress :percentage="commanderPercent" :show-text="false" color="#409eff" />
              <div class="next-run">{{ commanderRefreshLabel }}</div>
            </div>
            <div class="state-description">{{ commanderStateText }}</div>
            <div class="commander-stats">
              <span>机器人 {{ activeBotCards.length }}</span>
              <span>待执行 {{ pendingPlanCount }}</span>
            </div>
          </div>
        </el-card>

        <!-- 交易计划 -->
        <el-card ref="tradePlanCardRef" class="plan-card glass-effect dark-card">
          <template #header>
            <div class="card-header">
              <div class="card-title-group">
                <span class="card-title">
                  <el-icon><Calendar /></el-icon>
                  交易计划
                </span>
                <span class="count-badge">{{ pendingPlanCount }}</span>
              </div>
              <div class="header-ops">
                <span :class="{ active: tradePlanFilter === 'pending' }" @click="tradePlanFilter = 'pending'">待执行</span>
                <span :class="{ active: tradePlanFilter === 'all' }" @click="tradePlanFilter = 'all'">全部</span>
                <span :class="{ active: tradePlanFilter === 'executed' }" @click="tradePlanFilter = 'executed'">已执行</span>
                <span :class="{ active: tradePlanFilter === 'failed' }" @click="tradePlanFilter = 'failed'">失败</span>
                <el-button size="small" class="op-btn" @click="loadTradePlansFromDb()">刷新</el-button>
                <el-button size="small" class="op-btn" @click="openCreateTradePlan()">新建</el-button>
              </div>
            </div>
          </template>
          <div class="plan-list">
            <div v-if="!displayedTradingPlans.length" class="empty-list">暂无交易计划</div>
            <div v-for="p in displayedTradingPlans" :key="p.id" class="plan-item">
              <div class="p-header">
                <span class="p-name">
                  <el-icon><Pointer /></el-icon>
                  {{ tradePlanTitle(p) }}
                </span>
                <span class="p-time">{{ p.time }}</span>
              </div>
              <div class="p-content">{{ p.summary }}</div>
              <div class="p-actions">
                <el-tag v-if="p.status === 'pending'" size="small" type="warning" effect="dark">PENDING</el-tag>
                <el-tag v-else-if="p.status === 'executed'" size="small" type="success" effect="dark">EXECUTED</el-tag>
                <el-tag v-else-if="p.status === 'failed'" size="small" type="danger" effect="dark">FAILED</el-tag>
                <div class="p-action-buttons">
                  <el-button size="small" :disabled="isChatting" @click="openTradePlanDetail(p)">详情</el-button>
                    <el-button
                      v-if="p.status === 'pending'"
                      size="small"
                      type="primary"
                      :disabled="isChatting"
                      @click="previewTradePlan(p)"
                    >
                      {{ p.previewId ? "重新预检" : "预检" }}
                    </el-button>
                  <el-button
                    v-if="p.status === 'pending' && p.previewId"
                    type="primary"
                    size="small"
                    :disabled="isChatting"
                    @click="openTradePlanConfirm(p)"
                  >
                    Confirm
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 事件日志 -->
        <el-card class="event-log-card glass-effect dark-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Document /></el-icon>
                事件日志
              </span>
              <span class="count-badge">9141</span>
              <div class="header-filters">
                <span class="active">全部</span>
                <span>交易</span>
                <span>信号</span>
                <span>预警</span>
                <span>系统</span>
              </div>
            </div>
          </template>
          <div class="event-list">
            <div v-for="e in eventLogs" :key="e.id" class="event-item">
              <div class="e-header">
                <span class="e-type" :class="e.type">{{ e.typeText }}</span>
                <span class="e-time">{{ e.time }}</span>
              </div>
              <div class="e-content">{{ e.content }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog
      v-model="agentConfigOpen"
      title="设置"
      width="980px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-tabs v-model="activeAgentConfigTab">
        <el-tab-pane label="智能体" name="llm">
          <div class="llm-config-wrap">
            <div class="llm-active-row">
              <div class="llm-active-label">AI 选择列表</div>
              <el-select v-model="activeModelKey" style="width: 100%" filterable>
                <el-option
                  v-for="opt in activeModelOptions"
                  :key="opt.key"
                  :label="opt.label"
                  :value="opt.key"
                />
              </el-select>
            </div>

            <div class="llm-section-title">免费 - 本地部署</div>
            <div class="llm-provider-card" @click="toggleProviderEditor('ollama')">
              <div class="llm-provider-main">
                <div class="llm-provider-name">Ollama（本地）</div>
                <div class="llm-provider-desc">本地模型调用，无需 API Key</div>
              </div>
              <div class="llm-provider-status">
                <el-tag v-if="ollamaStatus.connected" type="success" effect="dark">已连接</el-tag>
                <el-tag v-else type="info" effect="dark">待检测</el-tag>
              </div>
            </div>
            <div v-if="expandedProvider === 'ollama'" class="llm-provider-editor">
              <el-form :model="providerConfigs.ollama" label-width="110px">
                <el-form-item label="模型">
                  <el-select
                    v-model="providerConfigs.ollama.model"
                    filterable
                    allow-create
                    default-first-option
                    style="width: 100%"
                    placeholder="选择或输入模型名，例如 qwen3:4b"
                  >
                    <el-option v-for="m in ollamaModels" :key="m" :label="m" :value="m" />
                  </el-select>
                </el-form-item>
                <el-form-item label="服务检测">
                  <div style="display: flex; gap: 10px; align-items: center; width: 100%">
                    <el-button :loading="ollamaStatus.checking" @click="testOllamaConnection">
                      测试连接
                    </el-button>
                    <el-button :loading="ollamaStatus.checking" @click="refreshOllamaModels">
                      刷新模型列表
                    </el-button>
                  </div>
                </el-form-item>
                <el-form-item label="请求地址">
                  <el-input v-model="ollamaRuntime.generatePath" placeholder="/api/generate 或 /ollama/api/generate" />
                </el-form-item>
                <el-form-item label="流式输出">
                  <el-switch v-model="ollamaRuntime.stream" />
                </el-form-item>
                <el-form-item label="超时(ms)">
                  <el-input-number v-model="ollamaRuntime.timeoutMs" :min="0" :max="1800000" :step="60000" style="width: 100%" />
                </el-form-item>
              </el-form>
            </div>

            <div class="llm-section-title">免费 - 云端 API</div>
            <div class="llm-subtitle">已配置</div>
            <div v-for="p in configuredCloudProviders" :key="p" class="llm-provider-group">
              <div class="llm-provider-card" @click="toggleProviderEditor(p)">
                <div class="llm-provider-main">
                  <div class="llm-provider-name">{{ providerLabel(p) }}</div>
                  <div class="llm-provider-desc">{{ providerDesc(p) }}</div>
                </div>
                <div class="llm-provider-status">
                  <el-tag type="success" effect="dark">已配置</el-tag>
                </div>
              </div>
              <div v-if="expandedProvider === p" class="llm-provider-editor">
                <el-form :model="providerConfigs[p]" label-width="110px">
                  <template v-if="p === 'deepseek'">
                    <el-form-item label="模型">
                      <el-select v-model="providerConfigs[p].model" style="width: 100%">
                        <el-option v-for="m in deepseekModelOptions" :key="m" :label="m" :value="m" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="API Base URL">
                      <el-input v-model="providerConfigs[p].apiBaseUrl" :placeholder="DEEPSEEK_API_BASE_URL" />
                    </el-form-item>
                  </template>
                  <template v-else-if="p === 'openclaw'">
                    <el-form-item label="模型">
                      <el-select v-model="providerConfigs[p].model" style="width: 100%">
                        <el-option v-for="m in openclawModelOptions" :key="m" :label="m" :value="m" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="Gateway URL">
                      <el-input v-model="providerConfigs[p].apiBaseUrl" :placeholder="OPENCLAW_DEFAULT_BASE_URL" />
                    </el-form-item>
                    <el-form-item label="Agent ID">
                      <el-input v-model="openclawAgentId" placeholder="main" />
                    </el-form-item>
                    <el-form-item label="Session Key">
                      <el-input v-model="openclawSessionKey" placeholder="可选，用于固定会话路由" />
                    </el-form-item>
                  </template>
                  <el-form-item :label="p === 'openclaw' ? 'Token' : 'API Key'">
                    <div style="display: flex; gap: 10px; align-items: center; width: 100%">
                      <el-input v-model="providerConfigs[p].apiKey" show-password placeholder="仅保存到后端（加密），不会回显" />
                      <el-tag v-if="providerConfigs[p].apiKeyConfigured" type="success" effect="dark">已配置</el-tag>
                    </div>
                  </el-form-item>
                  <el-form-item :label="p === 'openclaw' ? '高级配置(JSON)' : '额外配置'">
                    <el-input v-model="providerConfigs[p].extraConfig" type="textarea" :rows="3" placeholder="可选，JSON 字符串" />
                  </el-form-item>
                </el-form>
              </div>
            </div>

            <div class="llm-subtitle">未配置</div>
            <div v-for="p in unconfiguredCloudProviders" :key="p" class="llm-provider-group">
              <div class="llm-provider-card" @click="toggleProviderEditor(p)">
                <div class="llm-provider-main">
                  <div class="llm-provider-name">{{ providerLabel(p) }}</div>
                  <div class="llm-provider-desc">{{ providerDesc(p) }}</div>
                </div>
                <div class="llm-provider-status">
                  <el-tag type="info" effect="dark">去配置</el-tag>
                </div>
              </div>
              <div v-if="expandedProvider === p" class="llm-provider-editor">
                <el-form :model="providerConfigs[p]" label-width="110px">
                  <template v-if="p === 'deepseek'">
                    <el-form-item label="模型">
                      <el-select v-model="providerConfigs[p].model" style="width: 100%">
                        <el-option v-for="m in deepseekModelOptions" :key="m" :label="m" :value="m" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="API Base URL">
                      <el-input v-model="providerConfigs[p].apiBaseUrl" :placeholder="DEEPSEEK_API_BASE_URL" />
                    </el-form-item>
                  </template>
                  <template v-else-if="p === 'openclaw'">
                    <el-form-item label="模型">
                      <el-select v-model="providerConfigs[p].model" style="width: 100%">
                        <el-option v-for="m in openclawModelOptions" :key="m" :label="m" :value="m" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="Gateway URL">
                      <el-input v-model="providerConfigs[p].apiBaseUrl" :placeholder="OPENCLAW_DEFAULT_BASE_URL" />
                    </el-form-item>
                    <el-form-item label="Agent ID">
                      <el-input v-model="openclawAgentId" placeholder="main" />
                    </el-form-item>
                    <el-form-item label="Session Key">
                      <el-input v-model="openclawSessionKey" placeholder="可选，用于固定会话路由" />
                    </el-form-item>
                  </template>
                  <el-form-item :label="p === 'openclaw' ? 'Token' : 'API Key'">
                    <div style="display: flex; gap: 10px; align-items: center; width: 100%">
                      <el-input v-model="providerConfigs[p].apiKey" show-password placeholder="仅保存到后端（加密），不会回显" />
                      <el-tag v-if="providerConfigs[p].apiKeyConfigured" type="success" effect="dark">已配置</el-tag>
                    </div>
                  </el-form-item>
                  <el-form-item :label="p === 'openclaw' ? '高级配置(JSON)' : '额外配置'">
                    <el-input v-model="providerConfigs[p].extraConfig" type="textarea" :rows="3" placeholder="可选，JSON 字符串" />
                  </el-form-item>
                </el-form>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="机器人" name="bot">
          <div class="account-tab">
            <div class="account-toolbar">
              <el-button @click="loadBots">刷新</el-button>
              <el-button v-if="selectedBotId" @click="clearBotSelection">解除绑定</el-button>
            </div>
            <el-empty v-if="!bots || bots.length === 0" description="暂无机器人" />
            <div v-else class="account-grid">
              <div
                v-for="b in bots"
                :key="String(b.botId || b.id || '')"
                class="account-card"
                :class="{ 'is-active': String(selectedBotId) === String(b.botId || b.id || '') }"
                @click="onBotChange(String(b.botId || b.id || ''))"
              >
                <div class="account-title">
                  <span class="account-name">{{ b.botName || b.botId || b.id }}</span>
                  <el-tag v-if="String(selectedBotId) === String(b.botId || b.id || '')" type="success" effect="dark" size="small">当前</el-tag>
                </div>
                <div class="account-meta">
                  <span class="account-exchange">{{ b.tradingPair || '-' }}</span>
                  <span class="account-id">账户: {{ b.accountId || '-' }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="账户" name="account">
          <div class="account-tab">
            <div class="account-toolbar">
              <el-button @click="loadAccounts">刷新</el-button>
              <el-button type="primary" @click="showAddAccount = true">添加账户</el-button>
              <el-button v-if="selectedBotId" @click="clearBotSelection">解除机器人绑定</el-button>
            </div>
            <el-empty v-if="!accounts || accounts.length === 0" description="暂无账户" />
            <div v-else class="account-grid">
              <div
                v-for="a in accounts"
                :key="a.id"
                class="account-card"
                :class="{ 'is-active': selectedAccountId === a.id }"
                @click="onAccountChange(a.id)"
              >
                <div class="account-title">
                  <span class="account-name">{{ a.name || a.id }}</span>
                  <el-tag v-if="selectedAccountId === a.id" type="success" effect="dark" size="small">当前</el-tag>
                </div>
                <div class="account-meta">
                  <span class="account-exchange">{{ getExchangeName(a.exchange) }}</span>
                  <span class="account-id">ID: {{ a.id }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="agentConfigOpen = false">关闭</el-button>
        <el-button type="primary" @click="saveAgentConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加账户对话框 -->
    <el-dialog v-model="showAddAccount" title="添加交易账户" width="500px">
      <el-form :model="newAccount" label-width="120px">
        <el-form-item label="账户名称">
          <el-input v-model="newAccount.name" />
        </el-form-item>
        <el-form-item label="交易所">
          <el-select v-model="newAccount.exchange">
            <el-option label="币安" value="binance" />
            <el-option label="OKX" value="okx" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="newAccount.apiKey" show-password />
        </el-form-item>
        <el-form-item label="API Secret">
          <el-input v-model="newAccount.apiSecret" show-password />
        </el-form-item>
        <el-form-item v-if="newAccount.exchange === 'okx'" label="Passphrase">
          <el-input v-model="newAccount.passphrase" show-password />
        </el-form-item>
        <el-form-item label="测试网络">
          <el-switch v-model="newAccount.testnet" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddAccount = false">取消</el-button>
        <el-button type="primary" @click="addAccount">添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tradePlanDetailOpen" title="交易计划详情" width="720px" destroy-on-close>
      <div v-if="tradePlanDetailPlan" class="trade-plan-detail">
        <div class="trade-plan-detail-row">
          <span class="trade-plan-detail-label">名称</span>
          <span class="trade-plan-detail-value">{{ tradePlanDetailPlan.name }}</span>
        </div>
        <div class="trade-plan-detail-row">
          <span class="trade-plan-detail-label">状态</span>
          <span class="trade-plan-detail-value">{{ tradePlanDetailPlan.status }}</span>
        </div>
        <div class="trade-plan-detail-row" v-if="tradePlanDetailPlan.planContent?.symbol">
          <span class="trade-plan-detail-label">交易对</span>
          <span class="trade-plan-detail-value">{{ tradePlanDetailPlan.planContent?.symbol }}</span>
        </div>
        <div class="trade-plan-detail-row" v-if="tradePlanDetailPlan.planContent?.side">
          <span class="trade-plan-detail-label">方向</span>
          <span class="trade-plan-detail-value">{{ tradePlanDetailPlan.planContent?.side }}</span>
        </div>
        <div class="trade-plan-detail-row" v-if="tradePlanDetailPlan.planContent?.quantity != null">
          <span class="trade-plan-detail-label">数量</span>
          <span class="trade-plan-detail-value">{{ tradePlanDetailPlan.planContent?.quantity }}</span>
        </div>
        <div class="trade-plan-detail-row" v-if="tradePlanEntryText(tradePlanDetailPlan.planContent)">
          <span class="trade-plan-detail-label">入场</span>
          <span class="trade-plan-detail-value">{{ tradePlanEntryText(tradePlanDetailPlan.planContent) }}</span>
        </div>
        <div class="trade-plan-detail-row" v-if="tradePlanStopLossText(tradePlanDetailPlan.planContent)">
          <span class="trade-plan-detail-label">止损</span>
          <span class="trade-plan-detail-value">{{ tradePlanStopLossText(tradePlanDetailPlan.planContent) }}</span>
        </div>
        <div class="trade-plan-detail-row" v-if="tradePlanTakeProfitText(tradePlanDetailPlan.planContent)">
          <span class="trade-plan-detail-label">止盈</span>
          <span class="trade-plan-detail-value">{{ tradePlanTakeProfitText(tradePlanDetailPlan.planContent) }}</span>
        </div>
        <div class="trade-plan-detail-row" v-if="tradePlanDetailPlan.trace?.note">
          <span class="trade-plan-detail-label">备注</span>
          <span class="trade-plan-detail-value">{{ tradePlanDetailPlan.trace?.note }}</span>
        </div>
        <div class="trade-plan-detail-more">
          <el-collapse>
            <el-collapse-item title="更多信息" name="more">
              <div class="trade-plan-detail-row">
                <span class="trade-plan-detail-label">planUuid</span>
                <span class="trade-plan-detail-value">{{ tradePlanDetailPlan.planUuid }}</span>
              </div>
              <div class="trade-plan-detail-row" v-if="tradePlanDetailPlan.previewId">
                <span class="trade-plan-detail-label">previewId</span>
                <span class="trade-plan-detail-value">{{ tradePlanDetailPlan.previewId }}</span>
              </div>
              <div v-if="tradePlanDetailPlan.executionResult != null" class="trade-plan-detail-json">
                <div class="trade-plan-detail-label">executionResult</div>
                <pre class="trade-plan-detail-pre">{{ safeJson(tradePlanDetailPlan.executionResult) }}</pre>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
      <template #footer>
        <el-button @click="tradePlanDetailOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tradePlanConfirmOpen" title="确认执行交易计划" width="720px" destroy-on-close>
      <div v-if="tradePlanConfirmPlan" class="trade-plan-confirm">
        <div class="trade-plan-confirm-summary">
          <div class="trade-plan-confirm-title">{{ tradePlanTitle(tradePlanConfirmPlan) }}</div>
          <div class="trade-plan-confirm-sub">{{ tradePlanConfirmPlan.summary }}</div>
        </div>
        <div class="trade-plan-confirm-rows">
          <div class="trade-plan-detail-row" v-if="tradePlanConfirmPlan.planContent?.symbol">
            <span class="trade-plan-detail-label">交易对</span>
            <span class="trade-plan-detail-value">{{ tradePlanConfirmPlan.planContent?.symbol }}</span>
          </div>
          <div class="trade-plan-detail-row" v-if="tradePlanConfirmPlan.planContent?.side">
            <span class="trade-plan-detail-label">方向</span>
            <span class="trade-plan-detail-value">{{ tradePlanConfirmPlan.planContent?.side }}</span>
          </div>
          <div class="trade-plan-detail-row" v-if="tradePlanConfirmPlan.planContent?.quantity != null">
            <span class="trade-plan-detail-label">数量</span>
            <span class="trade-plan-detail-value">{{ tradePlanConfirmPlan.planContent?.quantity }}</span>
          </div>
          <div class="trade-plan-detail-row" v-if="tradePlanEntryText(tradePlanConfirmPlan.planContent)">
            <span class="trade-plan-detail-label">入场</span>
            <span class="trade-plan-detail-value">{{ tradePlanEntryText(tradePlanConfirmPlan.planContent) }}</span>
          </div>
        </div>
        <el-divider />
        <el-checkbox v-model="tradePlanConfirmChecked">我已确认以上参数无误</el-checkbox>
      </div>
      <template #footer>
        <el-button @click="tradePlanConfirmOpen = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="isChatting || !tradePlanConfirmChecked || !tradePlanConfirmPlan?.previewId"
          @click="doTradePlanConfirm"
        >
          确认执行
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="tradePlanCreateOpen" title="新建交易计划" width="820px" destroy-on-close>
      <el-form v-if="tradePlanCreateOpen" :model="tradePlanCreateForm" label-width="120px">
        <el-form-item label="账户">
          <el-select v-model="tradePlanCreateForm.accountId" filterable style="width: 100%">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name || a.id" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="tradePlanCreateForm.previewType">
            <el-radio-button label="OPEN">开仓</el-radio-button>
            <el-radio-button label="CLOSE">平仓</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="交易对">
          <el-input v-model="tradePlanCreateForm.symbol" placeholder="例如 BTC-USDT-SWAP" />
        </el-form-item>
        <el-form-item label="方向">
          <el-radio-group v-model="tradePlanCreateForm.side">
            <el-radio-button label="LONG">做多</el-radio-button>
            <el-radio-button label="SHORT">做空</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="下单类型">
          <el-radio-group v-model="tradePlanCreateForm.orderType">
            <el-radio-button label="MARKET">市价</el-radio-button>
            <el-radio-button label="LIMIT">限价</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="tradePlanCreateForm.orderType === 'LIMIT'" label="限价">
          <el-input-number v-model="tradePlanCreateForm.limitPrice" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="数量(张)">
          <el-input-number v-model="tradePlanCreateForm.quantity" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="杠杆">
          <el-input-number v-model="tradePlanCreateForm.leverage" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="止损价">
          <el-input-number v-model="tradePlanCreateForm.stopLossPrice" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="止盈价">
          <el-input-number v-model="tradePlanCreateForm.takeProfitPrice" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="tradePlanCreateForm.name" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="tradePlanCreateForm.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tradePlanCreateOpen = false">取消</el-button>
        <el-button type="primary" :disabled="isChatting" @click="submitCreateTradePlan">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, computed, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { get, post } from "@/api";
import { exchangeApi } from "@/api/exchange";
import { getLatestTickers, type LatestTicker } from "@/api/kline";
import xiaolingbaoLogo from "@/assets/xiaolingbao-logo.svg";
import DOMPurify from "dompurify";
import MarkdownIt from "markdown-it";
import {
  Monitor,
  Operation,
  ChatDotRound,
  Cpu,
  Calendar,
  Document,
  Setting,
  CaretTop,
  CaretBottom,
  Promotion,
  Pointer
} from "@element-plus/icons-vue";

interface Props {
  initialFocus?: string;
}

const props = withDefaults(defineProps<Props>(), {
  initialFocus: "",
});

const userInput = ref("");
const isChatting = ref(false);
let chatAbortController: AbortController | null = null;
const stopRequested = ref(false);

type BizPillKey = "commander" | "trade_plans" | "live_advice" | "open" | "close" | "positions" | "orders" | "risk" | "recap";

const bizPills = [
  { key: "commander" as const, label: "AI指挥官" },
  { key: "live_advice" as const, label: "实时建议" },
  { key: "trade_plans" as const, label: "交易计划" },
  { key: "open" as const, label: "开仓" },
  { key: "close" as const, label: "平仓" },
  { key: "positions" as const, label: "持仓" },
  { key: "orders" as const, label: "订单" },
  { key: "risk" as const, label: "风控" },
  { key: "recap" as const, label: "复盘" },
];

const activeBizPill = ref<BizPillKey | null>(null);

const chatInputPlaceholder = computed(() => {
  if (activeBizPill.value === "commander") return "输入标的或指令（例如 BTC / BTC-USDT-SWAP / 观望条件）";
  if (activeBizPill.value === "live_advice") return "请输入需要分析的标的";
  if (activeBizPill.value === "recap") return "发送复盘指令（例如 今日复盘 / 昨日复盘 / 本周复盘）";
  return "向小灵宝提问";
});

const stopChat = () => {
  if (!isChatting.value) return;
  stopRequested.value = true;
  chatAbortController?.abort();
};

const normalizeAdviceSymbolInput = (raw: string) => {
  const s = String(raw || "").trim();
  if (!s) return "";
  const stripped = s
    .replace(/^实时建议\s*[:：]\s*/i, "")
    .replace(/^标的\s*[:：]\s*/i, "")
    .replace(/^symbol\s*[:：]\s*/i, "")
    .trim();
  const up = stripped.replace(/\s+/g, "").replace(/\//g, "-").toUpperCase();
  const m = up.match(/[A-Z0-9]{2,12}-[A-Z0-9]{2,12}(?:-SWAP)?/);
  const picked = m?.[0] || up;
  if (/^[A-Z0-9]{2,12}$/.test(picked)) return `${picked}-USDT-SWAP`;
  return picked;
};

const buildLiveAdvicePrompt = async (symbolInput: string) => {
  const symbol = normalizeAdviceSymbolInput(symbolInput);
  if (!symbol) {
    ElMessage.warning("请输入需要分析的标的（例如 BTC-USDT-SWAP 或 BTC）");
    return null;
  }
  if (!selectedAccountId.value) {
    const botAccountId = String(selectedBot.value?.accountId || "").trim();
    if (botAccountId) {
      await onAccountChange(botAccountId);
    } else if (accounts.value.length > 0) {
      await onAccountChange(accounts.value[0].id);
    }
    if (!selectedAccountId.value) {
      ElMessage.warning("请先选择机器人或账户");
      return null;
    }
  }

  currentSymbol.value = symbol;
  await loadSymbolData({ silent: true });

  const px = currentPrice.value;
  const chg = priceChange.value;
  const bestBid =
    Array.isArray(buyOrders.value) && buyOrders.value.length ? (buyOrders.value[0] as any)?.[0] : "";
  const bestAsk =
    Array.isArray(sellOrders.value) && sellOrders.value.length ? (sellOrders.value[0] as any)?.[0] : "";

  const pos = Array.isArray(positions.value) ? positions.value : [];
  const posSummary = pos
    .slice(0, 3)
    .map((p: any) => {
      const ps = String(p?.symbol || "");
      const side = String(p?.side || p?.positionSide || "");
      const sz = p?.size ?? p?.positionAmt ?? p?.amount ?? "";
      const entry = p?.entryPrice ?? p?.avgPrice ?? p?.price ?? "";
      return [ps, side, sz ? `size=${sz}` : "", entry ? `entry=${entry}` : ""].filter(Boolean).join(" ");
    })
    .filter(Boolean)
    .join("; ");

  const lines: string[] = [];
  lines.push("基于当前行情给我一个可执行的短线交易建议（只给 1 个优先方案；不做也要明确观望与触发条件）：");
  lines.push("");
  lines.push("【行情】");
  lines.push(`- 交易对：${symbol}`);
  lines.push(`- 最新价：${px}`);
  lines.push(`- 涨跌幅：${chg}%`);
  if (bestBid || bestAsk) lines.push(`- 买一/卖一：${bestBid || "-"} / ${bestAsk || "-"}`);
  lines.push("");
  lines.push("【账户/约束】");
  lines.push(`- 账户：${selectedAccountId.value}`);
  if (posSummary) lines.push(`- 当前持仓摘要：${posSummary}`);
  lines.push("");
  lines.push("【输出要求】");
  lines.push("- 必须包含：方向、入场方式(触发条件/价格)、止损、止盈(1-3档)、仓位或张数、失效条件");
  lines.push("- 若能给出明确可执行方案：在末尾追加 tradeplan 严格 JSON（不要解释）");
  lines.push("- 若无法给出明确方案：不要输出 tradeplan");

  return {
    symbol,
    displayText: `实时建议：${symbol}`,
    promptText: lines.join("\n"),
  };
};

const buildCommanderPrompt = async (latestText: string) => {
  if (!selectedAccountId.value && accounts.value.length > 0) {
    await onAccountChange(accounts.value[0].id);
  }
  const botsList = (bots.value as any[]) || [];
  const shortlist = activeBotCards.value && activeBotCards.value.length ? activeBotCards.value : [];
  const chosen = shortlist[0]?.raw || botsList[0] || null;
  const accountUsd = Number(stats.account || 0) || computeAccountUsdFromBalances();
  const dayPnlUsd = Number(stats.targetCurrent || 0);

  const parseGoalPercent = (text: string) => {
    const s = String(text || "");
    const m1 = s.match(/(\d+(?:\.\d+)?)\s*%/);
    if (m1 && m1[1]) {
      const n = Number(m1[1]);
      return Number.isFinite(n) ? n : null;
    }
    const m2 = s.match(/(?:目标|收益|回报|盈利|涨幅)[^0-9]{0,6}(\d+(?:\.\d+)?)/);
    if (m2 && m2[1]) {
      const n = Number(m2[1]);
      if (!Number.isFinite(n)) return null;
      if (n > 0 && n <= 50) return n;
    }
    return null;
  };

  const desiredGoalPercent = parseGoalPercent(latestText);
  const desiredGoalUsd =
    desiredGoalPercent != null && accountUsd > 0 ? Number(((accountUsd * desiredGoalPercent) / 100).toFixed(2)) : null;
  const desiredGoalLeftUsd =
    desiredGoalUsd != null ? Number((desiredGoalUsd - dayPnlUsd).toFixed(2)) : null;
  const currentPnlPercent =
    accountUsd > 0 && Number.isFinite(dayPnlUsd) ? Number(((dayPnlUsd / accountUsd) * 100).toFixed(2)) : null;

  const items = shortlist.slice(0, 6).map((b) => ({
    id: String(b.raw?.botId || b.raw?.id || ""),
    accountId: String(b.raw?.accountId || ""),
    name: String(b.name || ""),
    status: String(b.statusText || ""),
    pair: String(b.tradingPair || ""),
    dailyPnl: Number(b.dailyPnl || 0),
    targetPercent: Number(b.targetPercent || 0),
    last: String(b.lastActionText || ""),
    targetTotal: Number(readBotDailyTarget(b.raw) || 0),
  }));

  const header =
    "你是“AI 指挥官”，只根据当前用户可用的机器人来指挥完成当日收益目标。" +
    "你必须先汇总当前可用机器人，再给出“目标拆分计划”，最后给出可执行的交易计划草案。" +
    "若无法安全执行，明确给出观望条件。所有建议需可执行、可审计。\n";

  const goalLines: string[] = [];
  goalLines.push(`资金：$${accountUsd || 0}`);
  goalLines.push(`今日PnL：$${Number.isFinite(dayPnlUsd) ? dayPnlUsd : 0}${currentPnlPercent != null ? `（${currentPnlPercent}%）` : ""}`);
  if (desiredGoalPercent != null) {
    goalLines.push(
      `今日目标：+${desiredGoalPercent}%${desiredGoalUsd != null ? `（约 $${desiredGoalUsd}）` : ""}${desiredGoalLeftUsd != null ? `；剩余约 $${desiredGoalLeftUsd}` : ""}`,
    );
  } else {
    const goalTotal = Number(stats.targetTotal || 0);
    const goalLeft = goalTotal > 0 ? Math.max(0, goalTotal - dayPnlUsd) : 0;
    goalLines.push(`今日目标(系统)：$${dayPnlUsd} / $${goalTotal}${goalTotal ? `（剩余 $${goalLeft}）` : ""}`);
  }
  const goalLine = `目标概览：${goalLines.join(" · ")}\n`;

  const botLines = items.length
    ? items
        .map(
          (x, idx) =>
            `- [${idx + 1}] ${x.name} (${x.status}) · botId=${x.id || "-"} · accountId=${x.accountId || "-"} · ${x.pair || "-"} · 今日PnL=${x.dailyPnl} · 目标进度=${x.targetPercent}% · 上次=${x.last} · 目标=${x.targetTotal || "-"}`,
        )
        .join("\n")
    : "- 暂无机器人";

  const outputReq =
    "\n输出要求：\n" +
    "1) 先给出“目标拆分计划”：用 1-3 个机器人完成目标，说明每个机器人预期贡献（$ 与 %）与最大可接受亏损（$）\n" +
    "2) 再给出“执行顺序”：明确先后顺序与触发条件（例如触发价/时间窗/信号），以及整体停止条件（例如达到目标/亏损达到上限）\n" +
    "3) 若明确可执行：为每个机器人输出一段 tradeplan 严格 JSON 代码块（```tradeplan ... ```），可输出多段；每段必须包含 planContent.accountId 与 trace.botId\n" +
    "4) 若不建议执行：给出“观望条件”和下一次检查时间，不要输出 tradeplan\n";

  const userMsg = latestText ? `用户指令：${latestText}\n` : "";

  return {
    displayText: desiredGoalPercent != null ? `AI 指挥官：目标 +${desiredGoalPercent}%` : "AI 指挥官",
    promptText: [header, goalLine, "机器人清单：\n", botLines, "\n", outputReq, userMsg].join(""),
    chosenBot: chosen,
  };
};

type ToolPreview = {
  previewId: string;
  tool: string;
  planUuid?: string;
  message?: string;
  warnings?: string[];
};

const extractFencedBlock = (content: string, lang: string) => {
  const raw = String(content || "");
  const re = new RegExp("```" + lang + "\\s*([\\s\\S]*?)```", "i");
  const m = raw.match(re);
  if (m && m[1]) return m[1].trim();
  return null;
};

const extractFencedBlocks = (content: string, lang: string) => {
  const raw = String(content || "");
  const re = new RegExp("```" + lang + "\\s*([\\s\\S]*?)```", "gi");
  const blocks: string[] = [];
  for (;;) {
    const m = re.exec(raw);
    if (!m) break;
    const body = m[1] ? String(m[1]).trim() : "";
    if (body) blocks.push(body);
  }
  return blocks;
};

const extractJsonCandidate = (content: string) => {
  const raw = String(content || "").trim();
  if (!raw) return null;
  const fenced = raw.match(/```json\s*([\s\S]*?)```/i);
  if (fenced && fenced[1]) {
    return fenced[1].trim();
  }
  if (raw.startsWith("{") && raw.endsWith("}")) {
    return raw;
  }
  const first = raw.indexOf("{");
  const last = raw.lastIndexOf("}");
  if (first !== -1 && last !== -1 && last > first) {
    return raw.slice(first, last + 1).trim();
  }
  return null;
};

type TradePlanDraft = {
  type: "trade_plan_draft";
  name?: string;
  previewType?: "OPEN" | "CLOSE";
  planContent?: Record<string, unknown>;
  trace?: Record<string, unknown>;
};

const parseTradePlanDrafts = (content: string): TradePlanDraft[] => {
  const drafts: TradePlanDraft[] = [];
  const blocksTradeplan = extractFencedBlocks(content, "tradeplan");
  const blocksJson = extractFencedBlocks(content, "json");
  const candidates = [...blocksTradeplan, ...blocksJson];
  for (const jsonText of candidates) {
    let obj: any;
    try {
      obj = JSON.parse(jsonText);
    } catch (_) {
      continue;
    }
    if (!obj || obj.type !== "trade_plan_draft") continue;
    if (!obj.planContent || typeof obj.planContent !== "object") continue;
    drafts.push(obj as TradePlanDraft);
  }
  if (!drafts.length) {
    const single = extractJsonCandidate(content);
    if (single) {
      try {
        const obj = JSON.parse(single);
        if (obj && obj.type === "trade_plan_draft" && obj.planContent && typeof obj.planContent === "object") {
          drafts.push(obj as TradePlanDraft);
        }
      } catch (_) {}
    }
  }
  return drafts;
};

const parseToolPreview = (content: string): ToolPreview | null => {
  const jsonText = extractJsonCandidate(content);
  if (!jsonText) return null;
  let obj: any;
  try {
    obj = JSON.parse(jsonText);
  } catch (_) {
    return null;
  }
  const data = obj?.data;
  const previewId = typeof data?.previewId === "string" ? data.previewId : "";
  const tool = typeof data?.next?.tool === "string" ? data.next.tool : "";
  if (!previewId || !tool) return null;
  if (tool !== "quant_open_order_confirm" && tool !== "quant_close_order_confirm" && tool !== "quant_trade_plan_confirm") return null;
  const planUuid = typeof data?.planUuid === "string" ? data.planUuid : "";
  const warnings = Array.isArray(data?.warnings) ? data.warnings.map((w: any) => String(w)) : [];
  const message = typeof obj?.message === "string" ? obj.message : "";
  return { previewId, tool, planUuid: planUuid || undefined, warnings, message };
};

const extractLeadingJsonObjectRange = (raw: string) => {
  const s = String(raw || "");
  const start = s.search(/\{/);
  if (start < 0) return null;
  let depth = 0;
  let inString = false;
  let escape = false;
  for (let i = start; i < s.length; i++) {
    const ch = s[i];
    if (inString) {
      if (escape) {
        escape = false;
        continue;
      }
      if (ch === "\\") {
        escape = true;
        continue;
      }
      if (ch === '"') {
        inString = false;
        continue;
      }
      continue;
    }
    if (ch === '"') {
      inString = true;
      continue;
    }
    if (ch === "{") {
      depth += 1;
      continue;
    }
    if (ch === "}") {
      depth -= 1;
      if (depth === 0) return { start, end: i };
    }
  }
  return null;
};

type LiveAdviceV1 = {
  type: "live_advice_v1";
  facts?: {
    symbol?: string;
    interval?: string;
    accountId?: string;
    robotId?: string;
    snapshotTs?: string;
    latestPrice?: number;
    riskStatus?: unknown;
  };
  advice?: {
    direction?: "LONG" | "SHORT" | "NO_TRADE";
    entry?: { type?: "MARKET" | "LIMIT" | "CONDITION"; price?: number | null; condition?: string | null } | null;
    stopLoss?: number | null;
    takeProfit?: Array<{ level?: number; ratio?: number }> | null;
    positionSize?: { suggestedContracts?: number | null; riskPercent?: number; calculationBasis?: string } | null;
    validUntil?: string | null;
    monitorConditions?: string[] | null;
    reason?: string;
  };
  tradePlanDraft?: TradePlanDraft | null;
};

const parseLeadingJsonObject = (raw: string) => {
  const s = String(raw || "");
  const trimmed = s.trimStart();
  if (!trimmed.startsWith("{")) return null;
  const range = extractLeadingJsonObjectRange(s);
  if (!range) return null;
  const jsonText = s.slice(range.start, range.end + 1);
  const normalized = jsonText.replace(/[“”]/g, '"').replace(/[‘’]/g, "'");
  try {
    return JSON.parse(normalized);
  } catch {
    return null;
  }
};

const parseLeadingLiveAdviceV1 = (raw: string): LiveAdviceV1 | null => {
  const obj: any = parseLeadingJsonObject(raw);
  if (!obj || obj.type !== "live_advice_v1") return null;
  return obj as LiveAdviceV1;
};

const renderLiveAdviceMarkdown = (obj: LiveAdviceV1): string => {
  const symbol = String(obj?.facts?.symbol || "").trim();
  const interval = String(obj?.facts?.interval || "").trim();
  const direction = String(obj?.advice?.direction || "NO_TRADE").toUpperCase();
  const reason = String(obj?.advice?.reason || "").trim();
  const monitors = Array.isArray(obj?.advice?.monitorConditions) ? obj.advice!.monitorConditions! : [];

  if (direction === "NO_TRADE") {
    const monitorLine = monitors.length ? `\n\n监控条件：${monitors.join("；")}` : "";
    return `**实时建议**（${symbol || "-"} ${interval || "-"}）\n\n结论：观望\n\n原因：${reason || "暂无"}${monitorLine}`;
  }

  const action = direction === "LONG" ? "做多" : "做空";
  const entry = obj?.advice?.entry || null;
  const entryType = entry?.type ? String(entry.type) : "";
  const entryPrice = entry?.price;
  const entryCond = entry?.condition ? String(entry.condition) : "";
  const entryLine =
    entryType === "MARKET"
      ? "市价入场"
      : entryType === "LIMIT"
        ? `限价入场：${Number(entryPrice ?? 0)}`
        : entryType === "CONDITION"
          ? `条件入场：${entryCond || "-"}（触发价 ${Number(entryPrice ?? 0)}）`
          : "-";

  const sl = obj?.advice?.stopLoss;
  const tps = Array.isArray(obj?.advice?.takeProfit) ? obj.advice!.takeProfit! : [];
  const tpLine = tps.length
    ? tps
        .map((tp) => `${Number(tp?.level ?? 0)} (${Math.round(Number(tp?.ratio ?? 0) * 100)}%)`)
        .join(" → ")
    : "-";

  const ps = obj?.advice?.positionSize || null;
  const contracts = ps?.suggestedContracts;
  const riskPct = Number(ps?.riskPercent ?? 0);
  const basis = String(ps?.calculationBasis || "").trim();
  const validUntil = obj?.advice?.validUntil ? String(obj.advice.validUntil) : "";

  const lines = [
    `**实时建议**（${symbol || "-"} ${interval || "-"}）`,
    ``,
    `结论：${action}`,
    `入场：${entryLine}`,
    `止损：${sl == null ? "-" : Number(sl)}`,
    `止盈：${tpLine}`,
    `仓位：${contracts == null ? "-" : Number(contracts)} 张（风险 ${riskPct}%）`,
    basis ? `依据：${basis}` : "",
    monitors.length ? `监控条件：${monitors.join("；")}` : "",
    validUntil ? `失效时间：${validUntil}` : "",
    reason ? `理由：${reason}` : "",
  ].filter(Boolean);

  return lines.join("\n");
};

const stripTradePlanDraftJsonPrefix = (raw: string) => {
  const s = String(raw || "");
  const trimmed = s.trimStart();
  if (!trimmed.startsWith("{")) return s;
  const range = extractLeadingJsonObjectRange(s);
  if (!range) return s;
  const jsonText = s.slice(range.start, range.end + 1);
  try {
    const obj: any = JSON.parse(jsonText);
    if (obj && obj.type === "trade_plan_draft") {
      return s.slice(range.end + 1).trimStart();
    }
  } catch {
  }
  return s;
};

const stripToolPreviewJsonBlock = (raw: string) => {
  const s = String(raw || "");
  if (!parseToolPreview(s)) return s;
  if (/```json/i.test(s)) {
    return s.replace(/```json\s*[\s\S]*?```/gi, "").trim();
  }
  const trimmed = s.trimStart();
  if (!trimmed.startsWith("{")) return s;
  const range = extractLeadingJsonObjectRange(s);
  if (!range) return s;
  const jsonText = s.slice(range.start, range.end + 1);
  try {
    const obj: any = JSON.parse(jsonText);
    const previewId = typeof obj?.data?.previewId === "string" ? obj.data.previewId : "";
    const tool = typeof obj?.data?.next?.tool === "string" ? obj.data.next.tool : "";
    if (previewId && tool) {
      return s.slice(range.end + 1).trimStart();
    }
  } catch {
  }
  return s;
};

const stripTradePlanFence = (raw: string) => {
  const s = String(raw || "");
  return s.replace(/```tradeplan\s*[\s\S]*?```/gi, "").trim();
};

const renderChatMessageContent = (msg: any) => {
  const raw = typeof msg?.content === "string" ? msg.content : String(msg?.content ?? "");
  if (msg?.role !== "assistant" || msg?.type !== "text") return raw;
  const liveAdvice = parseLeadingLiveAdviceV1(raw);
  if (liveAdvice) return renderLiveAdviceMarkdown(liveAdvice);
  let next = raw;
  next = stripToolPreviewJsonBlock(next);
  next = stripTradePlanFence(next);
  next = stripTradePlanDraftJsonPrefix(next);
  return next.trim();
};

const agentConfigOpen = ref(false);
const activeAgentConfigTab = ref("bot");

type LlmProvider = "ollama" | "deepseek" | "openclaw";

type ProviderConfig = {
  provider: LlmProvider;
  model: string;
  apiBaseUrl: string;
  apiKey: string;
  apiKeyConfigured: boolean;
  extraConfig: string;
};

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  typographer: true,
});

const router = useRouter();

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx];
  const href = token.attrGet("href") || "";
  if (/^javascript:/i.test(href)) {
    token.attrSet("href", "#");
  }
  token.attrSet("target", "_blank");
  token.attrSet("rel", "noopener noreferrer");
  return self.renderToken(tokens, idx, options);
};

const stripTradeplanForDisplay = (content: string) => {
  const raw = typeof content === "string" ? content : String(content ?? "");
  let stripped = raw.replace(
    /(^|\n)```{3,}tradeplan[^\n]*\n[\s\S]*?\n```{3,}\s*(?=\n|$)/g,
    "\n",
  );
  stripped = stripped.replace(/<!--tradeplan[\s\S]*?-->/g, "");
  return stripped.trim();
};

const renderMarkdown = (content: string) => {
  const raw = stripTradeplanForDisplay(content);
  const html = md.render(raw);
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: [
      "p",
      "br",
      "strong",
      "em",
      "ul",
      "ol",
      "li",
      "pre",
      "code",
      "blockquote",
      "a",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "hr",
    ],
    ALLOWED_ATTR: ["href", "target", "rel", "class"],
  });
};

const DEEPSEEK_API_BASE_URL = "https://api.deepseek.com";
const deepseekModelOptions = ["deepseek-chat", "deepseek-reasoner"] as const;
const OPENCLAW_DEFAULT_BASE_URL = "http://192.168.1.17:18789";
const openclawModelOptions = ["openclaw"] as const;

const ollamaModels = ref<string[]>([]);
const ollamaStatus = reactive({
  checking: false,
  connected: false,
  lastError: "",
});

const providerConfigs = reactive<Record<LlmProvider, ProviderConfig>>({
  ollama: {
    provider: "ollama",
    model: "qwen3:4b",
    apiBaseUrl: "",
    apiKey: "",
    apiKeyConfigured: false,
    extraConfig: "",
  },
  deepseek: {
    provider: "deepseek",
    model: "deepseek-chat",
    apiBaseUrl: DEEPSEEK_API_BASE_URL,
    apiKey: "",
    apiKeyConfigured: false,
    extraConfig: "",
  },
  openclaw: {
    provider: "openclaw",
    model: "openclaw",
    apiBaseUrl: OPENCLAW_DEFAULT_BASE_URL,
    apiKey: "",
    apiKeyConfigured: false,
    extraConfig: "{\"agentId\":\"main\"}",
  },
});

const ollamaRuntime = reactive({
  generatePath: "/api/generate",
  stream: true,
  timeoutMs: 1800000,
});

const expandedProvider = ref<LlmProvider | null>(null);

const activeSelection = reactive({
  provider: "ollama" as LlmProvider,
  model: "qwen3:4b",
});

const activeModelKey = ref("ollama:qwen3:4b");

const llmConfig = reactive({
  provider: "ollama" as LlmProvider,
  model: "qwen3:4b",
  apiBaseUrl: "",
  apiKeyConfigured: false,
  extraConfig: "",
  generatePath: "/api/generate",
  stream: true,
  timeoutMs: 1800000,
});

const syncActiveToChatConfig = () => {
  const p = activeSelection.provider;
  llmConfig.provider = p;
  llmConfig.model = activeSelection.model;
  llmConfig.apiBaseUrl = providerConfigs[p].apiBaseUrl;
  llmConfig.apiKeyConfigured = providerConfigs[p].apiKeyConfigured;
  llmConfig.extraConfig = providerConfigs[p].extraConfig;
  llmConfig.generatePath = ollamaRuntime.generatePath;
  llmConfig.stream = ollamaRuntime.stream;
  llmConfig.timeoutMs = ollamaRuntime.timeoutMs;
};

const isProviderConfigured = (p: LlmProvider) => {
  if (p === "ollama") return true;
  return Boolean(providerConfigs[p].apiKeyConfigured);
};

const activeModelOptions = computed(() => {
  const opts: Array<{ key: string; label: string }> = [];
  const add = (p: LlmProvider, model: string | null | undefined) => {
    const m = (model || "").trim();
    if (!m) return;
    opts.push({ key: `${p}:${m}`, label: `${providerLabel(p)} · ${m}` });
  };

  add("ollama", providerConfigs.ollama.model);
  if (isProviderConfigured("deepseek")) add("deepseek", providerConfigs.deepseek.model);
  if (isProviderConfigured("openclaw")) add("openclaw", providerConfigs.openclaw.model);

  if (opts.length === 0) {
    opts.push({ key: "ollama:qwen3:4b", label: "Ollama（本地） · qwen3:4b" });
  }
  return opts;
});

watch(
  activeModelKey,
  (val) => {
    const [pRaw, ...rest] = String(val || "").split(":");
    const p = (pRaw || "").trim().toLowerCase() as LlmProvider;
    const m = rest.join(":").trim();
    if (!p || !m) return;
    if (p !== "ollama" && p !== "deepseek" && p !== "openclaw") return;
    activeSelection.provider = p;
    activeSelection.model = m;
    syncActiveToChatConfig();
  },
  { immediate: true },
);

const toggleProviderEditor = (p: LlmProvider) => {
  expandedProvider.value = expandedProvider.value === p ? null : p;
};

const providerLabel = (p: LlmProvider) => {
  if (p === "ollama") return "Ollama（本地）";
  if (p === "deepseek") return "DeepSeek（云端）";
  return "OpenClaw（局域网）";
};

const providerDesc = (p: LlmProvider) => {
  if (p === "ollama") return "本地模型调用，无需 API Key";
  if (p === "deepseek") return "DeepSeek API，需配置 API Key";
  return "OpenClaw Gateway（/v1/responses），需配置 Token";
};

const configuredCloudProviders = computed(() => {
  const out: LlmProvider[] = [];
  if (isProviderConfigured("deepseek")) out.push("deepseek");
  if (isProviderConfigured("openclaw")) out.push("openclaw");
  return out;
});

const unconfiguredCloudProviders = computed(() => {
  const out: LlmProvider[] = [];
  if (!isProviderConfigured("deepseek")) out.push("deepseek");
  if (!isProviderConfigured("openclaw")) out.push("openclaw");
  return out;
});

const parseJsonObject = (raw: string) => {
  try {
    const v = JSON.parse(raw);
    if (v && typeof v === "object" && !Array.isArray(v)) return v as Record<string, any>;
  } catch (_) {}
  return {};
};

const openclawAgentId = computed<string>({
  get: () => {
    const obj = parseJsonObject(providerConfigs.openclaw.extraConfig || "{}");
    const v = typeof obj.agentId === "string" ? obj.agentId.trim() : "";
    return v || "main";
  },
  set: (val) => {
    const obj = parseJsonObject(providerConfigs.openclaw.extraConfig || "{}");
    obj.agentId = String(val || "main").trim() || "main";
    providerConfigs.openclaw.extraConfig = JSON.stringify(obj);
  },
});

const openclawSessionKey = computed<string>({
  get: () => {
    const obj = parseJsonObject(providerConfigs.openclaw.extraConfig || "{}");
    return typeof obj.sessionKey === "string" ? obj.sessionKey : "";
  },
  set: (val) => {
    const obj = parseJsonObject(providerConfigs.openclaw.extraConfig || "{}");
    const v = String(val || "").trim();
    if (v) obj.sessionKey = v;
    else delete obj.sessionKey;
    providerConfigs.openclaw.extraConfig = JSON.stringify(obj);
  },
});

const AGENT_CONFIG_STORAGE_KEY = "lynxai.agent.config.v1";
const LLM_PROVIDERS_ENDPOINT = "/api/llm/providers";
const LLM_ACTIVE_ENDPOINT = "/api/llm/active";

const loadAgentConfig = () => {
  try {
    const raw = localStorage.getItem(AGENT_CONFIG_STORAGE_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw) as any;

    const active = parsed?.active;
    if (active?.provider && active?.model) {
      activeSelection.provider = active.provider;
      activeSelection.model = active.model;
      activeModelKey.value = `${activeSelection.provider}:${activeSelection.model}`;
    }

    const providers = parsed?.providers;
    for (const p of ["ollama", "deepseek", "openclaw"] as LlmProvider[]) {
      const cfg = providers?.[p];
      if (!cfg) continue;
      if (typeof cfg.model === "string") providerConfigs[p].model = cfg.model;
      if (typeof cfg.apiBaseUrl === "string") providerConfigs[p].apiBaseUrl = cfg.apiBaseUrl;
      if (typeof cfg.apiKeyConfigured === "boolean") providerConfigs[p].apiKeyConfigured = cfg.apiKeyConfigured;
      if (typeof cfg.extraConfig === "string") providerConfigs[p].extraConfig = cfg.extraConfig;
    }
    if (!providerConfigs.deepseek.apiBaseUrl) providerConfigs.deepseek.apiBaseUrl = DEEPSEEK_API_BASE_URL;
    if (!providerConfigs.deepseek.model) providerConfigs.deepseek.model = "deepseek-chat";
    if (!providerConfigs.openclaw.apiBaseUrl) providerConfigs.openclaw.apiBaseUrl = OPENCLAW_DEFAULT_BASE_URL;
    if (!providerConfigs.openclaw.model) providerConfigs.openclaw.model = "openclaw";
    if (!providerConfigs.openclaw.extraConfig) providerConfigs.openclaw.extraConfig = "{\"agentId\":\"main\"}";

    const ollama = parsed?.ollamaRuntime;
    if (typeof ollama?.generatePath === "string" && ollama.generatePath.trim())
      ollamaRuntime.generatePath = ollama.generatePath.trim();
    if (typeof ollama?.stream === "boolean") ollamaRuntime.stream = ollama.stream;
    if (typeof ollama?.timeoutMs === "number" && Number.isFinite(ollama.timeoutMs))
      ollamaRuntime.timeoutMs = ollama.timeoutMs;
  } catch (_) {}
};

loadAgentConfig();
syncActiveToChatConfig();

const saveAgentConfigToLocal = () => {
  try {
    localStorage.setItem(
      AGENT_CONFIG_STORAGE_KEY,
      JSON.stringify({
        active: { provider: activeSelection.provider, model: activeSelection.model },
        providers: {
          ollama: {
            model: providerConfigs.ollama.model,
            apiBaseUrl: providerConfigs.ollama.apiBaseUrl,
            apiKeyConfigured: providerConfigs.ollama.apiKeyConfigured,
            extraConfig: providerConfigs.ollama.extraConfig,
          },
          deepseek: {
            model: providerConfigs.deepseek.model,
            apiBaseUrl: providerConfigs.deepseek.apiBaseUrl,
            apiKeyConfigured: providerConfigs.deepseek.apiKeyConfigured,
            extraConfig: providerConfigs.deepseek.extraConfig,
          },
          openclaw: {
            model: providerConfigs.openclaw.model,
            apiBaseUrl: providerConfigs.openclaw.apiBaseUrl,
            apiKeyConfigured: providerConfigs.openclaw.apiKeyConfigured,
            extraConfig: providerConfigs.openclaw.extraConfig,
          },
        },
        ollamaRuntime: {
          generatePath: ollamaRuntime.generatePath,
          stream: ollamaRuntime.stream,
          timeoutMs: ollamaRuntime.timeoutMs,
        },
      }),
    );
  } catch (_) {}
};

const loadProvidersFromBackend = async () => {
  const res = await fetch(LLM_PROVIDERS_ENDPOINT, { method: "GET" });
  const json = (await res.json()) as any;
  if (!res.ok) throw new Error(json?.message || `HTTP ${res.status}`);
  if (!json?.success) throw new Error(json?.message || "获取配置失败");

  const data = json?.data || {};
  const active = data?.active || {};
  if (active?.provider && active?.model) {
    activeSelection.provider = active.provider;
    activeSelection.model = active.model;
    activeModelKey.value = `${activeSelection.provider}:${activeSelection.model}`;
  }

  const providers = Array.isArray(data?.providers) ? data.providers : [];
  for (const p of providers as any[]) {
    const key = String(p?.provider || "").trim().toLowerCase() as LlmProvider;
    if (!key || !(key in providerConfigs)) continue;
    if (typeof p.model === "string") providerConfigs[key].model = p.model;
    if (typeof p.apiBaseUrl === "string") providerConfigs[key].apiBaseUrl = p.apiBaseUrl;
    providerConfigs[key].apiKeyConfigured = Boolean(p.apiKeyConfigured);
    if (typeof p.extraConfig === "string") providerConfigs[key].extraConfig = p.extraConfig;
    providerConfigs[key].apiKey = "";
  }
  if (!providerConfigs.deepseek.apiBaseUrl) providerConfigs.deepseek.apiBaseUrl = DEEPSEEK_API_BASE_URL;
  if (!providerConfigs.deepseek.model) providerConfigs.deepseek.model = "deepseek-chat";
  if (!providerConfigs.openclaw.apiBaseUrl) providerConfigs.openclaw.apiBaseUrl = OPENCLAW_DEFAULT_BASE_URL;
  if (!providerConfigs.openclaw.model) providerConfigs.openclaw.model = "openclaw";
  if (!providerConfigs.openclaw.extraConfig) providerConfigs.openclaw.extraConfig = "{\"agentId\":\"main\"}";
};

const saveProviderToBackend = async (p: LlmProvider) => {
  const payload: any = {
    model: providerConfigs[p].model || null,
    apiBaseUrl: providerConfigs[p].apiBaseUrl || null,
    extraConfig: providerConfigs[p].extraConfig || null,
  };
  if (p !== "ollama" && providerConfigs[p].apiKey && providerConfigs[p].apiKey.trim()) {
    payload.apiKey = providerConfigs[p].apiKey.trim();
  }

  const res = await fetch(`${LLM_PROVIDERS_ENDPOINT}/${p}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  const json = (await res.json()) as any;
  if (!res.ok) throw new Error(json?.message || `HTTP ${res.status}`);
  if (!json?.success) throw new Error(json?.message || "保存失败");
  const data = json?.data || {};
  providerConfigs[p].apiKeyConfigured = Boolean(data.apiKeyConfigured);
  providerConfigs[p].apiKey = "";
};

const saveActiveToBackend = async () => {
  const res = await fetch(LLM_ACTIVE_ENDPOINT, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      provider: activeSelection.provider,
      model: activeSelection.model,
    }),
  });
  const json = (await res.json()) as any;
  if (!res.ok) throw new Error(json?.message || `HTTP ${res.status}`);
  if (!json?.success) throw new Error(json?.message || "保存失败");
};

const saveAgentConfig = async () => {
  try {
    if (expandedProvider.value) {
      await saveProviderToBackend(expandedProvider.value);
    }
    await saveActiveToBackend();
    saveAgentConfigToLocal();
    syncActiveToChatConfig();
    agentConfigOpen.value = false;
    ElMessage.success("已保存");
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    ElMessage.error(msg || "保存失败");
  }
};

const openAgentConfig = async () => {
  activeAgentConfigTab.value = "llm";
  agentConfigOpen.value = true;
  try {
    await loadAccounts();
  } catch (_) {}
  try {
    await loadProvidersFromBackend();
    saveAgentConfigToLocal();
  } catch (_) {}
  if (ollamaModels.value.length === 0) {
    await refreshOllamaModels();
  }
  if (!ollamaStatus.connected) {
    await testOllamaConnection();
  }
};

const testOllamaConnection = async () => {
  if (ollamaStatus.checking) return;
  ollamaStatus.checking = true;
  ollamaStatus.lastError = "";
  try {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), 5000);
    const res = await fetch("/ollama/api/tags", { signal: controller.signal });
    clearTimeout(timeoutId);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    ollamaStatus.connected = true;
  } catch (e) {
    ollamaStatus.connected = false;
    ollamaStatus.lastError = e instanceof Error ? e.message : String(e);
  } finally {
    ollamaStatus.checking = false;
  }
};

const refreshOllamaModels = async () => {
  if (ollamaStatus.checking) return;
  ollamaStatus.checking = true;
  ollamaStatus.lastError = "";
  try {
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), 8000);
    const res = await fetch("/ollama/api/tags", { signal: controller.signal });
    clearTimeout(timeoutId);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = (await res.json()) as { models?: Array<{ name?: string }> };
    const names = (json.models ?? [])
      .map((m) => (typeof m.name === "string" ? m.name.trim() : ""))
      .filter(Boolean);
    ollamaModels.value = Array.from(new Set(names));
    ollamaStatus.connected = true;
  } catch (e) {
    ollamaStatus.connected = false;
    ollamaStatus.lastError = e instanceof Error ? e.message : String(e);
  } finally {
    ollamaStatus.checking = false;
  }
};

const roleLabel = (role: string) => {
  if (role === "assistant") return "小灵宝";
  if (role === "user") return "我";
  return role;
};

const isLiveAdviceAssistantMessage = (msg: any, idx: number) => {
  if (!msg || msg.role !== "assistant") return false;
  if (typeof msg.biz === "string" && msg.biz === "live_advice") return true;
  const content = typeof msg.content === "string" ? msg.content : String(msg.content ?? "");
  if (content.includes("实时建议")) return true;
  const prev = (chatMessages.value as any[])[idx - 1];
  if (prev && prev.role === "user") {
    const pc = typeof prev.content === "string" ? prev.content : String(prev.content ?? "");
    if (pc.trim().startsWith("实时建议")) return true;
  }
  return false;
};

const buildOllamaPrompt = (latestUserMessage: string) => {
  const system =
    "你是小灵宝（智能量化助手），回答以中文为主，偏向可执行建议，尽量简短清晰。";

  const parts: string[] = [system, ""];

  for (const msg of chatMessages.value as Array<{ role: string; content: string }>) {
    if (msg.role === "user") {
      parts.push(`用户：${msg.content}`);
      continue;
    }
    if (msg.role === "assistant") {
      parts.push(`助手：${msg.content}`);
    }
  }

  parts.push(`用户：${latestUserMessage}`);
  parts.push("助手：");

  return parts.join("\n");
};

const sendMessage = async () => {
  const messageText = userInput.value.trim();
  if (!messageText || isChatting.value) return;

  isChatting.value = true;
  stopRequested.value = false;
  chatAbortController?.abort();
  chatAbortController = new AbortController();
  let requestTimeoutId: number | null = null;
  let inactivityTimeoutId: number | null = null;
  let timedOut = false;

  const inferredBiz =
    /^实时建议\s*[:：]/.test(messageText) || /^实时建议\s+/.test(messageText) ? ("live_advice" as const) : null;
  const bizForThisMessage = (inferredBiz || activeBizPill.value) as BizPillKey;

  let displayUserText = messageText;
  let requestUserText = messageText;
  const useGateway = llmConfig.provider !== "ollama";
  if (bizForThisMessage === "live_advice") {
    if (useGateway) {
      displayUserText = /^实时建议\s*[:：]/.test(messageText) ? messageText : `实时建议：${messageText}`;
      requestUserText = messageText;
    } else {
      try {
        const advice = await buildLiveAdvicePrompt(messageText);
        if (!advice) {
          isChatting.value = false;
          chatAbortController = null;
          return;
        }
        displayUserText = advice.displayText;
        requestUserText = advice.promptText;
      } catch (e: any) {
        isChatting.value = false;
        chatAbortController = null;
        ElMessage.error(e?.message || "获取实时建议失败");
        return;
      }
    }
  } else if (activeBizPill.value === "commander") {
    try {
      const cmd = await buildCommanderPrompt(messageText);
      if (!cmd) {
        isChatting.value = false;
        chatAbortController = null;
        return;
      }
      displayUserText = cmd.displayText;
      requestUserText = cmd.promptText;
    } catch (e: any) {
      isChatting.value = false;
      chatAbortController = null;
      ElMessage.error(e?.message || "AI 指挥官构建失败");
      return;
    }
  }

  const historyForRequest = (chatMessages.value as any[])
    .filter((m) => (m?.role === "user" || m?.role === "assistant") && m?.type === "text")
    .map((m) => ({ role: String(m.role), content: String(m.content || "") }))
    .filter((m) => m.content.trim());

  const userMsg = reactive({ id: Date.now(), role: "user", type: "text", content: displayUserText, biz: bizForThisMessage });
  const assistantMsg = reactive({
    id: Date.now() + 1,
    role: "assistant",
    type: "text",
    content: "小灵宝思考中…",
    biz: bizForThisMessage,
  });
  chatMessages.value.push(userMsg);
  chatMessages.value.push(assistantMsg);
  userInput.value = "";
  scrollToBottom(true);

  try {
    const requestUrl =
      bizForThisMessage === "live_advice" && useGateway
        ? "/api/advice/live"
        : useGateway
          ? "/api/llm/generate"
          : (typeof llmConfig.generatePath === "string" && llmConfig.generatePath.trim()) || "/api/generate";

    const timeoutMs = Number(llmConfig.timeoutMs || 0);
    const enableTimeout = timeoutMs > 0;
    const streamMode = Boolean(llmConfig.stream);
    const inactivityMs = enableTimeout ? Math.max(30000, timeoutMs) : 0;
    const resetInactivityTimer = () => {
      if (!streamMode || inactivityMs <= 0) return;
      if (inactivityTimeoutId) window.clearTimeout(inactivityTimeoutId);
      inactivityTimeoutId = window.setTimeout(() => {
        timedOut = true;
        chatAbortController?.abort();
      }, inactivityMs);
    };
    if (enableTimeout && !streamMode) {
      requestTimeoutId = window.setTimeout(() => {
        timedOut = true;
        chatAbortController?.abort();
      }, Math.max(5000, timeoutMs));
    }
    if (streamMode) {
      resetInactivityTimer();
    }

    const systemPrompt =
      "你是小灵宝（智能量化助手），回答以中文为主，偏向可执行建议，尽量简短清晰。\n\n" +
      "行情数据规则：不要尝试访问公网行情源（例如 Binance/CoinGecko/OKX 公网 API）。当需要查询最新价格时，请通过 Quant Bridge 内网接口查询。\n" +
      "- 最新价格：GET /api/openclaw/price/latest?symbol=ETH-USDT-SWAP&interval=3m （需带 X-OpenClaw-Token），返回 data.price 作为最新价。\n\n" +
      "当你给出明确的开仓/平仓建议时（包含标的、方向、张数、入场方式、止损/止盈等），请在回复末尾追加一个严格 JSON 的交易计划草案代码块，供前端按钮直接生成交易计划。\n" +
      "代码块格式必须为：\n```tradeplan\n{...}\n```\n" +
      "其中 JSON schema:\n" +
      '{\n' +
      '  "type": "trade_plan_draft",\n' +
      '  "name": "可选：计划名称",\n' +
      '  "previewType": "OPEN 或 CLOSE",\n' +
      '  "planContent": {\n' +
      '    "symbol": "BTC-USDT-SWAP",\n' +
      '    "side": "LONG 或 SHORT",\n' +
      '    "orderType": "MARKET 或 LIMIT",\n' +
      '    "limitPrice": 0,\n' +
      '    "quantity": 1,\n' +
      '    "leverage": 1,\n' +
      '    "entry": {"type":"MARKET 或 LIMIT","price":0},\n' +
      '    "stopLoss": {"type":"FIXED","price":0},\n' +
      '    "takeProfit": [{"price":0,"volume":1}]\n' +
      '  },\n' +
      '  "trace": {"note":"可选：简短备注"}\n' +
      '}\n' +
      "要求：该代码块只包含 JSON，不要包含注释、Markdown 解释或多余字段；若无法给出明确可执行建议，则不要输出该代码块。";
    const promptParts: string[] = [systemPrompt, ""];
    for (const msg of historyForRequest) {
      if (msg.role === "user") {
        promptParts.push(`用户：${msg.content}`);
        continue;
      }
      if (msg.role === "assistant") {
        promptParts.push(`小灵宝：${msg.content}`);
      }
    }
    promptParts.push(`用户：${requestUserText}`);
    promptParts.push("小灵宝：");

    const res = await fetch(requestUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(
        useGateway
          ? bizForThisMessage === "live_advice"
            ? {
                stream: llmConfig.stream,
                symbolText: messageText,
                accountId: selectedAccountId.value || String((accounts.value as any[])?.[0]?.id || ""),
                robotId: String((selectedBot.value as any)?.id || ""),
                interval: "3m",
                history: historyForRequest,
              }
            : {
                stream: llmConfig.stream,
                messages: [
                  {
                    role: "system",
                    content: systemPrompt,
                  },
                  ...historyForRequest,
                  { role: "user", content: requestUserText },
                ],
              }
          : {
              model: llmConfig.model,
              stream: llmConfig.stream,
              prompt: promptParts.join("\n"),
            },
      ),
      signal: chatAbortController.signal,
    });

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(text || `HTTP ${res.status}`);
    }

    if (!llmConfig.stream) {
      const json = (await res.json()) as { response?: string; error?: string };
      if (json.error) throw new Error(json.error);
      assistantMsg.content = String(json.response ?? "");
      return;
    }

    if (!res.body) {
      throw new Error("响应体为空");
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    const contentType = res.headers.get("content-type") || "";
    const isSse = contentType.includes("text/event-stream");
    let buffer = "";
    const processPayload = (jsonText: string) => {
      const text = (jsonText || "").trim();
      if (!text) return false;
      if (text === "[DONE]") return true;
      if (!text.startsWith("{")) return false;

      let chunk: { response?: string; done?: boolean; error?: string };
      try {
        chunk = JSON.parse(text);
      } catch (_) {
        return false;
      }

      if (chunk.error) {
        throw new Error(chunk.error);
      }

      const delta = chunk.response ?? "";
      if (delta) {
        if (assistantMsg.content === "小灵宝思考中…") assistantMsg.content = "";
        assistantMsg.content += delta;
        resetInactivityTimer();
        scrollToBottom();
      }

      return Boolean(chunk.done);
    };

    const processSseEvent = (eventText: string) => {
      const lines = eventText.split(/\r?\n/);
      const dataParts: string[] = [];
      for (const raw of lines) {
        const line = raw.trimEnd();
        if (!line) continue;
        if (line.startsWith(":")) continue;
        if (line.startsWith("data:")) {
          dataParts.push(line.slice("data:".length).trimStart());
        }
      }
      if (dataParts.length === 0) return false;
      return processPayload(dataParts.join("\n"));
    };

    let doneByServer = false;
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      if (isSse) {
        for (;;) {
          const idxLF = buffer.indexOf("\n\n");
          const idxCRLF = buffer.indexOf("\r\n\r\n");
          let idx = -1;
          let sepLen = 0;
          if (idxCRLF !== -1 && (idxLF === -1 || idxCRLF < idxLF)) {
            idx = idxCRLF;
            sepLen = 4;
          } else if (idxLF !== -1) {
            idx = idxLF;
            sepLen = 2;
          }
          if (idx === -1) break;

          const eventText = buffer.slice(0, idx);
          buffer = buffer.slice(idx + sepLen);

          if (processSseEvent(eventText)) {
            doneByServer = true;
            break;
          }
        }
      } else {
        const lines = buffer.split(/\r?\n/);
        buffer = lines.pop() ?? "";
        for (const raw of lines) {
          const line = raw.trim();
          if (!line) continue;
          if (line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:")) continue;
          const jsonText = line.startsWith("data:") ? line.slice("data:".length).trim() : line;
          if (processPayload(jsonText)) {
            doneByServer = true;
            break;
          }
        }
      }

      if (doneByServer) {
        break;
      }
    }

    buffer += decoder.decode();
    if (isSse) {
      const rest = buffer.trim();
      if (rest) {
        try {
          processSseEvent(rest);
        } catch (_) {}
      }
    } else {
      const tailLines = buffer.split(/\r?\n/).filter((l) => l.trim());
      for (const raw of tailLines) {
        const line = raw.trim();
        if (!line) continue;
        if (line.startsWith(":") || line.startsWith("event:") || line.startsWith("id:")) continue;
        const jsonText = line.startsWith("data:") ? line.slice("data:".length).trim() : line;
        if (processPayload(jsonText)) break;
      }
    }
  } catch (err) {
    const errAny = err as any;
    const errName = typeof errAny?.name === "string" ? errAny.name : "";
    const errMessage =
      typeof errAny?.message === "string"
        ? errAny.message
        : err instanceof Error
          ? err.message
          : String(err);
    const errToString = (() => {
      try {
        return String(errAny);
      } catch (_) {
        return "";
      }
    })();
    const errMsg = `${errName} ${errMessage} ${errToString}`.trim();
    if (
      (err instanceof DOMException && err.name === "AbortError") ||
      /BodyStreamBuffer was aborted/i.test(errMsg) ||
      /\baborted\b/i.test(errMsg)
    ) {
      if (timedOut) {
        if (assistantMsg.content && assistantMsg.content !== "小灵宝思考中…") {
          assistantMsg.content += "\n\n（请求超时，可稍后重试）";
        } else {
          assistantMsg.content = "请求超时，请稍后重试。";
        }
      } else if (stopRequested.value) {
        if (assistantMsg.content && assistantMsg.content !== "小灵宝思考中…") {
          assistantMsg.content += "\n\n（已停止）";
        } else {
          assistantMsg.content = "已停止。";
        }
      } else {
        assistantMsg.content = "已取消。";
      }
      return;
    }
    const message = errMessage;
    const provider = llmConfig.provider;
    if (provider === "ollama") {
      assistantMsg.content = `连接本地 Ollama 失败：${message}\n\n请确认：\n1) ollama 已启动\n2) 已安装模型 qwen3:4b\n3) 前端通过 Vite 代理访问 /api/generate -> 127.0.0.1:11434`;
    } else if (provider === "openclaw") {
      assistantMsg.content = `连接云端模型失败：${message}\n\n请确认：\n1) 已配置 OpenClaw Token\n2) API Base URL 正确（例如 ${OPENCLAW_DEFAULT_BASE_URL}）\n3) extraConfig 包含 agentId（例如 {"agentId":"main"}）\n\n如果仍报 Invalid HTTP method，通常是网关路由或方法不匹配，优先检查后端 /api/llm/generate 是否正确转发到 OpenClaw（/v1/responses）。`;
    } else {
      assistantMsg.content = `连接云端模型失败：${message}\n\n请确认：\n1) 已配置 DeepSeek API Key\n2) API Base URL 为 https://api.deepseek.com\n3) 已选择模型 deepseek-chat 或 deepseek-reasoner`;
    }
    ElMessage.error("大模型调用失败");
  } finally {
    if (requestTimeoutId) {
      clearTimeout(requestTimeoutId);
      requestTimeoutId = null;
    }
    if (inactivityTimeoutId) {
      clearTimeout(inactivityTimeoutId);
      inactivityTimeoutId = null;
    }
    isChatting.value = false;
    chatAbortController = null;
    scrollToBottom();
  }
};

const CHAT_SCROLL_BOTTOM_THRESHOLD_PX = 80;
const chatHistoryEl = ref<HTMLElement | null>(null);
const chatAutoScrollEnabled = ref(true);

const onChatHistoryScroll = () => {
  const el = chatHistoryEl.value;
  if (!el) return;
  const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
  chatAutoScrollEnabled.value = distance <= CHAT_SCROLL_BOTTOM_THRESHOLD_PX;
};

const scrollToBottom = (force = false) => {
  nextTick(() => {
    const history = chatHistoryEl.value || (document.querySelector(".chat-history") as HTMLElement | null);
    if (!history) return;
    if (!force && !chatAutoScrollEnabled.value) return;
    history.scrollTop = history.scrollHeight;
    chatAutoScrollEnabled.value = true;
  });
};

// 基础状态
const tickCount = ref(45);
const POLL_INTERVAL_MS = 3000;
const stats = reactive({
  trades: 0,
  account: 0,
  pnl: 0,
  targetCurrent: 0,
  targetTotal: 10,
});

const commanderOnline = computed(() => Boolean(selectedAccountId.value) && Boolean(pollIntervalId));

const commanderTargetLabel = computed(() => {
  const t = Number(stats.targetTotal || 0);
  if (!Number.isFinite(t) || t <= 0) return "目标 -";
  return `目标 $${t.toFixed(0)}`;
});

const commanderTodayLabel = computed(() => {
  const v = Number(stats.targetCurrent || 0);
  if (!Number.isFinite(v)) return "今日 -";
  return `今日 $${v.toFixed(2)}`;
});

const commanderPercent = computed(() => {
  const cur = Number(stats.targetCurrent || 0);
  const total = Number(stats.targetTotal || 0);
  if (!Number.isFinite(cur) || !Number.isFinite(total) || total <= 0) return 0;
  const p = (cur / total) * 100;
  return Math.max(0, Math.min(100, Number(p.toFixed(2))));
});

const commanderPercentLabel = computed(() => {
  const total = Number(stats.targetTotal || 0);
  if (!Number.isFinite(total) || total <= 0) return "-%";
  return `${commanderPercent.value.toFixed(0)}%`;
});

const commanderRefreshLabel = computed(() => {
  if (!selectedAccountId.value) return "未选择账户";
  if (!pollIntervalId) return "未轮询";
  return `刷新: ${(POLL_INTERVAL_MS / 1000).toFixed(0)}s`;
});

const commanderStateText = computed(() => {
  const cur = Number(stats.targetCurrent || 0);
  const total = Number(stats.targetTotal || 0);
  const hasTarget = Number.isFinite(total) && total > 0;
  const remaining = hasTarget && Number.isFinite(cur) ? total - cur : NaN;

  const posList = Array.isArray(positions.value) ? (positions.value as any[]) : [];
  const nonZero = posList.filter((p) => {
    const q = Number(p?.positionAmt ?? p?.contracts ?? p?.qty ?? p?.size ?? p?.amount ?? p?.quantity ?? 0);
    return Number.isFinite(q) && q !== 0;
  });
  const first = nonZero[0];
  const symbol = String(first?.symbol || first?.tradingPair || first?.instrumentId || "").trim();
  const q = Number(first?.positionAmt ?? first?.contracts ?? first?.qty ?? first?.size ?? first?.amount ?? first?.quantity ?? 0);
  const sideRaw = String(first?.side || "").trim();
  const side = sideRaw ? sideRaw.toUpperCase() : Number.isFinite(q) && q < 0 ? "SHORT" : "LONG";
  const posText = nonZero.length
    ? `当前持仓：${symbol ? `${symbol} ` : ""}${side}${nonZero.length > 1 ? ` 等${nonZero.length}个` : ""}`
    : "当前无持仓";

  if (!hasTarget) {
    return `状态：IDLE（未设置目标） · ${posText}`;
  }
  if (!Number.isFinite(cur)) {
    return `状态：HUNTING · 距离目标还差 $${Number(total).toFixed(2)} · ${posText}`;
  }
  if (cur >= total) {
    return `状态：ACHIEVED · 当前PnL $${cur.toFixed(2)} · ${posText}`;
  }
  return `状态：HUNTING · 当前PnL $${cur.toFixed(2)} · 距离目标还差 $${Number(remaining).toFixed(2)} · ${posText}`;
});

// 跑马灯数据（从后端最新K线API填充）
const tickerItems = ref<{ symbol: string; displaySymbol: string; price: string; change: number }[]>([]);

const selectedBotId = ref("");
const bots = ref<any[]>([]);
const selectedBot = ref<any>(null);

const botDailyTargetTotal = ref<number | null>(null);
const botDailyTargetLabel = ref<string>("");

const RT_SELECTED_BOT_KEY = "rt_trading_selected_bot_id";
const RT_SELECTED_ACCOUNT_KEY = "rt_trading_selected_account_id";

// 市场分析数据
const sentimentScore = ref(50);
const sentimentLabel = ref("Neutral");
const sentimentColor = ref("#808080");
const aiMarketSummaryText = ref("");
const aiMarketSummaryError = ref("");
const aiMarketSummaryLoading = ref(false);
let aiMarketSummaryAbortController: AbortController | null = null;

type MarketAnalysis = {
  symbol: string;
  interval?: string;
  time?: number;
  price?: number;
  changePercent?: number;
  sentimentScore?: number;
  sentimentLabel?: string;
  trendLabel?: "Bullish" | "Bearish" | "Neutral" | string;
  trendStrength?: number;
  rsi14?: number;
  atr14Percent?: number;
  bollingerWidthPercent?: number;
  supports?: number[];
  resistances?: number[];
  tags?: string[];
};

const marketAnalysis = ref<MarketAnalysis | null>(null);
const marketAnalysisInterval = ref("3m");
const lastMarketAnalysisAtMs = ref(0);
const lastMarketBatchAtMs = ref(0);

const symbolSignals = ref<
  { symbol: string; price: string; trend: string; label: string; strength: number; change?: number }[]
>([]);

const botKeyOf = (b: any) => String(b?.botId || b?.id || "");

const readBotDailyPnl = (bot: any) => {
  const statsObj = parseJsonObject(bot?.statistics || "{}");
  const candidates = [
    statsObj?.dailyPnL,
    statsObj?.daily_pnl,
    statsObj?.todayPnL,
    statsObj?.today_pnl,
    statsObj?.pnlToday,
  ];
  for (const v of candidates) {
    const n = Number(v);
    if (Number.isFinite(n)) return n;
  }
  return NaN;
};

const parseTimeToMs = (v: any) => {
  if (v == null) return 0;
  if (typeof v === "number" && Number.isFinite(v)) return v > 1e12 ? v : v * 1000;
  const s = String(v || "").trim();
  if (!s) return 0;
  const n = Number(s);
  if (Number.isFinite(n)) return n > 1e12 ? n : n * 1000;
  const ms = Date.parse(s);
  return Number.isFinite(ms) ? ms : 0;
};

const readBotLastActionMs = (bot: any) => {
  const statsObj = parseJsonObject(bot?.statistics || "{}");
  const candidates = [
    statsObj?.lastTradeTime,
    statsObj?.lastOrderTime,
    statsObj?.lastActionTime,
    bot?.lastSignalTime,
    bot?.updatedAt,
    bot?.startTime,
  ];
  for (const v of candidates) {
    const ms = parseTimeToMs(v);
    if (ms > 0) return ms;
  }
  return 0;
};

const formatRelativeTime = (ms: number) => {
  if (!ms) return "-";
  const diff = Date.now() - ms;
  if (diff < 0) return "刚刚";
  const m = Math.floor(diff / 60000);
  if (m < 1) return "刚刚";
  if (m < 60) return `${m}分钟`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}小时`;
  const d = Math.floor(h / 24);
  if (d < 30) return `${d}天`;
  const dt = new Date(ms);
  const y = dt.getFullYear();
  const mm = String(dt.getMonth() + 1).padStart(2, "0");
  const dd = String(dt.getDate()).padStart(2, "0");
  return `${y}-${mm}-${dd}`;
};

const botStatusText = (s: string) => {
  const v = String(s || "").toUpperCase();
  if (v === "RUNNING") return "RUNNING";
  if (v === "PAUSED") return "PAUSED";
  if (v === "STOPPED") return "STOPPED";
  if (v === "ERROR") return "ERROR";
  if (v === "CREATED") return "CREATED";
  return v || "-";
};

const botStatusTagType = (s: string) => {
  const v = String(s || "").toUpperCase();
  if (v === "RUNNING") return "success";
  if (v === "PAUSED") return "warning";
  if (v === "ERROR") return "danger";
  return "info";
};

const activeBotCards = computed(() => {
  const now = Date.now();
  const dayMs = 24 * 60 * 60 * 1000;
  const pinnedId = String(selectedBotId.value || "").trim();
  const list = (bots.value as any[])
    .map((b) => {
      const botKey = botKeyOf(b);
      const status = String(b?.status || "");
      const statusText = botStatusText(status);
      const statusType = botStatusTagType(status);
      const isPinned = pinnedId && botKey === pinnedId;
      const lastMs = readBotLastActionMs(b);
      const isActive = String(status).toUpperCase() === "RUNNING" || (lastMs > 0 && now - lastMs <= dayMs);
      const dailyPnl = readBotDailyPnl(b);
      const dailyPnlText = Number.isFinite(dailyPnl) ? `${dailyPnl >= 0 ? "+" : ""}${dailyPnl.toFixed(2)}` : "-";
      const targetTotal = readBotDailyTarget(b);
      const targetText = Number.isFinite(Number(targetTotal)) && Number(targetTotal) > 0 ? `目标 $${Number(targetTotal).toFixed(0)}` : "目标 -";
      const targetPercent = (() => {
        const t = Number(targetTotal);
        if (!Number.isFinite(t) || t <= 0) return 0;
        if (!Number.isFinite(dailyPnl)) return 0;
        return Math.max(0, Math.min(100, Math.round((dailyPnl / t) * 100)));
      })();

      const st = String(status).toUpperCase();
      const toggleDisabled = st === "ERROR";
      const toggleBtnText = st === "RUNNING" ? "暂停" : st === "PAUSED" ? "恢复" : "启动";
      const toggleBtnType = st === "RUNNING" ? "warning" : st === "PAUSED" ? "success" : "primary";

      return {
        botKey,
        name: String(b?.botName || b?.botId || b?.id || "-"),
        statusText,
        statusType,
        tradingPair: String(b?.tradingPair || ""),
        lastActionText: formatRelativeTime(lastMs),
        dailyPnl: Number.isFinite(dailyPnl) ? dailyPnl : 0,
        dailyPnlText,
        targetText,
        targetPercent,
        isPinned,
        toggleBtnText,
        toggleBtnType,
        toggleDisabled,
        raw: b,
        isActive,
      };
    })
    .filter((x) => x.botKey && x.isActive);

  list.sort((a, b) => {
    if (a.isPinned && !b.isPinned) return -1;
    if (!a.isPinned && b.isPinned) return 1;
    const ar = a.statusText === "RUNNING";
    const br = b.statusText === "RUNNING";
    if (ar && !br) return -1;
    if (!ar && br) return 1;
    const am = readBotLastActionMs(a.raw);
    const bm = readBotLastActionMs(b.raw);
    return bm - am;
  });

  return list.slice(0, 6);
});

// 聊天数据
const CHAT_HISTORY_STORAGE_KEY = "xiaolingbao_chat_history_v1";

const defaultChatMessages = () => [
  {
    id: 1,
    role: "assistant",
    type: "text",
    content:
      "你好！我是 小灵宝 智能量化助手。\n你可以直接输入：\n- “根据BTC的波动，给我一个入场/止损/止盈的量化思路”\n- “给我一个网格策略参数建议（本金1000U）”",
  },
];

const chatMessages = ref<any[]>(defaultChatMessages());

let persistChatTimer: number | null = null;

const buildChatHistorySnapshot = () => {
  const items = (chatMessages.value as any[])
    .filter((m) => m && (m.role === "user" || m.role === "assistant") && m.type === "text")
    .map((m) => ({
      id: Number.isFinite(Number(m.id)) ? Number(m.id) : Date.now(),
      role: String(m.role),
      type: "text",
      content: String(m.content || ""),
      biz: typeof m.biz === "string" ? m.biz : undefined,
    }))
    .filter((m) => {
      const c = m.content.trim();
      if (!c) return false;
      if (m.role === "assistant" && c === "小灵宝思考中…") return false;
      return true;
    });

  const hasGreeting = items.some(
    (m) => m.role === "assistant" && String(m.content || "").includes("我是 小灵宝"),
  );
  const normalized = hasGreeting ? items : [...defaultChatMessages(), ...items];
  const capped = normalized.length > 120 ? [normalized[0], ...normalized.slice(-119)] : normalized;
  return capped;
};

const persistChatHistoryNow = () => {
  try {
    localStorage.setItem(CHAT_HISTORY_STORAGE_KEY, JSON.stringify(buildChatHistorySnapshot()));
  } catch {
  }
};

const schedulePersistChatHistory = () => {
  if (persistChatTimer) window.clearTimeout(persistChatTimer);
  persistChatTimer = window.setTimeout(() => {
    persistChatTimer = null;
    persistChatHistoryNow();
  }, 800);
};

const loadChatHistory = () => {
  try {
    const raw = localStorage.getItem(CHAT_HISTORY_STORAGE_KEY);
    if (!raw) {
      scrollToBottom(true);
      return;
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed) || parsed.length === 0) {
      scrollToBottom(true);
      return;
    }
    const normalized = (parsed as any[])
      .filter((m) => m && (m.role === "user" || m.role === "assistant") && m.type === "text")
      .map((m) => ({
        id: Number.isFinite(Number(m.id)) ? Number(m.id) : Date.now(),
        role: String(m.role),
        type: "text",
        content: String(m.content || ""),
        biz:
          typeof m.biz === "string"
            ? m.biz
            : String(m.role) === "user" && String(m.content || "").trim().startsWith("实时建议")
              ? "live_advice"
              : undefined,
      }))
      .filter((m) => {
        const c = m.content.trim();
        if (!c) return false;
        if (m.role === "assistant" && c === "小灵宝思考中…") return false;
        return true;
      });
    for (let i = 1; i < normalized.length; i++) {
      const cur = normalized[i] as any;
      const prev = normalized[i - 1] as any;
      if (!cur || cur.role !== "assistant") continue;
      if (typeof cur.biz === "string" && cur.biz) continue;
      if (prev && typeof prev.biz === "string" && prev.biz) {
        cur.biz = prev.biz;
        continue;
      }
      const text = String(cur.content || "");
      if (text.includes("实时建议")) cur.biz = "live_advice";
    }
    if (normalized.length > 0) {
      chatMessages.value = normalized;
    }
    isChatting.value = false;
    stopRequested.value = false;
    chatAbortController = null;
    const last = chatMessages.value[chatMessages.value.length - 1];
    if (last?.role === "assistant" && String(last?.content || "").trim() === "小灵宝思考中…") {
      chatMessages.value = chatMessages.value.slice(0, -1);
    }
    scrollToBottom(true);
  } catch {
    scrollToBottom(true);
  }
};

watch(chatMessages, schedulePersistChatHistory, { deep: true });

const toolPreviewMap = computed(() => {
  const map = new Map<number, ToolPreview>();
  for (const msg of chatMessages.value as any[]) {
    if (!msg || msg.role !== "assistant" || msg.type !== "text") continue;
    const pv = parseToolPreview(String(msg.content || ""));
    if (!pv) continue;
    map.set(Number(msg.id), pv);
  }
  return map;
});

const toolPreviewFor = (msgId: number) => toolPreviewMap.value.get(Number(msgId));

const tradePlanDraftMap = computed(() => {
  const map = new Map<number, TradePlanDraft[]>();
  for (const msg of chatMessages.value as any[]) {
    if (!msg || msg.role !== "assistant" || msg.type !== "text") continue;
    const drafts: TradePlanDraft[] = [];
    drafts.push(...parseTradePlanDrafts(String(msg.content || "")));
    const liveAdvice = parseLeadingLiveAdviceV1(String(msg.content || ""));
    const draftFromAdvice = liveAdvice?.tradePlanDraft;
    if (draftFromAdvice && draftFromAdvice.type === "trade_plan_draft") {
      drafts.push(draftFromAdvice);
    }
    if (!drafts.length) continue;
    map.set(Number(msg.id), drafts);
  }
  return map;
});

const tradePlanDraftsFor = (msgId: number) => tradePlanDraftMap.value.get(Number(msgId)) || [];

const confirmToolPreview = async (pv: ToolPreview) => {
  if (!pv || !pv.previewId || !pv.tool) return;
  if (isChatting.value) return;
  const params =
    pv.tool === "quant_trade_plan_confirm"
      ? `{"planUuid":"${pv.planUuid || ""}","previewId":"${pv.previewId}"}`
      : `{"previewId":"${pv.previewId}"}`;
  const text = `请调用工具 ${pv.tool}，参数为 ${params}。只执行，不要解释。`;
  userInput.value = text;
  await sendMessage();
};

type TradingPlanStatus = "pending" | "executed" | "failed";

type TradePlanContent = {
  symbol?: string;
  side?: string;
  orderType?: string;
  limitPrice?: number;
  quantity?: number;
  leverage?: number;
  entry?: Record<string, unknown>;
  stopLoss?: Record<string, unknown>;
  takeProfit?: Array<Record<string, unknown>>;
  accountId?: string;
};

type TradePlanTrace = {
  note?: string;
  [k: string]: unknown;
};

type TradingPlanItem = {
  id: string;
  planUuid: string;
  previewId?: string;
  previewType?: string;
  status: TradingPlanStatus;
  name: string;
  time: string;
  summary: string;
  planContent?: TradePlanContent;
  trace?: TradePlanTrace;
  executionResult?: unknown;
  updatedAtMs: number;
};

const tradingPlans = ref<TradingPlanItem[]>([]);

const pendingPlanCount = computed(() => tradingPlans.value.filter((p) => p.status === "pending").length);

const tradePlanFilter = ref<TradingPlanStatus | "all">("pending");

const displayedTradingPlans = computed(() => {
  if (tradePlanFilter.value === "all") return tradingPlans.value;
  return tradingPlans.value.filter((p) => p.status === tradePlanFilter.value);
});

const fmtHm = (ms: number) => {
  const d = new Date(ms);
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
};

const summarizePlanContent = (pc?: TradePlanContent) => {
  if (!pc) return "";
  const symbol = typeof pc.symbol === "string" ? pc.symbol : "";
  const side = typeof pc.side === "string" ? pc.side : "";
  const entry = typeof pc.entry === "object" && pc.entry ? pc.entry : null;
  const stopLoss = typeof pc.stopLoss === "object" && pc.stopLoss ? pc.stopLoss : null;
  const takeProfit = Array.isArray(pc.takeProfit) ? pc.takeProfit : null;
  const parts: string[] = [];
  if (symbol || side) parts.push([symbol, side].filter(Boolean).join(" "));
  if (entry && typeof (entry as any).type === "string") parts.push(`entry:${String((entry as any).type)}`);
  if (stopLoss && (stopLoss as any).price != null) parts.push(`SL:${String((stopLoss as any).price)}`);
  if (takeProfit && takeProfit.length) parts.push(`TP:${takeProfit.length}`);
  return parts.join(" · ");
};

const tradePlanSideLabel = (side: string, previewType?: string) => {
  const s = String(side || "").toUpperCase();
  const pt = String(previewType || "").toUpperCase();
  if (pt === "CLOSE") {
    if (s === "LONG") return "平多";
    if (s === "SHORT") return "平空";
    return s ? `平${s}` : "";
  }
  if (s === "LONG") return "做多";
  if (s === "SHORT") return "做空";
  return s;
};

const tradePlanTitle = (p: TradingPlanItem) => {
  const pc = p.planContent;
  const symbol = pc?.symbol || "";
  const side = pc?.side || "";
  const qty = pc?.quantity != null ? `${pc.quantity}张` : "";
  const sideText = tradePlanSideLabel(side, p.previewType);
  const core = [symbol, sideText].filter(Boolean).join(" ");
  if (core && qty) return `${core} · ${qty}`;
  if (core) return core;
  return p.name || "trade_plan";
};

const tradePlanEntryText = (pc?: TradePlanContent) => {
  const entry = pc?.entry;
  if (!entry || typeof entry !== "object") return "";
  const type = typeof (entry as any).type === "string" ? String((entry as any).type) : "";
  const price = (entry as any).price != null ? String((entry as any).price) : "";
  if (type && price) return `${type} @ ${price}`;
  return type || price || "";
};

const tradePlanStopLossText = (pc?: TradePlanContent) => {
  const sl = pc?.stopLoss;
  if (!sl || typeof sl !== "object") return "";
  const type = typeof (sl as any).type === "string" ? String((sl as any).type) : "";
  const price = (sl as any).price != null ? String((sl as any).price) : "";
  if (type && price) return `${type} @ ${price}`;
  return type || price || "";
};

const tradePlanTakeProfitText = (pc?: TradePlanContent) => {
  const tps = pc?.takeProfit;
  if (!Array.isArray(tps) || !tps.length) return "";
  const first = tps[0] || {};
  const price = (first as any).price != null ? String((first as any).price) : "";
  if (!price) return `${tps.length}档`;
  return tps.length === 1 ? `${price}` : `${price} 等${tps.length}档`;
};

const safeJson = (v: unknown) => {
  try {
    return JSON.stringify(v, null, 2);
  } catch (_) {
    return String(v);
  }
};

const parsePlanFromAssistant = (content: string) => {
  const jsonText = extractJsonCandidate(content);
  if (!jsonText) return null;
  let obj: any;
  try {
    obj = JSON.parse(jsonText);
  } catch (_) {
    return null;
  }
  const data = obj?.data;
  const planUuid = typeof data?.planUuid === "string" ? data.planUuid : "";
  if (!planUuid) return null;
  const previewId = typeof data?.previewId === "string" ? data.previewId : "";
  const previewType = typeof data?.previewType === "string" ? data.previewType : "";
  const statusRaw = typeof data?.status === "string" ? data.status : "";
  const status: TradingPlanStatus =
    statusRaw === "executed" ? "executed" : statusRaw === "failed" ? "failed" : "pending";
  const planContent = data?.planContent && typeof data.planContent === "object" ? (data.planContent as TradePlanContent) : undefined;
  const trace = data?.trace && typeof data.trace === "object" ? (data.trace as TradePlanTrace) : undefined;
  const executionResult = data?.executionResult != null ? data.executionResult : data?.executionResult;
  return {
    planUuid,
    previewId: previewId || undefined,
    previewType: previewType || undefined,
    status,
    planContent,
    trace,
    executionResult,
    message: typeof obj?.message === "string" ? obj.message : ""
  };
};

const upsertPlan = (planUuid: string, patch: Partial<TradingPlanItem>) => {
  const now = Date.now();
  const idx = tradingPlans.value.findIndex((p) => p.planUuid === planUuid);
  if (idx === -1) {
    const planContent = patch.planContent;
    const symbol = planContent && typeof planContent.symbol === "string" ? (planContent.symbol as string) : "";
    const side = planContent && typeof planContent.side === "string" ? (planContent.side as string) : "";
    const name = patch.name || [symbol, side].filter(Boolean).join(" ") || "trade_plan";
    const summary = patch.summary || summarizePlanContent(planContent) || "";
    tradingPlans.value.unshift({
      id: planUuid,
      planUuid,
      previewId: patch.previewId,
      status: patch.status || "pending",
      name,
      time: fmtHm(now),
      summary,
      planContent,
      trace: patch.trace,
      executionResult: patch.executionResult,
      updatedAtMs: now,
    });
    return;
  }
  const cur = tradingPlans.value[idx];
  const next: TradingPlanItem = {
    ...cur,
    ...patch,
    previewId: patch.previewId || cur.previewId,
    planContent: patch.planContent || cur.planContent,
    trace: patch.trace || cur.trace,
    executionResult: patch.executionResult ?? cur.executionResult,
    updatedAtMs: now,
    time: fmtHm(now),
  };
  if (!patch.summary && (patch.planContent || cur.planContent)) {
    next.summary = summarizePlanContent((patch.planContent || cur.planContent) as Record<string, unknown>) || next.summary;
  }
  tradingPlans.value.splice(idx, 1);
  tradingPlans.value.unshift(next);
};

const confirmTradePlan = async (p: TradingPlanItem) => {
  if (isChatting.value) return;
  try {
    isChatting.value = true;
    const res: any = await post(`/trading/trade-plans/${p.planUuid}/confirm`);
    const ok = Boolean(res?.success ?? true);
    if (!ok) {
      ElMessage.error(res?.message || "执行失败");
      return;
    }
    await loadTradePlansFromDb();
    ElMessage.success("执行成功");
  } catch (e: any) {
    ElMessage.error(e?.message || "执行失败");
  } finally {
    isChatting.value = false;
  }
};

const previewTradePlan = async (p: TradingPlanItem) => {
  if (isChatting.value) return;
  try {
    isChatting.value = true;
    const payload = selectedAccountId.value ? { accountId: selectedAccountId.value } : {};
    const res: any = await post(`/trading/trade-plans/${p.planUuid}/preview`, payload);
    const data = res?.data ?? res;
    const previewId = typeof data?.previewId === "string" ? data.previewId : "";
    if (!previewId) {
      ElMessage.error(res?.message || "预检失败");
      return;
    }
    upsertPlan(p.planUuid, { previewId });
    ElMessage.success("预检通过");
  } catch (e: any) {
    ElMessage.error(e?.message || "预检失败");
  } finally {
    isChatting.value = false;
  }
};

const tradePlanDetailOpen = ref(false);
const tradePlanDetailPlan = ref<TradingPlanItem | null>(null);

const openTradePlanDetail = (p: TradingPlanItem) => {
  tradePlanDetailPlan.value = p;
  tradePlanDetailOpen.value = true;
};

const tradePlanConfirmOpen = ref(false);
const tradePlanConfirmPlan = ref<TradingPlanItem | null>(null);
const tradePlanConfirmChecked = ref(false);

const openTradePlanConfirm = (p: TradingPlanItem) => {
  tradePlanConfirmPlan.value = p;
  tradePlanConfirmChecked.value = false;
  tradePlanConfirmOpen.value = true;
};

const doTradePlanConfirm = async () => {
  const p = tradePlanConfirmPlan.value;
  if (!p || !p.previewId) return;
  if (!tradePlanConfirmChecked.value) return;
  tradePlanConfirmOpen.value = false;
  await confirmTradePlan(p);
};

const tradePlanCreateOpen = ref(false);
const tradePlanCreateForm = reactive({
  accountId: "",
  previewType: "OPEN" as "OPEN" | "CLOSE",
  symbol: "",
  side: "LONG" as "LONG" | "SHORT",
  orderType: "MARKET" as "MARKET" | "LIMIT",
  limitPrice: 0,
  quantity: 1,
  leverage: 1,
  stopLossPrice: 0,
  takeProfitPrice: 0,
  name: "",
  note: "",
});

const makeDefaultPlanName = (symbol: string, side: string) => {
  const now = new Date();
  const hh = String(now.getHours()).padStart(2, "0");
  const mm = String(now.getMinutes()).padStart(2, "0");
  return `${symbol || "PLAN"}-${side || "LONG"}-${hh}${mm}`;
};

const extractTradePlanDraftFromText = (text: string) => {
  const t = String(text || "");
  const symbolMatch = t.match(/([A-Za-z0-9]{2,12}[-/][A-Za-z0-9]{2,12}(?:-SWAP)?)/);
  const symbol = symbolMatch ? symbolMatch[1].replace("/", "-").toUpperCase() : "";
  const side =
    /做空|SHORT/i.test(t) ? "SHORT" : /做多|LONG/i.test(t) ? "LONG" : "";
  const qtyMatch = t.match(/(\d+(?:\.\d+)?)\s*张/);
  const quantity = qtyMatch ? Math.max(1, Math.floor(Number(qtyMatch[1]))) : 1;
  const levMatch = t.match(/杠杆[^0-9]*(\d+)|(\d+)\s*[xX倍]/);
  const leverage = levMatch ? Math.max(1, Number(levMatch[1] || levMatch[2])) : 1;
  const slMatch = t.match(/止损[^0-9]*(\d+(?:\.\d+)?)/);
  const stopLossPrice = slMatch ? Number(slMatch[1]) : 0;
  const tpMatch = t.match(/止盈[^0-9]*(\d+(?:\.\d+)?)/);
  const takeProfitPrice = tpMatch ? Number(tpMatch[1]) : 0;
  const orderType = /限价|LIMIT/i.test(t) ? "LIMIT" : "MARKET";
  const limitMatch = t.match(/限价[^0-9]*(\d+(?:\.\d+)?)/);
  const limitPrice = limitMatch ? Number(limitMatch[1]) : 0;
  return { symbol, side, quantity, leverage, stopLossPrice, takeProfitPrice, orderType, limitPrice };
};

const openCreateTradePlan = (draft?: Partial<typeof tradePlanCreateForm>) => {
  tradePlanCreateForm.accountId = selectedAccountId.value || (accounts.value?.[0]?.id || "");
  tradePlanCreateForm.previewType = "OPEN";
  tradePlanCreateForm.symbol = currentSymbol.value || "";
  tradePlanCreateForm.side = "LONG";
  tradePlanCreateForm.orderType = "MARKET";
  tradePlanCreateForm.limitPrice = 0;
  tradePlanCreateForm.quantity = 1;
  tradePlanCreateForm.leverage = 1;
  tradePlanCreateForm.stopLossPrice = 0;
  tradePlanCreateForm.takeProfitPrice = 0;
  tradePlanCreateForm.note = "";
  if (draft) Object.assign(tradePlanCreateForm, draft);
  tradePlanCreateForm.name = tradePlanCreateForm.name || makeDefaultPlanName(tradePlanCreateForm.symbol, tradePlanCreateForm.side);
  tradePlanCreateOpen.value = true;
};

const tradePlanDraftLabel = (draft: TradePlanDraft, idx: number) => {
  const pc: any = draft?.planContent || {};
  const symbol = typeof pc.symbol === "string" ? pc.symbol : "";
  const side = typeof pc.side === "string" ? pc.side : "";
  const qty = pc.quantity != null ? String(pc.quantity) : "";
  const pt = draft.previewType === "CLOSE" ? "平仓" : "开仓";
  const core = [symbol, side].filter(Boolean).join(" ");
  const tail = qty ? ` · ${qty}张` : "";
  return `${idx + 1}. ${pt}${core ? ` · ${core}` : ""}${tail}`;
};

const openCreateTradePlanFromDraft = (draft: TradePlanDraft) => {
  if (!draft) return;
  const pc: any = draft.planContent || {};
  const symbol = typeof pc.symbol === "string" ? pc.symbol : currentSymbol.value || "";
  const side = typeof pc.side === "string" ? pc.side : "LONG";
  const orderType =
    typeof pc.orderType === "string" ? pc.orderType : (pc.entry?.type === "LIMIT" ? "LIMIT" : "MARKET");
  const limitPrice = pc.limitPrice != null ? Number(pc.limitPrice) : pc.entry?.price != null ? Number(pc.entry.price) : 0;
  const quantity = pc.quantity != null ? Math.max(1, Number(pc.quantity)) : 1;
  const leverage = pc.leverage != null ? Math.max(1, Number(pc.leverage)) : 1;
  const stopLossPrice = pc.stopLoss?.price != null ? Number(pc.stopLoss.price) : 0;
  const takeProfitPrice = Array.isArray(pc.takeProfit) && pc.takeProfit[0]?.price != null ? Number(pc.takeProfit[0].price) : 0;
  const note = typeof (draft.trace as any)?.note === "string" ? String((draft.trace as any).note) : "";
  const accountId = typeof pc.accountId === "string" ? pc.accountId : "";

  openCreateTradePlan({
    accountId: accountId || selectedAccountId.value || (accounts.value?.[0]?.id || ""),
    previewType: draft.previewType === "CLOSE" ? "CLOSE" : "OPEN",
    symbol,
    side: (String(side).toUpperCase() as any) || "LONG",
    orderType: (String(orderType).toUpperCase() as any) || "MARKET",
    limitPrice: isFinite(limitPrice) ? limitPrice : 0,
    quantity: isFinite(quantity) ? quantity : 1,
    leverage: isFinite(leverage) ? leverage : 1,
    stopLossPrice: isFinite(stopLossPrice) ? stopLossPrice : 0,
    takeProfitPrice: isFinite(takeProfitPrice) ? takeProfitPrice : 0,
    name: typeof draft.name === "string" ? draft.name : "",
    note,
  });
};

const openCreateTradePlanFromMessage = (msg: any, idx = 0) => {
  const id = Number(msg?.id);
  const drafts = tradePlanDraftsFor(id);
  const draft = drafts[idx];
  if (!draft) {
    ElMessage.warning("未检测到可执行的交易计划草案");
    return;
  }
  openCreateTradePlanFromDraft(draft);
};

const openCreateTradePlanFromAssistantText = (msg: any) => {
  const text = typeof msg?.content === "string" ? msg.content : String(msg?.content ?? "");
  const extracted = extractTradePlanDraftFromText(text);
  const symbol = extracted.symbol || currentSymbol.value || "";
  const side = (extracted.side as any) || "LONG";
  openCreateTradePlan({
    accountId: selectedAccountId.value || (accounts.value?.[0]?.id || ""),
    previewType: "OPEN",
    symbol,
    side,
    orderType: extracted.orderType as any,
    limitPrice: extracted.limitPrice,
    quantity: extracted.quantity,
    leverage: extracted.leverage,
    stopLossPrice: extracted.stopLossPrice,
    takeProfitPrice: extracted.takeProfitPrice,
    note: "来自实时建议文本提取",
  });
};

const createTradePlanFromDraft = async (draft: TradePlanDraft) => {
  const pc: any = draft?.planContent || {};
  const accountId = typeof pc.accountId === "string" ? pc.accountId : selectedAccountId.value || (accounts.value?.[0]?.id || "");
  if (!accountId) {
    throw new Error("缺少 accountId");
  }
  const symbol = typeof pc.symbol === "string" ? pc.symbol : "";
  const side = typeof pc.side === "string" ? pc.side : "";
  const name = typeof draft.name === "string" && draft.name.trim() ? draft.name.trim() : makeDefaultPlanName(symbol, side);
  const payload: any = {
    accountId,
    name,
    description: "",
    previewType: draft.previewType === "CLOSE" ? "CLOSE" : "OPEN",
    planContent: pc,
    trace: draft.trace && typeof draft.trace === "object" ? draft.trace : undefined,
  };
  await post("/trading/trade-plans", payload);
};

const createTradePlansFromMessage = async (msg: any) => {
  if (isChatting.value) return;
  const id = Number(msg?.id);
  const drafts = tradePlanDraftsFor(id);
  if (!drafts.length) {
    ElMessage.warning("未检测到可执行的交易计划草案");
    return;
  }
  try {
    isChatting.value = true;
    let ok = 0;
    for (const d of drafts) {
      try {
        await createTradePlanFromDraft(d);
        ok += 1;
      } catch (_) {}
    }
    tradePlanFilter.value = "pending";
    await loadTradePlansFromDb();
    ElMessage.success(`已生成交易计划：${ok}/${drafts.length}`);
  } catch (e: any) {
    ElMessage.error(e?.message || "批量生成失败");
  } finally {
    isChatting.value = false;
  }
};

const onTradePlanDraftCommand = async (msg: any, cmd: any) => {
  const t = typeof cmd?.type === "string" ? cmd.type : "";
  if (t === "single") {
    const idx = Number(cmd?.idx);
    openCreateTradePlanFromMessage(msg, Number.isFinite(idx) ? idx : 0);
    return;
  }
  if (t === "batch") {
    await createTradePlansFromMessage(msg);
  }
};

const submitCreateTradePlan = async () => {
  if (isChatting.value) return;
  try {
    isChatting.value = true;
    const planContent: any = {
      accountId: tradePlanCreateForm.accountId,
      symbol: tradePlanCreateForm.symbol,
      side: tradePlanCreateForm.side,
      orderType: tradePlanCreateForm.orderType,
      quantity: tradePlanCreateForm.quantity,
      leverage: tradePlanCreateForm.leverage,
    };
    if (tradePlanCreateForm.orderType === "LIMIT" && tradePlanCreateForm.limitPrice > 0) {
      planContent.limitPrice = tradePlanCreateForm.limitPrice;
      planContent.entry = { type: "LIMIT", price: tradePlanCreateForm.limitPrice };
    } else {
      planContent.entry = { type: "MARKET" };
    }
    if (tradePlanCreateForm.stopLossPrice > 0) {
      planContent.stopLoss = { type: "FIXED", price: tradePlanCreateForm.stopLossPrice };
    }
    if (tradePlanCreateForm.takeProfitPrice > 0) {
      planContent.takeProfit = [{ price: tradePlanCreateForm.takeProfitPrice, volume: 1 }];
    }
    const payload = {
      name: tradePlanCreateForm.name,
      description: "",
      previewType: tradePlanCreateForm.previewType,
      planContent,
      trace: tradePlanCreateForm.note ? { note: tradePlanCreateForm.note } : undefined,
    };
    await post("/trading/trade-plans", payload);
    tradePlanCreateOpen.value = false;
    tradePlanFilter.value = "pending";
    await loadTradePlansFromDb();
    ElMessage.success("已生成交易计划");
  } catch (e: any) {
    ElMessage.error(e?.message || "生成交易计划失败");
  } finally {
    isChatting.value = false;
  }
};

const tradePlanCardRef = ref<any>(null);

const onBizPillClick = async (key: BizPillKey) => {
  if (isChatting.value) return;
  if (activeBizPill.value === key) {
    activeBizPill.value = null;
    return;
  }
  activeBizPill.value = key;
  if (key === "trade_plans") {
    tradePlanFilter.value = "pending";
    await nextTick();
    const el = tradePlanCardRef.value?.$el || tradePlanCardRef.value;
    if (el && typeof el.scrollIntoView === "function") {
      el.scrollIntoView({ behavior: "smooth", block: "start" });
    }
    return;
  }
  if (key === "commander") {
    userInput.value = "";
    await nextTick();
    const input = document.querySelector(".chat-input-top input") as HTMLInputElement | null;
    input?.focus();
    return;
  }
  if (key === "live_advice") {
    userInput.value = "";
    await nextTick();
    const input = document.querySelector(".chat-input-top input") as HTMLInputElement | null;
    input?.focus();
    return;
  }
  if (key === "recap") {
    userInput.value = "今日复盘";
    await nextTick();
    const input = document.querySelector(".chat-input-top input") as HTMLInputElement | null;
    input?.focus();
  }
};

const loadTradePlansFromDb = async () => {
  try {
    const res: any = await get("/trading/trade-plans", { params: { limit: 50 } });
    const rows: any[] = Array.isArray(res) ? res : Array.isArray(res?.data) ? res.data : [];
    const nextPlans: TradingPlanItem[] = [];
    for (const r of rows) {
      const planUuid = typeof r?.planUuid === "string" ? r.planUuid : "";
      if (!planUuid) continue;

      const statusRaw = typeof r?.status === "string" ? r.status : "";
      const status: TradingPlanStatus =
        statusRaw === "executed" ? "executed" : statusRaw === "failed" ? "failed" : "pending";

      const previewType = typeof r?.previewType === "string" ? r.previewType : undefined;
      const previewId = typeof r?.previewId === "string" ? r.previewId : undefined;

      const planContent =
        r?.planContent && typeof r.planContent === "object" ? (r.planContent as TradePlanContent) : undefined;
      const trace = r?.trace && typeof r.trace === "object" ? (r.trace as TradePlanTrace) : undefined;
      const executionResult = r?.executionResult != null ? r.executionResult : undefined;

      const name = typeof r?.name === "string" && r.name ? r.name : "";
      const updatedAtMs =
        typeof r?.updatedAtMs === "number"
          ? r.updatedAtMs
          : typeof r?.createdAtMs === "number"
            ? r.createdAtMs
            : Date.now();
      const summary = summarizePlanContent(planContent) || "";

      nextPlans.push({
        id: String(r?.id || planUuid),
        planUuid,
        previewId,
        previewType,
        status,
        name: name || [planContent?.symbol, planContent?.side].filter(Boolean).join(" ") || "trade_plan",
        time: fmtHm(updatedAtMs),
        summary,
        planContent,
        trace,
        executionResult,
        updatedAtMs,
      });
    }
    nextPlans.sort((a, b) => (b.updatedAtMs || 0) - (a.updatedAtMs || 0));
    tradingPlans.value = nextPlans;
  } catch (_) {
    ElMessage.error("加载交易计划失败");
  }
};

// 事件日志数据
const eventLogs = ref([
  { id: 1, type: "risk", typeText: "风险拦截", time: "14:44:53", content: "[risk_manager] 风险管理器拦截: Rebalance: 3 trades, max deviation 90..." },
  { id: 2, type: "risk", typeText: "风险拦截", time: "14:44:53", content: "[risk_manager] 风险管理器拦截: Grid Buy: BTC/USDT @ $70217.16..." },
  { id: 3, type: "risk", typeText: "风险拦截", time: "14:44:53", content: "[risk_manager] 风险管理器拦截: Grid Buy: BTC/USDT @ $69535.44..." },
  { id: 4, type: "risk", typeText: "风险拦截", time: "14:44:53", content: "[risk_manager] 风险管理器拦截: Grid Buy: BTC/USDT @ $68853.44..." }
]);

// 响应式数据
const selectedAccountId = ref("");
const accounts = ref([]);
const selectedAccount = ref(null);
const balances = ref([]);
const positions = ref([]);
const orders = ref([]);
const currentSymbol = ref("");
const currentPrice = ref(0);
const priceChange = ref(0);
const buyOrders = ref([]);
const sellOrders = ref([]);
const recentTrades = ref([]);
const showAddAccount = ref(false);
const searchSymbol = ref("");
const activeTab = ref("buy");
const chartTimeframe = ref("1h");

// 表单数据
const buyForm = reactive({
  price: 0,
  amount: 0,
  type: "limit",
});

const sellForm = reactive({
  price: 0,
  amount: 0,
  type: "limit",
});

const newAccount = reactive({
  name: "",
  exchange: "binance",
  apiKey: "",
  apiSecret: "",
  passphrase: "",
  testnet: false,
});

let pollIntervalId: number | null = null;
let isPolling = false;

const focusXiaolingbaoPanel = () => {
  const el = document.querySelector(".ai-communication-card") as HTMLElement | null;
  if (!el) return;
  el.scrollIntoView({ behavior: "smooth", block: "start" });
};

// 初始化
onMounted(async () => {
  loadChatHistory();
  await loadAccounts();
  await loadBots();
  await loadTickerItems();
  await loadTradePlansFromDb();
  await restoreRuntimeSelection();
  if (props.initialFocus === "xiaolingbao") {
    nextTick(() => focusXiaolingbaoPanel());
  }
});

onUnmounted(() => {
  stopPolling();
  chatAbortController?.abort();
  aiMarketSummaryAbortController?.abort();
  persistChatHistoryNow();
});

// 加载账户列表
const loadAccounts = async () => {
  try {
    const response = await exchangeApi.getAccounts();
    accounts.value = response.data;
  } catch (error) {
    ElMessage.error("加载账户失败");
  }
};

const loadBots = async () => {
  try {
    const res: any = await get("/trading-bots", { params: { page: 1, limit: 200 } });
    const records: any[] = Array.isArray(res?.data?.records) ? res.data.records : [];
    bots.value = records;
  } catch (_) {
    bots.value = [];
  }
};

const readBotDailyTarget = (bot: any) => {
  const cfg = parseJsonObject(bot?.configuration || "");
  const flatCandidates = [
    cfg?.dailyTarget,
    cfg?.daily_target,
    cfg?.targetTotal,
    cfg?.target_total,
    cfg?.risk?.dailyTarget,
    cfg?.risk?.targetTotal,
    cfg?.targets?.daily,
    cfg?.targets?.dailyTarget,
  ];
  for (const v of flatCandidates) {
    const n = Number(v);
    if (Number.isFinite(n) && n > 0) return n;
  }
  return null;
};

const readBotDailyTargetLabel = (bot: any) => {
  const cfg = parseJsonObject(bot?.configuration || "");
  const labelCandidates = [cfg?.dailyTargetLabel, cfg?.targets?.label, cfg?.targets?.dailyLabel];
  for (const v of labelCandidates) {
    const s = typeof v === "string" ? v.trim() : "";
    if (s) return s;
  }
  return "";
};

const clearBotSelection = () => {
  selectedBotId.value = "";
  selectedBot.value = null;
  botDailyTargetTotal.value = null;
  botDailyTargetLabel.value = "";
  refreshTradingSummary({ silent: true });
};

const onBotChange = async (botId: string) => {
  const id = String(botId || "").trim();
  if (!id) return;
  selectedBotId.value = id;
  let bot = (bots.value as any[]).find((b) => String(b?.botId || b?.id || "") === id) || null;
  try {
    const detail: any = await get(`/trading-bots/${id}`);
    const dto = detail?.data && typeof detail.data === "object" ? detail.data : detail;
    if (dto && typeof dto === "object") bot = dto;
  } catch {
  }
  selectedBot.value = bot;
  botDailyTargetTotal.value = readBotDailyTarget(bot);
  botDailyTargetLabel.value = readBotDailyTargetLabel(bot);

  const accountId = String(bot?.accountId || "").trim();
  if (accountId) {
    await onAccountChange(accountId);
  }
  const pair = String(bot?.tradingPair || "").trim();
  if (pair) {
    currentSymbol.value = pair;
    await loadSymbolData({ silent: true });
  }
  await refreshTradingSummary({ silent: true });
  await refreshMarketAnalysis({ silent: true });
  await refreshMarketAnalysisBatch({ silent: true });
};

const enterBot = async (bot: any) => {
  const id = botKeyOf(bot);
  if (!id) return;
  await onBotChange(id);
};

const toggleBot = async (bot: any) => {
  const id = botKeyOf(bot);
  if (!id) return;
  const status = String(bot?.status || "").toUpperCase();
  const action =
    status === "RUNNING"
      ? "pause"
      : status === "PAUSED"
        ? "resume"
        : "start";
  try {
    await post(`/trading-bots/${id}/${action}`);
    await loadBots();
    if (selectedBotId.value === id) {
      await onBotChange(id);
    }
    ElMessage.success("操作成功");
  } catch (e: any) {
    ElMessage.error(e?.message || "操作失败");
  }
};

const openBotLogs = (bot: any) => {
  const accountId = String(bot?.accountId || selectedAccountId.value || "").trim();
  router.push({
    path: "/trading-logs",
    query: accountId ? { accountId } : undefined,
  });
};

const restoreRuntimeSelection = async () => {
  const savedBotId = String(localStorage.getItem(RT_SELECTED_BOT_KEY) || "").trim();
  const savedAccountId = String(localStorage.getItem(RT_SELECTED_ACCOUNT_KEY) || "").trim();

  if (savedBotId && (bots.value as any[]).some((b) => String(b?.botId || b?.id || "") === savedBotId)) {
    await onBotChange(savedBotId);
    return;
  }
  if (savedAccountId && accounts.value.some((a) => a.id === savedAccountId)) {
    await onAccountChange(savedAccountId);
    await refreshMarketAnalysis({ silent: true });
    await refreshMarketAnalysisBatch({ silent: true });
    return;
  }
  if ((bots.value as any[]).length > 0) {
    const first = bots.value[0];
    const id = String(first?.botId || first?.id || "").trim();
    if (id) {
      await onBotChange(id);
      return;
    }
  }
  if (accounts.value.length > 0) {
    await onAccountChange(accounts.value[0].id);
    await refreshMarketAnalysis({ silent: true });
    await refreshMarketAnalysisBatch({ silent: true });
  }
};

watch(selectedBotId, (v) => {
  const s = String(v || "").trim();
  if (s) localStorage.setItem(RT_SELECTED_BOT_KEY, s);
  else localStorage.removeItem(RT_SELECTED_BOT_KEY);
});

watch(selectedAccountId, (v) => {
  const s = String(v || "").trim();
  if (s) localStorage.setItem(RT_SELECTED_ACCOUNT_KEY, s);
  else localStorage.removeItem(RT_SELECTED_ACCOUNT_KEY);
});

watch(marketAnalysisInterval, () => {
  refreshMarketAnalysis({ silent: true });
  refreshMarketAnalysisBatch({ silent: true });
});

// 账户变更处理
const onAccountChange = async (accountId) => {
  selectedAccountId.value = accountId;
  selectedAccount.value = accounts.value.find((a) => a.id === accountId);

  if (selectedAccount.value) {
    await loadAccountData();
    await refreshTradingSummary({ silent: true });
    startPolling();
  }
};

// 加载账户数据
const loadAccountData = async (opts?: { silent?: boolean }) => {
  try {
    const accountRes = await exchangeApi.getAccount(selectedAccountId.value);

    const accountData: any = accountRes?.data ?? {};
    const balanceData: any = accountData?.balance ?? null;
    const positionData: any = Array.isArray(accountData?.positions) ? accountData.positions : null;

    if (balanceData) {
      // 兼容对象或数组两种形态
      // 若为对象且包含 assets 列表，则使用 assets；否则直接赋值
      if (Array.isArray(balanceData)) {
        balances.value = balanceData;
      } else if (Array.isArray(balanceData.assets)) {
        balances.value = balanceData.assets;
      } else {
        balances.value = balanceData;
      }
    } else {
      // 不再回退调用 /balance，避免无用请求
      balances.value = [];
    }

    if (positionData) {
      positions.value = positionData;
    } else {
      // 不再回退调用 /positions，避免无用请求
      positions.value = [];
    }
    if (Array.isArray(accountData?.orders)) {
      orders.value = accountData.orders;
      stats.trades = accountData.orders.length;
    }
    updateAccountHeaderBalance();
  } catch (error) {
    if (!opts?.silent) ElMessage.error("加载账户数据失败");
  }
};

const startPolling = () => {
  if (pollIntervalId) return;
  pollIntervalId = window.setInterval(async () => {
    if (isPolling) return;
    if (!selectedAccountId.value) return;
    isPolling = true;
    try {
      await loadAccountData({ silent: true });
      if (currentSymbol.value) {
        await loadSymbolData({ silent: true });
      }
      await refreshTradingSummary({ silent: true });
      await refreshMarketAnalysis({ silent: true });
      await refreshMarketAnalysisBatch({ silent: true });
    } finally {
      isPolling = false;
    }
  }, POLL_INTERVAL_MS);
};

const stopPolling = () => {
  if (pollIntervalId) {
    clearInterval(pollIntervalId);
    pollIntervalId = null;
  }
  isPolling = false;
};

// 订阅交易对
const subscribeSymbol = () => {
  if (!searchSymbol.value || !selectedAccountId.value) {
    ElMessage.warning("请选择账户和输入交易对");
    return;
  }

  currentSymbol.value = searchSymbol.value;
  startPolling();

  // 加载历史数据
  loadSymbolData();
};

// 加载交易对数据
const loadSymbolData = async (opts?: { silent?: boolean }) => {
  if (!selectedAccountId.value || !currentSymbol.value) return;
  const reqConfig = opts?.silent ? { silent: true } : undefined;
  try {
    const [tickerRes, orderbookRes, tradesRes] = await Promise.allSettled([
      exchangeApi.getTicker(selectedAccountId.value, currentSymbol.value, reqConfig),
      exchangeApi.getOrderBook(selectedAccountId.value, currentSymbol.value, undefined, reqConfig),
      exchangeApi.getTrades(selectedAccountId.value, currentSymbol.value, undefined, reqConfig),
    ]);

    if (tickerRes.status === "fulfilled") {
      currentPrice.value = (tickerRes.value as any)?.data?.last;
      priceChange.value = (tickerRes.value as any)?.data?.changePercent;
    }
    if (orderbookRes.status === "fulfilled") {
      updateOrderbook((orderbookRes.value as any)?.data);
    }
    if (tradesRes.status === "fulfilled") {
      const list = ((tradesRes.value as any)?.data || []) as any[];
      recentTrades.value = Array.isArray(list) ? list.slice(0, 20) : [];
    }
  } catch (error) {
    if (!opts?.silent) ElMessage.error("加载交易对数据失败");
  }
};

// 更新买卖盘
const updateOrderbook = (orderbook) => {
  buyOrders.value = orderbook.bids;
  sellOrders.value = orderbook.asks;
};

// 更新最近交易
const updateRecentTrades = (trade) => {
  recentTrades.value.unshift(trade);
  if (recentTrades.value.length > 50) {
    recentTrades.value = recentTrades.value.slice(0, 50);
  }
};

// 交易对展示名格式化：如 SOL-USDT-SWAP -> SOL永续，ETH-USDT-SWAP -> ETH永续
const formatSymbolDisplay = (symbol: string): string => {
  if (!symbol) return "";
  const m = symbol.match(/^([A-Z0-9]+)-[A-Z0-9]+-SWAP$/i);
  if (m && m[1]) {
    return m[1].toUpperCase() + "永续";
  }
  return symbol.toUpperCase();
};

// 加载跑马灯Ticker数据（默认使用15m周期）
const loadTickerItems = async () => {
  try {
    const res = await getLatestTickers({ limit: 50 });
    const body = res as { code: number; data: LatestTicker[] };
    const list = Array.isArray(body.data) ? body.data : [];
    tickerItems.value = list.map((t) => {
      const closeNum = t.close != null ? Number(t.close) : NaN;
      const price =
        Number.isFinite(closeNum) && closeNum !== 0
          ? closeNum.toFixed(closeNum >= 100 ? 2 : 4)
          : "-";
      const changeNum =
        t.changePercent != null ? Number(t.changePercent) : 0;
      return {
        symbol: t.symbol,
        displaySymbol: formatSymbolDisplay(t.symbol),
        price,
        change: Number.isFinite(changeNum)
          ? Math.round(changeNum * 100) / 100
          : 0,
      };
    });
  } catch (e) {
    // 静默失败，不影响页面其他功能
  }
};

const extractApiData = (res: any) => {
  if (res && typeof res === "object" && "data" in res) return (res as any).data;
  return res;
};

const applySentiment = (a: MarketAnalysis | null) => {
  const s = Number(a?.sentimentScore);
  const score = Number.isFinite(s) ? Math.max(0, Math.min(100, s)) : 50;
  sentimentScore.value = score;
  const label = String(a?.sentimentLabel || "Neutral");
  sentimentLabel.value = label;
  if (label === "Bullish") sentimentColor.value = "#2ecc71";
  else if (label === "Bearish") sentimentColor.value = "#e74c3c";
  else if (label === "Greed") sentimentColor.value = "#27ae60";
  else if (label === "Fear") sentimentColor.value = "#c0392b";
  else sentimentColor.value = "#808080";
};

const refreshTradingSummary = async ({ silent }: { silent?: boolean } = {}) => {
  if (!selectedAccountId.value) return;
  try {
    const resp: any = await get("/trading/summary", {
      params: {
        accountId: selectedAccountId.value,
        robotId: selectedBotId.value || undefined,
      },
    });
    const data = extractApiData(resp);
    const dailyPnL = Number(data?.dailyPnL);
    if (Number.isFinite(dailyPnL)) {
      stats.pnl = dailyPnL;
      stats.targetCurrent = dailyPnL;
    }
    const target = botDailyTargetTotal.value;
    if (Number.isFinite(Number(target)) && Number(target) > 0) stats.targetTotal = Number(target);
  } catch (e: any) {
    if (!silent) ElMessage.error(e?.message || "加载账户汇总失败");
  }
};

const formatPrice = (v: any) => {
  const n = Number(v);
  if (!Number.isFinite(n)) return "-";
  const abs = Math.abs(n);
  if (abs >= 1000) return n.toFixed(2);
  if (abs >= 1) return n.toFixed(4);
  return n.toFixed(8);
};

const refreshMarketAnalysis = async ({ silent }: { silent?: boolean } = {}) => {
  const now = Date.now();
  if (silent && now - lastMarketAnalysisAtMs.value < 8000) return;
  if (!currentSymbol.value) return;
  lastMarketAnalysisAtMs.value = now;
  try {
    const resp: any = await get("/trading/market-analysis", {
      params: { symbol: currentSymbol.value, interval: marketAnalysisInterval.value, limit: 240 },
    });
    const data = extractApiData(resp) as MarketAnalysis;
    marketAnalysis.value = data || null;
    applySentiment(marketAnalysis.value);
  } catch (e: any) {
    marketAnalysis.value = null;
    aiMarketSummaryText.value = "";
    aiMarketSummaryError.value = "";
    applySentiment(null);
    if (!silent) ElMessage.error(e?.message || "市场分析失败");
  }
};

const buildWatchlistSymbols = () => {
  const out: string[] = [];
  const pair = String(selectedBot.value?.tradingPair || "").trim();
  if (pair) out.push(pair);
  const current = String(currentSymbol.value || "").trim();
  if (current && !out.includes(current)) out.unshift(current);
  for (const t of tickerItems.value) {
    const s = String(t.symbol || "").trim();
    if (!s) continue;
    if (!out.includes(s)) out.push(s);
    if (out.length >= 6) break;
  }
  return out.slice(0, 6);
};

const refreshMarketAnalysisBatch = async ({ silent }: { silent?: boolean } = {}) => {
  const now = Date.now();
  if (silent && now - lastMarketBatchAtMs.value < 12000) return;
  lastMarketBatchAtMs.value = now;
  const symbols = buildWatchlistSymbols();
  if (symbols.length === 0) return;
  try {
    const resp: any = await post("/trading/market-analysis/batch", {
      symbols,
      interval: marketAnalysisInterval.value,
      limit: 240,
    });
    const data = extractApiData(resp);
    const list: MarketAnalysis[] = Array.isArray(data) ? data : Array.isArray(resp) ? resp : [];
    symbolSignals.value = list.slice(0, 5).map((x) => {
      const full = String(x.symbol || "");
      const trend = String(x.trendLabel || "Neutral");
      const strength = Number.isFinite(Number(x.trendStrength)) ? Number(x.trendStrength) : 50;
      const price = formatPrice(x.price);
      const change = Number.isFinite(Number(x.changePercent)) ? Number(x.changePercent) : 0;
      return { symbol: full, price, trend, label: trend, strength, change };
    });
  } catch (e: any) {
    symbolSignals.value = [];
    if (!silent) ElMessage.error(e?.message || "加载市场分析列表失败");
  }
};

const onWatchSymbolClick = async (symbol: string) => {
  const s = String(symbol || "").trim();
  if (!s) return;
  currentSymbol.value = s;
  await loadSymbolData({ silent: true });
  await refreshMarketAnalysis({ silent: true });
};

const buildMarketAnalysisReportPrompt = () => {
  const analysis = marketAnalysis.value;
  if (!analysis) return null;
  const payload = {
    analysis: {
      symbol: analysis.symbol,
      interval: analysis.interval,
      time: analysis.time,
      price: analysis.price,
      changePercent: analysis.changePercent,
      sentimentScore: analysis.sentimentScore,
      sentimentLabel: analysis.sentimentLabel,
      trendLabel: analysis.trendLabel,
      trendStrength: analysis.trendStrength,
      rsi14: analysis.rsi14,
      atr14Percent: analysis.atr14Percent,
      bollingerWidthPercent: analysis.bollingerWidthPercent,
      supports: analysis.supports,
      resistances: analysis.resistances,
      tags: analysis.tags,
    },
    watchlist: symbolSignals.value.slice(0, 5).map((x) => ({
      symbol: x.symbol,
      trend: x.trend,
      strength: x.strength,
      change: x.change,
      price: x.price,
    })),
  };

  const system =
    "你是资深量化分析师，写一段“研报风格”的中文市场解读。\n" +
    "强约束：\n" +
    "1) 只能基于用户提供的 JSON 数据做解读，不允许联网、不允许调用任何接口、不允许编造不存在的价格/成交量。\n" +
    "2) 不要输出任何 JSON / 代码块。\n" +
    "3) 输出结构：一句结论 + 3-6 条要点（趋势/动量/波动/关键位/风险） + 一条“可执行观察条件”（例如跌破/站上某价位）。\n" +
    "4) 若数据不足以判断，直接说明“数据不足”，并给出需要的字段。";

  const user =
    "请基于以下 MarketAnalysis JSON 输出研报总结：\n\n" +
    JSON.stringify(payload, null, 2);

  return { system, user };
};

const generateAiMarketSummary = async () => {
  const prompt = buildMarketAnalysisReportPrompt();
  if (!prompt) {
    aiMarketSummaryText.value = "";
    aiMarketSummaryError.value = "暂无市场分析数据，无法生成 AI 总结";
    return;
  }

  aiMarketSummaryAbortController?.abort();
  aiMarketSummaryAbortController = new AbortController();
  aiMarketSummaryLoading.value = true;
  aiMarketSummaryError.value = "";

  try {
    const useGateway = llmConfig.provider !== "ollama";
    const requestUrl = useGateway
      ? "/api/llm/generate"
      : (typeof llmConfig.generatePath === "string" && llmConfig.generatePath.trim()) || "/api/generate";

    const body = useGateway
      ? {
          stream: false,
          messages: [
            { role: "system", content: prompt.system },
            { role: "user", content: prompt.user },
          ],
        }
      : {
          model: llmConfig.model,
          stream: false,
          prompt: `${prompt.system}\n\n${prompt.user}`,
        };

    const res = await fetch(requestUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: aiMarketSummaryAbortController.signal,
    });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(text || `HTTP ${res.status}`);
    }
    const json = (await res.json()) as { response?: string; error?: string };
    if (json.error) throw new Error(json.error);
    aiMarketSummaryText.value = String(json.response ?? "").trim();
    if (!aiMarketSummaryText.value) aiMarketSummaryText.value = "数据不足，无法生成研报总结。";
  } catch (e: any) {
    const msg =
      typeof e?.message === "string"
        ? e.message
        : e instanceof Error
          ? e.message
          : String(e);
    aiMarketSummaryText.value = "";
    aiMarketSummaryError.value = msg || "AI 研报生成失败";
  } finally {
    aiMarketSummaryLoading.value = false;
  }
};

const scanMarketAnalysis = async () => {
  await refreshMarketAnalysis();
  await refreshMarketAnalysisBatch();
  await generateAiMarketSummary();
};

// 账户余额统计（用于顶部“账户 $”）
const toNum = (v: any): number => {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string") {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }
  return 0;
};

const computeAccountUsdFromBalances = (): number => {
  const b: any = balances.value as any;
  let totalUsd = 0;
  if (Array.isArray(b)) {
    for (const a of b) {
      if (!a) continue;
      // 优先使用资产的 USD 估值
      if (a.valueInUSD != null) {
        totalUsd += toNum(a.valueInUSD);
        continue;
      }
      // 若无估值，尝试以数量 * 单价估算
      const qty = toNum(a.total ?? (toNum(a.free) + toNum(a.locked)));
      const px = toNum(a.priceInUSD);
      if (qty && px) totalUsd += qty * px;
    }
  } else if (b && typeof b === "object") {
    // 若为对象，若包含总额字段，直接取用（假设为 USD）
    if (b.total != null) totalUsd = toNum(b.total);
    else if (b.available != null || b.used != null) {
      totalUsd = toNum(b.available) + toNum(b.used);
    } else if (Array.isArray(b.assets)) {
      for (const a of b.assets) {
        if (!a) continue;
        if (a.valueInUSD != null) totalUsd += toNum(a.valueInUSD);
        else {
          const qty = toNum(a.total ?? (toNum(a.free) + toNum(a.locked)));
          const px = toNum(a.priceInUSD);
          if (qty && px) totalUsd += qty * px;
        }
      }
    }
  }
  return Number(totalUsd.toFixed(2));
};

const updateAccountHeaderBalance = () => {
  if (!selectedAccountId.value) {
    stats.account = 0;
    return;
  }
  stats.account = computeAccountUsdFromBalances();
};

// 下单
const placeOrder = async (side) => {
  if (!selectedAccountId.value || !currentSymbol.value) {
    ElMessage.warning("请选择账户和交易对");
    return;
  }

  const formData = side === "buy" ? buyForm : sellForm;

  try {
    const orderData = {
      symbol: currentSymbol.value,
      type: formData.type,
      side: side,
      amount: formData.amount.toString(),
      price: formData.type === "limit" ? formData.price.toString() : undefined,
    };

    await exchangeApi.placeOrder(selectedAccountId.value, orderData);
    await loadAccountData({ silent: true });

    ElMessage.success("下单成功");
  } catch (error) {
    ElMessage.error("下单失败");
  }
};

// 取消订单
const cancelOrder = async (order) => {
  try {
    await exchangeApi.cancelOrder(
      selectedAccountId.value,
      order.id,
      order.symbol,
    );
    await loadAccountData({ silent: true });

    ElMessage.success("取消订单成功");
  } catch (error) {
    ElMessage.error("取消订单失败");
  }
};

// 刷新数据
const refreshBalance = async () => {
  await loadAccountData();
};

const refreshOrders = async () => {};

// 添加账户
const addAccount = async () => {
  try {
    await exchangeApi.createAccount(newAccount);
    ElMessage.success("添加账户成功");
    showAddAccount.value = false;
    await loadAccounts();

    // 重置表单
    Object.assign(newAccount, {
      name: "",
      exchange: "binance",
      apiKey: "",
      apiSecret: "",
      passphrase: "",
      testnet: false,
    });
  } catch (error) {
    ElMessage.error("添加账户失败");
  }
};

// 工具函数
const formatTime = (time) => {
  if (!time) return "从未";
  return new Date(time).toLocaleString();
};

const getOrderStatusType = (status) => {
  const statusMap = {
    open: "warning",
    closed: "success",
    cancelled: "info",
    rejected: "danger",
  };
  return statusMap[status] || "info";
};

const getExchangeName = (exchange) => {
  const exchangeMap = {
    binance: "币安",
    okx: "OKX",
  };
  return exchangeMap[exchange] || exchange;
};

const getExchangeIcon = (exchange) => {
  const iconMap = {
    binance: "Coin",
    okx: "Wallet",
  };
  return iconMap[exchange] || "Wallet";
};

const getStatusText = (status) => {
  const statusMap = {
    connected: "已连接",
    disconnected: "未连接",
    connecting: "连接中",
    error: "错误",
  };
  return statusMap[status] || status;
};

// 市场状态
const marketStatus = ref({
  type: "success",
  text: "市场开放",
});

const marketTime = ref(new Date().toLocaleTimeString());

// 更新市场时间
setInterval(() => {
  marketTime.value = new Date().toLocaleTimeString();
}, 1000);
</script>

<style scoped>
.real-time-trading {
  padding: 15px;
  height: 100%;
  overflow-y: auto;
  background: var(--primary-bg);
  color: var(--text-primary);
}

/* 顶部状态栏 V3 - 修正排版 */
.trading-header-v3 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: var(--secondary-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  margin-bottom: 15px;
  box-shadow: var(--card-shadow);
}

.account-tab {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.account-toolbar {
  display: flex;
  gap: 10px;
}

.account-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.account-card {
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--secondary-bg);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.account-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15) inset;
}

.account-card.is-active {
  border-color: var(--el-color-success);
  box-shadow: 0 0 0 2px rgba(103, 194, 58, 0.2) inset;
}

.account-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.account-name {
  font-weight: 600;
  color: var(--text-primary);
}

.account-meta {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: var(--text-secondary);
}

.header-left-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 15px;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.logo-area-v3 {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-svg-v3 {
  display: flex;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 0 5px rgba(64, 158, 255, 0.3));
}

.logo-text-v3 {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--accent-blue);
  text-shadow: 0 0 10px rgba(64, 158, 255, 0.2);
}

.version-text-v3 {
  font-size: 12px;
  color: var(--accent-blue);
  font-family: monospace;
  font-weight: bold;
}

.status-line-v3 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot-v3 {
  width: 8px;
  height: 8px;
  background: var(--accent-green);
  border-radius: 50%;
  box-shadow: 0 0 5px var(--accent-green);
}

.status-text-v3 {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.tick-text-v3 {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: 10px;
  font-family: monospace;
}

.header-right-v3 {
  display: flex;
  align-items: center;
}

.controls-row-v3 {
  display: flex;
  align-items: center;
  gap: 20px;
}

.settings-gear-v3 {
  font-size: 20px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.3s ease;
  padding: 8px;
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
}

.settings-gear-v3:hover {
  transform: rotate(90deg);
  color: var(--accent-blue);
  background: var(--tertiary-bg);
}

.agent-config-empty {
  padding: 12px 0;
  color: var(--text-muted);
}

.llm-config-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.llm-active-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.llm-active-label {
  width: 110px;
  color: var(--text-muted);
  font-size: 13px;
}

.llm-section-title {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-color);
  font-weight: 600;
}

.llm-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-muted);
}

.llm-provider-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.llm-provider-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.llm-provider-card:hover {
  border-color: var(--accent-blue);
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.25);
}

.llm-provider-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.llm-provider-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-color);
}

.llm-provider-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.llm-provider-editor {
  padding: 10px 14px;
  background: var(--secondary-bg);
  border: 1px solid var(--border-color);
  border-radius: 10px;
}

.tag-group-v3 {
  display: flex;
  gap: 8px;
  padding: 6px;
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 8px;
}

.custom-tag-v3 {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 4px;
  background: var(--tertiary-bg);
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.custom-tag-v3.green { color: var(--accent-green); }
.custom-tag-v3.blue { color: var(--accent-blue); }
.custom-tag-v3.purple { color: #a855f7; }
.custom-tag-v3.teal { color: #06b6d4; }

.stats-row-v3 {
  display: flex;
  gap: 12px;
}

.stat-item-v3 {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 8px 15px;
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  min-width: 90px;
}

.stat-item-v3 .s-label {
  font-size: 11px;
  color: var(--text-muted);
  text-transform: uppercase;
  margin-bottom: 4px;
}

.stat-item-v3 .s-value {
  font-size: 20px;
  font-weight: 800;
  font-family: 'Courier New', Courier, monospace;
  color: #00d2ff;
}

.stat-item-v3.highlight .s-value {
  color: var(--accent-green);
}

.stat-item-v3.target {
  min-width: 160px;
}

.target-top {
  display: flex;
  justify-content: space-between;
  width: 100%;
  margin-bottom: 6px;
}

.target-bar-v3 {
  width: 100%;
  height: 6px;
  background: var(--tertiary-bg);
  border-radius: 3px;
  overflow: hidden;
  margin: 2px 0;
}

.target-bar-v3 .bar-fill {
  height: 100%;
  background: var(--accent-blue);
  border-radius: 3px;
  box-shadow: 0 0 5px rgba(64, 158, 255, 0.5);
}

.target-bot {
  font-size: 10px;
  color: var(--text-muted);
  margin-top: 4px;
}

/* 跑马灯 Ticker */
.trading-ticker {
  background: var(--secondary-bg);
  border-bottom: 1px solid var(--border-color);
  padding: 5px 0;
  margin-bottom: 15px;
  overflow: hidden;
  white-space: nowrap;
}

.ticker-content {
  display: inline-block;
  animation: ticker 30s linear infinite;
}

@keyframes ticker {
  0% { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}

.ticker-item {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-right: 40px;
  font-size: 13px;
}

.ticker-symbol {
  font-weight: bold;
  color: var(--text-primary);
}

.ticker-price {
  color: var(--text-secondary);
}

.ticker-change.up { color: var(--accent-green); }
.ticker-change.down { color: var(--accent-red); }

/* 通用卡片样式 */
.dark-card {
  background: var(--secondary-bg) !important;
  border: 1px solid var(--border-color) !important;
  margin-bottom: 15px;
  box-shadow: var(--card-shadow);
}

.dark-card :deep(.el-card__header) {
  background: var(--tertiary-bg);
  border-bottom: 1px solid var(--border-color);
  padding: 10px 15px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.card-title-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: bold;
  color: var(--accent-blue);
}

/* 市场分析 */
.analyze-body {
  padding: 10px;
}

.fear-greed-gauge {
  text-align: center;
  margin-bottom: 10px;
}

.gauge-value {
  font-size: 24px;
  font-weight: bold;
}

.gauge-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.gauge-track {
  height: 6px;
  background: linear-gradient(90deg, var(--accent-red), var(--accent-orange), var(--accent-green));
  border-radius: 3px;
  position: relative;
  margin: 10px 0;
}

.gauge-pointer {
  position: absolute;
  top: -4px;
  width: 2px;
  height: 14px;
  background: var(--text-primary);
  box-shadow: 0 0 3px rgba(0, 0, 0, 0.2);
}

.analysis-mini {
  margin-top: 10px;
  padding: 10px;
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
}

.analysis-mini-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  padding: 2px 0;
}

.analysis-mini-label {
  color: var(--text-muted);
}

.analysis-mini-value {
  color: var(--text-primary);
  font-family: monospace;
}

.analysis-mini-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.analysis-tag {
  border: none;
}

.analysis-empty {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-muted);
}

.analysis-ai {
  margin-top: 10px;
  padding: 10px;
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
}

.analysis-ai-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.analysis-ai-empty {
  font-size: 12px;
  color: var(--text-muted);
}

.analysis-ai-error {
  font-size: 12px;
  color: var(--accent-red);
  white-space: pre-wrap;
}

.analysis-ai-body {
  font-size: 12px;
}

.symbol-signals {
  margin-top: 20px;
}

.signal-header {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 10px;
}

.signal-item {
  display: grid;
  grid-template-columns: 15px 1fr 80px 70px 60px 60px;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
}

.s-dot { width: 6px; height: 6px; border-radius: 50%; }
.s-dot.Bullish { background: var(--accent-green); }
.s-dot.Bearish { background: var(--accent-red); }
.s-dot.Neutral { background: var(--text-muted); }

.s-name { font-size: 12px; font-weight: bold; color: var(--text-primary); }
.s-price { font-size: 12px; font-family: monospace; text-align: right; color: var(--text-secondary); }
.s-label { font-size: 10px; text-align: center; border-radius: 4px; padding: 2px 4px; }
.s-label.Bullish { color: var(--accent-green); }
.s-label.Bearish { color: var(--accent-red); }

.s-bar-wrap { height: 4px; background: var(--tertiary-bg); border-radius: 2px; }
.s-bar { height: 100%; border-radius: 2px; }

.s-change {
  font-size: 11px;
  text-align: right;
  font-family: monospace;
}
.s-change.pos { color: var(--accent-green); }
.s-change.neg { color: var(--accent-red); }

/* 策略列表 */
.strategy-list-v2 { padding: 10px; }
.strategy-item-v2 {
  display: flex;
  justify-content: space-between;
  padding: 12px;
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 4px;
  margin-bottom: 8px;
}

.s-name { font-size: 13px; font-weight: bold; margin-bottom: 4px; color: var(--text-primary); }
.s-desc { font-size: 11px; color: var(--text-secondary); }
.s-status { text-align: right; }
.status-dot.online { background: var(--accent-green); }
.status-risk { font-size: 10px; color: var(--text-muted); margin-top: 4px; }

.bot-list {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bot-item {
  padding: 10px;
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 6px;
}

.bot-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.bot-name {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.bot-name-text {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bot-meta {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
}

.bot-pair {
  font-family: monospace;
  color: var(--text-primary);
}

.bot-last {
  color: var(--text-muted);
}

.bot-pnl {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
  font-size: 12px;
}

.bot-pnl-label {
  color: var(--text-muted);
}

.bot-pnl-value {
  font-family: monospace;
  font-weight: 700;
}

.bot-pnl-value.pos { color: var(--accent-green); }
.bot-pnl-value.neg { color: var(--accent-red); }

.bot-target {
  color: var(--text-muted);
}

.bot-progress {
  margin-top: 8px;
}

.bot-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 8px;
}

/* 智能体通讯 */
.chat-container-v2 {
  height: 600px;
  display: flex;
  flex-direction: column;
}

.biz-pill-bar {
  padding: 10px 6px 0;
  background: transparent;
  border-top: 0;
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.biz-pill {
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--primary-bg);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  user-select: none;
}

.biz-pill.disabled {
  opacity: 0.55;
  pointer-events: none;
}

.biz-pill.active {
  color: var(--text-primary);
  border-color: rgba(64, 158, 255, 0.6);
  background: rgba(64, 158, 255, 0.08);
}

.chat-history {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: var(--primary-bg);
}

.history-divider {
  text-align: center;
  font-size: 11px;
  color: var(--text-muted);
  margin: 20px 0;
  position: relative;
}

.history-divider::before, .history-divider::after {
  content: "";
  position: absolute;
  top: 50%;
  width: 40%;
  height: 1px;
  background: var(--border-color);
}
.history-divider::before { left: 0; }
.history-divider::after { right: 0; }

.chat-msg-v2 { margin-bottom: 25px; }
.msg-role-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  font-weight: bold;
  color: var(--accent-blue);
  margin-bottom: 5px;
}
.chat-msg-v2.user .msg-role-label { color: #009988; justify-content: flex-end; }
.msg-role-text { line-height: 18px; }
.msg-avatar {
  width: 18px;
  height: 18px;
  border-radius: 6px;
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
}
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--text-primary);
  font-size: 10px;
}
.chat-msg-v2.user { display: flex; flex-direction: column; align-items: flex-end; }

.msg-content-box {
  background: var(--secondary-bg);
  border: 1px solid var(--border-color);
  padding: 15px;
  border-radius: 8px;
  max-width: 90%;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
}

.text-msg {
  word-break: break-word;
  overflow-wrap: anywhere;
}

.markdown-body {
  width: 100%;
}

:deep(.markdown-body p) {
  margin: 0 0 10px 0;
}

:deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}

:deep(.markdown-body ul),
:deep(.markdown-body ol) {
  margin: 8px 0 10px 18px;
  padding: 0;
}

:deep(.markdown-body li) {
  margin: 4px 0;
}

:deep(.markdown-body code) {
  background: rgba(64, 158, 255, 0.12);
  border: 1px solid rgba(64, 158, 255, 0.2);
  padding: 0 6px;
  border-radius: 6px;
  font-size: 12px;
}

:deep(.markdown-body pre) {
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 12px;
  overflow: auto;
  margin: 10px 0;
}

:deep(.markdown-body pre code) {
  background: transparent;
  border: 0;
  padding: 0;
  font-size: 12px;
}

:deep(.markdown-body blockquote) {
  margin: 10px 0;
  padding: 8px 12px;
  border-left: 3px solid var(--accent-blue);
  background: rgba(64, 158, 255, 0.08);
  border-radius: 8px;
}

:deep(.markdown-body a) {
  color: var(--accent-blue);
  text-decoration: none;
}

:deep(.markdown-body a:hover) {
  text-decoration: underline;
}

:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3) {
  margin: 10px 0 8px 0;
  font-weight: 700;
}

.strategy-content {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.strategy-msg { border-left: 3px solid var(--accent-red); padding-left: 10px; }
.strategy-header { color: var(--accent-red); font-weight: bold; font-size: 11px; margin-bottom: 5px; }

.chat-input-shell {
  padding: 14px;
  background: var(--secondary-bg);
  border-top: 1px solid var(--border-color);
}

.chat-input-top {
  background: var(--primary-bg);
  border: 1px solid var(--border-color);
  border-radius: 22px;
  padding: 10px 10px 10px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-input-top input {
  flex: 1;
  background: transparent;
  border: 0;
  color: var(--text-primary);
  outline: none;
  font-size: 13px;
}

.chat-input-top input::placeholder {
  color: var(--text-muted);
}

.send-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(64, 158, 255, 0.14);
  border: 1px solid rgba(64, 158, 255, 0.35);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.send-btn.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.send-btn-icon {
  color: var(--accent-blue);
}

.stop-btn {
  height: 32px;
  padding: 0 12px;
  background: var(--primary-bg) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-secondary) !important;
}

.advice-pill {
  border-style: dashed;
}

.tool-preview-card {
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--tertiary-bg);
}

.assistant-ops {
  display: flex;
  justify-content: flex-end;
  margin: 8px 0;
}

.tool-preview-title {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.tool-preview-row {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin-bottom: 6px;
}

.tool-preview-label {
  font-size: 11px;
  color: var(--text-muted);
  min-width: 70px;
}

.tool-preview-value {
  font-size: 11px;
  color: var(--text-secondary);
  word-break: break-all;
}

.tool-preview-warnings {
  margin-top: 8px;
  border-top: 1px solid var(--border-color);
  padding-top: 8px;
}

.tool-preview-warn-title {
  font-size: 11px;
  color: var(--text-muted);
  margin-bottom: 6px;
}

.tool-preview-warn-item {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.tool-preview-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

/* AI 指挥官 */
.commander-body { padding: 15px; }
.progress-labels { display: flex; justify-content: space-between; font-size: 12px; margin-bottom: 8px; color: var(--text-secondary); }
.state-description {
  background: var(--tertiary-bg);
  padding: 15px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  font-style: italic;
  margin: 15px 0;
  line-height: 1.5;
  border: 1px solid var(--border-color);
}

.commander-stats { display: flex; gap: 20px; font-size: 11px; color: var(--text-muted); }

/* 交易计划 & 事件日志 */
.plan-list, .event-list { padding: 10px; }
.plan-item, .event-item {
  padding: 12px 10px;
  border-bottom: 1px solid var(--border-color);
}

.p-header, .e-header { display: flex; justify-content: space-between; gap: 10px; margin-bottom: 6px; }
.p-name {
  font-size: 12px;
  font-weight: bold;
  color: var(--accent-blue);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.p-time, .e-time { font-size: 10px; color: var(--text-muted); }
.p-content, .e-content { font-size: 11px; color: var(--text-secondary); line-height: 1.4; }

.p-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  flex-wrap: wrap;
  gap: 8px;
}

.p-action-buttons {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.trade-plan-detail-row {
  display: flex;
  gap: 12px;
  align-items: baseline;
  margin-bottom: 10px;
}

.trade-plan-detail-label {
  min-width: 70px;
  font-size: 12px;
  color: var(--text-muted);
}

.trade-plan-detail-value {
  font-size: 12px;
  color: var(--text-primary);
  word-break: break-all;
}

.trade-plan-detail-pre {
  margin: 8px 0 0;
  padding: 10px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--primary-bg);
  font-size: 11px;
  color: var(--text-secondary);
  max-height: 260px;
  overflow: auto;
}

.trade-plan-confirm-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.trade-plan-confirm-sub {
  font-size: 12px;
  color: var(--text-secondary);
}

.empty-list {
  padding: 18px 10px;
  font-size: 11px;
  color: var(--text-muted);
  text-align: center;
}

.e-type { font-size: 10px; padding: 1px 4px; border-radius: 2px; }
.e-type.risk { background: rgba(245, 108, 108, 0.1); color: var(--accent-red); border: 1px solid rgba(245, 108, 108, 0.2); }

.count-badge {
  background: var(--tertiary-bg);
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 10px;
  color: var(--text-muted);
}

.header-ops, .header-tabs, .header-filters {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: var(--text-muted);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.header-ops span, .header-tabs span, .header-filters span {
  cursor: pointer;
}

.header-ops span.active, .header-tabs span.active, .header-filters span.active {
  color: var(--accent-blue);
  font-weight: bold;
}

.op-btn {
  background: var(--primary-bg) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-secondary) !important;
  font-size: 10px !important;
}

/* 隐藏滚动条 */
::-webkit-scrollbar { width: 4px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: var(--border-color); border-radius: 2px; }
</style>
