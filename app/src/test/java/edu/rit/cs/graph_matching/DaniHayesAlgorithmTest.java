package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DaniHayesAlgorithmTest {
  @ParameterizedTest
  @CsvSource({
    "10, 4",
    "100, 5",
    "101, 6",
    "1000, 5",
    "1001, 6",
    "10000, 5",
    "100000, 5",
  })
  void test(int size, int degree) {
    Random rd = new Random(0);
    for (int j = 0; j < 10; j++) {
      MutableGraph g = GraphGenerator.generateRegularGraph(new SparseGraphImpl(size), degree);
      GraphGenerator.mutateRegularGraph(g, size * degree);

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
