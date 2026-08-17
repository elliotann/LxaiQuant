<template>
  <div class="risk-control-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>风控规则</span>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="日内风控" name="intraday">
          <el-form
            :model="form"
            label-width="160px"
            style="max-width: 720px"
          >
            <el-form-item label="启用日内风控">
              <el-switch v-model="form.enabled" />
            </el-form-item>

            <el-form-item label="预警阈值（%）">
              <el-input-number
                v-model="warningPercent"
                :min="0"
                :max="50"
                :step="0.1"
                :precision="1"
              />
            </el-form-item>

            <el-form-item label="熔断阈值（%）">
              <el-input-number
                v-model="stopPercent"
                :min="0"
                :max="80"
                :step="0.1"
                :precision="1"
              />
            </el-form-item>

            <el-form-item label="止盈锁定阈值（%）">
              <el-input-number
                v-model="profitTargetPercent"
                :min="0"
                :max="200"
                :step="0.1"
                :precision="1"
              />
            </el-form-item>

            <el-form-item label="默认止损（%）">
              <el-input-number
                v-model="defaultStopLossPercent"
                :min="0"
                :max="50"
                :step="0.1"
                :precision="1"
              />
            </el-form-item>

            <el-form-item label="滑点（%）">
              <el-input-number
                v-model="slippagePercent"
                :min="0"
                :max="5"
                :step="0.01"
                :precision="2"
              />
            </el-form-item>

            <el-form-item label="每笔风险（%）">
              <el-input-number
                v-model="riskPerExposurePercent"
                :min="0"
                :max="20"
                :step="0.1"
                :precision="1"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save"
                >保存</el-button
              >
              <el-button :loading="loading" @click="load">刷新</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { getIntradayRiskConfig, saveIntradayRiskConfig } from "@/api";

type IntradayRiskConfig = {
  enabled?: boolean;
  warningRatio?: number;
  stopRatio?: number;
  profitTargetRatio?: number;
  defaultStopLossPercent?: number;
  slippagePercent?: number;
  riskPerExposure?: number;
};

const activeTab = ref("intraday");
const loading = ref(false);
const saving = ref(false);

const form = reactive({
  enabled: false,
  warningRatio: 0.02,
  stopRatio: 0.04,
  profitTargetRatio: 0.06,
  defaultStopLossPercent: 0.01,
  slippagePercent: 0.001,
  riskPerExposure: 0.01,
});

const toPercent = (ratio: number) => Number(((ratio || 0) * 100).toFixed(4));
const toRatio = (pct: number) => Number(((pct || 0) / 100).toFixed(8));

const warningPercent = computed({
  get: () => toPercent(form.warningRatio),
  set: (v) => {
    form.warningRatio = toRatio(v as number);
  },
});

const stopPercent = computed({
  get: () => toPercent(form.stopRatio),
  set: (v) => {
    form.stopRatio = toRatio(v as number);
  },
});

const profitTargetPercent = computed({
  get: () => toPercent(form.profitTargetRatio),
  set: (v) => {
    form.profitTargetRatio = toRatio(v as number);
  },
});

const defaultStopLossPercent = computed({
  get: () => toPercent(form.defaultStopLossPercent),
  set: (v) => {
    form.defaultStopLossPercent = toRatio(v as number);
  },
});

const slippagePercent = computed({
  get: () => toPercent(form.slippagePercent),
  set: (v) => {
    form.slippagePercent = toRatio(v as number);
  },
});

const riskPerExposurePercent = computed({
  get: () => toPercent(form.riskPerExposure),
  set: (v) => {
    form.riskPerExposure = toRatio(v as number);
  },
});

const unwrapConfig = (resp: any) => {
  if (resp && typeof resp === "object" && "success" in resp && "data" in resp) {
    return (resp as any).data || {};
  }
  return resp || {};
};

const load = async () => {
  loading.value = true;
  try {
    const resp = await getIntradayRiskConfig();
    const cfg = unwrapConfig(resp) as IntradayRiskConfig;
    form.enabled = !!cfg.enabled;
    form.warningRatio = Number(cfg.warningRatio ?? form.warningRatio);
    form.stopRatio = Number(cfg.stopRatio ?? form.stopRatio);
    form.profitTargetRatio = Number(
      cfg.profitTargetRatio ?? form.profitTargetRatio,
    );
    form.defaultStopLossPercent = Number(
      cfg.defaultStopLossPercent ?? form.defaultStopLossPercent,
    );
    form.slippagePercent = Number(cfg.slippagePercent ?? form.slippagePercent);
    form.riskPerExposure = Number(cfg.riskPerExposure ?? form.riskPerExposure);
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : "加载失败";
    ElMessage.error(msg);
  } finally {
    loading.value = false;
  }
};

const save = async () => {
  saving.value = true;
  try {
    await saveIntradayRiskConfig({ ...form });
    await load();
    ElMessage.success("保存成功");
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : "保存失败";
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  load();
});
</script>

<style scoped>
.risk-control-page {
  padding: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
