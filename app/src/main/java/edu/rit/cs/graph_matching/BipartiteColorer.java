package edu.rit.cs.graph_matching;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * An algorithm to perform 2-coloring on a graph to identify its 
 * bipartite partitions.
 * Coverted from the C++ code on this page: https://www.scipublications.com/journal/index.php/ijmebac/article/view/422
 */
public class BipartiteColorer {

    /**
     * Converts an unpartitioned Bipartite Graph into an explicit mapping of
     * vertices to their respective partitions (0 or 1).
     *
     * @param graph The input graph to color.
     * @return A Map where keys are vertex IDs and values are partition IDs (0 or 1).
     * @throws IllegalArgumentException if the graph is found to be non-bipartite.
     */
    public Map<Integer, Integer> colorBipartite(Graph graph) {
        int n = graph.size();
        Map<Integer, Integer> colors = new HashMap<>();
        
        // Transverse all connected compoenents with a loop
        for (int i = 0; i < n; i++) {
            if (!colors.containsKey(i)) {
                bfsColor(graph, i, colors);
            }
        }
        
        return colors;
    }

    private void bfsColor(Graph graph, int startVertex, Map<Integer, Integer> colors) {
        Queue<Integer> queue = new ArrayDeque<>();
        
        // Initialize the first node in this component with color 0
        colors.put(startVertex, 0);
        queue.add(startVertex);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int currentColor = colors.get(current);
            int neighborColor = 1 - currentColor; // Toggle between 0 and 1

            Set<Integer> neighbors = graph.getAllNeighbors(current);
            for (int neighbor : neighbors) {
                if (!colors.containsKey(neighbor)) {
                    // Assign the opposite color to the neighbor
                    colors.put(neighbor, neighborColor);
                    queue.add(neighbor);
                } else if (colors.get(neighbor) == currentColor) {
                    // If neighbor has same color, it's not bipartite
                    throw new IllegalArgumentException("Graph contains an odd cycle and is not bipartite.");
                }
            }
        }
    }
}