/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
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

package org.artifactory.metrics.services;

import com.codahale.metrics.Histogram;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.callhome.CallHomeMetricFilterModel;
import org.artifactory.api.callhome.CallHomeMetricInfo;
import org.artifactory.api.callhome.CallHomeRequest;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.jackson.JacksonWriter;
import org.artifactory.api.rest.subscription.Subscription;
import org.artifactory.async.Async;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.subscription.SubscriptionConfig;
import org.artifactory.metrics.callhome.CallHomeDataModel;
import org.artifactory.metrics.callhome.CallHomeMetrics;
import org.artifactory.metrics.callhome.MetricCallHomeData;
import org.artifactory.metrics.providers.features.CallHomeFeature;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.DbMetaData;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.traffic.TrafficAction;
import org.artifactory.util.AolUtils;
import org.artifactory.util.CollectionUtils;
import org.artifactory.util.HttpClientConfigurator;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Michael Pasternak
 */
@Service
public class CallHomeService {

    public static final String CALL_HOME_BINTRAY_CONTEXT_PATH = "/products/jfrog/artifactory/stats/usage";
    private static final String ETC_RELEASE = "/etc/os-release";
    private static final Logger log = LoggerFactory.getLogger(CallHomeService.class);
    private static final String PARAM_OS_ARCH = "os.arch";
    private static final String PARAM_OS_NAME = "os.name";
    private static final String PARAM_JAVA_VERSION = "java.version";
    private static final String LOCALHOST = "localhost";
    private static final int ATTEMPTS_BEFORE_OFFLINE = 3;
    private final AddonsManager addonsManager;
    private final CentralConfigService configService;
    private final ArtifactoryServersCommonService serversService;
    private final AccessService accessService;
    private final DbService dbService;
    private final ArtifactoryDbProperties dbProperties;
    private final AolService aolService;
    private AtomicBoolean isOffline = new AtomicBoolean(false);
    private int failedAttempts = 0;
    @Autowired
    private List<CallHomeFeature> callHomeFeatures = Lists.newArrayList();
    @Autowired
    private CallHomeMetrics callHomeMetrics;

    @Autowired
    public CallHomeService(AddonsManager addonsManager, CentralConfigService configService,
            ArtifactoryServersCommonService serversService, AccessService accessService, DbService dbService,
            ArtifactoryDbProperties dbProperties, AolService aolService) {
        this.addonsManager = addonsManager;
        this.configService = configService;
        this.serversService = serversService;
        this.accessService = accessService;
        this.dbService = dbService;
        this.dbProperties = dbProperties;
        this.aolService = aolService;
    }

    /**
     * Sends post message with usage info to bintray
     */
    @Async
    public void callHome() {
        if (shouldExecuteCallHome()) {
            try (CloseableHttpClient client = createHttpClient()) {
                HttpPost postMethod = new HttpPost(getCallHomeUrl());
                HttpEntity callHomeData = callHomeEntity();
                postMethod.setEntity(callHomeData);
                log.debug("Calling home...");
                if (log.isDebugEnabled()) {
                    log.debug("Call Home Data : {}", EntityUtils.toString(callHomeData));
                }
                client.execute(postMethod);
                clearData();
                failedAttempts = 0;
                isOffline.set(false);
            } catch (Exception e) {
                if (isOffline.get()) {
                    log.debug("Call home is offline, skipping: {}", e.getMessage());
                    clearData();
                } else if (++failedAttempts >= ATTEMPTS_BEFORE_OFFLINE) {
                    isOffline.getAndSet(true);
                    log.warn("Call home failed {} consecutive times and will be suppressed. " +
                            "Last error: {}", failedAttempts, e.getMessage());
                    clearData();
                } else {
                    log.debug("Failed calling home (attempt {}/{}): {}", failedAttempts, ATTEMPTS_BEFORE_OFFLINE,
                            e.getMessage());
                }
            }
        }
    }

    boolean shouldExecuteCallHome() {
        if (!ConstantValues.versionQueryEnabled.getBoolean()) {
            return false;
        }
        return !configService.getDescriptor().isOfflineMode() || aolService.isAol();
    }

    private String getCallHomeUrl() {
        String urlOverride = ConstantValues.callHomeOverrideUrl.getString();
        return StringUtils.isNotBlank(urlOverride) ? urlOverride :
                ConstantValues.bintrayApiUrl.getString() + CALL_HOME_BINTRAY_CONTEXT_PATH;
    }

    public AtomicBoolean getIsOffline() {
        return isOffline;
    }

