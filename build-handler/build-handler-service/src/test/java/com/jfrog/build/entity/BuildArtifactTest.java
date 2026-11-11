package com.jfrog.build.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BuildArtifactTest {

    @Test
    void simpleBuildArtifactTest() {
        BuildArtifact b = new BuildArtifact(1L, 2L, "b", "t", "a1", "a2", "a3");
        assertEquals(1L, b.getArtifactId());
        assertEquals(2L, b.getModuleId());
        assertEquals("b", b.getArtifactName());
        assertEquals("t", b.getArtifactType());
        assertEquals("a1", b.getSha1());
        assertEquals("a2", b.getMd5());
        assertEquals("a3", b.getSha2());
    }

    @Test
    void mostlyNullBuildArtifactTest() {
        BuildArtifact b = new BuildArtifact(1L, 2L, "b", null, null, null, null);
        assertEquals(1L, b.getArtifactId());
        assertEquals(2L, b.getModuleId());
        assertEquals("b", b.getArtifactName());
        assertNull(b.getArtifactType());
        assertNull(b.getSha1());
        assertNull(b.getMd5());
        assertNull(b.getSha2());
    }

    @Test
    public void noArtifactIdTest() {
        assertThrows(IllegalArgumentException.class, () -> new BuildArtifact(0L, 2L, "b", null, null, null, null));
    }

    @Test
    public void negArtifactIdTest() {
        assertThrows(IllegalArgumentException.class, () -> new BuildArtifact(-2L, 2L, "b", null, null, null, null));
    }

    @Test
    public void noModuleIdTest() {
        assertThrows(IllegalArgumentException.class, () -> new BuildArtifact(1L, 0L, "b", null, null, null, null));
    }

    @Test
    public void negModuleIdTest() {
        assertThrows(IllegalArgumentException.class, () -> new BuildArtifact(1L, -2L, "b", null, null, null, null));
    }

    @Test
    public void noArtifactNameTest() {
        assertThrows(IllegalArgumentException.class, () -> new BuildArtifact(1L, 2L, null, null, null, null, null));
    }

    @Test
    public void emptyArtifactNameTest() {
        assertThrows(IllegalArgumentException.class, () -> new BuildArtifact(1L, 2L, " ", null, null, null, null));
    }

    @Test
    public void artifactTypeTooLong() {
        String name = "very_looooooooooooooooooooooooooooooooooooooooooooooooooooong_name";
        assertThrows(IllegalArgumentException.class, () -> new BuildArtifact(1L, 2L, "a", name, null, null, null));
    }
}
