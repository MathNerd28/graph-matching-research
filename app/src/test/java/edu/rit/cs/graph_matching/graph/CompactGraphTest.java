package edu.rit.cs.graph_matching.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CompactGraphTest {
    /**
     * Verifies that the CSR offsets expose exactly the selected undirected edges,
     * including empty adjacency slices for isolated vertices.
     */
    @Test
    void selectedEdgesDefineGraphQueries() {
        int[] edgeU = { 3, 0, 1, 4, 0 };
        int[] edgeV = { 1, 2, 2, 5, 4 };
        boolean[] selected = { true, true, false, true, true };

        Graph graph = CompactGraph.fromSelectedEdges(7, edgeU, edgeV, selected);

        assertEquals(7, graph.size());
        assertEquals(Set.of(2, 4), graph.getAllNeighbors(0));
        assertEquals(Set.of(3), graph.getAllNeighbors(1));
        assertEquals(Set.of(0), graph.getAllNeighbors(2));
        assertEquals(Set.of(1), graph.getAllNeighbors(3));
        assertEquals(Set.of(0, 5), graph.getAllNeighbors(4));
        assertEquals(Set.of(4), graph.getAllNeighbors(5));
        assertEquals(Set.of(), graph.getAllNeighbors(6));

        assertEquals(2, graph.getDegree(0));
        assertEquals(0, graph.getDegree(6));
        assertTrue(graph.hasEdge(0, 4));
        assertTrue(graph.hasEdge(4, 0));
        assertFalse(graph.hasEdge(1, 2), "Unselected edges must not enter the graph");
    }

    /**
     * Verifies random access, neighbor-set membership, iterator exhaustion, and
     * invalid graph indices at the Graph interface boundary.
     */
    @Test
    void graphInterfaceEdgeCases() {
        int[] edgeU = { 0, 0 };
        int[] edgeV = { 1, 2 };
        boolean[] selected = { true, true };
        Graph graph = CompactGraph.fromSelectedEdges(4, edgeU, edgeV, selected);
        Random random = new Random(0);

        for (int i = 0; i < 10; i++) {
            assertTrue(graph.hasEdge(0, graph.getRandomNeighbor(0, random)));
        }
        assertEquals(-1, graph.getRandomNeighbor(3, random));

        Set<Integer> neighbors = graph.getAllNeighbors(0);
        assertFalse(neighbors.contains(-1));
        assertFalse(neighbors.contains(graph.size()));
        assertFalse(neighbors.contains("1"));

        Iterator<Integer> iterator = graph.getAllNeighbors(3).iterator();
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);

        assertThrows(IndexOutOfBoundsException.class, () -> graph.getDegree(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> graph.hasEdge(0, graph.size()));
        assertThrows(IndexOutOfBoundsException.class, () -> graph.getAllNeighbors(graph.size()));
    }
}
