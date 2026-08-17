package com.chain.ai.trade.engine.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmConfigTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_llm_config'",
                Integer.class
            );
            if (cnt != null && cnt > 0) return;

            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS t_llm_config (" +
                    "id VARCHAR(32) NOT NULL PRIMARY KEY," +
                    "config_key VARCHAR(64) NOT NULL UNIQUE," +
                    "provider VARCHAR(32) NOT NULL," +
                    "model VARCHAR(128)," +
                    "api_base_url VARCHAR(512)," +
                    "api_key_enc TEXT," +
                    "api_key_configured TINYINT(1) NOT NULL DEFAULT 0," +
                    "extra_config TEXT," +
                    "create_by VARCHAR(32)," +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "update_by VARCHAR(32)," +
                    "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "delete_flag TINYINT(1) NOT NULL DEFAULT 0," +
                    "INDEX idx_llm_provider (provider)," +
                    "INDEX idx_llm_update_time (update_time DESC)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            log.info("t_llm_config initialized");
        } catch (Exception e) {
            log.warn("t_llm_config init skipped: {}", e.getMessage());
        }
    }
}

