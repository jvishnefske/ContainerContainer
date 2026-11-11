package org.artifactory.api.callhome;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallHomeMetricFilterModel {
    private String service;
    private String packageType;
    private String tool;
    private String action;
    private String actionStatus;
    private String repoType;
}
