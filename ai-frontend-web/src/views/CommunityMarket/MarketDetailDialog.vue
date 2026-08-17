<template>
  <el-dialog
    :model-value="visible"
    :width="720"
    top="5vh"
    :close-on-click-modal="false"
    :show-close="false"
    class="market-detail-dialog"
    @close="$emit('close')"
  >
    <template #header>
      <div class="dialog-header-close">
        <el-button text circle @click="$emit('close')">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </template>

    <div v-loading="loading" class="detail-container">
      <template v-if="detail">
        <!-- 头部：封面 + 基础信息 -->
        <div class="detail-header">
          <div class="header-cover" v-if="detail.previewImage" :style="{ background: '#f5f5f5' }">
            <img :src="detail.previewImage" :alt="detail.name" @error="imageError = true" />
          </div>
          <div v-else class="header-cover default-cover" :style="{ background: coverGradient }">
            <span class="cover-initials">{{ cardInitials(detail.name) }}</span>
          </div>
          <div class="header-info">
            <h2 class="detail-name">{{ detail.name }}</h2>
            <div class="detail-meta">
              <div class="author-info">
                <el-avatar :src="detail.authorAvatar" :size="28" />
                <span class="author-name">{{ detail.authorNickname }}</span>
              </div>
              <div class="publish-time" v-if="detail.createdAt">
                发布于: {{ formatTime(detail.createdAt) }}
              </div>
            </div>
            <div class="detail-stats">
              <div class="stat-cell">
                <el-icon><Download /></el-icon>
                <span>{{ detail.purchaseCount || 0 }}</span>
                <span class="stat-label">下载</span>
              </div>
              <div class="stat-cell">
                <el-rate :model-value="detail.avgRating || 0" disabled size="small" allow-half />
                <span class="rating-text">({{ detail.ratingCount || 0 }})</span>
              </div>
              <div class="stat-cell">
                <el-icon><View /></el-icon>
                <span>{{ detail.viewCount || 0 }}</span>
                <span class="stat-label">浏览</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 主体内容 -->
        <div class="detail-body">
          <!-- 描述 -->
          <div class="section">
            <h3>商品描述</h3>
            <p class="description">{{ detail.description || '暂无描述' }}</p>
          </div>

          <!-- 配置快照 -->
          <div v-if="detail.configSnapshot" class="section">
            <h3>{{ configSectionTitle }}</h3>
            <pre class="config-json">{{ formatJson(detail.configSnapshot) }}</pre>
          </div>

          <!-- 表现数据 -->
          <div v-if="detail.performance" class="section">
            <h3>{{ performanceSectionTitle }}</h3>
            <div class="perf-grid">
              <component :is="performanceComp" :performance="detail.performance" />
            </div>
          </div>

          <!-- 用户评价 -->
          <div class="section">
            <h3>用户评价 ({{ detail.comments?.length || 0 }})</h3>
            <div v-if="!detail.comments?.length" class="empty-comments">暂无评价</div>
            <div v-else class="comment-list">
              <div v-for="comment in detail.comments" :key="comment.id" class="comment-item">
                <div class="comment-header">
                  <el-avatar :size="22" :src="comment.userAvatar" />
                  <span class="comment-user">{{ comment.userNickname }}</span>
                  <el-rate v-if="comment.rating" :model-value="comment.rating" disabled size="small" />
                  <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
                </div>
                <p class="comment-content">{{ comment.content }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部操作栏 -->
        <div class="detail-footer">
          <div class="price-info">
            <span v-if="detail.pricingType === 'free'" class="free-badge">免费</span>
            <span v-else class="price-badge">{{ detail.price }} 积分</span>
            <span v-if="detail.vipFree" class="vip-tag">VIP免费</span>
          </div>
          <div class="action-buttons">
            <template v-if="detail.isOwn">
              <el-button disabled>我的商品</el-button>
            </template>
            <template v-else-if="detail.isPurchased">
              <el-button :loading="syncing" @click="handleSync">
                <el-icon><Refresh /></el-icon> 同步更新
                <el-tag v-if="detail.hasUpdate" size="small" type="warning" style="margin-left:4px">有更新</el-tag>
              </el-button>
              <el-button type="primary" @click="handleUse">
                <el-icon><Promotion /></el-icon> 立即使用
              </el-button>
            </template>
            <template v-else>
              <el-button type="primary" :loading="purchasing" @click="handlePurchase">
                <el-icon><ShoppingCart /></el-icon>
                {{ detail.pricingType === 'free' ? '免费获取' : `购买 (${detail.price} 积分)` }}
              </el-button>
            </template>
          </div>
        </div>
      </template>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, markRaw } from 'vue';
import { Close, Download, View, Refresh, Promotion, ShoppingCart } from '@element-plus/icons-vue';
import { getListingDetail, purchaseListing, syncListingUpdate } from '@/api/communityMarket';
import { ElMessage, ElMessageBox } from 'element-plus';

