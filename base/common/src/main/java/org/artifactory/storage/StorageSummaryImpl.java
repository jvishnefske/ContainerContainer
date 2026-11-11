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

import com.jfrog.base.util.NumberFormatter;
import org.artifactory.aol.AolService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.storage.binstore.ifc.model.BinariesDirInfo;
import org.jfrog.storage.common.StorageUnit;

import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents StorageSummary
 *
 * @author Michael Pasternak
 */
@XmlRootElement(name = "storage")
public class StorageSummaryImpl implements StorageSummary {

    private static final String ONLINE_STORAGE_DIRECTORY_DESCRIPTION = "Artifactory Cloud";
    private static final String EMPTY_STORAGE_DIRECTORY_DESCRIPTION = "Filesystem storage is not used";
    private static final String TOTAL_REPO_SUMMARY_KEY = "TOTAL";

    private BinariesSummary binariesSummary;
    private FileStoreSummary fileStoreSummary;
    private List<RepositorySummary> repositoriesSummaryList;

    /**
     * serialization .ctr
     */
    public StorageSummaryImpl() {
    }

    /**
     * Creates {@link StorageSummaryImpl} from {@link StorageSummaryInfo}
     */
    public StorageSummaryImpl(StorageSummaryInfo storageSummaryInfo) {
        // update binaries summary
        updateBinariesSummary(storageSummaryInfo);
        // update file store summary
        updateFileStoreSummary();
        // update repositorySummary
        updateRepositorySummary(storageSummaryInfo);
    }

    /**
     * Creates {@link StorageSummaryImpl} from {@link StorageSummaryInfo}. To be used for a specific project
     */
    public StorageSummaryImpl(List<RepoStorageSummaryInfo> repoStorageSummaryInfos, long projectTotalSize) {
        // update binaries summary
        binariesSummary = new BinariesSummary();
        // update file store summary
        fileStoreSummary = new FileStoreSummary();
        // update repository summary
        updateRepositorySummary(repoStorageSummaryInfos, projectTotalSize);
    }

    /**
     * Update repository summary
     *
     * @param storageSummaryInfo - storage info data
     */
    private void updateRepositorySummary(StorageSummaryInfo storageSummaryInfo) {
        long totalUsedSpace = getTotalUsedSpace(storageSummaryInfo);
        // update repos data
        repositoriesSummaryList = StreamSupportUtils.mapValuesStream(storageSummaryInfo.getRepoStorageSummaries())
                .map(summary -> new RepositorySummary(summary, totalUsedSpace))
                .collect(Collectors.toCollection(ArrayList::new)); // need to ensure the list is modifiable
        // update total data
        updateTotalRepositoryData(storageSummaryInfo, totalUsedSpace);
    }

    /**
     * Update repository summary in scope of a specific project
     */
    private void updateRepositorySummary(List<RepoStorageSummaryInfo> repoStorageSummaryInfos, long projectTotalSize) {
        repositoriesSummaryList = repoStorageSummaryInfos.stream()
                .map(repoStorageSummaryInfo -> new RepositorySummary(repoStorageSummaryInfo, projectTotalSize))
                .collect(Collectors.toCollection(ArrayList::new)); // need to ensure the list is modifiable
        updateTotalRepositoryData(repoStorageSummaryInfos, projectTotalSize);
    }

    /**
     * Update total repository data
     *
     * @param storageSummaryInfo - storage info data
     * @param totalUsedSpace     - total space taken by all repos
     */
    private void updateTotalRepositoryData(StorageSummaryInfo storageSummaryInfo, long totalUsedSpace) {
        RepositorySummary totalRepoSummary = new RepositorySummary();
        totalRepoSummary.setRepoKey(TOTAL_REPO_SUMMARY_KEY);
        totalRepoSummary.setRepoType(RepoStorageSummaryInfo.RepositoryType.NA);

        totalRepoSummary.setFoldersCount(storageSummaryInfo.getTotalFolders());
        totalRepoSummary.setFilesCount(storageSummaryInfo.getTotalFiles());
        totalRepoSummary.setItemsCount(storageSummaryInfo.getTotalItems());

        totalRepoSummary.setUsedSpace(StorageUnit.toReadableString(totalUsedSpace));
        totalRepoSummary.setUsedSpaceInBytes(totalUsedSpace);
        repositoriesSummaryList.add(totalRepoSummary);
    }

    /**
     * Calculates total used space
     */
    private long getTotalUsedSpace(StorageSummaryInfo storageSummaryInfo) {
        long reposTotalSum = storageSummaryInfo.getRepoStorageSummaries().values().stream()
                .mapToLong(RepoStorageSummaryInfo::getUsedSpace).sum();
        return Math.max(reposTotalSum, storageSummaryInfo.getTotalSize());
    }

