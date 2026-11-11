package org.artifactory.storage.db.monitor;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.home.ArtifactoryHome;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Naming convention and lifecycle (create / delete) for the per-node dirty-shutdown
 * marker file.
 *
 * <p>The marker is created on each startup (when the exceeding-rows remedy is enabled)
 * and deleted on graceful shutdown. If the marker is present when Artifactory starts,
 * the previous run ended unexpectedly — an OOM kill, {@code kill -9}, or any termination
 * that bypassed {@code @PreDestroy} hooks — as opposed to an orderly redeploy or upgrade.
 *
 * <p>{@link ExceedingRowsViolationService#onStartup()} reads the marker to decide
 * whether to install an exceeding-rows recovery blacklist; {@link ExceedingRowsViolationService#shutdown()}
 * deletes it to record a clean shutdown.
 *
 * <p>The {@code /storage} directory is a mounted volume in cloud deployments, so the
 * marker survives a container restart.
 *
 * @author Eli Fishman
 */
@Slf4j
final class DirtyShutdownMarker {

    private static final String DIR = "/storage";
    private static final String PREFIX = "dirty.";
    private static final String SUFFIX = ".flag";

    private DirtyShutdownMarker() {
    }

    /**
     * Returns the per-node dirty-shutdown marker path
     * ({@code /storage/dirty.<haAwareHostId>.flag}), or {@code null} if
     * {@link ArtifactoryHome} is not bound or the host ID cannot be resolved
     * (logged at DEBUG).
     */
    static File getMarkerForCurrentNode() {
        try {
            String nodeId = ArtifactoryHome.get().getHaAwareHostId();
            return new File(DIR, PREFIX + nodeId + SUFFIX);
        } catch (Exception e) {
            log.warn("Cannot resolve dirty-shutdown marker path", e);
            return null;
        }
    }

    /**
     * Creates {@code markerFile} atomically if it does not already exist.
     *
     * @return {@code true} if a new file was created; {@code false} if
     *         {@code markerFile} is {@code null}, the file already existed,
     *         or an I/O error occurred (logged at WARN with the absolute path).
     */
    static boolean createIfAbsent(File markerFile) {
        if (markerFile == null) {
            return false;
        }
        try {
            return markerFile.createNewFile();
        } catch (IOException e) {
            log.warn("Cannot create dirty-shutdown marker: {}", markerFile.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Deletes {@code markerFile} if it exists. I/O errors are logged at WARN.
     * A {@code null} path is silently ignored.
     */
    static void deleteIfExists(File markerFile) {
        if (markerFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(markerFile.toPath());
        } catch (IOException e) {
            log.warn("Cannot delete dirty-shutdown marker: {}", markerFile.getAbsolutePath(), e);
        }
    }
}