const props = defineProps<{
  visible: boolean;
  listingId: number | null;
}>();

const emit = defineEmits<{
  close: [];
  purchased: [id: number];
}>();

const GRADIENT_PRESETS = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a8edea 0%, #fed6e3 100%)',
  'linear-gradient(135deg, #d299c2 0%, #fef9d7 100%)',
  'linear-gradient(135deg, #89f7fe 0%, #66a6ff 100%)',
  'linear-gradient(135deg, #fddb92 0%, #d1fdff 100%)',
  'linear-gradient(135deg, #9890e3 0%, #b1f4cf 100%)',
  'linear-gradient(135deg, #ebc0fd 0%, #d9ded8 100%)',
  'linear-gradient(135deg, #f6d365 0%, #fda085 100%)',
];

const loading = ref(false);
const purchasing = ref(false);
const syncing = ref(false);
const detail = ref<any>(null);
const imageError = ref(false);

const coverGradient = computed(() => {
  const index = (props.listingId || 0) % GRADIENT_PRESETS.length;
  return GRADIENT_PRESETS[index];
});

const typeLabelMap: Record<string, string> = {
  bot: '机器人',
  indicator: '指标',
  strategy: '策略模板',
  signal: '信号',
};

const configSectionTitle = computed(() => {
  const map: Record<string, string> = {
    bot: '策略配置摘要',
    indicator: '指标参数说明',
    strategy: '策略逻辑说明',
  };
  return map[detail.value?.productType] || '配置快照';
});

const performanceSectionTitle = computed(() => {
  const map: Record<string, string> = {
    bot: '实盘表现',
    indicator: '信号表现',
    strategy: '回测表现',
  };
  return map[detail.value?.productType] || '表现数据';
});

// 性能表现子组件
const BotPerformance = {
  props: ['performance'],
  template: `
    <div class="perf-grid-inner">
      <div class="perf-item"><span class="perf-label">胜率</span><span class="perf-val">{{ safeData.win_rate || '-' }}%</span></div>
      <div class="perf-item"><span class="perf-label">总收益</span><span class="perf-val">{{ safeData.total_profit || '-' }}</span></div>
      <div class="perf-item"><span class="perf-label">最大回撤</span><span class="perf-val">{{ safeData.max_drawdown || '-' }}%</span></div>
      <div class="perf-item"><span class="perf-label">夏普比率</span><span class="perf-val">{{ safeData.sharpe_ratio || '-' }}</span></div>
      <div class="perf-item"><span class="perf-label">交易次数</span><span class="perf-val">{{ safeData.trade_count || '-' }}</span></div>
    </div>
  `,
  computed: {
    safeData() {
      try {
        return JSON.parse(this.performance?.performanceData || '{}');
      } catch { return {}; }
    },
  },
};

const IndicatorPerformance = {
  props: ['performance'],
  template: `
    <div class="perf-grid-inner">
      <div class="perf-item"><span class="perf-label">信号数量</span><span class="perf-val">{{ safeData.signal_count || '-' }}</span></div>
      <div class="perf-item"><span class="perf-label">准确率</span><span class="perf-val">{{ safeData.accuracy || '-' }}%</span></div>
      <div class="perf-item"><span class="perf-label">命中率</span><span class="perf-val">{{ safeData.hit_rate || '-' }}%</span></div>
      <div class="perf-item"><span class="perf-label">平均响应</span><span class="perf-val">{{ safeData.avg_response_time || '-' }}s</span></div>
    </div>
  `,
  computed: {
    safeData() {
      try {
        return JSON.parse(this.performance?.performanceData || '{}');
      } catch { return {}; }
    },
  },
};

const StrategyPerformance = {
  props: ['performance'],
  template: `
    <div class="perf-grid-inner">
      <div class="perf-item"><span class="perf-label">回测收益率</span><span class="perf-val">{{ safeData.backtest_ror || '-' }}%</span></div>
      <div class="perf-item"><span class="perf-label">最大回撤</span><span class="perf-val">{{ safeData.max_drawdown || '-' }}%</span></div>
      <div class="perf-item"><span class="perf-label">夏普比率</span><span class="perf-val">{{ safeData.sharpe_ratio || '-' }}</span></div>
      <div class="perf-item"><span class="perf-label">盈亏比</span><span class="perf-val">{{ safeData.profit_factor || '-' }}</span></div>
      <div class="perf-item"><span class="perf-label">交易次数</span><span class="perf-val">{{ safeData.trade_count || '-' }}</span></div>
    </div>
  `,
  computed: {
    safeData() {
      try {
        return JSON.parse(this.performance?.performanceData || '{}');
      } catch { return {}; }
    },
  },
};

