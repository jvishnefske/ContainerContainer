package org.artifactory.rest.resource.system;

import org.artifactory.aol.AolService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.config.ConfigurationException;
import org.artifactory.throtlling.common.model.RateLimiterConfig;
import org.artifactory.throtlling.common.model.RateLimiterSettings;
import org.artifactory.throtlling.qrl.service.QueryRateLimiterService;
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
import static org.artifactory.rest.resource.system.QueryRateLimiterResource.QRL_NAME;
import static org.artifactory.rest.resource.system.QueryRateLimiterResource.RL_DELETED_SUCCESSFULLY;
import static org.artifactory.rest.resource.system.QueryRateLimiterResource.RL_UPDATED_SUCCESSFULLY;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.DEFAULT;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.LOW_PRIORITY;
import static org.artifactory.util.AolUtils.NOT_DASHBOARD_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class QueryRateLimiterResourceTest {

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AolService aolService;
    @Mock
    private QueryRateLimiterService qrlService;
    @InjectMocks
    private QueryRateLimiterResource qrlResource;
    private AutoCloseable mocks;
    private RateLimiterConfig qrlConfig;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        var defaultSettings = new RateLimiterSettings(DEFAULT, 10_000, 5_000, 2000);
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, 1_000, 1_000, 500);
        qrlConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        when(artifactoryContext.beanForType(AolService.class)).thenReturn(aolService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        verifyNoMoreInteractions(qrlService, aolService);
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testGetConfig() {
        try (Response response = qrlResource.getConfig()) {
            verify(qrlService, times(1)).getConfig();
            verify(aolService, times(1)).isAol();
            assertEquals(OK.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testGetConfigException() {
        doThrow(new ConfigurationException("cool exception")).when(qrlService).getConfig();
        try (Response response = qrlResource.getConfig()) {
            verify(qrlService, times(1)).getConfig();
            verify(aolService, times(1)).isAol();
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals("cool exception", response.getEntity());
        }
    }

    @ParameterizedTest
    @MethodSource("getConfigAol")
    void testGetConfigAol(boolean dashboardUser, int invocations, int expectedStatus, String expectedEntity) {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(dashboardUser);
        try (Response response = qrlResource.getConfig()) {
            verify(qrlService, times(invocations)).getConfig();
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
        try (Response response = qrlResource.postConfig(qrlConfig)) {
            verify(qrlService, times(1)).updateConfig(qrlConfig);
            verify(aolService, times(1)).isAol();
            assertEquals(OK.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testPostConfigException() {
        doThrow(new ConfigurationException("cool exception")).when(qrlService).updateConfig(qrlConfig);
        try (Response response = qrlResource.postConfig(qrlConfig)) {
            verify(qrlService, times(1)).updateConfig(qrlConfig);
            verify(aolService, times(1)).isAol();
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals("cool exception", response.getEntity());
        }
    }

    @ParameterizedTest
    @MethodSource("postConfigAol")
    void testPostConfigAol(boolean dashboardUser, int invocations, int expectedStatus, String expectedEntity) {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(dashboardUser);
        try (Response response = qrlResource.postConfig(qrlConfig)) {
            verify(qrlService, times(invocations)).updateConfig(qrlConfig);
            verify(aolService, times(1)).isAol();
            verify(aolService, times(1)).isDashboardUser();
            assertEquals(expectedStatus, response.getStatus());
            assertEquals(expectedEntity, response.getEntity());
        }
    }

    private static Stream<Arguments> postConfigAol() {
        return Stream.of(
                Arguments.of(true, 1, OK.getStatusCode(), QRL_NAME + RL_UPDATED_SUCCESSFULLY),
                Arguments.of(false, 0, BAD_REQUEST.getStatusCode(), NOT_DASHBOARD_USER)
        );
    }

    @Test
    void testDeleteConfig() {
        try (Response response = qrlResource.deleteConfig()) {
            verify(qrlService, times(1)).deleteConfig();
            verify(aolService, times(1)).isAol();
            assertEquals(OK.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void testDeleteConfigException() {
        doThrow(new ConfigurationException("cool exception")).when(qrlService).deleteConfig();
        try (Response response = qrlResource.deleteConfig()) {
            verify(qrlService, times(1)).deleteConfig();
            verify(aolService, times(1)).isAol();
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals("cool exception", response.getEntity());
        }
    }

    @ParameterizedTest
    @MethodSource("deleteConfigAol")
    void testDeleteConfigAol(boolean dashboardUser, int invocations, int expectedStatus, String expectedEntity) {
        when(aolService.isAol()).thenReturn(true);
        when(aolService.isDashboardUser()).thenReturn(dashboardUser);
        try (Response response = qrlResource.deleteConfig()) {
            verify(qrlService, times(invocations)).deleteConfig();
            verify(aolService, times(1)).isAol();
            verify(aolService, times(1)).isDashboardUser();
            assertEquals(expectedStatus, response.getStatus());
            assertEquals(expectedEntity, response.getEntity());
        }
    }

    private static Stream<Arguments> deleteConfigAol() {
        return Stream.of(
                Arguments.of(true, 1, OK.getStatusCode(), QRL_NAME + RL_DELETED_SUCCESSFULLY),
                Arguments.of(false, 0, BAD_REQUEST.getStatusCode(), NOT_DASHBOARD_USER)
        );
    }
}
