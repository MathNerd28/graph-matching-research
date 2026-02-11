package edu.rit.cs.graph_matching.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class GraphGeneratorTest {
    private static final long SEED = 0x8294757462947573L;

    @Test
    void testGenerateStarGraph() {
        MutableGraph starGraph = new SparseGraphImpl(10);
        GraphGenerator.generateStarGraph(starGraph);

        assertEquals(9, starGraph.getDegree(0));

        for (int i = 1; i < starGraph.size(); i++) {
            assertTrue(starGraph.hasEdge(0, i));
            assertEquals(1, starGraph.getDegree(i));
        }
    }

    @Test
    void testGenerateStarGraphWithMatching() {
        MutableGraph starGraphWithMatching = new SparseGraphImpl(8);
        GraphGenerator.generateStarGraphWithMatching(starGraphWithMatching, 3);

        int edgeCount = 0;
        for (int i = 0; i < starGraphWithMatching.size(); i++) {
            edgeCount += starGraphWithMatching.getDegree(i);
        }
        edgeCount /= 2;

        assertEquals(9, edgeCount);
    }

    @Test
    void testGenerateStarGraphWithMatchingError() {
        MutableGraph starGraphWithMatching = new SparseGraphImpl(8);
        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateStarGraphWithMatching(starGraphWithMatching, 0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateStarGraphWithMatching(starGraphWithMatching, 10);
        });
    }

    @Test
    void testGenerateRandomGraphZeroProb() {
        MutableGraph randomGraph = new SparseGraphImpl(5);
        GraphGenerator.generateRandomGraph(randomGraph, 0.0, new Random(SEED));

        int edgeCount = 0;
        for (int i = 0; i < randomGraph.size(); i++) {
            edgeCount += randomGraph.getDegree(i);
        }
        edgeCount /= 2;

        assertEquals(0, edgeCount);
    }

    @Test
    void testGenerateRandomGraphFullProb() {
        MutableGraph randomGraph = new SparseGraphImpl(5);
        GraphGenerator.generateRandomGraph(randomGraph, 1.0, new Random(SEED));

        int edgeCount = 0;
        for (int i = 0; i < randomGraph.size(); i++) {
            edgeCount += randomGraph.getDegree(i);
        }
        edgeCount /= 2;

        assertEquals(10, edgeCount);
    }

    @Test
    void testGenerateRandomGraphInvalidProb() {
        MutableGraph randomGraph = new SparseGraphImpl(5);

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateRandomGraph(randomGraph, -0.1, new Random(SEED));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateRandomGraph(randomGraph, 1.1, new Random(SEED));
        });
    }

    @Test
    void testGenerateGraph() {
        int vertices = 6;
        int degree = 3;

        MutableGraph graph = new SparseGraphImpl(vertices);
        Random random = new Random();
        int[] degreeSequence = GraphUtils.generateRegularDegreeSequence(vertices, degree);

        graph = GraphGenerator.generateGraph(graph, degreeSequence, random);

        int expectedEdges = 0;
        for (int d : degreeSequence) {
            expectedEdges += d;
        }
        expectedEdges /= 2;

        int actualEdges = 0;
        for (int i = 0; i < graph.size(); i++) {
            actualEdges += graph.getAllNeighbors(i)
                                .size();
        }
        actualEdges /= 2;

        assertEquals(expectedEdges, actualEdges);

        for (int i = 0; i < vertices; i++) {
            assertEquals(degreeSequence[i], graph.getAllNeighbors(i)
                                                 .size());
        }
    }

    @Test
    void testGenerateBipartiteGraph() {
        MutableGraph bipartiteGraph = new SparseGraphImpl(6);
        Random random = new Random();
        int[] leftDegreeSequence = GraphUtils.generateRegularDegreeSequence(3, 2);
        int[] rightDegreeSequence = GraphUtils.generateRegularDegreeSequence(3, 2);

        bipartiteGraph = GraphGenerator.generateBipartiteGraph(bipartiteGraph, leftDegreeSequence,
                rightDegreeSequence, random);

        int expectedEdges = 0;
        for (int d : leftDegreeSequence) {
            expectedEdges += d;
        }

        int actualEdges = 0;
        for (int i = 0; i < bipartiteGraph.size(); i++) {
            actualEdges += bipartiteGraph.getAllNeighbors(i)
                                         .size();
        }
        actualEdges /= 2;

        assertEquals(expectedEdges, actualEdges);

        for (int i = 0; i < 3; i++) {
            assertEquals(leftDegreeSequence[i], bipartiteGraph.getAllNeighbors(i)
                                                              .size());
        }

        for (int i = 3; i < 6; i++) {
            assertEquals(rightDegreeSequence[i - 3], bipartiteGraph.getAllNeighbors(i)
                                                                   .size());
        }
    }
}
