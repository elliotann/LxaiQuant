const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Find the logical range check by looking for `const newLogicalRange`
const marker = 'const newLogicalRange = timeScale.getVisibleLogicalRange();';
const idx = content.indexOf(marker);
if (idx < 0) { console.log('NOT FOUND'); process.exit(1); }

// Read the 400 chars before
const before = content.substring(idx - 200, idx + 400);
console.log('--- Context ---');
console.log(before);
console.log('--- END ---');

// The block to replace: from `await nextTick(); // 等待图表更新` 
// through the `}\n          }\n          // 加载信号标注`
// Use `continueLoading = false;` as anchor to find the closing braces
const falseIdx = content.indexOf('continueLoading = false;', idx);
if (falseIdx < 0) { console.log('continueLoading = false not found'); process.exit(1); }

// After `continueLoading = false;`, we have `\n            }\n          }`
const afterFalse = content.substring(falseIdx, falseIdx + 40);
console.log('After false:', JSON.stringify(afterFalse));

// Find the two closing braces
let pos = falseIdx + 'continueLoading = false;'.length;
// Skip whitespace/newlines to find first }
while (pos < content.length && (content[pos] === ' ' || content[pos] === '\r' || content[pos] === '\n')) pos++;
// Now at or before first }
if (content[pos] !== '}') {
  // Skip the rest of the else block
  const rest = content.substring(pos, pos + 30);
  console.log('Expected } at position, got:', JSON.stringify(rest));
}
// Find first }
while (pos < content.length && content[pos] !== '}') pos++;
const firstClose = pos;
pos++; // skip first }
// Skip whitespace to find second }
while (pos < content.length && (content[pos] === ' ' || content[pos] === '\r' || content[pos] === '\n')) pos++;
if (content[pos] !== '}') {
  console.log('Expected second } at pos', pos, 'got:', content[pos]);
}
const secondClose = pos + 1; // include the }

// The `await nextTick` before the marker
const tickPos = content.lastIndexOf('await nextTick', idx);
const blockStart = content.lastIndexOf('\n', tickPos - 1) + 1; // start of line

const oldBlock = content.substring(blockStart, secondClose);
console.log('--- Old block to remove ---');
console.log(oldBlock);
console.log('--- END ---');

const newBlock = '            continueLoading = continueHasMore && iteration < maxIterations;\n          }';

content = content.substring(0, blockStart) + newBlock + content.substring(secondClose);

fs.writeFileSync(filePath, content, 'utf-8');
console.log('=== Part 2 done ===');

// Part 3: Add post-loop indicator updates
// Find: the `}` closing while loop, followed by `// 加载信号标注`
const indicatorInsertion = content.indexOf('continueLoading = continueHasMore && iteration < maxIterations;\n          }\n          // 加载信号标注');

if (indicatorInsertion >= 0) {
  const postLoopIndicators = 
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
  content = content.replace(indicatorInsertion, postLoopIndicators);
  fs.writeFileSync(filePath, content, 'utf-8');
  console.log('=== Part 3 done: Post-loop indicators added ===');
} else {
  console.log('Part 3 marker not found');
}
