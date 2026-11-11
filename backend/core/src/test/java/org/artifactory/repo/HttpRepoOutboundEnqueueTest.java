package org.artifactory.repo;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.service.OutboundRequestLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpRepoOutboundEnqueueTest {

    private static final long START_MS = 1_700_000_000_000L;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void nullSvcIsNoOp() {
        HttpGet method = methodWithUri("http://example.com/path");

        assertDoesNotThrow(
                () -> HttpRepo.doEnqueueFailedOutbound(null, "my-repo", method, START_MS, new IOException("fail")));
    }

    @Test
    void featureDisabledIsNoOp() {
        OutboundRequestLogService svc = mock(OutboundRequestLogService.class);
        when(svc.isFeatureEnabled()).thenReturn(false);
        HttpGet method = methodWithUri("http://example.com/path");

        HttpRepo.doEnqueueFailedOutbound(svc, "my-repo", method, START_MS, new IOException("fail"));

        verify(svc, never()).enqueueOutbound(any());
    }

    @Test
    void connectTimeoutYields408() {
        OutboundRequestLogService svc = mock(OutboundRequestLogService.class);
        when(svc.isFeatureEnabled()).thenReturn(true);
        HttpGet method = methodWithUri("http://example.com/path");

        HttpRepo.doEnqueueFailedOutbound(svc, "my-repo", method, START_MS, new ConnectTimeoutException());

        ArgumentCaptor<OutboundRequestLogRow> captor = ArgumentCaptor.forClass(OutboundRequestLogRow.class);
        verify(svc).enqueueOutbound(captor.capture());
        assertEquals(HttpStatus.SC_REQUEST_TIMEOUT, captor.getValue().statusCode());
    }

    @Test
    void socketTimeoutYields408() {
        OutboundRequestLogService svc = mock(OutboundRequestLogService.class);
        when(svc.isFeatureEnabled()).thenReturn(true);
        HttpGet method = methodWithUri("http://example.com/path");

        HttpRepo.doEnqueueFailedOutbound(svc, "my-repo", method, START_MS, new SocketTimeoutException());

        ArgumentCaptor<OutboundRequestLogRow> captor = ArgumentCaptor.forClass(OutboundRequestLogRow.class);
        verify(svc).enqueueOutbound(captor.capture());
        assertEquals(HttpStatus.SC_REQUEST_TIMEOUT, captor.getValue().statusCode());
    }

    @Test
    void genericIoExceptionYields500() {
        OutboundRequestLogService svc = mock(OutboundRequestLogService.class);
        when(svc.isFeatureEnabled()).thenReturn(true);
        HttpGet method = methodWithUri("http://example.com/path");

        HttpRepo.doEnqueueFailedOutbound(svc, "my-repo", method, START_MS, new IOException("network error"));

        ArgumentCaptor<OutboundRequestLogRow> captor = ArgumentCaptor.forClass(OutboundRequestLogRow.class);
        verify(svc).enqueueOutbound(captor.capture());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, captor.getValue().statusCode());
    }

    @Test
    void traceIdCapturedFromMdc() {
        MDC.put("uber-trace-id", "abc123");
        OutboundRequestLogService svc = mock(OutboundRequestLogService.class);
        when(svc.isFeatureEnabled()).thenReturn(true);
        HttpGet method = methodWithUri("http://example.com/path");

        HttpRepo.doEnqueueFailedOutbound(svc, "repo-key", method, START_MS, new IOException());

        ArgumentCaptor<OutboundRequestLogRow> captor = ArgumentCaptor.forClass(OutboundRequestLogRow.class);
        verify(svc).enqueueOutbound(captor.capture());
        assertEquals("abc123", captor.getValue().traceId());
        assertEquals("repo-key", captor.getValue().repoKey());
    }

    @Test
    void exceptionInEnqueueIsSwallowed() {
        OutboundRequestLogService svc = mock(OutboundRequestLogService.class);
        when(svc.isFeatureEnabled()).thenReturn(true);
        doThrow(new RuntimeException("enqueue failed")).when(svc).enqueueOutbound(any());
        HttpGet method = methodWithUri("http://example.com/path");

        assertDoesNotThrow(
                () -> HttpRepo.doEnqueueFailedOutbound(svc, "my-repo", method, START_MS, new IOException()));
    }

    private static HttpGet methodWithUri(String url) {
        try {
            HttpGet get = new HttpGet();
            get.setURI(new URI(url));
            return get;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
