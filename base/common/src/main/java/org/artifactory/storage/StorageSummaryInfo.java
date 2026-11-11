/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage;

import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.jfrog.common.ClockUtils;
import org.jfrog.storage.binstore.BinariesInfo;

import jakarta.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Holds summary about the storage used by the various repositories.
 *
 * @author Yossi Shaul
 */
@NoArgsConstructor
public class StorageSummaryInfo implements Serializable {

    private Map<String, RepoStorageSummaryInfo> repoStorageSummaries;
    private Map<String, Long> projectsTotalSizes;
    private Map<String, Long> projectsTotalFilesCount;
    private BinariesInfo binariesInfo;
    private long totalSize;
    private long totalFolders;
    private long totalFiles;
    private long lastUpdated;

    public StorageSummaryInfo(Map<String, RepoStorageSummaryInfo> repoStorageSummaries, BinariesInfo binariesInfo,
            Map<String, Long> projectsTotalSizes, Map<String, Long> projectsTotalFilesCount) {
        this.projectsTotalSizes = projectsTotalSizes;
        this.projectsTotalFilesCount = projectsTotalFilesCount;
        this.repoStorageSummaries = repoStorageSummaries;
        this.binariesInfo = binariesInfo;
        for (RepoStorageSummaryInfo s : repoStorageSummaries.values()) {
            totalFolders += s.getFoldersCount();
            totalFiles += s.getFilesCount();
            totalSize += s.getUsedSpace();
        }
        this.lastUpdated = ClockUtils.epochMillis();
    }

    public Map<String, Long> getProjectsTotalFilesCount() {
        return projectsTotalFilesCount;
    }

    public Map<String, Long> getProjectsTotalSizes() {
        return projectsTotalSizes;
    }

    public void setProjectsTotalSizes(Map<String, Long> projectSizeMap) {
        this.projectsTotalSizes = projectSizeMap;
    }

    /**
     * Atomically replaces current {@linkplain RepoStorageSummaryInfo} instance with new one.
     * @param mapModifier modifies the newly created map. This map will replace the existing one atomically
     */
    public void recalculateRepoStorageSummary(@NonNull Consumer<Map<String, RepoStorageSummaryInfo>> mapModifier) {
        Map<String, RepoStorageSummaryInfo> shallowCopy = new HashMap<>(repoStorageSummaries);
        mapModifier.accept(shallowCopy);
        repoStorageSummaries = shallowCopy;
    }

    /**
     * Returns the {@linkplain RepoStorageSummaryInfo} instance associated with the specified 'name' is mapped.
     * This method should be preferred over {@linkplain #getRepoStorageSummaries()} when a single mapping
     * needs to be retrieved.
     *
     * @param name the name whose associated {@linkplain RepoStorageSummaryInfo} instance is to be returned
     * @return the {@linkplain RepoStorageSummaryInfo} instance to which the specified name is mapped,
     * or <em>null</em> if this map contains no mapping for the name
     */
    @Nullable
    public RepoStorageSummaryInfo getRepoStorageSummary(String name) {
        return repoStorageSummaries.get(name);
    }

    /**
     * @return a <strong>unmodifiable view</strong> of the {@linkplain #repoStorageSummaries} map
     */
    public Map<String, RepoStorageSummaryInfo> getRepoStorageSummaries() {
        return Collections.unmodifiableMap(repoStorageSummaries);
    }

    /**
     * @return Total size, in bytes, of all the stored files in Artifactory. This is the non-checksum optimized storage
     * but can still be smaller than the binary store until storage GC is executed.
     */
    public long getTotalSize() {
        return totalSize;
    }

    /**
     * @return Number of folders stored in Artifactory (excluding repository root folders)
     */
    public long getTotalFolders() {
        return totalFolders;
    }

    /**
     * @return Number of files stored in Artifactory
     */
    public long getTotalFiles() {
        return totalFiles;
    }

    /**
     * @return Number of files and folder stored in Artifactory
     */
    public long getTotalItems() {
        return totalFolders + totalFiles;
    }

    /**
     * @return Returns the information about the binaries stored in Artifactory
     */
    public BinariesInfo getBinariesInfo() {
        return binariesInfo;
    }

    public double getOptimization() {
        return binariesInfo.getBinariesSize() / (double) totalSize;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }
}
