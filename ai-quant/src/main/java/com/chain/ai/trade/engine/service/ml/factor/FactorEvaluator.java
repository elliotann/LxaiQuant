package com.chain.ai.trade.engine.service.ml.factor;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FactorEvaluator {

    public static class FactorEvalResult {
        private final double fitness;
        private final double rankIc;
        private final double sharpe;
        private final double turnover;
        private final double corrWithLabel;
        private final double topRet;
        private final int depth;
        private final int nodeCount;

        public FactorEvalResult(double fitness, double rankIc, double sharpe, double turnover,
                                double corrWithLabel, double topRet, int depth, int nodeCount) {
            this.fitness = fitness;
            this.rankIc = rankIc;
            this.sharpe = sharpe;
            this.turnover = turnover;
            this.corrWithLabel = corrWithLabel;
            this.topRet = topRet;
            this.depth = depth;
            this.nodeCount = nodeCount;
        }

        public double getFitness() { return fitness; }
        public double getRankIc() { return rankIc; }
        public double getSharpe() { return sharpe; }
        public double getTurnover() { return turnover; }
        public double getCorrWithLabel() { return corrWithLabel; }
        public double getTopRet() { return topRet; }
        public int getDepth() { return depth; }
        public int getNodeCount() { return nodeCount; }
    }

    public enum FitnessMetric {
        RANK_IC, SHARPE, IC_SHARPE_COMBO
    }

    public FactorEvalResult evaluate(Node expression, Map<String, double[]> data,
                                     double[] labels, FitnessMetric metric,
                                     double parsimonyCoefficient, int startBar) {
        int len = labels.length;
        double[] factorValues = new double[len];
        for (int i = startBar; i < len; i++) {
            factorValues[i] = expression.eval(i, data);
        }
        int validCount = countValid(factorValues, startBar, len);
        if (validCount < 10) {
            return new FactorEvalResult(-999, 0, 0, 0, 0, 0, expression.depth(), expression.nodeCount());
        }
        double rankIc = computeRankIc(factorValues, labels, startBar, len);
        double sharpe = computeSharpe(factorValues, startBar, len);
        double turnover = computeTurnover(factorValues, startBar, len);
        double corrWithLabel = computePearson(factorValues, labels, startBar, len);
        double topRet = computeTopRet(factorValues, labels, startBar, len);
        int depth = expression.depth();
        int nodeCount = expression.nodeCount();
        double fitness;
        switch (metric) {
            case SHARPE:
                fitness = sharpe - parsimonyCoefficient * nodeCount;
                break;
            case IC_SHARPE_COMBO:
                fitness = (Math.abs(rankIc) + Math.abs(sharpe) * 0.1) - parsimonyCoefficient * nodeCount;
                break;
            case RANK_IC:
            default:
                fitness = Math.abs(rankIc) - parsimonyCoefficient * nodeCount;
                break;
        }
        return new FactorEvalResult(fitness, rankIc, sharpe, turnover, corrWithLabel, topRet, depth, nodeCount);
    }

    public double[] computeFactorSeries(Node expression, Map<String, double[]> data, int length) {
        double[] values = new double[length];
        for (int i = 0; i < length; i++) {
            values[i] = expression.eval(i, data);
        }
        return values;
    }

    private double computeRankIc(double[] factor, double[] label, int start, int end) {
        int n = end - start;
        if (n < 3) return 0;
        double[] fRanks = ranks(factor, start, end);
        double[] lRanks = ranks(label, start, end);
        double fMean = mean(fRanks, start, end);
        double lMean = mean(lRanks, start, end);
        double cov = 0, fVar = 0, lVar = 0;
        for (int i = start; i < end; i++) {
            double fd = fRanks[i] - fMean;
            double ld = lRanks[i] - lMean;
            cov += fd * ld;
            fVar += fd * fd;
            lVar += ld * ld;
        }
        double denom = Math.sqrt(fVar * lVar);
        return denom == 0 ? 0 : cov / denom;
    }

    private double computeSharpe(double[] factor, int start, int end) {
        double[] ret = new double[end - start];
        int idx = 0;
        for (int i = start + 1; i < end; i++) {
            ret[idx++] = factor[i] - factor[i - 1];
        }
        double m = mean(ret, 0, ret.length);
        double s = std(ret, 0, ret.length);
        return s == 0 ? 0 : m / s * Math.sqrt(252);
    }

    private double computeTurnover(double[] factor, int start, int end) {
        int changes = 0;
        for (int i = start + 1; i < end; i++) {
            if (Math.signum(factor[i]) != Math.signum(factor[i - 1])) {
                changes++;
            }
        }
        return (double) changes / (end - start - 1);
    }

    private double computeTopRet(double[] factor, double[] label, int start, int end) {
        int n = end - start;
        if (n < 10) return 0;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) indices[i] = start + i;
        Arrays.sort(indices, Comparator.comparingDouble(i -> -factor[i]));
        int topN = Math.max(1, n / 5);
        double sum = 0;
        for (int i = 0; i < topN; i++) {
            sum += label[indices[i]];
        }
        return sum / topN;
    }

    private double computePearson(double[] a, double[] b, int start, int end) {
        double aMean = mean(a, start, end);
        double bMean = mean(b, start, end);
        double cov = 0, aVar = 0, bVar = 0;
        for (int i = start; i < end; i++) {
            double ad = a[i] - aMean;
            double bd = b[i] - bMean;
            cov += ad * bd;
            aVar += ad * ad;
            bVar += bd * bd;
        }
        double denom = Math.sqrt(aVar * bVar);
        return denom == 0 ? 0 : cov / denom;
    }

    private double[] ranks(double[] values, int start, int end) {
        int n = end - start;
        double[] sorted = new double[n];
        for (int i = 0; i < n; i++) sorted[i] = values[start + i];
        Arrays.sort(sorted);
        double[] result = new double[end];
        for (int i = start; i < end; i++) {
            int rank = 1;
            for (double v : sorted) {
                if (values[i] > v) rank++;
            }
            result[i] = rank;
        }
        return result;
    }

    private int countValid(double[] values, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            if (Double.isFinite(values[i])) count++;
        }
        return count;
    }

    private double mean(double[] v, int start, int end) {
        double s = 0; int n = end - start;
        for (int i = start; i < end; i++) s += v[i];
        return n == 0 ? 0 : s / n;
    }

    private double std(double[] v, int start, int end) {
        double m = mean(v, start, end), s = 0; int n = end - start;
        for (int i = start; i < end; i++) s += (v[i] - m) * (v[i] - m);
        return Math.sqrt(s / n);
    }
}
