package org.artifactory.unifiedevent.builder.artifact;

import lombok.experimental.UtilityClass;
import org.artifactory.addon.replication.ReplicationTargetInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.unifiedevent.model.UserContextData;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.artifactory.unifiedevent.model.artifact.ArtifactEventData;

import java.util.List;

@UtilityClass
public class ArtifactEventDataBuilder {

    public static ArtifactEventData buildForArtifactEvent(
            ArtifactData artifactData,
            ArtifactoryRequest request,
            RepoPath actualRepoPath,
            UserContextData userContext) {
        return ArtifactEventData.builder()
                .artifact(artifactData)
                .context(userContext)
                .request(request)
                .actualRepoPath(actualRepoPath)
                .build();
    }

    public static ArtifactEventData buildForStorageEvent(
            ArtifactData artifactData,
            DeleteContext deletionContext,
            UserContextData userContext, RepoPath targetRepoPath) {
        return ArtifactEventData.builder()
                .artifact(artifactData)
                .context(userContext)
                .deletionContext(deletionContext)
                .targetRepoPath(targetRepoPath)
                .build();
    }

    public static ArtifactEventData buildForPropertyEvent(
            ArtifactData artifactData,
            String propertyName,
            List<String> propertyValues,
            UserContextData userContext) {
        return ArtifactEventData.builder()
                .artifact(artifactData)
                .propertyName(propertyName)
                .propertyValues(propertyValues)
                .context(userContext)
                .build();
    }

    public static ArtifactEventData buildForReplicationEvent(ArtifactData artifactData,
            ReplicationTargetInfo targetInfo, UserContextData userContext) {
        return ArtifactEventData.builder()
                .artifact(artifactData)
                .targetInfo(targetInfo)
                .context(userContext)
                .build();
    }
}
