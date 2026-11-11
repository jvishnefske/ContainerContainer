package org.artifactory.api.common;

import jakarta.annotation.Nonnull;
import lombok.Getter;

import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_MULTI_STATUS;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;

public enum RepoRemovalStatus {

    REPO_REMOVED(SC_OK, "Repository '%s' was removed successfully"),
    INVALID_REPO_KEY(SC_BAD_REQUEST, "repository key is blank, trash or support bundle"),
    REPO_NOT_FOUND(SC_NOT_FOUND, "'%s', repository config does not exist"),
    REPO_IMPORT_IN_PROGRESS(SC_CONFLICT, "'%s', import is still in progress"),
    REPO_DELETION_IN_PROGRESS(SC_ACCEPTED, "'%s', repository deletion is already in progress"),
    CONTENT_PARTIALLY_DELETED(SC_MULTI_STATUS, "Aborting deletion of repository: '%s' because some " +
            "artifacts could not be deleted. Delete " +
            "all artifacts from the repository and try again."),
    REPO_DELETE_FAILED(SC_FORBIDDEN, "'%s'. See error report for more info.")
    ;
    public static final String CANNOT_DELETE_REPO_MSG_PREFIX = "Cannot delete repository: ";

    @Getter
    private final int statusCode;
    private final String reason;

    RepoRemovalStatus(int statusCode, String reason) {
        this.statusCode = statusCode;
        this.reason = reason;
    }

    public String getReason(Object... params) {
        return getReason().formatted(params);
    }

    public static String getSuccessMsg(Object... params) {
        return REPO_REMOVED.reason.formatted(params);
    }

    @Nonnull
    private String getReason() {
        return CANNOT_DELETE_REPO_MSG_PREFIX + reason;
    }
}