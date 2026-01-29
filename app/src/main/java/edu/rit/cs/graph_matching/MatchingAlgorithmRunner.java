package edu.rit.cs.graph_matching;

import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * A common interface for running and testing matching algorithms. Algorithms
 */
public interface MatchingAlgorithmRunner {
  /**
   * Initialize the runner with a new input graph and random generator.
   *
   * @param g
   *   the input graph
   * @param random
   *   the random generator
   * @return true if initialization was successful
   */
  boolean initialize(Graph g, RandomGenerator random);

  /**
   * Asks the algorithm to search for a single augmenting path, and to augment
   * it, increasing the size of the matching by one.
   * <p>
   * Implementations should be cooperatively interruptible; that is, they should
   * periodically check {@link Thread#interrupted()}, and if so return swiftly.
   * This method may be called again after an interruption; implementations
   * should account for this.
   */
  void augmentOnce();

  /**
   * Get the current matching produced by the algorithm. The size of the
   * matching should be exactly equal to the number of times
   * {@link #augmentOnce()} returned {@code true}. This must not change the
   * state of the algorithm.
   *
   * @return the current matching produced by the algorithm
   */
  Set<Edge> getCurrentMatching();
}
