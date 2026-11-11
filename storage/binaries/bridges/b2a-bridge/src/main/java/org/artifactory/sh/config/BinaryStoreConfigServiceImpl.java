package org.artifactory.sh.config;

import com.jfrog.sysconf.SysConfig;
import io.opentracing.Tracer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.federation.FederationClientConfigurationService;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.repo.FederationClientService;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.util.HttpClientConfigurator;
import org.jfrog.access.client.AccessClient;
import org.jfrog.client.http.model.ProxyConfig;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.rtfs.ArtifactoryFederationClient;
import org.jfrog.security.crypto.JFrogMasterKeyEncrypter;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;
import org.jfrog.storage.common.prune.PruneStatusReport;
import org.jfrog.storage.config.BinaryStoreConfigService;
import org.jfrog.storage.config.BinaryStoreConstValues;
import org.jfrog.storage.events.BinariesSyncEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.IntConsumer;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.state.ArtifactoryServerState.RUNNING;

/**
 * @author TamirH
 */
@Service
@Slf4j
public class BinaryStoreConfigServiceImpl implements BinaryStoreConfigService {

    private final JdbcHelper jdbcHelper;
    private final ArtifactoryTracer artifactoryTracer;
    private final ApplicationEventPublisher eventPublisher;
    private AccessService accessService;
    private FederationClientConfigurationService federationClientConfigurationService;
    private InternalDbService dbService;

    // Some db tests doesn't initialize the addonManager bean,
    // but do have a dummy context that contains it .
    public AddonsManager getAddonsManager() {
        return ContextHelper.get().beanForType(AddonsManager.class);
    }

