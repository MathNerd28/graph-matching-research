package edu.rit.cs.graph_matching.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

import edu.rit.cs.graph_matching.graph.Graph.Edge;

public final class GraphGenerator {
    private GraphGenerator() {
    }

    public record DaniHayesHardConstruction(Graph graph,
                                            List<Edge> plantedMatching,
                                            int degree,
                                            int corridorLength,
                                            int freeVertexCount) {}

    /**
     * Generates a star graph with the given number of edges. Matchings: leaves
     * + 1 - Empty Set - Single Edge Maximum Matchings: 1
     *
     * @param graph
     *              the graph to edit in-place
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
     *                    the graph to edit in-place
     * @param maxMatching
     *                    the desired size of the maximum matching
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
     *                 number of vertices
     * @param edgeProb
     *                 probability of adding an edge between any pair
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
     *                the graph to edit in-place
     * @param degrees
     *                the desired degree sequence
     * @param random
     *                random number generator
     * @return the same graph instance
     */
    public static MutableGraph generateGraph(MutableGraph graph, int[] degrees, Random random) {
        long totalStubs = 0;
        for (int d : degrees) {
            totalStubs += d;
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

            for (long i = 0; i < edgeConnections.getSize(); i += 2) {
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
     *                        the graph to edit in-place
     * @param verticesPerSide
     *                        number of vertices on each side
     * @param degree
     *                        the desired degree sequence
     * @param random
     *                        random number generator
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

        LongIntArray leftStub = new LongIntArray(leftStubTotal);
        LongIntArray rightStub = new LongIntArray(rightStubTotal);

        long leftIndex = 0;
        for (int i = 0; i < leftVerticesCount; i++) {
            for (int d = 0; d < leftDegrees[i]; d++) {
                leftStub.set(leftIndex, i);
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

            for (long i = 0; i < leftStub.getSize(); i++) {
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
     * Generates a simple cycle graph on n vertices of the pattern
     * 0-1-2-...-(n-1)-0 Returns the graph directly if vertex count <= 1
     * 
     * @param graph
     *              the graph to edit in-place
     * @return the same graph instance
     */
    public static MutableGraph generateLoopGraph(MutableGraph graph) {
        graph.clear();
        int n = graph.size();

        if (n <= 1) {
            return graph;
        }

        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n; // in case the conversion: << i=n-1 -> 0 >>
            graph.addEdge(i, j);
        }
        return graph;
    }

    /**
     * Randomly shuffle the given integer array.
     *
     * @param array
     *               the array to shuffle
     * @param random
     *               the random generator to use
     */
    private static void shuffle(LongIntArray array, RandomGenerator random) {
        for (long i = array.getSize() - 1; i > 0; i--) {
            long i2 = random.nextLong(i + 1);
            int tmp = array.get(i);
            array.set(i, array.get(i2));
            array.set(i2, tmp);
        }
    }

    static class LongIntArray {
        private static final int BLOCK_SIZE = 1 << 30;
        private final int[][] array;
        private final long size;

        LongIntArray(long size) {
            this.size = size;
            long numBlocks = (size + BLOCK_SIZE - 1) / BLOCK_SIZE;
            array = new int[(int) numBlocks][];

            long remaining = size;
            for (int i = 0; i < numBlocks; i++) {
                if (remaining < BLOCK_SIZE) {
                    array[i] = new int[(int) remaining];
                    break;
                }
                array[i] = new int[BLOCK_SIZE];
                remaining -= BLOCK_SIZE;
            }
        }

        public long getSize() {
            return size;
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

    /**
     * Generates a graph composed of n cliques of size k arranged in a loop.
     * From each clique, one internal edge is removed (opens up 2 vertices), and the
     * cliques are
     * connected in a cycle using those exposed vertices. The resulting graph is a
     * (k - 1)-regular graph.
     *
     * @param graph
     *              the graph to edit in-place; vertices count must be exactly n * k
     * @param n
     *              the number of cliques (mus be at least 2)
     * @param k
     *              the size of each clique (must be at least 3 for this algorithm)
     * @return the same graph instance
     */

    public static MutableGraph generateCliqueLoopGraph(MutableGraph graph, int n, int k) {
        graph.clear();

        if (n < 2) {
            throw new IllegalArgumentException("The number of cliques n must be at least 2");
        }
        if (k < 3) {
            throw new IllegalArgumentException("K value error: must be at least a 3-clique");
        }
        if (graph.size() != n * k) {
            throw new IllegalArgumentException("Graph size must be exactly n * k");
        }

        // Build each clique
        for (int c = 0; c < n; c++) {
            int base = c * k;
            for (int i = 0; i < k; i++) {
                for (int j = i + 1; j < k; j++) {
                    graph.addEdge(base + i, base + j);
                }
            }
        }

        // Remove one edge from each clique
        for (int c = 0; c < n; c++) {
            int base = c * k;
            graph.removeEdge(base, base + 1);
        }

        // Connect cliques in a loop
        // Method: 0 indexed vertex of this clique -> 1 indexed vertex of the next
        // clique
        for (int c = 0; c < n; c++) {
            int base = c * k;
            int nextBase = ((c + 1) % n) * k;
            graph.addEdge(base + 1, nextBase);
        }

        return graph;
    }

    /**
     * Generates a bipartite graph on 2n vertices with a unique perfect matching.
     * Left side: vertices 0..n-1, right side: vertices n..2n-1.
     * The unique matching pairs vertex i with vertex n+i for each i.
     *
     * The construction recurses: at each level the pivot is chosen at position
     * floor(splitRatio * subproblemSize), clamped to a valid index. The right
     * vertex of the pivot is connected to every left vertex before the pivot,
     * and the left vertex of the pivot is connected to every right vertex after
     * the pivot, making all other matchings infeasible.
     *
     * @param graph
     *                   the graph to edit in-place; vertex count must be exactly 2 * n
     * @param n
     *                   number of vertices per side (must be >= 1)
     * @param splitRatio
     *                   fraction of the subproblem to place on the left of the pivot
     *                   at each recursion level (must be in [0.0, 1.0]);
     *                   0.0 or 1.0 produces a triangular graph (densest),
     *                   0.5 splits in the middle (sparsest)
     * @return the same graph instance
     */
    public static MutableGraph generateUniqueMatchingGraph(MutableGraph graph, int n,
            double splitRatio) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least 1");
        }
        if (!Double.isFinite(splitRatio) || splitRatio < 0.0 || splitRatio > 1.0) {
            throw new IllegalArgumentException("splitRatio must be between 0.0 and 1.0 inclusive");
        }
        if (graph.size() != 2 * n) {
            throw new IllegalArgumentException("Graph size must be exactly 2 * n");
        }

        graph.clear();
        buildUniqueMatchingRecursive(graph, 0, n - 1, n, splitRatio);
        return graph;
    }

    private static void buildUniqueMatchingRecursive(MutableGraph graph, int first, int last,
            int rightOffset, double splitRatio) {
        int subproblemSize = last - first + 1;
        if (subproblemSize <= 0) {
            return;
        }

        int pivotIndex = Math.min((int) (splitRatio * subproblemSize), subproblemSize - 1);
        int pivotLeft  = first + pivotIndex;
        int pivotRight = rightOffset + first + pivotIndex;

        graph.addEdge(pivotLeft, pivotRight);

        for (int leftBeforePivot = first; leftBeforePivot < pivotLeft; leftBeforePivot++) {
            graph.addEdge(leftBeforePivot, pivotRight);
        }
        for (int rightAfterPivot = pivotLeft + 1; rightAfterPivot <= last; rightAfterPivot++) {
            graph.addEdge(pivotLeft, rightOffset + rightAfterPivot);
        }

        buildUniqueMatchingRecursive(graph, first, pivotLeft - 1, rightOffset, splitRatio);
        buildUniqueMatchingRecursive(graph, pivotLeft + 1, last, rightOffset, splitRatio);
    }

    /**
     * Returns the vertex count for the even-degree Dani-Hayes hard-instance
     * experiment.
     *
     * @param degree
     *     the regular degree; must be even and at least 4
     * @return the number of vertices used by
     *     {@link #generateDaniHayesHardGraph(int)}
     */
    public static int getDaniHayesHardVertexCount(int degree) {
        validateDaniHayesHardDegree(degree);
        return 7 * degree * degree + 3 * degree - 1;
    }

    /**
     * Generates the parity-correct, disjoint-gadget Dani-Hayes adversarial
     * corridor construction. The construction is d-regular for even d, leaves
     * d-1 vertices free in the planted matching, and uses only local one-port
     * degree-completion gadgets.
     *
     * @param degree
     *     the regular degree; must be even and at least 4
     * @return the generated graph and planted matching
     */
    public static DaniHayesHardConstruction generateDaniHayesHardGraph(int degree) {
        return generateDaniHayesHardGraph(
                new AdjacencySetGraph(getDaniHayesHardVertexCount(degree)), degree);
    }

    /**
     * Generates the parity-correct, disjoint-gadget Dani-Hayes adversarial
     * corridor construction in-place.
     *
     * @param graph
     *     the graph to edit in-place
     * @param degree
     *     the regular degree; must be even and at least 4
     * @return the generated graph and planted matching
     */
    public static DaniHayesHardConstruction generateDaniHayesHardGraph(MutableGraph graph,
            int degree) {
        validateDaniHayesHardDegree(degree);
        int expectedSize = getDaniHayesHardVertexCount(degree);
        if (graph.size() != expectedSize) {
            throw new IllegalArgumentException(
                    "Graph size must be exactly " + expectedSize + " for degree " + degree);
        }

        graph.clear();
        List<Edge> plantedMatching = new ArrayList<>();

        int freeVertexCount = degree - 1;
        int corridorLength = degree / 2;
        int cursor = 0;

        // Lay out the top-level sets: S are the initially free vertices, A are
        // the shared choice vertices, and B are the per-corridor boundary
        // vertices. cursor always points at the next unused vertex id.
        int freeStart = cursor;
        cursor += freeVertexCount;
        int sharedStart = cursor;
        cursor += degree;
        int boundaryStart = cursor;
        cursor += degree;

        // Allocate the corridor vertices. With zero-based array indices,
        // inLayers[layer][corridor] is I_{layer+1}^{corridor+1}, and
        // outLayers[layer][corridor] is O_{layer+1}^{corridor+1}.
        int[][] inLayers = new int[corridorLength][degree];
        int[][] outLayers = new int[corridorLength][degree];
        for (int layer = 0; layer < corridorLength; layer++) {
            for (int corridor = 0; corridor < degree; corridor++) {
                inLayers[layer][corridor] = cursor++;
                outLayers[layer][corridor] = cursor++;
            }
        }

        // Add the complete bipartite graph S-A. Each free vertex receives all d
        // possible first choices; each A_j already has d-1 incident free edges
        // and will get one planted matching edge to B_j below.
        for (int free = 0; free < freeVertexCount; free++) {
            for (int shared = 0; shared < degree; shared++) {
                graph.addEdge(freeStart + free, sharedStart + shared);
            }
        }

        // Match A_j to B_j and add the unique corridor-entry edge B_j-I_1^j.
        // Thus choosing A_j commits the alternating walk to corridor j.
        for (int corridor = 0; corridor < degree; corridor++) {
            int shared = sharedStart + corridor;
            int boundary = boundaryStart + corridor;
            addMatchedEdge(graph, plantedMatching, shared, boundary);
            graph.addEdge(boundary, inLayers[0][corridor]);
        }

        // Add the planted matching inside every corridor layer:
        // I_i^j is matched to O_i^j.
        for (int layer = 0; layer < corridorLength; layer++) {
            for (int corridor = 0; corridor < degree; corridor++) {
                addMatchedEdge(graph, plantedMatching, inLayers[layer][corridor],
                        outLayers[layer][corridor]);
            }
        }

        // Add the unmatched forward edges O_i^j-I_{i+1}^j that continue along
        // the same corridor.
        for (int layer = 0; layer < corridorLength - 1; layer++) {
            for (int corridor = 0; corridor < degree; corridor++) {
                graph.addEdge(outLayers[layer][corridor], inLayers[layer + 1][corridor]);
            }
        }

        // Add the unmatched back edges from O_i^j to the previous layer. These
        // are the construction's deliberate cross-corridor choices; the
        // degree-completion gadgets below remain disjoint and add no paths
        // between corridors.
        for (int layer = 1; layer < corridorLength; layer++) {
            for (int corridor = 0; corridor < degree; corridor++) {
                int out = outLayers[layer][corridor];
                for (int shift = 0; shift < degree - 2; shift++) {
                    graph.addEdge(out, inLayers[layer - 1][(corridor + shift) % degree]);
                }
            }
        }

        // Pair terminal O vertices. At the last layer, each O has its planted
        // matching edge plus d-2 back edges, so this one extra edge completes
        // degree d without creating new free vertices.
        int terminalLayer = corridorLength - 1;
        for (int corridor = 0; corridor < degree; corridor += 2) {
            graph.addEdge(outLayers[terminalLayer][corridor],
                    outLayers[terminalLayer][corridor + 1]);
        }

        // Attach separate one-port completion gadgets to exactly the vertices
        // that are still short of degree: B_j, O_1^j, and I_ell^j. Each call
        // uses fresh vertices, so completions cannot connect one corridor to
        // another.
        for (int corridor = 0; corridor < degree; corridor++) {
            cursor = attachOnePortCompletion(graph, plantedMatching, boundaryStart + corridor,
                    degree, cursor);
            cursor = attachOnePortCompletion(graph, plantedMatching, outLayers[0][corridor],
                    degree, cursor);
            cursor = attachOnePortCompletion(graph, plantedMatching,
                    inLayers[terminalLayer][corridor], degree, cursor);
        }

        if (cursor != graph.size()) {
            throw new IllegalStateException(
                    "Internal vertex accounting error: used " + cursor + " of " + graph.size());
        }

        for (int vertex = 0; vertex < graph.size(); vertex++) {
            if (graph.getDegree(vertex) != degree) {
                throw new IllegalStateException("Expected vertex " + vertex + " to have degree "
                        + degree + " but found " + graph.getDegree(vertex));
            }
        }

        if (!GraphUtils.isValidMatching(graph, plantedMatching)) {
            throw new IllegalStateException("Generated planted matching is invalid");
        }

        return new DaniHayesHardConstruction(graph, List.copyOf(plantedMatching), degree,
                corridorLength, freeVertexCount);
    }

    private static void validateDaniHayesHardDegree(int degree) {
        if (degree < 4 || degree % 2 != 0) {
            throw new IllegalArgumentException("degree must be even and at least 4");
        }
    }

    private static int attachOnePortCompletion(MutableGraph graph, List<Edge> plantedMatching,
            int boundaryVertex, int degree, int cursor) {
        // Use fresh vertices for every completion gadget. This is what makes
        // the gadgets disjoint: the only old vertex they touch is boundaryVertex.
        int[] left = new int[degree];
        int[] right = new int[degree];
        for (int i = 0; i < degree; i++) {
            left[i] = cursor++;
            right[i] = cursor++;
        }

        // Start from K_{d,d} and remove q independent diagonal edges. The
        // endpoints of the removed edges are the only gadget vertices now short
        // of degree d.
        int removedPairs = (degree - 2) / 2;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                if (i == j && i < removedPairs) {
                    continue;
                }
                graph.addEdge(left[i], right[j]);
            }
        }

        // Connect the port to both endpoints of each removed edge. This gives
        // boundaryVertex exactly degree - 2 new incident edges and restores each
        // affected gadget vertex to degree d.
        for (int i = 0; i < removedPairs; i++) {
            graph.addEdge(boundaryVertex, left[i]);
            graph.addEdge(boundaryVertex, right[i]);
        }

        // Use a shifted perfect matching inside the gadget so no planted
        // matching edge is one of the removed K_{d,d} edges.
        for (int i = 0; i < degree; i++) {
            int matchedRight = right[(i + 1) % degree];
            if (!graph.hasEdge(left[i], matchedRight)) {
                throw new IllegalStateException("Local completion matching edge is missing");
            }
            plantedMatching.add(new Edge(left[i], matchedRight));
        }
        return cursor;
    }

    private static void addMatchedEdge(MutableGraph graph, List<Edge> plantedMatching, int vertex1,
            int vertex2) {
        graph.addEdge(vertex1, vertex2);
        plantedMatching.add(new Edge(vertex1, vertex2));
    }

}
