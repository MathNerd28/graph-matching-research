package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GraphUtilsTest {
    @Test
    void testEmptySet() {
        Set<Edge> edges = new HashSet<>();
        Graph g = new SparseGraphImpl(1);
        boolean result = GraphUtils.isValidMatching(g, edges);
        assertTrue(result, "An empty set of edges should be a valid matching");
    }

    @Test
    void testValidMatching() {
        Set<Edge> edges = new HashSet<>();
        edges.add(new Edge(0, 1));
        edges.add(new Edge(2, 3));
        edges.add(new Edge(4, 5));

        MutableGraph g = new SparseGraphImpl(6);
        for (Edge e : edges) {
            g.addEdge(e);
        }

        boolean result = GraphUtils.isValidMatching(g, edges);
        assertTrue(result, "These edges should form a valid matching");
    }

    @Test
    void testInValidMatching() {
        Set<Edge> edges = new HashSet<>();
        edges.add(new Edge(0, 1));
        edges.add(new Edge(1, 2));
        edges.add(new Edge(3, 4));

        MutableGraph g = new SparseGraphImpl(5);
        for (Edge e : edges) {
            g.addEdge(e);
        }

        boolean result = GraphUtils.isValidMatching(g, edges);
        assertFalse(result, "These edges should not form a valid matching");
    }

    @Test
    void testSingleEdge() {
        Set<Edge> edges = new HashSet<>();
        edges.add(new Edge(0, 1));

        MutableGraph g = new SparseGraphImpl(2);
        for (Edge e : edges) {
            g.addEdge(e);
        }

        boolean result = GraphUtils.isValidMatching(g, edges);
        assertTrue(result, "A single edge should form a valid matching");
    }

    @ParameterizedTest
    // @formatter:off
    @CsvSource({
        "10, 4, 0",
        "10, 4, 2",
        "50, 10, 3",
        "100, 5, 1",
        "100, 20, 5",
        "1000, 50, 10",
        "10000, 100, 20",
    })
    // @formatter:on
    void generateUniformDegreeSequenceTest(int size, int degree, int variation) {
        Random random = new Random(Objects.hash(size, degree, variation));
        int[] degrees = GraphUtils.generateUniformDegreeSequence(size, degree, variation, random);

        assertTrue(GraphUtils.isGraphical(degrees));
    }

    @Test
    void testAllZerosHavelHakimi() {
        int[] degrees = {0, 0, 0, 0};
        assertTrue(GraphUtils.isGraphical(degrees), "All zeros is always graphical");
    }

    @Test
    void testSimpleGraphicalHavelHakimi() {
        int[] degrees = {3, 3, 2, 2, 2};
        assertTrue(GraphUtils.isGraphical(degrees), "Valid sequence should be graphical");
    }

    @Test
    void testNonGraphicalHavelHakimi() {
        int[] degrees = {4, 3, 2, 1};
        assertFalse(GraphUtils.isGraphical(degrees), "Invalid sequence should not be graphical");
    }

    @Test
    void testNegativeDegreeHavelHakimi() {
        int[] degrees = {3, -1, 2};
        assertFalse(GraphUtils.isGraphical(degrees), "Negative degree sequence is invalid");
    }

    @Test
    void testTooLargeDegreeHavelHakimi() {
        int[] degrees = {5, 1, 1, 1};
        assertFalse(GraphUtils.isGraphical(degrees), "Degree larger than n-1 is invalid");
    }
}
