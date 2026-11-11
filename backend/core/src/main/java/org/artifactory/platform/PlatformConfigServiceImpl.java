package org.artifactory.platform;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.ConfigurationException;
import org.artifactory.security.access.AccessService;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.platformconfig.ConfigurationModel;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.artifactory.platform.PlatformConfigHolder.PLATFORM_GENERAL_CONFIG_KEY;
import static org.artifactory.platform.PlatformConfigHolder.PLATFORM_SYSTEM_MESSAGE_CONFIG_KEY;
import static org.artifactory.platform.PlatformConfigKeys.DATE_FORMAT;
import static org.artifactory.platform.PlatformConfigKeys.HELP_LINKS_ENABLED;
import static org.artifactory.platform.PlatformConfigKeys.LOGO;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_CONFIG;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_ENABLED;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_TITLE;
import static org.artifactory.platform.PlatformConfigKeys.SYSTEM_MESSAGE_TITLE_COLOR;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@Service
public class PlatformConfigServiceImpl implements PlatformConfigService {

    private final AccessService accessService;

    @Autowired
    public PlatformConfigServiceImpl(AccessService accessService) {
        this.accessService = accessService;
    }

    @Override
    public PlatformGeneralConfig getPlatformGeneralConfig() {
        return getPlatformConfigClient()
                .getConfig(PLATFORM_GENERAL_CONFIG_KEY, PlatformGeneralConfig.class)
                .orElse(PlatformGeneralConfig.builder().build());
    }

    @Override
    public PlatformSystemMessageConfig getPlatformSystemMessageConfig() {
        return getPlatformConfigClient()
                .getConfig(PLATFORM_SYSTEM_MESSAGE_CONFIG_KEY, PlatformSystemMessageConfig.class)
                .orElse(PlatformSystemMessageConfig.builder().build());
    }

    @Override
    public void updatePlatformGeneralConfig(String dateFormat, boolean helpLinksEnabled, String logo) {
        PlatformGeneralConfig platformGeneralConfig = PlatformGeneralConfig.builder()
                .dateFormat(dateFormat)
                .helpLinksEnabled(helpLinksEnabled)
                .logo(logo)
                .build();
        updatePlatformGeneralConfig(platformGeneralConfig);
    }

    private void updatePlatformGeneralConfig(PlatformGeneralConfig platformGeneralConfig) {
        try {
            getPlatformConfigClient().updateConfig(PLATFORM_GENERAL_CONFIG_KEY,
                    oldConfig -> platformGeneralConfig,
                    PlatformGeneralConfig.class, false);
            log.debug("Config {} was successfully sent to Platform Config", platformGeneralConfig);
        } catch (AccessClientException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    @Override
    public void updatePlatformSystemMessageConfig(boolean systemMessageEnabled, String systemMessageTitle,
            String systemMessageTitleColor, String systemMessage) {
        PlatformSystemMessageConfig platformSystemMessageConfig = PlatformSystemMessageConfig.builder()
                .systemMessageEnabled(systemMessageEnabled)
                .systemMessageTitle(systemMessageTitle)
                .systemMessageTitleColor(systemMessageTitleColor)
                .systemMessage(systemMessage)
                .build();
        updatePlatformSystemMessageConfig(platformSystemMessageConfig);
    }

    private void updatePlatformSystemMessageConfig(PlatformSystemMessageConfig platformSystemMessageConfig) {
        try {
            getPlatformConfigClient().updateConfig(PLATFORM_SYSTEM_MESSAGE_CONFIG_KEY,
                    oldConfig -> platformSystemMessageConfig,
                    PlatformSystemMessageConfig.class, false);
            log.debug("Config {} was successfully sent to Platform Config", platformSystemMessageConfig);
        } catch (AccessClientException e) {
            throw new ConfigurationException(e.getMessage());
        }
    }

    @Override
    public int movePlatformResources(Map<String, Object> map) {
        int moved = 0;
        if (map.containsKey(SYSTEM_MESSAGE_CONFIG)) {
            Map<String, Object> systemMessageMap = (Map<String, Object>) map.get(SYSTEM_MESSAGE_CONFIG);
            PlatformSystemMessageConfig platformSystemMessageConfig = getPlatformSystemMessageConfig();
            if (systemMessageMap.containsKey(SYSTEM_MESSAGE_ENABLED)) {
                boolean systemMessageEnabled = (Boolean) systemMessageMap.get(SYSTEM_MESSAGE_ENABLED);
                platformSystemMessageConfig.setSystemMessageEnabled(systemMessageEnabled);
                moved++;
            }
            if (systemMessageMap.containsKey(SYSTEM_MESSAGE_TITLE)) {
                String systemMessageTitle = (String) systemMessageMap.get(SYSTEM_MESSAGE_TITLE);
                platformSystemMessageConfig.setSystemMessageTitle(systemMessageTitle);
                moved++;
            }
            if (systemMessageMap.containsKey(SYSTEM_MESSAGE_TITLE_COLOR)) {
                String systemMessageTitleColor = (String) systemMessageMap.get(SYSTEM_MESSAGE_TITLE_COLOR);
                platformSystemMessageConfig.setSystemMessageTitleColor(systemMessageTitleColor);
                moved++;
            }
            if (systemMessageMap.containsKey(SYSTEM_MESSAGE)) {
                String systemMessage = (String) systemMessageMap.get(SYSTEM_MESSAGE);
                platformSystemMessageConfig.setSystemMessage(systemMessage);
                moved++;
            }
            updatePlatformSystemMessageConfig(platformSystemMessageConfig);
            map.remove(SYSTEM_MESSAGE_CONFIG);
        }
        PlatformGeneralConfig platformGeneralConfig = getPlatformGeneralConfig();
        if (map.containsKey(DATE_FORMAT)) {
            String dateFormat = (String) map.get(DATE_FORMAT);
            platformGeneralConfig.setDateFormat(dateFormat);
            moved++;
            map.remove(DATE_FORMAT);
        }
        if (map.containsKey(HELP_LINKS_ENABLED)) {
            boolean helpLinksEnabled = (Boolean) map.get(HELP_LINKS_ENABLED);
            platformGeneralConfig.setHelpLinksEnabled(helpLinksEnabled);
            moved++;
            map.remove(HELP_LINKS_ENABLED);
        }
        if (map.containsKey(LOGO)) {
            String logo = (String) map.get(LOGO);
            platformGeneralConfig.setLogo(logo);
            moved++;
            map.remove(LOGO);
        }
        updatePlatformGeneralConfig(platformGeneralConfig);
        return moved;
    }

    @Override
    public void updatePlatformConfigIfNeeded() {
        PlatformConfigHolder platformConfigHolder = PlatformConfigHolder.getInstance();
        if (!platformConfigHolder.isEmpty()) {
            Map<String, ConfigurationModel> platformConfigMap = platformConfigHolder.drainConfig();
            platformConfigMap.forEach(
                    (key, value) -> getPlatformConfigClient()
                            .updateConfig(key, oldConfig -> value, (Class<ConfigurationModel>) value.getClass(), false)
            );
        }
    }

    private GrpcPlatformConfigClient getPlatformConfigClient() {
        return accessService.getPlatformConfigClient();
    }
}
