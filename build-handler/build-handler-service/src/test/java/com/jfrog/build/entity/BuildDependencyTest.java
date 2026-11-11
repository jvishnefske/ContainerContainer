package com.jfrog.build.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildDependencyTest {

    @Test
    void simpleBuildDependencyTest() {
        BuildDependency b = new BuildDependency(1L, 2L, "b", "c,d", "t", "a1", "a2", "a3");
        assertEquals(1L, b.getDependencyId());
        assertEquals(2L, b.getModuleId());
        assertEquals("b", b.getDependencyNameId());
        assertEquals("c,d", b.getDependencyScopes());
        assertEquals("t", b.getDependencyType());
        assertEquals("a1", b.getSha1());
        assertEquals("a3", b.getSha2());
        assertEquals("a2", b.getMd5());
    }

    @Test
    void mostlyNullBuildDependencyTest() {
        BuildDependency b = new BuildDependency(1L, 2L, "b", "", null, null, null, null);
        assertEquals(1L, b.getDependencyId());
        assertEquals(2L, b.getModuleId());
        assertEquals("b", b.getDependencyNameId());
        assertEquals("", b.getDependencyScopes());
        assertTrue(b.getScopes().isEmpty());
        assertNull(b.getDependencyType());
        assertNull(b.getSha1());
        assertNull(b.getSha2());
        assertNull(b.getMd5());
    }

    @Test
    void mostlyNullBuildDependencyTest2() {
        BuildDependency b = new BuildDependency(1L, 2L, "b", (String) null, null, null, null, null);
        assertEquals(1L, b.getDependencyId());
        assertEquals(2L, b.getModuleId());
        assertEquals("b", b.getDependencyNameId());
        assertNull(b.getDependencyScopes());
        assertTrue(b.getScopes().isEmpty());
        assertNull(b.getDependencyType());
        assertNull(b.getSha1());
        assertNull(b.getSha2());
        assertNull(b.getMd5());
    }

    @Test
    void mostlyNullBuildDependencyTest3() {
        BuildDependency b = new BuildDependency(1L, 2L, "b", (Set<String>) null, null, null, null, null);
        assertEquals(1L, b.getDependencyId());
        assertEquals(2L, b.getModuleId());
        assertEquals("b", b.getDependencyNameId());
        assertEquals("", b.getDependencyScopes());
        assertTrue(b.getScopes().isEmpty());
        assertNull(b.getDependencyType());
        assertNull(b.getSha1());
        assertNull(b.getSha2());
        assertNull(b.getMd5());
    }

    @Test
    void noDependencyIdTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildDependency(0L, 2L, "b", "", null, null, null, null));
    }

    @Test
    void negDependencyIdTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildDependency(-2L, 2L, "b", "", null, null, null, null));
    }

    @Test
    void noModuleIdTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildDependency(1L, 0L, "b", "", null, null, null, null));
    }

    @Test
    void negModuleIdTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildDependency(1L, -2L, "b", "", null, null, null, null));
    }

    @Test
    void noDependencyNameTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildDependency(1L, 2L, null, (String) null, null, null, null, null));
    }

    @Test
    void emptyDependencyNameTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildDependency(1L, 2L, " ", (String) null, null, null, null, null));
    }
}
