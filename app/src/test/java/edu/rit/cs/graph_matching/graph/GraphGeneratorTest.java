package edu.rit.cs.graph_matching.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.rit.cs.graph_matching.util.IntHashSet;

class GraphGeneratorTest {
    private static final long SEED = 0x8294757462947573L;

    @Test
    void testGenerateStarGraph() {
        MutableGraph starGraph = new AdjacencySetGraph(10);
        GraphGenerator.generateStarGraph(starGraph);

        assertEquals(9, starGraph.getDegree(0));

        for (int i = 1; i < starGraph.size(); i++) {
            assertTrue(starGraph.hasEdge(0, i));
            assertEquals(1, starGraph.getDegree(i));
        }
    }

    @Test
    void testGenerateStarGraphWithMatching() {
        MutableGraph starGraphWithMatching = new AdjacencySetGraph(8);
        GraphGenerator.generateStarGraphWithMatching(starGraphWithMatching, 3);

        int edgeCount = 0;
        for (int i = 0; i < starGraphWithMatching.size(); i++) {
            edgeCount += starGraphWithMatching.getDegree(i);
        }
        edgeCount /= 2;

        assertEquals(9, edgeCount);
    }

    @Test
    void testGenerateStarGraphWithMatchingError() {
        MutableGraph starGraphWithMatching = new AdjacencySetGraph(8);
        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateStarGraphWithMatching(starGraphWithMatching, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateStarGraphWithMatching(starGraphWithMatching, 10);
        });
    }

    @Test
    void testGenerateRandomGraphZeroProb() {
        MutableGraph randomGraph = new AdjacencySetGraph(5);
        GraphGenerator.generateRandomGraph(randomGraph, 0.0, new Random(SEED));

        int edgeCount = 0;
        for (int i = 0; i < randomGraph.size(); i++) {
            edgeCount += randomGraph.getDegree(i);
        }
        edgeCount /= 2;

        assertEquals(0, edgeCount);
    }

    @Test
    void testGenerateRandomGraphFullProb() {
        MutableGraph randomGraph = new AdjacencySetGraph(5);
        GraphGenerator.generateRandomGraph(randomGraph, 1.0, new Random(SEED));

        int edgeCount = 0;
        for (int i = 0; i < randomGraph.size(); i++) {
            edgeCount += randomGraph.getDegree(i);
        }
        edgeCount /= 2;

        assertEquals(10, edgeCount);
    }

    @Test
    void testGenerateRandomGraphInvalidProb() {
        MutableGraph randomGraph = new AdjacencySetGraph(5);

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateRandomGraph(randomGraph, -0.1, new Random(SEED));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateRandomGraph(randomGraph, 1.1, new Random(SEED));
        });
    }

    @Test
    void testGenerateGraph() {
        int vertices = 6;
        int degree = 3;

        MutableGraph graph = new AdjacencySetGraph(vertices);
        Random random = new Random();
        int[] degreeSequence = GraphUtils.generateRegularDegreeSequence(vertices, degree);

        graph = GraphGenerator.generateGraph(graph, degreeSequence, random);

        int expectedEdges = 0;
        for (int d : degreeSequence) {
            expectedEdges += d;
        }
        expectedEdges /= 2;

        int actualEdges = 0;
        for (int i = 0; i < graph.size(); i++) {
            actualEdges += graph.getAllNeighbors(i)
                                .size();
        }
        actualEdges /= 2;

        assertEquals(expectedEdges, actualEdges);

        for (int i = 0; i < vertices; i++) {
            assertEquals(degreeSequence[i], graph.getAllNeighbors(i)
                                                 .size());
        }
    }

    @Test
    void testGenerateBipartiteGraph() {
        MutableGraph bipartiteGraph = new AdjacencySetGraph(6);
        Random random = new Random();
        int[] leftDegreeSequence = GraphUtils.generateRegularDegreeSequence(3, 2);
        int[] rightDegreeSequence = GraphUtils.generateRegularDegreeSequence(3, 2);

        bipartiteGraph = GraphGenerator.generateBipartiteGraph(bipartiteGraph, leftDegreeSequence,
                rightDegreeSequence, random);

        int expectedEdges = 0;
        for (int d : leftDegreeSequence) {
            expectedEdges += d;
        }

        int actualEdges = 0;
        for (int i = 0; i < bipartiteGraph.size(); i++) {
            actualEdges += bipartiteGraph.getAllNeighbors(i)
                                         .size();
        }
        actualEdges /= 2;

        assertEquals(expectedEdges, actualEdges);

        for (int i = 0; i < 3; i++) {
            assertEquals(leftDegreeSequence[i], bipartiteGraph.getAllNeighbors(i)
                                                              .size());
        }

        for (int i = 3; i < 6; i++) {
            assertEquals(rightDegreeSequence[i - 3], bipartiteGraph.getAllNeighbors(i)
                                                                   .size());
        }
    }

    @Test
    void testGenerateLoopGraph1() {
        // Edge case: a graph with a single vertex cannot form a cycle.
        MutableGraph graph = new AdjacencySetGraph(1);
        GraphGenerator.generateLoopGraph(graph);

        int edgeCount = 0;
        for (int i = 0; i < graph.size(); i++) {
            edgeCount += graph.getDegree(i);
        }
        edgeCount /= 2;

        assertEquals(0, edgeCount);
        assertEquals(0, graph.getDegree(0));
    }

    @Test
    void testGenerateLoopGraph2() {
        // Special small case: two vertices should form a single undirected
        // edge.
        MutableGraph graph = new AdjacencySetGraph(2);
        GraphGenerator.generateLoopGraph(graph);

        int edgeCount = 0;
        for (int i = 0; i < graph.size(); i++) {
            edgeCount += graph.getDegree(i);
        }
        edgeCount /= 2;

        assertEquals(1, edgeCount);
        assertTrue(graph.hasEdge(0, 1));
        assertEquals(1, graph.getDegree(0));
        assertEquals(1, graph.getDegree(1));
    }

    @ParameterizedTest
    @ValueSource(ints = { 3, 4, 5, 7, 10 })
    void testGenerateLoopGraphCycleProperties(int n) {
        // General case: a cycle graph on n >= 3 vertices
        // should contain exactly n edges.
        MutableGraph graph = new AdjacencySetGraph(n);
        GraphGenerator.generateLoopGraph(graph);

        int edgeCount = 0;
        for (int i = 0; i < graph.size(); i++) {
            edgeCount += graph.getDegree(i);
        }
        edgeCount /= 2;

        assertEquals(n, edgeCount);

        // Every vertex should have degree 2
        for (int i = 0; i < n; i++) {
            assertEquals(2, graph.getDegree(i));
        }

        // Consecutive vertices (including last to first) must be connected.
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            assertTrue(graph.hasEdge(i, j));
        }
    }

    // Test invalid inputs to the generateCliqueLoopGraph() function
    // For input n
    @Test
    void testGenerateLoopOfCliquesInvalidN() {
        MutableGraph graph = new AdjacencySetGraph(3);

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateCliqueLoopGraph(graph, 1, 3);
        });
    }

    // For input k
    @Test
    void testGenerateLoopOfCliquesInvalidK() {
        MutableGraph graph = new AdjacencySetGraph(4);

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateCliqueLoopGraph(graph, 2, 2);
        });
    }

    // Test if incorrect graph size throws error
    @Test
    void testGenerateLoopOfCliquesWrongGraphSize() {
        MutableGraph graph = new AdjacencySetGraph(10);

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateCliqueLoopGraph(graph, 3, 4);
        });
    }

    // Test cases
    @ParameterizedTest
    @CsvSource({ "10, 5", "20, 6", "30, 8", "100, 33", "150, 58" })
    void testGenerateCliqueLoopProperties(int n, int k) {
        MutableGraph graph = new AdjacencySetGraph(n * k);
        GraphGenerator.generateCliqueLoopGraph(graph, n, k);

        // Each vertex should have degree of k-1
        // The total number of edges should be n*k*(k-1)/2
        int edgeCount = 0;
        for (int v = 0; v < graph.size(); v++) {
            edgeCount += graph.getDegree(v);
            assertEquals(k - 1, graph.getDegree(v));
        }
        edgeCount /= 2;

        int expectedEdges = n * (k * (k - 1) / 2);
        assertEquals(expectedEdges, edgeCount);

        for (int c = 0; c < n; c++) {
            int base = c * k;
            int nextBase = ((c + 1) % n) * k;

            // Test the correct removal of a single edge in each initial clique
            assertFalse(graph.hasEdge(base, base + 1));

            // Test the correct connection between cliques
            assertTrue(graph.hasEdge(base + 1, nextBase));
        }
    }

    /**
     * Helper function Checks the connectivity of a (compressed) graph
     * 
     * @param adjacency
     *     the adjacency list
     * @param start
     *     the starting index
     * @return {@code true} if all vertices are reachable from {@code start}
     */

    private static boolean DFSConnectivityCheck(List<IntHashSet> adjacency, int start) {
        int order = adjacency.size();
        boolean[] visited = new boolean[order];
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        stack.push(start);
        visited[start] = true;

        int count = 0;
        while (!stack.isEmpty()) {
            int current = stack.pop();
            count++;

            for (int next : adjacency.get(current)) {
                if (!visited[next]) {
                    visited[next] = true;
                    stack.push(next);
                }
            }
        }

        return count == order;
    }

    // Test cases
    @ParameterizedTest
    @CsvSource({ "10, 5", "20, 6", "30, 8", "100, 33", "300, 100" })
    void testGenerateCliqueLoopGraphCliqueStructure(int n, int k) {
        MutableGraph graph = new AdjacencySetGraph(n * k);
        GraphGenerator.generateCliqueLoopGraph(graph, n, k);

        // Part 1: correctness within each clique
        // Loop through each clique
        // c: clique index
        for (int c = 0; c < n; c++) {
            int internalEdgeCount = 0;
            int modifiedVertexCount = 0;
            int untouchedVertexCount = 0;

            // Loop thorugh each vertex v within a clique c.
            // There must be k-2 untouched vertices (degree == k-1)
            // and 2 modified vertices (internal degree == k-2).
            // This is tracked by counting the number of
            // same-clique vertices connected to v.

            for (int v = c * k; v < (c + 1) * k; v++) {
                int internalDegree = 0;

                for (int u : graph.getAllNeighbors(v)) {
                    if (u / k == c) {
                        internalDegree++;
                    }
                }

                if (internalDegree == k - 2) {
                    modifiedVertexCount++;
                } else if (internalDegree == k - 1) {
                    untouchedVertexCount++;
                } else {
                    fail("Unexpected internal degree in clique " + c);
                }

                internalEdgeCount += internalDegree;
            }

            internalEdgeCount /= 2;

            assertEquals(k * (k - 1) / 2 - 1, internalEdgeCount);
            assertEquals(2, modifiedVertexCount);
            assertEquals(k - 2, untouchedVertexCount);
        }

        // Part 2: correct loop-connection between cliques
        List<IntHashSet> cliqueAdjacency = new ArrayList<>(n);
        for (int c = 0; c < n; c++) {
            cliqueAdjacency.add(new IntHashSet());
        }

        // Build a compressed graph whose vertices are cliques.
        // If there is any edge between clique i and clique j in the original
        // graph,
        // then the compressed graph contains the edge (i, j).
        for (int v = 0; v < graph.size(); v++) {
            int cliqueOfV = v / k;

            for (int u : graph.getAllNeighbors(v)) {
                int cliqueOfU = u / k;

                if (cliqueOfU != cliqueOfV) {
                    cliqueAdjacency.get(cliqueOfV)
                                   .add(cliqueOfU);
                }
            }
        }

        // The compressed graph should be 2-regular
        int compressedEdgeCount = 0;
        for (int c = 0; c < n; c++) {
            // In a loop of cliques, each clique should connect to exactly two
            // other cliques.
            assertEquals(2, cliqueAdjacency.get(c)
                                           .size());
            compressedEdgeCount += cliqueAdjacency.get(c)
                                                  .size();
        }
        compressedEdgeCount /= 2;

        // The compressed graph should be a cycle on n vertices, so it has
        // exactly n edges.
        assertEquals(n, compressedEdgeCount);

        // Test connectivity
        assertTrue(DFSConnectivityCheck(cliqueAdjacency, 0));

        // confirmed: 2 regular + connectivity -> 2-regular connected graph is a
        // cycle
    }
}
