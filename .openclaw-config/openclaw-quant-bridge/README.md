## OpenClaw Quant Bridge

### 作用

为 OpenClaw Gateway 增加一组可调用工具，用于访问本地量化系统接口（信号/汇总/下单/平仓）。

### 前置条件

- 量化系统后端已启动（默认 `http://127.0.0.1:8118`）
- OpenClaw Gateway 已启动

### 启用方式（openclaw.json）

在 OpenClaw 的 `openclaw.json` 中加入（示例）：

```json
{
  "plugins": {
    "load": {
      "paths": ["F:/project/lenzeto/.openclaw-config/openclaw-quant-bridge"]
    },
    "entries": {
      "quant-bridge": {
        "enabled": true,
        "config": {
          "baseUrl": "http://127.0.0.1:8118",
          "token": "",
          "defaultAccountId": "",
          "defaultSymbol": "BTCUSDT",
          "defaultInterval": "3m"
        }
      }
    }
  }
}
```

说明：

- `token` 对应量化系统的 `OPENCLAW_BRIDGE_TOKEN` 环境变量；若量化侧未设置该变量，可留空。
- `defaultAccountId` 可选；不填时下单/平仓需要显式传 `accountId`。

