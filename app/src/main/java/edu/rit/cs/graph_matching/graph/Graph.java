package edu.rit.cs.graph_matching.graph;

import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * An undirected graph that the Dani-Hayes algorithm works with. As stated in
 * their paper, the algorithm requires two primary functions:
 * <ol>
 * <li>Checking whether a specific edge exists: {@link #hasEdge(int, int)}</li>
 * <li>Getting a random neighbor of an edge:
 * {@link #getRandomNeighbor(int)}</li>
 * </ol>
 * Additionally, this interface provides one additional method that Dani-Hayes
 * does not use, which is enumerating all neighbors of a vertex. This is for
 * computing maximum matchings via Edmonds's "Blossom" algorithm, for comparison
 * purposes.
 * <p>
 * Vertices are numbered in the range [0, n).
 */
public interface Graph {
    /**
     * Gets the total number of vertices present in this graph. This method must
     * run in O(1) time.
     *
     * @return the number of vertices in this graph
     */
    int size();

    /**
     * Checks whether an edge exists between two vertices. Vertex order does not
     * matter.
     *
     * @param vertex1
     *     the first vertex
     * @param vertex2
     *     the second vertex
     * @return true iff an undirected edge exists between these two vertices
     */
    boolean hasEdge(int vertex1, int vertex2);

    /*
     * Checks whether an edge exists between two vertices. Vertex order does not
     * matter.
     * @param edge the edge to check
     * @return true iff the undirected edge exists
     */
    default boolean hasEdge(Edge edge) {
        return hasEdge(edge.vertex1(), edge.vertex2());
    }

    /**
     * Get the number of edges connected to a vertex that are present in this
     * graph.
     *
     * @param vertex
     *     the vertex
     * @return the degree of {@code vertex}
     */
    int getDegree(int vertex);

    /**
     * Gets a random neighbor of a vertex, or indicates that no neighbors exist.
     *
     * @param vertex
     *     the vertex
     * @param random
     *     the random number generator to use
     * @return a random neighbor of the vertex, or -1 if no such neighbors
     *     exist.
     */
    int getRandomNeighbor(int vertex, RandomGenerator random);

    /**
     * Get all vertices that share an edge with a vertex.
     *
     * @param vertex
     *     the vertex
     * @return all neighbors of the vertex
     */
    Set<Integer> getAllNeighbors(int vertex);

    /**
     * An edge in an undirected graph. Vertex order does not matter; edges with
     * the same vertices in the opposite order are treated as equal.
     */
    public record Edge(int vertex1,
                       int vertex2) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Edge(int v1, int v2)
                    && ((vertex1 == v1 && vertex2 == v2) || (vertex1 == v2 && vertex2 == v1));
        }

        @Override
        public int hashCode() {
            // Ensure that swapping the vertices doesn't affect the hash
            return Objects.hash(Math.min(vertex1, vertex2), Math.max(vertex1, vertex2));
        }

        @Override
        public String toString() {
            return new StringBuilder().append('(')
                                      .append(vertex1)
                                      .append(" = ")
                                      .append(vertex2)
                                      .append(')')
                                      .toString();
        }
    }
}
