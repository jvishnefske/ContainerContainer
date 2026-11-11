package org.artifactory.http;

import org.apache.http.HttpRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.artifactory.util.TenantIdProvider;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class SrcTenantIdHeaderInterceptorTest {

    private final SrcTenantIdHeaderInterceptor interceptor = new SrcTenantIdHeaderInterceptor();

    @Test
    void process_setsHeader_whenTenantIdPresent() {
        HttpRequest request = new BasicHttpRequest("GET", "/path");
        try (MockedStatic<TenantIdProvider> tenantMock = mockStatic(TenantIdProvider.class)) {
            tenantMock.when(TenantIdProvider::getTenantId).thenReturn(Optional.of("my-tenant-id"));
            interceptor.process(request, mock(HttpContext.class));
        }
        assertEquals("my-tenant-id",
                request.getFirstHeader(TenantIdProvider.X_JFROG_SRC_TENANT_ID).getValue());
    }

    @Test
    void process_doesNotSetHeader_whenTenantIdEmpty() {
        HttpRequest request = new BasicHttpRequest("GET", "/path");
        try (MockedStatic<TenantIdProvider> tenantMock = mockStatic(TenantIdProvider.class)) {
            tenantMock.when(TenantIdProvider::getTenantId).thenReturn(Optional.empty());
            interceptor.process(request, mock(HttpContext.class));
        }
        assertNull(request.getFirstHeader(TenantIdProvider.X_JFROG_SRC_TENANT_ID));
    }
}
