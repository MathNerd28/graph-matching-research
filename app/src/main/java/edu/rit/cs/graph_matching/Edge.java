package edu.rit.cs.graph_matching;

import java.util.Objects;

/**
 * An edge in an undirected graph. Vertice order does not matter; edges with the
 * same vertices in the opposite order are treated as equal.
 */
public record Edge(int vertice1,
                   int vertice2) {
  @Override
  public final boolean equals(Object arg0) {
    return arg0 instanceof Edge e
        && ((vertice1 == e.vertice1 && vertice2 == e.vertice2)
            || (vertice1 == e.vertice2 && vertice2 == e.vertice1));
  }

  @Override
  public final int hashCode() {
    // Ensure that swapping the vertices doesn't affect the hash
    return Objects.hash(Math.min(vertice1, vertice2), Math.max(vertice1, vertice2));
  }
}
