package org.artifactory.repo.cleanup;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.descriptor.repo.RepoType.Conda;
import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("resource")
class CondaShardsCleanupServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "conda-local";
    private static final String SHARD_PATH = ".jfrog/shards/linux-64/package_shards";

    @Mock
    private TaskService taskService;
    @Mock
    private AqlService aqlService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private ArtifactoryContext context;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() throws SQLException {
        closeable = org.mockito.MockitoAnnotations.openMocks(this);
        AqlLazyResult<AqlItem> emptyResult = createEmptyLazyResult();
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(emptyResult);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.condaShardsCleanupEnabled.getPropertyName(), "true");
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.condaShardsCleanupIntervalSecs.getPropertyName(), "0");
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.condaShardsCleanupBatchSize.getPropertyName(), "100");
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.condaShardsCleanupSafetyWindowMillis.getPropertyName(),
                        String.valueOf(TimeUnit.MINUTES.toMillis(5)));
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void constantValuesShouldUseExpectedCleanupDefaults() {
        Assertions.assertEquals("false", ConstantValues.condaShardsCleanupEnabled.getDefValue());
        Assertions.assertEquals("1800", ConstantValues.condaShardsCleanupIntervalSecs.getDefValue());
        Assertions.assertEquals("100", ConstantValues.condaShardsCleanupBatchSize.getDefValue());
        Assertions.assertEquals("300000", ConstantValues.condaShardsCleanupSafetyWindowMillis.getDefValue());
    }

    @Test
    void onContextCreatedShouldStartTask() {
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.onContextCreated();
        verify(taskService, times(1)).startTask(any(Task.class), anyBoolean());
    }

    @Test
    void onContextCreatedShouldNotStartTaskWhenDisabled() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.condaShardsCleanupEnabled.getPropertyName(), "false");
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.onContextCreated();
        verify(taskService, never()).startTask(any(Task.class), anyBoolean());
    }

    @Test
    void onContextCreatedShouldNotStartTaskWhenAlreadyScheduled() {
        when(taskService.hasTaskOfType(CondaShardsCleanupJob.class)).thenReturn(true);
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.onContextCreated();
        verify(taskService, never()).startTask(any(Task.class), anyBoolean());
    }

    @Test
    void cleanShouldSkipWhenDisabled() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.condaShardsCleanupEnabled.getPropertyName(), "false");
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.clean();
        verify(repositoryService, never()).getLocalAndFederatedRepoConfigsExcludingInfoRepos();
        verify(aqlService, never()).executeQueryLazy(any(AqlApiItem.class));
    }

    @Test
    void doCleanupShouldNotExecuteAqlWhenNoCondaReposExist() {
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(List.of());
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.doCleanup();
        verify(aqlService, times(0)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanupShouldIgnoreNonCondaRepos() {
        RepoConfig dockerConfig = mock(RepoConfig.class);
        when(dockerConfig.getKey()).thenReturn("docker-local");
        when(dockerConfig.getPackageType()).thenReturn(Docker);
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(dockerConfig));
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.doCleanup();
        verify(aqlService, times(0)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanupShouldNotUndeployWhenNoExpiredShardsFound() {
        RepoConfig condaRepo = condaRepo();
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(List.of(condaRepo));
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.doCleanup();
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanupShouldUndeployExpiredMarkedPackageShards() throws SQLException {
        RepoConfig condaRepo = condaRepo();
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(List.of(condaRepo));
        AqlLazyResult<AqlItem> lazyResult = createLazyResultWithFiles(
                SHARD_PATH,
                SHARD_PATH,
                "aaa.msgpack.zst",
                "bbb.msgpack.zst");
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(lazyResult);
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.doCleanup();
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(2)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanupShouldSkipNonPackageShardCandidates() throws SQLException {
        RepoConfig condaRepo = condaRepo();
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(List.of(condaRepo));
        AqlLazyResult<AqlItem> lazyResult = createLazyResultWithFiles(
                ".jfrog/shards/linux-64",
                ".jfrog/shards/linux-64/package_shards",
                "aaa.msgpack.zst",
                "bbb.json");
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(lazyResult);
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.doCleanup();
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanupShouldLimitAqlQueryByBatchSize() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.condaShardsCleanupBatchSize.getPropertyName(), "7");
        RepoConfig condaRepo = condaRepo();
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(List.of(condaRepo));
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.doCleanup();
        ArgumentCaptor<AqlApiItem> queryCaptor = ArgumentCaptor.forClass(AqlApiItem.class);
        verify(aqlService).executeQueryLazy(queryCaptor.capture());
        Assertions.assertTrue(queryCaptor.getValue().toNative(0).contains("limit(7)"));
    }

    @Test
    void doCleanupShouldUseFiveMinuteDeletionSafetyWindow() {
        long beforeCleanupCutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
        long cutoff = runCleanupAndCaptureDeletionTimestampCutoff();
        long afterCleanupCutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
        Assertions.assertTrue(cutoff >= beforeCleanupCutoff && cutoff <= afterCleanupCutoff,
                "Deletion timestamp cutoff should be approximately now minus the five-minute safety window");
    }

    @Test
    void doCleanupShouldUseConfiguredDeletionSafetyWindow() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.condaShardsCleanupSafetyWindowMillis.getPropertyName(),
                        String.valueOf(TimeUnit.MINUTES.toMillis(10)));
        long beforeCleanupCutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
        long cutoff = runCleanupAndCaptureDeletionTimestampCutoff();
        long afterCleanupCutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
        Assertions.assertTrue(cutoff >= beforeCleanupCutoff && cutoff <= afterCleanupCutoff,
                "Deletion timestamp cutoff should use configured safety window");
    }

    @Test
    void doCleanupShouldHandleAqlResultCloseException() throws Exception {
        RepoConfig condaRepo = condaRepo();
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(List.of(condaRepo));
        AqlLazyResult<AqlItem> lazyResult = createEmptyLazyResult();
        doThrow(new Exception("test exception")).when(lazyResult).close();
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(lazyResult);
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        Assertions.assertDoesNotThrow(cleanup::doCleanup);
    }

    @Test
    void doCleanupShouldHandleUndeployException() throws SQLException {
        RepoConfig condaRepo = condaRepo();
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(List.of(condaRepo));
        AqlLazyResult<AqlItem> lazyResult = createLazyResultWithFiles(
                SHARD_PATH,
                SHARD_PATH,
                "aaa.msgpack.zst",
                "bbb.msgpack.zst");
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(lazyResult);
        doThrow(new RuntimeException("test exception")).when(repositoryService)
                .undeploy(any(RepoPath.class), eq(false), eq(true));
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        Assertions.assertDoesNotThrow(cleanup::doCleanup);
        verify(repositoryService, times(1)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    private RepoConfig condaRepo() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        when(repoConfig.getPackageType()).thenReturn(Conda);
        return repoConfig;
    }

    private long runCleanupAndCaptureDeletionTimestampCutoff() {
        RepoConfig condaRepo = condaRepo();
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(List.of(condaRepo));
        CondaShardsCleanupServiceImpl cleanup = new CondaShardsCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        cleanup.doCleanup();
        ArgumentCaptor<AqlApiItem> queryCaptor = ArgumentCaptor.forClass(AqlApiItem.class);
        verify(aqlService).executeQueryLazy(queryCaptor.capture());
        return extractMarkedForDeletionTimestampCutoff(queryCaptor.getValue().toNative(0));
    }

    private static long extractMarkedForDeletionTimestampCutoff(String query) {
        Matcher matcher = Pattern.compile(
                        "\"@conda\\.shard\\.markedForDeletionTimestampMillis\":\\{\"\\$lt\":\"(-?\\d+)\"}")
                .matcher(query);
        Assertions.assertTrue(matcher.find(), "No deletion timestamp $lt filter in query: " + query);
        return Long.parseLong(matcher.group(1));
    }

    @SuppressWarnings("unchecked")
    private AqlLazyResult<AqlItem> createEmptyLazyResult() throws SQLException {
        AqlLazyResult<AqlItem> lazyResult = mock(AqlLazyResult.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        when(lazyResult.getResultSet()).thenReturn(resultSet);
        when(lazyResult.getFields()).thenReturn(aqlResultFields());
        return lazyResult;
    }

    @SuppressWarnings("unchecked")
    private AqlLazyResult<AqlItem> createLazyResultWithFiles(String firstPath, String secondPath,
            String firstName, String secondName) throws SQLException {
        AqlLazyResult<AqlItem> lazyResult = mock(AqlLazyResult.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("itemRepo")).thenReturn(REPO_KEY);
        when(resultSet.getString("itemPath")).thenReturn(firstPath, secondPath);
        when(resultSet.getString("itemName")).thenReturn(firstName, secondName);
        when(lazyResult.getResultSet()).thenReturn(resultSet);
        when(lazyResult.getFields()).thenReturn(aqlResultFields());
        return lazyResult;
    }

    private List<DomainSensitiveField> aqlResultFields() {
        return List.of(
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, List.of(AqlDomainEnum.items)),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemPath, List.of(AqlDomainEnum.items)),
                new DomainSensitiveField(AqlPhysicalFieldEnum.itemName, List.of(AqlDomainEnum.items)));
    }
}
