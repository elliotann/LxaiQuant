<template>
  <div class="market-detail">
    <!-- 头部 -->
    <div class="detail-header">
      <div class="header-bg" />
      <div class="header-content">
        <el-button class="back-btn" text @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回市场
        </el-button>
        <div class="header-info">
          <div class="cover-wrapper">
            <img
              :src="detail.previewImage || defaultImage"
              :alt="detail.name"
              @error="onImageError"
            />
            <span class="type-badge" :class="detail.productType">
              {{ typeLabel(detail.productType) }}
            </span>
          </div>
          <div class="info-text">
            <h1>{{ detail.name }}</h1>
            <div class="author-row">
              <el-avatar :size="24" :src="detail.authorAvatar" />
              <span>{{ detail.authorNickname }}</span>
            </div>
            <div class="stats-row">
              <div class="stat-item">
                <span class="stat-label">评分</span>
                <el-rate :model-value="detail.avgRating" disabled size="small" />
                <span class="stat-val">({{ detail.ratingCount }})</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">已售</span>
                <span class="stat-val">{{ detail.purchaseCount }}</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">浏览</span>
                <span class="stat-val">{{ detail.viewCount }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="detail-body">
      <div class="body-left">
        <!-- 描述 -->
        <el-card class="section-card">
          <template #header><span>商品描述</span></template>
          <p class="description">{{ detail.description || '暂无描述' }}</p>
        </el-card>

        <!-- 差异化配置区（按商品类型动态渲染） -->
        <el-card v-if="detail.configSnapshot" class="section-card">
          <template #header><span>{{ configSectionTitle }}</span></template>
          <pre class="config-json">{{ formatJson(detail.configSnapshot) }}</pre>
        </el-card>

        <!-- 表现数据 -->
        <el-card v-if="detail.performance" class="section-card">
          <template #header><span>{{ performanceSectionTitle }}</span></template>
          <component :is="performanceComp" :performance="detail.performance" />
        </el-card>

        <!-- 评论 -->
        <el-card class="section-card">
          <template #header><span>用户评价 ({{ detail.comments?.length || 0 }})</span></template>
          <div v-if="!detail.comments?.length" class="empty-comments">暂无评价</div>
          <div v-else class="comment-list">
            <div v-for="comment in detail.comments" :key="comment.id" class="comment-item">
              <div class="comment-header">
                <el-avatar :size="24" :src="comment.userAvatar" />
                <span class="comment-user">{{ comment.userNickname }}</span>
                <el-rate v-if="comment.rating" :model-value="comment.rating" disabled size="small" />
                <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
              </div>
              <p class="comment-content">{{ comment.content }}</p>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 侧边栏 - 操作区 -->
      <div class="body-right">
        <el-card class="action-card">
          <div class="price-area">
            <span v-if="detail.pricingType === 'paid'" class="price">
              {{ detail.price }} 积分
            </span>
            <span v-else class="price free">免费</span>
            <span v-if="detail.vipFree" class="vip-tag">VIP免费</span>
          </div>

          <div class="action-buttons">
            <template v-if="detail.isOwn">
              <el-button type="warning" disabled>我的商品</el-button>
            </template>
            <template v-else-if="detail.isPurchased">
              <el-button type="primary" @click="handleUse">创建实例</el-button>
              <el-button
                v-if="detail.hasUpdate"
                type="success"
                plain
                @click="handleSync"
              >同步更新</el-button>
            </template>
            <template v-else>
              <el-button type="primary" size="large" @click="handlePurchase">
                {{ detail.pricingType === 'paid' ? `购买 (${detail.price} 积分)` : '免费获取' }}
              </el-button>
            </template>
          </div>

          <div class="tags" v-if="detail.tags">
            <el-tag
              v-for="tag in tagList"
              :key="tag"
              size="small"
              class="tag-item"
            >{{ tag }}</el-tag>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, markRaw } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ArrowLeft } from '@element-plus/icons-vue';
import { getListingDetail, purchaseListing, syncListingUpdate } from '@/api/communityMarket';
import { ElMessage, ElMessageBox } from 'element-plus';

const route = useRoute();
const router = useRouter();
const defaultImage = new URL('@/assets/images/default-product.png', import.meta.url).href;

const detail = ref<any>({});
const currentUserId = ref('');

const typeLabelMap: Record<string, string> = {
  bot: '机器人',
  indicator: '指标',
  strategy: '策略模板',
  signal: '信号',
};

function typeLabel(type: string): string {
  return typeLabelMap[type] || type;
}

const tagList = computed(() => {
  if (!detail.value.tags) return [];
  return detail.value.tags.split(',').filter(Boolean);
});

const configSectionTitle = computed(() => {
  const map: Record<string, string> = {
    bot: '策略配置摘要',
    indicator: '指标参数说明',
    strategy: '策略逻辑说明',
  };
  return map[detail.value.productType] || '配置快照';
});

