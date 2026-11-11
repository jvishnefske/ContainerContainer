package org.artifactory.webapp.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class CapturingHttpServletResponseWrapperTest {

    @Mock
    private HttpServletResponse delegate;
    @Mock
    ServletOutputStream servletOutputStream;
    private CapturingHttpServletResponseWrapper responseWrapper;

    @BeforeEach
    void setUp() {
        responseWrapper = new CapturingHttpServletResponseWrapper(delegate, System::currentTimeMillis);
    }

    @Test
    void testSetStatus() {
        responseWrapper.setStatus(200);
        assertEquals(200, responseWrapper.getStatus());
        verify(delegate).setStatus(200);
    }

    @Test
    void testSetContentLength() {
        responseWrapper.setContentLength(100);
        assertEquals(100, responseWrapper.getContentLength());
        verify(delegate).setContentLength(100);
    }

    @Test
    void testAddHeader() {
        responseWrapper.addHeader("Test-Header", "Test-Value");
        verify(delegate).addHeader("Test-Header", "Test-Value");
    }

    @Test
    void testSetHeader() {
        responseWrapper.setHeader("Test-Header", "Test-Value");
        verify(delegate).setHeader("Test-Header", "Test-Value");
    }

    @Test
    void testAddIntHeader() {
        responseWrapper.addIntHeader("Test-Header", 100);
        verify(delegate).addIntHeader("Test-Header", 100);
    }

    @Test
    void testSetIntHeader() {
        responseWrapper.setIntHeader("Test-Header", 100);
        verify(delegate).setIntHeader("Test-Header", 100);
    }

    @Test
    void testSendRedirect() throws Exception {
        responseWrapper.sendRedirect("http://example.com");
        assertEquals(302, responseWrapper.getStatus());
        verify(delegate).sendRedirect("http://example.com");
    }

    @Test
    void testGetByteCount() throws IOException {
        byte[] data = new byte[5];
        when(delegate.getOutputStream()).thenReturn(servletOutputStream);
        responseWrapper.getOutputStream().write(data);
        assertEquals(5, responseWrapper.getContentLength());
    }

    @Test
    void testGetStatus() {
        when(delegate.getStatus()).thenReturn(400);
        assertEquals(400, responseWrapper.getStatusFromCommitedResponse());
    }

    @Test
    void testSendErrorWithStatus() throws IOException {
        PrintWriter writer = mock(PrintWriter.class);
        when(responseWrapper.getWriter()).thenReturn(writer);
        responseWrapper.sendError(404);
        assertEquals(404, responseWrapper.getStatus());
    }

    @Test
    void testSendErrorWithStatusAndMessage() throws IOException {
        PrintWriter writer = mock(PrintWriter.class);
        when(responseWrapper.getWriter()).thenReturn(writer);
        responseWrapper.sendError(500, "Internal Server Error");
        assertEquals(500, responseWrapper.getStatus());
    }

    @Test
    void testSendErrorWithStatusAlreadyCommitted() throws IOException {
        when(delegate.isCommitted()).thenReturn(true);
        responseWrapper.sendError(404);
        assertEquals(200, responseWrapper.getStatus());
        verify(delegate, times(0)).sendError(404);
    }

    @Test
    void testSendErrorWithStatusAndMessageAlreadyCommitted() throws IOException {
        when(delegate.isCommitted()).thenReturn(true);
        responseWrapper.sendError(500, "Internal Server Error");
        assertEquals(200, responseWrapper.getStatus());
        verify(delegate, times(0)).sendError(500, "Internal Server Error");
    }

    @Test
    void testCaptureString() {
        responseWrapper.setHeader(HttpHeaders.CONTENT_LENGTH, "1234");
        assertEquals(1234, responseWrapper.getContentLength());
    }

    @Test
    void recordStatusForMetrics_shouldSetStatusAndTimeToSetStatus() {
        // Arrange - use a controlled time supplier
        long expectedTime = 12345L;
        CapturingHttpServletResponseWrapper wrapper =
                new CapturingHttpServletResponseWrapper(delegate, () -> expectedTime);

        // Act
        wrapper.recordStatusForMetrics(500);

        // Assert - status and timeToSetStatus should be updated
        assertEquals(500, wrapper.getStatus());
        assertEquals(expectedTime, wrapper.getTimeToSetStatus());
    }

    @Test
    void recordStatusForMetrics_shouldNotCallSetStatusOnDelegate() {
        // Act
        responseWrapper.recordStatusForMetrics(500);

        // Assert - setStatus should NOT be called on the delegate
        verify(delegate, times(0)).setStatus(500);
        // But the wrapper's internal status should be updated
        assertEquals(500, responseWrapper.getStatus());
    }

    @Test
    void recordStatusForMetrics_shouldNotOverwriteExistingTimeToSetStatus() {
        // Arrange - first set a status to establish timeToSetStatus
        long firstTime = 100L;
        long secondTime = 200L;
        long[] times = {firstTime, secondTime};
        int[] index = {0};
        CapturingHttpServletResponseWrapper wrapper =
                new CapturingHttpServletResponseWrapper(delegate, () -> times[index[0]++]);

        // Act - first call records the time
        wrapper.recordStatusForMetrics(400);
        // Second call should not overwrite the time
        wrapper.recordStatusForMetrics(500);

        // Assert - status should be updated but timeToSetStatus should remain from first call
        assertEquals(500, wrapper.getStatus());
        assertEquals(firstTime, wrapper.getTimeToSetStatus());
    }
}