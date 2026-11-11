package com.jfrog.build.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildModuleTest {

    @Test
    void simpleBuildModuleTest() {
        BuildModule bm = new BuildModule(1L, 2L, "a");
        bm.setProperties(new HashSet<ModuleProperty>());
        assertEquals(1L, bm.getModuleId());
        assertEquals(2L, bm.getBuildId());
        assertEquals("a", bm.getModuleNameId());
        assertTrue(bm.getProperties().isEmpty());
    }

    @Test
    void simpleModulePropertyTest() {
        ModuleProperty mp = new ModuleProperty(1L, 2L, "k", "v");
        assertEquals(1L, mp.getPropId());
        assertEquals(2L, mp.getModuleId());
        assertEquals("k", mp.getPropKey());
        assertEquals("v", mp.getPropValue());
    }

    @Test
    void maxNullsModulePropertyTest() {
        ModuleProperty mp = new ModuleProperty(1L, 2L, "k", null);
        assertEquals(1L, mp.getPropId());
        assertEquals(2L, mp.getModuleId());
        assertEquals("k", mp.getPropKey());
        assertNull(mp.getPropValue());
    }

    @Test
    void equalsModulePropertyTest() {
        ModuleProperty mp1 = new ModuleProperty(1L, 2L, "k", "v");
        ModuleProperty mp2 = new ModuleProperty(2L, 2L, "k", "v");
        ModuleProperty mp2a = new ModuleProperty(2L, 3L, "k", "v");
        ModuleProperty mp2b = new ModuleProperty(2L, 2L, "k1", "v");
        ModuleProperty mp2c = new ModuleProperty(2L, 2L, "k", "v1");
        ModuleProperty mp3 = new ModuleProperty(1L, 3L, "nk", null);
        assertNotEquals(mp1, mp2);
        assertNotEquals(mp1.hashCode(), mp2.hashCode());
        assertNotEquals(mp3, mp2);
        assertNotEquals(mp3.hashCode(), mp2.hashCode());
        assertEquals(mp1, mp3);
        assertEquals(mp1.hashCode(), mp3.hashCode());
        assertEquals(mp2, mp2a);
        assertEquals(mp2.hashCode(), mp2a.hashCode());
        assertEquals(mp2, mp2b);
        assertEquals(mp2.hashCode(), mp2b.hashCode());
        assertEquals(mp2, mp2c);
        assertEquals(mp2.hashCode(), mp2c.hashCode());
        assertTrue(mp1.isIdentical(mp2));
        assertFalse(mp2.isIdentical(mp2a));
        assertFalse(mp2.isIdentical(mp2b));
        assertFalse(mp2.isIdentical(mp2c));
    }

    @Test
    void noIdModulePropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new ModuleProperty(0L, 2L, "k", "v"));
    }

    @Test
    void negIdModulePropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new ModuleProperty(-1L, 2L, "k", "v"));
    }

    @Test
    void noBuildIdModulePropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new ModuleProperty(1L, 0L, "k", "v"));
    }

    @Test
    void negBuildIdModulePropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new ModuleProperty(1L, -2L, "k", "v"));
    }

    @Test
    void nullKeyModulePropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new ModuleProperty(1L, 2L, null, "v"));
    }

    @Test
    void noKeyModulePropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new ModuleProperty(1L, 2L, " ", "v"));
    }

    @Test
    void noIdBuildModuleTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildModule(0L, 2L, "a"));
    }

    @Test
    void negIdBuildModuleTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildModule(-1L, 2L, "a"));
    }

    @Test
    void noBuildIdBuildModuleTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildModule(1L, 0L, "a"));
    }

    @Test
    void negBuildIdBuildModuleTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildModule(1L, -2L, "a"));
    }

    @Test
    void nullNameBuildModuleTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildModule(1L, 2L, null));
    }

    @Test
    void noNameBuildModuleTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildModule(1L, 2L, " "));
    }

    @Test
    void noPropertiesInBuildModuleTest() {
        BuildModule bm = new BuildModule(1L, 2L, "a");
        assertThrows(IllegalStateException.class, () -> bm.getProperties());
    }

    @Test
    void setNullPropertiesInBuildModuleTest() {
        BuildModule bm = new BuildModule(1L, 2L, "a");
        assertThrows(IllegalArgumentException.class, () -> bm.setProperties(null));
    }

    @Test
    void doubleSetPropertiesInBuildModuleTest() {
        BuildModule bm = new BuildModule(1L, 2L, "a");
        bm.setProperties(new HashSet<ModuleProperty>());
        assertThrows(IllegalStateException.class, () ->
                bm.setProperties(new HashSet<ModuleProperty>()));
    }
}
