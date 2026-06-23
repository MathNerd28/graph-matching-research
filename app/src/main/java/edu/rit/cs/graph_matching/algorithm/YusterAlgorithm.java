package edu.rit.cs.graph_matching.algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import edu.rit.cs.graph_matching.graph.AdjacencySetGraph;
import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
import edu.rit.cs.graph_matching.graph.MutableGraph;

/**
 * Raphael Yuster's algorithm for maximum matching in regular and almost-regular
 * graphs, which runs in {@code O(r * n^2 * log n)} time on an {@code n}-vertex,
 * {@code r}-almost-regular graph (and hence {@code O(n^2 log n)} on a regular
 * graph). See "Maximum matching in regular and almost regular graphs".
 * <p>
 * The algorithm computes an <em>exact</em> maximum matching. The idea is to
 * compute the matching "bottom-up" over a sequence of progressively denser
 * spanning subgraphs {@code G_t ⊆ ... ⊆ G_1 ⊆ G_0 = G}, where each
 * {@code G_i} has roughly half the maximum degree of {@code G_{i-1}} (Lemma
 * 2.2). The sparsest graph {@code G_t} has maximum degree {@code O(sqrt(n))},
 * so
 * a maximum matching can be found there relatively cheaply. That matching is
 * then lifted level-by-level: a maximum matching of {@code G_i} is a (not
 * necessarily maximum) matching of {@code G_{i-1}}, and only a few augmenting
 * paths are needed to make it maximum again. Because denser subgraphs require
 * fewer augmentations, the total work telescopes to {@code O(r * n^2 * log n)}.
 * <p>
 * The subgraph sequence is constructed via Euler tours: each {@code G_i} keeps
 * every other edge of an Euler tour of {@code G_{i-1}} (after supplementing it
 * to an even-degree multigraph), which roughly halves every vertex's degree.
 * <p>
 * The matching work at every level is delegated to existing solvers, all
 * sharing
 * one {@code matches} array so the matching of one level becomes the starting
 * matching of the next. The sparsest level (the base case) computes a maximum
 * matching of {@code G_t} from an empty matching with {@link GabowAlgorithm},
 * whose phased shortest-augmenting-path search runs in {@code O(m * sqrt(n))}
 * (Lemma 2.4); since {@code m_t = O(n^1.5)} this base step costs
 * {@code O(n^2)}.
 * Plain single-path augmentation would need {@code O(n)} searches at
 * {@code O(m)} each, i.e. {@code O(n^2.5)} on {@code G_t}, which would dominate
 * the {@code O(n^2 log n)} target. Each denser level only lifts the existing
 * near-maximum matching with a handful of single augmenting-path searches
 * ({@code O(m)} each, Lemma 2.5), for which the lighter
 * {@link EdmondsAlgorithm}
 * suffices. Correctness does not depend on the subgraph sequence or on which
 * solver is used — the final level operates on {@code G_0 = G} itself, so the
 * result is always a maximum matching of {@code G}; the subgraph sequence only
 * governs the running time.
 * <p>
 * The algorithm is cooperatively interruptible: {@link #augment()} periodically
 * checks {@link Thread#interrupted()} and returns promptly when interrupted.
 */
public class YusterAlgorithm implements MatchingAlgorithm {
    /** The input graph, {@code G_0 = G}. */
    private final Graph graph;

    /**
     * The spanning-subgraph sequence. Index {@code 0} is the input graph
     * {@code G} (densest); index {@code subgraphs.size() - 1} is the sparsest
     * graph {@code G_t}. {@code subgraphs.get(i)} is {@code G_i}.
     */
    private final List<Graph> subgraphs;

    /**
     * The matching shared across all levels. {@code matches[v]} is the vertex
     * matched with {@code v}, or {@code -1} if {@code v} is unmatched. Each
     * per-level solver reads and updates this same array.
     */
    private final int[] matches;

    /**
     * The level currently being made maximum, working from the sparsest graph
     * {@code G_t} down to {@code G_0 = G}. Once it drops below zero, the
     * matching is a maximum matching of {@code G} and the algorithm is finished.
     */
    private int level;

