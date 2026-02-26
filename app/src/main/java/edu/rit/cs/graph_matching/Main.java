package edu.rit.cs.graph_matching;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.rit.cs.graph_matching.algorithm.DaniHayesAlgorithm;
import edu.rit.cs.graph_matching.algorithm.EdmondsAlgorithm;
import edu.rit.cs.graph_matching.algorithm.GoelKapralovKhanna;
import edu.rit.cs.graph_matching.algorithm.HopcroftKarpAlgorithm;
import edu.rit.cs.graph_matching.algorithm.MatchingAlgorithm;
import edu.rit.cs.graph_matching.graph.AdjacencySetGraph;
import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.GraphGenerator;
import edu.rit.cs.graph_matching.graph.GraphUtils;
import edu.rit.cs.graph_matching.runner.GraphFileData;
import edu.rit.cs.graph_matching.runner.GraphStatistics.Stats;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.AugmentationDataPoint;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.DataPoint;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.InitializationDataPoint;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(subcommands = { Main.GenerateGraph.class, Main.RunTest.class },
         mixinStandardHelpOptions = true)
public final class Main {
    private Main() {}

    @Command(name = "generate", description = "Generate a graph using a particular method.",
             mixinStandardHelpOptions = true)
    static class GenerateGraph {
        static class OutputParams {
            @Option(names = { "-f", "--file-prefix" }, required = true,
                    description = "Prefix for the graph filenames")
            private String filePrefix;

            @Option(names = { "-o", "--output-dir" }, description = "Output data directory")
            private File outputDir = new File(System.getProperty("user.dir"));

            @Option(names = { "-c", "--count" }, required = true,
                    description = "The number of graphs to generate")
            private int graphCount;

            OutputParams() {}
        }

        @Command(name = "random", mixinStandardHelpOptions = true,
                 description = "Random graph: every edge is created with the same probability")
        public int generateRandomGraph(
        // @formatter:off
            @Mixin OutputParams params,
            @Option(names = { "-n", "--size" }, required = true,
                    description = "Number of vertices in the graph")
            int vertices,
            @Option(names = { "-p", "--probability" }, required = true,
                    description = "The probability that each edge exists")
            double edgeProbability
        // @formatter:on
        ) throws IOException {
            if (!Double.isFinite(edgeProbability) || edgeProbability < 0 || edgeProbability > 1) {
                return CommandLine.ExitCode.USAGE;
            }

            return generateGraphs(params, "Random",
                    String.format("regular -n=%d -p=%f", vertices, edgeProbability),
                    () -> GraphGenerator.generateRandomGraph(new AdjacencySetGraph(vertices),
                            edgeProbability, new Random()));
        }

        @Command(name = "regular", mixinStandardHelpOptions = true,
                 description = "Random-Regular graph: every vertex is connected to d random vertices")
        public int generateRegularGraph(
        // @formatter:off
            @Mixin OutputParams params,
            @Option(names = { "-n", "--size" }, required = true,
                    description = "Number of vertices in the graph")
            int vertices,
            @Option(names = { "-d", "--degree" }, required = true, description = "Degree of each vertex")
            int degree
        // @formatter:on
        ) throws IOException {
            if (degree >= vertices) {
                throw new IllegalArgumentException("degree must be less than vertices");
            }

            int[] degrees = GraphUtils.generateRegularDegreeSequence(vertices, degree);
            return generateGraphs(params, "Regular",
                    String.format("regular -n=%d -d=%d", vertices, degree),
                    () -> GraphGenerator.generateGraph(new AdjacencySetGraph(vertices), degrees,
                            new Random()));
        }

