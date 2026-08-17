package com.chain.ai.trade.engine.notifier.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmtpConfig {
    private String smtpHost;
    private int smtpPort = 587;
    private String emailUser;
    private String emailPassword;
    private boolean proxyEnabled;
    private String proxyHost;
    private int proxyPort = 7890;
    private String to;
}
