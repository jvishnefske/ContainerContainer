package org.artifactory.metrics;

import lombok.Data;

import java.util.List;

@Data
public class MetricsMetadata {
    private List<MetricsInfo> metricsInfoList;
}
