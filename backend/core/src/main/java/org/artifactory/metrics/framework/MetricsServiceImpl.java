/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.artifactory.metrics.framework;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.jfrog.sysconf.SysConfig;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.schedule.TaskService;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.spring.Reloadable;
import org.jfrog.client.util.PathUtils;
import org.jfrog.metrics.MetricsCollector;
import org.jfrog.metrics.MetricsHelper;
import org.jfrog.metrics.model.MetricProvider;
import org.jfrog.metrics.model.Providers;
import org.jfrog.metrics.model.TrackingSchema;
import org.jfrog.metrics.providers.FeatureAdoptionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

/**
 * @author leela
 */
@Service
@Reloadable(beanClass = MetricsService.class, initAfter = TaskService.class)
public class MetricsServiceImpl implements ContextCreationListener, MetricsService {
    private static final Logger log = LoggerFactory.getLogger(MetricsServiceImpl.class);
    private static final Logger metricsLogger = LoggerFactory.getLogger("METRICS");
    private static final Logger metricEventsLogger = LoggerFactory.getLogger("METRICS_EVENTS");

    public static final String ARTIFACTORY_METRICS_ENABLED = "artifactory.metrics.enabled";
    private static final String ARTIFACTORY_METRICS_EXCLUDE = "artifactory.metrics.exclude";
    private static final String ARTIFACTORY_METRICS_INTERVAL = "artifactory.metrics.intervalSecs";
    private static final String METRICS_ELASTICSEARCH_URL = "shared.metrics.filebeat.elasticsearch.url";
    private static final String METRICS_ELASTICSEARCH_USERNAME =
            "shared.metrics.filebeat.elasticsearch.username";
    private static final String METRICS_ELASTICSEARCH_CREDENTIAL =
            "shared.metrics.filebeat.elasticsearch.password";
    private static final String METRICS_FILEBEAT_SSL_VERIFICATION_MODE =
            "shared.metrics.filebeat.elasticsearch.ssl.verification_mode";
    private static final String METRICS_FILEBEAT_BINARY = "shared.metrics.filebeat.binary";
    private static final String METRICS_FILEBEAT_CONFIGURATION =
            "shared.metrics.filebeat.configuration";
    private static final String METRICS_FILEBEAT_LOG_TO_FILES = "shared.metrics.filebeat.log.enabled";
    private static final String METRICS_FILEBEAT_LOG_LEVEL = "shared.metrics.filebeat.log.level";
    private static final String METRICS_FILEBEAT_LOG_PATH = "shared.metrics.filebeat.log.path";
    private static final String METRICS_FILEBEAT_LOG_KEEPFILES =
            "shared.metrics.filebeat.log.keepfiles";
    private static final String METRICS_FILEBEAT_ENABLED = "shared.metrics.filebeat.enabled";
    public static final String METRICS_PLATFORM_DEPENDANT_LINE_BREAKS =
            "artifactory.metrics.logging.lineBreaks.platformDependant";
    private static final String ES_USERNAME_DEFAULT = "admin";
    private static final String ES_CREDENTIAL_DEFAULT = "password";
    private static final String APP_THIRD_PARTY_FILEBEAT = "app/third-party/filebeat/";
    private static final String FILEBEAT_YAML = "var/etc/";
    private final AccessService accessService;
    private ScheduledExecutorService scheduledExecutorService;
    private boolean metricServiceRunning;
    private Process filebeatProcess;
    private MetricsCollector metricsCollector;
    private static final String EXCLUDED_METRICS = "sys_memory,sys_cpu";
    private final MetricsExecutorServiceHelper executorHelper;
    private final List<MetricProvider> metricProviders;
    private final FeatureAdoptionProvider featureAdoptionProvider;
    private final List<OpenMetricsWriter> additionalWriters;
    private volatile int metricsBufferSize = 16;

