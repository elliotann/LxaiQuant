<template>
  <div class="signal-service-management">
    <div class="page-header">
      <h2>信号服务管理</h2>
    </div>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-card>
          <div class="section-title">服务配置</div>
          <el-form :model="form" label-width="120px" class="service-form">
            <el-form-item label="服务类">
              <el-select
                v-model="form.serviceKey"
                placeholder="请选择服务类"
                style="width: 100%"
                @change="handleServiceChange"
              >
                <el-option
                  v-for="service in serviceOptions"
                  :key="service.key"
                  :label="service.label"
                  :value="service.key"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="配置名称">
              <el-input v-model="form.name" placeholder="请输入配置名称" />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="form.enabled" />
            </el-form-item>
            <el-divider>参数配置</el-divider>
            <div class="param-list" v-if="currentParameters.length">
              <el-collapse v-model="activeGroups" class="param-groups">
                <el-collapse-item
                  v-for="group in groupedParameters"
                  :key="group.key"
                  :name="group.key"
                >
                  <template #title>
                    <span>{{ group.label }}</span>
                    <el-tag size="small" style="margin-left: 8px">{{ group.params.length }}</el-tag>
                    <el-button size="small" text type="info" style="margin-left: auto" @click.stop="resetGroupDefaults(group.key)">恢复默认</el-button>
                  </template>
                  <el-form-item
                    v-for="param in group.params"
                    :key="param.key"
                    :label="param.label"
                  >
                    <template #label>
                      <span v-if="param.description">
                        <el-tooltip :content="param.description" placement="top" effect="dark">
                          <span>{{ param.label }}<el-icon style="margin-left: 4px; vertical-align: middle; font-size: 14px;"><InfoFilled /></el-icon></span>
                        </el-tooltip>
                      </span>
                      <span v-else>{{ param.label }}</span>
                    </template>
                    <el-input-number
                      v-if="param.type === 'number'"
                      v-model="form.params[param.key]"
                      :min="param.min"
                      :max="param.max"
                      :step="param.step || 1"
                      controls-position="right"
                      style="width: 100%"
                    />
                    <el-switch
                      v-else-if="param.type === 'boolean'"
                      v-model="form.params[param.key]"
                    />
                    <el-select
                      v-else-if="param.type === 'select'"
                      v-model="form.params[param.key]"
                      placeholder="请选择"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="option in param.options || []"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                    <el-input
                      v-else
                      v-model="form.params[param.key]"
                      placeholder="请输入参数值"
                    />
                  </el-form-item>
                </el-collapse-item>
              </el-collapse>
            </div>
            <div class="empty-params" v-else>请选择服务类以配置参数</div>

            <el-divider>权重规则引擎</el-divider>
            <div class="weight-rule-summary" @click="openWeightRuleEngine(form.id ?? undefined)">
              <el-tag :type="form.weightRules?.enabled ? 'success' : 'info'" size="small">
                {{ form.weightRules?.enabled ? '已启用' : '未启用' }}
              </el-tag>
              <span class="weight-rule-count">{{ form.weightRules?.rules?.length || 0 }} 条规则</span>
              <el-icon class="weight-rule-edit-icon"><Edit /></el-icon>
            </div>

            <el-form-item>
              <el-button type="primary" @click="handleSave">保存配置</el-button>
              <el-button @click="handleReset">重置</el-button>
              <el-button v-if="currentParameters.length" text type="info" @click="resetAllDefaults">恢复全部默认</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card>
          <div class="section-title">
            配置列表
            <el-button size="small" text type="primary" style="float: right" @click="loadConfigs">
              刷新
            </el-button>
          </div>
          <el-table :data="tableData" border stripe style="width: 100%">
            <el-table-column prop="name" label="配置名称" min-width="140" />
            <el-table-column
              prop="serviceLabel"
              label="服务类"
              min-width="180"
            />
            <el-table-column prop="enabled" label="状态" min-width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? "启用" : "停用" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="权重规则" min-width="180">
              <template #default="{ row }">
                <div class="wr-table-cell">
                  <el-tag v-if="row.weightRules?.enabled" size="small" type="success">已启用</el-tag>
                  <el-tag v-else-if="row.weightRules" size="small" type="info">已关闭</el-tag>
                  <el-tag v-else size="small" type="warning">未配置</el-tag>
                  <span class="wr-table-count">{{ row.weightRules?.rules?.length || 0 }} 条</span>
                  <el-button size="small" text type="primary" @click="openWeightRuleEngine(row.id)">
                    管理
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              prop="updatedAt"
              label="更新时间"
              min-width="160"
            />
            <el-table-column label="操作" min-width="200">
              <template #default="{ row }">
                <el-button
                  size="small"
                  type="primary"
                  text
                  @click="handleEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  size="small"
                  text
                  type="primary"
                  @click="handleCopy(row)"
                >
                  复制
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  text
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <div class="bottom-spacer"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, inject } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { InfoFilled, Edit, Plus } from "@element-plus/icons-vue";
import {
  getSignalServiceDefinitions,
  getSignalServiceConfigs,
  createSignalServiceConfig,
  updateSignalServiceConfig,
  deleteSignalServiceConfig,
  type SignalServiceDefinition,
  type SignalServiceConfig,
  type WeightRuleConfig,
} from "@/api/priceSignal";

