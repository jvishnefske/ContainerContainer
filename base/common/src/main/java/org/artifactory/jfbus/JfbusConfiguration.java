package org.artifactory.jfbus;

import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.http.AccessTokenSupplier;
import org.artifactory.http.ArtifactoryTracerHeaderInjector;
import org.artifactory.http.AuthorizationHeaderInterceptor;
import org.artifactory.http.TracingHttpRequestInterceptor;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ConditionalOnExpression;
import org.artifactory.tenant.ArtifactoryDefaultTenantContextProvider;
import org.jfrog.client5.http.HttpBuilder;
import org.jfrog.eventing.facade.EventingFacade;
import org.jfrog.eventing.facade.impl.jfbus.JfbusEventingFacade;
import org.jfrog.eventing.jfbus.client.JfbusBrokerClient;
import org.jfrog.eventing.jfbus.client.JfbusBrokerRestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static java.util.Objects.requireNonNull;
import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;

/**
 * @author IgorG
 */
@Slf4j
@Configuration
@ConditionalOnExpression("'${artifactory.jfbus.working.mode:OFF}' != 'OFF'")
public class JfbusConfiguration {
    public static final String JFBUS_SERVICE_NAME = "jfbus";
    private static final String JFBUS_SERVICE_PATH = "/" + JFBUS_SERVICE_NAME + "/";
    private static final String INTERNAL_JFBUS_SCOPE = "internal:event-stream:x";
    private static final String JFBUS_AUDIENCE = JFBUS_SERVICE_NAME + "@*";
    private final String brokerUrl;
    private final ArtifactoryDefaultTenantContextProvider artifactoryDefaultTenantContextProvider;
    private final AccessService accessService;

    JfbusConfiguration(
            AccessService accessService,
            CentralConfigService configService,
            @Value("${artifactory.jfbus.broker.url:}") String brokerUrl
    ) {
        this.brokerUrl = !Strings.isNullOrEmpty(brokerUrl) ? brokerUrl :
                requireNonNull(configService.getSystemConfiguration().getRouterUrl(), "routerUrl is required")
                        + JFBUS_SERVICE_PATH;
        this.accessService = accessService;
        artifactoryDefaultTenantContextProvider = new ArtifactoryDefaultTenantContextProvider(accessService);
        log.info("JFBus broker URL set to {}", this.brokerUrl);
    }

    @Bean
    public EventingFacade jfbusEventingClient(JfbusBrokerClient brokerClient) {
        return JfbusEventingFacade.builder()
                .brokerClient(brokerClient)
                .tenantContextProvider(artifactoryDefaultTenantContextProvider)
                .callerUserAgent(ConstantValues.jfrogUserAgentDefaultURL.getString())
                .serviceName(ARTIFACTORY_SERVICE_NAME)
                .build();
    }

    @Bean
    public RestClient jfbusRestClient(
            @Value("${artifactory.jfbus.access.token.expiration.time.millis:7200000}") long accessTokenExpirationMillis,
            @Value("${artifactory.jfbus.broker.max.http.connections:100}") int maxHttpConnections,
            @Value("${artifactory.jfbus.broker.max.per.route:20}") int maxPerRoute
    ) {
        AccessTokenSupplier tokenSupplier = new AccessTokenSupplier(
                accessService::getAccessClient,
                accessTokenExpirationMillis,
                JFBUS_AUDIENCE,
                INTERNAL_JFBUS_SCOPE
        );
        HttpRequestInterceptor authInterceptor = new AuthorizationHeaderInterceptor(tokenSupplier);

        ArtifactoryTracer artifactoryTracer = ContextHelper.get().beanForType(ArtifactoryTracer.class);
        HttpRequestInterceptor tracingInterceptor = new TracingHttpRequestInterceptor(
                new ArtifactoryTracerHeaderInjector(artifactoryTracer));
        CloseableHttpClient httpClient5 = new HttpBuilder()
                .maxTotalConnections(maxHttpConnections)
                .maxConnectionsPerRoute(maxPerRoute)
                .addRequestInterceptor(authInterceptor)
                .addRequestInterceptor(tracingInterceptor)
                .build();

        return RestClient.builder()
                .baseUrl(brokerUrl)
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient5))
                .build();
    }

    @Bean
    public JfbusBrokerClient jfbusBrokerClient(RestClient jfbusRestClient) {
        return new JfbusBrokerRestClient(jfbusRestClient);
    }
}
