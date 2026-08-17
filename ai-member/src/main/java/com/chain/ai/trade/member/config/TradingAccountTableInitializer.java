package com.chain.ai.trade.member.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradingAccountTableInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 't_member_third_account'",
                    Integer.class
            );

            if (cnt == null || cnt == 0) {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS t_member_third_account (" +
                                "id VARCHAR(32) NOT NULL PRIMARY KEY," +
                                "member_id VARCHAR(32)," +
                                "member_platform VARCHAR(50)," +
                                "account_name VARCHAR(100)," +
                                "uid VARCHAR(50)," +
                                "api_key_enc TEXT," +
                                "api_secret_enc TEXT," +
                                "passphrase_enc TEXT," +
                                "api_enabled TINYINT(1) NOT NULL DEFAULT 1," +
                                "balances TEXT," +
                                "allocations TEXT," +
                                "simulated TINYINT(1)," +
                                "account_type VARCHAR(20)," +
                                "bind_status VARCHAR(20) NOT NULL DEFAULT 'UNBIND'," +
                                "partner_id VARCHAR(50)," +
                                "last_sync_time DATETIME," +
                                "create_by VARCHAR(32)," +
                                "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                                "update_by VARCHAR(32)," +
                                "update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                                "delete_flag TINYINT(1) NOT NULL DEFAULT 0," +
                                "INDEX idx_member_id (member_id)," +
                                "INDEX idx_member_platform (member_platform)," +
                                "INDEX idx_bind_status (bind_status)," +
                                "INDEX idx_create_time (create_time)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
                );
                log.info("t_member_third_account initialized");
                return;
            }

            Map<String, String> addColumns = new LinkedHashMap<>();
            addColumns.put("api_key_enc", "ALTER TABLE t_member_third_account ADD COLUMN api_key_enc TEXT");
            addColumns.put("api_secret_enc", "ALTER TABLE t_member_third_account ADD COLUMN api_secret_enc TEXT");
            addColumns.put("passphrase_enc", "ALTER TABLE t_member_third_account ADD COLUMN passphrase_enc TEXT");
            addColumns.put("simulated", "ALTER TABLE t_member_third_account ADD COLUMN simulated TINYINT(1)");
            addColumns.put("account_type", "ALTER TABLE t_member_third_account ADD COLUMN account_type VARCHAR(20)");

            for (Map.Entry<String, String> e : addColumns.entrySet()) {
                if (!columnExists("t_member_third_account", e.getKey())) {
                    jdbcTemplate.execute(e.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("t_member_third_account init skipped: {}", e.getMessage());
        }
    }

    private boolean columnExists(String table, String column) {
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                table,
                column
        );
        return cnt != null && cnt > 0;
    }
}

