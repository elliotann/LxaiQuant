<template>
  <div class="strategy-management-container">
    <!-- 创建策略页面 -->
    <div class="create-strategy">
      <div class="page-header">
        <h2>{{ isFromTemplate ? "从模板创建策略" : "创建策略" }}</h2>
        <el-button @click="handleBack">返回</el-button>
      </div>

      <el-card>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
          <el-form-item label="策略名称" prop="name">
            <el-input
              v-model="form.name"
              placeholder="请输入策略名称"
              maxlength="50"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="策略描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="4"
              placeholder="请输入策略描述"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="策略类型" prop="type">
            <el-select v-model="form.type" placeholder="请选择策略类型">
              <el-option label="趋势跟踪" value="trend" />
              <el-option label="均值回归" value="mean_reversion" />
              <el-option label="套利" value="arbitrage" />
              <el-option label="高频交易" value="high_frequency" />
              <el-option label="机器学习" value="machine_learning" />
            </el-select>
          </el-form-item>

          <el-form-item label="交易品种" prop="symbol">
            <el-select
              v-model="form.symbol"
              placeholder="搜索交易品种"
              filterable
              remote
              :remote-method="searchSymbols"
              :loading="symbolLoading"
            >
              <el-option
                v-for="s in symbolOptions"
                :key="s.symbol"
                :label="s.symbol"
                :value="s.symbol"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="时间框架" prop="timeframe">
            <el-select v-model="form.timeframe" placeholder="请选择时间框架">
              <el-option label="1分钟" value="1m" />
              <el-option label="3分钟" value="3m" />
              <el-option label="5分钟" value="5m" />
              <el-option label="15分钟" value="15m" />
              <el-option label="30分钟" value="30m" />
              <el-option label="1小时" value="1h" />
              <el-option label="4小时" value="4h" />
              <el-option label="1天" value="1d" />
            </el-select>
          </el-form-item>

          <el-form-item label="初始资金" prop="initialCapital">
            <el-input-number
              v-model="form.initialCapital"
              :min="100"
              :max="1000000"
              :step="100"
              :precision="2"
            />
            <span style="margin-left: 10px">USDT</span>
          </el-form-item>

          <el-form-item label="风险参数">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="最大仓位" prop="maxPosition">
                  <el-input-number
                    v-model="form.maxPosition"
                    :min="1"
                    :max="100"
                    :step="1"
                    :precision="0"
                  />
                  <span style="margin-left: 10px">%</span>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="止损比例" prop="stopLoss">
                  <el-input-number
                    v-model="form.stopLoss"
                    :min="0.1"
                    :max="100"
                    :step="0.1"
                    :precision="1"
                  />
                  <span style="margin-left: 10px">%</span>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="止盈比例" prop="takeProfit">
                  <el-input-number
                    v-model="form.takeProfit"
                    :min="0.1"
                    :max="20"
                    :step="0.1"
                    :precision="1"
                  />
                  <span style="margin-left: 10px">%</span>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form-item>

          <el-form-item label="策略代码" prop="code">
            <div class="code-editor">
              <el-input
                v-model="form.code"
                type="textarea"
                :rows="25"
                placeholder="请输入策略代码"
              />
            </div>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="handleSubmit">创建策略</el-button>
            <el-button @click="handleReset">重置</el-button>
            <el-button @click="handleBacktest">回测</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { createStrategy } from "@/api/strategy";
import type { StrategyCreateRequest } from "@/types/strategy";
import { getSymbols, type SymbolItem } from "@/api/symbols";

const router = useRouter();
const formRef = ref<FormInstance>();
const symbolOptions = ref<SymbolItem[]>([]);
const symbolLoading = ref(false);

async function searchSymbols(query: string) {
  const q = (query || "").trim();
  if (!q) {
    symbolOptions.value = [];
    return;
  }
  symbolLoading.value = true;
  try {
    const res = await getSymbols({ keyword: q });
    symbolOptions.value = res?.data || [];
  } catch {
    symbolOptions.value = [];
  } finally {
    symbolLoading.value = false;
  }
}

