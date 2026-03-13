package edu.rit.cs.graph_matching.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;

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

    // ----- Phase 2 DFS Structures ----- //

    /**
     * Indicates whether a node is currently part of the active DFS search
     * structure.
     */
    private final boolean[] inS;

    /** Tracks if a node is already part of a successfully found augmenting path. */
    private final boolean[] inP;

    /**
     * Disjoint-set array for tracking the bases of blossoms formed during the Phase
     * 2 DFS.
     */
    private final int[] b;

    /** Nodes are numbered by the time at which they become even (OUTER). */
    private final int[] outerTime;

    /** Global counter used to assign chronological timestamps to outerTime. */
    private int currentTime;

    /**
     * A flag used to immediately halt further recursive DFS exploration once a path
     * is found.
     */
    private boolean pathFound;

    // ----- H-Graph Mapping Structures ----- //

    /** Groups internal G-nodes inside a contracted Phase 1 H-node. */
    private final List<Integer>[] contractedInto;

    /** The single valid matching edge linking two contracted H-nodes. */
    private final int[] mateHG;

    /**
     * The physical G-node Edge representing the tree growth step between H-nodes.
     */
    private final Edge[] parentHG;

    /**
     * The physical G-node Edge representing the cross-bridge closing an H-node
     * blossom.
     */
    private final Edge[] bridgeHG;

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

        this.inS = new boolean[n];
        this.inP = new boolean[n];
        this.b = new int[n];
        this.outerTime = new int[n];

        this.contractedInto = new List[n];
        for (int i = 0; i < n; i++) {
            this.contractedInto[i] = new ArrayList<>();
        }
        this.mateHG = new int[n];
        this.parentHG = new Edge[n];
        this.bridgeHG = new Edge[n];
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
                commitDelayedUnions();
                return true;
            }

            // Dual Adjustment Step: Commit delayed blossom unions
            commitDelayedUnions();
            delta++;
        }

        return false;
    }

    private int computeDualY(int v) {
        int baseV = base.find(v);
        if (labels[baseV] == UNLABELED)
            return 1;
        if (labels[baseV] == OUTER)
            return yBase[v] - (delta - yDelta[v]);
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

    private void shrinkBlossom(int blossomBase, int start, int target) {
        // System.out.println("[Phase 1] Shrinking blossom at base: " + blossomBase + "
        // from " + start + " to " + target);
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
    // Phase 2: DFS on the Contracted H-Graph
    // -------------------------------------------------------------------

    private int executePhase2() {
        Arrays.fill(inS, false);
        Arrays.fill(inP, false);
        Arrays.fill(outerTime, 0);
        Arrays.fill(mateHG, -1);
        Arrays.fill(parentHG, null);
        Arrays.fill(bridgeHG, null);
        for (int i = 0; i < n; i++)
            contractedInto[i].clear();
        currentTime = 0;

        // 1. Inherit Phase 1 H-graph
        for (int v = 0; v < n; v++) {
            int hNode = dBase.find(v);
            b[v] = hNode; // Apply the Union-Find fix
            contractedInto[hNode].add(v); // Group the internal G-nodes

            // Map the matching edge to the H-graph level
            if (matches[v] != -1) {
                int hMate = dBase.find(matches[v]);
                if (hNode != hMate) {
                    mateHG[hNode] = hMate;
                }
            }
        }

        int pathsFoundCount = 0;

        // 2. Start the DFS strictly from FREE H-nodes
        for (int f = 0; f < n; f++) {
            int hNode = dBase.find(f);

            // Ensure f is a representative H-node, is free, and not already
            // processed/visited
            if (hNode == f && mateHG[f] == -1 && !inP[f] && !inS[f]) {
                inS[f] = true;
                outerTime[f] = ++currentTime;
                pathFound = false;

                find_ap(f);

                if (pathFound) {
                    pathsFoundCount++;
                }
            }
        }

        return pathsFoundCount;
    }

    private void find_ap(int xH) {
        if (pathFound)
            return;

        // 1. Iterate over all internal G-nodes inside the current H-node blossom
        for (int u : contractedInto[xH]) {
            for (int v : graph.getAllNeighbors(u)) {
                if (pathFound)
                    return;

                int yH = dBase.find(v);

                // Ignore internal edges, H-graph matching edges, and ALREADY augmented nodes
                // (inP)
                if (xH == yH || mateHG[xH] == yH || inP[yH])
                    continue;

                // The edge must be tight to exist in the H-graph
                if (!isEdgeTight(u, v))
                    continue;

                if (!inS[yH]) {
                    if (mateHG[yH] == -1) {
                        // yH is free -> Augmenting path found!
                        // Pass the exact G-node bridge (u, v) to unroll the path
                        augmentDFSPath(u, v);
                        pathFound = true;
                        return;
                    } else {
                        // Grow step
                        int yPrimeH = mateHG[yH];
                        inS[yH] = true;
                        inS[yPrimeH] = true;

                        // Track the specific G-node Edge representing the tree growth on the OUTER node
                        parentHG[yPrimeH] = new Edge(u, v);

                        // Inner nodes don't get outer times, Outer nodes do
                        outerTime[yPrimeH] = ++currentTime;
                        find_ap(yPrimeH);
                    }
                } else if (outerTime[dfsBase(yH)] > outerTime[dfsBase(xH)]) {
                    // Blossom step: yH is a proper descendant of xH (forward edge)
                    shrinkDFSBlossom(xH, yH, u, v);
                }
            }
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

    private int dfsBase(int v) {
        int root = v;
        while (root != b[root])
            root = b[root];

        int curr = v;
        while (curr != root) {
            int nxt = b[curr];
            b[curr] = root;
            curr = nxt;
        }
        // System.out.println("[Phase 2] dfsBase tracing node: " + v);
        return root;
    }

    private void shrinkDFSBlossom(int xH, int yH, int bridgeU, int bridgeV) {
        int baseNode = dfsBase(xH);
        int currH = yH;

        List<Integer> cycleNodes = new ArrayList<>();
        List<Integer> newOuterNodes = new ArrayList<>();

        // Walk up the H-graph tree from yH back to the base of xH
        while (currH != baseNode) {
            cycleNodes.add(currH);
            int mateH = mateHG[currH];
            cycleNodes.add(mateH);

            // Odd nodes become even. Add to the front so we explore
            // the nodes closest to the base first (preserving DFS order)
            newOuterNodes.add(0, mateH);

            // Step up to the parent H-node using the parent edge of the OUTER node
            Edge pEdge = parentHG[currH];
            int parentGNode = pEdge.vertex1();
            if (dBase.find(parentGNode) == currH || dBase.find(parentGNode) == mateH) {
                parentGNode = pEdge.vertex2();
            }
            currH = dfsBase(dBase.find(parentGNode));
        }

        // Pass 1: Contract the blossom in the Phase 2 'b' array
        for (int nodeH : cycleNodes) {
            b[dfsBase(nodeH)] = baseNode;
        }

        // Pass 2: Expand the search from the newly minted OUTER nodes
        for (int nodeH : newOuterNodes) {
            // Safely capture the specific bridge closing the cycle
            // Ensure vertex1 explicitly holds the descendant side (yH side)
            bridgeHG[nodeH] = new Edge(bridgeV, bridgeU);

            if (outerTime[nodeH] == 0) {
                outerTime[nodeH] = ++currentTime;
                if (!pathFound) {
                    find_ap(nodeH);
                }
            }
        }
    }

    private void augmentDFSPath(int breakU, int breakV) {
        // 1. Collect all non-matching edges that make up the augmenting path
        List<Edge> nonMatchingEdges = new ArrayList<>();
        nonMatchingEdges.add(new Edge(breakU, breakV));

        int xH = dBase.find(breakU);
        int yH = dBase.find(breakV);

        // Trace the path backwards through the H-graph from both ends of the bridge
        extractPathInHGraph(nonMatchingEdges, xH, -1);
        extractPathInHGraph(nonMatchingEdges, yH, -1);

        // 2. Expand the H-graph edges into the physical G-graph
        List<Integer> physicalPathNodes = new ArrayList<>();
        for (Edge e : nonMatchingEdges) {
            physicalPathNodes.add(e.vertex1());
            physicalPathNodes.add(e.vertex2());

            // Unwind any Phase 1 blossoms hidden inside the H-nodes
            extractPathInGGraph(physicalPathNodes, e.vertex1(), dBase.find(e.vertex1()));
            extractPathInGGraph(physicalPathNodes, e.vertex2(), dBase.find(e.vertex2()));
        }

        // 3. Finally, flip the matches along the resolved physical path
        while (!physicalPathNodes.isEmpty()) {
            int u = physicalPathNodes.remove(physicalPathNodes.size() - 1);
            int v = physicalPathNodes.remove(physicalPathNodes.size() - 1);

            inP[dBase.find(u)] = true;
            inP[dBase.find(v)] = true;

            matches[u] = v;
            matches[v] = u;
        }
    }

    /**
     * Helper: Traces the path backward through the Phase 2 H-Graph to a specified
     * target.
     * Corresponds to `find_path_in_HG` in Gabow's paper.
     */
    private void extractPathInHGraph(List<Edge> path, int vH, int uH) {
        if (vH == uH)
            return;
        if (vH == -1 || mateHG[vH] == -1)
            return; // Reached the root free node

        // If the node was natively OUTER (no Phase 2 bridge)
        if (bridgeHG[vH] == null) {
            Edge treeEdge = parentHG[vH];

            path.add(treeEdge);
            int nextH = dBase.find(treeEdge.vertex1());
            if (nextH == vH || nextH == mateHG[vH]) {
                nextH = dBase.find(treeEdge.vertex2());
            }

            extractPathInHGraph(path, nextH, uH);
        }
        // If the node was INNER but became OUTER via a Phase 2 blossom bridge
        else {
            Edge bridge = bridgeHG[vH];

            // To unwind a blossom cycle:
            // 1. Trace from the descendant side of the bridge UP to the mate
            extractPathInHGraph(path, dBase.find(bridge.vertex1()), dBase.find(mateHG[vH]));
            path.add(bridge);
            // 2. Trace from the ancestor side of the bridge UP to the target
            extractPathInHGraph(path, dBase.find(bridge.vertex2()), uH);
        }
    }

    /**
     * Helper: Unwinds internal Phase 1 blossoms hidden inside an H-node.
     * Corresponds to `find_path_in_G` in Gabow's paper.
     */
    private void extractPathInGGraph(List<Integer> pathNodes, int v, int targetH) {
        if (v == targetH)
            return; // FIX: Must strictly match the physical vertex

        if (labels[v] == OUTER) {
            int mateV = matches[v];
            int parentMateV = parents[mateV];

            pathNodes.add(mateV);
            pathNodes.add(parentMateV);
            extractPathInGGraph(pathNodes, parentMateV, targetH);
        } else {
            // It's an INNER node in Phase 1
            int sBridge = sourceBridge[v];
            int tBridge = targetBridge[v];

            extractPathInGGraph(pathNodes, sBridge, matches[v]);
            pathNodes.add(sBridge);
            pathNodes.add(tBridge);
            extractPathInGGraph(pathNodes, tBridge, targetH);
        }
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
