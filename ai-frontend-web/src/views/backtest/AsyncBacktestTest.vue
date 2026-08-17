<template>
  <div class="async-backtest-test">
    <h2>异步回测测试</h2>

    <div class="test-form">
      <div class="form-group">
        <label>交易对:</label>
        <select v-model="form.coinId">
          <option value="ETH-USDT-SWAP">ETH-USDT-SWAP</option>
          <option value="BTC-USDT-SWAP">BTC-USDT-SWAP</option>
        </select>
      </div>

      <div class="form-group">
        <label>天数:</label>
        <input type="number" v-model.number="form.days" min="1" max="365" />
      </div>

      <div class="form-group">
        <label>杠杆:</label>
        <input
          type="number"
          v-model.number="form.leverage"
          min="1"
          max="10"
          step="0.1"
        />
      </div>

      <div class="form-group">
        <label>合约交易:</label>
        <input type="checkbox" v-model="form.isContractTrading" />
      </div>

      <button @click="startBacktest" :disabled="isRunning" class="start-btn">
        {{ isRunning ? "执行中..." : "开始异步回测" }}
      </button>
    </div>

    <div v-if="currentTask" class="task-status">
      <h3>任务状态</h3>
      <div class="status-info">
        <p><strong>任务ID:</strong> {{ currentTask.taskId }}</p>
        <p>
          <strong>状态:</strong>
          <span :class="getStatusClass(currentTask.status)">{{
            currentTask.status
          }}</span>
        </p>
        <p><strong>进度:</strong> {{ currentTask.progress }}%</p>
        <div class="progress-bar">
          <div
            class="progress-fill"
            :style="{ width: currentTask.progress + '%' }"
          ></div>
        </div>
        <p v-if="currentTask.message">
          <strong>消息:</strong> {{ currentTask.message }}
        </p>
        <p v-if="currentTask.errorMessage" class="error">
          <strong>错误:</strong> {{ currentTask.errorMessage }}
        </p>
      </div>
    </div>

    <div class="logs-section">
      <h3>
        执行日志
        <el-button size="small" type="danger" plain @click="clearLogs">
          清空日志
        </el-button>
      </h3>
      <div class="logs-container">
        <div v-for="(log, index) in logs" :key="index" class="log-item">
          <span class="timestamp">{{ formatTime(log.timestamp) }}</span>
          <span class="level" :class="log.level">{{ log.level }}</span>
          <span class="message">{{ log.message }}</span>
        </div>
      </div>
    </div>

    <div v-if="backtestResult" class="result-section">
      <h3>回测结果</h3>
      <div class="result-grid">
        <div class="result-item">
          <label>总收益率:</label>
          <span>{{ formatPercent(backtestResult.totalReturn) }}</span>
        </div>
        <div class="result-item">
          <label>最大回撤:</label>
          <span>{{ formatPercent(backtestResult.maxDrawdown) }}</span>
        </div>
        <div class="result-item">
          <label>胜率:</label>
          <span>{{ formatPercent(backtestResult.winRate) }}</span>
        </div>
        <div class="result-item">
          <label>总交易次数:</label>
          <span>{{ backtestResult.totalTrades || 0 }}</span>
        </div>
        <div class="result-item">
          <label>盈利交易数:</label>
          <span>{{ backtestResult.winningTrades || 0 }}</span>
        </div>
        <div class="result-item">
          <label>盈亏比:</label>
          <span>{{ formatNumber(backtestResult.profitFactor) }}</span>
        </div>
        <div class="result-item">
          <label>最终价值:</label>
          <span>${{ formatNumber(backtestResult.finalValue) }}</span>
        </div>
      </div>
    </div>

    <div class="report-section">
      <h3>
        回测报告
        <el-button
          v-if="currentTask && currentTask.status === 'COMPLETED'"
          :loading="isGeneratingReport"
          size="small"
          type="primary"
          @click="generateReport(currentTask.taskId)"
        >
          生成报告
        </el-button>
      </h3>

      <!-- 调试信息 -->
      <div
        style="
          background: #f0f0f0;
          padding: 10px;
          margin: 10px 0;
          font-size: 12px;
        "
      >
        <strong>调试信息:</strong><br />
        currentTask: {{ currentTask ? "存在" : "不存在" }}<br />
        currentTask.status: {{ currentTask ? currentTask.status : "N/A" }}<br />
        backtestReport: {{ backtestReport ? "存在" : "不存在" }}<br />
        isGeneratingReport: {{ isGeneratingReport }}
      </div>

      <div v-if="!backtestReport" class="no-report-message">
        <p>报告尚未生成，请点击"生成报告"按钮创建分析报告。</p>
        <!-- 临时测试按钮 -->
        <el-button size="small" @click="testApiCall">测试API调用</el-button>
      </div>

      <div v-if="backtestReport" class="report-content">
        <h4>{{ backtestReport.title }}</h4>

        <div class="summary-section">
          <h5>策略总结</h5>
          <pre>{{ backtestReport.summary }}</pre>
        </div>

        <div v-if="backtestReport.analysis" class="analysis-section">
          <h5>深度分析</h5>
          <div class="analysis-grid">
            <div class="analysis-item">
              <strong>优势:</strong>
              <ul>
                <li
                  v-for="strength in backtestReport.analysis.strengths"
                  :key="strength"
                >
                  {{ strength }}
                </li>
              </ul>
            </div>
            <div class="analysis-item">
              <strong>劣势:</strong>
              <ul>
                <li
                  v-for="weakness in backtestReport.analysis.weaknesses"
                  :key="weakness"
                >
                  {{ weakness }}
                </li>
              </ul>
            </div>
          </div>
        </div>

        <div
          v-if="backtestReport.metrics && backtestReport.metrics.key_metrics"
          class="metrics-section"
        >
          <h5>关键指标</h5>
          <div class="metrics-grid">
            <div
              v-for="metric in backtestReport.metrics.key_metrics"
              :key="metric.label"
              class="metric-item"
              :class="metric.trend"
            >
              <label>{{ metric.label }}</label>
              <span>{{ metric.value }}</span>
            </div>
          </div>
        </div>

        <div class="notes-section">
          <h5>笔记</h5>
          <textarea
            v-model="backtestReport.notes"
            @blur="updateNotes"
            placeholder="添加笔记..."
          ></textarea>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import SockJS from "sockjs-client";
