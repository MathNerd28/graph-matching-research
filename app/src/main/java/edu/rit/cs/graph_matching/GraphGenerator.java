package edu.rit.cs.graph_matching;
import java.io.File;
import java.io.IOException;
import java.util.Random;

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
        int n = graph.size();

        if (degree >= n) {
            throw new IllegalArgumentException("Degree must be less than number of vertices");
        }

        for (int i = 0; i < n; i++) {
            for (int offset = 1; offset <= degree / 2; offset++) {
                int j = (i + offset) % n;
                graph.addEdge(i, j);
            }

            if (degree % 2 != 0) {
                if (n % 2 != 0) {
                    throw new IllegalArgumentException("Cannot create a regular graph with odd degree and odd number of vertices");
                }
                int opposite = (i + n / 2) % n;
                graph.addEdge(i, opposite);
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

            Graph randomGraph = GraphGenerator.generateRandomGraph(new SparseGraphImpl(5), 0.3);
            GraphUtils.generateDotFile(randomGraph, new File("randomGraph.dot"));

            Graph regularGraph = GraphGenerator.generateRegularGraph(new SparseGraphImpl(6), 4);
            GraphUtils.generateDotFile(regularGraph, new File("regularGraph.dot"));
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
