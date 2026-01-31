package edu.rit.cs.graph_matching;

import java.util.Map;
import java.util.Arrays;

public class BipartiteGraph {
    private int leftSize;
    private int rightSize;
    public IntHashSet left;
    public IntHashSet right;
    public int[] leftMatch;
    public int[] rightMatch; 


    public BipartiteGraph(Graph graph) {
        Map<Integer, Integer> coloring = new BipartiteColorer().colorBipartite(graph);
        leftSize = 0;
        rightSize = 0;
        left = new IntHashSet();
        right = new IntHashSet();
        leftMatch = new int[graph.size()];
        Arrays.fill(leftMatch, -1);
        rightMatch = new int[graph.size()];
        Arrays.fill(rightMatch, -1);
        for (int node: coloring.keySet()) {
            if (coloring.get(node) == 0) {
                leftSize++;
                left.add(node);
            } else {
                rightSize++;
                right.add(node);
            }
        }
    }

    public int getMatch(int vertex) {
        if (left.contains(vertex)) {
            return leftMatch[vertex];
        } else if (right.contains(vertex)) {
            return rightMatch[vertex];
        } else {
            return -1;
            // throw new IllegalArgumentException("Vertex " + vertex + " is not in the bipartite graph.");
        }
    }

    public int getLeftSize() {
        return leftSize;
    }

    public int getRightSize() {
        return rightSize;
    }
}