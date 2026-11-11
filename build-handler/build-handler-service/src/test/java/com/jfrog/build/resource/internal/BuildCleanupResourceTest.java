package com.jfrog.build.resource.internal;

import com.jfrog.build.model.BuildDeleteNumberResult;
import com.jfrog.build.model.api.cleanup.BuildNamesResponse;
import com.jfrog.build.model.api.cleanup.BuildNumbersResponse;
import com.jfrog.build.command.cleanup.BuildDeleteForCleanupCommand;
import com.jfrog.build.command.cleanup.BuildGetNamesForCleanupCommand;
import com.jfrog.build.command.cleanup.BuildGetNumbersForCleanupCommand;
import com.jfrog.build.model.api.BuildNumbersModel;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.model.BuildDeleteNumberResult.Status.FAILURE_ARTIFACTS;
import static com.jfrog.build.model.BuildDeleteNumberResult.Status.SUCCESS;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_PARTIAL_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildCleanupResourceTest {

    private BuildCleanupResource buildCleanupResource;

    private BuildDeleteForCleanupCommand deleteForCleanupCommand;
    private BuildGetNamesForCleanupCommand getNamesForCleanupCommand;
    private BuildGetNumbersForCleanupCommand getNumbersForCleanupCommand;

    @BeforeEach
    void setup() {
        deleteForCleanupCommand = mock(BuildDeleteForCleanupCommand.class);
        getNamesForCleanupCommand = mock(BuildGetNamesForCleanupCommand.class);
        getNumbersForCleanupCommand = mock(BuildGetNumbersForCleanupCommand.class);
        buildCleanupResource = new BuildCleanupResource(
                deleteForCleanupCommand, getNamesForCleanupCommand, getNumbersForCleanupCommand);
    }

    @Test
    void getBuildNames_returnsOkAndEntityFromCommand() {
        BuildNamesResponse body = new BuildNamesResponse(List.of());
        when(getNamesForCleanupCommand.getBuildNames(
                eq("some-build-name-prefix"),
                eq("phoenix"),
                eq(3L),
                eq(500L)))
                .thenReturn(body);

        Response response =
                buildCleanupResource.getBuildNames("some-build-name-prefix", "phoenix", 3L, 500L);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
    }

    @Test
    void getBuildNumbers_returnsOkAndEntityFromCommand() {
        BuildNumbersModel model = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .startedBefore(1L)
                .build();
        BuildNumbersResponse body = new BuildNumbersResponse("phoenix-build-info", "some-build-name", List.of(), 0L);
        when(getNumbersForCleanupCommand.getBuildNumbers(same(model))).thenReturn(body);

        Response response = buildCleanupResource.getBuildNumbers(model);

        assertEquals(200, response.getStatus());
        assertEquals(body, response.getEntity());
    }

    @Test
    void deleteBuild_mapsSuccessToOk() {
        BuildDeleteNumberResult result = new BuildDeleteNumberResult(SUCCESS, 0, 0, 0);
        when(deleteForCleanupCommand.deleteBuild(
                eq("some-build-name"),
                eq("some-build-number"),
                eq("1700000000000"),
                eq("phoenix"),
                eq(false),
                eq(true),
                eq(false)))
                .thenReturn(result);

        Response response = buildCleanupResource.deleteBuild(
                "some-build-name",
                "some-build-number",
                "1700000000000",
                "phoenix",
                false,
                true,
                false);

        assertEquals(SC_OK, response.getStatus());
        assertEquals(result, response.getEntity());
    }

    @Test
    void deleteBuild_mapsNonSuccessToPartialContent() {
        BuildDeleteNumberResult result = new BuildDeleteNumberResult(FAILURE_ARTIFACTS, 2, 1, 0);
        when(deleteForCleanupCommand.deleteBuild(
                eq("some-build-name"),
                eq("some-build-number"),
                eq("1700000000000"),
                eq("phoenix"),
                eq(false),
                eq(true),
                eq(false)))
                .thenReturn(result);

        Response response = buildCleanupResource.deleteBuild(
                "some-build-name",
                "some-build-number",
                "1700000000000",
                "phoenix",
                false,
                true,
                false);

        assertEquals(SC_PARTIAL_CONTENT, response.getStatus());
        assertEquals(result, response.getEntity());
        verify(deleteForCleanupCommand).deleteBuild(
                "some-build-name",
                "some-build-number",
                "1700000000000",
                "phoenix",
                false,
                true,
                false);
    }
}
