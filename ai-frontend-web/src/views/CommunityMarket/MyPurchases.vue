<template>
  <div class="my-purchases">
    <div class="page-header">
      <h2>我的已购</h2>
      <p>你购买的所有策略资产</p>
    </div>

    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="5" animated />
    </div>

    <div v-else-if="purchases.length === 0" class="empty-state">
      <el-empty description="还没有购买任何商品">
        <el-button type="primary" @click="$router.push('/community-market')">
          去市场看看
        </el-button>
      </el-empty>
    </div>

    <div v-else class="purchase-list">
      <el-card
        v-for="item in purchases"
        :key="item.id"
        class="purchase-card"
        shadow="hover"
      >
        <div class="card-content">
          <div class="card-left">
            <span class="type-tag" :class="item.productType">
              {{ typeLabel(item.productType) }}
            </span>
            <div class="info">
              <h3>{{ item.listingName }}</h3>
              <p class="desc">{{ item.listingDescription }}</p>
              <div class="meta">
                <span>作者：{{ item.authorNickname }}</span>
                <span>购买时间：{{ formatTime(item.purchaseTime) }}</span>
                <span v-if="item.creditsSpent > 0">花费：{{ item.creditsSpent }} 积分</span>
                <span v-else>免费获取</span>
              </div>
              <div v-if="item.hasUpdate" class="update-badge">
                <el-tag size="small" type="warning">有更新</el-tag>
              </div>
            </div>
          </div>
          <div class="card-actions">
            <el-button type="primary" @click="handleUse(item)">创建实例</el-button>
            <el-button
              v-if="item.hasUpdate"
              type="success"
              plain
              @click="handleSync(item)"
            >同步更新</el-button>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getMyPurchases, syncListingUpdate } from '@/api/communityMarket';
import { ElMessage } from 'element-plus';

const router = useRouter();
const purchases = ref<any[]>([]);
const loading = ref(false);

const typeLabelMap: Record<string, string> = {
  bot: '机器人',
  indicator: '指标',
  strategy: '策略模板',
  signal: '信号',
};

function typeLabel(type: string): string {
  return typeLabelMap[type] || type;
}

function formatTime(time: string): string {
  return time ? time.slice(0, 10) : '';
}

async function fetchPurchases() {
  loading.value = true;
  try {
    const res: any = await getMyPurchases();
    if (res.success && res.data) {
      purchases.value = res.data;
    }
  } catch (err) {
    console.error('获取已购列表失败', err);
    ElMessage.error('获取已购列表失败');
  } finally {
    loading.value = false;
  }
}

function handleUse(item: any) {
  const map: Record<string, string> = {
    bot: '/strategies/create',
    strategy: '/strategies/create',
  };
  const path = map[item.productType] || '/strategies';
  router.push(path);
}

async function handleSync(item: any) {
  try {
    const res: any = await syncListingUpdate(item.listingId);
    if (res.success) {
      ElMessage.success('同步成功！');
      await fetchPurchases();
    }
  } catch (err) {
    ElMessage.error('同步失败');
  }
}

onMounted(() => {
  fetchPurchases();
});
</script>

<style scoped>
.my-purchases {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 24px;
}

.page-header {
  margin-bottom: 32px;
}

.page-header h2 {
  font-size: 24px;
  margin: 0 0 8px;
  color: #303133;
}

.page-header p {
  color: #909399;
  margin: 0;
}

.purchase-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.purchase-card {
  border-radius: 8px;
}

.card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}

.card-left {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex: 1;
  min-width: 0;
}

.type-tag {
  flex-shrink: 0;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: #fff;
  white-space: nowrap;
}

.type-tag.bot { background: #409eff; }
.type-tag.indicator { background: #67c23a; }
.type-tag.strategy { background: #e6a23c; }
.type-tag.signal { background: #f56c6c; }

.info {
  flex: 1;
  min-width: 0;
}

.info h3 {
  font-size: 16px;
  margin: 0 0 4px;
  color: #303133;
}

.desc {
  font-size: 13px;
  color: #909399;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #c0c4cc;
}

.update-badge {
  margin-top: 8px;
}

.card-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.loading-wrapper,
.empty-state {
  padding: 80px 0;
  text-align: center;
}

@media (max-width: 768px) {
  .card-content {
    flex-direction: column;
    align-items: stretch;
  }
  .card-actions {
    flex-direction: row;
  }
  .meta {
    flex-wrap: wrap;
    gap: 8px;
  }
}
</style>
