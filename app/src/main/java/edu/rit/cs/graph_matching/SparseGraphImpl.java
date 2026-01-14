package edu.rit.cs.graph_matching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class SparseGraphImpl implements MutableGraph {
  private final List<OrderedIntSet> adjacencyList;

  private final Random random;

  public SparseGraphImpl(int vertices) {
    this.adjacencyList = new ArrayList<>(vertices);
    for (int i = 0; i < vertices; i++) {
      adjacencyList.add(new OrderedIntSet());
    }

    this.random = new Random();
  }

  @Override
  public void addEdge(int vertex1, int vertex2) {
    adjacencyList.get(vertex1)
                 .add(vertex2);
    adjacencyList.get(vertex2)
                 .add(vertex1);
  }

  @Override
  public void removeEdge(int vertex1, int vertex2) {
    adjacencyList.get(vertex1)
                 .remove(vertex2);
    adjacencyList.get(vertex2)
                 .remove(vertex1);
  }

  @Override
  public int size() {
    return adjacencyList.size();
  }

  @Override
  public boolean hasEdge(int vertex1, int vertex2) {
    return adjacencyList.get(vertex1)
                        .contains(vertex2);
  }

  @Override
  public int getRandomNeighbor(int vertex) {
    OrderedIntSet neighbors = adjacencyList.get(vertex);
    int index = random.nextInt(neighbors.size());
    return neighbors.get(index);
  }

  @Override
  public Collection<Integer> getAllNeighbors(int vertex) {
    return adjacencyList.get(vertex);
  }
}
