package edu.rit.cs.graph_matching;

import java.util.Objects;

/**
 * An edge in an undirected graph. Vertex order does not matter; edges with the
 * same vertices in the opposite order are treated as equal.
 */
public record Edge(int vertex1,
                   int vertex2) {
  @Override
  public final boolean equals(Object arg0) {
    return arg0 instanceof Edge e
        && ((vertex1 == e.vertex1 && vertex2 == e.vertex2)
            || (vertex1 == e.vertex2 && vertex2 == e.vertex1));
  }

  @Override
  public final int hashCode() {
    // Ensure that swapping the vertices doesn't affect the hash
    return Objects.hash(Math.min(vertex1, vertex2), Math.max(vertex1, vertex2));
  }
}
