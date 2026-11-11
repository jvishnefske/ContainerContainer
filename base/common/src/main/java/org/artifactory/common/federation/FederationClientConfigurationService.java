package org.artifactory.common.federation;

import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.storage.fs.service.ConfigsServiceKey;

public interface FederationClientConfigurationService extends ReloadableBean {
    /**
     * @return true, if Artifactory Federation external service is enabled.
     * <p>
     * It is enabled if the value of config table key {@code 'rtfs.enabled'} is set to true,
     * false otherwise.
     */
    boolean isRtfsEnabled();

    /**
     * @return true, if Artifactory Federation service binaries tasks feature is enabled.
     * <p>
     * The feature enabled if the value of config table key {@code 'rtfs.binaries.tasks.enabled'} is set to true,
     * However, for the feature to be fully active a restart is required in order to reload the necessary
     * V2 binary provider.
     *
     * false otherwise.
     */
    boolean isRtfsBinariesTasksEnabled();

    /**
     * @return true, if Artifactory Federation service binaries tasks feature is enabled
     * and ready to accept binary provider V2.
     * <p>
     * The feature enabled if the value of config table key {@code 'rtfs.binaries.tasks.enabled'} is set to true,
     * And ready to accept binary provider V2 if migration is over.
     * false otherwise.
     */
    boolean shouldUseFederationBinaryProviderV2();

    FederationMigrationStatus getMigrationStatus();

    void updateConfigKey(ConfigsServiceKey key, boolean value);

    void updateConfigKey(ConfigsServiceKey key, String value);

    <T> boolean updateConfigKey(ConfigsServiceKey key, T value);

    void deleteConfigKey(ConfigsServiceKey key);

    /**
     * Set the value of the 'rtfs.binaries.tasks.enabled' config key.
     * Note: this flag is effective only after restart of the Artifactory instance.
     * @param enable true to enable the feature, false to disable it.
     */
    void setBinaryTasksUsingRtfsFlag(boolean enable);

    boolean getBooleanConfig(ConfigsServiceKey key);

    <T> T getConfig(ConfigsServiceKey key, Class<T> type);
}