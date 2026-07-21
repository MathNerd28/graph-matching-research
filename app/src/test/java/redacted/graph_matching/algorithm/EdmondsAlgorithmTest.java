package redacted.graph_matching.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import redacted.graph_matching.graph.AdjacencySetGraph;
import redacted.graph_matching.graph.Graph;
import redacted.graph_matching.graph.Graph.Edge;
import redacted.graph_matching.graph.GraphGenerator;

class EdmondsAlgorithmTest {
    @ParameterizedTest
    @ValueSource(ints = { 2, 5, 10, 100, 1000, 10000 })
    void starGraphs(int vertices) {
        Graph g = GraphGenerator.generateStarGraph(new AdjacencySetGraph(vertices));
        EdmondsAlgorithm algorithm = new EdmondsAlgorithm(g);
        Set<Edge> matching = algorithm.getMaximumMatching();
        assertEquals(1, matching.size(), "Star graphs always have a maximum matching of 1 edge");
    }

    @ParameterizedTest
    @CsvSource({ "5, 2", "10, 2", "10, 3", "10, 4", "10, 5", "100, 25", "100, 49", "1000, 400",
                 "5000, 1000", })
    void starWheelHybridGraphs(int vertices, int matchingSize) {
        Graph g = GraphGenerator.generateStarGraphWithMatching(new AdjacencySetGraph(vertices),
                matchingSize);
        EdmondsAlgorithm algorithm = new EdmondsAlgorithm(g);
        Set<Edge> matching = algorithm.getMaximumMatching();
        assertEquals(matchingSize, matching.size(),
                "Star-wheel hybrid graphs have a fixed maximum matching size");
    }
}
