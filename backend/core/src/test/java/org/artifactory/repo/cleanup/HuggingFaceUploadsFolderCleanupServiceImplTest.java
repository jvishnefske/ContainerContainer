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
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.Repo;
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
import org.mockito.Captor;
import org.mockito.Mock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.HuggingFaceML;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
// Ignoring try-with-resources warning for the whole class.
@SuppressWarnings("resource")
public class HuggingFaceUploadsFolderCleanupServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    private static final String REPO_KEY = "repoKey";

    @Mock
    private TaskService taskService;
    @Mock
    private AqlService aqlService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private AqlLazyResult<AqlItem> aqlLazyResult;
    @Mock
    private Repo<RepoDescriptor, ?> repo;
    @Mock
    private RepoDescriptor repoDescriptor;
    @Mock
    private ArtifactoryContext context;
    @Captor
    private ArgumentCaptor<RepoPath> repoPathArgumentCaptor;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = org.mockito.MockitoAnnotations.openMocks(this);
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(aqlLazyResult);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.dockerCleanupUploadsTmpFolderJobSecs.getPropertyName(), "0");
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void onContextCreatedAddsTask() {
        HuggingFaceUploadsFolderCleanupServiceImpl hfTempUploadCleanup = new HuggingFaceUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        hfTempUploadCleanup.onContextCreated();
        verify(taskService, times(1)).startTask(any(Task.class), anyBoolean());
    }

    @Test
    void cleanExecutesQuery() {
        HuggingFaceUploadsFolderCleanupServiceImpl hfTempUploadCleanup = new HuggingFaceUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        hfTempUploadCleanup.clean();
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
    }

    @Test
    void cleanupForEmptyResults() {
        HuggingFaceUploadsFolderCleanupServiceImpl hfTempUploadCleanup = new HuggingFaceUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        hfTempUploadCleanup.doCleanup();
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    /**
     * In the following test case we check that cleanup is not executed for NON HF repository despite results were found
     */
    @Test
    void cleanupForExpiredItemsFromUploadDirForNonHFRepo() throws SQLException {
        initLazyResultsReturn();
        HuggingFaceUploadsFolderCleanupServiceImpl hfTempUploadCleanup = new HuggingFaceUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(repo);
        when(repo.getDescriptor()).thenReturn(repoDescriptor);
        when(repoDescriptor.getType()).thenReturn(HuggingFaceML);

        hfTempUploadCleanup.doCleanup();
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        // only one item path of which ends with _uploads should be deleted
        verify(repositoryService, times(0)).undeploy(repoPathArgumentCaptor.capture(), eq(false), eq(true));
    }

    /**
     * In the following test case we check that cleanup is executed for HF repository despite results were found
     */
    @Test
    void cleanupForExpiredItemsFromUploadDirForHFRepo() throws SQLException {
        // make current repo to be returned in list of HF repos
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        when(repoConfig.getPackageType()).thenReturn(HuggingFaceML);
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(repoConfig));

        initLazyResultsReturn();
        HuggingFaceUploadsFolderCleanupServiceImpl hfTempUploadCleanup = new HuggingFaceUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(repo);
        when(repo.getDescriptor()).thenReturn(repoDescriptor);
        when(repoDescriptor.getType()).thenReturn(HuggingFaceML);

        hfTempUploadCleanup.doCleanup();
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        // only one item path of which ends with _uploads should be deleted
        verify(repositoryService, times(2)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    /**
     * Initialize lazy query to return 2 results.
     */
    private void initLazyResultsReturn() throws SQLException {
        final ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("itemRepo")).thenReturn(REPO_KEY);
        when(resultSet.getString("itemPath")).thenReturn("path2/_uploads", "path3/_uploads", "path4");
        when(resultSet.getString("itemName")).thenReturn("name2", "name3", "name4");
        when(aqlLazyResult.getFields()).thenReturn(
                List.of(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, List.of(AqlDomainEnum.items)),
                        new DomainSensitiveField(AqlPhysicalFieldEnum.itemPath, List.of(AqlDomainEnum.items)),
                        new DomainSensitiveField(AqlPhysicalFieldEnum.itemName, List.of(AqlDomainEnum.items))));
        when(aqlLazyResult.getResultSet()).thenReturn(resultSet);
    }

    /**
     * In the following test case we check that cleanup is not executed for non HF repository
     */
    @Test
    void cleanupNotExecuted() {
        final AqlItem itemWithCorrectPath = mock(AqlItem.class);
        when(itemWithCorrectPath.getRepo()).thenReturn(REPO_KEY);
        when(itemWithCorrectPath.getPath()).thenReturn("path2/_uploads");
        when(itemWithCorrectPath.getName()).thenReturn("name2");
        ResultSet resultSet = mock(ResultSet.class);
        when(aqlLazyResult.getResultSet()).thenReturn(resultSet);
        HuggingFaceUploadsFolderCleanupServiceImpl hfTempUploadCleanup = new HuggingFaceUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(repo);
        when(repo.getDescriptor()).thenReturn(repoDescriptor);
        when(repoDescriptor.getType()).thenReturn(Docker);

        hfTempUploadCleanup.doCleanup();
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        // only one item path of which ends with _uploads should be deleted
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void nullRepoProcessed() {
        final AqlItem itemWithCorrectPath = mock(AqlItem.class);
        when(itemWithCorrectPath.getRepo()).thenReturn(REPO_KEY);
        when(itemWithCorrectPath.getPath()).thenReturn("path2/_uploads");
        when(itemWithCorrectPath.getName()).thenReturn("name2");
        ResultSet resultSet = mock(ResultSet.class);
        when(aqlLazyResult.getResultSet()).thenReturn(resultSet);
        HuggingFaceUploadsFolderCleanupServiceImpl hfTempUploadCleanup = new HuggingFaceUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);
        when(repositoryService.repositoryByKey(REPO_KEY)).thenReturn(null);

        hfTempUploadCleanup.doCleanup();
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        // only one item path of which ends with _uploads should be deleted
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanupHandlesException() throws Exception {
        HuggingFaceUploadsFolderCleanupServiceImpl hfTempUploadCleanup = new HuggingFaceUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);

        final AqlLazyResult<AqlItem> lazyResult = mock(AqlLazyResult.class);
        doThrow(new Exception("test exception")).when(lazyResult).close();
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(lazyResult);
        Assertions.assertDoesNotThrow(() -> hfTempUploadCleanup.doCleanup());
    }
}