package edu.rit.cs.graph_matching;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EdgeMatchingTest {
    @Test
    static void testEmptySet() {
        Set<Edge> edges = new HashSet<>();
        boolean result = new GraphUtils().isValidMatching(edges);
        assertTrue(result, "An empty set of edges should be a valid matching");
    }

    @Test
    static void testValidMatching() {
        Set<Edge> edges = new HashSet<>();
        edges.add(new Edge(0, 1));
        edges.add(new Edge(2, 3));
        edges.add(new Edge(4, 5));
        boolean result = new GraphUtils().isValidMatching(edges);
        assertTrue(result, "These edges should form a valid matching");
    }

    @Test
    static void testInValidMatching() {
        Set<Edge> edges = new HashSet<>();
        edges.add(new Edge(0, 1));
        edges.add(new Edge(1, 2));
        edges.add(new Edge(3, 4));
        boolean result = new GraphUtils().isValidMatching(edges);
        assertFalse(result, "These edges should not form a valid matching");
    }

    @Test
    static void testSingleEdge() {
        Set<Edge> edges = new HashSet<>();
        edges.add(new Edge(0, 1));
        boolean result = new GraphUtils().isValidMatching(edges);
        assertTrue(result, "A single edge should form a valid matching");
    }
}