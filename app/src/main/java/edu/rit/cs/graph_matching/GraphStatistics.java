package edu.rit.cs.graph_matching;

import java.util.Collection;

public class GraphStatistics implements Graph {
  private final Graph source;

  private int edgeCheckCount;
  private int randomNeighborCount;
  private int allNeighborsCount;

  public GraphStatistics(Graph source) {
    this.source = source;
  }

  @Override
  public int size() {
    return source.size();
  }

  @Override
  public boolean hasEdge(int vertex1, int vertex2) {
    edgeCheckCount++;
    return source.hasEdge(vertex1, vertex2);
  }

  @Override
  public int getRandomNeighbor(int vertex) {
    randomNeighborCount++;
    return source.getRandomNeighbor(vertex);
  }

  @Override
  public Collection<Integer> getAllNeighbors(int vertex) {
    allNeighborsCount++;
    return source.getAllNeighbors(vertex);
  }

  public Graph getSource() {
    return source;
  }

  public int getEdgeCheckCount() {
    return edgeCheckCount;
  }

  public int getRandomNeighborCount() {
    return randomNeighborCount;
  }

  public int getAllNeighborsCount() {
    return allNeighborsCount;
  }
}
