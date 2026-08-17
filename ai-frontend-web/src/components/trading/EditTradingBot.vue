<template>
  <el-drawer
    v-model="visible"
    :title="props.botId ? '编辑交易机器人' : '创建交易机器人'"
    size="680px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
      v-loading="loading"
    >
      <el-card shadow="never">
        <el-divider content-position="left">基本信息</el-divider>
        <el-form-item label="机器人名称" prop="botName">
          <el-input
            v-model="formData.botName"
            placeholder="请输入机器人名称"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="用户ID" prop="userId">
          <el-select
            v-if="isAdmin"
            v-model="formData.userId"
            placeholder="请选择用户"
            filterable
            style="width: 100%"
            :loading="loadingUsers"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.value"
              :label="user.label"
              :value="user.value"
            />
          </el-select>
          <el-input
            v-else
            v-model="formData.userId"
            :disabled="true"
            placeholder="当前登录用户"
          />
        </el-form-item>
        <el-divider content-position="left">交易配置</el-divider>
        <el-form-item label="交易所" prop="exchange">
          <el-select
            v-model="formData.exchange"
            placeholder="请选择交易所"
            style="width: 100%"
          >
            <el-option
              v-for="exchange in exchangeOptions"
              :key="exchange"
              :label="exchange"
              :value="exchange"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="账户ID" prop="accountId">
          <el-select
            v-model="formData.accountId"
            placeholder="请选择账户"
            filterable
            style="width: 100%"
            :loading="loadingAccounts"
            :disabled="!formData.exchange"
          >
            <el-option
              v-for="account in filteredAccountOptions"
              :key="account.value"
              :label="account.label"
              :value="account.value"
            >
              <div
                style="
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                "
              >
                <span>{{ account.label }}</span>
                <el-tooltip
                  v-if="account.platform"
                  :content="`平台: ${account.platform}`"
                  placement="right"
                >
                  <el-icon
                    style="color: #909399; font-size: 14px; margin-left: 8px"
                  >
                    <InfoFilled />
                  </el-icon>
                </el-tooltip>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="策略" prop="strategyId">
          <el-select
            v-model="formData.strategyId"
            placeholder="请选择策略"
            filterable
            style="width: 100%"
            :loading="loadingStrategies"
          >
            <el-option
              v-for="strategy in strategyOptions"
              :key="strategy.value"
              :label="strategy.label"
              :value="strategy.value"
            >
              <div
                style="
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                "
              >
                <span>{{ strategy.label }}</span>
                <el-tooltip
                  v-if="strategy.description"
                  :content="strategy.description"
                  placement="right"
                >
                  <el-icon
                    style="color: #909399; font-size: 14px; margin-left: 8px"
                  >
                    <InfoFilled />
                  </el-icon>
                </el-tooltip>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="做单方向" prop="direction">
          <el-radio-group v-model="formData.direction">
            <el-radio label="BOTH">双向</el-radio>
            <el-radio label="LONG">只做多</el-radio>
            <el-radio label="SHORT">只做空</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="杠杆倍数">
          <el-input-number
            v-model="configForm.leverage"
            :min="1"
            :precision="0"
            :step="1"
            placeholder="请输入杠杆倍数"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="手续费率 (%)" prop="commissionRate">
          <el-input-number
            v-model="configForm.commissionRate"
            :min="0"
            :max="1"
            :step="0.001"
            :precision="4"
            placeholder="0.045"
            style="width: 100%"
          />
          <span style="font-size: 12px; color: #909399; margin-left: 8px; white-space: nowrap;">小数格式，如 0.045 = 4.5%</span>
        </el-form-item>
        <el-form-item label="交易对" prop="tradingPair">
          <el-select
            v-model="formData.tradingPair"
            placeholder="请选择交易对"
            filterable
            style="width: 100%"
            :loading="symbolsLoading"
          >
            <el-option
              v-for="item in symbolOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">资金设置</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分配资金" prop="allocatedCapital">
              <el-input-number
                v-model="formData.allocatedCapital"
                :min="0"
                :precision="2"
                :step="100"
                placeholder="请输入分配资金"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="当前资金" prop="currentCapital">
              <el-input-number
                v-model="formData.currentCapital"
                :min="0"
                :precision="2"
                :step="100"
                placeholder="请输入当前资金"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="是否启用" prop="enabled">
          <el-switch
            v-model="formData.enabled"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
        <el-divider content-position="left">风控设置</el-divider>
      <RiskControlForm
        layout="inline"
        v-model:dailyProfitTarget="configForm.dailyProfitTarget"
        v-model:dailyLossLimit="configForm.dailyLossTarget"
        :readonly="!isOwnBot"
      />
        <el-divider content-position="left">备注</el-divider>
        <el-form-item label="备注">
          <el-input
            v-model="formData.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注信息"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-card>
    </el-form>

    <div style="padding: 16px 0; text-align: right; border-top: 1px solid var(--el-border-color-light); margin-top: 16px;">
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="submitting" :disabled="!isOwnBot">
        保存
      </el-button>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from "vue";
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
} from "element-plus";
import { InfoFilled } from "@element-plus/icons-vue";
import RiskControlForm from "@/components/trading/RiskControlForm.vue";
import * as tradingBotApi from "@/api/robot";
import { getStrategies, type StrategyTypeOption } from "@/api/strategy";
import { getTradingAccounts } from "@/api/trading";
import { getUsers } from "@/api/user";
import { getSupportedSymbolDetails } from "@/api/kline";
import { useAuthStore } from "@/stores/auth";

