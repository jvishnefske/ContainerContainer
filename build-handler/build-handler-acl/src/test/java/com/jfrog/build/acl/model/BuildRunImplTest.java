package com.jfrog.build.acl.model;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BuildRunImplTest {

    @Test
    void getters_shouldReturnValuesFromConstructor_whenConstructedWithNameNumberStarted() {
        String name = "build-name";
        String number = "1";
        String started = formatBuildTime(1700000000000L);
        BuildRunImpl run = new BuildRunImpl(name, number, started);

        assertEquals(name, run.getName());
        assertEquals(number, run.getNumber());
        assertEquals(started, run.getStarted());
        assertEquals("artifactory-build-info", run.getBuildRepo());
    }

    @Test
    void getters_shouldReturnValuesFromFullConstructor() {
        long buildId = 100L;
        String name = "build-name";
        String number = "1";
        String started = formatBuildTime(1700000000000L);
        String ciUrl = "http://ci.example.com";
        String releaseStatus = "released";
        String releaseRepoKey = "release-repo";
        String buildRepo = "build-repo";
        BuildRunImpl run = new BuildRunImpl(
                buildId,
                name,
                number,
                started,
                ciUrl,
                releaseStatus,
                releaseRepoKey,
                buildRepo
        );
        assertEquals(buildId, run.getBuildId());
        assertEquals(name, run.getName());
        assertEquals(number, run.getNumber());
        assertEquals(started, run.getStarted());
        assertEquals(ciUrl, run.getCiUrl());
        assertEquals(releaseStatus, run.getReleaseStatus());
        assertEquals(releaseRepoKey, run.getReleaseRepoKey());
        assertEquals(buildRepo, run.getBuildRepo());
    }

    @Test
    void getStartedDate_shouldReturnDateParsedFromStartedString() {
        long millis = 1700000000000L;
        String started = formatBuildTime(millis);
        BuildRunImpl run = new BuildRunImpl("build-name", "1", started);

        assertEquals(new Date(millis), run.getStartedDate());
    }

    @Test
    void equals_shouldBeBasedOnNameNumberAndStartedOnly() {
        String started = formatBuildTime(1700000000000L);
        BuildRunImpl a = new BuildRunImpl("build-name", "1", started, "repo-a");
        BuildRunImpl b = new BuildRunImpl("build-name", "1", started, "repo-b");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_shouldReturnFalse_whenNameDiffers() {
        String started = formatBuildTime(1700000000000L);
        BuildRunImpl a = new BuildRunImpl("build-name-a", "1", started);
        BuildRunImpl b = new BuildRunImpl("build-name-b", "1", started);

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldReturnFalse_whenNumberDiffers() {
        String started = formatBuildTime(1700000000000L);
        BuildRunImpl a = new BuildRunImpl("build-name", "1", started);
        BuildRunImpl b = new BuildRunImpl("build-name", "2", started);

        assertNotEquals(a, b);
    }

    @Test
    void equals_shouldReturnFalse_whenStartedDiffers() {
        BuildRunImpl a = new BuildRunImpl("build-name", "1", formatBuildTime(1700000000000L));
        BuildRunImpl b = new BuildRunImpl("build-name", "1", formatBuildTime(1700000000001L));

        assertNotEquals(a, b);
    }
}
