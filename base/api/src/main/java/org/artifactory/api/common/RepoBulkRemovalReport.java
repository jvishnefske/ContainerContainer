package org.artifactory.api.common;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.apache.http.HttpStatus.SC_MULTI_STATUS;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.api.common.RepoRemovalStatusHolder.RemoveRepositoryReport;

@Getter
public class RepoBulkRemovalReport {

    private static final String SUCCESS_MSG = "All repositories were removed successfully";
    private static final String PARTIAL_FAILURE_MSG = "Some repositories failed to be removed";
    private static final String FAILURE_MSG = "Repositories batch removal failed";
    private final Set<RemoveRepositoryReport> reports;
    @JsonIgnore
    private final int statusCode;
    private final String statusMessage;
    private static final Map<Integer, String> statusCodeToMsg = Map.of(
            SC_OK, SUCCESS_MSG,
            SC_MULTI_STATUS, PARTIAL_FAILURE_MSG
    );

    public RepoBulkRemovalReport(Set<RepoRemovalStatusHolder> statusHolders) {
        this.reports = statusHolders.stream().map(RepoRemovalStatusHolder::getReport).collect(toSet());
        this.statusCode = calcStatusCode(statusHolders);
        this.statusMessage = calcStatusMessage();
    }

    public RepoBulkRemovalReport(RepoRemovalStatusHolder statusHolder) {
        this.reports = null;
        this.statusCode = statusHolder.getStatusCode();
        this.statusMessage = statusHolder.getStatusMsg();
    }

    private String calcStatusMessage() {
        return statusCodeToMsg.getOrDefault(statusCode, FAILURE_MSG);
    }

    private int calcStatusCode(Set<RepoRemovalStatusHolder> statusHolders) {
        if (allOK(statusHolders)) {
            return SC_OK;
        }
        if (allFailed(statusHolders)) {
            // Since we fail-fast, we can get the first (and only) status code
            return statusHolders.iterator().next().getStatusCode();
        }
        return SC_MULTI_STATUS;
    }

    private boolean allFailed(Set<RepoRemovalStatusHolder> statusHolders) {
        return statusHolders.stream().allMatch(RepoRemovalStatusHolder::hasErrorsOrWarnings);
    }

    private boolean allOK(Set<RepoRemovalStatusHolder> statusHolders) {
        return statusHolders.stream().allMatch(RepoRemovalStatusHolder::isSuccess);
    }
}