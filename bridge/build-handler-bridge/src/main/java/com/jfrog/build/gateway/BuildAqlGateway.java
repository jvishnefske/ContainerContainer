package com.jfrog.build.gateway;

import com.jfrog.build.acl.model.BuildArtifactAqlRow;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.acl.model.ProducedBy;
import com.jfrog.build.acl.model.UsedBy;
import com.jfrog.build.aql.BuildAqlArtifacts;
import com.jfrog.build.aql.BuildAqlProducedBy;
import com.jfrog.build.aql.BuildAqlUsedBy;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class BuildAqlGateway {

    private final BuildAqlArtifacts buildAqlArtifacts;
    private final BuildAqlProducedBy buildAqlProducedBy;
    private final BuildAqlUsedBy buildAqlUsedBy;

    @NonNull
    public List<BuildArtifactAqlRow> getBuildArtifactsByNameNumberTimestamp(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        return buildAqlArtifacts.getBuildArtifactsByNameNumberTimestamp(
                buildWrapper,
                sourceRepos,
                excludedRepos
        );
    }

    @NonNull
    public List<BuildArtifactAqlRow> getBuildArtifactsByNameNumber(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        return buildAqlArtifacts.getBuildArtifactsByNameNumber(
                buildWrapper,
                sourceRepos,
                excludedRepos
        );
    }

    @NonNull
    public Stream<ProducedBy> getProducedBuilds(
            @Nullable String sha1,
            @Nullable String md5) {
        return buildAqlProducedBy.getProducedBuilds(sha1, md5);
    }

    @NonNull
    public Stream<UsedBy> getDependentBuilds(
            @Nullable String sha1,
            @Nullable String md5) {
        return buildAqlUsedBy.getDependentBuilds(sha1, md5);
    }
}
