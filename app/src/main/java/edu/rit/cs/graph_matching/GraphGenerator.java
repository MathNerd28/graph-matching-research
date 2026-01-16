package edu.rit.cs.graph_matching;

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

    public static Graph generateRandomGraph(int vertices) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static Graph generateRegularGraph(int vertices, int degree) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static Graph generateBipartiteGraph(int leftVertices, int rightVertices) {
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