    @Autowired
    public MetricsServiceImpl(AccessService accessService, MetricsExecutorServiceHelper executorHelper,
            List<MetricProvider> metricProviders, List<OpenMetricsWriter> additionalWriters) {
        this.accessService = accessService;
        this.executorHelper = executorHelper;
        this.metricProviders = metricProviders;
        // FeatureAdoptionProvider is stateful and shouldn't be shared between MetricsService and FeatureAdoptionService
        this.featureAdoptionProvider = new FeatureAdoptionProvider("artifactory");
        this.additionalWriters = List.copyOf(additionalWriters);
    }

    @Override
    public void onContextCreated() {
        ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
        SysConfig sysConfig = artifactoryHome.getSysConfig();
        boolean metricsEnabled = isMetricsEnabled(sysConfig);
        log.info("Metric Framework Service is enabled: {}", metricsEnabled);
        if (!metricsEnabled) {
            return;
        }
        List<MetricProvider> providers =
                Providers.getPredefinedProviders(artifactoryHome.getVarDir().getAbsolutePath());
        providers.addAll(metricProviders);
        providers.add(featureAdoptionProvider);
        startMetricsHelper(sysConfig, providers);
        boolean pushMetricsToFilebeat = sysConfig.getBoolean(METRICS_FILEBEAT_ENABLED, false);
        if (pushMetricsToFilebeat) {
            log.debug("Pushing metrics to Filebeat is enabled");
            filebeatProcess = startFilebeat(artifactoryHome, sysConfig);
        } else {
            log.debug("Filebeat is not enabled, hence ignoring to start Filebeat.");
        }
    }

    private void startMetricsHelper(SysConfig sysConfig, List<MetricProvider> metricProviders) {
        scheduledExecutorService = executorHelper.createMetricsScheduledExecutorService();
        List<String> excludedProvidersList = excludedProviders(sysConfig);
        validate(metricProviders);
        MetricsHelper mHelper = new MetricsHelper(metricProviders, excludedProvidersList);
        log.debug("Final metric providers list in Artifactory, {}", mHelper.getProviders());

        long interval = 60;
        try {
            interval = sysConfig.getLong(ARTIFACTORY_METRICS_INTERVAL, 60);
        } catch (NumberFormatException e) {
            log.warn("Metrics interval configuration is not a number, hence using defaults 60 seconds as the interval");
            log.debug("Exception occurred while reading metrics interval configuration, " +
                    "hence using defaults 60 seconds as the interval", e);
        }
        metricsCollector = mHelper.start(metricsLogger, metricEventsLogger, interval, scheduledExecutorService);
        setLineBreakConfig(sysConfig);
        metricServiceRunning = true;
        log.info("Metric Framework service is started and is running in background...");
    }

    private void validate(List<MetricProvider> metricProviders) {
        //validate metrics list not to have duplicate names
        ListMultimap<String, Class<? extends MetricProvider>> validationMap = ArrayListMultimap.create();
        for (MetricProvider metricProvider : metricProviders) {
            validationMap.put(metricProvider.getName(), metricProvider.getClass());
        }
        validationMap.asMap().forEach((key, collection) -> {
            if (collection.size() > 1) {
                printValidationErrorMessage(key, collection);
            }
        });
    }

    void printValidationErrorMessage(String key, Collection<Class<? extends MetricProvider>> collection) {
        log.error("Potential data loss! Found more than 1 metric provider for the same name '{}'\nproviders:{}",
                key, collection);
    }

    private void setLineBreakConfig(SysConfig sysConfig) {
        boolean platformDependantLineBreak = sysConfig
                .getBoolean(METRICS_PLATFORM_DEPENDANT_LINE_BREAKS, true);
        log.info("platformDependantLineBreak set config : {}", platformDependantLineBreak);
        if (!platformDependantLineBreak) {
            metricsCollector.setPlatformDependentLineBreak(false);
            log.info("Config for platformDependantLineBreak set in metricsCollector");
        }
    }

