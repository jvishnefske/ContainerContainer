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

package org.artifactory.storage.db.itest;

import com.google.common.collect.Maps;
import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ArtifactoryRunningMode;
import org.artifactory.addon.OssAddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.addon.ha.message.HaMessage;
import org.artifactory.addon.ha.message.HaMessageTopic;
import org.artifactory.addon.ha.propagation.uideploy.UIDeployPropagationResult;
import org.artifactory.addon.ha.workitem.HaMessageWorkItem;
import org.artifactory.addon.replication.event.ReplicationEventQueueData;
import org.artifactory.addon.replication.event.ReplicationOwnerModel;
import org.artifactory.addon.smartrepo.SmartRepoAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterManager;
import org.artifactory.fs.StatsInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.spring.SpringConfigPaths;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.version.VersionProvider;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.common.concurrency.JVMLockWrapper;
import org.jfrog.common.concurrency.JvmConflictGuard;
import org.jfrog.common.concurrency.JvmConflictsGuard;
import org.jfrog.common.concurrency.MonitoringReentrantLock;
import org.jfrog.common.logging.logback.servlet.LogbackConfigManager;
import org.jfrog.config.ifc.ConfigurationManager;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mamo
 */
public class DummyArtifactoryContext implements ArtifactoryContext {
    private final ApplicationContext applicationContext;
    private Map<Class<?>, Object> beans = Maps.newHashMap();

    public DummyArtifactoryContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void addBean(Object bean, Class<?>... types) {
        for (Class<?> type : types) {
            beans.put(type, bean);
        }
    }

