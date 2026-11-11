package org.artifactory.unifiedevent.model.artifact;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import org.artifactory.addon.replication.ReplicationTargetInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.unifiedevent.model.UserContextData;

import java.util.List;

@Builder(toBuilder = true)
public record ArtifactEventData(
        ArtifactData artifact,
        UserContextData context,
        // Below fields are kept for backward compatibility and will be removed in future
        @JsonIgnore ArtifactoryRequest request,
        @JsonIgnore RepoPath actualRepoPath,
        @JsonIgnore RepoPath targetRepoPath,
        @JsonIgnore DeleteContext deletionContext,
        @JsonIgnore String propertyName,
        @JsonIgnore List<String> propertyValues,
        @JsonIgnore ReplicationTargetInfo targetInfo
) {
}
