package edu.rit.cs.graph_matching;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import edu.rit.cs.graph_matching.GraphStatistics.Stats;

public class MatchingAlgorithmTester {
  private final MatchingAlgorithmRunner algorithm;
  private final Graph                   inputGraph;
  private final GraphStatistics         statistics;

  private Set<Edge> matching;

  private final List<DataPoint> data;
  private final ExecutorService executor;

  private final Consumer<DataPoint>[] callbacks;

  @SafeVarargs
  public MatchingAlgorithmTester(MatchingAlgorithmRunner algorithm, Graph inputGraph,
                                 RandomGenerator random, Consumer<DataPoint>... callbacks) {
    this.inputGraph = inputGraph;
    this.algorithm = algorithm;
    this.statistics = new GraphStatistics(inputGraph);
    this.data = new ArrayList<>();
    this.executor = Executors.newSingleThreadExecutor();
    this.callbacks = callbacks.clone();

    if (random == null) {
      random = new Random();
    }

    long start = System.nanoTime();
    boolean success = algorithm.initialize(statistics, random);
    long end = System.nanoTime();

    if (!success) {
      throw new IllegalStateException("Failed to initialize algorithm with input graph");
    }

    this.matching = algorithm.getCurrentMatching();
    if (!matching.isEmpty()) {
      throw new IllegalStateException("Algorithm initialized with non-empty matching");
    }

    data.add(new InitializationDataPoint(Duration.ofNanos(end - start), statistics.getSnapshot()));
  }

  private Duration iterationTask() {
    long start = System.nanoTime();
    algorithm.augmentOnce();
    long end = System.nanoTime();
    return Duration.ofNanos(end - start);
  }

  private DataPoint runIteration(Duration timeout) {
    try {
      statistics.clear();
      Future<Duration> future = executor.submit(this::iterationTask);
      Duration time = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      GraphStatistics.Stats stats = statistics.getSnapshot();

      Set<Edge> newMatching = algorithm.getCurrentMatching();
      boolean valid = GraphUtils.isValidMatching(inputGraph, newMatching);
      if (!valid) {
        return new FailureDataPoint(newMatching.size(), time, stats);
      }

      Set<Edge> changes = new HashSet<>(matching);
      changes.removeAll(newMatching);
      this.matching = newMatching;

      return new AugmentationDataPoint(matching.size(), changes.size(), time, stats);
    } catch (TimeoutException e) {
      return new TimeoutDataPoint(matching.size(), timeout, statistics.getSnapshot());
    } catch (InterruptedException |
             ExecutionException e) {
      // TODO: what happened here???
      e.printStackTrace();
      return new ErrorDataPoint(matching.size(), e);
    }
  }

  public void run(int matchingSize, int maxRetries, Duration iterationTimeout,
                  Duration globalTimeout) {
    long start = System.currentTimeMillis();
    int retries = 0;
    while (matching.size() < matchingSize
        && (System.currentTimeMillis() - start) < globalTimeout.toMillis()) {
      DataPoint dataPoint = runIteration(iterationTimeout);

      for (Consumer<DataPoint> callback : callbacks) {
        callback.accept(dataPoint);
      }

      if (dataPoint instanceof AugmentationDataPoint) {
        // successful iteration
        retries = 0;
      } else if (dataPoint instanceof TimeoutDataPoint || dataPoint instanceof ErrorDataPoint) {
        // failure, but potentially recoverable
        retries++;
        if (retries >= maxRetries) {
          return;
        }
      } else {
        // failure, and not recoverable
        return;
      }
    }
  }

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
