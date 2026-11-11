package org.artifactory.storage.service;

public interface StorageMetricCollector {

    void update();

    StorageMetric get();

}
