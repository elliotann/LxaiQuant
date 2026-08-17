# TradingBotController 迁移说明

## 迁移概述

TradingBotController 已从 ai-strategy 模块成功迁移至 ai-quant 模块，以实现更好的架构分层和统一API接口管理。

## 迁移详情

### 迁移前
- **位置**: `ai-strategy/src/main/java/com/chain/ai/trade/engine/strategy/controller/TradingBotController.java`
- **包路径**: `com.chain.ai.trade.engine.strategy.controller`

### 迁移后
- **位置**: `ai-quant/src/main/java/com/chain/ai/trade/engine/controller/TradingBotController.java`
- **包路径**: `com.chain.ai.trade.engine.controller`

## 变更内容

### 1. 包路径变更
```java
// 迁移前
package com.chain.ai.trade.engine.strategy.controller;

// 迁移后
package com.chain.ai.trade.engine.controller;
```

### 2. 依赖关系确认
- ai-quant 模块已包含 ai-strategy 模块的依赖
- TradingBotController 可以正常访问：
  - `TradingBot` 实体类
  - `BotStatus` 枚举
  - `ITradingBotService` 服务接口

### 3. 文件清理
- ✅ 已删除 ai-strategy 模块中的 TradingBotController.java
- ✅ 已删除空的 controller 目录
- ✅ 更新了 README 文档说明

## API 接口保持不变

所有 API 接口路径和功能保持完全不变：

### 基础 CRUD
- `GET /api/trading-bots` - 分页查询
- `GET /api/trading-bots/{botId}` - 详情查询
- `POST /api/trading-bots` - 创建机器人
- `PUT /api/trading-bots/{botId}` - 更新机器人
- `DELETE /api/trading-bots/{botId}` - 删除机器人

### 状态管理
- `POST /api/trading-bots/{botId}/start` - 启动
- `POST /api/trading-bots/{botId}/stop` - 停止
- `POST /api/trading-bots/{botId}/pause` - 暂停
- `POST /api/trading-bots/{botId}/resume` - 恢复

### 专项查询
- `GET /api/trading-bots/user/{userId}` - 按用户查询
- `GET /api/trading-bots/strategy/{strategyId}` - 按策略查询
- `GET /api/trading-bots/account/{accountId}` - 按账户查询
- `GET /api/trading-bots/status/{status}` - 按状态查询

### 监控统计
- `POST /api/trading-bots/{botId}/capital` - 更新资金
- `POST /api/trading-bots/{botId}/statistics` - 更新统计
- `GET /api/trading-bots/{botId}/running-status` - 运行状态
- `GET /api/trading-bots/stats/status` - 状态统计

## 架构优势

### 1. 统一管理
- 所有业务控制器集中在 ai-quant 主模块
- 更好的依赖管理和版本控制
- 统一的API网关和路由管理

### 2. 分层清晰
- ai-strategy: 专注策略定义和业务逻辑
- ai-quant: 专注API接口和控制器逻辑
- 清晰的职责分离和依赖关系

### 3. 维护便利
- 控制器变更不会影响策略核心逻辑
- 便于独立部署和测试
- 更好的代码组织结构

## 验证结果

- ✅ 代码编译检查通过
- ✅ Linting 检查通过
- ✅ 包路径正确
- ✅ 依赖关系完整
- ✅ API 接口保持一致

## 注意事项

1. **向后兼容**: 所有现有API接口保持不变，不影响前端调用
2. **依赖管理**: 确保 ai-quant 对 ai-strategy 的依赖关系正确维护
3. **文档同步**: 已更新相关文档说明控制器位置变更
4. **测试覆盖**: 建议运行相关测试用例验证功能正常

## 后续建议

1. 考虑将其他业务模块的控制器也统一迁移到 ai-quant 模块
2. 建立统一的控制器命名规范和包结构
3. 完善API文档和接口测试覆盖
