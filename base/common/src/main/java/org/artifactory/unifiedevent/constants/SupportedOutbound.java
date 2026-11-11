package org.artifactory.unifiedevent.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SupportedOutbound {
    //NOTE: The name must correspond to the outbound dispatcher bean name in java configuration
    // This Client corresponds to package org.artifactory.unifiedevent.dispatchers.outbound.httpOutbound
    HTTP("httpOutbound"),
    // This Client corresponds to package org.artifactory.unifiedevent.dispatchers.outbound.ClientOutbound
    CLIENT("clientOutbound"),
    UNKNOWN("unknown");

    private final String name;
}
