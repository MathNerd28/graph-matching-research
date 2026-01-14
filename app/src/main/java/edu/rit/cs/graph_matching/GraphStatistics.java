package edu.rit.cs.graph_matching;

import java.util.Set;

/**
 * A wrapper that tracks how many times graph methods are called.
 */
public class GraphStatistics implements Graph {
  /** The backing graph */
  private final Graph source;

  /** The number of times {@link #hasEdge(int, int)} has been called */
  private int edgeCheckCount;
  /** The number of times {@link #getRandomNeighbor(int)} has been called */
  private int randomNeighborCount;
  /** The number of times {@link #getAllNeighbors(int)} has been called */
  private int allNeighborsCount;

  /**
   * Construct a graph statistics wrapper.
   *
   * @param source
   *   the backing graph
   */
  public GraphStatistics(Graph source) {
    this.source = source;
  }

  @Override
  public int size() {
    return source.size();
  }

  /**
   * @inheritdoc Track the number of times this method was called using
   *   {@link #getEdgeCheckCount()}.
   */
  @Override
  public boolean hasEdge(int vertex1, int vertex2) {
    edgeCheckCount++;
    return source.hasEdge(vertex1, vertex2);
  }

  /**
   * @inheritdoc Track the number of times this method was called using
   *   {@link #getRandomNeighborCount()}.
   */
  @Override
  public int getRandomNeighbor(int vertex) {
    randomNeighborCount++;
    return source.getRandomNeighbor(vertex);
  }

  /**
   * @inheritdoc Track the number of times this method was called using
   *   {@link #getAllNeighborsCount()}.
   */
  @Override
  public Set<Integer> getAllNeighbors(int vertex) {
    allNeighborsCount++;
    return source.getAllNeighbors(vertex);
  }

  /**
   * @return the graph backing this statistics wrapper
   */
  public Graph getSource() {
    return source;
  }

  /**
   * @return the number of times {@link #hasEdge(int, int)} has been called
   */
  public int getEdgeCheckCount() {
    return edgeCheckCount;
  }

  /**
   * @return the number of times {@link #getRandomNeighbor(int)} has been called
   */
  public int getRandomNeighborCount() {
    return randomNeighborCount;
  }

  /**
   * @return the number of times {@link #getAllNeighbors(int)} has been called
   */
  public int getAllNeighborsCount() {
    return allNeighborsCount;
  }
}
