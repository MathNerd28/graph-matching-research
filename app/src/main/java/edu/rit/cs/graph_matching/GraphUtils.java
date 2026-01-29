package edu.rit.cs.graph_matching;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;

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
}