const serviceDefinitions = ref<SignalServiceDefinition[]>([]);

const serviceOptions = computed(() =>
  serviceDefinitions.value.map(({ key, label }) => ({ key, label })),
);

const configs = ref<SignalServiceConfig[]>([]);

const form = reactive({
  id: null as number | null,
  name: "",
  serviceKey: "",
  enabled: true,
  params: {} as Record<string, any>,
  weightRules: undefined as WeightRuleConfig | undefined,
});

const currentService = computed(() =>
  serviceDefinitions.value.find((service) => service.key === form.serviceKey),
);
const currentParameters = computed(
  () => currentService.value?.parameters || [],
);

const GROUP_LABELS: Record<string, string> = {
  core: "核心参数",
  ema: "EMA 过滤",
  macd1: "MACD 过滤 1",
  macd2: "MACD 过滤 2",
  risk: "风险模块",
  swing: "双Swing横盘",
  volatility: "波动率自适应",
  price: "价格变动过滤",
  rangeTrading: "横盘交易",
  smc: "SMC订单块横盘",
};

const activeGroups = ref<string[]>(["core"]);

const openWeightRuleEngine = inject<(configId?: number) => void>("openWeightRuleEngine", () => {});

const groupedParameters = computed(() => {
  const params = currentParameters.value;
  const map = new Map<string, { key: string; label: string; params: any[] }>();
  params.forEach((p) => {
    const g = p.group || "_other";
    if (!map.has(g)) {
      map.set(g, { key: g, label: GROUP_LABELS[g] || g, params: [] });
    }
    map.get(g)!.params.push(p);
  });
  return Array.from(map.values());
});

const applyDefaultParams = (serviceKey: string) => {
  const service = serviceDefinitions.value.find(
    (item) => item.key === serviceKey,
  );
  if (!service) {
    form.params = {};
    return;
  }
  const nextParams: Record<string, any> = {};
  service.parameters.forEach((param) => {
    nextParams[param.key] = param.defaultValue;
  });
  form.params = nextParams;
};

const handleServiceChange = (serviceKey: string) => {
  applyDefaultParams(serviceKey);
};

const resetForm = () => {
  form.id = null;
  form.name = "";
  form.serviceKey = "";
  form.enabled = true;
  form.params = {};
  form.weightRules = undefined;
};

const handleReset = () => {
  resetForm();
};

const handleSave = async () => {
  if (!form.serviceKey) {
    ElMessage.warning("请选择服务类");
    return;
  }
  if (!form.name.trim()) {
    ElMessage.warning("请输入配置名称");
    return;
  }
  const serviceLabel =
    serviceDefinitions.value.find((item) => item.key === form.serviceKey)
      ?.label || form.serviceKey;
  const payload: SignalServiceConfig = {
    id: form.id || undefined,
    name: form.name.trim(),
    serviceKey: form.serviceKey,
    enabled: form.enabled,
    params: { ...form.params },
    weightRules: form.weightRules,
  };
  const response = form.id
    ? await updateSignalServiceConfig(form.id, payload)
    : await createSignalServiceConfig(payload);
  if (response.success) {
    ElMessage.success(`已保存 ${serviceLabel} 配置`);
    await loadConfigs();
    resetForm();
  } else {
    ElMessage.error(response.message || "保存失败");
  }
};

const handleEdit = (row: SignalServiceConfig) => {
  form.id = row.id || null;
  form.name = row.name;
  form.serviceKey = row.serviceKey;
  form.enabled = row.enabled;
  form.params = { ...row.params };
  form.weightRules = row.weightRules ? { ...row.weightRules } : undefined;
};

