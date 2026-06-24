package edu.rit.cs.graph_matching.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import edu.rit.cs.graph_matching.util.IntHashSet;
import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;

/**
 * Phase 1 and 2 of Gabow's O(m*sqrt(n)) Matching Algorithm.
 * c.f.https://arxiv.org/abs/1703.03998
 * the implementation also refers to this paper:
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
public class GabowAlgorithm implements MatchingAlgorithm {

    private enum Label {
        UNLABELED, OUTER, INNER
    }

    private final Graph graph;
    private final int n;

    /**
     * Algorithm-specific operation counters, exposed via {@link #getStatistics()}.
     */
    private final GabowStatistics stats;

    /** Current matching status; matchG[v] = w, or -1 if free */
    private final int[] matchG;

    // ----- Blossom Contraction Structures ----- //
    private final int[] parentG; // ancestor in phase 1 search
    private final int[] parentH; // For Phase 2 DFS on H-graph

    private final int[] path1;
    private final int[] path2;

    /** Node labels in the alternating forest (UNLABELED, OUTER, INNER) */
    private final Label[] labelG;

    /** Array-based priority queue for O(1) tight-edge discovery */
    private final PriorityQueueArray queue;

    private int phase2Counter;

    private final NodePartition maxPositiveBlossoms; // Tracks the maximal positive blossoms in G
    private final IntHashSet phase1Tree; // Tracks nodes currently in the Phase 1 alternating tree

    private int lcaSearchTime = 0; // Global counter to mark nodes during least common ancestor search

    /**
     * base dual and the time when the node is labelled; the node is represented by
     * its base if contracted
     */
    private final int[] yBase;
    private final int[] yDelta;

    // ----- H-Graph Structures for Phase 2 ----- //
    private final IntHashSet nodeH;
    private final int[] matchH; // matchH[h] = h' if h is matched to h' in H, else -1
    private final Map<Integer, Set<Integer>> adjH;
    // Maps an H-graph edge (baseV, baseU) to the actual G-graph edge (v, u)
    private final Map<Integer, Map<Integer, Edge>> bridgeHG;
    private final Label[] labelH; // Labels for H-graph DFS (UNLABELED, OUTER, INNER)
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
    private final boolean[] visited;

    // ----- State Machine Variables for MatchingAlgorithm Interface ----- //
    private Iterator<Integer> nodeHItr;
    private boolean isFinished = false;
    private boolean augPathFound;

    // ----- Structural wrappers for unified blossom unrolling ----- //
    private final BlossomStructure gStruct;
    private final BlossomStructure hStruct;

    /**
     * Initializes the algorithm over the given graph.
     *
     * @param graph   the input graph
     * @param matches the current matching array (modified in place during
     *                execution)
     */
    public GabowAlgorithm(Graph graph, int[] matches) {
        this.graph = graph;
        this.n = graph.size();
        this.matchG = matches;
        this.stats = new GabowStatistics();

        this.maxPositiveBlossoms = new NodePartition(n, stats);
        this.phase1Tree = new IntHashSet();

        this.parentG = new int[n];
        this.parentH = new int[n];
        this.labelG = new Label[n];
        this.sourceBridge = new int[n];
        this.targetBridge = new int[n];
        this.sourceBridgeH = new int[n];
        this.targetBridgeH = new int[n];

        this.phase2Counter = 0;

        this.yBase = new int[n];
        Arrays.fill(this.yBase, 1); // Initial y(u) = 1 for all vertices
        this.yDelta = new int[n];

        // Max augmenting path length is n, meaning delta <= n/2
        this.queue = new PriorityQueueArray(n / 2 + 1, stats);

        this.path1 = new int[n];
        this.path2 = new int[n];

        // Phase 2 H-graph arrays
        this.nodeH = new IntHashSet();
        this.matchH = new int[n];
        Arrays.fill(this.matchH, -1);
        this.labelH = new Label[n];
        this.outerTime = new int[n];
        this.adjH = new HashMap<>();
        this.bridgeHG = new HashMap<>();

        this.visited = new boolean[n];
        this.maxBlossomsH = new NodePartition(n, stats);

        // Initialize reusable structural wrappers
        this.gStruct = new BlossomStructure(labelG, matchG, parentG, sourceBridge, targetBridge);
        this.hStruct = new BlossomStructure(labelH, matchH, parentH, sourceBridgeH, targetBridgeH);
    }

    /**
     * @return the algorithm-specific operation counters for this run
     */
    public GabowStatistics getStatistics() {
        return stats;
    }

    // -------------------------------------------------------------------
    // MatchingAlgorithm Interface Methods
    // -------------------------------------------------------------------

    @Override
    public int augment() {
        // Cooperatively check for thread interruption
        if (Thread.interrupted() || isFinished) {
            return -1;
        }

        // Initial setup or when a new Phase 1 search is needed
        if (nodeHItr == null) {
            if (!executePhase1()) {
                isFinished = true;
                return -1;
            }
            nodeHItr = nodeH.iterator();
        }

        while (true) {
            // Resume Phase 2 DFS from where we left off
            while (nodeHItr.hasNext()) {
                int vH = nodeHItr.next();
                if (matchH[vH] == -1 && labelH[vH] == Label.UNLABELED) {
                    augPathFound = false;
                    visited[vH] = true;
                    labelH[vH] = Label.OUTER;
                    outerTime[vH] = phase2Counter;
                    phase2Counter++;

                    ArrayList<Integer> apH = new ArrayList<>();
                    augPathDFS(vH, vH, apH);

                    // If a path is found, augment immediately and pause Phase 2
                    if (augPathFound) {
                        ArrayList<Integer> augPathG = findAugPathG(apH);

                        // Augment in G; each matched-edge update examines one edge
                        for (int i = 0; i < augPathG.size() - 1; i += 2) {
                            int v = augPathG.get(i);
                            int w = augPathG.get(i + 1);
                            matchG[v] = w;
                            matchG[w] = v;
                            stats.examineEdge();
                        }

                        return augPathG.size() - 1; // Return length for data collection
                    }
                }
            }

            // Phase 2 exhausted for this Phase 1 graph. Run Phase 1 again.
            if (!executePhase1()) {
                isFinished = true;
                return -1;
            }
            nodeHItr = nodeH.iterator();
        }
    }

    @Override
    public Set<Edge> getCurrentMatching() {
        Set<Edge> matchingEdges = new HashSet<>();
        for (int v = 0; v < n; v++) {
            int w = matchG[v];
            if (w != -1 && v < w) {
                matchingEdges.add(new Edge(v, w));
            }
        }
        return matchingEdges;
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    // -------------------------------------------------------------------
    // Phase 1: Dual-Driven Edmonds' Search for Shortest Augmenting Paths
    // -------------------------------------------------------------------

    private boolean executePhase1() {
        queue.clear();
        delta = 0;
        maxPositiveBlossoms.reset(n);
        boolean foundSap = false;
        phase1Tree.clear();
        nodeH.clear();
        Arrays.fill(matchH, -1);
        Arrays.fill(labelH, Label.UNLABELED);
        Arrays.fill(parentH, -1);
        Arrays.fill(outerTime, 0);
        Arrays.fill(visited, false); // Clear visited for DFS
        maxBlossomsH.reset(n);
        phase2Counter = 1;

        Arrays.fill(labelG, Label.UNLABELED);
        Arrays.fill(parentG, -1);

        for (int v = 0; v < n; v++) {
            yBase[v] = 1; // Free vertices start with y(u) = 1, matched vertices start with y(u) = 0
            yDelta[v] = 0;
            if (matchG[v] == -1) {
                labelG[v] = Label.OUTER;
                phase1Tree.add(v);
            }
        }
        for (int v = 0; v < n; v++) {
            if (matchG[v] == -1) {
                scanEdges(v); // incident edges added to the queue at their predicted tight level
            }
        }

        while (2 * delta <= n) {
            if (Thread.interrupted())
                return false;
            Edge edge;
            while ((edge = queue.pollNextAtDelta(delta)) != null) {
                // One dual-phase edge examined after being dequeued (the pop itself
                // is counted by the priority queue).
                stats.examineEdge();

                int u = edge.vertex1();
                int v = edge.vertex2();

                // Ensure 'u' is the OUTER node
                if (labelG[maxPositiveBlossoms.find(u)] != Label.OUTER) {
                    int temp = u;
                    u = v;
                    v = temp;
                }

                int baseU = maxPositiveBlossoms.find(u);
                int baseV = maxPositiveBlossoms.find(v);

                // Ignore invalid or stale edges inside the same blossom
                if (labelG[baseU] != Label.OUTER || v == matchG[u] || baseU == baseV || labelG[baseV] == Label.INNER) {
                    continue;
                }

                // The priority queue holds lazy predictions: an edge is enqueued at
                // the level it is predicted to become tight, but a later label change
                // (an endpoint becoming OUTER) moves that level. With dual feasibility
                // maintained (reduced weights stay non-negative), a popped entry that
                // is not yet tight has positive slack; re-enqueue it at its corrected,
                // strictly later tight level. Processing a non-tight OUTER–OUTER edge
                // would otherwise look like a cross-tree collision and produce a
                // spurious augmenting path, stalling the search.
                if (!isEdgeTight(u, v)) {
                    int slack = computeDualY(u) + computeDualY(v);
                    queue.add(new Edge(u, v),
                            labelG[baseV] == Label.UNLABELED ? delta + slack : delta + slack / 2);
                    continue;
                }

                if (labelG[baseV] == Label.UNLABELED) {
                    // Tree Growth Step: Found a free node
                    int matchedNode = matchG[v];
                    yDelta[v] = delta;
                    yDelta[matchedNode] = delta;

                    parentG[matchedNode] = v;
                    parentG[v] = u;

                    labelG[v] = Label.INNER;
                    labelG[matchedNode] = Label.OUTER;

                    phase1Tree.add(v);
                    phase1Tree.add(matchedNode);

                    scanEdges(matchedNode);
                } else if (labelG[baseV] == Label.OUTER) {
                    // Collision between OUTER nodes
                    int ancestor = findLeastCommonAncestor(baseU, baseV);

                    if (ancestor != -1) {
                        // Collision in the SAME tree -> Shrink Blossom
                        shrinkPath(ancestor, u, v);
                        shrinkPath(ancestor, v, u);
                    } else {
                        // Collision across DIFFERENT trees -> Augmenting Path Found
                        foundSap = true;
                    }
                }
            }

            if (foundSap) {
                buildHGraph();
                return true;
            }

            delta++;
        }

        return false;
    }

    /**
     * Explicitly construct the H-graph for Phase 2.
     * <p>
     * Following the correct definition of H (Ansaripour, Danaei &amp; Mehlhorn,
     * arXiv:2409.14849, §2.3.4): contract the maximal positive blossoms and keep
     * <em>all</em> tight edges connecting distinct contracted nodes. This must
     * include matching edges between two unlabeled nodes: an unlabeled vertex is
     * matched, and the augmenting path can enter such a matched pair through a
     * tight non-matching edge from an even node, traverse the matching edge, and
     * leave through another tight non-matching edge. Gabow's original rule (keep
     * only tight edges with an outer endpoint) drops those matching edges, so H
     * lacks some augmenting paths and Phase 1 stops short of the maximum. Because
     * those matched pairs are not on the alternating tree, a scan restricted to
     * {@link #phase1Tree} never visits them; we therefore scan all vertices.
     */
    private void buildHGraph() {
        adjH.clear(); // Clear from previous iterations
        bridgeHG.clear(); // Clear old bridges
        nodeH.clear(); // Ensure nodeH is completely rebuilt

        // Seed H with every search-structure node so free (unmatched) vertices
        // are present as Phase-2 search roots even if they have no tight edge.
        for (int v : phase1Tree) {
            int baseV = maxPositiveBlossoms.find(v);
            nodeH.add(baseV);
            adjH.putIfAbsent(baseV, new HashSet<>());
        }

        // Keep every tight edge between distinct contracted nodes.
        for (int u = 0; u < n; u++) {
            int baseU = maxPositiveBlossoms.find(u);
            for (int v : graph.getAllNeighbors(u)) {
                stats.examineEdge();

                if (!isEdgeTight(u, v)) {
                    continue;
                }

                int baseV = maxPositiveBlossoms.find(v);

                // Skip self-loops within a contracted blossom.
                if (baseU == baseV) {
                    continue;
                }

                nodeH.add(baseU);
                nodeH.add(baseV);
                adjH.computeIfAbsent(baseU, k -> new HashSet<>())
                        .add(baseV);
                adjH.computeIfAbsent(baseV, k -> new HashSet<>())
                        .add(baseU);

                // Record a representative G-edge for this H-edge (both directions;
                // findAugPathG fixes orientation from the contracted endpoints).
                bridgeHG.computeIfAbsent(baseU, k -> new HashMap<>())
                        .put(baseV, new Edge(u, v));

                if (matchG[u] == v) {
                    matchH[baseU] = baseV;
                    matchH[baseV] = baseU;
                }
            }
        }
    }

    private int computeDualY(int v) {
        int baseV = maxPositiveBlossoms.find(v);
        return switch (labelG[baseV]) {
            case UNLABELED -> yBase[v];
            case OUTER -> yBase[v] - (delta - yDelta[v]); // OUTER vertices decrease
            case INNER -> yBase[v] + (delta - yDelta[v]); // INNER vertices increase
            default -> yBase[v];
        };
    }

    private void scanEdges(int u) {
        for (int v : graph.getAllNeighbors(u)) {
            stats.examineEdge();
            int baseV = maxPositiveBlossoms.find(v);
            if (matchG[v] == u || labelG[baseV] == Label.INNER) {
                continue;
            }

            int slack = computeDualY(u) + computeDualY(v);
            if (labelG[baseV] == Label.UNLABELED) {
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

        if (path1[baseV] == lcaSearchTime) {
            return baseV;
        }
        if (path2[baseU] == lcaSearchTime) {
            return baseU;
        }
        return -1;
    }

    private boolean isEdgeTight(int u, int v) {
        // w(e) = 2 if e is in M, 0 otherwise
        int w = (matchG[u] == v) ? 2 : 0;
        return computeDualY(u) + computeDualY(v) == w;
    }

    // -------------------------------------------------------------------
    // Phase 2: DFS on the Contracted H-Graph
    // -------------------------------------------------------------------

    private void augPathDFS(int vH, int rootH, ArrayList<Integer> augPath) {
        if (augPathFound) {
            return;
        }

        for (int uH : adjH.getOrDefault(vH, new HashSet<>())) {
            // One H-graph edge scanned in Phase 2 DFS
            stats.examineEdge();

            int baseV = maxBlossomsH.find(vH);
            int baseU = maxBlossomsH.find(uH);

            if (labelH[baseU] == Label.UNLABELED) {
                labelH[uH] = Label.INNER;
                parentH[uH] = vH;

                if (matchH[uH] == -1) {
                    augPathFound = true;
                    augPath.addAll(findAugPathH(vH, uH, rootH));
                    return;
                } else {
                    int nextOuter = matchH[uH];
                    labelH[nextOuter] = Label.OUTER;
                    outerTime[nextOuter] = phase2Counter;
                    phase2Counter++;

                    augPathDFS(nextOuter, rootH, augPath);
                    if (augPathFound) {
                        return;
                    }
                }
            } else if (labelH[baseU] == Label.OUTER && outerTime[baseV] < outerTime[baseU]) {
                int curr = baseU;

                while (curr != baseV) {
                    maxBlossomsH.union(curr, baseV, baseV);

                    int matchedNode = matchH[curr];
                    maxBlossomsH.union(matchedNode, baseV, baseV);

                    sourceBridgeH[matchedNode] = uH;
                    targetBridgeH[matchedNode] = vH;

                    if (labelH[matchedNode] == Label.INNER) {
                        // Pass single list down recursively
                        augPathDFS(matchedNode, rootH, augPath);
                        if (augPathFound) {
                            return;
                        }
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
    private ArrayList<Integer> findAugPathH(int start, int end, int rootH) {
        ArrayList<Integer> augPath = new ArrayList<>();

        // 1. Add the final free node to the end of our backwards path
        augPath.add(end);

        // 2. Unroll the H-path backwards from the OUTER node (start) all the way up to
        // the root
        ArrayList<Integer> temp = new ArrayList<>();
        unrollBlossom(temp, start, rootH, hStruct);

        // 3. Append the unrolled sequence
        for (int x : temp) {
            augPath.add(x);
        }

        // 4. Reverse it so it correctly goes from root -> free node.
        Collections.reverse(augPath);

        return augPath;
    }

    private ArrayList<Integer> findAugPathG(ArrayList<Integer> apH) {
        ArrayList<Integer> augPathG = new ArrayList<>();

        // Mehlhorn's Pairwise Logic: Process the H-nodes in chunks of 2.
        // Every pair (b0, b1) is connected by an UNMATCHED bridge.
        for (int i = 0; i < apH.size(); i += 2) {
            int b0 = apH.get(i);
            int b1 = apH.get(i + 1);

            Edge bridge = bridgeHG.get(b0).get(b1);
            stats.examineEdge(); // the bridge edge connecting the two blossoms

            // 1. Identify which physical endpoint belongs to which blossom
            int u = bridge.vertex1();
            int v = bridge.vertex2();
            if (maxPositiveBlossoms.find(u) != b0) {
                u = bridge.vertex2();
                v = bridge.vertex1();
            }

            // 2. Unroll the Left Blossom (b0): Base -> Bridge
            // unrollBlossom natively traces Bridge -> Base, so we reverse it.
            ArrayList<Integer> leftPath = new ArrayList<>();
            unrollBlossom(leftPath, u, b0, gStruct);
            Collections.reverse(leftPath);
            augPathG.addAll(leftPath);

            // 3. Unroll the Right Blossom (b1): Bridge -> Base
            // unrollBlossom natively traces Bridge -> Base, so we just append it.
            ArrayList<Integer> rightPath = new ArrayList<>();
            unrollBlossom(rightPath, v, b1, gStruct);
            augPathG.addAll(rightPath);
        }

        return augPathG;
    }

    /**
     * Recursively tracks the internal path of a blossom, starting from entry
     * and ending at exit, and adds the internal nodes, including the entry and
     * exit, to the given list.
     */
    private void unrollBlossom(ArrayList<Integer> path, int entry, int exit, BlossomStructure struct) {
        if (entry == exit) {
            path.addLast(entry);
            return;
        }

        // Each step crosses one matched / tree / bridge edge of the blossom.
        stats.examineEdge();

        if (struct.label[entry] == Label.OUTER) {
            int matchedNode = struct.match[entry];
            int parentNode = struct.parent[matchedNode];
            path.addLast(entry);
            path.addLast(matchedNode);
            unrollBlossom(path, parentNode, exit, struct);
        } else {
            int src = struct.sourceBridge[entry];
            int tgt = struct.targetBridge[entry];
            int matchedNode = struct.match[entry];

            // 1. Add the initial matched edge for this INNER node
            path.addLast(entry);

            // 2. Trace upwards from the source of the bridge to the matched node.
            // Since we need to walk downwards from the matched node to the bridge,
            // we **reverse** the collected path.
            ArrayList<Integer> temp = new ArrayList<>();
            unrollBlossom(temp, src, matchedNode, struct);
            for (int i = temp.size() - 1; i >= 0; i--) {
                path.addLast(temp.get(i));
            }

            // 3. Cross the bridge and continue unrolling from the target side up to the
            // exit
            unrollBlossom(path, tgt, exit, struct);
        }
    }

    // -------------------------------------------------------------------
    // Auxiliary Data Structures
    // -------------------------------------------------------------------

    /**
     * Structural wrapper holding references to arrays required for blossom
     * unrolling.
     */
    private static class BlossomStructure {
        private final Label[] label;
        private final int[] match;
        private final int[] parent;
        private final int[] sourceBridge;
        private final int[] targetBridge;

        private BlossomStructure(Label[] label, int[] match, int[] parent, int[] sourceBridge, int[] targetBridge) {
            this.label = label;
            this.match = match;
            this.parent = parent;
            this.sourceBridge = sourceBridge;
            this.targetBridge = targetBridge;
        }
    }

    /**
     * Array-based priority queue strictly bounded to maximum delta n/2.
     * Guarantees O(1) amortized insertion and extraction.
     */
    private static class PriorityQueueArray {
        private final Stack<Edge>[] queues;
        private final int maxDelta;
        private final GabowStatistics stats;
        private int currentDelta = 0;

        @SuppressWarnings("unchecked")
        private PriorityQueueArray(int maxDelta, GabowStatistics stats) {
            this.maxDelta = maxDelta;
            this.stats = stats;
            this.queues = new Stack[maxDelta];
            for (int i = 0; i < maxDelta; i++) {
                queues[i] = new Stack<>();
            }
        }

        private void clear() {
            for (int i = 0; i < maxDelta; i++) {
                queues[i].clear();
            }
            currentDelta = 0;
        }

        private void add(Edge edge, int tightDelta) {
            if (tightDelta < maxDelta) {
                stats.recordPriorityQueueOperation(); // push
                queues[tightDelta].push(edge);
            }
        }

        private Edge pollNextAtDelta(int targetDelta) {
            if (targetDelta > currentDelta) {
                currentDelta = targetDelta;
            }
            if (targetDelta >= maxDelta || queues[targetDelta].isEmpty()) {
                return null;
            }
            stats.recordPriorityQueueOperation(); // pop
            return queues[targetDelta].pop();
        }
    }

    private static class NodePartition {
        private final int[] parent; // parent[i] gives the parent of node i in the union-find structure
        private final int[] rank;
        private final int[] blossomBase; // blossomBase[i] gives the base of the blossom that node i immediately belongs
                                         // to
        private final GabowStatistics stats;

        /**
         * Initializes the data structure for a maximum of n vertices.
         *
         * @param n     The maximum number of vertices in the graph.
         * @param stats counters into which each find/union is recorded
         */
        private NodePartition(int n, GabowStatistics stats) {
            this.stats = stats;
            parent = new int[n];
            rank = new int[n];
            blossomBase = new int[n];
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
        private int find(int u) {
            stats.recordDsuOperation();
            int root = getRoot(u);
            return blossomBase[root];
        }

        /**
         * union(u, v): Merges the sets containing u and v.
         * Precondition: {u, v} must be an edge in E(T).
         */
        private void union(int u, int v, int newBase) {
            stats.recordDsuOperation();
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
        private void makeRep(int base) {
            int root = getRoot(base);
            blossomBase[root] = base;
        }

        private void reset(int n) {
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
