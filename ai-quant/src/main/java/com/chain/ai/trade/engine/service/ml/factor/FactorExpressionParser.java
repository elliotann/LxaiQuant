package com.chain.ai.trade.engine.service.ml.factor;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FactorExpressionParser {

    public String toExpression(Node node) {
        return node.toExpression();
    }

    public String toLatex(Node node) {
        return node.toLatex();
    }

    public Node parse(String expression, List<String> terminalNames, List<Operator> operators) {
        expression = expression.trim();
        if (isTerminal(expression, terminalNames)) {
            return new TerminalNode(expression);
        }
        if (isConstant(expression)) {
            return new ConstantNode(Double.parseDouble(expression));
        }
        for (Operator op : operators) {
            if (op.isTsOperator()) {
                Pattern p = Pattern.compile("^" + op.getSymbol() + "\\((.+),\\s*(\\d+)\\)$");
                Matcher m = p.matcher(expression);
                if (m.find()) {
                    Node inner = parse(m.group(1).trim(), terminalNames, operators);
                    int window = Integer.parseInt(m.group(2));
                    return new FuncNode(op, Arrays.asList(inner, new ConstantNode(window)));
                }
            }
            if (op.getArity() == 1) {
                Pattern p = Pattern.compile("^" + op.getSymbol() + "\\((.+)\\)$");
                Matcher m = p.matcher(expression);
                if (m.find()) {
                    Node inner = parse(m.group(1).trim(), terminalNames, operators);
                    return new FuncNode(op, Collections.singletonList(inner));
                }
            }
        }
        for (Operator op : operators) {
            if (op.getArity() == 2 && !op.isTsOperator()) {
                int parenDepth = 0;
                int splitPos = -1;
                for (int i = expression.length() - 1; i >= 0; i--) {
                    char c = expression.charAt(i);
                    if (c == ')') parenDepth++;
                    else if (c == '(') parenDepth--;
                    else if (parenDepth == 0 && c == op.getSymbol().charAt(0)) {
                        if (op == Operator.SUB && i > 0 && !isOperatorChar(expression.charAt(i - 1))) {
                            splitPos = i;
                            break;
                        } else if (op != Operator.SUB) {
                            if (i > 0 && isOperatorChar(expression.charAt(i - 1))) continue;
                            splitPos = i;
                            break;
                        }
                    }
                }
                if (splitPos > 0) {
                    String left = expression.substring(0, splitPos).trim();
                    String right = expression.substring(splitPos + 1).trim();
                    if (left.startsWith("(") && left.endsWith(")")) {
                        left = left.substring(1, left.length() - 1).trim();
                    }
                    if (right.startsWith("(") && right.endsWith(")")) {
                        right = right.substring(1, right.length() - 1).trim();
                    }
                    Node leftNode = parse(left, terminalNames, operators);
                    Node rightNode = parse(right, terminalNames, operators);
                    return new FuncNode(op, Arrays.asList(leftNode, rightNode));
                }
            }
        }
        if (expression.startsWith("(") && expression.endsWith(")")) {
            return parse(expression.substring(1, expression.length() - 1).trim(), terminalNames, operators);
        }
        throw new IllegalArgumentException("Cannot parse expression: " + expression);
    }

    private boolean isTerminal(String s, List<String> terminalNames) {
        return terminalNames.contains(s);
    }

    private boolean isConstant(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isOperatorChar(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/';
    }
}
