package com.chain.ai.trade.engine.service.ml.factor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConstantNode extends Node {

    private final double value;

    public ConstantNode(double value) {
        this.value = value;
    }

    public double getValue() { return value; }

    @Override
    public double eval(int index, Map<String, double[]> data) {
        return value;
    }

    @Override
    public String toExpression() {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((int) value);
        }
        return String.format("%.2f", value);
    }

    @Override
    public String toLatex() {
        return toExpression();
    }

    @Override
    public int depth() {
        return 1;
    }

    @Override
    public int nodeCount() {
        return 1;
    }

    @Override
    public Node cloneTree() {
        return new ConstantNode(value);
    }

    @Override
    public List<String> usedTerminals() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return toExpression();
    }
}
