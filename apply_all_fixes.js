const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');
let lines = content.split('\n');

// ============================================================
// Verify we have the correct file (Sanity check)
// ============================================================
function checkLine(n, substr) {
  const line = (lines[n - 1] || '').trimEnd().replace(/\r$/, '');
  if (!line.includes(substr)) {
    console.error('FAIL: L' + n + ' expected "' + substr + '", got: ' + line);
    process.exit(1);
  }
}
checkLine(5202, 'let loadDebounceTimer: number | null = null;');
checkLine(7611, 'if (veryCloseToLeft || veryCloseToRight)');
checkLine(7856, 'candleSeries.value!.setData(dataCache.value);');
checkLine(7857, 'updateMACD();');
checkLine(7870, 'updateMultiTimeframeTrend();');
checkLine(7896, 'await nextTick();');
checkLine(7897, 'const newLogicalRange = timeScale.getVisibleLogicalRange();');
checkLine(7912, '          }');
checkLine(7914, 'void loadSignalsForVisibleRange();');
console.log('Sanity check passed');

// ============================================================
// A3: Add rAFLoadTimer variable
// ============================================================
console.log('A3: Adding rAFLoadTimer variable');
const rAFDecl = '// rAF \u8282\u6d41\u5b9a\u65f6\u5668\uff0c\u63a7\u5236\u5feb\u901f\u62d6\u52a8\u65f6\u7684\u52a0\u8f7d\u9891\u7387';
const rAFVar = 'let rAFLoadTimer: number | null = null;';
lines.splice(5202, 0, rAFDecl, rAFVar);
console.log('OK: rAFLoadTimer added at L5202');

// ============================================================
// A3: Replace veryCloseTo block with rAF throttling
// ============================================================
console.log('A3: Modifying veryCloseTo block with rAF throttling');
// Replace lines 7611-7617 (0-indexed: 7610-7616) with rAF version
// After previous insertion at L5202, the shift is +2 lines, so L7611 becomes L7613
// But let's recalculate: the splice at L5202 pushed lines 5203+ down by 2.
// So L7611 in original is now L7611 + 2 = L7613

const shift = 2; // lines inserted before veryCloseTo (5202)
const veryCloseStart = 7611 + shift; // Original L7611 => shifted by 2
const veryCloseEnd = 7617 + shift;   // Original L7617 => shifted by 2

const newVeryCloseBlock = [
  '    if (veryCloseToLeft || veryCloseToRight) {',
  '      if (rAFLoadTimer) cancelAnimationFrame(rAFLoadTimer);',
  '      rAFLoadTimer = requestAnimationFrame(() => {',
  '        rAFLoadTimer = null;',
  '        if (veryCloseToLeft) {',
  '          void loadMoreHistory("backward");',
  '        } else if (veryCloseToRight) {',
  '          void loadMoreHistory("forward");',
  '        }',
  '      });',
  '    }',
];

// Replace lines from veryCloseStart to veryCloseEnd (inclusive)
lines.splice(veryCloseStart - 1, veryCloseEnd - veryCloseStart + 1, ...newVeryCloseBlock);

// After this replacement, line shift changes
const replaceLen = newVeryCloseBlock.length;
const removedLen = veryCloseEnd - veryCloseStart + 1;
const secondShift = replaceLen - removedLen;
console.log('OK: veryCloseTo rAF throttling applied (shift after A3: ' + shift + '+' + secondShift + ')');

// ============================================================
// A1 Part 1: Remove indicator calls from while loop
// ============================================================
console.log('A1 Part 1: Removing indicator calls from forward while loop');

// Current line numbers:
// Original L7856 (setData) => 7856 + shift + secondShift
// Original L7857 (updateMACD) => 7857 + shift + secondShift
// Original L7870 (updateMultiTimeframeTrend) => 7870 + shift + secondShift
const totalShift = shift + secondShift;
const firstEncLine = 7857 + totalShift; // updateMACD line (to remove)
const lastEncLine = 7870 + totalShift;  // updateMultiTimeframeTrend (to remove)
const removedCount = lastEncLine - firstEncLine + 1;

// Verify content
checkLine(7856 + totalShift, 'candleSeries.value!.setData(dataCache.value);');
checkLine(firstEncLine, 'updateMACD();');
checkLine(lastEncLine, 'updateMultiTimeframeTrend();');

