with open(r'f:\project\lenzeto\ai-engine\src\main\java\com\chain\ai\trade\engine\core\impl\DefaultDealStrategyTrade.java', 'r', encoding='utf-8') as f:
    content = f.read()

old = 'order.getRootId()'
new = 'order.getRobotId()'

if old in content:
    content = content.replace(old, new)
    with open(r'f:\project\lenzeto\ai-engine\src\main\java\com\chain\ai\trade\engine\core\impl\DefaultDealStrategyTrade.java', 'w', encoding='utf-8') as f:
        f.write(content)
    print('REPLACED: rootId -> robotId')
else:
    print('NOT FOUND: getRootId')
    idx = content.find('getRootId')
    if idx >= 0:
        print(repr(content[idx-50:idx+100]))
    else:
        print('getRootId not found in file at all')
        # check what's in the PendingOrderWorker area
        idx2 = content.find('getOrdersByQry')
        if idx2 >= 0:
            print('Found getOrdersByQry:')
            print(repr(content[idx2-100:idx2+100]))
