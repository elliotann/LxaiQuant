<template>
  <div class="publish-page">
    <div class="page-header">
      <h2>发布到社区市场</h2>
      <p>将你的策略资产分享给社区其他用户</p>
    </div>

    <el-steps :active="step" align-center class="publish-steps">
      <el-step title="选择类型" icon="Select" />
      <el-step title="选择源商品" icon="Collection" />
      <el-step title="填写信息" icon="Edit" />
      <el-step title="定价提交" icon="Finished" />
    </el-steps>

    <div class="step-content">
      <!-- Step 1: 选择商品类型 -->
      <div v-if="step === 1" class="step-panel">
        <h3>选择要发布的商品类型</h3>
        <div class="type-cards">
          <div
            v-for="t in productTypes"
            :key="t.value"
            class="type-card"
            :class="{ active: form.productType === t.value }"
            @click="form.productType = t.value"
          >
            <span class="type-icon">{{ t.icon }}</span>
            <span class="type-name">{{ t.label }}</span>
            <span class="type-desc">{{ t.desc }}</span>
          </div>
        </div>
        <div class="step-actions">
          <el-button type="primary" :disabled="!form.productType" @click="step = 2">
            下一步
          </el-button>
        </div>
      </div>

      <!-- Step 2: 选择源商品 -->
      <div v-if="step === 2" class="step-panel">
        <h3>选择要发布的{{ currentTypeLabel }}</h3>
        <p class="step-hint">从你的{{ currentTypeLabel }}列表中选择一个</p>

        <div v-if="loadingSources" class="loading-wrapper">
          <el-skeleton :rows="3" animated />
        </div>

        <div v-else-if="sourceList.length === 0" class="empty-state">
          <el-empty :description="`你还没有${currentTypeLabel}`">
            <el-button type="primary" @click="handleCreateSource">
              去创建{{ currentTypeLabel }}
            </el-button>
          </el-empty>
        </div>

        <div v-else class="source-list">
          <el-radio-group v-model="form.sourceId" class="source-group">
            <el-radio
              v-for="src in sourceList"
              :key="src.id"
              :value="String(src.id)"
              class="source-item"
              border
            >
              <div class="source-info">
                <span class="source-name">{{ src.name }}</span>
                <span v-if="src.description" class="source-desc">{{ src.description }}</span>
              </div>
            </el-radio>
          </el-radio-group>
        </div>

        <div class="step-actions">
          <el-button @click="step = 1">上一步</el-button>
          <el-button type="primary" :disabled="!form.sourceId" @click="step = 3">
            下一步
          </el-button>
        </div>
      </div>

      <!-- Step 3: 填写信息 -->
      <div v-if="step === 3" class="step-panel">
        <h3>填写发布信息</h3>
        <el-form :model="form" label-width="80px" class="publish-form">
          <el-form-item label="名称" required>
            <el-input v-model="form.name" placeholder="请输入商品展示名称" maxlength="100" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              placeholder="描述你的商品特点和使用方法"
              maxlength="2000"
              show-word-limit
            />
          </el-form-item>
          <el-form-item label="封面图">
            <el-upload
              :auto-upload="false"
              :show-file-list="false"
              accept="image/*"
              @change="handleImageChange"
            >
              <div class="upload-wrapper">
                <img v-if="form.previewImage" :src="form.previewImage" class="upload-preview" />
                <div v-else class="upload-placeholder">
                  <el-icon><Plus /></el-icon>
                  <span>上传封面</span>
                </div>
              </div>
            </el-upload>
          </el-form-item>
          <el-form-item label="标签">
            <el-select
              v-model="form.tags"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="输入标签后回车添加"
            >
              <el-option v-for="tag in presetTags" :key="tag" :label="tag" :value="tag" />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="step-actions">
          <el-button @click="step = 2">上一步</el-button>
          <el-button type="primary" :disabled="!form.name" @click="step = 4">
            下一步
          </el-button>
        </div>
      </div>

      <!-- Step 4: 定价提交 -->
      <div v-if="step === 4" class="step-panel">
        <h3>定价与提交</h3>
        <el-form :model="form" label-width="100px" class="publish-form">
          <el-form-item label="定价方式">
            <el-radio-group v-model="form.pricingType">
              <el-radio value="free">
                <el-tag type="success">免费</el-tag> 供所有用户免费使用
              </el-radio>
              <el-radio value="paid">
                <el-tag type="warning">付费</el-tag> 设置积分价格
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.pricingType === 'paid'" label="积分价格" required>
            <el-input-number
              v-model="form.price"
              :min="1"
              :max="999999"
              :precision="0"
            />
            <span class="price-unit">积分</span>
          </el-form-item>
          <el-form-item label="VIP 免费">
            <el-switch v-model="form.vipFree" />
            <span class="switch-hint">开启后 VIP 用户可免费获取</span>
          </el-form-item>
        </el-form>

        <!-- 提交前摘要 -->
        <el-card class="summary-card" v-if="form.productType">
          <template #header><span>发布摘要</span></template>
          <div class="summary-item"><span class="sum-label">类型</span>{{ currentTypeLabel }}</div>
          <div class="summary-item"><span class="sum-label">名称</span>{{ form.name }}</div>
          <div class="summary-item"><span class="sum-label">定价</span>{{ form.pricingType === 'paid' ? form.price + ' 积分' : '免费' }}</div>
          <div class="summary-item"><span class="sum-label">VIP免费</span>{{ form.vipFree ? '是' : '否' }}</div>
        </el-card>

        <div class="step-actions">
          <el-button @click="step = 3">上一步</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            提交审核
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Plus } from '@element-plus/icons-vue';
import { publishListing } from '@/api/communityMarket';
import { getTradingBots } from '@/api/robot';
import { getStrategies } from '@/api/strategy';
import { ElMessage } from 'element-plus';

