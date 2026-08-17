/**
 * K线WebSocket服务
 * 使用 STOMP (Spring WebSocket)，与后端保持一致
 * 参考回测中的实现方式
 */
import { ref, onUnmounted } from "vue";
import { Stomp } from "@stomp/stompjs";
import { useAuthStore } from "@/stores/auth";
// @ts-ignore - sockjs-client 没有类型定义
import SockJS from "sockjs-client";

export interface WebSocketMessage {
  event: string;
  [key: string]: any;
}

export interface KLineUpdateMessage extends WebSocketMessage {
  event: "kline_update";
  symbol: string;
  interval: string;
  data: {
    time: number;
    open: number;
    high: number;
    low: number;
    close: number;
    volume: number;
  };
  timestamp: number;
}

export function useKLineWebSocket() {
  const stompClient = ref<any>(null);
  const connected = ref(false);
  const reconnectAttempts = ref(0);
  const MAX_RECONNECT_ATTEMPTS = 3;
  const isConnecting = ref(false);

  // 订阅列表（存储订阅对象，用于取消订阅）
  const subscriptions = ref<Map<string, any>>(new Map());
  // 待订阅列表（存储订阅信息，用于连接成功后自动订阅）
  const pendingSubscriptions = ref<
    Map<string, { symbol: string; interval: string }>
  >(new Map());

  // 消息处理器
  const messageHandlers = new Map<string, (data: any) => void>();

  /**
   * 连接WebSocket (使用 STOMP)
   * 参考回测中的实现方式：使用 Stomp.over(socket)
   * 连接失败时优雅降级，不阻塞主流程
   */
  const connect = (url?: string) => {
    if (stompClient.value?.connected) {
      console.log("WebSocket已连接");
      return;
    }

    if (isConnecting.value) {
      console.log("WebSocket正在连接中，跳过重复连接");
      return;
    }

    try {
      isConnecting.value = true;
      const authStore = useAuthStore();

      // 构建 WebSocket URL：后端 STOMP 端点为 /ws（与 WebSocketConfig 一致）
      // @ts-ignore
      let wsUrl =
        url ||
        import.meta.env?.VITE_WS_URL ||
        (import.meta.env?.VITE_API_URL
          ? String(import.meta.env.VITE_API_URL).replace(/\/api\/?$/, "") +
            "/ws"
          : "") ||
        "/ws";
      wsUrl = (typeof wsUrl === "string" ? wsUrl : "").trim() || "/ws";

      // 完整 URL：转为 http(s)，并确保以 /ws 结尾
      if (
        wsUrl.startsWith("http://") ||
        wsUrl.startsWith("https://") ||
        wsUrl.startsWith("ws://") ||
        wsUrl.startsWith("wss://")
      ) {
        if (wsUrl.startsWith("ws://") || wsUrl.startsWith("wss://")) {
          wsUrl = wsUrl.replace(/^ws/, "http").replace(/^wss/, "https");
        }
        if (!/\/ws\/?$/.test(wsUrl)) {
          wsUrl = wsUrl.replace(/\/?$/, "") + "/ws";
        }
      } else if (!wsUrl.startsWith("/")) {
        wsUrl = "/" + wsUrl;
      }

      console.log("尝试连接K线WebSocket (STOMP):", wsUrl);

      // 创建 SockJS 连接（参考回测中的实现）
      // 提供 factory 函数以支持自动重连
      const socketFactory = () => new SockJS(wsUrl);
      stompClient.value = Stomp.over(socketFactory);

      // 禁用调试日志（可选）
      stompClient.value.debug = () => {
        // 只在开发环境输出调试信息
        // if (import.meta.env.DEV) {
        //   console.log('STOMP:', ...arguments)
        // }
      };

      // 连接（参考回测中的实现）
      stompClient.value.connect(
        {
          Authorization: `Bearer ${authStore.token || ""}`,
        },
        () => {
          // 连接成功回调
          connected.value = true;
          reconnectAttempts.value = 0;
          isConnecting.value = false;
          console.log("✅ K线WebSocket (STOMP) 连接成功");

          // 重新订阅之前的订阅（延迟一下确保连接完全建立）
          setTimeout(() => {
            // 处理待订阅列表（这些是连接前保存的订阅）
            const pendingList = Array.from(
              pendingSubscriptions.value.entries(),
            );
            // 清空待订阅列表（避免重复处理）
            pendingSubscriptions.value.clear();

            // 执行订阅
            pendingList.forEach(([subKey, { symbol, interval }]) => {
              console.log("🔄 连接成功后订阅:", symbol, interval);
              doSubscribe(symbol, interval);
            });
          }, 100);
        },
        (error: any) => {
          // 连接失败回调
          reconnectAttempts.value++;
          isConnecting.value = false;

          if (reconnectAttempts.value >= MAX_RECONNECT_ATTEMPTS) {
            console.warn(
              "⚠️ K线WebSocket连接失败，已达到最大重试次数。将使用REST API轮询模式。",
              error,
            );
            if (stompClient.value) {
              stompClient.value.disconnect();
              stompClient.value = null;
            }
          } else {
            console.log(
              `K线WebSocket连接失败，正在重试 (${reconnectAttempts.value}/${MAX_RECONNECT_ATTEMPTS})...`,
            );
          }
        },
      );
    } catch (error) {
      isConnecting.value = false;
      console.warn("⚠️ K线WebSocket初始化失败，将使用REST API模式:", error);
      // 不抛出错误，允许应用继续运行
    }
  };

  /**
   * 处理消息（参考回测中的实现）
   */
  const handleMessage = (message: any) => {
    try {
      const data =
        typeof message.body === "string"
          ? JSON.parse(message.body)
          : message.body;
      const { event } = data;

      // 处理系统事件
      switch (event) {
        case "connected":
          console.log("WebSocket已连接:", data);
          break;
        case "subscribed":
          console.log("订阅成功:", data);
          break;
        case "unsubscribed":
          console.log("取消订阅成功:", data);
          break;
        case "pong":
          // 心跳响应
          break;
        case "kline_update":
          // K线更新
          const handler = messageHandlers.get("kline_update");
          if (handler) {
            handler(data as KLineUpdateMessage);
          }
          break;
        default:
          // 自定义事件
          const customHandler = messageHandlers.get(event);
          if (customHandler) {
            customHandler(data);
          }
      }
    } catch (error) {
      console.error("解析WebSocket消息失败:", error);
    }
  };

  /**
   * 发送消息 (使用 STOMP send，参考回测中的实现)
   */
  const send = (destination: string, data?: any) => {
    if (stompClient.value?.connected) {
      stompClient.value.send(destination, {}, JSON.stringify(data || {}));
    } else {
      console.warn("⚠️ WebSocket未连接，消息未送达:", destination, data);
    }
  };

  /**
   * 实际执行订阅（内部方法）
   */
  const doSubscribe = (symbol: string, interval: string) => {
    if (!stompClient.value?.connected) {
      console.warn("⚠️ WebSocket未连接，无法订阅:", symbol, interval);
      return;
    }

    const subKey = `${symbol}_${interval}`;

    // 如果已经订阅，先取消
    if (subscriptions.value.has(subKey)) {
      const oldSub = subscriptions.value.get(subKey);
      if (oldSub) {
        oldSub.unsubscribe();
        subscriptions.value.delete(subKey);
      }
    }

    // 订阅主题：/topic/kline/{symbol}_{interval}（参考回测中的实现）
    const topic = `/topic/kline/${subKey}`;
    console.log("🔔 开始订阅K线数据:", { symbol, interval, subKey, topic });

    const subscription = stompClient.value.subscribe(topic, (message: any) => {
      console.log("📨 收到K线消息:", { topic, message: message.body });
      handleMessage(message);
    });

    subscriptions.value.set(subKey, subscription);

    // 发送订阅请求到后端
    send("/app/kline/subscribe", {
      symbol,
      interval,
    });

    console.log("✅ 已订阅K线数据:", symbol, interval, topic);
  };

  /**
   * 订阅K线数据（参考回测中的实现）
   */
  const subscribe = (symbol: string, interval: string) => {
    const subKey = `${symbol}_${interval}`;

    // 存储订阅信息（即使未连接也存储，连接成功后会订阅）
    pendingSubscriptions.value.set(subKey, { symbol, interval });

    if (!stompClient.value?.connected) {
      // WebSocket未连接，已保存订阅信息，尝试重连
      console.log("⏳ WebSocket未连接，已保存订阅信息，尝试重连:", symbol, interval);
      // stompClient 存在但未连接，主动触发重连
      if (stompClient.value) {
        connect();
      }
      return;
    }

    // 如果已连接，直接订阅
    doSubscribe(symbol, interval);
  };

  /**
   * 取消订阅（参考回测中的实现）
   */
  const unsubscribe = (symbol: string, interval: string) => {
    const subKey = `${symbol}_${interval}`;

    // 从待订阅列表中移除
    pendingSubscriptions.value.delete(subKey);

    if (!stompClient.value?.connected) {
      return;
    }

    const subscription = subscriptions.value.get(subKey);

    if (subscription) {
      subscription.unsubscribe();
      subscriptions.value.delete(subKey);

      // 发送取消订阅请求到后端
      send("/app/kline/unsubscribe", {
        symbol,
        interval,
      });

      console.log("✅ 已取消订阅K线数据:", symbol, interval);
    }
  };

  /**
   * 注册消息处理器
   */
  const on = (event: string, handler: (data: any) => void) => {
    messageHandlers.set(event, handler);
  };

  /**
   * 移除消息处理器
   */
  const off = (event: string) => {
    messageHandlers.delete(event);
  };

  /**
   * 上报图表状态
   */
  const reportChartState = (state: {
    mode: "realtime" | "historical";
    visibleRange?: { from: number; to: number };
    isDragging?: boolean;
    isZooming?: boolean;
  }) => {
    send("/app/kline/chart-state", { state });
  };

  /**
   * 断开连接（参考回测中的实现）
   */
  const disconnect = () => {
    if (stompClient.value) {
      // 取消所有订阅
      subscriptions.value.forEach((subscription) => {
        subscription.unsubscribe();
      });
      subscriptions.value.clear();

      // 断开连接
      stompClient.value.disconnect();
      stompClient.value = null;
    }
    connected.value = false;
    messageHandlers.clear();
  };

  onUnmounted(() => {
    disconnect();
  });

  return {
    connected,
    connect,
    send,
    subscribe,
    unsubscribe,
    on,
    off,
    reportChartState,
    disconnect,
  };
}
