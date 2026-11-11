package org.artifactory.rest.resource.system;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.HttpUtils;
import org.jfrog.storage.DataSourceHealthException;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author Alexei Vainshtein
 */
public class PingResourceTest extends ArtifactoryHomeBoundTest {

    private InternalArtifactoryContext context;
    private StorageService storageService;

    @BeforeMethod
    public void setup() throws Exception {
        context = mock(InternalArtifactoryContext.class);
        when(context.isOffline()).thenReturn(false);
        when(context.getArtifactoryHome()).thenReturn(homeStub);
        storageService = mock(StorageService.class);
        doNothing().when(storageService).ping(true);
        when(context.beanForType(StorageService.class)).thenReturn(storageService);
        AccessService accessService = mock(AccessService.class);
        doNothing().when(accessService).ping();
        when(context.beanForType(AccessService.class)).thenReturn(accessService);
        InternalDbService internalDbService = mock(InternalDbService.class);
        when(context.beanForType(InternalDbService.class)).thenReturn(internalDbService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test(dataProvider = "userAgentProvider")
    public void testPingWithLocalOnlyBasedOnUserAgent(String userAgent, boolean localOnly) {
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(addonsManager.lockdown()).thenReturn(false);
        Mockito.when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.PRO);
        PingResource pingResource = new PingResource(addonsManager);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        Enumeration<String> headers = userAgent == null ? null : Collections.enumeration(List.of(userAgent));
        when(httpServletRequest.getHeaders(HttpHeaders.USER_AGENT)).thenReturn(headers);
        pingResource.httpRequest = httpServletRequest;
        Response response = pingResource.pingArtifactory(false);
        Assert.assertEquals(response.getStatus(), HttpStatus.SC_OK);

        ArgumentCaptor<Boolean> localOnlyArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(storageService).ping(localOnlyArgumentCaptor.capture());
        assertNotNull(localOnlyArgumentCaptor.getValue());
        assertEquals(localOnlyArgumentCaptor.getValue().booleanValue(), localOnly);
    }

    @DataProvider
    public static Object[][] userAgentProvider() {
        return new Object[][] {
                {"curl/7.11.0", false},
                {"", false},
                {null, false},
                {"jfrog/frontend", true},
                {"jfrogfoobar", true},
                {"frontend", false},
        };
    }

    @Test
    public void pingArtifactoryFreeSubscription() {
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(addonsManager.lockdown()).thenReturn(false);
        Mockito.when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.HA);
        try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
            httpUtils.when(() -> HttpUtils.getRequestUserAgent((HttpServletRequest) any())).thenReturn("DUMMY");
            PingResource pingResource = new PingResource(addonsManager);
            Response response = pingResource.pingArtifactory(false);
            Assert.assertEquals(response.getStatus(), HttpStatus.SC_OK);
        }
    }

    @Test
    public void pingArtifactoryNonFreeSubscription() {
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(addonsManager.lockdown()).thenReturn(true);
        Mockito.when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.HA);
        try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
            httpUtils.when(() -> HttpUtils.getRequestUserAgent((HttpServletRequest) any())).thenReturn("DUMMY");
            PingResource pingResource = new PingResource(addonsManager);
            Response response = pingResource.pingArtifactory(false);
            Assert.assertEquals(response.getStatus(), HttpStatus.SC_SERVICE_UNAVAILABLE);
            Assert.assertEquals((String) response.getEntity(), "Server state is offline");
        }
    }

    /**
     * Verify that InternalDbService.health() is successfully executed during ping
     *
     * @throws Exception
     */
    @Test
    public void pingArtifactoryHealthyDB() throws Exception {
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(addonsManager.lockdown()).thenReturn(false);
        Mockito.when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.HA);
        try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
            httpUtils.when(() -> HttpUtils.getRequestUserAgent((HttpServletRequest) any())).thenReturn("DUMMY");
            PingResource pingResource = new PingResource(addonsManager);
            Response response = pingResource.pingArtifactory(false);
            Assert.assertEquals(response.getStatus(), HttpStatus.SC_OK);
            verify(context.beanForType(InternalDbService.class), times(1)).health();
        }
    }

    /**
     * Verify that InternalDbService.health() is unsuccessfully executed during ping
     *
     * @throws Exception
     */
    @Test
    public void pingArtifactoryUnhealthyDB() {
        InternalDbService internalDbService = context.beanForType(InternalDbService.class);
        doThrow(DataSourceHealthException.class).when(internalDbService).health();

        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(addonsManager.lockdown()).thenReturn(false);
        Mockito.when(addonsManager.getArtifactoryRunningMode()).thenReturn(ArtifactoryRunningMode.HA);
        try (MockedStatic<HttpUtils> httpUtils = mockStatic(HttpUtils.class)) {
            httpUtils.when(() -> HttpUtils.getRequestUserAgent((HttpServletRequest) any())).thenReturn("DUMMY");
            PingResource pingResource = new PingResource(addonsManager);
            Response response = pingResource.pingArtifactory(false);
            Assert.assertEquals(response.getStatus(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
            verify(context.beanForType(InternalDbService.class), times(1)).health();
        }
    }
}