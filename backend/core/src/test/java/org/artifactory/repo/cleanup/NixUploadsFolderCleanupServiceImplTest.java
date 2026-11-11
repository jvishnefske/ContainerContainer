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
import org.mockito.Mock;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.descriptor.repo.RepoType.Nix;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Natalie Berni
 */
@SuppressWarnings("resource")
class NixUploadsFolderCleanupServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "nix-local";

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
        // Default: return empty result set
        AqlLazyResult<AqlItem> emptyLazyResult = createEmptyLazyResult();
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(emptyLazyResult);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.nixCleanupUploadsTmpFolderJobSecs.getPropertyName(), "0");
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void onContextCreated_shouldStartTask_whenCalled() {
        NixUploadsFolderCleanupServiceImpl nixCleanup = new NixUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);

        nixCleanup.onContextCreated();

        verify(taskService, times(1)).startTask(any(Task.class), anyBoolean());
    }

    @Test
    void clean_shouldExecuteAqlQueryPerRepo_whenNixReposExist() {
        // Arrange - one Nix repo configured
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        when(repoConfig.getPackageType()).thenReturn(Nix);
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(repoConfig));
        NixUploadsFolderCleanupServiceImpl nixCleanup = new NixUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);

        nixCleanup.clean();

        // Assert - AQL query executed once per Nix repo
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
    }

    @Test
    void doCleanup_shouldNotExecuteAqlQuery_whenNoNixReposConfigured() {
        // Arrange - no Nix repos configured (empty list)
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of());
        NixUploadsFolderCleanupServiceImpl nixCleanup = new NixUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);

        nixCleanup.doCleanup();

        // Assert - no AQL query executed when no Nix repos
        verify(aqlService, times(0)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanup_shouldNotExecuteAqlQuery_whenOnlyNonNixReposConfigured() {
        // Arrange - only Docker repo configured (not Nix)
        RepoConfig dockerConfig = mock(RepoConfig.class);
        when(dockerConfig.getKey()).thenReturn("docker-local");
        when(dockerConfig.getPackageType()).thenReturn(Docker);
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(dockerConfig));
        NixUploadsFolderCleanupServiceImpl nixCleanup = new NixUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);

        nixCleanup.doCleanup();

        // Assert - no AQL query executed for non-Nix repos
        verify(aqlService, times(0)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanup_shouldNotUndeploy_whenNoExpiredFilesFound() {
        // Arrange - Nix repo exists but no expired files in _uploads
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        when(repoConfig.getPackageType()).thenReturn(Nix);
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(repoConfig));
        // Default empty result set is already configured in setUp
        NixUploadsFolderCleanupServiceImpl nixCleanup = new NixUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);

        nixCleanup.doCleanup();

        // Assert
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(0)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanup_shouldUndeployOrphanedFiles_whenExpiredFilesFoundInUploadsDir() throws SQLException {
        // Arrange - Nix repo with expired orphaned files in _uploads folder
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        when(repoConfig.getPackageType()).thenReturn(Nix);
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(repoConfig));
        AqlLazyResult<AqlItem> lazyResult = createLazyResultWithFiles(2);
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(lazyResult);
        NixUploadsFolderCleanupServiceImpl nixCleanup = new NixUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);

        nixCleanup.doCleanup();

        // Assert - two orphaned files should be cleaned up
        verify(aqlService, times(1)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(2)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanup_shouldUndeployFromAllNixRepos_whenMultipleNixReposExist() throws SQLException {
        // Arrange - two Nix repos with orphaned files
        String repoKey2 = "nix-local-2";
        RepoConfig repoConfig1 = mock(RepoConfig.class);
        when(repoConfig1.getKey()).thenReturn(REPO_KEY);
        when(repoConfig1.getPackageType()).thenReturn(Nix);
        RepoConfig repoConfig2 = mock(RepoConfig.class);
        when(repoConfig2.getKey()).thenReturn(repoKey2);
        when(repoConfig2.getPackageType()).thenReturn(Nix);
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(repoConfig1, repoConfig2));
        // Each repo has 1 file - return new result for each call
        AqlLazyResult<AqlItem> lazyResult1 = createLazyResultWithFiles(1);
        AqlLazyResult<AqlItem> lazyResult2 = createLazyResultWithFiles(1);
        when(aqlService.executeQueryLazy(any(AqlApiItem.class)))
                .thenReturn(lazyResult1)
                .thenReturn(lazyResult2);
        NixUploadsFolderCleanupServiceImpl nixCleanup = new NixUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);

        // Act
        nixCleanup.doCleanup();

        // Assert - AQL query executed per repo, one file cleaned up per repo
        verify(aqlService, times(2)).executeQueryLazy(any(AqlApiItem.class));
        verify(repositoryService, times(2)).undeploy(any(RepoPath.class), eq(false), eq(true));
    }

    @Test
    void doCleanup_shouldHandleException_whenAqlResultCloseThrows() throws Exception {
        // Arrange - Nix repo configured but AQL close throws exception
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        when(repoConfig.getPackageType()).thenReturn(Nix);
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(repoConfig));
        AqlLazyResult<AqlItem> lazyResult = createEmptyLazyResult();
        doThrow(new Exception("test exception")).when(lazyResult).close();
        when(aqlService.executeQueryLazy(any(AqlApiItem.class))).thenReturn(lazyResult);
        NixUploadsFolderCleanupServiceImpl nixCleanup = new NixUploadsFolderCleanupServiceImpl(
                aqlService, repositoryService, taskService);

        // Act & Assert - should not throw
        Assertions.assertDoesNotThrow(nixCleanup::doCleanup);
    }

    /**
     * Create an empty lazy result (no files).
     */
    @SuppressWarnings("unchecked")
    private AqlLazyResult<AqlItem> createEmptyLazyResult() throws SQLException {
        AqlLazyResult<AqlItem> lazyResult = mock(AqlLazyResult.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(false);
        when(lazyResult.getResultSet()).thenReturn(resultSet);
        when(lazyResult.getFields()).thenReturn(
                List.of(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, List.of(AqlDomainEnum.items)),
                        new DomainSensitiveField(AqlPhysicalFieldEnum.itemPath, List.of(AqlDomainEnum.items)),
                        new DomainSensitiveField(AqlPhysicalFieldEnum.itemName, List.of(AqlDomainEnum.items))));
        return lazyResult;
    }

    /**
     * Create a lazy result with the specified number of files.
     */
    @SuppressWarnings("unchecked")
    private AqlLazyResult<AqlItem> createLazyResultWithFiles(int fileCount) throws SQLException {
        AqlLazyResult<AqlItem> lazyResult = mock(AqlLazyResult.class);
        ResultSet resultSet = mock(ResultSet.class);
        // Return true for fileCount times, then false
        Boolean[] nextResults = new Boolean[fileCount + 1];
        for (int i = 0; i < fileCount; i++) {
            nextResults[i] = true;
        }
        nextResults[fileCount] = false;
        when(resultSet.next()).thenReturn(nextResults[0],
                java.util.Arrays.copyOfRange(nextResults, 1, nextResults.length));
        when(resultSet.getString("itemRepo")).thenReturn(REPO_KEY);
        when(resultSet.getString("itemPath")).thenReturn("binary-cache/_uploads");
        when(resultSet.getString("itemName")).thenReturn("file1.nar.xz", "file2.nar.xz", "file3.nar.xz");
        when(lazyResult.getFields()).thenReturn(
                List.of(new DomainSensitiveField(AqlPhysicalFieldEnum.itemRepo, List.of(AqlDomainEnum.items)),
                        new DomainSensitiveField(AqlPhysicalFieldEnum.itemPath, List.of(AqlDomainEnum.items)),
                        new DomainSensitiveField(AqlPhysicalFieldEnum.itemName, List.of(AqlDomainEnum.items))));
        when(lazyResult.getResultSet()).thenReturn(resultSet);
        return lazyResult;
    }
}
