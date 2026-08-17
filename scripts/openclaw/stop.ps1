param(
  [int]$Port = 18789
)

$ErrorActionPreference = 'Stop'

function Find-OpenClawRoot() {
  $candidates = @(
    $env:OPENCLAW_ROOT,
    "F:\\openclawd\\openclaw",
    (Join-Path (Join-Path $PSScriptRoot "..\\..") "openclaw"),
    (Join-Path $env:USERPROFILE "openclaw")
  ) | Where-Object { $_ -and $_.Trim().Length -gt 0 }
  foreach ($c in $candidates) {
    try {
      if (Test-Path $c) {
        $entry = Join-Path $c "openclaw.mjs"
        if (Test-Path $entry) { return (Resolve-Path $c).Path }
      }
    } catch {}
  }
  return $null
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

try {
  $root = Find-OpenClawRoot
  if ($root) {
    $node = Get-Command node -ErrorAction SilentlyContinue
    if ($node) {
      Set-Location $root
      node (Join-Path $root "openclaw.mjs") gateway stop | Out-Null
    }
  }
} catch {}

Stop-PortListeners @($Port, ($Port + 2))
Write-Host ("Stopped listeners on ports: {0}, {1}" -f $Port, ($Port + 2))
