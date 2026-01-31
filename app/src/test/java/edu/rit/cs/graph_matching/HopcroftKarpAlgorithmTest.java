package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HopcroftKarpAlgorithmTest {
    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000, 10000 })
    void regularBipartiteGraphs(int vertices) {
        Graph g = GraphGenerator.generateRegularBipartiteGraph(new SparseGraphImpl(vertices), 3);
        HopcroftKarpAlgorithm algorithm = new HopcroftKarpAlgorithm(g);
        int matchingSize = algorithm.getMaximumMatching();
        assertEquals(vertices / 2, matchingSize,
                "Regular bipartite graphs should have perfect matchings");
    }

}