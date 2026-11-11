/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2025 JFrog Ltd.
 * Licensed under the GNU AGPL v3 or later — see project root for full notice.
 *
 */

package org.artifactory.rest.resource.devopsagent.requestlog.dto;

import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLogModelMapperTest {

    // -------------------------------------------------------------------------
    // toModel (inbound)
    // -------------------------------------------------------------------------

    @Test
    void toModel_copiesAllFields() {
        RequestLogRow row = RequestLogRow.builder()
                .id(1L)
                .requestTime(2L)
                .traceId("t")
                .username("u")
                .httpMethod("GET")
                .requestPath("/p")
                .statusCode(200)
                .durationMs(5L)
                .build();

        RequestLogEntryModel model = RequestLogModelMapper.toModel(row);

        assertEquals(1L, model.id());
        assertEquals(2L, model.requestTime());
        assertEquals("t", model.traceId());
        assertEquals("u", model.username());
        assertEquals("GET", model.httpMethod());
        assertEquals("/p", model.requestPath());
        assertEquals(200, model.statusCode());
        assertEquals(5L, model.durationMs());
    }

    // -------------------------------------------------------------------------
    // toInboundModels
    // -------------------------------------------------------------------------

    @Test
    void toInboundModels_emptyList() {
        List<RequestLogEntryModel> result =
                RequestLogModelMapper.toInboundModels(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void toInboundModels_multipleRows() {
        RequestLogRow row1 = RequestLogRow.builder()
                .id(10L).requestTime(100L).traceId("t1").username("alice")
                .httpMethod("GET").requestPath("/api/one").statusCode(404).durationMs(10L)
                .build();
        RequestLogRow row2 = RequestLogRow.builder()
                .id(20L).requestTime(200L).traceId("t2").username("bob")
                .httpMethod("POST").requestPath("/api/two").statusCode(500).durationMs(20L)
                .build();
        RequestLogRow row3 = RequestLogRow.builder()
                .id(30L).requestTime(300L).traceId("t3").username("carol")
                .httpMethod("DELETE").requestPath("/api/three").statusCode(403).durationMs(30L)
                .build();

        List<RequestLogEntryModel> result =
                RequestLogModelMapper.toInboundModels(List.of(row1, row2, row3));

        assertEquals(3, result.size());
        assertEquals(10L, result.get(0).id());
        assertEquals("alice", result.get(0).username());
        assertEquals(20L, result.get(1).id());
        assertEquals("bob", result.get(1).username());
        assertEquals(30L, result.get(2).id());
        assertEquals("carol", result.get(2).username());
    }

    // -------------------------------------------------------------------------
    // toOutboundModel
    // -------------------------------------------------------------------------

    @Test
    void toOutboundModel_copiesAllFields() {
        OutboundRequestLogRow row = OutboundRequestLogRow.builder()
                .id(2L)
                .requestTime(3L)
                .traceId("t2")
                .repoKey("npm-remote")
                .httpMethod("POST")
                .requestUrl("https://example.com")
                .statusCode(404)
                .durationMs(10L)
                .build();

        OutboundRequestLogEntryModel model = RequestLogModelMapper.toOutboundModel(row);

        assertEquals(2L, model.id());
        assertEquals(3L, model.requestTime());
        assertEquals("t2", model.traceId());
        assertEquals("npm-remote", model.repoKey());
        assertEquals("POST", model.httpMethod());
        assertEquals("https://example.com", model.requestUrl());
        assertEquals(404, model.statusCode());
        assertEquals(10L, model.durationMs());
    }

    // -------------------------------------------------------------------------
    // toOutboundModels
    // -------------------------------------------------------------------------

    @Test
    void toOutboundModels_emptyList() {
        List<OutboundRequestLogEntryModel> result =
                RequestLogModelMapper.toOutboundModels(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void toOutboundModels_multipleRows() {
        OutboundRequestLogRow row1 = OutboundRequestLogRow.builder()
                .id(1L).requestTime(100L).traceId("t1").repoKey("maven-remote")
                .httpMethod("GET").requestUrl("https://repo1.example.com/artifact")
                .statusCode(200).durationMs(50L)
                .build();
        OutboundRequestLogRow row2 = OutboundRequestLogRow.builder()
                .id(2L).requestTime(200L).traceId("t2").repoKey("npm-remote")
                .httpMethod("PUT").requestUrl("https://repo2.example.com/package")
                .statusCode(503).durationMs(100L)
                .build();

        List<OutboundRequestLogEntryModel> result =
                RequestLogModelMapper.toOutboundModels(List.of(row1, row2));

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("maven-remote", result.get(0).repoKey());
        assertEquals(2L, result.get(1).id());
        assertEquals("npm-remote", result.get(1).repoKey());
    }
}