interface Props {
  modelValue: boolean;
  botId?: string;
}

interface Emits {
  (e: "update:modelValue", value: boolean): void;
  (e: "success"): void;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  botId: "",
});

const emit = defineEmits<Emits>();

const authStore = useAuthStore();
const isAdmin = computed(() => authStore.hasRole?.('admin') ?? authStore.user?.role === 'admin');

// 判断当前用户是否是机器人创建者（自有机器人可编辑，购买机器人只读）
const isOwnBot = computed(() => {
  if (!props.botId) return true; // 新建机器人，可编辑
  if (isAdmin.value) return true; // 管理员可编辑所有
  return formData.createdBy === (authStore.user?.userId || authStore.user?.id);
});

const visible = ref(false);
const loading = ref(false);
const submitting = ref(false);
const loadingStrategies = ref(false);
const loadingAccounts = ref(false);
const loadingUsers = ref(false);
const formRef = ref<FormInstance>();
const strategyOptions = ref<StrategyTypeOption[]>([]);
const userOptions = ref<Array<{ value: string; label: string }>>([]);
const accountOptions = ref<
  Array<{ value: string; label: string; platform?: string }>
>([]);
// 交易对选项（从API动态获取）
const symbolOptions = ref<Array<{ label: string; value: string }>>([]);
const symbolsLoading = ref(false);

// 从账户列表中提取唯一的交易所选项
const exchangeOptions = computed(() => {
  const platforms = new Set(accountOptions.value.map(a => a.platform).filter(Boolean));
  return Array.from(platforms).sort();
});

// 根据所选交易所过滤账户
const filteredAccountOptions = computed(() => {
  if (!formData.exchange) return accountOptions.value;
  return accountOptions.value.filter(a => a.platform === formData.exchange);
});

const formData = reactive({
  botName: "",
  userId: "",
  exchange: "",
  accountId: "",
  strategyId: "",
  direction: "BOTH",
  tradingPair: "",
  allocatedCapital: 0,
  currentCapital: 0,
  enabled: true,
  configuration: "",
  remark: "",
});

// 配置表单数据
const configForm = reactive({
  dailyProfitTarget: 0, // 日盈利目标（%）
  dailyLossTarget: 0, // 日亏损目标（%）
  leverage: 1,
});

