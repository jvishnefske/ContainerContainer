package org.artifactory.jfbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.jfbus.outbox.ArtifactoryOutboxDatabaseBridge;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.ConditionalOnExpression;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.eventing.jfbus.client.JfbusBrokerClient;
import org.jfrog.eventing.jfbus.client.JfbusClientObjectMappers;
import org.jfrog.eventing.jfbus.client.JfbusOutboxFeature;
import org.jfrog.eventing.jfbus.outbox.TenantAwareEventsApi;
import org.jfrog.eventing.jfbus.outbox.bridge.OutboxAppBridge;
import org.jfrog.eventing.jfbus.outbox.bridge.OutboxAppBridgeProvider;
import org.jfrog.eventing.jfbus.outbox.bridge.SingleTenantAppBridgeProvider;
import org.jfrog.eventing.jfbus.outbox.dao.OutboxSqlConstants;
import org.jfrog.eventing.jfbus.outbox.processor.OutboxOperator;
import org.jfrog.eventing.jfbus.outbox.processor.OutboxOperatorConfig;
import org.jfrog.eventing.jfbus.producer.DefaultProducerRegistry;
import org.jfrog.eventing.jfbus.producer.ProducerRegistry;
import org.jfrog.eventing.jfbus.outbox.scheduler.mt.OutboxJobScheduler;
import org.jfrog.eventing.jfbus.outbox.scheduler.mt.OutboxJobSchedulerConfig;
import org.jfrog.jfbus.client.ApiClient;
import org.jfrog.jfbus.client.api.EventsApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.text.DateFormat;

/**
 * Defines JFBus outbox beans (database bridge, operator, and job scheduler).
 * <p>
 * Loaded only when both {@code artifactory.jfbus.working.mode != OFF} and
 * {@code artifactory.jfbus.use.outbox.enabled=true}. When the outbox toggle is false,
 * schema initialization, or outbox scheduler runs.
 *
 * @author IgorG
 */
@Slf4j
@Configuration
@ConditionalOnExpression("'${artifactory.jfbus.working.mode:OFF}' != 'OFF'" +
        " and '${artifactory.jfbus.use.outbox.enabled:true}' == 'true'")
public class JfbusOutboxConfiguration {

    /**
     * Tenant ID for outbox schema initialization. Null means single-tenant (one shared outbox table).
     * Passed to {@link OutboxOperator#initializeSchema(String)}.
     */
    private static final String SINGLE_TENANT_SCHEMA_TENANT_ID = null;

    @Bean
    public OutboxAppBridgeProvider outboxAppBridgeProvider(ArtifactoryJdbcHelper jdbcHelper, DbService dbService) {
        ArtifactoryOutboxDatabaseBridge bridge =
                new ArtifactoryOutboxDatabaseBridge(jdbcHelper, dbService.getDatabaseType());
        OutboxAppBridge appBridge = () -> bridge;
        return new SingleTenantAppBridgeProvider(appBridge);
    }

    @Bean
    public OutboxOperator outboxOperator(
            JfbusBrokerClient brokerClient,
            OutboxAppBridgeProvider outboxAppBridgeProvider,
            @Qualifier("jfbusRestClient") RestClient jfbusRestClient,
            @Value("${artifactory.jfbus.outbox.batch.size:100}") int batchSize) {

        EventsApi eventsApi = buildOutboxEventsApi(jfbusRestClient);
        OutboxOperatorConfig config = OutboxOperatorConfig.builder()
                .batchSize(batchSize)
                .tableName(OutboxSqlConstants.buildTableName("artifactory"))
                .build();

        log.info("Initializing JFBus outbox operator with batch size {}", batchSize);
        ProducerRegistry producerRegistry = new DefaultProducerRegistry(eventsApi, "artifactory");

        OutboxOperator operator = new OutboxOperator(eventsApi, config, producerRegistry, outboxAppBridgeProvider);
        operator.initializeSchema(SINGLE_TENANT_SCHEMA_TENANT_ID);

        if (!(brokerClient instanceof JfbusOutboxFeature outboxFeature)) {
            throw new IllegalStateException(
                    "JFBus outbox is enabled but broker client does not implement JfbusOutboxFeature: "
                            + brokerClient.getClass().getName());
        }
        outboxFeature.setOutbox(operator);
        log.info("JFBus outbox feature enabled");

        return operator;
    }

    @Bean
    public OutboxJobSchedulerConfig outboxJobSchedulerConfig() {
        return OutboxJobSchedulerConfig.builder()
                .namespace("artifactory")
                .metaTenantId(SingleTenantAppBridgeProvider.DEFAULT_TENANT_ID)
                .build();
    }

    @Bean(destroyMethod = "stop")
    public OutboxJobScheduler outboxJobScheduler(
            OutboxJobSchedulerConfig config,
            JfbusBrokerClient brokerClient,
            OutboxAppBridgeProvider outboxAppBridgeProvider,
            OutboxOperator outboxOperator) {
        return new OutboxJobScheduler(config, brokerClient, outboxAppBridgeProvider, outboxOperator);
    }

    @Bean(destroyMethod = "stop")
    public OutboxPartitionLifecycleManager outboxPartitionLifecycleManager(OutboxOperator outboxOperator) {
        long intervalMinutes = ConstantValues.jfbusOutboxPartitionLifecycleIntervalMinutes.getLong();
        return new OutboxPartitionLifecycleManager(outboxOperator, intervalMinutes);
    }

    @Bean
    public ContextCreationListener outboxPartitionLifecycleContextCreationListener(
            OutboxPartitionLifecycleManager lifecycleManager) {
        return lifecycleManager::start;
    }

    @Bean
    // Start scheduler after context creation so Access/JFBus dependencies are already initialized.
    public ContextCreationListener outboxSchedulerContextCreationListener(OutboxJobScheduler scheduler) {
        return scheduler::start;
    }

    private static EventsApi buildOutboxEventsApi(RestClient jfbusRestClient) {
        DateFormat dateFormat = ApiClient.createDefaultDateFormat();
        ObjectMapper objectMapper = JfbusClientObjectMappers.createApiMapper(dateFormat);
        RestClient outboxRestClient = jfbusRestClient.mutate()
                .requestInterceptor(new TenantAwareEventsApi.TenantHeaderInterceptor())
                .messageConverters(converters ->
                        converters.addFirst(new MappingJackson2HttpMessageConverter(objectMapper)))
                .build();
        ApiClient apiClient = new ApiClient(outboxRestClient, objectMapper, dateFormat);
        apiClient.setBasePath("/");
        return new TenantAwareEventsApi(apiClient);
    }
}