const route = useRoute();

// 定义props
const props = withDefaults(
  defineProps<{
    initialMode?: string;
    templateData?: any;
    isFromTemplate?: boolean;
  }>(),
  {
    initialMode: "",
    templateData: undefined,
    isFromTemplate: false,
  },
);

// 定义事件
const emit = defineEmits<{
  (e: "view-strategy", strategy: any): void;
  (e: "edit-strategy", strategy: any): void;
  (e: "create-strategy"): void;
  (e: "back-to-list"): void;
  (e: "back-to-detail"): void;
  (e: "save-success"): void;
  (e: "create-success"): void;
}>();

const form = reactive({
  name: "",
  description: "",
  type: "",
  symbol: "",
  timeframe: "",
  initialCapital: 10000,
  maxPosition: 10,
  stopLoss: 2,
  takeProfit: 3,
  code: "",
});

// 从模板数据填充表单
const populateFormFromTemplate = (template: any) => {
  console.log("🔥 Populating form from template:", template);

  // 基本信息填充
  if (template.name) form.name = template.name;
  if (template.description) form.description = template.description;
  if (template.category) form.type = template.category;
  if (template.language)
    form.code =
      template.code ||
      `// ${template.name} 策略代码\n// 语言: ${template.language}`;

  // 从配置中提取交易品种
  if (template.config?.symbols?.length > 0) {
    form.symbol = template.config.symbols[0];
  } else if (template.parameters?.symbol) {
    form.symbol = template.parameters.symbol;
  }

  // 从配置中提取时间周期
  if (template.config?.timeframe) {
    form.timeframe = template.config.timeframe;
  } else if (template.parameters?.timeframe) {
    form.timeframe = template.parameters.timeframe;
  }

  // 从配置中提取资金管理参数
  if (template.config?.riskManagement) {
    const risk = template.config.riskManagement;
    if (risk.maxPositionSize) form.maxPosition = risk.maxPositionSize * 100; // 转换为百分比
    if (risk.stopLoss) form.stopLoss = risk.stopLoss * 100; // 转换为百分比
    if (risk.takeProfit) form.takeProfit = risk.takeProfit * 100; // 转换为百分比
    if (risk.initialCapital) form.initialCapital = risk.initialCapital;
  } else if (template.parameters) {
    const params = template.parameters;
    if (params.maxPosition) form.maxPosition = params.maxPosition;
    if (params.stopLoss) form.stopLoss = params.stopLoss;
    if (params.takeProfit) form.takeProfit = params.takeProfit;
    if (params.initialCapital) form.initialCapital = params.initialCapital;
  }

  // 如果没有配置，使用默认值
  if (!form.symbol) form.symbol = "BTC/USDT";
  if (!form.timeframe) form.timeframe = "1h";
  if (!form.initialCapital) form.initialCapital = 10000;
  if (!form.maxPosition) form.maxPosition = 10;
  if (!form.stopLoss) form.stopLoss = 2;
  if (!form.takeProfit) form.takeProfit = 3;

  console.log("🔥 Form populated:", form);
};

// 监听模板数据变化，自动填充表单
watch(
  () => props.templateData,
  (newTemplate) => {
    if (newTemplate) {
      console.log("🔥 StrategyManagement received template:", newTemplate);
      populateFormFromTemplate(newTemplate);
    }
  },
  { immediate: true },
);

