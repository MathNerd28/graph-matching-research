package edu.rit.cs.graph_matching;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeSet;

public class OrderedIntSet extends AbstractSet<Integer> {
  private static final int DEFAULT_CAPACITY = 8;
  private static final double GROWTH_FACTOR = 2;

  private int[] data;
  private int   size;

  public OrderedIntSet(int capacity) {
    this.data = new int[capacity];
    this.size = 0;
  }

  public OrderedIntSet() {
    this(DEFAULT_CAPACITY);
  }

  @Override
  public int size() {
    return size;
  }

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
    if (size == 0 || data[size-1] < e) {
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

  private void grow() {
    int newCapacity = (int) (data.length * GROWTH_FACTOR);
    this.data = Arrays.copyOf(data, newCapacity);
  }

  @Override
  public Iterator<Integer> iterator() {
    return new OrderedIntSetIterator();
  }

  private class OrderedIntSetIterator implements Iterator<Integer> {
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

  public static void main(String[] args) {
    int attempts = 10;
    int elements = 1000_000;
    Random rd = new Random();

    for (int i = 0; i < attempts; i++) {
      long start = System.currentTimeMillis();
      OrderedIntSet set = new OrderedIntSet();
      for (int j = 0; j < elements; j++) {
        // set.add(rd.nextInt());
        set.add(j);
      }
      long end = System.currentTimeMillis();
      System.out.println("Custom:" + (end - start));
    }

    for (int i = 0; i < attempts; i++) {
      long start = System.currentTimeMillis();
      TreeSet<Integer> set = new TreeSet<>();
      for (int j = 0; j < elements; j++) {
        // set.add(rd.nextInt());
        set.add(j);
      }
      long end = System.currentTimeMillis();
      System.out.println("TreeSet:" + (end - start));
    }

    for (int i = 0; i < attempts; i++) {
      long start = System.currentTimeMillis();
      LinkedHashSet<Integer> set = new LinkedHashSet<>();
      for (int j = 0; j < elements; j++) {
        // set.add(rd.nextInt());
        set.add(j);
      }
      long end = System.currentTimeMillis();
      System.out.println("LinkedHashSet:" + (end - start));
    }

    for (int i = 0; i < attempts; i++) {
      long start = System.currentTimeMillis();
      HashSet<Integer> set = new HashSet<>();
      for (int j = 0; j < elements; j++) {
        // set.add(rd.nextInt());
        set.add(j);
      }
      long end = System.currentTimeMillis();
      System.out.println("HashSet:" + (end - start));
    }
  }
}
