$file = "F:\project\lenzeto\ai-frontend-web\src\views\market\MarketKlineV1.vue"
$content = Get-Content $file -Raw

$old = '            candleSeries.value!.setData(dataCache.value);
            updateMACD();
            updateBoll();
            updateTrendStrengthIndicator();
            updateMA();
            updateKalman();
            updateApexTrendLiquidity();
            updateRangeFilter();
            updateRSI();
            updateSmcLite();
            updateReversalConfirmation();
            updateTSM();
            updateTrendStrengthAfterReversal();
            updateAndeanOscillator();
            updateMultiTimeframeTrend();
            // 更新 anchorTime'

$new = '            candleSeries.value!.setData(dataCache.value);
            // 更新 anchorTime'

Write-Host "Old text found: $($content.Contains($old))"

if ($content.Contains($old)) {
    $content = $content.Replace($old, $new)
    [System.IO.File]::WriteAllText($file, $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "Indicators removed from forward loop"
}
