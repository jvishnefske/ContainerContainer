package org.artifactory.rest.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.jfrog.test.log.MemoryAppender;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.shadow.ShadowMetricRegistry;
import org.artifactory.shadow.ShadowRequestContext;
import org.artifactory.shadow.ShadowRequestService;
import org.artifactory.shadow.ShadowResponseType;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.util.HttpClientConfigurator;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.internal.process.Endpoint;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;
import org.jfrog.client.http.CloseableObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;

import static org.artifactory.common.ConstantValues.shadowingBackoffLimit;
import static org.artifactory.common.ConstantValues.shadowingEnabled;
import static org.artifactory.common.ConstantValues.shadowingProbabilityFactor;
import static org.artifactory.common.ConstantValues.vcsShadowingSupport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShadowResponseFilterTest extends ArtifactoryHomeBoundJUnitTest {

    private AutoCloseable mocks;

    private final ArtifactoryHomeStub homeStub = getOrCreateArtifactoryHomeStub();

    ShadowResponseFilter shadowResponseFilter;

    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    @Mock
    private RepositoryService repositoryService;

    MemoryAppender memoryAppender;

    private final static String SERVICE_LOGGER_NAME = "org.artifactory.shadow.ShadowRequestService";
    private final static String FILTER_LOGGER_NAME = "org.artifactory.rest.common.ShadowResponseFilter";

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        homeStub.setProperty(ConstantValues.artifactoryProductVersion,
                ArtifactoryServiceVersion.getCurrent().getVersion());
        homeStub.setProperty(shadowingEnabled.getPropertyName(), "true");
        homeStub.setProperty(vcsShadowingSupport.getPropertyName(), "true");
        homeStub.setProperty(shadowingProbabilityFactor.getPropertyName(), "1");
        homeStub.setProperty(shadowingBackoffLimit.getPropertyName(), "2");
        shadowResponseFilter = spy(new ShadowResponseFilter());
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(ContextHelper.get().beanForType(IdleConnectionMonitorService.class)).thenReturn(
                mock(IdleConnectionMonitorServiceForTest.class));
        when(ContextHelper.get().beanForType(DenyListCacheHolderService.class)).thenReturn(
                mock(DenyListCacheHolderService.class));
        setupLoggingAssertions();
        shadowResponseFilter.setRepositoryService(repositoryService);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
        homeStub.setProperty(shadowingEnabled.getPropertyName(),
                shadowingEnabled.getDefValue());
        homeStub.setProperty(vcsShadowingSupport.getPropertyName(),
                vcsShadowingSupport.getDefValue());
        homeStub.setProperty(shadowingProbabilityFactor.getPropertyName(),
                shadowingProbabilityFactor.getDefValue());
    }

    @Test
    void testShadowFilter() throws IOException {
        // Ensure VCS shadowing is enabled (may have been reset by previous test's tearDown)
        homeStub.setProperty(vcsShadowingSupport.getPropertyName(), "true");
        // Stub isTimeToShadow() to always return true, bypassing probability logic
        doReturn(true).when(shadowResponseFilter).isTimeToShadow();

        ShadowMetricRegistry shadowMetricRegistry = mock(ShadowMetricRegistry.class);
        doNothing().when(shadowMetricRegistry).inc(any(), any());

        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");

        // Mock executor service to run submitted tasks immediately (same pattern as testBackoff)
        ExecutorService executorService = mock(ExecutorService.class);
        when(shadowResponseFilter.getExecutorService()).thenReturn(executorService);
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocationOnMock -> {
            Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return null;
        });

        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getHeaders()).thenReturn(new MultivaluedHashMap<>());
        when(request.getRequestUri()).thenReturn(uri);
        UriRoutingContext uriInfo = mock(UriRoutingContext.class);
        Endpoint endpoint = mock(Endpoint.class);
        when(endpoint.toString()).thenReturn("endpoint");
        when(uriInfo.getEndpoint()).thenReturn(endpoint);
        when(request.getUriInfo()).thenReturn(uriInfo);

        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getStatus()).thenReturn(200);
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add("Some-Header", "Some-Value");
        when(response.getHeaders()).thenReturn(headers);
        when(response.getHeaderString("Some-Header")).thenReturn("Some-Value");

        // Check that shadowing shouldn't work for DELETE method
        when(request.getMethod()).thenReturn(HttpMethod.DELETE);
        shadowResponseFilter.filter(request, response);

        when(request.getMethod()).thenReturn(HttpMethod.GET);

        try (CloseableHttpResponse shadowResponse = mock(CloseableHttpResponse.class);
                CloseableHttpClient httpClient = mock(CloseableHttpClient.class)) {
            ShadowRequestService shadowRequestService = spy(new ShadowRequestService(shadowMetricRegistry, httpClient));
            when(shadowResponseFilter.getShadowRequestService()).thenReturn(shadowRequestService);

            when(shadowResponse.getStatusLine()).thenReturn(mock(StatusLine.class));
            when(shadowResponse.getStatusLine().getStatusCode()).thenReturn(200);
            Header[] phHeaders = new Header[] {new BasicHeader("Some-Header", "Some-Another-Value"),
                    new BasicHeader(HttpHeaders.CONTENT_LENGTH, "0")};
            when(shadowResponse.getAllHeaders()).thenReturn(phHeaders);
            when(shadowResponse.toString()).thenReturn("ShadowResponse");
            when(httpClient.execute(any())).thenReturn(shadowResponse);

            try (MockedConstruction<HttpClientConfigurator> ignored = Mockito.mockConstruction(
                    HttpClientConfigurator.class,
                    (mock, context) -> {
                        when(mock.socketTimeout(anyInt())).thenReturn(mock);
                        when(mock.connectionTimeout(anyInt())).thenReturn(mock);
                        when(mock.maxTotalConnections(anyInt())).thenReturn(mock);
                        when(mock.build()).thenReturn(httpClient);
                    })) {
                shadowResponseFilter.filter(request, response);

                assertEquals(2, memoryAppender.countEventsForLogger(SERVICE_LOGGER_NAME));
                assertEquals(1, memoryAppender.countEventsForLogger(FILTER_LOGGER_NAME));
                assertEquals(1, memoryAppender.search("method DELETE is not supported, skipping", Level.TRACE).size());
                assertEquals(1, memoryAppender.search("ShadowResponse", Level.DEBUG).size());
                assertEquals(1, memoryAppender.search(
                        "Header content expected: 'Some-Value', header content actual: 'Some-Another-Value'",
                        Level.WARN).size());

                // Verify Content-Length header removal
                assertFalse(response.getHeaders().containsKey(HttpHeaders.CONTENT_LENGTH),
                        "Content-Length should be removed from original headers.");

                // Now let's test with non-equivalent status code
                when(shadowResponse.getStatusLine().getStatusCode()).thenReturn(404);
                memoryAppender.reset();

                shadowResponseFilter.filter(request, response);

                assertEquals(2, memoryAppender.countEventsForLogger(SERVICE_LOGGER_NAME));
                assertEquals(1, memoryAppender.search("ShadowResponse", Level.DEBUG).size());
                assertEquals(1,
                        memoryAppender.search("Status expected: '200', actual: '404'", Level.WARN).size());
            }
        }
    }

    @Test
    void testShadowFilterConstructor() {
        assertNotNull(shadowResponseFilter.getExecutorService());
    }

    @Test
    void testShadowFilterRequestService() {
        assertNull(shadowResponseFilter.getShadowRequestService());
    }

    @Test
    void testProbabilityFactor() {
        homeStub.setProperty(shadowingProbabilityFactor.getPropertyName(), "1");
        ShadowResponseFilter shadowResponseFilter = new ShadowResponseFilter();
        assertTrue(shadowResponseFilter.isTimeToShadow());

        homeStub.setProperty(shadowingProbabilityFactor.getPropertyName(), "5");
        shadowResponseFilter = new ShadowResponseFilter();
        assertFalse(shadowResponseFilter.isTimeToShadow());

        homeStub.setProperty(shadowingProbabilityFactor.getPropertyName(), "10001");
        shadowResponseFilter = new ShadowResponseFilter();

        // It becomes 0 after 10_000
        for (int i = 1; i <= 10_000; i++) {
            shadowResponseFilter.isTimeToShadow();
        }

        assertFalse(shadowResponseFilter.isTimeToShadow());
    }

    @Test
    void testBackoff() {
        doReturn(true).when(shadowResponseFilter).isTimeToShadow();
        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");
        ExecutorService executorService = mock(ExecutorService.class);
        when(shadowResponseFilter.getExecutorService()).thenReturn(executorService);
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocationOnMock -> {
            Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return null;
        });
        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getRequestUri()).thenReturn(uri);
        UriRoutingContext uriInfo = mock(UriRoutingContext.class);
        Endpoint endpoint = mock(Endpoint.class);
        when(endpoint.toString()).thenReturn("endpoint2");
        when(uriInfo.getEndpoint()).thenReturn(endpoint);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        ShadowRequestService shadowRequestService = mock(ShadowRequestService.class);
        when(shadowRequestService.executeShadow(any(), any())).thenReturn(ShadowResponseType.FAILED);
        when(shadowResponseFilter.getShadowRequestService()).thenReturn(shadowRequestService);
        shadowResponseFilter.filter(request, null);
        shadowResponseFilter.filter(request, null);
        verify(shadowRequestService, times(2)).executeShadow(any(), any());
        //api should be blocked after the second execution
        shadowResponseFilter.filter(request, null);
        verify(shadowRequestService, times(2)).executeShadow(any(), any());
        assertEquals(1,
                memoryAppender.search("Shadowing is blocked for endpoint endpoint2 and repoType null", Level.DEBUG)
                        .size());
        when(endpoint.toString()).thenReturn("endpoint3");
        shadowResponseFilter.filter(request, null);
        verify(shadowRequestService, times(3)).executeShadow(any(), any());
        assertEquals(1, memoryAppender.countEventsForLogger(FILTER_LOGGER_NAME));
    }

    @Test
    void testShouldSkipShadowEndpointNull() {
        doReturn(true).when(shadowResponseFilter).isTimeToShadow();
        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");
        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getUriInfo()).thenReturn(mock(UriRoutingContext.class));
        when(request.getRequestUri()).thenReturn(uri);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        ShadowRequestService shadowRequestService = mock(ShadowRequestService.class);
        when(shadowRequestService.executeShadow(any(), any())).thenReturn(ShadowResponseType.FAILED);
        when(shadowResponseFilter.getShadowRequestService()).thenReturn(shadowRequestService);
        shadowResponseFilter.filter(request, null);
        verify(shadowRequestService, never()).executeShadow(any(), any());
        assertEquals(1, memoryAppender.search("Endpoint is not exists.Skipping shadow", Level.TRACE).size());
    }

    @Test
    void testBackoffConsiderRepoType() {
        doReturn(true).when(shadowResponseFilter).isTimeToShadow();
        URI localUri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local/test");
        URI remoteUri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-remote/test");
        ExecutorService executorService = mock(ExecutorService.class);
        when(shadowResponseFilter.getExecutorService()).thenReturn(executorService);
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocationOnMock -> {
            Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return null;
        });
        RepoConfig localRepoConfig = mock(RepoConfig.class);
        when(localRepoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        RepoConfig remoteRepoConfig = mock(RepoConfig.class);
        when(remoteRepoConfig.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repositoryService.getRepoConfigByKey("vcs-local")).thenReturn(localRepoConfig);
        when(repositoryService.getRepoConfigByKey("vcs-remote")).thenReturn(remoteRepoConfig);

        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getRequestUri()).thenReturn(localUri).thenReturn(remoteUri);
        when(request.getProperty("shadowRepoType")).thenReturn("local").thenReturn("remote");
        UriRoutingContext uriInfo = mock(UriRoutingContext.class);
        Endpoint endpoint = mock(Endpoint.class);
        when(endpoint.toString()).thenReturn("endpoint2");
        when(uriInfo.getEndpoint()).thenReturn(endpoint);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        ShadowRequestService shadowRequestService = mock(ShadowRequestService.class);
        ArgumentCaptor<ShadowRequestContext> captor = ArgumentCaptor.forClass(ShadowRequestContext.class);
        when(shadowRequestService.executeShadow(any(), any())).thenReturn(ShadowResponseType.NOT_IMPLEMENTED);
        when(shadowResponseFilter.getShadowRequestService()).thenReturn(shadowRequestService);
        homeStub.setProperty(shadowingBackoffLimit.getPropertyName(), "1");
        shadowResponseFilter.filter(request, null);
        verify(shadowRequestService, times(1)).executeShadow(captor.capture(), any());
        Assertions.assertEquals("local", captor.getValue().repoType());
        clearInvocations(shadowRequestService);
        shadowResponseFilter.filter(request, null);
        verify(shadowRequestService, times(1)).executeShadow(captor.capture(), any());
        Assertions.assertEquals("remote", captor.getValue().repoType());
        assertEquals(0, memoryAppender.countEventsForLogger(FILTER_LOGGER_NAME));
        when(request.getRequestUri()).thenReturn(localUri).thenReturn(remoteUri);
        when(request.getProperty("shadowRepoType")).thenReturn("local").thenReturn("remote");
        shadowResponseFilter.filter(request, null);
        shadowResponseFilter.filter(request, null);
        assertEquals(1,
                memoryAppender.search("Shadowing is blocked for endpoint endpoint2 and repoType local", Level.DEBUG)
                        .size());
        assertEquals(1,
                memoryAppender.search("Shadowing is blocked for endpoint endpoint2 and repoType remote", Level.DEBUG)
                        .size());
    }

    @Test
    void testBackoffWithNonImplementedApi() {
        doReturn(true).when(shadowResponseFilter).isTimeToShadow();
        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");
        ExecutorService executorService = mock(ExecutorService.class);
        when(shadowResponseFilter.getExecutorService()).thenReturn(executorService);
        when(executorService.submit(any(Runnable.class))).thenAnswer(invocationOnMock -> {
            Runnable runnable = invocationOnMock.getArgument(0);
            runnable.run();
            return null;
        });
        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getRequestUri()).thenReturn(uri);
        UriRoutingContext uriInfo = mock(UriRoutingContext.class);
        Endpoint endpoint = mock(Endpoint.class);
        when(endpoint.toString()).thenReturn("endpoint2");
        when(uriInfo.getEndpoint()).thenReturn(endpoint);
        when(request.getUriInfo()).thenReturn(uriInfo);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        ShadowRequestService shadowRequestService = mock(ShadowRequestService.class);
        when(shadowRequestService.executeShadow(any(), any())).thenReturn(ShadowResponseType.NOT_IMPLEMENTED);
        when(shadowResponseFilter.getShadowRequestService()).thenReturn(shadowRequestService);
        shadowResponseFilter.filter(request, null);
        verify(shadowRequestService, times(1)).executeShadow(any(), any());
        //api should be blocked after the first execution
        shadowResponseFilter.filter(request, null);
        verify(shadowRequestService, times(1)).executeShadow(any(), any());
        assertEquals(1,
                memoryAppender.search("Shadowing is blocked for endpoint endpoint2 and repoType null", Level.DEBUG)
                        .size());
        when(endpoint.toString()).thenReturn("endpoint3");
        shadowResponseFilter.filter(request, null);
        verify(shadowRequestService, times(2)).executeShadow(any(), any());
        assertEquals(1, memoryAppender.countEventsForLogger(FILTER_LOGGER_NAME));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/artifactory/vcs-local",
            "http://localhost:8080/artifactory/api/shadow/vcs/vcs-local"})
    void testSkipShadowRequestDueToMissingPackage(String urlPath) {
        doReturn(true).when(shadowResponseFilter).isTimeToShadow();
        URI uri = URI.create(urlPath);
        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getRequestUri()).thenReturn(uri);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        shadowResponseFilter.filter(request, null);
        assertEquals(1,
                memoryAppender.search("Shadow filter: requestUrl " + urlPath + " is not a legacy API url, skipping",
                        Level.TRACE).size());
    }

    @Test
    void testSkipShadowRequestDueToMissingProperty() {
        doReturn(true).when(shadowResponseFilter).isTimeToShadow();
        URI uri = URI.create("http://localhost:8080/artifactory/api/npm/npm-local");
        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getRequestUri()).thenReturn(uri);
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        shadowResponseFilter.filter(request, null);
        assertEquals(1, memoryAppender.search("Shadowing is disabled for package type npm", Level.TRACE).size());
    }

    private void setupLoggingAssertions() {
        memoryAppender = new MemoryAppender();
        memoryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(SERVICE_LOGGER_NAME);
        serviceLogger.setLevel(Level.DEBUG);
        serviceLogger.addAppender(memoryAppender);

        Logger filterLogger = (Logger) LoggerFactory.getLogger(FILTER_LOGGER_NAME);
        filterLogger.setLevel(Level.TRACE);
        filterLogger.addAppender(memoryAppender);

        memoryAppender.start();
    }

    public interface IdleConnectionMonitorServiceForTest extends CloseableObserver, IdleConnectionMonitorService {
    }
}