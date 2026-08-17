package com.chain.ai.trade.engine2.realtime;

import com.chain.ai.trade.engine2.core.execution.OrderIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"dev","paper", "live", "prod"})
public class RiskCheckHandler {

    public RiskResult checkBeforeEntry(OrderIntent intent, RealtimeContext context) {
        return RiskResult.PASS;
    }

    public RiskResult checkBeforeExit(OrderIntent intent, RealtimeContext context) {
        return RiskResult.PASS;
    }

    public static class RiskResult {
        public static final RiskResult PASS = new RiskResult(true, null);
        private final boolean pass;
        private final String reason;

        private RiskResult(boolean pass, String reason) {
            this.pass = pass;
            this.reason = reason;
        }

        public boolean isPass() { return pass; }
        public boolean isRejected() { return !pass; }
        public String getReason() { return reason; }

        public static RiskResult pass() { return PASS; }
        public static RiskResult reject(String reason) { return new RiskResult(false, reason); }
    }
}