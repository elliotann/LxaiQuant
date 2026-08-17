<template>
  <div class="credits-topup">
    <div class="page-header">
      <h2>产品定价中心</h2>
      <p class="page-subtitle">订阅套餐，可叠加购买一次性积分包</p>
    </div>

    <el-row :gutter="20">
      <el-col v-for="card in planCards" :key="card.level" :span="8">
        <el-card
          :class="['plan-card', { 'plan-highlight': card.level === 'PREMIUM' }]"
          shadow="hover"
        >
          <div class="plan-header">
            <h3>{{ card.levelName }}</h3>
            <div class="plan-price">
              <span class="price">${{ card.priceMonthlyUsdt }}</span>
              <span class="period">{{ card.pricePeriod }}</span>
            </div>
          </div>

          <div class="plan-credit-pkgs">
            <div
              v-for="pkg in card.packages"
              :key="pkg.id"
              class="credit-pkg-row"
            >
              <span class="pkg-credits-text">{{ pkg.credits.toLocaleString() }} 积分</span>
            </div>
          </div>

          <div class="plan-monthly-credits">
            每月赠送 {{ card.monthlyCredits }} 积分
          </div>

          <el-divider />

          <div class="plan-features">
            <p>🤖 机器人数量: 最多{{ card.maxBots }}个</p>
            <p>📊 策略数量: 最多{{ card.maxStrategies }}个</p>
            <p>🔄 每日回测: {{ card.maxBacktestsPerDay }}次</p>
            <p>🧠 每日AI分析: {{ card.maxAiAnalysisPerDay }}次</p>
            <p>{{ card.allowCustomFactor ? '✅' : '❌' }} 自定义因子</p>
            <p>{{ card.allowMlTraining ? '✅' : '❌' }} ML训练</p>
            <p>{{ card.allowApiAccess ? '✅' : '❌' }} API访问</p>
            <p>{{ card.prioritySupport ? '✅' : '❌' }} 优先客服</p>
          </div>

          <el-divider />

          <el-button type="primary" size="large" class="plan-cta" @click="handleSubscribe(card)">
            {{ card.ctaText }}
          </el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-alert type="info" :closable="false" show-icon class="plan-note">
      <template #title>
        说明：订阅套餐即可获得每月赠送积分。积分包可单独购买，与订阅积分叠加使用，永不过期。
      </template>
    </el-alert>

    <el-dialog v-model="showDialog" width="520px" class="usdt-payment-dialog" :close-on-click-modal="false" @closed="handleClose">
      <div class="usdt-payment">
        <div class="payment-header">
          <h2 class="payment-title">USDT 扫码支付</h2>
          <p class="payment-subtitle">请按所选网络与精确金额转账，到账后系统会自动开通会员并发放积分。</p>
        </div>

        <div class="payment-status">
          <div class="status-step" :class="{ 'is-active': paymentStatus === 'DETECTED' || paymentStatus === 'CONFIRMED' || paymentStatus === 'SUCCESS' }">
            <span class="step-dot" />
            <span class="step-label">已检测到账</span>
          </div>
          <div class="status-divider" :class="{ 'is-done': paymentStatus === 'CONFIRMED' || paymentStatus === 'SUCCESS' }" />
          <div class="status-step" :class="{ 'is-active': paymentStatus === 'CONFIRMED' || paymentStatus === 'SUCCESS' }">
            <span class="step-dot" />
            <span class="step-label">已确认</span>
          </div>
        </div>

        <div class="qr-info-row">
          <div class="qr-col">
            <canvas ref="qrCanvasRef" class="qr-canvas" />
          </div>
          <div class="info-col">
            <div class="field-group">
              <div class="field-label">收款地址</div>
              <div class="address-row">
                <span class="address-text">{{ paymentAddress }}</span>
                <el-button size="small" text class="copy-btn" @click="copyAddress">
                  <template #icon><el-icon><CopyDocument /></el-icon></template>
                  复制
                </el-button>
              </div>
            </div>

            <div class="field-group" style="margin-bottom: 0">
              <div class="field-label">金额</div>
              <div class="amount-hint">请按精确金额付款——末尾高亮的尾数用于识别您的订单。</div>
              <div class="amount-display">
                <span class="amount-whole">{{ amountWhole }}</span>
                <span class="amount-tail">.{{ amountTail }}</span>
                <span class="amount-currency">USDT</span>
              </div>
            </div>
          </div>
        </div>

        <div class="info-list">
          <div class="info-item">{{ paymentAmount }} USDT</div>
          <div class="info-item">TRC20</div>
          <div class="info-item">钱包提示</div>
          <div class="info-item">等待支付</div>
          <div class="info-item">{{ expireTime }}</div>
        </div>

        <div class="payment-footer">
          <el-button type="primary" class="btn-refresh" :loading="refreshing" @click="refreshStatus">
            刷新状态
          </el-button>
          <el-button class="btn-close" @click="showDialog = false">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from "vue";
