package edu.rit.cs.graph_matching.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

import edu.rit.cs.graph_matching.util.AbstractIntSet;
import edu.rit.cs.graph_matching.util.IntArraySet;
import edu.rit.cs.graph_matching.util.IntHashSet;

/**
 * A sparse undirected graph implementation with the following properties:
 * <ul>
 * <li>{@link #getRandomNeighbor(int)} runs in O(1) time</li>
 * <li>{@link #getAllNeighbors(int)} runs in O(1) time</li>
 * <li>{@link #hasEdge(int, int)} runs in O(1) time</li>
 * </ul>
 */
public class AdjacencySetGraph implements MutableGraph {
    /**
     * The size at which a neighbor set will switch from the lightweight
     * array-based implementation to the faster (but more memory intensive)
     * hash-based implementation. Such switches are permanent; that is, if
     * subsequently the size reduces below the threshold, the neighbor set will
     * NOT switch back to the array-based implementation.
     */
    private static final int HASH_THRESHOLD = 7;

    /**
     * The backing adjacency list. Uses IntHashSet for amortized O(1) lookup
     * with a small memory footprint.
     */
    private final List<AbstractIntSet> adjacencyList;

    /**
     * Construct a graph with no edges.
     *
     * @param vertices
     *     the number of vertices in this graph
     */
    public AdjacencySetGraph(int vertices) {
        if (vertices <= 0) {
            throw new IllegalArgumentException("Graphs require a positive number of vertices");
        }

        this.adjacencyList = new ArrayList<>(vertices);
        for (int i = 0; i < vertices; i++) {
            adjacencyList.add(new IntArraySet());
        }
    }

    @Override
    public void addEdge(int vertex1, int vertex2) {
        checkVertexIndex(vertex1);
        checkVertexIndex(vertex2);
        checkVerticesNotEqual(vertex1, vertex2);

        addNeighbor(vertex1, vertex2);
        addNeighbor(vertex2, vertex1);
    }

    private void addNeighbor(int vertex, int neighbor) {
        AbstractIntSet neighbors = adjacencyList.get(vertex);
        if (neighbors.add(neighbor)
                && neighbors.size() == HASH_THRESHOLD
                && neighbors.getClass() == IntArraySet.class) {
            // switch to hash implementation for performance
            adjacencyList.set(vertex, new IntHashSet(neighbors));
        }
    }

    @Override
    public void removeEdge(int vertex1, int vertex2) {
        checkVertexIndex(vertex1);
        checkVertexIndex(vertex2);
        checkVerticesNotEqual(vertex1, vertex2);

        adjacencyList.get(vertex1)
                     .remove(vertex2);
        adjacencyList.get(vertex2)
                     .remove(vertex1);
    }

    @Override
    public int size() {
        return adjacencyList.size();
    }

    @Override
    public int getDegree(int vertex) {
        checkVertexIndex(vertex);

        return adjacencyList.get(vertex)
                            .size();
    }

    @Override
    public boolean hasEdge(int vertex1, int vertex2) {
        checkVertexIndex(vertex1);
        checkVertexIndex(vertex2);

        return adjacencyList.get(vertex1)
                            .contains(vertex2);
    }

    @Override
    public int getRandomNeighbor(int vertex, RandomGenerator random) {
        checkVertexIndex(vertex);

        AbstractIntSet neighbors = adjacencyList.get(vertex);
        return neighbors.isEmpty() ? -1 : neighbors.getRandom(random);
    }

    @Override
    public Set<Integer> getAllNeighbors(int vertex) {
        checkVertexIndex(vertex);

        return adjacencyList.get(vertex);
    }

    @Override
    public void clear() {
        for (AbstractIntSet adjacents : adjacencyList) {
            adjacents.clear();
        }
    }

    protected final void checkVertexIndex(int vertex) {
        if (vertex < 0 || vertex >= size()) {
            throw new IndexOutOfBoundsException(vertex);
        }
    }

    protected static final void checkVerticesNotEqual(int vertex1, int vertex2) {
        if (vertex1 == vertex2) {
            throw new UnsupportedOperationException("Self-looping edges are not supported");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Graph g)) {
            return false;
        }

        if (size() != g.size()) {
            return false;
        }

        for (int v = 0; v < adjacencyList.size(); v++) {
            if (!getAllNeighbors(v).equals(g.getAllNeighbors(v))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(adjacencyList);
    }
}
