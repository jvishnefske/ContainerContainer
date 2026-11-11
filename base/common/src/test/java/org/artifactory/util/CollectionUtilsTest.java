package org.artifactory.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * @author Mor Merhav
 */
class CollectionUtilsTest {

    @Test
    void testNewLinkedHashSetStrings() {
        LinkedHashSet<String> linkedHashSet = CollectionUtils.newLinkedHashSet("aab", "aac", "1", "2", "3", "4", "t");
        Assertions.assertEquals(linkedHashSet.size(), 7);
        Iterator<String> iterator = linkedHashSet.iterator();
        Assertions.assertEquals("aab", iterator.next());
        Assertions.assertEquals("aac", iterator.next());
        Assertions.assertEquals("1", iterator.next());
        Assertions.assertEquals("2", iterator.next());
        Assertions.assertEquals("3", iterator.next());
        Assertions.assertEquals("4", iterator.next());
        Assertions.assertEquals("t", iterator.next());
    }

    @Test
    void testNewLinkedHashSetIntegers() {
        LinkedHashSet<Integer> linkedHashSet = CollectionUtils.newLinkedHashSet(4, 3, 2, 1);
        Assertions.assertEquals(linkedHashSet.size(), 4);
        Iterator<Integer> iterator = linkedHashSet.iterator();
        Assertions.assertEquals(4, iterator.next().intValue());
        Assertions.assertEquals(3, iterator.next().intValue());
        Assertions.assertEquals(2, iterator.next().intValue());
        Assertions.assertEquals(1, iterator.next().intValue());
    }

}