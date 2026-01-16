package edu.rit.cs.graph_matching;
import java.util.Random;

import java.io.File;
import java.io.IOException;

public class GraphGenerator {
    private GraphGenerator() {}

    /**
     * Generates a star graph with the given number of edges. Matchings: leaves
     * + 1 - Empty Set - Single Edge Maximum Matchings: 1
     *
     * @param graph
     *     the graph to edit in-place
     * @return the same graph instance
     */
    public static MutableGraph generateStarGraph(MutableGraph graph) {
        graph.clear();
        for (int i = 1; i < graph.size(); i++) {
            graph.addEdge(0, i);
        }
        return graph;
    }

    /**
     * Generates an edited star graph to have the specific maxMatching.
     *
     * @param graph
     *     the graph to edit in-place
     * @param maxMatching
     *     the desired size of the maximum matching
     * @return the same graph instance
     */
    public static MutableGraph generateStarGraphWithMatching(MutableGraph graph, int maxMatching) {
        if (maxMatching < 1 || maxMatching > (graph.size() + 1) / 2) {
            throw new IllegalArgumentException(
                    "Invalid maximum matching size for given graph size");
        }

        generateStarGraph(graph);

        int edgesToAdd = maxMatching - 1;
        for (int i = 0; i < edgesToAdd; i++) {
            graph.addEdge(i * 2 + 1, i * 2 + 2);
        }

        // potentially add additional redundant edges that don't affect maximum
        // matching

        return graph;
    }

    /**
     * Generates a random graph.
     *
     * @param vertices number of vertices
     * @param edgeProb probability of adding an edge between any pair
     * @return random graph
     */
    public static MutableGraph generateRandomGraph(MutableGraph graph, double edgeProb) {
        graph.clear();
        Random random = new Random();
        if (edgeProb < 0.0 || edgeProb > 1.0) {
            throw new IllegalArgumentException("edgeProb must be between 0.0 and 1.0");
        }

        for (int u = 0; u < graph.size(); u++) {
            for (int v = u + 1; v < graph.size(); v++) {
                if (random.nextDouble() < edgeProb) {
                    graph.addEdge(u, v);
                }
            }
        }

        return graph;
    }

    public static MutableGraph generateRegularGraph(MutableGraph graph, int degree) {
        graph.clear();

        if (degree >= graph.size() || degree % 2 != 0) {
            throw new IllegalArgumentException("Degree must be even and greater than the number of vertices");
        }

        for (int i = 0; i < graph.size(); i++) {
            for (int offset = 1; offset <= degree / 2; offset++) {
                int j = (i + offset) % graph.size();
                graph.addEdge(i, j);
            }
        }

        return graph;
    }

    public static MutableGraph generateBipartiteGraph(MutableGraph graph, int leftVertices, int rightVertices) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static void main(String[] args) {
        try {
            Graph starGraph = GraphGenerator.generateStarGraph(new SparseGraphImpl(100));
            GraphUtils.generateDotFile(starGraph, new File("star.dot"));

            Graph starGraphWithMatching = GraphGenerator.generateStarGraphWithMatching(new SparseGraphImpl(8), 3);
            GraphUtils.generateDotFile(starGraphWithMatching, new File("star_with_matching.dot"));
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
