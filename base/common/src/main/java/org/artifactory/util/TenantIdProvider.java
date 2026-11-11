package org.artifactory.util;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;

import java.util.Optional;

/**
 * Resolves the source tenant ID from {@code shared.system.technicalServerName} in system.yaml.
 * The resolved value is cached in memory since it does not change at runtime.
 * <p>
 * The tenant ID is only propagated when running in AOL (cloud) mode, since the technical server name
 * identifying the tenant is only meaningful between JPDs of the same JFrog cloud platform.
 */
public final class TenantIdProvider {

    public static final String X_JFROG_SRC_TENANT_ID = "X-Jfrog-Src-Tenant-Id";
    private static final String SHARED_SYSTEM_TECHNICAL_SERVER_NAME = "shared.system.technicalServerName";
    private static volatile Optional<String> cachedTenantId;

    private TenantIdProvider() {
    }

    /**
     * Returns the source tenant ID if the feature flag is enabled, the instance runs in AOL mode,
     * and the technical server name is configured.
     *
     * @return the tenant ID, or empty if the feature is disabled, not running on AOL, or not configured
     */
    public static Optional<String> getTenantId() {
        if (!ConstantValues.tenantIdHeaderEnabled.getBoolean() || !AolUtils.isAol()) {
            return Optional.empty();
        }
        if (cachedTenantId != null) {
            return cachedTenantId;
        }
        cachedTenantId = ArtifactoryHome.get().getSysConfig()
                .get(SHARED_SYSTEM_TECHNICAL_SERVER_NAME);
        return cachedTenantId;
    }
}