        @Command(name = "biregular", mixinStandardHelpOptions = true,
                 description = "Random-Biregular graph: every vertex is connected "
                         + "to d random vertices of the opposite color")
        public int generateBiregularGraph(
        // @formatter:off
            @Mixin OutputParams params,
            @Option(names = { "-n", "--size" }, required = true,
                    description = "Number of vertices in the graph")
            int vertices,
            @Option(names = { "-d", "--degree" }, required = true, description = "Degree of each vertex")
            int degree
        // @formatter:on
        ) throws IOException {
            if (vertices % 2 != 0) {
                throw new IllegalArgumentException("vertices must be even");
            }

            if (degree >= (vertices / 2)) {
                throw new IllegalArgumentException("degree must be at most half of vertices");
            }

            int[] halfDegrees = GraphUtils.generateRegularDegreeSequence(vertices / 2, degree);
            return generateGraphs(params, "Biregular",
                    String.format("biregular -n=%d -d=%d", vertices, degree),
                    () -> GraphGenerator.generateBipartiteGraph(new AdjacencySetGraph(vertices),
                            halfDegrees, halfDegrees, new Random()));
        }

        private static int generateGraphs(OutputParams params, String graphNamePrefix,
                                          String graphDescription,
                                          Supplier<Graph> generator) throws IOException {
            for (int i = 1; i <= params.graphCount; i++) {
                System.out.printf("Generating \"%s\" graph %d of %d...%n", graphDescription, i,
                        params.graphCount);

                Graph graph = generator.get();

                String name = String.format("%s-%08x", graphNamePrefix, graph.hashCode());
                String filename = String.format("%s%d.graph", params.filePrefix, i);
                System.out.printf("Saving \"%s\" graph %d of %d to %s...%n", graphDescription, i,
                        params.graphCount, filename);

                File file = new File(params.outputDir, filename);
                GraphFileData data = new GraphFileData(name, graphDescription, graph);
                data.writeToFile(file);
            }
            return CommandLine.ExitCode.OK;
        }
    }

    @Command(name = "run", mixinStandardHelpOptions = true,
             description = "Run the profiling suite on one or more graphs")
    static class RunTest implements Callable<Integer> {
        enum Algorithm {
            daniHayes,
            edmonds,
            hopcroftKarp,
            goelKapralovKhanna,
        }

        @Parameters(description = "Algorithm to run", index = "0")
        private Algorithm algorithm;

        @Parameters(description = "Graph files", index = "1..*", arity = "1..*")
        private File[] files;

        @Option(names = { "-o", "--output-dir" }, description = "Output data directory")
        private File outputDir = new File(System.getProperty("user.dir"));

        @Option(names = { "-g", "--round-timeout" }, required = true,
                description = "Timeout for an entire algorithm round")
        private Duration roundTimeout;

        @Option(names = { "-t", "--iteration-timeout" }, required = true,
                description = "Timeout per algorithm iteration")
        private Duration iterationTimeout;

        @Option(names = { "-w", "--warmup-time" }, description = "Timeout for warmup round")
        private Duration warmupTimeout = Duration.ofSeconds(10);

        @Option(names = { "-r", "--max-retries" },
                description = "Number of warmup iterations per graph before beginning measurement; default is auto")
        private int maxRetries = 0;

        @Option(names = { "-n", "--rounds" },
                description = "Number of measurement rounds to perform per graph")
        private int rounds = 5;

        @Option(names = { "-v", "--verbose" }, description = "Enable verbose output to stdout")
        private boolean verbose = false;

        private PrintWriter currentRunCsv;

        RunTest() {}

        @Override
        public Integer call() throws IOException {
            Instant startTime = Instant.now();

            for (File file : files) {
                System.out.printf("Reading graph file %s...%n", file.getName());
                GraphFileData data = GraphFileData.readFile(file);
                Graph graph = data.graph();

                System.out.printf("Performing warmup for %s on %s...%n", algorithm, data.name());
                try (MatchingAlgorithmTester tester = new MatchingAlgorithmTester(
                        this::getAlgorithm, graph, new Random(), this::printCallback)) {
                    tester.run(graph.size() / 2, maxRetries, warmupTimeout, warmupTimeout);
                }

                String fileBasename = file.getName();
                if (fileBasename.contains(".")) {
                    fileBasename = fileBasename.substring(0, fileBasename.lastIndexOf("."));
                }
                for (int i = 1; i <= rounds; i++) {
                    System.out.printf("Starting round %d of %d for %s on %s...%n", i, rounds,
                            algorithm, data.name());
                    String safeStartTime = startTime.toString()
                                                    .replace(":", "-");
                    File csvOutFile = new File(outputDir, String.format("%s_%s_%s_%d.csv",
                            safeStartTime, algorithm.name(), fileBasename, i));

                    int matchingSize;
                    try (PrintWriter csvWriter =
                            new PrintWriter(new BufferedWriter(new FileWriter(csvOutFile)))) {
                        this.currentRunCsv = csvWriter;
                        writeCsvHeader();

                        try (MatchingAlgorithmTester tester =
                                new MatchingAlgorithmTester(this::getAlgorithm, graph, new Random(),
                                        this::printCallback, this::csvCallback)) {
                            matchingSize = tester.run(graph.size() / 2, maxRetries,
                                    iterationTimeout, roundTimeout);
                        }
                    }

                    if (matchingSize == -1) {
                        System.out.printf("Round %d for %s on %s produced an invalid matching%n",
                                i + 1, algorithm, data.name());
                    } else {
                        System.out.printf(
                                "Round %d for %s on %s produced a matching with %d/%d edges%n", i,
                                algorithm, data.name(), matchingSize, graph.size() / 2);
                    }
                }
            }

            return CommandLine.ExitCode.OK;
        }

