<template>
  <div class="settings">
    <div class="page-header">
      <h2>系统设置</h2>
      <el-button type="primary" @click="handleSave">保存设置</el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="24">
        <el-tabs v-model="activeTab">
          <!-- 基本设置 -->
          <el-tab-pane label="基本设置" name="basic">
            <el-card>
              <el-form :model="basicSettings" label-width="120px">
                <el-form-item label="系统名称">
                  <el-input v-model="basicSettings.systemName" />
                </el-form-item>
                <el-form-item label="系统描述">
                  <el-input
                    v-model="basicSettings.systemDescription"
                    type="textarea"
                    :rows="3"
                  />
                </el-form-item>
                <el-form-item label="时区">
                  <el-select v-model="basicSettings.timezone">
                    <el-option label="Asia/Shanghai" value="Asia/Shanghai" />
                    <el-option label="UTC" value="UTC" />
                    <el-option
                      label="America/New_York"
                      value="America/New_York"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="语言">
                  <el-select v-model="basicSettings.language">
                    <el-option label="中文" value="zh-CN" />
                    <el-option label="English" value="en-US" />
                  </el-select>
                </el-form-item>
                <el-form-item label="主题">
                  <el-select v-model="basicSettings.theme">
                    <el-option label="浅色" value="light" />
                    <el-option label="深色" value="dark" />
                    <el-option label="跟随系统" value="auto" />
                  </el-select>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- 交易设置 -->
          <el-tab-pane label="交易设置" name="trading">
            <el-card>
              <el-form :model="tradingSettings" label-width="120px">
                <el-form-item label="默认杠杆">
                  <el-input-number
                    v-model="tradingSettings.defaultLeverage"
                    :min="1"
                    :max="125"
                    :step="1"
                  />
                </el-form-item>
                <el-form-item label="手续费率 (%)">
                  <el-input-number
                    v-model="tradingSettings.feeRate"
                    :min="0"
                    :max="1"
                    :step="0.001"
                    :precision="3"
                  />
                  <span style="margin-left: 10px">%</span>
                </el-form-item>
                <el-form-item label="最小交易量">
                  <el-input-number
                    v-model="tradingSettings.minTradeAmount"
                    :min="0"
                    :step="0.001"
                    :precision="3"
                  />
                </el-form-item>
                <el-form-item label="最大持仓数">
                  <el-input-number
                    v-model="tradingSettings.maxPositions"
                    :min="1"
                    :max="100"
                    :step="1"
                  />
                </el-form-item>
                <el-form-item label="风险限制">
                  <el-row :gutter="20">
                    <el-col :span="8">
                      <el-form-item label="单笔最大风险">
                        <el-input-number
                          v-model="tradingSettings.maxRiskPerTrade"
                          :min="0"
                          :max="100"
                          :step="0.1"
                          :precision="1"
                        />
                        <span style="margin-left: 10px">%</span>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="每日最大风险">
                        <el-input-number
                          v-model="tradingSettings.maxDailyRisk"
                          :min="0"
                          :max="100"
                          :step="0.1"
                          :precision="1"
                        />
                        <span style="margin-left: 10px">%</span>
                      </el-form-item>
                    </el-col>
                    <el-col :span="8">
                      <el-form-item label="最大回撤">
                        <el-input-number
                          v-model="tradingSettings.maxDrawdown"
                          :min="0"
                          :max="100"
                          :step="0.1"
                          :precision="1"
                        />
                        <span style="margin-left: 10px">%</span>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- 通知设置 -->
          <el-tab-pane label="通知设置" name="notification">
            <el-card>
              <el-form :model="notificationSettings" label-width="120px">
                <el-form-item label="站内信通知">
                  <el-switch v-model="notificationSettings.siteMsgEnabled" />
                </el-form-item>

                <el-divider />

                <el-form-item label="邮件通知">
                  <el-switch v-model="notificationSettings.emailEnabled" />
                </el-form-item>
                <el-form-item
                  v-if="notificationSettings.emailEnabled"
                  label="SMTP服务器"
                >
                  <el-input v-model="notificationSettings.smtpHost" />
                </el-form-item>
                <el-form-item
                  v-if="notificationSettings.emailEnabled"
                  label="SMTP端口"
                >
                  <el-input-number
                    v-model="notificationSettings.smtpPort"
                    :min="1"
                    :max="65535"
                  />
                </el-form-item>
                <el-form-item
                  v-if="notificationSettings.emailEnabled"
                  label="邮箱账号"
                >
                  <el-input v-model="notificationSettings.emailUser" />
                </el-form-item>
                <el-form-item
                  v-if="notificationSettings.emailEnabled"
                  label="邮箱密码"
                >
                  <el-input
                    v-model="notificationSettings.emailPassword"
                    type="password"
                    show-password
                  />
                </el-form-item>
                <el-form-item
                  v-if="notificationSettings.emailEnabled"
                  label="收件人邮箱"
                >
                  <el-input v-model="notificationSettings.to" placeholder="多个用逗号分隔" />
                </el-form-item>
                <el-form-item
                  v-if="notificationSettings.emailEnabled"
                  label="开启代理"
                >
                  <el-switch v-model="notificationSettings.proxyEnabled" />
                </el-form-item>
                <template v-if="notificationSettings.emailEnabled && notificationSettings.proxyEnabled">
                  <el-form-item label="代理主机">
                    <el-input v-model="notificationSettings.proxyHost" placeholder="127.0.0.1" />
                  </el-form-item>
                  <el-form-item label="代理端口">
                    <el-input-number
                      v-model="notificationSettings.proxyPort"
                      :min="1"
                      :max="65535"
                    />
                  </el-form-item>
                </template>
                <el-form-item
                  v-if="notificationSettings.emailEnabled"
                  label="测试发送"
                >
                  <el-button
                    @click="handleTestEmail"
                    :loading="testingEmail"
                    type="primary"
                    size="small"
                  >
                    发送测试邮件
                  </el-button>
                </el-form-item>

                <el-divider />

                <el-form-item label="短信通知">
                  <el-switch v-model="notificationSettings.smsEnabled" />
                </el-form-item>
                <el-form-item
                  v-if="notificationSettings.smsEnabled"
                  label="短信服务商"
                >
                  <el-select v-model="notificationSettings.smsProvider">
                    <el-option label="阿里云" value="aliyun" />
                    <el-option label="腾讯云" value="tencent" />
                    <el-option label="Twilio" value="twilio" />
                  </el-select>
                </el-form-item>

                <el-divider />

                <el-form-item label="通知类型">
                  <el-checkbox-group
                    v-model="notificationSettings.notificationTypes"
                  >
                    <el-checkbox label="trade">交易通知</el-checkbox>
                    <el-checkbox label="risk">风险警告</el-checkbox>
                    <el-checkbox label="system">系统通知</el-checkbox>
                    <el-checkbox label="strategy">策略通知</el-checkbox>
                  </el-checkbox-group>
                </el-form-item>

                <el-divider />

                <el-form-item label="未读消息">
                  <el-badge :value="unreadCount" :hidden="unreadCount === 0">
                    <el-button size="small" @click="openMessageCenter">
                      消息中心
                    </el-button>
                  </el-badge>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- 站内信 -->
          <el-tab-pane label="站内信" name="messages">
            <el-card>
              <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
                <span style="color: var(--text-secondary);">
                  共 {{ messagePage.total }} 条消息，{{ unreadCount }} 条未读
                </span>
                <div>
                  <el-button size="small" @click="loadMessages" :loading="messageLoading">刷新</el-button>
                  <el-button size="small" type="primary" @click="handleMarkAllRead" v-if="unreadCount > 0">
                    全部已读
                  </el-button>
                </div>
              </div>
              <el-table :data="messagePage.records" v-loading="messageLoading" style="width: 100%">
                <el-table-column prop="title" label="标题" min-width="150">
                  <template #default="{ row }">
                    <span :style="{ fontWeight: row.isRead ? 'normal' : 'bold' }">{{ row.title }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="type" label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.type === 'risk' ? 'danger' : row.type === 'trade' ? 'warning' : 'info'" size="small">
                      {{ { trade: '交易', risk: '风险', system: '系统', strategy: '策略' }[row.type] || row.type }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="severity" label="级别" width="80">
                  <template #default="{ row }">
                    <el-tag :type="row.severity === 'critical' ? 'danger' : row.severity === 'warning' ? 'warning' : 'info'" size="small">
                      {{ { critical: '严重', warning: '警告', info: '信息' }[row.severity] || row.severity }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="时间" width="180" />
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" @click="handleRead(row)" :type="row.isRead ? 'default' : 'primary'">
                      {{ row.isRead ? '查看' : '标记已读' }}
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div style="margin-top: 16px; text-align: center;">
                <el-pagination
                  v-model:current-page="messagePage.current"
                  :page-size="messagePage.size"
                  :total="messagePage.total"
                  layout="prev, pager, next"
                  @current-change="loadMessages"
                  background
                />
              </div>
            </el-card>
          </el-tab-pane>

          <!-- API设置 -->
          <el-tab-pane label="API设置" name="api">
            <el-card>
              <el-form :model="apiSettings" label-width="120px">
                <el-form-item label="数据源">
                  <el-select v-model="apiSettings.dataSource">
                    <el-option label="Binance" value="binance" />
                    <el-option label="OKX" value="okx" />
                    <el-option label="Huobi" value="huobi" />
                    <el-option label="聚合数据" value="juhe" />
                  </el-select>
                </el-form-item>
                <el-form-item label="API密钥">
                  <el-input
                    v-model="apiSettings.apiKey"
                    placeholder="请输入API密钥"
                    show-password
                  />
                </el-form-item>
                <el-form-item label="API密钥">
                  <el-input
                    v-model="apiSettings.apiSecret"
                    placeholder="请输入API密钥"
                    show-password
                  />
                </el-form-item>
                <el-form-item label="请求限制">
                  <el-row :gutter="20">
                    <el-col :span="12">
                      <el-form-item label="每分钟请求数">
                        <el-input-number
                          v-model="apiSettings.requestsPerMinute"
                          :min="1"
                          :max="10000"
                        />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="每小时请求数">
                        <el-input-number
                          v-model="apiSettings.requestsPerHour"
                          :min="1"
                          :max="100000"
                        />
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form-item>
                <el-form-item label="测试连接">
                  <el-button @click="testApiConnection">测试连接</el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>

          <!-- 数据库设置 -->
          <el-tab-pane label="数据库设置" name="database">
            <el-card>
              <el-form :model="databaseSettings" label-width="120px">
                <el-form-item label="数据库类型">
                  <el-select v-model="databaseSettings.type">
                    <el-option label="PostgreSQL" value="postgresql" />
                    <el-option label="MySQL" value="mysql" />
                    <el-option label="SQLite" value="sqlite" />
                  </el-select>
                </el-form-item>
                <el-form-item label="主机地址">
                  <el-input v-model="databaseSettings.host" />
                </el-form-item>
                <el-form-item label="端口">
                  <el-input-number
                    v-model="databaseSettings.port"
                    :min="1"
                    :max="65535"
                  />
                </el-form-item>
                <el-form-item label="数据库名">
                  <el-input v-model="databaseSettings.database" />
                </el-form-item>
                <el-form-item label="用户名">
                  <el-input v-model="databaseSettings.username" />
                </el-form-item>
                <el-form-item label="密码">
                  <el-input
                    v-model="databaseSettings.password"
                    type="password"
                    show-password
                  />
                </el-form-item>
                <el-form-item label="连接池大小">
                  <el-input-number
                    v-model="databaseSettings.poolSize"
                    :min="1"
                    :max="100"
                  />
                </el-form-item>
                <el-form-item label="备份策略">
                  <el-select v-model="databaseSettings.backupStrategy">
                    <el-option label="每日" value="daily" />
                    <el-option label="每周" value="weekly" />
                    <el-option label="每月" value="monthly" />
                  </el-select>
                </el-form-item>
              </el-form>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from "vue";
import { ElMessage, ElNotification, ElMessageBox } from "element-plus";
import { getConfigs, updateConfig, getUnreadCount, listMessages, markAsRead, markAllAsRead, testEmail } from "@/api/notification";
import { useAppStore } from "@/stores/app";

const appStore = useAppStore();
const activeTab = ref("basic");
const unreadCount = ref(0);
const messageLoading = ref(false);
const messagePage = reactive({
  records: [],
  total: 0,
  current: 1,
  size: 10,
  pages: 0,
});

const basicSettings = reactive({
  systemName: "量化交易系统",
  systemDescription: "专业的量化交易平台",
  timezone: "Asia/Shanghai",
  language: "zh-CN",
  theme: appStore.theme || "light",
});

const tradingSettings = reactive({
  defaultLeverage: 1,
  feeRate: 0.001,
  minTradeAmount: 0.001,
  maxPositions: 10,
  maxRiskPerTrade: 2,
  maxDailyRisk: 5,
  maxDrawdown: 10,
});

const notificationSettings = reactive({
  siteMsgEnabled: true,
  emailEnabled: false,
  smtpHost: "",
  smtpPort: 587,
  emailUser: "",
  emailPassword: "",
  to: "",
  proxyEnabled: false,
  proxyHost: "",
  proxyPort: 7890,
  smsEnabled: false,
  smsProvider: "aliyun",
  notificationTypes: ["trade", "risk"],
});

const apiSettings = reactive({
  dataSource: "binance",
  apiKey: "",
  apiSecret: "",
  requestsPerMinute: 1200,
  requestsPerHour: 72000,
});

const databaseSettings = reactive({
  type: "postgresql",
  host: "localhost",
  port: 5432,
  database: "quant_trading",
  username: "quant",
  password: "",
  poolSize: 10,
  backupStrategy: "daily",
});

const originalSettings = reactive({
  basic: { ...basicSettings },
  trading: { ...tradingSettings },
  notification: { ...notificationSettings },
  api: { ...apiSettings },
  database: { ...databaseSettings },
});

const fetchSettings = async () => {
  try {
    const configs = await getConfigs();
    for (const cfg of configs) {
      if (cfg.channel === "site_msg") notificationSettings.siteMsgEnabled = cfg.enabled;
      if (cfg.channel === "email") {
        notificationSettings.emailEnabled = cfg.enabled;
        if (cfg.configJson) {
          const smtp = JSON.parse(cfg.configJson);
          if (smtp.smtpHost) notificationSettings.smtpHost = smtp.smtpHost;
          if (smtp.smtpPort) notificationSettings.smtpPort = smtp.smtpPort;
          if (smtp.emailUser) notificationSettings.emailUser = smtp.emailUser;
          if (smtp.emailPassword) notificationSettings.emailPassword = smtp.emailPassword;
          if (smtp.to) notificationSettings.to = smtp.to;
          if (smtp.proxyEnabled !== undefined) notificationSettings.proxyEnabled = smtp.proxyEnabled;
          if (smtp.proxyHost) notificationSettings.proxyHost = smtp.proxyHost;
          if (smtp.proxyPort) notificationSettings.proxyPort = smtp.proxyPort;
        }
      }
      if (cfg.channel === "sms") notificationSettings.smsEnabled = cfg.enabled;
    }
    Object.assign(originalSettings.notification, notificationSettings);
  } catch (error) {
    console.warn("获取通知配置失败，使用默认值", error);
  }
};

const loadUnreadCount = async () => {
  try {
    unreadCount.value = await getUnreadCount();
  } catch (error) {
    console.warn("获取未读消息数失败", error);
  }
};

const buildEmailConfigJson = () => {
  return JSON.stringify({
    smtpHost: notificationSettings.smtpHost,
    smtpPort: notificationSettings.smtpPort,
    emailUser: notificationSettings.emailUser,
    emailPassword: notificationSettings.emailPassword,
    to: notificationSettings.to,
    proxyEnabled: notificationSettings.proxyEnabled,
    proxyHost: notificationSettings.proxyHost,
    proxyPort: notificationSettings.proxyPort,
  });
};

const handleSave = async () => {
  try {
    await updateConfig("site_msg", notificationSettings.siteMsgEnabled);
    await updateConfig("email", notificationSettings.emailEnabled, buildEmailConfigJson());
    await updateConfig("sms", notificationSettings.smsEnabled);

    Object.assign(originalSettings.basic, basicSettings);
    Object.assign(originalSettings.trading, tradingSettings);
    Object.assign(originalSettings.notification, notificationSettings);
    Object.assign(originalSettings.api, apiSettings);
    Object.assign(originalSettings.database, databaseSettings);

    ElMessage.success("设置保存成功");
  } catch (error) {
    ElMessage.error("设置保存失败");
  }
};

const testingEmail = ref(false);

const handleTestEmail = async () => {
  testingEmail.value = true;
  try {
    await testEmail({
      to: notificationSettings.to || "13713587424@163.com",
      smtpHost: notificationSettings.smtpHost,
      smtpPort: notificationSettings.smtpPort,
      emailUser: notificationSettings.emailUser,
      emailPassword: notificationSettings.emailPassword,
      proxyEnabled: notificationSettings.proxyEnabled,
      proxyHost: notificationSettings.proxyHost,
      proxyPort: notificationSettings.proxyPort,
    });
    ElMessage.success("测试邮件发送成功");
  } catch (error: any) {
    ElMessage.error(error?.data?.message || "测试邮件发送失败");
  } finally {
    testingEmail.value = false;
  }
};

const testApiConnection = async () => {
  try {
    await new Promise((resolve) => setTimeout(resolve, 1000));
    ElMessage.success("API连接测试成功");
  } catch (error) {
    ElMessage.error("API连接测试失败");
  }
};

const openMessageCenter = () => {
  activeTab.value = "messages";
};

const loadMessages = async () => {
  messageLoading.value = true;
  try {
    const res = await listMessages({ page: messagePage.current, size: messagePage.size });
    messagePage.records = res.records;
    messagePage.total = res.total;
    messagePage.pages = res.pages;
  } catch (error) {
    ElMessage.error("加载消息失败");
  } finally {
    messageLoading.value = false;
  }
};

const handleRead = async (row: any) => {
  if (!row.isRead) {
    await markAsRead(row.id);
    row.isRead = true;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
  }
};

const handleMarkAllRead = async () => {
  try {
    await markAllAsRead();
    unreadCount.value = 0;
    messagePage.records.forEach((r: any) => r.isRead = true);
    ElMessage.success("全部已读");
  } catch (error) {
    ElMessage.error("操作失败");
  }
};

watch(activeTab, (tab) => {
  if (tab === "messages") {
    messagePage.current = 1;
    loadMessages();
  }
});

watch(() => basicSettings.theme, (newTheme) => {
  appStore.setTheme(newTheme);
});

onMounted(() => {
  fetchSettings();
  loadUnreadCount();
});
</script>

<style scoped>
.settings {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
  background: var(--bg-primary);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px 20px;
  background: var(--surface-elevated);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  transition: all var(--transition-normal) var(--ease-out);
}

.page-header:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.page-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--font-2xl);
  font-weight: var(--font-semibold);
}

/* 卡片样式 */
:deep(.el-card) {
  background: var(--surface-elevated) !important;
  border: 1px solid var(--border-primary) !important;
  border-radius: var(--radius-lg) !important;
  box-shadow: var(--shadow-md) !important;
  transition: all var(--transition-normal) var(--ease-out) !important;
  position: relative;
  overflow: hidden;
}

:deep(.el-card:hover) {
  transform: translateY(-2px) !important;
  box-shadow: var(--shadow-lg) !important;
}

:deep(.el-card__header) {
  background: var(--surface-elevated) !important;
  border-bottom: 1px solid var(--border-primary) !important;
  padding: 16px 20px !important;
}

:deep(.el-card__header span) {
  color: var(--text-primary) !important;
  font-weight: var(--font-semibold) !important;
  font-size: var(--font-lg) !important;
}

:deep(.el-card__body) {
  padding: 20px !important;
}

/* 标签页样式 */
:deep(.el-tabs__item) {
  color: var(--text-secondary) !important;
  font-weight: var(--font-medium) !important;
  font-size: var(--font-base) !important;
}

:deep(.el-tabs__item.is-active) {
  color: var(--btn-primary) !important;
}

:deep(.el-tabs__active-bar) {
  background: var(--btn-primary) !important;
  height: 3px !important;
}

:deep(.el-tabs__item:hover) {
  color: var(--text-primary) !important;
}

:deep(.el-tabs__nav-wrap::after) {
  background: var(--border-secondary) !important;
}

/* 表单样式 */
:deep(.el-form-item__label) {
  color: var(--text-secondary) !important;
  font-weight: var(--font-medium) !important;
  font-size: var(--font-sm) !important;
}

:deep(.el-input__wrapper) {
  background: var(--input-bg) !important;
  border: 1px solid var(--input-border) !important;
  border-radius: var(--radius-md) !important;
  box-shadow: none !important;
  transition: all var(--transition-normal) var(--ease-out) !important;
}

:deep(.el-input__wrapper:hover) {
  border-color: var(--input-hover) !important;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: var(--input-focus) !important;
  box-shadow: 0 0 0 2px var(--glow-primary) !important;
}

:deep(.el-input__inner) {
  color: var(--input-text) !important;
  font-size: var(--font-base) !important;
}

:deep(.el-select .el-input__wrapper) {
  background: var(--input-bg) !important;
  border-color: var(--input-border) !important;
}

:deep(.el-select-dropdown) {
  background: var(--surface-overlay) !important;
  border: 1px solid var(--border-primary) !important;
  border-radius: var(--radius-lg) !important;
  box-shadow: var(--shadow-lg) !important;
}

:deep(.el-select-dropdown__item) {
  color: var(--text-primary) !important;
}

:deep(.el-select-dropdown__item:hover) {
  background: var(--bg-hover) !important;
  color: var(--text-primary) !important;
}

:deep(.el-input-number .el-input__wrapper) {
  background: var(--input-bg) !important;
  border-color: var(--input-border) !important;
}

/* 按钮样式 */
:deep(.el-button) {
  border-radius: var(--radius-md) !important;
  font-weight: var(--font-medium) !important;
  transition: all var(--transition-normal) var(--ease-out) !important;
}

:deep(.el-button:hover) {
  transform: translateY(-1px) !important;
}

:deep(.el-button--primary) {
  background: var(--btn-primary) !important;
  border-color: var(--btn-primary) !important;
  color: white !important;
}

:deep(.el-button--primary:hover) {
  background: var(--btn-primary-hover) !important;
  border-color: var(--btn-primary-hover) !important;
  box-shadow: var(--glow-primary) !important;
}

/* 分割线样式 */
:deep(.el-divider) {
  border-color: var(--border-secondary) !important;
}

:deep(.el-divider__text) {
  color: var(--text-secondary) !important;
  background: transparent !important;
}

/* 复选框样式 */
:deep(.el-checkbox__label) {
  color: var(--text-secondary) !important;
  font-size: var(--font-base) !important;
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: var(--btn-primary) !important;
  border-color: var(--btn-primary) !important;
}

:deep(.el-checkbox__inner:hover) {
  border-color: var(--btn-primary) !important;
}

:deep(.el-checkbox__inner) {
  background: var(--input-bg) !important;
  border-color: var(--input-border) !important;
  border-radius: var(--radius-sm) !important;
}

/* 开关样式 */
:deep(.el-switch__core) {
  background: var(--input-bg) !important;
  border-color: var(--input-border) !important;
}

:deep(.el-switch.is-checked .el-switch__core) {
  background: var(--btn-primary) !important;
  border-color: var(--btn-primary) !important;
}

/* 表格样式 - 与实盘交易一致 */
:deep(.el-table) {
  background: transparent !important;
  color: var(--text-primary) !important;
}

:deep(.el-table th) {
  background: var(--surface-elevated) !important;
  color: var(--text-secondary) !important;
  border-bottom: 1px solid var(--border-primary) !important;
  font-weight: var(--font-semibold) !important;
}

:deep(.el-table td) {
  background: transparent !important;
  border-bottom: 1px solid var(--border-secondary) !important;
  color: var(--text-primary) !important;
}

:deep(.el-table--enable-row-hover .el-table__body tr:hover > td) {
  background: var(--bg-hover) !important;
}

:deep(.el-table__border) {
  border: 1px solid var(--border-primary) !important;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .settings {
    padding: 12px;
  }

  .page-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
    padding: 12px 16px;
  }

  .page-header h2 {
    font-size: var(--font-xl);
  }

  :deep(.el-card) {
    margin-bottom: 16px;
  }

  :deep(.el-form-item__label) {
    float: none;
    display: block;
    text-align: left;
    margin-bottom: 8px;
  }

  :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }
}
</style>
