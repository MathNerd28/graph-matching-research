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
     * This function builds a random graph by 
     * considering every possible pair of vertices 
     * and adding an edge between them with a fixed 
     * probability.
     *
     * @param vertices 
     *      number of vertices
     * @param edgeProb 
     *      probability of adding an edge between any pair
     * @return the same graph instance
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

    /**
     * Generates a regular graph with a specific degree.
     * This function generates a regular graph where every 
     * vertex has the same degree. It connects each vertex 
     * to nearby vertices in a circular pattern. If the 
     * degree is odd, it also connects each vertex to the 
     * one directly opposite it, which only works when the 
     * total number of vertices is even.
     * 
     * @param graph
     *      the graph to edit in-place
     * @param degree
     *     the desired degree of each vertex
     * @return the same graph instance
     */
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

    /**
     * Mutates a regular graph by performing a double-edge swap.
     * This function randomly changes a regular graph by picking 
     * two edges and swapping their endpoints, while keeping all 
     * vertex degrees the same. It skips invalid choices like 
     * shared vertices or duplicate edges, and once a valid swap 
     * is found, it performs it and exits. This process is repeated
     * a specific amount of this.
     * 
     * @param graph
     *    the graph to mutate in-place
     * @param mutationCount
     *   number of mutations to perform
     */
    public static void mutateRegularGraph(MutableGraph graph, int mutationCount) {
        Random rand = new Random();
        int n = graph.size();

        for (int i = 0; i < mutationCount; i++) {
            while (true) {
                int u = rand.nextInt(n);
                int v = graph.getRandomNeighbor(u);

                int x = rand.nextInt(n);
                int y = graph.getRandomNeighbor(x);

                if (u == x || u == y || v == x || v == y) {
                    continue;
                }

                if (graph.hasEdge(u, y) || graph.hasEdge(x, v)) {
                    continue;
                }

                graph.removeEdge(u, v);
                graph.removeEdge(x, y);
                graph.addEdge(u, y);
                graph.addEdge(x, v);
                break;
            }
        }
    }

    /**
     * Generates a bipartite graph with a specific window size.
     * This function builds a bipartite graph with equal sized 
     * left and right sides. Each left vertex is connected to 
     * a specific amount of right vertices, ensuring every vertex 
     * on both sides has the same number of edges.
     * 
     * @param graph
     *    the graph to edit in-place
     * @param verticesPerSide
     *   number of vertices on each side
     * @param degree
     *  the desired degree of each vertex
     * @return the same graph instance
     */
    public static MutableGraph generateBipartiteGraph(MutableGraph graph, int verticesPerSide, int degree) {
        graph.clear();

        if (degree > verticesPerSide) {
            throw new IllegalArgumentException("Degree cannot exceed the number of vertices per side");
        }

        int offset = verticesPerSide;

        for (int i = 0; i < verticesPerSide; i++) {
            for (int w = 0; w < degree; w++) {
                int j = (i + w) % verticesPerSide;
                graph.addEdge(i, offset + j);
            }
        }

        return graph;
    }

    /**
     * Mutates a bipartite graph by performing a double-edge swap.
     * This function performs a series of double-edge swaps on a 
     * bipartite graph to randomly change connections while keeping 
     * it bipartite.
     * 
     * @param graph
     *    the graph to mutate in-place
     * @param leftVertices
     *   number of left side vertices
     * @param mutationCount
     *  number of mutations to perform
     */
    public static void mutateBipartiteGraph(MutableGraph graph, int leftVertices, int mutationCount) {
        Random rand = new Random();
        
        for (int i = 0; i < mutationCount; i++) {
            while (true) {
                int left1 = rand.nextInt(leftVertices);
                int left2 = rand.nextInt(leftVertices);
                if (left1 == left2) {
                    continue;
                }

                int right1 = graph.getRandomNeighbor(left1);
                int right2 = graph.getRandomNeighbor(left2);

                if (right1 < leftVertices || right2 < leftVertices) {
                    continue;
                }
                if (right1 == right2) {
                    continue;
                }

                if (graph.hasEdge(left1, right2) || graph.hasEdge(left2, right1)) {
                    continue;
                }

                graph.removeEdge(left1, right1);
                graph.removeEdge(left2, right2);
                graph.addEdge(left1, right2);
                graph.addEdge(left2, right1);

                break;
            }
        }
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

            GraphGenerator.mutateRegularGraph((MutableGraph) regularGraph, 100);
            GraphUtils.generateDotFile(regularGraph, new File("regularGraphMutated.dot"));

            Graph bipartiteGraph = GraphGenerator.generateBipartiteGraph(new SparseGraphImpl(8), 4, 2);
            GraphUtils.generateDotFile(bipartiteGraph, new File("bipartiteGraph.dot"));
            
            GraphGenerator.mutateBipartiteGraph((MutableGraph) bipartiteGraph, 4, 1000);
            GraphUtils.generateDotFile(bipartiteGraph, new File("bipartiteGraphMutated.dot"));
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}
