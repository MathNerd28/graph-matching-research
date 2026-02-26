package edu.rit.cs.graph_matching.graph;

import java.util.AbstractSet;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.random.RandomGenerator;

public abstract class AbstractIntSet extends AbstractSet<Integer> {
    /**
     * Get a random integer contained in this set with uniform probability.
     *
     * @param rd
     *     the random number generator to use to choose an element
     * @return a random integer from this set
     * @throws NoSuchElementException
     *     if this set contains no elements
     */
    public abstract int getRandom(RandomGenerator rd);

    /**
     * Adds the specified element to this set if it is not already present. More
     * formally, adds the specified element {@code e} to this set if the set
     * contains no element {@code e2} such that {@code Objects.equals(e, e2)}.
     * If this set already contains the element, the call leaves the set
     * unchanged and returns {@code false}. In combination with the restriction
     * on constructors, this ensures that sets never contain duplicate elements.
     *
     * @param e
     *     element to be added to this set
     * @return {@code true} if this set did not already contain the specified
     *     element
     * @throws NullPointerException
     *     if the specified element is null
     * @implSpec As this set only supports primitive int values, this method
     *     will reject {@code null} as a value.
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
     *     integer to be added to this set
     * @return {@code true} if this set did not already contain the specified
     *     integer
     */
    public abstract boolean add(int e);

    /**
     * Returns {@code true} if this set contains the specified element. More
     * formally, returns {@code true} if and only if this set contains an
     * element {@code e} such that {@code Objects.equals(o, e)}.
     *
     * @param o
     *     element whose presence in this set is to be tested
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
     *     integer whose presence in this set is to be tested
     * @return {@code true} if this set contains the specified integer
     */
    public abstract boolean contains(int e);

    /**
     * Removes the specified element from this set if it is present. More
     * formally, removes an element {@code e} such that
     * {@code Objects.equals(o, e)}, if this set contains such an element.
     * Returns {@code true} if this set contained the element (or equivalently,
     * if this set changed as a result of the call). (This set will not contain
     * the element once the call returns.)
     *
     * @param o
     *     object to be removed from this set, if present
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
     *     integer to be removed from this set, if present
     * @return {@code true} if this set contained the specified integer
     */
    public abstract boolean remove(int e);

    /**
     * Returns an iterator over the elements in this set. The elements are
     * returned in no particular order.
     *
     * @return an iterator over the elements in this set
     */
    @Override
    public abstract PrimitiveIterator.OfInt iterator();

    /**
     * Removes all of the elements from this set. The set will be empty after
     * this call returns.
     */
    @Override
    public void clear() {
        super.clear();
    }
}
