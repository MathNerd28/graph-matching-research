package edu.rit.cs.graph_matching;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

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
     * 
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
            degrees[i] = random.nextInt(averageDegree - variation, averageDegree + variation + 1);
        }
        return degrees;
    }

    /**
     * Determine whether a degree sequence is graphical, 
     * meaning that there exists at least one graph 
     * whose vertex degrees exactly match the sequence, 
     * using the Havel–Hakimi algorithm.
     * 
     * @param degrees
     *      the degree sequence
     * @return true iff the degree sequence is graphical
     */
    public static boolean isGraphical(int[] degrees) {
        int[] degreeCopy = degrees.clone();
        int[] auxArray = new int[degrees.length];

        Arrays.sort(degreeCopy);
        for (int i = 0, j = degrees.length - 1; i < j; i++, j--) {
            int temp = degreeCopy[i];
            degreeCopy[i] = degreeCopy[j];
            degreeCopy[j] = temp;
        }

        while (true) {
            if (degreeCopy[0] == 0) {
                return true;
            }

            int d = degreeCopy[0];
            if (d < 0 || d >= degrees.length) {
                return false;
            }

            for (int i = 1; i <= d; i++) {
                degreeCopy[i]--;
                if (degreeCopy[i] < 0) {
                    return false;
                }
            }
            degreeCopy[0] = 0;

            int i = 1;
            int j = d + 1;
            int k = 0;

            while (i <= d && j < degrees.length) {
                if (degreeCopy[i] >= degreeCopy[j]) {
                    auxArray[k] = degreeCopy[i];
                    k++;
                    i++;
                } else {
                    auxArray[k] = degreeCopy[j];
                    k++;
                    j++;
                }
            }
            while (i <= d) {
                auxArray[k] = degreeCopy[i];
                k++;
                i++;
            }
            while (j < degrees.length) {
                auxArray[k] = degreeCopy[j];
                k++;
                j++;
            }
            while (k < degrees.length) {
                auxArray[k] = 0;
                k++;
            }

            int[] temp = degreeCopy;
            degreeCopy = auxArray;
            auxArray = temp;
        }
    }
}
