<template>
  <div class="backtest-task-list">
    <div class="page-header">
      <h2>回测任务列表</h2>
    </div>

    <!-- 筛选和搜索 -->
    <el-card class="filter-card mb-20">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-select
            v-model="filterStatus"
            placeholder="筛选状态"
            clearable
            @change="handleFilter"
          >
            <el-option label="全部" value="" />
            <el-option label="进行中" value="running" />
            <el-option label="已完成" value="completed" />
            <el-option label="失败" value="failed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-input
            v-model="searchQuery"
            placeholder="搜索策略名称"
            clearable
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
        <el-col :span="6">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            @change="handleFilter"
          />
        </el-col>
        <el-col :span="6">
          <el-button type="primary" @click="refreshList">刷新</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 任务列表 -->
    <el-card>
      <el-table
        :data="filteredTasks"
        v-loading="loading"
        style="width: 100%"
        @row-click="handleRowClick"
      >
        <el-table-column prop="name" label="任务名称" min-width="200">
          <template #default="scope">
            <div class="task-name">
              <span class="name">{{ scope.row.name }}</span>
              <el-tag size="small" type="info">{{
                scope.row.strategyName
              }}</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="progress" label="进度" width="120">
          <template #default="scope">
            <div v-if="scope.row.status === 'running'" class="progress-cell">
              <el-progress
                :percentage="scope.row.progress || 0"
                :status="scope.row.progress === 100 ? 'success' : undefined"
                :stroke-width="8"
              />
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column prop="totalReturn" label="总收益率" width="120">
          <template #default="scope">
            <span
              v-if="scope.row.status === 'completed' && scope.row.metrics"
              :class="
                scope.row.metrics.totalReturn >= 0 ? 'positive' : 'negative'
              "
            >
              {{ formatPercent(scope.row.metrics.totalReturn) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column prop="sharpeRatio" label="夏普比率" width="100">
          <template #default="scope">
            <span v-if="scope.row.status === 'completed' && scope.row.metrics">
              {{ scope.row.metrics.sharpeRatio?.toFixed(2) || "-" }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column prop="maxDrawdown" label="最大回撤" width="100">
          <template #default="scope">
            <span v-if="scope.row.status === 'completed' && scope.row.metrics">
              {{ formatPercent(scope.row.metrics.maxDrawdown) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="scope">
            {{ formatTime(scope.row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column prop="duration" label="耗时" width="100">
          <template #default="scope">
            <span v-if="scope.row.duration">
              {{ formatDuration(scope.row.duration) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 'running'"
              type="danger"
              size="small"
              @click.stop="stopBacktest(scope.row)"
            >
              停止
            </el-button>
            <el-button
              v-if="scope.row.status === 'completed'"
              type="primary"
              size="small"
              @click.stop="viewResults(scope.row)"
            >
              查看结果
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click.stop="deleteBacktest(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalTasks"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, inject } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search } from "@element-plus/icons-vue";
import { useRouter } from "vue-router";
import * as API from "@/api/backtest";

const router = useRouter();

// 注入父组件提供的方法
const switchToBacktestResult = inject("switchToBacktestResult") as (
  taskId: string,
  task: any,
) => void;

// 响应式数据
const loading = ref(false);
const tasks = ref([]);
const totalTasks = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);

// 筛选条件
const filterStatus = ref("");
const searchQuery = ref("");
const dateRange = ref([]);

// 计算属性
const filteredTasks = computed(() => {
  let result = [...tasks.value];

  // 状态筛选
  if (filterStatus.value) {
    result = result.filter((task) => task.status === filterStatus.value);
  }

  // 搜索筛选
  if (searchQuery.value) {
    result = result.filter(
      (task) =>
        task.name.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
        (task.strategyName &&
          task.strategyName
            .toLowerCase()
            .includes(searchQuery.value.toLowerCase())),
    );
  }

  // 日期筛选
  if (dateRange.value && dateRange.value.length === 2) {
    const [startDate, endDate] = dateRange.value;
    result = result.filter((task) => {
      const taskDate = new Date(task.createdAt).toISOString().split("T")[0];
      return taskDate >= startDate && taskDate <= endDate;
    });
  }

  return result;
});

// 加载任务列表
const loadTasks = async () => {
  loading.value = true;
  try {
    const params = {
      page: currentPage.value,
      limit: pageSize.value,
      status: filterStatus.value || undefined,
    };

    const response = await API.getBacktestHistory(params);

    if (response.success) {
      // 后端返回的分页数据结构：response.data 包含分页信息
      const pageData = response.data;
      // 转换状态为前端期望的格式，并确保数据结构一致
      tasks.value = (pageData?.records || []).map((task) => ({
        ...task,
        status: (task.status || "").toLowerCase(), // 将COMPLETED转换为completed
      }));
      totalTasks.value = pageData?.total || 0;
    } else {
      console.error("加载回测任务失败:", response.message);
      ElMessage.error("加载回测任务失败");
      // 模拟数据
      tasks.value = [
        {
          id: "mock-task-1",
          name: "双均线策略回测",
          strategyName: "双均线策略",
          status: "completed",
          progress: 100,
          metrics: {
            totalReturn: 15.8,
            sharpeRatio: 1.45,
            maxDrawdown: 8.5,
          },
          createdAt: "2024-01-15T10:30:00Z",
          duration: 300,
        },
        {
          id: "mock-task-2",
          name: "RSI策略回测",
          strategyName: "RSI超买超卖策略",
          status: "running",
          progress: 65,
          createdAt: "2024-01-16T14:20:00Z",
        },
        {
          id: "mock-task-3",
          name: "MACD策略回测",
          strategyName: "MACD交叉策略",
          status: "failed",
          createdAt: "2024-01-14T09:15:00Z",
          error: "数据获取失败",
        },
      ];
      totalTasks.value = 3;
    }
  } catch (error) {
    console.error("加载回测任务失败:", error);
    ElMessage.error("加载回测任务失败");
    // 模拟数据
    tasks.value = [
      {
        id: "mock-task-1",
        name: "双均线策略回测",
        strategyName: "双均线策略",
        status: "completed",
        progress: 100,
        metrics: {
          totalReturn: 15.8,
          sharpeRatio: 1.45,
          maxDrawdown: 8.5,
        },
        createdAt: "2024-01-15T10:30:00Z",
        duration: 300,
      },
      {
        id: "mock-task-2",
        name: "RSI策略回测",
        strategyName: "RSI超买超卖策略",
        status: "running",
        progress: 65,
        createdAt: "2024-01-16T14:20:00Z",
      },
      {
        id: "mock-task-3",
        name: "MACD策略回测",
        strategyName: "MACD交叉策略",
        status: "failed",
        createdAt: "2024-01-14T09:15:00Z",
        error: "数据获取失败",
      },
    ];
    totalTasks.value = 3;
  } finally {
    loading.value = false;
  }
};

// 查看结果
const viewResults = (task) => {
  console.log("🔥 BacktestTaskList: 点击查看结果，任务数据:", task);
  // 通过 inject 的方法切换到结果详情页面
  if (switchToBacktestResult) {
    console.log("🔥 BacktestTaskList: 调用 inject 的方法");
    switchToBacktestResult(task.id, task);
  } else {
    console.error("🔥 BacktestTaskList: 无法找到 switchToBacktestResult 方法");
  }
};

// 停止回测
const stopBacktest = async (task) => {
  try {
    await ElMessageBox.confirm("确定要停止这个回测任务吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });

    const response = await API.cancelBacktest(task.id);
    if (response.success) {
      ElMessage.success("回测任务已停止");
      loadTasks();
    } else {
      ElMessage.error("停止回测失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("停止回测失败:", error);
      ElMessage.error("停止回测失败");
    }
  }
};

// 删除回测
const deleteBacktest = async (task) => {
  try {
    await ElMessageBox.confirm(
      "确定要删除这个回测任务吗？此操作不可恢复。",
      "提示",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    const response = await API.deleteBacktest(task.id);
    if (response.success) {
      ElMessage.success("回测任务已删除");
      loadTasks();
    } else {
      ElMessage.error("删除回测失败");
    }
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除回测失败:", error);
      ElMessage.error("删除回测失败");
    }
  }
};

// 事件处理
const handleRowClick = (row) => {
  if (row.status === "completed") {
    viewResults(row);
  }
};

const handleFilter = () => {
  currentPage.value = 1;
  loadTasks();
};

const handleSearch = () => {
  currentPage.value = 1;
  loadTasks();
};

const refreshList = () => {
  loadTasks();
};

const handleSizeChange = (size) => {
  pageSize.value = size;
  currentPage.value = 1;
  loadTasks();
};

const handleCurrentChange = (page) => {
  currentPage.value = page;
  loadTasks();
};

// 工具函数
const getStatusType = (status) => {
  const statusMap = {
    pending: "warning",
    running: "primary",
    completed: "success",
    failed: "danger",
    cancelled: "info",
  };
  return statusMap[status] || "info";
};

const getStatusText = (status) => {
  const statusMap = {
    pending: "等待中",
    running: "进行中",
    completed: "已完成",
    failed: "失败",
    cancelled: "已取消",
  };
  return statusMap[status] || status;
};

const formatPercent = (value) => {
  if (value === null || value === undefined) return "-";
  return `${value >= 0 ? "+" : ""}${value.toFixed(2)}%`;
};

const pad2 = (value) => String(value).padStart(2, "0");

const toDateTimeString = (date) => {
  const year = date.getFullYear();
  const month = pad2(date.getMonth() + 1);
  const day = pad2(date.getDate());
  const hour = pad2(date.getHours());
  const minute = pad2(date.getMinutes());
  const second = pad2(date.getSeconds());
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
};

const parseDateTime = (value) => {
  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value;
  }
  if (typeof value === "number") {
    const timestamp = value > 10000000000 ? value : value * 1000;
    const date = new Date(timestamp);
    return isNaN(date.getTime()) ? null : date;
  }
  if (!value) return null;
  const normalized = String(value).replace("T", " ").trim();
  const match = normalized.match(
    /^(\d{4})[/-](\d{1,2})[/-](\d{1,2})(?:\s+(\d{1,2})(?::(\d{1,2})(?::(\d{1,2}))?)?)?$/,
  );
  if (match) {
    const year = Number(match[1]);
    const month = Number(match[2]) - 1;
    const day = Number(match[3]);
    const hour = Number(match[4] ?? 0);
    const minute = Number(match[5] ?? 0);
    const second = Number(match[6] ?? 0);
    const date = new Date(year, month, day, hour, minute, second);
    return isNaN(date.getTime()) ? null : date;
  }
  const date = new Date(value);
  return isNaN(date.getTime()) ? null : date;
};

const formatTime = (time) => {
  if (!time) return "-";
  const parsed = parseDateTime(time);
  if (!parsed) return String(time);
  return toDateTimeString(parsed);
};

const formatDuration = (seconds) => {
  if (!seconds) return "-";
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;

  if (hours > 0) {
    return `${hours}时${minutes}分${secs}秒`;
  } else if (minutes > 0) {
    return `${minutes}分${secs}秒`;
  } else {
    return `${secs}秒`;
  }
};

// 生命周期
onMounted(() => {
  loadTasks();
});
</script>

<style scoped>
.backtest-task-list {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  color: var(--text-primary);
}

.filter-card {
  margin-bottom: 20px;
}

.task-name {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-name .name {
  font-weight: 500;
}

.progress-cell {
  width: 80px;
}

.positive {
  color: var(--success-color);
}

.negative {
  color: var(--danger-color);
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.mb-20 {
  margin-bottom: 20px;
}
</style>
