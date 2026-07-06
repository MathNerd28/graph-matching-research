package edu.rit.cs.graph_matching;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
import edu.rit.cs.graph_matching.graph.AdjacencySetGraph;
import edu.rit.cs.graph_matching.graph.Graph;
import edu.rit.cs.graph_matching.graph.GraphGenerator;
import edu.rit.cs.graph_matching.graph.GraphGenerator.DaniHayesHardConstruction;
import edu.rit.cs.graph_matching.graph.GraphUtils;
import edu.rit.cs.graph_matching.runner.GraphFileData;
import edu.rit.cs.graph_matching.runner.GraphStatistics;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.AlgorithmInitialization;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.AugmentationDataPoint;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.DataPoint;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.ErrorDataPoint;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.FailureDataPoint;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.InitializationDataPoint;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.StatsSnapshot;
import edu.rit.cs.graph_matching.runner.MatchingAlgorithmTester.TimeoutDataPoint;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(subcommands = { Main.GenerateGraph.class, Main.RunTest.class,
                         Main.DaniHayesHardTest.class },
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

        @Command(name = "loop", mixinStandardHelpOptions = true,
                 description = "Loop graph: a single cycle graph where vertices are connected sequentially"
                         + " with 0 connecting to n-1")
        public int generateLoopGraph(
        // @formatter:off
            @Mixin OutputParams params,
            @Option(names = { "-n", "--size" }, required = true,
                    description = "Number of vertices in the graph")
            int vertices
        // @formatter:on
        ) throws IOException {
            return generateGraphs(params, "Loop", String.format("loop -n=%d", vertices),
                    () -> GraphGenerator.generateLoopGraph(new AdjacencySetGraph(vertices)));
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

        private AlgorithmInitialization getAlgorithm(Graph graph, RandomGenerator random) {
            GraphStatistics graphStats = new GraphStatistics(graph);
            return switch (algorithm) {
                case Algorithm.daniHayes -> new AlgorithmInitialization(
                        new DaniHayesAlgorithm(graphStats, random), graphStats);
                case Algorithm.edmonds ->
                     new AlgorithmInitialization(new EdmondsAlgorithm(graphStats), graphStats);
                case Algorithm.hopcroftKarp ->
                     new AlgorithmInitialization(new HopcroftKarpAlgorithm(graphStats), graphStats);
                case Algorithm.goelKapralovKhanna -> new AlgorithmInitialization(
                        new GoelKapralovKhanna(graphStats, random), graphStats);
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
            if (dataPoint instanceof InitializationDataPoint(Duration time, StatsSnapshot stats)) {
                // CSV header
                StringBuilder builder = new StringBuilder();
                builder.append("Matching Size")
                       .append(',')
                       .append("Path Length")
                       .append(',')
                       .append("Iteration Time");
                for (Map.Entry<String, String> stat : stats) {
                    builder.append(",\"")
                           .append(stat.getKey())
                           .append("\"");
                }
                this.currentRunCsv.println(builder.toString());
            }

            if (dataPoint instanceof AugmentationDataPoint(
            // @formatter:off
                int matchingSize,
                int pathLength,
                Duration time,
                StatsSnapshot stats
            // @formatter:on
            )) {
                // CSV body
                StringBuilder builder = new StringBuilder();
                builder.append(matchingSize)
                       .append(',')
                       .append(pathLength)
                       .append(',')
                       .append(String.format("%.9f", 1e-9 * time.toNanos()));
                for (Map.Entry<String, String> stat : stats) {
                    builder.append(",\"")
                           .append(stat.getValue())
                           .append("\"");
                }
                this.currentRunCsv.println(builder.toString());
            }
        }
    }

    @Command(name = "dh-hard-test", mixinStandardHelpOptions = true,
             description = "Run Dani-Hayes on the disjoint-gadget hard construction")
    static class DaniHayesHardTest implements Callable<Integer> {
        @Option(names = { "-d", "--degree" }, required = true,
                description = "Regular degree; must be even and at least 4")
        private int degree;

        @Option(names = { "-n", "--rounds" },
                description = "Number of independent seeded rounds")
        private int rounds = 5;

        @Option(names = { "-a", "--augmentations" },
                description = "Augmentations to attempt in each round")
        private int augmentations = 1;

        @Option(names = { "-s", "--seed" }, description = "Base random seed")
        private long seed = 0xD0A17A9E5L;

        @Option(names = { "-t", "--iteration-timeout" },
                description = "Timeout per seeded augmentation")
        private Duration iterationTimeout = Duration.ofSeconds(30);

        @Option(names = { "-o", "--output-dir" }, description = "Output data directory")
        private File outputDir = new File(System.getProperty("user.dir"));

        private DaniHayesHardConstruction construction;
        private PrintWriter currentRunCsv;
        private boolean wroteCsvHeader;
        private int currentRound;
        private int currentAugmentation;
        private long successfulAugmentations;
        private long timedOutAugmentations;
        private long failedAugmentations;
        private long totalRandomNeighborCalls;

        DaniHayesHardTest() {}

        @Override
        public Integer call() throws IOException {
            if (rounds < 1) {
                throw new IllegalArgumentException("rounds must be positive");
            }
            if (augmentations < 1) {
                throw new IllegalArgumentException("augmentations must be positive");
            }
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IOException("Could not create output directory " + outputDir);
            }
            if (!outputDir.isDirectory()) {
                throw new IOException(outputDir + " is not a directory");
            }

            construction = GraphGenerator.generateDaniHayesHardGraph(degree);
            int initialMatchingSize = construction.plantedMatching()
                                                  .size();

            Instant startTime = Instant.now();
            String safeStartTime = startTime.toString()
                                            .replace(":", "-");
            File csvOutFile = new File(outputDir,
                    String.format("%s_dh-hard-d%d.csv", safeStartTime, degree));

            System.out.printf(
                    "Generated DH hard construction: d=%d, n=%d, corridor length=%d, free vertices=%d, planted matching=%d%n",
                    degree, construction.graph()
                                        .size(),
                    construction.corridorLength(), construction.freeVertexCount(),
                    initialMatchingSize);
            System.out.printf("Writing CSV to %s%n", csvOutFile);

            try (PrintWriter writer =
                    new PrintWriter(new BufferedWriter(new FileWriter(csvOutFile)))) {
                currentRunCsv = writer;
                wroteCsvHeader = false;

                for (int round = 1; round <= rounds; round++) {
                    currentRound = round;
                    currentAugmentation = 0;

                    try (MatchingAlgorithmTester tester =
                            new MatchingAlgorithmTester(this::getSeededDaniHayes,
                                    construction.graph(), new Random(seed + round - 1), true,
                                    this::hardExperimentCallback)) {
                        tester.run(initialMatchingSize + augmentations, 1, iterationTimeout,
                                iterationTimeout.multipliedBy(augmentations));
                    }
                }
            }

            if (successfulAugmentations > 0) {
                double averageRandomCalls =
                        (double) totalRandomNeighborCalls / successfulAugmentations;
                System.out.printf(
                        "Successful augmentations=%d, average getRandomNeighbor calls=%.3f%n",
                        successfulAugmentations, averageRandomCalls);
            }
            System.out.printf("Timeouts=%d, failures=%d%n", timedOutAugmentations,
                    failedAugmentations);
            return CommandLine.ExitCode.OK;
        }

        private AlgorithmInitialization getSeededDaniHayes(Graph graph, RandomGenerator random) {
            GraphStatistics graphStats = new GraphStatistics(graph);
            return new AlgorithmInitialization(
                    new DaniHayesAlgorithm(graphStats, random, construction.plantedMatching()),
                    graphStats);
        }

        private void hardExperimentCallback(DataPoint dataPoint) {
            if (dataPoint instanceof InitializationDataPoint(Duration time, StatsSnapshot stats)) {
                if (!wroteCsvHeader) {
                    writeHardExperimentHeader(stats);
                    wroteCsvHeader = true;
                }
                return;
            }

            currentAugmentation++;

            if (dataPoint instanceof AugmentationDataPoint(
            // @formatter:off
                int matchingSize,
                int pathLength,
                Duration time,
                StatsSnapshot stats
            // @formatter:on
            )) {
                successfulAugmentations++;
                totalRandomNeighborCalls += statAsLong(stats, "getRandomNeighbor(v)");
                writeHardExperimentRow(currentAugmentation, matchingSize - 1, matchingSize,
                        pathLength, time, stats, "success");
                printHardExperimentProgress(pathLength, time, stats, "success");
            } else if (dataPoint instanceof TimeoutDataPoint(
            // @formatter:off
                int matchingSize,
                Duration timeout,
                StatsSnapshot stats
            // @formatter:on
            )) {
                timedOutAugmentations++;
                writeHardExperimentRow(currentAugmentation, matchingSize, matchingSize, -1,
                        timeout, stats, "timeout");
                printHardExperimentProgress(-1, timeout, stats, "timeout");
            } else if (dataPoint instanceof FailureDataPoint(
            // @formatter:off
                int matchingSize,
                Duration time,
                StatsSnapshot stats
            // @formatter:on
            )) {
                failedAugmentations++;
                writeHardExperimentRow(currentAugmentation, matchingSize, matchingSize, -1, time,
                        stats, "failure");
                printHardExperimentProgress(-1, time, stats, "failure");
            } else if (dataPoint instanceof ErrorDataPoint) {
                failedAugmentations++;
                System.out.printf("Round %d/%d augmentation %d/%d: error%n", currentRound, rounds,
                        currentAugmentation, augmentations);
            }
        }

        private void writeHardExperimentHeader(StatsSnapshot stats) {
            StringBuilder builder = new StringBuilder();
            builder.append("Degree")
                   .append(',')
                   .append("Graph Size")
                   .append(',')
                   .append("Corridor Length")
                   .append(',')
                   .append("Free Vertices")
                   .append(',')
                   .append("Round")
                   .append(',')
                   .append("Augmentation")
                   .append(',')
                   .append("Matching Size Before")
                   .append(',')
                   .append("Matching Size After")
                   .append(',')
                   .append("Path Length")
                   .append(',')
                   .append("Iteration Time");
            for (Map.Entry<String, String> stat : stats) {
                builder.append(",\"")
                       .append(stat.getKey())
                       .append("\"");
            }
            builder.append(',')
                   .append("Status");
            currentRunCsv.println(builder.toString());
        }

        private void writeHardExperimentRow(int augmentation, int matchingSizeBefore,
                                            int matchingSizeAfter, int pathLength, Duration time,
                                            StatsSnapshot stats, String status) {
            StringBuilder builder = new StringBuilder();
            builder.append(degree)
                   .append(',')
                   .append(construction.graph()
                                       .size())
                   .append(',')
                   .append(construction.corridorLength())
                   .append(',')
                   .append(construction.freeVertexCount())
                   .append(',')
                   .append(currentRound)
                   .append(',')
                   .append(augmentation)
                   .append(',')
                   .append(matchingSizeBefore)
                   .append(',')
                   .append(matchingSizeAfter)
                   .append(',')
                   .append(pathLength)
                   .append(',')
                   .append(String.format("%.9f", 1e-9 * time.toNanos()));
            for (Map.Entry<String, String> stat : stats) {
                builder.append(",\"")
                       .append(stat.getValue())
                       .append("\"");
            }
            builder.append(',')
                   .append(status);
            currentRunCsv.println(builder.toString());
        }

        private void printHardExperimentProgress(int pathLength, Duration time, StatsSnapshot stats,
                                                 String status) {
            System.out.printf(
                    "Round %d/%d augmentation %d/%d: %s, pathLength=%d, randomNeighbor=%d, time=%.3fs%n",
                    currentRound, rounds, currentAugmentation, augmentations, status, pathLength,
                    statAsLong(stats, "getRandomNeighbor(v)"), 1e-9 * time.toNanos());
        }

        private static long statAsLong(StatsSnapshot stats, String key) {
            Object value = stats.entries()
                                .get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            return Long.parseLong(value.toString());
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
