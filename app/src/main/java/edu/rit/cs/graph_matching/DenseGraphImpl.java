package edu.rit.cs.graph_matching;

import java.util.BitSet;

/**
 * A sparse undirected graph implementation with the following properties:
 * <ul>
 * <li>{@link #getRandomNeighbor(int)} runs in O(1) time</li>
 * <li>{@link #getAllNeighbors(int)} runs in O(1) time</li>
 * <li>{@link #hasEdge(int, int)} runs in O(1) time</li>
 * </ul>
 * <p>
 * Dense graphs are limited to 65536 vertices or fewer.
 */
public class DenseGraphImpl extends SparseGraphImpl {
  /** The backing adjacency matrix */
  private final BitSet adjacencyMatrix;

  /**
   * Construct a graph with no edges.
   *
   * @param vertices
   *   the number of vertices in this graph
   */
  public DenseGraphImpl(int vertices) {
    super(vertices);

    if (vertices > (1 << 16)) {
      throw new IllegalArgumentException("DenseGraphImpl is limited to 65536 vertices");
    }

    this.adjacencyMatrix = new BitSet((int) ((long) vertices * (vertices - 1) / 2));
  }

  @Override
  public void addEdge(int vertex1, int vertex2) {
    super.addEdge(vertex1, vertex2);
    adjacencyMatrix.set(calculateIndex(vertex1, vertex2));
  }

  @Override
  public void removeEdge(int vertex1, int vertex2) {
    super.removeEdge(vertex1, vertex2);
    adjacencyMatrix.clear(calculateIndex(vertex1, vertex2));
  }

  @Override
  public boolean hasEdge(int vertex1, int vertex2) {
    return adjacencyMatrix.get(calculateIndex(vertex1, vertex2));
  }

  @Override
  public void clear() {
    super.clear();
    adjacencyMatrix.clear();
  }

  /**
   * Calculates the index used to store the edge between two vertices in the
   * adjacency matrix. Vertex order does not matter, and self-loops are not
   * permitted.
   *
   * @param vertex1
   *   the first vertex
   * @param vertex2
   *   the second vertex
   * @return the bit index where this edge should be stored
   */
  private int calculateIndex(int vertex1, int vertex2) {
    checkVertexIndex(vertex1);
    checkVertexIndex(vertex2);
    checkVerticesNotEqual(vertex1, vertex2);

    int min = Math.min(vertex1, vertex2);
    int max = Math.max(vertex1, vertex2);

    // int multiplication could overflow here, so we cast to long
    return (int) ((long) max * (max - 1) / 2 + min);
  }
}
