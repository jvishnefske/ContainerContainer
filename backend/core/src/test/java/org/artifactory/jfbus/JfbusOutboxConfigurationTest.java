package org.artifactory.jfbus;

import org.artifactory.jfbus.outbox.ArtifactoryOutboxDatabaseBridge;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.eventing.jfbus.client.JfbusBrokerClient;
import org.jfrog.eventing.jfbus.client.JfbusOutboxFeature;
import org.jfrog.eventing.jfbus.outbox.bridge.OutboxAppBridgeProvider;
import org.jfrog.eventing.jfbus.outbox.bridge.SingleTenantAppBridgeProvider;
import org.jfrog.eventing.jfbus.outbox.processor.OutboxOperator;
import org.jfrog.eventing.jfbus.outbox.scheduler.mt.OutboxJobScheduler;
import org.jfrog.eventing.jfbus.outbox.scheduler.mt.OutboxJobSchedulerConfig;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class JfbusOutboxConfigurationTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;

    @Mock
    private DbService dbService;

    @Mock
    private ResultSet schemaResultSet;

    @Mock
    private RestClient jfbusRestClient;

    @Test
    void testOutboxAppBridgeProviderReturnsSingleTenantProvider() {
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);
        JfbusOutboxConfiguration config = new JfbusOutboxConfiguration();

        OutboxAppBridgeProvider provider = config.outboxAppBridgeProvider(jdbcHelper, dbService);

        assertInstanceOf(SingleTenantAppBridgeProvider.class, provider);
        assertInstanceOf(ArtifactoryOutboxDatabaseBridge.class,
                provider.getBridge("default-tenant-id").getDatabaseBridge());
    }

    @Test
    void testOutboxOperatorRegistersWithBrokerClientOutboxFeature() throws SQLException {
        stubJfbusRestClientMutate();
        when(jdbcHelper.executeSelectForSmallResults(anyString(), any(Object[].class))).thenReturn(schemaResultSet);
        when(schemaResultSet.next()).thenReturn(false);
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);

        JfbusOutboxConfiguration config = new JfbusOutboxConfiguration();
        OutboxAppBridgeProvider bridgeProvider = config.outboxAppBridgeProvider(jdbcHelper, dbService);
        JfbusBrokerClient brokerClient = mock(JfbusBrokerClient.class,
                withSettings().extraInterfaces(JfbusOutboxFeature.class));
        doAnswer(invocation -> {
            when(((JfbusOutboxFeature) brokerClient).isOutboxEnabled()).thenReturn(true);
            return null;
        }).when((JfbusOutboxFeature) brokerClient).setOutbox(any());

        OutboxOperator operator = config.outboxOperator(
                brokerClient, bridgeProvider, jfbusRestClient, 100);

        assertNotNull(operator);
        assertInstanceOf(JfbusOutboxFeature.class, brokerClient);
        assertTrue(((JfbusOutboxFeature) brokerClient).isOutboxEnabled(),
                "Expected outbox to be enabled after outboxOperator bean creation");
    }

    @Test
    void testOutboxOperatorThrowsWhenBrokerClientDoesNotSupportOutbox() throws SQLException {
        stubJfbusRestClientMutate();
        when(jdbcHelper.executeSelectForSmallResults(anyString(), any(Object[].class))).thenReturn(schemaResultSet);
        when(schemaResultSet.next()).thenReturn(false);
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);

        JfbusOutboxConfiguration config = new JfbusOutboxConfiguration();
        OutboxAppBridgeProvider bridgeProvider = config.outboxAppBridgeProvider(jdbcHelper, dbService);
        JfbusBrokerClient nonOutboxClient = mock(JfbusBrokerClient.class);

        assertThrows(IllegalStateException.class,
                () -> config.outboxOperator(nonOutboxClient, bridgeProvider, jfbusRestClient, 50));
    }

    @Test
    void testOutboxJobSchedulerConfigUsesSingleTenantDefaults() {
        JfbusOutboxConfiguration config = new JfbusOutboxConfiguration();

        OutboxJobSchedulerConfig schedulerConfig = config.outboxJobSchedulerConfig();

        assertNotNull(schedulerConfig);
        assertTrue("artifactory".equals(schedulerConfig.getNamespace()));
        assertTrue(SingleTenantAppBridgeProvider.DEFAULT_TENANT_ID.equals(schedulerConfig.getMetaTenantId()));
    }

    @Test
    void testOutboxJobSchedulerBeanCreation() throws SQLException {
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);
        when(jdbcHelper.executeSelectForSmallResults(anyString(), any(Object[].class))).thenReturn(schemaResultSet);
        when(schemaResultSet.next()).thenReturn(false);
        stubJfbusRestClientMutate();

        JfbusOutboxConfiguration config = new JfbusOutboxConfiguration();
        OutboxAppBridgeProvider bridgeProvider = config.outboxAppBridgeProvider(jdbcHelper, dbService);
        OutboxJobSchedulerConfig schedulerConfig = config.outboxJobSchedulerConfig();
        OutboxOperator outboxOperator = config.outboxOperator(
                mock(JfbusBrokerClient.class, withSettings().extraInterfaces(JfbusOutboxFeature.class)),
                bridgeProvider,
                jfbusRestClient,
                100);
        JfbusBrokerClient schedulerBrokerClient = mock(JfbusBrokerClient.class);
        OutboxJobScheduler scheduler = config.outboxJobScheduler(
                schedulerConfig,
                schedulerBrokerClient,
                bridgeProvider,
                outboxOperator);

        assertNotNull(scheduler);
    }

    @Test
    void testContextCreationListenerStartsScheduler() {
        JfbusOutboxConfiguration config = new JfbusOutboxConfiguration();
        OutboxJobScheduler scheduler = mock(OutboxJobScheduler.class);

        ContextCreationListener listener = config.outboxSchedulerContextCreationListener(scheduler);
        listener.onContextCreated();

        verify(scheduler).start();
    }

    @Test
    void testPartitionLifecycleContextCreationListenerStartsManager() {
        JfbusOutboxConfiguration config = new JfbusOutboxConfiguration();
        OutboxPartitionLifecycleManager lifecycleManager = mock(OutboxPartitionLifecycleManager.class);

        ContextCreationListener listener = config.outboxPartitionLifecycleContextCreationListener(lifecycleManager);
        listener.onContextCreated();

        verify(lifecycleManager).start();
    }

    @Test
    void testOutboxPartitionLifecycleManagerBeanCreation() {
        JfbusOutboxConfiguration config = new JfbusOutboxConfiguration();
        OutboxOperator outboxOperator = mock(OutboxOperator.class);

        OutboxPartitionLifecycleManager manager = config.outboxPartitionLifecycleManager(outboxOperator);

        assertNotNull(manager);
    }

    private void stubJfbusRestClientMutate() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(jfbusRestClient.mutate()).thenReturn(builder);
        when(builder.requestInterceptor(any())).thenReturn(builder);
        when(builder.messageConverters(any(Consumer.class))).thenReturn(builder);
        when(builder.build()).thenReturn(mock(RestClient.class));
    }
}
