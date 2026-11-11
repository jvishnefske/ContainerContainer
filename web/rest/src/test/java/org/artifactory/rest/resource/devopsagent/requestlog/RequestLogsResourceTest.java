/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2025 JFrog Ltd.
 * Licensed under the GNU AGPL v3 or later — see project root for full notice.
 *
 */

package org.artifactory.rest.resource.devopsagent.requestlog;

import jakarta.ws.rs.core.Response;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.resource.devopsagent.requestlog.dto.RequestLogListResponseModel;
import org.artifactory.rest.resource.devopsagent.requestlog.dto.TraceCorrelationResponseModel;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.Cursor;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchResult;
import org.artifactory.storage.db.devopsagent.requestlog.model.TraceResult;
import org.artifactory.storage.db.devopsagent.requestlog.service.OutboundRequestLogService;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogException;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RequestLogsResourceTest {

    private RequestLogsResource resource;
    private RequestLogService requestLogService;
    private OutboundRequestLogService outboundRequestLogService;
    private ArtifactoryContext context;

    @BeforeEach
    void setUp() {
        resource = new RequestLogsResource();
        requestLogService = mock(RequestLogService.class);
        outboundRequestLogService = mock(OutboundRequestLogService.class);
        context = mock(ArtifactoryContext.class);
        when(context.beanForType(RequestLogService.class)).thenReturn(requestLogService);
        when(context.beanForType(OutboundRequestLogService.class)).thenReturn(outboundRequestLogService);
    }

    // -------------------------------------------------------------------------
    // Group A: searchInbound
    // -------------------------------------------------------------------------

    @Test
    void searchInbound_happyPath_emptyResult() {
        SearchResult<RequestLogRow> emptyResult =
                new SearchResult<>(Collections.emptyList(), false, null);
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(requestLogService.searchInbound(any())).thenReturn(emptyResult);

            Response response = resource.searchInbound(
                    null, null, null, null, null, null, null, null);

            assertEquals(200, response.getStatus());
            RequestLogListResponseModel<?> body = (RequestLogListResponseModel<?>) response.getEntity();
            assertNotNull(body);
            assertEquals(0, body.logs().size());
            assertEquals(false, body.hasMore());
            assertNull(body.nextCursor());
        }
    }

    @Test
    void searchInbound_happyPath_withNextCursor() {
        RequestLogRow row1 = minimalRow();
        RequestLogRow row2 = minimalRow();
        Cursor cursor = new Cursor(1000L, 42L);
        SearchResult<RequestLogRow> result = new SearchResult<>(List.of(row1, row2), true, cursor);
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(requestLogService.searchInbound(any())).thenReturn(result);

            Response response = resource.searchInbound(
                    null, null, null, null, null, null, null, null);

            assertEquals(200, response.getStatus());
            RequestLogListResponseModel<?> body = (RequestLogListResponseModel<?>) response.getEntity();
            assertNotNull(body);
            assertEquals(2, body.logs().size());
            assertEquals(true, body.hasMore());
            assertNotNull(body.nextCursor());
        }
    }

    @Test
    void searchInbound_throws501WhenDisabled() {
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(requestLogService.searchInbound(any()))
                    .thenThrow(new RequestLogException(501, "disabled"));

            Response response = resource.searchInbound(
                    null, null, null, null, null, null, null, null);

            assertEquals(501, response.getStatus());
        }
    }

    @Test
    void searchInbound_throws400OnBadParam() {
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(requestLogService.searchInbound(any()))
                    .thenThrow(new RequestLogException(400, "bad param"));

            Response response = resource.searchInbound(
                    null, null, null, null, null, null, null, null);

            assertEquals(400, response.getStatus());
        }
    }

    @Test
    void searchInbound_throws500OnInternalError() {
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(requestLogService.searchInbound(any()))
                    .thenThrow(new RequestLogException(500, "Internal error in request log API"));

            Response response = resource.searchInbound(
                    null, null, null, null, null, null, null, null);

            assertEquals(500, response.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Group B: searchOutbound
    // -------------------------------------------------------------------------

    @Test
    void searchOutbound_happyPath() {
        SearchResult<OutboundRequestLogRow> emptyResult =
                new SearchResult<>(Collections.emptyList(), false, null);
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(outboundRequestLogService.searchOutbound(any())).thenReturn(emptyResult);

            Response response = resource.searchOutbound(
                    null, null, null, null, null, null, null, null, null);

            assertEquals(200, response.getStatus());
            RequestLogListResponseModel<?> body = (RequestLogListResponseModel<?>) response.getEntity();
            assertNotNull(body);
            assertEquals(0, body.logs().size());
        }
    }

    @Test
    void searchOutbound_throws501WhenDisabled() {
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(outboundRequestLogService.searchOutbound(any()))
                    .thenThrow(new RequestLogException(501, "disabled"));

            Response response = resource.searchOutbound(
                    null, null, null, null, null, null, null, null, null);

            assertEquals(501, response.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Group C: trace
    // -------------------------------------------------------------------------

    @Test
    void trace_happyPath() {
        TraceResult traceResult = new TraceResult(
                Collections.emptyList(), false, Collections.emptyList(), false);
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(requestLogService.findTrace("trace-abc", null, null)).thenReturn(traceResult);

            Response response = resource.trace("trace-abc", null, null);

            assertEquals(200, response.getStatus());
            TraceCorrelationResponseModel body = (TraceCorrelationResponseModel) response.getEntity();
            assertNotNull(body);
            assertEquals("trace-abc", body.traceId());
        }
    }

    @Test
    void trace_withTimeParams() {
        TraceResult traceResult = new TraceResult(
                Collections.emptyList(), false, Collections.emptyList(), false);
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(requestLogService.findTrace("trace-xyz", 1000L, 2000L)).thenReturn(traceResult);

            Response response = resource.trace("trace-xyz", "1000", "2000");

            assertEquals(200, response.getStatus());
            TraceCorrelationResponseModel body = (TraceCorrelationResponseModel) response.getEntity();
            assertNotNull(body);
            assertEquals(1000L, body.timeFrom());
            assertEquals(2000L, body.timeTo());
        }
    }

    @Test
    void trace_throws400OnBadTraceId() {
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(requestLogService.findTrace(any(), any(), any()))
                    .thenThrow(new RequestLogException(400, "bad"));

            Response response = resource.trace("bad-trace", null, null);

            assertEquals(400, response.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static RequestLogRow minimalRow() {
        return RequestLogRow.builder()
                .id(1L).requestTime(100L).traceId("t1").username("u")
                .httpMethod("GET").requestPath("/api/test").statusCode(500).durationMs(1L)
                .build();
    }
}
