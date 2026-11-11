package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildArtifactChecksums;
import com.jfrog.build.acl.model.ProducedBy;
import com.jfrog.build.acl.model.UsedBy;
import com.jfrog.build.model.api.ArtifactBuildsModel;
import com.jfrog.build.service.BuildArtifactService;
import com.jfrog.build.service.BuildReadService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.defaultString;

/**
 * Resolves the builds related to an artifact (by repo path): the builds that depend on it and the builds that
 * produced it. Returns an empty result when the path resolves to a folder or has no checksums.
 */
@Slf4j
@RequiredArgsConstructor
public class BuildGetArtifactBuildsUiCommand {

    private final BuildArtifactService artifactService;
    private final BuildReadService readService;

    @NonNull
    public ArtifactBuildsModel getArtifactBuilds(
            @Nullable String repoKey,
            @Nullable String path) {
        Optional<BuildArtifactChecksums> checksums =
                artifactService.getArtifactChecksums(defaultString(repoKey), defaultString(path));
        if (checksums.isEmpty()) {
            return ArtifactBuildsModel.builder().usedBy(List.of()).producedBy(List.of()).build();
        }
        BuildArtifactChecksums artifactChecksums = checksums.get();
        List<UsedBy> usedBy;
        try (Stream<UsedBy> dependantBuilds =
                     readService.retrieveDependantBuilds(artifactChecksums.getSha1(), artifactChecksums.getMd5())) {
            usedBy = dependantBuilds.toList();
        }
        List<ProducedBy> producedBy;
        try (Stream<ProducedBy> producedBuilds =
                     readService.retrieveProducedBuilds(artifactChecksums.getSha1(), artifactChecksums.getMd5())) {
            producedBy = producedBuilds.toList();
        }
        return ArtifactBuildsModel.builder().usedBy(usedBy).producedBy(producedBy).build();
    }
}
