package org.artifactory.storage.service;

import jakarta.annotation.Nullable;
import lombok.NonNull;

import java.util.List;

/**
 * Service for accessing cached project binary storage values
 *
 * @author Evgeny Kurtser
 */
public interface ProjectBinarySizeService {

    /**
     * Get cached binary storage for a project
     *
     * @param projectKey the project key
     * @return ProjectBinaryStorageInfo or null if not found
     */
    @Nullable
    ProjectBinarySizeInfo getProjectBinarySizeInfo(@NonNull String projectKey);

    /**
     * Get all cached project binary storage values
     *
     * @return list of ProjectBinaryStorageInfo for all projects
     */
    List<ProjectBinarySizeInfo> getAllProjectsBinaryStorage();

    /**
     * Calculate binary storage for all eligible projects.
     * The results will be stored in the persistent cache.
     *
     * @return number of projects processed
     */
    int calculateAllProjectsSizes();

    /**
     * Cleanup cached entries older than the specified threshold
     *
     * @param since timestamp threshold in milliseconds
     * @return number of entries removed
     */
    int cleanup(long since);

    /**
     * Information about project binary storage.
     */
    record ProjectBinarySizeInfo(@NonNull StorageCacheService.StorageCacheEntry<ProjectSizeEntry> storageCacheEntry) {
        public String project() {
            return storageCacheEntry.key();
        }

        public ProjectSizeEntry projectSizeStatus() {
            return storageCacheEntry.value();
        }

        public long calculationTimestamp() {
            return storageCacheEntry.calculationTimestamp();
        }
    }
}