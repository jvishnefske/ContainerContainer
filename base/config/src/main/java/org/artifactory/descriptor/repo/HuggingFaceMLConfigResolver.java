package org.artifactory.descriptor.repo;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;

/**
 * @author shahafg
 */
@Slf4j
@UtilityClass
public class HuggingFaceMLConfigResolver {

    /**
     * Returns the effective value for xetEnabled considering UI/API configuration state and system property fallback.
     * When the {@code xetEnabled} flag was explicitly configured its value is used; otherwise the system property is
     * used.
     *
     * @param config the Hugging Face ML configuration (can be null)
     * @return the effective boolean value
     */
    public static boolean resolveIsXetEnabled(HuggingFaceMlGeneralConfig config) {
        if (config == null || !config.isXetEnabledExplicit()) {
            return ConstantValues.huggingFaceMLIsXetEnabled.getBoolean();
        }
        return config.isXetEnabled();
    }

    /**
     * Returns whether the configuration was explicitly set via UI/API.
     *
     * @param config the Hugging Face ML configuration (can be null)
     * @return true if the flag was explicitly configured
     */
    public static boolean isExplicitlyConfigured(HuggingFaceMlGeneralConfig config) {
        return config != null && config.isXetEnabledExplicit();
    }

    /**
     * Creates a new HuggingFaceMlGeneralConfig instance marked as UI/API configured.
     *
     * @param value the boolean value to store
     * @return new HuggingFaceMlGeneralConfig instance
     */
    public static HuggingFaceMlGeneralConfig createExplicitConfiguredInstance(boolean value) {
        return new HuggingFaceMlGeneralConfig(value, true);
    }

    /**
     * Creates a display-only HuggingFaceMlGeneralConfig instance with the system property value.
     * Used for showing current effective values in the UI/API without affecting the configuration state.
     *
     * @return new HuggingFaceMlGeneralConfig instance for display only
     */
    public static HuggingFaceMlGeneralConfig createHuggingFaceConfigBySystemProperty() {
        return new HuggingFaceMlGeneralConfig(ConstantValues.huggingFaceMLIsXetEnabled.getBoolean(), false);
    }
}
