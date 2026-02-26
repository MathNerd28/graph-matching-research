package edu.rit.cs.graph_matching.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import edu.rit.cs.graph_matching.graph.AdjacencySetGraph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
import edu.rit.cs.graph_matching.graph.GraphGenerator;
import edu.rit.cs.graph_matching.graph.GraphUtils;
import edu.rit.cs.graph_matching.graph.MutableGraph;

/**
 * Parameterized test that validates GKK on generated d-regular bipartite
 * graphs.
 * <p>
 * For each (size, degree) CSV-pair the test:
 * <ul>
 * <li>constructs a d-regular bipartite graph with partitionSize = size /
 * 2;</li>
 * <li>performs 5 deterministic randomized iterations (seeded from (size,
 * degree));</li>
 * <li>asserts the returned matching has cardinality equal to partitionSize
 * (perfect);</li>
 * <li>asserts the matching is valid (no shared endpoints).</li>
 * </ul>
 * </p>
 *
 * @param size
 *     total number of vertices in the generated graph (must be even)
 * @param degree
 *     degree for each vertex in the regular bipartite graph (0 &le; degree &le;
 *     partitionSize)
 * @throws AssertionError
 *     if the algorithm does not produce a perfect or valid matching
 * @see GraphUtils#generateRegularDegreeSequence(int,int)
 * @see GraphGenerator#generateBipartiteGraph
 * @see GraphUtils#isValidMatching
 */
class GoelKapralovKhannaTest {
    /**
     * Tests the GKK algorithm on valid d-regular bipartite graphs. Note: GKK
     * specifically requires the graph to be Bipartite and Regular.
     */
    @ParameterizedTest
    // @formatter:off
    @CsvSource({
        "10, 4",
        "100, 5",
        "100, 10",
        "1000, 5",
        "1002, 101",
        "10000, 5",
        "100000, 5",
    })
    // @formatter:on
    void regularBipartiteTest(int size, int degree) {
        Random random = new Random(Objects.hash(size, degree));

        // Generate degree sequences for a d-regular bipartite graph
        // size must be even for a perfect matching to exist in a regular graph
        int partitionSize = size / 2;
        int[] degrees = GraphUtils.generateRegularDegreeSequence(partitionSize, degree);

        // Run multiple iterations to ensure stability
        for (int j = 0; j < 5; j++) {
            Random rd = new Random(random.nextLong());

            MutableGraph g = GraphGenerator.generateBipartiteGraph(new AdjacencySetGraph(size),
                    degrees, degrees, rd);

            GoelKapralovKhanna alg = new GoelKapralovKhanna(g, rd);

            Set<Edge> matching = alg.getMaximumMatching();

            assertEquals(partitionSize, matching.size(),
                    "Matching size should be equal to partition size (perfect matching)");

            assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
        }
    }
}