const rules: FormRules = {
  name: [
    { required: true, message: "请输入策略名称", trigger: "blur" },
    {
      min: 2,
      max: 50,
      message: "策略名称长度在 2 到 50 个字符",
      trigger: "blur",
    },
  ],
  description: [
    { required: true, message: "请输入策略描述", trigger: "blur" },
    { max: 500, message: "策略描述最多500个字符", trigger: "blur" },
  ],
  type: [{ required: true, message: "请选择策略类型", trigger: "change" }],
  symbol: [{ required: true, message: "请选择交易品种", trigger: "change" }],
  timeframe: [{ required: true, message: "请选择时间周期", trigger: "change" }],
  initialCapital: [
    { required: true, message: "请输入初始资金", trigger: "blur" },
    {
      type: "number",
      min: 100,
      message: "初始资金不能少于100",
      trigger: "blur",
    },
  ],
  maxPosition: [
    { required: true, message: "请输入最大仓位", trigger: "blur" },
    {
      type: "number",
      min: 1,
      max: 100,
      message: "最大仓位在1-100之间",
      trigger: "blur",
    },
  ],
  stopLoss: [
    { required: true, message: "请输入止损比例", trigger: "blur" },
    {
      type: "number",
      min: 0.1,
      max: 100,
      message: "止损比例在0.1-100之间",
      trigger: "blur",
    },
  ],
  takeProfit: [
    { required: true, message: "请输入止盈比例", trigger: "blur" },
    {
      type: "number",
      min: 0.1,
      max: 20,
      message: "止盈比例在0.1-20之间",
      trigger: "blur",
    },
  ],
  code: [{ required: true, message: "请输入策略代码", trigger: "blur" }],
};

const handleSubmit = async () => {
  if (!formRef.value) return;

  try {
    await formRef.value.validate();

    // 构建符合后端API期望的数据格式
    const strategyData = {
      name: form.name,
      description: form.description,
      code: form.code,
      type: mapFormTypeToStrategyType(form.type),
      parameters: {
        symbol: form.symbol,
        timeframe: form.timeframe,
        initialCapital: form.initialCapital,
        maxPosition: form.maxPosition,
        stopLoss: form.stopLoss,
        takeProfit: form.takeProfit,
      },
    };

    console.log(
      "🔥 Creating strategy with data:",
      JSON.stringify(strategyData, null, 2),
    );

    // 调用API创建策略
    const response = await createStrategy(strategyData).catch((error) => {
      // 在API拦截器处理之前捕获错误，获取详细信息
      console.error("Detailed API error:", error);
      if (error.response) {
        console.error("Error response data:", error.response.data);
        console.error("Error response status:", error.response.status);
        console.error("Error response headers:", error.response.headers);
      }
      throw error; // 重新抛出错误，让后续处理继续
    });
    console.log("🔥 Strategy created successfully:", response);

    ElMessage.success(`策略 "${form.name}" 创建成功！`);

    // 触发创建成功事件
    emit("create-success");

    // 返回列表
    handleBackToList();
  } catch (error: any) {
    console.error("Strategy creation failed:", error);
    console.error("Error response:", error.response?.data);
    console.error("Error status:", error.response?.status);

    // 处理不同的错误类型
    if (error.response?.data?.errors) {
      // 显示验证错误详情
      const errors = error.response.data.errors;
      const errorMessage = errors
        .map((e: any) => e.msg || e.message)
        .join(", ");
      ElMessage.error(`验证失败: ${errorMessage}`);
    } else if (error.response?.data?.message) {
      ElMessage.error(`创建失败: ${error.response.data.message}`);
    } else if (error.message) {
      ElMessage.error(`创建失败: ${error.message}`);
    } else {
      ElMessage.error("策略创建失败，请稍后重试");
    }
  }
};

// 将表单类型映射到策略类型
const mapFormTypeToStrategyType = (formType: string) => {
  const typeMap: Record<
    string,
    "technical" | "statistical" | "ml" | "high_frequency"
  > = {
    trend: "technical",
    mean_reversion: "statistical",
    arbitrage: "statistical",
    high_frequency: "high_frequency",
    machine_learning: "ml",
  };
  return typeMap[formType] || "technical";
};

const handleReset = () => {
  if (formRef.value) {
    formRef.value.resetFields();
  }
};

const handleBacktest = () => {
  // TODO: 实现回测逻辑
  ElMessage.info("回测功能开发中");
};

const handleBack = () => {
  handleBackToList();
};

