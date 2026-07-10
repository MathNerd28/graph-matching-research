package edu.rit.cs.graph_matching.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.rit.cs.graph_matching.graph.AdjacencySetGraph;
import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
import edu.rit.cs.graph_matching.graph.GraphGenerator;
import edu.rit.cs.graph_matching.graph.GraphUtils;
import edu.rit.cs.graph_matching.graph.MutableGraph;
import edu.rit.cs.graph_matching.runner.GraphStatistics;

/**
 * Tests for {@link YusterAlgorithm}. Yuster's algorithm targets regular and
 * almost-regular graphs but is correct on any graph, so its matching size is
 * validated against {@link EdmondsAlgorithm} (the project's ground truth) in
 * addition to checks on known graph families.
 */
class YusterAlgorithmTest {
    /**
     * A star graph always has a maximum matching of exactly one edge.
     */
    @ParameterizedTest
    @ValueSource(ints = { 2, 5, 10, 100, 1000, 10000 })
    void starGraphs(int vertices) {
        Graph g = GraphGenerator.generateStarGraph(new AdjacencySetGraph(vertices));

        Set<Edge> matching = new YusterAlgorithm(g).getMaximumMatching();

        assertEquals(1, matching.size(), "Star graphs always have a maximum matching of 1 edge");
        assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
    }

    /**
     * Star-wheel hybrid graphs contain odd cycles, exercising blossom
     * contraction. They have a fixed, known maximum matching size.
     */
    @ParameterizedTest
    @CsvSource({ "5, 2", "10, 2", "10, 3", "10, 4", "10, 5", "100, 25", "100, 49", "1000, 400",
                 "5000, 1000", })
    void starWheelHybridGraphs(int vertices, int matchingSize) {
        Graph g = GraphGenerator.generateStarGraphWithMatching(new AdjacencySetGraph(vertices),
                matchingSize);

        Set<Edge> matching = new YusterAlgorithm(g).getMaximumMatching();

        assertEquals(matchingSize, matching.size(),
                "Star-wheel hybrid graphs have a fixed maximum matching size");
        assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
    }

    /**
     * A single even cycle (a 2-regular loop graph) has a perfect matching.
     */
    @ParameterizedTest
    @ValueSource(ints = { 4, 10, 100, 1000, 10000 })
    void loopGraphs(int vertices) {
        Graph g = GraphGenerator.generateLoopGraph(new AdjacencySetGraph(vertices));

        Set<Edge> matching = new YusterAlgorithm(g).getMaximumMatching();

        assertEquals(vertices / 2, matching.size(), "An even cycle has a perfect matching");
        assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
    }

    /**
     * d-regular bipartite graphs always have a perfect matching (by Hall's
     * theorem), which Yuster's algorithm must find.
     */
    @ParameterizedTest
    @CsvSource({ "10, 4", "100, 5", "100, 10", "1000, 5", "1002, 101", "10000, 5", })
    void regularBipartiteGraphs(int vertices, int degree) {
        Random random = new Random(Objects.hash(vertices, degree));
        int[] degrees = GraphUtils.generateRegularDegreeSequence(vertices / 2, degree);

        Graph g = GraphGenerator.generateBipartiteGraph(new AdjacencySetGraph(vertices), degrees,
                degrees, random);

        Set<Edge> matching = new YusterAlgorithm(g).getMaximumMatching();

        assertEquals(vertices / 2, matching.size(),
                "Regular bipartite graphs have perfect matchings");
        assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
    }

    /**
     * On general (possibly non-bipartite) regular graphs, the matching size
     * must match Edmonds' algorithm exactly, and the matching must be valid.
     */
    @ParameterizedTest
    @CsvSource({ "10, 4", "100, 5", "100, 10", "101, 6", "1000, 5", "1000, 101", "1001, 6", })
    void regularGeneralGraphsMatchEdmonds(int size, int degree) {
        Random seeds = new Random(Objects.hash(size, degree));

        for (int j = 0; j < 5; j++) {
            Random random = new Random(seeds.nextLong());
            MutableGraph g =
                    GraphGenerator.generateGraph(new AdjacencySetGraph(size), degrees(size, degree),
                            random);

            int expected = new EdmondsAlgorithm(g).getMaximumMatching()
                                                  .size();
            Set<Edge> matching = new YusterAlgorithm(g).getMaximumMatching();

            assertEquals(expected, matching.size(),
                    "Yuster must match Edmonds' maximum matching size");
            assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
        }
    }

    /**
     * On irregular (Erdos-Renyi random) graphs, Yuster's algorithm must still
     * produce a maximum matching, agreeing with Edmonds.
     */
    @ParameterizedTest
    @CsvSource({ "10, 0.2", "30, 0.1", "50, 0.05", "100, 0.03", "200, 0.02", })
    void randomGraphsMatchEdmonds(int size, double edgeProbability) {
        Random seeds = new Random(Objects.hash(size, edgeProbability));

        for (int j = 0; j < 5; j++) {
            Random random = new Random(seeds.nextLong());
            Graph g = GraphGenerator.generateRandomGraph(new AdjacencySetGraph(size),
                    edgeProbability, random);

            int expected = new EdmondsAlgorithm(g).getMaximumMatching()
                                                  .size();
            Set<Edge> matching = new YusterAlgorithm(g).getMaximumMatching();

            assertEquals(expected, matching.size(),
                    "Yuster must match Edmonds' maximum matching size on irregular graphs");
            assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
        }
    }

    /**
     * Regression test for Yuster's compact subgraph storage. The input graph may
     * be a statistics wrapper, so level 0 must remain that exact wrapper even
     * though the generated halved subgraphs are stored in CSR form.
     */
    @Test
    void graphStatisticsWrapperStillCountsInputGraphCalls() {
        int vertices = 60;
        int degree = 20;
        Random random = new Random(Objects.hash(vertices, degree));
        Graph g = GraphGenerator.generateGraph(new AdjacencySetGraph(vertices),
                degrees(vertices, degree), random);
        GraphStatistics stats = new GraphStatistics(g);

        Set<Edge> matching = new YusterAlgorithm(stats).getMaximumMatching();

        long degreeChecks = ((Number) stats.getStatistics()
                                           .get("getDegree(v)")).longValue();
        long neighborScans = ((Number) stats.getStatistics()
                                            .get("getAllNeighbors()")).longValue();

        assertEquals(vertices / 2, matching.size(), "The regular graph should have a perfect matching");
        assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
        assertTrue(degreeChecks > 0,
                "Yuster should still count degree checks on the wrapped input graph");
        assertTrue(neighborScans > 0,
                "Yuster should still count neighbor scans on the wrapped input graph");
    }

    private static int[] degrees(int size, int degree) {
        return GraphUtils.generateRegularDegreeSequence(size, degree);
    }
}
