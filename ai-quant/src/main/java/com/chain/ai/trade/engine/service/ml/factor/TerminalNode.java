package com.chain.ai.trade.engine.service.ml.factor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TerminalNode extends Node {

    private final String variableName;

    public TerminalNode(String variableName) {
        this.variableName = variableName;
    }

    public String getVariableName() { return variableName; }

    @Override
    public double eval(int index, Map<String, double[]> data) {
        double[] values = data.get(variableName);
        if (values == null || index < 0 || index >= values.length) {
            return 0;
        }
        return values[index];
    }

    @Override
    public String toExpression() {
        return variableName;
    }

    @Override
    public String toLatex() {
        return variableName;
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
        return new TerminalNode(variableName);
    }

    @Override
    public List<String> usedTerminals() {
        return Collections.singletonList(variableName);
    }

    @Override
    public String toString() {
        return variableName;
    }
}
