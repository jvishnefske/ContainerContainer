package org.artifactory.build;

import com.google.common.collect.Lists;
import org.jfrog.build.api.Agent;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildAgent;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.builder.PromotionStatusBuilder;
import org.jfrog.build.api.release.PromotionStatus;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static java.lang.String.valueOf;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.jfrog.build.api.Build.formatBuildStarted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailedBuildRunImplTest {

    @Test
    void copy_shouldCreateNewInstanceWithSameNameAndNumber_whenCopyCalled() {
        Build build = new Build();
        build.setNumber(valueOf(1));
        build.setName("build-name");
        build.setStarted(formatBuildStarted(new Date().getTime()));
        DetailedBuildRunImpl original = new DetailedBuildRunImpl(build, BUILDINFO_REPO_KEY);
        DetailedBuildRun copy = original.copy();

        assertNotEquals(copy, original);
        assertEquals(copy.getName(), original.getName());
        assertEquals(copy.getNumber(), original.getNumber());
        assertNotEquals(copy.getStarted(), original.getStarted());
        assertNull(copy.getBuildAgent());
        assertNull(copy.getAgent());
    }

    @Test
    void copy_shouldSetBuildNumber_whenBuildNumberProvided() {
        Build build = new Build();
        build.setNumber("1");
        build.setName("build-name");
        build.setStarted(formatBuildStarted(new Date().getTime()));
        DetailedBuildRunImpl original = new DetailedBuildRunImpl(build);

        DetailedBuildRun copy = original.copy("99");

        assertEquals("99", copy.getNumber());
        assertEquals("build-name", copy.getName());
    }

    @Test
    void getters_shouldDelegateToBuild_whenBuildHasAllFieldsSet() {
        long startedMillis = 1700000000000L;
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("123");
        build.setStarted(formatBuildStarted(startedMillis));
        build.setBuildAgent(new BuildAgent("agent-name", "1.0"));
        build.setAgent(new Agent("ci-name", "2.0"));
        build.setDurationMillis(5000L);
        build.setPrincipal("principal");
        build.setArtifactoryPrincipal("artifactory-user");
        build.setUrl("http://ci.example.com");
        build.setParentName("parent-build");
        build.setParentNumber("1");
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build, "custom-repo");

        assertEquals("build-name", run.getName());
        assertEquals("123", run.getNumber());
        assertEquals(formatBuildStarted(startedMillis), run.getStarted());
        assertEquals(new Date(startedMillis), run.getStartedDate());
        assertEquals("custom-repo", run.getBuildRepo());
        assertEquals("agent-name/1.0", run.getBuildAgent());
        assertEquals("ci-name/2.0", run.getAgent());
        assertEquals(5000L, run.getDurationMillis());
        assertEquals("principal", run.getPrincipal());
        assertEquals("artifactory-user", run.getArtifactoryPrincipal());
        assertEquals("http://ci.example.com", run.getUrl());
        assertEquals("parent-build", run.getParentName());
        assertEquals("1", run.getParentNumber());
    }

    @Test
    void getStartedDate_shouldReturnNull_whenBuildStartedIsNull() {
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        build.setStarted(null);
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertNull(run.getStartedDate());
    }

    @Test
    void constructor_shouldUseBuildInfoRepoKey_whenOnlyBuildProvided() {
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertEquals(BUILDINFO_REPO_KEY, run.getBuildRepo());
    }

    @Test
    void getModules_shouldReturnEmptyList_whenBuildHasNullModules() {
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        build.setModules(null);
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertNotNull(run.getModules());
        assertTrue(run.getModules().isEmpty());
    }

    @Test
    void getModules_shouldReturnWrappedModules_whenBuildHasModules() {
        Module apiModule = new Module();
        apiModule.setId("module-1");
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        build.setModules(Lists.newArrayList(apiModule));
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertEquals(1, run.getModules().size());
        assertEquals("module-1", run.getModules().getFirst().getId());
    }

    @Test
    void getReleaseStatuses_shouldReturnEmptyList_whenBuildHasNullStatuses() {
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        build.setStatuses(null);
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertNotNull(run.getReleaseStatuses());
        assertTrue(run.getReleaseStatuses().isEmpty());
    }

    @Test
    void getReleaseStatuses_shouldReturnWrappedStatuses_whenBuildHasPromotionStatuses() {
        PromotionStatus apiStatus = new PromotionStatusBuilder("released")
                .timestampDate(new Date())
                .repository("repo")
                .build();
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        build.setStatuses(Lists.newArrayList(apiStatus));
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertEquals(1, run.getReleaseStatuses().size());
        assertEquals("released", run.getReleaseStatuses().getFirst().getStatus());
    }

    @Test
    void getReleaseStatus_shouldReturnLatestPromotionStatus_whenBuildHasStatuses() {
        long now = System.currentTimeMillis();
        PromotionStatus older = new PromotionStatusBuilder("staged").timestampDate(new Date(now - 10000)).build();
        PromotionStatus newer = new PromotionStatusBuilder("released").timestampDate(new Date(now)).build();
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        build.setStatuses(Lists.newArrayList(older, newer));
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertEquals("released", run.getReleaseStatus());
    }

    @Test
    void getReleaseRepoKey_shouldReturnLatestPromotionRepository_whenBuildHasStatuses() {
        PromotionStatus status = new PromotionStatusBuilder("released")
                .timestampDate(new Date())
                .repository("release-repo")
                .build();
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        build.setStatuses(Lists.newArrayList(status));
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertEquals("release-repo", run.getReleaseRepoKey());
    }

    @Test
    void getReleaseStatus_shouldReturnNull_whenBuildHasNoStatuses() {
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        build.setStatuses(Lists.newArrayList());
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertNull(run.getReleaseStatus());
    }

    @Test
    void getReleaseRepoKey_shouldReturnEmpty_whenBuildHasNoStatuses() {
        Build build = new Build();
        build.setName("build-name");
        build.setNumber("1");
        build.setStatuses(Lists.newArrayList());
        DetailedBuildRunImpl run = new DetailedBuildRunImpl(build);

        assertEquals(EMPTY, run.getReleaseRepoKey());
    }
}