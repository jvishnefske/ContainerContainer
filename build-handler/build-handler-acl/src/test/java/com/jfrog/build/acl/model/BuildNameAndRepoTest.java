package com.jfrog.build.acl.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildNameAndRepoTest {

    @Test
    void compareTo_ReturnsNegative_WhenNameIsLessThanOther() {
        BuildNameAndRepo a = new BuildNameAndRepo("a", "repo");
        BuildNameAndRepo b = new BuildNameAndRepo("b", "repo");

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    void compareTo_ComparesByRepo_WhenNamesAreEqual() {
        BuildNameAndRepo a = new BuildNameAndRepo("same", "a-repo");
        BuildNameAndRepo b = new BuildNameAndRepo("same", "b-repo");

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    void compareTo_ReturnsZero_WhenNameAndRepoAreEqual() {
        BuildNameAndRepo a = new BuildNameAndRepo("same", "repo");
        BuildNameAndRepo b = new BuildNameAndRepo("same", "repo");

        assertEquals(0, a.compareTo(b));
        assertEquals(0, b.compareTo(a));
    }
}
