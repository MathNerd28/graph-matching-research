package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GoelKapralovKhannaTest {

    /*
     * Tests the GKK algorithm on valid d-regular bipartite graphs.
     * Note: GKK specifically requires the graph to be Bipartite and Regular.
     */
    @ParameterizedTest
    @CsvSource({
            "10, 4", // 5 vs 5, deg 4
            "100, 5", // 50 vs 50, deg 5
            "100, 10", // 50 vs 50, deg 10
            "1000, 5", // 500 vs 500, deg 5
            "1002, 101", // 501 vs 501, deg 101
            "2000, 3", // 1000 vs 1000, deg 3 (Sparse)
    })
    void regularBipartiteTest(int size, int degree) {
        Random random = new Random(Objects.hash(size, degree));

        // Generate degree sequences for a d-regular bipartite graph
        // size must be even for a perfect matching to exist in a regular graph
        int partitionSize = size / 2;
        int[] leftDegrees = GraphUtils.generateRegularDegreeSequence(partitionSize, degree);
        int[] rightDegrees = GraphUtils.generateRegularDegreeSequence(partitionSize, degree);

        // Run multiple iterations to ensure stability
        for (int j = 0; j < 5; j++) {
            Random rd = new Random(random.nextLong());

            MutableGraph g = GraphGenerator.generateBipartiteGraph(new SparseGraphImpl(size),
                    leftDegrees, rightDegrees, rd);

            GoelKapralovKhanna alg = new GoelKapralovKhanna(g, rd);

            Set<Edge> matching = alg.getMaximumMatching();

            assertEquals(partitionSize, matching.size(),
                    "Matching size should be equal to partition size (perfect matching)");

            Set<Integer> vertices = matching.stream()
                    .flatMapToInt(e -> IntStream.of(e.vertex1(), e.vertex2()))
                    .boxed()
                    .collect(Collectors.toCollection(TreeSet::new));

            assertEquals(size, vertices.size(), "All vertices must be matched");

            assertTrue(GraphUtils.isValidMatching(g, matching), "Matching must be valid");
        }
    }
}