# 后端重构计划表（分步实施）

本文档将《BACKEND_REFACTORING_RECOMMENDATIONS.md》中的建议拆解为可逐步执行的重构步骤。**每完成一步请执行编译与相关测试，通过后再进行下一步。**

---

## 阶段 0：准备（不改代码）

| 步骤 | 内容 | 产出 | 验证 |
|------|------|------|------|
| 0.1 | 确认当前主干/开发分支可编译、可运行 | - | `mvn clean compile` 及主应用启动成功 |
| 0.2 | 记录当前接口与关键用例（回测、K 线、订单、账户等） | 用例清单或测试列表 | 便于每步后回归 |
| 0.3 | 为本次重构开单独分支（如 `refactor/backend-phase1`） | 分支存在 | `git checkout -b refactor/backend-phase1` |

---

## 阶段 1：低风险基础修复（P0）

### 1.1 包名拼写修正：`contants` → `constants` ✅ 已完成

| 步骤 | 操作 | 涉及文件/包 | 验证 |
|------|------|--------------|------|
| 1.1.1 | 将 ai-data 中 `engine.data.entity.contants` 重命名为 `constants`（含 CandlestickIntervalEnum 所在目录） | ai-data：包名及该包下所有类 | 已做：新建 constants 包并迁移 CandlestickIntervalEnum |
| 1.1.2 | 将 ai-signal 中 `engine.signal.entity.contants` 重命名为 `constants`（含 OrderSideEnum、Trend 等） | ai-signal：包名及该包下所有类 | 已做：新建 constants 包并迁移 OrderSideEnum、Trend |
| 1.1.3 | 全局替换 import：`contants` → `constants` | 所有引用上述包的 Java 文件（约 46 处） | 已做：35 个文件替换，全量编译通过 |

**产出**：无 `contants` 包名，仅存在 `constants`。

---

### 1.2 统一 API 响应体（ai-common + 逐步替换） ✅ 已完成（2026-08-17）

| 步骤 | 操作 | 涉及文件 | 验证 |
|------|------|----------|------|
| 1.2.1 | 在 **ai-common** 新建 `com.chain.ai.trade.common.entity.dto.ApiResponse<T>`（或 `UnifiedResponse<T>`），字段：code(Integer)、message(String)、data(T)、timestamp(Long)；提供 success(data)、success(message, data)、error(code, message)、error(message) | ai-common 新建 1 个类 | ✅ |
| 1.2.2 | ai-quant 中所有引用 `engine.controller.dto.ApiResponse` 的 Controller 改为引用 ai-common 的 ApiResponse，并调整 success/error 调用以适配新 API（若无 success(message, data) 则在新类中补上） | KLineV1Controller 等 | ✅ 编译通过 |
| 1.2.3 | ai-quant 中所有引用 `engine.entity.vo.ApiResponse` 的 Controller 改为引用 ai-common 的 ApiResponse，同上适配 | OrderManagerController、TradingBotController、BacktestReportController 等 | ✅ |
| 1.2.4 | 删除 ai-quant 内 `controller/dto/ApiResponse.java` 与 `entity/vo/ApiResponse.java` | 删除 2 个文件 | ✅ `mvnw … compile` BUILD SUCCESS |

**产出**：全项目仅使用 ai-common 的 ApiResponse，ai-quant 无重复响应类。  
**兼容说明**：原 `controller.dto` 的 `timestamp` 为秒、成功文案为 `"success"`；统一后为毫秒 + `"操作成功"`。前端 `base.ts` 不读 `timestamp`，按 `code`/`success` 判断，行为兼容。

---

### 1.3 全局异常处理

| 步骤 | 操作 | 涉及文件 | 验证 |
|------|------|----------|------|
| 1.3.1 | 在 ai-common 新建 `com.chain.ai.trade.common.exception.BusinessException`（含 code、message），可选继承 RuntimeException | ai-common 新建 1 个类 | ai-common 编译 |
| 1.3.2 | 在 ai-quant 新建 `com.chain.ai.trade.engine.exception.GlobalExceptionHandler`，加 `@RestControllerAdvice`，处理：Exception、MethodArgumentNotValidException（校验）、BusinessException；统一返回 ai-common 的 ApiResponse 结构（如 code 500/400、message）并记录日志 | ai-quant 新建 1 个类 | 编译、故意触发异常看响应格式 |
| 1.3.3 | 在 1～2 个 Controller 中移除 try-catch，改为直接抛 BusinessException 或让异常上抛，由 GlobalExceptionHandler 兜底；确认响应与前端约定一致 | 如 OrderManagerController 中一个接口 | 该接口错误场景返回统一格式 |
| 1.3.4 | （可选）逐步将其他 Controller 中“仅 return error”的 catch 改为抛异常，由全局处理；每改几个接口跑一次用例 | 多个 Controller | 回归通过 |

