$ErrorActionPreference = "Stop"

$configPath = "F:\clawd\config\openclaw.json"

$existingToken = $null
if (Test-Path $configPath) {
  try {
    $existing = [System.IO.File]::ReadAllText($configPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
    $existingToken = $existing.gateway.auth.token
  } catch {
    $existingToken = $null
  }
}

if (-not $existingToken) {
  $existingToken = -join ((48..57) + (97..122) | Get-Random -Count 40 | ForEach-Object { [char]$_ })
}

$cfg = [ordered]@{
  meta = [ordered]@{
    lastTouchedVersion = "reset"
    lastTouchedAt = (Get-Date).ToUniversalTime().ToString("o")
  }
  commands = [ordered]@{
    native = "auto"
    nativeSkills = "auto"
    restart = $true
    ownerDisplay = "raw"
  }
  session = [ordered]@{
    dmScope = "per-channel-peer"
  }
  gateway = [ordered]@{
    port = 18789
    mode = "local"
    bind = "loopback"
    auth = [ordered]@{
      mode = "token"
      token = [string]$existingToken
    }
    tailscale = [ordered]@{
      mode = "off"
      resetOnExit = $false
    }
    http = [ordered]@{
      endpoints = [ordered]@{
        responses = [ordered]@{
          enabled = $true
        }
      }
    }
  }
  logging = [ordered]@{
    level = "info"
    consoleLevel = "silent"
    file = "F:\clawd\config\logs\openclaw.log"
    maxFileBytes = 524288000
  }
  auth = [ordered]@{
    profiles = [ordered]@{
      "deepseek:manual" = [ordered]@{
        provider = "deepseek"
        mode = "token"
      }
    }
  }
  models = [ordered]@{
    mode = "merge"
    providers = [ordered]@{
      deepseek = [ordered]@{
        baseUrl = "https://api.deepseek.com/v1"
        auth = "token"
        api = "openai-completions"
        models = @(
          [ordered]@{
            id = "deepseek-chat"
            name = "DeepSeek Chat"
            reasoning = $false
            input = @("text")
            cost = [ordered]@{ input = 0; output = 0; cacheRead = 0; cacheWrite = 0 }
            contextWindow = 128000
            maxTokens = 8192
          },
          [ordered]@{
            id = "deepseek-reasoner"
            name = "DeepSeek Reasoner"
            reasoning = $true
            input = @("text")
            cost = [ordered]@{ input = 0; output = 0; cacheRead = 0; cacheWrite = 0 }
            contextWindow = 128000
            maxTokens = 8192
          }
        )
      }
    }
  }
  agents = [ordered]@{
    defaults = [ordered]@{
      model = [ordered]@{ primary = "deepseek/deepseek-reasoner" }
    }
  }
  plugins = [ordered]@{
    allow = @("quant-bridge")
    load = [ordered]@{
      paths = @("F:\project\lenzeto\.openclaw-config\openclaw-quant-bridge")
    }
    entries = [ordered]@{
      "quant-bridge" = [ordered]@{
        enabled = $true
        config = [ordered]@{
          baseUrl = "http://192.168.1.23:8118"
          token = "oc-bridge-123456"
          defaultAccountId = "2020163742895513602"
          defaultSymbol = "BTC-USDT-SWAP"
          defaultInterval = "3m"
        }
      }
    }
  }
}

$out = $cfg | ConvertTo-Json -Depth 60
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($configPath, $out + [Environment]::NewLine, $utf8NoBom)

Write-Output "OK"
