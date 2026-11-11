package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.api.BuildRetentionWorkItem;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.BuildRetention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildRetentionServiceTest {

    private static final String BUILD_NAME = "some-build-name";
    private static final String BUILD_REPO = "artifactory-build-info";

    private BuildRetentionService retentionService;

    private BuildBridgeToArtifactory bridgeToArtifactory;
    private BuildDeletionService deletionService;
    private BuildPermissionService permissionService;
    private BuildReadService readService;
    private BuildSysConfigService sysConfigService;
    private MutableStatusHolder statusHolder;

    @BeforeEach
    void setUp() {
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        deletionService = mock(BuildDeletionService.class);
        permissionService = mock(BuildPermissionService.class);
        readService = mock(BuildReadService.class);
        sysConfigService = mock(BuildSysConfigService.class);
        statusHolder = mock(MutableStatusHolder.class);
        when(bridgeToArtifactory.newStatusHolder()).thenReturn(statusHolder);
        when(sysConfigService.isBuildRetentionEnabled()).thenReturn(true);
        retentionService = new BuildRetentionService(
                bridgeToArtifactory, deletionService, permissionService, readService, sysConfigService);
    }

    @Test
    void discardOldBuilds_sync_swallowsNotFoundException_withoutMarkingStatusError() {
        BuildRun oldBuild = buildRun("1", daysAgo(30));
        BuildRetention retention = retentionByDate(daysAgo(7));
        givenBuilds(oldBuild);
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(true);
        doThrow(new NotFoundException("Build cannot be found in path ..."))
                .when(deletionService).deleteBuild(same(oldBuild), same(statusHolder), eq(false), eq(true));

        MutableStatusHolder result = retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        assertSame(statusHolder, result);
        verify(deletionService).deleteBuild(same(oldBuild), same(statusHolder), eq(false), eq(true));
        verify(statusHolder, never()).error(any(), any(), any());
    }

    @Test
    void discardOldBuilds_sync_reportsOtherExceptionsViaStatusError_withNewMessageFormat() {
        BuildRun oldBuild = buildRun("42", daysAgo(30));
        BuildRetention retention = retentionByDate(daysAgo(7));
        givenBuilds(oldBuild);
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(true);
        RuntimeException boom = new RuntimeException("boom");
        doThrow(boom).when(deletionService).deleteBuild(same(oldBuild), same(statusHolder), eq(false), eq(true));

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(statusHolder).error(message.capture(), same(boom), any());
        String expected = "Failed deleting build " + BUILD_NAME + "/42 from " + BUILD_REPO + ": boom";
        assertEquals(expected, message.getValue());
    }

    @Test
    void discardOldBuilds_async_propagatesSynchronousExceptionsFromDeleteBuildAsync() {
        BuildRun oldBuild = buildRun("1", daysAgo(30));
        BuildRetention retention = retentionByDate(daysAgo(7));
        givenBuilds(oldBuild);
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(true);
        RuntimeException boom = new RuntimeException("queue full");
        doThrow(boom).when(deletionService).deleteBuildAsync(any(BuildRetentionWorkItem.class));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, true));

        assertSame(boom, thrown);
        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean(), anyBoolean());
        verify(statusHolder, never()).error(any(), any(), any());
    }

    @Test
    void discardOldBuilds_async_delegatesToDeleteBuildAsync_withWorkItem() {
        BuildRun oldBuild = buildRun("1", daysAgo(30));
        BuildRetention retention = retentionByDate(daysAgo(7));
        retention.setDeleteBuildArtifacts(true);
        givenBuilds(oldBuild);
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(true);

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, true);

        ArgumentCaptor<BuildRetentionWorkItem> captor = ArgumentCaptor.forClass(BuildRetentionWorkItem.class);
        verify(deletionService).deleteBuildAsync(captor.capture());
        assertSame(oldBuild, captor.getValue().getBuildId());
        assertTrue(captor.getValue().isDeleteArtifacts());
        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void discardOldBuilds_warnsAndReturns_whenRetentionDisabled() {
        when(sysConfigService.isBuildRetentionEnabled()).thenReturn(false);
        BuildRetention retention = retentionByDate(daysAgo(7));

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        verify(statusHolder).setActivateLogging(false);
        verify(statusHolder).warn(contains("Build Retention is disabled"), eq(400), any());
        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean(), anyBoolean());
        verify(deletionService, never()).deleteBuildAsync(any());
    }

    @Test
    void discardOldBuildsByDate_skipsPromotedBuilds() {
        BuildRun promoted = buildRun("1", daysAgo(30));
        when(promoted.getReleaseStatus()).thenReturn("released");
        BuildRetention retention = retentionByDate(daysAgo(7));
        givenBuilds(promoted);
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(true);

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void discardOldBuildsByDate_skipsPinnedBuilds() {
        BuildRun pinned = buildRun("1", daysAgo(30));
        BuildRetention retention = retentionByDate(daysAgo(7));
        retention.setBuildNumbersNotToBeDiscarded(List.of("1"));
        givenBuilds(pinned);
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(true);

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void discardOldBuildsByDate_doesNothing_whenUserCannotDelete() {
        BuildRun oldBuild = buildRun("1", daysAgo(30));
        BuildRetention retention = retentionByDate(daysAgo(7));
        givenBuilds(oldBuild);
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(false);
        when(bridgeToArtifactory.getCurrentUsername()).thenReturn("alice");

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean(), anyBoolean());
        verify(deletionService, never()).deleteBuildAsync(any());
    }

    @Test
    void discardOldBuildsByDate_doesNotDeleteBuildsNewerThanMinimumDate() {
        BuildRun recent = buildRun("1", daysAgo(1));
        BuildRetention retention = retentionByDate(daysAgo(7));
        givenBuilds(recent);
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(true);

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void discardOldBuildsByCount_doesNothing_whenCountNegative() {
        BuildRetention retention = new BuildRetention(false);

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        verify(readService, never()).searchBuildsByNameInternally(any(), any());
        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void discardOldBuildsByCount_doesNothing_whenFewerBuildsThanRetentionCount() {
        BuildRetention retention = retentionByCount(5);
        givenBuilds(buildRun("1", daysAgo(1)), buildRun("2", daysAgo(2)));
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(true);

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void discardOldBuildsByCount_keepsLatestNAndDeletesTheRest() {
        BuildRun newest = buildRun("3", daysAgo(1));
        BuildRun middle = buildRun("2", daysAgo(2));
        BuildRun oldest = buildRun("1", daysAgo(3));
        BuildRetention retention = retentionByCount(2);
        givenBuilds(oldest, newest, middle);
        when(permissionService.canDeleteBuildName(BUILD_NAME, BUILD_REPO)).thenReturn(true);

        retentionService.discardOldBuilds(BUILD_NAME, BUILD_REPO, retention, false);

        verify(deletionService, times(1)).deleteBuild(same(oldest), same(statusHolder), eq(false), eq(true));
        verify(deletionService, never()).deleteBuild(same(middle), any(), anyBoolean(), anyBoolean());
        verify(deletionService, never()).deleteBuild(same(newest), any(), anyBoolean(), anyBoolean());
    }

    private static BuildRun buildRun(String number, Date started) {
        BuildRun br = mock(BuildRun.class);
        when(br.getName()).thenReturn(BUILD_NAME);
        when(br.getNumber()).thenReturn(number);
        when(br.getBuildRepo()).thenReturn(BUILD_REPO);
        when(br.getStartedDate()).thenReturn(started);
        when(br.getStarted()).thenReturn(String.valueOf(started.getTime()));
        return br;
    }

    private static Date daysAgo(int days) {
        return new Date(System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L);
    }

    private void givenBuilds(BuildRun... runs) {
        Set<BuildRun> set = new LinkedHashSet<>(List.of(runs));
        when(readService.searchBuildsByNameInternally(BUILD_NAME, BUILD_REPO)).thenReturn(set);
    }

    private static BuildRetention retentionByDate(Date minDate) {
        BuildRetention r = new BuildRetention(false);
        r.setMinimumBuildDate(minDate);
        return r;
    }

    private static BuildRetention retentionByCount(int count) {
        BuildRetention r = new BuildRetention(false);
        r.setCount(count);
        return r;
    }
}
