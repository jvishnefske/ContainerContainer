package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jfrog.access.client.project.ProjectEntity;

import java.util.Map;

import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.toMap;
import static org.artifactory.exception.factory.ExceptionFactory.forbiddenException;

@RequiredArgsConstructor
public class BuildProjectService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    @NonNull
    public Map<String, ProjectEntity> getProjectsByKeys() {
        return bridgeToArtifactory.getAllProjects().stream()
                .collect(toMap(ProjectEntity::getProjectKey, identity()));
    }

    public void verifyProjectAdmin(@NonNull String projectKey) {
        if (!bridgeToArtifactory.isProjectAdmin(projectKey)) {
            throw forbiddenException("User is not a project admin for project: %s", projectKey);
        }
    }

    @NonNull
    public String extractProjectKeyFromReleaseBundleRepoKey(@NonNull String bundleRepoKey) {
        return bridgeToArtifactory.extractProjectKeyFromReleaseBundleRepoKey(bundleRepoKey);
    }
}