import { ElMessage } from "element-plus";
import { CopyDocument } from "@element-plus/icons-vue";
import { useMembershipStore } from "@/stores/membership";
import { createCreditsPayment, getPaymentById } from "@/api/credits";
import QRCode from "qrcode";

const store = useMembershipStore();
const showDialog = ref(false);
const paymentId = ref("");
const paymentAddress = ref("");
const paymentAmount = ref("");
const paymentCredits = ref("");
const paymentStatus = ref("");
const expireTime = ref("");
const refreshing = ref(false);
const qrCanvasRef = ref<HTMLCanvasElement | null>(null);

let pollTimer: ReturnType<typeof setInterval> | null = null;

const amountWhole = computed(() => {
  const v = String(paymentAmount.value);
  const dot = v.indexOf(".");
  return dot >= 0 ? v.substring(0, dot) : v;
});

const amountTail = computed(() => {
  const v = String(paymentAmount.value);
  const dot = v.indexOf(".");
  return dot >= 0 ? v.substring(dot + 1) : "00";
});

const CANONICAL_LEVELS = ["BASIC", "PREMIUM", "PROFESSIONAL"];

const LEVEL_ALIAS: Record<string, string> = {
  BASIC: "BASIC",
  PREMIUM: "PREMIUM",
  PROFESSIONAL: "PROFESSIONAL",
  PRO: "PROFESSIONAL",
  ADVANCED: "PROFESSIONAL",
};

const LEVEL_LABEL: Record<string, string> = {
  BASIC: "基础版",
  PREMIUM: "年用户",
  PROFESSIONAL: "无限版",
};

const LEVEL_PRICE_OVERRIDE: Record<string, string> = {
  BASIC: "19.90",
  PREMIUM: "199.90",
  PROFESSIONAL: "699.90",
};

const LEVEL_PRICE_PERIOD: Record<string, string> = {
  BASIC: "/月",
  PREMIUM: "/年",
  PROFESSIONAL: "",
};

const CTA_LABEL: Record<string, string> = {
  BASIC: "订阅基础版 →",
  PREMIUM: "订阅年用户 →",
  PROFESSIONAL: "订阅无限版 →",
};

const planCards = computed(() => {
  const sorted = [...store.packages].sort((a: any, b: any) => a.credits - b.credits);
  return store.benefits.map((b, idx) => {
    const key = LEVEL_ALIAS[b.level] || CANONICAL_LEVELS[CANONICAL_LEVELS.length - 1];
    return {
      ...b,
      levelName: LEVEL_LABEL[key] || b.level,
      priceMonthlyUsdt: LEVEL_PRICE_OVERRIDE[key] ?? b.priceMonthlyUsdt,
      pricePeriod: LEVEL_PRICE_PERIOD[key] || "/月",
      ctaText: CTA_LABEL[key] || "立即订阅",
      packages: sorted[idx] ? [sorted[idx]] : [],
    };
  });
});

watch(showDialog, async (open) => {
  if (open) {
    await nextTick();
    generateQrCode();
    startPolling();
  } else {
    stopPolling();
  }
});

