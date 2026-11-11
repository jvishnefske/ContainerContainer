package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.IssueModel;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.build.api.Issues;

import java.util.List;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetBuildIssuesUiCommand {

    private final BuildRepoService repoService;
    private final BuildReadService readService;

    @NonNull
    public List<IssueModel> getBuildIssues(
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
        return populateIssues(buildWrapper.getBuild().getIssues());
    }

    @NonNull
    private List<IssueModel> populateIssues(@Nullable Issues issues) {
        if (issues == null || issues.getAffectedIssues() == null) {
            return List.of();
        }
        return issues.getAffectedIssues().stream().map(IssueModel::from).toList();
    }
}
