/**
 * 回测功能 Composable
 * 提供回测的执行、进度查询和结果管理功能
 */
import { ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  runBacktest,
  getBacktestResult,
  getBacktestRecords,
  deleteBacktestRecord as deleteBacktestRecordAPI,
  stopBacktest,
  getBacktestProgress,
} from "@/api/member";
import { getAllStrategyTypes } from "@/api/strategy";

export function useBacktest(state: any) {
  const isLoading = ref(false);
  const error = ref<string | null>(null);
  let progressPollTimer: NodeJS.Timeout | null = null;

  /**
   * 加载策略列表
   */
  const loadStrategies = async () => {
    try {
      const response = await getAllStrategyTypes();
      if (response && response.data) {
        state.strategies = response.data.map((s: any) => ({
          value: s.id || s.strategyId,
          label: s.name || s.strategyName,
        }));
      } else {
        state.strategies = [];
      }
    } catch (err) {
      console.error("加载策略列表失败:", err);
      state.strategies = [];
    }
  };

  /**
   * 运行回测
   */
  const runBacktestHandler = async () => {
    if (!state.selectedBacktestStrategy) {
      ElMessage.error("请选择回测策略");
      return;
    }

    if (!state.backtestParams.startDate || !state.backtestParams.endDate) {
      ElMessage.error("请选择回测日期范围");
      return;
    }

    if (
      new Date(state.backtestParams.startDate) >=
      new Date(state.backtestParams.endDate)
    ) {
      ElMessage.error("结束日期必须大于开始日期");
      return;
    }

    state.backtestRunning = true;
    state.backtestProgress = 0;
    state.backtestResults = null;
    state.currentBacktestId = null;
    isLoading.value = true;
    error.value = null;

    try {
      console.log("开始回测，配置ID:", state.selectedBacktestStrategy);

      // 构建回测参数
      const backtestParams = {
        strategyId: state.selectedBacktestStrategy,
        symbol: state.selectedSymbol,
        startDate: state.backtestParams.startDate,
        endDate: state.backtestParams.endDate,
        initialCapital: state.backtestParams.initialCapital,
        feeRate: state.backtestParams.feeRate,
        interval: state.selectedInterval,
      };

      // 调用回测API
      const response = await runBacktest(backtestParams);

      if (response && response.success && response.result) {
        state.currentBacktestId = response.result.backtestId;
        console.log("回测已启动，ID:", state.currentBacktestId);

        // 开始轮询回测进度
        pollBacktestProgress();
      } else {
        throw new Error(response?.message || "回测启动失败");
      }
    } catch (err: any) {
      error.value = err.message || "回测执行失败";
      console.error("回测执行失败:", err);
      ElMessage.error("回测执行失败: " + (err.message || "请重试"));
      state.backtestRunning = false;
      state.backtestProgress = 0;
      isLoading.value = false;
    }
  };

  /**
   * 轮询回测进度
   */
  const pollBacktestProgress = async () => {
    if (!state.currentBacktestId || !state.backtestRunning) {
      console.log(
        "停止轮询: currentBacktestId=",
        state.currentBacktestId,
        "backtestRunning=",
        state.backtestRunning,
      );
      return;
    }

    try {
      console.log("轮询回测进度: backtestId=", state.currentBacktestId);
      const response = await getBacktestProgress(state.currentBacktestId);

      console.log("回测进度响应:", response);

      if (response && response.success && response.result) {
        const progress = response.result;

        console.log("回测进度更新:", progress);
        console.log(
          "回测状态:",
          progress.status,
          "进度:",
          progress.progress,
          "有结果:",
          progress.hasResult,
        );

        state.backtestProgress = progress.progress || 0;
        state.backtestMessage = progress.message || "";

        // 如果回测完成
        if (progress.status === "completed") {
          console.log("回测完成，开始获取结果");
          state.backtestRunning = false;
          state.backtestProgress = 100;

          // 获取回测结果
          await getBacktestResults();

          // 停止轮询
          if (progressPollTimer) {
            clearInterval(progressPollTimer);
            progressPollTimer = null;
          }
        } else if (progress.status === "failed") {
          console.error("回测失败:", progress.message);
          state.backtestRunning = false;
          state.backtestProgress = 0;
          state.backtestMessage = progress.message || "回测失败";
          ElMessage.error("回测失败: " + (progress.message || "未知错误"));

          // 停止轮询
          if (progressPollTimer) {
            clearInterval(progressPollTimer);
            progressPollTimer = null;
          }
        } else {
          // 继续轮询（每2秒）
          if (progressPollTimer) {
            clearInterval(progressPollTimer);
          }
          progressPollTimer = setTimeout(() => {
            pollBacktestProgress();
          }, 2000);
        }
      } else {
        console.warn("获取回测进度失败:", response);
        // 继续轮询
        if (progressPollTimer) {
          clearInterval(progressPollTimer);
        }
        progressPollTimer = setTimeout(() => {
          pollBacktestProgress();
        }, 2000);
      }
    } catch (err: any) {
      console.error("轮询回测进度失败:", err);
      // 继续轮询
      if (progressPollTimer) {
        clearInterval(progressPollTimer);
      }
      progressPollTimer = setTimeout(() => {
        pollBacktestProgress();
      }, 2000);
    }
  };

  /**
   * 获取回测结果
   */
  const getBacktestResults = async () => {
    if (!state.currentBacktestId) return;

    try {
      const response = await getBacktestResult(state.currentBacktestId);
      if (response && response.success && response.result) {
        state.backtestResults = response.result;
        console.log("回测结果:", state.backtestResults);
      }
    } catch (err) {
      console.error("获取回测结果失败:", err);
    }
  };

  /**
   * 停止回测
   */
  const stopBacktestHandler = async () => {
    if (!state.currentBacktestId) {
      ElMessage.warning("没有正在运行的回测");
      return;
    }

    try {
      const response = await stopBacktest(state.currentBacktestId);
      if (response && response.success) {
        state.backtestRunning = false;
        state.backtestProgress = 0;
        state.currentBacktestId = null;
        ElMessage.success("回测已停止");

        // 停止轮询
        if (progressPollTimer) {
          clearInterval(progressPollTimer);
          progressPollTimer = null;
        }
      } else {
        ElMessage.error("停止回测失败");
      }
    } catch (err: any) {
      console.error("停止回测失败:", err);
      ElMessage.error("停止回测失败: " + (err.message || "请重试"));
    }
  };

  /**
   * 加载回测记录
   */
  const loadBacktestRecords = async () => {
    if (!state.selectedRecordsStrategy) {
      state.backtestRecords = [];
      return;
    }

    state.backtestRecordsLoading = true;
    isLoading.value = true;
    error.value = null;

    try {
      const response = await getBacktestRecords({
        strategyId: state.selectedRecordsStrategy,
      });

      if (response && response.success && response.data) {
        // 后端返回分页格式 { records: [...], total, ... }，前端需要 records 数组
        state.backtestRecords = response.data.records || response.data;
      } else {
        state.backtestRecords = [];
      }
    } catch (err: any) {
      error.value = err.message || "加载回测记录失败";
      console.error("加载回测记录失败:", err);
      state.backtestRecords = [];
    } finally {
      state.backtestRecordsLoading = false;
      isLoading.value = false;
    }
  };

  /**
   * 查看回测记录
   */
  const viewBacktestRecord = (record: any) => {
    console.log("查看回测记录:", record);
    // 可以在这里实现查看回测记录的详细逻辑
    ElMessage.info(`查看回测记录: ${record.id}`);
  };

  /**
   * 删除回测记录
   */
  const deleteBacktestRecord = async (recordId: string) => {
    try {
      await ElMessageBox.confirm("确定要删除这条回测记录吗？", "确认删除", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      });

      const response = await deleteBacktestRecordAPI(recordId);
      if (response && response.success) {
        ElMessage.success("删除成功");
        await loadBacktestRecords();
      } else {
        ElMessage.error("删除失败");
      }
    } catch (err: any) {
      if (err !== "cancel") {
        console.error("删除回测记录失败:", err);
        ElMessage.error("删除失败: " + (err.message || "请重试"));
      }
    }
  };

  /**
   * 清理资源
   */
  const cleanup = () => {
    if (progressPollTimer) {
      clearInterval(progressPollTimer);
      progressPollTimer = null;
    }
  };

  return {
    isLoading,
    error,
    loadStrategies,
    runBacktest: runBacktestHandler,
    stopBacktest: stopBacktestHandler,
    loadBacktestRecords,
    viewBacktestRecord,
    deleteBacktestRecord,
    cleanup,
  };
}