    /**
     * The matching solver for the current level, operating on
     * {@code subgraphs.get(level)} and sharing {@link #matches}. This is a
     * {@link GabowAlgorithm} for the base level and an {@link EdmondsAlgorithm}
     * for the lift levels (see {@link #createLevelSolver(int)}). Lazily
     * (re)created whenever the level changes; {@code null} until first used.
     */
    private MatchingAlgorithm levelSolver;

    /**
     * Create a Yuster matching solver for the given graph. The subgraph
     * sequence is constructed eagerly here in {@code O(n * d)} time.
     *
     * @param graph
     *              the graph to be solved
     */
    public YusterAlgorithm(Graph graph) {
        this.graph = graph;

        this.matches = new int[graph.size()];
        Arrays.fill(matches, -1);

        this.subgraphs = buildSubgraphSequence(graph);
        this.level = subgraphs.size() - 1;
        this.levelSolver = null;
    }

    // Subgraph-sequence construction (Corollary 2.3)

    /**
     * Build the sequence {@code G_0, ..., G_t} of spanning subgraphs, where
     * {@code G_0} is the input graph and each subsequent graph has roughly half
     * the maximum degree of its predecessor. The number of levels {@code t} is
     * the least integer such that {@code d / 2^t <= sqrt(n)}, where {@code d} is
     * the maximum degree of {@code G}.
     *
     * @param g
     *          the input graph {@code G_0}
     * @return the subgraph sequence, with {@code G_0} at index 0 and the
     *         sparsest graph {@code G_t} last
     */
    private static List<Graph> buildSubgraphSequence(Graph g) {
        int n = g.size();

        int maxDegree = 0;
        for (int v = 0; v < n; v++) {
            maxDegree = Math.max(maxDegree, g.getDegree(v));
        }

        List<Graph> result = new ArrayList<>();
        result.add(g);

        // Halve the maximum degree until it drops to O(sqrt(n)).
        double sqrtN = Math.sqrt(n);
        Graph current = g;
        double degreeBound = maxDegree;
        while (degreeBound > sqrtN && !Thread.interrupted()) {
            current = halveDegrees(current);
            result.add(current);
            degreeBound /= 2.0;
        }

        return result;
    }

