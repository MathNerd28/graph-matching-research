package edu.rit.cs.graph_matching;

import java.util.Arrays;
import java.util.Set;

public class DaniHayesAlgorithm {
  private final Graph           graph;
  private final AlternatingPath alternatingPath;

  public DaniHayesAlgorithm(Graph graph) {
    this.graph = graph;
    this.alternatingPath = new AlternatingPath(graph.size());
  }

  public Set<Edge> buildMatching() {
    // TODO: implement buildMatching()
    throw new UnsupportedOperationException();
  }

  private AlternatingPath findAugmentingPath(Set<Edge> matching) {
    // TODO: implement buildMatching()
    throw new UnsupportedOperationException();
  }

  private PathStatus growPath(Set<Edge> matching, AlternatingPath path, int start, int head) {
    // TODO: implement buildMatching()
    throw new UnsupportedOperationException();
  }

  private enum PathStatus {
    ACTIVE,
    DONE,
    FAIL;
  }

  private static class AlternatingPath {
    private final int[] matched;
    private final int[] unmatched;

    public AlternatingPath(int vertexCount) {
      this.matched = new int[vertexCount];
      this.unmatched = new int[vertexCount];
      clear();
    }

    public void clear() {
      Arrays.fill(matched, -1);
      Arrays.fill(unmatched, -1);
    }

    public int getMatchedNeighbor(int vertex) {
      return matched[vertex];
    }

    public int getUnmatchedNeighbor(int vertex) {
      return unmatched[vertex];
    }

    public void addMatchedEdge(int vertex1, int vertex2) {
      if (matched[vertex1] != -1 || matched[vertex2] != -1) {
        throw new IllegalStateException();
      }

      matched[vertex1] = vertex2;
      matched[vertex2] = vertex1;
    }

    public void addUnmatchedEdge(int vertex1, int vertex2) {
      if (unmatched[vertex1] != -1 || unmatched[vertex2] != -1) {
        throw new IllegalStateException();
      }

      unmatched[vertex1] = vertex2;
      unmatched[vertex2] = vertex1;
    }

    public void removeMatchedEdge(int vertex) {
      int match = matched[vertex];
      matched[vertex] = -1;
      matched[match] = -1;
    }

    public void removeUnmatchedEdge(int vertex) {
      int adjacent = unmatched[vertex];
      unmatched[vertex] = -1;
      unmatched[adjacent] = -1;
    }

    public boolean hasVertex(int vertex) {
      return matched[vertex] != -1 || unmatched[vertex] != -1;
    }
  }
}
