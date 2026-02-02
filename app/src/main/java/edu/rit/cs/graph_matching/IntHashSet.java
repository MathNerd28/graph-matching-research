package edu.rit.cs.graph_matching;

import java.util.AbstractSet;
import java.util.BitSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.random.RandomGenerator;

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
  /** The maximum size/capacity ratio before increasing capacity */
  private static final double MAX_LOAD_FACTOR = 0.75;

  /** The maximum (size+deleted)/capacity ratio before rehashing */
  private static final double REHASH_LOAD_FACTOR = 0.8;

  /** The minimum size/capacity ratio before decreasing capacity */
  private static final double MIN_LOAD_FACTOR  = 0.25;

  /** The minimum capacity of the hash table */
  private static final int MIN_CAPACITY = 8;

  /**
   * The hash table. The array indexes are the hashes, and the array values are
   * the values in the set.
   */
  private int[] table;

  /**
   * Tracks whether each position in {@link #table} contains a live value.
   */
  private BitSet occupied;

  /**
   * Tracks whether each position in {@link #table} contains a deleted value
   * that hasn't yet been reclaimed.
   */
  private BitSet deleted;

  /** The maximum size of the hash table before growth. */
  private int maxFill;

  /** The maximum (size+deleted) of the hash table before rehashing. */
  private int rehashFill;

  /** The minimum size of the hash table before shrinking. */
  private int minFill;

  /**
   * The current number of deleted cells in the hash table. Equal to
   * {@link #deleted}.{@link BitSet#cardinality() cardinality()}.
   */
  private int deletedCount;

  /**
   * The current number of values in the hash table. Equal to
   * {@link #occupied}.{@link BitSet#cardinality() cardinality()}.
   */
  private int occupiedCount;

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
    createTable(capacity);
  }

  /**
   * Construct an IntHashSet with a default initial capacity.
   */
  public IntHashSet() {
    createTable(MIN_CAPACITY);
  }

  /**
   * Get a random integer contained in this set with uniform probability.
   *
   * @param rd
   *   the random number generator to use to choose an element
   * @return a random integer from this set
   * @throws NoSuchElementException
   *   if this set contains no elements
   */
  public int getRandom(RandomGenerator rd) {
    if (isEmpty()) {
      throw new NoSuchElementException();
    }

    int index;
    do {
      index = rd.nextInt(table.length);
    } while (!occupied.get(index));

    return table[index];
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
    if (occupiedCount >= maxFill) {
      // increase size of table
      rehashTable(table.length << 1);
    } else if (occupiedCount + deletedCount >= rehashFill) {
      // reclaim all deleted cells
      rehashTable(table.length);
    }

    int mask = table.length - 1;
    int index = hash1(e) & mask;
    int increment = hash2(e) & mask;
    int firstDeleted = -1;

    while (true) {
      if (deleted.get(index)) {
        if (firstDeleted < 0) {
          firstDeleted = index;
        }
      } else if (occupied.get(index)) {
        if (table[index] == e) {
          // value is already present
          return false;
        }
      } else {
        // value not present; add it

        if (firstDeleted >= 0) {
          index = firstDeleted;
          deleted.clear(index);
          deletedCount--;
        }

        table[index] = e;
        occupied.set(index);
        occupiedCount++;
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
    int mask = table.length - 1;
    int index = hash1(e) & mask;
    int increment = hash2(e) & mask;
    while (true) {
      if (!occupied.get(index) && !deleted.get(index)) {
        // empty slot
        return false;
      }
      if (occupied.get(index) && table[index] == e) {
        // found value
        return true;
      }
      index = (index + increment) & mask;
    }
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    boolean removed = false;
    for (Object o : c) {
      removed |= remove(o);
    }
    return removed;
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
    int mask = table.length - 1;
    int index = hash1(e) & mask;
    int increment = hash2(e) & mask;
    while (true) {
      if (!occupied.get(index) && !deleted.get(index)) {
        // empty slot
        return false;
      }
      if (occupied.get(index) && table[index] == e) {
        occupied.clear(index);
        deleted.set(index);
        occupiedCount--;
        deletedCount++;

        if (occupiedCount < minFill) {
          // decrease size of table
          rehashTable(table.length >>> 1);
        }

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
    occupiedCount = 0;
    deletedCount = 0;
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
    return occupiedCount;
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

  // --- Implementation-only details ---

  /**
   * Initialize the hash table with the specified capacity, and clear it of all
   * entries.
   *
   * @param capacity
   *   the capacity of the table, a power of 2
   */
  private void createTable(int capacity) {
    if ((capacity & (capacity - 1)) != 0) {
      throw new IllegalArgumentException("capacity is not a power of 2");
    }

    table = new int[capacity];
    occupied = new BitSet(capacity);
    deleted = new BitSet(capacity);

    maxFill = (int) (capacity * MAX_LOAD_FACTOR);
    rehashFill = (int) (capacity * REHASH_LOAD_FACTOR);
    minFill = (capacity <= MIN_CAPACITY) ? 0 : (int) (capacity * MIN_LOAD_FACTOR);

    occupiedCount = 0;
    deletedCount = 0;
  }

  /**
   * Reallocate {@link #table} and supporting structure with a new capacity, and
   * re-insert all existing entries. All values that were previously present in
   * this set will still be present after this operation, but not necessarily in
   * the same location. No cells will be marked 'deleted' after this operation.
   *
   * @param capacity
   *   new capacity, a power of 2
   */
  private void rehashTable(int capacity) {
    if (capacity < MIN_CAPACITY) {
      throw new IllegalArgumentException("capacity must be at least MIN_CAPACITY");
    }

    int[] oldTable = table;
    BitSet oldOccupied = occupied;
    createTable(capacity);

    // re-add previous elements
    oldOccupied.stream()
               .forEach(index -> add(oldTable[index]));
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
    int requiredCapacity = (int) (expected / MAX_LOAD_FACTOR);
    int capacity = (-1 >>> Integer.numberOfLeadingZeros(requiredCapacity - 1)) + 1;
    return (capacity < MIN_CAPACITY) ? MIN_CAPACITY : capacity;
  }

  /**
   * An iterator implementation for {@link IntHashSet}. Does NOT support
   * removal.
   */
  private final class IntHashSetIterator implements PrimitiveIterator.OfInt {
    private final int expectedSize = size();

    private int remaining = size();
    private int pos       = -1;

    @Override
    public boolean hasNext() {
      checkModification();
      return remaining > 0;
    }

    @Override
    public int nextInt() {
      checkModification();

      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      do {
        pos++;
      } while (!occupied.get(pos));

      int value = table[pos];
      remaining--;
      return value;
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
      if (expectedSize != size()) {
        // someone else must have modified the set during iteration
        throw new ConcurrentModificationException();
      }
    }
  }
}
