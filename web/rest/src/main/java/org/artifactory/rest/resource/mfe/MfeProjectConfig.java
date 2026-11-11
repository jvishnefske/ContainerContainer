package org.artifactory.rest.resource.mfe;

import lombok.Builder;
import lombok.Value;
import org.artifactory.common.ConstantValues;

import java.nio.file.Paths;

/**
 * Configuration for a frontend MFE project.
 * Defines the base path and subdirectories for serving static files.
 *
 * @author michaelku
 */
@Value
@Builder
public class MfeProjectConfig {
    /**
     * Name of the MFE project (e.g., MfeName.CORE, MfeName.RTUI).
     * Used for version management and API endpoints.
     */
    MfeName mfeName;
    /**
     * Base path of the project (e.g., "" for root, "/rtui" for rtui project).
     * This is the path relative to /webapp.
     */
    String basePath;

    /**
     * Whether this project has a migrated-routes.json file.
     * If true, the file is expected at {basePath}/migrated-routes.json
     */
    boolean hasMigratedRoutes;

    /**
     * Constant for external path base configuration (e.g., coreMfeExternalPathBase or rtuiMfeExternalPathBase).
     * Used by ExternalMFE to determine the base URL for external MFE files.
     */
    ConstantValues externalPathBase;

    /**
     * Constant for external version override configuration (e.g., coreMfeExternalOverrideExternalVersion or
     * rtuiMfeExternalOverrideExternalVersion).
     * Used by ExternalMFE to get the version override if set.
     */
    ConstantValues externalOverrideExternalVersion;

    /**
     * Metric name for tracking the version of this MFE project (e.g., "core.mfe.version" or "rtui.mfe.version").
     * Used by ExternalMFE to register metrics for version tracking.
     */
    String metricName;

    /**
     * When set, resolves whether this target MFE's {@code routesList} is fully unloaded from core ({@code true},
     * default).
     * When {@code false}, entries in {@code rolloutPhaseList} stay registered on core. Null for projects where the
     * toggle does not apply (e.g. core).
     */
    ConstantValues controlledRolloutEnabled;

    /**
     * @return controlled rollout toggle; unset or {@code true} yields full unload of {@code routesList}.
     */
    public boolean isControlledRolloutEnabled() {
        return controlledRolloutEnabled == null || controlledRolloutEnabled.getBoolean();
    }

    /**
     * Gets the full path for the js subdirectory relative to /webapp.
     * @param webappPath The webapp base path (e.g., "/webapp")
     * @return Path like "/webapp/js" or "/webapp/rtui/js"
     */
    public String getJsPath(String webappPath) {
        return getSubdirPath(webappPath, "js");
    }

    /**
     * Gets the full path for the img subdirectory relative to /webapp.
     * @param webappPath The webapp base path (e.g., "/webapp")
     * @return Path like "/webapp/img" or "/webapp/rtui/img"
     */
    public String getImgPath(String webappPath) {
        return getSubdirPath(webappPath, "img");
    }

    /**
     * Gets the full path for the assets subdirectory relative to /webapp.
     * @param webappPath The webapp base path (e.g., "/webapp")
     * @return Path like "/webapp/assets" or "/webapp/rtui/assets"
     */
    public String getAssetsPath(String webappPath) {
        return getSubdirPath(webappPath, "assets");
    }

    private String getSubdirPath(String webappPath, String subdir) {
        if (basePath.isEmpty()) {
            return webappPath + "/" + subdir;
        }
        String normalizedBasePath = basePath.startsWith("/") ? basePath.substring(1) : basePath;
        return Paths.get(webappPath, normalizedBasePath, subdir).toString().replace('\\', '/');
    }

    /**
     * Gets the path to migrated-routes.json relative to /webapp.
     * @return Path like "/rtui/migrated-routes.json" or null if not applicable
     */
    public String getMigratedRoutesPath() {
        if (!hasMigratedRoutes) {
            return null;
        }
        // Use simple string concatenation for web paths (always forward slashes)
        return basePath + "/migrated-routes.json";
    }
}

