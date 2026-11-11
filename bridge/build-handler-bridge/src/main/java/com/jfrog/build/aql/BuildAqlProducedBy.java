package com.jfrog.build.aql;

import com.jfrog.build.acl.model.ProducedBy;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.AqlApiElement;
import org.artifactory.aql.api.domain.sensitive.AqlApiBuild;
import org.artifactory.aql.result.rows.AqlBuild;
import org.artifactory.checksum.ChecksumType;

import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@RequiredArgsConstructor
public class BuildAqlProducedBy {

    private final AqlService aqlService;

    @NonNull
    public Stream<ProducedBy> getProducedBuilds(
            @Nullable String sha1,
            @Nullable String md5) {
        AqlApiElement<AqlApiBuild> queryChecksumFilter = buildChecksumFilterForArtifacts(sha1, md5);
        if (isNull(queryChecksumFilter)) {
            return Stream.empty();
        }
        AqlApiBuild buildDependenciesQuery = AqlApiBuild.create()
                .filter(queryChecksumFilter)
                .distinct(true)
                .include(
                        AqlApiBuild.name(),
                        AqlApiBuild.number(),
                        AqlApiBuild.started(),
                        AqlApiBuild.module().name(),
                        AqlApiBuild.url(),
                        AqlApiBuild.buildPromotions().status(),
                        AqlApiBuild.buildRepo()
                );
        return aqlService.executeQueryLazy(buildDependenciesQuery).asStream(e -> {
            if (nonNull(e)) {
                log.error("Failed to read produced builds from stream {}", e.getMessage());
            }
        }).filter(Objects::nonNull).map(this::buildProducedBy);
    }

    @Nullable
    private AqlApiElement<AqlApiBuild> buildChecksumFilterForArtifacts(
            @Nullable String sha1,
            @Nullable String md5) {
        if (ChecksumType.sha1.isValid(sha1)) {
            return AqlApiBuild.module().artifact().sha1().equal(sha1);
        }
        if (ChecksumType.md5.isValid(md5)) {
            return AqlApiBuild.module().artifact().md5().equal(md5);
        }
        return null;
    }

    @NonNull
    private ProducedBy buildProducedBy(@NonNull AqlBuild aqlBuild) {
        return new ProducedBy(
                aqlBuild.getBuildName(),
                aqlBuild.getBuildNumber(),
                aqlBuild.getBuildStarted().getTime(),
                aqlBuild.getBuildUrl(),
                aqlBuild.getBuildPromotionStatus(),
                aqlBuild.getBuildModuleName(),
                aqlBuild.getBuildRepo()
        );
    }
}