const router = useRouter();

const step = ref(1);
const submitting = ref(false);
const loadingSources = ref(false);
const sourceList = ref<any[]>([]);

const productTypes = [
  { value: 'bot', label: '交易机器人', icon: '🤖', desc: '已配置好的量化交易机器人' },
  { value: 'indicator', label: '技术指标', icon: '📊', desc: '自定义技术分析指标' },
  { value: 'strategy', label: '策略模板', icon: '📋', desc: '完整的交易策略模板' },
];

const presetTags = ['网格', '趋势跟踪', '缠论', '布林带', '均线策略', '突破策略', '套利', '高频'];

const currentTypeLabel = computed(() => {
  const t = productTypes.find((p) => p.value === form.value.productType);
  return t ? t.label : '';
});

const form = ref({
  productType: '',
  sourceId: '',
  name: '',
  description: '',
  previewImage: '',
  pricingType: 'free',
  price: 100,
  vipFree: false,
  tags: [] as string[],
});

// 当进入 Step 2 时，根据选择的商品类型加载用户的源商品列表
watch(step, async (newStep) => {
  if (newStep === 2 && form.value.productType) {
    await fetchSources();
  }
});

// 当商品类型变更时重置 sourceId
watch(() => form.value.productType, () => {
  form.value.sourceId = '';
  sourceList.value = [];
});

async function fetchSources() {
  loadingSources.value = true;
  try {
    const type = form.value.productType;
    let list: any[] = [];
    if (type === 'bot') {
      const res = await getTradingBots();
      list = (res as any)?.data?.records || (res as any)?.data?.rows || (Array.isArray(res) ? res : []);
    } else if (type === 'strategy') {
      const res = await getStrategies();
      list = (res as any)?.data?.records || (res as any)?.data?.rows || (Array.isArray(res) ? res : []);
    } else if (type === 'indicator') {
      // TODO: 当后端提供指标列表 API 后替换
      list = [];
    }
    sourceList.value = list.map((item: any) => ({
      id: item.id,
      name: item.name || item.botName || item.strategyName || '未命名',
      description: item.description || item.remarks || '',
    }));
  } catch (err) {
    console.error('获取源商品列表失败', err);
    sourceList.value = [];
  } finally {
    loadingSources.value = false;
  }
}

function handleImageChange(file: any) {
  const reader = new FileReader();
  reader.onload = (e: any) => {
    form.value.previewImage = e.target.result;
  };
  reader.readAsDataURL(file.raw);
}

async function handleCreateSource() {
  const map: Record<string, string> = {
    bot: '/trading-bots',
    indicator: '/market-kline-v1',
    strategy: '/strategies',
  };
  router.push(map[form.value.productType] || '/');
}

async function handleSubmit() {
  if (!form.value.name) {
    ElMessage.warning('请输入商品名称');
    return;
  }

  submitting.value = true;
  try {
    const res: any = await publishListing(form.value);
    if (res.success) {
      ElMessage.success('发布成功！等待审核');
      router.push('/community-market');
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '发布失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.publish-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 40px 24px;
}

.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-header h2 {
  font-size: 28px;
  margin: 0 0 8px;
  color: #303133;
}

.page-header p {
  color: #909399;
  margin: 0;
}

.publish-steps {
  margin-bottom: 40px;
}

.step-content {
  min-height: 400px;
}

.step-panel h3 {
  font-size: 18px;
  margin: 0 0 20px;
  color: #303133;
}

.step-hint {
  color: #909399;
  margin: -12px 0 20px;
  font-size: 13px;
}

.type-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.type-card {
  border: 2px solid #ebeef5;
  border-radius: 12px;
  padding: 24px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.type-card:hover {
  border-color: #409eff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.12);
}

.type-card.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.type-icon {
  font-size: 36px;
  line-height: 1;
}

.type-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.type-desc {
  font-size: 12px;
  color: #909399;
}

.source-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.source-item {
  width: 100%;
  height: auto;
  padding: 12px 16px;
}

.source-item :deep(.el-radio__label) {
  width: 100%;
}

.source-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.source-name {
  font-weight: 600;
  color: #303133;
}

.source-desc {
  font-size: 12px;
  color: #909399;
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}

.publish-form {
  max-width: 600px;
  margin: 0 auto;
}

.upload-wrapper {
  width: 200px;
  height: 120px;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
}

.upload-wrapper:hover {
  border-color: #409eff;
}

.upload-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #c0c4cc;
  font-size: 13px;
}

.price-unit {
  margin-left: 8px;
  color: #909399;
}

.switch-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.summary-card {
  margin-top: 20px;
}

.summary-item {
  padding: 8px 0;
  font-size: 14px;
  color: #606266;
}

.summary-item .sum-label {
  display: inline-block;
  width: 80px;
  color: #909399;
}

.loading-wrapper,
.empty-state {
  padding: 40px 0;
}

@media (max-width: 768px) {
  .type-cards {
    grid-template-columns: 1fr;
  }
}
</style>
