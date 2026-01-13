package edu.rit.cs.graph_matching;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EdgeTest {
  @Test
  public void checkEquality() {
    Edge e1 = new Edge(0, 1);
    assertEquals(e1, e1, "Edges should be equal to themselves");

    Edge e2 = new Edge(1, 0);
    assertEquals(e1, e2, "Edges with swapped vertices should be equal");

    Edge e3 = new Edge(1, 2);
    assertNotEquals(e1, e3, "Different edges should not be equal");
  }

  @Test
  public void checkHashCode() {
    Edge e1 = new Edge(0, 1);
    assertEquals(e1.hashCode(), e1.hashCode(), "Edges should never change their hashCode");

    Edge e2 = new Edge(1, 0);
    assertEquals(e1.hashCode(), e2.hashCode(),
        "Edges with swapped vertices should have the same hashCode");

    // The hashCode contract allows different edges to have the same hashCode
  }
}
