package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.dao.BuildsDao;
import com.jfrog.build.tx.BuildTxRunner;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ConflictException;
import org.artifactory.exception.http.ForbiddenException;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.jfrog.access.common.ResourceType;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildRetention;
import org.jfrog.build.api.Issue;
import org.jfrog.build.api.Issues;
import org.jfrog.build.api.release.PromotionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.jfrog.build.acl.model.BuildAccessLogType.BUILD_CREATE_DENIED;
import static com.jfrog.build.acl.model.BuildXrayEvent.CREATE_BUILD;
import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildCreationServiceTest {

    private BuildCreationService creationService;

    private BuildBridgeToArtifactory bridgeToArtifactory;

    private BuildTxRunner txRunner;
    private BuildDatabaseService databaseService;
    private BuildPermissionService permissionService;
    private BuildSysConfigService sysConfigService;

    @BeforeEach
    void setUp() {
        creationService = newCreationService(null, null);
    }

    private BuildCreationService newCreationService(
            BuildReadService readServiceOverride,
            BuildRetentionService retentionServiceOverride) {
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        BuildsDao buildsDao = mock(BuildsDao.class);
        txRunner = mock(BuildTxRunner.class);
        BuildAqlService aqlService = mock(BuildAqlService.class);
        permissionService = mock(BuildPermissionService.class);
        sysConfigService = mock(BuildSysConfigService.class);
        databaseService = mock(BuildDatabaseService.class);
        when(bridgeToArtifactory.getRepoKeys(any(), any()))
                .thenReturn(Set.of(BUILDINFO_REPO_KEY, "phoenix-build-info", "prjx-build-info"));
        when(bridgeToArtifactory.getProjectSpec("phoenix")).thenReturn(
                Optional.of(ProjectSlimEntity.builder().projectKey("phoenix").name("Phoenix").build()));
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(bridgeToArtifactory).doAsSystem(any(Runnable.class));
        BuildRepoService repoService = new BuildRepoService(bridgeToArtifactory);
        repoService.refreshAllBuildsRepositories();
        BuildReadService readService = readServiceOverride != null ? readServiceOverride : new BuildReadService(
                bridgeToArtifactory,
                buildsDao,
                databaseService,
                permissionService,
                repoService);
        BuildArtifactService artifactService = new BuildArtifactService(
                bridgeToArtifactory,
                aqlService,
                permissionService,
                readService,
                repoService,
                sysConfigService);
        BuildDeletionService deletionService = new BuildDeletionService(
                bridgeToArtifactory,
                txRunner,
                aqlService,
                artifactService,
                databaseService,
                permissionService,
                readService,
                sysConfigService);
        BuildRetentionService retentionService = retentionServiceOverride != null ? retentionServiceOverride
                : new BuildRetentionService(
                bridgeToArtifactory,
                deletionService,
                permissionService,
                readService,
                sysConfigService);
        return new BuildCreationService(
                bridgeToArtifactory,
                txRunner,
                databaseService,
                permissionService,
                readService,
                repoService,
                retentionService,
                sysConfigService
        );
    }

    @Test
    void createBuild_shouldThrow_whenRepoKeyMissingBuildInfoSuffix() {
        doNothing().when(sysConfigService).assertNotArtifactoryColdInstance();
        Build build = new Build();
        build.setName("b");
        build.setNumber("1");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> creationService.createBuild(build, "invalid-repo"));
        assertEquals("Build info repository 'invalid-repo' does not exist", e.getMessage());
        verify(txRunner, never()).runSupplierTx(any());
    }

    @Test
    void createBuild_shouldCallPersistBuildAndReturnParsedSha256_whenValidRepo() {
        doNothing().when(sysConfigService).assertNotArtifactoryColdInstance();
        when(bridgeToArtifactory.getProjectSpec("artifactory")).thenReturn(
                Optional.of(ProjectSlimEntity.builder().projectKey("artifactory").name("Default").build()));
        when(bridgeToArtifactory.getProjectForResource(BUILDINFO_REPO_KEY, ResourceType.REPO)).thenReturn(
                DEFAULT_PROJECT);
        when(bridgeToArtifactory.getCurrentUsername()).thenReturn("user");
        when(bridgeToArtifactory.upload(anyString(), anyString())).thenReturn(
                "{\"originalChecksums\":{\"sha256\":\"abc123\"}}");
        when(txRunner.runSupplierTx(any())).thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());

        Build build = new Build();
        build.setName("mybuild");
        build.setNumber("1");
        build.setStarted(formatBuildTime(1234567890000L));

        String sha256 = creationService.createBuild(build, BUILDINFO_REPO_KEY);

        assertEquals("abc123", sha256);
        verify(sysConfigService).assertNotArtifactoryColdInstance();
        verify(txRunner).runSupplierTx(any());
        verify(bridgeToArtifactory).upload(anyString(), anyString());
    }

    @Test
    void createBuild_shouldThrowBadRequest_whenBuildNameIsHidden() {
        // RTDEV-94003: publish of a ".jfrog" build must be rejected before it is deployed as build json.
        Build hiddenBuild = new Build();
        hiddenBuild.setName(".jfrog");
        hiddenBuild.setNumber("1");
        hiddenBuild.setStarted(formatBuildTime(1234567890000L));

        assertThrows(BadRequestException.class, () -> creationService.createBuild(hiddenBuild, BUILDINFO_REPO_KEY));
        verify(txRunner, never()).runSupplierTx(any());
        verify(bridgeToArtifactory, never()).upload(anyString(), anyString());
    }

    @Test
    void createBuildInternal_shouldThrowBadRequest_whenBuildNameIsHidden() {
        // Security chokepoint (RTDEV-94003): a ".jfrog" build must be rejected before the permission check /
        // DB write, covering the /api/copy synthetic-build ingestion vector that also reaches this method.
        Build hiddenBuild = new Build();
        hiddenBuild.setName(".jfrog");
        hiddenBuild.setNumber("1");
        hiddenBuild.setStarted(formatBuildTime(1234567890000L));
        BuildWrapper wrapper = new BuildWrapper(hiddenBuild, BUILDINFO_REPO_KEY);

        assertThrows(BadRequestException.class, () -> creationService.createBuildInternal(wrapper));
        verify(permissionService, never()).canUploadBuild(any(), any(), any(), any());
        verify(databaseService, never()).addBuild(any());
    }

    @Test
    void createBuildInternal_shouldThrowForbidden_whenUserCannotUpload() {
        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setStarted("123");
        BuildWrapper wrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);
        when(permissionService.canUploadBuild(any(), any(), any(), any())).thenReturn(false);

        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> creationService.createBuildInternal(wrapper));
        assertEquals("User is not authorized to access build info", e.getMessage());
        verify(bridgeToArtifactory).logAccessEvent(eq(BUILD_CREATE_DENIED), eq("b:1"));
        verify(databaseService, never()).addBuild(any());
    }

    @Test
    void createBuildInternal_shouldCreateBuildAndRunPluginInterceptor_whenUserCanUpload() {
        long startedMillis = 1234567890000L;
        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setStarted(formatBuildTime(startedMillis));
        BuildWrapper wrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);
        when(permissionService.canUploadBuild(any(), any(), any(), any())).thenReturn(true);
        when(databaseService.getBuildStoringRepo(any(), any(), any())).thenReturn(BUILDINFO_REPO_KEY);

        creationService.createBuildInternal(wrapper);

        verify(databaseService).addBuild(wrapper);
        verify(bridgeToArtifactory).runXrayInterceptor(
                eq(CREATE_BUILD), eq("b"), eq("1"), eq(startedMillis), eq(BUILDINFO_REPO_KEY));
        verify(bridgeToArtifactory).runPluginInterceptor(any(), any(), anyString());
    }

    @Test
    void createBuildInternal_shouldThrowConflict_whenBuildAlreadyExistsInSameRepo() {
        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setStarted(formatBuildTime(1234567890000L));
        BuildWrapper wrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);
        when(permissionService.canUploadBuild(any(), any(), any(), any())).thenReturn(true);
        when(databaseService.exists(wrapper)).thenReturn(true);
        when(databaseService.getBuildStoringRepo("b", "1", build.getStarted())).thenReturn(BUILDINFO_REPO_KEY);

        ConflictException e = assertThrows(ConflictException.class,
                () -> creationService.createBuildInternal(wrapper));
        assertTrue(e.getMessage().contains("already exists"));
        assertTrue(e.getMessage().contains("artifactory-build-info"));
        verify(databaseService, never()).addBuild(any());
    }

    @Test
    void createBuildInternal_shouldThrowConflictWithOtherRepo_whenBuildExistsInDifferentRepo() {
        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setStarted(formatBuildTime(1234567890000L));
        BuildWrapper wrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);
        when(permissionService.canUploadBuild(any(), any(), any(), any())).thenReturn(true);
        when(databaseService.exists(wrapper)).thenReturn(true);
        when(databaseService.getBuildStoringRepo("b", "1", build.getStarted())).thenReturn("phoenix-build-info");

        ConflictException e = assertThrows(ConflictException.class,
                () -> creationService.createBuildInternal(wrapper));
        assertTrue(e.getMessage().contains("already exists in repository"));
        assertTrue(e.getMessage().contains("phoenix-build-info"));
        verify(databaseService, never()).addBuild(any());
    }

    @Test
    void createBuildInternal_shouldRunRetention_whenBuildRetentionConfigured() {
        BuildRetention retention = mock(BuildRetention.class);
        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setStarted(formatBuildTime(1234567890000L));
        build.setBuildRetention(retention);
        BuildWrapper wrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);
        BuildRetentionService retentionService = mock(BuildRetentionService.class);
        creationService = newCreationService(null, retentionService);
        when(permissionService.canUploadBuild(any(), any(), any(), any())).thenReturn(true);

        creationService.createBuildInternal(wrapper);

        verify(retentionService).discardOldBuilds("b", BUILDINFO_REPO_KEY, retention, false);
    }

    @Test
    void parseBuildSha256_shouldReturnSha256_whenValidJson() {
        String json = "{\"originalChecksums\":{\"sha256\":\"deadbeef\"}}";
        assertEquals("deadbeef", creationService.parseBuildSha256(json));
    }

    @Test
    void parseBuildSha256_shouldReturnEmpty_whenEmptyOrInvalidJson() {
        assertEquals("", creationService.parseBuildSha256(""));
        assertEquals("", creationService.parseBuildSha256("not-json"));
    }

    @Test
    void createBuild_shouldThrow_whenProjectNotFoundForNonDefaultProjectRepo() {
        when(bridgeToArtifactory.getProjectSpec("prjx")).thenReturn(Optional.empty());
        doNothing().when(sysConfigService).assertNotArtifactoryColdInstance();
        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setStarted("123");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> creationService.createBuild(build, "prjx-build-info"));
        assertTrue(e.getMessage().contains("does not exist"));
    }

    @Test
    void createBuild_shouldAssignBuildInfoRepoToProject_whenRepoMappedToDefaultProject() {
        doNothing().when(sysConfigService).assertNotArtifactoryColdInstance();
        when(bridgeToArtifactory.getProjectForResource("phoenix-build-info", ResourceType.REPO)).thenReturn(
                DEFAULT_PROJECT);
        when(bridgeToArtifactory.getCurrentUsername()).thenReturn("user");
        when(bridgeToArtifactory.upload(anyString(), anyString())).thenReturn("{}");
        when(txRunner.runSupplierTx(any())).thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());

        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setStarted(formatBuildTime(1234567890000L));

        creationService.createBuild(build, "phoenix-build-info");

        verify(bridgeToArtifactory).upsertProjectResource(
                eq("phoenix"), eq(ResourceType.REPO), any(ProjectResourceEntity.class), eq(true));
    }

    @Test
    void createBuild_shouldCreateBuildInfoRepo_whenProjectExistsAndRepoMissing() {
        doNothing().when(sysConfigService).assertNotArtifactoryColdInstance();
        when(bridgeToArtifactory.getProjectSpec("newproj")).thenReturn(
                Optional.of(ProjectSlimEntity.builder().projectKey("newproj").name("New").build()));
        when(bridgeToArtifactory.getRepoDescriptor("newproj-build-info")).thenReturn(Optional.empty());
        when(bridgeToArtifactory.getCurrentUsername()).thenReturn("user");
        when(bridgeToArtifactory.upload(anyString(), anyString())).thenReturn("{}");
        when(txRunner.runSupplierTx(any())).thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());

        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setStarted(formatBuildTime(1234567890000L));

        creationService.createBuild(build, "newproj-build-info");

        verify(bridgeToArtifactory).createBuildRepo("newproj-build-info");
        verify(bridgeToArtifactory).upsertProjectResource(
                eq("newproj"), eq(ResourceType.REPO), any(ProjectResourceEntity.class), eq(true));
    }

    @Test
    void createBuild_shouldAggregatePreviousBuildIssues_whenLatestHasNewStatus() {
        BuildReadService readService = mock(BuildReadService.class);
        creationService = newCreationService(readService, null);
        doNothing().when(sysConfigService).assertNotArtifactoryColdInstance();
        when(bridgeToArtifactory.getProjectSpec("artifactory")).thenReturn(Optional.empty());
        when(bridgeToArtifactory.getProjectForResource(BUILDINFO_REPO_KEY, ResourceType.REPO)).thenReturn(
                DEFAULT_PROJECT);
        when(bridgeToArtifactory.getCurrentUsername()).thenReturn("user");
        when(bridgeToArtifactory.upload(anyString(), anyString())).thenReturn("{}");
        when(txRunner.runSupplierTx(any())).thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());

        Issue previousIssue = new Issue("PREV-1", "http://prev", "previous");
        Issues previousIssues = new Issues();
        previousIssues.addIssue(previousIssue);
        Build previousBuild = new Build();
        previousBuild.setName("mybuild");
        previousBuild.setNumber("1");
        previousBuild.setStarted(formatBuildTime(1000L));
        previousBuild.setIssues(previousIssues);
        BuildWrapper latestWrapper = new BuildWrapper(previousBuild, BUILDINFO_REPO_KEY);
        when(readService.getLatestBuildByNameAndStatus("mybuild", BUILDINFO_REPO_KEY, "LATEST"))
                .thenReturn(latestWrapper);

        Issue newIssue = new Issue("NEW-1", "http://new", "new");
        Issues newIssues = aggregateIssues(newIssue);
        Build build = new Build();
        build.setName("mybuild");
        build.setNumber("2");
        build.setStarted(formatBuildTime(2000L));
        build.setIssues(newIssues);

        creationService.createBuild(build, BUILDINFO_REPO_KEY);

        assertEquals(2, build.getIssues().getAffectedIssues().size());
        assertTrue(build.getIssues().getAffectedIssues().stream()
                .anyMatch(issue -> "PREV-1".equals(issue.getKey()) && issue.isAggregated()));
    }

    @Test
    void createBuild_shouldNotAggregatePreviousIssues_whenLatestAlreadyHasAggregationStatus() {
        BuildReadService readService = mock(BuildReadService.class);
        creationService = newCreationService(readService, null);
        doNothing().when(sysConfigService).assertNotArtifactoryColdInstance();
        when(bridgeToArtifactory.getProjectSpec("artifactory")).thenReturn(Optional.empty());
        when(bridgeToArtifactory.getProjectForResource(BUILDINFO_REPO_KEY, ResourceType.REPO)).thenReturn(
                DEFAULT_PROJECT);
        when(bridgeToArtifactory.getCurrentUsername()).thenReturn("user");
        when(bridgeToArtifactory.upload(anyString(), anyString())).thenReturn("{}");
        when(txRunner.runSupplierTx(any())).thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());

        PromotionStatus released = new PromotionStatus();
        released.setStatus(PromotionStatus.RELEASED);
        Build previousBuild = new Build();
        previousBuild.setName("mybuild");
        previousBuild.setNumber("1");
        previousBuild.setStarted(formatBuildTime(1000L));
        previousBuild.setStatuses(List.of(released));
        previousBuild.setIssues(new Issues());
        BuildWrapper latestWrapper = new BuildWrapper(previousBuild, BUILDINFO_REPO_KEY);
        when(readService.getLatestBuildByNameAndStatus("mybuild", BUILDINFO_REPO_KEY, "LATEST"))
                .thenReturn(latestWrapper);

        Issue newIssue = new Issue("NEW-1", "http://new", "new");
        Issues newIssues = aggregateIssues(newIssue);
        Build build = new Build();
        build.setName("mybuild");
        build.setNumber("2");
        build.setStarted(formatBuildTime(2000L));
        build.setIssues(newIssues);

        creationService.createBuild(build, BUILDINFO_REPO_KEY);

        assertEquals(1, build.getIssues().getAffectedIssues().size());
    }

    @Test
    void updateBuild_shouldAssertPermissionsAndRecreateBuild() {
        doNothing().when(sysConfigService).assertNotArtifactoryColdInstance();
        doNothing().when(permissionService).assertDeletePermissions(any(), any(), any(), any());
        doNothing().when(permissionService).assertUploadPermissions(any(), any(), any(), any());
        when(bridgeToArtifactory.getProjectSpec("artifactory")).thenReturn(Optional.empty());
        when(bridgeToArtifactory.getProjectForResource(BUILDINFO_REPO_KEY, ResourceType.REPO)).thenReturn(
                DEFAULT_PROJECT);
        when(bridgeToArtifactory.getCurrentUsername()).thenReturn("user");
        when(bridgeToArtifactory.upload(anyString(), anyString())).thenReturn(
                "{\"originalChecksums\":{\"sha256\":\"updated\"}}");
        when(txRunner.runSupplierTx(any())).thenAnswer(inv -> inv.getArgument(0, Supplier.class).get());
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(txRunner).runTx(any(Runnable.class));

        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setStarted(formatBuildTime(1234567890000L));
        BuildWrapper wrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);

        creationService.updateBuild(wrapper);

        verify(permissionService).assertDeletePermissions("b", "1", build.getStarted(), BUILDINFO_REPO_KEY);
        verify(permissionService).assertUploadPermissions("b", "1", build.getStarted(), BUILDINFO_REPO_KEY);
        verify(txRunner).runTx(any(Runnable.class));
        verify(bridgeToArtifactory).upload(anyString(), anyString());
    }

    private static Issues aggregateIssues(Issue issue) {
        Issues issues = new Issues();
        issues.setAggregateBuildIssues(true);
        issues.setAggregationBuildStatus(PromotionStatus.RELEASED);
        issues.addIssue(issue);
        return issues;
    }
}