const handleDelete = async (row: SignalServiceConfig) => {
  try {
    await ElMessageBox.confirm("确定要删除该配置吗？", "删除确认", {
      type: "warning",
    });
    if (!row.id) {
      ElMessage.error("配置ID不存在");
      return;
    }
    const response = await deleteSignalServiceConfig(row.id);
    if (response.success) {
      ElMessage.success("配置已删除");
      await loadConfigs();
      if (form.id === row.id) {
        resetForm();
      }
    } else {
      ElMessage.error(response.message || "删除失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      ElMessage.error("删除失败");
    }
  }
};

const handleCopy = (row: SignalServiceConfig) => {
  form.id = null;
  form.name = row.name + " (副本)";
  form.serviceKey = row.serviceKey;
  form.enabled = row.enabled;
  form.params = { ...row.params };
  form.weightRules = row.weightRules ? { ...row.weightRules } : undefined;
};

const resetGroupDefaults = (groupKey: string) => {
  const service = currentService.value;
  if (!service) return;
  service.parameters
    .filter((p) => (p.group || "_other") === groupKey)
    .forEach((p) => {
      form.params[p.key] = p.defaultValue;
    });
};

const resetAllDefaults = () => {
  applyDefaultParams(form.serviceKey);
};

const tableData = computed(() => {
  return configs.value.map((item) => ({
    ...item,
    serviceLabel:
      serviceDefinitions.value.find(
        (service) => service.key === item.serviceKey,
      )?.label || item.serviceKey,
  }));
});

const loadDefinitions = async () => {
  const response = await getSignalServiceDefinitions();
  if (response.success) {
    serviceDefinitions.value = response.data || [];
    const hasSsl = serviceDefinitions.value.some(
      (d) => d.key === "SslChannelSignService",
    );
    if (!hasSsl) {
      serviceDefinitions.value.push({
        key: "SslChannelSignService",
        label: "SSL Channel",
        parameters: [
          { key: "wicks", label: "使用影线", type: "boolean", defaultValue: false },
          { key: "highlight-state", label: "高亮状态", type: "boolean", defaultValue: true },
          {
            key: "combine-mode",
            label: "信号合并模式",
            type: "select",
            defaultValue: "any",
            options: [
              { label: "any", value: "any" },
              { label: "both", value: "both" },
              { label: "weighted", value: "weighted" },
            ],
          },
          { key: "use-risk-module", label: "启用风险模块", type: "boolean", defaultValue: false },
          { key: "risk-module-evaluators", label: "风险评估器列表", type: "text", defaultValue: "" },
          { key: "channel1.show-ma1", label: "通道1显示MA1", type: "boolean", defaultValue: true },
          {
            key: "channel1.ma1-type",
            label: "通道1 MA1类型",
            type: "select",
            defaultValue: "EMA",
            options: [
              { label: "SMA", value: "SMA" },
              { label: "EMA", value: "EMA" },
              { label: "WMA", value: "WMA" },
              { label: "SMMA (RMA)", value: "SMMA (RMA)" },
              { label: "VWMA", value: "VWMA" },
            ],
          },
          {
            key: "channel1.ma1-source",
            label: "通道1 MA1价格源",
            type: "select",
            defaultValue: "high",
            options: [
              { label: "high", value: "high" },
              { label: "low", value: "low" },
              { label: "close", value: "close" },
              { label: "open", value: "open" },
            ],
          },
          { key: "channel1.ma1-length", label: "通道1 MA1周期", type: "number", defaultValue: 100, min: 1, max: 1000, step: 1 },
          { key: "channel1.ma1-color", label: "通道1 MA1颜色", type: "text", defaultValue: "green" },
          { key: "channel1.show-ma2", label: "通道1显示MA2", type: "boolean", defaultValue: true },
          {
            key: "channel1.ma2-type",
            label: "通道1 MA2类型",
            type: "select",
            defaultValue: "EMA",
            options: [
              { label: "SMA", value: "SMA" },
              { label: "EMA", value: "EMA" },
              { label: "WMA", value: "WMA" },
              { label: "SMMA (RMA)", value: "SMMA (RMA)" },
              { label: "VWMA", value: "VWMA" },
            ],
          },
          {
            key: "channel1.ma2-source",
            label: "通道1 MA2价格源",
            type: "select",
            defaultValue: "low",
            options: [
              { label: "high", value: "high" },
              { label: "low", value: "low" },
              { label: "close", value: "close" },
              { label: "open", value: "open" },
            ],
          },
          { key: "channel1.ma2-length", label: "通道1 MA2周期", type: "number", defaultValue: 100, min: 1, max: 1000, step: 1 },
          { key: "channel1.ma2-color", label: "通道1 MA2颜色", type: "text", defaultValue: "red" },
          { key: "channel1.show-labels", label: "通道1显示标签", type: "boolean", defaultValue: true },
          { key: "channel2.enabled", label: "启用通道2", type: "boolean", defaultValue: false },
          { key: "channel2.show-ma3", label: "通道2显示MA3", type: "boolean", defaultValue: false },
          {
            key: "channel2.ma3-type",
            label: "通道2 MA3类型",
            type: "select",
            defaultValue: "SMA",
            options: [
              { label: "SMA", value: "SMA" },
              { label: "EMA", value: "EMA" },
              { label: "WMA", value: "WMA" },
              { label: "SMMA (RMA)", value: "SMMA (RMA)" },
              { label: "VWMA", value: "VWMA" },
            ],
          },
          {
            key: "channel2.ma3-source",
            label: "通道2 MA3价格源",
            type: "select",
            defaultValue: "high",
            options: [
              { label: "high", value: "high" },
              { label: "low", value: "low" },
              { label: "close", value: "close" },
              { label: "open", value: "open" },
            ],
          },
          { key: "channel2.ma3-length", label: "通道2 MA3周期", type: "number", defaultValue: 20, min: 1, max: 1000, step: 1 },
          { key: "channel2.ma3-color", label: "通道2 MA3颜色", type: "text", defaultValue: "orange" },
          { key: "channel2.show-ma4", label: "通道2显示MA4", type: "boolean", defaultValue: false },
          {
            key: "channel2.ma4-type",
            label: "通道2 MA4类型",
            type: "select",
            defaultValue: "SMA",
            options: [
              { label: "SMA", value: "SMA" },
              { label: "EMA", value: "EMA" },
              { label: "WMA", value: "WMA" },
              { label: "SMMA (RMA)", value: "SMMA (RMA)" },
              { label: "VWMA", value: "VWMA" },
            ],
          },
          {
            key: "channel2.ma4-source",
            label: "通道2 MA4价格源",
            type: "select",
            defaultValue: "low",
            options: [
              { label: "high", value: "high" },
              { label: "low", value: "low" },
              { label: "close", value: "close" },
              { label: "open", value: "open" },
            ],
          },
          { key: "channel2.ma4-length", label: "通道2 MA4周期", type: "number", defaultValue: 20, min: 1, max: 1000, step: 1 },
          { key: "channel2.ma4-color", label: "通道2 MA4颜色", type: "text", defaultValue: "blue" },
          { key: "channel2.show-labels", label: "通道2显示标签", type: "boolean", defaultValue: true },
        ],
      });
    }
  } else {
    ElMessage.error(response.message || "加载服务定义失败");
  }
};

const loadConfigs = async () => {
  const response = await getSignalServiceConfigs();
  if (response.success) {
    configs.value = response.data || [];
  } else {
    ElMessage.error(response.message || "加载配置列表失败");
  }
};

onMounted(async () => {
  await loadDefinitions();
  await loadConfigs();
});
</script>

<style scoped>
.signal-service-management {
  padding: 20px;
}

.page-header {
  margin-bottom: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-text);
  margin-bottom: 16px;
}

.service-form {
  margin-top: 8px;
}

.param-list {
  padding-bottom: 8px;
}

.param-groups {
  border-top: none;
}

.param-groups :deep(.el-collapse-item__header) {
  font-weight: 600;
  font-size: 13px;
  padding-left: 4px;
}

.param-groups :deep(.el-collapse-item__content) {
  padding-bottom: 4px;
}

.param-groups :deep(.el-form-item) {
  margin-bottom: 14px;
}

.param-groups :deep(.el-form-item__label) {
  font-size: 12px;
}

.empty-params {
  color: var(--muted-text);
  font-size: 13px;
  padding: 8px 0 16px;
}

.bottom-spacer {
  height: 40px;
}

.weight-rule-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px dashed var(--border-color, #dcdfe6);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 16px;
}

.weight-rule-summary:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.weight-rule-count {
  font-size: 13px;
  color: var(--muted-text, #909399);
}

.weight-rule-edit-icon {
  margin-left: auto;
  color: var(--muted-text, #909399);
  font-size: 14px;
}

.wr-table-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.wr-table-count {
  font-size: 12px;
  color: var(--muted-text, #909399);
  white-space: nowrap;
}
</style>
