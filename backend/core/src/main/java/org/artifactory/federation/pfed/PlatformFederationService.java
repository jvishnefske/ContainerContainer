package org.artifactory.federation.pfed;

import com.jfrog.platformfederation.client.PlatformFederationClient;
import lombok.NonNull;
import org.artifactory.spring.ReloadableBean;

import java.util.Optional;

/**
 * Manages the lifecycle of the {@link PlatformFederationClient}.
 * <p>
 * The client is created on {@link #init()} (after the Access service is ready) and closed on {@link #destroy()}.
 * <p>
 * The interface is separated from the implementation because of the {@code ReloadableBean} processing.
 * See {@code ArtifactoryApplicationContext#orderReloadableBeans} for the details.
 */
public interface PlatformFederationService extends ReloadableBean {

    /**
     * Returns the managed {@link PlatformFederationClient}, gated on the feature flag.
     * <p>
     * This is a cheap, local check (feature flag + client existence).
     * It does NOT perform a network readiness probe against the Platform Federation service,
     * callers that need readiness should use {@link PlatformFederationClient#isReady}.
     */
    @NonNull
    Optional<PlatformFederationClient> getPlatformFederationClient();
}
