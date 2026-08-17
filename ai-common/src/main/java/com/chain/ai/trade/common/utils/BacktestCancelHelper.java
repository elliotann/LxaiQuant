package com.chain.ai.trade.common.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 回测取消辅助工具
 * 使用 AtomicBoolean + ThreadLocal 方案实现回测中断，
 * 避免 ForkJoinPool.commonPool() 中 Thread.interrupt() 失效的问题
 */
public class BacktestCancelHelper {

    private static final ConcurrentHashMap<String, AtomicBoolean> CANCEL_FLAGS = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> CURRENT_TASK_ID = new ThreadLocal<>();

    /**
     * 注册回测任务并绑定到当前线程
     */
    public static void registerTask(String taskId) {
        CANCEL_FLAGS.put(taskId, new AtomicBoolean(false));
        CURRENT_TASK_ID.set(taskId);
    }

    /**
     * 注销回测任务并清理线程绑定
     */
    public static void unregisterTask(String taskId) {
        CANCEL_FLAGS.remove(taskId);
        CURRENT_TASK_ID.remove();
    }

    /**
     * 取消指定回测任务
     */
    public static void cancelTask(String taskId) {
        AtomicBoolean flag = CANCEL_FLAGS.get(taskId);
        if (flag != null) {
            flag.set(true);
        }
    }

    /**
     * 检查当前线程关联的回测任务是否被取消
     */
    public static boolean isCurrentTaskCancelled() {
        String taskId = CURRENT_TASK_ID.get();
        if (taskId == null) {
            return false;
        }
        AtomicBoolean flag = CANCEL_FLAGS.get(taskId);
        return flag != null && flag.get();
    }

    /**
     * 获取当前任务ID（用于日志）
     */
    public static String getCurrentTaskId() {
        return CURRENT_TASK_ID.get();
    }
}
