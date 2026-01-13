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
  public void addEdge(int vertice1, int vertice2) {
    adjacencyList.get(vertice1)
                 .add(vertice2);
    adjacencyList.get(vertice2)
                 .add(vertice1);
  }

  @Override
  public void removeEdge(int vertice1, int vertice2) {
    adjacencyList.get(vertice1)
                 .remove(vertice2);
    adjacencyList.get(vertice2)
                 .remove(vertice1);
  }

  @Override
  public int size() {
    return adjacencyList.size();
  }

  @Override
  public boolean hasEdge(int vertice1, int vertice2) {
    return adjacencyList.get(vertice1)
                        .contains(vertice2);
  }

  @Override
  public int getRandomNeighbor(int vertice) {
    OrderedIntSet neighbors = adjacencyList.get(vertice);
    int index = random.nextInt(neighbors.size());
    return neighbors.get(index);
  }

  @Override
  public Collection<Integer> getAllNeighbors(int vertice) {
    return adjacencyList.get(vertice);
  }
}
