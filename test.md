{
  "meta": {
    "lastTouchedVersion": "2026.3.8",
    "lastTouchedAt": "2026-03-14T17:29:10.490Z"
  },
  "wizard": {
    "lastRunAt": "2026-03-14T17:10:51.105Z",
    "lastRunVersion": "2026.3.8",
    "lastRunCommand": "doctor",
    "lastRunMode": "local"
  },
  "models": {
    "providers": {
      "deepseek": {
        "baseUrl": "https://api.deepseek.com",
        "apiKey": "sk-2259a258f3ee4719ad8aeb03bcdb0e41",
        "api": "openai-completions",
        "models": [
          {
            "id": "deepseek-chat",
            "name": "DeepSeek Chat"
          },
          {
            "id": "deepseek-reasoner",
            "name": "DeepSeek Reasoner"
          }
        ]
      }
    }
  },
  "agents": {
    "defaults": {
      "model": {
        "primary": "deepseek/deepseek-reasoner"
      },
      "models": {
        "anthropic/deepseek-reasoner": {},
        "deepseek/deepseek-reasoner": {
          "alias": "deepseek-reasoner"
        }
      },
      "workspace": "/Users/huangxuean/.openclaw/workspace",
      "compaction": {
        "mode": "safeguard"
      }
    },
    "list": [
      {
        "id": "main",
        "identity": {
          "name": "交易员(埃德·塞柯塔)",
          "emoji": "💹"
        }
      }
    ]
  },
  "commands": {
    "native": "auto",
    "nativeSkills": "auto",
    "restart": true,
    "ownerDisplay": "raw"
  },
  "gateway": {
    "port": 18789,
    "mode": "local",
    "bind": "lan",
    "controlUi": {
      "allowedOrigins": [
        "http://192.168.1.17:18789"
      ],
      "allowInsecureAuth": true,
      "dangerouslyDisableDeviceAuth": true
    },
    "auth": {
      "mode": "token",
      "token": "8008e88cb729154946ed26f78622cb4eb222f9a04eb0aa2a"
    },
    "tailscale": {
      "mode": "off",
      "resetOnExit": false
    }
  }
}

{
  "meta": {
    "lastTouchedVersion": "2026.3.8",
    "lastTouchedAt": "2026-03-14T17:29:10.490Z"
  },
  "wizard": {
    "lastRunAt": "2026-03-14T17:10:51.105Z",
    "lastRunVersion": "2026.3.8",
    "lastRunCommand": "doctor",
    "lastRunMode": "local"
  },
  "models": {
    "providers": {
      "deepseek": {
        "baseUrl": "https://api.deepseek.com",
        "apiKey": "sk-2259a258f3ee4719ad8aeb03bcdb0e41",
        "api": "openai-completions",
        "models": [
          {
            "id": "deepseek-chat",
            "name": "DeepSeek Chat"
          },
          {
            "id": "deepseek-reasoner",
            "name": "DeepSeek Reasoner"
          }
        ]
      }
    }
  },
  "agents": {
    "defaults": {
      "model": {
        "primary": "deepseek/deepseek-reasoner"
      },
      "models": {
        "anthropic/deepseek-reasoner": {},
        "deepseek/deepseek-reasoner": {
          "alias": "deepseek-reasoner"
        }
      },
      "workspace": "/Users/huangxuean/.openclaw/workspace",
      "compaction": {
        "mode": "safeguard"
      }
    },
    "list": [
      {
        "id": "main",
        "identity": {
          "name": "交易员(埃德·塞柯塔)",
          "emoji": "💹"
        }
      }
    ]
  },
  "commands": {
    "native": "auto",
    "nativeSkills": "auto",
    "restart": true,
    "ownerDisplay": "raw"
  },
  "gateway": {
    "port": 18789,
    "mode": "local",
    "bind": "lan",
    "controlUi": {
      "allowedOrigins": [
        "http://192.168.1.17:18789"
      ],
      "allowInsecureAuth": true,
      "dangerouslyDisableDeviceAuth": true
    },
    "auth": {
      "mode": "token",
      "token": "8008e88cb729154946ed26f78622cb4eb222f9a04eb0aa2a"
    },
    "tailscale": {
      "mode": "off",
      "resetOnExit": false
    }
  }
}


如果你希望我换成正确的路径（例如启用 /v1/responses 或使用 /v1/chat/completions），告诉我你期望的 API 路由和模型名，我立刻再抓一次流式输出。

****

New-NetFirewallRule `
  -DisplayName "OpenClaw Allowlist 18789 (192.168.1.23)" `
-Direction Inbound `
  -Action Allow `
-Protocol TCP `
  -LocalPort 18789 `
-RemoteAddress @("192.168.1.23","127.0.0.1") `
-Profile Private
PS C:\Users\zhouren> New-NetFirewallRule `
>>   -DisplayName "OpenClaw Allowlist 18789 (192.168.1.23)" `
>> -Direction Inbound `
>>   -Action Allow `
>> -Protocol TCP `
>>   -LocalPort 18789 `
>> -RemoteAddress @("192.168.1.23","127.0.0.1") `
>> -Profile Private
New-NetFirewallRule : 拒绝访问。
所在位置 行:1 字符: 1
+ New-NetFirewallRule `
+ ~~~~~~~~~~~~~~~~~~~~~
    + CategoryInfo          : PermissionDenied: (MSFT_NetFirewallRule:root/standardcimv2/MSFT_NetFirewallRule) [New-Ne
   tFirewallRule], CimException
    + FullyQualifiedErrorId : Windows System Error 5,New-NetFirewallRule
