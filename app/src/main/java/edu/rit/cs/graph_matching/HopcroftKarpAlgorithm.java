package edu.rit.cs.graph_matching;

import java.util.ArrayDeque;
import java.util.Queue;

public class HopcroftKarpAlgorithm {
    private Graph graph; // original graph
    private BipartiteGraph bipartiteGraph;
    protected int[] level;
    private IntHashSet Blocked;

    public HopcroftKarpAlgorithm(Graph graph) {
        this.graph = graph;
        this.bipartiteGraph = new BipartiteGraph(graph);
        this.level = new int[graph.size() + 1];
    }

    /**
     * Performs a bfs
     * to build the level graph used by the Hopcroft–Karp maximum matching algorithm.
     * This corresponds to Step 1 in Algorithm A in the paper.
     *
     * Effects:
     * - Initializes the per-vertex level values for left-side vertices: free left vertices
     *   are assigned level 0; non-free or unreachable left vertices are set to -1; reachable
     *   matched left vertices get levels corresponding to their distance from a free left vertex.
     * - Uses bipartiteGraph.getMatch() to follow matched edges and graph.getAllNeighbors()
     *   to iterate over neighbors.
     *
     * Behavior:
     * - Enqueues all free left vertices (level = 0) and performs BFS along alternating paths.
     * - Tracks the length of the shortest augmenting paths discovered and prunes further
     *   exploration beyond that length to limit search to the current layering.
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
     * For each neighbor of the vertex this method:
     *  - skips exploration if the starting vertex is already marked in Blocked,
     *  - checks the neighbor's current match,
     *  - if the neighbor is free or its matched partner lies on the next level and
     *    a recursive DFS from that partner succeeds, updates the matching arrays
     *    (bipartiteGraph.leftMatch and bipartiteGraph.rightMatch) and returns true.
     *
     * If no augmenting path is found, the vertex is added to Blocked to avoid
     * redundant work in the current phase and the method returns false.
     *
     * Side effects:
     *  - May modify bipartiteGraph.leftMatch and bipartiteGraph.rightMatch on success.
     *  - Adds vertex to Blocked on failure.
     *
     * Preconditions:
     *  - level[] must be initialized by the BFS layering step.
     *  - graph.getAllNeighbors(vertex) should iterate neighbors in the opposite partition.
     *
     * @param vertex the vertex to start DFS from (typically in the left partition)
     * @return true if an augmenting path was found (and matching updated), false otherwise
     */
    private boolean dfs(int vertex) {
        for (int neighbor: graph.getAllNeighbors(vertex)) {
            if (Blocked.contains(vertex)) {
                continue;
            }
            int matchingNode = bipartiteGraph.getMatch(neighbor);
            if (matchingNode == -1 ||
                    (level[matchingNode] == level[vertex] + 1 && dfs(matchingNode))) {
                // Found an augmenting path
                bipartiteGraph.leftMatch[vertex] = neighbor;
                bipartiteGraph.rightMatch[neighbor] = vertex;
                return true;
            }
        }
        Blocked.add(vertex);
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
    public int getMaximumMatching() {
        int matchingSize = 0;
        while (bfs()) {
            Blocked = new IntHashSet();
            for (int u: bipartiteGraph.left) {
                if (bipartiteGraph.getMatch(u) == -1 && !Blocked.contains(u)) {
                    if (dfs(u)) {
                        matchingSize++;
                    }
                }
            }
        }
        return matchingSize;
    }
}