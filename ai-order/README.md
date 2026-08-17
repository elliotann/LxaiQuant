# ai-order — 订单领域库模块

**类型**：Maven 库模块（`jar`），**不是**可独立部署的 Spring Boot 应用。

## 职责

- 订单生命周期：`ITradeOrderService` / `TradeOrderServiceImpl`
- 订单实体、Mapper、与交易所执行相关的领域逻辑
- 供 `ai-engine`、`ai-quant`、`ai-task` 通过依赖与 `@ComponentScan` 加载

## 运行时入口

订单相关 HTTP API 由 **`ai-quant`** 暴露（例如 `TradeOrderController`、`OrderManagerController`），配置使用 **`ai-quant`** 的 `application.yml`。

本模块 **不提供** `main()` 启动类，**不要** 在本目录执行 `mvn spring-boot:run`。

## 编译

```bash
# 在仓库根目录
mvn -pl ai-order -am compile
```

## 依赖关系（概要）

```text
ai-order → ai-common, ai-signal, ai-member, ai-xchange-extends, ai-strategy
```

被 `ai-engine`、`ai-quant` 等模块依赖。

## 集成测试（可选）

若需模块级 Spring 测试，在 `src/test/java` 使用 `@SpringBootTest` + 专用测试配置类，**勿** 恢复生产用 `Application` 主类。

## 更多说明

- 并发与订单细节：见 [CONCURRENCY_CONTROL_GUIDE.md](./CONCURRENCY_CONTROL_GUIDE.md)
- 后端重构计划：见 [docs/重构/Lenzito-后端重构计划.md](../docs/重构/Lenzito-后端重构计划.md)
