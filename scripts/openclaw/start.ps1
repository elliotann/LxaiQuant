param(
  [int]$Port = 18789,
  [string]$OpenClawRoot = $env:OPENCLAW_ROOT,
  [string]$StateDir,
  [string]$ConfigPath,
  [int]$WaitForPortSeconds = 180,
  [switch]$VerboseConsole,
  [switch]$Restart,
  [switch]$IsolateState,
  [switch]$Dev,
  [switch]$Background,
  [switch]$Wait
)

$ErrorActionPreference = 'Stop'

function Resolve-RepoRoot() {
  return Resolve-Path (Join-Path $PSScriptRoot "..\..")
}

function Find-OpenClawRoot([string]$RootFromArg) {
  $candidates = New-Object System.Collections.Generic.List[string]
  if ($RootFromArg -and $RootFromArg.Trim().Length -gt 0) { $candidates.Add($RootFromArg.Trim()) }
  if ($env:OPENCLAW_ROOT -and $env:OPENCLAW_ROOT.Trim().Length -gt 0) { $candidates.Add($env:OPENCLAW_ROOT.Trim()) }
  $candidates.Add("F:\openclawd\openclaw")
  $candidates.Add((Join-Path (Resolve-RepoRoot) "openclaw"))
  $candidates.Add((Join-Path $env:USERPROFILE "openclaw"))
  foreach ($c in $candidates) {
    try {
      if ($c -and (Test-Path $c)) {
        $entry = Join-Path $c "openclaw.mjs"
        if (Test-Path $entry) { return (Resolve-Path $c).Path }
      }
    } catch {}
  }
  return $null
}

function Ensure-Dir([string]$Path) {
  if (-not (Test-Path $Path)) {
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
  }
}

function New-Token([int]$Length = 40) {
  $chars = @()
  $chars += 48..57
  $chars += 97..122
  -join ($chars | Get-Random -Count $Length | ForEach-Object { [char]$_ })
}

function Ensure-OpenClawConfig([string]$CfgPath, [int]$PortValue) {
  if (Test-Path $CfgPath) { return }
  $token = New-Token 40
  $cfg = [ordered]@{
    meta = [ordered]@{
      lastTouchedVersion = "lenzeto-script"
      lastTouchedAt = (Get-Date).ToUniversalTime().ToString("o")
    }
    commands = [ordered]@{
      native = "auto"
      nativeSkills = "auto"
      restart = $true
      ownerDisplay = "raw"
    }
    gateway = [ordered]@{
      port = $PortValue
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
          responses = [ordered]@{ enabled = $true }
        }
      }
    }
  }
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($CfgPath, ($cfg | ConvertTo-Json -Depth 30) + [Environment]::NewLine, $utf8NoBom)
}

function Try-Resolve-OpenClawLogFile([string]$CfgPath) {
  try {
    if (-not (Test-Path $CfgPath)) { return $null }
    $cfg = Get-Content -Path $CfgPath -Raw | ConvertFrom-Json
    $p = $cfg.logging.file
    if ($p -and (Test-Path $p)) { return [string]$p }
  } catch {}
  return $null
}

function Ensure-DefaultWorkspaceLearnings() {
  try {
    $ws = Join-Path $env:USERPROFILE ".openclaw\\workspace"
    $learnDir = Join-Path $ws ".learnings"
    Ensure-Dir $learnDir
    $learnFile = Join-Path $learnDir "LEARNINGS.md"
    if (-not (Test-Path $learnFile)) {
      "" | Set-Content -Path $learnFile -Encoding UTF8
    }
  } catch {}
}

function Stop-PortListeners([int[]]$Ports) {
  foreach ($p in $Ports) {
    $owners = @()
    try { $owners += (Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue).OwningProcess } catch {}
    $owners = $owners | Where-Object { $_ -and $_ -ne 0 } | Select-Object -Unique
    foreach ($procId in $owners) {
      try { Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue } catch {}
    }
  }
}

function Is-PortListening([int]$P) {
  try {
    $c = Get-NetTCPConnection -LocalPort $P -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $c
  } catch {
    return $false
  }
}

$node = Get-Command node -ErrorAction SilentlyContinue
if (-not $node) { throw "Node.js is not available on PATH" }

$repoRoot = Resolve-RepoRoot
$resolvedOpenClawRoot = Find-OpenClawRoot $OpenClawRoot
if (-not $resolvedOpenClawRoot) { throw "OpenClaw root not found (openclaw.mjs required). Use -OpenClawRoot or set OPENCLAW_ROOT." }

