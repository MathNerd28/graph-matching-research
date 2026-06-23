package edu.rit.cs.graph_matching.algorithm;

import java.util.Map;

import edu.rit.cs.graph_matching.runner.Statistics;

/**
 * Operation counters specific to {@link GabowAlgorithm}, recorded through the
 * generic {@link Statistics} framework rather than by hijacking graph-method
 * calls. Three quantities are tracked:
 * <ul>
 * <li>{@code edgeExaminations} — every edge the algorithm looks at: Phase 1
 * edge scans, H-graph construction, Phase 2 DFS edge scans, blossom unrolling
 * steps, and the augmenting-path flip.</li>
 * <li>{@code dsuOperations} — every union-find {@code find}/{@code union} on the
 * G- and H-graph blossom partitions.</li>
 * <li>{@code priorityQueueOperations} — every push/pop in the dual phase.</li>
 * </ul>
 * Increment methods are package-private: only {@link GabowAlgorithm} and its
 * helper structures record into them.
 */
public final class GabowStatistics implements Statistics {
    private long edgeExaminations;
    private long dsuOperations;
    private long priorityQueueOperations;

    /** Record one edge examination. */
    void examineEdge() {
        edgeExaminations++;
    }

    /** Record one union-find {@code find} or {@code union}. */
    void recordDsuOperation() {
        dsuOperations++;
    }

    /** Record one priority-queue push or pop. */
    void recordPriorityQueueOperation() {
        priorityQueueOperations++;
    }

    @Override
    public void reset() {
        edgeExaminations = 0;
        dsuOperations = 0;
        priorityQueueOperations = 0;
    }

    @Override
    public Map<String, Object> getStatistics() {
        return Map.of("edgeExaminations", edgeExaminations, "dsuOperations", dsuOperations,
                "priorityQueueOperations", priorityQueueOperations);
    }
}
