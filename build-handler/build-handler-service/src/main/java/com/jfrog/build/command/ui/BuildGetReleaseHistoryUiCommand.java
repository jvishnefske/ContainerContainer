package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.PromotionStatusModel;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.build.api.release.PromotionStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetReleaseHistoryUiCommand {

    private final BuildRepoService repoService;
    private final BuildReadService readService;

    @NonNull
    public List<PromotionStatusModel> getReleaseHistory(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        String formattedDate = isNotBlank(buildStarted) ? formatBuildTime(Long.parseLong(buildStarted)) : null;
        BuildWrapper buildWrapper = readService.getBuild(buildName, buildNumber, formattedDate, normalisedRepo);
        if (buildWrapper == null) {
            throw notFoundException("Failed to locate build %s:%s-%s", buildName, buildNumber,
                    defaultString(formattedDate));
        }
        return toPromotionStatusModels(buildWrapper.getBuild().getStatuses());
    }

    @NonNull
    private List<PromotionStatusModel> toPromotionStatusModels(@Nullable List<PromotionStatus> statuses) {
        Comparator<PromotionStatus> byTimestamp = Comparator.comparing(PromotionStatus::getTimestampDate);
        return Optional.ofNullable(statuses).orElseGet(List::of).stream()
                .filter(Objects::nonNull)
                .sorted(byTimestamp.reversed())
                .map(PromotionStatusModel::from)
                .toList();
    }
}
