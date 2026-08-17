param(
  [int]$Port = 18789,
  [switch]$Dev,
  [switch]$Wait,
  [switch]$Background,
  [switch]$AuthOnly
)
$ErrorActionPreference = 'Stop'
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$preferredStateDir = "F:\openclawd\.openclaw-state"
$defaultStateDir = $null
if (Test-Path $preferredStateDir) {
  $defaultStateDir = $preferredStateDir
} else {
  $defaultStateDir = (Join-Path $repoRoot ".openclaw-state")
}
if (-not $env:OPENCLAW_STATE_DIR -or $env:OPENCLAW_STATE_DIR.Trim().Length -eq 0 -or -not (Test-Path $env:OPENCLAW_STATE_DIR)) {
  $env:OPENCLAW_STATE_DIR = $defaultStateDir
}
if (-not $env:OPENCLAW_CONFIG_PATH -or $env:OPENCLAW_CONFIG_PATH.Trim().Length -eq 0 -or -not (Test-Path $env:OPENCLAW_CONFIG_PATH)) {
  $env:OPENCLAW_CONFIG_PATH = (Join-Path $env:OPENCLAW_STATE_DIR "openclaw.json")
}
if (-not (Test-Path $env:OPENCLAW_CONFIG_PATH)) {
  $legacy = Join-Path $env:USERPROFILE ".openclaw\openclaw.json"
  if (Test-Path $legacy) {
    New-Item -ItemType Directory -Force -Path $env:OPENCLAW_STATE_DIR | Out-Null
    Copy-Item -Force $legacy $env:OPENCLAW_CONFIG_PATH
  }
}
$openclawRoot = "F:\openclawd\openclaw"
Set-Location $openclawRoot
$openclawEntry = Join-Path $openclawRoot "openclaw.mjs"
$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) { Write-Error "Node.js 未安装或不可用"; exit 1 }

function Stop-PortListeners([int[]]$Ports) {
  foreach ($p in $Ports) {
    $owners = @()
    try {
      $owners += (Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue).OwningProcess
    } catch {}
    $owners = $owners | Where-Object { $_ -and $_ -ne 0 } | Select-Object -Unique
    foreach ($pid in $owners) {
      try { Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue } catch {}
    }
  }
}

function Is-PortListening([int]$Port) {
  try {
    $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $c
  } catch {
    return $false
  }
}

