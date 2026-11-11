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

package org.artifactory.descriptor.config;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.addon.AddonSettings;
import org.artifactory.descriptor.authentication.AuthenticationDescriptor;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.cleanup.CleanupConfigDescriptor;
import org.artifactory.descriptor.distribution.ReleaseBundlesConfig;
import org.artifactory.descriptor.download.DownloadRedirectConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.eula.EulaDescriptor;
import org.artifactory.descriptor.gc.GcConfigDescriptor;
import org.artifactory.descriptor.index.IndexerDescriptor;
import org.artifactory.descriptor.mail.MailServerDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.property.SystemPropertyDescriptor;
import org.artifactory.descriptor.quota.QuotaConfigDescriptor;
import org.artifactory.descriptor.replication.GlobalReplicationsConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.replication.ReplicationBaseDescriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.descriptor.repo.jaxb.FederatedRepositoriesMapAdapter;
import org.artifactory.descriptor.repo.jaxb.LocalRepositoriesMapAdapter;
import org.artifactory.descriptor.repo.jaxb.ReleaseBundlesRepositoriesMapAdapter;
import org.artifactory.descriptor.repo.jaxb.RemoteRepositoriesMapAdapter;
import org.artifactory.descriptor.repo.jaxb.VirtualRepositoriesMapAdapter;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.descriptor.retention.ArtifactRetentionPolicyDescriptor;
import org.artifactory.descriptor.retention.ArtifactRetentionSettings;
import org.artifactory.descriptor.rlm.RlmConfig;
import org.artifactory.descriptor.security.ProxyServices;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.subscription.SubscriptionConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.descriptor.util.DescriptorUtils;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.util.AlreadyExistsException;
import org.artifactory.util.DoesNotExistException;
import org.artifactory.util.IdUtils;
import org.artifactory.util.RepoLayoutUtils;
import org.jfrog.access.common.ServiceType;
import org.jfrog.client.util.PathUtils;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.DiffReferenceable;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Fred Simon
 */
@XmlRootElement(name = "config")
@XmlType(name = "CentralConfigType",
        propOrder = {"repositoriesGeneralConfig", "forceNativeDbLocksMechanismInAutoMode",
                "useOnlyTokenForIdentifyingXrayUser", "serverName", "offlineMode",
                "fileUploadMaxSizeMb", "revision", "addons",
                "mailServer", "xrayConfig", "security", "backups", "indexer", "localRepositoriesMap",
                "remoteRepositoriesMap", "virtualRepositoriesMap", "federatedRepositoriesMap",
                "releaseBundlesRepositoriesMap",
                "proxies", "reverseProxies", "propertySets", "systemProperties", "urlBase", "federatedRepoUrlBase",
                "footer", "repoLayouts", "remoteReplications",
                "localReplications", "gcConfig", "cleanupConfig", "virtualCacheCleanupConfig", "quotaConfig",
                "folderDownloadConfig", "trashcanConfig", "itemsBrowserPagingSize",
                "replicationsConfig", "sumoLogicConfig", "releaseBundlesConfig", "rlmConfig",
                "signedUrlConfig", "downloadRedirectConfig",
                "eulaConfig", "subscriptionConfig", "keyPairs", "retentionSettings",
                "authentication"
        },
        namespace = Descriptor.NS)
@XmlAccessorType(XmlAccessType.FIELD)
@GenerateDiffFunction
public class CentralConfigDescriptorImpl implements MutableCentralConfigDescriptor {

    private static final Logger log = LoggerFactory.getLogger(CentralConfigDescriptorImpl.class);
    private static final String ALREADY_EXISTS_ERR = " already exists";
    private static final String IS_NOT_AVAILABLE_ERR = " is not available ";

    @XmlElement(defaultValue = "false")
    private boolean forceNativeDbLocksMechanismInAutoMode;

    @XmlElement(defaultValue = "true")
    private boolean useOnlyTokenForIdentifyingXrayUser = true;

    @XmlElement(name = "localRepositories", required = true)
    @XmlJavaTypeAdapter(LocalRepositoriesMapAdapter.class)
    @DiffIgnore
    private Map<String, LocalRepoDescriptor> localRepositoriesMap = Maps.newLinkedHashMap();

    @XmlElement(name = "remoteRepositories", required = false)
    @XmlJavaTypeAdapter(RemoteRepositoriesMapAdapter.class)
    @DiffIgnore
    private Map<String, RemoteRepoDescriptor> remoteRepositoriesMap = Maps.newLinkedHashMap();

    @XmlElement(name = "federatedRepositories", required = false)
    @XmlJavaTypeAdapter(FederatedRepositoriesMapAdapter.class)
    @DiffIgnore
    private Map<String, FederatedRepoDescriptor> federatedRepositoriesMap = Maps.newLinkedHashMap();

    @XmlElement(name = "virtualRepositories", required = false)
    @XmlJavaTypeAdapter(VirtualRepositoriesMapAdapter.class)
    @DiffIgnore
    private Map<String, VirtualRepoDescriptor> virtualRepositoriesMap = Maps.newLinkedHashMap();

    @XmlElement(name = "releaseBundlesRepositories", required = false)
    @XmlJavaTypeAdapter(ReleaseBundlesRepositoriesMapAdapter.class)
    @DiffIgnore
    private Map<String, ReleaseBundlesRepoDescriptor> releaseBundlesRepositoriesMap =
            Maps.newLinkedHashMap();

    @XmlElementWrapper(name = "proxies")
    @XmlElement(name = "proxy", required = false)
    @DiffReferenceable
    private List<ProxyDescriptor> proxies = new ArrayList<>();

    @XmlElementWrapper(name = "reverseProxies")
    @XmlElement(name = "reverseProxy", required = false)
    private List<ReverseProxyDescriptor> reverseProxies = new ArrayList<>();

    @XmlElement(defaultValue = "100", required = false)
    private int fileUploadMaxSizeMb = 100;

    @XmlElement(defaultValue = "500", required = true)
    private int itemsBrowserPagingSize = 500;

    @XmlElementWrapper(name = "backups")
    @XmlElement(name = "backup", required = false)
    private List<BackupDescriptor> backups = new ArrayList<>();

    private IndexerDescriptor indexer;

    @XmlElement
    private GlobalReplicationsConfigDescriptor replicationsConfig = new GlobalReplicationsConfigDescriptor();

    /**
     * A name uniquely identifying this artifactory server instance
     */
    @XmlElement
    @NotUnicode
    private String serverName;

    /**
     * if this flag is set all the remote repos will work in offline mode
     */
    @XmlElement(defaultValue = "false", required = false)
    private boolean offlineMode;

    private AddonSettings addons = new AddonSettings();

    @XmlElement
    @Valid
    private MailServerDescriptor mailServer;

    @XmlElement
    private XrayDescriptor xrayConfig;

    /**
     * security might not be present in the xml but we always want to create it
     */
    @XmlElement
    private SecurityDescriptor security = new SecurityDescriptor();

    @XmlElement
    private AuthenticationDescriptor authentication = new AuthenticationDescriptor();

    @XmlElementWrapper(name = "propertySets")
    @XmlElement(name = "propertySet", required = false)
    @DiffReferenceable
    private List<PropertySet> propertySets = new ArrayList<>();

