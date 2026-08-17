<template>
  <div class="community-market">
    <!-- 头部 Tab + 搜索筛选 -->
    <div class="market-header">
      <div class="header-left">
        <h2 class="page-title">
          社区市场
        </h2>
        <el-radio-group v-model="activeType" @change="handleTypeChange">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="bot">机器人</el-radio-button>
          <el-radio-button label="indicator">指标</el-radio-button>
          <el-radio-button label="strategy">策略模板</el-radio-button>
          <el-radio-button label="signal">信号</el-radio-button>
        </el-radio-group>
      </div>
      <div class="header-right">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索商品名称..."
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-radio-group v-model="pricingFilter" @change="handleFilterChange">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="free">免费</el-radio-button>
          <el-radio-button label="paid">付费</el-radio-button>
        </el-radio-group>
        <el-select v-model="sortBy" style="width: 150px" @change="handleFilterChange">
          <el-option label="最新发布" value="newest" />
          <el-option label="最热门" value="popular" />
          <el-option label="评分最高" value="rating" />
          <el-option label="价格从低到高" value="price_asc" />
          <el-option label="价格从高到低" value="price_desc" />
        </el-select>
        <el-button text @click="$router.push('/community-market/my-listings')">
          <el-icon><ShoppingCart /></el-icon>
          我的发布
        </el-button>
      </div>
    </div>

    <!-- 商品网格 -->
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-empty v-else-if="listings.length === 0" description="暂无商品" />

    <div v-else class="product-grid">
      <div
        v-for="item in listings"
        :key="item.id"
        class="product-card"
        @click="goToDetail(item.id)"
      >
        <div class="card-cover" :style="{ background: item.previewImage ? '#f5f5f5' : coverGradient(item) }">
          <img v-if="item.previewImage" :src="item.previewImage" :alt="item.name" @error="onImageError" />
          <div v-else class="default-cover">
            <span class="cover-title">{{ cardInitials(item.name) }}</span>
            <span class="cover-subtitle">{{ item.name }}</span>
          </div>
          <span class="price-tag" :class="item.pricingType === 'paid' ? 'paid' : 'free'">
            {{ item.pricingType === 'paid' ? item.price + ' 积分' : '免费' }}
          </span>
          <span class="type-tag" :class="item.productType">
            {{ typeLabel(item.productType) }}
          </span>
        </div>
        <div class="card-content">
          <h3 class="card-title" :title="item.name">{{ item.name }}</h3>
          <p class="card-desc">{{ item.description || '暂无描述' }}</p>
          <div class="card-author">
            <el-avatar :size="24" :src="item.authorAvatar" />
            <span class="author-name">{{ item.authorNickname }}</span>
          </div>
          <div class="card-stats">
            <span class="stat-item">
              <el-icon><Download /></el-icon>
              {{ item.purchaseCount || 0 }}
            </span>
            <span class="stat-item">
              <el-icon><StarFilled /></el-icon>
              {{ formatRating(item.avgRating) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="totalListings > pageSize" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="totalListings"
        layout="prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 商品详情弹窗 -->
    <MarketDetailDialog
      :visible="dialogVisible"
      :listing-id="selectedListingId"
      @close="onDialogClose"
      @purchased="onDialogPurchased"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Search, ShoppingCart, Download, StarFilled } from '@element-plus/icons-vue';
import { getListings } from '@/api/communityMarket';
import { ElMessage } from 'element-plus';
import MarketDetailDialog from './MarketDetailDialog.vue';

const selectedListingId = ref<number | null>(null);
const dialogVisible = ref(false);

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
  'linear-gradient(135deg, #f6d365 0%, #fda085 100%)'
];

const listings = ref<any[]>([]);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(20);
const totalListings = ref(0);
const activeType = ref('');
const pricingFilter = ref('');
const sortBy = ref('newest');
const searchKeyword = ref('');

