package redacted.graph_matching.runner;

import java.util.Map;

/**
 * Statistics that can be collected and serialized. The {@link #getStatistics()}
 * method must always return entries representing a fixed keyset: after the
 * first call, keys may not be added nor removed. Values may be any type
 * including null, but are typically coerced to {@link String Strings} for
 * serialization.
 */
public interface Statistics {
    /**
     * Get the current values of the statistics collected by this object.
     *
     * @return the current statistics values
     */
    Map<String, Object> getStatistics();

    /**
     * Reset statistics to their default values. For counters, this is typically
     * zero.
     */
    void reset();
}
