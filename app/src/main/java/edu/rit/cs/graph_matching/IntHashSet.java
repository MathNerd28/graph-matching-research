package edu.rit.cs.graph_matching;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Random;

/**
 * A performant and memory-efficient hash set implementation specialized for
 * primitive ints. This implementation reduces a significant amount of memory
 * overhead from the standard {@link HashSet} that forces the use of boxed
 * {@link Integer}s.
 * <p>
 * The iterators returned by this class's {@code iterator} method are
 * <i>fail-fast</i>: if the set is structurally modified at any time after the
 * iterator is created, in any way except through the Iterator's own
 * {@code remove} or {@code add} methods, the iterator will throw a
 * {@link ConcurrentModificationException}. Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the future.
 * <p>
 * Note that the fail-fast behavior of an iterator cannot be guaranteed as it
 * is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification. Fail-fast iterators throw
 * {@code ConcurrentModificationException} on a best-effort basis. Therefore, it
 * would be wrong to write a program that depended on this exception for its
 * correctness: <i>the fail-fast behavior of iterators should be used only to
 * detect bugs.</i>
 */
public class IntHashSet extends AbstractSet<Integer> {
  /** The proportion of the hash table to fill before increasing its size */
  private static final double LOAD_FACTOR = 0.75;

  /**
   * The hash table. The array indexes are the hashes, and the array values are
   * pointers to the value in {@link #values}.
   */
  private int[] table;

  /**
   * Tracks whether each position in {@link #table} contains a live value.
   */
  private final BitSet occupied;

  /**
   * Tracks whether each position in {@link #table} contains a deleted value
   * that hasn't yet been reclaimed.
   */
  private final BitSet deleted;

  /** The bitmask applied to hashes to generate table indexes. */
  private int mask;

  /** The maximum permissible size of the hash table before growth. */
  private int maxFill;

  /** The current number of empty cells in the hash table. */
  private int empty;

  /**
   * The actual values in the hash table. All values in the range [0, size) can
   * be safely iterated over; further values were previously deleted but not yet
   * reclaimed.
   */
  private int[] values;

  /** The current number of valid values in the hash table. */
  private int size;

  // --- Public API ---

  /**
   * Construct an IntHashSet that is capable of holding expectedSize elements
   * without rehashing or growing.
   *
   * @param expectedSize
   *   the expected number of elements
   */
  public IntHashSet(int expectedSize) {
    int capacity = tableSizeFor(expectedSize);
    occupied = new BitSet(capacity);
    deleted = new BitSet(capacity);
    values = new int[expectedSize];
    createTable(capacity);
  }

  /**
   * Construct an IntHashSet with a default initial capacity.
   */
  public IntHashSet() {
    this(4);
  }

  /**
   * Get a random integer contained in this set with uniform probability.
   *
   * @param rd
   *   the {@link Random} to use to choose an element
   * @return a random integer from this set
   * @throws NoSuchElementException
   *   if this set contains no elements
   */
  public int getRandom(Random rd) {
    if (isEmpty()) {
      throw new NoSuchElementException();
    }

    return values[rd.nextInt(size)];
  }

  /**
   * Adds the specified element to this set if it is not already present. More
   * formally, adds the specified element {@code e} to this set if the set
   * contains no element {@code e2} such that {@code Objects.equals(e, e2)}. If
   * this set already contains the element, the call leaves the set unchanged
   * and returns {@code false}. In combination with the restriction on
   * constructors, this ensures that sets never contain duplicate elements.
   *
   * @param e
   *   element to be added to this set
   * @return {@code true} if this set did not already contain the specified
   *   element
   * @throws NullPointerException
   *   if the specified element is null
   * @implSpec As this set only supports primitive int values, this method will
   *   reject {@code null} as a value.
   */
  @Override
  public boolean add(Integer e) {
    return add(e.intValue());
  }

  /**
   * Adds the specified integer to this set if it is not already present. If
   * this set already contains the integer, the call leaves the set unchanged
   * and returns {@code false}. In combination with the restriction on
   * constructors, this ensures that sets never contain duplicate integers.
   *
   * @param e
   *   integer to be added to this set
   * @return {@code true} if this set did not already contain the specified
   *   integer
   */
  public boolean add(int e) {
    if (table.length - empty >= maxFill) {
      rehashTable();
    }
    if (size >= values.length) {
      growValues();
    }

    int index = hash1(e) & mask;
    int increment = hash2(e);
    int firstDeleted = -1;

    while (true) {
      if (deleted.get(index)) {
        if (firstDeleted < 0) {
          firstDeleted = index;
        }
      } else if (occupied.get(index)) {
        if (values[table[index]] == e) {
          // value is already present
          return false;
        }
      } else {
        // value not present; add it

        if (firstDeleted >= 0) {
          index = firstDeleted;
        } else {
          empty--;
        }

        table[index] = size;
        values[size] = e;
        deleted.clear(index);
        occupied.set(index);
        size++;
        return true;
      }

      index = (index + increment) & mask;
    }
  }

