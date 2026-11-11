package com.jfrog.build.entity;

import com.google.common.collect.ImmutableSortedSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildEntityTest {

    @Test
    void simpleBuildTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, "h", 34L, "c", 36L, "m", null);
        b.setProperties(new HashSet<BuildProperty>());
        b.setPromotions(new HashSet<BuildPromotionStatus>());
        assertEquals(1L, b.getBuildId());
        assertEquals("a", b.getBuildName());
        assertEquals("1", b.getBuildNumber());
        assertEquals(10000000L, b.getBuildDate());
        assertEquals("h", b.getCiUrl());
        assertEquals(34L, b.getCreated());
        assertEquals("c", b.getCreatedBy());
        assertEquals(36L, b.getModified());
        assertEquals("m", b.getModifiedBy());
        assertTrue(b.getProperties().isEmpty());
        assertTrue(b.getPromotions().isEmpty());
    }

    @Test
    void maxNullBuildTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, null, 3L, null, 0L, null, null);
        assertEquals(1L, b.getBuildId());
        assertEquals("a", b.getBuildName());
        assertEquals("1", b.getBuildNumber());
        assertEquals(10000000L, b.getBuildDate());
        assertNull(b.getCiUrl());
        assertEquals(3L, b.getCreated());
        assertNull(b.getCreatedBy());
        assertEquals(0L, b.getModified());
        assertNull(b.getModifiedBy());
        b = new BuildEntity(1L, "a", "1", 10000000L, null, 3L, null, -2L, null, null);
        assertEquals(1L, b.getBuildId());
        assertEquals("a", b.getBuildName());
        assertEquals("1", b.getBuildNumber());
        assertEquals(10000000L, b.getBuildDate());
        assertNull(b.getCiUrl());
        assertEquals(3L, b.getCreated());
        assertNull(b.getCreatedBy());
        assertEquals(-2L, b.getModified());
        assertNull(b.getModifiedBy());
    }

    @Test
    void nullNameBuildTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildEntity(1L, null, "1", 10000000L, null, 34L, "c", 36L, "m", null));
    }

    @Test
    void noNameBuildTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildEntity(1L, " ", "1", 10000000L, null, 34L, "c", 36L, "m", null));
    }

    @Test
    void nullNumberBuildTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildEntity(1L, "a", null, 10000000L, null, 34L, "c", 36L, "m", null));
    }

    @Test
    void noNumberBuildTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildEntity(1L, "a", " ", 10000000L, null, 34L, "c", 36L, "m", null));
    }

    @Test
    void nullDateBuildTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildEntity(1L, "a", "1", 0L, null, 34L, "c", 36L, "m", null));
    }

    @Test
    void noDateBuildTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildEntity(1L, "a", "1", -3L, null, 34L, "c", 36L, "m", null));
    }

    @Test
    void noPropertiesInBuildTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, null, 1L, null, 0L, null, null);
        assertThrows(IllegalStateException.class, () -> b.getProperties());
    }

    @Test
    void setNullPropertiesInBuildTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, null, 1L, null, 0L, null, null);
        assertThrows(IllegalArgumentException.class, () -> b.setProperties(null));
    }

    @Test
    void doubleSetPropertiesInBuildTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, null, 1L, null, 0L, null, null);
        b.setProperties(new HashSet<BuildProperty>());
        assertThrows(IllegalStateException.class, () ->
                b.setProperties(new HashSet<BuildProperty>()));
    }

    @Test
    void noPromotionsInBuildTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, null, 1L, null, 0L, null, null);
        assertThrows(IllegalStateException.class, () -> b.getPromotions());
    }

    @Test
    void setNullPromotionsInBuildTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, null, 1L, null, 0L, null, null);
        assertThrows(IllegalArgumentException.class, () -> b.setPromotions(null));
    }

    @Test
    void doubleSetPromotionsInBuildTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, null, 1L, null, 0L, null, null);
        b.setPromotions(new HashSet<BuildPromotionStatus>());
        assertThrows(IllegalStateException.class, () ->
                b.setPromotions(new HashSet<BuildPromotionStatus>()));
    }

    @Test
    void buildPropertiesTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, "h", 34L, "c", 36L, "m", null);
        Collection<BuildProperty> properties = new ArrayList<BuildProperty>();
        properties.add(new BuildProperty(1L, 1L, "k1", "v1"));
        properties.add(new BuildProperty(2L, 1L, "k2", "v2"));
        BuildProperty badApple = new BuildProperty(2L, 1L, "k-bad", "v2");
        properties.add(badApple);
        b.setProperties(properties);
        assertEquals(1L, b.getBuildId());
        assertEquals("a", b.getBuildName());
        assertEquals("1", b.getBuildNumber());
        assertEquals(10000000L, b.getBuildDate());
        assertEquals("h", b.getCiUrl());
        assertEquals(34L, b.getCreated());
        assertEquals("c", b.getCreatedBy());
        assertEquals(36L, b.getModified());
        assertEquals("m", b.getModifiedBy());
        assertEquals(2, b.getProperties().size());
        assertTrue(b.getProperties().contains(badApple));
        for (BuildProperty buildProperty : b.getProperties()) {
            assertFalse(buildProperty.isIdentical(badApple));
        }
    }

    @Test
    void buildPromotionsTest() {
        BuildEntity b = new BuildEntity(1L, "a", "1", 10000000L, "h", 34L, "c", 36L, "m", null);
        Collection<BuildPromotionStatus> promotions = new ArrayList<BuildPromotionStatus>();
        BuildPromotionStatus first = new BuildPromotionStatus(1L, 2L, "me", "rel", null, null, null);
        BuildPromotionStatus second = new BuildPromotionStatus(1L, 3L, "me", "roll", null, null, null);
        // Insert second first ;)
        promotions.add(second);
        promotions.add(first);
        BuildPromotionStatus badApple = new BuildPromotionStatus(1L, 2L, "me", "k-bad", null, null, null);
        promotions.add(badApple);
        b.setPromotions(promotions);
        assertEquals(1L, b.getBuildId());
        assertEquals("a", b.getBuildName());
        assertEquals("1", b.getBuildNumber());
        assertEquals(10000000L, b.getBuildDate());
        assertEquals("h", b.getCiUrl());
        assertEquals(34L, b.getCreated());
        assertEquals("c", b.getCreatedBy());
        assertEquals(36L, b.getModified());
        assertEquals("m", b.getModifiedBy());
        ImmutableSortedSet<BuildPromotionStatus> statuses = b.getPromotions();
        assertEquals(2, statuses.size());
        assertTrue(statuses.contains(badApple));
        assertTrue(statuses.first().isIdentical(first));
        assertTrue(statuses.last().isIdentical(second));
        for (BuildPromotionStatus buildPromo : statuses) {
            assertFalse(buildPromo.isIdentical(badApple));
        }
    }

    @Test
    void buildCreatedByAndModifiedByLongUsername() {
        BuildEntity buildEntity = new BuildEntity(1L, "a", "1", 10000000L, "h", 34L,
                "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-cc&testing", 36L,
                "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-dd&testing", null);
        assertEquals("verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-cc&...", buildEntity.getCreatedBy());
        assertEquals("verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-dd&...", buildEntity.getModifiedBy());
    }
}
