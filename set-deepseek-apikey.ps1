param(
  [string]$ConfigPath = "F:\\clawd\\config\\openclaw.json",
  [switch]$FromClipboard,
  [string]$ApiKey = ""
)

$ErrorActionPreference = "Stop"

function Read-ApiKeyFromClipboard {
  try {
    return (Get-Clipboard -Raw).ToString()
  } catch {
    return ""
  }
}

if (-not (Test-Path $ConfigPath)) {
  throw "Config not found: $ConfigPath"
}

$rawKey = if ($ApiKey) { $ApiKey } elseif ($FromClipboard) { Read-ApiKeyFromClipboard } else { "" }
$rawKey = [string]$rawKey
$key = $rawKey -replace "[\r\n\u2028\u2029]+", ""
$key = $key.Trim()

if (-not $key) {
  throw "DeepSeek API key is empty. Copy it to clipboard, or pass -ApiKey."
}

if ($key.Length -lt 16) {
  throw "DeepSeek API key looks too short (len=$($key.Length)). Refusing to write."
}

$json = [System.IO.File]::ReadAllText($ConfigPath, [System.Text.Encoding]::UTF8)
$cfg = $json | ConvertFrom-Json

if (-not $cfg.models) { $cfg | Add-Member -NotePropertyName models -NotePropertyValue (@{}) }
if (-not $cfg.models.providers) { $cfg.models | Add-Member -NotePropertyName providers -NotePropertyValue (@{}) }
if (-not $cfg.models.providers.deepseek) { $cfg.models.providers | Add-Member -NotePropertyName deepseek -NotePropertyValue (@{}) }

$deepseek = $cfg.models.providers.deepseek
if ($null -eq ($deepseek | Get-Member -MemberType NoteProperty -Name apiKey -ErrorAction SilentlyContinue)) {
  $deepseek | Add-Member -NotePropertyName apiKey -NotePropertyValue $key -Force
} else {
  $deepseek.apiKey = $key
}
$out = $cfg | ConvertTo-Json -Depth 60

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($ConfigPath, $out + [Environment]::NewLine, $utf8NoBom)

Write-Output "OK"
