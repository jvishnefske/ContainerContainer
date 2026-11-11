package com.jfrog.build.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildPropertyTest {

    @Test
    void simpleBuildPropertyTest() {
        BuildProperty bp = new BuildProperty(1L, 2L, "k", "v");
        assertEquals(1L, bp.getPropId());
        assertEquals(2L, bp.getBuildId());
        assertEquals("k", bp.getPropKey());
        assertEquals("v", bp.getPropValue());
    }

    @Test
    void maxNullsBuildPropertyTest() {
        BuildProperty bp = new BuildProperty(1L, 2L, "k", null);
        assertEquals(1L, bp.getPropId());
        assertEquals(2L, bp.getBuildId());
        assertEquals("k", bp.getPropKey());
        assertNull(bp.getPropValue());
    }

    @Test
    void equalsBuildPropertyTest() {
        BuildProperty bp1 = new BuildProperty(1L, 2L, "k", "v");
        BuildProperty bp2 = new BuildProperty(2L, 2L, "k", "v");
        BuildProperty bp2a = new BuildProperty(2L, 3L, "k", "v");
        BuildProperty bp2b = new BuildProperty(2L, 2L, "k1", "v");
        BuildProperty bp2c = new BuildProperty(2L, 2L, "k", "v1");
        BuildProperty bp3 = new BuildProperty(1L, 3L, "nk", null);
        assertNotEquals(bp1, bp2);
        assertNotEquals(bp1.hashCode(), bp2.hashCode());
        assertNotEquals(bp3, bp2);
        assertNotEquals(bp3.hashCode(), bp2.hashCode());
        assertEquals(bp1, bp3);
        assertEquals(bp1.hashCode(), bp3.hashCode());
        assertEquals(bp2, bp2a);
        assertEquals(bp2.hashCode(), bp2a.hashCode());
        assertEquals(bp2, bp2b);
        assertEquals(bp2.hashCode(), bp2b.hashCode());
        assertEquals(bp2, bp2c);
        assertEquals(bp2.hashCode(), bp2c.hashCode());
        assertTrue(bp1.isIdentical(bp2));
        assertFalse(bp2.isIdentical(bp2a));
        assertFalse(bp2.isIdentical(bp2b));
        assertFalse(bp2.isIdentical(bp2c));
    }

    @Test
    void noIdBuildPropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildProperty(0L, 2L, "k", "v"));
    }

    @Test
    void negIdBuildPropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildProperty(-1L, 2L, "k", "v"));
    }

    @Test
    void noBuildIdBuildPropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildProperty(1L, 0L, "k", "v"));
    }

    @Test
    void negBuildIdBuildPropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildProperty(1L, -2L, "k", "v"));
    }

    @Test
    void nullKeyBuildPropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildProperty(1L, 2L, null, "v"));
    }

    @Test
    void noKeyBuildPropertyTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildProperty(1L, 2L, " ", "v"));
    }
}