const formRules: FormRules = {
  botName: [
    { required: true, message: "请输入机器人名称", trigger: "blur" },
    {
      min: 1,
      max: 100,
      message: "机器人名称长度在1到100个字符",
      trigger: "blur",
    },
  ],
  userId: [{ required: true, message: "请选择用户", trigger: "change" }],
  exchange: [{ required: true, message: "请选择交易所", trigger: "change" }],
  accountId: [{ required: true, message: "请选择账户", trigger: "change" }],
  strategyId: [{ required: true, message: "请选择策略", trigger: "change" }],
  tradingPair: [{ required: true, message: "请选择交易对", trigger: "change" }],
  allocatedCapital: [
    { required: true, message: "请输入分配资金", trigger: "blur" },
    {
      type: "number",
      min: 0,
      message: "分配资金必须大于等于0",
      trigger: "blur",
    },
  ],
  currentCapital: [
    { required: true, message: "请输入当前资金", trigger: "blur" },
    {
      type: "number",
      min: 0,
      message: "当前资金必须大于等于0",
      trigger: "blur",
    },
  ],
};

// 加载策略列表
const loadStrategies = async () => {
  if (strategyOptions.value.length > 0) {
    return; // 已经加载过了
  }

  loadingStrategies.value = true;
  try {
    // 直接调用策略列表API，获取完整的策略数据
    const response = await getStrategies({ page: 1, limit: 1000 });

    if (response && response.strategies && Array.isArray(response.strategies)) {
      // 转换策略数据，优先使用 strategyId，如果没有则使用 id
      strategyOptions.value = response.strategies
        .map((strategy: any) => ({
          value: strategy.strategyId || strategy.id || "",
          label: strategy.name || strategy.displayName || "未命名策略",
          description: strategy.description || strategy.briefDescription || "",
        }))
        .filter((item: StrategyTypeOption) => item.value);
    } else if (
      response &&
      response.data &&
      response.data.strategies &&
      Array.isArray(response.data.strategies)
    ) {
      // 处理另一种响应格式
      strategyOptions.value = response.data.strategies
        .map((strategy: any) => ({
          value: strategy.strategyId || strategy.id || "",
          label: strategy.name || strategy.displayName || "未命名策略",
          description: strategy.description || strategy.briefDescription || "",
        }))
        .filter((item: StrategyTypeOption) => item.value);
    }

    if (strategyOptions.value.length === 0) {
      ElMessage.warning("未找到可用策略");
    }
  } catch (error) {
    console.error("Load strategies error:", error);
    ElMessage.error("加载策略列表失败");
  } finally {
    loadingStrategies.value = false;
  }
};

// 加载账户列表
const loadAccounts = async () => {
  if (accountOptions.value.length > 0) {
    return; // 已经加载过了
  }

  loadingAccounts.value = true;
  try {
    const response = await getTradingAccounts();

    if (
      response &&
      response.success &&
      response.data &&
      Array.isArray(response.data)
    ) {
      // 转换账户数据，使用 id 作为 value，accountName 或 id 作为 label
      accountOptions.value = response.data
        .filter((account: any) => account.id) // 只保留有效的账户
        .map((account: any) => ({
          value: account.id || "",
          label:
            account.accountName || account.name || account.id || "未命名账户",
          platform: account.memberPlatform || account.platform || "",
        }));
    } else if (response && Array.isArray(response)) {
      // 处理直接返回数组的情况
      accountOptions.value = response
        .filter((account: any) => account.id)
        .map((account: any) => ({
          value: account.id || "",
          label:
            account.accountName || account.name || account.id || "未命名账户",
          platform: account.memberPlatform || account.platform || "",
        }));
    }

    if (accountOptions.value.length === 0) {
      ElMessage.warning("未找到可用账户");
    }
  } catch (error) {
    console.error("Load accounts error:", error);
    ElMessage.error("加载账户列表失败");
  } finally {
    loadingAccounts.value = false;
  }
};

// 加载用户列表（管理员使用）
const loadUsers = async () => {
  if (userOptions.value.length > 0) {
    return;
  }

  loadingUsers.value = true;
  try {
    const response = await getUsers();
    if (response.success && response.data?.users) {
      userOptions.value = response.data.users.map((user: any) => ({
        value: user.userId || user.id || "",
        label: `${user.username || ""}(${user.email || user.id || ""})`,
      }));
    }
  } catch (error) {
    console.error("Load users error:", error);
  } finally {
    loadingUsers.value = false;
  }
};