const performanceComp = computed(() => {
  const map: Record<string, any> = {
    bot: markRaw(BotPerformance),
    indicator: markRaw(IndicatorPerformance),
    strategy: markRaw(StrategyPerformance),
  };
  return map[detail.value?.productType] || null;
});

function cardInitials(name: string): string {
  if (!name) return '?';
  if (/[\u4e00-\u9fa5]/.test(name)) {
    return name.slice(0, 2);
  }
  const words = name.trim().split(/\s+/);
  if (words.length >= 2) {
    return (words[0][0] + words[1][0]).toUpperCase();
  }
  return name.slice(0, 2).toUpperCase();
}

function formatTime(time: string): string {
  return time ? time.slice(0, 10) : '';
}

function formatJson(json: string): string {
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}

async function loadDetail() {
  if (!props.listingId) return;
  loading.value = true;
  try {
    const res: any = await getListingDetail(props.listingId);
    if (res.success && res.data) {
      detail.value = res.data;
    } else {
      ElMessage.error('获取商品详情失败');
    }
  } catch {
    ElMessage.error('获取商品详情失败');
  } finally {
    loading.value = false;
  }
}

async function handlePurchase() {
  if (!detail.value) return;
  try {
    await ElMessageBox.confirm(
      `确认花费 ${detail.value.price} 积分购买「${detail.value.name}」吗？`,
      '购买确认',
      { confirmButtonText: '确认购买', cancelButtonText: '取消', type: 'info' }
    );
    purchasing.value = true;
    const res: any = await purchaseListing(detail.value.id);
    if (res.success) {
      ElMessage.success('购买成功！');
      emit('purchased', detail.value.id);
      await loadDetail();
    }
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '购买失败');
    }
  } finally {
    purchasing.value = false;
  }
}

async function handleSync() {
  if (!detail.value) return;
  syncing.value = true;
  try {
    const res: any = await syncListingUpdate(detail.value.id);
    if (res.success) {
      ElMessage.success('同步成功！');
      await loadDetail();
    }
  } catch {
    ElMessage.error('同步失败');
  } finally {
    syncing.value = false;
  }
}

function handleUse() {
  const map: Record<string, string> = {
    bot: '/trading-bots',
    strategy: '/strategies/create',
  };
  const path = map[detail.value?.productType] || '/strategies';
  window.open(path, '_blank');
}

watch(() => props.visible, (val) => {
  if (val && props.listingId) {
    imageError.value = false;
    loadDetail();
  } else if (!val) {
    detail.value = null;
  }
});
</script>

<style scoped>
.detail-container {
  max-height: 80vh;
  overflow-y: auto;
}

.dialog-header-close {
  display: flex;
  justify-content: flex-end;
}

/* 头部：封面 */
.detail-header {
  display: flex;
  gap: 20px;
  padding: 0 24px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.header-cover {
  width: 120px;
  height: 120px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
}

.header-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.default-cover {
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-initials {
  font-size: 36px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 2px 8px rgba(0,0,0,0.2);
}

.header-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.detail-name {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 16px;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.author-name {
  font-size: 13px;
  color: var(--text-secondary);
}

.publish-time {
  font-size: 12px;
  color: var(--text-muted);
}

.detail-stats {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}

.stat-cell .el-icon {
  font-size: 14px;
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: 2px;
}

.rating-text {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: 4px;
}

/* 主体 */
.detail-body {
  padding: 20px 24px;
}

.section {
  margin-bottom: 20px;
}

.section h3 {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.description {
  color: var(--text-secondary);
  line-height: 1.7;
  font-size: 13px;
  margin: 0;
}

.config-json {
  background: var(--bg-secondary, #f5f7fa);
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
  overflow-x: auto;
  margin: 0;
  color: var(--text-secondary);
}

.perf-grid-inner {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.perf-item {
  background: var(--bg-secondary, #f5f7fa);
  padding: 10px 12px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.perf-label {
  font-size: 12px;
  color: var(--text-muted, #909399);
}

.perf-val {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #303133);
}

/* 评论 */
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-item {
  border-bottom: 1px solid var(--border-color, #ebeef5);
  padding-bottom: 10px;
}

.comment-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.comment-user {
  font-weight: 600;
  font-size: 13px;
  color: var(--text-primary);
}

.comment-time {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: auto;
}

.comment-content {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.empty-comments {
  color: var(--text-muted);
  text-align: center;
  padding: 20px;
  font-size: 13px;
}

/* 底部操作栏 */
.detail-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: var(--bg-secondary, #fafafa);
  border-top: 1px solid var(--border-color, #f0f0f0);
  border-radius: 0 0 8px 8px;
}

.price-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.free-badge {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent-green, #67c23a);
}

.price-badge {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent-orange, #e6a23c);
}

.vip-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  background: var(--el-color-warning-light-9, #fdf6ec);
  color: var(--accent-orange, #e6a23c);
}

.action-buttons {
  display: flex;
  gap: 8px;
}
</style>
