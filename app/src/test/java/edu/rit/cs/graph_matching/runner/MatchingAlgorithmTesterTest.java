package edu.rit.cs.graph_matching.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.rit.cs.graph_matching.algorithm.MatchingAlgorithm;
import edu.rit.cs.graph_matching.graph.AdjacencySetGraph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
import edu.rit.cs.graph_matching.graph.MutableGraph;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.AlgorithmInitialization;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.DataPoint;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.FailureDataPoint;

class MatchingAlgorithmTesterTest {
    @Test
    void defaultConstructorRejectsInitialMatching() {
        MutableGraph graph = twoEdgeGraph();

        assertThrows(IllegalStateException.class,
                () -> new MatchingAlgorithmTester((g, r) -> new AlgorithmInitialization(
                        new OneStepAlgorithm(Set.of(new Edge(0, 1)), new Edge(2, 3), 1)),
                        graph, null));
    }

    @Test
    void explicitConstructorAllowsInitialMatching() {
        MutableGraph graph = twoEdgeGraph();

        try (MatchingAlgorithmTester tester =
                new MatchingAlgorithmTester((g, r) -> new AlgorithmInitialization(
                        new OneStepAlgorithm(Set.of(new Edge(0, 1)), new Edge(2, 3), 1)),
                        graph, null, true)) {
            assertEquals(2, tester.run(2, 1, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        }
    }

    @Test
    void failedAugmentationDoesNotAdvanceMatchingSize() {
        MutableGraph graph = twoEdgeGraph();
        DataPoint[] lastPoint = new DataPoint[1];

        try (MatchingAlgorithmTester tester =
                new MatchingAlgorithmTester((g, r) -> new AlgorithmInitialization(
                        new OneStepAlgorithm(Set.of(), new Edge(0, 1), -1)), graph, null, true,
                        point -> lastPoint[0] = point)) {
            assertEquals(0, tester.run(1, 1, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        }

        FailureDataPoint failure = (FailureDataPoint) lastPoint[0];
        assertEquals(0, failure.matchingSize());
    }

    private static MutableGraph twoEdgeGraph() {
        MutableGraph graph = new AdjacencySetGraph(4);
        graph.addEdge(0, 1);
        graph.addEdge(2, 3);
        return graph;
    }

    private static final class OneStepAlgorithm implements MatchingAlgorithm {
        private final Set<Edge> matching;
        private final Edge edgeToAdd;
        private final int pathLength;
        private boolean augmented;

        OneStepAlgorithm(Set<Edge> initialMatching, Edge edgeToAdd, int pathLength) {
            this.matching = new LinkedHashSet<>(initialMatching);
            this.edgeToAdd = edgeToAdd;
            this.pathLength = pathLength;
        }

        @Override
        public int augment() {
            if (pathLength > 0) {
                matching.add(edgeToAdd);
            }
            augmented = true;
            return pathLength;
        }

        @Override
        public Set<Edge> getCurrentMatching() {
            return matching;
        }

        @Override
        public boolean isFinished() {
            return augmented;
        }
    }
}
