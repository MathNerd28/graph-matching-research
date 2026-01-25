package edu.rit.cs.graph_matching;

import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

public class DaniHayesAlgorithm {
  private final Graph  graph;
  private final Random random;

  private final int[]      matches;
  private final int[]      adjacents;
  private final BitSet     inPath;
  private final IntHashSet unmatched;

  private int start;
  private int head;

  public DaniHayesAlgorithm(Graph graph) {
    this.graph = graph;
    this.random = new Random();

    this.matches = new int[graph.size()];
    this.adjacents = new int[graph.size()];
    this.inPath = new BitSet(graph.size());
    this.unmatched = new IntHashSet();

    Arrays.fill(matches, -1);
    for (int v = 0; v < graph.size(); v++) {
      unmatched.add(v);
    }
    clearPath();
  }

  public Set<Edge> generateMatching() {
    double averageDegree = 0;
    for (int v = 0; v < graph.size(); v++) {
      averageDegree += graph.getAllNeighbors(v)
                            .size();
    }
    averageDegree /= graph.size();
    double targetMatchingSize =
        graph.size() / 2.0 * (1.0 - 1.0 / (Math.floor(averageDegree) + 1.0));

    buildMatching((int) targetMatchingSize);
    return buildResult();
  }

  public Set<Edge> generatePerfectMatching() {
    buildMatching(graph.size() / 2);
    return buildResult();
  }

  private Set<Edge> buildResult() {
    Set<Edge> matching = new LinkedHashSet<>();
    for (int v = 0; v < graph.size(); v++) {
      if (matches[v] != -1) {
        matching.add(new Edge(v, matches[v]));
      }
    }
    return matching;
  }

  private void buildMatching(int targetMatchingEdges) {
    // Loop could run indefinitely; allow interruption for e.g. timeouts
    while ((graph.size() - unmatched.size()) / 2 < targetMatchingEdges
        && !Thread.currentThread()
                  .isInterrupted()) {
      if (findAugmentingPath()) {
        // We have an augmenting path; augment it

        int vertex = start;
        while (true) {
          int next = getAdjacent(vertex);

          if (next == head) {
            setMatch(vertex, head);
            break;
          }

          int nextNext = getMatch(next);
          setMatch(vertex, next);
          vertex = nextNext;
        }
        unmatched.remove(start);
        unmatched.remove(head);
      }
    }
  }

  private boolean findAugmentingPath() {
    // Loop could run indefinitely; allow interruption for e.g. timeouts
    while (!Thread.currentThread()
                  .isInterrupted()) {
      clearPath();

      start = unmatched.getRandom(random);
      head = start;
      addVertex(start);

      PathStatus status = PathStatus.ACTIVE;
      while (status == PathStatus.ACTIVE) {
        status = growPath();
      }
      if (status != PathStatus.FAIL) {
        return true;
      }
    }

    return false;
  }

  private PathStatus growPath() {
    // v0 = random element of N(h) \ M(h), i.e. a random neighbor of head except
    // its match
    // scale attempts by degree to handle non-regular hubs correctly
    int v0 = -1;
    int maxAttempts = 2 * graph.getAllNeighbors(head)
                               .size()
        + 10;
    int headMatch = getMatch(head);
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      int neighbor = graph.getRandomNeighbor(head);
      if (neighbor != headMatch) {
        v0 = neighbor;
        break;
      }
    }
    if (v0 == -1 || v0 == start) {
      return PathStatus.FAIL;
    }

    // w0 = M(v0), i.e. the match of v0
    int w0 = getMatch(v0);
    if (w0 == -1) {
      // Case 1: v0 is unmatched, path is augmenting

      // Add {h, v0} (unmatched) to path
      addEdge(head, v0);
      addVertex(v0);

      head = v0;
      return PathStatus.DONE;
    }

    if (!hasVertex(v0)) {
      // Case 2: v0 is matched but not in path

      // Add {h, v0} (unmatched) to path
      // Add {v0, w0} (matched) to path
      addEdge(head, v0);
      addVertex(v0);
      addVertex(w0);

      head = w0;
      return PathStatus.ACTIVE;
    }

    // v0 is already in P, forming a cycle
    // Attempt local repair
    int w = w0;
    while (true) {
      int vP = getAdjacent(w);
      int wP = getMatch(vP);

      // Delete {w, vP} (unmatched) from path
      removeEdge(w);
      removeVertex(w);

      if (graph.hasEdge(vP, head)) {
        // Shortcut (Odd Cycle)

        // Add {vP, h} (unmatched) to path
        addEdge(vP, head);

        addVertex(w0);
        head = w0;
        return PathStatus.ACTIVE;
      } else if (wP == head) {
        // Pop (Even Cycle)

        // Delete {vP, wP} (matched) from path
        removeVertex(vP);
        removeVertex(wP);

        head = w0;
        return PathStatus.ACTIVE;
      } else if (vP == start) {
        return PathStatus.FAIL;
      }

      // Delete {vP, wP} (matched) from path
      removeVertex(vP);
      removeVertex(wP);

      w = wP;
    }
  }

  private enum PathStatus {
    ACTIVE,
    DONE,
    FAIL;
  }

  private void clearPath() {
    Arrays.fill(adjacents, -1);
    inPath.clear();
    this.start = -1;
    this.head = -1;
  }

  private int getMatch(int vertex) {
    return matches[vertex];
  }

  private int getAdjacent(int vertex) {
    return adjacents[vertex];
  }

  private void addEdge(int vertex1, int vertex2) {
    adjacents[vertex1] = vertex2;
    adjacents[vertex2] = vertex1;
  }

  private void removeEdge(int vertex) {
    int adjacent = adjacents[vertex];
    adjacents[vertex] = -1;
    if (adjacent == -1) {
      return;
    }
    adjacents[adjacent] = -1;
  }

  private boolean hasVertex(int vertex) {
    return inPath.get(vertex);
  }

  private void addVertex(int vertex) {
    inPath.set(vertex);
  }

  private void removeVertex(int vertex) {
    inPath.clear(vertex);
  }

  private void setMatch(int vertex1, int vertex2) {
    matches[vertex1] = vertex2;
    matches[vertex2] = vertex1;
  }
}
