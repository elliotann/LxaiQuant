# 行情数据迁移到 ai-data 模块方案

> 目标：将所有行情数据相关的类迁移到 ai-data 模块，使得 ai-data 成为纯粹的行情数据中心。
> 注意：**Controller 暂不迁移**，留在原模块。

---

## 一、ai-data 模块现有结构（不动）

```
ai-data/src/main/java/com/chain/ai/trade/engine/data/
├── entity
│   ├── constants
│   │   └── CandlestickIntervalEnum.java        ✅ 已有
│   ├── dos
│   │   ├── Candlestick.java                    ✅ 已有
│   │   ├── Symbol.java                         ✅ 已有
│   │   ├── UserFavorite.java                   ✅ 已有
│   │   ├── OptimizationTask.java               ✅ 已有
│   │   ├── OptimizationResult.java             ✅ 已有
│   │   ├── SmcResponse.java                    ✅ 已有
│   │   ├── SmcBosChochSignal.java              ✅ 已有
│   │   ├── SmcBarResult.java                   ✅ 已有
│   │   └── SmcOrderBlock.java                  ✅ 已有
│   ├── dto
│   │   └── CriticalLevel.java                  ✅ 已有
│   └── param
│       ├── KlineParam.java                     ✅ 已有
│       └── CandlestickRequest.java             ✅ 已有
├── mapper
│   ├── CandlestickMapper.java                  ✅ 已有
│   ├── SymbolsMapper.java                      ✅ 已有
│   ├── UserFavoriteMapper.java                 ✅ 已有
│   ├── OptimizationTaskMapper.java             ✅ 已有
│   └── OptimizationResultMapper.java           ✅ 已有
├── service
│   ├── impl
│   │   ├── CandlestickServiceImpl.java         ✅ 已有
│   │   ├── SymbolsServiceImpl.java             ✅ 已有
│   │   ├── UserFavoriteServiceImpl.java        ✅ 已有
│   │   ├── OptimizationTaskServiceImpl.java    ✅ 已有
│   │   └── OptimizationResultServiceImpl.java  ✅ 已有
│   ├── ICandlestickService.java                ✅ 已有
│   ├── ISymbolsService.java                    ✅ 已有
│   ├── IUserFavoriteService.java               ✅ 已有
│   ├── IOptimizationTaskService.java           ✅ 已有
│   └── IOptimizationResultService.java         ✅ 已有
├── provider
│   ├── ExchangeKlineFetcher.java               ✅ 已有（接口）
│   ├── ExchangeKlineFetcherFactory.java         ✅ 已有
│   ├── KlineDataProvider.java                  ✅ 已有（接口）
│   ├── KlineDataProviderFactory.java            ✅ 已有
│   ├── RealKlineDataProvider.java              ✅ 已有
│   └── TestKlineDataProvider.java              ✅ 已有
└── utils
    └── IndicatorWrapHelper.java                ✅ 已有
```

---

## 二、需迁移清单（按模块）

### 2.1 ai-quant → ai-data

#### 2.1.1 Provider 实现类（2个）

| 文件 | 源路径 | 目标路径 | 确认 |
|------|--------|----------|------|
| `OkxExchangeKlineFetcher.java` | `ai-quant/.../data/provider/impl/` | `ai-data/.../data/provider/impl/` | ☐ |
| `GateioExchangeKlineFetcher.java` | `ai-quant/.../data/provider/impl/` | `ai-data/.../data/provider/impl/` | ☐ |

#### 2.1.2 Service（6个）

| 文件 | 源路径 | 目标路径 | 确认 |
|------|--------|----------|------|
| `KLineV1Service.java` | `ai-quant/.../service/` | `ai-data/.../data/service/` | ☐ |
| `KLineV1ServiceImpl.java` | `ai-quant/.../service/impl/` | `ai-data/.../data/service/impl/` | ☐ |
| `KLineWebSocketService.java` | `ai-quant/.../service/` | `ai-data/.../data/service/` | ☐ |
| `MarketAnalysisService.java` | `ai-quant/.../service/` | `ai-data/.../data/service/` | ☐ |
| `TrendlineService.java` | `ai-quant/.../service/` | `ai-data/.../data/service/` | ☐ |
| `TrendlineServiceImpl.java` | `ai-quant/.../service/impl/` | `ai-data/.../data/service/impl/` | ☐ |

#### 2.1.3 DTO/Request/Response

