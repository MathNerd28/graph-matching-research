package edu.rit.cs.graph_matching;

import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EdgeMatchingTest {
    @Test
    void testEmptySet() {
        Set<Edge> edges = new HashSet<>();
        boolean result = GraphUtils.isValidMatching(edges);
        assertTrue(result, "An empty set of edges should be a valid matching");
    }

    @Test
    void testValidMatching() {
        Set<Edge> edges = new HashSet<>();
        edges.add(new Edge(0, 1));
        edges.add(new Edge(2, 3));
        edges.add(new Edge(4, 5));
        boolean result = GraphUtils.isValidMatching(edges);
        assertTrue(result, "These edges should form a valid matching");
    }

    @Test
    void testInValidMatching() {
        Set<Edge> edges = new HashSet<>();
        edges.add(new Edge(0, 1));
        edges.add(new Edge(1, 2));
        edges.add(new Edge(3, 4));
        boolean result = GraphUtils.isValidMatching(edges);
        assertFalse(result, "These edges should not form a valid matching");
    }

    @Test
    void testSingleEdge() {
        Set<Edge> edges = new HashSet<>();
        edges.add(new Edge(0, 1));
        boolean result = GraphUtils.isValidMatching(edges);
        assertTrue(result, "A single edge should form a valid matching");
    }
}
