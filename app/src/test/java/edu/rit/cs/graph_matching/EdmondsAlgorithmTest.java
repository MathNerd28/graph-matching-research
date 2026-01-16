package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EdmondsAlgorithmTest {
  @ParameterizedTest
  @ValueSource(ints = { 2, 5, 10, 100, 1000, 10000 })
  void starGraphs(int vertices) {
    Graph g = GraphGenerator.generateStarGraph(new SparseGraphImpl(vertices));
    EdmondsAlgorithm algorithm = new EdmondsAlgorithm(g);
    Set<Edge> matching = algorithm.computeMaximumMatching();
    assertEquals(1, matching.size(), "Star graphs always have a maximum matching of 1 edge");
  }

  @ParameterizedTest
  @CsvSource({ "5, 2", "10, 2", "10, 3", "10, 4", "10, 5", "100, 25", "100, 49", "1000, 400",
               "5000, 1000", })
  void starWheelHybridGraphs(int vertices, int matchingSize) {
    Graph g =
        GraphGenerator.generateStarGraphWithMatching(new SparseGraphImpl(vertices), matchingSize);
    EdmondsAlgorithm algorithm = new EdmondsAlgorithm(g);
    Set<Edge> matching = algorithm.computeMaximumMatching();
    assertEquals(matchingSize, matching.size(),
        "Star-wheel hybrid graphs have a fixed maximum matching size");
  }
}