// 加载交易对列表（从API动态获取，与市场行情/回测页面一致）
const loadSymbolOptions = async () => {
  symbolsLoading.value = true;
  try {
    const res = await getSupportedSymbolDetails();
    const data = res?.data || [];
    symbolOptions.value = data.map((s: any) => ({
      label: s.name || s.symbol,
      value: s.symbol,
    }));
  } catch (error) {
    console.error("加载交易对列表失败:", error);
    symbolOptions.value = [];
  } finally {
    symbolsLoading.value = false;
  }
};

// 监听 visible 变化
watch(
  () => props.modelValue,
  (newVal) => {
    visible.value = newVal;
    if (newVal) {
      loadStrategies(); // 打开对话框时加载策略列表
      loadAccounts(); // 打开对话框时加载账户列表
      loadSymbolOptions(); // 加载交易对列表
      if (isAdmin.value) {
        loadUsers(); // 管理员加载用户列表
      } else {
        formData.userId = authStore.user?.userId || authStore.user?.id || "";
      }
      if (props.botId) {
        loadBotData();
      }
    }
  },
);

watch(visible, (newVal) => {
  if (!newVal) {
    emit("update:modelValue", false);
    resetForm();
  }
});

// 加载机器人数据
const loadBotData = async () => {
  if (!props.botId) {
    return;
  }

  loading.value = true;
  try {
    const response = await tradingBotApi.getTradingBotById(props.botId);

    if (response.success && response.data) {
      const bot = response.data;
      Object.assign(formData, {
        botName: bot.botName || "",
        userId: bot.userId || "",
        createdBy: bot.createdBy || "",
        exchange: bot.exchange || "",
        accountId: bot.accountId || "",
        strategyId: bot.strategyId || "",
        direction: bot.direction || "BOTH",
        tradingPair: bot.tradingPair || "",
        allocatedCapital: bot.allocatedCapital || 0,
        currentCapital: bot.currentCapital || 0,
        enabled: bot.enabled !== undefined ? bot.enabled : true,
        configuration: bot.configuration || "",
        remark: bot.remark || "",
      });

      // 兼容旧数据：如果机器人没有 exchange 字段，从账户中推导
      if (!formData.exchange && formData.accountId) {
        const account = accountOptions.value.find(
          (a) => a.value === formData.accountId,
        );
        if (account?.platform) {
          formData.exchange = account.platform;
        }
      }

      // 从 configuration 解析杠杆倍数
      if (bot.configuration) {
        try {
          const config = JSON.parse(bot.configuration);
          const configLeverage = Number(config.leverage);
          configForm.leverage =
            Number.isFinite(configLeverage) && configLeverage > 0
              ? configLeverage
              : 1;
        } catch (e) {
          console.warn("解析配置信息失败，使用默认值", e);
          configForm.leverage = 1;
        }
      } else {
        configForm.leverage = 1;
      }

      // 从 bot_parameter 读取参数（新格式：单条完整 JSON；兼容旧格式：多条展开记录）
      try {
        const [configRes] = await Promise.all([
          tradingBotApi.getBotParameters(props.botId!, 'config')
        ]);

        // 辅助：尝试从数组中找指定 name 的单条 JSON 记录 
        const parseRecord = (data: any[], name: string): any | null => {
          if (!Array.isArray(data)) return null;
          const rec = data.find((p: any) => p.name === name);
          if (!rec || !rec.value) return null;
          try { return JSON.parse(rec.value); } catch { return null; }
        };

        // ---- config 组 ----
        if (configRes.success && Array.isArray(configRes.data)) {
          const cfg = parseRecord(configRes.data, 'config');
          if (cfg) {
            // 新格式：单条 JSON
            configForm.dailyProfitTarget = cfg.daily_profit_target_pct ?? 0;
            configForm.dailyLossTarget = cfg.daily_loss_limit_pct ?? 0;
          } else {
            // 兼容旧格式：多条展开记录
            const dailyProfit = configRes.data.find((p: any) => p.name === 'daily_profit_target_pct');
            const dailyLoss = configRes.data.find((p: any) => p.name === 'daily_loss_limit_pct');
            configForm.dailyProfitTarget = dailyProfit ? parseFloat(dailyProfit.value) || 0 : 0;
            configForm.dailyLossTarget = dailyLoss ? parseFloat(dailyLoss.value) || 0 : 0;
          }
        }

        // ---- positionRisk / exitRules 组已移除（风控参数由策略参数 strategy_parameter 管理） ----

      } catch (e) {
        console.warn('读取机器人参数失败', e);
        configForm.dailyProfitTarget = 0;
        configForm.dailyLossTarget = 0;
      }
    } else {
      ElMessage.error(response.message || "获取机器人信息失败");
    }
  } catch (error) {
    console.error("Load bot data error:", error);
    ElMessage.error("获取机器人信息失败");
  } finally {
    loading.value = false;
  }
};

