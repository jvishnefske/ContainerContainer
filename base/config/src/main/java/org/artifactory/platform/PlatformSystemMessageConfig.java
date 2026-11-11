package org.artifactory.platform;

import lombok.Builder;
import lombok.Data;
import org.jfrog.access.client.platformconfig.ConfigurationModel;

/**
 * @author Andrey Tashlitsky
 */
@Data
@Builder
public class PlatformSystemMessageConfig implements ConfigurationModel {

    private boolean systemMessageEnabled;
    private String systemMessage;
    private String systemMessageTitle;
    @Builder.Default
    private String systemMessageTitleColor = "#429f46";
}
