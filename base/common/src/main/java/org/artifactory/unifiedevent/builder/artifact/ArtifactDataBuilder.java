package org.artifactory.unifiedevent.builder.artifact;

import lombok.experimental.UtilityClass;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.unifiedevent.UnifiedEventContextProvider;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@UtilityClass
public class ArtifactDataBuilder {

    private static final String PATH_DELIMITER = "/";

    public static ArtifactData buildDataForArtifactAction(
            ArtifactoryRequest request,
            UnifiedEventContextProvider contextProvider) {

        String repoKey = request.getRepoKey();
        return ArtifactData.builder()
                .name(request.getName())
                .repoKey(repoKey)
                .path(request.getPath())
                .repoType(contextProvider.getRepoType(repoKey))
                .lastModified(request.getLastModified())
                .properties(getPropertiesFromRequest(request))
                .build();
    }

    public static ArtifactData buildDataFormItemInfo(
            VfsItem<? extends ItemInfo> item,
            UnifiedEventContextProvider contextProvider) {

        String repoKey = item.getRepoKey();
        return ArtifactData.builder()
                .name(item.getName())
                .repoKey(repoKey)
                .path(buildItemPath(item.getRepoPath()))
                .repoType(contextProvider.getRepoType(repoKey))
                .created(item.getCreated())
                .lastModified(item.getInfo().getLastModified())
                .deployedBy(item.getInfo().getCreatedBy())
                .properties(item.getProperties().asMap())
                .build();
    }

    private static String buildItemPath(RepoPath repoPath) {
        return repoPath.getPath() + (repoPath.isFolder() ? PATH_DELIMITER : "");
    }

    private static Map<String, Collection<String>> getPropertiesFromRequest(ArtifactoryRequest request) {
        if (request.getProperties() != null) {
            return request.getProperties().asMap();
        }
        return Collections.emptyMap();
    }

    public static ArtifactData buildDataForReplicationInfo(RepoPath repoPath,
            UnifiedEventContextProvider contextProvider) {
        String repoKey = repoPath.getRepoKey();
        return ArtifactData.builder()
                .path(buildItemPath(repoPath))
                .repoKey(repoKey)
                .repoType(contextProvider.getRepoType(repoKey))
                .build();
    }
}
