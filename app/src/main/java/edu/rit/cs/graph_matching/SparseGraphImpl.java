package edu.rit.cs.graph_matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A sparse undirected graph implementation with the following properties:
 * <ul>
 * <li>{@link #getRandomNeighbor(int)} runs in O(1) time</li>
 * <li>{@link #getAllNeighbors(int)} runs in O(1) time</li>
 * <li>{@link #hasEdge(int, int)} runs in O(1) time</li>
 * </ul>
 */
public class SparseGraphImpl implements MutableGraph {
  /**
   * The backing adjacency list. Uses IntHashSet for amortized O(1) lookup with
   * a small memory footprint.
   */
  private final List<IntHashSet> adjacencyList;

  /**
   * The pseudo-random number generator used for {@link #getRandomNeighbor(int)}
   */
  private final Random random;

  /**
   * Construct a sparse graph with no edges.
   *
   * @param vertices
   *   the number of vertices in this graph
   */
  public SparseGraphImpl(int vertices) {
    if (vertices <= 0) {
      throw new IllegalArgumentException("Graphs require a positive number of vertices");
    }

    this.adjacencyList = new ArrayList<>(vertices);
    for (int i = 0; i < vertices; i++) {
      adjacencyList.add(new IntHashSet());
    }

    this.random = new Random();
  }

  @Override
  public void addEdge(int vertex1, int vertex2) {
    checkVertexIndex(vertex1);
    checkVertexIndex(vertex2);
    checkVerticesNotEqual(vertex1, vertex2);

    adjacencyList.get(vertex1)
                 .add(vertex2);
    adjacencyList.get(vertex2)
                 .add(vertex1);
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
  public boolean hasEdge(int vertex1, int vertex2) {
    checkVertexIndex(vertex1);
    checkVertexIndex(vertex2);

    return adjacencyList.get(vertex1)
                        .contains(vertex2);
  }

  @Override
  public int getRandomNeighbor(int vertex) {
    checkVertexIndex(vertex);

    return adjacencyList.get(vertex)
                        .getRandom(random);
  }

  @Override
  public Set<Integer> getAllNeighbors(int vertex) {
    checkVertexIndex(vertex);

    return adjacencyList.get(vertex);
  }

  @Override
  public void clear() {
    for (IntHashSet adjacents : adjacencyList) {
      adjacents.clear();
    }
  }

  protected final void checkVertexIndex(int vertex) {
    if (vertex < 0 || vertex >= size()) {
      throw new IndexOutOfBoundsException(vertex);
    }
  }

  protected final void checkVerticesNotEqual(int vertex1, int vertex2) {
    if (vertex1 == vertex2) {
      throw new UnsupportedOperationException("Self-looping edges are not supported");
    }
  }
}
