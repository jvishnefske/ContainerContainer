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
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.descriptor.retention.ArtifactRetentionPolicyDescriptor;
import org.artifactory.descriptor.rlm.RlmConfig;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.subscription.SubscriptionConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.model.RepoConfig;

import java.util.List;
import java.util.Map;

/**
 * Mutable interface for the central config.
 *
 * @author Yossi Shaul
 */
public interface MutableCentralConfigDescriptor extends CentralConfigDescriptor {
    @Deprecated(since = "7.48")
    void setLocalRepositoriesMap(Map<String, LocalRepoDescriptor> localRepositoriesMap);

    @Deprecated(since = "7.48")
    void setFederatedRepositoriesMap(Map<String, FederatedRepoDescriptor> federatedRepositoriesMap);

    @Deprecated(since = "7.48")
    void setRemoteRepositoriesMap(Map<String, RemoteRepoDescriptor> remoteRepositoriesMap);

    @Deprecated(since = "7.48")
    void setVirtualRepositoriesMap(Map<String, VirtualRepoDescriptor> virtualRepositoriesMap);

    @Deprecated(since = "7.48")
    void setReleaseBundlesRepositoriesMap(Map<String, ReleaseBundlesRepoDescriptor> releaseBundlesRepositoriesMap);

    void setProxies(List<ProxyDescriptor> proxies);

    void setReverseProxies(List<ReverseProxyDescriptor> proxies);

    void setFileUploadMaxSizeMb(int fileUploadMaxSizeMb);

    void setItemsBrowserPagingSize(int itemsBrowserPagingSize);

    void setRevision(long revision);

    void setBackups(List<BackupDescriptor> backups);

    void setIndexer(IndexerDescriptor descriptor);

    void setServerName(String serverName);

    void setSecurity(SecurityDescriptor security);

    void setAuthentication(AuthenticationDescriptor authentication);

    void setOfflineMode(boolean offlineMode);

    void setForceNativeDbLocksMechanismInAutoMode(boolean forceNativeDbLocksMechanismInAutoMode);

    void setUseOnlyTokenForIdentifyingXrayUser(boolean useOnlyTokenForIdentifyingXrayUser);

    /**
     * Removes any references to the provided repository from config descriptor entities (backup, indexer,
     * replication and reverse proxy)
     *
     * @param removedRepo The repository config to remove.
     */
    void removeReferences(RepoConfig removedRepo);

    /**
     * Adds the local repository to local repos map.
     *
     * @param localRepoDescriptor The local repo to add.
     */
    void addLocalRepository(LocalRepoDescriptor localRepoDescriptor);


    /**
     * Adds the federated repository to federated repos map.
     *
     * @param federatedRepoDescriptor The federated repo to add.
     */
    void addFederatedRepository(FederatedRepoDescriptor federatedRepoDescriptor);

    /**
     * Adds the remote repository to remote repos map.
     *
     * @param remoteRepoDescriptor The remote repo to add.
     */
    void addRemoteRepository(RemoteRepoDescriptor remoteRepoDescriptor);

    /**
     * Updates the remote repository in remote repos map.
     *
     * @param remoteRepoDescriptor The remote repo to update.
     */
    void updateRemoteRepository(RemoteRepoDescriptor remoteRepoDescriptor);

    /**
     * Adds the virtual repository to virtual repos map.
     *
     * @param virtualRepoDescriptor The virtual repo to add.
     */
    void addVirtualRepository(VirtualRepoDescriptor virtualRepoDescriptor);

    /**
     * This methods checks if the key is used by any descriptor. This check is importans since all the descriptors keys
     * are defined as XmlIds and must be inique in the xml file.
     *
     * @param key The key to check.
     * @return True if the key is not used by any other descriptor.
     */
    boolean isKeyAvailable(String key);

    /**
     * @param proxyKey The proxy key to check.
     * @return True if a proxy with the input key exists.
     */
    boolean isProxyExists(String proxyKey);

    /**
     * Adds the proxy to the proxies list.
     *
     * @param proxyDescriptor The new proxy to add.
     */
    void addProxy(ProxyDescriptor proxyDescriptor, boolean shouldUpdateRemotes);

    /**
     * Removes the proxy with the specified key from the proxies list. Will also remove any references to this proxy
     * from remote repos
     *
     * @param proxyKey The proxy key to check.
     * @return The removed proxy descriptor or null if not found.
     */
    ProxyDescriptor removeProxy(String proxyKey);

    /**
     * Checks if the given reverse proxy exists by key
     *
     * @param key The reverse proxy key to check
     * @return True if the reverse proxy with the given key exists, false otherwise
     */
    boolean isReverseProxyExists(String key);

    /**
     * Adds the reverse proxy to the list
     *
     * @param descriptor The new reverse proxy to add
     */
    void addReverseProxy(ReverseProxyDescriptor descriptor);

    /**
     * Adds the reverse proxy to the list
     *
     * @param descriptor The new reverse proxy to add
     */
    void updateReverseProxy(ReverseProxyDescriptor descriptor);

    /**
     * Removes the reverse proxy with the specified key from the list.
     *
     * @param key The reverse proxy key to remove
     * @return The removed reverse proxy descriptor or null if not found
     */
    ReverseProxyDescriptor removeReverseProxy(String key);


    /**
     * Removes the reverse proxy with the specified key from the list.
     *
     * @return The removed reverse proxy descriptor or null if not found
     */
    void removeCurrentReverseProxy();

