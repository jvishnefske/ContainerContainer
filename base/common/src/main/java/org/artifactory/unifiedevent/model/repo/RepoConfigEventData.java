package org.artifactory.unifiedevent.model.repo;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import lombok.Builder;

import java.util.List;

/**
 * Canonical repo CUD (create/update/delete) event payload carried by the unified
 * {@link org.artifactory.unifiedevent.model.EventEnvelope} and serialized to the JFBus/UEM wire. This is the OSS
 * event-seam type accepted by {@link org.artifactory.unifiedevent.UnifiedEventService#sendRepoConfigEvent}; it is the
 * sibling of {@link org.artifactory.unifiedevent.model.artifact.ArtifactEventData} for the repo-config domain.
 * The same type is injected into {@code WorkerClientExecutionRequest.eventData} for repo CUD Worker handlers.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepoConfigEventData(
        RepositoryEventContext context,
        RepositoryMetadata metadata,
        UserContext userContext,
        @Nullable Object config,
        @Nullable List<ChangeAttribute> changeAttributes,
        @Nullable Integer statusCode) {

    public String repoKey() {
        return metadata == null ? null : metadata.repositoryKey();
    }

    public String repoType() {
        return metadata == null ? null : metadata.repositoryType();
    }

    public String triggerSource() {
        return userContext == null ? null : userContext.triggerSource();
    }

    public record RepositoryEventContext(String actor) {
    }

    public record RepositoryMetadata(
            String repositoryKey,
            String repositoryType,
            String packageType,
            Boolean isFederated,
            @Nullable String project,
            Boolean hasSharedProjects,
            Boolean hasReplication,
            List<String> environments) {
    }

    /**
     * Actor/user context for repo CUD events: identity, realm, token flag, and the resolved trigger source. Distinct
     * from the thin {@link org.artifactory.unifiedevent.model.UserContextData} (actor-only) used by artifact events.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UserContext(String id, boolean isToken, String realm, @Nullable String triggerSource) {
    }

    /**
     * A single field-level repository configuration change for Update events; sensitive values are masked before
     * reaching this payload.
     */
    public record ChangeAttribute(String attributeName, @Nullable Object oldValue, @Nullable Object newValue) {
    }
}
