package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DenseGraphImplTest {
  /**
   * Covers {@link DenseGraphImpl#DenseGraphImpl(int)},
   * {@link DenseGraphImpl#size()}
   */
  @Test
  void construct() {
    Graph g = new DenseGraphImpl(1);
    assertEquals(1, g.size());
    g = new DenseGraphImpl(2);
    assertEquals(2, g.size());
    g = new DenseGraphImpl(50);
    assertEquals(50, g.size());
    g = new DenseGraphImpl(65536);
    assertEquals(65536, g.size());

    assertThrows(IllegalArgumentException.class, () -> new DenseGraphImpl(0),
        "Graphs should not support 0 vertices");
    assertThrows(IllegalArgumentException.class, () -> new DenseGraphImpl(65537),
        "DenseGraphImpl doesn't support more than 65536 vertices");
  }

  /**
   * Covers {@link DenseGraphImpl#hasEdge(int, int)},
   * {@link DenseGraphImpl#addEdge(int, int)},
   * {@link DenseGraphImpl#removeEdge(int, int)}, {@link DenseGraphImpl#clear()}
   */
  @Test
  void modify() {
    MutableGraph g = new DenseGraphImpl(3);

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

    MutableGraph g = new DenseGraphImpl(5);
    for (Edge e : edges) {
      g.addEdge(e.vertex1(), e.vertex2());
    }

    // getRandomNeighbor is tested 5 times for consistency
    for (int i = 0; i < 5; i++) {
      assertEquals(0, g.getRandomNeighbor(3));
      assertTrue(g.hasEdge(0, g.getRandomNeighbor(0)));
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
   * Briefly checks that operations work properly on a very large dense graph.
   */
  @Test
  void veryLargeGraph() {
    MutableGraph g = new DenseGraphImpl(10000);

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