| 文件 | 源包 | 目标包 | 确认 |
|------|------|--------|------|
| `TickerDTO.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `KLineHistoryRequest.java` | `controller.dto` | `data.entity.param` | ☐ |
| `KLineHistoryResponse.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `KLineLoadRequest.java` | `controller.dto` | `data.entity.param` | ☐ |
| `KLineLoadResponse.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `KLineJumpRequest.java` | `controller.dto` | `data.entity.param` | ☐ |
| `KLineJumpResponse.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `KLineDataDTO.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `KLineImportFromExchangeRequest.java` | `controller.dto` | `data.entity.param` | ☐ |
| `KLineImportFromExchangeResponse.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `KLineSignalRequest.java` | `controller.dto` | `data.entity.param` | ☐ |
| `KLineSignalResponse.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `KLineSignalDTO.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `SymbolInfoDTO.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `MarketAnalysisDTO.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `MarketAnalysisBatchRequest.java` | `controller.dto` | `data.entity.param` | ☐ |
| `TrendlineRequest.java` | `controller.dto` | `data.entity.param` | ☐ |
| `TrendlineResponse.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `TrendlineParams.java` | `controller.dto` | `data.entity.param` | ☐ |
| `TrendlineData.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `TimeValuePoint.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `TrendSegment.java` | `controller.dto` | `data.entity.dto` | ☐ |
| `SupportResistanceData.java` | `controller.dto` | `data.entity.dto` | ☐ |

> 分类原则：
> - `*Request.java` → `data.entity.param`
> - `*Response.java`, `*DTO.java` → `data.entity.dto`

#### 2.1.4 工具类（1个）

| 文件 | 源路径 | 目标路径 | 确认 |
|------|--------|----------|------|
| `SymbolNormalizer.java` | `ai-quant/.../util/` | `ai-data/.../data/utils/` | ☐ |

> 说明：`SymbolNormalizer` 涉及行情标准化，与策略/交易无关，适合放入 ai-data。

---

## 三、暂不迁移清单

| 类 | 模块 | 原因 |
|----|------|------|
| **所有 Controller** | ai-quant | 用户确认暂不迁移 |
| `SmcMultiPeriodService` | ai-quant | 用户确认暂不迁移 |
| `ChanLunService` | ai-quant | 用户确认暂不迁移 |
| `AiRadarService` | ai-quant | 用户确认暂不迁移 |
| `KLineRealTimeUpdateTask` | ai-quant | 用户确认暂不迁移 |
| `IChartService` / `ChartService` | ai-quant | 与回测强关联 |
| `ReviewMetricsService` | ai-quant | 涉及复盘业务 |
| `AnalysisScheduler` | ai-quant | 调度逻辑 |
| `KLineSocketIOHandler` | ai-quant | 属于 WebSocket 网关层 |
| **ai-task 全部 3 个 JobHandler** | ai-task | 用户确认暂不迁移 |
| **ai-xchange-extends 全部 5 个类** | ai-xchange-extends | 用户确认暂不迁移 |
| **ai-engine 全部 2 个类** | ai-engine | 用户确认暂不迁移 |
| `MarketType`、`Exchange`、`ContractSpec` | ai-common | 全局常量，多模块共享 |
| **以下 DTO 随不迁移的服务暂留** | | |
| `SmcMultiPeriodResponse.java` | ai-quant | 关联 SmcMultiPeriodService |
| `AiRadarOpportunityDTO.java` | ai-quant | 关联 AiRadarService |
| `AnalysisReportPageRequest.java` | ai-quant | 关联 AnalysisScheduler |
| `AiAnalysisTaskCreateRequest.java` | ai-quant | 关联 AI 分析任务 |
| `AiAnalysisTaskUpdateRequest.java` | ai-quant | 关联 AI 分析任务 |
| `ElliottWaveAnalysisDTO.java` | ai-quant | 关联 ChartService |

---

## 四、迁移顺序建议

```
第一批（DTO/Param → 无代码依赖）
  └─ 所有 Request/Response/DTO 复制到 ai-data

第二批（Provider 实现 → 只依赖 ai-data 已有接口）
  └─ OkxExchangeKlineFetcher
  └─ GateioExchangeKlineFetcher

第三批（Service → 依赖 Provider + DTO）
  └─ KLineV1Service / KLineV1ServiceImpl
  └─ KLineWebSocketService
  └─ MarketAnalysisService
  └─ TrendlineService / TrendlineServiceImpl

第四批（工具类）
  └─ SymbolNormalizer
```

---

## 五、迁移后模块职责

| 模块 | 职责 |
|------|------|
| **ai-data** | 行情数据：entity / mapper / service / provider / dto |
| **ai-quant** | 策略 / 交易 / 信号 / 回测 / AI 分析 / SMC/缠论/雷达 / Controller（暂留）/ Task（暂留） |
| **ai-task** | XXL-Job 任务调度编排（具体逻辑下沉到各模块） |
| **ai-xchange-extends** | 交易所对接层（行情部分暂留） |
| **ai-engine** | 引擎核心（行情数据源暂留） |
| **ai-common** | 全局共享常量 / 公共工具 |
