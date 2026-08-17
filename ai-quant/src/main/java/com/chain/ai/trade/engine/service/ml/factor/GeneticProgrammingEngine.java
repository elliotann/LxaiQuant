package com.chain.ai.trade.engine.service.ml.factor;

import com.chain.ai.trade.engine.config.MlProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
public class GeneticProgrammingEngine {

    private static final Logger log = LoggerFactory.getLogger(GeneticProgrammingEngine.class);
    private static final int MAX_INIT_DEPTH = 4;
    private static final double ELITE_RATIO = 0.02;

    private final MlProperties.FactorMining config;
    private final FactorEvaluator evaluator;

    public GeneticProgrammingEngine(MlProperties mlProperties, FactorEvaluator evaluator) {
        this.config = mlProperties.getFactorMining();
        this.evaluator = evaluator;
    }

    public interface EvolutionCallback {
        void onGeneration(int gen, int totalGen, Node best, double bestFitness, int populationSize);
        boolean isCancelled();
    }

    public static class EvolutionResult {
        private final int generations;
        private final Node bestIndividual;
        private final double bestFitness;
        private final List<FactorEvaluator.FactorEvalResult> history;

        public EvolutionResult(int generations, Node bestIndividual, double bestFitness,
                               List<FactorEvaluator.FactorEvalResult> history) {
            this.generations = generations;
            this.bestIndividual = bestIndividual;
            this.bestFitness = bestFitness;
            this.history = history;
        }

        public int getGenerations() { return generations; }
        public Node getBestIndividual() { return bestIndividual; }
        public double getBestFitness() { return bestFitness; }
        public List<FactorEvaluator.FactorEvalResult> getHistory() { return history; }
    }

    public EvolutionResult evolve(
            List<String> terminalNames,
            List<Operator> operators,
            Map<String, double[]> data,
            double[] labels,
            int populationSize,
            int generations,
            int tournamentSize,
            double crossoverProb,
            double mutationProb,
            double parsimonyCoefficient,
            FactorEvaluator.FitnessMetric fitnessMetric,
            int startBar,
            EvolutionCallback callback) {

        List<Individual> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            Node tree = createRandomTree(terminalNames, operators, MAX_INIT_DEPTH);
            double fit = evaluateFitness(tree, data, labels, fitnessMetric, parsimonyCoefficient, startBar);
            population.add(new Individual(tree, fit));
        }
        Collections.sort(population);

        Node bestTree = population.get(0).getTree().cloneTree();
        double bestFitness = population.get(0).getFitness();
        List<FactorEvaluator.FactorEvalResult> history = new ArrayList<>();
        history.add(evaluator.evaluate(bestTree, data, labels, fitnessMetric, parsimonyCoefficient, startBar));

