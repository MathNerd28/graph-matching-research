package edu.rit.cs.graph_matching.graph;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Immutable graph stored in compressed sparse row (CSR) form.
 */
public final class CompactGraph implements Graph {
    private final int vertexCount;
    private final int[] neighborOffsets;
    private final int[] neighbors;

    private CompactGraph(int vertexCount, int[] neighborOffsets, int[] neighbors) {
        this.vertexCount = vertexCount;
        this.neighborOffsets = neighborOffsets;
        this.neighbors = neighbors;
    }

    /**
     * Build a graph from the selected entries in parallel edge arrays.
     *
     * @param vertexCount
     *              number of vertices
     * @param edgeU
     *              first endpoint of each candidate edge
     * @param edgeV
     *              second endpoint of each candidate edge
     * @param selected
     *              whether each candidate edge belongs to the graph
     * @return a graph containing exactly the selected edges
     */
    public static CompactGraph fromSelectedEdges(int vertexCount, int[] edgeU, int[] edgeV,
            boolean[] selected) {
        int[] neighborOffsets = new int[vertexCount + 1];
        for (int edge = 0; edge < selected.length; edge++) {
            if (selected[edge]) {
                neighborOffsets[edgeU[edge] + 1]++;
                neighborOffsets[edgeV[edge] + 1]++;
            }
        }
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            neighborOffsets[vertex + 1] += neighborOffsets[vertex];
        }

        int[] neighbors = new int[neighborOffsets[vertexCount]];
        int[] nextNeighborIndex = neighborOffsets.clone();
        for (int edge = 0; edge < selected.length; edge++) {
            if (selected[edge]) {
                neighbors[nextNeighborIndex[edgeU[edge]]++] = edgeV[edge];
                neighbors[nextNeighborIndex[edgeV[edge]]++] = edgeU[edge];
            }
        }

        // Sort each adjacency slice for binary-search edge lookup.
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            Arrays.sort(neighbors, neighborOffsets[vertex], neighborOffsets[vertex + 1]);
        }

        return new CompactGraph(vertexCount, neighborOffsets, neighbors);
    }

    @Override
    public int size() {
        return vertexCount;
    }

    @Override
    public boolean hasEdge(int vertex1, int vertex2) {
        checkVertexIndex(vertex1);
        checkVertexIndex(vertex2);
        return Arrays.binarySearch(neighbors, neighborOffsets[vertex1],
                neighborOffsets[vertex1 + 1], vertex2) >= 0;
    }

    @Override
    public int getDegree(int vertex) {
        checkVertexIndex(vertex);
        return neighborOffsets[vertex + 1] - neighborOffsets[vertex];
    }

    @Override
    public int getRandomNeighbor(int vertex, RandomGenerator random) {
        checkVertexIndex(vertex);
        int degree = getDegree(vertex);
        return degree == 0
                ? -1
                : neighbors[neighborOffsets[vertex] + random.nextInt(degree)];
    }

    @Override
    public Set<Integer> getAllNeighbors(int vertex) {
        checkVertexIndex(vertex);
        return new NeighborView(vertex);
    }

    private void checkVertexIndex(int vertex) {
        if (vertex < 0 || vertex >= vertexCount) {
            throw new IndexOutOfBoundsException(vertex);
        }
    }

    private final class NeighborView extends AbstractSet<Integer> {
        private final int vertex;

        private NeighborView(int vertex) {
            this.vertex = vertex;
        }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<>() {
                private int position = neighborOffsets[vertex];

                @Override
                public boolean hasNext() {
                    return position < neighborOffsets[vertex + 1];
                }

                @Override
                public Integer next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return neighbors[position++];
                }
            };
        }

        @Override
        public int size() {
            return getDegree(vertex);
        }

        @Override
        public boolean contains(Object candidate) {
            return candidate instanceof Integer neighbor
                    && neighbor >= 0
                    && neighbor < vertexCount
                    && hasEdge(vertex, neighbor);
        }
    }
}
