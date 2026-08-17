-- ============================================================
-- 社区市场菜单（市场首页 + 发布商品 + 我的已购 + 商品审核）
-- 执行前确认已执行 create_sys_menu.sql 创建了 sys_menu 表
-- ============================================================

-- 1) 插入父菜单（如果不存在）
INSERT INTO sys_menu (menu_code, menu_name, icon, route_path, parent_id, sort_order, perm_code)
SELECT 'community-market', '社区市场', 'Goods', NULL, NULL, 46, NULL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'community-market');

-- 2) 插入子菜单：市场首页
INSERT INTO sys_menu (menu_code, menu_name, icon, route_path, parent_id, sort_order, perm_code)
SELECT 'community-market:index', '市场首页', 'ShoppingCart', '/community-market', id, 1, NULL
FROM sys_menu
WHERE menu_code = 'community-market'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'community-market:index');

-- 3) 插入子菜单：发布商品
INSERT INTO sys_menu (menu_code, menu_name, icon, route_path, parent_id, sort_order, perm_code)
SELECT 'community-market:publish', '发布商品', 'Edit', '/community-market/publish', id, 2, NULL
FROM sys_menu
WHERE menu_code = 'community-market'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'community-market:publish');

-- 4) 插入子菜单：我的已购
INSERT INTO sys_menu (menu_code, menu_name, icon, route_path, parent_id, sort_order, perm_code)
SELECT 'community-market:my-purchases', '我的已购', 'Wallet', '/community-market/my-purchases', id, 3, NULL
FROM sys_menu
WHERE menu_code = 'community-market'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'community-market:my-purchases');

-- 5) 插入子菜单：商品审核
INSERT INTO sys_menu (menu_code, menu_name, icon, route_path, parent_id, sort_order, perm_code)
SELECT 'community-market:review', '商品审核', 'CircleCheckFilled', '/community-market/review', id, 4, NULL
FROM sys_menu
WHERE menu_code = 'community-market'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'community-market:review');
