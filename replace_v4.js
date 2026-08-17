const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Part 2: Replace logical range check with simple flag
// Find the unique marker: `await nextTick(); // 等待图表更新` before `const newLogicalRange = ...`
const tickMarker = 'await nextTick(); // 等待图表更新';
const logicalMarker = 'const newLogicalRange = timeScale.getVisibleLogicalRange();';
const firstTick = content.indexOf(tickMarker);

// But there are multiple nextTick calls - find the one right before newLogicalRange
const logicalIdx = content.indexOf(logicalMarker);
if (logicalIdx < 0) {
  console.log('newLogicalRange not found!');
  process.exit(1);
}

// Find the last `await nextTick()` before logicalMarker
const beforeLogical = content.substring(logicalIdx - 100, logicalIdx);
const tickIdx = beforeLogical.lastIndexOf('await nextTick');
const absTickIdx = logicalIdx - 100 + tickIdx;
console.log(`await nextTick found at absolute position ${absTickIdx}`);

// Read from tickIdx to find the end of the while loop (the `}` after `continueLoading = false;`)
const snippet = content.substring(absTickIdx, absTickIdx + 500);
console.log('--- Block to replace ---');
console.log(snippet);
console.log('--- END ---');

// Find the `}` that closes the while loop after the logical check
// After `continueLoading = false;` the next `}` on same indentation closes the while loop
const whileCloseBrace = snippet.indexOf('continueLoading = false;');
if (whileCloseBrace < 0) {
  console.log('continueLoading = false not found in snippet');
  process.exit(1);
}

// Find the `}` after continueLoading = false block
const afterFalse = snippet.substring(whileCloseBrace);
// The pattern is:
// ... continueLoading = false;\n            }\n          }
// We need to find the second `}` (at 10-space indent, then 8-space indent)
// Actually first } closes the if/else, second } closes the while loop
let braceCount = 0;
let targetBrace = -1;
for (let i = 0; i < afterFalse.length; i++) {
  if (afterFalse[i] === '{') braceCount++;
  if (afterFalse[i] === '}') {
    braceCount--;
    if (braceCount < 0) {
      // This shouldn't happen
      break;
    }
    if (braceCount === 0) {
      // Found the matching close brace for the if
      // The next `}` on the same or outer indentation closes the while loop
      // Look for the next `}`
      const afterClosing = afterFalse.substring(i + 1);
      const nextBrace = afterClosing.indexOf('}');
      if (nextBrace >= 0) {
        targetBrace = absTickIdx + (whileCloseBrace + i + 1 + nextBrace + 1);
        console.log(`While close brace at position ${targetBrace}`);
        break;
      }
    }
  }
}

if (targetBrace < 0) {
  console.log('Could not find while close brace');
  process.exit(1);
}

// Build new content: replace [absTickIdx, targetBrace) with simplified continueLoading
const oldBlock = content.substring(absTickIdx, targetBrace);
const newBlock = `            continueLoading = continueHasMore && iteration < maxIterations;
          }`;

console.log('Replacing block of length', oldBlock.length);

content = content.substring(0, absTickIdx) + newBlock + content.substring(targetBrace);

fs.writeFileSync(filePath, content, 'utf-8');
console.log('=== DONE: Replaced logical range check ===');

// Part 3: Add one-time indicator updates after the while loop
// Find the pattern: `continueLoading = continueHasMore && iteration < maxIterations;\n          }`
// followed by `\n          // 加载信号标注`
const marker = 'continueLoading = continueHasMore && iteration < maxIterations;\n          }\n          // 加载信号标注';
const markerNew = 'continueLoading = continueHasMore && iteration < maxIterations;\n          }\n          // 循环结束后一次性更新图表和指标\n          candleSeries.value!.setData(dataCache.value);\n          updateMACD();\n          updateBoll();\n          updateTrendStrengthIndicator();\n          updateMA();\n          updateKalman();\n          updateApexTrendLiquidity();\n          updateRangeFilter();\n          updateRSI();\n          updateSmcLite();\n          updateReversalConfirmation();\n          updateTSM();\n          updateTrendStrengthAfterReversal();\n          updateAndeanOscillator();\n          updateMultiTimeframeTrend();\n          // 加载信号标注';

if (content.includes(marker)) {
  content = content.replace(marker, markerNew);
  fs.writeFileSync(filePath, content, 'utf-8');
  console.log('=== DONE: Added post-loop indicator updates ===');
} else {
  console.log('Marker not found, debugging...');
  const idx = content.indexOf('continueLoading = continueHasMore');
  if (idx >= 0) {
    const snip = content.substring(idx, idx + 150);
    console.log('Found closest match:');
    console.log(snip);
  } else {
    console.log('continueLoading not found!');
  }
}
