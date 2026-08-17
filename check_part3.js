const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Find the marker
const marker = 'continueLoading = continueHasMore && iteration < maxIterations;';
const idx = content.indexOf(marker);
if (idx < 0) { console.log('Marker not found'); process.exit(1); }

// Show 400 chars after
const after = content.substring(idx, idx + 400);
console.log('=== RAW CONTENT (showing char codes for key chars) ===');
for (let i = 0; i < after.length; i++) {
  const c = after[i];
  if (c === '\n' || c === '\r' || c === '/' || c.charCodeAt(0) > 127) {
    process.stdout.write('[' + c.charCodeAt(0) + ']');
  } else {
    process.stdout.write(c);
  }
}
console.log('\n=== END ===');

// Count how many times `// 加载信号标注` and `// 循环结束后` appear
const comment1 = '// 加载信号标注';
const comment2 = '// 循环结束后';
console.log('Occurrences of comment1:', (content.match(new RegExp(comment1.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length);
console.log('Occurrences of comment2:', (content.match(new RegExp(comment2.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length);
