# AI Trading Platform

AI量化交易操作系统，基于 Java 25 + Spring Boot 3.2+ + Ta4j 构建。

## 项目结构

```
lenzeto/
├── pom.xml                 # 父级POM，统一管理依赖版本和模块构建
├── ai-frontend-web/        # Vue3 前端项目 (端口:3000) ⭐️
├── ai-quant/               # 📦 Boot 应用 - 量化分析主应用 (端口:8118)
├── ai-task/                # 📦 Boot 应用 - XXL-JOB 定时任务执行器
├── ai-engine/              # 📚 库模块 - 交易引擎核心
├── ai-data/                # 📚 库模块 - 市场数据与K线持久化
├── ai-signal/              # 📚 库模块 - 技术指标计算与信号生成
├── ai-strategy/            # 📚 库模块 - 策略模板与参数配置
├── ai-order/               # 📚 库模块 - 订单领域服务与持久化
├── ai-member/              # 📚 库模块 - 用户认证与权限管理
├── ai-risk/                # 📚 库模块 - 风控规则引擎
├── ai-common/              # 📚 库模块 - 公共工具与实体类
├── ai-backtest/            # 📚 库模块 - 回测引擎
├── ai-xchange-extends/     # 📚 库模块 - 交易所 API 封装扩展
├── ai-extension/           # 📚 库模块 - Ta4j 自定义指标与扩展
├── ai-logs/                # 📚 库模块 - 业务日志模块
├── ai-agent/               # 📚 库模块 - AI Agent 与 LangChain4j 集成
└── ai-account/             # 🗑️ 已废弃 - 资金账户（待合并到 ai-member）
```

## 模块说明

### Boot 应用

| 模块 | 端口 | 说明 |
|------|------|------|
| ai-quant | 8118 | 量化分析主应用，聚合所有库模块的 REST API、WebSocket |
| ai-task | — | XXL-JOB 定时任务执行器，独立部署执行定时任务 |

### 库模块

| 模块 | 说明 | 依赖方 |
|------|------|--------|
| ai-engine | 交易引擎核心，回测、策略执行 | ai-quant |
| ai-data | 市场数据 K 线持久化、Candlestick 服务 | ai-quant, ai-task |
| ai-signal | 技术指标计算、信号生成与聚合 | ai-quant, ai-task |
| ai-strategy | 策略模板、TradingBot 实体、参数配置 | ai-quant, ai-task |
| ai-order | 订单生命周期管理、交易记录存储 | ai-quant, ai-task |
| ai-member | 用户认证（JWT）、API Key、RBAC 权限 | ai-quant, ai-task |
| ai-risk | 风控规则引擎（仓位/止损/止盈/加仓规则） | ai-quant |
| ai-common | 公共工具类、统一响应、实体基类 | 所有模块 |
| ai-backtest | 回测引擎，支持参数优化 | ai-quant |
| ai-xchange-extends | XChange 交易所 API 封装扩展 | ai-quant, ai-task |
| ai-extension | Ta4j 自定义指标、SMC 逻辑扩展 | ai-quant |
| ai-logs | 业务操作日志记录 | ai-quant |
| ai-agent | AI Agent、LLM 交互（基于 LangChain4j / Spring AI） | ai-quant |

### 前端

| 项目 | 框架 | 端口 | 说明 |
|------|------|------|------|
| ai-frontend-web | Vue 3 + Vite + Element Plus | 3000 | 新一代前端，支持 TypeScript ⭐️ |

## 技术栈

### 后端
- **框架**: Spring Boot 4.0.1
- **语言**: Java 25
- **数据库**: MySQL 8.0 + Redis 7
- **交易库**: Ta4j 0.22.6
- **AI库**: Smile 6.1.0 + LangChain4j 1.0.0 + Spring AI MCP 1.1.5
- **ORM**: MyBatis-Plus 3.5.15
- **任务调度**: XXL-JOB 2.4.1

### 前端
- **框架**: Vue 3.4.0
- **构建工具**: Vite 5.0.0
- **UI库**: Element Plus 2.4.4
- **图表库**: ECharts 5.4.3 + Lightweight Charts 4.1.0
- **状态管理**: Pinia 2.1.7
- **路由**: Vue Router 4.2.5

## 构建和运行

### 前端项目 (frontend)
```bash
cd frontend
npm install
npm run serve  # 开发环境
npm run build  # 生产环境构建
```

### 构建后端模块
```bash
mvn clean package -DskipTests
```

### 运行量化分析平台
```bash
cd ai-quant
mvn spring-boot:run
```

### 运行AI引擎
```bash
cd ai-engine
mvn spring-boot:run
```

### 运行定时任务（可选）
```bash
cd ai-task
mvn spring-boot:run
```

> **说明**：`ai-order`、`ai-engine` 等为库模块，不单独 `spring-boot:run`。生产可启动应用仅为 **ai-quant**（主 API）与 **ai-task**（XXL-JOB）。

## 访问地址

### 前端界面
- **Vue2前端**: `http://localhost:8080` (传统版本)
- **Vue3前端**: `http://localhost:3000` (新一代版本) ⭐️
- 功能: K线图展示、策略回测、订单管理

### API文档

#### AI量化平台 (ai-quant)
- 默认端口: 8081
- 回测API: `/api/backtest`
  - `POST /api/backtest/run` - 执行单策略回测
  - `POST /api/backtest/compare` - 多策略对比回测
  - `GET /api/backtest/config` - 获取回测配置
- 文档: 启动后访问 `http://localhost:8081/swagger-ui.html`

#### 订单 API（由 ai-quant 提供）
- 与主应用同端口（默认 8081）
- 示例：`TradeOrderController`、`OrderManagerController` 等

## 开发环境要求

- JDK 21+
- Maven 3.6+
- MySQL 8.0+
- MongoDB 4.0+

## 配置

各模块的配置文件位于 `src/main/resources/application.yml`
