package com.chain.ai.trade.engine.signal.rule;

import com.chain.ai.trade.common.entity.constants.CompositeState;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RuleEvaluationResult {

    private boolean vetoed;
    private double finalWeight;
    private double totalScore;
    private String reason;
    private List<RuleEvaluationTrace> traces;
    private Map<String, Double> indicatorSnapshot;
    private CompositeState trendState;

    public static RuleEvaluationResult veto(String reason, List<RuleEvaluationTrace> traces, Map<String, Double> indicatorSnapshot) {
        RuleEvaluationResult r = new RuleEvaluationResult();
        r.setVetoed(true);
        r.setFinalWeight(0);
        r.setTotalScore(0);
        r.setReason(reason);
        r.setTraces(traces);
        r.setIndicatorSnapshot(indicatorSnapshot);
        return r;
    }

    public static RuleEvaluationResult scored(double weight, double totalScore, String reason,
                                              List<RuleEvaluationTrace> traces, Map<String, Double> indicatorSnapshot) {
        RuleEvaluationResult r = new RuleEvaluationResult();
        r.setVetoed(false);
        r.setFinalWeight(weight);
        r.setTotalScore(totalScore);
        r.setReason(reason);
        r.setTraces(traces);
        r.setIndicatorSnapshot(indicatorSnapshot);
        return r;
    }
}
