package edu.rit.cs.graph_matching.algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import edu.rit.cs.graph_matching.graph.CompactGraph;
import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;

/**
 * Raphael Yuster's exact maximum-matching algorithm for regular and
 * almost-regular graphs ("Maximum matching in regular and almost regular
 * graphs"): {@code O(r n^2 log n)} on an {@code r}-almost-regular
 * {@code n}-vertex
 * graph, hence {@code O(n^2 log n)} when regular.
 * <p>
 * It works bottom-up over spanning subgraphs {@code G_t ⊆ ... ⊆ G_0 = G}, each
 * with roughly half the max degree of the next (Lemma 2.2), built by keeping
 * every other edge of an Euler tour. A maximum matching is found on the
 * sparsest
 * {@code G_t} (max degree {@code O(sqrt(n))}), then lifted level-by-level: a
 * maximum matching of {@code G_i} is a valid matching of {@code G_{i-1}}
 * needing
 * only a few more augmenting paths.
 * <p>
 * Each level is delegated to a solver sharing one {@code matches} array:
 * {@link GabowAlgorithm} ({@code O(m sqrt(n))}, Lemma 2.4) for the base,
 * {@link EdmondsAlgorithm} ({@code O(m)} per path, Lemma 2.5) for the lifts.
 * Correctness is independent of the subgraph sequence — the last level is
 * {@code G} itself, so the result is always a maximum matching of {@code G};
 * the
 * sequence only governs running time. {@link #augment()} is cooperatively
 * interruptible.
 */
public class YusterAlgorithm implements MatchingAlgorithm {
    /** The input graph, {@code G_0 = G}. */
    private final Graph graph;

    /**
     * Spanning subgraphs, densest first: index 0 is {@code G}, the last is the
     * sparsest {@code G_t}.
     */
    private final List<Graph> subgraphs;

    /**
     * Matching shared across all levels: {@code matches[v]} is v's mate, or -1 if
     * unmatched.
     */
    private final int[] matches;

    /**
     * Level being made maximum, from the sparsest {@code G_t} down to
     * {@code G_0 = G}; finished when {@code < 0}.
     */
    private int level;

    /**
     * Solver for the current level (Gabow at the base, Edmonds for lifts); lazily
     * (re)created per level.
     */
    private MatchingAlgorithm levelSolver;

    /**
     * Create a Yuster matching solver; builds the subgraph sequence eagerly in
     * {@code O(n * d)} time.
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
     * Build {@code G_0, ..., G_t}: {@code G_0 = g}, each next graph has ~half the
     * max degree, stopping once the max degree drops to {@code O(sqrt(n))}.
     *
     * @param g
     *          the input graph {@code G_0}
     * @return the subgraphs, densest ({@code G_0}) first, sparsest ({@code G_t})
     *         last
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
     * Spanning subgraph with each degree roughly halved (Lemma 2.2): supplement
     * each component to even degree with a matching {@code S} on its odd-degree
     * vertices, take an Euler tour {@code e_1, ..., e_s}, and keep {@code F \ S}
     * where {@code F = {e_2, e_4, ...}}. With odd-degree vertices present the tour
     * starts on a supplement edge ({@code e_1 ∈ S}), giving the degree bound
     * {@code ⌊δ/2⌋ ≤ d'(v) ≤ ⌈Δ/2⌉}.
     *
     * @param g
     *          the graph to thin out
     * @return a spanning subgraph of {@code g} with roughly half the degree
     */
    private static Graph halveDegrees(Graph g) {
        int n = g.size();

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

        // Pair each component's odd-degree vertices with supplement edges so all
        // degrees are even (Euler tour exists). Added BEFORE the originals so they
        // get the lowest edge indices and thus come first in each vertex's CSR
        // incidence list; a tour rooted at an odd-degree vertex then starts on its
        // supplement edge (the paper's e_1 ∈ S). componentStart = such a root per
        // component, or -1 if the component is all-even.
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
        boolean[] kept = new boolean[edgeCount];

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
                    kept[e] = true;
                }
            }
        }

        return CompactGraph.fromSelectedEdges(n, edgeU, edgeV, kept);
    }

    /**
     * Euler tour of the component containing {@code start} (Hierholzer's
     * algorithm; all degrees must be even). Written forward into {@code tour}, so
     * consecutive edges share a vertex and {@code tour[1], tour[3], ...} are the
     * even positions {@code e_2, e_4, ...}.
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
     * Label each vertex with its connected-component index in
     * {@code [0, componentCount)}.
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
     * Solver for {@code level}, sharing {@link #matches}. The base (sparsest)
     * level builds {@code M_t} from scratch with {@link GabowAlgorithm}
     * ({@code O(m sqrt(n))}, Lemma 2.4); denser levels only lift it with a few
     * single augmenting paths, for which {@link EdmondsAlgorithm} ({@code O(m)}
     * each, Lemma 2.5) suffices.
     *
     * @param level
     *              the level whose subgraph the solver should operate on
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
                // Shares the matching, so the previous level's result is lifted forward.
                levelSolver = createLevelSolver(level);
            }

            int result = levelSolver.augment();
            if (result != -1) {
                return result;
            }

            // Maximum for this level; descend to the next, denser subgraph.
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