    @XmlElementWrapper(name = "systemProperties")
    @XmlElement(name = "systemProperty", required = false)
    @DiffReferenceable
    private List<SystemPropertyDescriptor> systemProperties = new ArrayList<>();

    @XmlElement
    @NotUnicode
    private String urlBase;

    @XmlElement
    @NotUnicode
    private String federatedRepoUrlBase;

    @XmlElement
    private FolderDownloadConfigDescriptor folderDownloadConfig;

    @XmlElement
    @NotUnicode
    private String footer;

    @XmlElementWrapper(name = "repoLayouts")
    @XmlElement(name = "repoLayout", required = false)
    @DiffReferenceable
    private List<RepoLayout> repoLayouts = Lists.newArrayList();

    @XmlElementWrapper(name = "remoteReplications")
    @XmlElement(name = "remoteReplication", required = false)
    private List<RemoteReplicationDescriptor> remoteReplications = Lists.newArrayList();

    @XmlElementWrapper(name = "localReplications")
    @XmlElement(name = "localReplication", required = false)
    private List<LocalReplicationDescriptor> localReplications = Lists.newArrayList();


    @XmlElement
    private GcConfigDescriptor gcConfig;

    @XmlElement
    private CleanupConfigDescriptor cleanupConfig;

    @XmlElement
    private CleanupConfigDescriptor virtualCacheCleanupConfig;

    @XmlElement
    private DownloadRedirectConfigDescriptor downloadRedirectConfig = new DownloadRedirectConfigDescriptor();

    private QuotaConfigDescriptor quotaConfig;

    @XmlElement
    private TrashcanConfigDescriptor trashcanConfig = new TrashcanConfigDescriptor();

    private SumoLogicConfigDescriptor sumoLogicConfig = new SumoLogicConfigDescriptor();

    private ReleaseBundlesConfig releaseBundlesConfig = new ReleaseBundlesConfig();

    private RlmConfig rlmConfig = new RlmConfig();

    private SignedUrlConfig signedUrlConfig = new SignedUrlConfig();

    @XmlElement
    private long revision;

    @XmlElement(name = "eula", required = false)
    private EulaDescriptor eulaConfig;

    @XmlElement(name = "subscription", required = false)
    private SubscriptionConfig subscriptionConfig;

    @XmlElement(name = "repositoriesGeneralConfig", required = false)
    private RepositoriesGeneralConfigDescriptor repositoriesGeneralConfig = new RepositoriesGeneralConfigDescriptor();

    @XmlElementWrapper(name = "keyPairs")
    @XmlElement(name = "keyPair", required = false)
    @DiffReferenceable
    private List<KeyPairDescriptor> keyPairs = new ArrayList<>();

    @XmlElement(name = "retentionSettings", required = false)
    private ArtifactRetentionSettings retentionSettings = new ArtifactRetentionSettings();

    @Override
    public Map<String, LocalRepoDescriptor> getLocalRepositoriesMap() {
        return filterBundleRepositories(localRepositoriesMap);
    }

    @Override
    public Map<String, LocalRepoDescriptor> getLocalAndFederatedRepositoriesMap() {
        Map<String, LocalRepoDescriptor> aggregatedMap = new HashMap<>();
        aggregatedMap.putAll(localRepositoriesMap);
        aggregatedMap.putAll(federatedRepositoriesMap);
        return filterBundleRepositories(aggregatedMap);
    }

    @Override
    public void setLocalRepositoriesMap(Map<String, LocalRepoDescriptor> localRepositoriesMap) {
        this.localRepositoriesMap = Optional.ofNullable(localRepositoriesMap).orElseGet(Maps::newLinkedHashMap);
    }

    @Override
    public Map<String, FederatedRepoDescriptor> getFederatedRepositoriesMap() {
        return filterBundleRepositories(federatedRepositoriesMap);
    }

    @Override
    public void setFederatedRepositoriesMap(Map<String, FederatedRepoDescriptor> federatedRepositoriesMap) {
        this.federatedRepositoriesMap = Optional.ofNullable(federatedRepositoriesMap).orElseGet(Maps::newLinkedHashMap);
    }

    @Override
    public Map<String, RemoteRepoDescriptor> getRemoteRepositoriesMap() {
        return filterBundleRepositories(remoteRepositoriesMap);
    }

    @Override
    public void setRemoteRepositoriesMap(Map<String, RemoteRepoDescriptor> remoteRepositoriesMap) {
        this.remoteRepositoriesMap = Optional.ofNullable(remoteRepositoriesMap).orElseGet(Maps::newLinkedHashMap);
    }

    @Override
    public Map<String, VirtualRepoDescriptor> getVirtualRepositoriesMap() {
        return filterBundleRepositories(virtualRepositoriesMap);
    }

    @Override
    public void setVirtualRepositoriesMap(Map<String, VirtualRepoDescriptor> virtualRepositoriesMap) {
        this.virtualRepositoriesMap = Optional.ofNullable(virtualRepositoriesMap).orElseGet(Maps::newLinkedHashMap);
    }

    public Map<String, ReleaseBundlesRepoDescriptor> getReleaseBundlesRepositoriesMap() {
        return releaseBundlesRepositoriesMap;
    }

    public void setReleaseBundlesRepositoriesMap(
            Map<String, ReleaseBundlesRepoDescriptor> releaseBundlesRepositoriesMap) {
        this.releaseBundlesRepositoriesMap = releaseBundlesRepositoriesMap;
    }

    @Override
    public List<ProxyDescriptor> getProxies() {
        return proxies;
    }

    @Override
    public void setProxies(List<ProxyDescriptor> proxies) {
        this.proxies = Optional.ofNullable(proxies).orElseGet(ArrayList::new);
    }

    @Override
    public List<ReverseProxyDescriptor> getReverseProxies() {
        return reverseProxies;
    }

    public ArtifactRetentionSettings getRetentionSettings() {
        return retentionSettings;
    }

    @Override
    public int getRepoCount() {
        return getLocalRepositoriesMap().size() +
                getRemoteRepositoriesMap().size() +
                getFederatedRepositoriesMap().size() +
                getVirtualRepositoriesMap().size() +
                getReleaseBundlesRepositoriesMap().size();
    }

    public void setRetentionSettings(ArtifactRetentionSettings retentionSettings) {
        this.retentionSettings = retentionSettings;
    }

    @Override
    public void setReverseProxies(List<ReverseProxyDescriptor> reverseProxies) {
        this.reverseProxies = Optional.ofNullable(reverseProxies).orElseGet(ArrayList::new);
    }

    @Override
    public ProxyDescriptor getDefaultProxy() {
        return proxies.stream()
                .filter(proxy -> (proxy != null) && isArtifactoryDefaultProxy(proxy))
                .findFirst()
                .orElse(null);
    }

    @Override
    public int getFileUploadMaxSizeMb() {
        return fileUploadMaxSizeMb;
    }

    @Override
    public int getItemsBrowserPagingSize() {
        return itemsBrowserPagingSize;
    }

    @Override
    public void setItemsBrowserPagingSize(int itemsBrowserPagingSize) {
        this.itemsBrowserPagingSize = itemsBrowserPagingSize;
    }