**产出**：有统一异常处理器，新接口/逐步迁移的接口不再手写 error 返回。

---

## 阶段 2：消除重复实体与枚举（P1）

### 2.1 BaseEntity 统一到 ai-common

| 步骤 | 操作 | 涉及文件 | 验证 |
|------|------|----------|------|
| 2.1.1 | 确认 ai-common 的 `common.entity.dos.BaseEntity` 字段与 ai-order 的 `order.entity.BaseEntity` 一致（含注解、类型） | 对比两个类 | - |
| 2.1.2 | ai-order 中所有继承 `order.entity.BaseEntity` 的实体类改为继承 `com.chain.ai.trade.common.entity.dos.BaseEntity`，并改 import | TradeOrder、TradeOrderItem、TradeOrderMain、TrailingDetail、TradeOrderClose、TradeOrderCloseItem、MemberRobotConfig 等 | ai-order 编译 |
| 2.1.3 | 删除 ai-order 的 `order.entity.BaseEntity.java` | 删除 1 个文件 | 全量编译、订单相关接口/用例通过 |

**产出**：全项目仅使用 ai-common 的 BaseEntity。

---

### 2.2 OrderSideEnum 与信号侧枚举区分

| 步骤 | 操作 | 涉及文件 | 验证 |
|------|------|----------|------|
| 2.2.1 | 在 ai-signal 将 `engine.signal.entity.constants.OrderSideEnum` 重命名为 `SignalSideEnum`（或 `SignalDirectionEnum`），保留 BUY/SELL/LONG/SHORT 等值 | ai-signal 枚举类及该枚举所在包内引用 | ai-signal 编译 |
| 2.2.2 | 全局替换 ai-signal 内对原 OrderSideEnum 的引用为 SignalSideEnum（import 与类型） | ai-signal 内使用该枚举的类 | ai-signal、ai-quant 编译 |
| 2.2.3 | 确认 ai-common 的 OrderSideEnum 仅用于“交易方向”（order/engine 等），ai-signal 仅用 SignalSideEnum；在文档或类注释中写明两者职责 | - | 无编译错误、语义清晰 |

**产出**：交易侧用 OrderSideEnum，信号侧用 SignalSideEnum，命名不混淆。

---

## 阶段 3：Controller 统一返回与错误处理（P1）

| 步骤 | 操作 | 涉及文件 | 验证 |
|------|------|----------|------|
| 3.1 | 在 ai-quant 或 ai-common 提供 `ResponseUtil`（或沿用 ApiResponse 静态方法）：如 `success(data)`、`error(code, message)`，返回类型与 ai-common ApiResponse 一致 | 1 个工具类或扩展现有 ApiResponse | 编译 |
| 3.2 | 将 BacktestController 中返回 `ResponseEntity.badRequest().body(...)` / `ResponseEntity.internalServerError().body(...)` 的接口改为返回 `ApiResponse` 或 `ResponseEntity<ApiResponse<?>>`，错误分支使用 `ApiResponse.error(...)` 或 ResponseUtil | BacktestController | 回测相关接口响应格式统一、前端无需改协议 |
| 3.3 | 对其余仍用 ResponseEntity 裸体的 Controller（如 TradingAccountController、BacktestReportController）做同样替换 | 上述 Controller | 全量接口风格一致 |
| 3.4 | （可选）为 4xx/5xx 统一约定 HTTP 状态码与 body 中 code 的对应关系，并在 GlobalExceptionHandler 中落实 | GlobalExceptionHandler | 文档更新、错误响应一致 |

**产出**：REST 接口统一返回 ApiResponse（或 ResponseEntity<ApiResponse>），错误由全局异常或统一工具方法生成。