    /**
     * @return {@link CloseableHttpClient}
     */
    @VisibleForTesting
    CloseableHttpClient createHttpClient() {
        ProxyDescriptor proxy = InternalContextHelper.get().getCentralConfig().getDescriptor().getDefaultProxy();
        return new HttpClientConfigurator()
                .proxy(proxy)
                .socketTimeout(15000)
                .connectionTimeout(1500)
                .noRetry()
                .build();
    }

    /**
     * Produces callHomeEntity
     *
     * @return {@link HttpEntity}
     * @throws IOException on serialization errors
     */
    private HttpEntity callHomeEntity() throws IOException {
        CallHomeRequest request = new CallHomeRequest();

        addInstanceAndEnvInfo(request);
        addFeatures(request);
        addMetrics(request);
        return serializeToStringEntity(request);
    }

    private void addMetrics(CallHomeRequest request) {
        ConcurrentMap<String, MetricCallHomeData> callHomeMetricsList = callHomeMetrics.getCallHomeData();
        callHomeMetricsList.forEach((k, v) ->
        {
            List<CallHomeMetricFilterModel> filterList = new ArrayList<>();
            Histogram histogram = v.getHistogram();
            CallHomeDataModel model = v.callHomeDataModel;

            if (StringUtils.isNotEmpty(model.getAction())) {
                String metric = "";
                if (model.getAction().equalsIgnoreCase(TrafficAction.UPLOAD.name())) {
                    metric = "upload_in_bytes";
                } else if (model.getAction().equalsIgnoreCase(TrafficAction.DOWNLOAD.name())) {
                    metric = "download_in_bytes";
                }

                CallHomeMetricFilterModel callHomeMetricFilter =
                        CallHomeMetricFilterModel.builder().service(model.getService()).action(
                                        model.getAction()).actionStatus(model.getStatusFamily())
                                .packageType(model.getPackageType())
                                .repoType(model.getRepoType())
                                .tool(model.getTool()).build();
                filterList.add(callHomeMetricFilter);

                if (!metric.isEmpty()) {
                    CallHomeMetricInfo callHomeUsageMetricInfo =
                            CallHomeMetricInfo.builder().metric(metric)
                                    .value(String.valueOf(Arrays.stream(histogram.getSnapshot().getValues()).sum()))
                                    .type("long").filters(filterList).build();
                    request.addCallHomeMetric(callHomeUsageMetricInfo);
                }

                CallHomeMetricInfo callHomeCounterMetricInfo =
                        CallHomeMetricInfo.builder().metric("count").value(String.valueOf(histogram.getCount()))
                                .type("integer").filters(filterList).build();

                request.addCallHomeMetric(callHomeCounterMetricInfo);
            }
        });
        callHomeMetrics.reset();
    }

    /**
     * Serializes {@link CallHomeRequest} to {@link org.apache.http.entity.StringEntity}
     *
     * @param request {@link CallHomeRequest}
     * @return {@link org.apache.http.entity.StringEntity}
     * @throws IOException happens if serialization fails
     */
    private StringEntity serializeToStringEntity(CallHomeRequest request) throws IOException {
        String serialized = JacksonWriter.serialize(request, true);
        return new StringEntity(serialized, ContentType.APPLICATION_JSON);
    }


