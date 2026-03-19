package edu.rit.cs.graph_matching.util;

import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.PrimitiveIterator;

/**
 * A queue of integers that prohibits duplicate elements.
 */
public class IntSetQueue extends AbstractQueue<Integer> {
    /** next[e] is e's successor element */
    private final int[] next;

    /** prev[e] is e's preceding element */
    private final int[] prev;

    /** The next element in the queue */
    private int head;

    /** The number of elements remaining in the queue */
    private int size;

    /** The number of times this set has been modified. */
    private int modCount = 0;

    /**
     * Construct an empty IntSetQueue.
     *
     * @param n
     *     the maximum element in the queue
     */
    public IntSetQueue(int n) {
        this.next = new int[n];
        this.prev = new int[n];

        this.head = -1;
        this.size = 0;

        Arrays.fill(next, -1);
        Arrays.fill(prev, -1);
    }

    /**
     * Check if an element is present in the queue.
     *
     * @param e
     *     the element to check
     * @return true if the element is currently enqueued
     */
    public boolean contains(int e) {
        return e >= 0 && e < next.length && next[e] >= 0;
    }

    /**
     * @return the number of elements currently enqueued
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * Retrieves and removes the head of this queue, or returns {@code null} if
     * this queue is empty.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    @Override
    public Integer poll() {
        int e = pollInt();
        return e < 0 ? null : Integer.valueOf(e);
    }

    /**
     * Retrieves and removes the head of this queue, or returns -1 if this queue
     * is empty.
     *
     * @return the head of this queue, or -1 if this queue is empty
     */
    public int pollInt() {
        int e = head;
        if (head >= 0) {
            remove(e);
        }
        return e;
    }

    /**
     * Inserts the specified element into this queue.
     *
     * @param e
     *     the element to add
     * @return {@code true} if the element was added to this queue, else
     *     {@code false}
     * @throws IllegalArgumentException
     *     if some property of this element prevents it from being added to this
     *     queue
     */
    public boolean offer(int e) {
        if (e < 0 || e >= prev.length || contains(e)) {
            // Invalid or duplicate element
            throw new IllegalArgumentException();
        }

        if (head < 0) {
            // Only element in queue
            head = e;
            prev[e] = e;
            next[e] = e;
        } else {
            // Append element to the end of the queue
            next[prev[head]] = e;
            prev[e] = prev[head];
            next[e] = head;
            prev[head] = e;
        }

        size++;
        modCount++;
        return true;
    }

    /**
     * Removes the specified element from this queue, if it is present. More
     * formally, removes an element {@code e} such that
     * {@code Objects.equals(o, e)}, if this queue contains one or more such
     * elements. Returns {@code true} if this queue contained the specified
     * element (or equivalently, if this queue changed as a result of the call).
     *
     * @param o
     *     element to be removed from this queue, if present
     * @return {@code true} if an element was removed as a result of this call
     */
    @Override
    public boolean remove(Object o) {
        return o instanceof Integer e && remove(e.intValue());
    }

    /**
     * Removes the specified element from this queue, if it is present. Returns
     * {@code true} if this queue contained the specified element (or
     * equivalently, if this queue changed as a result of the call).
     *
     * @param e
     *     element to be removed from this queue, if present
     * @return {@code true} if an element was removed as a result of this call
     */
    public boolean remove(int e) {
        if (!contains(e)) {
            return false;
        }

        // Update head if necessary
        if (head == e) {
            head = next[head];

            if (head == e) {
                // No more elements
                head = -1;
            }
        }

        // Connect the previous element to the next element
        if (prev[e] != -1) {
            next[prev[e]] = next[e];
        }

        // Connect the next element to the previous element
        if (next[e] != -1) {
            prev[next[e]] = prev[e];
        }

        // Mark element as deleted
        next[e] = -1;
        prev[e] = -1;
        size--;
        modCount++;
        return true;
    }

    /**
     * Inserts the specified element into this queue.
     *
     * @param e
     *     the element to add
     * @return {@code true} if the element was added to this queue, else
     *     {@code false}
     * @throws IllegalArgumentException
     *     if some property of this element prevents it from being added to this
     *     queue
     */
    @Override
    public boolean offer(Integer e) {
        if (e == null) {
            throw new IllegalArgumentException();
        }

        return offer(e.intValue());
    }

    /**
     * Retrieves, but does not remove, the head of this queue, or returns -1 if
     * this queue is empty.
     *
     * @return the head of this queue, or -1 if this queue is empty
     */
    public int peekInt() {
        return head;
    }

    /**
     * Retrieves, but does not remove, the head of this queue, or returns
     * {@code null} if this queue is empty.
     *
     * @return the head of this queue, or {@code null} if this queue is empty
     */
    @Override
    public Integer peek() {
        return (head < 0) ? null : head;
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return new IntSetQueueItr();
    }

    /**
     * An iterator over the elements in an IntSetQueue
     */
    private class IntSetQueueItr implements PrimitiveIterator.OfInt {
        private int modCount = IntSetQueue.this.modCount;

        private int     val       = IntSetQueue.this.head;
        private boolean canRemove = false;

        @Override
        public boolean hasNext() {
            checkForComodification();
            return val >= 0;
        }

        @Override
        public int nextInt() {
            checkForComodification();

            int ret = val;
            val = next[val];
            canRemove = true;

            if (val == head) {
                val = -1;
            }

            checkForComodification();
            return ret;
        }

        @Override
        public void remove() {
            checkForComodification();

            if (!canRemove) {
                throw new IllegalStateException();
            }

            if (val < 0) {
                // Last element
                IntSetQueue.this.remove(prev[head]);
            } else {
                IntSetQueue.this.remove(prev[val]);
            }

            canRemove = false;
            modCount++;
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
            if (modCount != IntSetQueue.this.modCount) {
                // someone else must have modified the set during iteration
                throw new ConcurrentModificationException();
            }
        }
    }
}
