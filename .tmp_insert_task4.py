import re

file_path = r"f:\project\lenzeto\ai-engine\src\main\java\com\chain\ai\trade\engine\core\impl\DefaultDealStrategyTrade.java"

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Find the return statement
idx = content.find("return IDealStrategyBasic.super.determineExitTypes")
if idx >= 0:
    print(f"Found return at index {idx}")
    # Show 200 chars before and after
    start = max(0, idx - 50)
    end = min(len(content), idx + 200)
    print("Context (repr):")
    print(repr(content[start:end]))
    print()
    print("Context (raw):")
    print(content[start:end])
    
    # Count occurrences
    count = content.count("determineExitTypes")
    print(f"\nTotal 'determineExitTypes' occurrences: {count}")
