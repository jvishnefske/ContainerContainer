package org.artifactory.ui.rest.model.storage;

import lombok.Getter;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.storage.BinariesSummary;
import org.artifactory.storage.FileStoreSummary;
import org.artifactory.storage.RepositorySummary;
import org.artifactory.storage.StorageSummary;
import org.artifactory.storage.StorageSummaryImpl;
import org.artifactory.storage.StorageSummaryInfo;

import java.util.List;

/**
 * @author Inbar Tal
 */
public class StorageSummaryUIModel extends BaseModel implements StorageSummary {

    private StorageSummaryImpl storageSummary;

    @Getter
    private long lastUpdatedCache;

    /**
     * serialization .ctr
     */
    public StorageSummaryUIModel() {
        storageSummary = new StorageSummaryImpl();
    }

    public StorageSummaryUIModel(StorageSummaryInfo storageSummaryInfo) {
        this.storageSummary = new StorageSummaryImpl(storageSummaryInfo);
        this.lastUpdatedCache = storageSummaryInfo.getLastUpdated();
    }

    public StorageSummaryUIModel(StorageSummaryInfo storageSummaryInfo, List<RepoStorageSummaryInfo> filteredList,
            long projectCurrentSize) {
        this.storageSummary = new StorageSummaryImpl(filteredList, projectCurrentSize);
        this.lastUpdatedCache = storageSummaryInfo.getLastUpdated();
    }

    @Override
    public BinariesSummary getBinariesSummary() {
        return storageSummary.getBinariesSummary();
    }

    @Override
    public void setBinariesSummary(BinariesSummary binariesSummary) {
        storageSummary.setBinariesSummary(binariesSummary);
    }

    @Override
    public FileStoreSummary getFileStoreSummary() {
        return storageSummary.getFileStoreSummary();
    }

    @Override
    public void setFileStoreSummary(FileStoreSummary fileStoreSummary) {
        storageSummary.setFileStoreSummary(fileStoreSummary);
    }

    @Override
    public List<RepositorySummary> getRepositoriesSummaryList() {
        return storageSummary.getRepositoriesSummaryList();
    }

    @Override
    public void setRepositoriesSummaryList(List<RepositorySummary> repositoriesSummaryList) {
        storageSummary.setRepositoriesSummaryList(repositoriesSummaryList);
    }
}
