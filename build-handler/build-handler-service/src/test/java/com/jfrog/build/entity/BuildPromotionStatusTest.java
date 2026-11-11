package com.jfrog.build.entity;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildPromotionStatusTest {

    @Test
    void simpleBuildPromotionTest() {
        BuildPromotionStatus bp = new BuildPromotionStatus(1L, 2L, "me", "s", "r", "c", "u");
        assertEquals(1L, bp.getBuildId());
        assertEquals(2L, bp.getCreated());
        assertEquals("me", bp.getCreatedBy());
        assertEquals("s", bp.getStatus());
        assertEquals("r", bp.getRepository());
        assertEquals("c", bp.getComment());
        assertEquals("u", bp.getCiUser());
    }

    @Test
    void maxNullsBuildPromotionTest() {
        BuildPromotionStatus bp = new BuildPromotionStatus(1L, 2L, null, "s", null, null, null);
        assertEquals(1L, bp.getBuildId());
        assertEquals(2L, bp.getCreated());
        assertEquals("s", bp.getStatus());
        assertNull(bp.getCreatedBy());
        assertNull(bp.getRepository());
        assertNull(bp.getComment());
        assertNull(bp.getCiUser());
    }

    @Test
    void equalsBuildPromotionsTest() {
        BuildPromotionStatus bp1 = new BuildPromotionStatus(1L, 2L, null, "s", null, null, null);
        BuildPromotionStatus bp2 = new BuildPromotionStatus(2L, 2L, null, "s", null, null, null);
        BuildPromotionStatus bp3 = new BuildPromotionStatus(2L, 1L, null, "s", null, null, null);

        ImmutableList<BuildPromotionStatus> allEqualsToBp2 = ImmutableList.of(
                new BuildPromotionStatus(2L, 2L, null, "s1", null, null, null),
                new BuildPromotionStatus(2L, 2L, "me", "s", null, null, null),
                new BuildPromotionStatus(2L, 2L, null, "s", "re", null, null),
                new BuildPromotionStatus(2L, 2L, null, "s", null, "co", null),
                new BuildPromotionStatus(2L, 2L, null, "s", null, null, "ci"));

        assertNotEquals(bp1, bp2);
        assertNotEquals(bp1.hashCode(), bp2.hashCode());
        assertFalse(bp1.isIdentical(bp2));
        assertTrue(bp1.compareTo(bp2) < 0);

        assertNotEquals(bp1, bp3);
        assertNotEquals(bp1.hashCode(), bp3.hashCode());
        assertFalse(bp1.isIdentical(bp3));
        assertTrue(bp1.compareTo(bp3) < 0);

        assertNotEquals(bp3, bp2);
        assertNotEquals(bp3.hashCode(), bp2.hashCode());
        assertFalse(bp3.isIdentical(bp2));
        assertTrue(bp3.compareTo(bp2) < 0);

        for (BuildPromotionStatus status : allEqualsToBp2) {
            assertEquals(bp2, status);
            assertEquals(bp2.hashCode(), status.hashCode());
            assertFalse(bp2.isIdentical(status));
            assertEquals(0, status.compareTo(bp2));
            assertTrue(status.compareTo(bp1) > 0);
            assertTrue(status.compareTo(bp3) > 0);
        }
    }

    @Test
    void testBuildPromotionsOrderTest() {
        // The order should be 1, 3, 2
        BuildPromotionStatus bp1 = new BuildPromotionStatus(1L, 2L, null, "s", null, null, null);
        BuildPromotionStatus bp2 = new BuildPromotionStatus(2L, 2L, null, "s", null, null, null);
        BuildPromotionStatus bp3 = new BuildPromotionStatus(2L, 1L, null, "s", null, null, null);

        ArrayList<BuildPromotionStatus> statuses = new ArrayList<BuildPromotionStatus>(3);
        statuses.add(bp2);
        statuses.add(bp1);
        statuses.add(bp3);
        Collections.sort(statuses);
        assertEquals(bp1, statuses.get(0));
        assertEquals(bp3, statuses.get(1));
        assertEquals(bp2, statuses.get(2));
    }

    @Test
    void noBuildIdBuildPromotionTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildPromotionStatus(0L, 1L, null, "s", null, null, null));
    }

    @Test
    void negBuildIdBuildPromotionTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildPromotionStatus(-2L, 1L, null, "s", null, null, null));
    }

    @Test
    void noCreatedBuildPromotionTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildPromotionStatus(1L, 0L, null, "s", null, null, null));
    }

    @Test
    void negCreatedBuildPromotionTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildPromotionStatus(1L, -1L, null, "s", null, null, null));
    }

    @Test
    void nullStatusBuildPromotionTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildPromotionStatus(1L, 1L, null, null, null, null, null));
    }

    @Test
    void emptyStatusBuildPromotionTest() {
        assertThrows(IllegalArgumentException.class, () ->
                new BuildPromotionStatus(1L, 1L, null, " ", null, null, null));
    }

    @Test
    void buildCreatedByAndCiUserLongUsername() {
        BuildPromotionStatus bps =
                new BuildPromotionStatus(1L, 2L, "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-cc&testing",
                        "s", "r", "c", "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-dd&testing");
        assertEquals("verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-cc&...", bps.getCreatedBy());
        assertEquals("verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-dd&...", bps.getCiUser());
    }

    @Test
    void longCommentStatusRepo() {
        String comment = RandomStringUtils.random(2048);
        String status = RandomStringUtils.random(2048);
        String repo = RandomStringUtils.random(2048);

        BuildPromotionStatus bps =
                new BuildPromotionStatus(1L, 2L, "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-cc&testing",
                        status, repo, comment, "verylongnamefortestingthe128ofusernameto-dev-ats-openpaas-dd&testing");

        assertEquals(repo.substring(0, 64), bps.getRepository());
        assertTrue(bps.getComment().endsWith("..."));
        assertTrue(bps.getStatus().endsWith("..."));
        assertTrue(bps.getComment().startsWith(comment.substring(0, 1021)));
        assertTrue(bps.getStatus().startsWith(status.substring(0, 61)));
    }
}
