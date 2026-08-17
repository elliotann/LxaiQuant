const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Find the while loop area
const marker = 'continueLoading = continueHasMore && iteration < maxIterations;';
const idx = content.indexOf(marker);
if (idx >= 0) {
  const snippet = content.substring(idx, idx + 300);
  console.log('==== EXACT CONTENT ====');
  console.log(snippet);
  console.log('==== END ====');
}

// Also count how many indicator update calls exist in the file
const indicators = [
  'updateMACD', 'updateBoll', 'updateTrendStrengthIndicator', 
  'updateMA', 'updateKalman', 'updateApexTrendLiquidity',
  'updateRangeFilter', 'updateRSI', 'updateSmcLite', 
  'updateReversalConfirmation', 'updateTSM', 'updateTrendStrengthAfterReversal',
  'updateAndeanOscillator', 'updateMultiTimeframeTrend'
];

for (const ind of indicators) {
  // Count occurrences in function bodies (not in @change handlers in template)
  const matches = content.match(new RegExp(ind + '\\(\\)', 'g'));
  console.log(ind + '(): ' + (matches ? matches.length : 0) + ' occurrences');
}

// Check if we can find the "循环结束后" text
console.log('\nHas "循环结束后":', content.includes('循环结束后'));
