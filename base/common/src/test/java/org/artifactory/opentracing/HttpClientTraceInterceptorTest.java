package org.artifactory.opentracing;

import org.apache.http.HttpRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.artifactory.util.HttpClientConfigurator;
import org.jfrog.common.opentracing.TraceHeaderInjector;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClientTraceInterceptorTest {

    @Test
    void configure_shouldAddInterceptor_whenInjectorPresent() {
        // Arrange
        TraceHeaderInjector injector = mock(TraceHeaderInjector.class);
        HttpClientTraceInterceptor interceptor = new HttpClientTraceInterceptor(injector);
        HttpClientConfigurator configurator = mock(HttpClientConfigurator.class);

        // Act
        interceptor.configure(configurator);

        // Assert
        verify(configurator, times(1)).addRequestInterceptor(interceptor);
    }

    @Test
    void configure_shouldNotAddInterceptor_whenInjectorIsNull() {
        // Arrange
        HttpClientTraceInterceptor interceptor = new HttpClientTraceInterceptor(null);
        HttpClientConfigurator configurator = mock();

        // Act
        interceptor.configure(configurator);

        // Assert
        verify(configurator, never()).addRequestInterceptor(any());
    }

    @Test
    void process_shouldInjectHeaders_whenNotPresent() {
        TraceHeaderInjector injector = mock();
        Map<String, String> headers = new HashMap<>();
        headers.put("uber-trace-id", "70884e0b7239cc0d:5f9f725e373899a0:7e9bf68835fd9324:0");
        when(injector.headersToInject(anyString(), anyString())).thenReturn(headers);

        HttpClientTraceInterceptor interceptor = new HttpClientTraceInterceptor(injector);
        HttpRequest request = new BasicHttpRequest("GET", "/api/test");
        HttpContext context = mock(HttpContext.class);

        interceptor.process(request, context);

        assertTrue(request.containsHeader("uber-trace-id"));
        assertEquals("70884e0b7239cc0d:5f9f725e373899a0:7e9bf68835fd9324:0",
                request.getFirstHeader("uber-trace-id").getValue());
    }

    @Test
    void process_shouldNotOverrideExistingHeader() {
        TraceHeaderInjector injector = mock(TraceHeaderInjector.class);
        Map<String, String> headers = new HashMap<>();
        headers.put("trace-id", "abc123");
        when(injector.headersToInject(anyString(), anyString())).thenReturn(headers);

        HttpClientTraceInterceptor interceptor = new HttpClientTraceInterceptor(injector);
        HttpRequest request = new BasicHttpRequest("GET", "/api/test");
        request.setHeader("trace-id", "existing");
        HttpContext context = mock(HttpContext.class);

        interceptor.process(request, context);

        assertEquals("existing", request.getFirstHeader("trace-id").getValue());
    }

    @Test
    void process_shouldNotFail_whenNoHeadersToInject() {
        TraceHeaderInjector injector = mock(TraceHeaderInjector.class);
        when(injector.headersToInject(anyString(), anyString())).thenReturn(Collections.emptyMap());

        HttpClientTraceInterceptor interceptor = new HttpClientTraceInterceptor(injector);
        HttpRequest request = new BasicHttpRequest("GET", "/api/test");
        HttpContext context = mock(HttpContext.class);

        interceptor.process(request, context);

        assertEquals(0, request.getAllHeaders().length);
    }
}
