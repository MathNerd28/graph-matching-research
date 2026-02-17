package edu.rit.cs.graph_matching;

import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.random.RandomGenerator;

public class IntArraySet extends AbstractIntSet {
    private static final int DEFAULT_SIZE = 4;

    /** The backing array. */
    private int[] data;

    /** The current size of this set. */
    private int size;

    /** The number of times this set has been modified. */
    private int modCount = 0;

    /**
     * Construct an IntArraySet with a default initial capacity.
     */
    public IntArraySet() {
        this.data = new int[DEFAULT_SIZE];
        this.size = 0;
    }

    /**
     * Construct an IntArraySet that is capable of holding expectedSize elements
     * without growing.
     *
     * @param expectedSize
     *     the expected number of elements
     */
    public IntArraySet(int expectedSize) {
        this.data = new int[expectedSize];
        this.size = 0;
    }

    /**
     * Construct an IntArraySet that contains all of the specified elements.
     *
     * @param c
     *     the elements to add
     */
    public IntArraySet(Collection<Integer> c) {
        this(c.size());
        addAll(c);
    }

    @Override
    public boolean add(int e) {
        if (contains(e)) {
            return false;
        }

        if (data.length <= size) {
            data = Arrays.copyOf(data, data.length << 1);
        }

        data[size] = e;
        size++;
        modCount++;
        return true;
    }

    @Override
    public boolean contains(int e) {
        for (int i = 0; i < size; i++) {
            if (data[i] == e) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean remove(int e) {
        for (int i = 0; i < size; i++) {
            if (data[i] == e) {
                size--;
                data[i] = data[size];
                return true;
            }
        }
        return false;
    }

    @Override
    public int getRandom(RandomGenerator rd) {
        if (isEmpty()) {
            throw new NoSuchElementException();
        }

        return data[rd.nextInt(size)];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new IntArrayIterator();
    }

    // Make explicit that we want to use AbstractSet's equals & hashCode
    // implementations

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    private class IntArrayIterator implements PrimitiveIterator.OfInt {
        private final int modCount = IntArraySet.this.modCount;

        private int pos = 0;

        @Override
        public boolean hasNext() {
            checkForComodification();

            return pos < size;
        }

        @Override
        public int nextInt() {
            checkForComodification();

            int e = data[pos];
            pos++;
            return e;
        }

        @Override
        public void remove() {
            checkForComodification();

            pos--;
            size--;
            data[pos] = data[size];

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
            if (modCount != IntArraySet.this.modCount) {
                // someone else must have modified the set during iteration
                throw new ConcurrentModificationException();
            }
        }
    }
}
