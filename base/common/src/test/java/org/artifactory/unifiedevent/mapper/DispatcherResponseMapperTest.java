package org.artifactory.unifiedevent.mapper;

import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.unifiedevent.model.DispatcherResponse;
import org.artifactory.unifiedevent.model.ResponseContextData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatcherResponseMapperTest {

    @Test
    void toConsolidatedDispatcherResponse_shouldReturnDefault_whenResponsesNull() {
        DispatcherResponse consolidated = DispatcherResponseMapper.toConsolidatedDispatcherResponse(null);

        assertNotNull(consolidated);
        assertTrue(consolidated.shouldProceed());
    }

    @Test
    void toConsolidatedDispatcherResponse_shouldCombineAllFields_andPreserveLastValues() {
        RepoPath firstRepoPath = RepoPathFactory.create("libs-release", "com/example/app/1.0/app-1.0.jar");
        RepoPath secondRepoPath = RepoPathFactory.create("plugins", "org/example/plugin/2.0/plugin-2.0.zip");

        DispatcherResponse first = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(ResponseContextData.builder()
                        .headers(Map.of("h1", "v1", "shared", "first"))
                        .statusCode(400)
                        .message("Bad request")
                        .size(1024L)
                        .modifiedRepoPath(firstRepoPath)
                        .build())
                .build();

        DispatcherResponse second = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(ResponseContextData.builder()
                        .headers(Map.of("h2", "v2", "shared", "second"))
                        .statusCode(409)
                        .message("Conflict")
                        .size(2048L)
                        .modifiedRepoPath(secondRepoPath)
                        .build())
                .build();

        DispatcherResponse third = new DispatcherResponse(true, null, null);

        DispatcherResponse fourth = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(ResponseContextData.builder()
                        .statusCode(409)
                        .build())
                .build();

        DispatcherResponse consolidated =
                DispatcherResponseMapper.toConsolidatedDispatcherResponse(
                        Arrays.asList(first, second, third, fourth, null));

        assertTrue(consolidated.shouldProceed(), "Consolidated response should always proceed");
        assertNotNull(consolidated.data());
        assertEquals(3, consolidated.data().headers().size());
        assertEquals("v1", consolidated.data().headers().get("h1"));
        assertEquals("v2", consolidated.data().headers().get("h2"));
        assertEquals("second", consolidated.data().headers().get("shared"),
                "Later headers should overwrite earlier ones");
        assertEquals(409, consolidated.data().statusCode(), "Last non-null status code should win");
        assertEquals("Conflict", consolidated.data().message(), "Last non-null message should win");
        assertEquals(2048L, consolidated.data().size(), "Last non-null size should win");
        assertEquals(secondRepoPath, consolidated.data().modifiedRepoPath(), "Last non-null repoPath should win");
    }

    @Test
    void toConsolidatedDispatcherResponse_shouldStop_whenAnyResponseStops() {
        DispatcherResponse first = DispatcherResponse.builder()
                .shouldProceed(true)
                .data(ResponseContextData.builder().headers(Map.of("h1", "v1")).build())
                .build();
        DispatcherResponse second = DispatcherResponse.builder()
                .shouldProceed(false)
                .data(ResponseContextData.builder().headers(Map.of("h2", "v2")).build())
                .build();

        DispatcherResponse consolidated =
                DispatcherResponseMapper.toConsolidatedDispatcherResponse(List.of(first, second));

        assertFalse(consolidated.shouldProceed(), "Consolidated response should stop if any individual response stops");
    }
}