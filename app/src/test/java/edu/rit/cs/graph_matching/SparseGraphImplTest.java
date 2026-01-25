package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SparseGraphImplTest {
  /**
   * Covers {@link SparseGraphImpl#SparseGraphImpl(int)},
   * {@link SparseGraphImpl#size()}
   */
  @Test
  void construct() {
    Graph g = new SparseGraphImpl(1);
    assertEquals(1, g.size());
    g = new SparseGraphImpl(2);
    assertEquals(2, g.size());
    g = new SparseGraphImpl(50);
    assertEquals(50, g.size());
    g = new SparseGraphImpl(65536);
    assertEquals(65536, g.size());
    g = new SparseGraphImpl(10_000_000);
    assertEquals(10_000_000, g.size());

    assertThrows(IllegalArgumentException.class, () -> new SparseGraphImpl(0),
        "Graphs should not support 0 vertices");
  }

  /**
   * Covers {@link SparseGraphImpl#hasEdge(int, int)},
   * {@link SparseGraphImpl#addEdge(int, int)},
   * {@link SparseGraphImpl#removeEdge(int, int)}, {@link SparseGraphImpl#clear()}
   */
  @Test
  void modify() {
    MutableGraph g = new SparseGraphImpl(3);

    assertFalse(g.hasEdge(0, 1), "Edges should not exist before being added");
    g.addEdge(0, 1);
    assertTrue(g.hasEdge(0, 1), "Edges should exist after being added");
    assertDoesNotThrow(() -> g.addEdge(0, 1), "Graphs should support silently re-adding edges");

    assertFalse(g.hasEdge(1, 2), "Edges should not exist before being added");
    g.addEdge(1, 2);
    assertTrue(g.hasEdge(1, 2), "Edges should exist after being added");
    assertTrue(g.hasEdge(0, 1), "Edges should exist after others have been added");

    g.removeEdge(0, 1);
    assertFalse(g.hasEdge(0, 1), "Edges should not exist after being removed");
    assertTrue(g.hasEdge(1, 2), "Removing one edge shouldn't affect another edge");

    g.clear();
    assertFalse(g.hasEdge(1, 2), "Clearing a graph should remove all edges");

    assertThrows(UnsupportedOperationException.class, () -> g.addEdge(0, 0),
        "Graphs should not support adding self-looping edges");
    assertThrows(IndexOutOfBoundsException.class, () -> g.addEdge(0, 3),
        "Graphs should not support adding edges with out-of-range vertices");
    assertThrows(UnsupportedOperationException.class, () -> g.removeEdge(0, 0),
        "Graphs should not support removing self-looping edges");
    assertThrows(IndexOutOfBoundsException.class, () -> g.removeEdge(0, 3),
        "Graphs should not support removing edges with out-of-range vertices");
  }

  /**
   * Covers {@link SparseGraphImpl#hasEdge(int, int)},
   * {@link SparseGraphImpl#getRandomNeighbor(int)},
   * {@link SparseGraphImpl#getAllNeighbors(int)}
   * <p>
   * {@link SparseGraphImpl#getRandomNeighbor(int)} has a RNG component; thus, it
   * will only be tested in a method that is guaranteed to work.
   */
  @Test
  void query() {
    Set<Edge> edges = Set.of(new Edge(0, 1), new Edge(0, 2), new Edge(0, 3), new Edge(1, 2));
    Random rd = new Random(0);

    MutableGraph g = new SparseGraphImpl(5);
    for (Edge e : edges) {
      g.addEdge(e.vertex1(), e.vertex2());
    }

    // getRandomNeighbor is tested 5 times for consistency
    for (int i = 0; i < 5; i++) {
      assertEquals(0, g.getRandomNeighbor(3, rd));
      assertTrue(g.hasEdge(0, g.getRandomNeighbor(0, rd)));
    }

    for (Edge e : edges) {
      assertTrue(g.hasEdge(e.vertex1(), e.vertex2()));
      assertTrue(g.hasEdge(e.vertex2(), e.vertex1()), "Vertex order doesn't matter");
    }

    assertFalse(g.hasEdge(0, 4));
    assertFalse(g.hasEdge(4, 0));

    assertEquals(Set.of(1, 2, 3), g.getAllNeighbors(0));
    assertEquals(Set.of(), g.getAllNeighbors(4));
  }

  /**
   * Briefly checks that operations work properly on a very large sparse graph.
   */
  @Test
  void veryLargeGraph() {
    MutableGraph g = new SparseGraphImpl(10000);

    // Ensure the graph is initially empty
    g.clear();
    for (int i = 0; i < g.size(); i++) {
      for (int j = i + 1; j < g.size(); j++) {
        assertFalse(g.hasEdge(i, j));
        assertFalse(g.hasEdge(j, i));
      }
    }

    // Make every other vertex a neighbor of 0
    Set<Integer> neighbors = new LinkedHashSet<>();
    for (int i = 1; i < g.size(); i++) {
      g.addEdge(0, i);
      neighbors.add(i);
    }
    // Ensure all of those edges exist
    assertEquals(neighbors, g.getAllNeighbors(0));
    for (int i = 1; i < g.size(); i++) {
      assertTrue(g.hasEdge(0, i));
      assertTrue(g.hasEdge(i, 0));
    }
    // Ensure no other edges exist
    for (int i = 1; i < g.size(); i++) {
      for (int j = i + 1; j < g.size(); j++) {
        assertFalse(g.hasEdge(i, j));
        assertFalse(g.hasEdge(j, i));
      }
      assertEquals(Set.of(0), g.getAllNeighbors(i));
    }

    // Fill the entire graph
    for (int i = 0; i < g.size(); i++) {
      for (int j = i + 1; j < g.size(); j++) {
        g.addEdge(i, j);
      }
    }
    // Check that every edge is present
    for (int i = 0; i < g.size(); i++) {
      for (int j = i + 1; j < g.size(); j++) {
        assertTrue(g.hasEdge(i, j));
        assertTrue(g.hasEdge(j, i));
      }
    }

    // Clear the graph, and ensure no edges are present
    g.clear();
    for (int i = 0; i < g.size(); i++) {
      for (int j = i + 1; j < g.size(); j++) {
        assertFalse(g.hasEdge(i, j));
        assertFalse(g.hasEdge(j, i));
      }
    }
  }
}
