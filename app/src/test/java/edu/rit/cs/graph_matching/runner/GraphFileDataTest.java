package edu.rit.cs.graph_matching.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Random;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import edu.rit.cs.graph_matching.graph.AdjacencySetGraph;
import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.GraphGenerator;

class GraphFileDataTest {
    @ParameterizedTest
  // @formatter:off
  @CsvSource({
    "10, 0.0",
    "10, 1.0",
    "10, 0.5",
    "1000, 0.2",
    "10000, 0.1",
  })
  // @formatter:on
    void testReadWriteGraph(int size, double edgeProb, @TempDir Path tmpDir) throws IOException {
        Random rd = new Random(Objects.hash(size, edgeProb));
        Graph graph = GraphGenerator.generateRandomGraph(new AdjacencySetGraph(size), edgeProb, rd);

        GraphFileData writeData = new GraphFileData("testRandom",
                String.format("random n=%d p=%.2f", size, edgeProb), graph);

        File file = tmpDir.resolve("test.graph")
                          .toFile();
        writeData.writeToFile(file);

        GraphFileData readData = GraphFileData.readFile(file);
        assertEquals(writeData, readData);
    }
}
