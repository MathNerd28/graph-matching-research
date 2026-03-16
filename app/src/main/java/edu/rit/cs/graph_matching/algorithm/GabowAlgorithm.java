package edu.rit.cs.graph_matching.algorithm;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Map;

import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
import edu.rit.cs.graph_matching.util.IntHashSet;

/**
 * Phase 1 and 2 of Gabow's O(m*sqrt(n)) Matching Algorithm.
 * c.f.https://arxiv.org/abs/1703.03998
 * the implementation is also based on this paper:
 * https://arxiv.org/abs/2409.14849
 * <p>
 * This class implements a dual-driven adaptation of Edmonds' algorithm. It
 * searches for a maximal set of Shortest Augmenting Paths (SAPs) by implicitly
 * tracking the dual variables y(u) for vertices and z(B) for blossoms.
 * It halts exactly when the shortest augmenting path length is discovered,
 * preserving the contracted graph state for Phase 2, which then constructs
 * augmenting paths over the implicitly contracted H-graph using Depth-First
 * Search.
 */
public class GabowAlgorithm {

    // path
    private static final int UNLABELED = 0;
    private static final int OUTER = 1;
    private static final int INNER = 2;

    private final Graph graph;
    private final int n;

    /** Current matching status; matches[v] = w, or -1 if free */
    private final int[] matches;

    // ----- Blossom Contraction Structures ----- //

    /** Disjoint-set tracking the base of a blossom during Phase 1 */
    private final NodePartition base;

    /** Maximal positive blossoms in G */
    private final NodePartition dBase;
    // rep[v] is equivalent to dBase.find(v) and is used for clarity

    /** Alternating BFS tree parent pointers */
    private final int[] parents;
    private final int[] parentHG; // For Phase 2 DFS on H-graph

    /** Bridges linking the contracted blossoms (source and target) */
    private final int[] sourceBridge;
    private final int[] targetBridge;
    // a blossom consists of two paths x--z and
    // y--z plus the edge xy; z is the base of the blossom.
    // the nodes on the path from x to z store x as source_bridge and y as
    // target_bridge.

    private final Edge[] bridgeHG;
    private final int[] dirHG;

    private final int[] path1;
    private final int[] path2;

    /** Node labels in the alternating forest (UNLABELED, OUTER, INNER) */
    private final int[] labels;

    /** Array-based priority queue for O(1) tight-edge discovery */
    private final PriorityQueueArray queue;

    private int phase2Counter;

    private final ArrayList<Integer> phase1Tree = new ArrayList<>();

    int lcaSearchTime = 0; // Global counter to mark nodes during least common ancestor search

    /**
     * base dual and the time when the node is labelled; the node is represented by
     * its base if contracted
     */
    private int[] yBase;
    private int[] yDelta;

    // ----- H-Graph Structures for Phase 2 ----- //
    private final Graph H;
    private final int[] matchHG; // mateHG[h] = h' if h is matched to h' in H, else -1
    private final Map<Edge, Boolean> isEdgeofH; // isEdgeofH[e] = 1 iff the edge e in G corresponds to an edge in H
    private final List<Integer>[] contractedInto; // contractedInto[h] = list of G-nodes contracted into H-node h
    private final int[] labelHG; // Labels for H-graph DFS (UNLABELED, OUTER, INNER)

    /** The global dual adjustment counter (number of adjustments applied) */
    private int delta;
    private final int[] evenTime;

    // ----- Priority Queue & Auxiliary Path Tracking ----- //

    /** Delayed union commitments for dual adjustments */
    // private final Queue<Integer> delayedUnions;

    /**
     * Initializes the algorithm over the given graph.
     *
     * @param graph   the input graph
     * @param matches the current matching array (modified in place during Phase 2)
     */
    @SuppressWarnings("unchecked")
    public GabowAlgorithm(Graph graph, int[] matches) {
        this.graph = graph;
        this.n = graph.size();
        this.matches = matches;

        this.parents = new int[n];
        this.parentHG = new int[n];
        this.labels = new int[n];
        this.sourceBridge = new int[n];
        this.targetBridge = new int[n];

        this.phase2Counter = 0;

        this.yBase = new int[n];
        java.util.Arrays.fill(this.yBase, 1); // Initial y(u) = 1 for all vertices
        this.yDelta = new int[n];

        this.base = new NodePartition(n);
        this.dBase = new NodePartition(n);

        // Max augmenting path length is n, meaning delta <= n/2
        this.queue = new PriorityQueueArray(n / 2 + 1);

        this.path1 = new int[n];
        this.path2 = new int[n];

    }

