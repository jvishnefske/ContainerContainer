package org.artifactory.webapp.servlet;

import org.artifactory.api.request.ArtifactoryResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceLoggingResponseTest {

    @Test
    void sendResponse_rendersHeadersBlock_whenHeadersPresent() throws IOException {
        StringWriter out = new StringWriter();
        ArtifactoryResponse delegate = mock(ArtifactoryResponse.class);
        when(delegate.getWriter()).thenReturn(out);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Orig-Client-Uri", "/orig/client/uri");
        headers.put("X-Forwarded-Host", "example.com");

        TraceLoggingResponse response = new TraceLoggingResponse(delegate);
        response.setRequestHeaders(headers);
        response.sendResponse("req-id", "GET", "the-user", "repo/path");

        String rendered = out.toString();
        // Exact structure: a "Headers:" line followed by two-space-indented "key: value" lines
        assertTrue(rendered.contains("User: the-user\n"));
        assertTrue(rendered.contains("Headers:\n  X-Orig-Client-Uri: /orig/client/uri\n"
                + "  X-Forwarded-Host: example.com\n"), "Unexpected headers block:\n" + rendered);
        // No trailing space after "Headers:"
        assertFalse(rendered.contains("Headers: \n"));
        verify(delegate).sendSuccess();
    }

    @Test
    void sendResponse_omitsHeadersBlock_whenHeadersNotSet() throws IOException {
        StringWriter out = new StringWriter();
        ArtifactoryResponse delegate = mock(ArtifactoryResponse.class);
        when(delegate.getWriter()).thenReturn(out);

        TraceLoggingResponse response = new TraceLoggingResponse(delegate);
        response.sendResponse("req-id", "GET", "the-user", "repo/path");

        String rendered = out.toString();
        assertFalse(rendered.contains("Headers:"), "Headers block should be omitted when no headers are set");
    }

    @Test
    void sendResponse_omitsHeadersBlock_whenHeadersEmpty() throws IOException {
        StringWriter out = new StringWriter();
        ArtifactoryResponse delegate = mock(ArtifactoryResponse.class);
        when(delegate.getWriter()).thenReturn(out);

        TraceLoggingResponse response = new TraceLoggingResponse(delegate);
        response.setRequestHeaders(new LinkedHashMap<>());
        response.sendResponse("req-id", "GET", "the-user", "repo/path");

        assertFalse(out.toString().contains("Headers:"));
    }

    @Test
    void sendResponse_sanitizesCrlfInHeaderValue_preventingLineInjection() throws IOException {
        StringWriter out = new StringWriter();
        ArtifactoryResponse delegate = mock(ArtifactoryResponse.class);
        when(delegate.getWriter()).thenReturn(out);

        Map<String, String> headers = new LinkedHashMap<>();
        // Crafted value attempting to forge an extra "User:" line in the trace output
        headers.put("X-Forwarded-Host", "real.com\r\nUser: admin");

        TraceLoggingResponse response = new TraceLoggingResponse(delegate);
        response.setRequestHeaders(headers);
        response.sendResponse("req-id", "GET", "the-user", "repo/path");

        String rendered = out.toString();
        // The injected newline must be collapsed so the value stays on a single line
        assertTrue(rendered.contains("  X-Forwarded-Host: real.com User: admin\n"),
                "CRLF was not sanitized:\n" + rendered);
        assertFalse(rendered.contains("real.com\r\n"), "Raw CRLF leaked into trace output");
    }
}
