package com.chain.ai.trade.engine.service.ml.factor;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

public enum Operator {
    ADD("+", 2, false),
    SUB("-", 2, false),
    MUL("*", 2, false),
    DIV("/", 2, false),
    NEG("neg", 1, false),
    SQRT("sqrt", 1, false),
    LOG("log", 1, false),
    ABS("abs", 1, false),
    SIGN("sign", 1, false),
    MAX("max", 2, false),
    MIN("min", 2, false),
    TS_SUM("ts_sum", 2, true),
    TS_MEAN("ts_mean", 2, true),
    TS_STD("ts_std", 2, true),
    TS_MAX("ts_max", 2, true),
    TS_MIN("ts_min", 2, true),
    TS_MEDIAN("ts_median", 2, true);

    private final String symbol;
    private final int arity;
    private final boolean tsOperator;

    Operator(String symbol, int arity, boolean tsOperator) {
        this.symbol = symbol;
        this.arity = arity;
        this.tsOperator = tsOperator;
    }

    public String getSymbol() { return symbol; }
    public int getArity() { return arity; }
    public boolean isTsOperator() { return tsOperator; }

    public double apply(double... args) {
        if (args.length != arity) {
            throw new IllegalArgumentException("Operator " + symbol + " expects " + arity + " args, got " + args.length);
        }
        switch (this) {
            case ADD: return args[0] + args[1];
            case SUB: return args[0] - args[1];
            case MUL: return args[0] * args[1];
            case DIV: return args[1] == 0 ? 0 : args[0] / args[1];
            case NEG: return -args[0];
            case SQRT: return args[0] <= 0 ? 0 : Math.sqrt(args[0]);
            case LOG: return args[0] <= 0 ? 0 : Math.log1p(args[0]);
            case ABS: return Math.abs(args[0]);
            case SIGN: return Math.signum(args[0]);
            case MAX: return Math.max(args[0], args[1]);
            case MIN: return Math.min(args[0], args[1]);
            case TS_SUM:
            case TS_MEAN:
            case TS_STD:
            case TS_MAX:
            case TS_MIN:
            case TS_MEDIAN:
                throw new UnsupportedOperationException("TS operators require series data, use TsFuncNode");
            default: throw new UnsupportedOperationException("Unknown operator: " + this);
        }
    }

    public static Operator fromSymbol(String symbol) {
        for (Operator op : values()) {
            if (op.symbol.equals(symbol)) return op;
        }
        throw new IllegalArgumentException("Unknown operator symbol: " + symbol);
    }
}
