package org.artifactory.rest.resource.system;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.config.ConfigurationException;
import org.artifactory.throtlling.generic.rrl.model.IRateLimiterConfig;
import org.artifactory.throtlling.generic.rrl.model.RequestRateLimitConfig;
import org.artifactory.throtlling.generic.rrl.model.RequestRateLimiterSettings;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import jakarta.ws.rs.core.Response;
import java.util.Set;
import java.util.stream.Stream;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.artifactory.rest.resource.system.RequestRateLimiterResourceV2.RL_DELETED_SUCCESSFULLY;
import static org.artifactory.rest.resource.system.RequestRateLimiterResourceV2.RL_UPDATED_SUCCESSFULLY;
import static org.artifactory.util.AolUtils.NOT_DASHBOARD_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class RequestRateLimiterResourceV2Test {
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AolService aolService;
    @Mock
    private IRequestRateLimiterService rlConfigService;
    @InjectMocks
    private RequestRateLimiterResourceV2 rrlResource;
    private AutoCloseable mocks;
    private IRateLimiterConfig rlConfig;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        var defaultSettings = new RequestRateLimiterSettings();
        defaultSettings.setDomainSuffix(""); // Unit tests fail without this line because domainSuffix is @NotNull
        var dockerhubSettings = new RequestRateLimiterSettings("dockerhub", ".docker.io", 1_000, 1_000, true);
        rlConfig = new RequestRateLimitConfig(Set.of(defaultSettings, dockerhubSettings));
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testGetConfig() {
        try (Response response = rrlResource.getConfig()) {
            verify(rlConfigService, times(1)).getConfig();
            verify(aolService, times(1)).isAol();
            assertEquals(OK.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testGetConfigException() {
        String someException = "some exception";
        doThrow(new ConfigurationException(someException)).when(rlConfigService).getConfig();
        try (Response response = rrlResource.getConfig()) {
            verify(rlConfigService, times(1)).getConfig();
            verify(aolService, times(1)).isAol();
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals(someException, response.getEntity());
        }
    }

    @ParameterizedTest
    @MethodSource("getConfigAol")
    void testGetConfigAol(boolean dashboardUser, int invocations, int expectedStatus, String expectedEntity) {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(dashboardUser);
        try (Response response = rrlResource.getConfig()) {
            verify(rlConfigService, times(invocations)).getConfig();
            verify(aolService, times(1)).isAol();
            verify(aolService, times(1)).isDashboardUser();
            assertEquals(expectedStatus, response.getStatus());
            assertEquals(expectedEntity, response.getEntity());
        }
    }

    private static Stream<Arguments> getConfigAol() {
        return Stream.of(
                Arguments.of(true, 1, OK.getStatusCode(), null),
                Arguments.of(false, 0, BAD_REQUEST.getStatusCode(), NOT_DASHBOARD_USER)
        );
    }

    @Test
    void testPostConfig() {
        try (Response response = rrlResource.postConfig(rlConfig)) {
            verify(rlConfigService, times(1)).updateConfig(rlConfig);
            verify(aolService, times(1)).isAol();
            assertEquals(OK.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testPostConfigException() {
        String someException = "some exception";
        doThrow(new ConfigurationException(someException)).when(rlConfigService).updateConfig(rlConfig);
        try (Response response = rrlResource.postConfig(rlConfig)) {
            verify(rlConfigService, times(1)).updateConfig(rlConfig);
            verify(aolService, times(1)).isAol();
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals(someException, response.getEntity());
        }
    }

    @ParameterizedTest
    @MethodSource("postConfigAol")
    void testPostConfigAol(boolean dashboardUser, int invocations, int expectedStatus, String expectedEntity) {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(dashboardUser);
        try (Response response = rrlResource.postConfig(rlConfig)) {
            verify(rlConfigService, times(invocations)).updateConfig(rlConfig);
            verify(aolService, times(1)).isAol();
            verify(aolService, times(1)).isDashboardUser();
            assertEquals(expectedStatus, response.getStatus());
            assertEquals(expectedEntity, response.getEntity());
        }
    }

    private static Stream<Arguments> postConfigAol() {
        return Stream.of(
                Arguments.of(true, 1, OK.getStatusCode(), RL_UPDATED_SUCCESSFULLY),
                Arguments.of(false, 0, BAD_REQUEST.getStatusCode(), NOT_DASHBOARD_USER)
        );
    }

    @Test
    void testDeleteConfig() {
        try (Response response = rrlResource.deleteConfig()) {
            verify(rlConfigService, times(1)).deleteConfig();
            verify(aolService, times(1)).isAol();
            assertEquals(OK.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testDeleteConfigException() {
        String someException = "some exception";
        doThrow(new ConfigurationException(someException)).when(rlConfigService).deleteConfig();
        try (Response response = rrlResource.deleteConfig()) {
            verify(rlConfigService, times(1)).deleteConfig();
            verify(aolService, times(1)).isAol();
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals(someException, response.getEntity());
        }
    }

    @ParameterizedTest
    @MethodSource("deleteConfigAol")
    void testDeleteConfigAol(boolean dashboardUser, int invocations, int expectedStatus, String expectedEntity) {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(dashboardUser);
        try (Response response = rrlResource.deleteConfig()) {
            assertEquals(expectedStatus, response.getStatus());
            assertEquals(expectedEntity, response.getEntity());
        }
    }

    private static Stream<Arguments> deleteConfigAol() {
        return Stream.of(
                Arguments.of(true, 1, OK.getStatusCode(), RL_DELETED_SUCCESSFULLY),
                Arguments.of(false, 0, BAD_REQUEST.getStatusCode(), NOT_DASHBOARD_USER)
        );
    }
}