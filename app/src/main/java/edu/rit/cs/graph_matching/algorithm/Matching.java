package edu.rit.cs.graph_matching.algorithm;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import edu.rit.cs.graph_matching.graph.Graph.Edge;

/**
 * A common implementation for a matching in a graph
 */
public class Matching extends AbstractMap<Integer, Integer> implements Iterable<Edge> {
    private final int[] matches;

    private int edgeCount = 0;
    private int modCount  = 0;

    public Matching(int n) {
        this.matches = new int[n];
        Arrays.fill(matches, -1);
    }

    @Override
    public int size() {
        return edgeCount * 2;
    }

    @Override
    public boolean containsValue(Object value) {
        return value instanceof Integer v && contains(v);
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof Integer v && contains(v);
    }

    public boolean contains(int vertex) {
        return vertex >= 0 && vertex < matches.length && matches[vertex] >= 0;
    }

    public boolean contains(Edge e) {
        return get(e.vertex1()) == e.vertex2();
    }

    @Override
    public Integer get(Object key) {
        if (!(key instanceof Integer v)) {
            return null;
        }

        int ret = get(v.intValue());
        return ret < 0 ? null : ret;
    }

    public int get(int vertex) {
        if (vertex < 0 || vertex >= matches.length) {
            return -1;
        }

        return matches[vertex];
    }

    @Override
    public Integer put(Integer key, Integer value) {
        if (key < 0 || key >= matches.length || value < 0 || value >= matches.length) {
            throw new IllegalArgumentException();
        }

        int ret = put(key.intValue(), value.intValue());
        return ret < 0 ? null : ret;
    }

    public int put(int key, int value) {
        int prevValue = get(key);
        remove(key);
        remove(value);
        matches[key] = value;
        matches[value] = key;
        return prevValue;
    }

    public void put(Edge e) {
        put(e.vertex1(), e.vertex2());
    }

    @Override
    public Integer remove(Object key) {
        if (!(key instanceof Integer v)) {
            return null;
        }

        int ret = remove(v.intValue());
        return ret < 0 ? null : ret;
    }

    public int remove(int vertex) {
        if (vertex < 0 || vertex >= matches.length) {
            return -1;
        }

        int match = matches[vertex];
        if (match < 0) {
            return -1;
        }

        matches[match] = -1;
        matches[vertex] = -1;
        return match;
    }

    public boolean remove(Edge e) {
        if (!contains(e)) {
            return false;
        }

        remove(e.vertex1());
        return true;
    }

    @Override
    public Set<Entry<Integer, Integer>> entrySet() {
        return new MatchingEntrySet();
    }

    public Set<Edge> edgeSet() {
        return new MatchingEdgeSet();
    }

    @Override
    public Iterator<Edge> iterator() {
        return new MatchingItr();
    }

    @Override
    public String toString() {
        // super implementation duplicates edges

        Iterator<Edge> itr = iterator();
        if (!itr.hasNext()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        while (true) {
            Edge e = itr.next();
            sb.append(e);
            if (!itr.hasNext()) {
                return sb.append('}')
                         .toString();
            }
            sb.append(',')
              .append(' ');
        }
    }

    // Explicit overrides of equals() and hashCode() redirect to superclass

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    private class MatchingItr implements Iterator<Edge> {
        private final int modCount = MatchingItr.this.modCount;

        private int pos  = -1;
        private int seen = 0;

        @Override
        public boolean hasNext() {
            checkForComodification();
            return seen < edgeCount;
        }

        @Override
        public Edge next() {
            checkForComodification();

            int m;
            do {
                pos++;
                m = matches[pos];
            } while (pos < matches.length && m <= pos);

            if (pos >= matches.length) {
                throw new NoSuchElementException();
            }

            seen++;
            return new Edge(pos, m);
        }

        @Override
        public void remove() {
            checkForComodification();

            int match = matches[pos];
            matches[match] = -1;
            matches[pos] = -1;
            edgeCount--;
            seen--;

            checkForComodification();
        }

        /**
         * Detects some concurrent modifications. If a concurrent modification
         * is detected, a {@link ConcurrentModificationException} is thrown.
         * <p>
         * Note that the fail-fast behavior of an iterator cannot be guaranteed
         * as it is, generally speaking, impossible to make any hard guarantees
         * in the presence of unsynchronized concurrent modification. Fail-fast
         * iterators throw {@code ConcurrentModificationException} on a
         * best-effort basis. Therefore, it would be wrong to write a program
         * that depended on this exception for its correctness: <i>the fail-fast
         * behavior of iterators should be used only to detect bugs.</i>
         *
         * @throws ConcurrentModificationException
         *     if a concurrent modification was detected
         */
        private void checkForComodification() {
            if (modCount != Matching.this.modCount) {
                // someone else must have modified the set during iteration
                throw new ConcurrentModificationException();
            }
        }
    }

    private class MatchingEntrySet extends AbstractSet<Entry<Integer, Integer>> {
        @Override
        public int size() {
            return Matching.this.size();
        }

        @Override
        public Iterator<Entry<Integer, Integer>> iterator() {
            return new MatchingEntrySetItr();
        }

        @Override
        public boolean add(Entry<Integer, Integer> e) {
            Integer oldValue = Matching.this.put(e.getKey(), e.getValue());
            return !Objects.equals(oldValue, e.getValue());
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry<?, ?> e)) {
                return false;
            }

            Object k = e.getKey();
            Object v = e.getValue();
            return Objects.equals(Matching.this.get(k), v);
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Entry<?, ?> e)) {
                return false;
            }

            Object k = e.getKey();
            return Matching.this.remove(k) != null;
        }

        private class MatchingEntrySetItr implements Iterator<Entry<Integer, Integer>> {
            private final Iterator<Edge> itr = Matching.this.iterator();

            private Edge    last       = null;
            private boolean didReverse = true;

            @Override
            public boolean hasNext() {
                return !didReverse || itr.hasNext();
            }

            @Override
            public Entry<Integer, Integer> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }

                if (didReverse) {
                    // next edge
                    last = itr.next();
                    didReverse = false;
                    return Map.entry(last.vertex1(), last.vertex2());
                } else {
                    // flip current edge
                    didReverse = true;
                    return Map.entry(last.vertex2(), last.vertex1());
                }
            }

            @Override
            public void remove() {
                itr.remove();
                didReverse = true;
            }
        }
    }

    private class MatchingEdgeSet extends AbstractSet<Edge> {
        @Override
        public int size() {
            return Matching.this.edgeCount;
        }

        @Override
        public Iterator<Edge> iterator() {
            return new MatchingItr();
        }

        @Override
        public boolean add(Edge e) {
            if (contains(e)) {
                return false;
            }

            put(e);
            return true;
        }

        @Override
        public boolean contains(Object o) {
            return o instanceof Edge e && Matching.this.contains(e);
        }

        @Override
        public boolean remove(Object o) {
            return o instanceof Edge e && Matching.this.remove(e);
        }
    }
}
