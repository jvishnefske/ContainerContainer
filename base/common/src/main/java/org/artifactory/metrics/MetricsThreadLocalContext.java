package org.artifactory.metrics;

public class MetricsThreadLocalContext {
    private final MetricsMetadata metadata;

    protected MetricsThreadLocalContext(MetricsMetadata metadata) {
        this.metadata = metadata;
    }

    public static MetricsThreadLocalContext create(MetricsMetadata metadata) {
        return new MetricsThreadLocalContext(metadata);
    }

    protected MetricsMetadata getMetricsThreadLocal() {
        return this.metadata;
    }
}
