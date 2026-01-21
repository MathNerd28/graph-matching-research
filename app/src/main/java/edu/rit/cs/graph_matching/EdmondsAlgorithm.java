package edu.rit.cs.graph_matching;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Edmonds' Blossom Algorithm for maximum matching in a general undirected
 * graph. Adapted from Rosetta Code:
 * https://rosettacode.org/wiki/Blossom_algorithm#Java. Added documentation, and
 * slightly rewrote some parts for performance and readability.
 */
public class EdmondsAlgorithm {
  /** The input graph */
  private final Graph graph;

  /**
   * matches[v] = vertex matched with vertex v, or -1 if unmatched
   */
  private final int[] matches;

  /**
   * parents[v] = parent of vertex v in the alternating BFS tree
   */
  private final int[] parents;

  /**
   * bases[v] = base of the blossom containing vertex v
   */
  private final int[] bases;

  /**
   * Create a blossom matching solver for the given graph.
   *
   * @param graph
   *   the graph to be solved
   */
  public EdmondsAlgorithm(Graph graph) {
    this.graph = graph;

    this.matches = new int[graph.size()];
    this.parents = new int[graph.size()];
    this.bases = new int[graph.size()];
  }

  /**
   * Compute the least common ancestor of two vertices in the alternating
   * forest. This identifies the base of a newly found blossom.
   * <p>
   * This method assumes that such a common ancestor exists, and should only be
   * called in such cases.
   *
   * @param vertex1
   *   the first vertex
   * @param vertex2
   *   the second vertex
   */
  private int findLeastCommonAncestor(int vertex1, int vertex2) {
    Set<Integer> ancestors1 = new LinkedHashSet<>();

    // Walk upward from a to find all of a's alternating ancestors
    int a = vertex1;
    while (true) {
      // Add base of current vertex to ancestors
      int base1 = bases[a];
      ancestors1.add(base1);

      // If unmatched, then no more ancestors exist
      int match = matches[base1];
      if (match < 0) {
        break;
      }

      // Continue walking up the alternating forest
      a = parents[match];
    }

    // Walk upward from b until an ancestor of a is found
    int b = vertex2;
    while (true) {
      int base2 = bases[b];
      if (ancestors1.contains(base2)) {
        // found a common ancestor
        return base2;
      } else {
        // since we haven't found a common ancestor yet,
        // and we assume one exists, we assume a match exists here
        b = parents[matches[base2]];
      }
    }
  }

  /**
   * Mark all vertices on the path from vertex v to a blossom base as belonging
   * to the blossom, and fix parent pointers during contraction.
   *
   * @param vertex
   *   the source vertex
   * @param base
   *   the blossom base
   * @param parent
   *   the new parent of the vertex
   */
  private Set<Integer> computeBlossomPath(int vertex, int base, int parent) {
    Set<Integer> blossomPath = new LinkedHashSet<>();

    // Walk upward from v until we find the base
    int v = vertex;
    int p = parent;
    while (bases[v] != base) {
      int matchOfV = matches[v];

      // Add v's base and its match's base to the blossom
      blossomPath.add(bases[v]);
      blossomPath.add(bases[matchOfV]);

      // Fix parent pointers
      parents[v] = p;
      p = matchOfV;
      v = parents[matchOfV];
    }

    return blossomPath;
  }

  /**
   * Run a BFS to find an augmenting path starting from the given unmatched root
   * vertex.
   *
   * @param root
   *   the unmatched root vertex
   * @return true if an augmenting path was found
   */
  private boolean findAugmentingPath(int root) {
    Arrays.fill(parents, -1);

    // Initially, each vertex is its own blossom base
    for (int i = 0; i < graph.size(); i++) {
      bases[i] = i;
    }

    // Enqueue root
    Queue<Integer> bfsQueue = new ArrayDeque<>();
    Set<Integer> enqueued = new LinkedHashSet<>();
    bfsQueue.add(root);
    enqueued.add(root);

    while (!bfsQueue.isEmpty()) {
      int vertex = bfsQueue.poll();

      for (int neighbor : graph.getAllNeighbors(vertex)) {
        // Ignore self-loops inside a blossom or matched edges
        if (bases[vertex] == bases[neighbor] || matches[vertex] == neighbor) {
          continue;
        }

        if (neighbor == root || (matches[neighbor] >= 0 && parents[matches[neighbor]] >= 0)) {
          // Case 1: Found a blossom (odd cycle)

          int commonAncestor = findLeastCommonAncestor(vertex, neighbor);

          Set<Integer> currentBlossom = new LinkedHashSet<>();
          currentBlossom.addAll(computeBlossomPath(vertex, commonAncestor, neighbor));
          currentBlossom.addAll(computeBlossomPath(neighbor, commonAncestor, vertex));

          // Contract the blossom
          for (int i = 0; i < graph.size(); i++) {
            if (currentBlossom.contains(bases[i])) {
              bases[i] = commonAncestor;
              if (!enqueued.contains(i)) {
                enqueued.add(i);
                bfsQueue.add(i);
              }
            }
          }
        } else if (parents[neighbor] < 0) {
          // Case 2: Extend alternating tree

          parents[neighbor] = vertex;

          // Found an augmenting path
          if (matches[neighbor] < 0) {
            augmentMatching(neighbor);
            return true;
          }

          // Continue BFS from the matched partner
          int matchedNeighbor = matches[neighbor];
          if (!enqueued.contains(matchedNeighbor)) {
            enqueued.add(matchedNeighbor);
            bfsQueue.add(matchedNeighbor);
          }
        }
      }
    }
    return false;
  }

  /**
   * Flip matching edges along the discovered augmenting path.
   *
   * @param freeVertex
   *   the initial vertex in the augmenting path
   */
  private void augmentMatching(int freeVertex) {
    int current = freeVertex;

    while (current >= 0) {
      int previous = parents[current];
      int next = (previous >= 0) ? matches[previous] : -1;

      matches[current] = previous;
      if (previous >= 0) {
        matches[previous] = current;
      }

      current = next;
    }
  }

  /**
   * Compute the maximum matching on the input graph.
   *
   * @return the edges in the maximum matching
   */
  public Set<Edge> computeMaximumMatching() {
    // Initially, all vertices are unmatched
    Arrays.fill(matches, -1);

    // Compute matching by repeatedly augmenting
    for (int v = 0; v < graph.size(); v++) {
      if (matches[v] < 0) {
        findAugmentingPath(v);
      }
    }

    // Build results
    Set<Edge> matching = new LinkedHashSet<>();
    for (int v = 0; v < graph.size(); v++) {
      if (matches[v] >= 0) {
        matching.add(new Edge(v, matches[v]));
      }
    }
    return matching;
  }
}
