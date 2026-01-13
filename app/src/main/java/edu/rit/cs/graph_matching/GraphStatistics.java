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
  public boolean hasEdge(int vertice1, int vertice2) {
    edgeCheckCount++;
    return source.hasEdge(vertice1, vertice2);
  }

  @Override
  public int getRandomNeighbor(int vertice) {
    randomNeighborCount++;
    return source.getRandomNeighbor(vertice);
  }

  @Override
  public Collection<Integer> getAllNeighbors(int vertice) {
    allNeighborsCount++;
    return source.getAllNeighbors(vertice);
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
