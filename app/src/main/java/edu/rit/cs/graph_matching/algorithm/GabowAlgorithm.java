package edu.rit.cs.graph_matching.algorithm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.Map;

import edu.rit.cs.graph_matching.IntHashSet;
import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;

/**
 * Phase 1 and 2 of Gabow's O(m*sqrt(n)) Matching Algorithm.
 * c.f.https://arxiv.org/abs/1703.03998
 * the implementation is also based on this paper:fs
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

    // /** Edmond search tree parent pointers */
    private final int[] parentG; // ancestor in phase 1 search
    private final int[] parentH; // For Phase 2 DFS on H-graph

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
    private final Map<Integer, Set<Integer>> adjH;
    // Maps an H-graph edge (baseV, baseU) to the actual G-graph edge (v, u)
    private final Map<Integer, Map<Integer, Edge>> bridgeHG;
    // private final Map<Edge, Boolean> isEdgeofH; // isEdgeofH[e] = 1 iff the edge
    // e in G corresponds to an edge in H
    private final int[] labelH; // Labels for H-graph DFS (UNLABELED, OUTER, INNER)
    private final NodePartition maxBlossomsH;

    // Mehlhorn's Bridge Caching Arrays
    private final int[] sourceBridge;
    private final int[] targetBridge;
    // Phase 2 (H-Graph) Bridge Caching
    private final int[] sourceBridgeH;
    private final int[] targetBridgeH;

    /** The global dual adjustment counter (number of adjustments applied) */
    private int delta;
    private final int[] outerTime;
    // private final int[] outerTimeH;

    // private final Stack<Integer> dfStack;
    private final boolean[] visited;

    private boolean augPathFound;

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
        this.sourceBridge = new int[n];
        this.targetBridge = new int[n];
        this.sourceBridgeH = new int[n];
        this.targetBridgeH = new int[n];

        this.phase2Counter = 0;

        this.yBase = new int[n];
        java.util.Arrays.fill(this.yBase, 1); // Initial y(u) = 1 for all vertices
        this.yDelta = new int[n];

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
        this.adjH = new java.util.HashMap<>();
        this.bridgeHG = new java.util.HashMap<>();

        this.visited = new boolean[n];
        this.maxBlossomsH = new NodePartition(n);
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
        java.util.Arrays.fill(parentH, -1);
        java.util.Arrays.fill(outerTime, 0);
        java.util.Arrays.fill(visited, false); // Clear visited for DFS
        maxBlossomsH.reset(n);
        phase2Counter = 1;

        java.util.Arrays.fill(labelG, UNLABELED);
        java.util.Arrays.fill(parentG, -1);

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

        if (debug) {
            int freeCount = 0;
            StringBuilder freeNodes = new StringBuilder("[Phase 1] Free nodes: ");
            for (int i = 0; i < n; i++) {
                if (matchG[i] == -1) {
                    if (freeCount > 0)
                        freeNodes.append(", ");
                    freeNodes.append(i);
                    freeCount++;
                }
            }
            freeNodes.append(" (count: ").append(freeCount).append(")");
            System.out.println(freeNodes.toString());
        }

        while (2 * delta <= n) {
            if (debug) {
                System.out.println("[Phase 1] Processing at Delta: " + delta);
            }
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

                if (debug) {
                    System.out.println("Current out node: " + u + " Outer base: " + baseU + " neighbor: " + v
                            + " neighbor base: " + baseV);
                }

                if (labelG[baseV] == UNLABELED) {
                    // Tree Growth Step: Found a free node
                    int matchedNode = matchG[v];
                    if (debug) {
                        System.out.println("Tree growth: Adding " + v + " as INNER and " + matchedNode + " as OUTER");
                    }
                    // yBase[v] = yBase[matchedNode] = 1;
                    yDelta[v] = yDelta[matchedNode] = delta;
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
                        if (debug) {
                            System.out.println("Blossom step: " + u + " and " + v + " with base "
                                    + ancestor);
                        }
                        // Collision in the SAME tree -> Shrink Blossom
                        shrinkPath(ancestor, u, v);
                        shrinkPath(ancestor, v, u);
                    } else {
                        if (debug) {
                            System.out.println("Augmenting path found ending at " + v);
                        }
                        // Collision across DIFFERENT trees -> Augmenting Path Found
                        foundSap = true;
                        // break;
                    }
                }
            }

            if (foundSap) {
                adjH.clear(); // Clear from previous iterations
                bridgeHG.clear(); // Clear old bridges
                nodeH.clear(); // Ensure nodeH is completely rebuilt

                // Construct H explicitly
                for (int v : phase1Tree) {
                    int baseV = maxPositiveBlossoms.find(v);

                    // We must include all active tree nodes (both OUTER and unshrunk INNER)
                    nodeH.add(baseV);
                    adjH.putIfAbsent(baseV, new java.util.HashSet<>());

                    for (int u : graph.getAllNeighbors(v)) {
                        if (isEdgeTight(u, v)) {
                            int baseU = maxPositiveBlossoms.find(u);

                            // Only add edges between distinct blossom bases
                            if (baseU != baseV) {

                                // H must contain EXACTLY the alternating tree + the valid OUTER cross-edges.
                                boolean isMatched = (matchG[u] == v);
                                boolean isTreeEdge = (parentG[u] == v) || (parentG[v] == u);
                                boolean isOuterCrossEdge = (labelG[baseV] == OUTER && labelG[baseU] == OUTER);

                                if (isMatched || isTreeEdge || isOuterCrossEdge) {
                                    adjH.get(baseV).add(baseU);
                                    adjH.putIfAbsent(baseU, new java.util.HashSet<>());
                                    adjH.get(baseU).add(baseV);

                                    bridgeHG.putIfAbsent(baseV, new java.util.HashMap<>());
                                    bridgeHG.get(baseV).put(baseU, new Edge(v, u));

                                    if (isMatched) {
                                        matchH[baseV] = baseU;
                                        matchH[baseU] = baseV;
                                    }
                                }
                            }
                        }
                    }
                }
                return true;
            }

            delta++;
        }

        return false;
    }

    private int computeDualY(int v) {
        int baseV = maxPositiveBlossoms.find(v);
        if (labelG[baseV] == UNLABELED)
            return yBase[v];
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

    private void shrinkPath(int blossomBase, int outerNodeThisPath, int outerNodeOtherPath) {
        int v = maxPositiveBlossoms.find(outerNodeThisPath);
        while (v != blossomBase) {
            // Union the current OUTER node
            maxPositiveBlossoms.union(v, blossomBase, blossomBase);

            // Union the matched node, which is INNER
            v = matchG[v];
            maxPositiveBlossoms.union(v, blossomBase, blossomBase);

            // Set the bridge for the OUTER node
            sourceBridge[v] = outerNodeThisPath;
            targetBridge[v] = outerNodeOtherPath;

            // Adjust dual baselines for the newly OUTER nodes
            yBase[v] = yBase[v] + (delta - yDelta[v]);
            yDelta[v] = delta;

            // Scan edges from the newly OUTER nodes
            scanEdges(v);
            v = maxPositiveBlossoms.find(parentG[v]);
        }
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

    private void executePhase2() {
        ArrayList<ArrayList<Integer>> augPaths = new ArrayList<>(n);
        for (int vH : nodeH) {
            if (matchH[vH] == -1 && labelH[vH] == UNLABELED) {
                augPathFound = false;

                visited[vH] = true;
                labelH[vH] = OUTER;
                outerTime[vH] = phase2Counter++;

                // FIX: Pass vH as the rootH
                augPathDFS(vH, vH, augPaths);

                if (debug && augPathFound) {
                    System.out.println("Finished DFS from node " + vH + ", found augmenting path in contracted H: "
                            + augPaths.get(augPaths.size() - 1));
                }
            }
        }
        for (ArrayList<Integer> aphG : augPaths) {
            augmentG(aphG);
        }
    }

    private void augPathDFS(int vH, int rootH, ArrayList<ArrayList<Integer>> augPaths) {
        if (augPathFound)
            return;

        if (debug)
            System.out.println("\n[Phase 2] Starting DFS from outer node: " + vH);

        for (int uH : adjH.getOrDefault(vH, new java.util.HashSet<>())) {
            if (augPathFound)
                return;
            if (debug)
                System.out.println("DFS visiting edge: (" + vH + ", " + uH + ")");

            int baseV = maxBlossomsH.find(vH);
            int baseU = maxBlossomsH.find(uH);

            if (labelH[baseU] == UNLABELED) {
                labelH[uH] = INNER;
                parentH[uH] = vH;

                if (matchH[uH] == -1) {
                    if (debug)
                        System.out.println("DFS found augmenting path to free node: " + uH);
                    augPathFound = true;

                    ArrayList<Integer> augPath = findAugPathH(vH, uH, rootH);
                    augPaths.add(augPath);

                    return;
                } else {
                    int nextOuter = matchH[uH];
                    if (debug)
                        System.out.println("DFS tree growth: Adding " + uH + " and " + nextOuter + " to search stack");

                    labelH[nextOuter] = OUTER;
                    outerTime[nextOuter] = phase2Counter++;

                    augPathDFS(nextOuter, rootH, augPaths);
                    if (augPathFound)
                        return;
                }
            } else if (labelH[baseU] == OUTER && outerTime[baseV] < outerTime[baseU]) {
                int curr = baseU;

                while (curr != baseV) {
                    maxBlossomsH.union(curr, baseV, baseV);

                    int matchedNode = matchH[curr];
                    maxBlossomsH.union(matchedNode, baseV, baseV);

                    sourceBridgeH[matchedNode] = uH;
                    targetBridgeH[matchedNode] = vH;

                    if (labelH[matchedNode] == INNER) {
                        // Pass rootH recursively
                        augPathDFS(matchedNode, rootH, augPaths);
                        if (augPathFound)
                            return;
                    }

                    curr = maxBlossomsH.find(parentH[matchedNode]);
                }
            }
        }
    }

    /**
     * Constructs the augmenting path in the H-graph by tracing parent pointers
     * and expanding any crossed H-blossoms using the cached bridges.
     */

    ArrayList<Integer> findAugPathH(int start, int end, int rootH) {
        ArrayList<Integer> augPath = new ArrayList<>();

        // 1. Add the final free node to the end of our backwards path
        augPath.add(end);

        // 2. Unroll the H-path backwards from the OUTER node (start) all the way up to
        // the root
        ArrayList<Integer> temp = new ArrayList<>();
        unrollBlossomH(temp, start, rootH); // FIX: Safely unroll up to rootH

        // 3. Append the unrolled sequence
        for (int x : temp) {
            augPath.add(x);
        }

        // 4. Reverse it so it correctly goes from root -> free node.
        java.util.Collections.reverse(augPath);

        return augPath;
    }

    ArrayList<Integer> findAugPathG(ArrayList<Integer> apH) {
        ArrayList<Integer> augPathG = new ArrayList<>();

        // Mehlhorn's Pairwise Logic:
        // Process the H-nodes in chunks of 2.
        // Every pair (b0, b1) is connected by an UNMATCHED bridge.
        for (int i = 0; i < apH.size(); i += 2) {
            int b0 = apH.get(i);
            int b1 = apH.get(i + 1);

            Edge bridge = bridgeHG.get(b0).get(b1);

            // 1. Identify which physical endpoint belongs to which blossom
            int u = bridge.vertex1();
            int v = bridge.vertex2();
            if (maxPositiveBlossoms.find(u) != b0) {
                u = bridge.vertex2();
                v = bridge.vertex1();
            }

            // 2. Unroll the Left Blossom (b0): Base -> Bridge
            // unrollBlossomG gives Bridge -> Base, so we reverse it.
            ArrayList<Integer> leftPath = new ArrayList<>();
            unrollBlossomG(leftPath, u, b0);
            java.util.Collections.reverse(leftPath);
            augPathG.addAll(leftPath);

            // 3. Unroll the Right Blossom (b1): Bridge -> Base
            // unrollBlossomG natively traces Bridge -> Base, so we just append it.
            ArrayList<Integer> rightPath = new ArrayList<>();
            unrollBlossomG(rightPath, v, b1);
            augPathG.addAll(rightPath);

        }

        return augPathG;
    }

    /**
     * Recursively tracks the internal path of a blossom in G, starting from entry
     * and ending at exit, and adds the internal nodes, including the entry and
     * exit, to augPath.
     * * @param augPath
     * 
     * @param entry
     * @param exit
     */
    void unrollBlossomG(ArrayList<Integer> augPath, int entry, int exit) {
        if (entry == exit) {
            augPath.addLast(entry);
            return;
        }

        if (labelG[entry] == OUTER) {
            int matchedNode = matchG[entry];
            int parentNode = parentG[matchedNode];
            augPath.addLast(entry);
            augPath.addLast(matchedNode);
            unrollBlossomG(augPath, parentNode, exit);
        } else {
            int src = sourceBridge[entry];
            int tgt = targetBridge[entry];
            int matchedNode = matchG[entry];

            // 1. Add the initial matched edge for this INNER node
            augPath.addLast(entry);

            // 2. Trace upwards from the source of the bridge to the matched node.
            // Since we need to walk downwards from the matched node to the bridge,
            // we **reverse** the collected path.
            ArrayList<Integer> temp = new ArrayList<>();
            unrollBlossomG(temp, src, matchedNode);
            for (int i = temp.size() - 1; i >= 0; i--) {
                augPath.addLast(temp.get(i));
            }

            // 3. Cross the bridge and continue unrolling from the target side up to the
            // exit
            unrollBlossomG(augPath, tgt, exit);
        }
    }

    /**
     * Recursively tracks the internal path of a blossom in the contracted H-graph.
     */
    void unrollBlossomH(ArrayList<Integer> pathH, int entry, int exit) {
        if (entry == exit) {
            pathH.addLast(entry);
            return;
        }

        if (labelH[entry] == OUTER) {
            int matchedNode = matchH[entry];
            int parentNode = parentH[matchedNode]; // This MUST point directly to the next OUTER node
            pathH.addLast(entry);
            pathH.addLast(matchedNode);
            unrollBlossomH(pathH, parentNode, exit);
        } else {
            // entry is an INNER node trapped in an H-blossom. Jump using the cached bridge!
            int src = sourceBridgeH[entry];
            int tgt = targetBridgeH[entry];
            int matchedNode = matchH[entry];

            pathH.addLast(entry);

            ArrayList<Integer> temp = new ArrayList<>();
            unrollBlossomH(temp, src, matchedNode);
            for (int i = temp.size() - 1; i >= 0; i--) {
                pathH.addLast(temp.get(i));
            }

            unrollBlossomH(pathH, tgt, exit);
        }
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
