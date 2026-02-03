package edu.rit.cs.graph_matching;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;

public class HopcroftKarpAlgorithm {
    private Graph graph; // original graph
    private BipartiteGraph bipartiteGraph;
    protected int[] level;
    private IntHashSet blocked;
    private Set<Edge> maximumMatching;

    public HopcroftKarpAlgorithm(Graph graph) {
        this.graph = graph;
        this.bipartiteGraph = new BipartiteGraph(graph);
        this.level = new int[graph.size() + 1];
        this.maximumMatching = new HashSet<>();
    }

    /**
     * Performs a bfs
     * to build the level graph used by the Hopcroft–Karp maximum matching algorithm.
     * This corresponds to Step 1 in Algorithm A in the paper.
     * Enqueues all free left vertices (level = 0) and performs BFS along alternating paths.
     * Tracks the length of the shortest augmenting paths discovered and prunes further
     * exploration beyond that length to limit search to the current layering.
     *
     * @return true if at least one augmenting path was discovered (i.e., the level graph
     *         contains augmenting paths); false if no augmenting path exists.
     *
     * Complexity: O(V + E).
     */
    private boolean bfs() {
        Queue<Integer> Q = new ArrayDeque<>();
        int shortestAugmentingPathLength = Integer.MAX_VALUE;
        for (int u: bipartiteGraph.left) {
            if (bipartiteGraph.getMatch(u) == -1) {
                level[u] = 0;
                Q.offer(u);
            } else {
                level[u] = -1;
            }
        }

        while (!Q.isEmpty()) {
            int u = Q.poll();
            if (level[u] == -1 || level[u] + 1 > shortestAugmentingPathLength) continue;
            for (int v: graph.getAllNeighbors(u)) {
                int matchingNode = bipartiteGraph.getMatch(v);
                if (matchingNode != -1) {
                    if (level[matchingNode] == -1) {
                        level[matchingNode] = level[u] + 1;
                        Q.offer(matchingNode);
                        shortestAugmentingPathLength = level[matchingNode];
                    }
                } else {
                    shortestAugmentingPathLength = level[u] + 1;
                }
            }
        }
        return shortestAugmentingPathLength != Integer.MAX_VALUE;
    }
 
    /**
     * Performs a depth-first search for an augmenting path starting at the given vertex,
     * guided by the layering produced by the Hopcroft–Karp BFS (level[]).
     *
     * REQUIRES:
     *  - level[] must be initialized by the BFS layering step.
     *  - graph.getAllNeighbors(vertex) should iterate neighbors in the opposite partition.
     *
     * @param vertex the vertex to start DFS from (typically in the left partition)
     * @return true if an augmenting path was found (and matching updated), false otherwise
     */
    private boolean dfs(int vertex) {
        for (int neighbor: graph.getAllNeighbors(vertex)) {
            if (blocked.contains(vertex)) {
                continue;
            }
            int matchingNode = bipartiteGraph.getMatch(neighbor);
            if (matchingNode == -1 ||
                    (level[matchingNode] == level[vertex] + 1 && dfs(matchingNode))) {
                // Found an augmenting path
                bipartiteGraph.match[vertex] = neighbor;
                bipartiteGraph.match[neighbor] = vertex;
                return true;
            }
        }
        blocked.add(vertex);
        return false;
    }

    /**
     * Computes and returns the size of a maximum matching for the current bipartite graph
     * using the Hopcroft–Karp algorithm.
     *
     * Complexity: worst case O((m + n)\sqrt(n)).
     *
     * @return the number of matched pairs in the maximum matching
     */
    public Set<Edge> getMaximumMatching() {
        while (bfs()) {
            blocked = new IntHashSet();
            for (int u: bipartiteGraph.left) {
                if (bipartiteGraph.getMatch(u) == -1 && !blocked.contains(u)) {
                    dfs(u);
                }
            }
        }
        for (int u: bipartiteGraph.left) {
            int v = bipartiteGraph.getMatch(u);
            if (v != -1) {
                maximumMatching.add(new Edge(u, v));
            }
        }
        return maximumMatching;
    }
}