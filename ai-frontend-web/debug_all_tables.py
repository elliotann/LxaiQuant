# -*- coding: utf-8 -*-
file_path = r'F:\project\lenzeto\ai-frontend-web\src\views\market\MarketKlineV1.vue'

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Count occurrences of key markers
for marker in ['el-table-column label="毛利"', 'el-table-column label="成本"', 
               'el-table-column label="盈亏(USD)"', 'el-table-column label="收益率"',
               'el-table-column label="平仓时间"']:
    matches = [(i+1, lines[i]) for i in range(len(lines)) if marker in lines[i]]
    print(f"\n'{marker}': {len(matches)} occurrence(s)")
    for ln, text in matches:
        # Show if item template exists within next 30 lines
        has_item = any("_type === 'item'" in lines[j] for j in range(i, min(i+30, len(lines))))
        has_close = any("_type === 'closeItem'" in lines[j] for j in range(i, min(i+30, len(lines))))
        print(f"  Line {ln}: item={has_item}, close={has_close}")
        print(f"    {text.strip()}")
