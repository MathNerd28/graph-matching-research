package edu.rit.cs.graph_matching.runner;

import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

import edu.rit.cs.graph_matching.graph.Graph;

/**
 * A wrapper that tracks how many times graph methods are called.
 */
public class GraphStatistics implements Graph, Statistics {
    /** The backing graph */
    private final Graph source;

    /** The number of times {@link #size()} has been called */
    private int sizeCheckCount;
    /** The number of times {@link #hasEdge(int, int)} has been called */
    private int edgeCheckCount;
    /** The number of times {@link #getDegree(int)} has been called */
    private int degreeCheckCount;
    /** The number of times {@link #getRandomNeighbor(int)} has been called */
    private int randomNeighborCount;
    /** The number of times {@link #getAllNeighbors(int)} has been called */
    private int allNeighborsCount;

    /**
     * Construct a graph statistics wrapper.
     *
     * @param source
     *     the backing graph
     */
    public GraphStatistics(Graph source) {
        this.source = source;
    }

    @Override
    public int size() {
        sizeCheckCount++;
        return source.size();
    }

    @Override
    public int getDegree(int vertex) {
        degreeCheckCount++;
        return source.getDegree(vertex);
    }

    /**
     * @inheritdoc Track the number of times this method was called using
     *     {@link #getEdgeCheckCount()}.
     */
    @Override
    public boolean hasEdge(int vertex1, int vertex2) {
        edgeCheckCount++;
        return source.hasEdge(vertex1, vertex2);
    }

    /**
     * @inheritdoc Track the number of times this method was called using
     *     {@link #getRandomNeighborCount()}.
     */
    @Override
    public int getRandomNeighbor(int vertex, RandomGenerator random) {
        randomNeighborCount++;
        return source.getRandomNeighbor(vertex, random);
    }

    /**
     * @inheritdoc Track the number of times this method was called using
     *     {@link #getAllNeighborsCount()}.
     */
    @Override
    public Set<Integer> getAllNeighbors(int vertex) {
        allNeighborsCount++;
        return source.getAllNeighbors(vertex);
    }

    /**
     * @return the graph backing this statistics wrapper
     */
    public Graph getSource() {
        return source;
    }

    /**
     * Reset all statistics counters to 0.
     */
    @Override
    public void reset() {
        sizeCheckCount = 0;
        edgeCheckCount = 0;
        degreeCheckCount = 0;
        randomNeighborCount = 0;
        allNeighborsCount = 0;
    }

    @Override
    public Map<String, Object> getStatistics() {
        return Map.of("getAllNeighbors()", allNeighborsCount, "getDegree(v)", degreeCheckCount,
                "hasEdge(v1,v2)", edgeCheckCount, "getRandomNeighbor(v)", randomNeighborCount,
                "size()", sizeCheckCount);
    }
}