  /**
   * Returns {@code true} if this set contains the specified element. More
   * formally, returns {@code true} if and only if this set contains an element
   * {@code e} such that {@code Objects.equals(o, e)}.
   *
   * @param o
   *   element whose presence in this set is to be tested
   * @return {@code true} if this set contains the specified element
   */
  @Override
  public boolean contains(Object o) {
    return o instanceof Integer i && contains(i.intValue());
  }

  /**
   * Returns {@code true} if this set contains the specified integer.
   *
   * @param e
   *   integer whose presence in this set is to be tested
   * @return {@code true} if this set contains the specified integer
   */
  public boolean contains(int e) {
    int index = hash1(e) & mask;
    int increment = hash2(e);
    while (true) {
      if (!occupied.get(index) && !deleted.get(index)) {
        // empty slot
        return false;
      }
      if (occupied.get(index) && values[table[index]] == e) {
        // found value
        return true;
      }
      index = (index + increment) & mask;
    }
  }

  /**
   * Removes the specified element from this set if it is present. More
   * formally, removes an element {@code e} such that
   * {@code Objects.equals(o, e)}, if this set contains such an element. Returns
   * {@code true} if this set contained the element (or equivalently, if this
   * set changed as a result of the call). (This set will not contain the
   * element once the call returns.)
   *
   * @param o
   *   object to be removed from this set, if present
   * @return {@code true} if this set contained the specified element
   */
  @Override
  public boolean remove(Object o) {
    return o instanceof Integer i && remove(i.intValue());
  }

  /**
   * Removes the specified integer from this set if it is present. Returns
   * {@code true} if this set contained the integer (or equivalently, if this
   * set changed as a result of the call). (This set will not contain the
   * integer once the call returns.)
   *
   * @param e
   *   integer to be removed from this set, if present
   * @return {@code true} if this set contained the specified integer
   */
  public boolean remove(int e) {
    int index = hash1(e) & mask;
    int increment = hash2(e);
    while (true) {
      if (!occupied.get(index) && !deleted.get(index)) {
        // empty slot
        return false;
      }
      if (occupied.get(index) && values[table[index]] == e) {
        // found value
        int removeIndex = table[index];

        if (removeIndex != size - 1) {
          // swap last value into removed slot
          int toMove = values[size - 1];
          updateIndex(toMove, removeIndex);
          values[removeIndex] = toMove;
        }

        occupied.clear(index);
        deleted.set(index);
        size--;
        return true;
      }

      index = (index + increment) & mask;
    }
  }

  /**
   * Removes all of the elements from this set. The set will be empty after this
   * call returns.
   */
  @Override
  public void clear() {
    size = 0;
    empty = table.length;
    occupied.clear();
    deleted.clear();
  }

  /**
   * Returns an iterator over the elements in this set. The elements are
   * returned in no particular order.
   *
   * @return an iterator over the elements in this set
   */
  @Override
  public PrimitiveIterator.OfInt iterator() {
    return new IntHashSetIterator();
  }

  @Override
  public int size() {
    return size;
  }

  // --- Implementation-only details ---

  /**
   * Initialize the hash table with the specified capacity, and clear it of all
   * entries. Preserve {@link #values}.
   *
   * @param capacity
   *   the capacity of the table, a power of 2
   */
  private void createTable(int capacity) {
    mask = capacity - 1;
    maxFill = (int) (capacity * LOAD_FACTOR);
    table = new int[capacity];
    size = 0;
    empty = capacity;
    occupied.clear();
    deleted.clear();
  }

  /**
   * Update the position of an integer in the {@link #values} array.
   *
   * @param e
   *   the integer to update
   * @param newIndex
   *   the new index of that element in {@link #values}
   * @implNote {@code e} must remain in its old position when calling this
   *   method.
   */
  private void updateIndex(int e, int newIndex) {
    int index = hash1(e) & mask;
    int increment = hash2(e);
    while (true) {
      if (occupied.get(index) && values[table[index]] == e) {
        table[index] = newIndex;
        return;
      }
      index = (index + increment) & mask;
    }
  }

