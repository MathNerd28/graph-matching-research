package edu.rit.cs.graph_matching.algorithm;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
import edu.rit.cs.graph_matching.util.IntSetQueue;

public class MeetInTheMiddleAlgorithm implements MatchingAlgorithm {
    private static final int RETRY_THRESHOLD = 1;

    private final Graph           graph;
    private final RandomGenerator random;

    private final IntSetQueue unmatched;

    private final int[] matches;

    private final int[] heads;
    private final int[] pathIds;

    private final int[] adjacents;
    private final int[] inPathId;
    private final int[] inPathVertex;

    private final int[] retries;

    public MeetInTheMiddleAlgorithm(Graph graph, RandomGenerator random) {
        this.graph = graph;
        this.random = random;

        this.matches = new int[graph.size()];
        this.adjacents = new int[graph.size()];
        this.heads = new int[graph.size()];
        this.pathIds = new int[graph.size()];
        this.inPathId = new int[graph.size()];
        this.inPathVertex = new int[graph.size()];
        this.retries = new int[graph.size()];

        Arrays.fill(matches, -1);
        Arrays.fill(heads, -1);
        Arrays.fill(pathIds, 1);
        Arrays.fill(inPathVertex, -1);

        this.unmatched = new IntSetQueue(graph.size());
        for (int v = 0; v < graph.size(); v++) {
            unmatched.add(v);
        }
    }

    public MeetInTheMiddleAlgorithm(Graph graph) {
        this(graph, new Random());
    }

    /**
     * Augment the path that was previously found. Toggle each of the edges
     * between matched/unmatched, and remove the start and head vertices from
     * the unmatched set.
     *
     * @return the number of edges in the path
     */
    private int augmentPath(int start) {
        int head = heads[start];
        int length = 1;
        int vertex = start;
        while (true) {
            int next = adjacents[vertex];

            if (unmatched.contains(next)) {
                matches[vertex] = next;
                matches[next] = vertex;
                break;
            }

            int nextNext = matches[next];
            matches[vertex] = next;
            matches[next] = vertex;
            vertex = nextNext;
            length += 2;
        }
        unmatched.remove(start);
        unmatched.remove(head);
        pathIds[start]++;
        pathIds[head]++;
        return length;
    }

    /**
     * @return false if the algorithm was interrupted
     */
    private int findAugmentingPath() {
        // Loop could run indefinitely; allow interruption for e.g. timeouts
        while (!Thread.interrupted()) {
            int start = unmatched.pollInt();

            if (heads[start] == -1) {
                heads[start] = start;
                addVertex(start, start);
            }

            PathStatus status = growPath(start, heads[start]);
            switch (status) {
                case DONE:
                    return start;
                case PARITY:
                    retries[start]++;
                    if (retries[start] < RETRY_THRESHOLD) {
                        break;
                    } else {
                        // fallthrough to FAIL
                    }
                case FAIL:
                    // Invalidate path
                    heads[start] = -1;
                    pathIds[start]++;

                    // fallthrough to ACTIVE
                case ACTIVE:
                    retries[start] = 0;
            }

            // Re-enqueue start vertex
            unmatched.offer(start);
        }

        return -1;
    }

