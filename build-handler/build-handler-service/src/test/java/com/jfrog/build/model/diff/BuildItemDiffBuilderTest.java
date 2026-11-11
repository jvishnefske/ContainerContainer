package com.jfrog.build.model.diff;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildItemDiffBuilderTest {

    @Test
    void build_shouldThrow_whenIdEmpty() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new BuildItemDiff.Builder("", "", "").build());
        assertTrue(e.getMessage().matches(".*provide an id.*"));
    }

    @Test
    void build_shouldThrow_whenFullIdEmpty() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new BuildItemDiff.Builder("id", "", "").build());
        assertTrue(e.getMessage().matches(".*provide full id.*"));
    }

    @Test
    void build_shouldThrow_whenModuleIdEmpty() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new BuildItemDiff.Builder("id", "fullId", "").build());
        assertTrue(e.getMessage().matches(".*provide module id.*"));
    }

    @Test
    void build_shouldReturnDefaultValues_whenNoOptionalFieldsSet() {
        BuildItemDiff build = new BuildItemDiff.Builder("id", "fullId", "moduleId").build();

        assertEquals("id", build.getId(), "Unexpected id.");
        assertEquals("fullId", build.getBaseId(), "Unexpected base id.");
        assertEquals("moduleId", build.getModuleId(), "Unexpected module id.");
        assertNull(build.getMd5(), "Default md5 should be null.");
        assertNull(build.getSha1(), "Default sha1 should be null.");
        assertNull(build.getType(), "Default type should be null.");
    }

    @Test
    void build_shouldReturnSetValues_whenOptionalFieldsSet() {
        BuildItemDiff build = new BuildItemDiff.Builder("id", "fullId", "moduleId")
                .md5("md5").sha1("sha1").type("type").build();

        assertEquals("id", build.getId(), "Unexpected id.");
        assertEquals("fullId", build.getBaseId(), "Unexpected base id.");
        assertEquals("moduleId", build.getModuleId(), "Unexpected module id.");
        assertEquals("md5", build.getMd5(), "Unexpected md5 value.");
        assertEquals("sha1", build.getSha1(), "Unexpected sha1 value.");
        assertEquals("type", build.getType(), "Unexpected type value.");
    }

    @Test
    void getBaseId_shouldParseFromFullId_whenFullIdContainsColons() {
        BuildItemDiff build = new BuildItemDiff.Builder("id", "a:a:1.0-SNAPSHOT", "moduleId").build();

        assertEquals("a:a", build.getBaseId(), "Unexpected base id.");
    }
}