    @Override
    public void setFileUploadMaxSizeMb(int fileUploadMaxSizeMb) {
        this.fileUploadMaxSizeMb = fileUploadMaxSizeMb;
    }

    @Override
    public long getRevision() {
        return revision;
    }

    @Override
    public void setRevision(long revision) {
        this.revision = revision;
    }

    @Override
    public List<BackupDescriptor> getBackups() {
        return backups;
    }

    @Override
    public void setBackups(List<BackupDescriptor> backups) {
        this.backups = Optional.ofNullable(backups).orElseGet(ArrayList::new);
    }

    @Override
    public IndexerDescriptor getIndexer() {
        return indexer;
    }

    @Override
    public void setIndexer(IndexerDescriptor mavenIndexer) {
        this.indexer = mavenIndexer;
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public void setServerName(String serverName) {
        this.serverName = StringUtils.stripToNull(serverName);
    }

    @Override
    public SecurityDescriptor getSecurity() {
        return security;
    }

    @Override
    public void setSecurity(SecurityDescriptor security) {
        if (security == null) {
            security = new SecurityDescriptor();
        }
        this.security = security;
    }

    @Override
    public AuthenticationDescriptor getAuthentication() {
        return authentication;
    }

    @Override
    public void setAuthentication(AuthenticationDescriptor authentication) {
        if (authentication == null) {
            authentication = new AuthenticationDescriptor();
        }
        this.authentication = authentication;
    }

    @Override
    public AddonSettings getAddons() {
        return addons;
    }

    @Override
    public void setAddons(AddonSettings addons) {
        this.addons = addons;
    }

    @Override
    public MailServerDescriptor getMailServer() {
        return mailServer;
    }

    @Override
    public void setMailServer(MailServerDescriptor mailServer) {
        this.mailServer = mailServer;
    }

    @Override
    public XrayDescriptor getXrayConfig() {
        return xrayConfig;
    }

    @Override
    public void setXrayConfig(XrayDescriptor xrayConfig) {
        this.xrayConfig = xrayConfig;
    }

    @Override
    public List<PropertySet> getPropertySets() {
        return propertySets;
    }

    @Override
    public List<SystemPropertyDescriptor> getSystemProperties() {
        return systemProperties;
    }

    @Override
    public void setPropertySets(List<PropertySet> propertySets) {
        this.propertySets = Optional.ofNullable(propertySets).orElseGet(ArrayList::new);
    }

    @Override
    public void setSystemProperties(List<SystemPropertyDescriptor> systemProperties) {
        this.systemProperties = systemProperties;
    }

    @Override
    public String getUrlBase() {
        return urlBase;
    }

    @Override
    public String getFederatedRepoUrlBase() {
        return federatedRepoUrlBase;
    }

    @Override
    public String getFederatedRepoUrlBaseOrDefault() {
        if (StringUtils.isNotBlank(getFederatedRepoUrlBase())) {
            return getFederatedRepoUrlBase();
        } else {
            return getUrlBase();
        }
    }

    @Override
    public String getJFrogUrlBase(int listeningPort) {
        if (StringUtils.isNotBlank(urlBase)) {
            URL url;
            try {
                url = new URL(urlBase);
            } catch (MalformedURLException e) {
                log.error(e.getMessage(), e);
                return getUrlBase();
            }
            int port = url.getPort();
            if (port == listeningPort) {
                port = ArtifactoryHome.get().getSysConfig().helper().getRouterExternalPort();
            }
            return new URIBuilder().setScheme(url.getProtocol())
                    .setHost(url.getHost())
                    .setPort(port).toString();
        }
        return null;
    }

    @Override
    public void setUrlBase(String urlBase) {
        this.urlBase = PathUtils.trimTrailingSlashes(urlBase);
    }

    @Override
    public void setFederatedRepoUrlBase(String urlBase) {
        this.federatedRepoUrlBase = PathUtils.trimTrailingSlashes(urlBase);
    }

    @Override
    public void removeReferences(RepoConfig removedRepo) {
        String removedRepoKey = removedRepo.getKey();
        if (removedRepo.isReal()) {
            // remove the repository from any backup exclude list
            for (BackupDescriptor backup : getBackups()) {
                backup.removeExcludedRepository(removedRepoKey);
            }
        }

        // remove from the indexer include list
        IndexerDescriptor indexerDescriptor = getIndexer();
        if (indexerDescriptor != null) {
            indexerDescriptor.removeIncludedRepository(removedRepoKey);
        }

        if (removedRepo instanceof RemoteRepoConfig) {
            RemoteReplicationDescriptor existingReplication = getRemoteReplication(removedRepoKey);
            if (existingReplication != null) {
                removeRemoteReplication(existingReplication);
            }
        }

        if (removedRepo instanceof LocalConfig) {
            List<LocalReplicationDescriptor> existingReplications = getMultiLocalReplications(removedRepoKey);
            if (existingReplications != null && !existingReplications.isEmpty()) {
                existingReplications.forEach(this::removeLocalReplication);
            }
        }

        //Remove repo's reverse proxy config
        getReverseProxies()
                .forEach(reverseProxyDescriptor -> reverseProxyDescriptor.deleteReverseProxyConfig(removedRepoKey));
    }

    @Override
    public String getFooter() {
        return footer;
    }

    public void setFooter(String footer) {
        this.footer = footer;
    }

    @Override
    public boolean isKeyAvailable(String key) {
        return !(isProxyExists(key) ||
                isReverseProxyKey(key) ||
                isBackupExists(key) ||
                isPropertySetExists(key) ||
                isRepoLayoutExists(key) ||
                isKeyPairExists(key) ||
                getRetentionPolicy(key) != null);
    }

    @Override
    public void addLocalRepository(LocalRepoDescriptor localRepoDescriptor)
            throws AlreadyExistsException {
        String repoKey = localRepoDescriptor.getKey();
        localRepositoriesMap.put(repoKey, localRepoDescriptor);
    }

    @Override
    public void addFederatedRepository(FederatedRepoDescriptor federatedRepoDescriptor)
            throws AlreadyExistsException {
        String repoKey = federatedRepoDescriptor.getKey();
        federatedRepositoriesMap.put(repoKey, federatedRepoDescriptor);
    }

    @Override
    public void addRemoteRepository(RemoteRepoDescriptor remoteRepoDescriptor) {
        addOrUpdateRemoteRepository(remoteRepoDescriptor, false);
    }

    @Override
    public void updateRemoteRepository(RemoteRepoDescriptor remoteRepoDescriptor) {
        addOrUpdateRemoteRepository(remoteRepoDescriptor, true);
    }

    @Override
    public boolean conditionallyAddToBackups(String realRepoKey) {
        // Conditionally add the repository to any backup exclude list
        boolean addToBackups = false;
        for (BackupDescriptor backup : getBackups()) {
            addToBackups = backup.addExcludedRepository(realRepoKey) || addToBackups;
        }
        return addToBackups;
    }

    @Override
    public void addVirtualRepository(VirtualRepoDescriptor virtualRepoDescriptor) {
        String repoKey = virtualRepoDescriptor.getKey();
        virtualRepositoriesMap.put(repoKey, virtualRepoDescriptor);
    }

    @Override
    public boolean isProxyExists(String proxyKey) {
        return getProxy(proxyKey) != null;
    }

    @Override
    public void addProxy(ProxyDescriptor proxyDescriptor, boolean shouldUpdateRemotes) {
        String proxyKey = proxyDescriptor.getKey();
        String errorMessagePrefix = "Proxy Key " + proxyKey;
        if (isProxyExists(proxyKey)) {
            throw new AlreadyExistsException(errorMessagePrefix + ALREADY_EXISTS_ERR);
        }
        if (!isKeyAvailable(proxyKey)) {
            throw new AlreadyExistsException(errorMessagePrefix + IS_NOT_AVAILABLE_ERR);
        }
        DescriptorUtils.assertNoSpecialCharactersAndValidXmlName(proxyKey, errorMessagePrefix);
        proxyChanged(proxyDescriptor, shouldUpdateRemotes);
        proxies.add(proxyDescriptor);
        log.info("Proxy '{}' has been added", proxyDescriptor.getKey());
    }

    @Override
    public ProxyDescriptor removeProxy(String proxyKey) {
        ProxyDescriptor proxyDescriptor = getProxy(proxyKey);
        if (proxyDescriptor == null) {
            return null;
        }

        // remove the proxy from the proxies list
        proxies.remove(proxyDescriptor);

        for (LocalReplicationDescriptor localReplication : localReplications) {
            localReplication.setProxy(null);
        }

        if (sumoLogicConfig.getProxy() != null && proxyKey.equals(sumoLogicConfig.getProxy().getKey())) {
            sumoLogicConfig.setProxy(null);
        }

        return proxyDescriptor;
    }

    @Override
    public boolean isReverseProxyExists(String key) {
        return getReverseProxy(key) != null;
    }

    @Override
    public void addReverseProxy(ReverseProxyDescriptor descriptor) {
        String proxyKey = descriptor.getKey();
        String errorMessagePrefix = "Reverse Proxy " + proxyKey;
        if (isReverseProxyExists(proxyKey)) {
            throw new AlreadyExistsException(errorMessagePrefix + ALREADY_EXISTS_ERR);
        }
        DescriptorUtils.assertNoSpecialCharactersAndValidXmlName(proxyKey, errorMessagePrefix);
        reverseProxies.add(descriptor);
    }

    @Override
    public void updateReverseProxy(ReverseProxyDescriptor descriptor) {
        if (descriptor != null) {
            removeCurrentReverseProxy();
            addReverseProxy(descriptor);
        }
    }

    @Override
    public ReverseProxyDescriptor removeReverseProxy(String key) {
        ReverseProxyDescriptor reverseProxy = getReverseProxy(key);
        if (reverseProxy == null) {
            return null;
        }

        reverseProxies.remove(reverseProxy);
        return reverseProxy;
    }

    @Override
    public void removeCurrentReverseProxy() {
        if (!reverseProxies.isEmpty()) {
            reverseProxies.clear();
        }
    }

    @Override
    public void proxyChanged(ProxyDescriptor proxy, boolean shouldUpdateRemotes) {
        // Update the Artifactory relevant fields
        if (isArtifactoryDefaultProxy(proxy)) {
            sumoLogicConfig.setProxy(proxy);
        }

        // If services are set, removing the same service from all other proxies if any, or inverting the default proxy
        if (!proxy.getServicesList().isEmpty()) {
            handleProxyServices(proxy);
        }

        // If its platform default - unset default from all other proxies if any, and remove services
        if (proxy.isPlatformDefault()) {
            handleDefaultProxy(proxy);
            proxy.setServices(null);
        }
        log.info("Proxy '{}' has been altered", proxy.getKey());
    }

    private boolean isReverseProxyKey(String key) {
        return key != null && List.of("direct", "apache", "nginx").stream().anyMatch(key::equalsIgnoreCase);
    }

    private void handleDefaultProxy(ProxyDescriptor proxy) {
        for (ProxyDescriptor proxyDescriptor : proxies) {
            if (!proxy.equals(proxyDescriptor)) {
                proxyDescriptor.setPlatformDefault(false);
                proxyDescriptor.setServices(null);
            }
        }
    }

    private void handleProxyServices(ProxyDescriptor proxy) {
        for (ProxyDescriptor proxyDescriptor : proxies) {
            if (!proxy.equals(proxyDescriptor)) {
                Set<String> allServices;
                if (proxyDescriptor.isPlatformDefault()) {
                    // Set services to be ALL except the proxy's services
                    allServices = Sets.newHashSet(ProxyServices.VALID_PROXY_SERVICES);
                } else {
                    // Set services to be the previous entry, minus the new entry
                    allServices = proxyDescriptor.getServicesList();
                }
                allServices.removeAll(proxy.getServicesList());
                proxyDescriptor.setServices(String.join(",", allServices));
                // Remove default of ALL other proxies, as since services exists, there is no one default
                proxyDescriptor.setPlatformDefault(false);
            }
        }
    }

    /**
     * Determines if the proxy descriptor is considered an Artifactory proxy (either Platform default, or the proxy's
     * services list contain the {@link ServiceType#ARTIFACTORY} service.
     */
    private boolean isArtifactoryDefaultProxy(ProxyDescriptor proxy) {
        boolean isArtifactoryServiceExists = proxy.getServicesList().contains(ServiceType.ARTIFACTORY);
        boolean isPlatformDefault = proxy.isPlatformDefault();
        log.debug("Proxy {}: platformDefault: {}, services: {}", proxy.getKey(), isPlatformDefault,
                proxy.getServices());
        return (isArtifactoryServiceExists || isPlatformDefault);
    }

    private void updateExistingLocalReplications(ProxyDescriptor proxy) {
        ProxyDescriptor previousDefaultProxy = findPreviousProxyDescriptor(proxy);
        for (LocalReplicationDescriptor localReplication : localReplications) {
            if (!localReplication.isDisableProxy()) {
                ProxyDescriptor existingProxy = localReplication.getProxy();
                if (existingProxy == null || existingProxy.equals(previousDefaultProxy)) {
                    localReplication.setProxy(proxy);
                }
            }
        }
    }

    private ProxyDescriptor findPreviousProxyDescriptor(final ProxyDescriptor proxyDescriptor) {
        return proxies.stream()
                .filter(input -> (input != null) && isArtifactoryDefaultProxy(input) &&
                        !input.getKey().equals(proxyDescriptor.getKey()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean isBackupExists(String backupKey) {
        return getBackup(backupKey) != null;
    }

    @Override
    public FolderDownloadConfigDescriptor getFolderDownloadConfig() {
        return folderDownloadConfig;
    }

    @Override
    public void setFolderDownloadConfig(FolderDownloadConfigDescriptor folderDownloadConfig) {
        this.folderDownloadConfig = folderDownloadConfig;
    }

    @Override
    public TrashcanConfigDescriptor getTrashcanConfig() {
        return trashcanConfig;
    }

    @Override
    public void setTrashcanConfig(TrashcanConfigDescriptor trashcanConfig) {
        this.trashcanConfig = trashcanConfig;
    }

    @Override
    public SumoLogicConfigDescriptor getSumoLogicConfig() {
        return sumoLogicConfig;
    }

    @Override
    public void setSumoLogicConfig(SumoLogicConfigDescriptor sumoLogicConfig) {
        this.sumoLogicConfig = sumoLogicConfig;
    }

    @Override
    public ReleaseBundlesConfig getReleaseBundlesConfig() {
        return releaseBundlesConfig;
    }

    @Override
    public void setReleaseBundlesConfig(ReleaseBundlesConfig releaseBundlesConfig) {
        this.releaseBundlesConfig = releaseBundlesConfig;
    }

    @Override
    public RlmConfig getRlmConfig() {
        return rlmConfig;
    }

    @Override
    public void setRlmConfig(RlmConfig rlmConfig) {
        this.rlmConfig = rlmConfig;
    }

    @Override
    public SignedUrlConfig getSignedUrlConfig() {
        return signedUrlConfig;
    }

    @Override
    public void setSignedUrlConfig(SignedUrlConfig signedUrlConfig) {
        this.signedUrlConfig = signedUrlConfig;
    }

    @Override
    public void addBackup(BackupDescriptor backupDescriptor) {
        String backupKey = backupDescriptor.getKey();
        String errorMessagePrefix = "Backup " + backupKey;
        if (isBackupExists(backupKey)) {
            throw new AlreadyExistsException(errorMessagePrefix + ALREADY_EXISTS_ERR);
        }
        if (!isKeyAvailable(backupKey)) {
            throw new AlreadyExistsException(errorMessagePrefix + IS_NOT_AVAILABLE_ERR);
        }
        DescriptorUtils.assertNoSpecialCharactersAndValidXmlName(backupKey, errorMessagePrefix);
        backups.add(backupDescriptor);
    }

    @Override
    public BackupDescriptor removeBackup(String backupKey) {
        BackupDescriptor backupDescriptor = getBackup(backupKey);
        if (backupDescriptor == null) {
            return null;
        }

        // remove the backup from the backups list
        backups.remove(backupDescriptor);

        return backupDescriptor;
    }

    @Override
    public boolean isPropertySetExists(String propertySetName) {
        return getPropertySet(propertySetName) != null;
    }

    @Override
    public boolean isSystemPropertyExists(String sysPropName) {
        return getSystemProperty(sysPropName) != null;
    }

    @Override
    public void addPropertySet(PropertySet propertySet) {
        String propertySetName = propertySet.getName();
        String errorMessagePrefix = "Property set " + propertySetName;
        if (isPropertySetExists(propertySetName)) {
            throw new AlreadyExistsException(errorMessagePrefix + ALREADY_EXISTS_ERR);
        }
        if (!isKeyAvailable(propertySetName)) {
            throw new AlreadyExistsException(errorMessagePrefix + IS_NOT_AVAILABLE_ERR);
        }
        DescriptorUtils.assertNoSpecialCharactersAndValidXmlName(propertySetName, errorMessagePrefix);
        propertySets.add(propertySet);
    }

    @Override
    public void addSystemProperty(SystemPropertyDescriptor systemPropertyDescriptor) {
        String propName = systemPropertyDescriptor.getKey();
        if (isSystemPropertyExists(propName)) {
            String message = "System property " + systemPropertyDescriptor + ALREADY_EXISTS_ERR;
            log.error(message);
            throw new AlreadyExistsException(message);
        }
        log.debug("Adding system property {}", systemPropertyDescriptor.getKey());
        systemProperties.add(systemPropertyDescriptor);
    }

    @Override
    public PropertySet removePropertySet(String propertySetName) {
        PropertySet propertySet = getPropertySet(propertySetName);
        if (propertySet == null) {
            return null;
        }

        //Remove the property set from the property sets list
        propertySets.remove(propertySet);
        return propertySet;
    }

    @Override
    public SystemPropertyDescriptor removeSystemProperty(String systemPropertyName) {
        SystemPropertyDescriptor systemProperty = getSystemProperty(systemPropertyName);
        if (systemProperty == null) {
            log.debug("No property with key {} found", systemPropertyName);
            return null;
        }
        log.debug("Removing system property {}", systemPropertyName);
        //Remove the property set from the property sets list
        systemProperties.remove(systemProperty);
        return systemProperty;
    }

    @Override
    public boolean isOfflineMode() {
        return offlineMode;
    }

    @Override
    public boolean isForceNativeDbLocksMechanismInAutoMode() {
        return forceNativeDbLocksMechanismInAutoMode;
    }

    @Override
    public boolean isUseOnlyTokenForIdentifyingXrayUser() {
        return useOnlyTokenForIdentifyingXrayUser;
    }


    @Override
    public void setOfflineMode(boolean offlineMode) {
        this.offlineMode = offlineMode;
    }

    @Override
    public void setForceNativeDbLocksMechanismInAutoMode(boolean forceNativeDbLocksMechanismInAutoMode) {
        this.forceNativeDbLocksMechanismInAutoMode = forceNativeDbLocksMechanismInAutoMode;
    }

    @Override
    public void setUseOnlyTokenForIdentifyingXrayUser(boolean useOnlyTokenForIdentifyingXrayUser) {
        this.useOnlyTokenForIdentifyingXrayUser = useOnlyTokenForIdentifyingXrayUser;
    }

    @Override
    public ProxyDescriptor getProxy(String proxyKey) {
        for (ProxyDescriptor proxy : proxies) {
            if (proxy.getKey().equals(proxyKey)) {
                return proxy;
            }
        }
        return null;
    }

    @Override
    public ReverseProxyDescriptor getReverseProxy(String key) {
        for (ReverseProxyDescriptor reverseProxy : reverseProxies) {
            if (reverseProxy.getKey().equals(key)) {
                return reverseProxy;
            }
        }
        return null;
    }

    @Override
    public ReverseProxyDescriptor getCurrentReverseProxy() {
        if (reverseProxies != null && !reverseProxies.isEmpty()) {
            return reverseProxies.get(0);
        }
        return null;
    }

    @Override
    public BackupDescriptor getBackup(String backupKey) {
        for (BackupDescriptor backup : backups) {
            if (backup.getKey().equals(backupKey)) {
                return backup;
            }
        }
        return null;
    }

    private PropertySet getPropertySet(String propertySetName) {
        for (PropertySet propertySet : propertySets) {
            if (propertySet.getName().equals(propertySetName)) {
                return propertySet;
            }
        }

        return null;
    }

    @Override
    public SystemPropertyDescriptor getSystemProperty(String propertySetName) {
        for (SystemPropertyDescriptor systemProperty : systemProperties) {
            if (systemProperty.getKey().equals(propertySetName)) {
                log.trace("Found existing system property with key {}", propertySetName);
                return systemProperty;
            }
        }
        log.debug("No system property with key {} found", propertySetName);
        return null;
    }

    @Override
    public List<RepoLayout> getRepoLayouts() {
        return repoLayouts;
    }

    @Override
    public void setRepoLayouts(List<RepoLayout> repoLayouts) {
        this.repoLayouts = Optional.ofNullable(repoLayouts).orElseGet(Lists::newArrayList);
    }

    @Override
    public boolean isRepoLayoutExists(String repoLayoutName) {
        for (RepoLayout repoLayout : repoLayouts) {
            if (repoLayout.getName().equals(repoLayoutName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void addRepoLayout(RepoLayout repoLayout) {
        String repoLayoutName = repoLayout.getName();
        String errorMessagePrefix = "Repo Layout " + repoLayoutName;
        if (isRepoLayoutExists(repoLayoutName)) {
            throw new AlreadyExistsException(errorMessagePrefix + ALREADY_EXISTS_ERR);
        }
        if (!isKeyAvailable(repoLayoutName)) {
            throw new AlreadyExistsException(errorMessagePrefix + IS_NOT_AVAILABLE_ERR);
        }
        DescriptorUtils.assertNoSpecialCharactersAndValidXmlName(repoLayoutName, errorMessagePrefix);
        repoLayouts.add(repoLayout);
    }

    @Override
    public RepoLayout removeRepoLayout(String repoLayoutName) {
        RepoLayout repoLayout = getRepoLayout(repoLayoutName);
        if (repoLayout == null) {
            return null;
        }

        repoLayouts.remove(repoLayout);


        Collection<LocalRepoDescriptor> localRepoDescriptorCollection = localRepositoriesMap.values();
        for (LocalRepoDescriptor localRepoDescriptor : localRepoDescriptorCollection) {
            if (repoLayout.equals(localRepoDescriptor.getRepoLayout())) {
                localRepoDescriptor.setRepoLayout(RepoLayoutUtils.MAVEN_2_DEFAULT);
            }
        }

        Collection<RemoteRepoDescriptor> remoteRepoDescriptors = remoteRepositoriesMap.values();
        for (RemoteRepoDescriptor remoteRepoDescriptor : remoteRepoDescriptors) {
            if (repoLayout.equals(remoteRepoDescriptor.getRepoLayout())) {
                remoteRepoDescriptor.setRepoLayout(RepoLayoutUtils.MAVEN_2_DEFAULT);
            }
            if (repoLayout.equals(remoteRepoDescriptor.getRemoteRepoLayout())) {
                remoteRepoDescriptor.setRemoteRepoLayout(null);
            }
        }

        Collection<VirtualRepoDescriptor> virtualRepoDescriptors = virtualRepositoriesMap.values();
        for (VirtualRepoDescriptor virtualRepoDescriptor : virtualRepoDescriptors) {
            if (repoLayout.equals(virtualRepoDescriptor.getRepoLayout())) {
                virtualRepoDescriptor.setRepoLayout(null);
            }
        }

        return repoLayout;
    }

    @Nullable
    @Override
    public RepoLayout getRepoLayout(String repoLayoutName) {
        for (RepoLayout repoLayout : repoLayouts) {
            if (repoLayout.getName().equals(repoLayoutName)) {
                return repoLayout;
            }
        }

        return null;
    }

    @Override
    public boolean isRemoteReplicationExists(RemoteReplicationDescriptor descriptor) {
        return remoteReplications.contains(descriptor);
    }

    @Override
    public boolean isLocalReplicationExists(LocalReplicationDescriptor descriptor) {
        return localReplications.contains(descriptor);
    }

    @Override
    public List<RemoteReplicationDescriptor> getRemoteReplications() {
        return remoteReplications;
    }

    @Override
    public void setRemoteReplications(List<RemoteReplicationDescriptor> replicationDescriptors) {
        remoteReplications = Optional.ofNullable(replicationDescriptors).orElseGet(Lists::newArrayList);
    }

    @Override
    public List<LocalReplicationDescriptor> getLocalReplications() {
        return localReplications;
    }

    @Override
    public void setLocalReplications(List<LocalReplicationDescriptor> localReplications) {
        this.localReplications = Optional.ofNullable(localReplications).orElseGet(Lists::newArrayList);
    }

    @Override
    public RemoteReplicationDescriptor getRemoteReplication(String replicatedRepoKey) {
        return getReplication(replicatedRepoKey, remoteReplications);
    }

    @Override
    public LocalReplicationDescriptor getLocalReplication(String replicatedRepoKey) {
        return getReplication(replicatedRepoKey, localReplications);
    }

    @Override
    public LocalReplicationDescriptor getEnabledLocalReplication(String replicatedRepoKey) {
        return get1stEnableLocalReplication(replicatedRepoKey, localReplications);
    }

    @Override
    public int getTotalNumOfActiveLocalReplication(String replicatedRepoKey) {
        return getNumOfActiveLocalReplication(replicatedRepoKey, localReplications);
    }

    @Override
    public Set<String> getReplicationKeysByRepoKey(String replicatedRepoKey) {
        return getReplicationKeysByRepoKey(replicatedRepoKey, localReplications);
    }

    @Override
    public LocalReplicationDescriptor getLocalReplication(String replicatedRepoKey, String replicateRepoUrl) {
        return getSpecificLocalReplication(replicatedRepoKey, replicateRepoUrl, localReplications);
    }

    @Override
    public void addRemoteReplication(RemoteReplicationDescriptor replDesc) {
        if (StringUtils.isBlank(replDesc.getReplicationKey())) {
            replDesc.setReplicationKey(IdUtils.createReplicationKey(replDesc.getRepoKey(), null));
        }
        addReplication(replDesc, remoteReplications);
    }

    @Override
    public void addLocalReplication(LocalReplicationDescriptor replDesc) {
        if (StringUtils.isBlank(replDesc.getReplicationKey())) {
            replDesc.setReplicationKey(IdUtils.createReplicationKey(replDesc.getRepoKey(), replDesc.getUrl()));
        }
        addLocalReplication(replDesc, localReplications);
    }

    @Override
    public void removeRemoteReplication(RemoteReplicationDescriptor replicationDescriptor) {
        if (replicationDescriptor == null) {
            return;
        }
        removeReplication(replicationDescriptor, remoteReplications);
    }

    @Override
    public void removeLocalReplication(LocalReplicationDescriptor replicationDescriptor) {
        if (replicationDescriptor == null) {
            return;
        }
        removeReplication(replicationDescriptor, localReplications);
    }

    @Override
    public void setGlobalReplicationConfig(GlobalReplicationsConfigDescriptor globalReplicationConfig) {
        this.replicationsConfig = globalReplicationConfig;
    }

    @Override
    public String getServerUrlForEmail() {
        String serverUrl = "";
        if (mailServer != null) {
            String artifactoryUrl = mailServer.getArtifactoryUrl();
            if (StringUtils.isNotBlank(artifactoryUrl)) {
                serverUrl = artifactoryUrl;
            }
        }

        if (StringUtils.isBlank(serverUrl) && StringUtils.isNotBlank(urlBase)) {
            serverUrl = urlBase;
        }

        if (StringUtils.isNotBlank(serverUrl) && !serverUrl.endsWith("/")) {
            serverUrl += "/";
        }

        return serverUrl;
    }

    @Override
    public GcConfigDescriptor getGcConfig() {
        return gcConfig;
    }

    @Override
    public void setGcConfig(GcConfigDescriptor gcConfig) {
        this.gcConfig = gcConfig;
    }

    @Override
    public CleanupConfigDescriptor getCleanupConfig() {
        return cleanupConfig;
    }

    @Override
    public void setCleanupConfig(CleanupConfigDescriptor cleanupConfigDescriptor) {
        this.cleanupConfig = cleanupConfigDescriptor;
    }

    @Override
    public QuotaConfigDescriptor getQuotaConfig() {
        return quotaConfig;
    }

    @Override
    public void setQuotaConfig(QuotaConfigDescriptor descriptor) {
        this.quotaConfig = descriptor;
    }

    /**
     * Local repository can contain multiple replications.
     * Returns a map that each repo key (is the map key) contains a map of the replications for that repository per
     * url.
     * Map key -> String repo key
     * Map<String, LocalReplicationDescriptor> - url, local replication descriptor
     *
     * @return - the local replications map per repo.
     */
    @Override
    public Map<String, Map<String, LocalReplicationDescriptor>> getLocalReplicationsMap() {
        Map<String, Map<String, LocalReplicationDescriptor>> localReplicationsMap = Maps.newHashMap();
        for (LocalReplicationDescriptor localReplication : localReplications) {
            String repoKey = localReplication.getRepoKey();
            Map<String, LocalReplicationDescriptor> urlPerReplication =
                    localReplicationsMap.computeIfAbsent(repoKey, k -> Maps.newHashMap());
            urlPerReplication.put(localReplication.getUrl(), localReplication);
        }

        return localReplicationsMap;
    }

    @Override
    public Map<String, LocalReplicationDescriptor> getSingleReplicationPerRepoMap() {
        Map<String, LocalReplicationDescriptor> localReplicationsMap = Maps.newHashMap();
        for (LocalReplicationDescriptor localReplication : localReplications) {
            localReplicationsMap.put(localReplication.getRepoKey(), localReplication);
        }
        return localReplicationsMap;
    }

    @Override
    public Map<String, LocalReplicationDescriptor> getLocalReplicationsPerRepoMap(String repoName) {
        Map<String, LocalReplicationDescriptor> localReplicationsMap = new HashMap();
        for (LocalReplicationDescriptor localReplication : localReplications) {
            if (localReplication.getRepoKey().equals(repoName)) {
                localReplicationsMap.put(localReplication.getUrl(), localReplication);
            }
        }
        return localReplicationsMap;
    }

    @Override
    public Map<String, RemoteReplicationDescriptor> getRemoteReplicationsPerRepoMap(String repoName) {
        Map<String, RemoteReplicationDescriptor> localReplicationsMap = new HashMap();
        for (RemoteReplicationDescriptor remoteReplication : remoteReplications) {
            if (remoteReplication.getRepoKey().equals(repoName)) {
                localReplicationsMap.put(remoteReplication.getRepoKey(), remoteReplication);
            }
        }
        return localReplicationsMap;
    }

    @Override
    public List<String> getLocalReplicationsUniqueKeyForProperty(String repoName) {
        List<String> localReplicationsList = new ArrayList<>();
        for (LocalReplicationDescriptor localReplication : localReplications) {
            if (localReplication.getRepoKey().equals(repoName)) {
                String uniqueKey = localReplication.getUrl().replaceAll("^(http|https)://", "_").replaceAll("/|:", "_");
                localReplicationsList.add(uniqueKey);
            }
        }
        return localReplicationsList;
    }

    @Override
    public CleanupConfigDescriptor getVirtualCacheCleanupConfig() {
        return virtualCacheCleanupConfig;
    }

    @Override
    public void setVirtualCacheCleanupConfig(CleanupConfigDescriptor virtualCacheCleanupConfig) {
        this.virtualCacheCleanupConfig = virtualCacheCleanupConfig;
    }

    private <T extends ReplicationBaseDescriptor> void addReplication(T replicationDescriptor,
            List<T> replications) {
        if (replications.contains(replicationDescriptor)) {
            throw new AlreadyExistsException("Replication for '" + replicationDescriptor.getRepoKey() +
                    "'" + ALREADY_EXISTS_ERR);
        }
        replications.add(replicationDescriptor);
    }

    /**
     * update if exist / add (new) local replication descriptor
     *
     * @param replicationDescriptor - new or update local replication descriptor
     * @param replications          - all replication descriptors
     */
    private <T extends ReplicationBaseDescriptor> void addLocalReplication(T replicationDescriptor,
            List<T> replications) {
        if (replications.contains(replicationDescriptor)) {
            replications.remove(replicationDescriptor);
        }
        replications.add(replicationDescriptor);
    }

    private <T extends ReplicationBaseDescriptor> T getReplication(String replicatedRepoKey, List<T> replications) {
        if (StringUtils.isNotBlank(replicatedRepoKey)) {
            for (T replication : replications) {
                if (replicatedRepoKey.equals(replication.getRepoKey())) {
                    return replication;
                }
            }
        }

        return null;
    }

    /**
     * get specific Local Replication based on replicate repo key and repo url
     *
     * @param replicatedRepoKey - repository key
     * @param replicateRepoUrl  - repository url
     * @param replications      - all replication in artifactory
     */
    private <T extends ReplicationBaseDescriptor> T getSpecificLocalReplication(String replicatedRepoKey,
            String replicateRepoUrl, List<T> replications) {
        if (StringUtils.isNotBlank(replicatedRepoKey)) {
            for (T replication : replications) {

                if (replicatedRepoKey.equals(replication.getRepoKey()) && replicateRepoUrl.equals(
                        ((LocalReplicationDescriptor) replication).getUrl())) {
                    return replication;
                }
            }
        }

        return null;
    }

    /**
     * get specific Local Replication based on replicate repo key and repo url
     *
     * @param replicatedRepoKey - repository key
     * @param replications      - all replication in artifactory
     */
    private <T extends ReplicationBaseDescriptor> T get1stEnableLocalReplication(String replicatedRepoKey,
            List<T> replications) {
        if (StringUtils.isNotBlank(replicatedRepoKey)) {
            for (T replication : replications) {
                if (replicatedRepoKey.equals(replication.getRepoKey()) && replication.isEnabled()) {
                    return replication;
                }
            }
        }
        return null;
    }

    /**
     * get the count of specific Local Replication based on replicate repo key and repo url
     *
     * @param replicatedRepoKey - repository key
     * @param replications      - all replication in artifactory
     */
    private <T extends ReplicationBaseDescriptor> int getNumOfActiveLocalReplication(String replicatedRepoKey,
            List<T> replications) {
        int replicationCounter = 0;
        if (StringUtils.isNotBlank(replicatedRepoKey)) {
            for (T replication : replications) {
                if (replicatedRepoKey.equals(replication.getRepoKey()) && replication.isEnabled()) {
                    replicationCounter++;
                }
            }
        }
        return replicationCounter;
    }

    /**
     * Get the replication keys of all enabled replications in artifactory of which the given repo key is the source
     *
     * @param replicatedRepoKey - source repository key
     * @param replications      - all replication in artifactory
     */
    private <T extends ReplicationBaseDescriptor> Set<String> getReplicationKeysByRepoKey(String replicatedRepoKey,
            List<T> replications) {
        Set<String> targetURLs = new HashSet<>();
        if (StringUtils.isNotBlank(replicatedRepoKey)) {
            for (T replication : replications) {
                if (replicatedRepoKey.equals(replication.getRepoKey()) && replication.isEnabled()) {
                    targetURLs.add(replication.getReplicationKey());
                }
            }
        }
        return targetURLs;
    }

    private <T extends ReplicationBaseDescriptor> void removeReplication(T replicationDescriptor,
            List<T> replications) {
        if (replicationDescriptor.getReplicationKey() == null) {
            throw new IllegalStateException("Replication key must be initialized for" +
                    replicationDescriptor.getRepoKey() + " replication.");
        }
        replications.removeIf(repl -> replicationDescriptor.getReplicationKey().equals(repl.getReplicationKey()));
    }

    @Override
    public DownloadRedirectConfigDescriptor getDownloadRedirectConfig() {
        return downloadRedirectConfig;
    }

    public void setDownloadRedirectConfig(DownloadRedirectConfigDescriptor downloadRedirectConfig) {
        this.downloadRedirectConfig = downloadRedirectConfig;
    }

    @Override
    public GlobalReplicationsConfigDescriptor getReplicationsConfig() {
        return replicationsConfig;
    }

    public void setReplicationsConfig(GlobalReplicationsConfigDescriptor replications) {
        this.replicationsConfig = Optional.ofNullable(replications).orElseGet(GlobalReplicationsConfigDescriptor::new);
    }

    @Override
    public List<LocalReplicationDescriptor> getMultiLocalReplications(String repoKey) {
        return getMultiLocalReplications(repoKey, localReplications);
    }

    private <T extends ReplicationBaseDescriptor> List<T> getMultiLocalReplications(String replicatedRepoKey,
            List<T> replications) {
        List<T> localReplicationList = new ArrayList<>();
        if (StringUtils.isNotBlank(replicatedRepoKey)) {
            for (T replication : replications) {
                if (replicatedRepoKey.equals(replication.getRepoKey())) {
                    localReplicationList.add(replication);
                }
            }
        }
        return localReplicationList;
    }

    @Override
    public List<KeyPairDescriptor> getKeyPairs() {
        return keyPairs;
    }

    @Override
    public KeyPairDescriptor getKeyPair(String keyPairKey) {
        return keyPairs.stream().filter(keyPair -> keyPair.getKey().equals(keyPairKey)).findFirst().orElse(null);
    }

    @Override
    public boolean isKeyPairExists(String keyPairKey) {
        return keyPairs.stream().anyMatch(keyPairDescriptor -> keyPairDescriptor.getKey().equals(keyPairKey));
    }

    @Override
    public void addKeyPair(KeyPairDescriptor keyPair) {
        String keyPairKey = keyPair.getKey();
        String errorMessagePrefix = "Key Pair " + keyPairKey;
        if (isKeyPairExists(keyPairKey)) {
            throw new AlreadyExistsException(errorMessagePrefix + ALREADY_EXISTS_ERR);
        }
        if (!isKeyAvailable(keyPairKey)) {
            throw new AlreadyExistsException(errorMessagePrefix + IS_NOT_AVAILABLE_ERR);
        }
        DescriptorUtils.assertNoSpecialCharactersAndValidXmlName(keyPairKey, errorMessagePrefix);
        keyPairs.add(keyPair);
        log.debug("Added Key Pair {} of type {}", keyPair.getKey(), keyPair.getKeyType());
    }

    @Override
    public void updateKeyPair(KeyPairDescriptor newKeyPair) {
        String keyPairKey = newKeyPair.getKey();
        if (!isKeyPairExists(keyPairKey)) {
            throw new DoesNotExistException("Key Pair " + keyPairKey + " no exists.");
        }
        KeyPairDescriptor existingKeyPair = getKeyPair(keyPairKey);
        existingKeyPair.setVaultType(newKeyPair.getVaultType());
        existingKeyPair.setPrivateKey(newKeyPair.getPrivateKey());
        existingKeyPair.setPublicKey(newKeyPair.getPublicKey());
        existingKeyPair.setAlias(newKeyPair.getAlias());
        existingKeyPair.setPassphrase(newKeyPair.getPassphrase());
        log.debug("Updated Key Pair {} of type {}", newKeyPair.getKey(), newKeyPair.getKeyType());
    }

    @Override
    public void removeKeyPair(String keyPairKey) {
        KeyPairDescriptor keyPair = getKeyPair(keyPairKey);
        if (keyPair == null) {
            return;
        }
        keyPairs.remove(keyPair);
        log.debug("Removed Key Pair {} of type {}", keyPair.getKey(), keyPair.getKeyType());
    }


    @Override
    public void setKeyPairs(List<KeyPairDescriptor> keyPairs) {
        this.keyPairs = keyPairs;
    }

    @Override
    public ArtifactRetentionPolicyDescriptor getRetentionPolicy(String policyKey) {
        return retentionSettings.getRetentionPolicies().stream()
                .filter(policy -> policyKey.equals(policy.getKey()))
                .findAny()
                .orElse(null);
    }

    @Override
    public List<ArtifactRetentionPolicyDescriptor> getRetentionPolicies() {
        return retentionSettings.getRetentionPolicies();
    }

    @Override
    public void addRetentionPolicy(ArtifactRetentionPolicyDescriptor policyDescriptor) {
        String policyKey = policyDescriptor.getKey();
        String errorMessagePrefix = "Retention policy " + policyKey;
        if (getRetentionPolicy(policyKey) != null) {
            throw new AlreadyExistsException(errorMessagePrefix + ALREADY_EXISTS_ERR);
        }
        if (!isKeyAvailable(policyKey)) {
            throw new AlreadyExistsException(errorMessagePrefix + IS_NOT_AVAILABLE_ERR);
        }
        DescriptorUtils.assertNoSpecialCharactersAndValidXmlName(policyKey, errorMessagePrefix);
        retentionSettings.getRetentionPolicies().add(policyDescriptor);
        log.debug("Retention policy '{}' has been added", policyDescriptor.getKey());
    }

    @Override
    public void removeRetentionPolicy(String policyKey) {
        ArtifactRetentionPolicyDescriptor policy = getRetentionPolicy(policyKey);
        if (policy == null) {
            return;
        }
        retentionSettings.getRetentionPolicies().remove(policy);
    }

    private void addOrUpdateRemoteRepository(RemoteRepoDescriptor remoteRepoDescriptor, boolean update) {
        String repoKey = remoteRepoDescriptor.getKey();
        remoteRepoDescriptor.processUrl();
        remoteRepositoriesMap.put(repoKey, remoteRepoDescriptor);
        if (!update) {
            conditionallyAddToBackups(remoteRepoDescriptor.getKey());
        }
    }

    @Override
    public EulaDescriptor getEulaConfig() {
        return eulaConfig;
    }

    @Override
    public void setEulaConfig(EulaDescriptor eulaConfig) {
        this.eulaConfig = eulaConfig;
    }

    @Override
    public SubscriptionConfig getSubscriptionConfig() {
        return subscriptionConfig;
    }

    @Override
    public void setSubscriptionConfig(SubscriptionConfig subscriptionConfig) {
        this.subscriptionConfig = subscriptionConfig;
    }

    @Override
    public RepositoriesGeneralConfigDescriptor getRepositoriesGeneralConfig() {
        return repositoriesGeneralConfig;
    }

    @Override
    public void setRepositoriesGeneralConfig(RepositoriesGeneralConfigDescriptor repositoriesGeneralConfig) {
        this.repositoriesGeneralConfig = repositoriesGeneralConfig;
    }

    private <T extends RepoDescriptor> Map<String, T> filterBundleRepositories(
            @NonNull Map<String, T> sourceRepositories) {
        return sourceRepositories;
    }

}
