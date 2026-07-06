package edu.rit.cs.graph_matching.algorithm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import edu.rit.cs.graph_matching.graph.AdjacencySetGraph;
import edu.rit.cs.graph_matching.graph.Graph.Edge;
import edu.rit.cs.graph_matching.graph.GraphGenerator;
import edu.rit.cs.graph_matching.graph.GraphUtils;
import edu.rit.cs.graph_matching.graph.MutableGraph;

class DaniHayesAlgorithmTest {
    /** Runs are seeded such that the generated graphs are always the same */
    private static final long SEED = 0xD0A17A9E5L;

    @Test
    void seededConstructorUsesInitialMatching() {
        MutableGraph graph = new AdjacencySetGraph(4);
        graph.addEdge(0, 1);
        graph.addEdge(2, 3);

        Set<Edge> initialMatching = Set.of(new Edge(0, 1));
        DaniHayesAlgorithm alg = new DaniHayesAlgorithm(graph, new Random(SEED), initialMatching);

        assertEquals(initialMatching, alg.getCurrentMatching());
    }

    @Test
    void seededConstructorRejectsInvalidMatching() {
        MutableGraph graph = new AdjacencySetGraph(4);
        graph.addEdge(0, 1);
        graph.addEdge(1, 2);

        assertThrows(IllegalArgumentException.class,
                () -> new DaniHayesAlgorithm(graph, new Random(SEED),
                        Set.of(new Edge(0, 1), new Edge(1, 2))));
        assertThrows(IllegalArgumentException.class,
                () -> new DaniHayesAlgorithm(graph, new Random(SEED),
                        Set.of(new Edge(2, 3))));
    }

    @ParameterizedTest
    // @formatter:off
    @CsvSource({
        "10, 4",
        "100, 5",
        "100, 10",
        "101, 6",
        "1000, 5",
        "1000, 101",
        "1001, 6",
        "10000, 5",
        "100000, 5",
    })
    // @formatter:on
    void regularTest(int size, int degree) {
        Random random = new Random(Objects.hash(size, degree));
        int[] degrees = GraphUtils.generateRegularDegreeSequence(size, degree);

        for (int j = 0; j < 10; j++) {
            Random rd = new Random(random.nextLong());

            MutableGraph g = GraphGenerator.generateGraph(new AdjacencySetGraph(size), degrees, rd);

            DaniHayesAlgorithm alg = new DaniHayesAlgorithm(g, rd);
            Set<Edge> matching = alg.getMaximumMatching();

            Set<Integer> vertices = matching.stream()
                                            .flatMapToInt(
                                                    e -> IntStream.of(e.vertex1(), e.vertex2()))
                                            .boxed()
                                            .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(g.size() / 2, matching.size());
            assertEquals(g.size() / 2 * 2, vertices.size());
        }
    }

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
        int[] leftDegrees = GraphUtils.generateRegularDegreeSequence(size / 2, degree);
        int[] rightDegrees = GraphUtils.generateRegularDegreeSequence(size / 2, degree);

        for (int j = 0; j < 10; j++) {
            Random rd = new Random(random.nextLong());

            MutableGraph g = GraphGenerator.generateBipartiteGraph(new AdjacencySetGraph(size),
                    leftDegrees, rightDegrees, rd);

            DaniHayesAlgorithm alg = new DaniHayesAlgorithm(g, rd);
            Set<Edge> matching = alg.getMaximumMatching();

            Set<Integer> vertices = matching.stream()
                                            .flatMapToInt(
                                                    e -> IntStream.of(e.vertex1(), e.vertex2()))
                                            .boxed()
                                            .collect(Collectors.toCollection(TreeSet::new));
            assertEquals(g.size() / 2, matching.size());
            assertEquals(g.size() / 2 * 2, vertices.size());
        }
    }
}
