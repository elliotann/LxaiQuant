$ErrorActionPreference = "Stop"

$configRoot = "F:\clawd\config"
$stateDir = Join-Path $configRoot "state"
$logsDir = Join-Path $configRoot "logs"
$configPath = Join-Path $configRoot "openclaw.json"

New-Item -ItemType Directory -Force -Path $stateDir, $logsDir | Out-Null

$existingToken = $null
if (Test-Path $configPath) {
  try {
    $existing = Get-Content -Raw $configPath | ConvertFrom-Json
    $existingToken = $existing.gateway.auth.token
  } catch {
    $existingToken = $null
  }
}

function New-Token([int]$length = 40) {
  $chars = @()
  $chars += 48..57
  $chars += 97..122
  -join ($chars | Get-Random -Count $length | ForEach-Object { [char]$_ })
}

$token = if ($existingToken) { [string]$existingToken } else { New-Token 40 }

$config = [ordered]@{
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
      token = $token
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
  plugins = [ordered]@{
    allow = @()
    load = [ordered]@{ paths = @() }
    entries = [ordered]@{}
  }
}

$config | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 $configPath

Write-Output $token

