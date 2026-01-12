package edu.rit.cs.graph_matching;

public interface UndirectedGraph {
  /**
   * @return the number of vertices in this graph
   */
  int getVertices();

  /**
   * Checks whether an edge exists between two vertices. Vertice order does not
   * matter.
   *
   * @param vertice1
   *   the first vertice
   * @param vertice2
   *   the second vertice
   * @return true iff an undirected edge exists between these two vertices
   */
  boolean doesEdgeExist(int vertice1, int vertice2);

  /**
   * Gets a random neighbor of a vertex, or indicates that no neighbors exist.
   *
   * @param vertice
   *   the vertex
   * @return a random neighbor of the vertex, or -1 if no such neighbors exist.
   */
  int getRandomNeighbor(int vertice);
}
