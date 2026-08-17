package com.chain.ai.trade.engine.service;

import com.chain.ai.trade.engine.controller.market.KLineSocketIOHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * K线数据WebSocket推送服务
 * 支持Spring WebSocket (STOMP) 推送实时K线数据
 * 如果启用了Socket.IO，也会通过Socket.IO推送
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KLineWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private KLineSocketIOHandler socketIOHandler;

    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    public void addSubscription(String sessionId, String symbol, String interval) {
        String key = symbol + "_" + interval;
        sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    public void removeSubscription(String sessionId, String symbol, String interval) {
        String key = symbol + "_" + interval;
        Set<String> subs = sessionSubscriptions.get(sessionId);
        if (subs != null) {
            subs.remove(key);
            if (subs.isEmpty()) {
                sessionSubscriptions.remove(sessionId);
            }
        }
    }

    public void removeSession(String sessionId) {
        sessionSubscriptions.remove(sessionId);
    }

    public Set<String> getActiveSubscriptionKeys() {
        Set<String> active = ConcurrentHashMap.newKeySet();
        for (Set<String> subs : sessionSubscriptions.values()) {
            active.addAll(subs);
        }
        return active;
    }

    /**
     * 推送K线更新数据
     * 同时支持STOMP和Socket.IO两种方式
     * @param symbol 交易对
     * @param interval 时间周期
     * @param klineData K线数据
     */
    public void broadcastKLineUpdate(String symbol, String interval, Map<String, Object> klineData) {
        try {
            // 1. 通过STOMP推送（Spring WebSocket）
            String subscriptionKey = symbol + "_" + interval;
            String destination = "/topic/kline/" + subscriptionKey;

            Map<String, Object> message = new HashMap<>();
            message.put("event", "kline_update");
            message.put("symbol", symbol);
            message.put("interval", interval);
            message.put("data", klineData);
            message.put("timestamp", System.currentTimeMillis() / 1000);

            messagingTemplate.convertAndSend(destination, (Object) message);

            // 2. 通过Socket.IO推送（如果启用）
            if (socketIOHandler != null) {
                socketIOHandler.broadcastKLineUpdate(symbol, interval, klineData);
            }

            log.debug("K线数据推送: symbol={}, interval={}, destination={}",
                    symbol, interval, destination);

        } catch (Exception e) {
            log.error("推送K线数据失败: symbol={}, interval={}", symbol, interval, e);
        }
    }

    /**
     * 推送K线更新数据（使用DTO格式）
     */
    public void broadcastKLineUpdate(String symbol, String interval,
                                     Long time, BigDecimal open, BigDecimal high,
                                     BigDecimal low, BigDecimal close, BigDecimal volume) {
        Map<String, Object> klineData = new HashMap<>();
        klineData.put("time", time);
        klineData.put("open", open);
        klineData.put("high", high);
        klineData.put("low", low);
        klineData.put("close", close);
        klineData.put("volume", volume);

        broadcastKLineUpdate(symbol, interval, klineData);
    }

    /**
     * 发送订阅成功响应
     */
    public void sendSubscribed(String sessionId, String symbol, String interval) {
        try {
            String destination = "/user/" + sessionId + "/queue/kline";
            Map<String, Object> message = new HashMap<>();
            message.put("event", "subscribed");
            message.put("symbol", symbol);
            message.put("interval", interval);
            message.put("timestamp", System.currentTimeMillis() / 1000);

            messagingTemplate.convertAndSendToUser(sessionId, "/queue/kline", message);

            log.info("订阅成功响应已发送: sessionId={}, symbol={}, interval={}",
                    sessionId, symbol, interval);

        } catch (Exception e) {
            log.error("发送订阅成功响应失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 发送取消订阅成功响应
     */
    public void sendUnsubscribed(String sessionId, String symbol, String interval) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("event", "unsubscribed");
            message.put("symbol", symbol);
            message.put("interval", interval);
            message.put("timestamp", System.currentTimeMillis() / 1000);

            messagingTemplate.convertAndSendToUser(sessionId, "/queue/kline", message);

            log.info("取消订阅成功响应已发送: sessionId={}, symbol={}, interval={}",
                    sessionId, symbol, interval);

        } catch (Exception e) {
            log.error("发送取消订阅成功响应失败: sessionId={}", sessionId, e);
        }
    }
}

