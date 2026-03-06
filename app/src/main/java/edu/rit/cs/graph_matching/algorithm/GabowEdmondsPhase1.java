package edu.rit.cs.graph_matching.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;

/**
 * Phase 1 of Gabow's O(m*sqrt(n)) Matching Algorithm.
 * <p>
 * This class implements a dual-driven adaptation of Edmonds' algorithm. It
 * searches for a maximal set of Shortest Augmenting Paths (SAPs) by implicitly
 * tracking the dual variables y(u) for vertices and z(B) for blossoms.
 * It halts exactly when the shortest augmenting path length is discovered,
 * preserving the contracted graph state for Phase 2.
 */
public class GabowEdmondsPhase1 {

    // path
    private static final int UNLABELED = 0;
    private static final int OUTER = 1;
    private static final int INNER = 2;

    private final Graph graph;
    private final int n;

    /** Current matching status; matches[v] = w, or -1 if free */
    private final int[] matches;

    /** Alternating BFS tree parent pointers */
    private final int[] parents;

    /** Node labels in the alternating forest (UNLABELED, OUTER, INNER) */
    private final int[] labels;

    // ----- Dual Variable Tracking ----- //

    /**
     * The implicit dual variable y(u) is computed on the fly.
     * yBase[v] records the dual value at the time the vertex was labeled.
     */
    private final int[] yBase;

    /** Records the global 'delta' at the time yBase was set. */
    private final int[] yDelta;

    /** The global dual adjustment counter (number of adjustments applied) */
    private int delta;

    // ----- Blossom Contraction Structures ----- //

    /** Disjoint-set tracking the base of a blossom during Phase 1 */
    private final NodePartition base;

    /** Disjoint-set tracking the maximal positive blossoms for Phase 2 H-graph */
    private final NodePartition dBase;

    /** Bridges linking the contracted blossoms (source and target) */
    private final int[] sourceBridge;
    private final int[] targetBridge;

    // ----- Priority Queue & Auxiliary Path Tracking ----- //

    /** Array-based priority queue for O(1) tight-edge discovery */
    private final PriorityQueueArray queue;

    /** Delayed union commitments for dual adjustments */
    private final Queue<Integer> delayedUnions;

    private final int[] path1;
    private final int[] path2;
    private int lcaSearchTime = 0;

    /**
     * Initializes the Phase 1 search over the given graph.
     *
     * @param graph   the input graph
     * @param matches the current matching array (modified in place during Phase 2)
     */
    public GabowEdmondsPhase1(Graph graph, int[] matches) {
        this.graph = graph;
        this.n = graph.size();
        this.matches = matches;

        this.parents = new int[n];
        this.labels = new int[n];
        this.sourceBridge = new int[n];
        this.targetBridge = new int[n];

        this.yBase = new int[n];
        this.yDelta = new int[n];

        this.base = new NodePartition(n);
        this.dBase = new NodePartition(n);

        // Max augmenting path length is n, meaning delta <= n/2
        this.queue = new PriorityQueueArray(n / 2 + 1);
        this.delayedUnions = new LinkedList<>();

        this.path1 = new int[n];
        this.path2 = new int[n];
    }