        private MatchingAlgorithm getAlgorithm(Graph graph, RandomGenerator random) {
            return switch (algorithm) {
                case Algorithm.daniHayes -> new DaniHayesAlgorithm(graph, random);
                case Algorithm.edmonds -> new EdmondsAlgorithm(graph);
                case Algorithm.hopcroftKarp -> new HopcroftKarpAlgorithm(graph);
                case Algorithm.goelKapralovKhanna -> new GoelKapralovKhanna(graph, random);
            };
        }

        private void printCallback(DataPoint dataPoint) {
            if (verbose
                    || !(dataPoint instanceof AugmentationDataPoint
                            || dataPoint instanceof InitializationDataPoint)) {
                System.out.println(dataPoint);
            }
        }

        private void csvCallback(DataPoint dataPoint) {
            if (dataPoint instanceof AugmentationDataPoint(
            // @formatter:off
                int matchingSize,
                int pathLength,
                Duration time,
                Stats statsSnapshot
            // @formatter:on
            )) {
                StringBuilder builder = new StringBuilder();
                builder.append(matchingSize)
                       .append(',')
                       .append(pathLength)
                       .append(',')
                       .append(String.format("%.9f", 1e-9 * time.toNanos()))
                       .append(',')
                       .append(statsSnapshot.allNeighborsCount())
                       .append(',')
                       .append(statsSnapshot.degreeCheckCount())
                       .append(',')
                       .append(statsSnapshot.edgeCheckCount())
                       .append(',')
                       .append(statsSnapshot.randomNeighborCount())
                       .append(',')
                       .append(statsSnapshot.sizeCheckCount());
                this.currentRunCsv.println(builder.toString());
            }
        }

        private void writeCsvHeader() {
            StringBuilder builder = new StringBuilder();
            builder.append("Matching Size")
                   .append(',')
                   .append("Path Length")
                   .append(',')
                   .append("Iteration Time")
                   .append(',')
                   .append("getAllNeighbors()")
                   .append(',')
                   .append("getDegree(v)")
                   .append(',')
                   .append("\"hasEdge(v1,v2)\"")
                   .append(',')
                   .append("getRandomNeighbor(v)")
                   .append(',')
                   .append("size()");
            this.currentRunCsv.println(builder.toString());
        }
    }

    public static void main(String... args) {
        int exitCode =
                new CommandLine(new Main()).registerConverter(Duration.class, Main::parseDuration)
                                           .execute(args);
        System.exit(exitCode);
    }

    private static final Pattern VALID_DURATION =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)(ms|s|m|h|D)$");

    private static Duration parseDuration(String arg) {
        Matcher matcher = VALID_DURATION.matcher(arg);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration");
        }

        double magnitude = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);

        long nanos = (long) (magnitude * switch (unit) {
            case "ms" -> 1e6;
            case "s" -> 1e9;
            case "m" -> 60e9;
            case "h" -> 3600e9;
            case "D" -> 86400e9;
            default -> throw new IllegalStateException();
        });
        return Duration.ofNanos(nanos);
    }
}
