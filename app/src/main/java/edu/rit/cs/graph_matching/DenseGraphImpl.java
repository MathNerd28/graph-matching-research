package edu.rit.cs.graph_matching;

import java.util.BitSet;

// Currently maxes out at 46340 vertices because the size of adjacencyMatrix is
// an int
// With optimized indexing, 65536 vertices is possible before exceeding the
// integer limit
public class DenseGraphImpl extends SparseGraphImpl {
  private final BitSet adjacencyMatrix;

  public DenseGraphImpl(int vertices) {
    super(vertices);
    this.adjacencyMatrix = new BitSet(vertices * vertices);
  }

  @Override
  public void addEdge(int vertex1, int vertex2) {
    super.addEdge(vertex1, vertex2);
    adjacencyMatrix.set(vertex1 * size() + vertex2);
    adjacencyMatrix.set(vertex2 * size() + vertex1);
  }

  @Override
  public void removeEdge(int vertex1, int vertex2) {
    super.removeEdge(vertex1, vertex2);
    adjacencyMatrix.clear(vertex1 * size() + vertex2);
    adjacencyMatrix.clear(vertex2 * size() + vertex1);
  }

  @Override
  public boolean hasEdge(int vertex1, int vertex2) {
    return adjacencyMatrix.get(vertex1 * size() + vertex2);
  }
}
