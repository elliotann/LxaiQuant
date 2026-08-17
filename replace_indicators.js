const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Find SECOND setData occurrence: forward while loop
const targetStr = 'candleSeries.value!.setData(dataCache.value);';
let idx = content.indexOf(targetStr);
let count = 1;
while (idx !== -1) {
  if (count === 2) {
    // This is the forward while-loop block
    // Read 1000 chars after the setData to find the end of the block to replace
    const after = content.substring(idx, idx + 1500);
    console.log('--- Forward while-loop block ---');
    console.log(after);
    console.log('--- END ---');
    
    // Build the replacement: remove indicators, keep setData + anchorTime section,
    // then replace the logicalRange check with simple continueLoading
    // Strategy: extract the anchorTime section and combine
    break;
  }
  idx = content.indexOf(targetStr, idx + 1);
  count++;
}