function Ensure-DeepSeekAuthStore() {
  $key = $env:DEEPSEEK_API_KEY
  if (-not $key -or $key.Trim().Length -eq 0) {
    if ($Host -and $Host.UI -and $Host.UI.RawUI) {
      $secure = Read-Host -Prompt "Paste DEEPSEEK_API_KEY" -AsSecureString
      if ($secure) {
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try {
          $key = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        } finally {
          [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
        if ($key -and $key.Trim().Length -gt 0) {
          $env:DEEPSEEK_API_KEY = $key.Trim()
        }
      }
    }
    $key = $env:DEEPSEEK_API_KEY
    if (-not $key -or $key.Trim().Length -eq 0) {
      return
    }
  }
  $agentDir = Join-Path $env:OPENCLAW_STATE_DIR "agents\\main\\agent"
  New-Item -ItemType Directory -Force -Path $agentDir | Out-Null
  $authPath = Join-Path $agentDir "auth-profiles.json"
  $store = $null
  if (Test-Path $authPath) {
    try { $store = Get-Content -Path $authPath -Raw | ConvertFrom-Json } catch { $store = $null }
  }
  if (-not $store) {
    $store = [pscustomobject]@{ version = 1; profiles = [pscustomobject]@{}; order = [pscustomobject]@{} }
  }
  if (-not $store.profiles) { $store | Add-Member -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{}) -Force }
  if (-not $store.order) { $store | Add-Member -NotePropertyName order -NotePropertyValue ([pscustomobject]@{}) -Force }
  $store.version = 1

  $needs = $true
  try {
    $existing = $store.profiles."deepseek:manual"
    if ($existing -and $existing.provider -eq "deepseek" -and $existing.type -eq "token" -and $existing.token) {
      $needs = $false
    }
  } catch {}
  if ($needs) {
    $store.profiles | Add-Member -NotePropertyName "deepseek:manual" -NotePropertyValue ([pscustomobject]@{ type="token"; provider="deepseek"; token=$key }) -Force
    $store.order | Add-Member -NotePropertyName "deepseek" -NotePropertyValue @("deepseek:manual") -Force
    ($store | ConvertTo-Json -Depth 10) | Set-Content -Path $authPath -Encoding UTF8
    Write-Host ("DeepSeek auth stored for agent: {0}" -f $authPath)
  }
}

Ensure-DeepSeekAuthStore
if ($AuthOnly) { exit 0 }

function OpenGatewayDashboard([int]$Port) {
  try {
    $cfg = Get-Content -Path $env:OPENCLAW_CONFIG_PATH -Raw | ConvertFrom-Json
    $token = $cfg.gateway.auth.token
    if ($token) {
      Start-Process ("http://127.0.0.1:{0}/#token={1}" -f $Port, $token)
      return
    }
  } catch {}
  Start-Process ("http://127.0.0.1:{0}/" -f $Port)
}

if ($Dev) {
  $nodeArgs = @("$openclawEntry", "--dev", "gateway", "run", "--port", "$Port")
} else {
  $nodeArgs = @("$openclawEntry", "gateway", "run", "--port", "$Port")
}
Write-Host ("Starting OpenClaw Gateway: node " + ($nodeArgs -join " "))
$logsDir = Join-Path $env:OPENCLAW_STATE_DIR "logs"
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
$stdoutLog = Join-Path $logsDir "gateway.out.log"
$stderrLog = Join-Path $logsDir "gateway.err.log"
if (-not $Background) {
  Stop-PortListeners @($Port, ($Port + 2))
  OpenGatewayDashboard $Port
  Write-Host ("Logs: {0}" -f $stdoutLog)
  Write-Host ("Logs: {0}" -f $stderrLog)
  node @nodeArgs 2>&1 | Tee-Object -FilePath $stdoutLog
  exit 0
}

Stop-PortListeners @($Port, ($Port + 2))
$proc = Start-Process -FilePath "node" -ArgumentList $nodeArgs -WorkingDirectory $openclawRoot -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru

function Show-LogTail([string]$Path, [int]$Lines = 80) {
  if (Test-Path $Path) {
    Write-Host ("--- " + $Path + " (tail " + $Lines + ")")
    Get-Content -Path $Path -Tail $Lines
  } else {
    Write-Host ("--- missing: " + $Path)
  }
}

$maxWait = 30
$ok = $false
for ($i=0; $i -lt $maxWait; $i++) {
  if ($proc -and $proc.HasExited) { break }
  try {
    if (Is-PortListening $Port) { $ok = $true; break }
  } catch {}
  Start-Sleep -Seconds 1
}
if (-not $ok) {
  Write-Error "Gateway 未启动"
  if ($proc -and $proc.HasExited) {
    Write-Error ("Gateway 进程已退出 (exitCode=" + $proc.ExitCode + ")")
  }
  Show-LogTail $stderrLog 120
  Show-LogTail $stdoutLog 120
  try { if ($proc -and $proc.Id) { Stop-Process -Id $proc.Id -Force } } catch {}
  exit 1
}
Write-Host ("Logs: {0}" -f $stdoutLog)
Write-Host ("Logs: {0}" -f $stderrLog)
try {
  $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 5 -Property LocalAddress,LocalPort,OwningProcess
  if ($listeners) {
    Write-Host "--- Listening sockets:"
    $listeners | Format-Table -AutoSize | Out-Host
  }
} catch {}

if ($Wait) {
  Write-Host "Gateway is running. Keep this window open (Ctrl+C to stop)."
  while ($true) {
    if ($proc -and $proc.HasExited) {
      Write-Error ("Gateway 进程已退出 (exitCode=" + $proc.ExitCode + ")")
      Show-LogTail $stderrLog 200
      Show-LogTail $stdoutLog 200
      exit 1
    }
    Start-Sleep -Seconds 3
  }
}
