package edu.rit.cs.graph_matching;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.random.RandomGenerator;

import edu.rit.cs.graph_matching.MatchingAlgorithmTester.DataPoint;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(subcommands = { Main.GenerateGraph.class, Main.RunTest.class },
         mixinStandardHelpOptions = true)
public class Main {
    @Command(name = "generate", description = "Generate a graph using a particular method.",
             mixinStandardHelpOptions = true)
    static class GenerateGraph {
        static class GenerationParams {
            @Parameters(description = "Output graph files")
            private File[] outputFiles;

            @Option(names = { "--name" }, description = "Name of the graph")
            private String graphName = null;

            @Option(names = { "-n", "--size" }, required = true,
                    description = "Number of vertices in the graph")
            private int vertices;
        }

        @Command(name = "random", mixinStandardHelpOptions = true,
                 description = "Random graph: every edge is created with the same probability")
        public int generateRandomGraph(
        // @formatter:off
            @Mixin GenerationParams params,
            @Option(names = { "-p", "--probability" }, required = true,
                    description = "The probability that each edge exists")
            double edgeProbability
        // @formatter:on
        ) throws IOException {
            if (!Double.isFinite(edgeProbability) || edgeProbability < 0 || edgeProbability > 1) {
                return CommandLine.ExitCode.USAGE;
            }

            for (int i = 0; i < params.outputFiles.length; i++) {
                System.out.printf("Generating random graph %d of %d...%n", i + 1,
                        params.outputFiles.length);
                Graph graph = GraphGenerator.generateRandomGraph(
                        new SparseGraphImpl(params.vertices), edgeProbability, new Random());

                String name = params.graphName;
                String description =
                        String.format("random -n=%d -p=%f", params.vertices, edgeProbability);
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

        @Command(name = "regular", mixinStandardHelpOptions = true,
                 description = "Random-Regular graph: every vertex is connected to d random vertices")
        public int generateRegularGraph(
        // @formatter:off
            @Mixin GenerationParams params,
            @Option(names = { "-d", "--degree" }, required = true, description = "Degree of each vertex")
            int degree
        // @formatter:on
        ) throws IOException {
            for (int i = 0; i < params.outputFiles.length; i++) {
                System.out.printf("Generating regular graph %d of %d...%n", i + 1,
                        params.outputFiles.length);

                int[] degrees = GraphUtils.generateRegularDegreeSequence(params.vertices, degree);
                Graph graph = GraphGenerator.generateGraph(new SparseGraphImpl(params.vertices),
                        degrees, new Random());

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

        @Command(name = "bipartite", mixinStandardHelpOptions = true,
                 description = "Random-Bipartite-Regular graph: every vertex is connected "
                         + "to d random vertices of the opposite color")
        public int generateBipartiteGraph(
        // @formatter:off
            @Mixin GenerationParams params,
            @Option(names = { "-d", "--degree" }, required = true, description = "Degree of each vertex")
            int degree
        // @formatter:on
        ) throws IOException {
            for (int i = 0; i < params.outputFiles.length; i++) {
                System.out.printf("Generating bipartite-regular graph %d of %d...%n", i + 1,
                        params.outputFiles.length);

                int[] leftDegrees =
                        GraphUtils.generateRegularDegreeSequence(params.vertices / 2, degree);
                int[] rightDegrees =
                        GraphUtils.generateRegularDegreeSequence(params.vertices / 2, degree);
                Graph graph =
                        GraphGenerator.generateBipartiteGraph(new SparseGraphImpl(params.vertices),
                                leftDegrees, rightDegrees, new Random());

                String name = params.graphName;
                String description =
                        String.format("bipartite -n=%d -d=%d", params.vertices, degree);
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

    @Command(name = "run", mixinStandardHelpOptions = true,
             description = "Run the profiling suite on one or more graphs")
    static class RunTest implements Callable<Integer> {
        enum Algorithm {
            DANI_HAYES,
            EDMONDS;
        }

        @Parameters(description = "Algorithm to run", index = "0")
        private Algorithm algorithm;

        @Parameters(description = "Graph files", index = "1..*")
        private File[] files;

        @Option(names = { "-g", "--round-timeout" }, required = true,
                description = "Timeout for an entire algorithm round")
        private Duration roundTimeout;

        @Option(names = { "-t", "--iteration-timeout" }, required = true,
                description = "Timeout per algorithm iteration")
        private Duration iterationTimeout;

        @Option(names = { "-w", "--warmup-time" }, description = "Timeout for warmup round")
        private Duration warmupTimeout = Duration.ofSeconds(5);

        @Option(names = { "-r", "--max-retries" },
                description = "Number of warmup iterations per graph before beginning measurement; default is auto")
        private int maxRetries = 0;

        @Option(names = { "-n", "--rounds" },
                description = "Number of measurement rounds to perform per graph")
        private int rounds = 5;

        @Override
        public Integer call() throws IOException {
            for (File file : files) {
                System.out.printf("Reading graph file %s...%n", file.getName());
                GraphFileData data = GraphFileData.readFile(file);
                Graph graph = data.graph();

                System.out.printf("Performing warmup round for %s on %s...%n", algorithm,
                        data.name());
                try (MatchingAlgorithmTester tester = new MatchingAlgorithmTester(
                        this::getAlgorithm, graph, new Random(), RunTest::printCallback)) {
                    tester.run(graph.size() / 2, maxRetries, warmupTimeout, warmupTimeout);
                }

                for (int i = 0; i < rounds; i++) {
                    System.out.printf("Starting round %d of %d for %s on %s...%n", i, rounds,
                            algorithm, data.name());
                    try (MatchingAlgorithmTester tester = new MatchingAlgorithmTester(
                            this::getAlgorithm, graph, new Random(), RunTest::printCallback)) {
                        tester.run(graph.size() / 2, maxRetries, iterationTimeout, roundTimeout);
                    }
                }
            }

            return CommandLine.ExitCode.OK;
        }

        private MatchingAlgorithm getAlgorithm(Graph graph, RandomGenerator random) {
            return switch (algorithm) {
                case Algorithm.DANI_HAYES -> new DaniHayesAlgorithm(graph, random);
                case Algorithm.EDMONDS -> new EdmondsAlgorithm(graph);
            };
        }

        private static void printCallback(DataPoint dataPoint) {
            System.out.println(dataPoint);
        }
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
