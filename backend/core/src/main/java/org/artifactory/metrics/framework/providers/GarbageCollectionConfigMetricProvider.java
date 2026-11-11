package org.artifactory.metrics.framework.providers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.cron.CronUtils;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provider to collect next Garbage Collection Run time details
 *
 * @author leela
 */
@Slf4j
@Component
public class GarbageCollectionConfigMetricProvider implements MetricProvider {

    private final CentralConfigService configService;

    private Date lastStoredGCExecutionDate;
    private Instant lastCollectionDate;

    @Autowired
    public GarbageCollectionConfigMetricProvider(CentralConfigService centralConfigService) {
        this.configService = centralConfigService;
    }

    @Override
    public String getName() {
        return "jfrt_artifacts";
    }

    @Override
    public String getDescription() {
        return "Artifactory Garbage Collection Config Metric Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return TimeUnit.SECONDS.toMillis(60);
    }

    @Override
    public List<Metric> collect() {
        String gcCronExp = this.configService.getDescriptor().getGcConfig().getCronExp();
        Date nextExecutionDate = CronUtils.getNextExecution(gcCronExp);
        List<Metric> metrics = new ArrayList<>();
        if (null == lastStoredGCExecutionDate || lastStoredGCExecutionDate != nextExecutionDate) {
            lastStoredGCExecutionDate = nextExecutionDate;
            lastCollectionDate = Instant.now();
            log.debug("Next GC execution date : {}", nextExecutionDate.toString());
            Duration diff = Duration.between(lastCollectionDate, nextExecutionDate.toInstant());
            metrics.add(Metric.builder()
                    .name("gc_next_run")
                    .type(Metric.MetricType.SECONDS)
                    .labels(new HashMap<>())
                    .description("Next GC Run")
                    .value(diff.toSeconds())
                    .build());
        }
        return metrics;
    }

    @Override
    public ReportType getReportType() {
        return ReportType.DEDUPED;
    }

    @Override
    public long getLastUpdatedMillis() {
        if (null == lastCollectionDate ||
                lastStoredGCExecutionDate.compareTo(CronUtils.
                        getNextExecution(this.configService.getDescriptor().getGcConfig().getCronExp())) != 0) {
            lastCollectionDate = Instant.now();
        }
        return lastCollectionDate.toEpochMilli();

    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

}