import { Stomp } from "@stomp/stompjs";
import {
  runAsyncBacktest,
  getBacktestTaskStatus,
  getAsyncBacktestResult,
  generateBacktestReport,
  getBacktestReport,
  updateReportNotes,
} from "@/api/backtest";

export default {
  name: "AsyncBacktestTest",
  data() {
    return {
      form: {
        coinId: "ETH-USDT-SWAP",
        days: 30,
        leverage: 1.0,
        isContractTrading: true,
      },
      currentTask: null,
      backtestResult: null,
      backtestReport: null,
      isRunning: false,
      isGeneratingReport: false,
      logs: [],
      stompClient: null,
      statusCheckInterval: null,
    };
  },
  mounted() {
    this.connectWebSocket();
  },
  beforeUnmount() {
    this.disconnectWebSocket();
    if (this.statusCheckInterval) {
      clearInterval(this.statusCheckInterval);
    }
  },
  methods: {
    async startBacktest() {
      try {
        this.isRunning = true;
        this.currentTask = null;
        this.backtestResult = null;
        // 不清空日志，保留历史记录
        this.addLog("info", "=== 开始新的回测任务 ===");
        this.addLog("info", "开始创建回测任务...");

        const response = await runAsyncBacktest(this.form);

        if (response.success) {
          this.currentTask = {
            taskId: response.taskId,
            status: "PENDING",
            progress: 0,
            message: "任务已创建，等待执行",
          };

          this.addLog("info", `回测任务已创建，任务ID: ${response.taskId}`);

          // 开始定期检查任务状态
          this.startStatusChecking(response.taskId);
        } else {
          this.addLog("error", `创建任务失败: ${response.errorMessage}`);
        }
      } catch (error) {
        this.addLog("error", `请求失败: ${error.message}`);
      } finally {
        this.isRunning = false;
      }
    },

    startStatusChecking(taskId) {
      // 立即检查一次
      this.checkTaskStatus(taskId);

      // 每5秒检查一次状态
      this.statusCheckInterval = setInterval(() => {
        this.checkTaskStatus(taskId);
      }, 5000);
    },

    async checkTaskStatus(taskId) {
      try {
        const response = await getBacktestTaskStatus(taskId);

        if (response.success) {
          this.currentTask = {
            taskId: response.taskId,
            status: response.status,
            progress: response.progress || 0,
            message: response.message,
            errorMessage: response.errorMessage,
          };

          // 如果任务完成或失败，停止检查
          if (response.status === "COMPLETED" || response.status === "FAILED") {
            clearInterval(this.statusCheckInterval);
            this.statusCheckInterval = null;

            if (response.status === "COMPLETED") {
              this.addLog("success", "回测任务执行完成");
              // 获取结果
              await this.loadBacktestResult(taskId);
              // 获取或生成报告
              await this.loadOrGenerateReport(taskId);
            } else {
              this.addLog(
                "error",
                `回测任务执行失败: ${response.errorMessage}`,
              );
            }
          }
        }
      } catch (error) {
        this.addLog("error", `检查任务状态失败: ${error.message}`);
      }
    },

    async loadBacktestResult(taskId) {
      try {
        const response = await getAsyncBacktestResult(taskId);

        if (response.success) {
          this.backtestResult = response;
          this.addLog("success", "回测结果已加载");
        } else {
          this.addLog("error", `加载结果失败: ${response.errorMessage}`);
        }
      } catch (error) {
        this.addLog("error", `加载结果失败: ${error.message}`);
      }
    },

    async loadOrGenerateReport(taskId) {
      try {
        this.addLog("info", "正在获取回测报告...");
        console.log("=== 开始获取报告 ===");
        console.log("taskId:", taskId);

        // 先尝试获取已有的报告
        console.log("调用 getBacktestReport API...");
        const response = await getBacktestReport(taskId);
        console.log("getBacktestReport 响应:", response);
        console.log("response.success:", response.success);
        console.log("response.report:", response.report);

        if (response.success && response.report) {
          this.backtestReport = response.report;
          console.log("报告数据已设置到组件:", this.backtestReport);
          console.log("报告标题:", this.backtestReport.title);
          console.log("报告总结:", this.backtestReport.summary);
          this.addLog("success", "回测报告已加载");
          this.$forceUpdate(); // 强制重新渲染
        } else {
          // 如果没有报告，尝试生成新的报告
          console.log("报告不存在，开始生成新报告...");
          this.addLog("info", "报告不存在，正在生成新报告...");
          await this.generateReport(taskId);
        }
      } catch (error) {
        console.error("=== 获取报告异常 ===", error);
        this.addLog("error", `获取报告失败: ${error.message}`);
        // 尝试生成报告
        await this.generateReport(taskId);
      }
    },

    async generateReport(taskId) {
      try {
        this.isGeneratingReport = true;
        this.addLog("info", "正在生成回测报告...");
        console.log("=== 开始生成报告 ===");
        console.log("taskId:", taskId);

        console.log("调用 generateBacktestReport API...");
        const response = await generateBacktestReport(taskId);
        console.log("generateBacktestReport 响应:", response);
        console.log("response.success:", response.success);

        if (response.success && response.report) {
          this.backtestReport = response.report;
          console.log("生成报告成功，设置数据:", this.backtestReport);
          console.log("报告标题:", this.backtestReport.title);
          console.log("报告总结:", this.backtestReport.summary);
          this.addLog("success", "回测报告生成成功");
          this.$forceUpdate(); // 强制重新渲染
        } else {
          console.error("生成报告失败:", response.message || "未知错误");
          this.addLog(
            "error",
            `生成报告失败: ${response.message || "未知错误"}`,
          );
        }
      } catch (error) {
        console.error("=== 生成报告异常 ===", error);
        this.addLog("error", `生成报告失败: ${error.message}`);
      } finally {
        this.isGeneratingReport = false;
      }
    },

    async testApiCall() {
      try {
        console.log("=== 测试API调用 ===");
        // 测试报告生成API
        if (this.currentTask) {
          console.log("测试报告生成API...");
          const reportResponse = await generateBacktestReport(
            this.currentTask.taskId,
          );
          console.log("报告生成API响应:", reportResponse);
        } else {
          console.log("没有当前任务，无法测试");
        }
      } catch (error) {
        console.error("测试API调用失败:", error);
      }
    },

    clearLogs() {
      this.$confirm("确定要清空所有日志吗？", "确认清空", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          this.logs = [];
          this.addLog("info", "日志已清空");
        })
        .catch(() => {
          // 用户取消
        });
    },

    async updateNotes() {
      if (!this.backtestReport || !this.currentTask) return;

      try {
        const response = await updateReportNotes(
          this.currentTask.taskId,
          this.backtestReport.notes,
        );
        if (response.success) {
          this.addLog("success", "笔记更新成功");
        } else {
          this.addLog("error", `笔记更新失败: ${response.message}`);
        }
      } catch (error) {
        this.addLog("error", `笔记更新失败: ${error.message}`);
      }
    },

    connectWebSocket() {
      // 连接WebSocket以接收实时通知
      const socket = new SockJS("/ws");
      this.stompClient = Stomp.over(socket);

      this.stompClient.connect(
        {},
        (frame) => {
          this.addLog("info", "WebSocket连接成功");

          // 订阅任务状态更新
          this.stompClient.subscribe("/topic/backtest/*", (message) => {
            const notification = JSON.parse(message.body);
            this.handleWebSocketMessage(notification);
          });
        },
        (error) => {
          this.addLog("error", `WebSocket连接失败: ${error}`);
        },
      );
    },

    disconnectWebSocket() {
      if (this.stompClient) {
        this.stompClient.disconnect();
      }
    },

    handleWebSocketMessage(notification) {
      this.addLog(
        "info",
        `WebSocket通知: ${notification.status} - ${notification.message}`,
      );

      if (this.currentTask && this.currentTask.taskId === notification.taskId) {
        this.currentTask.status = notification.status;
        this.currentTask.progress = notification.progress || 0;
        this.currentTask.message = notification.message;

        if (notification.status === "COMPLETED" && notification.resultData) {
          this.backtestResult = notification.resultData;
        }
      }
    },

    addLog(level, message) {
      this.logs.unshift({
        timestamp: Date.now(),
        level: level,
        message: message,
      });

      // 限制日志数量
      if (this.logs.length > 100) {
        this.logs = this.logs.slice(0, 100);
      }
    },

    getStatusClass(status) {
      switch (status) {
        case "PENDING":
          return "status-pending";
        case "RUNNING":
          return "status-running";
        case "COMPLETED":
          return "status-completed";
        case "FAILED":
          return "status-failed";
        default:
          return "status-unknown";
      }
    },

    formatPercent(value) {
      if (value == null || value === undefined) return "0.00%";
      return (Number(value) * 100).toFixed(2) + "%";
    },

    formatNumber(value) {
      if (value == null || value === undefined) return "0.00";
      return Number(value).toFixed(2);
    },

    formatTime(timestamp) {
      return new Date(timestamp).toLocaleTimeString();
    },
  },
};
</script>

