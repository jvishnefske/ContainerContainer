package org.artifactory.addon.replication;

import org.artifactory.addon.plugin.replication.ReplicationCtx;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Shashank Taliwal
 */
public class ReplicationCtxTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String INSTANCE_URL = "http://jpd/artifactory";
    private static final String REPO_KEY = "repoKey";

    @Test
    void testReplicationCtxInitialization() {
        ReplicationTargetInfo targetInfo = new ReplicationTargetInfo(INSTANCE_URL, REPO_KEY);
        ReplicationCtx ctx = new ReplicationCtx(targetInfo);

        assertNotNull(ctx.getTargetInfo());
        assertFalse(ctx.isSkip());
    }

    @Test
    void testIsSkip() {
        ReplicationTargetInfo targetInfo = new ReplicationTargetInfo(INSTANCE_URL, REPO_KEY);
        ReplicationCtx ctx = new ReplicationCtx(targetInfo);

        assertFalse(ctx.isSkip());
    }

    @Test
    void testSetSkip() {
        ReplicationTargetInfo targetInfo = new ReplicationTargetInfo(INSTANCE_URL, REPO_KEY);
        ReplicationCtx ctx = new ReplicationCtx(targetInfo);

        ctx.setSkip(true);
        assertTrue(ctx.isSkip());

        ctx.setSkip(false);
        assertTrue(ctx.isSkip()); // skip should remain true
    }

    @Test
    void testGetTargetInfo() {
        ReplicationTargetInfo targetInfo = new ReplicationTargetInfo(INSTANCE_URL, REPO_KEY);
        ReplicationCtx ctx = new ReplicationCtx(targetInfo);

        assertEquals(targetInfo, ctx.getTargetInfo());
    }
}
