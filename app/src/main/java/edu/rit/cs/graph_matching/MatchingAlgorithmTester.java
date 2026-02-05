package edu.rit.cs.graph_matching;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import edu.rit.cs.graph_matching.GraphStatistics.Stats;

public class MatchingAlgorithmTester implements AutoCloseable {
  private final MatchingAlgorithm     algorithm;
  private final Graph                 inputGraph;
  private final Consumer<DataPoint>[] callbacks;

  private final ExecutorService executor;
  private final GraphStatistics statistics;

  private int matchingSize;

  @SafeVarargs
  public MatchingAlgorithmTester(BiFunction<Graph, RandomGenerator, MatchingAlgorithm> supplier,
                                 Graph inputGraph, RandomGenerator random,
                                 Consumer<DataPoint>... callbacks) {
    this.inputGraph = inputGraph;
    this.statistics = new GraphStatistics(inputGraph);
    this.executor = Executors.newSingleThreadExecutor();
    this.callbacks = callbacks.clone();

    if (random == null) {
      random = new Random();
    }

    long start = System.nanoTime();
    this.algorithm = supplier.apply(statistics, random);
    long end = System.nanoTime();

    if (algorithm == null) {
      throw new IllegalStateException("Failed to initialize algorithm with input graph");
    }

    this.matchingSize = algorithm.getCurrentMatching()
                                 .size();
    if (matchingSize != 0) {
      throw new IllegalStateException("Algorithm initialized with non-empty matching");
    }

    runCallbacks(
        new InitializationDataPoint(Duration.ofNanos(end - start), statistics.getSnapshot()));
  }

  private IterationResult iterationTask() {
    long start = System.nanoTime();
    int pathLength = algorithm.augment();
    long end = System.nanoTime();
    return new IterationResult(pathLength, Duration.ofNanos(end - start));
  }

  private DataPoint runIteration(Duration timeout) {
    try {
      statistics.clear();
      Future<IterationResult> future = executor.submit(this::iterationTask);
      IterationResult result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      GraphStatistics.Stats stats = statistics.getSnapshot();
      this.matchingSize++;

      if (result.pathLength() == -1 || result.pathLength() % 2 == 0) {
        // augmentation failed
        return new FailureDataPoint(matchingSize, result.time(), stats);
      }

      return new AugmentationDataPoint(matchingSize, result.pathLength(), result.time(), stats);
    } catch (TimeoutException e) {
      return new TimeoutDataPoint(matchingSize, timeout, statistics.getSnapshot());
    } catch (InterruptedException |
             ExecutionException e) {
      // TODO: what happened here???
      e.printStackTrace();
      return new ErrorDataPoint(matchingSize, e);
    }
  }

  public int run(int targetSize, int maxRetries, Duration iterationTimeout,
                 Duration globalTimeout) {
    long start = System.currentTimeMillis();
    int retries = 0;
    while (this.matchingSize < targetSize
        && !algorithm.isFinished()
        && (System.currentTimeMillis() - start) < globalTimeout.toMillis()) {
      DataPoint dataPoint = runIteration(iterationTimeout);

      runCallbacks(dataPoint);

      if (dataPoint instanceof AugmentationDataPoint) {
        // successful iteration
        retries = 0;
      } else if (dataPoint instanceof TimeoutDataPoint || dataPoint instanceof FailureDataPoint) {
        // failure, but potentially recoverable
        retries++;
        if (retries >= maxRetries) {
          break;
        }
      } else {
        // failure, and not recoverable
        break;
      }
    }

    if (GraphUtils.isValidMatching(inputGraph, algorithm.getCurrentMatching())) {
      return matchingSize;
    } else {
      return -1;
    }
  }

  private void runCallbacks(DataPoint dataPoint) {
    for (Consumer<DataPoint> callback : callbacks) {
      callback.accept(dataPoint);
    }
  }

  @Override
  public void close() {
    executor.close();
  }

  private record IterationResult(int pathLength,
                                 Duration time) {}

  public sealed interface DataPoint {}

  public record InitializationDataPoint(Duration time,
                                        Stats statsSnapshot)
      implements DataPoint {}

  public record AugmentationDataPoint(int matchingSize,
                                      int pathLength,
                                      Duration time,
                                      Stats statsSnapshot)
      implements DataPoint {}

  public record TimeoutDataPoint(int matchingSize,
                                 Duration timeout,
                                 Stats statsSnapshot)
      implements DataPoint {}

  public record FailureDataPoint(int matchingSize,
                                 Duration time,
                                 Stats statsSnapshot)
      implements DataPoint {}

  public record ErrorDataPoint(int matchingSize,
                               Throwable cause)
      implements DataPoint {}
}
