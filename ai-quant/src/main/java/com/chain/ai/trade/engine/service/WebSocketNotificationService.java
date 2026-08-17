package com.chain.ai.trade.engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket通知服务
 * 负责向客户端推送回测任务状态更新
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 发送任务状态更新通知
     * @param taskId 任务ID
     * @param status 任务状态
     * @param progress 进度（0-100）
     * @param message 消息
     */
    public void sendTaskStatusUpdate(String taskId, String status, Integer progress, String message) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("taskId", taskId);
            notification.put("status", status);
            notification.put("progress", progress != null ? progress : 0);
            notification.put("message", message);
            notification.put("timestamp", System.currentTimeMillis());

            // 发送到 /topic/backtest/{taskId} 频道
            String destination = "/topic/backtest/" + taskId;
            messagingTemplate.convertAndSend(destination, notification, new java.util.HashMap<>());

            log.info("WebSocket通知已发送: taskId={}, status={}, progress={}, destination={}",
                    taskId, status, progress, destination);

        } catch (Exception e) {
            log.error("发送WebSocket通知失败: taskId={}", taskId, e);
        }
    }

    /**
     * 发送任务完成通知
     * @param taskId 任务ID
     * @param success 是否成功
     * @param resultData 结果数据
     */
    public void sendTaskCompleted(String taskId, boolean success, Object resultData) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("taskId", taskId);
            notification.put("status", success ? "COMPLETED" : "FAILED");
            notification.put("progress", 100);
            notification.put("success", success);
            notification.put("resultData", resultData);
            notification.put("timestamp", System.currentTimeMillis());

            String destination = "/topic/backtest/" + taskId;
            messagingTemplate.convertAndSend(destination, notification, new java.util.HashMap<>());

            log.info("任务完成通知已发送: taskId={}, success={}, destination={}",
                    taskId, success, destination);

        } catch (Exception e) {
            log.error("发送任务完成通知失败: taskId={}", taskId, e);
        }
    }

    /**
     * 向指定用户发送 WebSocket 消息
     * @param userId 用户ID
     * @param destination 目标地址
     * @param payload 消息内容
     */
    public void sendToUser(String userId, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId, destination, payload);
            log.debug("WebSocket用户消息已发送: userId={}, destination={}", userId, destination);
        } catch (Exception e) {
            log.error("发送WebSocket用户消息失败: userId={}, destination={}", userId, destination, e);
        }
    }

    /**
     * 发送任务失败通知
     * @param taskId 任务ID
     * @param errorMessage 错误信息
     */
    public void sendTaskFailed(String taskId, String errorMessage) {
        sendTaskStatusUpdate(taskId, "FAILED", 0, errorMessage);
    }

    /**
     * 发送任务进度更新
     * @param taskId 任务ID
     * @param progress 进度（0-100）
     * @param message 进度消息
     */
    public void sendTaskProgress(String taskId, int progress, String message) {
        sendTaskStatusUpdate(taskId, "RUNNING", progress, message);
    }
}
