package org.artifactory.rest.resource.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class ServerInfo {
    private String serverId;
    private String hostname;
    private String baseUrl;
    private String npmBaseUrl;
    private boolean isSkipDockerDns;
}