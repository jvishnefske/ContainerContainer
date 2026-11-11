package org.artifactory.repo.metadata;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoMetadata;
import org.artifactory.repo.RepoReplicationUtil;
import org.jfrog.access.client.project.EnvironmentEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.common.ResourceType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolves the project / replication / environments / shared-with-projects fields surfaced on the repo CUD event
 * {@code metadata} payload. Wraps {@link ProjectsService} (Access integration) and {@link CentralConfigService}
 * (replication descriptors) so callers stay decoupled from those high-level services.
 *
 * <p>Lookup is best-effort: if the repository has already been removed from descriptors / Access any unresolved
 * field falls back to the {@link RepoMetadata#empty()} default (null project, false flags, empty environments).
 *
 * <p>The resolver lives in OSS so it can be invoked by the change factory at the same point where the
 * {@link org.artifactory.repo.RepoConfigChange} itself is constructed; the resolved snapshot then travels through
 * both BEFORE and AFTER interceptor phases without being re-fetched (which is the whole point: by the time the
 * AFTER_REPO_DELETE event fires, the underlying Access / descriptor state is gone).
 */
@Component
@Slf4j
public class RepoMetadataResolver {

    private final ProjectsService projectsService;
    private final CentralConfigService centralConfigService;

    public RepoMetadataResolver(ProjectsService projectsService, CentralConfigService centralConfigService) {
        this.projectsService = projectsService;
        this.centralConfigService = centralConfigService;
    }

    /**
     * Best-effort resolve. Never throws; logs at debug on lookup errors and returns a partial / empty result.
     */
    public RepoMetadata resolve(@Nullable RepoConfig repoConfig) {
        if (repoConfig == null || repoConfig.getKey() == null) {
            return RepoMetadata.empty();
        }
        String repoKey = repoConfig.getKey();
        String projectKey = resolveProjectKey(repoKey);
        ProjectResourceEntity resource = resolveResourceEntity(repoKey, projectKey);
        boolean hasReplication = resolveHasReplication(repoConfig, repoKey);
        return new RepoMetadata(
                wireProject(projectKey),
                hasSharedProjects(resource),
                hasReplication,
                environments(repoKey, resource));
    }

    private boolean resolveHasReplication(RepoConfig repoConfig, String repoKey) {
        try {
            return RepoReplicationUtil.hasEnabledReplication(repoConfig, centralConfigService.getDescriptor());
        } catch (RuntimeException e) {
            log.warn("Could not resolve replication status for repo '{}': {}", repoKey, e.getMessage());
            return false;
        }
    }

    @Nullable
    private String resolveProjectKey(String repoKey) {
        try {
            return projectsService.getProjectKeyOfRepository(repoKey);
        } catch (RuntimeException e) {
            log.debug("Could not resolve project key for repo '{}': {}", repoKey, e.getMessage());
            return null;
        }
    }

    /**
     * Maps the Access default-project sentinel ({@link ProjectsService#DEFAULT_PROJECT}) to {@code null} so the wire
     * payload omits the field for unassigned repositories, matching the {@code project?} shape in the published
     * worker {@code typesDefinitions.ts}.
     */
    @Nullable
    private static String wireProject(@Nullable String projectKey) {
        if (projectKey == null || ProjectsService.DEFAULT_PROJECT.equals(projectKey)) {
            return null;
        }
        return projectKey;
    }

    @Nullable
    private ProjectResourceEntity resolveResourceEntity(String repoKey, @Nullable String projectKey) {
        if (projectKey == null || ProjectsService.DEFAULT_PROJECT.equals(projectKey)) {
            return null;
        }
        try {
            Map<String, ProjectResourceEntity> entities = projectsService.getProjectResourceEntitiesMap(projectKey,
                    ResourceType.REPO, false);
            return entities == null ? null : entities.get(repoKey);
        } catch (RuntimeException e) {
            log.debug("Could not resolve project resource entity for repo '{}' in project '{}': {}",
                    repoKey, projectKey, e.getMessage());
            return null;
        }
    }

    private static boolean hasSharedProjects(@Nullable ProjectResourceEntity resource) {
        if (resource == null) {
            return false;
        }
        var sharedWith = resource.getSharedWithProjects();
        return sharedWith != null && !sharedWith.isEmpty();
    }

    private List<String> environments(String repoKey, @Nullable ProjectResourceEntity resource) {
        if (resource != null && resource.getEnvironments() != null && !resource.getEnvironments().isEmpty()) {
            return resource.getEnvironments().stream().sorted().toList();
        }
        try {
            List<EnvironmentEntity> defaultEnvs = projectsService.getEnvironmentEntities(repoKey);
            if (defaultEnvs == null || defaultEnvs.isEmpty()) {
                return Collections.emptyList();
            }
            return defaultEnvs.stream().map(EnvironmentEntity::getName).sorted().toList();
        } catch (RuntimeException e) {
            log.debug("Could not resolve environments for repo '{}': {}", repoKey, e.getMessage());
            return Collections.emptyList();
        }
    }
}
