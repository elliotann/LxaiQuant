-- ============================================================
-- 系统菜单表（左侧导航栏）
-- ============================================================

DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
    id          INT           NOT NULL AUTO_INCREMENT,
    menu_code   VARCHAR(50)   NOT NULL COMMENT '菜单编码，唯一标识',
    menu_name   VARCHAR(100)  NOT NULL COMMENT '菜单显示名称',
    icon        VARCHAR(50)   DEFAULT NULL COMMENT 'Element Plus 图标名称',
    route_path  VARCHAR(100)  DEFAULT NULL COMMENT '前端路由路径，目录/分组为空',
    parent_id   INT           DEFAULT NULL COMMENT '父菜单ID',
    sort_order  INT           NOT NULL DEFAULT 0 COMMENT '排序号',
    perm_code   VARCHAR(50)   DEFAULT NULL COMMENT '所需权限编码；NULL 或空表示登录即可见',
    enabled     TINYINT(1)    NOT NULL DEFAULT 1,
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_code (menu_code),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单表';

-- ============================================================
-- 初始化菜单数据（使用显式 ID 避免自增漂移）
-- ============================================================

INSERT INTO sys_menu (id, menu_code, menu_name, icon, route_path, parent_id, sort_order, perm_code) VALUES

-- 1) 仪表板
(1, 'dashboard',  '仪表板',   'Monitor',     '/dashboard',          NULL, 1, NULL),

-- 2) 策略管理（目录 + 子项）
(10, 'strategy',       '策略管理',  'TrendCharts', NULL,                NULL, 2, NULL),
(11, 'strategy:list',  '策略列表',  'TrendCharts', '/strategies',       10,   1, NULL),
(12, 'strategy:create','创建策略',  'Edit',        '/strategies/create',10,   2, 'strategy:create'),
(13, 'strategy:templates','策略模板','Files',      '/strategy-templates',10,   3, NULL),

-- 3) 回测分析（目录 + 子项）
(20, 'backtest',       '回测分析',  'DataLine',    NULL,                 NULL, 3, NULL),
(21, 'backtest:tasks', '回测任务',  'DataLine',    '/backtest',          20,   1, NULL),
(22, 'backtest:optimization','策略优化测试','DataLine','/backtest/optimization',20, 2, NULL),

-- 4) 交易管理（目录 + 子项）
(30, 'trading',           '实盘交易', 'Money',     NULL,                  NULL, 4, NULL),
(31, 'trading:real-time', '实盘交易', 'Lightning', '/trading/real-time', 30,   1, 'trade:execute'),
(32, 'trading:positions', '持仓管理', 'PieChart',  '/trading/positions', 30,   2, 'trade:execute'),
(33, 'trading:orders',    '订单管理', 'List',      '/trading/orders',    30,   3, 'trade:execute'),
(34, 'trading:bots',      '交易机器人','Setting',  '/trading-bots',      30,   4, NULL),
(35, 'trading:accounts',  '账户管理', 'Wallet',    '/accounts',          30,   5, NULL),
(36, 'trading:logs',      '交易日志', 'Document',  '/trading-logs',      30,   6, NULL),
(37, 'trading:exchange',  '交易所维护', 'Wallet',   '/trading',           30,   7, NULL),
(81, 'trading:kline',     '市场行情', 'DataBoard', '/market-kline-v1',   30,   0, NULL),

-- 5) 风控规则（单独一级）
(40, 'risk-control', '风控规则', 'Warning', '/risk-control', NULL, 5, NULL),

-- 6) 预警监控（单独一级）
(41, 'alerts',       '预警监控', 'Bell',    '/alerts',        NULL, 6, NULL),

-- 7) 数据管理（目录 + 子项）
(80, 'data',            '数据管理', 'DataBoard',  NULL,                  NULL, 7, NULL),
(82, 'data:import',     '数据导入', 'Upload',     '/data-import',       80,   1, NULL),
(83, 'data:source-test','数据源测试','Connection','/data-source-test',   80,   2, NULL),
(42, 'factor:create',  '因子挖掘',  'Aim',        '/factor-mining',     80,   3, NULL),
(50, 'ml',             '机器学习',  'Cpu',        '/ml',                80,   4, 'ml:train'),
(84, 'data:ai-radar',  'AI雷达',    'Aim',        '/ai-radar',          80,   5, NULL),
(85, 'data:signal-service','信号服务管理','Setting','/signal-service-management',80,6, NULL),
(86, 'data:price-signal','信号管理','TrendCharts','/price-signal-management',80,7, NULL),
(87, 'data:weight-rule','权重规则引擎','Operation','/weight-rule-engine',80,8, NULL),

-- 8) 会员与充值
(60, 'membership', '会员方案', 'Star',  '/membership',        NULL, 8, 'membership:view'),
(61, 'credits',    '积分充值', 'Wallet','/membership/credits', NULL, 9, NULL),

-- 9) 系统管理（目录 + 子项）
(70, 'system',             '系统管理', 'Setting',   NULL,                   NULL, 10, NULL),
(71, 'system:users',       '用户管理', 'User',      '/users',               70,   1, 'user:read'),
(72, 'system:permissions', '权限管理', 'Key',       '/admin/permissions',   70,   2, 'permission:manage'),
(73, 'system:settings',    '系统设置', 'Setting',   '/settings',            70,   3, 'system:config'),
(74, 'system:logs',        '系统日志', 'Document',  '/system/logs',         70,   4, 'system:logs'),
(75, 'system:menus',       '菜单维护', 'Operation', '/system/menus',        70,   5, 'system:menu');
