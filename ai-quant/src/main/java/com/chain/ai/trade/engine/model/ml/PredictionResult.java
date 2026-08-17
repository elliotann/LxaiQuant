package com.chain.ai.trade.engine.model.ml;

import lombok.Data;

@Data
public class PredictionResult {
    private boolean success;
    private String direction;
    private double confidence;
    private double probabilityUp;
    private double probabilityDown;
    private String message;

    public static PredictionResult insufficientData() {
        PredictionResult r = new PredictionResult();
        r.setSuccess(false);
        r.setMessage("数据不足，无法进行预测");
        return r;
    }

    public static PredictionResult noModel() {
        PredictionResult r = new PredictionResult();
        r.setSuccess(false);
        r.setMessage("未找到已训练的模型，请先训练");
        return r;
    }
}
