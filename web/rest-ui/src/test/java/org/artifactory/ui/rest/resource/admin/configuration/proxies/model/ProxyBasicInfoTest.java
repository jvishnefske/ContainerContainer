package org.artifactory.ui.rest.resource.admin.configuration.proxies.model;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ProxyBasicInfoTest {

    @Test
    void toBasicInfo_shouldReturnOnlyKeyAndPlatformDefault_whenResponseHasFullProxyData() {
        Response response = Mockito.mock(Response.class);
        when(response.getEntity()).thenReturn("""
                [
                    {"key":"proxy-1","host":"h1","port":8080,"platformDefault":true},
                    {"key":"proxy-2","host":"h2","port":9090,"platformDefault":false}
                ]
                """);
        List<ProxyBasicInfo> result = ProxyBasicInfo.toBasicInfo(response);
        assertEquals(2, result.size());
        assertEquals("proxy-1", result.get(0).key());
        assertTrue(result.get(0).platformDefault());
        assertEquals("proxy-2", result.get(1).key());
        assertFalse(result.get(1).platformDefault());
    }

    @Test
    void toBasicInfo_shouldReturnEmptyList_whenResponseHasNoProxies() {
        Response response = Mockito.mock(Response.class);
        when(response.getEntity()).thenReturn("[]");
        List<ProxyBasicInfo> result = ProxyBasicInfo.toBasicInfo(response);
        assertTrue(result.isEmpty());
    }

    @Test
    void toBasicInfo_shouldReturnEmptyList_whenEntityIsInvalidJson() {
        Response response = Mockito.mock(Response.class);
        when(response.getEntity()).thenReturn("not-valid-json");
        List<ProxyBasicInfo> result = ProxyBasicInfo.toBasicInfo(response);
        assertEquals(result,List.of());
    }

    @Test
    void toBasicInfo_shouldReturnEmptyList_whenEntityIsNull() {
        Response response = Mockito.mock(Response.class);
        when(response.getEntity()).thenReturn(null);
        List<ProxyBasicInfo> result = ProxyBasicInfo.toBasicInfo(response);
        assertEquals(result,List.of());
    }
}
