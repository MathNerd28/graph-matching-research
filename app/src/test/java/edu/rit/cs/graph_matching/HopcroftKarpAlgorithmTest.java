package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HopcroftKarpAlgorithmTest {
    @ParameterizedTest
    @CsvSource({
        "10, 4",
        "20, 4",
        "50, 5",
        "100, 5",
        "100, 10",
        "100, 6",
        "1000, 5",
        "1000, 101",
        "1000, 6",
        "10000, 5",
        "100000, 5",
    })
    void regularBipartiteGraphs(int vertices, int degree) {
        Random random = new Random(Objects.hash(vertices, degree));
        int[] degrees = GraphUtils.generateRegularDegreeSequence(vertices / 2, degree);
        Graph g = GraphGenerator.generateBipartiteGraph(new SparseGraphImpl(vertices), degrees, degrees, random);
        HopcroftKarpAlgorithm algorithm = new HopcroftKarpAlgorithm(g);
        Set<Edge> matching = algorithm.getMaximumMatching();
        assertEquals(vertices / 2, matching.size(),
                "Regular bipartite graphs should have perfect matchings");
        assertEquals(GraphUtils.isValidMatching(matching), true,
                "Matching should be valid");
    }

}
