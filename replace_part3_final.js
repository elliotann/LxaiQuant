const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Strategy: Find the marker `continueLoading = continueHasMore && iteration < maxIterations;`
// Then find the `void loadSignalsForVisibleRange()` call after it
// Insert indicator updates right before `void loadSignalsForVisibleRange()`

const marker = 'continueLoading = continueHasMore && iteration < maxIterations;';
const markerIdx = content.indexOf(marker);
if (markerIdx < 0) { console.log('Marker not found'); process.exit(1); }

// Find `void loadSignalsForVisibleRange()` after the marker
const voidCall = 'void loadSignalsForVisibleRange();';
const voidIdx = content.indexOf(voidCall, markerIdx);
if (voidIdx < 0) {
  console.log('void loadSignalsForVisibleRange() not found after marker');
  process.exit(1);
}

// Verify there's only the closing brace between marker and void call
const between = content.substring(markerIdx + marker.length, voidIdx);
console.log('Between marker and void call:', JSON.stringify(between));

// The indicator block to insert (use same line ending as the original file)
// We'll insert right before `void loadSignalsForVisibleRange()`
const indicatorBlock = 
'\n' +
'          // 循环结束后一次性更新图表和指标\n' +
'          candleSeries.value!.setData(dataCache.value);\n' +
'          updateMACD();\n' +
'          updateBoll();\n' +
'          updateTrendStrengthIndicator();\n' +
'          updateMA();\n' +
'          updateKalman();\n' +
'          updateApexTrendLiquidity();\n' +
'          updateRangeFilter();\n' +
'          updateRSI();\n' +
'          updateSmcLite();\n' +
'          updateReversalConfirmation();\n' +
'          updateTSM();\n' +
'          updateTrendStrengthAfterReversal();\n' +
'          updateAndeanOscillator();\n';

content = content.substring(0, voidIdx) + indicatorBlock + content.substring(voidIdx);
fs.writeFileSync(filePath, content, 'utf-8');
console.log('=== Part 3 done: Inserted indicator block before void loadSignalsForVisibleRange() ===');