    @Override
    public CentralConfigService getCentralConfig() {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T beanForType(Class<T> type) {
        //Let overridden beans go first
        if (beans.containsKey(type)) {
            return (T) beans.get(type);
        }
        if (AddonsManager.class.equals(type)) {
            return (T) new DummyOssAddonsManager();
        }
        if (type.equals(HaCommonAddon.class)) {
            return (T) new DummyHaCommonAddon();
        }
        if (type.equals(RepositoryService.class)) {
            return (T) Mockito.mock(RepositoryService.class);
        }
        if (type.equals(RepositoryConfigService.class)) {
            return (T) Mockito.mock(RepositoryConfigService.class);
        }
        if (type.equals(CentralConfigService.class)) {
            return (T) Mockito.mock(CentralConfigService.class);
        }
        if (AuthorizationService.class.equals(type)) {
            return (T) Mockito.mock(AuthorizationService.class);
        }
        if (SecurityService.class.equals(type)) {
            return (T) Mockito.mock(SecurityService.class);
        }
        if (CacheExpiry.class.equals(type)) {
            return (T) Mockito.mock(CacheExpiry.class);
        }
        if (PropertiesService.class.equals(type)) {
            return (T) new DummyPropertiesService();
        }
        if (type.equals(SmartRepoAddon.class)) {
            return (T) new SmartRepoAddon() {

                @Override
                public boolean isDefault() {
                    return false;
                }

                @Override
                public boolean supportRemoteStats() {
                    return true;
                }

                @Override
                public void fileDownloadedRemotely(StatsInfo statsInfo, String origin, RepoPath repoPath) {

                }
            };
        }
        return applicationContext.getBean(type);
    }

    @Override
    public <T> T beanForType(String name, Class<T> type) {
        return applicationContext.getBean(name, type);
    }

    @Override
    public <T> Map<String, T> beansForType(Class<T> type) {
        return null;
    }

    @Override
    public RepositoryService getRepositoryService() {
        return beanForType(RepositoryService.class);
    }

    @Override
    public RepositoryConfigService getRepositoryConfigService() {
        return beanForType(RepositoryConfigService.class);
    }

    @Override
    public AuthorizationService getAuthorizationService() {
        return null;
    }

    @Override
    public long getUptime() {
        return 0;
    }

    @Override
    public ArtifactoryHome getArtifactoryHome() {
        return null;
    }

    @Override
    public String getContextId() {
        return null;
    }

    @Override
    public SpringConfigPaths getConfigPaths() {
        return null;
    }

    @Override
    public String getServerId() {
        return "serverId";
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean isCentralConfigImportInProgress() {
        return false;
    }

    @Override
    public boolean isReposImportInProgress() {
        return false;
    }

    @Override
    public boolean isVanillaInstallation() {
        return false;
    }

    @Override
    public boolean isOffline() {
        return false;
    }

    @Override
    public void setOffline() {
    }

    @Override
    public ConfigurationManager getConfigurationManager() {
        return null;
    }

    @Override
    public ConverterManager getConverterManager() {
        return null;
    }

    @Override
    public VersionProvider getVersionProvider() {
        return null;
    }

    @Override
    public LogbackConfigManager getLogbackConfigManager() {
        return null;
    }

    @Override
    public void destroy() {
    }

    @Override
    public void exportTo(ExportSettings settings) {
    }

    @Override
    public void importFrom(ImportSettings settings) {
    }

    private static class DummyHaCommonAddon implements HaCommonAddon {
        private ConflictsGuard conflictsGuard = new JvmConflictsGuard(
                ConstantValues.lockLeaseTime.getLong());

        @Override
        public boolean isHaEnabled() {
            return false;
        }

        @Override
        public boolean isHaConfigured() {
            return false;
        }

        @Override
        public void notifyAsync(HaMessageWorkItem workItem) {

        }

        @Override
        public void notify(HaMessageTopic haMessageTopic, HaMessage haMessage) {
        }

        @Override
        public String getHostId() {
            return null;
        }

        @Override
        public void propagateConfigReload() {
        }

        @Override
        public void updateArtifactoryServerRole() {
        }

        @Override
        public void propagateLicenseChanges() {

        }

        @Override
        public void propagateReplicationListener(ReplicationOwnerModel replicationChannelModel) {

        }

        @Override
        public void propagateRemoveReplicationListener(ReplicationOwnerModel replicationChannelModel) {

        }

        @Override
        public void propagateReplicationEvents(String target, ReplicationEventQueueData queue) {

        }

        @Override
        public void propagateStopSha256Migration(long sleepIntervalMillis) {

        }

        @Override
        public void propagateAddColumnMonitorMigration() {

        }

        @Override
        public ConflictGuard getConflictGuard(String key) {
            return new JvmConflictGuard(new JVMLockWrapper(new MonitoringReentrantLock()));
        }

        @Override
        public ConflictsGuard getConflictsGuard(String mapName) {
            return conflictsGuard;
        }

        @Override
        public SessionRepository<? extends Session> createSessionRepository(int sessionDurationSecs) {
            MapSessionRepository mapSessionRepository = new MapSessionRepository(new ConcurrentHashMap<>());
            mapSessionRepository.setDefaultMaxInactiveInterval(Duration.of(sessionDurationSecs, ChronoUnit.SECONDS));
            return mapSessionRepository;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<ArtifactoryServer> getAllArtifactoryServers() {
            return new ArrayList<>();
        }

        @Override
        public boolean deleteArtifactoryServer(String id) {
            return false;
        }

        @Override
        public boolean artifactoryServerHasHeartbeat(ArtifactoryServer artifactoryServer) {
            return false;
        }

        @Override
        public String getCurrentMemberServerId() {
            return null;
        }

        @Override
        public void propagateDebianUpdateCache(RepoPath path) {

        }

        @Override
        public void propagateOpkgReindexAll(ArtifactoryServer server, String repoKey, boolean async,
                boolean writeProps) {
        }

        @Override
        public void propagateActivateLicense(ArtifactoryServer server, Set<String> skipLicense) {

        }

        @Override
        public void propagatePluginReload() {

        }

        @Override
        public UIDeployPropagationResult propagateUiUploadRequest(String nodeId, String clientAddress, String payload,
                String oauth) {
            return null;
        }

        @Override
        public void propagateArtifactoryEncryptionKeyChanged() {

        }

        @Override
        public <T> List<T> propagateTrafficCollector(long startLong, long endLong, List<String> ipsToFilter,
                List<ArtifactoryServer> servers, Class<T> clazz) {
            return null;
        }

        @Override
        public <T> List<T> propagateTasksList(List<ArtifactoryServer> servers, Class<T> clazz) {
            return null;
        }

        @Override
        public boolean isDefault() {
            return false;
        }
    }

    private static class DummyOssAddonsManager extends OssAddonsManager {

        private DummyOssAddonsManager() {
            context = ContextHelper.get();
        }

        @Override
        public void prepareAddonManager() {
            // Do nothing as this type of AddonsManager requires no preparation.
        }

        @Override
        public boolean isAddonSupported(AddonType addonType) {
            return false;
        }

        @Override
        public boolean isTrialLicense() {
            return false;
        }

        @Override
        public boolean isOssLicensed(String licenseKeyHash) {
            return true;
        }

        @Override
        public ArtifactoryRunningMode getArtifactoryRunningMode() {
            return ArtifactoryRunningMode.OSS;
        }

        @Override
        public boolean isPartnerLicense() {
            return false;
        }

        @Override
        public boolean shouldReAcquireLicense() {
            return false;
        }

        @Override
        public void resetLicenseCache() {

        }

        @Override
        public boolean isUploadRequestBlocked(ArtifactoryRequest request,
                ArtifactoryResponse response) {
            return false;
        }
    }
}