    private List<String> excludedProviders(SysConfig sysConfig) {
        String excludedProvidersStr = sysConfig.get(ARTIFACTORY_METRICS_EXCLUDE, "");
        excludedProvidersStr += EXCLUDED_METRICS;
        List<String> excludedProvidersList = Stream.of(excludedProvidersStr.split(",")).toList();
        log.debug("Excluded metrics in Artifactory: {}", excludedProvidersList);
        return excludedProvidersList;
    }

    private boolean isMetricsEnabled(SysConfig sysConfig) {
        return sysConfig.getBoolean(ARTIFACTORY_METRICS_ENABLED, false) ||
                ConstantValues.metricsEnabled.getBoolean();
    }

    private Map<String, String> getEnvVarsForFilebeat(ArtifactoryHome artifactoryHome, SysConfig sysConfig) {
        String elasticsearchURL = getESUrlFromSysConfig(sysConfig);
        if (StringUtils.isBlank(elasticsearchURL)) {
            log.warn("Elasticsearch Url is not available in system.yaml, hence not starting Filebeat.");
            return null;
        }
        Map<String, String> envVars = new HashMap<>();
        envVars.put("METRICS_LOG_ENABLED", "true");
        envVars.put("NODE_ID", ContextHelper.get().getServerId());
        envVars.put("ARTIFACTORY_SERVICE_ID", accessService.getArtifactoryServiceId().getFormattedName());
        envVars.put("ELASTICSEARCH_URL", elasticsearchURL);
        envVars.put("ELASTICSEARCH_USERNAME", sysConfig.get(METRICS_ELASTICSEARCH_USERNAME, ES_USERNAME_DEFAULT));
        envVars.put("ELASTICSEARCH_PASSWORD", artifactoryHome.getMasterEncryptionWrapper()
                .decryptIfNeeded(sysConfig.get(METRICS_ELASTICSEARCH_CREDENTIAL, ES_CREDENTIAL_DEFAULT))
                .getDecryptedData());
        envVars.put("FILEBEAT_LOG_LEVEL", sysConfig.get(METRICS_FILEBEAT_LOG_LEVEL, "info"));
        envVars.put("FILEBEAT_LOG_PATH", sysConfig.get(METRICS_FILEBEAT_LOG_PATH, ""));
        envVars.put("FILEBEAT_LOG_TOFILES",
                Boolean.toString(sysConfig.getBoolean(METRICS_FILEBEAT_LOG_TO_FILES, false)));
        envVars.put("FILEBEAT_LOG_KEEPFILES", sysConfig.get(METRICS_FILEBEAT_LOG_KEEPFILES, "2"));
        envVars.put("ARTIFACTORY_HOME", artifactoryHome.getHomeDir().getAbsolutePath());
        String verificationModeInConfig = sysConfig.get(METRICS_FILEBEAT_SSL_VERIFICATION_MODE, "");
        log.debug("VerificationMode as set in config : {}", verificationModeInConfig);
        String verificationMode = Objects.equals(verificationModeInConfig, "") ? "none" : verificationModeInConfig;
        log.debug("VerificationMode resolved as : {}", verificationMode);
        envVars.put("SSL_VERIFICATION_MODE", verificationMode);
        return envVars;
    }

    @Nullable
    String getESUrlFromSysConfig(SysConfig sysConfig) {
        return sysConfig.get(METRICS_ELASTICSEARCH_URL).map(PathUtils::addTrailingSlash).orElse(null);
    }

    /**
     * Starts Filebeat process with the given environment variables.
     */
    Process startFilebeat(ArtifactoryHome artifactoryHome, SysConfig sysConfig) {
        Process process = null;
        String startCommand = getFilebeatStartCommand(artifactoryHome, sysConfig);
        Map<String, String> env = getEnvVarsForFilebeat(artifactoryHome, sysConfig);
        if (null != env && null != startCommand) {
            try {
                ProcessBuilder builder = new ProcessBuilder("bash", "-c", startCommand, "&");
                builder.environment().putAll(env);
                log.debug("Starting Filebeat.. {}", startCommand);
                process = builder.start();
                log.debug("Filebeat started successfully.");
            } catch (IOException e) {
                log.warn("Unable to start Filebeat, hence metrics may not be available in JFrog Mission Control.", e);
            }
        }
        return process;
    }

