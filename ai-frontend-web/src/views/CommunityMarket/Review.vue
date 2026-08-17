<template>
  <div class="review-page">
    <div class="page-header">
      <h2>商品审核管理</h2>
      <div class="header-actions">
        <el-select
          v-model="productTypeFilter"
          placeholder="商品类型"
          clearable
          size="small"
          @change="fetchPending"
        >
          <el-option label="全部" value="" />
          <el-option label="机器人" value="bot" />
          <el-option label="指标" value="indicator" />
          <el-option label="策略模板" value="strategy" />
        </el-select>
      </div>
    </div>

    <!-- 统计 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <span class="stat-label">待审核</span>
            <span class="stat-value pending">{{ stats.pending }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <span class="stat-label">已通过</span>
            <span class="stat-value approved">{{ stats.approved }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <span class="stat-label">已拒绝</span>
            <span class="stat-value rejected">{{ stats.rejected }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 待审核列表 -->
    <el-card class="list-card">
      <template #header><span>待审核列表</span></template>

      <el-table :data="pendingList" v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="tagType(row.productType)" size="small">
              {{ typeLabel(row.productType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column label="定价" width="100">
          <template #default="{ row }">
            {{ row.pricingType === 'paid' ? row.price + ' 积分' : '免费' }}
          </template>
        </el-table-column>
        <el-table-column prop="authorNickname" label="发布者" width="120" />
        <el-table-column prop="createdAt" label="提交时间" width="160">
          <template #default="{ row }">{{ row.createdAt?.slice(0, 16) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button
              type="success"
              size="small"
              @click="handleReview(row, 'approved')"
            >通过</el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleReview(row, 'rejected')"
            >拒绝</el-button>
            <el-button
              size="small"
              @click="viewDetail(row)"
            >详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
    >
      <p v-if="dialogAction === 'approved'" class="dialog-desc">
        确认通过该商品的上架审核？通过后将在市场对所有用户可见。
      </p>
      <p v-else class="dialog-desc">
        确认拒绝该商品？请填写拒绝原因。
      </p>
      <el-input
        v-if="dialogAction === 'rejected'"
        v-model="reviewNote"
        type="textarea"
        :rows="3"
        placeholder="请输入拒绝原因"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="reviewing"
          @click="confirmReview"
        >确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getPendingList, reviewListing, getReviewStats } from '@/api/communityMarket';
import { ElMessage } from 'element-plus';

const router = useRouter();

const pendingList = ref<any[]>([]);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);
const productTypeFilter = ref('');
const stats = ref({ pending: 0, approved: 0, rejected: 0 });

const dialogVisible = ref(false);
const dialogAction = ref('');
const currentRow = ref<any>(null);
const reviewNote = ref('');
const reviewing = ref(false);

const dialogTitle = ref('');

const typeLabelMap: Record<string, string> = {
  bot: '机器人',
  indicator: '指标',
  strategy: '策略模板',
  signal: '信号',
};

function typeLabel(type: string): string {
  return typeLabelMap[type] || type;
}

function tagType(type: string): 'primary' | 'success' | 'warning' | 'danger' {
  const map: Record<string, any> = { bot: 'primary', indicator: 'success', strategy: 'warning', signal: 'danger' };
  return map[type] || 'info';
}

async function fetchStats() {
  try {
    const res: any = await getReviewStats();
    if (res.success && res.data) {
      stats.value = res.data;
    }
  } catch (err) {
    console.error('获取审核统计失败', err);
  }
}

async function fetchPending() {
  loading.value = true;
  try {
    const res: any = await getPendingList(
      currentPage.value,
      pageSize.value,
      productTypeFilter.value || undefined
    );
    if (res.success && res.data) {
      pendingList.value = res.data.items || [];
      total.value = res.data.total || 0;
    }
  } catch (err) {
    console.error('获取待审核列表失败', err);
    ElMessage.error('获取待审核列表失败');
  } finally {
    loading.value = false;
  }
}

function handleReview(row: any, action: string) {
  currentRow.value = row;
  dialogAction.value = action;
  dialogTitle.value = action === 'approved' ? '确认通过审核' : '确认拒绝';
  reviewNote.value = '';
  dialogVisible.value = true;
}

async function confirmReview() {
  if (!currentRow.value) return;

  reviewing.value = true;
  try {
    const res: any = await reviewListing({
      id: currentRow.value.id,
      action: dialogAction.value,
      note: reviewNote.value,
    });
    if (res.success) {
      ElMessage.success(
        dialogAction.value === 'approved' ? '已通过审核' : '已拒绝'
      );
      dialogVisible.value = false;
      await fetchPending();
      await fetchStats();
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败');
  } finally {
    reviewing.value = false;
  }
}

function viewDetail(row: any) {
  router.push(`/community-market/item/${row.id}`);
}

function handlePageChange(page: number) {
  currentPage.value = page;
  fetchPending();
}

onMounted(() => {
  fetchStats();
  fetchPending();
});
</script>

<style scoped>
.review-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 22px;
  margin: 0;
  color: #303133;
}

.stats-row {
  margin-bottom: 24px;
}

.stat-item {
  text-align: center;
  padding: 12px;
}

.stat-label {
  display: block;
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
}

.stat-value.pending { color: #e6a23c; }
.stat-value.approved { color: #67c23a; }
.stat-value.rejected { color: #f56c6c; }

.list-card {
  border-radius: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 16px;
  padding: 16px 0;
}

.dialog-desc {
  color: #606266;
  line-height: 1.6;
}
</style>