    @Autowired
    public BinaryStoreConfigServiceImpl(JdbcHelper jdbcHelper, ArtifactoryTracer artifactoryTracer,
            ApplicationEventPublisher eventPublisher) {
        this.jdbcHelper = jdbcHelper;
        this.artifactoryTracer = artifactoryTracer;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public JdbcHelper getJdbcHelper() {
        return this.jdbcHelper;
    }

    @Override
    public DbType getDbType() {
        return ArtifactoryHome.get().getDBProperties().getDbType();
    }

    public Tracer getTracer() {
        // For @evgenyku
        // TODO: Storage handler is going to switch to the OpenTelemetry soon.
        //       We will remove this method as soon as the Storage team will
        //       close the RTDEV-57906.  Until then, distributed tracing within
        //       the storage handler will NOT be integrated with what Artifactory does
        //       (e.g. trace id will be different!)
        return io.opentracing.util.GlobalTracer.get();
    }

    @Override
    public File getArtifactoryDataDir() {
        return ArtifactoryHome.get().getDataDir();
    }

    @Override
    public boolean isAol() {
        return getAddonsManager().isAddonSupported(AddonType.AOL);
    }

    @Override
    public boolean isLicenseInstalled() {
        return getAddonsManager().isLicenseInstalled();
    }

    @Override
    public boolean isEntitlementEnforced() {
        return getAddonsManager().isEntitlementEnforced();
    }

    @Override
    public boolean isEntitledForRshProvider() {
        return getAddonsManager().isEntitled(EntitlementType.BINARY_PROVIDER_RSH);
    }

    @Override
    public boolean isEntitledForCustomEndpoint() {
        return getAddonsManager().isEntitled(EntitlementType.BINARY_PROVIDER_CUSTOM_ENDPOINT);
    }

    @Override
    public boolean isEntitledCdn() {
        return getAddonsManager().isEntitled(EntitlementType.CDN);
    }

    @Override
    public ConflictsGuard<String> getConflictsGuard(String type) {
        return getAddonsManager().addonByType(HaCommonAddon.class).getConflictsGuard(type);
    }

    @Override
    public Map<String, String> getArtifactRetentionBinaryHeaders(String repoKey) {
        ArtifactRetentionAddon artifactRetentionAddon = getAddonsManager().addonByType(ArtifactRetentionAddon.class);
        return artifactRetentionAddon.getCreateBinaryHeaders(repoKey);
    }

    @Override
    public Map<String, String> getArtifactRetentionInitiateBinaryRestoreHeaders() {
        ArtifactRetentionAddon artifactRetentionAddon = getAddonsManager().addonByType(ArtifactRetentionAddon.class);
        return artifactRetentionAddon.getInitiateBinaryRestoreHeaders();
    }

    @Override
    public Map<String, String> getArtifactRetentionIsBinaryRestoredHeaders() {
        ArtifactRetentionAddon artifactRetentionAddon = getAddonsManager().addonByType(ArtifactRetentionAddon.class);
        return artifactRetentionAddon.getIsBinaryRestoredHeaders();
    }

    @Override
    public String getArtifactoryRunningModeName() {
        return getAddonsManager().getArtifactoryRunningMode().name();
    }

    @Override
    public boolean isTrialLicense() {
        return getAddonsManager().isTrialLicense();
    }

    @Override
    public boolean isJCR() {
        return getAddonsManager().getArtifactoryRunningMode().isJCR();
    }

    @Override
    public SysConfig getSysConfig() {
        return ArtifactoryHome.get().getSysConfig();
    }

    @Override
    public File getBinaryStoreGcpCredentialsFile() {
        return ArtifactoryHome.get().getBinaryStoreGcpCredentialsFile();
    }

    @Override
    public CloseableHttpClient createClosableHttpClient(Integer maxConnectionsPerRoute,
            Integer maxConnectionsTotal, Integer connectionTimeout,
            ProxyConfig proxy, boolean withParams) {
        HttpClientConfigurator configurator = new HttpClientConfigurator();
        if (maxConnectionsPerRoute != null) {
            configurator.maxConnectionsPerRoute(maxConnectionsPerRoute);
        }
        if (maxConnectionsTotal != null) {
            configurator.maxTotalConnections(maxConnectionsTotal);
        }
        if (connectionTimeout != null) {
            configurator.connectionTimeout(connectionTimeout);
        }
        if (proxy != null && isNotBlank(proxy.getHost())) {
            configurator.proxy(proxy);
        }
        if (withParams) {
            return configurator.build(true);
        }
        return configurator.build();
    }

    /**
     * Create a closable http client with the given parameters.
     *
     * @param clientParams map of parameters
     * @param proxy        proxy configuration
     * @param withParams   whether to include the parameters in the client
     * @return a closable http client
     */
    @Override
    public CloseableHttpClient createClosableHttpClient(@NonNull Map<String, String> clientParams, ProxyConfig proxy,
            boolean withParams) {
        HttpClientConfigurator configurator = new HttpClientConfigurator();
        setIntegerFromMap(clientParams, "maxConnectionsPerRoute", configurator::maxConnectionsPerRoute);
        setIntegerFromMap(clientParams, "maxConnectionsTotal", configurator::maxTotalConnections);
        setIntegerFromMap(clientParams, "socketTimeout", configurator::socketTimeout);
        setIntegerFromMap(clientParams, "connectionTimeout", connectionTimeout -> {
            if (connectionTimeout > 0) {
                configurator.connectionTimeout(connectionTimeout);
            } else {
                log.warn("A non-positive {} won't be set as connection timeout", connectionTimeout);
            }
        });
        if (proxy != null && isNotBlank(proxy.getHost())) {
            configurator.proxy(proxy);
        }
        return configurator.build(withParams);
    }

    /**
     * Set an integer value from a map if it exists and is parsable to an integer.
     *
     * @param clientParams map of parameters
     * @param key          key to look for in the map
     * @param consumer     consumer to accept the result if it exists and convertable to an integer
     */
    private static void setIntegerFromMap(@NonNull Map<String, String> clientParams, @NonNull String key,
            @NonNull IntConsumer consumer) {
        int intValue = NumberUtils.toInt(clientParams.get(key), Integer.MIN_VALUE);
        if (intValue != Integer.MIN_VALUE) {
            consumer.accept(intValue);
        } else {
            log.warn("{} is not a valid integer value", key);
        }
    }

    @Override
    public boolean isHaConfigured() {
        return ArtifactoryHome.get().isHaConfigured();
    }

    @Override
    public int getIntlValue(BinaryStoreConstValues value) {
        return ConstantValues.valueOf(value.getPropertyName()).getInt();
    }

    @Override
    public boolean getBoolValue(BinaryStoreConstValues value) {
        return ConstantValues.valueOf(value.getPropertyName()).getBoolean();
    }

    @Override
    public long getLongValue(BinaryStoreConstValues value) {
        return ConstantValues.valueOf(value.getPropertyName()).getLong();
    }

    @Override
    public void publishStorageEvent(ApplicationEvent event) {
        eventPublisher.publishEvent(event);
    }

    @Override
    public boolean deletePruneStopMarker() throws StorageException {
        ConfigsService configsService = ContextHelper.get().beanForType(ConfigsService.class);
        return configsService.deleteConfig(ConfigsServiceKey.STORAGE_PRUNE_STOP_MARKER.getKeyName()) > 0;
    }

    @Override
    public void updatePruneReport(PruneStatusReport psm) throws StorageException {
        ConfigsService configsService = ContextHelper.get().beanForType(ConfigsService.class);
        configsService.addOrUpdateConfig(ConfigsServiceKey.STORAGE_PRUNE_REPORT, psm,
                System.currentTimeMillis());
    }

    @Override
    public String getServerId() {
        return ContextHelper.get().getServerId();
    }

    @Override
    public AccessClient getAccessClient() {
        return this.accessService.getAccessClient();
    }

    @Override
    public Properties getHadNodeProperties() {
        final HaNodeProperties haNodeProperties = ArtifactoryHome.get().getHaNodeProperties();
        return haNodeProperties != null ? haNodeProperties.getProperties() : null;
    }

    @Override
    public JFrogMasterKeyEncrypter getJfrogMasterKey() {
        return ArtifactoryHome.get().getJFrogMasterKeyEncrypter();
    }

    @Override
    public File getArtifactoryEtcDir() {
        return ArtifactoryHome.get().getEtcDir();
    }

    @Override
    public File getArtifactorySecurityDir() {
        return ArtifactoryHome.get().getSecurityDir();
    }

    @Override
    public String getClusterDataDir() {
        final HaNodeProperties haNodeProperties = ArtifactoryHome.get().getHaNodeProperties();
        return haNodeProperties != null ? haNodeProperties.getClusterDataDir() : null;
    }

    @Override
    public long getNextDbId() {
        return this.dbService.nextId();
    }

    @Override
    public int getNumOfActiveServersInCluster() {
        ArtifactoryServersCommonService artifactoryServersCommonService =
                ContextHelper.get().beanForType(ArtifactoryServersCommonService.class);
        return (int) artifactoryServersCommonService.getActiveMembers().stream()
                .filter(server -> server.getServerState().equals(
                        RUNNING)).count();
    }

    @Override
    public void binariesSync(List<BinaryElement> binariesToDelete) {
        eventPublisher.publishEvent(new BinariesSyncEvent(this, binariesToDelete));
    }

    @Override
    public ArtifactoryFederationClient getFederationClient() {
        FederationClientService federationClientService =
                ContextHelper.get().beanForType(FederationClientService.class);

        return federationClientService.getFederationClient();
    }

    @Override
    public boolean shouldUseFederationV2() {
        return federationClientConfigurationService.shouldUseFederationBinaryProviderV2();
    }

    @Override
    public String getTenantId() {
        return ArtifactoryHome.get().getTenantId().orElse(null);
    }

    //we have no choice but to create the artifactory services as lazy, as in some circumstances they create
    //cyclic dependency for this config class (usually because of AccessService)
    //binaryStoreConfigService -> dbServiceImpl -> artifactoryEncryptionService (via postInitialize in an if condition)
    // -> accessService (deleted dependency now) -> repositoryService -> securityService -> projectsService
    // -> storageService -> artifactoryBinaryStoreService -> BinaryStoreService -> binaryStoreConfigService

    @Autowired
    @Lazy
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @Autowired
    @Lazy
    public void setInternalDbService(InternalDbService internalDbService) {
        this.dbService = internalDbService;
    }

    @Autowired
    @Lazy
    public void setFederatedRepoConfigurationService(
            FederationClientConfigurationService federationClientConfigurationService) {
        this.federationClientConfigurationService = federationClientConfigurationService;
    }
}