lines.splice(firstEncLine - 1, removedCount);
console.log('OK: ' + removedCount + ' indicator calls removed from forward loop');

// ============================================================
// A1 Part 2: Replace logical range check with simple assignment
// ============================================================
console.log('A1 Part 2: Replacing logical range check');

// Original L7896 (await nextTick) => 7896 + totalShift (no change from A1 Part 1 removal since it's before)
// Original L7911 (closing }) => 7911 + totalShift
//
// Actually after A1 Part 1 removed lines, line numbers before the removed block are unaffected.
// Line 7896 is before line 7857+removedCount, so it shifts by totalShift (from A3 only).
// Wait let me recalculate:
// The A1 Part 1 removes lines L7857+totalShift to L7870+totalShift (14 lines), which are AFTER L7896+totalShift.
// So L7896+totalShift is NOT affected by A1 Part 1 removal (the removal is above).
// So L7896+totalShift still has the correct content.

const a1p2Lo = 7896 + totalShift; // Original L7896 (await nextTick)
const a1p2Hi = 7911 + totalShift; // Original L7911 (closing })

checkLine(a1p2Lo, 'await nextTick();');
checkLine(a1p2Lo + 1, 'const newLogicalRange = timeScale.getVisibleLogicalRange();');
checkLine(a1p2Hi, '            }');

const replacement = [
  '            continueLoading = continueHasMore && iteration < maxIterations;',
];
lines.splice(a1p2Lo - 1, a1p2Hi - a1p2Lo + 1, ...replacement);
console.log('OK: Logical range check replaced');

// ============================================================
// A1 Part 3: Add post-loop indicator updates
// ============================================================
console.log('A1 Part 3: Adding post-loop indicator updates');

// After A1 Part 2 replacement, line numbers shifted again.
// The while loop closing } was at original L7912 => L7912 + totalShift
// This line is right after a1p2Hi (which was replaced), so:
// a1p2Hi - a1p2Lo + 1 lines were removed (14 original - 1 replacement = 13 removed)
// So L7912 + totalShift - 13 (wait, the inserted content has 1 line, removed had 16 lines)
// So net reduction = 16 - 1 = 15 lines
// The while loop closing } line is now at: 7912 + totalShift - 15

// Actually, let me trace this more carefully.
// The while loop closing brace was at original L7912.
// After A3: L7912 + totalShift (shift=2, secondShift = 4) = L7918
// Wait, the secondShift... let me calculate:
// veryClose block: 7 old lines → 11 new lines, so secondShift = 11 - 7 = 4
// totalShift = 2 + 4 = 6
// So L7912 (original) => L7912 + 6 = L7918
// After A1 Part 1 removes 14 lines (all after L7857+6=L7863), so:
// L7918 - 14 = L7904 (since the removed 14 lines are before L7918)
// After A1 Part 2 replaces 16 lines (L7896+6 to L7911+6 = L7902-L7917) with 1 line:
// Wait, after A1 Part 1, the lines shift: L7912+totalShift - 14 = L7912+6-14 = L7904
// But the replacement also changes things...

// Actually I'm overcomplicating this. Let me just find the correct position by searching.
let postLoopIdx = -1;
for (let i = 0; i < lines.length; i++) {
  if (lines[i].trimEnd().replace(/\r$/, '') === '          }' && 
      i >= 7900 + totalShift - 14 - 20 && // approximate position
      i <= 7900 + totalShift - 14 + 5) {
    // Check if this is followed by the signals comment
    const nextLine = (lines[i + 1] || '').trimEnd().replace(/\r$/, '');
    if (nextLine.includes('加载信号标注')) {
      postLoopIdx = i;
      break;
    }
  }
}

if (postLoopIdx < 0) {
  console.error('FAIL: Could not find while loop closing brace');
  process.exit(1);
}

console.log('Found while loop closing } at line ' + (postLoopIdx + 1));

const indicatorBlock = [
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

// Insert after the while loop closing brace (before the // 加载信号标注 line)
lines.splice(postLoopIdx + 1, 0, ...indicatorBlock);
console.log('OK: Post-loop indicator updates added');

// ============================================================
// Write file
// ============================================================
content = lines.join('\n');
fs.writeFileSync(filePath, content, 'utf-8');
console.log('=== ALL FIXES APPLIED SUCCESSFULLY ===');
