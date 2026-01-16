package edu.rit.cs.graph_matching;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class GraphGenerator {
    Random random = new Random();

    /**
     * Generates a star graph with the given number of edges.
     * Matchings: leaves + 1
     *  - Empty Set
     *  - Single Edge
     * Maximum Matchings: 1
     * 
     * @param leaves
     * @return a star graph
     */
    public Graph generateStarGraph(int leaves) {
        SparseGraphImpl starGraph = new SparseGraphImpl(leaves + 1);

        int centerVertex = random.nextInt(leaves + 1);
        for (int i = 0; i <= leaves; i++) {
            if (i == centerVertex) {
                continue;
            }
            starGraph.addEdge(centerVertex, i);
        }

        return starGraph;
    }

    /**
     * Generates an edited star graph to have the specific maxMatching.
     * 
     * @param edges
     * @param maxMatching
     * @return an edited star graph
     */
    public Graph generateStarGraphWithMatching(int leaves, int maxMatching) {
        if (maxMatching < 1 || maxMatching > (leaves / 2 + 1)) {
            throw new IllegalArgumentException("Invalid maxMatching for given leaves");
        }

        SparseGraphImpl starGraphWithMatching = (SparseGraphImpl) generateStarGraph(leaves);
        int centerVertex = -1;
        for (int i = 0; i <= leaves; i++) {
            if (starGraphWithMatching.getAllNeighbors(i).size() == leaves) {
                centerVertex = i;
                break;
            }
        }

        Set<Integer> leavesSet = starGraphWithMatching.getAllNeighbors(centerVertex);
        List<Integer> leavesArray = new ArrayList<>(leavesSet);
        Collections.shuffle(leavesArray);

        int edgesToAdd = maxMatching - 1;
        for (int i = 0; i < edgesToAdd * 2; i += 2) {
            if (i + 1 < leavesArray.size()) {
                starGraphWithMatching.addEdge(leavesArray.get(i), leavesArray.get(i + 1));
            }
        }

        return starGraphWithMatching;
    }

    public Graph generateRandomGraph(int vertices) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Graph generateRegularGraph(int vertices, int degree) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public Graph generateBipartiteGraph(int leftVertices, int rightVertices) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    
}