// 构建配置信息JSON（只保留杠杆倍数，风控参数走 bot_parameter）
const buildConfiguration = (): string => {
  const config: any = {};

  if (configForm.leverage !== undefined && configForm.leverage !== null) {
    config.leverage = configForm.leverage;
  }

  return JSON.stringify(config);
};

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) {
    return;
  }

  // 验证表单
  await formRef.value.validate((valid) => {
    if (!valid) {
      return false;
    }
  });

  submitting.value = true;
  try {
    // 构建配置信息
    const configuration = buildConfiguration();

    const updateData = {
      botName: formData.botName,
      userId: formData.userId,
      exchange: formData.exchange,
      accountId: formData.accountId,
      strategyId: formData.strategyId,
      direction: formData.direction,
      tradingPair: formData.tradingPair,
      allocatedCapital: formData.allocatedCapital,
      currentCapital: formData.currentCapital,
      enabled: formData.enabled,
      configuration: configuration || null,
      remark: formData.remark || null,
    };

    const response = props.botId
      ? await tradingBotApi.updateTradingBot(props.botId, updateData)
      : await tradingBotApi.createTradingBot(updateData);

    if (response.success) {
      // 保存参数到 bot_parameter（风控参数由策略参数 strategy_parameter 管理，机器人仅存 config 组）
      const targetBotId = props.botId || response.data?.botId || response.data?.id;
      if (targetBotId) {
        await tradingBotApi.saveBotParameters(targetBotId, 'config', {
          config: JSON.stringify({
            daily_profit_target_pct: configForm.dailyProfitTarget,
            daily_loss_limit_pct: configForm.dailyLossTarget,
            commission_rate: configForm.commissionRate,
          }),
        });
      }

      ElMessage.success(props.botId ? "机器人更新成功" : "机器人创建成功");
      emit("success");
      handleClose();
    } else {
      ElMessage.error(response.message || (props.botId ? "更新失败" : "创建失败"));
    }
  } catch (error) {
    console.error("Update bot error:", error);
    ElMessage.error(props.botId ? "更新机器人失败" : "创建机器人失败");
  } finally {
    submitting.value = false;
  }
};

// 关闭对话框
const handleClose = () => {
  visible.value = false;
  resetForm();
};

// 重置表单
const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields();
  }
  Object.assign(formData, {
    botName: "",
    userId: "",
    exchange: "",
    accountId: "",
    strategyId: "",
    direction: "BOTH",
    tradingPair: "",
    allocatedCapital: 0,
    currentCapital: 0,
    enabled: true,
    configuration: "",
    remark: "",
  });
  Object.assign(configForm, {
    dailyProfitTarget: 0,
    dailyLossTarget: 0,
    leverage: 1,
  });
};
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.form-item-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}


/* drawer 内容可滚动 */
:deep(.el-drawer__body) {
  overflow-y: auto;
  padding-bottom: 0;
}
</style>
