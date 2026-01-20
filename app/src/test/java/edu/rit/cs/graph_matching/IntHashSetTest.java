package edu.rit.cs.graph_matching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntHashSetTest {
  /**
   * Check that the load factor is based on the number of empty slots, not the
   * number of occupied slots. (Some table entries are marked as deleted, but
   * still need to be traversed when checking if a key is present.)
   */
  @Test
  @Timeout(1) // detect infinite loops
  void checkLoadFactorCondition() {
    IntHashSet set = new IntHashSet();
    for (int i = 0; i < 1024; i++) {
      assertTrue(set.add(i));
      assertTrue(set.remove(i));
    }
  }

  @Test
  void testTypeRejection() {
    IntHashSet set = new IntHashSet();

    assertTrue(set.add(0));
    assertTrue(set.contains(0));
    assertTrue(set.remove(0));

    assertTrue(set.add(Integer.valueOf(0)));
    assertTrue(set.contains(Integer.valueOf(0)));
    assertTrue(set.remove(Integer.valueOf(0)));
    assertFalse(set.contains(Integer.valueOf(0)));

    assertThrows(NullPointerException.class, () -> set.add(null));
    assertFalse(set.contains(null));
    assertFalse(set.remove(null));

    assertFalse(set.contains(new Object()));
    assertFalse(set.remove(new Object()));
  }

  @ParameterizedTest
  @ValueSource(ints = { 10, 100, 1_000, 10_000, 100_000, 1_000_000 })
  void testSequentialData(int size) {
    IntHashSet set = new IntHashSet();
    for (int i = 0; i < size; i++) {
      assertFalse(set.contains(i));
      assertTrue(set.add(i));
      assertTrue(set.contains(i));
      assertFalse(set.add(i));
      assertTrue(set.contains(i));
    }

    assertEquals(size, set.size());
    for (int i = 0; i < size; i++) {
      assertTrue(set.contains(i));
    }
    for (int i = size; i < 2 * size; i++) {
      assertFalse(set.contains(i));
    }

    Random random = new Random(size);
    for (int i = 0; i < size; i++) {
      int num = set.getRandom(random);
      assertTrue(set.contains(num));
    }

    set.clear();
    assertEquals(0, set.size());
    assertThrows(NoSuchElementException.class, () -> set.getRandom(random));

    for (int i = 0; i < size; i++) {
      assertFalse(set.contains(i));
      assertTrue(set.add(i));
      assertTrue(set.contains(i));
      assertTrue(set.remove(i));
      assertFalse(set.contains(i));
    }
    assertTrue(set.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(ints = { 10, 100, 1_000, 10_000, 100_000, 1_000_000 })
  void testRandomData(int size) {
    Random random = new Random(size);

    LinkedHashSet<Integer> values = new LinkedHashSet<>(size);
    IntHashSet set = new IntHashSet(size);

    while (values.size() < size) {
      int num = random.nextInt();

      if (values.contains(num)) {
        assumeTrue(values.remove(num));

        assertTrue(set.contains(num));
        assertFalse(set.add(num));
        assertTrue(set.remove(num));
        assertFalse(set.contains(num));
      } else {
        assumeTrue(values.add(num));

        assertFalse(set.contains(num));
        assertFalse(set.remove(num));
        assertTrue(set.add(num));
        assertTrue(set.contains(num));
      }
    }

    assumeTrue(values.size() == size);

    assertEquals(size, set.size());
    for (int num : values) {
      assertTrue(set.contains(num));
    }

    for (int i = 0; i < size; i++) {
      int num = set.getRandom(random);
      assertTrue(set.contains(num));
    }

    set.removeAll(values);
    assertTrue(set.isEmpty());
    assertThrows(NoSuchElementException.class, () -> set.getRandom(random));
  }
}
