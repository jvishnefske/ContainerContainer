package org.artifactory.config;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.addon.AddonSettings;
import org.artifactory.descriptor.authentication.AuthenticationDescriptor;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.cleanup.CleanupConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
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
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.descriptor.retention.ArtifactRetentionPolicyDescriptor;
import org.artifactory.descriptor.retention.ArtifactRetentionSettings;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPathsCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPropertiesCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPropertyCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicySearchCriteriaDescriptor;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.signature.SignedUrlConfig;
import org.artifactory.descriptor.subscription.SubscriptionConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.client.model.HttpSsoSetting;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.SamlSetting;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.config.CentralConfigDescriptorSanitizer.merge;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * @author igoru
 */
public class CentralConfigDescriptorSanitizerTest {

    private CentralConfigDescriptorImpl source;
    private CentralConfigDescriptorImpl target;
    private static MockedStatic<AccessConfigHolder> staticAccessConfigHolder;
    private static final AccessConfigHolder spyAccessConfigHolder = Mockito.spy(AccessConfigHolder.class);

    @BeforeMethod
    public void setUp() {
        source = new CentralConfigDescriptorImpl();
        target = new CentralConfigDescriptorImpl();
        staticAccessConfigHolder = Mockito.mockStatic(AccessConfigHolder.class);
        staticAccessConfigHolder.when(AccessConfigHolder::getInstance).thenReturn(spyAccessConfigHolder);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        SysConfig sysConfig = mock(SysConfig.class);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterMethod
    public void cleanup() throws Exception {
        ArtifactoryHome.unbind();
        staticAccessConfigHolder.close();
    }

    @Test
    public void takesAuthenticationFromSource() {
        AuthenticationDescriptor sourceDescriptor = new AuthenticationDescriptor();
        AuthenticationToken sourceToken = new AuthenticationToken();
        sourceToken.setTokenId("source");
        sourceDescriptor.setTokens(Collections.singletonList(sourceToken));
        source.setAuthentication(sourceDescriptor);

        AuthenticationDescriptor targetDescriptor = new AuthenticationDescriptor();
        AuthenticationToken targetToken = new AuthenticationToken();
        targetToken.setTokenId("target");
        targetDescriptor.setTokens(Collections.singletonList(targetToken));
        target.setAuthentication(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getAuthentication().getTokens().size(), 1);
        assertEquals(result.getAuthentication().getTokens().get(0).getTokenId(), "source");
    }

    @Test
    public void takesProxiesFromTarget() {
        ProxyDescriptor sourceProxyDescriptor = new ProxyDescriptor();
        sourceProxyDescriptor.setKey("sourceProxy");
        source.setProxies(Collections.singletonList(sourceProxyDescriptor));

        ProxyDescriptor targetProxyDescriptor = new ProxyDescriptor();
        targetProxyDescriptor.setKey("targetProxy");
        target.setProxies(Collections.singletonList(targetProxyDescriptor));

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getProxies().size(), 1);
        assertEquals(result.getProxies().get(0).getKey(), "targetProxy");
    }