    /**
     * Update total repository data for specific project request
     *
     * @param projectTotalSize - project total size in bytes
     */
    private void updateTotalRepositoryData(List<RepoStorageSummaryInfo> repoStorageSummaryInfos,
            long projectTotalSize) {
        RepositorySummary totalRepoSummary = new RepositorySummary();
        totalRepoSummary.setRepoKey(TOTAL_REPO_SUMMARY_KEY);
        totalRepoSummary.setRepoType(RepoStorageSummaryInfo.RepositoryType.NA);

        totalRepoSummary.setFoldersCount(
                repoStorageSummaryInfos.stream().mapToLong(RepoStorageSummaryInfo::getFoldersCount).sum());
        totalRepoSummary
                .setFilesCount(repoStorageSummaryInfos.stream().mapToLong(RepoStorageSummaryInfo::getFilesCount).sum());
        totalRepoSummary
                .setItemsCount(repoStorageSummaryInfos.stream().mapToLong(RepoStorageSummaryInfo::getItemsCount).sum());

        totalRepoSummary.setUsedSpace(StorageUnit.toReadableString(projectTotalSize));
        totalRepoSummary.setUsedSpaceInBytes(projectTotalSize);
        repositoriesSummaryList.add(totalRepoSummary);
    }

    /**
     * Update storage summary with file store details.
     */
    private void updateFileStoreSummary() {
        StorageService storageService = ContextHelper.get().beanForType(StorageService.class);
        FileStoreStorageSummary fileStoreSummaryInfo = storageService.getFileStoreStorageSummary();
        fileStoreSummary = new FileStoreSummary();
        fileStoreSummary.setStorageType(fileStoreSummaryInfo.getBinariesStorageType());
        AolService aolService = ContextHelper.get().beanForType(AolService.class);
        if (aolService.isAol() && !aolService.isDashboardUser()) {
            fileStoreSummary.setStorageDirectory(ONLINE_STORAGE_DIRECTORY_DESCRIPTION);
            return; // don't show file storage details for a cloud instance
        }
        List<BinariesDirInfo> binariesFolders = fileStoreSummaryInfo.getBinariesFolders();
        String storageDirLabel = EMPTY_STORAGE_DIRECTORY_DESCRIPTION;
        if (binariesFolders != null && !binariesFolders.isEmpty()) {
            storageDirLabel = binariesFolders.stream().map(BinariesDirInfo::absolutePath)
                    .collect(Collectors.joining(", "));
        }
        fileStoreSummary.setStorageDirectory(storageDirLabel);
        fileStoreSummary.setTotalSpace(StorageUnit.toReadableString(fileStoreSummaryInfo.getTotalSpace()));
        fileStoreSummary.setUsedSpace(
                StorageUnit.toReadableString(fileStoreSummaryInfo.getUsedSpace()) + " (" +
                        NumberFormatter.formatPercentage(fileStoreSummaryInfo.getUsedSpaceFraction()) + ")");
        fileStoreSummary.setFreeSpace(
                StorageUnit.toReadableString(fileStoreSummaryInfo.getFreeSpace()) + " (" +
                        NumberFormatter.formatPercentage(fileStoreSummaryInfo.getFreeSpaceFraction()) + ")");
    }

    /**
     * Update storage summary with binaries repositories
     *
     * @param storageSummaryInfo - storage info
     */
    private void updateBinariesSummary(StorageSummaryInfo storageSummaryInfo) {
        binariesSummary = new BinariesSummary();
        binariesSummary.setBinariesCount(NumberFormatter.formatLong(
                storageSummaryInfo.getBinariesInfo().getBinariesCount()));
        binariesSummary.setBinariesSize(StorageUnit.toReadableString(
                storageSummaryInfo.getBinariesInfo().getBinariesSize()));
        binariesSummary.setOptimization(NumberFormatter.formatPercentage(storageSummaryInfo.getOptimization()));
        binariesSummary.setArtifactsSize(StorageUnit.toReadableString(storageSummaryInfo.getTotalSize()));
        binariesSummary.setItemsCount(NumberFormatter.formatLong((storageSummaryInfo.getTotalItems())));
        binariesSummary.setArtifactsCount(NumberFormatter.formatLong((storageSummaryInfo.getTotalFiles())));
    }

    public BinariesSummary getBinariesSummary() {
        return binariesSummary;
    }

    public void setBinariesSummary(BinariesSummary binariesSummary) {
        this.binariesSummary = binariesSummary;
    }

    public FileStoreSummary getFileStoreSummary() {
        return fileStoreSummary;
    }

    public void setFileStoreSummary(FileStoreSummary fileStoreSummary) {
        this.fileStoreSummary = fileStoreSummary;
    }

    public List<RepositorySummary> getRepositoriesSummaryList() {
        return repositoriesSummaryList;
    }

    public void setRepositoriesSummaryList(List<RepositorySummary> repositoriesSummaryList) {
        this.repositoriesSummaryList = repositoriesSummaryList;
    }
}
