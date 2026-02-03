package edu.rit.cs.graph_matching;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(subcommands = { Main.GenerateGraph.class },
         mixinStandardHelpOptions = true)
public class Main {
  @Command(name = "generate",
           description = "Generate a graph using a particular method.",
           mixinStandardHelpOptions = true)
  static class GenerateGraph {
    static class GenerationParams {
      @Parameters(description = "Output graph files")
      private File[] outputFiles;

      @Option(names = { "--name" },
              description = "Name of the graph")
      private String graphName = null;

      @Option(names = { "-n", "--size" },
              required = true,
              description = "Number of vertices in the graph")
      private int vertices;
    }

    @Command(name = "random",
             description = "Random graph: every edge is created with the same probability",
             mixinStandardHelpOptions = true)
    int generateRandomGraph(@Mixin GenerationParams params,
                            @Option(names = { "-p", "--probability" },
                                    required = true,
                                    description = "The probability that each edge exists") double edgeProbability) throws IOException {
      if (!Double.isFinite(edgeProbability) || edgeProbability < 0 || edgeProbability > 1) {
        return CommandLine.ExitCode.USAGE;
      }

      for (int i = 0; i < params.outputFiles.length; i++) {
        System.out.printf("Generating random graph %d of %d...%n", i + 1,
            params.outputFiles.length);
        Graph graph = GraphGenerator.generateRandomGraph(new SparseGraphImpl(params.vertices),
            edgeProbability, new Random());

        String name = params.graphName;
        String description = String.format("random -n=%d -p=%f", params.vertices, edgeProbability);
        if (name == null) {
          name = String.format("Random-%d-%08x", params.vertices, graph.hashCode());
        }

        File file = params.outputFiles[i];
        System.out.printf("Saving random graph %d of %d to %s...%n", i + 1,
            params.outputFiles.length, file.getName());
        GraphFileData data = new GraphFileData(name, description, graph);
        data.writeToFile(file);
      }

      return CommandLine.ExitCode.OK;
    }

    @Command(name = "regular",
             description = "Random-Regular graph: every vertex is connected to d random vertices",
             mixinStandardHelpOptions = true)
    int generateRegularGraph(@Mixin GenerationParams params, @Option(names = { "-d", "--degree" },
                                                                     required = true,
                                                                     description = "The degree of each vertex") int degree) throws IOException {
      for (int i = 0; i < params.outputFiles.length; i++) {
        System.out.printf("Generating regular graph %d of %d...%n", i + 1,
            params.outputFiles.length);

        int[] degrees = GraphUtils.generateRegularDegreeSequence(params.vertices, degree);
        Graph graph = GraphGenerator.generateGraph(new SparseGraphImpl(params.vertices), degrees,
            new Random());

        String name = params.graphName;
        String description = String.format("regular -n=%d -d=%d", params.vertices, degree);
        if (name == null) {
          name = String.format("Regular-%d-%08x", params.vertices, graph.hashCode());
        }

        File file = params.outputFiles[i];
        System.out.printf("Saving regular graph %d of %d to %s...%n", i + 1,
            params.outputFiles.length, file.getName());
        GraphFileData data = new GraphFileData(name, description, graph);
        data.writeToFile(file);
      }

      return CommandLine.ExitCode.OK;
    }

    @Command(name = "bipartite",
             description = "Random-Bipartite-Regular graph: every vertex is connected to d random vertices of the opposite color",
             mixinStandardHelpOptions = true)
    int generateBipartiteGraph(@Mixin GenerationParams params, @Option(names = { "-d", "--degree" },
                                                                       required = true,
                                                                       description = "The degree of each vertex") int degree) throws IOException {
      for (int i = 0; i < params.outputFiles.length; i++) {
        System.out.printf("Generating bipartite-regular graph %d of %d...%n", i + 1,
            params.outputFiles.length);

        int[] leftDegrees = GraphUtils.generateRegularDegreeSequence(params.vertices / 2, degree);
        int[] rightDegrees = GraphUtils.generateRegularDegreeSequence(params.vertices / 2, degree);
        Graph graph = GraphGenerator.generateBipartiteGraph(new SparseGraphImpl(params.vertices),
            leftDegrees, rightDegrees, new Random());

        String name = params.graphName;
        String description = String.format("bipartite -n=%d -d=%d", params.vertices, degree);
        if (name == null) {
          name = String.format("Bipartite-%d-%08x", params.vertices, graph.hashCode());
        }

        File file = params.outputFiles[i];
        System.out.printf("Saving bipartite-regular graph %d of %d to %s...%n", i + 1,
            params.outputFiles.length, file.getName());
        GraphFileData data = new GraphFileData(name, description, graph);
        data.writeToFile(file);
      }

      return CommandLine.ExitCode.OK;
    }
  }

  public static void main(String... args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }
}
