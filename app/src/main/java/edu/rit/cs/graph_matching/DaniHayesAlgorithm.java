package edu.rit.cs.graph_matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    int failures = 0;
    while ((graph.size() - unmatched.size()) / 2 < targetMatchingEdges) {
      if (findAugmentingPath()) {
        failures = 0;
        continue;
      }

      failures++;
      if (failures > 5) {
        // If we fail 5 times in a row, give up
        return;
      }
    }
  }

  private boolean findAugmentingPath() {
    if (unmatched.isEmpty()) {
      return false;
    }

    int maxAttempts = 10 * graph.size();
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      clearPath();

      start = unmatched.getRandom(random);
      head = start;
      addVertex(start);

      PathStatus status = PathStatus.ACTIVE;
      while (status == PathStatus.ACTIVE) {
        status = growPath();
      }
      if (status == PathStatus.FAIL) {
        continue;
      }

      // We have an augmenting path; augment it

      // Optimized version; currently broken
      // int vertex = start;
      // while (true) {
      // int next = getAdjacent(vertex);

      // if (next == head) {
      // setMatch(vertex, head);
      // break;
      // } else if (next == -1) {
      // // this shouldn't happen
      // break;
      // }

      // int nextNext = getMatch(next);
      // setMatch(vertex, next);
      // vertex = nextNext;
      // }
      // unmatched.remove(start);
      // unmatched.remove(head);
      // return true;

      // Unoptimized routine directly from Yongrui
      List<Integer> path = new ArrayList<>();
      path.add(start);
      int curr = start;
      int prev = -1;

      while (curr != head) {
        int next = -1;
        if (path.size() % 2 != 0) {
          next = getAdjacent(curr);
        } else {
          next = getMatch(curr);
        }

        if (next == -1 || next == prev) {
          break;
        }
        if (path.contains(next)) {
          break;
        }

        path.add(next);
        prev = curr;
        curr = next;
      }

      if (curr == head) {
        for (int i = 0; i < path.size() - 1; i += 2) {
          int u = path.get(i);
          int v = path.get(i + 1);
          setMatch(u, v);
        }
        unmatched.remove(start);
        unmatched.remove(head);
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
      addEdge(head, v0);
      addVertex(v0);
      head = v0;
      return PathStatus.DONE;
    }

    if (!hasVertex(v0)) {
      // Case 2: v0 is matched but not in path
      // Extend path to add v0 and w0
      addEdge(head, v0);
      addVertex(v0);
      addVertex(w0);
      head = w0;
      return PathStatus.ACTIVE;
    }

    // v0 is already in P, forming a cycle
    // Attempt local repair
    int v = v0;
    int w = w0;
    int maxRepair = graph.size() + 10;
    for (int repairSteps = 0; repairSteps < maxRepair; repairSteps++) {
      int vP = getAdjacent(w);
      if (vP == -1) {
        return PathStatus.FAIL;
      }

      int wP = getMatch(vP);
      removeEdge(w);
      removeVertex(w);

      // TODO: why are both conditions necessary?
      if (graph.hasEdge(vP, head) && getMatch(head) != vP && getMatch(vP) != head) {
        // Shortcut (Odd Cycle)
        addEdge(vP, head);
        addVertex(vP);
        addVertex(w0);
        head = w0;
        return PathStatus.ACTIVE;
      }

      if (wP == head) {
        // Pop (Even Cycle)
        removeEdge(vP); // implicitly removes head
        removeVertex(vP);
        removeVertex(head);
        addVertex(v0);
        addVertex(w0);
        return PathStatus.ACTIVE;
      }

      if (vP == start) {
        return PathStatus.FAIL;
      }

      removeVertex(vP);
      removeVertex(wP);
      v = vP;
      w = wP;
    }

    // Repair took too many steps; fail
    return PathStatus.FAIL; // TODO: why is this needed?
    // can't we prove that the loop will terminate based on previous state?
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
    if (vertex == -1) {
      return;
    }
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

  public static void main(String[] args) {
    MutableGraph g = GraphGenerator.generateRegularGraph(new SparseGraphImpl(100), 5);
    // GraphGenerator.mutateRegularGraph(g, 100);
    DaniHayesAlgorithm alg = new DaniHayesAlgorithm(new GraphStatistics(g));
    Set<Edge> matching = alg.generatePerfectMatching();

    List<Integer> vertices = matching.stream()
                                     .flatMapToInt(e -> IntStream.of(e.vertex1(), e.vertex2()))
                                     .boxed()
                                     .collect(Collectors.toCollection(ArrayList::new));
    vertices.sort(null);
    System.out.println(vertices);
    for (int i = 1; i < vertices.size(); i++) {
      if (vertices.get(i) != vertices.get(i - 1) + 1) {
        System.out.printf("%d ", vertices.get(i - 1));
      }
    }
    System.out.println();
    System.out.println(vertices.size());
  }
}
