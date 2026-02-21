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
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.rit.cs.graph_matching.MatchingAlgorithmTester.AugmentationDataPoint;
import edu.rit.cs.graph_matching.MatchingAlgorithmTester.DataPoint;
import edu.rit.cs.graph_matching.MatchingAlgorithmTester.InitializationDataPoint;
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
            @Option(names = { "-f", "--file-prefix" }, required = true,
                    description = "Prefix for the graph filenames")
            private String filePrefix;

            @Option(names = { "-o", "--output-dir" }, description = "Output data directory")
            private File outputDir = new File(System.getProperty("user.dir"));

            @Option(names = { "--name" }, description = "Name of the graph")
            private String graphName = null;

            @Option(names = { "-n", "--size" }, required = true,
                    description = "Number of vertices in the graph")
            private int vertices;

            @Option(names = { "--verify" },
                    description = "Verify that a degree sequence is possible before generating.")
            private boolean verify = false;

            @Option(names = { "-c", "--count" }, required = true,
                    description = "The number of graphs to generate")
            private int graphCount;
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

            for (int i = 1; i <= params.graphCount; i++) {
                System.out.printf("Generating random graph %d of %d...%n", i, params.graphCount);
                Graph graph = GraphGenerator.generateRandomGraph(
                        new AdjacencySetGraph(params.vertices), edgeProbability, new Random());

                String name = params.graphName;
                String description =
                        String.format("random -n=%d -p=%f", params.vertices, edgeProbability);
                if (name == null) {
                    name = String.format("Random-%d-%08x", params.vertices, graph.hashCode());
                }

                String filename = String.format("%s%d.graph", params.filePrefix, i);
                System.out.printf("Saving random graph %d of %d to %s...%n", i, params.graphCount,
                        filename);
                File file = new File(params.outputDir, filename);
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
            if (degree >= params.vertices) {
                throw new IllegalArgumentException("degree must be less than vertices");
            }

            for (int i = 1; i <= params.graphCount; i++) {
                System.out.printf("Generating regular graph %d of %d...%n", i, params.graphCount);

                int[] degrees = GraphUtils.generateRegularDegreeSequence(params.vertices, degree);
                if (params.verify && !GraphUtils.isGraphical(degrees)) {
                    throw new IllegalArgumentException("degree sequence cannot be generated");
                }
                Graph graph = GraphGenerator.generateGraph(new AdjacencySetGraph(params.vertices),
                        degrees, new Random());

                String name = params.graphName;
                String description = String.format("regular -n=%d -d=%d", params.vertices, degree);
                if (name == null) {
                    name = String.format("Regular-%d-%08x", params.vertices, graph.hashCode());
                }

                String filename = String.format("%s%d.graph", params.filePrefix, i);
                System.out.printf("Saving regular graph %d of %d to %s...%n", i, params.graphCount,
                        filename);
                File file = new File(params.outputDir, filename);
                GraphFileData data = new GraphFileData(name, description, graph);
                data.writeToFile(file);
            }

            return CommandLine.ExitCode.OK;
        }

        @Command(name = "biregular", mixinStandardHelpOptions = true,
                 description = "Random-Biregular graph: every vertex is connected "
                         + "to d random vertices of the opposite color")
        public int generateBiregularGraph(
        // @formatter:off
            @Mixin GenerationParams params,
            @Option(names = { "-d", "--degree" }, required = true, description = "Degree of each vertex")
            int degree
        // @formatter:on
        ) throws IOException {
            if (degree >= params.vertices) {
                throw new IllegalArgumentException("degree must be less than vertices");
            }

            for (int i = 1; i <= params.graphCount; i++) {
                System.out.printf("Generating bipartite-regular graph %d of %d...%n", i,
                        params.graphCount);

                int[] halfDegrees =
                        GraphUtils.generateRegularDegreeSequence(params.vertices / 2, degree);
                if (params.verify && !GraphUtils.isGraphical(halfDegrees, halfDegrees)) {
                    throw new IllegalArgumentException("degree sequence cannot be generated");
                }
                Graph graph = GraphGenerator.generateBipartiteGraph(
                        new AdjacencySetGraph(params.vertices), halfDegrees, halfDegrees,
                        new Random());

                String name = params.graphName;
                String description =
                        String.format("bipartite -n=%d -d=%d", params.vertices, degree);
                if (name == null) {
                    name = String.format("Bipartite-%d-%08x", params.vertices, graph.hashCode());
                }

                String filename = String.format("%s%d.graph", params.filePrefix, i);
                System.out.printf("Saving bipartite-regular graph %d of %d to %s...%n", i,
                        params.graphCount, filename);
                File file = new File(params.outputDir, filename);
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
            daniHayes,
            edmonds;
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
                    String safeStartTime = startTime.toString().replace(":", "-");
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
            if (dataPoint instanceof AugmentationDataPoint aug) {
                StringBuilder builder = new StringBuilder();
                builder.append(aug.matchingSize())
                       .append(',')
                       .append(aug.pathLength())
                       .append(',')
                       .append(String.format("%.9f", 1e-9 * aug.time()
                                                               .toNanos()))
                       .append(',')
                       .append(aug.statsSnapshot()
                                  .allNeighborsCount())
                       .append(',')
                       .append(aug.statsSnapshot()
                                  .degreeCheckCount())
                       .append(',')
                       .append(aug.statsSnapshot()
                                  .edgeCheckCount())
                       .append(',')
                       .append(aug.statsSnapshot()
                                  .randomNeighborCount())
                       .append(',')
                       .append(aug.statsSnapshot()
                                  .sizeCheckCount());
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
