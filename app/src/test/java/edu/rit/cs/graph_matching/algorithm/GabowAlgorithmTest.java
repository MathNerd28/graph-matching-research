package edu.rit.cs.graph_matching.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.rit.cs.graph_matching.graph.*;

class GabowAlgorithmTest {

    /**
     * Tests basic non-perfect matching termination.
     * A star graph always has exactly 1 edge in its maximum matching.
     */
    @ParameterizedTest
    @ValueSource(ints = { 2, 5, 10, 100, 1000, 10000 })
    void testStarGraphs(int vertices) {
        Graph g = GraphGenerator.generateStarGraph(new AdjacencySetGraph(vertices));

        int[] matches = new int[g.size()];
        Arrays.fill(matches, -1);
        GabowAlgorithm algorithm = new GabowAlgorithm(g, matches);

        Set<Edge> matching = algorithm.getMaximumMatching();

        assertEquals(1, matching.size(), "Star graphs always have a maximum matching of 1 edge");
        assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
    }

    /**
     * Tests blossom contraction correctness.
     * Star-wheel hybrid graphs contain odd cycles, forcing the algorithm to
     * properly
     * identify, shrink, and expand blossoms to achieve the known maximum matching.
     */
    @ParameterizedTest
    @CsvSource({
            "5, 2",
            "10, 2",
            "10, 3",
            "10, 4",
            "10, 5",
            "100, 25",
            "100, 49",
            "1000, 400",
            "5000, 1000"
    })
    void testStarWheelHybridGraphs(int vertices, int matchingSize) {
        Graph g = GraphGenerator.generateStarGraphWithMatching(new AdjacencySetGraph(vertices), matchingSize);

        int[] matches = new int[g.size()];
        Arrays.fill(matches, -1);
        GabowAlgorithm algorithm = new GabowAlgorithm(g, matches);

        Set<Edge> matching = algorithm.getMaximumMatching();

        assertEquals(matchingSize, matching.size(), "Star-wheel hybrid graphs have a fixed maximum matching size");
        assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
    }

    /**
     * Tests scaling and efficiency on large bipartite graphs.
     * Asserts that a perfect matching is found and valid for d-regular bipartite
     * graphs.
     */
    @ParameterizedTest
    @CsvSource({
            "10, 4",
            "100, 5",
            "100, 10",
            "1000, 5",
            "1002, 101",
            "10000, 5",
            "100000, 5" // Stresses efficiency constraints
    })
    void testRegularBipartiteGraphs(int vertices, int degree) {
        Random random = new Random(Objects.hash(vertices, degree));
        int[] degrees = GraphUtils.generateRegularDegreeSequence(vertices / 2, degree);

        Graph g = GraphGenerator.generateBipartiteGraph(new AdjacencySetGraph(vertices), degrees, degrees, random);

        int[] matches = new int[g.size()];
        Arrays.fill(matches, -1);
        GabowAlgorithm algorithm = new GabowAlgorithm(g, matches);

        Set<Edge> matching = algorithm.getMaximumMatching();

        assertEquals(vertices / 2, matching.size(), "Regular bipartite graphs should have perfect matchings");
        assertTrue(GraphUtils.isValidMatching(g, matching), "Matching should be valid");
    }

    /**
     * Tests scaling and efficiency on general dense non-bipartite graphs.
     * Due to the degree sequences, these graphs almost surely have a perfect
     * matching.
     */
    @ParameterizedTest
    @CsvSource({
            "10, 4",
            "100, 5",
            "100, 10",
            "101, 6",
            "1000, 5",
            "1000, 101",
            "1001, 6",
            "10000, 5",
            "100000, 5"
    })
    void testRegularGeneralGraphs(int size, int degree) {
        Random random = new Random(Objects.hash(size, degree));
        int[] degrees = GraphUtils.generateRegularDegreeSequence(size, degree);

        // Run 5 iterations to ensure stability across random graph topologies
        for (int j = 0; j < 5; j++) {
            Random rd = new Random(random.nextLong());
            MutableGraph g = GraphGenerator.generateGraph(new AdjacencySetGraph(size), degrees, rd);

            int[] matches = new int[g.size()];
            Arrays.fill(matches, -1);
            GabowAlgorithm algorithm = new GabowAlgorithm(g, matches);

            Set<Edge> matching = algorithm.getMaximumMatching();

            assertEquals(g.size() / 2, matching.size(),
                    "Generated regular general graphs should hit near-perfect matchings");
            assertTrue(GraphUtils.isValidMatching(g, matching), "Matching should be valid");
        }
    }
}
