package org.artifactory.metrics.callhome;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CallHomeDataModel {
    private final String service;
    @Builder.Default
    private final String packageType = "NA";
    private final String tool;
    private final String action;
    private final int statusCode;
    private final String statusFamily;
    private final Long requestContentLength;
    private final Long responseContentLength;
    private final String remoteAddress;
    private final String userAgent;
    @Builder.Default
    private final String repoType = "NA";
}

