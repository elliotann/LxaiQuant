const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Search for the pattern with flexible line endings
const searchPatterns = [
  'continueLoading = continueHasMore && iteration < maxIterations;\n          }\n          // 加载信号标注',
  'continueLoading = continueHasMore && iteration < maxIterations;\r\n          }\r\n          // 加载信号标注',
  'continueLoading = continueHasMore && iteration < maxIterations;\n          }\r\n          // 加载信号标注',
  'continueLoading = continueHasMore && iteration < maxIterations;\r\n          }\n          // 加载信号标注',
];

let found = false;
for (const pattern of searchPatterns) {
  const idx = content.indexOf(pattern);
  if (idx >= 0) {
    console.log('Found with pattern index', searchPatterns.indexOf(pattern));
    const replacement = 
'continueLoading = continueHasMore && iteration < maxIterations;\n' +
'          }\n' +
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
'          updateAndeanOscillator();\n' +
'          updateMultiTimeframeTrend();\n' +
'          // 加载信号标注';
    content = content.replace(pattern, replacement);
    fs.writeFileSync(filePath, content, 'utf-8');
    console.log('=== Part 3 done ===');
    found = true;
    break;
  }
}

if (!found) {
  console.log('Marker not found with any pattern');
  // Debug: show exact bytes around the target
  const idx = content.indexOf('continueLoading = continueHasMore && iteration < maxIterations;');
  if (idx >= 0) {
    const snippet = content.substring(idx, idx + 120);
    console.log('Context bytes:');
    for (let i = 0; i < snippet.length; i++) {
      process.stdout.write(snippet.charCodeAt(i) + ' ');
    }
    process.stdout.write('\n');
    console.log('Context string:', JSON.stringify(snippet));
  }
}
