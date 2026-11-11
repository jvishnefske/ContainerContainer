package org.artifactory.platform;

import java.util.Map;

/**
 * @author Andrey Tashlitsky
 */
public interface PlatformConfigService {

    PlatformGeneralConfig getPlatformGeneralConfig();

    PlatformSystemMessageConfig getPlatformSystemMessageConfig();

    void updatePlatformGeneralConfig(String dateFormat, boolean helpLinksEnabled, String logo);

    void updatePlatformSystemMessageConfig(boolean systemMessageEnabled, String systemMessageTitle,
            String systemMessageTitleColor, String systemMessage);

    int movePlatformResources(Map<String, Object> map);

    void updatePlatformConfigIfNeeded();
}
