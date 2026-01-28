package edu.rit.cs.graph_matching;

import java.util.ArrayList;
import java.util.List;
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
     * Generates a graph with a specific degree sequence. It 
     * creates a list of vertex stubs according to their 
     * degrees, shuffles them, and pairs them to form edges 
     * while avoiding self-loops and duplicates. Any conflicts 
     * are resolved by swapping edges in the graph, ensuring 
     * the final graph matches the specified degree sequence.
     * 
     * @param graph
     *      the graph to edit in-place
     * @param degrees
     *      the desired degree sequence
     * @param random
     *      random number generator
     * @return the same graph instance
     */
    public static MutableGraph generateGraph(MutableGraph graph, int[] degrees, Random random) {
        int totalStubs = 0;
        for (int d : degrees) {
            totalStubs += d;
        }

        if ((totalStubs % 2) != 0) {
            throw new IllegalArgumentException("Sum of degrees must be even");
        }

        int[] edgeConnections = new int[totalStubs];
        int index = 0;
        for (int v = 0; v < graph.size(); v++) {
            for (int d = 0; d < degrees[v]; d++) {
                edgeConnections[index] = v;
                index++;
            }
        }

        for (int i = edgeConnections.length - 1; i > 0; i--) {
            int i2 = random.nextInt(i);
            int tmp = edgeConnections[i];
            edgeConnections[i] = edgeConnections[i2];
            edgeConnections[i2] = tmp;
        }

        graph.clear();
        List<Edge> conflictEdges = new ArrayList<>();
        for (int i = 0; i < edgeConnections.length; i += 2) {
            int v1 = edgeConnections[i];
            int v2 = edgeConnections[i + 1];
            if (v1 != v2 && !graph.hasEdge(v1, v2)) {
                graph.addEdge(v1, v2);
            } else {
                conflictEdges.add(new Edge(v1, v2));
            }
        }

        for (Edge e : conflictEdges) {
            int v1 = e.vertex1();
            int v2 = e.vertex2();
            while (true) {
                int w1 = random.nextInt(graph.size());
                if (w1 == v1 || w1 == v2) {
                    continue;
                }

                int w2 = graph.getRandomNeighbor(w1);
                if (w2 == -1 || w2 == v1 || w2 == v2) {
                    continue;
                }

                if (!graph.hasEdge(v1, w1) && !graph.hasEdge(v2, w2)) {
                    graph.removeEdge(w1, w2);
                    graph.addEdge(v1, w1);
                    graph.addEdge(v2, w2);
                    break;
                } else if (!graph.hasEdge(v1, w2) && !graph.hasEdge(v2, w1)) {
                    graph.removeEdge(w1, w2);
                    graph.addEdge(v1, w2);
                    graph.addEdge(v2, w1);
                    break;
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
     *      the desired degree of each vertex
     * @return the same graph instance
     */
    public static MutableGraph generateRegularGraph(MutableGraph graph, int degree) {
        graph.clear();
        int n = graph.size();

        if (degree >= n) {
            throw new IllegalArgumentException("Degree must be less than number of vertices");
        }

        if (degree % 2 != 0 && n % 2 != 0) {
            throw new IllegalArgumentException("Cannot create a regular graph with odd degree and odd number of vertices");
        }

        for (int i = 0; i < n; i++) {
            for (int offset = 1; offset <= degree / 2; offset++) {
                int j = (i + offset) % n;
                graph.addEdge(i, j);
            }

            if (degree % 2 != 0) {
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
     *      the graph to mutate in-place
     * @param mutationCount
     *      number of mutations to perform
     */
    public static void mutateRegularGraph(MutableGraph graph, int mutationCount) {
        Random rand = new Random();
        int n = graph.size();

        int mutations = 0;
        while (mutations < mutationCount) {
            int u = rand.nextInt(n);
            int v = graph.getRandomNeighbor(u);

            int x = rand.nextInt(n);
            int y = graph.getRandomNeighbor(x);

            if (u == x || u == y || v == x || v == y) {
                continue;
            }

            if (!graph.hasEdge(u, y) && !graph.hasEdge(x, v)) {
                graph.removeEdge(u, v);
                graph.removeEdge(x, y);
                graph.addEdge(u, y);
                graph.addEdge(x, v);
                mutations++;
            }
            else if (!graph.hasEdge(u, x) && !graph.hasEdge(v, y)) {
                graph.removeEdge(u, v);
                graph.removeEdge(x, y);
                graph.addEdge(u, x);
                graph.addEdge(v, y);
                mutations++;
            }
        }
    }

    /**
     * Generates a regular bipartite graph with a specific window size.
     * This function builds a bipartite graph with equal sized
     * left and right sides. Each left vertex is connected to
     * a specific amount of right vertices, ensuring every vertex
     * on both sides has the same number of edges.
     *
     * @param graph
     *      the graph to edit in-place
     * @param verticesPerSide
     *      number of vertices on each side
     * @param degree
     *      the desired degree of each vertex
     * @return the same graph instance
     */
    public static MutableGraph generateRegularBipartiteGraph(MutableGraph graph, int verticesPerSide, int degree) {
        graph.clear();

        if (degree > verticesPerSide) {
            throw new IllegalArgumentException("Degree cannot exceed the number of vertices per side");
        }

        for (int i = 0; i < verticesPerSide; i++) {
            for (int w = 0; w < degree; w++) {
                int j = (i + w) % verticesPerSide;
                graph.addEdge(i, verticesPerSide + j);
            }
        }

        return graph;
    }

    /**
     * Generates a bipartite graph with a specific degree sequence. 
     * It creates two list of vertex stubs (one of left and one for right) 
     * according to their degrees, shuffles them, and pairs one from each 
     * list to form edges while avoiding self-loops and duplicates. Any 
     * conflicts are resolved by swapping edges in the graph, ensuring 
     * the final graph matches the specified degree sequence.
     * 
     * @param graph
     *      the graph to edit in-place
     * @param verticesPerSide
     *      number of vertices on each side
     * @param degree
     *      the desired degree sequence
     * @param random
     *      random number generator
     * @return the same graph instance
     */
    public static MutableGraph generateBipartiteGraph(MutableGraph graph, int leftVerticesCount, int rightVerticesCount, int[] leftDegrees, int[] rightDegrees, Random random) {
        int leftStubTotal = 0;
        for (int i = 0; i < leftVerticesCount; i++) {
            leftStubTotal += leftDegrees[i];
        }

        int rightStubTotal = 0;
        for (int i = 0; i < rightVerticesCount; i++) {
            rightStubTotal += rightDegrees[i];
        }

        if (leftStubTotal != rightStubTotal) {
            throw new IllegalArgumentException("Left and right degree sums must match");
        }

        int leftStub[] = new int[leftStubTotal];
        int rightStub[] = new int[rightStubTotal];

        int leftIndex = 0;
        for (int i = 0; i < leftVerticesCount; i++) {
            for (int d = 0; d < leftDegrees[i]; d++) {
                leftStub[leftIndex] = i;
                leftIndex++;
            }
        }

        int rightIndex = 0;
        for (int i = 0; i < rightVerticesCount; i++) {
            for (int d = 0; d < rightDegrees[i]; d++) {
                rightStub[rightIndex] = i + leftVerticesCount;
                rightIndex++;
            }
        }

        for (int i = leftStub.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = leftStub[i];
            leftStub[i] = leftStub[j];
            leftStub[j] = temp;

            j = random.nextInt(i + 1);
            temp = rightStub[i];
            rightStub[i] = rightStub[j];
            rightStub[j] = temp;
        }

        graph.clear();
        List<Edge> conflictEdges = new ArrayList<>();
        for (int i = 0; i < leftStub.length; i++) {
            int v1 = leftStub[i];
            int v2 = rightStub[i];
            if (!graph.hasEdge(v1, v2)) {
                graph.addEdge(v1, v2);
            } else {
                conflictEdges.add(new Edge(v1, v2));
            }
        }

        for (Edge e : conflictEdges) {
            int v1 = e.vertex1();
            int v2 = e.vertex2();
            while (true) {
                int w1 = random.nextInt(leftVerticesCount);
                if (w1 == v1) {
                    continue;
                }

                int w2 = graph.getRandomNeighbor(w1);
                if (w2 == -1 || w2 == v2 || w2 < leftVerticesCount) {
                    continue;
                }

                if (!graph.hasEdge(v1, w2) && !graph.hasEdge(v2, w1)) {
                    graph.removeEdge(w1, w2);
                    graph.addEdge(v1, w2);
                    graph.addEdge(v2, w1);
                    break;
                }
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
     *      the graph to mutate in-place
     * @param leftVertices
     *      number of left side vertices
     * @param mutationCount
     *      number of mutations to perform
     */
    public static void mutateBipartiteGraph(MutableGraph graph, int leftVertices, int mutationCount) {
        Random rand = new Random();

        int mutations = 0;
        while (mutations < mutationCount) {
            int left1 = rand.nextInt(leftVertices);
            int left2 = rand.nextInt(leftVertices);
            if (left1 == left2) {
                continue;
            }

            int right1 = graph.getRandomNeighbor(left1);
            int right2 = graph.getRandomNeighbor(left2);

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

            mutations++;
        }

    }

    /**
     * Irregularizes a graph by randomly adding or removing edges.
     * This function modifies a graph by randomly adding or removing
     * edges based on a given probability, making the graph less regular.
     *
     * @param graph
     *      the graph to irregularize in-place
     * @param p
     *      the probability of adding or removing each edge
     */
    public static void irregularizeGraph(MutableGraph graph, double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Probability must be between 0 and 1");
        }

        Random rand = new Random();
        int n = graph.size();

        for (int u = 0; u < n; u++) {
            for (int v = u + 1; v < n; v++) {
                if (rand.nextDouble() >= p) {
                    continue;
                }

                if (graph.hasEdge(u, v)) {
                    graph.removeEdge(u, v);
                } else {
                    graph.addEdge(u, v);
                }
            }
        }
    }

    /**
     * Irregularizes a bipartite graph by randomly adding or removing edges.
     * This function modifies a bipartite graph by randomly adding or removing
     * edges based on a given probability, making the graph less regular.
     *
     * @param graph
     *      the graph to irregularize in-place
     * @param leftVertices
     *      number of left side vertices
     * @param p
     *      the probability of adding or removing each edge
     */
    public static void irregularizeBipartiteGraph(MutableGraph graph, int leftVertices, double p) {
        if (p < 0.0 || p > 1.0) {
            throw new IllegalArgumentException("Probability must be between 0 and 1");
        }

        Random rand = new Random();
        int n = graph.size();

        for (int u = 0; u < leftVertices; u++) {
            for (int v = leftVertices; v < n; v++) {
                if (rand.nextDouble() >= p) {
                    continue;
                }

                if (graph.hasEdge(u, v)) {
                    graph.removeEdge(u, v);
                } else {
                    graph.addEdge(u, v);
                }
            }
        }
    }
}