    /**
     * Executes the Phase 1 search. It grows alternating trees and updates
     * dual variables until the first shortest augmenting path(s) are found.
     *
     * @return true if an augmenting path length was discovered; false if
     *         no more augmenting paths exist (maximum matching reached).
     */
    public boolean executeSearch() {
        queue.clear();
        delta = 0;
        boolean foundSap = false;

        Arrays.fill(parents, -1);
        Arrays.fill(labels, UNLABELED);
        base.reset();
        dBase.reset();

        // Initialize all free vertices as OUTER roots
        for (int v = 0; v < n; v++) {
            if (matches[v] == -1) {
                labels[v] = OUTER;
                yBase[v] = 1; // Initial y(u) for free vertices
                yDelta[v] = delta;
                scanEdges(v);
            }
        }

        while (2 * delta <= n) {
            Edge edge;
            while ((edge = queue.pollNextAtDelta(delta)) != null) {
                int u = edge.vertex1();
                int v = edge.vertex2();

                // Ensure 'u' is the OUTER node
                if (labels[base.find(u)] != OUTER) {
                    int temp = u;
                    u = v;
                    v = temp;
                }

                int baseU = base.find(u);
                int baseV = base.find(v);

                // Ignore invalid or stale edges inside the same blossom
                if (labels[baseU] != OUTER || v == matches[u] || baseU == baseV || labels[baseV] == INNER) {
                    continue;
                }

                if (labels[baseV] == UNLABELED) {
                    // Tree Growth Step: Found a free inner node
                    int matchedNode = matches[v];
                    yBase[v] = yBase[matchedNode] = 1;
                    yDelta[v] = yDelta[matchedNode] = delta;

                    parents[matchedNode] = v;
                    parents[v] = u;

                    labels[v] = INNER;
                    labels[matchedNode] = OUTER;

                    scanEdges(matchedNode);
                } else if (labels[baseV] == OUTER) {
                    // Collision between OUTER nodes
                    lcaSearchTime++;
                    int ancestor = findLeastCommonAncestor(baseU, baseV);

                    if (ancestor != -1) {
                        // Collision in the SAME tree -> Shrink Blossom
                        shrinkBlossom(ancestor, u, v);
                        shrinkBlossom(ancestor, v, u);
                    } else {
                        // Collision across DIFFERENT trees -> Augmenting Path Found
                        foundSap = true;
                        break;
                    }
                }
            }

            if (foundSap) {
                // Halt Edmonds' tree growth; auxiliary graph is ready for DFS
                return true;
            }

            // Dual Adjustment Step: Commit delayed blossom unions
            commitDelayedUnions();
            delta++; // Effectively updates y(u) and z(B) for all active nodes
        }

        return false;
    }

    /**
     * Calculates the implicit dual variable y(u) for a vertex in O(1) time.
     * * @param v the vertex
     * 
     * @return the current dual value y(v)
     */
    private int computeDualY(int v) {
        int baseV = base.find(v);
        if (labels[baseV] == UNLABELED)
            return 1;
        if (labels[baseV] == OUTER)
            return yBase[v] - (delta - yDelta[v]);
        return yBase[v] + (delta - yDelta[v]); // INNER vertices increase
    }

    /**
     * Scans the neighbors of an OUTER vertex to calculate when the edges
     * will become perfectly tight, adding them to the priority queue.
     * * @param u the newly OUTER vertex
     */
    private void scanEdges(int u) {
        for (int v : graph.getAllNeighbors(u)) {
            int baseV = base.find(v);
            if (matches[v] == u || labels[baseV] == INNER)
                continue;

            int slack = computeDualY(u) + computeDualY(v);
            if (labels[baseV] == UNLABELED) {
                queue.add(new Edge(u, v), delta + slack);
            } else {
                // Both are OUTER; slack drops twice as fast
                queue.add(new Edge(u, v), delta + slack / 2);
            }
        }
    }

    /**
     * Shrinks an alternating path into a newly discovered blossom.
     * * @param blossomBase the base of the new blossom
     * 
     * @param start  the starting boundary node
     * @param target the target boundary node
     */
    private void shrinkBlossom(int blossomBase, int start, int target) {
        int v = base.find(start);
        while (v != blossomBase) {
            base.union(v, blossomBase, blossomBase);
            delayedUnions.add(v);
            delayedUnions.add(blossomBase);

            v = matches[v];
            base.union(v, blossomBase, blossomBase);
            delayedUnions.add(v);
            delayedUnions.add(blossomBase);

            sourceBridge[v] = start;
            targetBridge[v] = target;

            // Adjust dual baselines for the newly assimilated nodes
            yBase[v] = yBase[v] + (delta - yDelta[v]);
            yDelta[v] = delta;

            // Re-scan from the newly OUTER nodes
            scanEdges(v);
            v = base.find(parents[v]);
        }
        delayedUnions.add(blossomBase);
        delayedUnions.add(blossomBase);
    }

