$file = "F:\project\lenzeto\ai-frontend-web\src\views\market\MarketKlineV1.vue"
$lines = [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)

# Find lines ending with ? that look like they should end with "
for ($i = 0; $i -lt $lines.Length; $i++) {
    $trimmed = $lines[$i].TrimEnd()
    if ($trimmed.EndsWith("?") -and $trimmed.Contains('="')) {
        Write-Host ("L{0}: [{1}]" -f ($i+1), $lines[$i].Trim())
    }
}
