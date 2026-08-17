param(
  [string]$ConfigPath = "F:\\clawd\\config\\openclaw.json"
)

$ErrorActionPreference = "Stop"

$json = [System.IO.File]::ReadAllText($ConfigPath, [System.Text.Encoding]::UTF8)
$cfg = $json | ConvertFrom-Json

if (-not $cfg.gateway) { throw "Missing gateway section in config: $ConfigPath" }
if (-not $cfg.gateway.controlUi) { $cfg.gateway | Add-Member -NotePropertyName controlUi -NotePropertyValue (@{}) }

$cfg.gateway.controlUi.dangerouslyAllowHostHeaderOriginFallback = $true

$out = $cfg | ConvertTo-Json -Depth 80
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($ConfigPath, $out + [Environment]::NewLine, $utf8NoBom)

Write-Output "OK"

