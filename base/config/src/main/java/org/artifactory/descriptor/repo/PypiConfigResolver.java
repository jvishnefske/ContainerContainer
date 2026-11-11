package org.artifactory.descriptor.repo;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;

/**
 * @author shahafg
 */
@Slf4j
@UtilityClass
public class PypiConfigResolver {

    /**
     * Returns the effective value for simpleJsonFormatEnabled considering
     * UI/API configuration state and system property fallback.
     *
     * @param config the PyPI configuration (can be null)
     * @return the effective boolean value
     */
    public static boolean resolveIsSimpleJsonFormatEnabled(PypiGeneralConfig config) {
        if (config == null || !config.isSimpleJsonFormatEnabledExplicit()) {
            return ConstantValues.pypiSimpleJsonFormatEnabled.getBoolean();
        }
        return config.isSimpleJsonFormatEnabled();
    }

    /**
     * Creates a new PypiGeneralConfig instance marked as UI/API configured.
     *
     * @param simpleJsonFormatEnabled the simpleJsonFormatEnabled value to store
     * @return new PypiGeneralConfig instance
     */
    public static PypiGeneralConfig createExplicitConfiguredInstance(boolean simpleJsonFormatEnabled) {
        return new PypiGeneralConfig(simpleJsonFormatEnabled, true);
    }

    /**
     * Creates a display-only PypiGeneralConfig instance with the system property values
     * Used for showing current effective values in the UI/API without affecting the configuration state.
     *
     * @return new PypiGeneralConfig instance for display only
     */
    public static PypiGeneralConfig createPypiConfigBySystemProperty() {
        return new PypiGeneralConfig(ConstantValues.pypiSimpleJsonFormatEnabled.getBoolean(), false);
    }
}
