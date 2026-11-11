package org.artifactory.metrics.framework.adoption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.api.metrics.FeatureAdoptionAction;
import org.artifactory.api.metrics.FeatureAdoptionFeature;
import org.artifactory.api.metrics.FeatureAdoptionLabels;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.metrics.framework.MetricsExecutorServiceHelper;
import org.artifactory.schedule.TaskService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.Reloadable;
import org.jfrog.metrics.MetricsHelper;
import org.jfrog.metrics.model.MetricProvider;
import org.jfrog.metrics.model.TrackingSchema;
import org.jfrog.metrics.providers.FeatureAdoptionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Oleksandr Legenkiy
 */
@Service
@Reloadable(beanClass = FeatureAdoptionService.class, initAfter = TaskService.class)
public class FeatureAdoptionServiceImpl implements FeatureAdoptionService, ContextCreationListener {

    private static final Logger log = LoggerFactory.getLogger(FeatureAdoptionServiceImpl.class);
    private static final Logger faLogger = LoggerFactory.getLogger("USAGE_METRICS");
    private static final String SERVICE_NAME = "artifactory";
    static final String FEATURE_ADOPTION_DOMAIN = "adoption";
    static final String ARTIFACTORY_FA_METRICS_INTERVAL = "artifactory.usageMetrics.intervalSecs";
    static final Long DEFAULT_FA_METRIC_INTERVAL_SECONDS = 3600L;
    private final MetricsExecutorServiceHelper executorHelper;
    private final FeatureAdoptionProvider provider;
    private final MetricsHelper metricsHelper;
    private ScheduledExecutorService scheduledExecutorService;
    private boolean faServiceRunning;

    @Autowired
    FeatureAdoptionServiceImpl(MetricsExecutorServiceHelper executorHelper) {
        this.executorHelper = executorHelper;
        provider = new FeatureAdoptionProvider(SERVICE_NAME);
        // This collection has to be modifiable. See MetricsHelper#filterProviders
        List<MetricProvider> providers = new ArrayList<>(1);
        providers.add(provider);
        metricsHelper = new MetricsHelper(providers, Collections.emptyList());
    }

    @Override
    public void onContextCreated() {
        boolean featureAdoptionEnabled = FeatureAdoptionUtils.isFeatureAdoptionEnabled();
        log.info("Usage Metrics Service is enabled: {}", featureAdoptionEnabled);
        if (!featureAdoptionEnabled) {
            faServiceRunning = false; // explicitly set to false
            return;
        }
        scheduledExecutorService = executorHelper.createMetricsScheduledExecutorService();
        metricsHelper.startAdoption(faLogger, initCollectionInterval(ArtifactoryHome.get().getSysConfig()),
                scheduledExecutorService);
        faServiceRunning = true;
        log.info("Usage Metrics service is started and running in the background...");
    }

    @Override
    public void trackAdoption(FeatureAdoptionFeature feature, FeatureAdoptionAction action, boolean success,
                              FeatureAdoptionLabels labels) {
        this.trackIncrease(new TrackingSchema(feature.featureName(), FEATURE_ADOPTION_DOMAIN, action.action(),
                1, success, labels.labels()));
    }

    @Override
    public void trackIncrease(TrackingSchema trackingSample) {
        callProvider(provider::trackIncrease, trackingSample);
    }

    @Override
    public void trackDecrease(TrackingSchema trackingSample) {
        callProvider(provider::trackDecrease, trackingSample);
    }

    @Override
    public void trackAverage(TrackingSchema trackingSample) {
        callProvider(provider::trackAverage, trackingSample);
    }

    @Override
    public void trackSet(TrackingSchema trackingSample) {
        callProvider(provider::trackSet, trackingSample);
    }

    @Override
    public void trackUnset(TrackingSchema trackingSample) {
        callProvider(provider::trackUnset, trackingSample);
    }

    @Override
    public void destroy() {
        faServiceRunning = false;
        if (null != scheduledExecutorService) {
            scheduledExecutorService.shutdown();
        }
        log.info("Stopped feature adoption service.");
    }

    private long initCollectionInterval(SysConfig sysConfig) {
        long interval;
        try {
            long sysInterval = sysConfig.getLong(ARTIFACTORY_FA_METRICS_INTERVAL, DEFAULT_FA_METRIC_INTERVAL_SECONDS);
            interval = sysInterval != 0L ? sysInterval : DEFAULT_FA_METRIC_INTERVAL_SECONDS;
        } catch (NumberFormatException e) {
            log.warn("Usage Metrics interval configuration is not a number, hence using defaults {} seconds as the " +
                    "interval", DEFAULT_FA_METRIC_INTERVAL_SECONDS);
            log.debug(String.format("Exception occurred while reading metrics interval configuration, " +
                    "hence using defaults %d seconds as the interval", DEFAULT_FA_METRIC_INTERVAL_SECONDS), e);
            interval = DEFAULT_FA_METRIC_INTERVAL_SECONDS;
        }
        return interval;
    }

    private void callProvider(ConsumerWithThrowsException<TrackingSchema> consumer, TrackingSchema trackingSample) {
        try {
            if (faServiceRunning) {
                consumer.accept(trackingSample);
            }
        } catch (Exception e) {
            log.error("Error providing feature adoption metrics for {}", trackingSample.getFeatureName());
            log.debug(String.format("Exception occurred when providing feature adoption metrics for %s",
                    trackingSample.getFeatureName()), e);
        }
    }

    private interface ConsumerWithThrowsException<T> {
        void accept(T t) throws Exception;
    }
}
