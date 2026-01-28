package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DaniHayesAlgorithmTest {
  /** Runs are seeded such that the generated graphs are always the same */

  @ParameterizedTest
  // @formatter:off
  @CsvSource({
    "10, 4",
    "100, 5",
    "100, 50",
    "101, 6",
    "1000, 5",
    "1000, 501",
    "1001, 6",
    "10000, 5",
    "100000, 5",
  })
  // @formatter:on
  void regularTest(int size, int degree) {
    Random random = new Random(Objects.hash(size, degree));
    for (int j = 0; j < 10; j++) {
      Random rd = new Random(random.nextLong());

      MutableGraph g = GraphGenerator.generateRegularGraph(new SparseGraphImpl(size), degree);
      GraphGenerator.mutateRegularGraph(g, size * degree, rd);

      DaniHayesAlgorithm alg = new DaniHayesAlgorithm(new GraphStatistics(g), rd);
      Set<Edge> matching = alg.generatePerfectMatching();

      Set<Integer> vertices = matching.stream()
                                      .flatMapToInt(e -> IntStream.of(e.vertex1(), e.vertex2()))
                                      .boxed()
                                      .collect(Collectors.toCollection(TreeSet::new));
      assertEquals(g.size() / 2, matching.size());
      assertEquals(g.size() / 2 * 2, vertices.size());
    }
  }

  @ParameterizedTest
  // @formatter:off
  @CsvSource({
    "10, 4",
    "100, 5",
    "100, 50",
    "1000, 5",
    "1002, 501",
    "10000, 5",
    "100000, 5",
  })
  // @formatter:on
  void regularBipartiteTest(int size, int degree) {
    Random random = new Random(Objects.hash(size, degree));
    for (int j = 0; j < 10; j++) {
      Random rd = new Random(random.nextLong());

      MutableGraph g =
          GraphGenerator.generateRegularBipartiteGraph(new SparseGraphImpl(size), degree);
      GraphGenerator.mutateRegularGraph(g, size * degree, rd);

      DaniHayesAlgorithm alg = new DaniHayesAlgorithm(new GraphStatistics(g), rd);
      Set<Edge> matching = alg.generatePerfectMatching();

      Set<Integer> vertices = matching.stream()
                                      .flatMapToInt(e -> IntStream.of(e.vertex1(), e.vertex2()))
                                      .boxed()
                                      .collect(Collectors.toCollection(TreeSet::new));
      assertEquals(g.size() / 2, matching.size());
      assertEquals(g.size() / 2 * 2, vertices.size());
    }
  }
}
