package org.artifactory.federation;

import org.artifactory.federated.service.FederationService;

/**
 * This class acts as a proxy for the correct federation service based on the migration status.
 * <p>
 * It uses the template methods `getRtfsService()` and `getLegacyService()` which are implemented by the subclasses to
 * provide concrete instances of services.
 * Also, inheriting classes must implement the `shouldUseFederationService()` method to determine which service to use.
 * @author Tamar Rosen
 * @author Bilal Issa
 */
public abstract class FederationServiceProxy<T extends FederationService> {

    T rtfsService;
    T legacyService;

    protected FederationServiceProxy(T rtfsService, T legacyService) {
        this.rtfsService = rtfsService;
        this.legacyService = legacyService;
    }

    protected T getRtfsService() {
        return rtfsService;
    }

    protected T getLegacyService() {
        return legacyService;
    }

    public T getService() {
        return shouldUseFederationService() ? getRtfsService() : getLegacyService();
    }

    /**
     * Resolves the criteria for activating Artifactory Federation service.
     *
     * @return true if Artifactory Federation service should be used, otherwise the legacy logic will still run.
     */
    protected abstract boolean shouldUseFederationService();
}
