package edu.rit.cs.graph_matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import edu.rit.cs.graph_matching.GraphUtils.BipartiteColor;

/**
 * Implementation of the Goel–Kapralov–Khanna randomized algorithm for finding a
 * perfect matching in a d-regular bipartite graph.
 * <p>
 * Given a bipartite d-regular graph with equal partition sizes n this class
 * constructs a matching using truncated random walks and loop erasure c.f.
 * https://epubs.siam.org/doi/10.1137/100812513
 * </p>
 *
 * @see Graph
 * @see GraphUtils
 */
public class GoelKapralovKhanna implements MatchingAlgorithm {
    private final Graph graph;

    /** Size of one partition (half the total vertices) */
    private final int n;

    /** Degree of the regular graph */
    private final int d;

    private final IntHashSet left;

    // If u is unmatched, match[u] == -1.
    private final int[] match;

    private final int[] posInPath;

    private final RandomGenerator random;

    private final IntHashSet freeLeft;

    private int matchedCount = 0;

    /**
     * Constructs the solver and preprocesses the graph into adjacency-array
     * format.
     * <p>
     * The constructor colors the graph to identify left/right partitions and
     * initializes internal bookkeeping arrays.
     * </p>
     * <p>
     * Precondition: {@code graph} is bipartite and d-regular with equal
     * partition sizes. Those invariants are assumed (not validated) by this
     * implementation.
     * </p>
     *
     * @param graph
     *     input bipartite graph (assumed d-regular with equal partitions)
     * @param random
     *     random number generator used for sampling neighbors
     * @throws NullPointerException
     *     if {@code graph} or {@code random} is null
     */
    public GoelKapralovKhanna(Graph graph, RandomGenerator random) {
        this.graph = graph;
        this.d = graph.getDegree(0);
        this.random = random;
        this.n = graph.size() / 2; // Assuming graph is properly bipartite with
                                   // equal partitions

        this.match = new int[2 * n];
        Arrays.fill(this.match, -1);

        this.posInPath = new int[2 * n];
        Arrays.fill(this.posInPath, -1);

        this.left = new IntHashSet(n);
        this.freeLeft = new IntHashSet(n);

        BipartiteColor[] coloring = GraphUtils.colorBipartite(graph);
        for (int i = 0; i < coloring.length; i++) {
            if (coloring[i] == BipartiteColor.LEFT) {
                left.add(i);
                freeLeft.add(i);
            }
        }
    }

    /**
     * SAMPLE-OUT-EDGE(u): return a uniformly random neighbor {@code v} of the
     * left vertex {@code u} such that {@code v != match[u]}.
     * <p>
     * The method repeatedly samples random neighbors until it finds one that is
     * not the current partner of {@code u}. Expected O(1) time when the degree
     * {@code d} is constant.
     * </p>
     * <p>
     * Precondition: there exists at least one neighbor {@code v} of {@code u}
     * with {@code v != match[u]}. If {@code d == 0} this method returns -1.
     * Behavior is undefined (may loop) when the precondition is not met.
     * </p>
     *
     * @param u
     *     a left vertex index
     * @return a right-vertex neighbor of {@code u} different from
     *     {@code match[u]}, or -1 if the graph degree {@code d} is zero
     */
    private int sampleOutEdge(int u) {
        if (d == 0) {
            return -1;
        }
        while (true) {
            int v = graph.getRandomNeighbor(u, random);
            if (match[u] != v) {
                return v;
            }
        }
    }

    /**
     * Performs loop-erasure on a walk over left-side vertices (P).
     * <p>
     * Converts the sequence {@code walkP} into a simple path by removing cycles
     * (standard loop-erasure). Temporarily uses {@link #posInPath} for index
     * bookkeeping and restores it before returning.
     * </p>
     *
     * @param walkP
     *     sequence of left-side vertices produced by a (possibly cyclic) random
     *     walk
     * @return a loop-erased path (no repeated vertices), in the same vertex
     *     index space as {@code walkP}
     */
    private List<Integer> removeLoops(List<Integer> walkP) {
        List<Integer> path = new ArrayList<>();

        // Note: posInPath is guaranteed to be all -1 here.

        for (int u : walkP) {
            if (posInPath[u] != -1) {
                // Cycle detected: Erase loop
                int truncatePos = posInPath[u];

                for (int k = truncatePos + 1; k < path.size(); k++) {
                    posInPath[path.get(k)] = -1;
                }

                path.subList(truncatePos + 1, path.size())
                    .clear();
            } else {
                posInPath[u] = path.size();
                path.add(u);
            }
        }

        // Final Cleanup: Reset the remaining nodes in the valid path
        // so posInPath is clean for the next augmentation step.
        // Time: O(|walkP|) = O(n) in worst case, but typically much smaller.
        for (int u : path) {
            posInPath[u] = -1;
        }

        return path;
    }

    /**
     * Executes the truncated-random-walk matching procedure (Algorithm 2).
     * <p>
     * Repeatedly attempts to augment the current matching by performing
     * truncated random walks from free left vertices, applying loop erasure and
     * augmenting along discovered augmenting paths until a matching that covers
     * the left partition is found or no free vertices remain.
     * </p>
     * <p>
     * The algorithm is randomized; expected running time for a d-regular
     * bipartite graph is O(n log n) as described in the reference paper.
     * </p>
     *
     * @return a set of edges representing the matching found. If the input
     *     graph admits a perfect matching, the returned set contains exactly
     *     {@code n} edges (one per left vertex).
     */
    @Override
    public int augment() {
        int freeCount = n - matchedCount;
        int bj = (int) (2.0 * (4.0 + (double) (2 * n) / freeCount));

        while (true) {
            if (freeLeft.isEmpty()) {
                break;
            }

            // Pick a random free vertex from P
            int uStart = freeLeft.getRandom(random);

            List<Integer> walkP = new ArrayList<>();
            walkP.add(uStart);

            int currU = uStart;
            int steps = 0;
            int endV = -1;

            boolean success = false;
            while (steps < bj) {
                int v = sampleOutEdge(currU);

                if (match[v] != -1) {
                    int nextU = match[v];
                    currU = nextU;
                    walkP.add(currU);
                    steps++;
                } else {
                    // v is unmatched in. Augmenting path found.
                    endV = v;
                    success = true;
                    break;
                }
            }

            if (success) {
                List<Integer> pathP = removeLoops(walkP);
                int pathLength = -1;

                int vNext = endV;
                for (int i = pathP.size() - 1; i >= 0; i--) {
                    int u = pathP.get(i);
                    int vOldMatch = match[u];

                    match[u] = vNext;
                    match[vNext] = u;

                    vNext = vOldMatch;
                    pathLength += 2;
                }

                // Remove the starting node from free set
                freeLeft.remove(uStart);
                matchedCount++;

                return pathLength;
            }
        }

        return -1;
    }

    @Override
    public Set<Edge> getCurrentMatching() {
        Set<Edge> edges = new HashSet<>();
        for (int u : left) {
            if (match[u] != -1) {
                edges.add(new Edge(u, match[u]));
            }
        }
        return edges;
    }

    @Override
    public boolean isFinished() {
        return matchedCount >= n || freeLeft.isEmpty();
    }
}
