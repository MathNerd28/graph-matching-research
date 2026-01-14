package edu.rit.cs.graph_matching;

import java.util.Collection;

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
   *   the first vertex
   * @param vertex2
   *   the second vertex
   * @return true iff an undirected edge exists between these two vertices
   */
  boolean hasEdge(int vertex1, int vertex2);

  /**
   * Gets a random neighbor of a vertex, or indicates that no neighbors exist.
   *
   * @param vertex
   *   the vertex
   * @return a random neighbor of the vertex, or -1 if no such neighbors exist.
   */
  int getRandomNeighbor(int vertex);

  /**
   * @param vertex
   * @return
   */
  Collection<Integer> getAllNeighbors(int vertex);
}
