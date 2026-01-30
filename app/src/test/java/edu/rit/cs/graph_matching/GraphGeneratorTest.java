package edu.rit.cs.graph_matching;

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
    void testGenerateRegularGraph() {
        MutableGraph regularGraph = new SparseGraphImpl(6);
        GraphGenerator.generateRegularGraph(regularGraph, 4);

        for (int i = 0; i < regularGraph.size(); i++) {
            assertEquals(4, regularGraph.getDegree(i));
        }
    }

    @Test
    void testGenerateRegularGraphInvalid() {
        MutableGraph regularGraph = new SparseGraphImpl(5);

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateRegularGraph(regularGraph, 6);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateRegularGraph(regularGraph, 3);
        });
    }

    @Test
    void testMutatedRegularGraph() {
        MutableGraph mutatedRegularGraph = new SparseGraphImpl(6);
        GraphGenerator.generateRegularGraph(mutatedRegularGraph, 4);

        GraphGenerator.mutateRegularGraph(mutatedRegularGraph, 50, new Random(SEED));

        for (int i = 0; i < mutatedRegularGraph.size(); i++) {
            assertEquals(4, mutatedRegularGraph.getDegree(i));
        }
    }

    @Test
    void testBipartiteGraph() {
        MutableGraph bipartiteGraph = new SparseGraphImpl(8);
        GraphGenerator.generateRegularBipartiteGraph(bipartiteGraph, 2);

        for (int u = 0; u < 4; u++) {
            assertEquals(2, bipartiteGraph.getDegree(u));
            for (int v : bipartiteGraph.getAllNeighbors(u)) {
                assertTrue(v >= 4);
            }
        }
    }

    @Test
    void testBipartiteGraphInvalid() {
        MutableGraph bipartiteGraph = new SparseGraphImpl(8);

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateRegularBipartiteGraph(bipartiteGraph, 5);
        });
    }

    @Test
    void testMutatedBipartiteGraph() {
        MutableGraph mutatedBipartiteGraph = new SparseGraphImpl(8);
        GraphGenerator.generateRegularBipartiteGraph(mutatedBipartiteGraph, 2);

        GraphGenerator.mutateBipartiteRegularGraph(mutatedBipartiteGraph, 100, new Random(SEED));

        for (int i = 0; i < 4; i++) {
            assertEquals(2, mutatedBipartiteGraph.getDegree(i));
            for (int v : mutatedBipartiteGraph.getAllNeighbors(i)) {
                assertTrue(v >= 4);
            }
        }
    }

    @Test
    void testIrregularizeGraph() {
        MutableGraph irregularGraph = new SparseGraphImpl(5);
        GraphGenerator.generateRegularGraph(irregularGraph, 4);

        GraphGenerator.irregularizeGraph(irregularGraph, 0.5, new Random(SEED));

        int edgeCount = 0;
        for (int i = 0; i < irregularGraph.size(); i++) {
            edgeCount += irregularGraph.getDegree(i);
        }
        edgeCount /= 2;

        assertTrue(edgeCount >= 0 && edgeCount <= 10);
    }

    @Test
    void testIrregularizeGraphZeroProb() {
        MutableGraph irregularGraph = new SparseGraphImpl(5);
        GraphGenerator.generateRegularGraph(irregularGraph, 2);

        int edgeCount = 0;
        for (int i = 0; i < irregularGraph.size(); i++) {
            edgeCount += irregularGraph.getDegree(i);
        }
        edgeCount /= 2;
        int edgesBefore = edgeCount;

        GraphGenerator.irregularizeGraph(irregularGraph, 0.0, new Random(SEED));
        edgeCount = 0;
        for (int i = 0; i < irregularGraph.size(); i++) {
            edgeCount += irregularGraph.getDegree(i);
        }
        edgeCount /= 2;
        assertEquals(edgesBefore, edgeCount);
    }

    @Test
    void testIrregularizeGraphInvalid() {
        MutableGraph irregularGraph = new SparseGraphImpl(5);
        GraphGenerator.generateRegularGraph(irregularGraph, 2);

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.irregularizeGraph(irregularGraph, -0.1, new Random(SEED));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.irregularizeGraph(irregularGraph, 1.1, new Random(SEED));
        });
    }

    @Test
    void testIrregularizeBipartiteGraph() {
        MutableGraph irregularBipartiteGraph = new SparseGraphImpl(8);
        GraphGenerator.generateRegularBipartiteGraph(irregularBipartiteGraph, 2);

        GraphGenerator.irregularizeBipartiteGraph(irregularBipartiteGraph, 0.5, new Random(SEED));

        for (int u = 0; u < 4; u++) {
            for (int v : irregularBipartiteGraph.getAllNeighbors(u)) {
                assertTrue(v >= 4);
            }
        }

        for (int u = 4; u < 8; u++) {
            for (int v : irregularBipartiteGraph.getAllNeighbors(u)) {
                assertTrue(v < 4);
            }
        }

        int edges = 0;
        for (int u = 0; u < 4; u++) {
            edges += irregularBipartiteGraph.getDegree(u);
        }
        assertTrue(edges >= 0 && edges <= 16);
    }

    @Test
    void testIrregularizeBipartiteGraphInvalid() {
        MutableGraph irregularBipartiteGraph = new SparseGraphImpl(8);
        GraphGenerator.generateRegularBipartiteGraph(irregularBipartiteGraph, 2);

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.irregularizeBipartiteGraph(irregularBipartiteGraph, -0.1,
                    new Random(SEED));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.irregularizeBipartiteGraph(irregularBipartiteGraph, 1.1,
                    new Random(SEED));
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
            actualEdges += graph.getAllNeighbors(i).size();
        }
        actualEdges /= 2;

        assertEquals(expectedEdges, actualEdges);

        for (int i = 0; i < vertices; i++) {
            assertEquals(degreeSequence[i], graph.getAllNeighbors(i).size());
        }
    }

    @Test
    void testGenerateGraphInvalidDegreeSequence() {
        MutableGraph graph = new SparseGraphImpl(6);
        Random random = new Random();
        int[] invalidDegreeSequence = {3, 3, 3, 3, 3, 2};

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateGraph(graph, invalidDegreeSequence, random);
        });
    }

    @Test
    void testGenerateBipartiteGraph() {
        MutableGraph bipartiteGraph = new SparseGraphImpl(6);
        Random random = new Random();
        int[] leftDegreeSequence = GraphUtils.generateRegularDegreeSequence(3, 2);
        int[] rightDegreeSequence = GraphUtils.generateRegularDegreeSequence(3, 2);

        bipartiteGraph = GraphGenerator.generateBipartiteGraph(bipartiteGraph, 3, 3, leftDegreeSequence, rightDegreeSequence, random);

        int expectedEdges = 0;
        for (int d : leftDegreeSequence) {
            expectedEdges += d;
        }

        int actualEdges = 0;
        for (int i = 0; i < bipartiteGraph.size(); i++) {
            actualEdges += bipartiteGraph.getAllNeighbors(i).size();
        }
        actualEdges /= 2;

        assertEquals(expectedEdges, actualEdges);

        for (int i = 0; i < 3; i++) {
            assertEquals(leftDegreeSequence[i], bipartiteGraph.getAllNeighbors(i).size());
        }

        for (int i = 3; i < 6; i++) {
            assertEquals(rightDegreeSequence[i - 3], bipartiteGraph.getAllNeighbors(i).size());
        }
    }

    @Test
    void testGenerateBipartiteGraphInvalidDegreeSequence() {
        MutableGraph bipartiteGraph = new SparseGraphImpl(6);
        Random random = new Random();
        int[] invalidLeftDegreeSequence = {2, 2, 2};
        int[] invalidRightDegreeSequence = {2, 2, 3};

        assertThrows(IllegalArgumentException.class, () -> {
            GraphGenerator.generateBipartiteGraph(bipartiteGraph, 3, 3, invalidLeftDegreeSequence, invalidRightDegreeSequence, random);
        });
    }
}
