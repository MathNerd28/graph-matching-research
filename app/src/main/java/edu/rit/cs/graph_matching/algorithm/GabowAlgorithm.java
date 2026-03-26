package edu.rit.cs.graph_matching.algorithm;

// import java.lang.reflect.Array;
import java.util.ArrayList;
// import java.util.Arrays;
import java.util.HashSet;
// import java.util.LinkedList;
import java.util.List;
// import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import java.util.Map;

import edu.rit.cs.graph_matching.IntHashSet;
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
    private final int[] matchG;

    // ----- Blossom Contraction Structures ----- //

    // /** Disjoint-set tracking the base of a blossom during Phase 1 */
    // private final NodePartition base;

    // /** Maximal positive blossoms in G */
    // private final NodePartition dBase;
    // /** Snapshot of dBase to preserve the H-graph structure during Phase 2 */
    // private final int[] rep;

    // /** Alternating BFS tree parent pointers */
    private final int[] parentG; // ancestor in phase 1 search
    private final int[] parentH; // For Phase 2 DFS on H-graph

    // /** Bridges linking the contracted blossoms (source and target) */
    // private final int[] sourceBridge;
    // private final int[] targetBridge;
    // a blossom consists of two paths x--z and
    // y--z plus the edge xy; z is the base of the blossom.
    // the nodes on the path from x to z store x as source_bridge and y as
    // target_bridge.

    // // private final Edge[] bridgeHG;
    // private final int[] dirHG;

    private final int[] path1;
    private final int[] path2;

    /** Node labels in the alternating forest (UNLABELED, OUTER, INNER) */
    private final int[] labelG;

    /** Array-based priority queue for O(1) tight-edge discovery */
    private final PriorityQueueArray queue;

    private int phase2Counter;

    private final NodePartition maxPositiveBlossoms; // Tracks the maximal positive blossoms in G
    private final IntHashSet phase1Tree; // Tracks nodes currently in the Phase 1 alternating tree

    int lcaSearchTime = 0; // Global counter to mark nodes during least common ancestor search

    /**
     * base dual and the time when the node is labelled; the node is represented by
     * its base if contracted
     */
    private int[] yBase;
    private int[] yDelta;

    // ----- H-Graph Structures for Phase 2 ----- //
    private final IntHashSet nodeH;
    private final int[] matchH; // matchH[h] = h' if h is matched to h' in H, else -1
    private final Map<Edge, Boolean> isEdgeofH; // isEdgeofH[e] = 1 iff the edge e in G corresponds to an edge in H
    private final int[] labelH; // Labels for H-graph DFS (UNLABELED, OUTER, INNER)
    private final NodePartition maxBlossomsH;

    /** The global dual adjustment counter (number of adjustments applied) */
    private int delta;
    private final int[] outerTime;

    private final Stack<Integer> dfStack;
    private final boolean[] visited;

    // ----- Priority Queue & Auxiliary Path Tracking ----- //

    /** Delayed union commitments for dual adjustments */
    // private final Queue<Integer> delayedUnions;

    /**
     * Initializes the algorithm over the given graph.
     *
     * @param graph   the input graph
     * @param matches the current matching array (modified in place during Phase 2)
     */
    public GabowAlgorithm(Graph graph, int[] matches) {
        this.graph = graph;
        this.n = graph.size();
        this.matchG = matches;

        this.maxPositiveBlossoms = new NodePartition(n);
        this.phase1Tree = new IntHashSet();

        this.parentG = new int[n];
        this.parentH = new int[n];
        this.labelG = new int[n];
        // this.sourceBridge = new int[n];
        // this.targetBridge = new int[n];

        this.phase2Counter = 0;

        this.yBase = new int[n];
        java.util.Arrays.fill(this.yBase, 1); // Initial y(u) = 1 for all vertices
        this.yDelta = new int[n];

        // this.base = new NodePartition(n);
        // this.dBase = new NodePartition(n);
        // this.rep = new int[n];

        // Max augmenting path length is n, meaning delta <= n/2
        this.queue = new PriorityQueueArray(n / 2 + 1);

        this.path1 = new int[n];
        this.path2 = new int[n];

        // Phase 2 H-graph arrays
        this.nodeH = new IntHashSet();
        this.matchH = new int[n];
        java.util.Arrays.fill(this.matchH, -1);
        this.labelH = new int[n];
        this.outerTime = new int[n];
        // this.bridgeHG = new Edge[n];
        // this.dirHG = new int[n];

        // Hash map for tight edge lookups
        this.isEdgeofH = new java.util.HashMap<>();

        this.dfStack = new Stack<>();
        this.visited = new boolean[n];
        this.maxBlossomsH = new NodePartition(n);

        // Array of Lists: Requires a loop to instantiate the inner lists
        // this.contractedInto = new ArrayList[n];
        // for (int i = 0; i < n; i++) {
        // this.contractedInto[i] = new ArrayList<>();
        // }
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
            int w = matchG[v];
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
        maxPositiveBlossoms.reset(n);
        boolean foundSap = false;
        phase1Tree.clear();
        nodeH.clear();
        java.util.Arrays.fill(matchH, -1);
        java.util.Arrays.fill(labelH, UNLABELED);
        java.util.Arrays.fill(outerTime, 0);
        java.util.Arrays.fill(visited, false); // Clear visited for DFS
        maxBlossomsH.reset(n);
        dfStack.clear();
        phase2Counter = 1;

        java.util.Arrays.fill(labelG, UNLABELED);

        for (int v = 0; v < n; v++) {
            if (matchG[v] == -1) {
                labelG[v] = OUTER;
                yBase[v] = 0; // Free vertices have y = 0 initially
                yDelta[v] = 0;
                phase1Tree.add(v);
                scanEdges(v);
            } else {
                yBase[v] = 1; // matched vertices start with y = 1 initially
            }
        }

        while (2 * delta <= n) {
            // System.out.println("[Phase 1] Processing at Delta: " + delta);
            Edge edge;
            while ((edge = queue.pollNextAtDelta(delta)) != null) {
                int u = edge.vertex1();
                int v = edge.vertex2();

                // Ensure 'u' is the OUTER node
                if (labelG[maxPositiveBlossoms.find(u)] != OUTER) {
                    int temp = u;
                    u = v;
                    v = temp;
                }

                int baseU = maxPositiveBlossoms.find(u);
                int baseV = maxPositiveBlossoms.find(v);

                // Ignore invalid or stale edges inside the same blossom
                if (labelG[baseU] != OUTER || v == matchG[u] || baseU == baseV || labelG[baseV] == INNER) {
                    continue;
                }

                if (labelG[baseV] == UNLABELED) {
                    // Tree Growth Step: Found a free node
                    int matchedNode = matchG[v];
                    yBase[v] = yBase[matchedNode] = 1;
                    // yDelta[v] = yDelta[matchedNode] = delta;
                    yDelta[matchedNode] = delta;

                    parentG[matchedNode] = v;
                    parentG[v] = u;

                    labelG[v] = INNER;
                    labelG[matchedNode] = OUTER;

                    phase1Tree.add(v);
                    phase1Tree.add(matchedNode);

                    scanEdges(matchedNode);
                } else if (labelG[baseV] == OUTER) {
                    // Collision between OUTER nodes
                    int ancestor = findLeastCommonAncestor(baseU, baseV);

                    if (ancestor != -1) {
                        if (debug)
                            System.out.printf("[Phase 1] Blossom detected between %d and %d with base %d\n", u, v,
                                    ancestor);
                        // Collision in the SAME tree -> Shrink Blossom
                        shrinkPath(ancestor, u);
                        shrinkPath(ancestor, v);
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

                // Construct H explicitly

                for (int v : phase1Tree) {
                    // contractedInto[dBase.find(v)].add(v);
                    // matchHG[v] = -1;
                    v = maxPositiveBlossoms.find(v);
                    nodeH.add(v);

                    for (int u : graph.getAllNeighbors(v)) {
                        isEdgeofH.put(new Edge(u, v), isEdgeTight(u, v)); // Initialize all edges as not in H
                        if (isEdgeTight(u, v) && matchG[u] == v) {
                            matchH[v] = u;
                            matchH[u] = v;
                        }
                    }
                }

                // Halt Edmonds' tree growth; auxiliary graph is ready for DFS
                // commitDelayedUnions(delayedUnions);
                return true;
            }

            // Dual Adjustment Step: Commit delayed blossom unions
            // commitDelayedUnions(delayedUnions);
            delta++;
        }

        return false;
    }

    private int computeDualY(int v) {
        int baseV = maxPositiveBlossoms.find(v);
        if (labelG[baseV] == UNLABELED)
            return 1;
        if (labelG[baseV] == OUTER)
            return yBase[v] - (delta - yDelta[v]); // OUTER vertices decrease
        return yBase[v] + (delta - yDelta[v]); // INNER vertices increase
    }

    private void scanEdges(int u) {
        for (int v : graph.getAllNeighbors(u)) {
            int baseV = maxPositiveBlossoms.find(v);
            if (matchG[v] == u || labelG[baseV] == INNER)
                continue;

            int slack = computeDualY(u) + computeDualY(v);
            if (labelG[baseV] == UNLABELED) {
                queue.add(new Edge(u, v), delta + slack);
            } else {
                queue.add(new Edge(u, v), delta + slack / 2);
            }
        }
    }

    private void shrinkPath(int blossomBase, int outerNode) {
        // System.out.println("[Phase 1] Shrinking blossom at base: " + blossomBase + "
        // from " + start + " to " + target);
        int v = maxPositiveBlossoms.find(outerNode);
        while (v != blossomBase) {
            // base.union(v, blossomBase);
            // delayedUnions.add(v);
            // delayedUnions.add(blossomBase);
            maxPositiveBlossoms.union(v, blossomBase, blossomBase);

            // v = matchG[v];
            // base.union(v, blossomBase);
            // delayedUnions.add(v);
            // delayedUnions.add(blossomBase);
            v = matchG[v];
            maxPositiveBlossoms.union(v, blossomBase, blossomBase);

            // base.makeRep(blossomBase);

            // sourceBridge[v] = start;
            // targetBridge[v] = target;

            // Adjust dual baselines for the newly assimilated nodes
            yBase[v] = yBase[v] + (delta - yDelta[v]);
            yDelta[v] = delta;

            // Re-scan from the newly OUTER nodes
            scanEdges(v);
            v = maxPositiveBlossoms.find(parentG[v]);
        }
        // delayedUnions.add(blossomBase);
        // delayedUnions.add(blossomBase);
    }

    private int findLeastCommonAncestor(int baseU, int baseV) {
        lcaSearchTime++; // Guarantees unique path markers per search

        path1[baseU] = lcaSearchTime;
        path2[baseV] = lcaSearchTime;

        while ((path1[baseV] != lcaSearchTime && path2[baseU] != lcaSearchTime)
                && (matchG[baseU] != -1 || matchG[baseV] != -1)) {

            if (matchG[baseU] != -1) {
                baseU = maxPositiveBlossoms.find(parentG[matchG[baseU]]);
                path1[baseU] = lcaSearchTime;
            }
            if (matchG[baseV] != -1) {
                baseV = maxPositiveBlossoms.find(parentG[matchG[baseV]]);
                path2[baseV] = lcaSearchTime;
            }
        }

        if (path1[baseV] == lcaSearchTime)
            return baseV;
        if (path2[baseU] == lcaSearchTime)
            return baseU;
        return -1;
    }

    // -------------------------------------------------------------------
    // Phase 2: DFS on the Contracted H-Graph
    // -------------------------------------------------------------------

    boolean augPathFound = false;

    private void executePhase2() {

        ArrayList<ArrayList<Integer>> augPaths = new ArrayList<>(n);
        for (int vH : nodeH) {
            if (matchH[vH] == -1 && labelH[vH] == UNLABELED) {
                augPathDFS(vH, augPaths);
            }
        }
        for (ArrayList<Integer> aphG : augPaths) {
            augmentG(aphG);
        }

    }

    private void augPathDFS(int vH, ArrayList<ArrayList<Integer>> augPaths) {
        if (augPathFound)
            return;
        dfStack.push(vH);
        visited[vH] = true;
        labelH[vH] = OUTER;
        outerTime[vH] = phase2Counter++;

        for (int uH : graph.getAllNeighbors(vH)) {
            if (!isEdgeofH.getOrDefault(new Edge(uH, vH), false))
                continue;
            parentH[uH] = vH;
            if (visited[uH] && labelH[maxBlossomsH.find(uH)] == OUTER
                    && outerTime[maxBlossomsH.find(uH)] < outerTime[maxBlossomsH.find(vH)]) {
                int zH = maxBlossomsH.find(vH);
                int currH = uH;
                while (zH != currH) {
                    if (labelH[currH] == INNER) {
                        labelH[currH] = OUTER;
                        augPathDFS(currH, augPaths);
                    }
                    maxBlossomsH.union(zH, maxBlossomsH.find(currH), zH);
                    currH = dfStack.pop();
                }
            } else if (!visited[uH]) {
                visited[uH] = true;
                if (matchH[uH] == -1) {
                    // TODO: is every free node in H necessarily a free node in G?
                    augPathFound = true;
                    ArrayList<Integer> augPath = new ArrayList<>();
                    dfStack.push(uH);
                    labelH[uH] = INNER;
                    augPath = findAugPathH();
                    augPaths.add(augPath);
                    return;
                } else {
                    dfStack.push(uH);
                    labelH[uH] = INNER;
                    parentH[matchH[uH]] = uH;
                    augPathDFS(matchH[uH], augPaths);
                }
            }
        }
    }

    ArrayList<Integer> findAugPathH() {
        ArrayList<Integer> augPath = new ArrayList<>();
        while (!dfStack.isEmpty()) {
            int curr = dfStack.pop();
            augPath.add(curr);

            if (maxBlossomsH.find(curr) == curr) {
                continue;
            } else if (labelH[curr] == OUTER) {
                int base = maxBlossomsH.find(curr);
                curr = matchH[curr];
                // Added curr != -1 safety check
                while (base != curr && curr != -1) {
                    augPath.add(curr);
                    curr = parentH[curr];
                }
            }
        }
        return augPath;
    }

    ArrayList<Integer> findAugPathG(ArrayList<Integer> apH) {
        ArrayList<Integer> augPathG = new ArrayList<>();
        while (!apH.isEmpty()) {
            int curr = apH.remove(apH.size() - 1);
            augPathG.add(curr);

            if (maxPositiveBlossoms.find(curr) == curr) {
                continue;
            } else if (labelG[curr] == OUTER) {
                int base = maxPositiveBlossoms.find(curr);
                curr = matchG[curr];
                // Added curr != -1 safety check
                while (base != curr && curr != -1) {
                    augPathG.add(curr);
                    curr = parentG[curr];
                }
            }
        }
        return augPathG;
    }

    /**
     * Fills in the parts inside the dbase-blossoms and then augments the path.
     */
    private void augmentG(ArrayList<Integer> aphG) {
        ArrayList<Integer> augPathG = findAugPathG(aphG);
        if (debug)
            System.out.println("[Phase 2] Corresponding augmenting path in G: " + augPathG);

        for (int i = 0; i < augPathG.size() - 1; i += 2) {
            int v = augPathG.get(i);
            int w = augPathG.get(i + 1);
            matchG[v] = w;
            matchG[w] = v;
        }
    }

    private boolean isEdgeTight(int u, int v) {
        // w(e) = 2 if e is in M, 0 otherwise
        int w = (matchG[u] == v) ? 2 : 0;
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
        private int[] blossomBase; // blossomBase[i] gives the base of the blossom that node i immediately belongs
                                   // to; for non-blossom nodes, this is just the node itself

        /**
         * Initializes the data structure for a maximum of n vertices.
         * 
         * @param n The maximum number of vertices in the graph.
         */
        public NodePartition(int n) {
            reset(n);
        }

        /**
         * Internal helper to find the structural root of the union find tree with path
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
        public void union(int u, int v, int newBase) {
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
            makeRep(newBase);
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

        public void reset(int n) {
            parent = new int[n];
            rank = new int[n];
            blossomBase = new int[n];
            // Initially, the tree is empty.
            // We initialize the arrays such that when a node is added, it represents
            // itself.
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                rank[i] = 0;
                blossomBase[i] = i;
            }
        }

    }
}
