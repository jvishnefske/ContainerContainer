package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.InternalServerException;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildDeleteCommandTest {

    private BuildDeleteCommand command;

    private BuildRepoService repoService;
    private BuildReadService readService;
    private BuildDeletionService deletionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        deletionService = mock(BuildDeletionService.class);
        command = new BuildDeleteCommand(repoService, readService, deletionService);
    }

    @Test
    void deleteBuilds_whenBlankBuildName_throwsBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThrows(
                BadRequestException.class,
                () -> command.deleteBuilds(request, "  ", null, null, List.of("1"), false, false));
    }

    @Test
    void deleteBuilds_whenNullBuildName_throwsBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThrows(
                BadRequestException.class,
                () -> command.deleteBuilds(request, null, null, null, List.of("1"), false, false));
    }

    @Test
    void deleteBuilds_whenNoBuildNumbersAndNotDeleteAll_throwsBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThrows(
                BadRequestException.class,
                () -> command.deleteBuilds(request, "some-build-name", null, null, List.of(), false, false));
    }

    @Test
    void deleteBuilds_whenNullBuildNumbersAndNotDeleteAll_throwsBadRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertThrows(
                BadRequestException.class,
                () -> command.deleteBuilds(request, "some-build-name", null, null, null, false, false));
    }

    @Test
    void deleteBuilds_whenLegacyBuildClient_decodesBuildName() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(USER_AGENT)).thenReturn("ArtifactoryBuildClient/2.0.10");
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        when(deletionService.newStatusHolder()).thenReturn(status);

        command.deleteBuilds(request, "a%20b", null, null, null, false, true);

        verify(deletionService).deleteAllBuildsByName("a b", "artifactory-build-info", status, false);
        verify(deletionService).logBuildDeleteAccessEvent("a b");
    }

    @Test
    void deleteBuilds_deleteAllBuilds_returnsSuccessMessageAndLogsAccessEvent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        when(deletionService.newStatusHolder()).thenReturn(status);

        String message = command.deleteBuilds(request, "some-build-name", null, null, null, true, true);

        assertEquals(
                "All builds 'some-build-name' under 'artifactory-build-info' have been deleted successfully",
                message);
        verify(deletionService).deleteAllBuildsByName("some-build-name", "artifactory-build-info", status, true);
        verify(deletionService).logBuildDeleteAccessEvent("some-build-name");
    }

    @Test
    void deleteBuilds_deleteAllBuilds_whenStatusHasError_throwsCancelException() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, "phoenix")).thenReturn("phoenix-build-info");
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        when(deletionService.newStatusHolder()).thenReturn(status);
        when(status.isError()).thenReturn(true);
        StatusEntry error = mock(StatusEntry.class);
        when(error.getStatusCode()).thenReturn(500);
        when(status.getErrors()).thenReturn(List.of(error));

        assertThrows(
                CancelException.class,
                () -> command.deleteBuilds(request, "some-build-name", null, "phoenix", null, false, true));
        verify(deletionService, never()).logBuildDeleteAccessEvent("some-build-name");
    }

    @Test
    void deleteBuilds_deleteAllBuilds_whenStatusHasWarning_throwsInternalServerException() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        when(deletionService.newStatusHolder()).thenReturn(status);
        when(status.isError()).thenReturn(false);
        when(status.isWarning()).thenReturn(true);

        InternalServerException thrown = assertThrows(
                InternalServerException.class,
                () -> command.deleteBuilds(request, "some-build-name", null, null, null, false, true));
        assertTrue(thrown.getMessage().contains("Warnings have been produced"));
        verify(deletionService, never()).logBuildDeleteAccessEvent("some-build-name");
    }

    @Test
    void deleteBuilds_deleteByNumbers_whenBuildNameDoesNotExist_throwsNotFound() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(readService.searchBuildsByNameAndNumbersInternal("missing-build", List.of("1"), "artifactory-build-info"))
                .thenReturn(Set.of());
        when(readService.buildNameExists("missing-build", "artifactory-build-info")).thenReturn(false);

        NotFoundException thrown = assertThrows(
                NotFoundException.class,
                () -> command.deleteBuilds(request, "missing-build", null, null, mutableList("1"), false, false));
        assertTrue(thrown.getMessage().contains("Unable to find build 'missing-build'"));
        // The whole build-name history must never be loaded for the by-numbers path (RTDEV-91082).
        verify(readService, never()).searchBuildsByNameInternally(anyString(), anyString());
    }

    @Test
    void deleteBuilds_deleteByNumbers_returnsSuccessMessageAndLogsAccessEvent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        BuildRun buildOne =
                new BuildRunImpl("some-build-name", "1", "2024-01-01T00:00:00.000+0000", "artifactory-build-info");
        when(readService.searchBuildsByNameAndNumbersInternal(
                "some-build-name", List.of("1"), "artifactory-build-info"))
                .thenReturn(orderedSetOf(buildOne));
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        when(deletionService.newStatusHolder()).thenReturn(status);

        String message = command.deleteBuilds(
                request, "some-build-name", null, null, mutableList("1"), true, false);

        assertEquals(
                "The following builds have been deleted successfully: 'some-build-name#1'.\n",
                message);
        verify(deletionService).deleteBuild(same(buildOne), same(status), eq(true));
        verify(deletionService).logBuildDeleteAccessEvent("some-build-name");
        // The requested numbers are resolved in one batched query; the full history is never loaded (RTDEV-91082).
        verify(readService, never()).searchBuildsByNameInternally(anyString(), anyString());
    }

    @Test
    void deleteBuilds_deleteByNumbers_appendsWarningForNumbersThatCouldNotBeDeleted() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        BuildRun buildOne =
                new BuildRunImpl("some-build-name", "1", "2024-01-01T00:00:00.000+0000", "artifactory-build-info");
        when(readService.searchBuildsByNameAndNumbersInternal(
                "some-build-name", List.of("1", "missing"), "artifactory-build-info"))
                .thenReturn(orderedSetOf(buildOne));
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        when(deletionService.newStatusHolder()).thenReturn(status);

        String message = command.deleteBuilds(
                request, "some-build-name", null, null, mutableList("1", "missing"), false, false);

        assertEquals(
                "The following builds have been deleted successfully: 'some-build-name#1'.\n"
                        + "Warning - the following builds could not be removed: 'missing'.\n",
                message);
        verify(deletionService).logBuildDeleteAccessEvent("some-build-name");
    }

    @Test
    void deleteBuilds_deleteByNumbers_whenStatusHasError_throwsCancelException() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        BuildRun buildOne =
                new BuildRunImpl("some-build-name", "1", "2024-01-01T00:00:00.000+0000", "artifactory-build-info");
        when(readService.searchBuildsByNameAndNumbersInternal(
                "some-build-name", List.of("1"), "artifactory-build-info"))
                .thenReturn(orderedSetOf(buildOne));
        MutableStatusHolder status = mock(MutableStatusHolder.class);
        when(deletionService.newStatusHolder()).thenReturn(status);
        when(status.isError()).thenReturn(true);
        StatusEntry error = mock(StatusEntry.class);
        when(error.getStatusCode()).thenReturn(500);
        when(status.getErrors()).thenReturn(List.of(error));

        assertThrows(
                CancelException.class,
                () -> command.deleteBuilds(
                        request, "some-build-name", null, null, mutableList("1"), true, false));
        verify(deletionService, never()).logBuildDeleteAccessEvent("some-build-name");
    }

    @Test
    void deleteBuilds_deleteByNumbers_whenNameExistsButNoneOfTheRequestedNumbersMatch_throwsNotFound() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(repoService.getPreferredBuildRepo(null, null)).thenReturn("artifactory-build-info");
        when(readService.searchBuildsByNameAndNumbersInternal(
                "some-build-name", List.of("999"), "artifactory-build-info"))
                .thenReturn(Set.of());
        when(readService.buildNameExists("some-build-name", "artifactory-build-info")).thenReturn(true);
        when(deletionService.newStatusHolder()).thenReturn(mock(MutableStatusHolder.class));

        NotFoundException thrown = assertThrows(
                NotFoundException.class,
                () -> command.deleteBuilds(
                        request, "some-build-name", null, null, mutableList("999"), false, false));
        assertTrue(thrown.getMessage().contains("Unable to find the given build numbers"));
        // A missing number on an existing name must not fall back to loading the whole history (RTDEV-91082).
        verify(readService, never()).searchBuildsByNameInternally(anyString(), anyString());
    }

    private static Set<BuildRun> orderedSetOf(BuildRun... builds) {
        return new LinkedHashSet<>(List.of(builds));
    }

    private static List<String> mutableList(String... values) {
        return new java.util.ArrayList<>(List.of(values));
    }
}
