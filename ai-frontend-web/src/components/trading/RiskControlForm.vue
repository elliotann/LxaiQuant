<template>
  <div class="risk-control-form" :class="{ 'is-readonly': readonly }">
    <!-- 日内风控 -->
    <template v-if="layout === 'cards'">
      <el-card class="form-section">
        <template #header>
          <div class="card-header">
            <el-icon><WarningFilled /></el-icon>
            <span>日内风控</span>
          </div>
        </template>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="日盈利目标(%)">
              <el-input-number :model-value="dailyProfitTarget" @update:model-value="(val: any) => $emit('update:dailyProfitTarget', val)" :min="0" :max="100" :precision="2" :step="0.1" style="width: 100%" />
              <div class="form-item-tip">机器人每日的盈利目标百分比</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="日亏损限额(%)">
              <el-input-number :model-value="dailyLossLimit" @update:model-value="(val: any) => $emit('update:dailyLossLimit', val)" :min="0" :max="100" :precision="2" :step="0.1" style="width: 100%" />
              <div class="form-item-tip">机器人每日的亏损限额百分比</div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-card>
    </template>
    <template v-else>
      <el-divider content-position="left">日内风控</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="日盈利目标(%)">
            <el-input-number :model-value="dailyProfitTarget" @update:model-value="(val: any) => $emit('update:dailyProfitTarget', val)" :min="0" :max="100" :precision="2" :step="0.1" style="width: 100%" />
            <div class="form-item-tip">机器人每日的盈利目标百分比</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="日亏损限额(%)">
            <el-input-number :model-value="dailyLossLimit" @update:model-value="(val: any) => $emit('update:dailyLossLimit', val)" :min="0" :max="100" :precision="2" :step="0.1" style="width: 100%" />
            <div class="form-item-tip">机器人每日的亏损限额百分比</div>
          </el-form-item>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  dailyProfitTarget: number;
  dailyLossLimit: number;
  layout?: 'cards' | 'inline';
  readonly?: boolean;
}>();

defineEmits<{
  (e: "update:dailyProfitTarget", val: number): void;
  (e: "update:dailyLossLimit", val: number): void;
}>();
</script>

<style scoped>
.form-section {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.card-header .el-icon {
  font-size: 18px;
}

.form-hint {
  font-size: 12px;
  color: #909399;
}

.form-item-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 2px;
}

:deep(.el-form-item) {
  margin-bottom: 16px;
}

:deep(.el-card__body) {
  padding: 16px 20px;
}

.el-divider {
  margin: 20px 0;
}

/* ----- 只读模式 ----- */
.risk-control-form.is-readonly {
  position: relative;
  pointer-events: none;
  opacity: 0.65;
}

.risk-control-form.is-readonly::before {
  content: '已购买的机器人，参数只读';
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 10;
  background: #f56c6c;
  color: #fff;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  pointer-events: none;
}
</style>