package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.IssueModel;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Issue;
import org.jfrog.build.api.Issues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetBuildIssuesUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "1";
    private static final String BUILD_STARTED_EPOCH = "1700000000000";
    private static final String BUILD_STARTED_FORMATTED = formatBuildTime(Long.parseLong(BUILD_STARTED_EPOCH));

    private BuildGetBuildIssuesUiCommand command;

    private BuildRepoService repoService;
    private BuildReadService readService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        command = new BuildGetBuildIssuesUiCommand(repoService, readService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildIssues_shouldFormatDateAndReturnMappedIssues() {
        BuildWrapper wrapper = buildWrapper("PROJ-1", "http://jira/PROJ-1", "bug", true);
        when(readService.getBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY))
                .thenReturn(wrapper);

        List<IssueModel> result = command.getBuildIssues(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH,
                BUILDINFO_REPO_KEY);

        verify(readService).getBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY);
        assertEquals(1, result.size());
        IssueModel model = result.getFirst();
        assertEquals("PROJ-1", model.getKey());
        assertEquals("http://jira/PROJ-1", model.getUrl());
        assertEquals("bug", model.getSummary());
        assertTrue(model.isAggregated());
    }

    @Test
    void getBuildIssues_shouldReturnEmptyList_whenNoIssues() {
        BuildWrapper wrapper = wrapperWithIssues(null);
        when(readService.getBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY))
                .thenReturn(wrapper);

        List<IssueModel> result = command.getBuildIssues(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH,
                BUILDINFO_REPO_KEY);

        assertTrue(result.isEmpty());
    }

    @Test
    void getBuildIssues_shouldThrowNotFound_whenBuildMissing() {
        when(readService.getBuild(eq(BUILD_NAME), eq(BUILD_NUMBER), eq(BUILD_STARTED_FORMATTED),
                eq(BUILDINFO_REPO_KEY))).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> command.getBuildIssues(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH, BUILDINFO_REPO_KEY));
    }

    @Test
    void getBuildIssues_shouldPassNullDate_whenBuildStartedIsBlank() {
        BuildWrapper wrapper = wrapperWithIssues(null);
        when(readService.getBuild(eq(BUILD_NAME), eq(BUILD_NUMBER), isNull(), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(wrapper);

        command.getBuildIssues(BUILD_NAME, BUILD_NUMBER, "", BUILDINFO_REPO_KEY);

        verify(readService).getBuild(BUILD_NAME, BUILD_NUMBER, null, BUILDINFO_REPO_KEY);
    }

    private static BuildWrapper buildWrapper(String key, String url, String summary, boolean aggregated) {
        Issue issue = new Issue(key, url, summary);
        issue.setAggregated(aggregated);
        Issues issues = new Issues();
        issues.setAffectedIssues(Set.of(issue));
        return wrapperWithIssues(issues);
    }

    private static BuildWrapper wrapperWithIssues(Issues issues) {
        Build build = new Build();
        build.setIssues(issues);
        return new BuildWrapper(build, BUILDINFO_REPO_KEY);
    }
}
