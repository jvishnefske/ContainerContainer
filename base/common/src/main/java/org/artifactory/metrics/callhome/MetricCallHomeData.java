package org.artifactory.metrics.callhome;

import com.codahale.metrics.Histogram;
import jakarta.annotation.Nonnull;

public class MetricCallHomeData{

    public final String dataId;
    public final CallHomeDataModel callHomeDataModel;
    private final CallHomeMetrics metrics;
    private final Histogram histogram;

    public MetricCallHomeData(@Nonnull String dataId, @Nonnull CallHomeDataModel callHomeDataModel,
            CallHomeMetrics callHomeMetrics, @Nonnull Histogram histogram) {
        this.dataId = dataId;
        this.callHomeDataModel = callHomeDataModel;
        this.histogram = histogram;
        this.metrics = callHomeMetrics;
    }

    public Histogram getHistogram() {
        return histogram;
    }
}
