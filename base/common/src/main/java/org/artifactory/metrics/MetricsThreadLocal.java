package org.artifactory.metrics;

import jakarta.annotation.Nullable;
import java.util.List;

public class MetricsThreadLocal {
    private static final ThreadLocal<MetricsThreadLocalContext> context = new ThreadLocal<>();

    private MetricsThreadLocal() {
    }

    public static void bind(MetricsMetadata metadata) {
        context.set(MetricsThreadLocalContext.create(metadata));
    }

    public static void unbind() {
        context.remove();
    }

    @Nullable
    public static List<MetricsInfo> getMetricsInfoList() {
        MetricsThreadLocalContext metricsThreadLocalContext = context.get();
        return metricsThreadLocalContext != null ?
                metricsThreadLocalContext.getMetricsThreadLocal().getMetricsInfoList() : null;
    }

    public static void setMetricsInfoList(List<MetricsInfo> metricsInfoList) {
        MetricsThreadLocalContext metricsThreadLocalContext = context.get();
        if (metricsThreadLocalContext != null) {
            metricsThreadLocalContext.getMetricsThreadLocal().setMetricsInfoList(metricsInfoList);
        }
    }
}
