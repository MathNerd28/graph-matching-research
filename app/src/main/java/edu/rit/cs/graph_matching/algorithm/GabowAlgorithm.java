package edu.rit.cs.graph_matching.algorithm;

// import java.lang.reflect.Array;
import java.util.ArrayList;
// import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
// import java.util.Queue;
import java.util.Set;
import java.util.Map;

import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
// import edu.rit.cs.graph_matching.util.IntHashSet;

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

    public boolean debug = false; // Add this near your other class fields

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
    /** Snapshot of dBase to preserve the H-graph structure during Phase 2 */
    private final int[] rep;

    /** Alternating BFS tree parent pointers */
    private final int[] parents;
    private final Edge[] parentHG; // For Phase 2 DFS on H-graph

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

    private final ArrayList<Integer> phase1Tree;

    int lcaSearchTime = 0; // Global counter to mark nodes during least common ancestor search

    /**
     * base dual and the time when the node is labelled; the node is represented by
     * its base if contracted
     */
    private int[] yBase;
    private int[] yDelta;

    // ----- H-Graph Structures for Phase 2 ----- //
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

        this.phase1Tree = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            this.phase1Tree.add(i);
        }

        this.parents = new int[n];
        this.parentHG = new Edge[n];
        this.labels = new int[n];
        this.sourceBridge = new int[n];
        this.targetBridge = new int[n];

        this.phase2Counter = 0;

        this.yBase = new int[n];
        java.util.Arrays.fill(this.yBase, 1); // Initial y(u) = 1 for all vertices
        this.yDelta = new int[n];

        this.base = new NodePartition(n);
        this.dBase = new NodePartition(n);
        this.rep = new int[n];

        // Max augmenting path length is n, meaning delta <= n/2
        this.queue = new PriorityQueueArray(n / 2 + 1);

        this.path1 = new int[n];
        this.path2 = new int[n];

        // Phase 2 H-graph arrays
        this.matchHG = new int[n];
        java.util.Arrays.fill(this.matchHG, -1);
        this.labelHG = new int[n];
        this.evenTime = new int[n];
        this.bridgeHG = new Edge[n];
        this.dirHG = new int[n];

        // Hash map for tight edge lookups
        this.isEdgeofH = new java.util.HashMap<>();

        // Array of Lists: Requires a loop to instantiate the inner lists
        this.contractedInto = new ArrayList[n];
        for (int i = 0; i < n; i++) {
            this.contractedInto[i] = new ArrayList<>();
        }
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
        if (debug)
            System.out.println("[Phase 1] Processing at Delta: " + delta);
        queue.clear();
        delta = 0;
        boolean foundSap = false;
        List<Integer> delayedUnions = new ArrayList<>();

        base.split(phase1Tree);
        dBase.split(phase1Tree);

        // Use an Iterator to safely remove matched nodes from the tracking list
        java.util.Iterator<Integer> it = phase1Tree.iterator();
        while (it.hasNext()) {
            int v = it.next();
            labels[v] = ((matches[v] == -1) ? OUTER : UNLABELED);
            if (matches[v] == -1) {
                scanEdges(v);
            } else {
                it.remove(); // Safely deletes the item from phase1Tree
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

                    phase1Tree.add(v);
                    phase1Tree.add(matchedNode);

                    scanEdges(matchedNode);
                } else if (labels[baseV] == OUTER) {
                    // Collision between OUTER nodes
                    int ancestor = findLeastCommonAncestor(baseU, baseV);

                    if (ancestor != -1) {
                        if (debug)
                            System.out.printf("[Phase 1] Blossom detected between %d and %d with base %d\n", u, v,
                                    ancestor);
                        // Collision in the SAME tree -> Shrink Blossom
                        shrinkPath(ancestor, u, v, delayedUnions);
                        shrinkPath(ancestor, v, u, delayedUnions);
                    } else {
                        if (debug)
                            System.out.printf("[Phase 1] SAP collision found between %d and %d\n", u, v);
                        // Collision across DIFFERENT trees -> Augmenting Path Found
                        foundSap = true;
                        // break;
                    }
                }
            }

            if (foundSap) {

                // Group G-nodes into their contracted H-nodes (dbase)
                for (int v : phase1Tree) {
                    contractedInto[dBase.find(v)].add(v);
                    matchHG[v] = -1;
                    for (int u : graph.getAllNeighbors(v)) {
                        isEdgeofH.put(new Edge(u, v), false); // Initialize all edges as not in H

                    }
                }

                // Evaluate edges to establish tight connections and H-graph matchings
                for (int u : phase1Tree) {
                    int uH = dBase.find(u);

                    for (int v : graph.getAllNeighbors(u)) {
                        int vH = dBase.find(v);

                        // Check if the edge crosses H-node boundaries and is tight ( d(u) + d(v) ==
                        // w[e] )
                        if (uH != vH && isEdgeTight(u, v) && labels[base.find(v)] != UNLABELED) {
                            isEdgeofH.put(new Edge(u, v), true);
                            // In the paper, w[e] == 2 implies 'e' is a matching edge.
                            if (matches[u] == v) {
                                matchHG[uH] = vH;
                                matchHG[vH] = uH;
                            }

                        }
                    }
                }

                // Halt Edmonds' tree growth; auxiliary graph is ready for DFS
                // commitDelayedUnions(delayedUnions);
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
        java.util.Arrays.fill(labelHG, UNLABELED);
        java.util.Arrays.fill(evenTime, 0); // FIX: Clear stale times
        phase2Counter = 1; // FIX: Start > 0

        // Take a snapshot of the original Phase 1 H-nodes
        for (int v : phase1Tree) {
            rep[v] = dBase.find(v);
        }

        ArrayList<ArrayList<Integer>> augPaths = new ArrayList<>(n);

        for (int vH : phase1Tree) {
            if (vH != rep[vH])
                continue;

            if (matchHG[vH] == -1 && labelHG[vH] == UNLABELED) {
                labelHG[vH] = OUTER;
                evenTime[vH] = phase2Counter++;
                int freeNode = augPathDFS(vH);

                if (freeNode != -1) {
                    ArrayList<Integer> augPath = new ArrayList<>();

                    Edge e = parentHG[freeNode];
                    int eSource = e.vertex1();
                    int eTarget = e.vertex2();

                    if (rep[eTarget] == freeNode) {
                        int temp = eSource;
                        eSource = eTarget;
                        eTarget = temp;
                    }

                    augPath.add(eSource);
                    augPath.add(eTarget);

                    int nextHNode = (rep[eSource] == freeNode) ? eTarget : eSource;
                    augPath.addAll(findAugPathInH(rep[nextHNode], vH));

                    if (debug)
                        System.out.println("[Phase 2] Found augmenting path in H-graph: " + augPath);
                    augPaths.add(augPath);
                }
            }
        }

        for (ArrayList<Integer> aphG : augPaths) {
            augmentG(aphG);
        }

        for (int v : phase1Tree) {
            contractedInto[v].clear();
        }
    }

    private int augPathDFS(int vH) {
        for (int v : contractedInto[vH]) {
            for (int u : graph.getAllNeighbors(v)) {
                if (!isEdgeofH.getOrDefault(new Edge(u, v), false))
                    continue;

                int uH = rep[u];
                if (matchHG[vH] == uH)
                    continue;

                if (labelHG[uH] == UNLABELED) {
                    int uHmatch = matchHG[uH];
                    if (uHmatch == -1) {
                        labelHG[uH] = INNER;
                        parentHG[uH] = new Edge(v, u);
                        return uH;
                    } else {
                        labelHG[uH] = INNER;
                        labelHG[uHmatch] = OUTER;
                        parentHG[uH] = new Edge(v, u);
                        evenTime[uHmatch] = phase2Counter++;
                        int s = augPathDFS(uHmatch);
                        if (s != -1)
                            return s;
                    }
                } else { // Blossom step
                    int bH = dBase.find(vH);
                    int zH = dBase.find(uH);

                    // Ensure they aren't already in the same Phase 2 blossom
                    if (bH != zH && evenTime[bH] < evenTime[zH]) {
                        List<Integer> tmp = new LinkedList<>();
                        List<Integer> endpointsOfM = new ArrayList<>();

                        int curr = zH;
                        while (curr != bH) {
                            endpointsOfM.add(curr);
                            int mate_curr = matchHG[curr];
                            endpointsOfM.add(curr);

                            tmp.add(0, curr);

                            Edge pEdge = parentHG[mate_curr];
                            int pNode = (rep[pEdge.vertex1()] == mate_curr) ? pEdge.vertex2() : pEdge.vertex1();
                            curr = dBase.find(pNode);
                        }

                        for (int z : endpointsOfM) {
                            dBase.union(z, bH);
                        }
                        dBase.makeRep(bH);

                        for (int z : tmp) {
                            bridgeHG[z] = new Edge(v, u);
                            dirHG[z] = v; // Store the descendant G-node side
                        }

                        for (int z : tmp) {
                            int s = augPathDFS(z);
                            if (s != -1)
                                return s;
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

        if (vH == uH)
            return p;

        if (labelHG[vH] == OUTER) {
            int mvH = matchHG[vH];
            Edge e = parentHG[mvH];

            int eSource = e.vertex1();
            int eTarget = e.vertex2();

            if (rep[eTarget] == mvH) {
                int temp = eSource;
                eSource = eTarget;
                eTarget = temp;
            }

            p.add(eSource);
            p.add(eTarget);

            int nextNode = (rep[eSource] == mvH) ? eTarget : eSource;
            p.addAll(findAugPathInH(rep[nextNode], uH));

            return p;
        } else {
            Edge bridge = bridgeHG[vH];

            // FIX: dirHG actually stores the ANCESTOR side of the bridge
            int ancestorSide = dirHG[vH];
            int descendantSide = (bridge.vertex1() == ancestorSide) ? bridge.vertex2() : bridge.vertex1();

            // Trace UP from the descendant side to the mate
            p.addAll(findAugPathInH(rep[descendantSide], matchHG[vH]));

            p.add(descendantSide);
            p.add(ancestorSide);

            // Trace UP from the ancestor side to the root
            p.addAll(findAugPathInH(rep[ancestorSide], uH));

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
        ArrayList<Integer> ap = new ArrayList<>();

        for (int i = 0; i < aphG.size(); i += 2) {
            int u = aphG.get(i);
            int v = aphG.get(i + 1);

            ap.add(u);
            ap.add(v);

            ap.addAll(findAugPathInG(u, rep[u])); // FIX: Expand using the Phase 1 boundaries
            ap.addAll(findAugPathInG(v, rep[v]));
        }

        if (debug)
            System.out.println("[Phase 2] Lifted path to G-graph: " + ap);
        // Mate each pair of nodes in ap
        for (int i = 0; i < ap.size(); i += 2) {
            int u = ap.get(i);
            int v = ap.get(i + 1);

            matches[u] = v;
            matches[v] = u;
        }
    }

    private boolean isEdgeTight(int u, int v) {
        // w(e) = 2 if e is in M, 0 otherwise
        int w = (matches[u] == v) ? 2 : 0;
        return computeDualY(u) + computeDualY(v) == w;
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
