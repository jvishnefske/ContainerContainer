package org.artifactory.ui.rest.resource.admin.configuration.proxies;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.ui.rest.resource.admin.configuration.proxies.model.ProxyBasicInfo;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import jakarta.ws.rs.core.Response;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
class ProxyResourceTest {
    @Mock
    protected ConfigServiceFactory configServiceFactory;
    @Mock
    private SecurityService securityService;
    @Spy
    private ProxyResource proxyResource;
    private AutoCloseable closable;

    @BeforeEach
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        closable = MockitoAnnotations.openMocks(this);
        proxyResource.setSecurityService(securityService);
        proxyResource.setConfigServiceFactory(configServiceFactory);
        proxyResource.setArtifactoryResponse(Mockito.mock(ArtifactoryRestResponse.class));
        doReturn(Mockito.mock(Response.class)).when(proxyResource).runService(any(), anyBoolean());
    }

    @AfterEach
    public void tearDown() throws Exception {
        closable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void getProxiesForNonAuthorizedUser() {
        when(securityService.isAnyProjectAdmin()).thenReturn(false);
        Assertions.assertThrows(AuthorizationRestException.class, () -> proxyResource.getProxies());
    }

    @Test
    void ensureIsAdminPassedToRequestAttribute() {
        when(securityService.isAnyProjectAdmin()).thenReturn(true);
        proxyResource.getProxies();
        Mockito.verify(proxyResource, times(1)).runService(any(), eq(false));
    }

    @Test
    void getNonAdminProxies_shouldReturnOnlyKeyAndPlatformDefault() {
        Response serviceResponse = Response.ok("""
                [
                    {"key":"proxy-1","host":"h1","port":8080,"platformDefault":true},
                    {"key":"proxy-2","host":"h2","port":9090,"platformDefault":false}
                ]
                """).build();
        doReturn(serviceResponse)
                .when(proxyResource).runService(any(), eq(false));
        Response result = proxyResource.getNonAdminProxies();
        assertEquals(200, result.getStatus());
        List<ProxyBasicInfo> proxies = (List<ProxyBasicInfo>) result.getEntity();
        assertEquals(2, proxies.size());
        assertEquals("proxy-1", proxies.get(0).key());
        assertTrue(proxies.get(0).platformDefault());
        assertEquals("proxy-2", proxies.get(1).key());
        assertFalse(proxies.get(1).platformDefault());
    }

    @Test
    void getNonAdminProxies_shouldReturnOriginalResponse_whenParsingFails() {
        Response serviceResponse = Response.ok("not-valid-json").build();
        doReturn(serviceResponse)
                .when(proxyResource).runService(any(), eq(false));
        Response result = proxyResource.getNonAdminProxies();
        assertEquals(200, result.getStatus());
        assertEquals("not-valid-json", result.getEntity());
    }

    @Test
    void getNonAdminProxies_shouldReturnOriginalResponse_whenEntityIsNull() {
        Response serviceResponse = Response.ok().build();
        doReturn(serviceResponse)
                .when(proxyResource).runService(any(), eq(false));
        Response result = proxyResource.getNonAdminProxies();
        assertEquals(200, result.getStatus());
        assertNull(result.getEntity());
    }
}