    /**
     * Changes the default proxy and services of other proxies in case the proxy has either set.
     * Will set the default proxies in already existing repositories if the new proxy is the new Artifactory default.
     *
     * @param proxy The proxy descriptor to add
     */
    void proxyChanged(ProxyDescriptor proxy, boolean shouldUpdateRemotes);

    /**
     * @param backupKey The backup key to check.
     * @return True if a backup with the input key exists.
     */
    boolean isBackupExists(String backupKey);

    /**
     * Adds the backup to the backups list.
     *
     * @param backupDescriptor The new backup to add.
     */
    void addBackup(BackupDescriptor backupDescriptor);

    /**
     * Removes the backup with the specified key from the backups list. Will also remove any references to this backup
     * from remote repos
     *
     * @param backupKey The backup key to check.
     * @return The removed backup descriptor or null if not found.
     */
    BackupDescriptor removeBackup(String backupKey);

    /**
     * @param propertySetName The property set name to check
     * @return True if a property set with the given name exists
     */
    boolean isPropertySetExists(String propertySetName);

    /**
     * @param sysPropName - The system property that searching for.
     * @return True if the system property exists or false if not.
     */
    boolean isSystemPropertyExists(String sysPropName);

    /**
     * Adds the property set to the property sets list
     *
     * @param propertySet The new property set to add.
     */
    void addPropertySet(PropertySet propertySet);

    /**
     * Adds the system property to the system properties list
     *
     * @param systemPropertyDescriptor The new property to add.
     */
    void addSystemProperty(SystemPropertyDescriptor systemPropertyDescriptor);

    /**
     * Removes the property set with the specified name from the property sets list. Will also remove any references to
     * this property set from local repos
     *
     * @param propertySetName The property set name to check.
     * @return The removed property set descriptor or null if not found.
     */
    PropertySet removePropertySet(String propertySetName);

    /**
     * Removes the system property with the specified name.
     *
     * @param systemPropertyName - The system property name
     * @return The removed system property or null if not found.
     */
    SystemPropertyDescriptor removeSystemProperty(String systemPropertyName);

    void setMailServer(MailServerDescriptor mailServer);

    void setXrayConfig(XrayDescriptor xrayConfig);

    void setPropertySets(List<PropertySet> propertySets);

    void setSystemProperties(List<SystemPropertyDescriptor> systemProperties);

    void setUrlBase(String baseUrl);

    void setFederatedRepoUrlBase(String baseUrl);

    void setAddons(AddonSettings addonSettings);

    void setFolderDownloadConfig(FolderDownloadConfigDescriptor folderDownloadConfig);

    void setTrashcanConfig(TrashcanConfigDescriptor trashcanConfig);

    void setSumoLogicConfig(SumoLogicConfigDescriptor sumoLogicConfig);

    void setReleaseBundlesConfig(ReleaseBundlesConfig releaseBundlesConfig);

    void setRlmConfig(RlmConfig releaseBundlesConfig);

    void setSignedUrlConfig(SignedUrlConfig signedUrlConfig);

    boolean isRepoLayoutExists(String repoLayoutName);

    void addRepoLayout(RepoLayout repoLayout);

    RepoLayout removeRepoLayout(String repoLayoutName);

    void setRepoLayouts(List<RepoLayout> repoLayouts);

    boolean isRemoteReplicationExists(RemoteReplicationDescriptor descriptor);

    boolean isLocalReplicationExists(LocalReplicationDescriptor descriptor);

    void addRemoteReplication(RemoteReplicationDescriptor replicationDescriptor);

    void addLocalReplication(LocalReplicationDescriptor replicationDescriptor);

    void removeRemoteReplication(RemoteReplicationDescriptor replicationDescriptor);

    void removeLocalReplication(LocalReplicationDescriptor replicationDescriptor);

    void setGlobalReplicationConfig(GlobalReplicationsConfigDescriptor globalReplicationConfig);

    void setRemoteReplications(List<RemoteReplicationDescriptor> replicationDescriptors);

    void setLocalReplications(List<LocalReplicationDescriptor> localReplications);

    void setGcConfig(GcConfigDescriptor gcConfigDescriptor);

    void setCleanupConfig(CleanupConfigDescriptor cleanupConfigDescriptor);

    void setVirtualCacheCleanupConfig(CleanupConfigDescriptor virtualCacheCleanupConfig);

    void setQuotaConfig(QuotaConfigDescriptor descriptor);

    void setDownloadRedirectConfig(DownloadRedirectConfigDescriptor downloadRedirectConfig);

    BackupDescriptor getBackup(String backupKey);

    boolean conditionallyAddToBackups(String realRepoKey);

    void setEulaConfig(EulaDescriptor eulaDescriptor);

    void setSubscriptionConfig(SubscriptionConfig subscription);

    void setRepositoriesGeneralConfig(RepositoriesGeneralConfigDescriptor repositoriesGeneralConfig);

    boolean isKeyPairExists(String keyPairKey);

    void addKeyPair(KeyPairDescriptor keyPair);

    void updateKeyPair(KeyPairDescriptor keyPair);

    void removeKeyPair(String keyPair);

    void setKeyPairs(List<KeyPairDescriptor> keyPairs);

    /**
     * Adds the policy to the retentionPolicies list.
     *
     * @param artifactRetentionPolicyDescriptor The new policy to add.
     */
    void addRetentionPolicy(ArtifactRetentionPolicyDescriptor artifactRetentionPolicyDescriptor);

    /**
     * Removes the policy with the specified key from the retentionPolicies list.
     *
     * @param policyKey The retention policy key to check.
     */
    void removeRetentionPolicy(String policyKey);

    int getRepoCount();
}
