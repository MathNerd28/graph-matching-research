package edu.rit.cs.graph_matching;
import java.util.*;

public class GraphUtils {
    /**
     * Takes in a set of edges and determines whether they are considered matching
     * 
     * The function basically keeps track of all vertices seen so far, and if any vertex is
     * already used in another edge, it returns false. Otherwise, it returns true.
     * 
     * @param edges the set of edges
     * @return true iff the edges are considered matching
     */
    boolean isValidMatching(Set<Edge> edges) {
        Set<Integer> vertices = new HashSet<>();
        for (Edge edge : edges) {
            int v1 = edge.vertex1();
            int v2 = edge.vertex1();
            if (vertices.contains(v1) || vertices.contains(v2)) {
                return false;
            }
            vertices.add(v1);
            vertices.add(v2);
        }
        return true;
    }
}