    /**
     * Searches upward in lock-step to find the lowest common ancestor of two
     * OUTER nodes, returning -1 if they belong to different alternating trees.
     */
    private int findLeastCommonAncestor(int baseU, int baseV) {
        path1[baseU] = lcaSearchTime;
        path2[baseV] = lcaSearchTime;

        while ((path1[baseV] != lcaSearchTime && path2[baseU] != lcaSearchTime)
                && (matches[baseU] != -1 || matches[baseV] != -1)) {

            if (matches[baseU] != -1) {
                baseU = base.find(parents[matches[baseU]]);
                path1[baseU] = lcaSearchTime;
            }
            if (matches[baseV] != -1) {
                baseV = base.find(parents[matches[baseV]]);
                path2[baseV] = lcaSearchTime;
            }
        }

        if (path1[baseV] == lcaSearchTime)
            return baseV;
        if (path2[baseU] == lcaSearchTime)
            return baseU;
        return -1;
    }

    private void commitDelayedUnions() {
        while (!delayedUnions.isEmpty()) {
            int u = delayedUnions.poll();
            int v = delayedUnions.poll();
            if (u == v) {
                dBase.makeRepresentative(u);
            } else {
                dBase.union(u, v, v);
            }
        }
    }

    // -------------------------------------------------------------------
    // Internal O(1) Array-based Data Structures
    // -------------------------------------------------------------------

    /**
     * Array-based priority queue strictly bounded to maximum delta n/2.
     * Guarantees O(1) amortized insertion and extraction.
     */
    private static class PriorityQueueArray {
        private final List<Edge>[] queues;
        private final int maxDelta;
        private int currentDelta = 0;

        @SuppressWarnings("unchecked")
        public PriorityQueueArray(int maxDelta) {
            this.maxDelta = maxDelta;
            this.queues = new ArrayList[maxDelta];
            for (int i = 0; i < maxDelta; i++) {
                queues[i] = new ArrayList<>();
            }
        }

        public void clear() {
            for (int i = 0; i < maxDelta; i++)
                queues[i].clear();
            currentDelta = 0;
        }

        public void add(Edge edge, int tightDelta) {
            if (tightDelta < maxDelta) {
                queues[tightDelta].add(edge);
            }
        }

        public Edge pollNextAtDelta(int targetDelta) {
            if (targetDelta > currentDelta)
                currentDelta = targetDelta;
            if (targetDelta >= maxDelta || queues[targetDelta].isEmpty())
                return null;
            return queues[targetDelta].remove(queues[targetDelta].size() - 1);
        }
    }

    /**
     * Modified Union-Find offering path compression while strictly
     * preserving the structural "base" representative of the blossom.
     */
    private static class NodePartition {
        private final int[] parent; // structural parent for union-find
        private final int[] trueBase; // true base for each node

        public NodePartition(int size) {
            parent = new int[size];
            trueBase = new int[size];
            reset();
        }

        public void reset() {
            for (int i = 0; i < parent.length; i++) {
                parent[i] = i;
                trueBase[i] = i;
            }
        }

        public int find(int i) {
            int root = i;
            while (root != parent[root])
                root = parent[root];

            // Path compression
            int curr = i;
            while (curr != root) {
                int nxt = parent[curr];
                parent[curr] = root;
                curr = nxt;
            }
            return trueBase[root];
        }

        public void union(int i, int j, int newBase) {
            int rootI = getRawRoot(i);
            int rootJ = getRawRoot(j);
            if (rootI != rootJ) {
                parent[rootI] = rootJ;
                trueBase[rootJ] = newBase;
            } else {
                trueBase[rootI] = newBase;
            }
        }

        public void makeRepresentative(int i) {
            trueBase[getRawRoot(i)] = i;
        }

        private int getRawRoot(int i) {
            int root = i;
            while (root != parent[root])
                root = parent[root];
            return root;
        }
    }
}
