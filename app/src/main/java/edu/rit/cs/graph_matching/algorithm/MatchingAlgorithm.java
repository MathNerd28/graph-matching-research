package edu.rit.cs.graph_matching.algorithm;

import java.util.Set;

import edu.rit.cs.graph_matching.graph.Graph.Edge;

/**
 * A common interface for running and testing matching algorithms.
 */
public interface MatchingAlgorithm {
    /**
     * Search for a single augmenting path and augment it, increasing the size
     * of the matching by one.
     * <p>
     * Implementations should be cooperatively interruptible; that is, they
     * should periodically check {@link Thread#interrupted()}, and if so return
     * swiftly. This method may be called again after an interruption;
     * implementations should account for this.
     *
     * @return the length of the path that was augmented, or -1 if unsuccessful
     */
    int augment();

    /**
     * Get the current matching produced by the algorithm. The size of the
     * matching should be exactly equal to the number of times
     * {@link #augment()} returned {@code true}. This must not change the state
     * of the algorithm.
     *
     * @return the current matching produced by the algorithm
     */
    Set<Edge> getCurrentMatching();

    /**
     * Check if the algorithm has run to completion, i.e. it knows it cannot
     * improve the matching any further.
     *
     * @return true if the algorithm cannot improve the current matching
     */
    boolean isFinished();

    /**
     * Compute the maximum matching on the input graph.
     *
     * @return the edges in the maximum matching
     */
    default Set<Edge> getMaximumMatching() {
        while (!isFinished() && !Thread.interrupted()) {
            augment();
        }
        return getCurrentMatching();
    }
}
