package org.artifactory.sh.config;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.federation.FederationClientConfigurationService;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.repo.FederationClientService;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.util.HttpClientConfigurator;
import org.jfrog.client.http.model.ProxyConfig;
import org.jfrog.rtfs.ArtifactoryFederationClient;
import org.jfrog.storage.JdbcHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.artifactory.concurrent.ArtifactoryExecutorFactory.newFixedThreadPool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author TamirH
 */
class BinaryStoreConfigServiceImplTest {

    @Test
    void createContextBinderFunc() throws InterruptedException {
        ArtifactoryStorageContext context = mock(ArtifactoryStorageContext.class);
        ArtifactoryTracer artifactoryTracer = mock(ArtifactoryTracer.class);
        BinaryStoreConfigServiceImpl binaryStoreConfigService =
                new BinaryStoreConfigServiceImpl(mock(JdbcHelper.class), artifactoryTracer, mock(
                        ApplicationEventPublisher.class));
        binaryStoreConfigService.setAccessService(mock(AccessService.class));
        binaryStoreConfigService.setInternalDbService(mock(InternalDbService.class));
        when(context.beanForType(ArtifactoryTracer.class)).thenReturn(artifactoryTracer);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryExecutor artifactoryExecutor = newFixedThreadPool(1, context, false);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            artifactoryExecutor.submit(() -> {
                //contextBinderFunc.bind();
                assertEquals(context, ContextHelper.get());
                latch.countDown();
            });
            latch.await();
        } finally {
            ArtifactoryContextThreadBinder.unbind();
            artifactoryExecutor.shutdownNow();

        }
    }

    @Test
    void deletePruneStopMarker() throws StorageException {
        Map.of(0, false, 1, true).forEach((key, value) -> {
            int deleteConfigReturnValue = key;
            boolean expectedResult = value;
            try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
                contextHelper.when(ContextHelper::get)
                        .thenAnswer(invocation -> {
                            ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
                            when(artifactoryContext.beanForType(any())).thenAnswer(x -> {
                                ConfigsService configsService = mock(ConfigsService.class);
                                when(configsService.deleteConfig(anyString())).thenReturn(deleteConfigReturnValue);
                                return configsService;
                            });
                            return artifactoryContext;
                        });
                BinaryStoreConfigServiceImpl binaryStoreConfigService =
                        new BinaryStoreConfigServiceImpl(null, null, null);
                assertEquals(expectedResult, binaryStoreConfigService.deletePruneStopMarker());
            }
        });
    }

    @Test
    void updatePruneReport() throws StorageException {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get)
                    .thenAnswer(invocation -> {
                        ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
                        when(artifactoryContext.beanForType(any())).thenAnswer(x -> {
                            ConfigsService configsService = mock(ConfigsService.class);
                            when(configsService.addOrUpdateConfig(any(ConfigsServiceKey.class), any(), anyLong()))
                                    .thenReturn(true);
                            return configsService;
                        });
                        return artifactoryContext;
                    });
            BinaryStoreConfigServiceImpl binaryStoreConfigService =
                    new BinaryStoreConfigServiceImpl(null, null, null);
            try {
                binaryStoreConfigService.updatePruneReport(null);
            } catch (StorageException e) {
                fail("Should not have failed", e);
            }
        }
    }

    @Test
    void getFederationClient() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get)
                    .thenAnswer(invocation -> {
                        ArtifactoryContext context = mock(ArtifactoryContext.class);
                        FederationClientService clientService = mock(FederationClientService.class);
                        when(context.beanForType(FederationClientService.class)).thenReturn(clientService);
                        ArtifactoryFederationClient federationClient = mock(ArtifactoryFederationClient.class);
                        when(clientService.getFederationClient()).thenReturn(federationClient);
                        return context;
                    });
            BinaryStoreConfigServiceImpl binaryStoreConfigService =
                    new BinaryStoreConfigServiceImpl(null, null, null);
            assertNotNull(binaryStoreConfigService.getFederationClient());
        }
    }

    @Test
    void shouldUseFederationV2() {
        BinaryStoreConfigServiceImpl binaryStoreConfigService =
                new BinaryStoreConfigServiceImpl(null, null, null);
        var fedClientConfigService = mock(FederationClientConfigurationService.class);
        binaryStoreConfigService.setFederatedRepoConfigurationService(fedClientConfigService);
        when(fedClientConfigService.shouldUseFederationBinaryProviderV2()).thenReturn(true);
        assertTrue(binaryStoreConfigService.shouldUseFederationV2());
    }

    @Test
    void testCreateClosableHttpClient() {
        BinaryStoreConfigServiceImpl x = new BinaryStoreConfigServiceImpl(null, null, null);

        // Create a test subclass of HttpClientConfigurator that we can track
        HttpClientConfigurator mockConfigurator = mock(HttpClientConfigurator.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);

        // Setup the mock behavior
        when(mockConfigurator.maxConnectionsPerRoute(123)).thenReturn(mockConfigurator);
        when(mockConfigurator.build()).thenReturn(mockClient);

        // Act
        // Use the map-based version which we can test without modifying the class
        // Connection timeout will be tested below.
        Map<String, String> clientParams = new HashMap<>();
        clientParams.put("maxConnectionsPerRoute", "123");
        clientParams.put("maxConnectionsTotal", "12LaLaLa3");
        clientParams.put("socketTimeout", null);

        // Use a custom HttpClientConfigurator with reflection
        try (MockedConstruction<HttpClientConfigurator> httpClientConfigMockedCtr =
                     Mockito.mockConstruction(HttpClientConfigurator.class,
                             (mock, context) -> {
                                 // Return our prepared mock for any calls
                                 when(mock.maxConnectionsPerRoute(anyInt())).thenReturn(mockConfigurator);
                                 when(mock.maxTotalConnections(anyInt())).thenReturn(mockConfigurator);
                                 when(mock.connectionTimeout(anyInt())).thenReturn(mockConfigurator);
                                 when(mock.socketTimeout(anyInt())).thenReturn(mockConfigurator);
                                 when(mock.build(anyBoolean())).thenReturn(mockClient);
                             })) {

            ProxyConfig proxy = mock(ProxyConfig.class);
            when(proxy.getHost()).thenReturn("proxyHost");

            List<String> connectionTimeouts = List.of("", "-10", "-1", "0", "1", "2");
            for (int i = 0; i < connectionTimeouts.size(); i++) {
                // Set current connection timeout
                String connectionTimeout = connectionTimeouts.get(i);
                clientParams.put("connectionTimeout", connectionTimeout);
                testHttpClientCall(clientParams, x, proxy, mockClient);
                testHttpClientConfig(connectionTimeout, i, proxy, httpClientConfigMockedCtr);
            }
        }
    }

    private static void testHttpClientCall(Map<String, String> clientParams, BinaryStoreConfigServiceImpl x,
            ProxyConfig proxy, CloseableHttpClient mockClient) {
        CloseableHttpClient result = x.createClosableHttpClient(clientParams, proxy, true);
        assertNotNull(result);
        assertEquals(mockClient, result);
    }

    private static void testHttpClientConfig(String connectionTimeout, int iteration,
            ProxyConfig proxy, MockedConstruction<HttpClientConfigurator> httpClientConfigMockedCtr) {

        HttpClientConfigurator clientConfigurator = httpClientConfigMockedCtr.constructed().get(iteration);
        // The configurations below should not be called since they aren't have a valid value
        verify(clientConfigurator, never()).maxTotalConnections(anyInt());
        verify(clientConfigurator, never()).socketTimeout(anyInt());

        verify(clientConfigurator).maxConnectionsPerRoute(123);
        int iConnectionTimeout = NumberUtils.toInt(connectionTimeout, Integer.MIN_VALUE);
        if (iConnectionTimeout > 0) {
            verify(clientConfigurator).connectionTimeout(iConnectionTimeout);
        } else {
            verify(clientConfigurator, never()).connectionTimeout(anyInt());
        }

        verify(clientConfigurator).proxy(proxy);
        verify(clientConfigurator).build(true);
    }
}