package com.chain.ai.trade.engine.trader;

import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import com.chain.ai.trade.engine.model.FeatureVector;

public class MLModelEvaluator {
    private final RandomForest model;

    public MLModelEvaluator(RandomForest model) {
        this.model = model;
    }

    public int predict(FeatureVector fv) {
        DataFrame input = new DataFrame(
                new DoubleVector("rsi", new double[]{fv.getRsi()}),
                new DoubleVector("macd", new double[]{fv.getMacd()}),
                new DoubleVector("bollinger_score", new double[]{fv.getBollingerPercentB()}),
                new DoubleVector("price_change", new double[]{fv.getPriceChangePercent()})
        );
        return model.predict(input)[0];
    }
}