    /**
     * Construct a spanning subgraph in which every vertex's degree is roughly
     * halved, faithfully following Lemma 2.2. Each connected component is
     * supplemented to an even-degree multigraph by adding a perfect matching
     * {@code S} on its odd-degree vertices, an Euler tour {@code e_1, ..., e_s}
     * is taken, and the subgraph keeps {@code F \ S} where
     * {@code F = {e_2, e_4, ...}} is the set of even-position tour edges. When a
     * component has odd-degree vertices, its tour is rooted at one of them and
     * begins on a supplement edge ({@code e_1 ∈ S}), which is what makes the
     * resulting degrees satisfy {@code ⌊δ(G)/2⌋ ≤ d_{G'}(v) ≤ ⌈Δ(G)/2⌉}.
     *
     * @param g
     *          the graph to thin out
     * @return a spanning subgraph of {@code g} with roughly half the degree
     */
    private static Graph halveDegrees(Graph g) {
        int n = g.size();
        MutableGraph result = new AdjacencySetGraph(n);

        // Count original edges, so the multigraph edge arrays can be sized
        // exactly (originals + at most n/2 supplement edges).
        int originalEdges = 0;
        for (int v = 0; v < n; v++) {
            originalEdges += g.getDegree(v);
        }
        originalEdges /= 2;

        int capacity = originalEdges + n / 2 + 1;
        int[] edgeU = new int[capacity];
        int[] edgeV = new int[capacity];
        boolean[] isSupplement = new boolean[capacity];
        int edgeCount = 0;

        int[] componentId = computeComponents(g);
        int componentCount = Arrays.stream(componentId)
                .max()
                .getAsInt() + 1;

        // Supplement each connected component by pairing up its odd-degree
        // vertices, making all degrees even so an Euler tour exists. These edges
        // are created BEFORE the original edges, so they receive the lowest edge
        // indices and therefore appear first in every vertex's incidence list
        // (the CSR below is filled in edge-index order). Rooting a component's
        // tour at an odd-degree vertex then traverses its supplement edge first,
        // realizing the paper's requirement that the first tour edge be a
        // supplement edge (e_1 ∈ S). componentStart records, per component, such
        // a root vertex, or -1 when all degrees in the component are even.
        int[] componentStart = new int[componentCount];
        Arrays.fill(componentStart, -1);
        int[] oddPartner = new int[componentCount];
        Arrays.fill(oddPartner, -1);
        for (int v = 0; v < n; v++) {
            if ((g.getDegree(v) & 1) == 0) {
                continue;
            }
            int c = componentId[v];
            if (componentStart[c] == -1) {
                componentStart[c] = v;
            }
            if (oddPartner[c] == -1) {
                oddPartner[c] = v;
            } else {
                edgeU[edgeCount] = oddPartner[c];
                edgeV[edgeCount] = v;
                isSupplement[edgeCount] = true;
                edgeCount++;
                oddPartner[c] = -1;
            }
        }

        // Add every original edge once (u < v), after the supplement edges.
        for (int u = 0; u < n; u++) {
            for (int w : g.getAllNeighbors(u)) {
                if (u < w) {
                    edgeU[edgeCount] = u;
                    edgeV[edgeCount] = w;
                    edgeCount++;
                }
            }
        }

        // Build a CSR incidence structure over the multigraph.
        int[] incidenceStart = new int[n + 1];
        for (int e = 0; e < edgeCount; e++) {
            incidenceStart[edgeU[e] + 1]++;
            incidenceStart[edgeV[e] + 1]++;
        }
        for (int v = 0; v < n; v++) {
            incidenceStart[v + 1] += incidenceStart[v];
        }
        int[] incidence = new int[2 * edgeCount];
        int[] fill = incidenceStart.clone();
        for (int e = 0; e < edgeCount; e++) {
            incidence[fill[edgeU[e]]++] = e;
            incidence[fill[edgeV[e]]++] = e;
        }

        // Walk an Euler tour of each component and keep its even-position edges.
        boolean[] used = new boolean[edgeCount];
        int[] pointer = incidenceStart.clone();
        int[] stackVertex = new int[edgeCount + 1];
        int[] stackEdge = new int[edgeCount + 1];
        int[] tour = new int[edgeCount];

        for (int s = 0; s < n; s++) {
            // Skip already-consumed edges at this vertex.
            while (pointer[s] < incidenceStart[s + 1] && used[incidence[pointer[s]]]) {
                pointer[s]++;
            }
            if (pointer[s] == incidenceStart[s + 1]) {
                continue;
            }

            // Root the tour at an odd-degree vertex when one exists (so e_1 is a
            // supplement edge); otherwise the component is all-even and any
            // vertex works, so use the first one reached.
            int root = componentStart[componentId[s]];
            if (root == -1) {
                root = s;
            }

            int tourLength = eulerTour(root, incidence, incidenceStart, pointer, used, edgeU, edgeV,
                    stackVertex, stackEdge, tour);

            // Keep F = {e_2, e_4, ...}, the even-position tour edges, dropping
            // supplement edges (so the kept set is F \ S). Consecutive tour
            // edges share a vertex, so this retains roughly half of each
            // vertex's incident edges.
            for (int i = 1; i < tourLength; i += 2) {
                int e = tour[i];
                if (!isSupplement[e]) {
                    result.addEdge(edgeU[e], edgeV[e]);
                }
            }
        }

        return result;
    }