    /**
     * Computes the maximum cardinality matching by repeatedly running Phase 1 and
     * Phase 2.
     *
     * @return a Set containing the edges that make up the maximum matching
     */
    public Set<Edge> computeMaximumMatching() {
        // Loop: Phase 1 -> if no augmenting path halt -> Phase 2 -> augment
        while (executePhase1()) {
            executePhase2();
        }

        // Construct the final matching set from the matches array
        Set<Edge> matchingEdges = new HashSet<>();
        for (int v = 0; v < n; v++) {
            int w = matches[v];
            // Only add if matched, and strictly v < w to avoid duplicate (w, v) entries
            if (w != -1 && v < w) {
                matchingEdges.add(new Edge(v, w));
            }
        }

        return matchingEdges;
    }

    // -------------------------------------------------------------------
    // Phase 1: Dual-Driven Edmonds' Search for Shortest Augmenting Paths
    // -------------------------------------------------------------------

    public boolean executePhase1() {
        queue.clear();
        delta = 0;
        boolean foundSap = false;
        List<Integer> delayedUnions = new ArrayList<>();

        // TODO: fininish initalization
        base.split(phase1Tree);
        dBase.split(phase1Tree);
        for (int v : phase1Tree) {
            labels[v] = ((matches[v] == -1) ? OUTER : UNLABELED);
            if (matches[v] == -1) {
                scanEdges(v);
            } else {
                phase1Tree.set(v, -1);
            }
        }

        while (2 * delta <= n) {
            // System.out.println("[Phase 1] Processing at Delta: " + delta);
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
                    int ancestor = findLeastCommonAncestor(baseU, baseV);

                    if (ancestor != -1) {
                        // Collision in the SAME tree -> Shrink Blossom
                        shrinkPath(ancestor, u, v, delayedUnions);
                        shrinkPath(ancestor, v, u, delayedUnions);
                    } else {
                        // Collision across DIFFERENT trees -> Augmenting Path Found
                        foundSap = true;
                        break;
                    }
                }
            }

            if (foundSap) {
                // 1. Initialize H-graph mapping structures
                // Arrays.fill(mateHG, -1);
                // for (int i = 0; i < n; i++) {
                // contractedInto[i].clear();
                // }

                // 2. Group G-nodes into their contracted H-nodes (dbase)
                for (int v : phase1Tree) {
                    contractedInto[dBase.find(v)].add(v);
                    matchHG[v] = -1;
                    for (int u : graph.getAllNeighbors(v)) {
                        isEdgeofH.put(new Edge(u, v), false); // Initialize all edges as not in H

                    }
                }

                // 3. Evaluate edges to establish tight connections and H-graph matchings
                for (int u : phase1Tree) {
                    int uH = dBase.find(u);

                    for (int v : graph.getAllNeighbors(u)) {
                        int vH = dBase.find(v);

                        // Check if the edge crosses H-node boundaries and is tight ( d(u) + d(v) ==
                        // w[e] )
                        if (uH != vH && isEdgeTight(u, v)) {

                            // In the paper, w[e] == 2 implies 'e' is a matching edge.
                            if (matches[u] == v) {
                                matchHG[uH] = vH;
                                matchHG[vH] = uH;
                            }

                        }
                    }
                }

                // Halt Edmonds' tree growth; auxiliary graph is ready for DFS
                commitDelayedUnions(delayedUnions);
                return true;
            }