    /**
     * The GROW_PATH(M, P, s, h) procedure as specified in the paper.
     *
     * @return the status of the current ALP
     */
    private PathStatus growPath(int start, int head) {
        // Detect cases where there are no valid choices for v0
        if (graph.getDegree(head) < 2 && (head != start || graph.getDegree(head) == 0)) {
            return PathStatus.FAIL;
        }

        // v0 = random element of N(h) \ M(h), i.e. a random neighbor of head
        // except its match
        int headMatch = matches[head];
        int v0;
        do {
            v0 = graph.getRandomNeighbor(head, random);
        } while (v0 == headMatch);

        if (v0 == -1 || v0 == start) {
            return PathStatus.FAIL;
        }

        // w0 = M(v0), i.e. the match of v0
        int w0 = matches[v0];
        if (w0 == -1) {
            // Case 1: v0 is unmatched, path is augmenting

            // Add {h, v0} (unmatched) to path
            addEdge(head, v0);
            addVertex(v0, start);

            heads[start] = v0;
            return PathStatus.DONE;
        }

        if (!isVertexInPath(v0)) {
            // Case 2: v0 is matched but not in any path

            // Add {h, v0} (unmatched) to path
            // Add {v0, w0} (matched) to path
            addEdge(head, v0);
            addVertex(v0, start);
            addVertex(w0, start);

            heads[start] = w0;
            return PathStatus.ACTIVE;
        }

        if (!hasVertex(v0, start)) {
            // New case: v0 is matched and in a DIFFERENT path
            // Walk the other path to check if the parity is correct
            // DON'T make changes to the other path until we're sure

            int w = w0;
            while (true) {
                if (heads[inPathVertex[w]] >= 0) {
                    // wrong parity
                    return PathStatus.PARITY;
                }

                int v = adjacents[w];
                w = matches[v];

                if (unmatched.contains(v)) {
                    // correct parity
                    addEdge(head, v0);
                    addVertex(v0, start);
                    addVertex(w0, start);
                    heads[start] = v;
                    return PathStatus.DONE;
                } else if (v == v0) {
                    // Intersected an even cycle of a different path before the
                    // other path was invalidated

                    // This is so incredibly unlikely that it doesn't really
                    // matter what we do here, so just do the simplest thing
                    return PathStatus.PARITY;
                }
            }
        }

        // v0 is already in P, forming a cycle
        // Attempt local repair
        int w = w0;
        while (true) {
            int vP = adjacents[w];
            int wP = matches[vP];

            // Delete {w, vP} (unmatched) from path
            removeEdge(w);
            removeVertex(w);

            if (graph.hasEdge(vP, head) && head != wP) {
                // Shortcut (Odd Cycle)

                // Add {vP, h} (unmatched) to path
                addEdge(vP, head);

                addVertex(w0, start);
                heads[start] = w0;
                return PathStatus.ACTIVE;
            } else if (wP == head) {
                // Pop (Even Cycle)

                // Delete {vP, wP} (matched) from path
                removeVertex(vP);
                removeVertex(wP);

                addVertex(w0, start);
                heads[start] = w0;
                return PathStatus.ACTIVE;
            } else if (vP == start) {
                return PathStatus.FAIL;
            }

            // Delete {vP, wP} (matched) from path
            removeVertex(vP);

            w = wP;
        }
    }

    /**
     * Adds an unmatched edge to the current ALP. Does NOT implicitly add the
     * vertices to the ALP. The edge must be unmatched, and neither vertex can
     * have another unmatched edge in the ALP.
     *
     * @param vertex1
     *     the first vertex in the edge
     * @param vertex2
     *     the second vertex in the edge
     */
    private void addEdge(int vertex1, int vertex2) {
        adjacents[vertex1] = vertex2;
        adjacents[vertex2] = vertex1;
    }

    /**
     * Removes an unmatched edge from the current ALP. Does NOT implicitly
     * remove the vertices from the ALP. The edge must be unmatched and already
     * present in the ALP.
     *
     * @param vertex
     *     one of the vertices in the edge.
     */
    private void removeEdge(int vertex) {
        int adjacent = adjacents[vertex];
        adjacents[vertex] = -1;
        adjacents[adjacent] = -1;
    }

    /**
     * Checks whether the current ALP contains a vertex.
     *
     * @param vertex
     *     the vertex
     * @return true of the ALP contains {@code vertex}
     */
    private boolean hasVertex(int vertex, int start) {
        return inPathVertex[vertex] == start && inPathId[vertex] == pathIds[start];
    }

    private boolean isVertexInPath(int vertex) {
        return inPathVertex[vertex] >= 0 && inPathId[vertex] == pathIds[inPathVertex[vertex]];
    }

    /**
     * Adds a vertex to the current ALP.
     *
     * @param vertex
     *     the vertex to add
     */
    private void addVertex(int vertex, int start) {
        inPathVertex[vertex] = start;
        inPathId[vertex] = pathIds[start];
    }

    /**
     * Removes a vertex from the current ALP.
     *
     * @param vertex
     *     the vertex to remove
     */
    private void removeVertex(int vertex) {
        inPathVertex[vertex] = -1;
    }

    @Override
    public int augment() {
        int start = findAugmentingPath();
        if (start >= 0) {
            return augmentPath(start);
        } else {
            return -1;
        }
    }

    @Override
    public Set<Edge> getCurrentMatching() {
        Set<Edge> matching = new LinkedHashSet<>();
        for (int v = 0; v < graph.size(); v++) {
            if (matches[v] != -1) {
                matching.add(new Edge(v, matches[v]));
            }
        }
        return matching;
    }

    @Override
    public boolean isFinished() {
        // Dani-Hayes has no explicit termination condition
        return unmatched.size() < 2;
    }

    /**
     * The possible results of {@link DaniHayesAlgorithm#growPath()}
     */
    private enum PathStatus {
        /** The ALP is incomplete but can continue growing */
        ACTIVE,
        /** The ALP is complete and ready to be augmented */
        DONE,
        /** The ALP cannot be recovered; need to start over */
        FAIL,
        /** The ALP cannot be recovered; need to start over */
        PARITY;
    }
}
