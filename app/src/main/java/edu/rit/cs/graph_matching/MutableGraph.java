package edu.rit.cs.graph_matching;

public interface MutableGraph extends Graph {
  void addEdge(int vertice1, int vertice2);

  void removeEdge(int vertice1, int vertice2);
}
