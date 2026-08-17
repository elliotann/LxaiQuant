-- 策略管理模块数据库初始化脚本

-- 2.2.3 策略分类表 (strategy_category)
-- 策略分类表 - 存储策略分类信息
CREATE TABLE IF NOT EXISTS strategy_category (
    -- 主键
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    
    -- 分类信息
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '分类代码',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    description TEXT COMMENT '分类描述',
    icon VARCHAR(100) COMMENT '分类图标',
    
    -- 层级结构
    parent_id BIGINT COMMENT '父分类ID',
    level INT DEFAULT 1 COMMENT '层级',
    path VARCHAR(500) COMMENT '分类路径',
    sort_order INT DEFAULT 0 COMMENT '排序序号',
    
    -- 分类属性
    is_system BOOLEAN DEFAULT FALSE COMMENT '是否为系统分类',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    
    -- 统计信息
    strategy_count INT DEFAULT 0 COMMENT '策略数量',
    backtest_count INT DEFAULT 0 COMMENT '回测次数',
    avg_sharpe_ratio DECIMAL(10,4) COMMENT '平均夏普比率',
    
    -- 元数据
    tags JSON COMMENT '标签',
    metadata JSON COMMENT '元数据',
    
    -- 权限
    owner_id BIGINT COMMENT '所有者ID',
    
    -- 时间戳
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    
    -- 索引
    INDEX idx_code (code),
    INDEX idx_parent (parent_id),
    INDEX idx_level (level),
    INDEX idx_sort_order (sort_order),
    INDEX idx_is_active (is_active),
    
    -- 外键
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) 
        REFERENCES strategy_category(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略分类表';

-- 2.2.1 策略定义表 (strategy)
-- 策略定义表 - 存储策略核心定义
CREATE TABLE IF NOT EXISTS strategy (
    -- 核心标识
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    strategy_id VARCHAR(64) NOT NULL UNIQUE COMMENT '策略唯一标识',
    
    -- 策略基本信息
    name VARCHAR(100) NOT NULL COMMENT '策略名称',
    display_name VARCHAR(150) COMMENT '显示名称',
    description TEXT COMMENT '策略描述',
    brief_description VARCHAR(500) COMMENT '简要描述',
    
    -- 策略类型和分类
    strategy_type VARCHAR(50) NOT NULL COMMENT '策略类型: JAVA_CLASS, GROOVY_SCRIPT, PYTHON_SCRIPT, JAVASCRIPT',
    category_id BIGINT COMMENT '分类ID',
    sub_category VARCHAR(50) COMMENT '子分类',
    tags JSON COMMENT '标签',
    
    -- 策略代码
    code_content LONGTEXT COMMENT '策略代码内容（脚本类策略）',
    class_name VARCHAR(200) COMMENT 'Java类名（Java类策略）',
    file_path VARCHAR(500) COMMENT '文件路径',
    entry_point VARCHAR(100) COMMENT '入口函数/方法',
    
    -- 策略配置
    auto_signal TEXT COMMENT 'AI 智能过滤配置',
    default_parameters JSON COMMENT '默认参数',
    parameter_schema JSON COMMENT '参数JSON Schema',
    dependencies JSON COMMENT '依赖配置',
    
    -- 状态管理
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态: DRAFT, TESTING, ACTIVE, DEPRECATED, ARCHIVED',
    visibility VARCHAR(20) DEFAULT 'PRIVATE' COMMENT '可见性: PRIVATE, TEAM, PUBLIC',
    is_system BOOLEAN DEFAULT FALSE COMMENT '是否为系统策略',
    is_template BOOLEAN DEFAULT FALSE COMMENT '是否为模板策略',
    
    -- 策略属性
    frequency VARCHAR(20) DEFAULT 'DAILY' COMMENT '运行频率: DAILY, HOURLY, MINUTELY, REALTIME',
    market_type VARCHAR(20) DEFAULT 'STOCK' COMMENT '市场类型: STOCK, FUTURE, CRYPTO, FOREX',
    time_frame VARCHAR(20) COMMENT '时间框架: DAILY, HOUR, MINUTE_5, MINUTE_15',
    
    -- 策略能力
    supports_long BOOLEAN DEFAULT TRUE COMMENT '支持做多',
    supports_short BOOLEAN DEFAULT FALSE COMMENT '支持做空',
    supports_leverage BOOLEAN DEFAULT FALSE COMMENT '支持杠杆',
    max_position_count INT DEFAULT 10 COMMENT '最大持仓数量',
    min_capital DECIMAL(18,4) DEFAULT 10000.00 COMMENT '最小资金要求',
    
    -- 版本信息
    current_version VARCHAR(20) DEFAULT '1.0.0' COMMENT '当前版本',
    latest_version_id BIGINT COMMENT '最新版本ID',
    
    -- 统计信息
    backtest_count INT DEFAULT 0 COMMENT '回测次数',
    avg_sharpe_ratio DECIMAL(10,4) COMMENT '平均夏普比率',
    avg_annual_return DECIMAL(10,4) COMMENT '平均年化收益',
    avg_max_drawdown DECIMAL(10,4) COMMENT '平均最大回撤',
    success_rate DECIMAL(5,2) COMMENT '成功率',
    
    -- 权限和审计
    owner_id BIGINT NOT NULL COMMENT '所有者ID',
    owner_name VARCHAR(100) COMMENT '所有者姓名',
    team_id BIGINT COMMENT '团队ID',
    created_by BIGINT NOT NULL COMMENT '创建者ID',
    created_by_name VARCHAR(100) COMMENT '创建者姓名',
    approved_by BIGINT COMMENT '审核者ID',
    approved_at DATETIME(3) COMMENT '审核时间',
    
    -- 时间戳
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    last_executed_at DATETIME(3) COMMENT '最后执行时间',
    last_modified_at DATETIME(3) COMMENT '最后修改时间',
    
    -- 索引
    INDEX idx_strategy_id (strategy_id),
    INDEX idx_name (name),
    INDEX idx_owner (owner_id),
    INDEX idx_team (team_id),
    INDEX idx_category (category_id),
    INDEX idx_status (status),
    INDEX idx_visibility (visibility),
    INDEX idx_strategy_type (strategy_type),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_updated_at (updated_at DESC),
    INDEX idx_backtest_count (backtest_count DESC),
    INDEX idx_avg_sharpe_ratio (avg_sharpe_ratio DESC),
    
    -- 外键
    CONSTRAINT fk_strategy_category FOREIGN KEY (category_id) 
        REFERENCES strategy_category(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略定义表';

-- 2.2.2 策略版本表 (strategy_version)
-- 策略版本表 - 存储策略版本历史
CREATE TABLE IF NOT EXISTS strategy_version (
    -- 主键
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    version_id VARCHAR(64) UNIQUE NOT NULL COMMENT '版本唯一标识',
    
    -- 关联策略
    strategy_id VARCHAR(64) NOT NULL COMMENT '策略ID',
    version_name VARCHAR(50) NOT NULL COMMENT '版本名称',
    version_code VARCHAR(20) NOT NULL COMMENT '版本号，格式: 1.0.0',
    
    -- 版本内容
    code_content LONGTEXT COMMENT '策略代码内容',
    class_name VARCHAR(200) COMMENT 'Java类名',
    file_hash VARCHAR(64) COMMENT '文件哈希值',
    file_size BIGINT COMMENT '文件大小',
    
    -- 参数配置
    parameters JSON COMMENT '参数配置',
    parameter_schema JSON COMMENT '参数JSON Schema',
    dependencies JSON COMMENT '依赖配置',
    
    -- 变更信息
    change_type VARCHAR(20) DEFAULT 'CREATE' COMMENT '变更类型: CREATE, UPDATE, BUGFIX, ENHANCEMENT',
    change_log TEXT COMMENT '变更日志',
    major_changes TEXT COMMENT '主要变更',
    
    -- 版本状态
    is_current BOOLEAN DEFAULT FALSE COMMENT '是否为当前版本',
    is_stable BOOLEAN DEFAULT FALSE COMMENT '是否为稳定版本',
    is_deprecated BOOLEAN DEFAULT FALSE COMMENT '是否已废弃',
    
    -- 测试结果
    backtest_count INT DEFAULT 0 COMMENT '回测次数',
    avg_sharpe_ratio DECIMAL(10,4) COMMENT '平均夏普比率',
    avg_annual_return DECIMAL(10,4) COMMENT '平均年化收益',
    test_result JSON COMMENT '测试结果汇总',
    
    -- 元数据
    tags JSON COMMENT '标签',
    metadata JSON COMMENT '元数据',
    
    -- 权限和审计
    created_by BIGINT NOT NULL COMMENT '创建者ID',
    created_by_name VARCHAR(100) COMMENT '创建者姓名',
    reviewed_by BIGINT COMMENT '审核者ID',
    reviewed_at DATETIME(3) COMMENT '审核时间',
    
    -- 时间戳
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    
    -- 索引
    UNIQUE KEY uk_strategy_version (strategy_id, version_code),
    INDEX idx_strategy_id (strategy_id),
    INDEX idx_version_code (version_code),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_is_current (is_current),
    INDEX idx_is_stable (is_stable),
    
    -- 外键
    CONSTRAINT fk_version_strategy FOREIGN KEY (strategy_id) 
        REFERENCES strategy(strategy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略版本表';

-- 2.2.4 策略参数表 (strategy_parameter)
-- 策略参数表 - 存储策略参数定义
CREATE TABLE IF NOT EXISTS strategy_parameter (
    -- 主键
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    param_id VARCHAR(64) UNIQUE NOT NULL COMMENT '参数唯一标识',
    
    -- 关联策略
    strategy_id VARCHAR(64) NOT NULL COMMENT '策略ID',
    
    -- 参数基本信息
    name VARCHAR(100) NOT NULL COMMENT '参数名称',
    display_name VARCHAR(150) COMMENT '显示名称',
    description TEXT COMMENT '参数描述',
    group_name VARCHAR(100) COMMENT '参数分组',
    
    -- 参数类型
    param_type VARCHAR(50) NOT NULL COMMENT '参数类型: INTEGER, DECIMAL, STRING, BOOLEAN, SELECT, DATE, DATETIME',
    data_type VARCHAR(50) COMMENT '数据类型',
    
    -- 参数值约束
    default_value TEXT COMMENT '默认值',
    min_value TEXT COMMENT '最小值',
    max_value TEXT COMMENT '最大值',
    step_value DECIMAL(18,4) COMMENT '步长',
    options JSON COMMENT '选项列表（SELECT类型）',
    format_pattern VARCHAR(100) COMMENT '格式模式',
    
    -- 验证规则
    is_required BOOLEAN DEFAULT FALSE COMMENT '是否必填',
    is_advanced BOOLEAN DEFAULT FALSE COMMENT '是否为高级参数',
    is_array BOOLEAN DEFAULT FALSE COMMENT '是否为数组',
    min_length INT COMMENT '最小长度',
    max_length INT COMMENT '最大长度',
    regex_pattern VARCHAR(500) COMMENT '正则表达式',
    validation_message VARCHAR(500) COMMENT '验证提示信息',
    
    -- 显示配置
    display_order INT DEFAULT 0 COMMENT '显示顺序',
    is_visible BOOLEAN DEFAULT TRUE COMMENT '是否可见',
    ui_component VARCHAR(50) COMMENT 'UI组件类型',
    placeholder VARCHAR(200) COMMENT '占位符',
    tooltip TEXT COMMENT '提示信息',
    
    -- 元数据
    metadata JSON COMMENT '元数据',
    
    -- 时间戳
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    
    -- 索引
    UNIQUE KEY uk_strategy_param (strategy_id, group_name, name),
    INDEX idx_strategy_id (strategy_id),
    INDEX idx_group_name (group_name),
    INDEX idx_display_order (display_order),
    
    -- 外键
    CONSTRAINT fk_parameter_strategy FOREIGN KEY (strategy_id) 
        REFERENCES strategy(strategy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='策略参数表';

-- 插入系统默认分类
INSERT INTO strategy_category (code, name, description, is_system, sort_order) VALUES
('TREND', '趋势跟踪', '基于价格趋势的策略', TRUE, 1),
('REVERSAL', '反转策略', '基于价格反转的策略', TRUE, 2),
('ARBITRAGE', '套利策略', '利用市场价差的策略', TRUE, 3),
('MEAN_REVERSION', '均值回归', '基于均值回归的策略', TRUE, 4),
('BREAKOUT', '突破策略', '基于价格突破的策略', TRUE, 5),
('HIGH_FREQUENCY', '高频交易', '高频交易策略', TRUE, 6),
('QUANTITATIVE', '量化策略', '基于量化分析的策略', TRUE, 7),
('ML_AI', '机器学习/AI', '基于机器学习和AI的策略', TRUE, 8)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 插入demo策略数据
-- 注意：需要先获取一个分类ID，这里假设使用TREND分类（id=1）
INSERT INTO strategy (
    strategy_id,
    name,
    display_name,
    description,
    brief_description,
    strategy_type,
    category_id,
    status,
    visibility,
    is_system,
    is_template,
    frequency,
    market_type,
    time_frame,
    supports_long,
    supports_short,
    supports_leverage,
    max_position_count,
    min_capital,
    current_version,
    backtest_count,
    owner_id,
    created_by,
    created_by_name
) VALUES (
    'STRATEGY_DEMO_001',
    '双均线策略',
    '双均线交叉策略示例',
    '这是一个基于双均线交叉的示例策略。当短期均线上穿长期均线时产生买入信号，当短期均线下穿长期均线时产生卖出信号。',
    '双均线交叉策略，适用于趋势明显的市场',
    'JAVA_CLASS',
    (SELECT id FROM strategy_category WHERE code = 'TREND' LIMIT 1),
    'ACTIVE',
    'PUBLIC',
    FALSE,
    FALSE,
    'DAILY',
    'CRYPTO',
    'MINUTE_5',
    TRUE,
    FALSE,
    FALSE,
    10,
    10000.00,
    '1.0.0',
    0,
    1,
    1,
    '系统管理员'
)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2.2.4 交易机器人表 (trading_bot)
-- 交易机器人表 - 存储交易机器人配置和状态信息
CREATE TABLE IF NOT EXISTS trading_bot (
    -- 核心标识
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    bot_id VARCHAR(64) NOT NULL UNIQUE COMMENT '机器人唯一标识',

    -- 机器人基本信息
    bot_name VARCHAR(100) NOT NULL COMMENT '机器人名称',

    -- 关联信息
    user_id VARCHAR(64) NOT NULL COMMENT '所属用户ID',
    account_id VARCHAR(64) NOT NULL COMMENT '使用的交易账户ID',
    strategy_id VARCHAR(64) NOT NULL COMMENT '使用的策略ID',

    -- 交易配置
    trading_pair VARCHAR(50) NOT NULL COMMENT '交易对',
    allocated_capital DECIMAL(18,4) NOT NULL COMMENT '分配的资金额度',
    current_capital DECIMAL(18,4) NOT NULL COMMENT '当前剩余资金',

    -- 状态管理
    status VARCHAR(20) DEFAULT 'CREATED' COMMENT '机器人状态: CREATED, RUNNING, PAUSED, STOPPED, ERROR',

    -- 时间信息
    start_time DATETIME(3) COMMENT '开始时间',
    last_signal_time DATETIME(3) COMMENT '最后信号时间',

    -- 配置和统计
    configuration JSON COMMENT '配置信息（JSON格式）',
    statistics JSON COMMENT '统计信息（JSON格式）',

    -- 控制字段
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    remark TEXT COMMENT '备注',

    -- 审计字段
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    created_by VARCHAR(64) COMMENT '创建者ID',
    updated_by VARCHAR(64) COMMENT '更新者ID',

    -- 索引
    INDEX idx_bot_id (bot_id),
    INDEX idx_user_id (user_id),
    INDEX idx_account_id (account_id),
    INDEX idx_strategy_id (strategy_id),
    INDEX idx_status (status),
    INDEX idx_enabled (enabled),
    INDEX idx_created_at (created_at DESC),
    INDEX idx_updated_at (updated_at DESC)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易机器人表';

-- 插入示例机器人数据
INSERT INTO trading_bot (
    bot_id,
    bot_name,
    user_id,
    account_id,
    strategy_id,
    trading_pair,
    allocated_capital,
    current_capital,
    status,
    enabled,
    created_by,
    remark
) VALUES (
    'BOT-TEST-001',
    '测试交易机器人',
    'USER-TEST-001',
    'ACCOUNT-TEST-001',
    'STRATEGY_DEMO_001',
    'BTC-USDT',
    10000.00,
    10000.00,
    'CREATED',
    TRUE,
    'SYSTEM',
    '系统创建的测试机器人'
)
ON DUPLICATE KEY UPDATE bot_name = VALUES(bot_name);

-- 2.2.5 入场规则头表 (strategy_entry_rule)
-- 每个方向(LONG/SHORT)一条记录
CREATE TABLE IF NOT EXISTS strategy_entry_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    rule_id VARCHAR(64) UNIQUE NOT NULL COMMENT '规则唯一标识',
    strategy_id VARCHAR(64) NOT NULL COMMENT '策略ID',
    direction VARCHAR(20) NOT NULL COMMENT '方向: LONG, SHORT',
    disabled TINYINT(1) DEFAULT 0 COMMENT '是否禁用: 0启用, 1禁用',
    version INT DEFAULT 1 COMMENT '规则版本',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    INDEX idx_strategy_id (strategy_id),
    INDEX idx_strategy_direction (strategy_id, direction),
    CONSTRAINT fk_entry_rule_strategy FOREIGN KEY (strategy_id)
        REFERENCES strategy(strategy_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入场规则头表';

-- 2.2.6 入场规则条件明细表 (entry_rule_condition)
CREATE TABLE IF NOT EXISTS entry_rule_condition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    rule_id VARCHAR(64) NOT NULL COMMENT '关联 strategy_entry_rule.rule_id',
    sequence INT NOT NULL COMMENT '顺序，从1开始',
    connector VARCHAR(10) DEFAULT NULL COMMENT '与前一个条件的连接符: AND, OR，第一个条件为NULL',
    indicator_type VARCHAR(50) NOT NULL COMMENT '指标类型: RSI, MACD, Volume, MA, Bollinger, Stoch, ATR',
    indicator_params JSON DEFAULT NULL COMMENT '指标参数，如 {"period":14}',
    operator VARCHAR(20) NOT NULL COMMENT '操作符: gt, lt, eq, cross_up, cross_down',
    threshold DECIMAL(20,8) DEFAULT NULL COMMENT '阈值',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX idx_rule_seq (rule_id, sequence),
    CONSTRAINT fk_condition_rule FOREIGN KEY (rule_id)
        REFERENCES strategy_entry_rule(rule_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='入场规则条件明细表';

