package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.tx.BuildTxRunner;
import org.artifactory.common.status.MutableStatusHolder;
import org.jfrog.build.api.Build;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.jfrog.build.acl.util.BuildConstants.PROP_BUILD_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildRenameServiceTest {

    private BuildRenameService renameService;

    private BuildBridgeToArtifactory bridgeToArtifactory;
    private BuildTxRunner txRunner;
    private BuildReadService readService;
    private BuildCreationService creationService;
    private BuildDeletionService deletionService;
    private BuildPermissionService permissionService;

    @BeforeEach
    void setUp() {
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        txRunner = mock(BuildTxRunner.class);
        readService = mock(BuildReadService.class);
        creationService = mock(BuildCreationService.class);
        deletionService = mock(BuildDeletionService.class);
        permissionService = mock(BuildPermissionService.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(txRunner).runTx(any(Runnable.class));
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        when(bridgeToArtifactory.newStatusHolder()).thenReturn(statusHolder);
        renameService = new BuildRenameService(
                bridgeToArtifactory,
                txRunner,
                readService,
                creationService,
                deletionService,
                permissionService
        );
    }

    @Test
    void renameBuilds_whenNoMatchingBuilds_doesNotRunTransactions() {
        when(readService.searchBuildsByNameInternally("missing", "artifactory-build-info")).thenReturn(Set.of());

        renameService.renameBuilds("missing", "to", "artifactory-build-info");

        verify(txRunner, never()).runTx(any(Runnable.class));
    }

    @Test
    void renameBuilds_whenBuildExists_deletesAndRecreatesWithNewName() {
        BuildRunImpl buildRun = new BuildRunImpl(
                "old-name",
                "1",
                "2024-01-01T00:00:00.000+0000",
                "artifactory-build-info");
        when(readService.searchBuildsByNameInternally("old-name", "artifactory-build-info")).thenReturn(Set.of(buildRun));
        when(permissionService.canReadBuild("old-name", "1", buildRun.getStarted(), "artifactory-build-info"))
                .thenReturn(true);
        when(permissionService.canDeleteBuild("old-name", "1", buildRun.getStarted(), "artifactory-build-info"))
                .thenReturn(true);
        when(permissionService.canUploadBuild("old-name", "1", buildRun.getStarted(), "artifactory-build-info"))
                .thenReturn(true);
        when(readService.isImmutableBuild("old-name", "1", buildRun.getStarted())).thenReturn(false);
        Build buildJson = new Build();
        buildJson.setName("old-name");
        buildJson.setNumber("1");
        buildJson.setStarted(buildRun.getStarted());
        BuildWrapper wrapper = new BuildWrapper(buildJson, "artifactory-build-info");
        when(readService.getBuildInternally(buildRun)).thenReturn(wrapper);

        renameService.renameBuilds("old-name", "new-name", "artifactory-build-info");

        verify(txRunner, times(1)).runTx(any(Runnable.class));
        verify(deletionService).deleteBuild(same(buildRun), any(MutableStatusHolder.class), eq(false));
        verify(creationService).createBuild(
                argThat(b -> "new-name".equals(b.getName())), eq("artifactory-build-info"));
    }

    @Test
    void renameBuildsAsync_renamesBuildsThenRenamesBuildNameProperty() {
        when(readService.searchBuildsByNameInternally("from", "artifactory-build-info")).thenReturn(Set.of());

        renameService.renameBuildsAsync("from", "to", "artifactory-build-info");

        verify(bridgeToArtifactory).renameProperty(PROP_BUILD_NAME, "from", "to");
    }
}