    /**
     * Trace an Euler tour of the connected component containing {@code start},
     * using Hierholzer's algorithm. All vertices in the component must have even
     * degree in the multigraph. The tour is written into {@code tour} in forward
     * order, so {@code tour[0]} is the first edge taken from {@code start} and
     * any two consecutive edges share a vertex (as do the first and last). This
     * lets even array indices ({@code tour[1], tour[3], ...}) correspond to the
     * even tour positions {@code e_2, e_4, ...}.
     *
     * @return the number of edges written into {@code tour}
     */
    private static int eulerTour(int start, int[] incidence, int[] incidenceStart, int[] pointer,
            boolean[] used, int[] edgeU, int[] edgeV, int[] stackVertex,
            int[] stackEdge, int[] tour) {
        int sp = 0;
        stackVertex[sp] = start;
        stackEdge[sp] = -1;
        sp++;

        int tourLength = 0;
        while (sp > 0) {
            int v = stackVertex[sp - 1];

            // Advance past edges already consumed elsewhere in the tour.
            while (pointer[v] < incidenceStart[v + 1] && used[incidence[pointer[v]]]) {
                pointer[v]++;
            }

            if (pointer[v] == incidenceStart[v + 1]) {
                // No edges left at v: backtrack, recording the arriving edge.
                int e = stackEdge[sp - 1];
                sp--;
                if (e != -1) {
                    tour[tourLength++] = e;
                }
            } else {
                int e = incidence[pointer[v]];
                used[e] = true;
                int w = (edgeU[e] == v) ? edgeV[e] : edgeU[e];
                stackVertex[sp] = w;
                stackEdge[sp] = e;
                sp++;
            }
        }

        // Hierholzer with post-order recording emits the circuit in reverse;
        // reverse it so tour[0..tourLength) is e_1, e_2, ... in forward order.
        for (int i = 0, j = tourLength - 1; i < j; i++, j--) {
            int tmp = tour[i];
            tour[i] = tour[j];
            tour[j] = tmp;
        }

        return tourLength;
    }

    /**
     * Label each vertex with the index of its connected component.
     *
     * @param g
     *          the graph
     * @return an array mapping each vertex to a component index in
     *         {@code [0, componentCount)}
     */
    private static int[] computeComponents(Graph g) {
        int n = g.size();
        int[] componentId = new int[n];
        Arrays.fill(componentId, -1);

        int nextComponent = 0;
        Queue<Integer> queue = new ArrayDeque<>();
        for (int s = 0; s < n; s++) {
            if (componentId[s] != -1) {
                continue;
            }

            componentId[s] = nextComponent;
            queue.add(s);
            while (!queue.isEmpty()) {
                int v = queue.poll();
                for (int w : g.getAllNeighbors(v)) {
                    if (componentId[w] == -1) {
                        componentId[w] = nextComponent;
                        queue.add(w);
                    }
                }
            }
            nextComponent++;
        }

        return componentId;
    }

    // MatchingAlgorithm interface

    /**
     * Create the matching solver for a given level, sharing {@link #matches} so
     * the matching carries across levels.
     * <p>
     * The sparsest level (the base case, {@code subgraphs.size() - 1}) builds a
     * maximum matching of {@code G_t} from an empty matching, so it uses
     * {@link GabowAlgorithm}, whose phased shortest-augmenting-path search runs
     * in {@code O(m * sqrt(n))} (Lemma 2.4) rather than the {@code O(n * m)} of
     * repeated single-path augmentation. Every denser level merely lifts an
     * existing near-maximum matching with a few single augmenting paths, for
     * which {@link EdmondsAlgorithm} ({@code O(m)} per path, Lemma 2.5) suffices.
     *
     * @param level
     *     the level whose subgraph the solver should operate on
     * @return a solver over {@code subgraphs.get(level)} sharing {@link #matches}
     */
    private MatchingAlgorithm createLevelSolver(int level) {
        Graph subgraph = subgraphs.get(level);
        boolean baseLevel = level == subgraphs.size() - 1;
        return baseLevel
                ? new GabowAlgorithm(subgraph, matches)
                : new EdmondsAlgorithm(subgraph, matches);
    }

    @Override
    public int augment() {
        while (level >= 0) {
            if (Thread.interrupted()) {
                return -1;
            }

            if (levelSolver == null) {
                // Operate on this level's subgraph while sharing the matching,
                // so the previous (denser-bound) matching is lifted forward.
                levelSolver = createLevelSolver(level);
            }

            int result = levelSolver.augment();
            if (result != -1) {
                return result;
            }

            // No augmenting paths remain at this level; the matching is maximum
            // for G_level. Descend to the next, denser subgraph and rescan.
            levelSolver = null;
            level--;
        }

        return -1;
    }

    @Override
    public boolean isFinished() {
        return level < 0;
    }

    @Override
    public Set<Edge> getCurrentMatching() {
        Set<Edge> matching = new LinkedHashSet<>();
        for (int v = 0; v < graph.size(); v++) {
            if (matches[v] >= 0) {
                matching.add(new Edge(v, matches[v]));
            }
        }
        return matching;
    }
}
