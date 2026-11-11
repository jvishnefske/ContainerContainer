package org.artifactory.storage.db.devopsagent.requestlog.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.TraceResult;
import org.artifactory.test.ArtifactoryHomeStub;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for {@link RequestLogServiceImpl#findTrace} — paths not covered by
 * {@link RequestLogServiceImplFeatureFlagTest}.
 */
public class RequestLogServiceImplTest {

    private static final long FIXED_CLOCK_MS = 1_700_000_000_000L;

    private ArtifactoryHomeStub homeStub;
    private RequestLogDao requestLogDao;
    private OutboundRequestLogService outboundRequestLogService;
    private RequestLogServiceImpl service;

    @BeforeMethod
    public void setUp() throws Exception {
        homeStub = new ArtifactoryHomeStub(getClass().getName());
        homeStub.bind();
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "true");

        requestLogDao = mock(RequestLogDao.class);
        outboundRequestLogService = mock(OutboundRequestLogService.class);

        service = new RequestLogServiceImpl(requestLogDao, 10);

        Field f = RequestLogServiceImpl.class.getDeclaredField("outboundRequestLogService");
        f.setAccessible(true);
        f.set(service, outboundRequestLogService);

        service.setClock(() -> FIXED_CLOCK_MS);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryHomeStub.unbind();
    }

    // -------------------------------------------------------------------------
    // findTrace — feature flag
    // -------------------------------------------------------------------------

    @Test
    public void findTrace_throwsWhenFeatureDisabled() {
        homeStub.setProperty(ConstantValues.devopsTroubleshootingEnabled, "false");
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> service.findTrace("trace-1", null, null));
        assertEquals(ex.getStatusCode(), 501);
    }

    // -------------------------------------------------------------------------
    // findTrace — traceId validation
    // -------------------------------------------------------------------------

    @Test
    public void findTrace_throwsWhenTraceIdNull() {
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> service.findTrace(null, null, null));
        assertEquals(ex.getStatusCode(), 400);
        assertTrue(ex.getMessage().contains("traceId"),
                "Message should mention traceId, got: " + ex.getMessage());
    }

    @Test
    public void findTrace_throwsWhenTraceIdBlank() {
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> service.findTrace("  ", null, null));
        assertEquals(ex.getStatusCode(), 400);
    }

    // -------------------------------------------------------------------------
    // findTrace — time range validation
    // -------------------------------------------------------------------------

    @Test
    public void findTrace_throwsWhenFromAfterTo() {
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> service.findTrace("t1", 2000L, 1000L));
        assertEquals(ex.getStatusCode(), 400);
        assertTrue(ex.getMessage().contains("time_from"),
                "Message should mention time_from, got: " + ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // findTrace — default window
    // -------------------------------------------------------------------------

    @Test
    public void findTrace_usesDefaultWindowWhenTimesNull() throws Exception {
        long expectedFrom = FIXED_CLOCK_MS - RequestLogServiceImpl.DEFAULT_TRACE_WINDOW_MS;
        long expectedTo = FIXED_CLOCK_MS;

        when(requestLogDao.findByTraceId(
                eq("t1"), eq(expectedFrom), eq(expectedTo),
                eq(AbstractRequestLogServiceImpl.TRACE_PER_DIRECTION_CAP + 1)))
                .thenReturn(Collections.emptyList());
        when(outboundRequestLogService.findOutboundByTrace("t1", expectedFrom, expectedTo))
                .thenReturn(Collections.emptyList());

        TraceResult result = service.findTrace("t1", null, null);

        assertNotNull(result);
        assertTrue(result.inbound().isEmpty());
        assertFalse(result.truncatedInbound());
        assertTrue(result.outbound().isEmpty());
        assertFalse(result.truncatedOutbound());

        ArgumentCaptor<Long> fromCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> toCaptor = ArgumentCaptor.forClass(Long.class);
        verify(requestLogDao).findByTraceId(
                eq("t1"), fromCaptor.capture(), toCaptor.capture(),
                eq(AbstractRequestLogServiceImpl.TRACE_PER_DIRECTION_CAP + 1));
        assertEquals((long) fromCaptor.getValue(), expectedFrom);
        assertEquals((long) toCaptor.getValue(), expectedTo);
    }

    // -------------------------------------------------------------------------
    // findTrace — happy path
    // -------------------------------------------------------------------------

    @Test
    public void findTrace_happyPath() throws Exception {
        List<RequestLogRow> inboundRows = List.of(minimalRow(), minimalRow());
        List<OutboundRequestLogRow> outboundRows = List.of(minimalOutboundRow());

        when(requestLogDao.findByTraceId(anyString(), anyLong(), anyLong(), anyInt()))
                .thenReturn(inboundRows);
        when(outboundRequestLogService.findOutboundByTrace(anyString(), anyLong(), anyLong()))
                .thenReturn(outboundRows);

        TraceResult result = service.findTrace("t1", 0L, 1000L);

        assertNotNull(result);
        assertEquals(result.inbound().size(), 2);
        assertFalse(result.truncatedInbound());
        assertEquals(result.outbound().size(), 1);
        assertFalse(result.truncatedOutbound());
    }

    // -------------------------------------------------------------------------
    // findTrace — truncation
    // -------------------------------------------------------------------------

    @Test
    public void findTrace_truncatesInboundAtCap() throws Exception {
        int cap = AbstractRequestLogServiceImpl.TRACE_PER_DIRECTION_CAP;
        when(requestLogDao.findByTraceId(anyString(), anyLong(), anyLong(), anyInt()))
                .thenAnswer(inv -> {
                    List<RequestLogRow> list = new ArrayList<>();
                    for (int i = 0; i <= cap; i++) {
                        list.add(minimalRow());
                    }
                    return list;
                });
        when(outboundRequestLogService.findOutboundByTrace(anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());

        TraceResult result = service.findTrace("t1", 0L, 1000L);

        assertTrue(result.truncatedInbound());
        assertEquals(result.inbound().size(), cap);
        assertFalse(result.truncatedOutbound());
    }

    // -------------------------------------------------------------------------
    // findTrace — SQLException wrapping
    // -------------------------------------------------------------------------

    @Test
    public void findTrace_wrapsDAOSQLException() throws Exception {
        when(requestLogDao.findByTraceId(anyString(), anyLong(), anyLong(), anyInt()))
                .thenThrow(new SQLException("db error"));

        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> service.findTrace("t1", 0L, 1000L));

        assertEquals(ex.getStatusCode(), 500);
        assertTrue(ex.getMessage().contains(AbstractRequestLogServiceImpl.INTERNAL_ERROR_MESSAGE),
                "Message should contain INTERNAL_ERROR_MESSAGE, got: " + ex.getMessage());
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause() instanceof SQLException);
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

    private static OutboundRequestLogRow minimalOutboundRow() {
        return OutboundRequestLogRow.builder()
                .id(2L).requestTime(100L).traceId("t1").repoKey("npm-remote")
                .httpMethod("GET").requestUrl("https://example.com").statusCode(404).durationMs(5L)
                .build();
    }
}