<style scoped>
.async-backtest-test {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  max-height: 90vh;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #ccc #f0f0f0;
}

.test-form {
  background: #f5f5f5;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: inline-block;
  width: 100px;
  margin-right: 10px;
}

.form-group input,
.form-group select {
  padding: 5px 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.start-btn {
  background: #007bff;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
}

.start-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.task-status,
.result-section {
  background: white;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.status-info p {
  margin: 5px 0;
}

.progress-bar {
  width: 100%;
  height: 10px;
  background: #eee;
  border-radius: 5px;
  overflow: hidden;
  margin: 10px 0;
}

.progress-fill {
  height: 100%;
  background: #007bff;
  transition: width 0.3s ease;
}

.status-pending {
  color: #ffc107;
}
.status-running {
  color: #007bff;
}
.status-completed {
  color: #28a745;
}
.status-failed {
  color: #dc3545;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
}

.result-item {
  display: flex;
  justify-content: space-between;
  padding: 10px;
  background: #f8f9fa;
  border-radius: 4px;
}

.result-item label {
  font-weight: bold;
}

.logs-section {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.logs-container {
  max-height: 500px;
  overflow-y: auto;
  background: #f8f9fa;
  padding: 10px;
  border-radius: 4px;
}

.log-item {
  display: flex;
  margin-bottom: 5px;
  font-family: monospace;
  font-size: 12px;
}

.timestamp {
  color: #666;
  margin-right: 10px;
  min-width: 80px;
}

.level {
  margin-right: 10px;
  min-width: 60px;
  text-align: center;
  border-radius: 2px;
  padding: 0 4px;
}

.level.info {
  background: #d1ecf1;
  color: #0c5460;
}
.level.success {
  background: #d4edda;
  color: #155724;
}
.level.warning {
  background: #fff3cd;
  color: #856404;
}
.level.error {
  background: #f8d7da;
  color: #721c24;
}

.message {
  flex: 1;
}

.error {
  color: #dc3545;
}

/* 报告样式 */
.report-section {
  background: white;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.report-content h4 {
  color: #333;
  margin-bottom: 20px;
  border-bottom: 2px solid #007bff;
  padding-bottom: 10px;
}

.summary-section,
.analysis-section,
.metrics-section,
.notes-section {
  margin-bottom: 20px;
}

.summary-section h5,
.analysis-section h5,
.metrics-section h5,
.notes-section h5 {
  color: #555;
  margin-bottom: 10px;
  font-size: 16px;
}

.summary-section pre {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 4px;
  border-left: 4px solid #007bff;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.analysis-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.analysis-item {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 4px;
}

.analysis-item ul {
  margin: 0;
  padding-left: 20px;
}

.analysis-item li {
  margin-bottom: 5px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
}

.metric-item {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 4px;
  border-left: 4px solid #28a745;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.metric-item.up {
  border-left-color: #28a745;
}
.metric-item.down {
  border-left-color: #dc3545;
}
.metric-item.neutral {
  border-left-color: #ffc107;
}

.metric-item label {
  font-weight: bold;
  color: #333;
}

.metric-item span {
  font-size: 18px;
  font-weight: bold;
}

.notes-section textarea {
  width: 100%;
  min-height: 100px;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-family: inherit;
  font-size: 14px;
  resize: vertical;
}

.notes-section textarea:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.no-report-message {
  text-align: center;
  padding: 40px 20px;
  color: #666;
  background: #f8f9fa;
  border-radius: 4px;
  border: 2px dashed #dee2e6;
}

.no-report-message p {
  margin: 0;
  font-size: 16px;
}

/* WebKit滚动条样式 */
.async-backtest-test::-webkit-scrollbar {
  width: 8px;
}

.async-backtest-test::-webkit-scrollbar-track {
  background: #f0f0f0;
  border-radius: 4px;
}

.async-backtest-test::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 4px;
}

.async-backtest-test::-webkit-scrollbar-thumb:hover {
  background: #aaa;
}
</style>
