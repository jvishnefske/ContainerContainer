package org.artifactory.repo.service;

import lombok.Getter;
import org.artifactory.api.common.RepoBulkRemovalReport;
import org.artifactory.api.common.RepoRemovalStatusHolder;

@Getter
public class RepositoriesRemovalFailedException extends RuntimeException {

    private final transient RepoBulkRemovalReport repoBulkRemovalReport;

    public RepositoriesRemovalFailedException(RepoBulkRemovalReport repoBulkRemovalReport) {
        this.repoBulkRemovalReport = repoBulkRemovalReport;
    }

    public RepositoriesRemovalFailedException(RepoRemovalStatusHolder statusHolder) {
        this.repoBulkRemovalReport = new RepoBulkRemovalReport(statusHolder);
    }
}