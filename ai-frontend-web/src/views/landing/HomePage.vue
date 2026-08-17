<template>
  <div class="landing-page">
    <header class="landing-header" :class="{ 'header-scrolled': scrolled }">
      <nav class="nav-container">
        <a class="nav-logo" href="#top">
          <img src="@/assets/xiaolingbao-logo.svg" alt="灵猞量化" />
          <span class="nav-brand">灵猞量化</span>
        </a>
        <div class="nav-links" :class="{ 'nav-open': mobileMenuOpen }">
          <a
            v-for="item in navItems"
            :key="item.key"
            :href="item.href"
            class="nav-link"
            @click="closeMobileMenu"
          >{{ item.label }}</a>
          <div class="nav-actions-mobile">
            <el-button class="nav-btn-login" @click="openLogin">登录</el-button>
            <el-button class="nav-btn-signup" @click="openRegister">注册</el-button>
          </div>
        </div>
        <div class="nav-actions">
          <el-button class="nav-btn-login" @click="openLogin">登录</el-button>
          <el-button class="nav-btn-signup" @click="openRegister">注册</el-button>
        </div>
        <button class="mobile-menu-toggle" @click="toggleMobileMenu">
          <span></span><span></span><span></span>
        </button>
      </nav>
    </header>

    <section id="top" class="hero-section">
      <div class="hero-bg-glow"></div>
      <div class="hero-container">
        <div class="hero-badge">v1.0.0 · AI量化交易系统</div>
        <h1 class="hero-title">
          您的专属高质量 <span class="gradient-text">AI 量化</span>系统
        </h1>
        <p class="hero-desc">
          只交付实战利刃—— 每一套策略都经回测验证、模拟盘试炼、实盘自动执行，为您的资金安全反复验真，全部私有化部署。
        </p>
        <div class="hero-actions">
          <el-button class="hero-cta-primary" @click="openRegister">🚀 开始体验</el-button>
          <el-button class="hero-cta-secondary" @click="scrollTo('features')">📊 查看认证策略</el-button>
        </div>
        <div class="stats">
          <div class="stats__grid">
            <div class="stats__item">
              <div class="stats__val">10+</div>
              <div class="stats__sublabel">交易所</div>
              <div class="stats__label">全市场覆盖，不挑市场</div>
            </div>
            <div class="stats__item">
              <div class="stats__val">6</div>
              <div class="stats__sublabel">大 LLM 引擎</div>
              <div class="stats__label">择优路由，动态调度</div>
            </div>
            <div class="stats__item">
              <div class="stats__val">4+</div>
              <div class="stats__sublabel">自动策略</div>
              <div class="stats__label">开箱即用，全经系统认证</div>
            </div>
            <div class="stats__item">
              <div class="stats__val">100%</div>
              <div class="stats__sublabel">私有化</div>
              <div class="stats__label">代码在您手里，数据在您盘里</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 系统执行板块（交易闭环 + 核心理念） -->
    <section id="system-execution" class="system-exec-section">
      <div class="section-container">
        <div class="section-header">
          <span class="section-label">交易闭环</span>
          <h2 class="section-title">用时间复利 + 系统执行，让每一次交易都更有价值</h2>
          <p class="section-desc">交易不是买和卖，而是一个完整闭环 —— 从计划到执行再到复盘，建立属于你的长期盈利系统</p>
        </div>
        <div class="exec-grid">
          <!-- 左侧5个步骤 -->
          <div class="exec-steps">
            <div class="exec-step-item" v-for="(item, idx) in execSteps" :key="item.title">
              <div class="step-badge">0{{ idx+1 }}</div>
              <div class="step-info">
                <h4>{{ item.title }}</h4>
                <p>{{ item.desc }}</p>
                <span class="time-tag">{{ item.time }}</span>
              </div>
            </div>
          </div>
          <!-- 右侧功能亮点 -->
          <div class="exec-right">
            <div class="dashboard-card">
              <h4>闭环交易核心能力</h4>
              <div class="dashboard-stats">
                <div class="capability-item">
                  <span class="capability-icon">📋</span>
                  <div>
                    <strong>交易计划</strong>
                    <p>预设进场、止损、仓位规则</p>
                  </div>
                </div>
                <div class="capability-item">
                  <span class="capability-icon">⚡</span>
                  <div>
                    <strong>自动执行</strong>
                    <p>信号触发，自动下单</p>
                  </div>
                </div>
                <div class="capability-item">
                  <span class="capability-icon">📊</span>
                  <div>
                    <strong>持仓追踪</strong>
                    <p>动态止盈止损，移动保护</p>
                  </div>
                </div>
                <div class="capability-item">
                  <span class="capability-icon">🔄</span>
                  <div>
                    <strong>复盘分析</strong>
                    <p>交易记录，持续优化迭代</p>
                  </div>
                </div>
              </div>
              <div class="dashboard-divider"></div>
              <div class="cumulative">
                <span>闭环价值</span>
                <strong style="color: #00ffa3;">从计划到复盘，持续进化</strong>
              </div>
              <p class="dashboard-note">系统化执行，让交易不再是赌博</p>
            </div>
          </div>
        </div>
        <!-- 核心理念卡片区域 -->
        <div class="core-principles">
          <div class="section-subtitle">核心理念：长期主义的交易系统</div>
          <div class="principles-grid">
            <div class="principle-card" v-for="item in corePrinciples" :key="item.title">
              <div class="principle-icon">{{ item.icon }}</div>
              <h4>{{ item.title }}</h4>
              <p>{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section id="features" class="features-section">
      <div class="section-container">
        <div class="section-header">
          <span class="section-label">平台能力</span>
          <h2 class="section-title">一个技术栈，所有交易优势</h2>
          <p class="section-desc">从研究到执行一体化 —— AI 分析、图表、策略代码、回测、快速交易和实盘操作无缝连接。</p>
        </div>
        <div class="features-grid">
          <div
            v-for="feature in features"
            :key="feature.title"
            class="feature-card"
          >
            <div class="feature-icon" :style="{ background: feature.iconBg }">
              <el-icon :size="28"><component :is="feature.icon" /></el-icon>
            </div>
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-desc">{{ feature.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <section id="use-cases" class="usecases-section">
      <div class="section-container">
        <div class="section-header">
          <span class="section-label">适用人群</span>
          <h2 class="section-title">为真正运行策略的每一个人打造</h2>
          <p class="section-desc">无论您是独立交易者、量化团队还是资管机构 —— 灵猞量化以您实际工作的方式呈现。</p>
        </div>
        <div class="usecases-grid">
          <div
            v-for="item in useCases"
            :key="item.title"
            class="usecase-card"
          >
            <div class="usecase-number">{{ item.number }}</div>
            <h3 class="usecase-title">{{ item.title }}</h3>
            <p class="usecase-desc">{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 产品界面预览 -->
    <section id="preview" class="preview-section">
      <div class="section-container">
        <div class="section-header">
          <span class="section-label">界面预览</span>
          <h2 class="section-title">一目了然的量化工作台</h2>
          <p class="section-desc">AI 分析、策略回测、交易监控 —— 所有核心功能集成在一个干净、高效的工作空间。</p>
        </div>
        <div class="preview-mockup">
          <div class="mockup-header">
            <div class="mockup-dots">
              <span></span><span></span><span></span>
            </div>
            <div class="mockup-tabs">
              <span class="mockup-tab active">市场分析</span>
              <span class="mockup-tab">策略回测</span>
              <span class="mockup-tab">实盘监控</span>
              <span class="mockup-tab">交易历史</span>
            </div>
          </div>
          <div class="mockup-body">
            <div class="mockup-sidebar">
              <div class="sidebar-item" v-for="i in 5" :key="i"></div>
            </div>
            <div class="mockup-main">
              <div class="mockup-chart-area">
                <div class="mockup-chart-line" v-for="i in 3" :key="'line'+i"></div>
              </div>
              <div class="mockup-panels">
                <div class="mockup-panel">
                  <div class="panel-dot"></div>
                  <div class="panel-text"></div>
                </div>
                <div class="mockup-panel">
                  <div class="panel-dot"></div>
                  <div class="panel-text"></div>
                </div>
                <div class="mockup-panel">
                  <div class="panel-dot"></div>
                  <div class="panel-text"></div>
                </div>
              </div>
            </div>
          </div>
          <div class="mockup-overlay-glow"></div>
        </div>
      </div>
    </section>

    <section id="coverage" class="coverage-section">
      <div class="section-container">
        <div class="section-header">
          <span class="section-label">市场覆盖</span>
          <h2 class="section-title">每个值得交易的交易场所</h2>
          <p class="section-desc">覆盖主流交易所、美股、外汇和期货市场，统一界面管理。</p>
        </div>
        <div class="coverage-grid">
          <div
            v-for="item in coverageItems"
            :key="item.label"
            class="coverage-card"
          >
            <span class="coverage-icon">{{ item.icon }}</span>
            <div>
              <h4 class="coverage-label">{{ item.label }}</h4>
              <p class="coverage-desc">{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 商业授权 -->
    <section id="license" class="license-section">
      <div class="section-container">
        <div class="section-header">
          <span class="section-label">商业授权</span>
          <h2 class="section-title">申请商业部署授权</h2>
          <p class="section-desc">后端为 Apache 2.0。预构建 Vue 前端为源代码可用许可 —— 个人及合规非商业使用免费；商业部署、白标或 SaaS 转售需向我们申请授权。</p>
        </div>
        <div class="license-grid">
          <div class="license-grid__info">
            <ul class="license-points">
              <li>自托管企业 / 基金部署</li>
              <li>面向客户的 SaaS 或白标产品</li>
              <li>OEM 嵌入 UI 栈</li>
            </ul>
            <p class="license-grid__note">我们通常会在 2–3 个工作日内回复。一般咨询请发邮件至 <a href="mailto:support@lingyumao.com">support@lingyumao.com</a>。</p>
            <el-button class="license-btn-ghost">立即申请</el-button>
          </div>
          <form class="license-form" id="licenseForm">
            <div class="license-form__hp" aria-hidden="true">
              <label>Leave blank<input type="text" name="_gotcha" tabindex="-1" autocomplete="off" /></label>
            </div>
            <div class="license-form__row">
              <label class="license-form__field">
                <span>姓名 *</span>
                <input type="text" name="name" required autocomplete="name" />
              </label>
              <label class="license-form__field">
                <span>工作邮箱 *</span>
                <input type="email" name="email" required autocomplete="email" />
              </label>
            </div>
            <div class="license-form__row">
              <label class="license-form__field">
                <span>手机号 *</span>
                <input type="tel" name="phone" required autocomplete="tel" />
              </label>
              <label class="license-form__field">
                <span>主要用途 *</span>
                <select name="use_case" required>
                  <option value="" disabled selected>请选择…</option>
                  <option value="internal">内部交易 / 研究</option>
                  <option value="saas">面向客户的 SaaS</option>
                  <option value="education">教育 / 培训</option>
                  <option value="other">其他(请在下方说明)</option>
                </select>
              </label>
            </div>
            <label class="license-form__field">
              <span>公司 / 组织</span>
              <input type="text" name="company" autocomplete="organization" />
            </label>
            <label class="license-form__field">
              <span>补充说明</span>
              <textarea name="message" rows="4" placeholder="团队规模、时间线、地区等…"></textarea>
            </label>
            <div class="license-form__actions">
              <el-button class="license-btn-primary">提交申请</el-button>
            </div>
            <div class="license-form__status" hidden role="status"></div>
          </form>
        </div>
      </div>
    </section>

    <!-- 最终 CTA -->
    <section class="cta-section">
      <div class="section-container">
        <div class="cta__inner">
          <h2 class="cta-title">准备好开始你的量化交易了吗？</h2>
          <p class="cta-desc">从 AI 辅助分析到实盘交易，一站式搞定。免费体验灵猞量化。</p>
          <div class="cta-actions">
            <el-button class="hero-cta-primary" @click="openRegister">
              开始体验
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
            </el-button>
            <el-button class="cta-btn-ghost" @click="scrollTo('features')">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.57.1.78-.25.78-.55v-2.1c-3.2.7-3.87-1.38-3.87-1.38-.52-1.33-1.27-1.68-1.27-1.68-1.04-.71.08-.7.08-.7 1.15.08 1.76 1.18 1.76 1.18 1.02 1.75 2.68 1.25 3.34.95.1-.74.4-1.25.72-1.54-2.55-.29-5.24-1.28-5.24-5.7 0-1.26.45-2.29 1.19-3.1-.12-.29-.52-1.47.11-3.06 0 0 .97-.31 3.18 1.18a11 11 0 0 1 5.78 0c2.2-1.49 3.17-1.18 3.17-1.18.64 1.59.23 2.77.12 3.06.74.81 1.19 1.84 1.19 3.1 0 4.43-2.69 5.41-5.25 5.69.41.36.78 1.07.78 2.16v3.2c0 .3.2.66.79.55A11.51 11.51 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5z"></path></svg>
              查看认证策略
            </el-button>
            <el-button class="cta-btn-ghost" @click="scrollTo('license')">
              立即申请
            </el-button>
          </div>
        </div>
      </div>
    </section>

    <!-- 法律与安全 -->
    <section id="legal" class="legal-section">
      <div class="section-container">
        <div class="section-header">
          <span class="section-label">法律与安全</span>
          <h2 class="section-title">法律声明</h2>
          <p class="section-desc">使用软件之前、或在接触任何自称代表本项目的人之前，请务必阅读以下内容。</p>
        </div>

        <article class="legal__alert">
          <div class="legal__alert-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
          </div>
          <div class="legal__alert-body">
            <h3>灵猞量化 从未发行、不会背书、也不会上线任何代币、币种、ICO、空投或加密货币。</h3>
            <p>任何售卖灵猞量化代币、预售名额、私募轮、空投领取、质押产品或付费 VIP 群的账号、群组、网站或"团队成员"，都是<strong>骗局</strong>，与我们没有任何关联。如果有人让你转账、签署钱包授权，或以"认证账户"为由索取 API Key —— 那一定不是我们。有任何疑问，请通过下面的官方渠道核实。</p>
            <div class="legal__official">
              <strong>官方渠道 —— 除此之外一律为仿冒：</strong>
              <ul>
                <li><a href="https://lingyumao.com" target="_blank" rel="noopener">lingyumao.com</a></li>
                <li><a href="https://github.com/..." target="_blank" rel="noopener">GitHub</a></li>
                <li><a href="mailto:support@lingyumao.com">support@lingyumao.com</a></li>
              </ul>
            </div>
          </div>
        </article>

        <div class="legal__grid">
          <article class="legal__card">
            <h3>交易风险</h3>
            <p>交易加密货币、股票、外汇、期货与衍生品具有重大风险，并非适合每位投资者。你可能损失全部投入本金。历史表现 —— 包括软件生成的任何回测或模拟结果 —— 不代表未来收益。</p>
          </article>
          <article class="legal__card">
            <h3>非投资建议</h3>
            <p>灵猞量化是一个技术平台。本网站、软件内部以及任何由 AI 生成的输出，都不构成投资、财务、法律或税务建议。任何交易决策及其结果，均由你自行负责。</p>
          </article>
          <article class="legal__card">
            <h3>软件按"现状"提供</h3>
            <p>软件按"现状"(AS IS)提供，不提供任何明示或默示的保证。你需要自行负责自有部署、API Key、基础设施，以及所在司法管辖区的合规事项。</p>
          </article>
          <article class="legal__card">
            <h3>商标与仿冒</h3>
            <p>"灵猞量化"名称与 Logo 均为本项目的商标。任何未经授权的使用 —— 包括仿冒身份、假客服、虚假空投、或付费入群的所谓"灵猞量化"社群 —— 都将被记录并追究。发现仿冒，请发送邮件至 <code>support@lingyumao.com</code> 举报。</p>
          </article>
          <article class="legal__card">
            <h3>合规与司法管辖</h3>
            <p>部分市场、交易所与产品在某些司法管辖区受限或不可用。你需要自行确保使用灵猞量化及接入的任何交易场所，符合适用于你的法律与法规。</p>
          </article>
          <article class="legal__card">
            <h3>你的密钥，你的数据</h3>
            <p>在自行部署的模式下，你的 API Key、策略、AI 历史与交易记录始终留在你自己控制的基础设施上。我们绝不会向你索取密钥、助记词或钱包私签。</p>
          </article>
        </div>
      </div>
    </section>

    <footer class="landing-footer">
      <div class="footer-container">
        <div class="footer-top">
          <div class="footer-brand">
            <img src="@/assets/xiaolingbao-logo.svg" alt="灵猞量化" />
            <span class="footer-brand-name">灵猞量化</span>
            <p class="footer-brand-desc">您的专属高质量 AI 量化系统</p>
          </div>
          <div class="footer-links">
            <div class="footer-col">
              <h4>产品</h4>
              <a href="#features">功能特性</a>
              <a href="#workflow">工作流程</a>
              <a href="#coverage">市场覆盖</a>
            </div>
            <div class="footer-col">
              <h4>资源</h4>
              <a href="#preview">界面预览</a>
              <a href="#workflow">工作流程</a>
              <a href="#coverage">市场覆盖</a>
            </div>
            <div class="footer-col">
              <h4>关于</h4>
              <a>团队介绍</a>
              <a>服务条款</a>
              <a>隐私政策</a>
            </div>
          </div>
        </div>
        <div class="footer-bottom">
          <span>&copy; {{ currentYear }} 灵猞量化. All rights reserved.</span>
          <div class="footer-legal">
            <a>服务条款</a>
            <a>隐私政策</a>
          </div>
        </div>
      </div>
    </footer>

    <LoginDialog
      v-model="loginDialogVisible"
      @switch-to-register="openRegister"
      @switch-to-forgot-password="openForgotPassword"
    />
    <RegisterDialog
      v-model="registerDialogVisible"
      @switch-to-login="openLogin"
    />
    <ForgotPasswordDialog
      v-model="forgotDialogVisible"
      @switch-to-login="openLogin"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import {
  TrendCharts,
  DataAnalysis,
  Aim,
  Connection,
  Monitor,
  Setting,
  MagicStick,
  Wallet,
  Grid,
  Guide,
  CollectionTag,
  Share,
  Iphone,
  Document,
  Headset
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import LoginDialog from '@/components/auth/LoginDialog.vue'
import RegisterDialog from '@/components/auth/RegisterDialog.vue'
import ForgotPasswordDialog from '@/components/auth/ForgotPasswordDialog.vue'

const authStore = useAuthStore()

const scrolled = ref(false)
const mobileMenuOpen = ref(false)
const loginDialogVisible = ref(false)
const registerDialogVisible = ref(false)
const forgotDialogVisible = ref(false)
const currentYear = computed(() => new Date().getFullYear())

const navItems = [
  { key: 'features', label: '平台能力', href: '#features' },
  { key: 'use-cases', label: '适用人群', href: '#use-cases' },
  { key: 'system-execution', label: '交易闭环', href: '#system-execution' },
  { key: 'preview', label: '界面预览', href: '#preview' },
  { key: 'workflow', label: '工作流程', href: '#workflow' },
  { key: 'coverage', label: '市场覆盖', href: '#coverage' }
]

const features = [
  {
    icon: MagicStick,
    title: '快速 AI 市场分析',
    desc: '结构化、低延迟的分析，支持记忆、集成投票和置信度校准。可在 OpenAI、Claude、Gemini、DeepSeek 等多个模型间比较。',
    iconBg: 'linear-gradient(135deg, #7a5cff 0%, #2de2e6 100%)'
  },
  {
    icon: Document,
    title: 'Python 原生策略',
    desc: '编写基于 DataFrame 的 IndicatorStrategy 或事件驱动的 ScriptStrategy。AI 可以起草起点，您掌控迭代。',
    iconBg: 'linear-gradient(135deg, #00ffa3 0%, #2de2e6 100%)'
  },
  {
    icon: Aim,
    title: '确定性回测',
    desc: '佣金和滑点建模、逐笔交易分析、权益曲线。每次运行都锁定代码哈希和配置快照，实现完美复现。',
    iconBg: 'linear-gradient(135deg, #ff6b6b 0%, #ffa94d 100%)'
  },
  {
    icon: Connection,
    title: '自主交易机器人',
    desc: '内置网格、马丁格尔、趋势跟踪和 DCA 策略。执行感知、重启弹性、支持信号或完全实盘模式。',
    iconBg: 'linear-gradient(135deg, #4ecdc4 0%, #44bd9e 100%)'
  },
  {
    icon: Monitor,
    title: '私有化部署',
    desc: '一条 Docker Compose 命令启动 Flask API、PostgreSQL、Redis 和 Nginx。您的密钥、策略和数据永不离开基础设施。',
    iconBg: 'linear-gradient(135deg, #6c5ce7 0%, #a29bfe 100%)'
  },
  {
    icon: Setting,
    title: '运维就绪',
    desc: '多用户 PostgreSQL 后端、基于角色的访问、OAuth 登录、会员积分、USDT 支付和 Telegram/Email/SMS/Discord 告警。',
    iconBg: 'linear-gradient(135deg, #fd79a8 0%, #e84393 100%)'
  }
]

const useCases = [
  {
    number: '01',
    title: 'AI 辅助的个人交易者',
    desc: '独立交易者和量化爱好者，希望在同一个工作空间中拥有 AI、图表、回测和机器人，而不是在 5 个工具之间切换。'
  },
  {
    number: '02',
    title: '量化研究团队',
    desc: '希望拥有统一工具链的团队 —— 数据摄入、策略代码、回测、实盘执行和共享实验历史。'
  },
  {
    number: '03',
    title: 'Python 策略开发者',
    desc: '希望从 Python 想法到回测到实盘执行有一条快速路径的开发者，拥有文档完善的 SDK 和清晰的扩展点。'
  },
  {
    number: '04',
    title: '隐私优先团队',
    desc: '不能将密钥、alpha 或交易历史发送到托管 SaaS 的研究部门和操作者。一切都保留在您的基础设施内。'
  }
]

const execSteps = [
  { title: '看机会', desc: '发现有价值的交易机会', time: '10分钟/天' },
  { title: '做计划', desc: '制定交易计划，明确进出场规则', time: '15分钟/天' },
  { title: '控风险', desc: '计算仓位与止损，把风险控制在可接受范围', time: '15分钟/天' },
  { title: '去执行', desc: '严格执行计划，不被情绪干扰', time: '全天执行' },
  { title: '做复盘', desc: '记录交易结果，总结经验教训', time: '20分钟/天' }
]

const corePrinciples = [
  { icon: '⏱️', title: '时间复利', desc: '每天优化1%，一年后可能提升37倍' },
  { icon: '🛡️', title: '风险优先', desc: '先保住本金，才有机会长期盈利' },
  { icon: '📊', title: '数据驱动', desc: '用记录代替记忆，用数据代替主观判断' },
  { icon: '🔄', title: '持续复盘', desc: '复盘是进步的阶梯，每一次总结都是系统升级' }
]

const workflowSteps = [
  {
    title: '研究与分析',
    desc: '拉取多时间周期数据，运行集成 AI 分析，检查指标信号和可比标的 —— 全部在一个工作空间中。'
  },
  {
    title: '构建或生成',
    desc: '使用可视化向导配置现成的机器人，或使用我们文档完善的 SDK 编写完整的 Python 策略。'
  },
  {
    title: '回测与迭代',
    desc: '具有真实佣金、滑点和杠杆的确定性运行。通过代码哈希和配置快照冻结每次实验。'
  },
  {
    title: '部署与运行',
    desc: '一键上线。实时盈亏、权益追踪、投资组合监控、告警和内建幂等订单重试。'
  }
]

const coverageItems = [
  { icon: '₿', label: '加密货币交易所', desc: 'Binance · OKX · Bybit · Bitget · Gate · HTX ...' },
  { icon: '$', label: '美股', desc: 'IBKR · Yahoo · Finnhub' },
  { icon: '€', label: '外汇与贵金属', desc: 'MT5 · OANDA · XAU · XAG' },
  { icon: '⬢', label: '期货', desc: 'CME · COMEX · CBOT' },
  { icon: '¥', label: '港股通', desc: 'SSE · SZSE · HKEX' },
  { icon: '↯', label: '现货与衍生品', desc: '永续合约 · 交割合约 · 期权' },
  { icon: '◇', label: '可接入数据源', desc: '自定义 REST/WS 适配器' }
]

const openLogin = () => {
  registerDialogVisible.value = false
  forgotDialogVisible.value = false
  loginDialogVisible.value = true
}

const openRegister = () => {
  loginDialogVisible.value = false
  forgotDialogVisible.value = false
  registerDialogVisible.value = true
}

const openForgotPassword = () => {
  loginDialogVisible.value = false
  registerDialogVisible.value = false
  forgotDialogVisible.value = true
}

const scrollTo = (id: string) => {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth' })
  }
  mobileMenuOpen.value = false
}

const toggleMobileMenu = () => {
  mobileMenuOpen.value = !mobileMenuOpen.value
}

const closeMobileMenu = () => {
  mobileMenuOpen.value = false
}

const handleScroll = () => {
  scrolled.value = window.scrollY > 60
}

onMounted(() => {
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style>
html {
  scroll-behavior: smooth;
}
</style>

<style scoped>
.landing-page {
  min-height: 100vh;
  background: #0a0a1a;
  color: #ffffff;
  overflow-x: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.section-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

.section-header {
  text-align: center;
  margin-bottom: 60px;
}

.section-label {
  display: inline-block;
  padding: 6px 16px;
  border-radius: 20px;
  background: rgba(122, 92, 255, 0.15);
  color: #7a5cff;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 1px;
  margin-bottom: 16px;
  text-transform: uppercase;
}

.section-title {
  font-size: 36px;
  font-weight: 800;
  color: #ffffff;
  margin-bottom: 16px;
  line-height: 1.2;
}

.section-desc {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.6);
  max-width: 640px;
  margin: 0 auto;
  line-height: 1.6;
}

.gradient-text {
  background: linear-gradient(135deg, #2de2e6 0%, #7a5cff 50%, #00ffa3 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* Header / Nav */
.landing-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  padding: 16px 0;
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  background: rgba(10, 10, 26, 0.55);
  border-bottom: 1px solid transparent;
  transition: all 0.3s ease;
}

.landing-header.header-scrolled {
  background: rgba(10, 10, 26, 0.82);
  border-bottom-color: rgba(255, 255, 255, 0.08);
  padding: 10px 0;
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  z-index: 1001;
}

.nav-logo img {
  width: 32px;
  height: 32px;
}

.nav-brand {
  font-size: 20px;
  font-weight: 700;
  background: linear-gradient(135deg, #2de2e6 0%, #7a5cff 50%, #00ffa3 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 32px;
}

.nav-link {
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: color 0.3s ease;
  cursor: pointer;
}

.nav-link:hover {
  color: #ffffff;
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.nav-actions-mobile {
  display: none;
}

.nav-btn-login {
  background: transparent !important;
  color: rgba(255, 255, 255, 0.8) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  border-radius: 8px !important;
  font-size: 13px !important;
  font-weight: 600 !important;
  padding: 8px 20px !important;
  transition: all 0.3s ease !important;
}

.nav-btn-login:hover {
  border-color: rgba(122, 92, 255, 0.6) !important;
  color: #ffffff !important;
  background: rgba(122, 92, 255, 0.1) !important;
}

.nav-btn-signup {
  background: linear-gradient(135deg, #7a5cff 0%, #2de2e6 100%) !important;
  color: #ffffff !important;
  border: none !important;
  border-radius: 8px !important;
  font-size: 13px !important;
  font-weight: 600 !important;
  padding: 8px 20px !important;
  transition: all 0.3s ease !important;
}

.nav-btn-signup:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 15px rgba(122, 92, 255, 0.4) !important;
}

.mobile-menu-toggle {
  display: none;
  flex-direction: column;
  gap: 5px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  z-index: 1001;
}

.mobile-menu-toggle span {
  display: block;
  width: 24px;
  height: 2px;
  background: #ffffff;
  border-radius: 2px;
  transition: all 0.3s ease;
}

/* Hero */
.hero-section {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 120px 24px 80px;
  overflow: hidden;
}

.hero-bg-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 800px;
  height: 800px;
  background: radial-gradient(circle, rgba(122, 92, 255, 0.15) 0%, rgba(45, 226, 230, 0.08) 30%, transparent 60%);
  pointer-events: none;
}

.hero-container {
  position: relative;
  text-align: center;
  max-width: 900px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
  border-radius: 20px;
  background: rgba(122, 92, 255, 0.1);
  border: 1px solid rgba(122, 92, 255, 0.2);
  color: rgba(255, 255, 255, 0.8);
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 32px;
}

.hero-title {
  font-size: 56px;
  font-weight: 800;
  line-height: 1.1;
  margin-bottom: 24px;
  letter-spacing: -1px;
}

.hero-desc {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.7;
  margin-bottom: 40px;
  max-width: 700px;
  margin-left: auto;
  margin-right: auto;
}

.hero-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-bottom: 60px;
}

.hero-cta-primary {
  background: linear-gradient(135deg, #7a5cff 0%, #2de2e6 100%) !important;
  color: #ffffff !important;
  border: none !important;
  border-radius: 12px !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  padding: 22px 28px !important;
  line-height: 1 !important;
  transition: all 0.3s ease !important;
}

.hero-cta-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(122, 92, 255, 0.4) !important;
}

.hero-cta-secondary {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #ffffff !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  border-radius: 12px !important;
  font-size: 16px !important;
  font-weight: 500 !important;
  padding: 22px 28px !important;
  line-height: 1 !important;
  transition: all 0.3s ease !important;
}

.hero-cta-secondary:hover {
  background: rgba(255, 255, 255, 0.12) !important;
  border-color: rgba(122, 92, 255, 0.5) !important;
}

/* Stats strip */
.stats {
  padding: 20px 0 60px;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding-left: 24px;
  padding-right: 24px;
}

.stats__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.stats__item {
  text-align: center;
}

.stats__val {
  font-size: clamp(30px, 3.5vw, 44px);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.1;
  background: linear-gradient(135deg, #2de2e6 0%, #7a5cff 50%, #00ffa3 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 8px;
}

.stats__label {
  color: rgba(255, 255, 255, 0.5);
  font-size: 15px;
  line-height: 1.4;
}

/* Features */
.features-section {
  padding: 70px 0;
  background: linear-gradient(180deg, #0a0a1a 0%, #0f0f2a 100%);
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.feature-card {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.05) 0%, rgba(255, 255, 255, 0.02) 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  padding: 32px;
  transition: all 0.4s ease;
}

.feature-card:hover {
  border-color: rgba(122, 92, 255, 0.3);
  transform: translateY(-4px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
}

.feature-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  color: #ffffff;
}

.feature-title {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 12px;
}

.feature-desc {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.7;
  margin: 0;
}

/* Use Cases */
.usecases-section {
  padding: 70px 0;
  background: #0a0a1a;
}

.usecases-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.usecase-card {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.04) 0%, rgba(255, 255, 255, 0.01) 100%);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 16px;
  padding: 32px;
  transition: all 0.4s ease;
  position: relative;
  overflow: hidden;
}

.usecase-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, #7a5cff, #2de2e6, #00ffa3);
  opacity: 0;
  transition: opacity 0.4s ease;
}

.usecase-card:hover::before {
  opacity: 1;
}

.usecase-card:hover {
  border-color: rgba(122, 92, 255, 0.2);
  transform: translateY(-4px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
}

.usecase-number {
  font-size: 32px;
  font-weight: 800;
  background: linear-gradient(135deg, #2de2e6 0%, #7a5cff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 16px;
  opacity: 0.6;
}

.usecase-title {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 12px;
}

.usecase-desc {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.7;
  margin: 0;
}

/* 系统执行 */
.system-exec-section {
  padding: 100px 0;
  background: #0a0a1a;
}

.exec-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 40px;
  align-items: start;
  margin-bottom: 60px;
}

.exec-steps {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.exec-step-item {
  display: flex;
  gap: 16px;
  background: linear-gradient(135deg, rgba(255,255,255,0.04), rgba(255,255,255,0.01));
  border-radius: 16px;
  padding: 20px;
  border: 1px solid rgba(255,255,255,0.06);
  transition: all 0.3s ease;
}

.exec-step-item:hover {
  border-color: rgba(122, 92, 255, 0.3);
  transform: translateX(5px);
}

.step-badge {
  font-size: 24px;
  font-weight: 800;
  background: linear-gradient(135deg, #7a5cff, #2de2e6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  width: 48px;
}

.step-info h4 {
  font-size: 18px;
  margin: 0 0 4px;
}

.step-info p {
  font-size: 13px;
  color: rgba(255,255,255,0.6);
  margin: 0 0 6px;
}

.time-tag {
  font-size: 11px;
  background: rgba(122,92,255,0.2);
  padding: 2px 8px;
  border-radius: 20px;
  color: #a29bfe;
}

.exec-right {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.dashboard-card {
  background: linear-gradient(135deg, rgba(0,0,0,0.4), rgba(122,92,255,0.05));
  border-radius: 24px;
  padding: 24px;
  border: 1px solid rgba(122,92,255,0.2);
  backdrop-filter: blur(4px);
}

.dashboard-card h4 {
  font-size: 18px;
  margin-bottom: 20px;
  border-left: 3px solid #7a5cff;
  padding-left: 12px;
}

.dashboard-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}

.capability-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  background: rgba(255,255,255,0.03);
  border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.06);
  transition: all 0.3s ease;
}

.capability-item:hover {
  border-color: rgba(122,92,255,0.3);
  background: rgba(122,92,255,0.05);
}

.capability-icon {
  font-size: 24px;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(122,92,255,0.1);
  border-radius: 10px;
  flex-shrink: 0;
}

.capability-item div {
  flex: 1;
}

.capability-item strong {
  font-size: 14px;
  color: #ffffff;
  display: block;
  margin-bottom: 2px;
}

.capability-item p {
  font-size: 12px;
  color: rgba(255,255,255,0.5);
  margin: 0;
  line-height: 1.3;
}

.dashboard-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent, #7a5cff, transparent);
  margin: 16px 0;
}

.cumulative {
  display: flex;
  justify-content: space-between;
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 20px;
}

.dashboard-note {
  font-size: 12px;
  color: rgba(255,255,255,0.5);
  text-align: right;
  margin: 12px 0 0;
}

/* 核心理念 */
.core-principles {
  margin-top: 40px;
  text-align: center;
}

.section-subtitle {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 32px;
  color: rgba(255,255,255,0.9);
  letter-spacing: 1px;
}

.principles-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.principle-card {
  background: linear-gradient(135deg, rgba(255,255,255,0.05), rgba(122,92,255,0.08));
  border: 1px solid rgba(122,92,255,0.2);
  border-radius: 20px;
  padding: 24px 16px;
  transition: all 0.3s ease;
  backdrop-filter: blur(4px);
}

.principle-card:hover {
  transform: translateY(-5px);
  border-color: #7a5cff;
  box-shadow: 0 10px 25px rgba(122,92,255,0.2);
}

.principle-icon {
  font-size: 36px;
  margin-bottom: 12px;
}

.principle-card h4 {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 10px;
  background: linear-gradient(135deg, #2de2e6, #7a5cff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.principle-card p {
  font-size: 13px;
  color: rgba(255,255,255,0.65);
  line-height: 1.5;
  margin: 0;
}

/* Workflow */


/* 界面预览 */
.preview-section {
  padding: 100px 0;
  background: linear-gradient(180deg, #0a0a1a 0%, #0f0f2a 50%, #0a0a1a 100%);
}

.preview-mockup {
  position: relative;
  max-width: 960px;
  margin: 0 auto;
  background: linear-gradient(135deg, rgba(255,255,255,0.06), rgba(255,255,255,0.02));
  border: 1px solid rgba(122,92,255,0.2);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 30px 60px rgba(0,0,0,0.4), 0 0 40px rgba(122,92,255,0.05);
}

.mockup-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  background: rgba(0,0,0,0.3);
  border-bottom: 1px solid rgba(255,255,255,0.06);
}

.mockup-dots {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.mockup-dots span {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255,255,255,0.15);
}

.mockup-dots span:nth-child(1) { background: #ff5f56; }
.mockup-dots span:nth-child(2) { background: #ffbd2e; }
.mockup-dots span:nth-child(3) { background: #27c93f; }

.mockup-tabs {
  display: flex;
  gap: 4px;
}

.mockup-tab {
  padding: 6px 14px;
  font-size: 12px;
  color: rgba(255,255,255,0.4);
  border-radius: 6px;
  cursor: default;
  transition: all 0.3s ease;
}

.mockup-tab.active {
  color: #ffffff;
  background: rgba(122,92,255,0.2);
}

.mockup-body {
  display: flex;
  height: 320px;
}

.mockup-sidebar {
  width: 60px;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: rgba(0,0,0,0.15);
  border-right: 1px solid rgba(255,255,255,0.06);
}

.sidebar-item {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(255,255,255,0.06);
  transition: all 0.3s ease;
}

.sidebar-item:nth-child(1) { background: rgba(122,92,255,0.3); }
.sidebar-item:nth-child(3) { background: rgba(45,226,230,0.15); }

.mockup-main {
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mockup-chart-area {
  flex: 1;
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 8%;
  padding: 0 10px;
  border-radius: 12px;
  background: rgba(255,255,255,0.02);
  overflow: hidden;
}

.mockup-chart-line {
  flex: 1;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(180deg, rgba(122,92,255,0.4), rgba(45,226,230,0.1));
  min-width: 12px;
  transition: all 0.3s ease;
}

.mockup-chart-line:nth-child(1) { height: 45%; }
.mockup-chart-line:nth-child(2) { height: 70%; }
.mockup-chart-line:nth-child(3) { height: 35%; }

.mockup-panels {
  display: flex;
  gap: 12px;
}

.mockup-panel {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: rgba(255,255,255,0.03);
  border-radius: 10px;
  border: 1px solid rgba(255,255,255,0.06);
}

.panel-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.mockup-panel:nth-child(1) .panel-dot { background: #00ffa3; }
.mockup-panel:nth-child(2) .panel-dot { background: #7a5cff; }
.mockup-panel:nth-child(3) .panel-dot { background: #2de2e6; }

.panel-text {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: rgba(255,255,255,0.08);
}

.mockup-overlay-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 60%;
  height: 60%;
  background: radial-gradient(circle, rgba(122,92,255,0.06) 0%, transparent 70%);
  pointer-events: none;
}

/* Coverage */
.coverage-section {
  padding: 100px 0;
  background: #0a0a1a;
}

.coverage-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.coverage-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.04) 0%, rgba(255, 255, 255, 0.01) 100%);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 20px;
  transition: all 0.3s ease;
}

.coverage-card:hover {
  border-color: rgba(122, 92, 255, 0.3);
  background: rgba(122, 92, 255, 0.05);
}

.coverage-icon {
  font-size: 24px;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(122, 92, 255, 0.1);
  border-radius: 10px;
  flex-shrink: 0;
}

.coverage-label {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
  margin: 0 0 4px;
}

.coverage-desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
  line-height: 1.4;
}

/* CTA 区域 */
.cta-section {
  padding: 100px 0;
  background:
    radial-gradient(ellipse at top, rgba(122, 92, 255, 0.14) 0%, transparent 60%),
    linear-gradient(180deg, #0a0a1a 0%, #060612 100%);
  position: relative;
}

.cta__inner {
  text-align: center;
  max-width: 720px;
  margin: 0 auto;
}

.cta-title {
  font-size: clamp(30px, 4.5vw, 52px);
  font-weight: 800;
  color: #ffffff;
  margin-bottom: 14px;
  line-height: 1.08;
  letter-spacing: -0.03em;
}

.cta-desc {
  font-size: 17px;
  color: rgba(255, 255, 255, 0.55);
  margin: 0 auto 30px;
  max-width: 520px;
  line-height: 1.6;
}

.cta-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.cta-actions .el-button svg {
  vertical-align: middle;
  margin-right: 6px;
}

.cta-btn-ghost {
  background: rgba(255, 255, 255, 0.04) !important;
  color: #eef0ff !important;
  border: 1px solid rgba(255, 255, 255, 0.18) !important;
  border-radius: 12px !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  padding: 20px 28px !important;
  line-height: 1 !important;
  transition: all 0.25s ease !important;
}
.cta-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.09) !important;
  border-color: rgba(255, 255, 255, 0.28) !important;
  transform: translateY(-1px);
}

/* Footer */
.landing-footer {
  background: #060612;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding: 60px 0 30px;
}

.footer-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

.footer-top {
  display: flex;
  justify-content: space-between;
  gap: 60px;
  margin-bottom: 40px;
}

.footer-brand {
  max-width: 240px;
}

.footer-brand img {
  width: 36px;
  height: 36px;
  margin-bottom: 12px;
}

.footer-brand-name {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 8px;
}

.footer-brand-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
  margin: 0;
  line-height: 1.5;
}

.footer-links {
  display: flex;
  gap: 60px;
}

.footer-col h4 {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin: 0 0 16px;
}

.footer-col a {
  display: block;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.65);
  text-decoration: none;
  margin-bottom: 10px;
  cursor: pointer;
  transition: color 0.3s ease;
}

.footer-col a:hover {
  color: #7a5cff;
}

.footer-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 24px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
}

.footer-legal {
  display: flex;
  gap: 24px;
}

.footer-legal a {
  color: rgba(255, 255, 255, 0.4);
  text-decoration: none;
  cursor: pointer;
  transition: color 0.3s ease;
}

.footer-legal a:hover {
  color: rgba(255, 255, 255, 0.7);
}

/* 商业授权 */
.license-section {
  padding: 70px 0;
}

.license-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.25fr);
  gap: 28px;
  align-items: start;
  margin-top: 8px;
}

.license-points {
  margin: 0 0 16px;
  padding-left: 18px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.65;
  font-size: 14px;
}

.license-points li + li {
  margin-top: 6px;
}

.license-grid__note {
  font-size: 13px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.55);
  margin: 0 0 16px;
}

.license-grid__note a {
  color: #7a5cff;
  text-decoration: none;
}

.license-grid__note a:hover {
  color: #9b7fff;
}

.license-btn-ghost {
  background: rgba(255, 255, 255, 0.03) !important;
  color: #ffffff !important;
  border: 1px solid rgba(255, 255, 255, 0.14) !important;
  border-radius: 12px !important;
  font-size: 14px !important;
  font-weight: 500 !important;
  padding: 12px 24px !important;
  line-height: 1 !important;
  transition: all 0.3s ease !important;
}

.license-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.07) !important;
  border-color: rgba(255, 255, 255, 0.22) !important;
}

