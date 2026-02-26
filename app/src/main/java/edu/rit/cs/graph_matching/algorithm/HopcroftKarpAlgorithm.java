package edu.rit.cs.graph_matching.algorithm;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;

import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
import edu.rit.cs.graph_matching.graph.GraphUtils;
import edu.rit.cs.graph_matching.graph.GraphUtils.BipartiteColor;
import edu.rit.cs.graph_matching.util.IntHashSet;

public class HopcroftKarpAlgorithm implements MatchingAlgorithm {
    private final Graph      graph;  // original graph
    private final IntHashSet left;
    private final IntHashSet right;
    private final int[]      match;
    private final int[]      level;
    private final IntHashSet blocked;

    private Iterator<Integer> leftItr;
    private boolean           finished = false;

    public HopcroftKarpAlgorithm(Graph graph) {
        this.graph = graph;
        this.level = new int[graph.size()];
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
     * Performs a bfs to build the level graph used by the Hopcroft–Karp maximum
     * matching algorithm. This corresponds to Step 1 in Algorithm A in the
     * paper. Enqueues all free left vertices (level = 0) and performs BFS along
     * alternating paths. Tracks the length of the shortest augmenting paths
     * discovered and prunes further exploration beyond that length to limit
     * search to the current layering.
     *
     * @return true if at least one augmenting path was discovered (i.e., the
     *     level graph contains augmenting paths); false if no augmenting path
     *     exists. Complexity: O(V + E).
     */
    private boolean bfs() {
        Queue<Integer> queue = new ArrayDeque<>();
        int shortestAugmentingPathLength = Integer.MAX_VALUE;
        for (int u : left) {
            if (match[u] == -1) {
                level[u] = 0;
                queue.offer(u);
            } else {
                level[u] = -1;
            }
        }

        while (!queue.isEmpty()) {
            int u = queue.poll();
            if (level[u] == -1 || level[u] + 1 > shortestAugmentingPathLength) {
                continue;
            }
            for (int v : graph.getAllNeighbors(u)) {
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
     * Performs a depth-first search for an augmenting path starting at the
     * given vertex, guided by the layering produced by the Hopcroft–Karp BFS
     * (level[]).
     * <p>
     * REQUIRES:
     * <ul>
     * <li>level[] must be initialized by the BFS layering step.</li>
     * <li>graph.getAllNeighbors(vertex) should iterate neighbors in the
     * opposite partition.</li>
     * </ul>
     *
     * @param vertex
     *     the vertex to start DFS from (typically in the left partition)
     * @return if an augmenting path was found (and matching updated), the
     *     length of the augmenting path; otherwise, -1
     */
    private int dfs(int vertex) {
        for (int neighbor : graph.getAllNeighbors(vertex)) {
            if (blocked.contains(vertex)) {
                continue;
            }
            int matchingNode = match[neighbor];
            if (matchingNode == -1) {
                // Found an augmenting path
                match[vertex] = neighbor;
                match[neighbor] = vertex;
                return 1;
            } else if (level[matchingNode] == level[vertex] + 1) {
                int pathLength = dfs(matchingNode);
                if (pathLength > 0) {
                    // Found an augmenting path
                    match[vertex] = neighbor;
                    match[neighbor] = vertex;
                    return pathLength + 2;
                }
            }
        }
        blocked.add(vertex);
        return -1;
    }

    @Override
    public int augment() {
        if (finished) {
            return -1;
        }

        if (leftItr == null) {
            if (!bfs()) {
                finished = true;
                return -1;
            }
            blocked.clear();
            leftItr = left.iterator();
        }

        while (true) {
            while (leftItr.hasNext()) {
                int u = leftItr.next();
                if (match[u] == -1 && !blocked.contains(u)) {
                    int pathLength = dfs(u);
                    if (pathLength > 0) {
                        return pathLength;
                    }
                }
            }

            if (!bfs()) {
                finished = true;
                return -1;
            }
            blocked.clear();
            leftItr = left.iterator();
        }
    }

    @Override
    public Set<Edge> getCurrentMatching() {
        Set<Edge> matching = new HashSet<>();
        for (int u : left) {
            int v = match[u];
            if (v != -1) {
                matching.add(new Edge(u, v));
            }
        }
        return matching;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }
}