const typeLabelMap: Record<string, string> = {
  bot: '机器人',
  indicator: '指标',
  strategy: '策略模板',
  signal: '信号',
};

function typeLabel(type: string): string {
  return typeLabelMap[type] || type;
}

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

function coverGradient(item: any): string {
  const index = (item.id || 0) % GRADIENT_PRESETS.length;
  return GRADIENT_PRESETS[index];
}

function formatRating(rating: any): string {
  const r = parseFloat(rating) || 0;
  return r > 0 ? r.toFixed(1) : '-';
}

function onImageError(e: Event) {
  (e.target as HTMLImageElement).style.display = 'none';
}

async function fetchListings() {
  loading.value = true;
  try {
    const res: any = await getListings({
      page: currentPage.value,
      pageSize: pageSize.value,
      productType: activeType.value,
      keyword: searchKeyword.value || undefined,
      pricingType: pricingFilter.value || undefined,
      sortBy: sortBy.value,
    });
    if (res.success && res.data) {
      listings.value = res.data.items || [];
      totalListings.value = res.data.total || 0;
    } else {
      listings.value = [];
      totalListings.value = 0;
    }
  } catch (err) {
    console.error('获取市场列表失败', err);
    ElMessage.error('获取市场列表失败');
    listings.value = [];
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  currentPage.value = 1;
  fetchListings();
}

function handleTypeChange() {
  currentPage.value = 1;
  fetchListings();
}

function handleFilterChange() {
  currentPage.value = 1;
  fetchListings();
}

function handlePageChange(page: number) {
  currentPage.value = page;
  fetchListings();
}

function goToDetail(id: number) {
  selectedListingId.value = id;
  dialogVisible.value = true;
}

function onDialogClose() {
  dialogVisible.value = false;
  selectedListingId.value = null;
}

function onDialogPurchased(id: number) {
  fetchListings();
}

onMounted(() => {
  fetchListings();
});
</script>

<style scoped>
.community-market {
  padding: 24px;
  min-height: calc(100vh - 120px);
  background: var(--bg-primary);
}

/* 头部卡片：标题 + 筛选 + 搜索 + 排序 + 发布按钮 */
.market-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px 20px;
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-glass);
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 响应式商品网格 */
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.product-card {
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: var(--shadow-glass);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  border-color: var(--border-glow-primary);
}

.card-cover {
  position: relative;
  width: 100%;
  height: 140px;
  overflow: hidden;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.default-cover {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  position: relative;
  overflow: hidden;
}

.default-cover::before {
  content: '';
  position: absolute;
  top: -20%;
  right: -20%;
  width: 80%;
  height: 80%;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
}

.default-cover::after {
  content: '';
  position: absolute;
  bottom: -30%;
  left: -20%;
  width: 60%;
  height: 60%;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}

.cover-title {
  font-size: 36px;
  font-weight: 700;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  z-index: 1;
  letter-spacing: 2px;
}

.cover-subtitle {
  font-size: 12px;
  margin-top: 8px;
  opacity: 0.9;
  max-width: 80%;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  z-index: 1;
}

.price-tag {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  z-index: 2;
}

.price-tag.free {
  background: #52c41a;
  color: #fff;
}

.price-tag.paid {
  background: linear-gradient(135deg, #f5af19 0%, #f12711 100%);
  color: #fff;
}

.type-tag {
  position: absolute;
  bottom: 8px;
  left: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  z-index: 2;
}

.card-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.card-content .card-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 6px 0;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-content .card-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0 0 8px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.5;
  min-height: 36px;
}

.card-author {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.author-name {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-stats {
  display: flex;
  gap: 12px;
  margin-top: auto;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.stat-item .el-icon {
  font-size: 14px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 32px;
  padding: 16px;
  background: var(--glass-bg);
  backdrop-filter: blur(20px);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-glass);
}

@media (max-width: 768px) {
  .market-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  .header-right {
    flex-wrap: wrap;
    gap: 8px;
  }
  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
