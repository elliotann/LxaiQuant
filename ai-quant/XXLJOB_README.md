# XXLJOB 分布式任务调度配置指南

## 📋 概述

本项目已集成 XXLJOB 分布式任务调度框架，用于执行定时任务、实盘交易策略等后台任务。

## 🔧 配置说明

### 1. Maven 依赖

已在 `pom.xml` 中添加 XXLJOB 核心依赖：

```xml
<dependency>
    <groupId>com.xuxueli</groupId>
    <artifactId>xxl-job-core</artifactId>
    <version>2.4.1</version>
</dependency>
```

### 2. 应用配置 (application.yml)

```yaml
# XXLJOB 配置
xxl:
  job:
    admin:
      # XXLJOB 管理后台地址
      addresses: http://127.0.0.1:8080/xxl-job-admin
    executor:
      # 执行器名称
      appname: ai-quant-executor
      # 执行器地址（为空时自动注册）
      address:
      # 执行器IP
      ip:
      # 执行器端口号
      port: 9999
      # 执行器日志路径
      logpath: logs/xxl-job/jobhandler
      # 日志保留天数
      logretentiondays: 30
    accessToken: default_token
```

### 3. Java 配置类 (XxlJobConfig.java)

自动配置 XXLJOB 执行器，读取配置文件中的参数进行初始化。

## 🚀 使用方法

### 1. 启动 XXLJOB 管理后台

1. 下载 XXLJOB 管理后台：
   ```bash
   git clone https://github.com/xuxueli/xxl-job.git
   cd xxl-job/xxl-job-admin
   ```

2. 启动管理后台（默认端口 8080）：
   ```bash
   mvn spring-boot:run
   ```

3. 访问管理后台：http://localhost:8080/xxl-job-admin

### 2. 注册执行器

启动 ai-quant 应用后，执行器会自动注册到管理后台。

### 3. 配置任务

在管理后台中配置以下任务：

#### 实盘交易策略任务
- **任务名称**: liveTradingStrategyJob
- **Cron 表达式**: 0 */5 * * * ? (每5分钟执行)
- **执行器**: ai-quant-executor
- **任务参数**: ETH-USDT-SWAP=rsi (交易对=策略类型)

#### 停止实盘交易任务
- **任务名称**: stopLiveTradingJob
- **Cron 表达式**: 根据需要配置
- **执行器**: ai-quant-executor

#### 数据同步任务
- **任务名称**: dataSyncJob
- **Cron 表达式**: 0 0 */1 * * ? (每小时执行)
- **执行器**: ai-quant-executor

## 📝 开发指南

### 添加新的任务处理器

1. 创建任务处理器类：

```java
@Component
public class MyJobHandler {

    @XxlJob("myJob")
    public ReturnT<String> executeMyJob(String param) {
        try {
            // 任务逻辑
            log.info("执行我的任务，参数：{}", param);
            return ReturnT.SUCCESS;
        } catch (Exception e) {
            log.error("任务执行失败", e);
            return ReturnT.FAIL;
        }
    }
}
```

2. 在管理后台配置对应的任务。

### 任务参数说明

- 任务方法参数类型为 `String`，可传递 JSON 或简单字符串
- 返回值使用 `ReturnT<String>` 类型
- 成功返回 `ReturnT.SUCCESS`，失败返回 `ReturnT.FAIL`

## 🔍 监控和日志

- **执行器日志**: `logs/xxl-job/jobhandler/`
- **管理后台**: 查看任务执行历史和状态
- **应用日志**: 包含详细的任务执行日志

## ⚠️ 注意事项

1. 确保 XXLJOB 管理后台正常运行
2. 检查网络连接，确保执行器能访问管理后台
3. 合理配置任务执行频率，避免资源浪费
4. 定期清理日志文件

## 📚 相关链接

- [XXLJOB 官方文档](https://www.xuxueli.com/xxl-job/)
- [XXLJOB GitHub](https://github.com/xuxueli/xxl-job)
