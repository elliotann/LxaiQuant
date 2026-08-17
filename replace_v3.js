const fs = require('fs');
const path = require('path');

const filePath = path.resolve(__dirname, 'ai-frontend-web/src/views/market/MarketKlineV1.vue');
let content = fs.readFileSync(filePath, 'utf-8');

// Part 2: Find and replace the logical range check block
// Use getVisibleLogicalRange as marker
const logicalMarker = 'timeScale.getVisibleLogicalRange()';
const loc = content.indexOf(logicalMarker);
if (loc < 0) {
  console.log('Logical range check not found!');
  process.exit(1);
}

// Read 700 chars around the logical range check
const snippet = content.substring(loc - 30, loc + 700);
console.log('--- Before replacement ---');
console.log(snippet);
console.log('--- END ---');

// Find exact boundaries
// We need to replace from `await nextTick();` (before the check) 
// to `}` (closing while loop)
const nextTickBefore = content.lastIndexOf('await nextTick();', loc);
if (nextTickBefore < 0) {
  console.log('nextTick before not found');
  process.exit(1);
}

// Read from nextTick to find the end
const fromNextTick = content.substring(nextTickBefore, nextTickBefore + 800);
console.log('--- From nextTick ---');
console.log(fromNextTick);
console.log('--- END ---');
