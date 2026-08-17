package com.chain.ai.trade.engine.service.ml.factor;

import java.util.*;
import java.util.stream.Collectors;

public class FuncNode extends Node {

    private final Operator operator;
    private final List<Node> children;

    public FuncNode(Operator operator, List<Node> children) {
        if (children.size() != operator.getArity()) {
            throw new IllegalArgumentException("Operator " + operator.getSymbol()
                    + " expects " + operator.getArity() + " children, got " + children.size());
        }
        this.operator = operator;
        this.children = children;
    }

    public Operator getOperator() { return operator; }
    public List<Node> getChildren() { return children; }

    @Override
    public double eval(int index, Map<String, double[]> data) {
        if (operator.isTsOperator()) {
            return evalTs(index, data);
        }
        double[] args = new double[operator.getArity()];
        for (int i = 0; i < operator.getArity(); i++) {
            args[i] = children.get(i).eval(index, data);
        }
        return operator.apply(args);
    }

    private double evalTs(int index, Map<String, double[]> data) {
        int window = (int) Math.round(children.get(1).eval(index, data));
        if (window < 2) window = 2;
        int start = Math.max(0, index - window + 1);
        int count = index - start + 1;
        double[] values = new double[count];
        for (int i = start; i <= index; i++) {
            values[i - start] = children.get(0).eval(i, data);
        }
        switch (operator) {
            case TS_SUM: return sum(values);
            case TS_MEAN: return mean(values);
            case TS_STD: return std(values);
            case TS_MAX: return max(values);
            case TS_MIN: return min(values);
            case TS_MEDIAN: return median(values);
            default: return 0;
        }
    }

    @Override
    public String toExpression() {
        if (operator.isTsOperator()) {
            return operator.getSymbol() + "(" + children.get(0).toExpression() + ", " + children.get(1).toExpression() + ")";
        }
        if (operator.getArity() == 1) {
            return operator.getSymbol() + "(" + children.get(0).toExpression() + ")";
        }
        return "(" + children.get(0).toExpression() + " " + operator.getSymbol() + " " + children.get(1).toExpression() + ")";
    }

    @Override
    public String toLatex() {
        if (operator.isTsOperator()) {
            return "\\text{" + operator.getSymbol() + "}(" + children.get(0).toLatex() + ", " + children.get(1).toLatex() + ")";
        }
        if (operator.getArity() == 1) {
            return operator.getSymbol() + "(" + children.get(0).toLatex() + ")";
        }
        String left = children.get(0).toLatex();
        String right = children.get(1).toLatex();
        switch (operator) {
            case ADD: return left + " + " + right;
            case SUB: return left + " - " + right;
            case MUL: return left + " \\cdot " + right;
            case DIV: return "\\frac{" + left + "}{" + right + "}";
            default: return "(" + left + " " + operator.getSymbol() + " " + right + ")";
        }
    }

    @Override
    public int depth() {
        return 1 + children.stream().mapToInt(Node::depth).max().orElse(0);
    }

    @Override
    public int nodeCount() {
        return 1 + children.stream().mapToInt(Node::nodeCount).sum();
    }

    @Override
    public Node cloneTree() {
        List<Node> clonedChildren = new ArrayList<>(children.size());
        for (Node child : children) {
            clonedChildren.add(child.cloneTree());
        }
        return new FuncNode(operator, clonedChildren);
    }

    @Override
    public List<String> usedTerminals() {
        return children.stream()
                .flatMap(c -> c.usedTerminals().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return toExpression();
    }

    private static double sum(double[] v) { double s = 0; for (double d : v) s += d; return s; }
    private static double mean(double[] v) { return v.length == 0 ? 0 : sum(v) / v.length; }
    private static double max(double[] v) { double m = Double.NEGATIVE_INFINITY; for (double d : v) if (d > m) m = d; return m == Double.NEGATIVE_INFINITY ? 0 : m; }
    private static double min(double[] v) { double m = Double.POSITIVE_INFINITY; for (double d : v) if (d < m) m = d; return m == Double.POSITIVE_INFINITY ? 0 : m; }
    private static double std(double[] v) { double m = mean(v), s = 0; for (double d : v) s += (d - m) * (d - m); return Math.sqrt(s / v.length); }
    private static double median(double[] v) { double[] s = v.clone(); Arrays.sort(s); return s[s.length / 2]; }
}