  /**
   * Increase the size of the {@link #values} array to accommodate more
   * elements. All integers within this set are copied to the same index in the
   * new array.
   *
   * @implNote This method doubles the size of {@link #values}.
   */
  private void growValues() {
    int[] newValues = new int[values.length << 1];
    System.arraycopy(values, 0, newValues, 0, size);
    values = newValues;
  }

  /**
   * Increase the size of {@link #table} to accommodate more elements. All
   * integers within this set are copied to the new table, but not necessarily
   * to the same location.
   *
   * @implNote This method doubles the size of {@link #table}.
   * @implNote {@link #values} is preserved as-is.
   */
  private void rehashTable() {
    int oldSize = size;
    createTable(table.length << 1); // double the previous size

    // re-add previous elements
    // it's safe to iterate over values, because elements will be re-inserted
    // at the same position
    for (int i = 0; i < oldSize; i++) {
      add(values[i]);
    }
  }

  /**
   * The hash function used for initial bucket selection. Optimized for
   * performance over low bias.
   *
   * @param x
   *   the integer to hash
   * @return the hash of that integer
   * @implNote This function is a bijection and easily reversible, and therefore
   *   vulnerable against maliciously-constructed data.
   */
  private static int hash1(int x) {
    x ^= x << 13;
    x ^= x >>> 17;
    x ^= x << 5;
    return x;
  }

  /**
   * The hash function used for linear probing increments. Optimized for
   * performance over low bias.
   *
   * @param x
   *   the integer to hash
   * @return the hash of that integer
   * @implSpec The hash table size is always a power of 2; since the outputs of
   *   this function must be coprime with all powers of 2, this function must
   *   always return an odd integer.
   * @implNote This function is easily reversible, and therefore vulnerable
   *   against maliciously-constructed data.
   */
  private static int hash2(int x) {
    x ^= x << 15;
    x ^= x >>> 12;
    x ^= x << 7;
    return x | 1;
  }

  /**
   * Calculate a power-of-two table size that can contain a given number of
   * integers.
   *
   * @param expected
   *   the number of elements
   * @return the size of the hash table
   */
  private static int tableSizeFor(int expected) {
    int minCapacity = (int) (expected / LOAD_FACTOR);
    int n = -1 >>> Integer.numberOfLeadingZeros(minCapacity - 1);
    return (n < 0) ? 1 : (n + 1);
  }

  /**
   * An iterator implementation for {@link IntHashSet}.
   */
  private final class IntHashSetIterator implements PrimitiveIterator.OfInt {
    int     size       = IntHashSet.this.size;
    int     pos        = 0;
    boolean calledNext = false;

    @Override
    public boolean hasNext() {
      checkModification();
      return pos < size;
    }

    @Override
    public int nextInt() {
      checkModification();

      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      int value = values[pos];
      pos++;
      calledNext = true;
      return value;
    }

    /**
     * Removes from the underlying collection the last element returned by this
     * iterator. This method can be called only once per call to {@link #next}.
     * <p>
     * The behavior of an iterator is unspecified if the underlying collection
     * is modified while the iteration is in progress in any way other than by
     * calling this method.
     * <p>
     * The behavior of an iterator is unspecified if this method is called after
     * a call to the {@link #forEachRemaining forEachRemaining} method.
     *
     * @throws IllegalStateException
     *   if the {@code next} method has not yet been called, or the
     *   {@code remove} method has already been called after the last call to
     *   the {@code next} method
     */
    @Override
    public void remove() {
      checkModification();

      if (!calledNext) {
        throw new IllegalStateException();
      }

      pos--;
      size--;
      calledNext = false;
      IntHashSet.this.remove(values[pos]);
    }

    /**
     * Detects some concurrent modifications. If a concurrent modification is
     * detected, a {@link ConcurrentModificationException} is thrown.
     * <p>
     * Note that the fail-fast behavior of an iterator cannot be guaranteed as
     * it is, generally speaking, impossible to make any hard guarantees in the
     * presence of unsynchronized concurrent modification. Fail-fast iterators
     * throw {@code ConcurrentModificationException} on a best-effort basis.
     * Therefore, it would be wrong to write a program that depended on this
     * exception for its correctness: <i>the fail-fast behavior of iterators
     * should be used only to detect bugs.</i>
     *
     * @throws ConcurrentModificationException
     *   if a concurrent modification was detected
     */
    private void checkModification() {
      if (size != IntHashSet.this.size) {
        // someone else must have modified the set during iteration
        throw new ConcurrentModificationException();
      }
    }
  }
}
