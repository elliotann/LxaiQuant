# AI Task 模块

## 📋 概述

`ai-task` 是一个专门用于运行 XXL-JOB 定时任务的 Spring Boot 应用。它将所有定时任务从业务模块中分离出来，统一管理和执行。

## 🎯 设计目标

1. **职责分离**：将定时任务从业务模块（如 ai-quant）中分离
2. **统一管理**：所有定时任务集中在一个应用中
3. **独立部署**：可以独立部署和扩展
4. **易于维护**：任务代码集中，便于维护和监控

## 🏗️ 项目结构

```
ai-task/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/chain/ai/trade/task/
│   │   │       ├── AiTaskApplication.java      # 主应用类
│   │   │       ├── config/
│   │   │       │   └── XxlJobConfig.java       # XXL-JOB 配置
│   │   │       └── jobhandler/                 # 任务处理器目录
│   │   │           └── SampleJobHandler.java   # 示例任务
│   │   └── resources/
│   │       └── application.yml                 # 应用配置
│   └── test/
└── pom.xml
```

## 🔧 配置说明

### 1. XXL-JOB 配置

在 `application.yml` 中配置：

```yaml
xxl:
  job:
    admin:
      addresses: http://localhost:9002/xxl-job-admin
    executor:
      appname: ai-task-executor
      port: 9998
      logpath: logs/xxl-job/jobhandler
      logretentiondays: 30
    accessToken: ai-task-token
```

**重要配置项：**
- `appname`: 执行器名称，需要在 XXL-JOB 管理后台注册
- `port`: 执行器端口，确保不与其他应用冲突（默认 9998）
- `accessToken`: 访问令牌，需要与管理后台配置一致

### 2. 数据库配置

配置 MySQL 数据库连接：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/lenzeto?...
    username: root
    password: root
```

## 🚀 使用方法

### 1. 启动应用

```bash
cd ai-task
mvn spring-boot:run
```

或者打包后运行：

```bash
mvn clean package
java -jar target/ai-task-0.0.1-SNAPSHOT.jar
```

### 2. 在 XXL-JOB 管理后台注册执行器

1. 访问 XXL-JOB 管理后台：http://localhost:9002/xxl-job-admin
2. 进入"执行器管理"
3. 添加执行器：
   - **AppName**: `ai-task-executor`
   - **名称**: `AI任务执行器`
   - **注册方式**: 自动注册
   - **机器地址**: 自动获取

### 3. 配置任务

在管理后台"任务管理"中添加任务：

#### 示例任务1：简单任务
- **任务描述**: 示例任务
- **Cron表达式**: `0 */5 * * * ?` (每5分钟执行)
- **运行模式**: BEAN
- **JobHandler**: `sampleJob`
- **执行器**: `ai-task-executor`

#### 示例任务2：带参数任务
- **任务描述**: 带参数示例任务
- **Cron表达式**: `0 0 */1 * * ?` (每小时执行)
- **运行模式**: BEAN
- **JobHandler**: `sampleJobWithParam`
- **任务参数**: `{"key":"value"}` (可选)
- **执行器**: `ai-task-executor`

## 📝 开发指南

### 添加新的任务处理器

1. 在 `jobhandler` 包下创建任务处理器类：

```java
@Slf4j
@Component
public class MyTaskHandler {

    @XxlJob("myTask")
    public void executeMyTask() {
        try {
            String param = XxlJobHelper.getJobParam();
            log.info("执行我的任务，参数：{}", param);

            // 任务逻辑
            // ...

            XxlJobHelper.handleSuccess("任务执行成功");
        } catch (Exception e) {
            log.error("任务执行失败", e);
            XxlJobHelper.handleFail("任务执行失败: " + e.getMessage());
        }
    }
}
```

2. 在 XXL-JOB 管理后台配置对应的任务

### 任务类型

#### 1. 简单任务
```java
@XxlJob("taskName")
public void executeTask() {
    // 任务逻辑
}
```

#### 2. 带参数任务
```java
@XxlJob("taskName")
public void executeTask() {
    String param = XxlJobHelper.getJobParam();
    // 解析参数并执行任务
}
```

#### 3. 分片任务
```java
@XxlJob("taskName")
public void executeShardingTask() {
    int shardIndex = XxlJobHelper.getShardIndex();
    int shardTotal = XxlJobHelper.getShardTotal();
    // 根据分片参数处理数据
}
```

### 常用 API

- `XxlJobHelper.getJobParam()`: 获取任务参数
- `XxlJobHelper.getShardIndex()`: 获取当前分片序号
- `XxlJobHelper.getShardTotal()`: 获取总分片数
- `XxlJobHelper.handleSuccess(String msg)`: 标记任务成功
- `XxlJobHelper.handleFail(String msg)`: 标记任务失败
- `XxlJobHelper.log(String log)`: 记录日志（可在管理后台查看）

## 📦 依赖说明

### 核心依赖
- `spring-boot-starter-web`: Web 支持
- `xxl-job-core`: XXL-JOB 核心库
- `mybatis-plus-spring-boot4-starter`: MyBatis Plus
- `mysql-connector-j`: MySQL 驱动

### 业务模块依赖
- `ai-common`: 公共模块
- `ai-data`: 数据模块
- `ai-signal`: 信号模块
- `ai-order`: 订单模块
- `ai-engine`: 引擎模块
- `ai-strategy`: 策略模块
- `ai-member`: 会员模块

## 🔍 监控和日志

### 日志位置
- **应用日志**: `logs/ai-task.log`
- **XXL-JOB 执行日志**: `logs/xxl-job/jobhandler/`

### 监控方式
1. **XXL-JOB 管理后台**: 查看任务执行历史、状态、日志
2. **应用日志**: 查看详细的任务执行日志
3. **执行器状态**: 在管理后台查看执行器在线状态

## ⚠️ 注意事项

1. **端口冲突**: 确保执行器端口（默认 9998）不与其他应用冲突
2. **执行器名称**: 执行器名称（appname）需要在管理后台唯一
3. **访问令牌**: accessToken 需要与管理后台配置一致
4. **网络连接**: 确保执行器能访问管理后台地址
5. **日志清理**: 定期清理日志文件，避免磁盘空间不足

## 🔄 与 ai-quant 的关系

- **ai-quant**: 业务应用，包含业务逻辑和 API
- **ai-task**: 定时任务应用，专门执行定时任务

**迁移建议：**
- 将 ai-quant 中的定时任务逐步迁移到 ai-task
- 保持 ai-quant 专注于业务逻辑
- ai-task 专注于定时任务执行

## 📚 相关文档

- [XXL-JOB 官方文档](https://www.xuxueli.com/xxl-job/)
- [XXL-JOB GitHub](https://github.com/xuxueli/xxl-job)
- [ai-quant XXLJOB_README.md](../ai-quant/XXLJOB_README.md)

