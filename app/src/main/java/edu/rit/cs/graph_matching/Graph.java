package edu.rit.cs.graph_matching;

/**
 * The model of a graph that the Dani-Hayes algorithm works with. As stated in
 * their paper, the algorithm requires two primary functions:
 * <ol>
 * <li>Checking whether a specific edge exists: {@link #hasEdge(int, int)}</li>
 * <li>Getting a random neighbor of an edge:
 * {@link #getRandomNeighbor(int)}</li>
 * </ol>
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
   * Checks whether an edge exists between two vertices. Vertice order does not
   * matter. This method should run in O(1) time.
   *
   * @param vertice1
   *   the first vertice
   * @param vertice2
   *   the second vertice
   * @return true iff an undirected edge exists between these two vertices
   */
  boolean hasEdge(int vertice1, int vertice2);

  /**
   * Gets a random neighbor of a vertex, or indicates that no neighbors exist.
   * This method should run in O(1) time.
   *
   * @param vertice
   *   the vertex
   * @return a random neighbor of the vertex, or -1 if no such neighbors exist.
   */
  int getRandomNeighbor(int vertice);
}
