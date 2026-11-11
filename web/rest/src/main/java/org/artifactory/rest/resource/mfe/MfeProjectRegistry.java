package org.artifactory.rest.resource.mfe;

import org.artifactory.common.ConstantValues;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Registry for all MFE project configurations.
 * Provides a single source of truth for MFE project definitions and project resolution.
 *
 * To add a new project, simply add a new MfeProjectConfig to the MFE_PROJECTS list.
 *
 * Example for adding a new project in "/newproject" folder:
 * Chain {@code .controlledRolloutEnabled(&lt;mfe-specific ConstantValues&gt;)} on the builder when the target MFE ships
 * {@code migrated-routes.json}.
 *
 * MfeProjectConfig.builder()
 *     .basePath("/newproject")
 *     .hasMigratedRoutes(false)  // or true if it has migrated-routes.json
 *     .build()
 *
 * @author michaelku
 */
@Component
public class MfeProjectRegistry {

    /**
     * Configuration for all frontend MFE projects.
     * To add a new project, simply add a new MfeProjectConfig to this list.
     */
    private final List<MfeProjectConfig> mfeProjects = Arrays.asList(
            // Root project (UMD) - files in ./webapp/js and ./webapp/img
            MfeProjectConfig.builder()
                    .mfeName(MfeName.CORE)
                    .basePath("")
                    .hasMigratedRoutes(false)
                    .externalPathBase(ConstantValues.coreMfeExternalPathBase)
                    .externalOverrideExternalVersion(ConstantValues.coreMfeExternalOverrideExternalVersion)
                    .metricName("core.mfe.version")
                    .build(),

            MfeProjectConfig.builder()
                    .mfeName(MfeName.RTUI)
                    .basePath("/rtui")
                    .hasMigratedRoutes(true)
                    .externalPathBase(ConstantValues.rtuiMfeExternalPathBase)
                    .externalOverrideExternalVersion(ConstantValues.rtuiMfeExternalOverrideExternalVersion)
                    .metricName("rtui.mfe.version")
                    .controlledRolloutEnabled(ConstantValues.rtuiMfeControlledRolloutEnabled)
                    .build(),

            MfeProjectConfig.builder()
                    .mfeName(MfeName.RETENTION)
                    .basePath("/retention")
                    .hasMigratedRoutes(true)
                    .externalPathBase(ConstantValues.retentionMfeExternalPathBase)
                    .externalOverrideExternalVersion(ConstantValues.retentionMfeExternalOverrideExternalVersion)
                    .metricName("retention.mfe.version")
                    .controlledRolloutEnabled(ConstantValues.retentionMfeControlledRolloutEnabled)
                    .build(),

            MfeProjectConfig.builder()
                    .mfeName(MfeName.PACKAGES)
                    .basePath("/packages")
                    .hasMigratedRoutes(true)
                    .externalPathBase(ConstantValues.packagesMfeExternalPathBase)
                    .externalOverrideExternalVersion(ConstantValues.packagesMfeExternalOverrideExternalVersion)
                    .metricName("packages.mfe.version")
                    .controlledRolloutEnabled(ConstantValues.packagesMfeControlledRolloutEnabled)
                    .build(),

            MfeProjectConfig.builder()
                    .mfeName(MfeName.RELEASEBUNDLES)
                    .basePath("/releasebundles")
                    .hasMigratedRoutes(true)
                    .externalPathBase(ConstantValues.releasebundlesMfeExternalPathBase)
                    .externalOverrideExternalVersion(ConstantValues.releasebundlesMfeExternalOverrideExternalVersion)
                    .metricName("releasebundles.mfe.version")
                    .controlledRolloutEnabled(ConstantValues.releasebundlesMfeControlledRolloutEnabled)
                    .build(),

            MfeProjectConfig.builder()
                    .mfeName(MfeName.RBV1RECEIVED)
                    .basePath("/rbv1received")
                    .hasMigratedRoutes(true)
                    .externalPathBase(ConstantValues.rbv1receivedMfeExternalPathBase)
                    .externalOverrideExternalVersion(ConstantValues.rbv1receivedMfeExternalOverrideExternalVersion)
                    .metricName("rbv1received.mfe.version")
                    .controlledRolloutEnabled(ConstantValues.rbv1receivedMfeControlledRolloutEnabled)
                    .build(),

            MfeProjectConfig.builder()
                    .mfeName(MfeName.BUILDINFOMFE)
                    .basePath("/buildinfomfe")
                    .hasMigratedRoutes(true)
                    .externalPathBase(ConstantValues.buildinfomfeMfeExternalPathBase)
                    .externalOverrideExternalVersion(ConstantValues.buildinfomfeMfeExternalOverrideExternalVersion)
                    .metricName("buildinfomfe.mfe.version")
                    .controlledRolloutEnabled(ConstantValues.buildinfomfeMfeControlledRolloutEnabled)
                    .build()
    );

    /**
     * Finds the project configuration that matches the given resource path.
     * Resource paths are already stripped of the /webapp prefix and follow the pattern:
     * {basePath}/js/..., {basePath}/img/..., or {basePath}/assets/...
     * where basePath is empty for core project (e.g., "/js/file.js", "/img/logo.png", "/assets/...")
     * or non-empty for other projects (e.g., "/rtui/js/file.js", "/rtui/assets/...").
     *
     * @param resourcePath The resource path (e.g., "/js/manifest.esm.js" or "/rtui/assets/file.js")
     * @return The matching project config, or null if no match found
     */
    public MfeProjectConfig findProjectForResource(String resourcePath) {
        if (resourcePath == null) {
            return null;
        }

        // Check if path starts with /js/, /img/, or /assets/ (core project with empty basePath)
        if (resourcePath.startsWith("/js/") || resourcePath.startsWith("/img/")
                || resourcePath.startsWith("/assets/")) {
            return mfeProjects.stream()
                    .filter(project -> project.getBasePath().isEmpty())
                    .findFirst()
                    .orElse(null);
        }

        // Otherwise, match non-root projects by checking {basePath}/js/, /img/, or /assets/ pattern
        // For migrated-routes.json: use exact match only, and only for projects with hasMigratedRoutes
        return mfeProjects.stream()
                .filter(project -> !project.getBasePath().isEmpty())
                .filter(project -> resourcePath.startsWith(project.getBasePath() + "/js/")
                        || resourcePath.startsWith(project.getBasePath() + "/img/")
                        || resourcePath.startsWith(project.getBasePath() + "/assets/")
                        || (project.getMigratedRoutesPath() != null
                                && resourcePath.equals(project.getMigratedRoutesPath()))
                )
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the project configuration by MFE name.
     * @param mfeName The MFE name (e.g., MfeName.CORE, MfeName.RTUI)
     * @return The matching project config, or null if no match found
     */
    public MfeProjectConfig findProjectByMfeName(MfeName mfeName) {
        if (mfeName == null) {
            return null;
        }
        return mfeProjects.stream()
                .filter(project -> mfeName.equals(project.getMfeName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets all configured MFE projects.
     * @return List of all MFE project configurations
     */
    public List<MfeProjectConfig> getAllProjects() {
        return mfeProjects;
    }
}

