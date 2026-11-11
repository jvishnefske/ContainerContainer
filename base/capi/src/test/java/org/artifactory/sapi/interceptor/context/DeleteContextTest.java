package org.artifactory.sapi.interceptor.context;

import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DeleteContextTest {

    @Test
    void fullRepoContentDeletion_defaultsToFalse() {
        DeleteContext deleteContext = new DeleteContext(mock(RepoPath.class));

        assertFalse(deleteContext.isFullRepoContentDeletion());
    }

    @Test
    void fullRepoContentDeletion_setsFlagAndReturnsSameContext() {
        DeleteContext deleteContext = new DeleteContext(mock(RepoPath.class));

        DeleteContext returnedContext = deleteContext.fullRepoContentDeletion(true);

        assertSame(deleteContext, returnedContext);
        assertTrue(deleteContext.isFullRepoContentDeletion());
    }
}
