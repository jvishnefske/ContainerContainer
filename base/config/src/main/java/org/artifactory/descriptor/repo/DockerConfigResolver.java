package org.artifactory.descriptor.repo;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.springframework.stereotype.Component;

/**
 * Helper for resolving Docker configuration values and handling
 * the business logic around UI vs system property precedence.
 *
 * @author Nir Paz
 */
@Component
@Slf4j
public class DockerConfigResolver {

    /**
     * returns the effective value for completeListManifestOverride considering
     * UI/API configuration state and system property fallback.
     *
     * @param config the Docker configuration (can be null)
     * @return the effective boolean value
     */
    public boolean resolveCompleteManifestOverrideEnabled(DockerGeneralConfig config) {
        if (config == null || !config.isExplicitConfig()) {
            return ConstantValues.dockerCompleteManifestOverrideEnabled.getBoolean();
        }
        return config.isCompleteListManifestOverwrite();
    }

    /**
     * Creates a display-only DockerGeneralConfig instance with the override enabled value
     * Used for showing current effective values in the UI/API without affecting the configuration state.
     *
     * @return new DockerGeneralConfig instance for display only
     */
    public DockerGeneralConfig createDockerConfigBySystemProperty() {
        return DockerGeneralConfig.builder()
                .completeListManifestOverwrite(ConstantValues.dockerCompleteManifestOverrideEnabled.getBoolean())
                .sharedDigestStatsSyncEnabled(ConstantValues.dockerSharedDigestStatsSyncEnabled.getBoolean())
                .explicitConfig(false)
                .build();
    }

    /**
     * Returns the effective value for enforceStrictTagOverwrite: when enabled, only the last uploaded
     * manifest or list is kept per tag (last wins). Uses UI/API config when explicit, else default false.
     *
     * @param config the Docker configuration (can be null)
     * @return the effective boolean value (default false)
     */
    public boolean resolveEnforceStrictTagOverwrite(DockerGeneralConfig config) {
        if (config == null || !config.isExplicitConfig()) {
            return false;
        }
        return config.isEnforceStrictTagOverwrite();
    }

    /**
     * Returns the effective value for sharedDigestStatsSyncEnabled considering UI/API configuration state
     * and system property fallback (same precedence as {@link #resolveCompleteManifestOverrideEnabled}).
     *
     * @param config the Docker configuration (can be null)
     * @return the effective boolean value
     */
    public boolean resolveSharedDigestStatsSyncEnabled(DockerGeneralConfig config) {
        if (config == null || !config.isExplicitConfig()) {
            return ConstantValues.dockerSharedDigestStatsSyncEnabled.getBoolean();
        }
        return config.isSharedDigestStatsSyncEnabled();
    }

    /**
     * Merges an incoming UI/API Docker config with the existing persisted config.
     * Marks result as explicit only when at least one field changed from its effective value,
     * preventing spurious "UI configured" state when nothing actually changed.
     *
     * @param incoming the Docker config from the UI/API request (may be null)
     * @param existing the currently persisted Docker config
     * @return merged config, or existing unchanged if nothing changed
     */
    public DockerGeneralConfig mergeFromUiRequest(DockerGeneralConfig incoming, DockerGeneralConfig existing) {
        if (incoming == null) {
            return existing;
        }

        if (resolveEffective(existing).equals(incoming)) {
            return existing;
        }

        log.debug("Docker config changed - marking as UI/API configured");
        return incoming.toBuilder().explicitConfig(true).build();
    }

    /**
     * Builds a config representing the currently effective values (system property fallbacks applied),
     * with explicitConfig=false — matching the state of any incoming JSON-deserialized config
     * so equals() gives a clean field-by-field diff.
     * When adding a new field to DockerGeneralConfig, add its resolve call here
     * (the field-count unit test enforces this).
     */
    private DockerGeneralConfig resolveEffective(DockerGeneralConfig config) {
        return DockerGeneralConfig.builder()
                .completeListManifestOverwrite(resolveCompleteManifestOverrideEnabled(config))
                .enforceStrictTagOverwrite(resolveEnforceStrictTagOverwrite(config))
                .sharedDigestStatsSyncEnabled(resolveSharedDigestStatsSyncEnabled(config))
                .build();
    }
}
