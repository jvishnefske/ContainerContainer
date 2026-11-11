package org.artifactory.build;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;

import java.util.Date;

import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;

@Value
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BuildRunImpl implements BuildRun { // NOSONAR

    @EqualsAndHashCode.Include
    String name;

    @EqualsAndHashCode.Include
    String number;

    @EqualsAndHashCode.Include
    String started;

    String ciUrl;
    String buildRepo;
    String releaseStatus;
    String releaseRepoKey;

    public BuildRunImpl(
            String name,
            String number,
            Date started) {
        this(new com.jfrog.build.acl.model.BuildRunImpl(
                name,
                number,
                started)
        );
    }

    public BuildRunImpl(
            String name,
            String number,
            String started) {
        this(new com.jfrog.build.acl.model.BuildRunImpl(
                name,
                number,
                started)
        );
    }

    public BuildRunImpl(
            String name,
            String number,
            String started,
            String buildRepo) {
        this(new com.jfrog.build.acl.model.BuildRunImpl(
                name,
                number,
                started,
                buildRepo)
        );
    }

    public BuildRunImpl(
            String name,
            String number,
            String started,
            String ciUrl,
            String releaseStatus,
            String buildRepo) {
        this(new com.jfrog.build.acl.model.BuildRunImpl(
                name,
                number,
                started,
                ciUrl,
                releaseStatus,
                buildRepo)
        );
    }

    public BuildRunImpl(
            long buildId,
            String name,
            String number,
            String started,
            String ciUrl,
            String releaseStatus,
            String releaseRepoKey,
            String buildRepo) {
        this(new com.jfrog.build.acl.model.BuildRunImpl(
                buildId,
                name,
                number,
                started,
                ciUrl,
                releaseStatus,
                releaseRepoKey,
                buildRepo)
        );
    }

    public BuildRunImpl(@NonNull com.jfrog.build.acl.model.BuildRun buildRun) {
        this.buildRepo = buildRun.getBuildRepo();
        this.name = buildRun.getName();
        this.number = buildRun.getNumber();
        this.started = buildRun.getStarted();
        this.ciUrl = buildRun.getCiUrl();
        this.releaseStatus = buildRun.getReleaseStatus();
        this.releaseRepoKey = buildRun.getReleaseRepoKey();
    }

    @NonNull
    @Override
    public Date getStartedDate() {
        return new Date(parseBuildTime(getStarted()));
    }
}