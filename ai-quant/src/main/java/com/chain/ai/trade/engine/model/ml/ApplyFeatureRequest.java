package com.chain.ai.trade.engine.model.ml;

import lombok.Data;
import java.util.List;

@Data
public class ApplyFeatureRequest {
    private String symbol;
    private List<String> featureNames;
}
