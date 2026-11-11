package org.artifactory.platform;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.version.converter.v324.MigrateConfigDescriptorFooterFieldsConverter;
import org.jfrog.access.client.platformconfig.ConfigurationModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * This object holds all the Platform related config values that were migrated during
 * {@link MigrateConfigDescriptorFooterFieldsConverter}.
 * After that, the stored values are sent to the Platform config in
 * {@link CentralConfigServiceImpl#sendRelevantConfigToPlatformConfigIfNeeded()}
 *
 * @author Andrey Tashlitsky
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PlatformConfigHolder {

    public static final String PLATFORM_CONFIG_PREFIX = "platform.config.";
    public static final String PLATFORM_GENERAL_CONFIG_KEY = PLATFORM_CONFIG_PREFIX + "general";
    public static final String PLATFORM_SYSTEM_MESSAGE_CONFIG_KEY = PLATFORM_CONFIG_PREFIX + "system.message";

    private final Map<String, ConfigurationModel> config = new ConcurrentHashMap<>();


    private static final class InstanceHolder {
        private static final PlatformConfigHolder INSTANCE = new PlatformConfigHolder();
    }

    public static PlatformConfigHolder getInstance() {
        return PlatformConfigHolder.InstanceHolder.INSTANCE;
    }

    public void addPlatformGeneralConfig(PlatformGeneralConfig platformGeneralConfig) {
        config.put(PLATFORM_GENERAL_CONFIG_KEY, platformGeneralConfig);
    }

    public void addPlatformSystemMessageConfig(PlatformSystemMessageConfig platformSystemMessageConfig) {
        config.put(PLATFORM_SYSTEM_MESSAGE_CONFIG_KEY, platformSystemMessageConfig);
    }

    public Map<String, ConfigurationModel> drainConfig() {
        Map<String, ConfigurationModel> mapCopy =
                config.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        config.clear();
        return mapCopy;
    }

    public boolean isEmpty() {
        return config.isEmpty();
    }
}
