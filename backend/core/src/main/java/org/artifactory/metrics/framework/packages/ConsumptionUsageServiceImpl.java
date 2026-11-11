package org.artifactory.metrics.framework.packages;

import com.jfrog.sysconf.SysConfig;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.metrics.framework.MetricsExecutorServiceHelper;
import org.artifactory.metrics.framework.providers.PackageConsumptionMetricProvider;
import org.artifactory.metrics.framework.providers.PackageCountMetricProvider;
import org.artifactory.schedule.TaskService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.StorageSummaryService;
import org.javers.common.collections.Lists;
import org.jfrog.metrics.MetricsCollector;
import org.jfrog.metrics.MetricsHelper;
import org.jfrog.metrics.model.MetricProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;

import static org.artifactory.metrics.framework.MetricsServiceImpl.METRICS_PLATFORM_DEPENDANT_LINE_BREAKS;

/**
 * Package usage service is scheduling the package consumption metric collector to run every
 * {@link ConstantValues.consumptionUsageMetricsCollectorIntervalSeconds} seconds.
 *
 * @author BarakH
 */
@Slf4j
@Service
@Reloadable(beanClass = ConsumptionUsageService.class, initAfter = TaskService.class)
public class ConsumptionUsageServiceImpl implements ConsumptionUsageService, ContextCreationListener {

    private static final Logger faLogger = LoggerFactory.getLogger("CONSUMPTION_USAGE");
    private MetricsExecutorServiceHelper executorHelper;
    private ScheduledExecutorService scheduledExecutorService;
    private MetricsCollector metricsCollector;
    private AddonsManager addonsManager;
    private StorageSummaryService storageSummaryService;

    @Autowired
    public void setExecutorHelper(MetricsExecutorServiceHelper executorHelper) {
        this.executorHelper = executorHelper;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setStorageSummaryService(StorageSummaryService storageSummaryService) {
        this.storageSummaryService = storageSummaryService;
    }

    @Override
    public void onContextCreated() {
        if (!isPackageUsageMetricsEnabled()) {
            log.info("Consumption usage service is disabled");
            return;
        }
        MetricProvider packageConsumptionMetricProvider = new PackageConsumptionMetricProvider(addonsManager);
        MetricProvider packageCountMetricProvider = new PackageCountMetricProvider(storageSummaryService);
        MetricsHelper metricsHelper =
                new MetricsHelper(Lists.asList(packageConsumptionMetricProvider, packageCountMetricProvider),
                        Collections.emptyList());
        long interval = ConstantValues.consumptionUsageMetricsCollectorIntervalSeconds.getLong();
        log.info("Scheduled consumption usage metrics collector to run every {} seconds", interval);
        scheduledExecutorService = executorHelper.createMetricsScheduledExecutorService();
        metricsCollector = metricsHelper.start(faLogger, null, interval, scheduledExecutorService);
        setLineBreakConfig();
    }

    private void setLineBreakConfig() {
        boolean platformDependantLineBreak =
                getArtifactorySystemConfigField(METRICS_PLATFORM_DEPENDANT_LINE_BREAKS, true);
        if (!platformDependantLineBreak) {
            metricsCollector.setPlatformDependentLineBreak(false);
            log.info("Config for platformDependantLineBreak set in consumption usage metricsCollector");
        }
    }

    @Override
    public void destroy() {
        if (null != scheduledExecutorService) {
            scheduledExecutorService.shutdown();
        }
        log.info("Stopped consumption usage service");
    }

    boolean isPackageUsageMetricsEnabled() {
        return ConstantValues.consumptionUsageMetricsEnabled.getBoolean();
    }

    private static boolean getArtifactorySystemConfigField(String fieldName, boolean defaultValue) {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        SysConfig sysConfig = artifactoryHome.getSysConfig();
        return sysConfig.getBoolean(fieldName, defaultValue);
    }
}
