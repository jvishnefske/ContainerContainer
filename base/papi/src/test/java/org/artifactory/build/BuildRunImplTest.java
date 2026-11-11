package org.artifactory.build;

import com.jfrog.build.acl.model.BuildRunImpl;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildRunImplTest {

    @Test
    void getters_shouldDelegateToWrappedBuildRun_whenConstructedFromBuildRun() {
        long buildId = 50L;
        String name = "build-name";
        String number = "1";
        String started = formatBuildTime(1700000000000L);
        String ciUrl = "http://ci.example.com";
        String releaseStatus = "staged";
        String releaseRepoKey = "release-repo";
        String buildRepo = "build-repo";
        com.jfrog.build.acl.model.BuildRun inner = new BuildRunImpl(
                buildId,
                name,
                number,
                started,
                ciUrl,
                releaseStatus,
                releaseRepoKey,
                buildRepo
        );
        org.artifactory.build.BuildRunImpl run = new org.artifactory.build.BuildRunImpl(inner);
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
        com.jfrog.build.acl.model.BuildRun inner = new BuildRunImpl("build-name", "1", started);
        org.artifactory.build.BuildRunImpl run = new org.artifactory.build.BuildRunImpl(inner);
        assertEquals(new Date(millis), run.getStartedDate());
    }

    @Test
    void constructor_shouldCopyFieldsFromInnerBuildRun_whenGivenNameNumberStarted() {
        org.artifactory.build.BuildRunImpl run = new org.artifactory.build.BuildRunImpl(
                "build-name", "1", formatBuildTime(1700000000000L));

        assertEquals("build-name", run.getName());
        assertEquals("1", run.getNumber());
        assertEquals(new Date(1700000000000L), run.getStartedDate());
    }
}