// 查看策略详情
const handleViewStrategy = (strategy) => {
  emit("view-strategy", strategy);
};

// 编辑策略
const handleEditStrategy = (strategy) => {
  emit("edit-strategy", strategy);
};

// 创建策略
const handleCreateStrategy = () => {
  emit("create-strategy");
};

// 返回列表
const handleBackToList = () => {
  emit("back-to-list");
};

// 返回详情
const handleBackToDetail = () => {
  emit("back-to-detail");
};

// 保存成功
const handleSaveSuccess = () => {
  emit("save-success");
};

// 创建成功
const handleCreateSuccess = () => {
  emit("create-success");
};

// 组件挂载时处理模板数据
onMounted(() => {
  if (props.isFromTemplate && props.templateData) {
    console.log(
      "🔥 StrategyManagement mounted with template data:",
      props.templateData,
    );
    populateFormFromTemplate(props.templateData);
    ElMessage.success(`已加载模板: ${props.templateData.name}`);
  }
});
</script>

<style scoped>
.strategy-management-container {
  height: 100vh;
  background: var(--bg-secondary);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.create-strategy {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
  background: var(--bg-secondary);
  flex: 1;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--surface-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
}

.page-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-2xl);
  font-weight: var(--font-semibold);
}

/* Element Plus 组件样式覆盖 */
:deep(.el-card) {
  background: var(--surface-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  margin-bottom: 20px;
}

:deep(.el-card__body) {
  padding: 24px;
}

:deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-weight: var(--font-medium);
  font-size: var(--font-sm);
}

:deep(.el-form-item) {
  margin-bottom: 16px;
}

:deep(.el-input__inner) {
  background: var(--input-bg);
  border: none;
  color: var(--input-text);
  border-radius: var(--radius-md);
  font-size: var(--font-base);
}

:deep(.el-input__inner:focus) {
  box-shadow: 0 0 0 2px var(--glow-primary);
}

:deep(.el-input__inner::placeholder) {
  color: var(--input-placeholder);
}

:deep(.el-textarea__inner) {
  background: var(--input-bg);
  border: none;
  color: var(--input-text);
  border-radius: var(--radius-md);
  font-size: var(--font-base);
}

:deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px var(--glow-primary);
}

:deep(.el-select) {
  width: 100%;
}

:deep(.el-select .el-input__inner) {
  cursor: pointer;
}

:deep(.el-input-number) {
  width: 100%;
}

:deep(.el-input-number .el-input__inner) {
  text-align: left;
}

.code-editor {
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--bg-primary);
  width: 100%;
  max-width: 100%;
  min-height: 300px;
  max-height: 400px;
  margin-bottom: 20px;
}

.code-editor :deep(.el-textarea__inner) {
  border: none;
  background: transparent;
  font-family: "Monaco", "Menlo", "Ubuntu Mono", "Consolas", monospace;
  font-size: 13px;
  line-height: 1.5;
  resize: none;
  color: var(--text-primary);
  width: 100% !important;
  min-height: 280px !important;
  max-height: 380px !important;
  padding: 16px !important;
}

.code-editor :deep(.el-textarea__inner):focus {
  box-shadow: none;
}

/* 按钮样式 */
:deep(.el-button) {
  border-radius: var(--radius-md);
  font-weight: var(--font-medium);
  transition: all var(--transition-normal) var(--ease-out);
}

:deep(.el-button--primary) {
  background: var(--btn-primary);
  border-color: var(--btn-primary);
  color: white;
}

:deep(.el-button--primary:hover) {
  background: var(--btn-primary-hover);
  border-color: var(--btn-primary-hover);
  box-shadow: var(--glow-primary);
}

:deep(.el-button--default) {
  background: var(--btn-secondary);
  border-color: var(--border-primary);
  color: var(--text-secondary);
}

:deep(.el-button--default:hover) {
  background: var(--bg-hover);
  border-color: var(--border-secondary);
  color: var(--text-primary);
}
</style>
