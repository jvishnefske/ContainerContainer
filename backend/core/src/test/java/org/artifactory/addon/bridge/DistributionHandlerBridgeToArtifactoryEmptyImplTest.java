package org.artifactory.addon.bridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.testng.Assert;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DistributionHandlerBridgeToArtifactoryEmptyImplTest {

    private DistributionHandlerBridgeToArtifactoryEmptyImpl bridgeToArtifactory;

    @BeforeEach
    public void setUp() {
        bridgeToArtifactory = new DistributionHandlerBridgeToArtifactoryEmptyImpl();
    }

    @Test
    public void testGetArtifactoryDetails() {
        Executable target = () -> bridgeToArtifactory.getArtifactoryDetails();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    public void testGetDatabaseType() {
        Executable target = () -> bridgeToArtifactory.getDatabaseType();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    public void testGetSysConfig() {
        Executable target = () -> bridgeToArtifactory.getSysConfig();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    public void testGetJdbcHelper() {
        Executable target = () -> bridgeToArtifactory.getJdbcHelper();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    public void testGetAccessClient() {
        Executable target = () -> bridgeToArtifactory.getAccessClient();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    public void testGetNextDatabaseId() {
        Executable target = () -> bridgeToArtifactory.getNextDatabaseId();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    public void testDeleteTargetBundle() {
        Executable target = () -> bridgeToArtifactory.deleteTargetBundle("rbName", "rbVersion");
        assertThrows(UnsupportedOperationException.class, target);

        Assert.assertThrows(NullPointerException.class,
                () -> bridgeToArtifactory.deleteTargetBundle(null, ""));
        Assert.assertThrows(NullPointerException.class,
                () -> bridgeToArtifactory.deleteTargetBundle("", null));
    }

    @Test
    public void testIsTargetBundleMarkedAsKeep() {
        Executable target = () -> bridgeToArtifactory.isTargetBundleMarkedAsKeep("rbName", "rbVersion");
        assertThrows(UnsupportedOperationException.class, target);

        Assert.assertThrows(NullPointerException.class,
                () -> bridgeToArtifactory.isTargetBundleMarkedAsKeep(null, ""));
        Assert.assertThrows(NullPointerException.class,
                () -> bridgeToArtifactory.isTargetBundleMarkedAsKeep("", null));
    }

    @Test
    public void testMarkTargetBundleKeep() {
        Executable target = () -> bridgeToArtifactory.markTargetBundleKeep("rbName", "rbVersion", true);
        assertThrows(UnsupportedOperationException.class, target);

        Assert.assertThrows(NullPointerException.class,
                () -> bridgeToArtifactory.markTargetBundleKeep(null, "", true));
        Assert.assertThrows(NullPointerException.class,
                () -> bridgeToArtifactory.markTargetBundleKeep("", null, true));
    }

    @Test
    public void testScheduleRetentionCleanup() {
        Executable target = () -> bridgeToArtifactory.scheduleRetentionCleanup("cron-expression");
        assertThrows(UnsupportedOperationException.class, target);

        Assert.assertThrows(NullPointerException.class,
                () -> bridgeToArtifactory.scheduleRetentionCleanup(null));
    }

    @Test
    public void testCancelScheduledRetentionCleanup() {
        Executable target = () -> bridgeToArtifactory.cancelScheduledRetentionCleanup("taskToken");
        assertThrows(UnsupportedOperationException.class, target);

        Assert.assertThrows(NullPointerException.class,
                () -> bridgeToArtifactory.cancelScheduledRetentionCleanup(null));
    }

    @Test
    public void testPropagateToNeighbours() {
        Executable target = () -> bridgeToArtifactory.propagateToNeighbours("data");
        assertThrows(UnsupportedOperationException.class, target);

        Assert.assertThrows(NullPointerException.class, () -> bridgeToArtifactory.propagateToNeighbours(null));
    }
}