package org.artifactory.model.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * @author Igor Usenko
 */
class RemoteRepoPathImplTest {

    @Test
    void equalsAndHashCode_sameOriginAndPath() {
        RepoPathImpl repoPath = new RepoPathImpl("local-02", "flow.sh");
        RemoteRepoPathImpl a = new RemoteRepoPathImpl("127.0.0.1", repoPath);
        RemoteRepoPathImpl b = new RemoteRepoPathImpl("127.0.0.1",
                new RepoPathImpl("local-02", "flow.sh"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equals_differentOrigin() {
        RepoPathImpl repoPath = new RepoPathImpl("local-02", "flow.sh");
        RemoteRepoPathImpl a = new RemoteRepoPathImpl("127.0.0.1", repoPath);
        RemoteRepoPathImpl b = new RemoteRepoPathImpl("127.0.0.2", repoPath);
        assertNotEquals(a, b);
    }

    @Test
    void notEqualToPlainRepoPath() {
        RepoPathImpl repoPath = new RepoPathImpl("local-02", "flow.sh");
        RemoteRepoPathImpl remote = new RemoteRepoPathImpl("127.0.0.1", repoPath);
        assertNotEquals(remote, repoPath);
    }
}
