const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Find SECOND setData + updateMultiTimeframeTrend + anchorTime section
// Use index-based approach to avoid Chinese character encoding issues

const setDataMarker = 'candleSeries.value!.setData(dataCache.value);';
const mfTrendMarker = 'updateMultiTimeframeTrend();';

// Find all setData positions
let positions = [];
let pos = -1;
while ((pos = content.indexOf(setDataMarker, pos + 1)) !== -1) {
  positions.push(pos);
}
console.log(`Found ${positions.length} setData occurrences at: ${JSON.stringify(positions)}`);

// Second occurrence is the forward loop one
const secondPos = positions[1]; // 0-indexed, so second = index 1

// Find the updateMultiTimeframeTrend right after it
const afterSecond = content.substring(secondPos, secondPos + 600);
const mfTrendIdx = afterSecond.indexOf(mfTrendMarker);
console.log(`updateMultiTimeframeTrend found at offset ${mfTrendIdx} in the 600-char snippet`);

if (mfTrendIdx >= 0) {
  // The block to remove: from after `candleSeries.value!.setData(dataCache.value);\n` 
  // to after `updateMultiTimeframeTrend();\n`
  const markersStart = afterSecond.indexOf(setDataMarker) + setDataMarker.length;
  const markersEnd = mfTrendIdx + mfTrendMarker.length;
  
  const blockToRemove = afterSecond.substring(markersStart, markersEnd);
  
  // Verify the block contains only indicator calls
  console.log('Block to remove:');
  console.log(blockToRemove);
  
  // Replace: remove the indicators between setData and anchorTime comment
  content = content.substring(0, secondPos + setDataMarker.length) + content.substring(secondPos + markersEnd);
  
  fs.writeFileSync(filePath, content, 'utf-8');
  console.log('=== DONE: Removed indicators from forward loop ===');
  
  // Now find and remove the logical range check
  // The continueLoading logical check uses: const newLogicalRange = timeScale.getVisibleLogicalRange();
  const logicalMarker = 'const newLogicalRange = timeScale.getVisibleLogicalRange();';
  const newPos = content.indexOf(logicalMarker);
  if (newPos >= 0) {
    console.log('Found logical range check, now replacing...');
    
    // Find the full block from `await nextTick(); // 等待图表更新` to the closing `}` of the while loop
    // We need to replace: everything from the last `await nextTick()` before the logical check
    // through the logical check block, down to the } that closes the while loop
    
    // Strategy: Find the `}` that closes the while loop after the logical check
    // ... this is getting complex. Let me read more of the context.
    content = fs.readFileSync(filePath, 'utf-8');
    const logicalIdx = content.indexOf(logicalMarker);
    const snippet = content.substring(logicalIdx - 50, logicalIdx + 600);
    console.log('--- Logical range check context ---');
    console.log(snippet);
    console.log('--- END ---');
  }
} else {
  console.log('updateMultiTimeframeTrend NOT found in 600-char range');
}
