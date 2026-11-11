package org.artifactory.metrics;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MetricsInfo {
    String repoClass;
    String repoType;
    String action;
}
