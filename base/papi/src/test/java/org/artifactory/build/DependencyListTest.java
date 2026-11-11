package org.artifactory.build;

import lombok.NonNull;
import org.jfrog.build.api.builder.DependencyBuilder;
import org.jfrog.build.api.Dependency;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyListTest {

    @Test
    void size_shouldReturnZero_whenListEmpty() {
        DependencyList list = new DependencyList(new ArrayList<>());
        assertEquals(0, list.size());
    }

    @Test
    void isEmpty_shouldReturnTrue_whenListEmpty() {
        DependencyList list = new DependencyList(new ArrayList<>());
        assertTrue(list.isEmpty());
    }

    @Test
    void size_shouldReturnElementCount_whenListHasElements() {
        List<Dependency> inner = new ArrayList<>(List.of(
                createApiDependency("dep1", "jar"),
                createApiDependency("dep2", "pom")
        ));
        DependencyList list = new DependencyList(inner);

        assertEquals(2, list.size());
        assertFalse(list.isEmpty());
    }

    @Test
    void get_shouldReturnDependencyAtIndex() {
        Dependency apiDep = createApiDependency("my-dep", "jar");
        DependencyList list = new DependencyList(new ArrayList<>(List.of(apiDep)));

        org.artifactory.build.Dependency wrapper = list.getFirst();

        assertNotNull(wrapper);
        assertEquals("my-dep", wrapper.getId());
        assertEquals("jar", wrapper.getType());
    }

    @Test
    void contains_shouldReturnTrue_whenDependencyInList() {
        Dependency apiDep = createApiDependency("dep", "jar");
        DependencyList list = new DependencyList(new ArrayList<>(List.of(apiDep)));
        org.artifactory.build.Dependency wrapper = new org.artifactory.build.Dependency(apiDep);

        assertTrue(list.contains(wrapper));
    }

    @Test
    void add_shouldAppendAndReturnTrue() {
        DependencyList list = new DependencyList(new ArrayList<>());
        org.artifactory.build.Dependency toAdd = new org.artifactory.build.Dependency(
                createApiDependency("new-dep", "pom"));

        assertTrue(list.add(toAdd));
        assertEquals(1, list.size());
        assertEquals("new-dep", list.getFirst().getId());
    }

    @Test
    void remove_shouldRemoveDependencyAndReturnTrue() {
        Dependency apiDep = createApiDependency("dep", "jar");
        DependencyList list = new DependencyList(new ArrayList<>(List.of(apiDep)));
        org.artifactory.build.Dependency wrapper = new org.artifactory.build.Dependency(apiDep);

        assertTrue(list.remove(wrapper));
        assertEquals(0, list.size());
    }

    @Test
    void clear_shouldRemoveAllElements() {
        List<Dependency> inner = new ArrayList<>(List.of(createApiDependency("x", "jar")));
        DependencyList list = new DependencyList(inner);

        list.clear();

        assertEquals(0, list.size());
        assertTrue(list.isEmpty());
    }

    @Test
    void subList_shouldReturnSublistAsDependencyWrappers() {
        List<Dependency> inner = new ArrayList<>(List.of(
                createApiDependency("a", "jar"),
                createApiDependency("b", "pom"),
                createApiDependency("c", "jar")
        ));
        DependencyList list = new DependencyList(inner);

        List<org.artifactory.build.Dependency> sub = list.subList(1, 3);

        assertEquals(2, sub.size());
        assertEquals("b", sub.get(0).getId());
        assertEquals("c", sub.get(1).getId());
    }

    @Test
    void iterator_shouldIterateOverDependencyElements() {
        Dependency d1 = createApiDependency("first", "jar");
        Dependency d2 = createApiDependency("second", "pom");
        DependencyList list = new DependencyList(new ArrayList<>(List.of(d1, d2)));

        var iterator = list.iterator();
        assertTrue(iterator.hasNext());
        assertEquals("first", iterator.next().getId());
        assertTrue(iterator.hasNext());
        assertEquals("second", iterator.next().getId());
        assertFalse(iterator.hasNext());
    }

    @Test
    void set_shouldReplaceElementAndReturnPrevious() {
        Dependency oldDep = createApiDependency("old", "jar");
        Dependency newDep = createApiDependency("new", "pom");
        DependencyList list = new DependencyList(new ArrayList<>(List.of(oldDep)));
        org.artifactory.build.Dependency newWrapper = new org.artifactory.build.Dependency(newDep);

        org.artifactory.build.Dependency previous = list.set(0, newWrapper);

        assertNotNull(previous);
        assertEquals("old", previous.getId());
        assertEquals("new", list.getFirst().getId());
    }

    @Test
    void removeByIndex_shouldRemoveAndReturnDependency() {
        Dependency apiDep = createApiDependency("x", "jar");
        DependencyList list = new DependencyList(new ArrayList<>(List.of(apiDep)));

        org.artifactory.build.Dependency removed = list.removeFirst();

        assertNotNull(removed);
        assertEquals("x", removed.getId());
        assertEquals(0, list.size());
    }

    @Test
    void indexOf_shouldReturnIndex_whenElementPresent() {
        Dependency d1 = createApiDependency("a", "jar");
        Dependency d2 = createApiDependency("b", "pom");
        DependencyList list = new DependencyList(new ArrayList<>(List.of(d1, d2)));
        org.artifactory.build.Dependency wrapper = new org.artifactory.build.Dependency(d2);

        assertEquals(1, list.indexOf(wrapper));
    }

    @NonNull
    private static Dependency createApiDependency(@NonNull String id, @NonNull String type) {
        return new DependencyBuilder().id(id).type(type).sha1("a".repeat(40)).build();
    }
}
