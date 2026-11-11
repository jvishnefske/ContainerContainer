package org.artifactory.addon.watch;

import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Amir Avidan
 */
class ArtifactWatchAddonTest {

    private final ArtifactWatchAddon defaultAddon = () -> true;

    @Test
    void removeWatcherAsManager_defaultIsNoOp() {
        assertDoesNotThrow(() -> defaultAddon.removeWatcherAsManager(Map.of()));
    }

    @Test
    void removeWatcher_defaultIsNoOp() {
        assertDoesNotThrow(() -> defaultAddon.removeWatcher((RepoPath) null, "user"));
    }

    @Test
    void addWatcher_defaultIsNoOp() {
        assertDoesNotThrow(() -> defaultAddon.addWatcher((RepoPath) null, "user"));
    }

    @Test
    void getAllWatchers_defaultReturnsNull() {
        assertNull(defaultAddon.getAllWatchers(null));
    }

    @Test
    void getNearestWatchDefinition_defaultReturnsNull() {
        assertNull(defaultAddon.getNearestWatchDefinition(null, "user"));
    }

    @Test
    void getWatchers_defaultReturnsNull() {
        assertNull(defaultAddon.getWatchers(null));
    }

    @Test
    void getWatchStatus_defaultReturnsNotAllowed() {
        assertEquals(WatchStatus.NOT_ALLOWED, defaultAddon.getWatchStatus(null));
    }
}
