package org.artifactory.unifiedevent;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.HeadersData;
import org.artifactory.unifiedevent.model.UserContextData;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

import static org.artifactory.security.AuthenticationHelper.getAuthentication;


@Component
@Slf4j
public class UnifiedEventContextProvider {

    private static final String PATH_DELIMITER = "/";

    private final SecurityService securityService;
    private final AccessService accessService;
    private final ArtifactoryHome artifactoryHome;
    private final RepositoryConfigService repositoryConfigService;

    public UnifiedEventContextProvider(SecurityService securityService, RepositoryConfigService repositoryConfigService,
            AccessService accessService) {
        this.securityService = securityService;
        this.repositoryConfigService = repositoryConfigService;
        this.accessService = accessService;
        this.artifactoryHome = ContextHelper.get().getArtifactoryHome();
    }

    public String generateEventId() {
        return UUID.randomUUID().toString();
    }

    public String getDataSchema(SupportedUnifiedEvents eventType) {
        return eventType.getId();
    }

    public HeadersData getEventHeaders() {
        return HeadersData.builder().build();
    }

    /**
     * Returns the current timestamp in ISO-8601 format.
     * Example: "2025-04-16T08:56:12.992Z"
     *
     * @return current timestamp as ISO-8601 string
     */
    public String getCurrentTimestamp() {
        return Instant.now().toString();
    }

    public UserContextData getUserContext() {
        Authentication authentication = getAuthentication();
        String actor = securityService.currentUsername();

        if (authentication instanceof AccessTokenAuthentication accessToken) {
            actor = accessToken.getAccessToken().getSubject();
        }

        return UserContextData.builder()
                .actor(actor)
                .build();
    }

    public RepoDetailsType getRepoType(String repoKey) {
        try {
            RepoConfig repoConfig = repositoryConfigService.getRepoConfigByKey(repoKey);
            return repoConfig != null ? repoConfig.getRepoType() : null;
        } catch (IllegalArgumentException e) {
            log.debug("Got exception while getting repoConfig for repo '{}'. Returning empty repoType", repoKey, e);
            return null;
        }
    }

    public String getSubjectForArtifactData(ArtifactData artifactData) {
        return artifactData.repoKey() + PATH_DELIMITER + artifactData.path();
    }

    public String buildSourceHeader() {
        return accessService.getArtifactoryServiceId().getFormattedName() + "//" + artifactoryHome.getServerId();
    }

}

