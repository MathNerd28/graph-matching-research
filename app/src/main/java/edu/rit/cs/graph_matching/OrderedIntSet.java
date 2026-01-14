package edu.rit.cs.graph_matching;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A custom self-ordered, growable list of ints that prohibits unique elements.
 * Supports O(n) writes, O(log n) searches, O(1) iteration, and uses primitive
 * ints to reduce memory overhead.
 * <p>
 * When adding/removing elements, the elements are self-sorted. Thus, users have
 * no control over the ordering of the collection.
 */
public class OrderedIntSet extends AbstractSet<Integer> {
  /** The default initial capacity, if none is specified. */
  private static final int    DEFAULT_CAPACITY = 8;
  /** The factor of growth each time the capacity is exceeded. */
  private static final double GROWTH_FACTOR    = 2;

  /** The data array, always kept in sorted order */
  private int[] data;
  /** The current number of elements in this list */
  private int   size;

  /**
   * Construct an OrderedIntSet with zero elements and a specified initial
   * capacity.
   *
   * @param initialCapacity
   *   the number of elements this set should be able to hold before growing
   */
  public OrderedIntSet(int initialCapacity) {
    this.data = new int[initialCapacity];
    this.size = 0;
  }

  /**
   * Construct an OrderedIntSet with zero elements and the default initial
   * capacity.
   */
  public OrderedIntSet() {
    this(DEFAULT_CAPACITY);
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public void clear() {
    this.size = 0;
  }

  /**
   * Gets the integer at the specified index in the ordering.
   *
   * @param index
   *   the index of the element to get
   * @return the element at that index
   * @throws IndexOutOfBoundsException
   *   if the index is negative, or greater than or equal to {@link #size()}
   */
  public int get(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(index);
    }
    return data[index];
  }

  @Override
  public boolean add(Integer e) {
    if (data.length == size) {
      grow();
    }

    // Shortcut for common case where this is the new largest element
    if (size == 0 || data[size - 1] < e) {
      data[size] = e;
      size++;
      return true;
    }

    int index = binarySearch(e);
    if (index < size && data[index] == e) {
      return false;
    }

    System.arraycopy(data, index, data, index + 1, size - index);
    data[index] = e;
    size++;
    return true;
  }

  @Override
  public boolean remove(Object o) {
    if (!(o instanceof Integer e)) {
      return false;
    }

    int index = binarySearch(e);
    if (index < size && data[index] != e) {
      return false;
    }

    System.arraycopy(data, index + 1, data, index, size - index - 1);
    size--;
    return true;
  }

  @Override
  public boolean contains(Object o) {
    if (!(o instanceof Integer e)) {
      return false;
    }

    int index = binarySearch(e);
    return index < size && data[index] == e;
  }

  /**
   * Searches for an element in the data array using binary search. If the
   * element is present, returns its index; otherwise, returns the index at
   * which the element should be inserted.
   *
   * @param target
   *   the element to find
   * @return the index of the element, or if not present then the index it
   *   should be inserted
   */
  private int binarySearch(int target) {
    int low = 0;
    int high = size;
    while (low < high) {
      int mid = (low + high) / 2;
      if (data[mid] < target) {
        low = mid + 1;
      } else {
        high = mid;
      }
    }
    return low;
  }

  /**
   * Grows this set by {@link #GROWTH_FACTOR}.
   */
  private void grow() {
    int newCapacity = (int) (data.length * GROWTH_FACTOR);
    this.data = Arrays.copyOf(data, newCapacity);
  }

  @Override
  public Iterator<Integer> iterator() {
    return new OrderedIntSetIterator();
  }

  /**
   * An iterator implementation for {@link OrderedIntSet}.
   */
  private class OrderedIntSetIterator implements Iterator<Integer> {
    /** The index of the next element to return */
    private int index = 0;

    @Override
    public boolean hasNext() {
      return index < size;
    }

    @Override
    public Integer next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }

      int next = data[index];
      index++;
      return next;
    }

    @Override
    public void remove() {
      OrderedIntSet.this.remove(index);
      index--;
    }
  }
}
