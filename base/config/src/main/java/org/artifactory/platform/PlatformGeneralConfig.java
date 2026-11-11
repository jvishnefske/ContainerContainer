package org.artifactory.platform;

import lombok.Builder;
import lombok.Data;
import org.jfrog.access.client.platformconfig.ConfigurationModel;

/**
 * @author Andrey Tashlitsky
 */
@Data
@Builder
public class PlatformGeneralConfig implements ConfigurationModel {

    @Builder.Default
    private String dateFormat = "dd-MM-yy HH:mm:ss z";
    @Builder.Default
    private boolean helpLinksEnabled = true;
    private String logo;
}
