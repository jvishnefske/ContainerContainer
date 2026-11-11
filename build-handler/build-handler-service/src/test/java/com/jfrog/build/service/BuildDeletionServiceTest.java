package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.ArtifactoryBuildArtifact;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.api.BuildRetentionWorkItem;
import com.jfrog.build.model.BuildDeleteNumberResult;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.tx.BuildTxRunner;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.Build;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.jfrog.build.model.BuildDeleteNumberResult.ZERO_ARTIFACTS;
import static com.jfrog.build.acl.model.BuildXrayEvent.DELETE_BUILD;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildDeletionServiceTest {

    private BuildDeletionService deletionService;

    private BuildBridgeToArtifactory bridgeToArtifactory;
    private BuildDatabaseService databaseService;
    private BuildReadService readService;
    private BuildTxRunner txRunner;
    private BuildArtifactService artifactService;

    @BeforeEach
    void setUp() {
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        txRunner = mock(BuildTxRunner.class);
        BuildAqlService aqlService = mock(BuildAqlService.class);
        artifactService = mock(BuildArtifactService.class);
        BuildPermissionService permissionService = mock(BuildPermissionService.class);
        BuildSysConfigService sysConfigService = mock(BuildSysConfigService.class);
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        when(bridgeToArtifactory.newStatusHolder()).thenReturn(statusHolder);
        when(statusHolder.getLastError()).thenReturn(null);
        databaseService = mock(BuildDatabaseService.class);
        readService = mock(BuildReadService.class);
        deletionService = new BuildDeletionService(
                bridgeToArtifactory,
                txRunner,
                aqlService,
                artifactService,
                databaseService,
                permissionService,
                readService,
                sysConfigService
        );
    }

    @Test
    void deleteBuild_byCoordinates_throwsNotFound_whenBuildRunMissing() {
        String buildName = "some-build-name";
        String buildNumber = "some-build-number";
        long startedMillis = 1700000000000L;
        String startedFormatted = formatBuildTime(startedMillis);
        String buildRepo = "phoenix-build-info";

        when(readService.getBuildRun(buildName, buildNumber, startedFormatted, buildRepo)).thenReturn(null);

        NotFoundException e = assertThrows(NotFoundException.class, () ->
                deletionService.deleteBuild(buildRepo, buildName, buildNumber, startedFormatted, false, true, false));

        assertTrue(e.getMessage().contains("Build not found"));
    }

    @Test
    void deleteBuild_byCoordinates_returnsZeroArtifacts_whenJsonExistsAndDeleteArtifactsDisabled() {
        String buildName = "some-build-name";
        String buildNumber = "some-build-number";
        long startedMillis = 1700000000000L;
        String startedFormatted = formatBuildTime(startedMillis);
        String buildRepo = "phoenix-build-info";

        BuildRun buildRun = mock(BuildRun.class);
        when(buildRun.getName()).thenReturn(buildName);
        when(buildRun.getNumber()).thenReturn(buildNumber);
        when(buildRun.getStarted()).thenReturn(startedFormatted);
        when(buildRun.getBuildRepo()).thenReturn(buildRepo);
        when(readService.getBuildRun(buildName, buildNumber, startedFormatted, buildRepo)).thenReturn(buildRun);
        when(bridgeToArtifactory.repoPathExists(anyString())).thenReturn(true);

        MutableStatusHolder statusHolder = bridgeToArtifactory.newStatusHolder();
        BuildDeleteNumberResult result =
                deletionService.deleteBuild(buildRepo, buildName, buildNumber, startedFormatted, false, false, false);

        assertSame(ZERO_ARTIFACTS, result);
        verify(bridgeToArtifactory).undeploy(anyString(), eq(statusHolder), eq(false), eq(true), eq(false), eq(false),
                eq(false));
    }

    @Test
    void deleteBuild_forStructuredCleanup_throwsNotFound_whenBuildJsonMissing() {
        long startedMillis = 1700000000000L;
        String startedFormatted = formatBuildTime(startedMillis);
        String buildRepo = "phoenix-build-info";
        BuildRun buildRun = mock(BuildRun.class);
        when(buildRun.getName()).thenReturn("some-build-name");
        when(buildRun.getNumber()).thenReturn("some-build-number");
        when(buildRun.getStarted()).thenReturn(startedFormatted);
        when(buildRun.getBuildRepo()).thenReturn(buildRepo);
        when(bridgeToArtifactory.repoPathExists(anyString())).thenReturn(false);

        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> deletionService.deleteBuild(buildRun, statusHolder, false, false, false));

        assertTrue(ex.getMessage().contains("Build cannot be found"));
    }

    @Test
    void deleteBuildInternal_invokesXrayWithParsedBuildStarted() {
        long startedMillis = 1700000000000L;
        String startedFormatted = formatBuildTime(startedMillis);
        Build build = new Build();
        build.setName("some-build-name");
        build.setNumber("some-build-number");
        build.setStarted(startedFormatted);
        MutableStatusHolder status = mock(MutableStatusHolder.class);

        deletionService.deleteBuildInternal(build, "phoenix-build-info", status);

        verify(databaseService).deleteBuild(
                "some-build-name", "some-build-number", startedFormatted, "phoenix-build-info");
        verify(bridgeToArtifactory).runXrayInterceptor(
                eq(DELETE_BUILD), eq("some-build-name"), eq("some-build-number"), eq(startedMillis),
                eq("phoenix-build-info"));
    }

    @Test
    void deleteBuildByBuildRun_invokesXrayWithParsedBuildStarted() {
        long startedMillis = 1700000000123L;
        String startedFormatted = formatBuildTime(startedMillis);
        BuildRun buildRun = mock(BuildRun.class);
        when(buildRun.getName()).thenReturn("some-build-name");
        when(buildRun.getNumber()).thenReturn("some-build-number");
        when(buildRun.getStarted()).thenReturn(startedFormatted);
        MutableStatusHolder status = mock(MutableStatusHolder.class);

        deletionService.deleteBuild(buildRun, "phoenix-build-info", status);

        verify(databaseService).deleteBuildFromBuildRun(buildRun);
        verify(bridgeToArtifactory).runXrayInterceptor(
                eq(DELETE_BUILD), eq("some-build-name"), eq("some-build-number"), eq(startedMillis),
                eq("phoenix-build-info"));
    }

    @Test
    void deleteBuild_retentionFlow_passesIdempotentDeleteTrueToUndeploy() {
        BuildRun buildRun = buildRun("phoenix-build-info");
        when(bridgeToArtifactory.repoPathExists(anyString())).thenReturn(true);
        MutableStatusHolder status = mock(MutableStatusHolder.class);

        deletionService.deleteBuild(buildRun, status, false, true);

        verify(bridgeToArtifactory).undeploy(anyString(), eq(status), eq(false), eq(true), eq(false), eq(false),
                eq(true));
    }

    @Test
    void deleteBuild_nonRetentionFlow_passesIdempotentDeleteFalseToUndeploy() {
        BuildRun buildRun = buildRun("phoenix-build-info");
        when(bridgeToArtifactory.repoPathExists(anyString())).thenReturn(true);
        MutableStatusHolder status = mock(MutableStatusHolder.class);

        deletionService.deleteBuild(buildRun, status, false);

        verify(bridgeToArtifactory).undeploy(anyString(), eq(status), eq(false), eq(true), eq(false), eq(false),
                eq(false));
    }

    @Test
    void deleteBuild_nullBuildRun_recordsErrorWithoutDeleting() {
        MutableStatusHolder status = mock(MutableStatusHolder.class);

        deletionService.deleteBuild(null, status, false, true);

        verify(status).error(eq("Couldn't find requested build"), isNull(), any());
        verify(bridgeToArtifactory, never()).undeploy(
                anyString(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void deleteBuild_buildJsonMissing_throwsNotFound() {
        BuildRun buildRun = buildRun("phoenix-build-info");
        when(bridgeToArtifactory.repoPathExists(anyString())).thenReturn(false);
        MutableStatusHolder status = mock(MutableStatusHolder.class);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> deletionService.deleteBuild(buildRun, status, false, false));

        assertTrue(ex.getMessage().contains("was not found in path"));
    }

    @Test
    void deleteBuildAsync_retention_deletesBuildJsonIdempotently() {
        BuildRun buildRun = buildRun("phoenix-build-info");
        when(bridgeToArtifactory.repoPathExists(anyString())).thenReturn(true);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(txRunner).runTx(any(Runnable.class));

        deletionService.deleteBuildAsync(new BuildRetentionWorkItem(buildRun, false));

        verify(bridgeToArtifactory).undeploy(anyString(), any(), eq(false), eq(true), eq(false), eq(false), eq(true));
    }

    @Test
    void deleteBuild_withDeleteArtifacts_removesArtifactsThenBuildJson() {
        BuildRun buildRun = buildRun("phoenix-build-info");
        BuildWrapper buildWrapper = buildWrapper();
        when(readService.getBuildInternally(buildRun)).thenReturn(buildWrapper);
        when(artifactService.getBuildArtifactsFileInfos(eq(buildWrapper), isNull(), isNull(), eq(false)))
                .thenReturn(Set.of(artifact("phoenix", "path/to/artifact.jar")));
        when(bridgeToArtifactory.repoPathExists(anyString())).thenReturn(true);
        MutableStatusHolder status = mock(MutableStatusHolder.class);

        deletionService.deleteBuild(buildRun, status, true, true);

        verify(bridgeToArtifactory).undeploy(eq("phoenix/path/to/artifact.jar"), eq(status), eq(true), eq(true),
                eq(false), eq(false), eq(false));
        verify(bridgeToArtifactory).undeploy(anyString(), eq(status), eq(false), eq(true), eq(false), eq(false),
                eq(true));
    }

    @Test
    void deleteBuild_byBuildRun_withDeleteArtifacts_deletesArtifacts() {
        BuildRun buildRun = buildRun("phoenix-build-info");
        BuildWrapper buildWrapper = buildWrapper();
        when(readService.getBuildInternally(buildRun)).thenReturn(buildWrapper);
        when(artifactService.getBuildArtifactsFileInfos(buildWrapper))
                .thenReturn(Set.of(artifact("phoenix", "path/a.jar")));
        when(bridgeToArtifactory.repoPathExists(anyString())).thenReturn(true);
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        when(status.getLastError()).thenReturn(null);

        deletionService.deleteBuild(buildRun, status, false, true, false);

        verify(bridgeToArtifactory).undeploy(eq("phoenix/path/a.jar"), eq(status), eq(true), eq(true), eq(false),
                eq(false), eq(false));
    }

    private static BuildRun buildRun(String buildRepo) {
        BuildRun buildRun = mock(BuildRun.class);
        when(buildRun.getName()).thenReturn("some-build-name");
        when(buildRun.getNumber()).thenReturn("some-build-number");
        when(buildRun.getStarted()).thenReturn(formatBuildTime(1700000000000L));
        when(buildRun.getBuildRepo()).thenReturn(buildRepo);
        return buildRun;
    }

    private static BuildWrapper buildWrapper() {
        Build build = new Build();
        build.setName("some-build-name");
        build.setNumber("some-build-number");
        build.setStarted(formatBuildTime(1700000000000L));
        return new BuildWrapper(build, "phoenix-build-info");
    }

    private static ArtifactoryBuildArtifact artifact(String repoKey, String relativePath) {
        BuildFileInfo fileInfo = BuildFileInfo.builder()
                .repoPath(new BuildRepoPath(repoKey, relativePath))
                .size(10L)
                .build();
        return new ArtifactoryBuildArtifact(null, fileInfo);
    }
}
