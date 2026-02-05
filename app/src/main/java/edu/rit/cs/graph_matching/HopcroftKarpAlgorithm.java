package edu.rit.cs.graph_matching;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.Set;

import edu.rit.cs.graph_matching.GraphUtils.BipartiteColor;

import java.util.HashSet;

public class HopcroftKarpAlgorithm {
    private final Graph graph; // original graph
    private final IntHashSet left;
    private final IntHashSet right;
    private final int[] match;
    private final int[] level;
    private final IntHashSet blocked;

    public HopcroftKarpAlgorithm(Graph graph) {
        this.graph = graph;
        this.level = new int[graph.size() + 1];
        this.blocked = new IntHashSet();
        BipartiteColor[] coloring = GraphUtils.colorBipartite(graph);
        left = new IntHashSet();
        right = new IntHashSet();
        match = new int[graph.size()];
        Arrays.fill(match, -1);
        for (int node = 0; node < coloring.length; node++) {
            if (coloring[node] == BipartiteColor.LEFT) {
                left.add(node);
            } else {
                right.add(node);
            }
        }
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
        Queue<Integer> queue = new ArrayDeque<>();
        int shortestAugmentingPathLength = Integer.MAX_VALUE;
        for (int u: left) {
            if (match[u] == -1) {
                level[u] = 0;
                queue.offer(u);
            } else {
                level[u] = -1;
            }
        }

        while (!queue.isEmpty()) {
            int u = queue.poll();
            if (level[u] == -1 || level[u] + 1 > shortestAugmentingPathLength) continue;
            for (int v: graph.getAllNeighbors(u)) {
                int matchingNode = match[v];
                if (matchingNode != -1) {
                    if (level[matchingNode] == -1) {
                        level[matchingNode] = level[u] + 1;
                        queue.offer(matchingNode);
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
            int matchingNode = match[neighbor];
            if (matchingNode == -1 ||
                    (level[matchingNode] == level[vertex] + 1 && dfs(matchingNode))) {
                // Found an augmenting path
                match[vertex] = neighbor;
                match[neighbor] = vertex;
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
            blocked.clear();
            for (int u: left) {
                if (match[u] == -1 && !blocked.contains(u)) {
                    dfs(u);
                }
            }
        }
        Set<Edge> maximumMatching = new HashSet<>();
        for (int u: left) {
            int v = match[u];
            if (v != -1) {
                maximumMatching.add(new Edge(u, v));
            }
        }
        return maximumMatching;
    }
}