function generateQrCode() {
  if (!qrCanvasRef.value || !paymentAddress.value) return;
  QRCode.toCanvas(qrCanvasRef.value, paymentAddress.value, {
    width: 140,
    margin: 1,
    color: { dark: "#000000", light: "#ffffff" },
  });
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(async () => {
    if (!paymentId.value) return;
    try {
      const res = await getPaymentById(paymentId.value);
      if (res.success && res.payment) {
        paymentStatus.value = res.payment.status || "";
        expireTime.value = formatTime(res.payment.expireAt);
        if (res.payment.status === "SUCCESS") {
          stopPolling();
          ElMessage.success("支付成功！");
        }
      }
    } catch {
      // ignore polling error
    }
  }, 5000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function formatTime(t: string | null | undefined): string {
  if (!t) return "--";
  try {
    const d = new Date(t);
    const pad = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}/${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  } catch {
    return t;
  }
}

onMounted(() => {
  store.fetchPackages();
  store.fetchBenefits();
});

function copyAddress() {
  navigator.clipboard.writeText(paymentAddress.value);
  ElMessage.success("已复制到剪贴板");
}

function handleClose() {
  stopPolling();
  paymentId.value = "";
  paymentAddress.value = "";
  paymentAmount.value = "";
  paymentCredits.value = "";
  paymentStatus.value = "";
  expireTime.value = "";
}

async function handleSubscribe(card: any) {
  const pkg = card.packages?.[0];
  if (!pkg?.id) {
    ElMessage.info("订阅功能即将上线，敬请期待");
    return;
  }
  const res = await createCreditsPayment({ packageId: pkg.id });
  if (res.success) {
    paymentId.value = res.paymentId;
    paymentAddress.value = res.paymentAddress;
    paymentAmount.value = card.priceMonthlyUsdt;
    showDialog.value = true;
  } else {
    ElMessage.error(res.message || "创建订单失败");
  }
}

async function refreshStatus() {
  if (!paymentId.value) return;
  refreshing.value = true;
  try {
    const res = await getPaymentById(paymentId.value);
    if (res.success && res.payment) {
      paymentStatus.value = res.payment.status || "";
      expireTime.value = formatTime(res.payment.expireAt);
      if (res.payment.status === "SUCCESS") {
        stopPolling();
        ElMessage.success("支付成功！");
      } else {
        ElMessage.info("未检测到支付，请确认已转账后重试");
      }
    }
  } catch {
    ElMessage.error("查询失败");
  } finally {
    refreshing.value = false;
  }
}
</script>

<style scoped>
.credits-topup { padding: 20px; max-width: 1200px; margin: 0 auto; }

.page-header { text-align: center; margin-bottom: 32px; }
.page-header h2 { font-size: 26px; font-weight: 700; margin: 0 0 8px; color: var(--el-text-color-primary); }
.page-subtitle { font-size: 14px; color: var(--el-text-color-secondary); margin: 0; }

.plan-card { text-align: center; display: flex; flex-direction: column; }
.plan-highlight { border: 2px solid var(--el-color-primary); position: relative; }
.plan-highlight::before {
  content: "推荐";
  position: absolute; top: 0; right: 0;
  background: var(--el-color-primary); color: #fff;
  font-size: 12px; padding: 2px 12px;
  border-radius: 0 4px 0 8px;
}

.plan-header h3 { font-size: 22px; margin: 0 0 12px; }
.plan-price .price { font-size: 28px; font-weight: bold; color: var(--el-color-primary); }
.plan-price .period { color: #999; font-size: 14px; }

.plan-credit-pkgs { margin: 16px 0 8px; }
.credit-pkg-row {
  display: inline-flex; align-items: center; gap: 8px;
  background: var(--el-fill-color); border: 1px solid var(--el-border-color-light);
  border-radius: 6px; padding: 6px 14px; margin: 4px;
}
.pkg-credits-text { font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }

.plan-monthly-credits { font-size: 13px; color: #e6a23c; margin-bottom: 4px; }

.plan-features p { margin: 8px 0; font-size: 14px; }

.plan-cta { width: 100%; height: 44px; font-size: 15px; }

.plan-note { margin-top: 24px; }
</style>

<style scoped>
.usdt-payment { padding: 8px 0; }

.payment-header { text-align: center; margin-bottom: 20px; }
.payment-title { font-size: 22px; font-weight: 700; margin: 0 0 8px; color: var(--el-text-color-primary); }
.payment-subtitle { font-size: 13px; color: var(--el-text-color-secondary); line-height: 1.6; margin: 0; }

.payment-status { display: flex; align-items: center; justify-content: center; gap: 0; margin-bottom: 24px; padding: 14px 20px; background: var(--el-fill-color-light); border-radius: 8px; }
.status-step { display: flex; align-items: center; gap: 8px; }
.step-dot { width: 10px; height: 10px; border-radius: 50%; background: var(--el-border-color); transition: background 0.3s; flex-shrink: 0; }
.status-step.is-active .step-dot { background: var(--el-color-primary); box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.2); }
.step-label { font-size: 14px; color: var(--el-text-color-secondary); transition: color 0.3s; white-space: nowrap; }
.status-step.is-active .step-label { color: var(--el-color-primary); font-weight: 600; }
.status-divider { flex: 1; height: 2px; background: var(--el-border-color); margin: 0 12px; min-width: 40px; transition: background 0.3s; border-radius: 1px; }
.status-divider.is-done { background: var(--el-color-primary); }

.qr-info-row { display: flex; gap: 20px; margin-bottom: 20px; }
.qr-col { flex-shrink: 0; }
.qr-canvas { display: block; width: 140px; height: 140px; border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 8px; background: #fff; }
.info-col { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; }

.field-group { margin-bottom: 16px; }
.field-label { font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); margin-bottom: 6px; }

.address-row { display: flex; align-items: center; gap: 6px; background: var(--el-fill-color); border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 8px 10px; }
.address-text { flex: 1; font-family: monospace; font-size: 12px; word-break: break-all; color: var(--el-text-color-primary); line-height: 1.5; }
.copy-btn { flex-shrink: 0; }

.amount-hint { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 6px; line-height: 1.4; }
.amount-display { font-size: 26px; font-weight: 700; color: var(--el-text-color-primary); line-height: 1.3; }
.amount-tail { color: var(--el-color-warning); }
.amount-currency { font-size: 16px; font-weight: 600; margin-left: 6px; color: var(--el-text-color-secondary); }

.info-list { background: var(--el-fill-color); border-radius: 8px; padding: 12px 16px; margin-bottom: 24px; }
.info-item { font-size: 13px; color: var(--el-text-color-secondary); line-height: 2; }

.payment-footer { display: flex; gap: 12px; }
.btn-refresh { flex: 1; height: 44px; font-size: 15px; }
.btn-close { flex: 1; height: 44px; font-size: 15px; }
</style>
