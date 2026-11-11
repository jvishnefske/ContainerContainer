package org.artifactory.repo.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.RepoBulkRemovalReport;
import org.artifactory.api.common.RepoRemovalStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.exception.WorkerStopException;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.LocalRepositoryRepoTypeConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.repo.interceptor.RepoConfigCudEventEmitter;
import org.artifactory.repo.metadata.RepoConfigChangeFactory;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_FAILED_DEPENDENCY;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_MULTI_STATUS;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.api.common.RepoRemovalStatus.REPO_NOT_FOUND;
import static org.artifactory.api.common.RepoRemovalStatusHolder.ArtifactDeleteFailure;
import static org.artifactory.api.common.RepoRemovalStatusHolder.RemoveRepositoryFailureReport;
import static org.artifactory.api.common.RepoRemovalStatusHolder.RemoveRepositoryReport;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
class RemoveRepositoryServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "repoKey";

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private RemoveRepositoryValidator removeRepositoryValidator;
    @Mock
    private RepoConfigChangeInterceptor repoConfigChangeInterceptor;
    @Mock
    private RepoConfigCudEventEmitter repoConfigCudEventEmitter;
    @Mock
    private InternalRepositoryConfigService repositoryConfigService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaCommonAddon haCommonAddon;
    @Mock
    private ConflictsGuard<String> repoDeletionConflictsGuard;
    @Mock
    private FederatedRepoAddon federatedRepoAddon;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private DeleteRepositoryContentService deleteRepositoryContentService;
    @Mock
    private SecurityService securityService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private RepoConfigChangeFactory repoConfigChangeFactory;
    @InjectMocks
    private RemoveRepositoryServiceImpl removeRepositoryService;
    private AutoCloseable mocks;

    private static RepoRemovalStatusHolder createPositiveStatusHolder() {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder();
        statusHolder.status("OK", log);
        return statusHolder;
    }

    private static RepoRemovalStatusHolder createNegativeStatusHolder() {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder();
        statusHolder.error("error", SC_FORBIDDEN, log);
        return statusHolder;
    }

    public static Stream<Arguments> repoConfigAndExpectedSuccessMsg() {
        return Stream.of(
                Arguments.of(getVirtualRepoConfig(),
                        "Repository '%s' has been removed successfully.".formatted(REPO_KEY)),
                Arguments.of(getRealRepoConfig(),
                        "Repository '%s' and all its content have been removed successfully.".formatted(REPO_KEY))
        );
    }

    private static RepoConfig getVirtualRepoConfig() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        BaseConfig baseConfig = mock(BaseConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        when(repoConfig.isReal()).thenReturn(false);
        when(repoConfig.getRepoType()).thenReturn(VIRTUAL);
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);
        when(baseConfig.isFederated()).thenReturn(false);
        return repoConfig;
    }

    private static RepoConfig getRealRepoConfig() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        BaseConfig baseConfig = mock(BaseConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getRepoType()).thenReturn(LOCAL);
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);
        when(baseConfig.isFederated()).thenReturn(false);
        return repoConfig;
    }

    @BeforeEach
    void beforeEach() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        prepareMocks();
    }

    private void prepareMocks() {
        prepareInterceptorMock();
        prepareRepoConfigServiceMock(REPO_KEY);
        prepareAddonsMocks();
        prepareCentralConfigServiceMock();
        prepareRepoConfigChangeFactoryMock();
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(securityService).doAsSystem(any(Runnable.class));
    }

    private void prepareRepoConfigChangeFactoryMock() {
        when(repoConfigChangeFactory.delete(any(RepoConfig.class))).thenAnswer(invocation ->
                RepoConfigChange.deleteOf(invocation.getArgument(0, RepoConfig.class)));
    }

    private void prepareCentralConfigServiceMock() {
        when(artifactoryContext.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        MutableCentralConfigDescriptor configDescriptor =
                mock(MutableCentralConfigDescriptor.class, RETURNS_DEEP_STUBS);
        when(centralConfigService.getMutableDescriptor()).thenReturn(configDescriptor);
    }

    @SneakyThrows
    private void prepareAddonsMocks() {
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        doReturn(repoDeletionConflictsGuard).when(haCommonAddon).getConflictsGuard(anyString());
        when(repoDeletionConflictsGuard.tryToLock(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        doNothing().when(repoDeletionConflictsGuard).unlock(anyString());
        when(addonsManager.addonByType(FederatedRepoAddon.class)).thenReturn(federatedRepoAddon);
    }

    private void prepareRepoConfigServiceMock(String repoKey) {
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(anyString())).thenReturn(
                LocalRepoConfig.create(repoKey, Generic));
        when(artifactoryContext.beanForType(InternalRepositoryConfigService.class)).thenReturn(repositoryConfigService);
    }

    private void prepareInterceptorMock() {
        when(artifactoryContext.beansForType(RepoConfigChangeInterceptor.class)).thenReturn(
                Map.of("repoConfigChangeInterceptor", repoConfigChangeInterceptor));
        removeRepositoryService.init();
    }

    @AfterEach
    void afterEach() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        if (mocks != null) {
            mocks.close();
        }
    }

    @ParameterizedTest
    @MethodSource("repoConfigAndExpectedSuccessMsg")
    void testRemoveRepository_HappyFlow(RepoConfig repoConfig, String expectedSuccessMsg) {
        givenAllValidationsPass();

        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(REPO_KEY)).thenReturn(repoConfig);

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertFalse(statusHolder.hasErrors(), statusHolder.toString());
        assertFalse(statusHolder.hasWarnings(), statusHolder.toString());
        assertEquals(SC_OK, statusHolder.getStatusCode());


        verify(repoConfigChangeInterceptor).onBeforeSave(any(RepoConfigChanges.class));
        verify(deleteRepositoryContentService).deleteRepositoryContent(REPO_KEY, statusHolder);
    }

    @Test
    void testRemoveRepository_ValidationFailure() {
        givenValidationFails();

        BasicStatusHolder statusHolder = removeRepositoryService.removeRepository(REPO_KEY);

        assertTrue(statusHolder.hasErrors());
    }

    @Test
    void testRemoveRepository_RepoNotFound() {
        givenRepoConfigNotFound(REPO_KEY);
        BasicStatusHolder statusHolder = removeRepositoryService.removeRepository(REPO_KEY);
        assertEquals(REPO_NOT_FOUND.getStatusCode(), statusHolder.getStatusCode());
    }

    @SneakyThrows
    @Test
    void removeRepository_shouldReturnDeletionInProgressStatus_whenLockAlreadyTaken() {
        givenAllValidationsPass();
        when(repoDeletionConflictsGuard.tryToLock(eq(REPO_KEY), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertFalse(statusHolder.hasWarnings());
        assertEquals(SC_ACCEPTED, statusHolder.getStatusCode());
        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(anyString(), any());
    }

    @SneakyThrows
    @Test
    void removeRepository_shouldReturnInternalServerErrorStatus_whenLockAcquisitionFails() {
        givenAllValidationsPass();
        when(repoDeletionConflictsGuard.tryToLock(eq(REPO_KEY), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("lock interrupted"));

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertEquals(SC_INTERNAL_SERVER_ERROR, statusHolder.getStatusCode());
        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(anyString(), any());
        Thread.interrupted();
    }

    @SneakyThrows
    @Test
    void removeRepositories_shouldAddDeletionInProgressToReport_whenOneRepoLockFails() {
        String repoKey1 = "repo-a";
        String repoKey2 = "repo-b";
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(repoKey1))
                .thenReturn(LocalRepoConfig.create(repoKey1, Generic));
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(repoKey2))
                .thenReturn(LocalRepoConfig.create(repoKey2, Generic));
        when(repoDeletionConflictsGuard.tryToLock(eq(repoKey1), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);
        when(repoDeletionConflictsGuard.tryToLock(eq(repoKey2), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        givenAllValidationsPass();

        LinkedHashSet<String> repoKeys = new LinkedHashSet<>();
        repoKeys.add(repoKey1);
        repoKeys.add(repoKey2);
        RepoBulkRemovalReport bulkReport = removeRepositoryService.removeRepositories(repoKeys);

        Set<RemoveRepositoryReport> reports = bulkReport.getReports();
        assertEquals(2, reports.size());
        RemoveRepositoryReport reportRepoA = reports.stream()
                .filter(r -> repoKey1.equals(r.getRepoKey()))
                .findFirst()
                .orElseThrow();
        RemoveRepositoryReport reportRepoB = reports.stream()
                .filter(r -> repoKey2.equals(r.getRepoKey()))
                .findFirst()
                .orElseThrow();
        assertTrue(reportRepoA.isSuccess());
        assertTrue(reportRepoB.isSuccess());
        verify(deleteRepositoryContentService).deleteRepositoryContent(eq(repoKey2),
                any(RepoRemovalStatusHolder.class));
        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(eq(repoKey1), any());
    }

    @Test
    void removeRepositories_shouldDeleteRemainingReposAndReportEach_whenOneRepoFails() {
        String repoA = "repo-a";
        String repoB = "repo-b";
        String repoC = "repo-c";
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(repoA))
                .thenReturn(LocalRepoConfig.create(repoA, Generic));
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(repoB))
                .thenReturn(LocalRepoConfig.create(repoB, Generic));
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(repoC))
                .thenReturn(LocalRepoConfig.create(repoC, Generic));
        givenAllValidationsPass();
        // repo-b content deletion fails (the failure is recorded on the passed-in status holder, which carries
        // repo-b's key); repo-a and repo-c succeed (Mockito void default no-op).
        doAnswer(invocation -> {
            RepoRemovalStatusHolder repoBStatus = invocation.getArgument(1, RepoRemovalStatusHolder.class);
            repoBStatus.error("failed to delete artifact", SC_FORBIDDEN, log);
            throw new RepositoryContentDeletionFailedException(repoBStatus);
        }).when(deleteRepositoryContentService)
                .deleteRepositoryContent(eq(repoB), any(RepoRemovalStatusHolder.class));

        LinkedHashSet<String> repoKeys = new LinkedHashSet<>();
        repoKeys.add(repoA);
        repoKeys.add(repoB);
        repoKeys.add(repoC);
        RepoBulkRemovalReport bulkReport = removeRepositoryService.removeRepositories(repoKeys);

        // Best-effort: a mid-batch failure does not abort the batch; every repo is attempted and every per-repo
        // status is surfaced in the single bulk report.
        Set<RemoveRepositoryReport> reports = bulkReport.getReports();
        assertEquals(3, reports.size());
        assertTrue(reportFor(reports, repoA).isSuccess());
        assertFalse(reportFor(reports, repoB).isSuccess());
        assertTrue(reportFor(reports, repoC).isSuccess());
        // repo-c is still attempted even though repo-b (earlier in the batch) failed.
        verify(deleteRepositoryContentService).deleteRepositoryContent(eq(repoC), any(RepoRemovalStatusHolder.class));
    }

    @Test
    void testRemoveRepository_DeleteRepositoryContent_Failure() {
        givenAllValidationsPass();
        givenDeleteRepoContentFailure();

        BasicStatusHolder statusHolder = removeRepositoryService.removeRepository(REPO_KEY);

        assertTrue(statusHolder.hasErrors(), statusHolder.toString());
        assertEquals(1, statusHolder.getEntries().size());
        assertEquals(SC_FORBIDDEN, statusHolder.getStatusCode(), statusHolder.toString());
    }

    @Test
    void testRemoveRepository_InterceptionValidationFailure() {
        givenAllValidationsPass();
        givenRepoConfigChangeInterceptorFailure(new ConfigurationException("Failed by test", SC_FORBIDDEN));

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertTrue(statusHolder.hasWarnings());
        assertEquals(SC_FORBIDDEN, statusHolder.getStatusCode());

        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(anyString(), eq(statusHolder));
    }

    @Test
    void removeRepository_shouldFireBeforeDeleteInterceptor_beforePreparingRepoForRemoval() {
        givenAllValidationsPass();

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertFalse(statusHolder.hasErrors(), statusHolder.toString());
        InOrder inOrder = inOrder(repoConfigChangeInterceptor, repositoryConfigService);
        inOrder.verify(repoConfigChangeInterceptor).onBeforeSave(any(RepoConfigChanges.class));
        inOrder.verify(repositoryConfigService).updateRepoConfigSilently(any());
    }

    @Test
    void removeRepository_shouldReturnConflictStatus_whenWorkerStopExceptionThrown() {
        givenAllValidationsPass();
        givenRepoConfigChangeInterceptorFailure(
                new WorkerStopException("BEFORE_REPO_DELETE Worker Event Error: blocked by policy"));

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertTrue(statusHolder.hasWarnings());
        assertEquals(SC_CONFLICT, statusHolder.getStatusCode());
        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(anyString(), any());
    }

    @Test
    void removeRepositories_shouldReturnConflictReport_whenWorkerStopExceptionThrown() {
        LinkedHashSet<String> repoKeys = CollectionUtils.newLinkedHashSet("repo1", "repo2");
        givenConfigsFound("repo1", "repo2");
        givenAllValidationsPass();
        givenRepoConfigChangeInterceptorFailure(
                new WorkerStopException("BEFORE_REPO_DELETE Worker Event Error: blocked by policy"));

        RepoBulkRemovalReport bulkReport = removeRepositoryService.removeRepositories(repoKeys);

        // Each repo fires its own BEFORE event inside its lock; both are vetoed independently.
        assertEquals(SC_CONFLICT, bulkReport.getStatusCode());
        assertEquals(2, bulkReport.getReports().size());
        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(anyString(), any());
    }

    @Test
    void testRemoveRepository_FederationRemovalRemoteMembersFailure() {
        givenAllValidationsPass();
        givenFederatedRepository();
        givenFederationRemovalRemoteMembersFailure();

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertTrue(statusHolder.hasWarnings());
        assertEquals(SC_FAILED_DEPENDENCY, statusHolder.getStatusCode());

        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(anyString(), eq(statusHolder));
    }

    @Test
    void testRemoveRepository_RemoteRepoWithFederation_Success() {
        givenAllValidationsPass();
        givenRemoteRepositoryWithFederation();

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertFalse(statusHolder.hasErrors(), statusHolder.toString());
        assertFalse(statusHolder.hasWarnings(), statusHolder.toString());
        assertEquals(SC_OK, statusHolder.getStatusCode());

        verify(federatedRepoAddon).removeRepoFromFederationMembers(any(RepoConfig.class));
        verify(deleteRepositoryContentService).deleteRepositoryContent(eq(REPO_KEY), eq(statusHolder));
    }

    @Test
    void testRemoveRepository_RemoteRepoWithFederation_RemovalFailure() {
        givenAllValidationsPass();
        givenRemoteRepositoryWithFederation();
        givenFederationRemovalRemoteMembersFailure();

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertTrue(statusHolder.hasWarnings());
        assertEquals(SC_FAILED_DEPENDENCY, statusHolder.getStatusCode());

        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(anyString(), eq(statusHolder));
    }

    @Test
    void testRemoveRepository_RemoteRepoWithoutFederation_SkipsFederationRemoval() {
        givenAllValidationsPass();
        givenRemoteRepositoryWithoutFederation();

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertFalse(statusHolder.hasErrors(), statusHolder.toString());
        assertFalse(statusHolder.hasWarnings(), statusHolder.toString());
        assertEquals(SC_OK, statusHolder.getStatusCode());

        verify(federatedRepoAddon, never()).removeRepoFromFederationMembers(any(RepoConfig.class));
        verify(deleteRepositoryContentService).deleteRepositoryContent(eq(REPO_KEY), eq(statusHolder));
    }

    @Test
    void testRemoveRepository_LocalRepoWithFederation_Success() {
        givenAllValidationsPass();
        givenLocalRepositoryWithFederation();

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertFalse(statusHolder.hasErrors(), statusHolder.toString());
        assertFalse(statusHolder.hasWarnings(), statusHolder.toString());
        assertEquals(SC_OK, statusHolder.getStatusCode());

        verify(federatedRepoAddon).removeRepoFromFederationMembers(any(RepoConfig.class));
        verify(deleteRepositoryContentService).deleteRepositoryContent(eq(REPO_KEY), eq(statusHolder));
    }

    @Test
    void testRemoveRepository_LocalRepoWithFederation_RemovalFailure() {
        givenAllValidationsPass();
        givenLocalRepositoryWithFederation();
        givenFederationRemovalRemoteMembersFailure();

        RepoRemovalStatusHolder statusHolder =
                (RepoRemovalStatusHolder) removeRepositoryService.removeRepository(REPO_KEY);

        assertTrue(statusHolder.hasWarnings());
        assertEquals(SC_FAILED_DEPENDENCY, statusHolder.getStatusCode());

        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(anyString(), eq(statusHolder));
    }

    @Test
    void testPrepareRepoForRemoval_RealRepoConfig_NotBlackedOut() {
        RealRepoConfig realRepoConfig = mock(RealRepoConfig.class);
        LocalRepositoryRepoTypeConfig repoTypeConfig = mock(LocalRepositoryRepoTypeConfig.class);
        when(realRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.isBlackedOut()).thenReturn(false);
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(REPO_KEY)).thenReturn(realRepoConfig);

        removeRepositoryService.prepareRepoForRemoval(realRepoConfig);

        verify(repoTypeConfig).setBlackedOut(true);
        verify(repositoryConfigService).updateRepoConfigSilently(realRepoConfig);
    }

    @Test
    void testPrepareRepoForRemoval_RemoteRepoConfig_NotStoresArtifactsLocally() {
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.isStoreArtifactsLocally()).thenReturn(false);
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(REPO_KEY)).thenReturn(remoteRepoConfig);

        removeRepositoryService.prepareRepoForRemoval(remoteRepoConfig);

        verify(repoTypeConfig).setStoreArtifactsLocally(true);
        verify(repositoryConfigService).updateRepoConfigSilently(remoteRepoConfig);
    }

    @Test
    void testPrepareRepoForRemoval_RemoteRepoConfig_storesArtifactsLocally() {
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.isStoreArtifactsLocally()).thenReturn(true);
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(REPO_KEY)).thenReturn(remoteRepoConfig);

        removeRepositoryService.prepareRepoForRemoval(remoteRepoConfig);

        verify(repoTypeConfig, never()).setStoreArtifactsLocally(true);
        verify(repositoryConfigService).updateRepoConfigSilently(remoteRepoConfig);
    }

    @Test
    void testPrepareRepoForRemoval_OtherRepoConfig() {
        RepoConfig otherRepoConfig = mock(RepoConfig.class);
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(REPO_KEY)).thenReturn(otherRepoConfig);

        removeRepositoryService.prepareRepoForRemoval(otherRepoConfig);

        verify(repositoryConfigService, never()).updateRepoConfigSilently(any());
    }

    @Test
    void testRemoveRepositories_Success() {
        String repoKey1 = "repoKey1";
        String repoKey2 = "repoKey2";
        prepareRepoConfigServiceMock(repoKey1);
        prepareRepoConfigServiceMock(repoKey2);
        LinkedHashSet<String> repoKeys = new LinkedHashSet<>();
        repoKeys.add(repoKey1);
        repoKeys.add(repoKey2);
        givenAllValidationsPass();

        RepoBulkRemovalReport bulkReport = removeRepositoryService.removeRepositories(repoKeys);
        assertEquals(SC_OK, bulkReport.getStatusCode());

        Set<RemoveRepositoryReport> reports = bulkReport.getReports();
        List<String> repoKeysList = new ArrayList<>();
        repoKeysList.add(repoKey1);
        repoKeysList.add(repoKey2);

        for (RemoveRepositoryReport report : reports) {
            assertTrue(repoKeysList.contains(report.getRepoKey()));
            assertTrue(report.isSuccess());
        }
    }

    @Test
    void testRemoveRepositories_EmptyOrNullKeys() {
        LinkedHashSet<String> repoKeys = new LinkedHashSet<>();

        RepoBulkRemovalReport bulkReport = removeRepositoryService.removeRepositories(repoKeys);
        assertEquals(SC_BAD_REQUEST, bulkReport.getStatusCode());
        assertNull(bulkReport.getReports());

        repoKeys = null;

        bulkReport = removeRepositoryService.removeRepositories(repoKeys);
        assertNull(bulkReport.getReports());
    }

    @Test
    void testRemoveRepositories_Failure() {
        LinkedHashSet<String> repoKeys = CollectionUtils.newLinkedHashSet("repo1", "repo2");
        givenValidationFails();

        RepoBulkRemovalReport bulkReport = removeRepositoryService.removeRepositories(repoKeys);

        assertEquals(SC_FORBIDDEN, bulkReport.getStatusCode());
    }

    @Test
    void testRemoveRepositories_AllReposNotFound() {
        LinkedHashSet<String> repoKeys = CollectionUtils.newLinkedHashSet("missing1", "missing2");
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks("missing1")).thenReturn(null);
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks("missing2")).thenReturn(null);
        when(repositoryService.repositoryByKey("missing1")).thenReturn(null);
        when(repositoryService.repositoryByKey("missing2")).thenReturn(null);

        RepoBulkRemovalReport bulkReport = removeRepositoryService.removeRepositories(repoKeys);

        assertEquals(SC_OK, bulkReport.getStatusCode());
        Set<RemoveRepositoryReport> reports = bulkReport.getReports();
        assertEquals(2, reports.size());
        for (RemoveRepositoryReport report : reports) {
            assertTrue(report.getRepoKey().equals("missing1") || report.getRepoKey().equals("missing2"));
        }
        verify(removeRepositoryValidator, never()).validate(any(RepoConfig.class));
        verify(deleteRepositoryContentService, never()).deleteRepositoryContent(anyString(), any());
    }

    @Test
    void testRemoveRepositories_MiddleRepoNotFoundOnValidation_Success() throws JSONException {
        String nonExistingRepoKey = "non-existing-repo";
        LinkedHashSet<String> repoKeys = CollectionUtils.newLinkedHashSet("repo1", nonExistingRepoKey, "repo2");
        givenRepoConfigNotFound(nonExistingRepoKey);
        givenConfigsFound("repo1", "repo2");

        RepoBulkRemovalReport repoBulkRemovalReport = removeRepositoryService.removeRepositories(repoKeys);

        assertEquals(SC_OK, repoBulkRemovalReport.getStatusCode());
        JSONAssert.assertEquals("""
                {
                  "reports" : [ {
                    "repoKey" : "repo2",
                    "statusMsg" : "Repository 'repo2' and all its content have been removed successfully.",
                    "deletedArtifactsCount" : 0,
                    "success" : true
                  }, {
                    "repoKey" : "non-existing-repo",
                    "statusMsg" : "Cannot delete repository: 'non-existing-repo', repository config does not exist",
                    "deletedArtifactsCount" : 0,
                    "success" : true
                  }, {
                    "repoKey" : "repo1",
                    "statusMsg" : "Repository 'repo1' and all its content have been removed successfully.",
                    "deletedArtifactsCount" : 0,
                    "success" : true
                  } ],
                  "statusMessage" : "All repositories were removed successfully"
                }
                """, JsonUtils.getInstance().writeValueAsString(repoBulkRemovalReport), true);
    }

    @Test
    void testRemoveRepositories_deleteRepositoryContent_Failure() {
        LinkedHashSet<String> repoKeys = new LinkedHashSet<>();
        repoKeys.add("repo1");
        repoKeys.add("repo2");
        givenAllValidationsPass();
        givenDeleteRepoContentFailure();

        RepoBulkRemovalReport bulkReport = removeRepositoryService.removeRepositories(repoKeys);
        assertEquals(SC_FORBIDDEN, bulkReport.getStatusCode());

        Set<RemoveRepositoryReport> reports = bulkReport.getReports();
        for (RemoveRepositoryReport report : reports) {
            assertFalse(report.isSuccess());
            RemoveRepositoryFailureReport failureReport = (RemoveRepositoryFailureReport) report;
            assertEquals(1, failureReport.getErrors().size());
            ArtifactDeleteFailure deleteFailure = failureReport.getErrors().getFirst();
            assertEquals(SC_FORBIDDEN, deleteFailure.status());
            assertEquals("error", deleteFailure.message());
        }
    }

    @Test
    void removeRepositoriesInternal_basicStatusHolderDownstream_doesNotThrowClassCastException() {
        BasicStatusHolder downstreamStatusHolder = new BasicStatusHolder();
        downstreamStatusHolder.error("error", SC_FORBIDDEN, log);
        doThrow(new RepositoryContentDeletionFailedException(downstreamStatusHolder))
                .when(deleteRepositoryContentService)
                .deleteRepositoryContent(eq(REPO_KEY), any(RepoRemovalStatusHolder.class));
        LinkedHashSet<String> repoKeys = CollectionUtils.newLinkedHashSet(REPO_KEY);
        givenAllValidationsPass();

        RepoBulkRemovalReport bulkReport =
                assertDoesNotThrow(() -> removeRepositoryService.removeRepositories(repoKeys));

        assertEquals(SC_FORBIDDEN, bulkReport.getStatusCode());
    }

    @Test
    void removeRepositoriesInternal_basicStatusHolderDownstream_preservesWarningsAndStatuses() {
        BasicStatusHolder downstreamStatusHolder = new BasicStatusHolder();
        downstreamStatusHolder.status("deleted artifact", SC_OK, log);
        downstreamStatusHolder.warn("warning", SC_BAD_REQUEST, log);
        doThrow(new RepositoryContentDeletionFailedException(downstreamStatusHolder))
                .when(deleteRepositoryContentService)
                .deleteRepositoryContent(eq(REPO_KEY), any(RepoRemovalStatusHolder.class));
        LinkedHashSet<String> repoKeys = CollectionUtils.newLinkedHashSet(REPO_KEY);
        givenAllValidationsPass();

        RepoBulkRemovalReport bulkReport =
                assertDoesNotThrow(() -> removeRepositoryService.removeRepositories(repoKeys));

        assertEquals(SC_BAD_REQUEST, bulkReport.getStatusCode());
        RemoveRepositoryFailureReport failureReport =
                (RemoveRepositoryFailureReport) bulkReport.getReports().iterator().next();
        assertEquals(1, failureReport.getDeleteArtifactsFailureCount());
        assertEquals("warning", failureReport.getErrors().getFirst().message());
    }

    @Test
    void removeRepository_shouldDispatchAfterDeleteWith207_whenContentDeletionPartiallyFails() {
        givenAllValidationsPass();
        doAnswer(invocation -> {
            RepoRemovalStatusHolder statusHolder = invocation.getArgument(1, RepoRemovalStatusHolder.class);
            statusHolder.incrementDeletedCount();
            statusHolder.warn("failed to delete artifact", SC_BAD_REQUEST, log);
            throw new RepositoryContentDeletionFailedException(statusHolder);
        }).when(deleteRepositoryContentService)
                .deleteRepositoryContent(eq(REPO_KEY), any(RepoRemovalStatusHolder.class));

        BasicStatusHolder statusHolder = removeRepositoryService.removeRepository(REPO_KEY);

        assertEquals(SC_MULTI_STATUS, statusHolder.getStatusCode());
        verify(repoConfigCudEventEmitter).emitAfter(argThat(change ->
                change.isDelete() && REPO_KEY.equals(change.getRepoKey())), eq(SC_MULTI_STATUS));
        verify(repositoryConfigService, never()).deleteRepoConfig(REPO_KEY);
    }

    private static RemoveRepositoryReport reportFor(Set<RemoveRepositoryReport> reports, String repoKey) {
        return reports.stream()
                .filter(report -> repoKey.equals(report.getRepoKey()))
                .findFirst()
                .orElseThrow();
    }

    private void givenConfigsFound(String repo1, String repo2) {
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(repo1))
                .thenReturn(LocalRepoConfig.create(repo1, Generic));
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(repo2))
                .thenReturn(LocalRepoConfig.create(repo2, Generic));
    }

    private void givenRepoConfigNotFound(String nonExistingRepoKey) {
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(nonExistingRepoKey))
                .thenReturn(null);
    }

    private void givenFederatedRepository() {
        FederatedRepoConfig config = FederatedRepoConfig.create(REPO_KEY, Generic);
        // Set federationOnGrid=true to make isFederated() return true
        config.getBaseConfig().getFederationConfig().setFederationOnGrid(true);
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(REPO_KEY)).thenReturn(config);
    }

    private void givenRemoteRepositoryWithFederation() {
        RemoteRepoConfig config = RemoteRepoConfig.create(REPO_KEY, Generic);
        config.getRepoTypeConfig().setUrl("https://registry.example.com");
        // Set federationOnGrid=true to make isFederated() return true
        config.getBaseConfig().getFederationConfig().setFederationOnGrid(true);
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(REPO_KEY)).thenReturn(config);
    }

    private void givenRemoteRepositoryWithoutFederation() {
        RemoteRepoConfig config = RemoteRepoConfig.create(REPO_KEY, Generic);
        config.getRepoTypeConfig().setUrl("https://registry.example.com");
        // No federation configured - federationOnGrid=false and no members
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(REPO_KEY)).thenReturn(config);
    }

    private void givenLocalRepositoryWithFederation() {
        LocalRepoConfig config = LocalRepoConfig.create(REPO_KEY, Generic);
        // Set federationOnGrid=true to make isFederated() return true
        config.getBaseConfig().getFederationConfig().setFederationOnGrid(true);
        when(repositoryConfigService.getRepoConfigByKeyNoValidationChecks(REPO_KEY)).thenReturn(config);
    }

    private void givenDeleteRepoContentFailure() {
        doThrow(new RepositoryRemovalFailedException(createNegativeStatusHolder()))
                .when(deleteRepositoryContentService)
                .deleteRepositoryContent(anyString(), any(RepoRemovalStatusHolder.class));
    }

    private void givenFederationRemovalRemoteMembersFailure() {
        doThrow(new FederatedRemoteMembersUpdateException(List.of("URL #1", "URL #2"),
                "Federation remote members failure"))
                .when(federatedRepoAddon).removeRepoFromFederationMembers(any(RepoConfig.class));
    }

    private void givenRepoConfigChangeInterceptorFailure(Exception e) {
        doThrow(e).when(repoConfigChangeInterceptor).onBeforeSave(any(RepoConfigChanges.class));
    }

    private void givenValidationFails() {
        doThrow(new RepositoryRemovalFailedException(createNegativeStatusHolder())).when(
                removeRepositoryValidator).validate(any(RepoConfig.class));
    }

    private void givenAllValidationsPass() {
        doAnswer(invocation -> createPositiveStatusHolder())
                .when(removeRepositoryValidator).validate(any(RepoConfig.class));
    }
}