$file = "F:\project\lenzeto\ai-frontend-web\src\views\market\MarketKlineV1.vue"
$content = Get-Content $file -Raw

Write-Host "File length: $($content.Length)"

# Find the first occurrence of setData in the forward loop
$target = "candleSeries.value!.setData(dataCache.value);"
$idx = $content.IndexOf($target)
if ($idx -ge 0) {
    Write-Host "Found target at index $idx"
    $chunk = $content.Substring($idx, 1200)
    Write-Host "--- Chunk start ---"
    Write-Host $chunk
    Write-Host "--- Chunk end ---"
}