            // Dual Adjustment Step: Commit delayed blossom unions
            commitDelayedUnions(delayedUnions);
            delta++;
        }

        return false;
    }

    private int computeDualY(int v) {
        int baseV = base.find(v);
        if (labels[baseV] == UNLABELED)
            return 1;
        if (labels[baseV] == OUTER)
            return yBase[v] - (delta - yDelta[v]); // OUTER vertices decrease
        return yBase[v] + (delta - yDelta[v]); // INNER vertices increase
    }

    private void scanEdges(int u) {
        for (int v : graph.getAllNeighbors(u)) {
            int baseV = base.find(v);
            if (matches[v] == u || labels[baseV] == INNER)
                continue;

            int slack = computeDualY(u) + computeDualY(v);
            if (labels[baseV] == UNLABELED) {
                queue.add(new Edge(u, v), delta + slack);
            } else {
                queue.add(new Edge(u, v), delta + slack / 2);
            }
        }
    }

    private void shrinkPath(int blossomBase, int start, int target, List<Integer> delayedUnions) {
        // System.out.println("[Phase 1] Shrinking blossom at base: " + blossomBase + "
        // from " + start + " to " + target);
        int v = base.find(start);
        while (v != blossomBase) {
            base.union(v, blossomBase);
            delayedUnions.add(v);
            delayedUnions.add(blossomBase);

            v = matches[v];
            base.union(v, blossomBase);
            delayedUnions.add(v);
            delayedUnions.add(blossomBase);

            base.makeRep(blossomBase);

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

    private int findLeastCommonAncestor(int baseU, int baseV) {
        lcaSearchTime++; // Guarantees unique path markers per search

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

    private void commitDelayedUnions(List<Integer> delayedUnions) {
        while (!delayedUnions.isEmpty()) {
            int u = delayedUnions.remove(delayedUnions.size() - 1);
            int v = delayedUnions.remove(delayedUnions.size() - 1);
            if (u == v && u != -1) {
                dBase.makeRep(u);
            } else {
                dBase.union(u, v);
            }
        }
    }

    // -------------------------------------------------------------------
    // Phase 2: DFS on the Contracted H-Graph
    // -------------------------------------------------------------------

    private void executePhase2() {
        // TODO: Implement DFS to find augmenting paths in the H-graph and update the
        // matching
        java.util.Arrays.fill(labelHG, UNLABELED);
        ArrayList<ArrayList<Integer>> augPaths = new ArrayList<>(n); // find a maximal set of augmenting paths in H

        for (int vH : phase1Tree) {
            if (vH != dBase.find(vH))
                continue; // In this case vH is not node in H.
            if (matchHG[vH] == -1 && labelHG[vH] == UNLABELED) {
                labelHG[vH] = OUTER;
                evenTime[vH] = phase2Counter++;
                int freeNode = augPathDFS(vH);
                if (freeNode != -1) {
                    ArrayList<Integer> augPath = findAugPathInH(freeNode);
                    augPaths.add(augPath);
                }
            }

        }

        // clear H
        for (int v : phase1Tree) {
            contractedInto[dBase.find(v)].clear();
        }
    }

    private int augPathDFS(int vH) {
        for (int v : contractedInto[vH]) {
            for (int u : graph.getAllNeighbors(v)) {
                if (!isEdgeofH.get(new Edge(u, v)))
                    continue; // Only consider edges in H
                int uH = dBase.find(u);
                if (matchHG[vH] == uH)
                    continue;
                if (labelHG[uH] == UNLABELED) { // grow step
                    int uHmatch = matchHG[uH];
                    if (uHmatch == -1) {
                        // Found an augmenting path to a free node in H
                        labelHG[uH] = INNER;
                        parentHG[uH] = vH;
                        return uH;
                    } else { // extend by 2 edges
                        labelHG[uH] = INNER;
                        labelHG[uHmatch] = OUTER;
                        parentHG[uH] = vH;
                        evenTime[uHmatch] = phase2Counter++;
                        int s = augPathDFS(uHmatch);
                        if (s != -1) {
                            return s;
                        }
                    }
                } else { // Blossom step
                    int bH = dBase.find(vH);
                    int zH = dBase.find(uH);

                    if (evenTime[bH] < evenTime[zH]) { // Blossom step along forward edge
                        // Using LinkedList allows O(1) additions to the front (push_front equivalent)
                        List<Integer> tmp = new LinkedList<>();
                        List<Integer> endpointsOfM = new ArrayList<>();

                        // Trace back from the target node to the base of the blossom
                        while (zH != bH) {
                            endpointsOfM.add(zH);
                            zH = matchHG[zH]; // Jump across the matching H-edge
                            endpointsOfM.add(zH);

                            tmp.addFirst(zH); // zH is INNER, add to the front of tmp

                            // Move up the alternating tree. Because Java's parentHG stores H-nodes,
                            // this directly replaces the C++ G-edge source/target resolution.
                            zH = dBase.find(parentHG[zH]);
                        }

                        // Contract the blossom in the H-graph
                        for (int z : endpointsOfM) {
                            dBase.union(z, bH);
                        }
                        dBase.makeRep(bH); // Ensure bH is the canonical representative

                        // Record the bridge edge and direction for path reconstruction
                        for (int z : tmp) {
                            bridgeHG[z] = new Edge(v, u);
                            dirHG[z] = v; // Java adaptation: directly store the 'v' side of the bridge
                        }

                        // Recursively search from the newly-promoted OUTER nodes
                        for (int z : tmp) { // The new OUTER node closest to bH comes first
                            int s = augPathDFS(z);
                            if (s != -1) {
                                return s;
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Corresponds exactly to find_path_in_HG in the paper.
     * Constructs the even-length alternating path connecting vH and uH in the
     * H-graph.
     * Returns a flat list of nodes representing the non-matching edges.
     */
    private ArrayList<Integer> findAugPathInH(int vH, int uH) {
        ArrayList<Integer> p = new ArrayList<>();

        // If the start and target are the same, the path is empty.
        if (vH == uH) {
            return p;
        }

        if (labelHG[vH] == OUTER) {
            int mvH = matchHG[vH];
            int parentNode = parentHG[mvH];

            // Reconstruct the tight edge between mvH and parentNode
            // since parentHG stores H-nodes rather than explicit edges.
            int eSource = -1, eTarget = -1;
            searchEdge: for (int u : contractedInto[mvH]) {
                for (int v : graph.getAllNeighbors(u)) {
                    if (dBase.find(v) == parentNode && Boolean.TRUE.equals(isEdgeofH.get(new Edge(u, v)))) {
                        eSource = u;
                        eTarget = v;
                        break searchEdge;
                    }
                }
            }

            p.add(eSource);
            p.add(eTarget);

            // Determine which endpoint maps to the parent H-node to continue the recursion
            int nextNode = (dBase.find(eSource) == mvH) ? eTarget : eSource;
            p.addAll(findAugPathInH(dBase.find(nextNode), uH));

            return p;
        } else {
            // vH is an INNER node (blossom step).
            Edge bridge = bridgeHG[vH];

            // From our augPathDFS adaptation, dirHG stores the G-node on the vH side.
            int vSide = dirHG[vH];
            int uSide = (bridge.vertex1() == vSide) ? bridge.vertex2() : bridge.vertex1();

            // Recursively construct the path through the bridge.
            p.addAll(findAugPathInH(dBase.find(vSide), dBase.find(matchHG[vH])));
            p.add(vSide);
            p.add(uSide);
            p.addAll(findAugPathInH(dBase.find(uSide), uH));

            return p;
        }
    }

    /**
     * Corresponds exactly to find_path_in_G in the paper.
     * Finds the even path from v to u in the original graph using blossom bridges.
     * Returns a flat list of nodes representing the non-matching edges.
     */
    private ArrayList<Integer> findAugPathInG(int v, int u) {
        ArrayList<Integer> p = new ArrayList<>();

        // Base case: if we have reached the target base, return the empty path.
        if (v == u) {
            return p;
        }

        if (labels[v] == OUTER) {
            // For OUTER nodes, append the mate and its parent, then recurse.
            p.add(matches[v]);
            p.add(parents[matches[v]]);

            p.addAll(findAugPathInG(parents[matches[v]], u));
            return p;
        } else {
            // v is INNER; route through the blossom bridge.
            p.addAll(findAugPathInG(sourceBridge[v], matches[v]));
            p.add(sourceBridge[v]);
            p.add(targetBridge[v]);

            p.addAll(findAugPathInG(targetBridge[v], u));
            return p;
        }
    }

    /**
     * Fills in the parts inside the dbase-blossoms and then augments the path.
     */
    private void augmentG(ArrayList<Integer> aphG) {
        // Augmenting path in G as a list of nodes, two nodes for each non-matching edge
        ArrayList<Integer> ap = new ArrayList<>();

        for (int i = 0; i < aphG.size(); i += 2) {
            int u = aphG.get(i);
            int v = aphG.get(i + 1);

            ap.add(u);
            ap.add(v);

            ap.addAll(findAugPathInG(u, dBase.find(u)));
            ap.addAll(findAugPathInG(v, dBase.find(v)));
        }

        // Mate each pair of nodes in ap
        for (int i = 0; i < ap.size(); i += 2) {
            int u = ap.get(i);
            int v = ap.get(i + 1);

            matches[u] = v;
            matches[v] = u;
        }
    }

    private boolean isEdgeTight(int u, int v) {
        int dualSum = computeDualY(u) + computeDualY(v);

        if (dualSum == 0) {
            return true;
        }

        if (dualSum == 1) {
            return labels[base.find(u)] == OUTER && labels[base.find(v)] == OUTER;
        }

        return false;
    }

    // -------------------------------------------------------------------
    // Auxiliary Data Structures
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

    public class NodePartition {
        private int[] parent; // parent[i] gives the parent of node i in the union-find structure
        private int[] rank;
        private int[] blossomBase; // blossomBase[i] gives the base of the blossom that node i belongs to

        // Optional: Maintain the explicit tree edges E(T) as defined in the ADT
        private List<int[]> treeEdges;

        /**
         * Initializes the data structure for a maximum of n vertices.
         * 
         * @param n The maximum number of vertices in the graph.
         */
        public NodePartition(int n) {
            parent = new int[n];
            rank = new int[n];
            blossomBase = new int[n];
            treeEdges = new ArrayList<>();

            // Initially, the tree is empty.
            // We initialize the arrays such that when a node is added, it represents
            // itself.
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                rank[i] = 0;
                blossomBase[i] = i;
            }
        }

        /**
         * AddEdge(u, v): Adds a new vertex u to the tree T connected to an existing
         * vertex v.
         * Initializes u as a new singleton set in the partition S.
         */
        public void addEdge(int u, int v) {
            treeEdges.add(new int[] { u, v });
            // In a static array allocation, the singleton set {u} is already conceptually
            // established by the constructor. If dynamically sizing, we would allocate
            // space here.
            parent[u] = u;
            blossomBase[u] = u;
        }

        /**
         * Internal helper to find the structural root of the DSU tree with path
         * compression.
         */
        private int getRoot(int u) {
            if (parent[u] == u) {
                return u;
            }
            // Path compression: flatten the tree to guarantee O(alpha(n)) amortized time
            parent[u] = getRoot(parent[u]);
            return parent[u];
        }

        /**
         * Find(u): Returns the canonical representative (the base of the blossom)
         * for the set containing u.
         */
        public int find(int u) {
            int root = getRoot(u);
            return blossomBase[root];
        }

        /**
         * union(u, v): Merges the sets containing u and v.
         * Precondition: {u, v} must be an edge in E(T).
         */
        public void union(int u, int v) {
            int rootU = getRoot(u);
            int rootV = getRoot(v);

            if (rootU != rootV) {
                // Standard Union by Rank to keep trees shallow
                if (rank[rootU] < rank[rootV]) {
                    parent[rootU] = rootV;
                } else if (rank[rootU] > rank[rootV]) {
                    parent[rootV] = rootU;
                } else {
                    parent[rootV] = rootU;
                    rank[rootU]++;
                }
            }
        }

        /**
         * makeRep(base): A critical requirement for Gabow's Algorithm.
         * After unioning a cycle into a blossom, this forces a specific node
         * (the lowest common ancestor) to act as the canonical base of the new set.
         */
        public void makeRep(int base) {
            int root = getRoot(base);
            blossomBase[root] = base;
        }

        public void split(List<Integer> vertices) {
            // Reset the partition to have each vertex in its own set
            for (int v : vertices) {
                parent[v] = v;
                rank[v] = 0;
                blossomBase[v] = v;
            }
            treeEdges.clear();
        }

    }
}
