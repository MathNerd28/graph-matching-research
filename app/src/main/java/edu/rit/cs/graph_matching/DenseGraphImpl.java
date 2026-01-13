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
  public void addEdge(int vertice1, int vertice2) {
    super.addEdge(vertice1, vertice2);
    adjacencyMatrix.set(vertice1 * size() + vertice2);
    adjacencyMatrix.set(vertice2 * size() + vertice1);
  }

  @Override
  public void removeEdge(int vertice1, int vertice2) {
    super.removeEdge(vertice1, vertice2);
    adjacencyMatrix.clear(vertice1 * size() + vertice2);
    adjacencyMatrix.clear(vertice2 * size() + vertice1);
  }

  @Override
  public boolean hasEdge(int vertice1, int vertice2) {
    return adjacencyMatrix.get(vertice1 * size() + vertice2);
  }
}
