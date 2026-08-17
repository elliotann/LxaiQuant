# K线WebSocket实时推送实现说明

## 概述

已实现K线数据的WebSocket实时推送功能，支持两种方式：
1. **Spring WebSocket (STOMP)** - 已实现，可直接使用
2. **Socket.IO** - 需要添加依赖后启用

## 当前实现

### 1. Spring WebSocket (STOMP) - 已启用

#### 配置
- 配置文件：`WebSocketConfig.java`
- 端点：`/ws` (支持SockJS)
- 消息代理：`/topic`
- 应用前缀：`/app`

#### 使用方式

**前端订阅：**
```javascript
// 使用STOMP客户端
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: {
    Authorization: 'Bearer ' + token
  },
  onConnect: () => {
    // 订阅K线数据
    client.subscribe('/topic/kline/ETH-USDT-SWAP_OKXMIN3', (message) => {
      const data = JSON.parse(message.body);
      console.log('K线更新:', data);
    });
    
    // 发送订阅请求
    client.publish({
      destination: '/app/kline/subscribe',
      body: JSON.stringify({
        symbol: 'ETH-USDT-SWAP',
        interval: 'OKXMIN3'
      })
    });
  }
});

client.activate();
```

**后端推送：**
```java
// 在需要推送的地方调用
kLineWebSocketService.broadcastKLineUpdate(
    "ETH-USDT-SWAP", 
    "OKXMIN3", 
    klineDataMap
);
```

### 2. Socket.IO - 需要启用

#### 启用步骤

**1. 添加依赖（在 `ai-quant/pom.xml`）：**
```xml
<dependency>
    <groupId>com.corundumstudio.socketio</groupId>
    <artifactId>netty-socketio</artifactId>
    <version>2.0.3</version>
</dependency>
```

**2. 配置（在 `application.yml`）：**
```yaml
socketio:
  enabled: true
  host: 0.0.0.0
  port: 8000
```

**3. 启用Socket.IO配置：**
- 取消 `SocketIOConfig.java` 中的注释
- 取消 `KLineSocketIOHandler.java` 中的注释

**4. 前端使用（已实现）：**
前端代码已支持Socket.IO，连接地址：`http://localhost:8000`

## 实时数据推送

### 定时任务

已创建 `KLineRealTimeUpdateTask`，每3秒自动推送最新K线数据：
- 支持的交易对：ETH-USDT-SWAP, BTC-USDT-SWAP, SOL-USDT-SWAP, BNB-USDT-SWAP
- 支持的时间周期：1m, 3m, 5m, 15m, 30m, 1H, 4H, 1D

### 手动推送

在需要的地方调用：
```java
@Autowired
private KLineWebSocketService kLineWebSocketService;

// 推送K线更新
Map<String, Object> klineData = new HashMap<>();
klineData.put("time", timestamp);
klineData.put("open", openPrice);
klineData.put("high", highPrice);
klineData.put("low", lowPrice);
klineData.put("close", closePrice);
klineData.put("volume", volume);

kLineWebSocketService.broadcastKLineUpdate(
    "ETH-USDT-SWAP", 
    "OKXMIN3", 
    klineData
);
```

## WebSocket事件

### STOMP事件

**客户端发送：**
- `/app/kline/subscribe` - 订阅K线数据
- `/app/kline/unsubscribe` - 取消订阅
- `/app/kline/chart-state` - 上报图表状态

**服务端推送：**
- `/topic/kline/{symbol}_{interval}` - K线数据更新

### Socket.IO事件（启用后）

**客户端发送：**
- `kline:subscribe` - 订阅K线数据
- `kline:unsubscribe` - 取消订阅
- `kline:chart-state` - 上报图表状态
- `ping` - 心跳

**服务端推送：**
- `kline:update` - K线数据更新
- `kline:subscribed` - 订阅成功
- `kline:unsubscribed` - 取消订阅成功
- `pong` - 心跳响应
- `connected` - 连接成功

## 消息格式

### K线更新消息
```json
{
  "event": "kline_update",
  "symbol": "ETH-USDT-SWAP",
  "interval": "OKXMIN3",
  "data": {
    "time": 1234567890,
    "open": 3000.00,
    "high": 3010.00,
    "low": 2990.00,
    "close": 3005.00,
    "volume": 1000000.00
  },
  "timestamp": 1234567890
}
```

## 注意事项

1. **Socket.IO依赖**：如果使用Socket.IO，需要添加 `netty-socketio` 依赖
2. **端口冲突**：Socket.IO默认使用8000端口，确保端口未被占用
3. **性能考虑**：定时任务每3秒执行一次，可根据需要调整频率
4. **订阅管理**：系统会自动管理订阅，客户端断开连接时自动清理

## 测试

### 测试STOMP连接
```bash
# 使用WebSocket客户端工具测试
# 连接: ws://localhost:8080/ws
# 订阅: /topic/kline/ETH-USDT-SWAP_OKXMIN3
# 发送: /app/kline/subscribe
```

### 测试Socket.IO连接（启用后）
```bash
# 使用Socket.IO客户端测试
# 连接: http://localhost:8000
# 事件: kline:subscribe
```

