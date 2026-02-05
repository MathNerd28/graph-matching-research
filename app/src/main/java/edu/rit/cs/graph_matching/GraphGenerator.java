package edu.rit.cs.graph_matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

public final class GraphGenerator {
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
     * Generates a random graph. This function builds a random graph by
     * considering every possible pair of vertices and adding an edge between
     * them with a fixed probability.
     *
     * @param vertices
     *     number of vertices
     * @param edgeProb
     *     probability of adding an edge between any pair
     * @return the same graph instance
     */
    public static MutableGraph generateRandomGraph(MutableGraph graph, double edgeProb,
                                                   RandomGenerator random) {
        if (edgeProb < 0.0 || edgeProb > 1.0) {
            throw new IllegalArgumentException("edgeProb must be between 0.0 and 1.0");
        }

        graph.clear();
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
     * Generates a graph with a specific degree sequence. It creates a list of
     * vertex stubs according to their degrees, shuffles them, and pairs them to
     * form edges while avoiding self-loops and duplicates. Any conflicts are
     * resolved by swapping edges in the graph, ensuring the final graph matches
     * the specified degree sequence. Note: This method may run slower (jump
     * from polynomial to exponential runtime) for dense graphs (graphs with an
     * average degree greater than 50% of total vertices).
     *
     * @param graph
     *     the graph to edit in-place
     * @param degrees
     *     the desired degree sequence
     * @param random
     *     random number generator
     * @return the same graph instance
     */
    public static MutableGraph generateGraph(MutableGraph graph, int[] degrees, Random random) {
        long totalStubs = 0;
        for (int d : degrees) {
            totalStubs += d;
        }

        if (!GraphUtils.isGraphical(degrees)) {
            throw new IllegalArgumentException("The given degree sequence is not graphical");
        }

        LongIntArray edgeConnections = new LongIntArray(totalStubs);
        long index = 0;
        for (int v = 0; v < graph.size(); v++) {
            for (int d = 0; d < degrees[v]; d++) {
                edgeConnections.set(index, v);
                index++;
            }
        }

        graph.clear();
        List<Edge> conflictEdges = new ArrayList<>();

        do {
            conflictEdges.clear();
            shuffle(edgeConnections, random);

            for (long i = 0; i < edgeConnections.size; i += 2) {
                int v1 = edgeConnections.get(i);
                int v2 = edgeConnections.get(i + 1);
                if (v1 != v2 && !graph.hasEdge(v1, v2)) {
                    // no conflict
                    graph.addEdge(v1, v2);
                    continue;
                }

                int w1 = random.nextInt(graph.size());
                if (w1 == v1 || w1 == v2) {
                    // bad generation; conflict
                    conflictEdges.add(new Edge(v1, v2));
                    continue;
                }

                int w2 = graph.getRandomNeighbor(w1, random);
                if (w2 == -1 || w2 == v1 || w2 == v2) {
                    // bad generation; conflict
                    conflictEdges.add(new Edge(v1, v2));
                    continue;
                }

                if (!graph.hasEdge(v1, w1) && !graph.hasEdge(v2, w2)) {
                    // mutation fixes conflict
                    graph.removeEdge(w1, w2);
                    graph.addEdge(v1, w1);
                    graph.addEdge(v2, w2);
                } else if (!graph.hasEdge(v1, w2) && !graph.hasEdge(v2, w1)) {
                    // mutation fixes conflict
                    graph.removeEdge(w1, w2);
                    graph.addEdge(v1, w2);
                    graph.addEdge(v2, w1);
                } else {
                    // bad generation; conflict
                    conflictEdges.add(new Edge(v1, v2));
                }
            }

            // reshuffle conflicts
            edgeConnections = new LongIntArray(conflictEdges.size() * 2);
            for (int i = 0; i < conflictEdges.size(); i++) {
                Edge e = conflictEdges.get(i);
                edgeConnections.set(2 * i, e.vertex1());
                edgeConnections.set(2 * i + 1, e.vertex2());
            }
        } while (!conflictEdges.isEmpty());

        return graph;
    }

    /**
     * Generates a bipartite graph with a specific degree sequence. It creates
     * two list of vertex stubs (one of left and one for right) according to
     * their degrees, shuffles them, and pairs one from each list to form edges
     * while avoiding self-loops and duplicates. Any conflicts are resolved by
     * swapping edges in the graph, ensuring the final graph matches the
     * specified degree sequence.
     *
     * @param graph
     *     the graph to edit in-place
     * @param verticesPerSide
     *     number of vertices on each side
     * @param degree
     *     the desired degree sequence
     * @param random
     *     random number generator
     * @return the same graph instance
     */
    public static MutableGraph generateBipartiteGraph(MutableGraph graph, int[] leftDegrees,
                                                      int[] rightDegrees, RandomGenerator random) {
        int leftVerticesCount = leftDegrees.length;
        int rightVerticesCount = rightDegrees.length;
        if (graph.size() != leftVerticesCount + rightVerticesCount) {
            throw new IllegalArgumentException(
                    "Degree sequence size does not add up to the graph size");
        }
        
        long leftStubTotal = 0;
        for (int i = 0; i < leftVerticesCount; i++) {
            leftStubTotal += leftDegrees[i];
        }

        long rightStubTotal = 0;
        for (int i = 0; i < rightVerticesCount; i++) {
            rightStubTotal += rightDegrees[i];
        }

        if (leftStubTotal != rightStubTotal) {
            throw new IllegalArgumentException("The given degree sequences are not bigraphical");
        }

        int[] combinedDegrees = new int[leftVerticesCount + rightVerticesCount];
        System.arraycopy(leftDegrees, 0, combinedDegrees, 0, leftVerticesCount);
        System.arraycopy(rightDegrees, 0, combinedDegrees, leftVerticesCount, rightVerticesCount);
        if (!GraphUtils.isGraphical(combinedDegrees)) {
            // Havel-Hakimi is necessary but not sufficient for bipartite graphs
            throw new IllegalArgumentException("The given degree sequences are not bigraphical");
        }

        LongIntArray leftStub = new LongIntArray(leftStubTotal);
        LongIntArray rightStub = new LongIntArray(rightStubTotal);

        long leftIndex = 0;
        for (int i = 0; i < leftVerticesCount; i++) {
            for (int d = 0; d < leftDegrees[i]; d++) {
                leftStub.set(leftIndex , i);
                leftIndex++;
            }
        }

        long rightIndex = 0;
        for (int i = 0; i < rightVerticesCount; i++) {
            for (int d = 0; d < rightDegrees[i]; d++) {
                rightStub.set(rightIndex, i + leftVerticesCount);
                rightIndex++;
            }
        }

        graph.clear();
        List<Edge> conflictEdges = new ArrayList<>();

        do {
            conflictEdges.clear();
            shuffle(leftStub, random);
            shuffle(rightStub, random);

            for (long i = 0; i < leftStub.size; i++) {
                int v1 = leftStub.get(i);
                int v2 = rightStub.get(i);
                if (!graph.hasEdge(v1, v2)) {
                    // no conflict
                    graph.addEdge(v1, v2);
                    continue;
                }

                int w1 = random.nextInt(leftVerticesCount);
                if (w1 == v1) {
                    // bad generation; conflict
                    conflictEdges.add(new Edge(v1, v2));
                    continue;
                }

                int w2 = graph.getRandomNeighbor(w1, random);
                if (w2 == -1 || w2 == v2) {
                    // bad generation; conflict
                    conflictEdges.add(new Edge(v1, v2));
                    continue;
                }

                if (!graph.hasEdge(v1, w2) && !graph.hasEdge(v2, w1)) {
                    // mutation fixes conflict
                    graph.removeEdge(w1, w2);
                    graph.addEdge(v1, w2);
                    graph.addEdge(v2, w1);
                } else {
                    // bad generation; conflict
                    conflictEdges.add(new Edge(v1, v2));
                }
            }

            // setup remaining stubs to reshuffle
            leftStub = new LongIntArray(conflictEdges.size());
            rightStub = new LongIntArray(conflictEdges.size());
            for (int i = 0; i < conflictEdges.size(); i++) {
                Edge e = conflictEdges.get(i);
                leftStub.set(i, e.vertex1());
                rightStub.set(i, e.vertex2());
            }
        } while (!conflictEdges.isEmpty());

        return graph;
    }

    /**
     * Randomly shuffle the given integer array.
     *
     * @param array
     *     the array to shuffle
     * @param random
     *     the random generator to use
     */
    private static void shuffle(LongIntArray array, RandomGenerator random) {
        for (long i = array.size - 1; i > 0; i--) {
            long i2 = random.nextLong(i + 1);
            int tmp = array.get(i);
            array.set(i, array.get(i2));
            array.set(i2, tmp);
        }
    }

    static class LongIntArray {
        final int BLOCK_SIZE = 1 << 30;
        int[][] array;
        long size;

        LongIntArray(long size) {
            this.size = size;
            long numBlocks = size / BLOCK_SIZE;
            if (size % BLOCK_SIZE != 0) {
                numBlocks++;
            }
            array = new int[(int) numBlocks][];

            for (int i = 0; i < numBlocks; i++) {
                array[i] = new int[BLOCK_SIZE];
            }
        }

        public int get(long index) {
            int blockIndex = (int) (index / BLOCK_SIZE);
            int withinBlockIndex = (int) (index % BLOCK_SIZE);
            return array[blockIndex][withinBlockIndex];
        }

        public void set(long index, int value) {
            int blockIndex = (int) (index / BLOCK_SIZE);
            int withinBlockIndex = (int) (index % BLOCK_SIZE);
            array[blockIndex][withinBlockIndex] = value;
        }
    }
}