.license-btn-primary {
  background: linear-gradient(135deg, #7a5cff 0%, #2de2e6 100%) !important;
  color: #0a0b14 !important;
  border: none !important;
  border-radius: 12px !important;
  font-size: 14px !important;
  font-weight: 600 !important;
  padding: 12px 24px !important;
  line-height: 1 !important;
  box-shadow: 0 10px 28px -10px rgba(122, 92, 255, 0.6) !important;
  transition: all 0.3s ease !important;
}

.license-btn-primary:hover {
  box-shadow: 0 16px 40px -10px rgba(122, 92, 255, 0.75) !important;
}

.license-form {
  padding: 22px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(122, 92, 255, 0.06) 0%, rgba(10, 10, 26, 0.2) 100%);
}

.license-form__hp {
  position: absolute;
  left: -9999px;
  width: 1px;
  height: 1px;
  overflow: hidden;
}

.license-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.license-form__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
  font-size: 13px;
  color: #c8cbdc;
}

.license-form__field input,
.license-form__field textarea {
  width: 100%;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  color: #eef0ff;
  font: inherit;
  box-sizing: border-box;
}

.license-form__field select {
  width: 100%;
  padding: 10px 36px 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background-color: #14151f;
  color: #eef0ff;
  font: inherit;
  cursor: pointer;
  -webkit-appearance: none;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 24 24' fill='none' stroke='%23a8adc4' stroke-width='2.2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  box-sizing: border-box;
}

