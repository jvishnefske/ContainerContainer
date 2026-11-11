package org.artifactory.unifiedevent.model;

import lombok.Builder;
import org.artifactory.repo.RepoPath;

import java.util.Map;

@Builder
public record ResponseContextData(
        Map<String, String> headers,
        Integer statusCode,
        String message,
        Long size,
        RepoPath modifiedRepoPath,
        Boolean isExpired
) {
}