    @Nullable
    private String getFilebeatStartCommand(ArtifactoryHome artifactoryHome, SysConfig sysConfig) {
        String fileBeatBinary = SystemUtils.IS_OS_WINDOWS ? "filebeat.exe" : "filebeat";
        String filebeatYamlPath =
                new File(artifactoryHome.getHomeDir(), FILEBEAT_YAML + "filebeat.yaml").getAbsolutePath();
        String filebeatBinaryPath =
                new File(artifactoryHome.getHomeDir(), APP_THIRD_PARTY_FILEBEAT + fileBeatBinary).getAbsolutePath();
        String elasticsearchURL = sysConfig.get(METRICS_ELASTICSEARCH_URL, "");
        if (elasticsearchURL.isEmpty()) {
            log.warn("Elasticsearch Url is empty, hence not starting Filebeat.");
            return null;
        }
        String binary = sysConfig.get(METRICS_FILEBEAT_BINARY, filebeatBinaryPath);
        String configurationPath = sysConfig.get(METRICS_FILEBEAT_CONFIGURATION, filebeatYamlPath);
        if (binary.isEmpty() || configurationPath.isEmpty()) {
            log.warn(
                    "Binary and configuration for Filebeat are empty, hence not starting Filebeat. Please set in " +
                            "system.yaml");
            return null;
        }
        return binary + " -c " + configurationPath;
    }

    @Override
    public void destroy() {
        if (null != filebeatProcess) {filebeatProcess.destroy();}
        if (null != scheduledExecutorService) {scheduledExecutorService.shutdown();}
        metricServiceRunning = false;
        log.info("Stopped metric service.");
    }

    @Override
    public void trackAdoption(String featureName, String action, boolean success,
            Map<String, String> labels) {
        if (!metricServiceRunning) {
            log.trace("Metrics service is not running. Ignoring the track adoption call...");
            return;
        }
        try {
            log.trace("Logging adoption to metrics: {}, {}", featureName, action);
            featureAdoptionProvider.trackIncrease(
                    new TrackingSchema(featureName, "adoption", action, 1, success, labels));
        } catch (Exception e) {
            log.error("Error providing adoption metrics for {}", featureName);
            log.debug(String.format("Exception occurred when providing adoption metrics for %s",
                    featureName), e);
        }
    }

    @Override
    public boolean isMetricServiceRunning() {
        return metricServiceRunning;
    }

    @Nonnull
    @Override
    public String getOpenMetrics(boolean applicationMetricsOnly) {
        boolean metricsEnabled = isMetricsEnabled(ArtifactoryHome.get().getSysConfig());
        if (!metricsEnabled) {
            return "";
        }

        // First, let the JFrog metrics infrastructure to dump the last metrics snapshot it collected
        String metrics = metricsCollector.openMetricsCollect(applicationMetricsOnly);

        // Append metrics, coming from the instrumentation of various third-party frameworks (performed by Micrometer):
        // System metrics, JVM metrics, Jersey server metrics, Spring Web metrics, Guava cache metrics and more.
        if (!this.additionalWriters.isEmpty()) {
            StringWriter writer = new StringWriter(this.metricsBufferSize);
            writeAdditionalMetrics(writer, applicationMetricsOnly);
            this.metricsBufferSize = writer.getBuffer().length();
            metrics += writer.toString();
        }

        return metrics;
    }

    protected void writeAdditionalMetrics(StringWriter target, boolean applicationMetricsOnly) {
        int offset;
        for (OpenMetricsWriter writer : this.additionalWriters) {
            offset = target.getBuffer().length();
            try {
                writer.write(target, applicationMetricsOnly);
            } catch (IOException error) {
                log.error("{} writer failed writing additional metrics", writer.getClass().getName(), error);
                target.getBuffer().setLength(offset);
            }
        }
    }
}