    /**
     * Collects features metadata  {@see RTFACT-8412}
     *
     * @param request that holds the entire callhome content
     */
    private void addFeatures(CallHomeRequest request) {
        callHomeFeatures.stream()
                .map(callHomeFeature -> {
                    try {
                        return callHomeFeature.getFeature();
                    } catch (Exception e) {
                        if (isAolInstance()) {
                            log.error("Failed to get feature from: " + callHomeFeature.getClass().getSimpleName(), e);
                        } else {
                            log.debug("Failed to get feature from: " + callHomeFeature.getClass().getSimpleName(), e);
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .forEach(request::addCallHomeFeature);
    }

    /**
     * Optionally clears any data kept in memory for the job execution
     */
    private void clearData() {
        callHomeFeatures.forEach((CallHomeFeature::clearData));
    }

    /**
     * Collect environment and instance information
     *
     * @param request that should the entire call home information
     */
    private void addInstanceAndEnvInfo(CallHomeRequest request) {
        request.version = configService.getProductVersionInfo().getVersion();

        String license = addonsManager.getLicenseType();
        if (license != null && license.equals("aol")) {
            boolean isDedicated = ArtifactoryHome.get().getArtifactoryProperties()
                    .getBooleanProperty(ConstantValues.aolDedicatedServer);
            license = isDedicated ? license + " dedicated" : license;
        }
        request.licenseType = license;

        request.licenseOEM = addonsManager.isPartnerLicense() ? "AWS" : null;
        Date licenseValidUntil = addonsManager.getLicenseValidUntil();
        if (licenseValidUntil != null) {
            request.licenseExpiration = ISODateTimeFormat.dateTime().print(new DateTime(licenseValidUntil));
        }
        request.setDist(System.getProperty("artdist"));
        populateSetup(request);
        request.environment.hostId = addonsManager.addonByType(HaCommonAddon.class).getHostId();
        request.environment.serviceId = accessService.getArtifactoryServiceId().getFormattedName();
        request.environment.licenseHash = addonsManager.getLicenseKeyHash(false);
        request.environment.attributes.osName = System.getProperty(PARAM_OS_NAME);
        request.environment.attributes.osArch = System.getProperty(PARAM_OS_ARCH);
        request.environment.attributes.javaVersion = System.getProperty(PARAM_JAVA_VERSION);
        addLinuxDistributionAttributesIfNeeded(request);
        setDbInfo(request);
        setAllowNonPostgresProperty(request);
        SubscriptionConfig subscriptionConfig = configService.getMutableDescriptor().getSubscriptionConfig();
        if (subscriptionConfig != null && CollectionUtils.notNullOrEmpty(subscriptionConfig.getEmails())) {
            request.subscription = new Subscription(subscriptionConfig.getEmails());
        }
    }

    void addLinuxDistributionAttributesIfNeeded(CallHomeRequest request) {
        if (isLinux()) {
            try (BufferedReader reader = createBufferedReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (StringUtils.startsWith(line, "NAME=")) {
                        request.environment.attributes.osDistribution = extractValue(line);
                    } else if (StringUtils.startsWith(line, "PRETTY_NAME=")) {
                        request.environment.attributes.osPrettyName = extractValue(line);
                    } else if (StringUtils.startsWith(line, "VERSION=")) {
                        request.environment.attributes.osVersion = extractValue(line);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to read " + ETC_RELEASE, e);
            }
        }
    }

    @VisibleForTesting
    BufferedReader createBufferedReader() throws IOException {
        return new BufferedReader(new FileReader(ETC_RELEASE));
    }

    @VisibleForTesting
    boolean isLinux() {
        return SystemUtils.IS_OS_LINUX;
    }

    private String extractValue(String line) {
        return line.split("=", 2)[1].replace("\"", "");
    }

    private void setDbInfo(CallHomeRequest request) {
        try {
            DbMetaData metaData = dbService.getDbMetaData();
            request.dbType = dbService.getDatabaseType().toString();
            request.dbVersion = metaData.getProductVersion();
        } catch (StorageException e) {
            log.warn("Can not retrieve database and driver name / version", e);
        }
    }

    private void setAllowNonPostgresProperty(final CallHomeRequest target) {
        target.allowNonPostgresql = this.dbProperties.allowNonPgFlag()
                .map(b -> b.toString().toUpperCase())
                .orElse("Not exist");
    }

    @VisibleForTesting
    String getAccountName(String aolAccountURL) {
        if (aolAccountURL != null) {
            try {
                URL url = new URL(aolAccountURL);
                String host = url.getHost();
                if (!LOCALHOST.equalsIgnoreCase(host)) {
                    return host.split("\\.")[0];
                }
            } catch (Exception e) {
                String msg = String.format("Caught exception while trying to parse aol Account URL: %s", aolAccountURL);
                log.debug(msg, e);
                return "N/A";
            }
        }
        return "N/A";
    }

    private void populateSetup(CallHomeRequest request) {
        String aolAccountURL = ArtifactoryHome.get().getArtifactoryProperties()
                .getProperty(ConstantValues.artifatoryServiceName);
        request.accountName = getAccountName(aolAccountURL);
        int numberOfNodes;
        if (isAolInstance()) {
            boolean isDedicated = ArtifactoryHome.get().getArtifactoryProperties()
                    .getBooleanProperty(ConstantValues.aolDedicatedServer);
            request.setup = isDedicated ? "AOL-Dedicated" : "AOL";
        } else if (addonsManager.getArtifactoryRunningMode() == ArtifactoryRunningMode.HA &&
                (numberOfNodes = getNumberOfNodes()) > 1) {
            request.setup = "cluster";
            request.numberOfNodes = numberOfNodes;
            return;
        }
        request.setup = "standalone";
    }

    private int getNumberOfNodes() {
        return serversService.getAllArtifactoryServers().size();
    }

    private boolean isAolInstance() {
        return AolUtils.isAol();
    }
}