.license-form__field select option {
  background-color: #14151f;
  color: #eef0ff;
}

.license-form__field select option:checked,
.license-form__field select option:hover {
  background-color: #2a2540;
  color: #ffffff;
}

.license-form__field select option:disabled {
  color: #8b8fa3;
}

.license-form__field input:focus,
.license-form__field select:focus,
.license-form__field textarea:focus {
  outline: none;
  border-color: rgba(122, 92, 255, 0.55);
  box-shadow: 0 0 0 3px rgba(122, 92, 255, 0.15);
}

.license-form__actions {
  margin-top: 4px;
}

.license-form__status {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.55;
}

.license-form__status--ok {
  border: 1px solid rgba(52, 211, 153, 0.35);
  background: rgba(52, 211, 153, 0.08);
  color: #bbf7d0;
}

.license-form__status--error {
  border: 1px solid rgba(248, 113, 113, 0.35);
  background: rgba(248, 113, 113, 0.08);
  color: #fecaca;
}

/* 法律与安全 */
.legal-section {
  padding: 70px 0;
}

.legal__alert {
  position: relative;
  display: grid;
  grid-template-columns: 54px 1fr;
  gap: 22px;
  margin: 40px auto 28px;
  padding: 28px 30px;
  max-width: 100%;
  border-radius: 20px;
  background:
    linear-gradient(180deg, rgba(255,176,80,0.11), rgba(255,99,130,0.06)),
    rgba(22,18,26,0.65);
  border: 1px solid rgba(255,176,80,0.28);
  box-shadow:
    0 0 0 1px rgba(255,176,80,0.08) inset,
    0 30px 80px -40px rgba(255,120,60,0.35),
    0 20px 60px -30px rgba(0,0,0,0.7);
  overflow: hidden;
}

