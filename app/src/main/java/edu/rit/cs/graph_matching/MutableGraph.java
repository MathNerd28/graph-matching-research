package edu.rit.cs.graph_matching;

public interface MutableGraph extends Graph {
  void addEdge(int vertex1, int vertex2);

  void removeEdge(int vertex1, int vertex2);
}
