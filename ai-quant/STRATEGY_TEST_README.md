# 实盘策略执行测试指南

## 🎯 测试目的

通过调用真实的 **启动实盘交易策略接口** 来验证整个实盘交易流程是否可行，包括：
- 策略注册 (`/api/live-trading/register`)
- 实盘交易启动 (`/api/live-trading/start`) ⭐ **核心测试**
- 策略运行状态验证
- 异步执行机制验证

## 📋 策略架构说明

### ta4j Rule 体系集成
现在**止损也作为 ta4j 的 Rule 体系一部分**，实现完整的策略规则：

```java
// 买入规则：RSI < 30
Rule buyRule = new UnderIndicatorRule(rsi, 30);

// 卖出规则：技术信号 OR 止损信号
Rule technicalExit = new OverIndicatorRule(rsi, 70);  // RSI > 70
Rule stopLoss = new StopLossRule(series, 0.05);       // 5%止损
Rule sellRule = new OrRule(technicalExit, stopLoss);   // 复合规则

Strategy strategy = new BaseStrategy(buyRule, sellRule);
```

### 优势
- ✅ **统一规则系统**: 所有交易逻辑都通过 ta4j Rule 管理
- ✅ **回测一致性**: 实盘和回测使用完全相同的规则
- ✅ **扩展性**: 可轻松添加更多类型的退出规则
- ✅ **性能优化**: ta4j 的规则引擎经过优化

### TradingStrategyParams 参数对象
```java
{
  symbol: "BTC-USDT",        // 交易对
  strategyType: "RSI",       // 策略类型 (默认RSI)
  interval: "1h",            // 时间间隔
  additionalParams: {...}    // 其他参数
}
```

## 🧪 测试步骤

### 1. 启动后端服务
```bash
cd ai-quant
mvn spring-boot:run
```

### 2. 启动前端服务
```bash
cd ver-manage-web
npm run serve
```

### 3. 访问实盘交易页面
```
http://localhost:8080/backtest.html
```
切换到"实盘交易"标签页

### 4. 点击测试按钮
点击页面顶部的 **🧪 测试策略执行** 按钮

## 📊 测试流程

### 前端执行流程
1. **生成测试策略名称** - `test-rsi-{timestamp}`
2. **注册RSI策略** - 调用 `/api/live-trading/register/{strategyName}?strategyType=rsi`
3. **启动实盘交易** - 调用真实的 `/api/live-trading/start` 接口 ⭐ **核心测试**
4. **验证执行状态** - 检查策略是否出现在活跃策略列表中

### 测试验证内容
- ✅ **策略注册API** - 是否能成功注册策略
- ✅ **实盘交易启动API** - 是否能成功启动实盘交易 ⭐ **重点验证**
- ✅ **参数传递** - 交易对、策略类型等参数是否正确传递
- ✅ **策略状态管理** - 是否能正确管理策略生命周期
- ✅ **异步执行机制** - 是否能正常启动后台线程

### 前端显示结果
- ✅ **成功步骤** - 绿色显示
- ❌ **失败步骤** - 红色显示
- 📝 **详细日志** - 每个步骤的时间戳和消息
- 🔍 **实时验证** - 通过重新加载策略列表验证执行状态

## 🔍 测试结果解读

### 成功情况
```
✅ 开始测试: 正在执行策略执行测试...
✅ 策略注册: 测试策略已注册
✅ 策略启动: 测试策略已启动
✅ 执行验证: 策略正在运行中
```

### 失败情况
```
❌ 测试失败: 后端测试失败: {具体错误信息}
```

## 🚨 常见问题

### 1. 网络连接失败
```
错误: Failed to fetch
解决: 检查后端服务是否运行在 http://localhost:8080
```

### 2. 策略注册失败
```
错误: 策略注册失败
解决: 检查 ta4j 依赖是否正确加载
```

### 3. 策略启动失败
```
错误: 策略启动失败
解决: 检查策略实现类是否存在
```

## 🔧 调试技巧

### 查看后端日志
```bash
# 在 IDEA 中查看控制台输出
# 或者查看日志文件
tail -f ai-quant/logs/spring.log
```

### 检查前端网络请求
```javascript
// 在浏览器开发者工具的 Network 面板中查看
// 查找 /api/live-trading/test-execution 请求
```

### 手动测试API
```bash
# 直接调用测试接口
curl -X POST http://localhost:8080/api/live-trading/test-execution
```

## 📋 测试通过的标准

**API调用成功**:
- ✅ 策略注册API返回 `success: true`
- ✅ **实盘交易启动API返回 `success: true`** ⭐ **核心验证**
- ✅ 策略出现在活跃策略列表中

**前端显示**: 4个绿色成功的测试步骤
1. 生成策略名称 ✅
2. 注册策略 ✅
3. **启动实盘交易** ✅ ⭐ **调用真实接口**
4. 验证执行状态 ✅

## 🎉 测试成功后

一旦测试通过，就可以放心地：

1. **使用完整功能** - 注册自定义策略
2. **启动实盘交易** - 使用真实的交易参数
3. **监控策略运行** - 查看实时状态和统计信息

## 📞 技术支持

如果测试失败，请按以下顺序排查：

1. **检查服务状态** - 确认前后端服务都正常运行
2. **查看错误日志** - 检查控制台和网络请求
3. **验证配置** - 确保数据库和依赖配置正确
4. **重启服务** - 有时重启可以解决临时问题

---

**🎯 通过测试验证，确认真实交易环境下的策略执行流程完全可行！**</contents>
</xai:function_call="file">ai-quant/STRATEGY_TEST_README.md