    @Test
    public void takesReverseProxiesFromTarget() {
        ReverseProxyDescriptor sourceProxyDescriptor = new ReverseProxyDescriptor();
        sourceProxyDescriptor.setKey("sourceProxy");
        source.setReverseProxies(Collections.singletonList(sourceProxyDescriptor));

        ReverseProxyDescriptor targetProxyDescriptor = new ReverseProxyDescriptor();
        targetProxyDescriptor.setKey("targetProxy");
        target.setReverseProxies(Collections.singletonList(targetProxyDescriptor));

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getReverseProxies().size(), 1);
        assertEquals(result.getReverseProxies().get(0).getKey(), "targetProxy");
    }

    @Test
    public void takesFileUploadMaxSizeMbFromTarget() {
        source.setFileUploadMaxSizeMb(48);
        target.setFileUploadMaxSizeMb(58);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getFileUploadMaxSizeMb(), 58);
    }

    @Test
    public void takesBackupsFromTarget() {
        BackupDescriptor sourceDescriptor = new BackupDescriptor();
        sourceDescriptor.setKey("sourceBackup");
        source.setBackups(Collections.singletonList(sourceDescriptor));

        BackupDescriptor targetDescriptor = new BackupDescriptor();
        targetDescriptor.setKey("targetBackup");
        target.setBackups(Collections.singletonList(targetDescriptor));

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getBackups().size(), 1);
        assertEquals(result.getBackups().get(0).getKey(), "targetBackup");
    }

    @Test
    public void takesIndexerFromTarget() {
        IndexerDescriptor sourceDescriptor = new IndexerDescriptor();
        sourceDescriptor.setCronExp("sourceCron");
        source.setIndexer(sourceDescriptor);

        IndexerDescriptor targetDescriptor = new IndexerDescriptor();
        targetDescriptor.setCronExp("targetCron");
        target.setIndexer(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getIndexer().getCronExp(), "targetCron");
    }

    @Test
    public void takesServerNameFromTarget() {
        source.setServerName("source");
        target.setServerName("target");

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getServerName(), "target");
    }

    @Test
    public void takesOfflineModeFromTarget() {
        source.setOfflineMode(true);
        target.setOfflineMode(false);

        CentralConfigDescriptor result = merge(source, target);

        assertFalse(result.isOfflineMode());
    }

    @Test
    public void takesAddonsFromTarget() {
        AddonSettings sourceSettings = new AddonSettings();
        sourceSettings.setShowAddonsInfoCookie("source");
        source.setAddons(sourceSettings);

        AddonSettings targetSettings = new AddonSettings();
        targetSettings.setShowAddonsInfoCookie("target");
        target.setAddons(targetSettings);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getAddons().getShowAddonsInfoCookie(), "target");
    }

    @Test
    public void takesMailServerFromTarget() {
        MailServerDescriptor sourceDescriptor = new MailServerDescriptor();
        sourceDescriptor.setHost("source");
        source.setMailServer(sourceDescriptor);

        MailServerDescriptor targetDescriptor = new MailServerDescriptor();
        targetDescriptor.setHost("target");
        target.setMailServer(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getMailServer().getHost(), "target");
    }

    @Test
    public void takesXrayConfigFromTarget() {
        XrayDescriptor sourceDescriptor = new XrayDescriptor();
        sourceDescriptor.setXrayId("source");
        source.setXrayConfig(sourceDescriptor);

        XrayDescriptor targetDescriptor = new XrayDescriptor();
        targetDescriptor.setXrayId("target");
        target.setXrayConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getXrayConfig().getXrayId(), "target");
    }

    @Test
    public void takesUrlBaseFromTarget() {
        source.setUrlBase("source");
        target.setUrlBase("target");

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getUrlBase(), "target");
    }

    @Test
    public void takesFederatedRepoUrlBaseFromTarget() {
        source.setFederatedRepoUrlBase("source");
        target.setFederatedRepoUrlBase("target");

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getFederatedRepoUrlBase(), "target");
    }

    @Test
    public void takesFolderDownloadConfigFromTarget() {
        FolderDownloadConfigDescriptor sourceDescriptor = new FolderDownloadConfigDescriptor();
        sourceDescriptor.setMaxFiles(5);
        source.setFolderDownloadConfig(sourceDescriptor);

        FolderDownloadConfigDescriptor targetDescriptor = new FolderDownloadConfigDescriptor();
        targetDescriptor.setMaxFiles(6);
        target.setFolderDownloadConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getFolderDownloadConfig().getMaxFiles(), 6);
    }

    @Test
    public void takesGcConfigFromTarget() {
        GcConfigDescriptor sourceDescriptor = new GcConfigDescriptor();
        sourceDescriptor.setCronExp("source");
        source.setGcConfig(sourceDescriptor);

        GcConfigDescriptor targetDescriptor = new GcConfigDescriptor();
        targetDescriptor.setCronExp("target");
        target.setGcConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getGcConfig().getCronExp(), "target");
    }

    @Test
    public void takesCleanupConfigFromTarget() {
        CleanupConfigDescriptor sourceDescriptor = new CleanupConfigDescriptor();
        sourceDescriptor.setCronExp("source");
        source.setCleanupConfig(sourceDescriptor);

        CleanupConfigDescriptor targetDescriptor = new CleanupConfigDescriptor();
        targetDescriptor.setCronExp("target");
        target.setCleanupConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getCleanupConfig().getCronExp(), "target");
    }

    @Test
    public void takesVirtualCacheCleanupConfigFromTarget() {
        CleanupConfigDescriptor sourceDescriptor = new CleanupConfigDescriptor();
        sourceDescriptor.setCronExp("source");
        source.setVirtualCacheCleanupConfig(sourceDescriptor);

        CleanupConfigDescriptor targetDescriptor = new CleanupConfigDescriptor();
        targetDescriptor.setCronExp("target");
        target.setVirtualCacheCleanupConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getVirtualCacheCleanupConfig().getCronExp(), "target");
    }

    @Test
    public void takesDownloadRedirectConfigFromTarget() {
        DownloadRedirectConfigDescriptor sourceDescriptor = new DownloadRedirectConfigDescriptor();
        sourceDescriptor.setFileMinimumSize(42);
        source.setDownloadRedirectConfig(sourceDescriptor);

        DownloadRedirectConfigDescriptor targetDescriptor = new DownloadRedirectConfigDescriptor();
        targetDescriptor.setFileMinimumSize(43);
        target.setDownloadRedirectConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getDownloadRedirectConfig().getFileMinimumSize(), 43);
    }


    @Test
    public void takesQuotaConfigFromTarget() {
        QuotaConfigDescriptor sourceDescriptor = new QuotaConfigDescriptor();
        sourceDescriptor.setDiskSpaceLimitPercentage(42);
        source.setQuotaConfig(sourceDescriptor);

        QuotaConfigDescriptor targetDescriptor = new QuotaConfigDescriptor();
        targetDescriptor.setDiskSpaceLimitPercentage(43);
        target.setQuotaConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getQuotaConfig().getDiskSpaceLimitPercentage(), 43);
    }

    @Test
    public void takesTrashcanConfigFromSource() {
        TrashcanConfigDescriptor sourceDescriptor = new TrashcanConfigDescriptor();
        sourceDescriptor.setRetentionPeriodDays(42);
        source.setTrashcanConfig(sourceDescriptor);

        TrashcanConfigDescriptor targetDescriptor = new TrashcanConfigDescriptor();
        targetDescriptor.setRetentionPeriodDays(43);
        target.setTrashcanConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getTrashcanConfig().getRetentionPeriodDays(), 42);
    }

    @Test
    public void takesReleaseBundlesConfigFromSource() {
        ReleaseBundlesConfig sourceConfig = new ReleaseBundlesConfig();
        sourceConfig.setIncompleteCleanupPeriodHours(42L);
        source.setReleaseBundlesConfig(sourceConfig);

        ReleaseBundlesConfig targetConfig = new ReleaseBundlesConfig();
        targetConfig.setIncompleteCleanupPeriodHours(43L);
        target.setReleaseBundlesConfig(targetConfig);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getReleaseBundlesConfig().getIncompleteCleanupPeriodHours().longValue(), 42L);
    }

    @Test
    public void takesSignedUrlConfigFromSource() {
        SignedUrlConfig sourceConfig = new SignedUrlConfig();
        sourceConfig.setMaxValidForSeconds(42L);
        source.setSignedUrlConfig(sourceConfig);

        SignedUrlConfig targetConfig = new SignedUrlConfig();
        targetConfig.setMaxValidForSeconds(43L);
        target.setSignedUrlConfig(targetConfig);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getSignedUrlConfig().getMaxValidForSeconds().longValue(), 42L);
    }

    @Test
    public void takesEulaFromSource() {
        EulaDescriptor sourceDescriptor = new EulaDescriptor();
        sourceDescriptor.setAccepted(true);
        source.setEulaConfig(sourceDescriptor);

        EulaDescriptor targetDescriptor = new EulaDescriptor();
        targetDescriptor.setAccepted(false);
        target.setEulaConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertTrue(result.getEulaConfig().isAccepted());
    }

    @Test
    public void takesKeyPairsFromSource() {
        KeyPairDescriptor sourceDescriptor = new KeyPairDescriptor();
        sourceDescriptor.setKey("source");
        source.setKeyPairs(Collections.singletonList(sourceDescriptor));

        KeyPairDescriptor targetDescriptor = new KeyPairDescriptor();
        targetDescriptor.setKey("target");
        target.setKeyPairs(Collections.singletonList(targetDescriptor));

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getKeyPairs().size(), 1);
        assertEquals(result.getKeyPairs().get(0).getKey(), "source");
    }

    @Test
    public void takesSubscriptionFromSource() {
        SubscriptionConfig sourceConfig = new SubscriptionConfig();
        sourceConfig.setEmails(Collections.singleton("source"));
        source.setSubscriptionConfig(sourceConfig);

        SubscriptionConfig targetConfig = new SubscriptionConfig();
        targetConfig.setEmails(Collections.singleton("target"));
        target.setSubscriptionConfig(targetConfig);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getSubscriptionConfig().getEmails().size(), 1);
        assertEquals(result.getSubscriptionConfig().getEmails().iterator().next(), "source");
    }

    @Test
    public void takesRetentionSettingsFromTarget() {
        ArtifactRetentionSettings sourceSettings = new ArtifactRetentionSettings();
        sourceSettings.setJfrogColdStorageArchivingNamespace("source");
        source.setRetentionSettings(sourceSettings);

        ArtifactRetentionSettings targetSettings = new ArtifactRetentionSettings();
        targetSettings.setJfrogColdStorageArchivingNamespace("target");
        target.setRetentionSettings(targetSettings);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getRetentionSettings().getJfrogColdStorageArchivingNamespace(), "target");
    }

    @Test
    public void takesReplicationsConfigFromSource() {
        GlobalReplicationsConfigDescriptor sourceDescriptor = new GlobalReplicationsConfigDescriptor();
        sourceDescriptor.setBlockPullReplications(true);
        source.setReplicationsConfig(sourceDescriptor);

        GlobalReplicationsConfigDescriptor targetDescriptor = new GlobalReplicationsConfigDescriptor();
        targetDescriptor.setBlockPullReplications(false);
        target.setReplicationsConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertTrue(result.getReplicationsConfig().isBlockPullReplications());
    }

    @Test
    public void takesSumoLogicConfigFromSource() {
        SumoLogicConfigDescriptor sourceDescriptor = new SumoLogicConfigDescriptor();
        sourceDescriptor.setClientId("source");
        ProxyDescriptor sourceProxy = new ProxyDescriptor();
        sourceProxy.setKey("source-proxy");
        sourceDescriptor.setProxy(sourceProxy);
        source.setSumoLogicConfig(sourceDescriptor);

        SumoLogicConfigDescriptor targetDescriptor = new SumoLogicConfigDescriptor();
        targetDescriptor.setClientId("target");
        ProxyDescriptor targetProxy = new ProxyDescriptor();
        targetProxy.setKey("target-proxy");
        targetDescriptor.setProxy(targetProxy);
        target.setSumoLogicConfig(targetDescriptor);

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getSumoLogicConfig().getClientId(), "source");
        assertNull(result.getSumoLogicConfig().getProxy());
    }

    @Test
    public void takesRemoteReplicationsFromSource() {
        RemoteReplicationDescriptor sourceDescriptor = new RemoteReplicationDescriptor();
        sourceDescriptor.setReplicationKey("source");
        source.setRemoteReplications(Collections.singletonList(sourceDescriptor));

        RemoteReplicationDescriptor targetDescriptor = new RemoteReplicationDescriptor();
        targetDescriptor.setReplicationKey("target");
        target.setRemoteReplications(Collections.singletonList(targetDescriptor));

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getRemoteReplications().size(), 1);
        assertEquals(result.getRemoteReplications().get(0).getReplicationKey(), "source");
    }

    @Test
    public void takesRepoLayoutsFromSource() {
        RepoLayout sourceLayout = new RepoLayout();
        sourceLayout.setName("source");
        source.setRepoLayouts(Collections.singletonList(sourceLayout));

        RepoLayout targetLayout = new RepoLayout();
        targetLayout.setName("target");
        target.setRepoLayouts(Collections.singletonList(targetLayout));

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getRepoLayouts().size(), 1);
        assertEquals(result.getRepoLayouts().get(0).getName(), "source");
    }

    @Test
    public void takesLocalReplicationsFromSource() {
        LocalReplicationDescriptor sourceDescriptor = new LocalReplicationDescriptor();
        sourceDescriptor.setReplicationKey("source");
        ProxyDescriptor sourceProxy = new ProxyDescriptor();
        sourceProxy.setKey("source-proxy");
        sourceDescriptor.setProxy(sourceProxy);
        source.setLocalReplications(Collections.singletonList(sourceDescriptor));

        LocalReplicationDescriptor targetDescriptor = new LocalReplicationDescriptor();
        targetDescriptor.setReplicationKey("target");
        ProxyDescriptor targetProxy = new ProxyDescriptor();
        targetProxy.setKey("target-proxy");
        targetDescriptor.setProxy(targetProxy);
        target.setLocalReplications(Collections.singletonList(targetDescriptor));

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getLocalReplications().size(), 1);
        assertEquals(result.getLocalReplications().get(0).getReplicationKey(), "source");
        assertNull(result.getLocalReplications().get(0).getProxy());
    }

    @Test
    public void takesSystemPropertiesFromTarget() {
        SystemPropertyDescriptor sourceDescriptor = new SystemPropertyDescriptor();
        sourceDescriptor.setKey("source");
        source.setSystemProperties(Collections.singletonList(sourceDescriptor));

        SystemPropertyDescriptor targetDescriptor = new SystemPropertyDescriptor();
        targetDescriptor.setKey("target");
        target.setSystemProperties(Collections.singletonList(targetDescriptor));

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getSystemProperties().size(), 1);
        assertEquals(result.getSystemProperties().get(0).getKey(), "target");
    }

    @Test
    public void takesPropertySetsFromSource() {
        PropertySet sourceSet = new PropertySet();
        sourceSet.setName("source");
        source.setPropertySets(Collections.singletonList(sourceSet));

        PropertySet targetSet = new PropertySet();
        targetSet.setName("target");
        target.setPropertySets(Collections.singletonList(targetSet));

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getPropertySets().size(), 1);
        assertEquals(result.getPropertySets().get(0).getName(), "source");
    }

    @Test
    public void takesReleaseBundlesRepositoriesFromSourceIgnoringSystemRepos() {
        Map<String, ReleaseBundlesRepoDescriptor> sourceMap = new HashMap<>();
        sourceMap.put("source", releaseBundlesRepoDescriptor("source"));
        sourceMap.put("jfrog-usage-logs", releaseBundlesRepoDescriptor("jfrog-usage-logs"));
        sourceMap.put("jfrog-logs", releaseBundlesRepoDescriptor("jfrog-logs"));
        source.setReleaseBundlesRepositoriesMap(sourceMap);

        target.setReleaseBundlesRepositoriesMap(new HashMap<>());

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getReleaseBundlesRepositoriesMap().size(), 1);
        assertEquals(result.getReleaseBundlesRepositoriesMap().get("source").getKey(), "source");
    }

    private ReleaseBundlesRepoDescriptor releaseBundlesRepoDescriptor(String key) {
        ReleaseBundlesRepoDescriptor descriptor = new ReleaseBundlesRepoDescriptor();
        descriptor.setKey("source");
        return descriptor;
    }

    @Test
    public void takesVirtualRepositoriesFromSourceIgnoringSystemRepos() {
        Map<String, VirtualRepoDescriptor> sourceMap = new HashMap<>();
        sourceMap.put("source", virtualRepoDescriptor("source"));
        sourceMap.put("jfrog-usage-logs", virtualRepoDescriptor("jfrog-usage-logs"));
        sourceMap.put("jfrog-logs", virtualRepoDescriptor("jfrog-logs"));
        source.setVirtualRepositoriesMap(sourceMap);

        target.setVirtualRepositoriesMap(new HashMap<>());

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getVirtualRepositoriesMap().size(), 1);
        assertEquals(result.getVirtualRepositoriesMap().get("source").getKey(), "source");
    }

    private VirtualRepoDescriptor virtualRepoDescriptor(String key) {
        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey("source");
        return descriptor;
    }

    @Test
    public void takesFederatedRepositoriesFromSourceIgnoringSystemRepos() {
        Map<String, FederatedRepoDescriptor> sourceMap = new HashMap<>();
        sourceMap.put("source", federatedRepoDescriptor("source"));
        sourceMap.put("jfrog-usage-logs", federatedRepoDescriptor("jfrog-usage-logs"));
        sourceMap.put("jfrog-logs", federatedRepoDescriptor("jfrog-logs"));
        source.setFederatedRepositoriesMap(sourceMap);

        target.setFederatedRepositoriesMap(new HashMap<>());

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getFederatedRepositoriesMap().size(), 1);
        assertEquals(result.getFederatedRepositoriesMap().get("source").getKey(), "source");
        assertNull(result.getFederatedRepositoriesMap().get("source").getProxy());
    }

    private FederatedRepoDescriptor federatedRepoDescriptor(String key) {
        FederatedRepoDescriptor descriptor = new FederatedRepoDescriptor();
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setKey(key + "-proxy");
        descriptor.setKey(key);
        descriptor.setProxy(proxy);
        return descriptor;
    }

    @Test
    public void takesRemoteRepositoriesFromSourceIgnoringSystemRepos() {
        Map<String, RemoteRepoDescriptor> sourceMap = new HashMap<>();
        sourceMap.put("source", httpRepoDescriptor("source"));
        sourceMap.put("jfrog-usage-logs", httpRepoDescriptor("jfrog-usage-logs"));
        sourceMap.put("jfrog-logs", httpRepoDescriptor("jfrog-logs"));
        source.setRemoteRepositoriesMap(sourceMap);

        target.setRemoteRepositoriesMap(new HashMap<>());

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getRemoteRepositoriesMap().size(), 1);
        assertEquals(result.getRemoteRepositoriesMap().get("source").getKey(), "source");
        assertNull(((HttpRepoDescriptor) (result.getRemoteRepositoriesMap().get("source"))).getProxy());
    }

    private HttpRepoDescriptor httpRepoDescriptor(String key) {
        HttpRepoDescriptor descriptor = new HttpRepoDescriptor();
        ProxyDescriptor proxy = new ProxyDescriptor();
        proxy.setKey(key + "-proxy");
        descriptor.setKey(key);
        descriptor.setProxy(proxy);
        return descriptor;
    }

    @Test
    public void takesLocalRepositoriesFromSourceIgnoringSystemRepos() {
        Map<String, LocalRepoDescriptor> sourceMap = new HashMap<>();
        sourceMap.put("source", localRepoDescriptor("source"));
        sourceMap.put("jfrog-usage-logs", localRepoDescriptor("jfrog-usage-logs"));
        sourceMap.put("jfrog-logs", localRepoDescriptor("jfrog-logs"));
        source.setLocalRepositoriesMap(sourceMap);

        target.setLocalRepositoriesMap(new HashMap<>());

        CentralConfigDescriptor result = merge(source, target);

        assertEquals(result.getLocalRepositoriesMap().size(), 1);
        assertEquals(result.getLocalRepositoriesMap().get("source").getKey(), "source");
    }

    private LocalRepoDescriptor localRepoDescriptor(String key) {
        LocalRepoDescriptor descriptor = new LocalRepoDescriptor();
        descriptor.setKey(key);
        return descriptor;
    }

    @Test
    public void takesRetentionPolicies() {
        ArtifactRetentionPolicyDescriptor originalPolicy = createRetentionPoliceDescriptor();
        source.addRetentionPolicy(originalPolicy);
        CentralConfigDescriptor result = merge(source, target);
        ArtifactRetentionPolicyDescriptor copiedPolicy = result.getRetentionPolicy("someKey");
        assertEquals(copiedPolicy, originalPolicy);
        // ArtifactRetentionPolicyDescriptor#equals only compares keys, so we need to check all the fields here
        comparePolicies(copiedPolicy, originalPolicy);
    }

    @Test
    public void takesLdapSetting() {
        List<LdapSetting> ldapSettings = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LdapSetting ldapSetting = new LdapSetting();
            ldapSetting.setEnabled(true);
            ldapSetting.setKey("key" + i);
            ldapSetting.setAutoCreateUser(true);
            ldapSetting.setUserDnPattern("patter" + i);
            ldapSetting.setEmailAttribute("email" + i);
            ldapSettings.add(ldapSetting);
        }
        spyAccessConfigHolder.addLdapSetting(ldapSettings);
        merge(source, target);
        List<LdapSetting> actualLdapSettings = spyAccessConfigHolder.drainLdapSetting();
        for (int i = 0; i < 2; i++) {
            LdapSetting actualLdapSetting = actualLdapSettings.get(i);
            assertFalse(actualLdapSetting.isEnabled());
            assertEquals(actualLdapSetting.getKey(), ldapSettings.get(i).getKey());
            assertEquals(actualLdapSetting.getEmailAttribute(), ldapSettings.get(i).getEmailAttribute());
            assertEquals(actualLdapSetting.isAutoCreateUser(), ldapSettings.get(i).isAutoCreateUser());
            assertEquals(actualLdapSetting.getUserDnPattern(), ldapSettings.get(i).getUserDnPattern());
        }
    }

    @Test
    public void takesSamlSetting() {
        SamlSetting samlSetting = SamlSetting.builder()
                .enableIntegration(true)
                .certificate("password")
                .loginUrl("serverUrl")
                .logoutUrl("customCookieTokenKey")
                .serviceProviderName("serviceProviderName")
                .emailAttribute("emailAttribute")
                .groupAttribute("groupAttribute")
                .verifyAudienceRestriction(true)
                .allowUserToAccessProfile(true)
                .autoRedirect(true)
                .syncGroups(true)
                .build();
        spyAccessConfigHolder.setSamlSetting(samlSetting);
        merge(source, target);
        SamlSetting actual = spyAccessConfigHolder.drainSamlSetting();
        assertFalse(actual.isEnableIntegration());
        assertEquals(actual.getCertificate(), samlSetting.getCertificate());
        assertEquals(actual.getLoginUrl(), samlSetting.getLoginUrl());
        assertEquals(actual.getLogoutUrl(), samlSetting.getLogoutUrl());
        assertEquals(actual.getServiceProviderName(), samlSetting.getServiceProviderName());
        assertEquals(actual.getEmailAttribute(), samlSetting.getEmailAttribute());
        assertEquals(actual.getGroupAttribute(), samlSetting.getGroupAttribute());
        assertEquals(actual.isVerifyAudienceRestriction(), samlSetting.isVerifyAudienceRestriction());
        assertEquals(actual.isAllowUserToAccessProfile(), samlSetting.isAllowUserToAccessProfile());
        assertEquals(actual.isAutoRedirect(), samlSetting.isAutoRedirect());
        assertEquals(actual.isSyncGroups(), samlSetting.isSyncGroups());
    }

    @Test
    public void takesOAuthSettings() {
        // Create and add OAuth general settings
        OAuthGeneralSettings generalSettings = OAuthGeneralSettings.builder()
                .enable(true)
                .defaultSetting("defaultProvider")
                .build();
        spyAccessConfigHolder.addOAuthGeneralSettings(generalSettings);

        // Create and add specific OAuth settings
        OAuthSetting oAuthSetting = OAuthSetting.builder()
                .enabled(true)
                .name("testOAuth")
                .clientId("clientId")
                .clientSecret("clientSecret")
                .providerType("providerType")
                .build();
        List<OAuthSetting> oAuthSettings = List.of(oAuthSetting);
        spyAccessConfigHolder.addOAuthSetting(oAuthSettings);

        // Perform the merge
        merge(source, target);

        // Validate general settings
        OAuthGeneralSettings actualGeneralSettings = spyAccessConfigHolder.drainOAuthGeneralSettings();
        assertFalse(actualGeneralSettings.isEnable());
        assertEquals(actualGeneralSettings.getDefaultSetting(), generalSettings.getDefaultSetting());

        // Validate specific OAuth settings
        List<OAuthSetting> actualOAuthSettings = spyAccessConfigHolder.drainOAuthSetting();
        for (OAuthSetting actualOAuthSetting : actualOAuthSettings) {
            assertFalse(actualOAuthSetting.isEnabled());
            assertEquals(actualOAuthSetting.getName(), oAuthSetting.getName());
            assertEquals(actualOAuthSetting.getClientId(), oAuthSetting.getClientId());
            assertEquals(actualOAuthSetting.getClientSecret(), oAuthSetting.getClientSecret());
            assertEquals(actualOAuthSetting.getProviderType(), oAuthSetting.getProviderType());
        }
    }

    @Test
    public void takesCrowdSetting() {
        CrowdSetting crowdSetting = CrowdSetting.builder()
                .enable(true)
                .applicationName("applicationName")
                .password("password")
                .serverUrl("serverUrl")
                .customCookieTokenKey("customCookieTokenKey")
                .build();
        spyAccessConfigHolder.setCrowdSetting(crowdSetting);
        merge(source, target);
        CrowdSetting actual = spyAccessConfigHolder.drainCrowdSetting();
        assertFalse(actual.isEnable());
        assertEquals(actual.getApplicationName(), crowdSetting.getApplicationName());
        assertEquals(actual.getPassword(), crowdSetting.getPassword());
        assertEquals(actual.getCustomCookieTokenKey(), crowdSetting.getCustomCookieTokenKey());
        assertEquals(actual.getServerUrl(), crowdSetting.getServerUrl());

    }

    @Test
    public void takesHttpSsoSetting() {
        HttpSsoSetting httpSso = HttpSsoSetting.builder()
                .httpSsoProxied(true)
                .allowUserToAccessProfile(true)
                .syncLdapGroups(false)
                .autoCreateUser(false)
                .remoteUserRequestVariable("as")
                .build();
        spyAccessConfigHolder.setHttpSsoSetting(httpSso);
        merge(source, target);
        assertNull(spyAccessConfigHolder.drainHttpSsoSetting());

    }

    private ArtifactRetentionPolicyDescriptor createRetentionPoliceDescriptor() {
        ArtifactRetentionPolicyDescriptor policy = new ArtifactRetentionPolicyDescriptor();
        policy.setKey("someKey");
        policy.setCronExp("someCron");
        policy.setAqlQuery("aql");
        policy.setEnabled(true);
        policy.setDescription("description");
        policy.setDurationInMinutes(123L);
        policy.setSearchCriteriaForm(createSearchCriteriaDescriptor());
        policy.setExpirationTimeInMonths(1L);
        policy.setSkipTrashcan(true);
        policy.setType("someType");
        return policy;
    }

    private ArtifactRetentionPolicySearchCriteriaDescriptor createSearchCriteriaDescriptor() {
        ArtifactRetentionPolicySearchCriteriaDescriptor desc = new ArtifactRetentionPolicySearchCriteriaDescriptor();
        desc.setCreatedBefore(1234L); // one field should be enough for test purposes

        ArtifactRetentionPolicyPathsCriteriaDescriptor pathDesc = new ArtifactRetentionPolicyPathsCriteriaDescriptor();
        ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor incDesc =
                new ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor();
        incDesc.setOperator("operator");
        incDesc.setValues(Collections.singletonList("value1"));
        pathDesc.setInclude(incDesc);
        desc.setRepositories(pathDesc);

        desc.setPaths(pathDesc);
        desc.setNames(pathDesc);

        ArtifactRetentionPolicyPropertiesCriteriaDescriptor propDesc =
                new ArtifactRetentionPolicyPropertiesCriteriaDescriptor();
        ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor propIncDesc =
                new ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor();
        propIncDesc.setOperator("propIncOper");
        ArtifactRetentionPolicyPropertyCriteriaDescriptor propCriteriaDesc =
                new ArtifactRetentionPolicyPropertyCriteriaDescriptor();
        propCriteriaDesc.setPropertyKey("propKey");
        propCriteriaDesc.setPropertyValue("propValue");
        propIncDesc.setValues(Collections.singletonList(propCriteriaDesc));
        propDesc.setInclude(propIncDesc);
        desc.setProperties(propDesc);

        return desc;
    }

    private void comparePolicies(ArtifactRetentionPolicyDescriptor p1, ArtifactRetentionPolicyDescriptor p2) {
        assertEquals(p1.getKey(), p2.getKey());
        assertEquals(p1.getCronExp(), p2.getCronExp());
        assertEquals(p1.getAqlQuery(), p2.getAqlQuery());
        assertEquals(p1.getDescription(), p2.getDescription());
        assertEquals(p1.getSearchCriteriaForm(), p2.getSearchCriteriaForm());
        assertEquals(p1.getDurationInMinutes(), p2.getDurationInMinutes());
        assertEquals(p1.getExpirationTimeInMonths(), p2.getExpirationTimeInMonths());
        assertEquals(p1.getType(), p2.getType());
        assertEquals(p1.isSkipTrashcan(), p2.isSkipTrashcan());
        assertEquals(p1.isEnabled(), p2.isEnabled());
    }
}