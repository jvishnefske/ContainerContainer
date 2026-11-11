package org.artifactory.metrics;

import org.artifactory.metrics.callhome.CallHomeDataModel;

public interface MetricsSenderListener {
    void send(CallHomeDataModel metricsObject);
}
