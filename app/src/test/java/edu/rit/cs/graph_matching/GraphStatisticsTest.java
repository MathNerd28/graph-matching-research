package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GraphStatisticsTest {
  /**
   * Covers {@link GraphStatistics#GraphStatistics(Graph)},
   * {@link GraphStatistics#size()}
   */
  @Test
  void construct() {
    Graph g = new GraphStatistics(new DenseGraphImpl(1));
    assertEquals(1, g.size());
    g = new GraphStatistics(new DenseGraphImpl(2));
    assertEquals(2, g.size());
    g = new GraphStatistics(new DenseGraphImpl(50));
    assertEquals(50, g.size());
    g = new GraphStatistics(new DenseGraphImpl(65536));
    assertEquals(65536, g.size());
    g = new GraphStatistics(new SparseGraphImpl(10_000_000));
    assertEquals(10_000_000, g.size());
  }

  /**
   * Covers {@link DenseGraphImpl#hasEdge(int, int)},
   * {@link DenseGraphImpl#getRandomNeighbor(int)},
   * {@link DenseGraphImpl#getAllNeighbors(int)}
   * <p>
   * {@link DenseGraphImpl#getRandomNeighbor(int)} has a RNG component; thus, it
   * will only be tested in a method that is guaranteed to work.
   */
  @Test
  void query() {
    Set<Edge> edges = Set.of(new Edge(0, 1), new Edge(0, 2), new Edge(0, 3), new Edge(1, 2));
    Random rd = new Random(0);

    MutableGraph g = new DenseGraphImpl(5);
    for (Edge e : edges) {
      g.addEdge(e.vertex1(), e.vertex2());
    }

    GraphStatistics stats = new GraphStatistics(g);

    // getRandomNeighbor is tested 5 times for consistency
    for (int i = 0; i < 5; i++) {
      assertEquals(0, stats.getRandomNeighbor(3, rd));
      assertTrue(stats.hasEdge(0, stats.getRandomNeighbor(0, rd)));
    }

    assertEquals(Set.of(1, 2, 3), stats.getAllNeighbors(0));
    assertEquals(Set.of(), stats.getAllNeighbors(4));

    assertEquals(5, stats.getEdgeCheckCount());
    assertEquals(10, stats.getRandomNeighborCount());
    assertEquals(2, stats.getAllNeighborsCount());
  }
}
