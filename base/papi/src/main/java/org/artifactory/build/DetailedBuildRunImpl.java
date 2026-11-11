package org.artifactory.build;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.Value;
import org.apache.commons.lang3.SerializationUtils;
import org.jfrog.build.api.Agent;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildAgent;
import org.jfrog.build.api.release.PromotionStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;
import static java.util.Collections.max;
import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Value
public class DetailedBuildRunImpl implements DetailedBuildRun {

    Build build;
    String buildRepo;

    public DetailedBuildRunImpl(@NonNull Build build) {
        this.build = build;
        this.buildRepo = BUILDINFO_REPO_KEY;
    }

    public DetailedBuildRunImpl(@NonNull Build build, @Nullable String buildRepo) {
        this.build = build;
        this.buildRepo = buildRepo;
    }

    @Override
    public String getName() {
        return build.getName();
    }

    @Override
    public String getNumber() {
        return build.getNumber();
    }

    @Override
    public String getStarted() {
        return build.getStarted();
    }

    @Override
    public Date getStartedDate() {
        String startedDate = getStarted();
        return isNull(startedDate) ? null : new Date(parseBuildTime(startedDate));
    }

    @Override
    public String getBuildAgent() {
        BuildAgent buildAgent = build.getBuildAgent();
        return isNull(buildAgent) ? null : buildAgent.toString();
    }

    @Override
    public String getAgent() {
        Agent agent = build.getAgent();
        return isNull(agent) ? null : agent.toString();
    }

    @Override
    public long getDurationMillis() {
        return build.getDurationMillis();
    }

    @Override
    public String getPrincipal() {
        return build.getPrincipal();
    }

    @Override
    public String getArtifactoryPrincipal() {
        return build.getArtifactoryPrincipal();
    }

    @Override
    public String getUrl() {
        return build.getUrl();
    }

    @Override
    public String getParentName() {
        return build.getParentName();
    }

    @Override
    public String getParentNumber() {
        return build.getParentNumber();
    }

    @Override
    @NonNull
    public List<Module> getModules() {
        List<Module> modulesToReturn = new ArrayList<>();
        if (nonNull(build.getModules())) {
            build.getModules().forEach(module -> modulesToReturn.add(new Module(module)));
        }
        return modulesToReturn;
    }

    @Override
    @NonNull
    public DetailedBuildRun copy() {
        return copy(null);
    }

    @Override
    @NonNull
    public DetailedBuildRun copy(String buildNumber) {
        Build copy = SerializationUtils.clone(build);
        copy.setStartedDate(new Date());
        if (isNotBlank(buildNumber)) {
            copy.setNumber(buildNumber);
        }
        return new DetailedBuildRunImpl(copy);
    }

    @Override
    public List<ReleaseStatus> getReleaseStatuses() {
        List<PromotionStatus> promotionStatuses = build.getStatuses();
        if (isNull(promotionStatuses)) {
            promotionStatuses = new ArrayList<>();
            build.setStatuses(promotionStatuses);
        }
        return new ReleaseStatusList(promotionStatuses);
    }

    @Override
    public String getCiUrl() {
        return build.getUrl();
    }

    @Override
    public String getReleaseStatus() {
        return getLatestPromotion(build).map(PromotionStatus::getStatus).orElse(null);
    }

    @NonNull
    @Override
    public String getReleaseRepoKey() {
        return getLatestPromotion(build).map(PromotionStatus::getRepository).orElse(EMPTY);
    }

    @NonNull
    private Optional<PromotionStatus> getLatestPromotion(@NonNull Build build) {
        List<PromotionStatus> statuses = build.getStatuses();
        return isEmpty(statuses) ? empty() : of(max(statuses, comparing(PromotionStatus::getTimestampDate)));
    }
}