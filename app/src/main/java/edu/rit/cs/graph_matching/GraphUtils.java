package edu.rit.cs.graph_matching;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public class GraphUtils {
    private GraphUtils() {}

    /**
     * Takes in a set of edges and determines whether they are considered
     * matching The function basically keeps track of all vertices seen so far,
     * and if any vertex is already used in another edge, it returns false.
     * Otherwise, it returns true.
     *
     * @param edges
     *     the set of edges
     * @return true iff the edges are considered matching
     */
    public static boolean isValidMatching(Set<Edge> edges) {
        Set<Integer> vertices = new HashSet<>();
        for (Edge edge : edges) {
            int v1 = edge.vertex1();
            int v2 = edge.vertex2();
            if (vertices.contains(v1) || vertices.contains(v2)) {
                return false;
            }
            vertices.add(v1);
            vertices.add(v2);
        }
        return true;
    }

    /**
     * Generate a Graphviz "dot" file representation of this graph. "Dot" files
     * can subsequently be visualized as images or interactive previews using
     * compatible tooling.
     *
     * @param graph
     *     the graph to convert
     * @param outputFile
     *     the file to write the data
     */
    public static void generateDotFile(Graph graph, File outputFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("graph G {");

            for (int v = 0; v < graph.size(); v++) {
                writer.println(v + ";");
            }

            for (int v = 0; v < graph.size(); v++) {
                for (int u : graph.getAllNeighbors(v)) {
                    if (v < u) {
                        writer.println(v + " -- " + u + ";");
                    }
                }
            }

            writer.println("}");
        }
    }

    /**
     * Generate a regular degree sequence.
     * 
     * @param numVertices
     *      the number of vertices
     * @param degree
     *      the degree of each vertex
     * @return the degree sequence
     */
    public static int[] generateRegularDegreeSequence(int numVertices, int degree) {
        int[] degrees = new int[numVertices];
        for (int i = 0; i < numVertices; i++) {
            degrees[i] = degree;
        }
        return degrees;
    }

    /**
     * Generate a somewhat regular degree sequence.
     * @param numVertices
     *      the number of vertices
     * @param averageDegree
     *      the average degree
     * @param variation
     *      the variation from the average degree
     * @return the degree sequence
     */
    public static int[] generateSomewhatRegularDegreeSequence(int numVertices, int averageDegree, int variation, Random random) {
        int[] degrees = new int[numVertices];
        for (int i = 0; i < numVertices; i++) {
            degrees[i] = averageDegree - variation + random.nextInt(2 * variation + 1);
        }
        return degrees;
    }
}
