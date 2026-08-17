package com.chain.ai.trade.engine.service.ml.factor;

public class Individual implements Comparable<Individual> {
    private final Node tree;
    private double fitness;

    public Individual(Node tree) {
        this.tree = tree;
        this.fitness = Double.NEGATIVE_INFINITY;
    }

    public Individual(Node tree, double fitness) {
        this.tree = tree;
        this.fitness = fitness;
    }

    public Node getTree() { return tree; }
    public double getFitness() { return fitness; }
    public void setFitness(double fitness) { this.fitness = fitness; }

    public Individual cloneIndividual() {
        return new Individual(tree.cloneTree(), fitness);
    }

    @Override
    public int compareTo(Individual o) {
        return Double.compare(o.fitness, this.fitness);
    }
}
