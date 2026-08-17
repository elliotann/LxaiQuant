package com.chain.ai.trade.engine.service.ml.factor;

import java.util.Map;

public abstract class Node {

    public abstract double eval(int index, Map<String, double[]> data);

    public abstract String toExpression();

    public abstract String toLatex();

    public abstract int depth();

    public abstract int nodeCount();

    public abstract Node cloneTree();

    public abstract java.util.List<String> usedTerminals();
}
