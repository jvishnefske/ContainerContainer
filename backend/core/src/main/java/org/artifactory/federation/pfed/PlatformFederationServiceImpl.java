package org.artifactory.federation.pfed;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.platformfederation.client.PlatformFederationClient;
import com.jfrog.platformfederation.client.PlatformFederationClientBuilder;
import com.jfrog.platformfederation.client.model.TokenInfoModel;
import com.jfrog.sysconf.SysConfig;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.Reloadable;
import org.artifactory.tenant.ArtifactoryDefaultTenantContextProvider;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.token.TokenRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.artifactory.common.ConstantValues.platformFederationFeatureEnabled;
import static org.artifactory.opentracing.ArtifactoryTracer.UBER_TRACE_ID;

/**
 * Default implementation of {@link PlatformFederationService}.
 * <p>
 * Wires the {@link PlatformFederationClient} the same way Access does in its {@code PlatformFederationConfig},
 * but using the Artifactory-side primitives (Access client for tokens, the artifactory server id for the
 * node id, the router port from the system config). The build is feature-flag gated so no gRPC/JFBus
 * connections are opened when the integration is off.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Reloadable(beanClass = PlatformFederationService.class, initAfter = {AccessService.class})
public class PlatformFederationServiceImpl implements PlatformFederationService {

    /**
     * The federation "service name" identifying Artifactory to the Platform Federation service.
     * Used for JFBus topic naming (serviceName.entityType).
     */
    private static final String PLATFORM_FEDERATION_SERVICE_NAME = "artifactory";

    /**
     * system.yaml key holding this JPD's SaaS technical server name, which is the tenant id the
     * (multi-tenant) Platform Federation service expects on every produced/consumed message.
     */
    private static final String TECHNICAL_SERVER_NAME_KEY = "shared.system.technicalServerName";

    private final AccessService accessService;

    private PlatformFederationClient platformFederationClient;

    @Override
    public void init() {
        if (platformFederationFeatureEnabled.getBoolean()) {
            platformFederationClient = createPlatformFederationClient();
            log.info("Platform Federation client has been created");
        }
    }

    /**
     * On SaaS this JPD is a single tenant within a multi-tenant Platform Federation cell,
     * so the technical server name must be stamped as the tenant id.
     * Otherwise, the client defaults to "default-tenant-id" and the federation service
     * drops the messages as non-PFED-enabled.
     */
    @NonNull
    @VisibleForTesting
    PlatformFederationClient createPlatformFederationClient() {
        PlatformFederationClientBuilder builder = PlatformFederationClient.builder()
                .serviceName(PLATFORM_FEDERATION_SERVICE_NAME)
                .serviceIdSupplier(() -> accessService.getArtifactoryServiceId().getFormattedName())
                .nodeId(ContextHelper.get().getServerId())
                .tokenProvider(this::createToken)
                .tenantProvider(new ArtifactoryDefaultTenantContextProvider(accessService))
                .routerPort(ArtifactoryHome.get().getSysConfig().helper().getRouterPort())
                .traceHeaderSupplier(() -> MDC.get(UBER_TRACE_ID));
        SysConfig sysConfig = ArtifactoryHome.get().getSysConfig();
        sysConfig.get(TECHNICAL_SERVER_NAME_KEY).ifPresent(builder::singleTenantTechnicalServerName);
        return builder.create();
    }

    @NonNull
    @VisibleForTesting
    TokenInfoModel createToken(@NonNull TokenRequest tokenRequest) {
        AccessClient accessClient = accessService.getAccessClient();
        String tokenValue = accessClient.token().create(tokenRequest).getTokenValue();
        Long expirationTimestampMs = accessClient.token().parse(tokenValue).getExpirationTime();
        return TokenInfoModel.builder()
                .tokenValue(tokenValue)
                .expirationTimestampMs(expirationTimestampMs)
                .build();
    }

    @NonNull
    @Override
    public Optional<PlatformFederationClient> getPlatformFederationClient() {
        return platformFederationFeatureEnabled.getBoolean() ? ofNullable(platformFederationClient) : empty();
    }

    @Override
    public void destroy() {
        if (nonNull(platformFederationClient)) {
            try {
                log.info("Closing the Platform Federation client");
                platformFederationClient.close();
            } catch (Exception e) {
                log.error("Failed to close the Platform Federation client", e);
            } finally {
                platformFederationClient = null;
            }
        }
    }
}
