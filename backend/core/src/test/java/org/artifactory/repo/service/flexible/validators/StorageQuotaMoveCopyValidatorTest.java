package org.artifactory.repo.service.flexible.validators;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StorageQuotaMoveCopyValidatorTest {

    @Mock
    RepositoryService repositoryService;
    @Mock
    ProjectsService projectsService;
    @Mock
    MoveCopyItemInfo moveCopyItemInfo;
    StorageQuotaMoveCopyValidator storageQuotaMoveCopyValidator;
    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        storageQuotaMoveCopyValidator = spy(new StorageQuotaMoveCopyValidator(repositoryService, projectsService));
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
        verifyNoMoreInteractions(repositoryService, projectsService);
    }

    @Test
    void validateWhereTargetRepoIsTrashcan() {
        RepoPath targetRepoPath = RepoPathFactory.create(TRASH_REPO_KEY, "stam");
        when(moveCopyItemInfo.getTargetRepoPath()).thenReturn(targetRepoPath);
        assertTrue(storageQuotaMoveCopyValidator.validate(moveCopyItemInfo, null, null));
    }

    @Test
    void validateWithUnlimitedStorageQuota() {
        String repoKey = "local";
        RepoPath targetRepoPath = RepoPathFactory.create(repoKey, "stam");
        when(moveCopyItemInfo.getTargetRepoPath()).thenReturn(targetRepoPath);
        when(projectsService.getProjectQuotaSizeByRepository(repoKey)).thenReturn(ProjectsService.UNLIMITED_STORAGE);
        assertTrue(storageQuotaMoveCopyValidator.validate(moveCopyItemInfo, null, null));
        verify(projectsService, times(1)).getProjectQuotaSizeByRepository(repoKey);
    }

    @Test
    void validateWithIdenticalSourceAndTarget() {
        String repoKey = "local";
        RepoPath targetRepoPath = RepoPathFactory.create(repoKey, "target");
        RepoPath sourceRepoPath = RepoPathFactory.create(repoKey, "source");

        when(moveCopyItemInfo.getTargetRepoPath()).thenReturn(targetRepoPath);
        when(moveCopyItemInfo.getSourceRepoPath()).thenReturn(sourceRepoPath);
        when(projectsService.getProjectQuotaSizeByRepository(repoKey)).thenReturn(1000L);
        assertTrue(storageQuotaMoveCopyValidator.validate(moveCopyItemInfo, null, null));
        verify(projectsService, times(1)).getProjectQuotaSizeByRepository(repoKey);
    }
}