$legacyConfigRoot = "F:\clawd\config"
$useLegacy = (Test-Path $legacyConfigRoot)
if (-not $StateDir -or $StateDir.Trim().Length -eq 0) {
  if ($useLegacy) {
    if ($IsolateState) {
      $StateDir = Join-Path $legacyConfigRoot "state-cli"
    } else {
      $StateDir = Join-Path $legacyConfigRoot "state"
    }
  } else {
    $StateDir = Join-Path $repoRoot ".openclaw-state"
  }
}
Ensure-Dir $StateDir

if (-not $ConfigPath -or $ConfigPath.Trim().Length -eq 0) {
  if ($useLegacy) {
    $ConfigPath = Join-Path $legacyConfigRoot "openclaw.json"
  } else {
    $ConfigPath = Join-Path $StateDir "openclaw.json"
  }
}
Ensure-OpenClawConfig $ConfigPath $Port

$env:OPENCLAW_STATE_DIR = $StateDir
$env:OPENCLAW_CONFIG_PATH = $ConfigPath
Ensure-DefaultWorkspaceLearnings

$entry = Join-Path $resolvedOpenClawRoot "openclaw.mjs"
$nodeArgs = @($entry)
if ($Dev) { $nodeArgs += "--dev" }
$nodeArgs += @("gateway", "run")
if ($VerboseConsole) { $nodeArgs += "--verbose" }
$nodeArgs += @("--allow-unconfigured", "--port", "$Port")

$logsDir = if ($useLegacy) { Join-Path $legacyConfigRoot "logs" } else { Join-Path $StateDir "logs" }
Ensure-Dir $logsDir
$stdoutLog = Join-Path $logsDir "gateway.out.log"
$stderrLog = Join-Path $logsDir "gateway.err.log"

if (-not $Restart) {
  if (Is-PortListening $Port) {
    Write-Host ("OpenClaw Gateway already listening: http://127.0.0.1:{0}/" -f $Port)
    exit 0
  }
}

if ($Restart) {
  try {
    Set-Location $resolvedOpenClawRoot
    node $entry gateway stop | Out-Null
  } catch {}
}

Stop-PortListeners @($Port, ($Port + 2))

Write-Host ("OPENCLAW_ROOT=" + $resolvedOpenClawRoot)
Write-Host ("OPENCLAW_STATE_DIR=" + $StateDir)
Write-Host ("OPENCLAW_CONFIG_PATH=" + $ConfigPath)
Write-Host ("Starting: node " + ($nodeArgs -join " "))
Write-Host ("Logs: " + $stdoutLog)
Write-Host ("Logs: " + $stderrLog)

if (-not $Background) {
  Set-Location $resolvedOpenClawRoot
  node @nodeArgs 2>&1 | Tee-Object -FilePath $stdoutLog
  exit 0
}

$proc = Start-Process -FilePath "node" -ArgumentList $nodeArgs -WorkingDirectory $resolvedOpenClawRoot -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog -PassThru

$ok = $false
for ($i = 0; $i -lt $WaitForPortSeconds; $i++) {
  if ($proc -and $proc.HasExited) { break }
  if (Is-PortListening $Port) { $ok = $true; break }
  Start-Sleep -Seconds 1
}
if (-not $ok) {
  if (Test-Path $stderrLog) {
    try { Get-Content -Path $stderrLog -Tail 120 | Out-Host } catch {}
  }
  if (Test-Path $stdoutLog) {
    try { Get-Content -Path $stdoutLog -Tail 120 | Out-Host } catch {}
  }
  $openclawLog = Try-Resolve-OpenClawLogFile $ConfigPath
  if ($openclawLog) {
    try {
      Write-Host ("--- Tail OpenClaw log: " + $openclawLog)
      Get-Content -Path $openclawLog -Tail 120 | Out-Host
    } catch {}
  }
  throw "OpenClaw Gateway failed to start (check log files)"
}

Write-Host ("OpenClaw Gateway Ready: http://127.0.0.1:{0}/" -f $Port)

if ($Wait) {
  while ($true) {
    if ($proc -and $proc.HasExited) { throw ("OpenClaw Gateway 进程已退出 (exitCode=" + $proc.ExitCode + ")") }
    Start-Sleep -Seconds 3
  }
}
