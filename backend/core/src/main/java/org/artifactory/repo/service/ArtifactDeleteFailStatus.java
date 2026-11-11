package org.artifactory.repo.service;

import jakarta.annotation.Nonnull;
import lombok.Getter;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

public enum ArtifactDeleteFailStatus {
    REQUIRES_ADMIN_PERMISSION(SC_FORBIDDEN,
            "No permissions to delete content of repository. This requires admin privileges. User: '%s', path: '%s'"),
    REQUIRES_DELETE_PERMISSION(SC_FORBIDDEN,
            "Not enough permissions to delete/overwrite all artifacts under '%s' (user: '%s' needs DELETE" +
                    " permission)."),
    REPOSITORY_NOT_FOUND(SC_NOT_FOUND, "Could not find storing repository by key: '%s'"),
    ARTIFACT_NOT_FOUND(SC_NOT_FOUND, "Could not locate artifact. Path: '%s'"),
    ITEM_NOT_FOUND(SC_NOT_FOUND, "Item %s does not exist"),
    CANCELED_BY_PLUGIN(-1, "Undeploy was canceled by user plugin");

    private static final String ARTIFACT_DELETION_ERROR_PREFIX = "Artifact deletion error: ";

    @Getter
    private final int statusCode;
    private final String reason;

    ArtifactDeleteFailStatus(int statusCode, String reason) {
        this.statusCode = statusCode;
        this.reason = reason;
    }

    public String getReason(Object ... params) {
        return getReason().formatted(params);
    }

    @Nonnull
    private String getReason() {
        return ARTIFACT_DELETION_ERROR_PREFIX + reason;
    }
}