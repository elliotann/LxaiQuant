-- ============================================================
-- 社区市场 - 商品表（支持多类型商品：bot/indicator/strategy/signal）
-- ============================================================
CREATE TABLE `community_market_listings` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT,
    `product_type`    VARCHAR(16)    NOT NULL COMMENT '商品类型: bot/indicator/strategy/signal',
    `source_id`       VARCHAR(64)    NOT NULL COMMENT '源商品ID（机器人ID/指标ID/策略ID等）',
    `name`            VARCHAR(128)   NOT NULL COMMENT '展示名称',
    `description`     TEXT           COMMENT '描述',
    `preview_image`   VARCHAR(512)   COMMENT '预览图URL',
    `config_snapshot` TEXT           COMMENT '配置快照(JSON)，购买时基于此模板创建实例',
    `pricing_type`    VARCHAR(16)    NOT NULL DEFAULT 'free' COMMENT '定价类型: free/paid',
    `price`           DECIMAL(18,4)  NOT NULL DEFAULT 0 COMMENT '价格（积分）',
    `vip_free`        TINYINT(1)     NOT NULL DEFAULT 0 COMMENT 'VIP是否免费',
    `author_id`       VARCHAR(64)    NOT NULL COMMENT '发布者用户ID',
    `status`          VARCHAR(16)    NOT NULL DEFAULT 'pending' COMMENT '状态: pending/approved/rejected/offline',
    `review_note`     TEXT           COMMENT '审核备注',
    `reviewer_id`     VARCHAR(64)    COMMENT '审核人ID',
    `reviewed_at`     DATETIME       COMMENT '审核时间',
    `view_count`      INT            NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `purchase_count`  INT            NOT NULL DEFAULT 0 COMMENT '购买次数',
    `avg_rating`      DECIMAL(3,2)   NOT NULL DEFAULT 0 COMMENT '平均评分',
    `rating_count`    INT            NOT NULL DEFAULT 0 COMMENT '评分人数',
    `has_update`      TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否有更新',
    `tags`            VARCHAR(512)   COMMENT '标签(JSON数组)',
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_type_status` (`product_type`, `status`),
    INDEX `idx_author` (`author_id`),
    INDEX `idx_pricing` (`pricing_type`),
    INDEX `idx_score` (`avg_rating`, `purchase_count`),
    INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区市场商品表';

-- ============================================================
-- 社区市场 - 购买记录表
-- ============================================================
CREATE TABLE `community_purchases` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT,
    `listing_id`      BIGINT         NOT NULL COMMENT '市场商品ID',
    `user_id`         VARCHAR(64)    NOT NULL COMMENT '购买用户ID',
    `purchase_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `credits_spent`   DECIMAL(18,4)  NOT NULL DEFAULT 0 COMMENT '花费积分',
    `last_sync_time`  DATETIME       COMMENT '最后同步时间',
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_listing` (`user_id`, `listing_id`),
    INDEX `idx_user` (`user_id`),
    INDEX `idx_listing` (`listing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区市场购买记录表';

-- ============================================================
-- 社区市场 - 表现数据表（JSON字段支持多类型商品差异化指标）
-- ============================================================
CREATE TABLE `community_performance` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `listing_id`       BIGINT       NOT NULL COMMENT '市场商品ID',
    `usage_count`      INT          NOT NULL DEFAULT 0 COMMENT '被使用次数',
    `performance_data` JSON         COMMENT '表现数据(JSON, 按商品类型不同)',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_listing` (`listing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区市场表现数据表';

-- ============================================================
-- 社区市场 - 评论表
-- ============================================================
CREATE TABLE `community_comments` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `listing_id`  BIGINT       NOT NULL COMMENT '市场商品ID',
    `user_id`     VARCHAR(64)  NOT NULL COMMENT '评论用户ID',
    `rating`      TINYINT      COMMENT '评分 1-5',
    `content`     TEXT         NOT NULL COMMENT '评论内容',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_listing` (`listing_id`),
    INDEX `idx_user_listing` (`user_id`, `listing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区市场评论表';
