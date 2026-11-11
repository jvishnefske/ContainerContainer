package org.artifactory.metrics.callhome;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.UniformReservoir;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.jfrog.sysconf.SysConfigConstants;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.metrics.MetricsSenderListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentMap;

@Service
public class CallHomeMetrics implements MetricsSenderListener {
    static final int DEFAULT_MAX_CALLS_TO_TRACK = 100;
    private static final Logger log = LoggerFactory.getLogger(CallHomeMetrics.class);
    private final MetricRegistry metrics = new MetricRegistry();

    /**
     * Tracked queries by the unique ID of call
     */
    private final Cache<String, MetricCallHomeData> callHomeData;
    private long traceStartTime;


    /**
     * Builds the callhome metrics with default values
     */
    public CallHomeMetrics() {
        this(ArtifactoryHome.get().getSysConfig()
                .getInt(SysConfigConstants.KEY_CALLHOME_MAX_CALLS_TRACK, DEFAULT_MAX_CALLS_TO_TRACK));
    }

    public CallHomeMetrics(int maxTrackedCalls) {
        traceStartTime = System.currentTimeMillis();
        callHomeData = CacheBuilder.newBuilder().maximumSize(maxTrackedCalls).concurrencyLevel(8)
                .removalListener((RemovalListener<String, MetricCallHomeData>) notification ->
                        metrics.remove(notification.getValue().dataId)).build();
    }

    public MetricRegistry getMetricsRegistry() {
        return metrics;
    }

    public long getTraceStartTime() {
        return traceStartTime;
    }

    @Override
    public void send(CallHomeDataModel callHomeDataModel) {
        String dataId = getUniqueDataIDForCall(callHomeDataModel);
        MetricCallHomeData metricCallHomeData = callHomeData.asMap()
                .computeIfAbsent(dataId,
                        s -> new MetricCallHomeData(dataId, callHomeDataModel, this,
                                metrics.register(dataId, new Histogram(new UniformReservoir()))));
        if (null == callHomeDataModel.getAction()) {
            return;
        }
        if (callHomeDataModel.getAction().equalsIgnoreCase("upload")) {
            metricCallHomeData.getHistogram().update(callHomeDataModel.getRequestContentLength());
        } else {
            metricCallHomeData.getHistogram().update(callHomeDataModel.getResponseContentLength());
        }
    }

    private String getUniqueDataIDForCall(CallHomeDataModel callHomeDataModel) {
        return callHomeDataModel.getService() +
                "_" +
                callHomeDataModel.getPackageType() +
                "_" +
                callHomeDataModel.getRepoType() +
                "_" +
                callHomeDataModel.getTool() +
                "_" +
                callHomeDataModel.getAction() +
                "_" +
                callHomeDataModel.getStatusFamily();
    }

    public ConcurrentMap<String, MetricCallHomeData> getCallHomeData() {
        return callHomeData.asMap();
    }

    /**
     * Resets all the metrics collected so far and starts tracing from scratch
     */
    public void reset() {
        log.debug("Resetting call home metrics");
        metrics.getNames().forEach(metrics::remove);
        callHomeData.invalidateAll();
        traceStartTime = System.currentTimeMillis();
    }
}
