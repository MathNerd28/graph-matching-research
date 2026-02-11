package edu.rit.cs.graph_matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import edu.rit.cs.graph_matching.GraphUtils.BipartiteColor;

/*
 * Goel-Kapralov-Khanna Algorithm for Perfect Matching in Regular Bipartite Graphs.
 *  Ashish Goel, Michael Kapralov, and Sanjeev Khanna. 
 * "Perfect Matchings in O(n log n) Time in Regular Bipartite Graphs." 
 * SIAM J. Comput., 42(3), 1392–1404.
 */
public class GoelKapralovKhanna {

    private final Graph graph;
    private final int n; // Size of one partition (half the total vertices)
    private final int d; // Degree of the regular graph
    private final IntHashSet left;
    private final IntHashSet right;

    // If u is unmatched, match[u] == -1.
    private final int[] match;

    private final int[] posInPath;

    private final RandomGenerator random;

    /*
     * Constructs the solver and preprocesses the graph into an adjacency array
     * format.
     * 
     * @param graph The input bipartite graph.
     * 
     * @param random The random number generator to use.
     */
    public GoelKapralovKhanna(Graph graph, RandomGenerator random) {
        this.graph = graph;
        this.d = graph.getDegree(0);
        this.random = random;
        this.n = graph.size() / 2; // Assuming graph is properly bipartite with equal partitions

        this.match = new int[2 * n];
        Arrays.fill(this.match, -1);

        this.posInPath = new int[2 * n];
        Arrays.fill(this.posInPath, -1);

        this.left = new IntHashSet();
        this.right = new IntHashSet();

        BipartiteColor[] coloring = GraphUtils.colorBipartite(graph);
        for (int i = 0; i < coloring.length; i++) {
            if (coloring[i] == BipartiteColor.LEFT) {
                left.add(i);
            } else {
                right.add(i);
            }
        }
    }

    /*
     * SAMPLE-OUT-EDGE(u): Returns a random neighbor of u in P that is NOT matched
     * to u.
     * Runs in O(1) expected time.
     * * @param u A left vertex.
     * 
     * @return A right vertex that is a neighbor of u but is not matched to u.
     */
    private int sampleOutEdge(int u) {
        if (d == 0)
            return -1;
        while (true) {
            int v = graph.getRandomNeighbor(u, random);
            if (match[u] != v) {
                return v;
            }
        }
    }

    /*
     * Performs loop erasure on the random walk.
     * * @param walkP The sequence of vertices visited in P.
     * 
     * @return The path with loops removed.
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

                path.subList(truncatePos + 1, path.size()).clear();
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

    /*
     * Executes Algorithm 2: Perfect Matching with Truncated Random Walks.
     * 
     * @return The maximum matching.
     */
    public Set<Edge> getMaximumMatching() {
        List<Integer> freeLeft = new ArrayList<>(n);
        int matchedCount = 0;

        for (int u : left) {
            if (match[u] == -1) {
                freeLeft.add(u);
            } else {
                matchedCount++;
            }
        }

        while (matchedCount < n && !freeLeft.isEmpty()) {
            double freeCount = (double) (n - matchedCount);

            int b_j = (int) (2.0 * (4.0 + (2.0 * n) / freeCount));

            boolean success = false;

            while (!success) {
                if (freeLeft.isEmpty())
                    break;

                // Pick a random free vertex from P
                int randIdx = random.nextInt(freeLeft.size());
                int uStart = freeLeft.get(randIdx);

                List<Integer> walkP = new ArrayList<>();
                walkP.add(uStart);

                int currU = uStart;
                int steps = 0;
                int endV = -1;

                while (steps < b_j) {
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

                    int vNext = endV;
                    for (int i = pathP.size() - 1; i >= 0; i--) {
                        int u = pathP.get(i);
                        int vOldMatch = match[u];

                        match[u] = vNext;
                        match[vNext] = u;

                        vNext = vOldMatch;
                    }

                    // Remove the starting node from free set
                    // Efficient removal: swap with last element and pop
                    int lastFree = freeLeft.get(freeLeft.size() - 1);
                    freeLeft.set(randIdx, lastFree);
                    freeLeft.remove(freeLeft.size() - 1);

                    matchedCount++;
                }
            }
        }

        Set<Edge> edges = new HashSet<>();
        for (int u : left) {
            if (match[u] != -1) {
                edges.add(new Edge(u, match[u]));
            }
        }
        return edges;
    }
}
