package redacted.graph_matching.graph;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Queue;
import java.util.Set;
import java.util.random.RandomGenerator;

import redacted.graph_matching.graph.Graph.Edge;
import redacted.graph_matching.util.IntHashSet;

public final class GraphUtils {
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
    public static boolean isValidMatching(Graph graph, Collection<Edge> edges) {
        IntHashSet vertices = new IntHashSet(edges.size() * 2);
        for (Edge edge : edges) {
            if (!graph.hasEdge(edge)) {
                return false;
            }

            if (!vertices.add(edge.vertex1()) || !vertices.add(edge.vertex2())) {
                return false;
            }
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
     *     the number of vertices
     * @param degree
     *     the degree of each vertex
     * @return the degree sequence
     */
    public static int[] generateRegularDegreeSequence(int numVertices, int degree) {
        int[] degrees = new int[numVertices];
        Arrays.fill(degrees, degree);
        return degrees;
    }

    /**
     * Produces a uniform distribution of degrees over the range: [averageDegree
     * - variation, averageDegree + variation]
     *
     * @param numVertices
     *     the number of vertices
     * @param averageDegree
     *     the average degree
     * @param variation
     *     the variation from the average degree
     * @return the degree sequence
     */
    public static int[] generateUniformDegreeSequence(int numVertices, int averageDegree,
                                                      int variation, RandomGenerator random) {
        int[] degrees = new int[numVertices];
        int sum = 0;
        for (int i = 0; i < numVertices; i++) {
            degrees[i] = random.nextInt(averageDegree - variation, averageDegree + variation + 1);
            sum += degrees[i];
        }

        if ((sum % 2) == 1) {
            int i = random.nextInt(numVertices);
            if (degrees[i] == averageDegree + variation) {
                degrees[i]--;
            } else if (degrees[i] == averageDegree - variation) {
                degrees[i]++;
            } else {
                if (random.nextBoolean()) {
                    degrees[i]++;
                } else {
                    degrees[i]--;
                }
            }
        }

        return degrees;
    }

    /**
     * Determine whether degree sequences for a bipartite graph is graphical,
     * meaning that there exists at least one graph whose vertex degrees exactly
     * match the sequence, using the Havel–Hakimi algorithm.
     *
     * @param leftDegrees
     *     the left degree sequence
     * @param rightDegrees
     *     the right degree sequence
     * @return false iff the degree sequences are not graphical
     * @implNote This is a necessary condition for being bigraphical, but it is
     *     not sufficient.
     */
    public static boolean isGraphical(int[] leftDegrees, int[] rightDegrees) {
        int[] degrees = new int[leftDegrees.length + rightDegrees.length];
        System.arraycopy(leftDegrees, 0, degrees, 0, leftDegrees.length);
        System.arraycopy(rightDegrees, 0, degrees, leftDegrees.length, rightDegrees.length);
        return isGraphical(degrees);
    }

    /**
     * Determine whether a degree sequence is graphical, meaning that there
     * exists at least one graph whose vertex degrees exactly match the
     * sequence, using the Havel–Hakimi algorithm.
     *
     * @param degrees
     *     the degree sequence
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
            Boolean result = isGraphicalIteration(degreeCopy, auxArray);
            if (result != null) {
                return result;
            }

            int[] temp = degreeCopy;
            degreeCopy = auxArray;
            auxArray = temp;
        }
    }

    /**
     * The inner loop body of {@link #isGraphical(int[])}, broken out as a
     * dedicated function for performance.
     *
     * @param degrees
     *     the degrees array, in sorted order
     * @param auxArray
     *     the array to swap with degrees, with unspecified contents
     * @return true of the distribution is graphical, false if it isn't, null if
     *     more iterations are needed
     */
    private static Boolean isGraphicalIteration(int[] degrees, int[] auxArray) {
        if (degrees[0] == 0) {
            return true;
        }

        int d = degrees[0];
        if (d < 0 || d >= degrees.length) {
            return false;
        }

        for (int i = 1; i <= d; i++) {
            degrees[i]--;
            if (degrees[i] < 0) {
                return false;
            }
        }

        int i = 1;
        int j = d + 1;
        int k = 0;

        while (i <= d && j < degrees.length) {
            if (degrees[i] >= degrees[j]) {
                auxArray[k] = degrees[i];
                k++;
                i++;
            } else {
                auxArray[k] = degrees[j];
                k++;
                j++;
            }
        }

        if (i <= d) {
            System.arraycopy(degrees, i, auxArray, k, d - i + 1);
            k += d - i + 1;
        } else {
            System.arraycopy(degrees, j, auxArray, k, degrees.length - j);
            k += degrees.length - j;
        }

        if (k < auxArray.length) {
            Arrays.fill(auxArray, k, auxArray.length, 0);
        }

        return null;
    }

    public enum BipartiteColor {
        LEFT,
        RIGHT;
    }

    /**
     * An algorithm to perform 2-coloring on a graph to identify its bipartite
     * partitions. Ported from the C++ code on this page:
     * https://www.scipublications.com/journal/index.php/ijmebac/article/view/422
     *
     * @param graph
     *     The input graph to color.
     * @return A Map where keys are vertex IDs and values are partition IDs (0
     *     or 1).
     * @throws IllegalArgumentException
     *     if the graph is found to be non-bipartite.
     */
    public static BipartiteColor[] colorBipartite(Graph graph) {
        int n = graph.size();
        BipartiteColor[] colors = new BipartiteColor[n];

        // Transverse all connected components with a loop
        for (int i = 0; i < n; i++) {
            if (colors[i] == null) {
                bfsColor(graph, i, colors);
            }
        }

        return colors;
    }

    private static void bfsColor(Graph graph, int startVertex, BipartiteColor[] colors) {
        Queue<Integer> queue = new ArrayDeque<>();

        // Initialize the first node in this component with color LEFT
        colors[startVertex] = BipartiteColor.LEFT;
        queue.add(startVertex);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            BipartiteColor currentColor = colors[current];
            BipartiteColor neighborColor = (currentColor == BipartiteColor.LEFT)
                    ? BipartiteColor.RIGHT : BipartiteColor.LEFT;

            Set<Integer> neighbors = graph.getAllNeighbors(current);
            for (int neighbor : neighbors) {
                if (colors[neighbor] == null) {
                    // Assign the opposite color to the neighbor
                    colors[neighbor] = neighborColor;
                    queue.add(neighbor);
                } else if (colors[neighbor] == currentColor) {
                    // If neighbor has same color, it's not bipartite
                    throw new IllegalArgumentException(
                            "Graph contains an odd cycle and is not bipartite.");
                }
            }
        }
    }
}
