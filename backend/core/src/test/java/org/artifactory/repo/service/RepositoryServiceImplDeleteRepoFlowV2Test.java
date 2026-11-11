package org.artifactory.repo.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.RepoRemovalStatusHolder;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Mor Merhav
 */
@Slf4j
class RepositoryServiceImplDeleteRepoFlowV2Test extends RepositoryServiceImplTest {

    @Mock
    private RemoveRepositoryService removeRepositoryService;

    @BeforeEach
    void beforeEach() {
        setStringSystemProperty(ConstantValues.removeRepositoryFlowV2Enabled, Boolean.TRUE.toString());
        super.beforeEach();
    }

    @Test
    @Override
    void testDeprecatedRemoveRepositoryFlow() {
        // Not needed on remove repo flow V2
    }

    @Test
    @Override
    void testDeprecatedRemoveRepositoryFlow_DeleteRepoNotAllowed() {
        // Not needed on remove repo flow V2
    }

    @Test
    void removeRepositoryFlowV2() {
        setField(repositoryService, "removeRepositoryService", removeRepositoryService);
        String repoKey = "repoKey";
        repositoryService.removeRepository(repoKey);
        verify(removeRepositoryService).removeRepository(repoKey);
    }

    @ParameterizedTest
    @CsvSource({
            "false,0",
            "true,1",
    })
    @Override
    void undeploySingleItemTransactions(boolean canDelete, int times) {
        RepoRemovalStatusHolder statusHolder = new RepoRemovalStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path", true);
        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        when(storageInterceptors.canDelete(eq(repoPath), any(BasicStatusHolder.class))).thenReturn(canDelete);


        UndeployContext undeployContext = new UndeployContext(statusHolder, false, 0, false);
        boolean result = repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);
        setStringSystemProperty(ConstantValues.undeploySingleItemWithTimeCheckEnabled, Boolean.FALSE.toString());
        assertEquals(canDelete, result);
        verify(fileService, times(times)).loadChildren(any());
        if (canDelete) {
            assertEquals(1, statusHolder.getReport().getDeletedArtifactsCount());
        }
    }
}