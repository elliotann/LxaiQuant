# AI Strategy Module

策略管理模块，提供策略的定义、版本管理、分类和参数配置等功能。

## 模块结构

```
ai-strategy/
├── src/main/java/com/chain/ai/trade/engine/strategy/
│   ├── entity/
│   │   └── dos/              # 实体类（DO - Data Object）
│   │       ├── Strategy.java
│   │       ├── StrategyVersion.java
│   │       ├── StrategyCategory.java
│   │       └── StrategyParameter.java
│   ├── enums/                # 枚举类
│   │   ├── StrategyType.java
│   │   ├── StrategyStatus.java
│   │   ├── Visibility.java
│   │   ├── Frequency.java
│   │   └── ChangeType.java
│   ├── mapper/               # MyBatis Mapper接口
│   │   ├── StrategyMapper.java
│   │   ├── StrategyVersionMapper.java
│   │   ├── StrategyCategoryMapper.java
│   │   └── StrategyParameterMapper.java
│   └── service/              # 服务层
│       ├── IStrategyService.java
│       ├── IStrategyVersionService.java
│       ├── IStrategyCategoryService.java
│       ├── IStrategyParameterService.java
│       └── impl/
│           └── StrategyServiceImpl.java
```

## 数据库表结构

### 1. strategy（策略定义表）
- 存储策略的核心定义信息
- 主键：id
- 唯一标识：strategy_id

### 2. strategy_version（策略版本表）
- 存储策略的版本历史
- 主键：id
- 唯一标识：version_id
- 关联：strategy_id

### 3. strategy_category（策略分类表）
- 存储策略分类信息
- 支持层级结构（parent_id）
- 主键：id

### 4. strategy_parameter（策略参数表）
- 存储策略参数定义
- 主键：id
- 唯一标识：param_id
- 关联：strategy_id

## 使用说明

### 1. 实体类说明

实体类使用 MyBatis Plus 的 `@TableName` 注解，JSON 字段使用 String 类型存储。

### 2. Service 接口

所有 Service 接口继承自 MyBatis Plus 的 `IService<T>`，提供基础的 CRUD 操作，同时定义业务特定的方法。

### 3. 枚举类型

- **StrategyType**: 策略类型（JAVA_CLASS, GROOVY_SCRIPT, PYTHON_SCRIPT, JAVASCRIPT）
- **StrategyStatus**: 策略状态（DRAFT, TESTING, ACTIVE, DEPRECATED, ARCHIVED）
- **Visibility**: 可见性（PRIVATE, TEAM, PUBLIC）
- **Frequency**: 运行频率（DAILY, HOURLY, MINUTELY, REALTIME, CUSTOM）
- **ChangeType**: 变更类型（CREATE, UPDATE, BUGFIX, ENHANCEMENT）

## 后续工作

1. 完成其他 Service 实现类（StrategyVersionServiceImpl、StrategyCategoryServiceImpl、StrategyParameterServiceImpl）
2. 创建 DTO 和 VO 类用于数据传输
3. 创建 Controller 类提供 REST API
4. 创建查询条件类（Query）用于复杂查询
5. 实现 JSON 字段的序列化/反序列化工具类（如果需要）

## 注意事项

- JSON 字段目前使用 String 类型存储，如需类型安全，可以创建工具类进行转换
- 时间字段使用 LocalDateTime 类型
- 所有实体类实现了 Serializable 接口
- 使用 Lombok 简化代码

