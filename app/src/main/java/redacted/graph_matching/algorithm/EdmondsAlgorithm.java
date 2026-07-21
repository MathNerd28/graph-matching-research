package redacted.graph_matching.algorithm;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

import redacted.graph_matching.graph.Graph;
import redacted.graph_matching.graph.Graph.Edge;

/**
 * Edmonds' Blossom Algorithm for maximum matching in a general undirected
 * graph. Adapted from Rosetta Code:
 * https://rosettacode.org/wiki/Blossom_algorithm#Java. Added documentation, and
 * slightly rewrote some parts for performance and readability.
 */
public class EdmondsAlgorithm implements MatchingAlgorithm {
    /** The input graph */
    private final Graph graph;

    /**
     * matches[v] = vertex matched with vertex v, or -1 if unmatched
     */
    private final int[] matches;

    /**
     * parents[v] = parent of vertex v in the alternating BFS tree
     */
    private final int[] parents;

    /**
     * bases[v] = base of the blossom containing vertex v
     */
    private final int[] bases;

    /** The next root to search */
    private int nextRoot = 0;

    /**
     * Create a blossom matching solver for the given graph, starting from an
     * empty matching.
     *
     * @param graph
     *     the graph to be solved
     */
    public EdmondsAlgorithm(Graph graph) {
        this.graph = graph;

        this.matches = new int[graph.size()];
        this.parents = new int[graph.size()];
        this.bases = new int[graph.size()];

        Arrays.fill(matches, -1);
    }

    /**
     * Create a blossom matching solver that operates on a caller-supplied
     * matching array. The array is used directly (not copied), so the caller
     * can observe augmentations and share matching state across solver
     * instances — for example, lifting a matching from one subgraph to another.
     * The array must follow the convention {@code matches[v] = } the vertex
     * matched with {@code v}, or {@code -1} if {@code v} is unmatched.
     *
     * @param graph
     *     the graph to be solved
     * @param matches
     *     the (length-{@code graph.size()}) matching array to read and update
     */
    public EdmondsAlgorithm(Graph graph, int[] matches) {
        this.graph = graph;

        this.matches = matches;
        this.parents = new int[graph.size()];
        this.bases = new int[graph.size()];
    }

    /**
     * Compute the least common ancestor of two vertices in the alternating
     * forest. This identifies the base of a newly found blossom.
     * <p>
     * This method assumes that such a common ancestor exists, and should only
     * be called in such cases.
     *
     * @param vertex1
     *     the first vertex
     * @param vertex2
     *     the second vertex
     */
    private int findLeastCommonAncestor(int vertex1, int vertex2) {
        Set<Integer> ancestors1 = new LinkedHashSet<>();

        // Walk upward from a to find all of a's alternating ancestors
        int a = vertex1;
        while (true) {
            // Add base of current vertex to ancestors
            int base1 = bases[a];
            ancestors1.add(base1);

            // If unmatched, then no more ancestors exist
            int match = matches[base1];
            if (match < 0) {
                break;
            }

            // Continue walking up the alternating forest
            a = parents[match];
        }

        // Walk upward from b until an ancestor of a is found
        int b = vertex2;
        while (true) {
            int base2 = bases[b];
            if (ancestors1.contains(base2)) {
                // found a common ancestor
                return base2;
            } else {
                // since we haven't found a common ancestor yet,
                // and we assume one exists, we assume a match exists here
                b = parents[matches[base2]];
            }
        }
    }

    /**
     * Mark all vertices on the path from vertex v to a blossom base as
     * belonging to the blossom, and fix parent pointers during contraction.
     *
     * @param vertex
     *     the source vertex
     * @param base
     *     the blossom base
     * @param parent
     *     the new parent of the vertex
     */
    private Set<Integer> computeBlossomPath(int vertex, int base, int parent) {
        Set<Integer> blossomPath = new LinkedHashSet<>();

        // Walk upward from v until we find the base
        int v = vertex;
        int p = parent;
        while (bases[v] != base) {
            int matchOfV = matches[v];

            // Add v's base and its match's base to the blossom
            blossomPath.add(bases[v]);
            blossomPath.add(bases[matchOfV]);

            // Fix parent pointers
            parents[v] = p;
            p = matchOfV;
            v = parents[matchOfV];
        }

        return blossomPath;
    }

    /**
     * Run a BFS to find an augmenting path starting from the given unmatched
     * root vertex.
     *
     * @param root
     *     the unmatched root vertex
     * @return the length of the augmenting path, or 0 if none was found
     */
    private int findAugmentingPath(int root) {
        Arrays.fill(parents, -1);

        // Initially, each vertex is its own blossom base
        for (int i = 0; i < graph.size(); i++) {
            bases[i] = i;
        }

        // Enqueue root
        Queue<Integer> bfsQueue = new ArrayDeque<>();
        Set<Integer> enqueued = new LinkedHashSet<>();
        bfsQueue.add(root);
        enqueued.add(root);

        while (!bfsQueue.isEmpty() && !Thread.interrupted()) {
            int vertex = bfsQueue.poll();

            for (int neighbor : graph.getAllNeighbors(vertex)) {
                // Ignore self-loops inside a blossom or matched edges
                if (bases[vertex] == bases[neighbor] || matches[vertex] == neighbor) {
                    continue;
                }

                if (neighbor == root
                        || (matches[neighbor] >= 0 && parents[matches[neighbor]] >= 0)) {
                    // Case 1: Found a blossom (odd cycle)

                    int commonAncestor = findLeastCommonAncestor(vertex, neighbor);

                    Set<Integer> currentBlossom = new LinkedHashSet<>();
                    currentBlossom.addAll(computeBlossomPath(vertex, commonAncestor, neighbor));
                    currentBlossom.addAll(computeBlossomPath(neighbor, commonAncestor, vertex));

                    // Contract the blossom
                    for (int i = 0; i < graph.size(); i++) {
                        if (currentBlossom.contains(bases[i])) {
                            bases[i] = commonAncestor;
                            if (!enqueued.contains(i)) {
                                enqueued.add(i);
                                bfsQueue.add(i);
                            }
                        }
                    }
                } else if (parents[neighbor] < 0) {
                    // Case 2: Extend alternating tree

                    parents[neighbor] = vertex;

                    // Found an augmenting path
                    if (matches[neighbor] < 0) {
                        return augmentMatching(neighbor);
                    }

                    // Continue BFS from the matched partner
                    int matchedNeighbor = matches[neighbor];
                    if (!enqueued.contains(matchedNeighbor)) {
                        enqueued.add(matchedNeighbor);
                        bfsQueue.add(matchedNeighbor);
                    }
                }
            }
        }
        return -1;
    }

    /**
     * Flip matching edges along the discovered augmenting path.
     *
     * @param freeVertex
     *     the initial vertex in the augmenting path
     */
    private int augmentMatching(int freeVertex) {
        int length = -1;
        int current = freeVertex;

        while (current >= 0) {
            length += 2;
            int previous = parents[current];
            int next = (previous >= 0) ? matches[previous] : -1;

            matches[current] = previous;
            if (previous >= 0) {
                matches[previous] = current;
            }

            current = next;
        }

        return length;
    }

    @Override
    public int augment() {
        for (; nextRoot < graph.size(); nextRoot++) {
            if (matches[nextRoot] < 0) {
                int result = findAugmentingPath(nextRoot);
                if (result != -1) {
                    nextRoot++;
                    return result;
                }
            }
        }

        return -1;
    }

    @Override
    public boolean isFinished() {
        return nextRoot >= graph.size();
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