.legal__alert::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(600px 220px at 0% 0%, rgba(255,176,80,0.16), transparent 60%),
    radial-gradient(500px 200px at 100% 100%, rgba(255,99,130,0.10), transparent 60%);
  opacity: 0.9;
}

.legal__alert > * { position: relative; z-index: 1; }

.legal__alert-icon {
  width: 54px; height: 54px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255,176,80,0.22), rgba(255,99,130,0.16));
  border: 1px solid rgba(255,176,80,0.35);
  color: #ffd28a;
  box-shadow: 0 6px 18px -6px rgba(255,120,60,0.6);
  flex-shrink: 0;
}

.legal__alert-body h3 {
  margin: 0 0 10px;
  font-size: 1.22rem;
  line-height: 1.4;
  font-weight: 700;
  color: #ffe1b8;
  letter-spacing: -0.005em;
}

.legal__alert-body p {
  margin: 0 0 16px;
  color: rgba(235,225,215,0.82);
  font-size: 0.965rem;
  line-height: 1.7;
}

.legal__alert-body p strong {
  color: #ff9e7d;
  font-weight: 700;
}

.legal__alert-body code {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 0.88em;
  padding: 1px 6px;
  border-radius: 5px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.08);
}

.legal__official {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: rgba(10,10,16,0.55);
  border: 1px solid rgba(255,255,255,0.06);
}