---

## 阶段 4：BacktestResult 命名与文档（P2）

| 步骤 | 操作 | 涉及文件 | 验证 |
|------|------|----------|------|
| 4.1 | 在文档（如 BACKEND_REFACTORING_RECOMMENDATIONS.md 或新建 BACKTEST_LAYERS.md）中写明：BacktestEngine.BacktestResult = 引擎内存结果；ai-backtest 的 BacktestResult = 持久化 DO；TrendLineBacktestService/Response 内 BacktestResult = 趋势线专用；BacktestResultDTO = 传输 DTO | 文档 | - |
| 4.2 | （可选）若团队希望避免同名，可将引擎/趋势线内部类改名为 `EngineBacktestResult`、`TrendLineResult` 等，并替换引用；若保留原名，则至少保证包与文档区分清晰 | 若干类 | 编译、回测与报表功能正常 |

**产出**：BacktestResult 多处用途有文档说明，或通过重命名减少混淆。

---

## 阶段 5：关键 Service 抽接口（P2）

| 步骤 | 操作 | 涉及文件 | 验证 |
|------|------|----------|------|
| 5.1 | 为 BacktestService 定义接口（如 IBacktestService），方法签名与现 BacktestService 对外方法一致，BacktestService 改为 implements IBacktestService | ai-engine | 编译、回测调用处改为依赖接口 |
| 5.2 | 为 ChartService 定义接口（如 IChartService），ChartService 改为实现该接口；调用方（如 BacktestController）依赖接口 | ai-quant | 同上 |
| 5.3 | （可选）对 BacktestResultSaveService、KLineWebSocketService 等按需抽接口，优先改被多处在用的服务 | ai-quant 等 | 编译、相关用例通过 |

**产出**：核心回测与图表服务面向接口编程，便于测试与替换。

---

## 阶段 6：命名与配置规范（P3）

| 步骤 | 操作 | 涉及文件 | 验证 |
|------|------|----------|------|
| 6.1 | 团队约定 DTO 后缀统一为 DTO 或 Dto，并在新代码中执行；旧代码可在后续改动时逐步统一 | 新代码 + 渐进式旧代码 | 新类命名一致 |
| 6.2 | 在 README 或架构文档中说明：主应用为 ai-quant/ai-task/ai-order，配置入口为其 application.yml；ai-data、ai-signal、ai-risk 等为库模块，无独立 application.yml | 文档 | - |
| 6.3 | （可选）将 ai-common 下 `engine.utils` 迁到 `common.utils`，并全局替换 import；若影响面大可延后 | ai-common + 所有引用 | 全量编译 |

**产出**：命名与配置职责有明确约定并落文档。

---

## 执行顺序与依赖关系

```
阶段 0（准备）
    ↓
阶段 1.1（contants → constants）   ← 独立，可最先做
    ↓
阶段 1.2（统一 ApiResponse）       ← 1.3、3.x 依赖统一响应体
    ↓
阶段 1.3（全局异常处理）
    ↓
阶段 2.1（BaseEntity 统一）
阶段 2.2（OrderSideEnum / SignalSideEnum）  ← 2.1 与 2.2 可并行
    ↓
阶段 3（Controller 统一返回）
    ↓
阶段 4（BacktestResult 文档/重命名）
阶段 5（Service 抽接口）          ← 4 与 5 可并行
    ↓
阶段 6（命名与配置规范）
```

---

## 单步检查清单（建议每步执行后打勾）

- [ ] `mvn clean compile -pl <受影响模块> -am` 或全量编译通过  
- [ ] 主应用启动成功（若改动 ai-quant）  
- [ ] 该步骤涉及的接口或用例跑通  
- [ ] 无新增 linter 报错（可选）  
- [ ] 提交前 diff 自查，无无关改动  

---

## 回滚建议

- 每完成一个「阶段」或 2～3 个关联步骤，做一次提交，提交信息注明阶段与步骤（如 `refactor(phase1): contants -> constants`）。
- 若某步导致问题，可回滚到该阶段前的最后一次提交，再缩小改动范围重做。

完成阶段 1～3 后，重复类与统一响应/异常处理即可基本到位；阶段 4～6 可按排期逐步推进。
