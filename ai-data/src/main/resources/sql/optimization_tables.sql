CREATE TABLE IF NOT EXISTS optimization_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(64) NOT NULL UNIQUE,
  strategy_id VARCHAR(128),
  coin_id VARCHAR(64),
  start_time BIGINT,
  end_time BIGINT,
  param_ranges TEXT,
  objective TEXT,
  config TEXT,
  engine_version VARCHAR(32),
  strategy_version VARCHAR(32),
  num_type VARCHAR(32),
  execution_model VARCHAR(64),
  fee_model VARCHAR(64),
  status VARCHAR(32),
  progress INT DEFAULT 0,
  total_combinations INT DEFAULT 0,
  created_at DATETIME,
  updated_at DATETIME
);

CREATE TABLE IF NOT EXISTS optimization_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(64) NOT NULL,
  param_values TEXT,
  total_return DOUBLE,
  max_drawdown DOUBLE,
  win_rate DOUBLE,
  sharpe_ratio DOUBLE,
  score DOUBLE,
  created_at DATETIME,
  INDEX idx_task_id (task_id)
);