        for (int gen = 0; gen < generations; gen++) {
            if (callback != null && callback.isCancelled()) {
                log.info("GP evolution cancelled at generation {}", gen);
                break;
            }
            List<Individual> newPopulation = new ArrayList<>(populationSize);
            int eliteCount = Math.max(1, (int) (populationSize * ELITE_RATIO));
            for (int i = 0; i < eliteCount; i++) {
                newPopulation.add(population.get(i).cloneIndividual());
            }
            while (newPopulation.size() < populationSize) {
                Individual parent1 = tournamentSelect(population, tournamentSize);
                Individual parent2 = tournamentSelect(population, tournamentSize);
                Node offspringTree;
                if (ThreadLocalRandom.current().nextDouble() < crossoverProb) {
                    offspringTree = crossover(parent1.getTree(), parent2.getTree());
                } else {
                    offspringTree = parent1.getTree().cloneTree();
                }
                if (ThreadLocalRandom.current().nextDouble() < mutationProb) {
                    mutate(offspringTree, terminalNames, operators);
                }
                double fit = evaluateFitness(offspringTree, data, labels, fitnessMetric, parsimonyCoefficient, startBar);
                newPopulation.add(new Individual(offspringTree, fit));
            }
            population = newPopulation;
            Collections.sort(population);
            if (population.get(0).getFitness() > bestFitness) {
                bestTree = population.get(0).getTree().cloneTree();
                bestFitness = population.get(0).getFitness();
            }
            history.add(evaluator.evaluate(bestTree, data, labels, fitnessMetric, parsimonyCoefficient, startBar));
            if (callback != null) {
                callback.onGeneration(gen + 1, generations, bestTree, bestFitness, populationSize);
            }
        }
        return new EvolutionResult(generations, bestTree, bestFitness, history);
    }

    public double evaluateFitness(Node tree, Map<String, double[]> data, double[] labels,
                                  FactorEvaluator.FitnessMetric metric, double parsimonyCoefficient, int startBar) {
        return evaluator.evaluate(tree, data, labels, metric, parsimonyCoefficient, startBar).getFitness();
    }

    public FactorEvaluator.FactorEvalResult evaluateFull(Node tree, Map<String, double[]> data, double[] labels,
                                                         FactorEvaluator.FitnessMetric metric,
                                                         double parsimonyCoefficient, int startBar) {
        return evaluator.evaluate(tree, data, labels, metric, parsimonyCoefficient, startBar);
    }

    public Node createRandomTree(List<String> terminalNames, List<Operator> operators, int maxDepth) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (maxDepth <= 1) {
            return createTerminal(terminalNames, rng);
        }
        Operator op = operators.get(rng.nextInt(operators.size()));
        List<Node> children = new ArrayList<>(op.getArity());
        for (int i = 0; i < op.getArity(); i++) {
            if (op.isTsOperator() && i == 1) {
                children.add(new ConstantNode(2 + rng.nextInt(30)));
            } else if (rng.nextDouble() < 0.3) {
                children.add(createTerminal(terminalNames, rng));
            } else {
                children.add(createRandomTree(terminalNames, operators, rng.nextInt(maxDepth - 1) + 1));
            }
        }
        return new FuncNode(op, children);
    }

    private Node createTerminal(List<String> terminalNames, ThreadLocalRandom rng) {
        if (rng.nextDouble() < 0.15) {
            return new ConstantNode(2 + rng.nextInt(30));
        }
        return new TerminalNode(terminalNames.get(rng.nextInt(terminalNames.size())));
    }

    private Individual tournamentSelect(List<Individual> population, int tournamentSize) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Individual best = null;
        for (int i = 0; i < tournamentSize; i++) {
            Individual candidate = population.get(rng.nextInt(population.size()));
            if (best == null || candidate.getFitness() > best.getFitness()) {
                best = candidate;
            }
        }
        return best;
    }

    public Node crossover(Node parent1, Node parent2) {
        Node p1 = parent1.cloneTree();
        List<Node> p1Nodes = collectNodes(p1);
        List<Node> p2Nodes = collectNodes(parent2);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Node subtree1 = p1Nodes.get(rng.nextInt(p1Nodes.size()));
        Node subtree2 = p2Nodes.get(rng.nextInt(p2Nodes.size()));
        return replaceNode(p1, subtree1, subtree2.cloneTree());
    }

    public void mutate(Node node, List<String> terminalNames, List<Operator> operators) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<Node> nodes = collectNodes(node);
        Node target = nodes.get(rng.nextInt(nodes.size()));
        Node replacement;
        if (target instanceof TerminalNode) {
            if (rng.nextDouble() < 0.3) {
                replacement = new ConstantNode(2 + rng.nextInt(30));
            } else {
                replacement = new TerminalNode(terminalNames.get(rng.nextInt(terminalNames.size())));
            }
        } else if (target instanceof ConstantNode) {
            if (rng.nextDouble() < 0.5) {
                replacement = createTerminal(terminalNames, rng);
            } else {
                double v = ((ConstantNode) target).getValue() + (rng.nextDouble() - 0.5) * 5;
                replacement = new ConstantNode(Math.max(2, Math.round(v)));
            }
        } else if (target instanceof FuncNode) {
            if (rng.nextDouble() < 0.5) {
                Operator newOp = operators.get(rng.nextInt(operators.size()));
                FuncNode ft = (FuncNode) target;
                List<Node> newChildren = new ArrayList<>();
                if (newOp.getArity() == ft.getChildren().size()) {
                    for (Node c : ft.getChildren()) {
                        newChildren.add(c.cloneTree());
                    }
                } else {
                    for (int i = 0; i < newOp.getArity(); i++) {
                        newChildren.add(createTerminal(terminalNames, rng));
                    }
                }
                replacement = new FuncNode(newOp, newChildren);
            } else {
                replacement = createTerminal(terminalNames, rng);
            }
        } else {
            replacement = createTerminal(terminalNames, rng);
        }
        replaceNode(node, target, replacement);
    }

    private List<Node> collectNodes(Node root) {
        List<Node> result = new ArrayList<>();
        collectRecursive(root, result);
        return result;
    }

    private void collectRecursive(Node node, List<Node> result) {
        result.add(node);
        if (node instanceof FuncNode) {
            for (Node child : ((FuncNode) node).getChildren()) {
                collectRecursive(child, result);
            }
        }
    }

    private Node replaceNode(Node root, Node target, Node replacement) {
        if (root == target) return replacement;
        replaceRecursive(root, target, replacement);
        return root;
    }

    private void replaceRecursive(Node node, Node target, Node replacement) {
        if (node instanceof FuncNode) {
            FuncNode fn = (FuncNode) node;
            List<Node> children = fn.getChildren();
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i) == target) {
                    children.set(i, replacement);
                    return;
                }
                replaceRecursive(children.get(i), target, replacement);
            }
        }
    }
}
