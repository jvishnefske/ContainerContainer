package org.artifactory.servicetrust;

import lombok.NonNull;
import org.apache.commons.lang3.NotImplementedException;
import org.artifactory.api.security.access.TokenSpec;
import org.jfrog.access.common.ServiceId;

import java.util.Optional;

import static org.artifactory.servicetrust.ServiceTrustService.INTERNAL_SCOPE_PREFIX;

/**
 * @author igoru
 */
public interface TokenSpecInboundService {

    /**
     * Create the specification for creating the paring token for this Artifactory instance
     */
    TokenSpec createPairingTokenSpec(@NonNull ServiceTrustTokenData tokenData, @NonNull ServiceTrust trust,
            @NonNull ServiceTrustUrls urls);

    /**
     * Create the specification for creating the Master token for this Artifactory instance
     */
    TokenSpec createMasterTokenSpec(ServiceTrustTokenData tokenData, String useCasePath, ServiceTrustUrls urls);

    @NonNull
    default String createRequiredInternalScope(@NonNull String useCasePath) {
        return INTERNAL_SCOPE_PREFIX + useCasePath;
    }

    /**
     * Create the specification for creating the scope token for this Artifactory instance
     */
    TokenSpec createExchangeTokenSpec(ServiceId artifactoryServiceId, String useCasePath);

    default void deleteAuthenticationToken(String baseUrl) {
        throw new NotImplementedException();
    }

    default Optional<String> getAuthenticationToken(String baseUrl) {
        throw new NotImplementedException();
    }

    /**
     * Controls whether forced unpairing is allowed for this service.
     *
     * <p>Used together with a user's 'force=true' parameter to enable unpairing even when
     * the remote service is unavailable or returns an error. Both this method AND the user
     * must explicitly allow forced unpairing for it to proceed.</p>
     *
     * <p>Example: Cold Storage prohibits forced unpairing when Archive Policies are active
     * to protect data integrity.</p>
     *
     * @return {@code true} to allow forced unpairing, {@code false} to prohibit it
     */
    default boolean isForcedUnpairingAllowed() {
        return false;
    }
}
