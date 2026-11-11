package org.artifactory.unifiedevent.model.artifact;

import lombok.Builder;
import org.artifactory.repo.RepoDetailsType;

import java.util.Collection;
import java.util.Map;

@Builder
public record ArtifactData(
        String name,
        String repoKey,
        RepoDetailsType repoType,
        String path,
        Long created,
        Long lastModified,
        String deployedBy,
        String md5,
        String sha1,
        String sha256,
        Map<String, Collection<String>> properties
) {
}