const performanceSectionTitle = computed(() => {
  const map: Record<string, string> = {
    bot: '实盘表现',
    indicator: '信号表现',
    strategy: '回测表现',
  };
  return map[detail.value.productType] || '表现数据';
});

// 动态表现组件
const BotPerformance = {
  props: ['performance'],
  template: `
    <div class="perf-grid">
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
    <div class="perf-grid">
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
    <div class="perf-grid">
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
  return map[detail.value.productType] || null;
});

function onImageError(e: Event) {
  (e.target as HTMLImageElement).src = defaultImage;
}

function formatJson(json: string): string {
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}

function formatTime(time: string): string {
  return time ? time.slice(0, 10) : '';
}

async function fetchDetail() {
  try {
    const id = Number(route.params.id);
    const res: any = await getListingDetail(id);
    if (res.success && res.data) {
      detail.value = res.data;
    }
  } catch (err) {
    console.error('获取详情失败', err);
    ElMessage.error('获取商品详情失败');
  }
}

async function handlePurchase() {
  try {
    await ElMessageBox.confirm(
      `确认花费 ${detail.value.price} 积分购买「${detail.value.name}」吗？`,
      '购买确认',
      { confirmButtonText: '确认购买', cancelButtonText: '取消', type: 'info' }
    );
    const res: any = await purchaseListing(detail.value.id);
    if (res.success) {
      ElMessage.success('购买成功！');
      await fetchDetail();
    }
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '购买失败');
    }
  }
}

async function handleSync() {
  try {
    const res: any = await syncListingUpdate(detail.value.id);
    if (res.success) {
      ElMessage.success('同步成功！');
      await fetchDetail();
    }
  } catch (err) {
    ElMessage.error('同步失败');
  }
}

function handleUse() {
  const map: Record<string, string> = {
    bot: '/trading-bots',
    strategy: '/strategies/create',
  };
  const path = map[detail.value.productType] || '/strategies';
  router.push(path);
}

function goBack() {
  router.push('/community-market');
}

onMounted(() => {
  fetchDetail();
});
</script>

<style scoped>
.market-detail {
  min-height: 100vh;
  background: #f5f7fa;
}

.detail-header {
  position: relative;
  overflow: hidden;
}

.header-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  height: 280px;
}

.header-content {
  position: relative;
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px 24px;
}

.back-btn {
  color: rgba(255,255,255,0.9) !important;
  margin-bottom: 16px;
}

.header-info {
  display: flex;
  gap: 24px;
  align-items: flex-end;
}

.cover-wrapper {
  position: relative;
  width: 180px;
  height: 180px;
  border-radius: 12px;
  overflow: hidden;
  border: 3px solid rgba(255,255,255,0.3);
  flex-shrink: 0;
}

.cover-wrapper img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.type-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: #fff;
}

.type-badge.bot { background: #409eff; }
.type-badge.indicator { background: #67c23a; }
.type-badge.strategy { background: #e6a23c; }
.type-badge.signal { background: #f56c6c; }

.info-text {
  color: #fff;
  padding-bottom: 8px;
}

.info-text h1 {
  font-size: 28px;
  margin: 0 0 12px;
}

.author-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  opacity: 0.9;
}

.stats-row {
  display: flex;
  gap: 24px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.stat-label { opacity: 0.7; }
.stat-val { font-weight: 600; }

.detail-body {
  display: flex;
  gap: 24px;
  max-width: 1200px;
  margin: -60px auto 0;
  padding: 0 24px 40px;
  position: relative;
}

.body-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.body-right {
  width: 320px;
  flex-shrink: 0;
}

.section-card {
  border-radius: 8px;
}

.description {
  color: #606266;
  line-height: 1.8;
  margin: 0;
}

.config-json {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  margin: 0;
}

.perf-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.perf-item {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.perf-label {
  font-size: 12px;
  color: #909399;
}

.perf-val {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.action-card {
  position: sticky;
  top: 16px;
  border-radius: 8px;
}

.price-area {
  text-align: center;
  margin-bottom: 16px;
}

.price {
  font-size: 28px;
  font-weight: 700;
  color: #e6a23c;
}

.price.free {
  color: #67c23a;
}

.vip-tag {
  display: inline-block;
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  background: #fdf6ec;
  color: #e6a23c;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.action-buttons .el-button {
  width: 100%;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comment-item {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 12px;
}

.comment-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.comment-user {
  font-weight: 600;
  font-size: 13px;
}

.comment-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-left: auto;
}

.comment-content {
  margin: 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}

.empty-comments {
  color: #c0c4cc;
  text-align: center;
  padding: 24px;
}

@media (max-width: 768px) {
  .detail-body {
    flex-direction: column;
  }
  .body-right {
    width: 100%;
  }
  .header-info {
    flex-direction: column;
    align-items: flex-start;
  }
  .cover-wrapper {
    width: 120px;
    height: 120px;
  }
}
</style>
