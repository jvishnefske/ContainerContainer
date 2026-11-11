package com.jfrog.build.aql;

import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.acl.model.UsedBy;
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

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.artifactory.storage.db.aql.sql.builder.query.sql.type.BasicSqlGenerator.setUseInnerJoin;

@Slf4j
@RequiredArgsConstructor
public class BuildAqlUsedBy {

    private final AqlService aqlService;

    @NonNull
    public Stream<UsedBy> getDependentBuilds(
            @Nullable String sha1,
            @Nullable String md5) {
        AqlApiElement<AqlApiBuild> queryChecksumFilter = buildChecksumFilterForDependencies(sha1, md5);
        if (isNull(queryChecksumFilter)) {
            return Stream.empty();
        }
        AqlApiBuild buildDependenciesQuery = AqlApiBuild.create()
                .filter(queryChecksumFilter)
                .distinct(true)
                .include(
                        AqlApiBuild.name(),
                        AqlApiBuild.module().dependecy().scope(),
                        AqlApiBuild.number(),
                        AqlApiBuild.started(),
                        AqlApiBuild.module().name(),
                        AqlApiBuild.url(),
                        AqlApiBuild.buildRepo()
                );
        try {
            setUseInnerJoin(true);
            return aqlService.executeQueryLazy(buildDependenciesQuery).asStream(e -> {
                if (nonNull(e)) {
                    log.error("Failed to read dependent builds item from stream {}", e.getMessage());
                }
            }).filter(Objects::nonNull).map(this::buildUsedBy);
        } finally {
            setUseInnerJoin(false);
        }
    }

    @Nullable
    private AqlApiElement<AqlApiBuild> buildChecksumFilterForDependencies(
            @Nullable String sha1,
            @Nullable String md5) {
        if (ChecksumType.sha1.isValid(sha1)) {
            return AqlApiBuild.module().dependecy().sha1().equal(sha1);
        }
        if (ChecksumType.md5.isValid(md5)) {
            return AqlApiBuild.module().dependecy().md5().equal(md5);
        }
        return null;
    }

    @NonNull
    private UsedBy buildUsedBy(@NonNull AqlBuild aqlBuild) {
        String started = formatBuildTime((aqlBuild.getBuildStarted().getTime()));
        return new UsedBy(
                new BuildRunImpl(
                        aqlBuild.getBuildName(),
                        aqlBuild.getBuildNumber(),
                        started,
                        aqlBuild.getBuildUrl(),
                        aqlBuild.getBuildPromotionStatus(),
                        aqlBuild.getBuildRepo()
                ),
                aqlBuild.getBuildModuleName(),
                aqlBuild.getBuildDependencyScope()
        );
    }
}
