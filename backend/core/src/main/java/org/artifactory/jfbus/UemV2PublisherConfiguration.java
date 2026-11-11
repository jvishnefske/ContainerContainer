package org.artifactory.jfbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.spring.ConditionalOnExpression;
import org.jfrog.eventing.facade.EventingFacade;
import org.jfrog.eventing.messagebus.unified.EventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the active UEM v2 publisher beans when JFBus is enabled and the UEM v2 publisher flag is on.
 * <p>
 * The condition is declared at the class level (mirroring {@link JfbusConfiguration} and the other
 * {@link ConditionalOnExpression} usages in the codebase). A method-level condition on the {@code @Bean}
 * methods is evaluated during the {@code REGISTER_BEAN} phase, which reaches the {@code autoConfigurationReport}
 * report bean and throws {@code BeanNotOfRequiredTypeException} in the full distribution, aborting context
 * refresh. The disabled fallback lives in {@link DisabledUemV2PublisherConfiguration}.
 */
@Configuration
@ConditionalOnExpression(UemV2PublisherConfiguration.ENABLED_EXPRESSION)
public class UemV2PublisherConfiguration {

    static final String ENABLED_EXPRESSION =
            "'${artifactory.jfbus.working.mode:OFF}' != 'OFF' && ${artifactory.uemv2.publisher.enabled:false}";

    @Bean
    public EventPublisher uemV2EventPublisher(EventingFacade eventingFacade) {
        // tenantIdSupplier is intentionally not wired: this matches the V1 path (JfbusEventingService
        // produces without a tenant), so UEM v2 is single-tenant for now. MT/SaaS tenant stamping should be
        // added here (and via the publishViaOutbox tenant overload) together with the V1 path when needed.
        return EventPublisher.builder()
                .facade(eventingFacade)
                .namespace(ArtifactoryHome.ARTIFACTORY_SERVICE_NAME)
                .build();
    }

    @Bean
    public UemV2Publisher uemV2Publisher(EventPublisher uemV2EventPublisher,
                                         JfbusEventingService jfbusEventingService) {
        return new UemV2PublisherImpl(uemV2EventPublisher, uemV2DataMapper(), jfbusEventingService);
    }

    /**
     * Mapper for the CloudEvents {@code data} payload. A vanilla {@code ObjectMapper} has no JSR-310 support, so
     * register the modules present on the classpath and emit dates as ISO-8601 strings (not numeric timestamps).
     * Deliberately not the canonical Artifactory mapper, whose internal mixins/inclusion rules are not part of
     * this external wire contract.
     */
    private static ObjectMapper uemV2DataMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
