# AI Backtest Module

量化回测系统核心库，提供回测任务管理、结果计算和报告生成功能。

## 功能特性

- ✅ 回测任务管理（创建、执行、状态跟踪）
- ✅ 绩效指标计算（夏普比率、最大回撤、胜率等）
- ✅ 结果数据存储和查询
- ✅ 报告生成和管理
- ✅ 完整的数据库表结构和索引优化

## 🚨 常见问题排查

### 1. 编译错误

#### 问题：`ObjectMapper` 注入失败
```
No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'
```

**解决方案：**
- 确保 `MyBatisPlusConfig` 中的 `ObjectMapper` bean 已正确配置
- 检查 Spring Boot 版本兼容性

#### 问题：依赖冲突
```
java.lang.ClassNotFoundException: com.fasterxml.jackson.databind.ObjectMapper
```

**解决方案：**
- 检查 `pom.xml` 中的 Jackson 依赖版本
- 确保与父项目的依赖版本兼容

### 2. 数据库连接问题

#### 问题：表不存在
```
Table 'backtest_task' doesn't exist
```

**解决方案：**
- 执行 `src/main/resources/db/init.sql` 中的建表语句
- 确保数据库用户有建表权限

#### 问题：连接超时
```
Communications link failure
```

**解决方案：**
- 检查数据库服务器是否运行
- 验证 `application.yml` 中的数据库配置
- 确认数据库端口和防火墙设置

### 3. IntelliJ IDEA 配置问题

#### 问题：源根重复
```
源根 'xxx' 在模块 'ai-backtest' 中重复
```

**解决方案：**
- 删除模块目录下的 `.idea` 文件夹
- 确保只在根目录使用 `.idea` 配置
- 重新导入 Maven 项目

### 4. Spring 上下文问题

#### 问题：Bean 注入失败
```
Could not autowire. No beans of 'BacktestService' type found
```

**解决方案：**
- 检查 `@Service` 注解是否正确添加
- 确保包扫描路径包含所有组件
- 验证 Spring Boot 启动类配置

### 5. JSON 处理问题

#### 问题：日期序列化失败
```
Java 8 date/time type `java.time.LocalDate` not supported
```

**解决方案：**
- 确保 `ObjectMapper` 配置了 `JavaTimeModule`
- 检查 Jackson 版本是否支持 Java 8 时间类型

#### 问题：BigDecimal 类型转换错误
```
不兼容的类型: double无法转换为java.math.BigDecimal
```

**解决方案：**
- 使用 `BigDecimal.valueOf(double)` 而不是直接赋值
- 示例：`BigDecimal.valueOf(0.156789)` 而不是 `0.156789`

#### 问题：List<String> 转 String[] 类型错误
```
不兼容的类型: java.util.List<java.lang.String>无法转换为java.lang.String[]
```

**解决方案：**
- 使用 `list.toArray(new String[0])` 转换 List 为数组
- 使用 `Arrays.asList(array)` 转换数组为 List

### 6. 测试问题

#### 问题：测试无法运行
```
java.lang.IllegalStateException: Unable to find a @SpringBootConfiguration
```

**解决方案：**
- 由于此模块为 JAR 包，没有主启动类，测试需要外部配置
- 建议在集成到其他模块后进行测试

### 7. 模块依赖问题

#### 问题：无法找到依赖模块
```
Could not find artifact com.chain.ai:ai-common:jar:0.0.1-SNAPSHOT
```

**解决方案：**
- 确保所有依赖模块已正确安装到本地仓库
- 运行 `mvn clean install` 安装所有模块

## 🔧 配置验证

### 检查配置完整性

运行以下命令验证配置：

```bash
# 1. 检查项目结构
find src -name "*.java" | wc -l

# 2. 验证依赖
mvn dependency:tree

# 3. 检查编译
mvn clean compile

# 4. 运行测试（如果有）
mvn test
```

### 数据库验证

```sql
-- 检查表是否创建成功
SHOW TABLES LIKE 'backtest_%';

-- 检查表结构
DESCRIBE backtest_task;
DESCRIBE backtest_result;
DESCRIBE backtest_report;
```

## 📞 技术支持

如果遇到其他问题，请：

1. 检查控制台错误日志
2. 验证配置文件语法
3. 确认数据库连接正常
4. 查看 Spring Boot 启动日志

## 🔄 版本兼容性

- **Java**: 21+
- **Spring Boot**: 4.0.1
- **MyBatis Plus**: 3.5.3.1
- **MySQL**: 8.0+
- **Jackson**: 2.15+

## 技术栈

- Spring Boot (非Web应用)
- MyBatis Plus
- MySQL
- Jackson JSON处理
- Ta4j 交易库
- Lombok

## 核心接口

### BacktestTaskService

```java
public interface BacktestTaskService {
    // 创建回测任务
    BacktestTaskDTO createBacktestTask(BacktestTaskDTO taskDTO);

    // 执行回测任务
    boolean executeBacktestTask(String taskId);

    // 获取回测结果
    BacktestResultDTO getBacktestResult(String taskId);

    // 更新任务状态
    boolean updateTaskStatus(String taskId, String status, String errorMessage);

    // 删除回测任务
    boolean deleteBacktestTask(String taskId);
}
```

## 使用方式

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.chain.ai</groupId>
    <artifactId>ai-backtest</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置数据库

确保application.yml中包含数据库配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_db
    username: your_username
    password: your_password
```

### 3. 初始化数据库

执行 `src/main/resources/db/init.sql` 中的建表语句。

### 4. 使用示例

```java
@Autowired
private BacktestTaskService backtestService;

// 创建回测任务
BacktestTaskDTO task = BacktestTaskDTO.builder()
    .strategyName("均线策略")
    .startDate(LocalDate.of(2023, 1, 1))
    .endDate(LocalDate.of(2023, 12, 31))
    .initialCapital(BigDecimal.valueOf(1000000))
    .config(Map.of("fastPeriod", 20, "slowPeriod", 60))
    .build();

BacktestTaskDTO createdTask = backtestService.createBacktestTask(task);

// 执行回测
boolean success = backtestService.executeBacktestTask(createdTask.getTaskId());

// 获取结果
BacktestResultDTO result = backtestService.getBacktestResult(createdTask.getTaskId());
```

## 数据库表结构

### backtest_task (回测任务表)
- 存储任务配置和状态信息
- 支持JSON配置参数
- 任务状态机管理

### backtest_result (回测结果表)
- 存储完整的绩效指标
- 支持曲线数据存储
- 外键关联到任务表

### backtest_report (回测报告表)
- 存储分析报告和用户交互
- 支持图表配置
- 用户偏好和统计

## 开发说明

### 任务状态流转
```
PENDING → RUNNING → COMPLETED
    ↓         ↓         ↓
 CANCELLED  FAILED   (成功)
```

### 性能指标体系
- 收益类：总收益率、年化收益率
- 风险类：最大回撤、波动率、夏普比率
- 交易类：总交易次数、胜率、盈亏比
- 其他：Alpha、Beta、信息比率等

### 注意事项
- 此模块作为jar包提供，不包含Web接口
- 需要外部模块提供Web接口调用
- 数据库表需要预先创建
- 回测逻辑目前为模拟实现，需要接入实际的回测引擎