.legal__official strong {
  display: block;
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: rgba(255,210,160,0.9);
  margin-bottom: 10px;
}

.legal__official ul {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.legal__official li { margin: 0; padding: 0; min-width: 0; }

.legal__official a {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 34px;
  padding: 6px 10px;
  border-radius: 999px;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 0.78rem;
  text-align: center;
  color: rgba(255,230,200,0.92);
  background: rgba(255,176,80,0.08);
  border: 1px solid rgba(255,176,80,0.22);
  transition: background .18s ease, color .18s ease, border-color .18s ease, transform .18s ease;
  word-break: break-word;
  text-decoration: none;
}

.legal__official a:hover {
  background: rgba(255,176,80,0.18);
  border-color: rgba(255,176,80,0.45);
  color: #fff;
  transform: translateY(-1px);
}

.legal__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 28px;
  align-items: stretch;
}

.legal__card {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 22px 22px 20px;
  border-radius: 16px;
  background: rgba(18,19,34,0.6);
  border: 1px solid rgba(255,255,255,0.08);
  transition: transform .2s ease, border-color .2s ease, background .2s ease;
}

.legal__card:hover {
  transform: translateY(-2px);
  border-color: rgba(255,255,255,0.14);
  background: rgba(22,23,40,0.7);
}

.legal__card h3 {
  margin: 0 0 8px;
  font-size: 1.02rem;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: -0.005em;
}

