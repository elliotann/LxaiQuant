const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Split into lines
const lines = content.split(/\r?\n/);

// Find the line containing `continueLoading = continueHasMore && iteration < maxIterations;`
let targetLineIdx = -1;
for (let i = 0; i < lines.length; i++) {
  if (lines[i].includes('continueLoading = continueHasMore && iteration < maxIterations;')) {
    targetLineIdx = i;
    break;
  }
}

if (targetLineIdx < 0) {
  console.log('Marker line not found');
  process.exit(1);
}

console.log('Found marker at line', targetLineIdx + 1);
console.log('Line content:', lines[targetLineIdx]);
console.log('Next line (closing brace):', lines[targetLineIdx + 1]);

// The closing brace of the while loop should be at targetLineIdx + 1
// After that is the comment line we need to insert before
const insertBeforeLine = targetLineIdx + 2; // The line after the closing brace
console.log('Inserting before line', insertBeforeLine + 1, ':', lines[insertBeforeLine]);

const indicatorLines = [
  '          // 循环结束后一次性更新图表和指标',
  '          candleSeries.value!.setData(dataCache.value);',
  '          updateMACD();',
  '          updateBoll();',
  '          updateTrendStrengthIndicator();',
  '          updateMA();',
  '          updateKalman();',
  '          updateApexTrendLiquidity();',
  '          updateRangeFilter();',
  '          updateRSI();',
  '          updateSmcLite();',
  '          updateReversalConfirmation();',
  '          updateTSM();',
  '          updateTrendStrengthAfterReversal();',
  '          updateAndeanOscillator();',
];

// Get the original line ending style
const lineEnding = content.includes('\r\n') ? '\r\n' : '\n';

// Insert the indicator lines
lines.splice(insertBeforeLine, 0, ...indicatorLines);

content = lines.join(lineEnding);
fs.writeFileSync(filePath, content, 'utf-8');
console.log('=== Part 3 done, inserted', indicatorLines.length, 'lines ===');
