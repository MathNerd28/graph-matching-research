package edu.rit.cs.graph_matching;

/**
 * An undirected graph that can have edges added or removed. The number of
 * vertices cannot be changed.
 */
public interface MutableGraph extends Graph {
  /**
   * Adds an edge to this graph, if it doesn't already exist.
   *
   * @param vertex1
   *   the first vertex in the edge
   * @param vertex2
   *   the second vertex in the edge
   */
  void addEdge(int vertex1, int vertex2);

  /**
   * Removes an edge from this graph, if it exists.
   *
   * @param vertex1
   *   the first vertex in the edge
   * @param vertex2
   *   the second vertex in the edge
   */
  void removeEdge(int vertex1, int vertex2);

  /**
   * Removes all existing edges from this graph, leaving it with zero edges
   * remaining.
   */
  void clear();
}