.legal__card p {
  margin: 0;
  flex: 1;
  color: rgba(255,255,255,0.55);
  font-size: 0.9rem;
  line-height: 1.7;
}

.legal__card code {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 0.84em;
  padding: 1px 5px;
  border-radius: 4px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.08);
}

/* Responsive */
@media (max-width: 968px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .usecases-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .coverage-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .hero-title {
    font-size: 40px;
  }

  .exec-grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .principles-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .nav-links {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(10, 10, 26, 0.98);
    flex-direction: column;
    justify-content: center;
    gap: 24px;
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.3s ease;
    z-index: 1000;
  }

  .nav-links.nav-open {
    opacity: 1;
    pointer-events: all;
  }

  .nav-actions {
    display: none;
  }

  .nav-actions-mobile {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-top: 24px;
    width: 200px;
  }

  .nav-actions-mobile .nav-btn-login,
  .nav-actions-mobile .nav-btn-signup {
    width: 100%;
  }

  .mobile-menu-toggle {
    display: flex;
  }

  .hero-title {
    font-size: 32px;
  }

  .hero-desc {
    font-size: 15px;
  }

  .stats__grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .section-title {
    font-size: 28px;
  }

  .features-grid,
  .usecases-grid {
    grid-template-columns: 1fr;
  }

  .coverage-grid {
    grid-template-columns: 1fr 1fr;
  }

  .footer-top {
    flex-direction: column;
    gap: 32px;
  }

  .footer-links {
    flex-wrap: wrap;
    gap: 32px;
  }

  .hero-actions {
    flex-direction: column;
  }

  .mockup-body {
    height: 240px;
  }

  .mockup-tabs {
    display: none;
  }

  .mockup-sidebar {
    width: 44px;
    padding: 12px 8px;
  }

  .sidebar-item {
    width: 28px;
    height: 28px;
  }

  .mockup-panels {
    flex-direction: column;
  }

  .cta-title {
    font-size: 28px;
  }

  .cta__inner {
    max-width: 100%;
  }

  .cta-actions {
    flex-direction: column;
  }

  .license-grid {
    grid-template-columns: 1fr;
  }

  .legal__grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .legal__official ul {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .hero-title {
    font-size: 26px;
  }

  .coverage-grid {
    grid-template-columns: 1fr;
  }

  .stats__grid {
    grid-template-columns: 1fr;
  }

  .stats__val {
    font-size: 28px;
  }

  .principles-grid {
    grid-template-columns: 1fr;
  }

  .license-form__row {
    grid-template-columns: 1fr;
  }

  .legal__alert {
    grid-template-columns: 1fr;
    padding: 20px;
  }

  .legal__grid {
    grid-template-columns: 1fr;
  }

  .mockup-body {
    height: 180px;
  }

  .mockup-sidebar {
    display: none;
  }

  .cta-title {
    font-size: 22px;
  }
}
</style>