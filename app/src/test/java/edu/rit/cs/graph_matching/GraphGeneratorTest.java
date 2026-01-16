package edu.rit.cs.graph_matching;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class GraphGeneratorTest {
    /**
     * Generates a visual representation of the graph using Graphviz (Need to download for this to work).
     * @param graph
     * @param filename
     */
    public static void getVisual(Graph graph, String filename) {
        try (PrintWriter writer = new PrintWriter(new File(filename))) {
            writer.println("graph G {");

            for (int v = 0; v < graph.size(); v++) {
                writer.println("    " + v + ";");
            }

            for (int v = 0; v < graph.size(); v++) {
                for (int u : graph.getAllNeighbors(v)) {
                    if (v < u) {
                        writer.println("    " + v + " -- " + u + ";");
                    }
                }
            }

            writer.println("}");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            String[] command = {
                "dot",
                "-Tpng",
                filename,
                "-o",
                filename.replace(".dot", ".png")
            };
            Runtime.getRuntime().exec(command);
        } catch (Exception e) {

        }
    }

    public static void main(String[] args) {
        Graph starGraph = GraphGenerator.generateStarGraph(new SparseGraphImpl(100));
        getVisual(starGraph, "star.dot");

        Graph starGraphWithMatching = GraphGenerator.generateStarGraphWithMatching(new SparseGraphImpl(8), 3);
        getVisual(starGraphWithMatching, "star_with_matching.dot");
    }
}
