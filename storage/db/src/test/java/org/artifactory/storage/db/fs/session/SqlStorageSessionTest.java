package org.artifactory.storage.db.fs.session;

import org.artifactory.storage.tx.SessionResource;
import org.artifactory.storage.tx.SessionResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SqlStorageSessionTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void afterCompletion_setsCommitState(boolean success) {
        SqlStorageSession session = new SqlStorageSession();
        SessionResourceManager resourceManager = mock(SessionResourceManager.class);
        setResourceManager(session, resourceManager);

        session.afterCompletion(success);
        session.afterConnectionReleased();

        verify(resourceManager).afterConnectionReleased(success);
    }

    @Test
    void getOrCreateResource_delegatesToResourceManager() {
        SqlStorageSession session = new SqlStorageSession();
        SessionResource resource = session.getOrCreateResource(TestSessionResource.class);
        SessionResource sameResource = session.getOrCreateResource(TestSessionResource.class);
        assert resource == sameResource;
    }

    private void setResourceManager(SqlStorageSession session, SessionResourceManager resourceManager) {
        try {
            var field = SqlStorageSession.class.getDeclaredField("sessionResourceManager");
            field.setAccessible(true);
            field.set(session, resourceManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class TestSessionResource implements SessionResource {
        @Override
        public void beforeCommit() {
        }
    }
}
