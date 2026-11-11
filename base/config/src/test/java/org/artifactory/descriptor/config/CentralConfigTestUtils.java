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
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.addon.AddonSettings;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.cleanup.CleanupConfigDescriptor;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.eula.EulaDescriptor;
import org.artifactory.descriptor.gc.GcConfigDescriptor;
import org.artifactory.descriptor.index.IndexerDescriptor;
import org.artifactory.descriptor.mail.MailServerDescriptor;
import org.artifactory.descriptor.property.PredefinedValue;
import org.artifactory.descriptor.property.Property;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.property.SystemPropertyDescriptor;
import org.artifactory.descriptor.quota.QuotaConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.descriptor.repo.RepoBaseDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.RepositoriesGeneralConfigDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyDescriptor;
import org.artifactory.descriptor.repo.ReverseProxyMethod;
import org.artifactory.descriptor.repo.ReverseProxyRepoConfig;
import org.artifactory.descriptor.repo.WebServerType;
import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.descriptor.retention.ArtifactRetentionMasterToken;
import org.artifactory.descriptor.retention.ArtifactRetentionPolicyDescriptor;
import org.artifactory.descriptor.retention.ArtifactRetentionSettings;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPathsCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPropertiesCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPropertyCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicySearchCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.PackageSearchCriteriaDescriptor;
import org.artifactory.descriptor.rlm.RlmConfig;
import org.artifactory.descriptor.security.PasswordExpirationPolicy;
import org.artifactory.descriptor.security.PasswordResetPolicy;
import org.artifactory.descriptor.security.PasswordSettings;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.oauth.CentralOAuthProviders;
import org.artifactory.descriptor.security.signingkeys.SigningKeysSettings;
import org.artifactory.descriptor.security.sshserver.SshServerSettings;
import org.artifactory.descriptor.subscription.SubscriptionConfig;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.jfrog.common.config.diff.DiffElement;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.DiffMerger;
import org.jfrog.common.config.diff.DiffReference;
import org.jfrog.common.config.diff.DiffUtils;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jfrog.common.ExceptionUtils.wrapException;

/**
 * @author Noam Shemesh
 */
public class CentralConfigTestUtils {
    public static CentralConfigDescriptorImpl initCentralConfig() {
        CentralConfigDescriptorImpl cc = new CentralConfigDescriptorImpl();

        ProxyDescriptor proxy1 = new ProxyDescriptor();
        proxy1.setKey("proxy1");
        cc.addProxy(proxy1, true);

        ProxyDescriptor proxy2 = new ProxyDescriptor();
        proxy2.setKey("proxy2");
        cc.addProxy(proxy2, true);

        BackupDescriptor backup1 = new BackupDescriptor();
        backup1.setKey("backup1");
        cc.addBackup(backup1);

        BackupDescriptor backup2 = new BackupDescriptor();
        backup2.setKey("backup2");
        cc.addBackup(backup2);

        BackupDescriptor backup3 = new BackupDescriptor();
        backup3.setKey("backup3");
        backup3.setExcludeNewRepositories(true);
        cc.addBackup(backup3);

        PropertySet set1 = new PropertySet();
        set1.setName("set1");
        cc.addPropertySet(set1);

        PropertySet set2 = new PropertySet();
        set2.setName("set2");
        cc.addPropertySet(set2);

        cc.addSystemProperty(new SystemPropertyDescriptor("key1", "value1"));
        cc.addSystemProperty(new SystemPropertyDescriptor("key2", "value2"));

        RepoLayout repoLayout1 = new RepoLayout();
        repoLayout1.setName("layout1");
        cc.addRepoLayout(repoLayout1);

        RepoLayout repoLayout2 = new RepoLayout();
        repoLayout2.setName("layout2");
        cc.addRepoLayout(repoLayout2);

        KeyPairDescriptor keyPair = new KeyPairDescriptor();
        keyPair.setKey("keyPair1");
        cc.addKeyPair(keyPair);

        RemoteReplicationDescriptor remoteReplication1 = new RemoteReplicationDescriptor();
        remoteReplication1.setCronExp("0 0/5 * * * ?");
        remoteReplication1.setRepoKey("remote1");
        cc.addRemoteReplication(remoteReplication1);

        RemoteReplicationDescriptor remoteReplication2 = new RemoteReplicationDescriptor();
        remoteReplication2.setCronExp("0 0/6 * * * ?");
        remoteReplication2.setRepoKey("remote2");
        cc.addRemoteReplication(remoteReplication2);

        LocalReplicationDescriptor localReplication1 = new LocalReplicationDescriptor();
        localReplication1.setCronExp("0 0/7 * * * ?");
        localReplication1.setRepoKey("local1");
        localReplication1.setUrl("http://momo.com");
        localReplication1.setUsername("user1");
        localReplication1.setPassword("password1");
        localReplication1.setEnableEventReplication(true);
        localReplication1.setReplicationKey("local1_http___momo");
        cc.addLocalReplication(localReplication1);

        LocalReplicationDescriptor localReplication2 = new LocalReplicationDescriptor();
        localReplication2.setCronExp("0 0/8 * * * ?");
        localReplication2.setRepoKey("local2");
        localReplication2.setUrl("http://popo.com");
        localReplication2.setUsername("user2");
        localReplication2.setPassword("password2");
        localReplication2.setReplicationKey("local2_http___popo");
        localReplication2.setEnableEventReplication(true);
        localReplication2.setDisableProxy(true);
        cc.addLocalReplication(localReplication2);

        AddonSettings addonSettings = new AddonSettings();
        addonSettings.setShowAddonsInfoCookie("Hello");
        cc.setAddons(addonSettings);

        RlmConfig rlmConfig = new RlmConfig();
        rlmConfig.setRlmBuildAutoPromotionEnabled(true);
        cc.setRlmConfig(rlmConfig);

        return cc;
    }

    public static CentralConfigDescriptorImpl getFullConfig() {
        CentralConfigDescriptorImpl cc = initCentralConfig();

        cc.getPropertySets().forEach(propertySet -> {
            Property property = new Property(propertySet.getName() + "property");
            Property property2 = new Property(propertySet.getName() + "property2");
            property.setPredefinedValues(
                    Lists.newArrayList(new PredefinedValue("123", true), new PredefinedValue("456", false)));
            property2.setPredefinedValues(
                    Lists.newArrayList(new PredefinedValue("abc", true), new PredefinedValue("def", false)));
            property.setPropertyType("type1");
            property2.setPropertyType("type2");
            propertySet.setProperties(Lists.newArrayList(property, property2));
        });

        cc.getRepoLayouts().forEach(repoLayout -> {
            repoLayout.setArtifactPathPattern("artifactpath" + repoLayout.getName());
            repoLayout.setDescriptorPathPattern("descpath" + repoLayout.getName());
            repoLayout.setFileIntegrationRevisionRegExp("fileintg" + repoLayout.getName());
            repoLayout.setFolderIntegrationRevisionRegExp("folderintg" + repoLayout.getName());
        });

        cc.getKeyPairs().forEach(keyPair -> {
            keyPair.setAlias("alias" + keyPair.getKey());
            keyPair.setKeyType("type" + keyPair.getKey());
            keyPair.setVaultType("vault" + keyPair.getVaultType());
            keyPair.setPrivateKey("private" + keyPair.getKey());
            keyPair.setPublicKey("public" + keyPair.getKey());
            keyPair.setPassphrase("passphrase" + keyPair.getKey());
        });

        cc.getBackups().forEach(backup -> {
            backup.setCronExp("123");
            backup.setDir(new File("/tmp"));
            backup.setRetentionPeriodHours(1000);
            backup.setExcludedRepositories(
                    new ArrayList<>(cc.getLocalRepositoriesMap().values().stream()
                            .map(RepoBaseDescriptor::getKey)
                            .collect(Collectors.toList())));
        });

        cc.getProxies().forEach(proxy -> {
            proxy.setHost("host" + proxy.getKey());
            proxy.setNtHost("nthost" + proxy.getKey());
            proxy.setPassword("password" + proxy.getKey());
            proxy.setRedirectedToHosts("redirected" + proxy.getKey());
            proxy.setUsername("username" + proxy.getKey());
            proxy.setDomain("domain" + proxy.getKey());
            proxy.setServices("services" + proxy.getKey());
        });

        cc.getLocalReplications().forEach(lrd -> {
            lrd.setIncludePathPrefixPattern("pathPrefix" + lrd.getRepoKey());
            lrd.setExcludePathPrefixPattern("");
            lrd.setProxy(cc.getProxy("proxy" + lrd.getRepoKey().charAt(lrd.getRepoKey().length() - 1)));
        });

        cc.setCleanupConfig(new CleanupConfigDescriptor());
        cc.getCleanupConfig().setCronExp("cron-exp");

        cc.setReverseProxies(Lists.newArrayList(getReverseProxy(cc, 1), getReverseProxy(cc, 2)));

        cc.setFolderDownloadConfig(new FolderDownloadConfigDescriptor());
        cc.setFooter("footer");

        cc.setGcConfig(new GcConfigDescriptor());
        cc.getGcConfig().setCronExp("cron-ta");

        cc.setIndexer(new IndexerDescriptor());
        cc.getIndexer().setCronExp("abcder");
        EulaDescriptor eulaDescriptor = new EulaDescriptor();
        eulaDescriptor.setAccepted(true);
        eulaDescriptor.setAcceptDate("xyz");
        cc.setEulaConfig(eulaDescriptor);
        SubscriptionConfig subscriptionConfig = new SubscriptionConfig();
        subscriptionConfig.setEmails(new HashSet<>());
        cc.setSubscriptionConfig(subscriptionConfig);
        RepositoriesGeneralConfigDescriptor repositoriesConfig = new RepositoriesGeneralConfigDescriptor();
        cc.setRepositoriesGeneralConfig(repositoriesConfig);
        SortedSet<String> sortedSet = new TreeSet<>();
        sortedSet.add("local1");
        sortedSet.add("local2");
        cc.getIndexer().setIncludedRepositories(sortedSet);
        cc.setMailServer(new MailServerDescriptor());
        cc.getMailServer().setArtifactoryUrl("http://localhost");
        cc.getMailServer().setFrom("from");
        cc.getMailServer().setHost("host");
        cc.getMailServer().setPassword("password");
        cc.getMailServer().setSubjectPrefix("subjectprefix");
        cc.getMailServer().setUsername("username");

        cc.setQuotaConfig(new QuotaConfigDescriptor());

        cc.setRemoteReplications(Lists.newArrayList(getRemoteReplication(cc, 1), getRemoteReplication(cc, 2)));

        addSecurityToCc(cc);

        cc.setServerName("servername");

        cc.setSumoLogicConfig(new SumoLogicConfigDescriptor());
        cc.getSumoLogicConfig().setProxy(cc.getProxy("proxy1"));
        cc.getSumoLogicConfig().setBaseUri("baseurl");
        cc.getSumoLogicConfig().setClientId("clientid");
        cc.getSumoLogicConfig().setCollectorUrl("collectorId");
        cc.getSumoLogicConfig().setDashboardUrl("dashboard");
        cc.getSumoLogicConfig().setSecret("secret");
        cc.setUrlBase("urlbase");
        cc.setFederatedRepoUrlBase("federatedRepoUrlBase");
        cc.setVirtualCacheCleanupConfig(new CleanupConfigDescriptor());
        cc.getVirtualCacheCleanupConfig().setCronExp("croni");
        cc.setItemsBrowserPagingSize(500);

        cc.setXrayConfig(new XrayDescriptor());
        cc.getXrayConfig().setArtifactoryId("art1");
        cc.getXrayConfig().setBaseUrl("base1");
        cc.getXrayConfig().setPassword("password");
        cc.getXrayConfig().setUser("user");
        cc.getXrayConfig().setXrayId("1212");

        ArtifactRetentionPolicyDescriptor policyDescriptor = new ArtifactRetentionPolicyDescriptor();
        policyDescriptor.setKey("key");
        policyDescriptor.setEnabled(true);
        policyDescriptor.setAqlQuery("aqlQuery");
        policyDescriptor.setCronExp("cronExp");
        policyDescriptor.setDescription("description");
        policyDescriptor.setType("Archive");
        policyDescriptor.setDurationInMinutes(100);
        policyDescriptor.setExpirationTimeInMonths(10);
        policyDescriptor.setSearchCriteriaForm(getSearchCriteriaForm());
        policyDescriptor.setPackageSearchCriteriaForm(getPackageCriteriaForm());
        ArtifactRetentionSettings retentionSettings = new ArtifactRetentionSettings();
        retentionSettings.setJfrogColdStorageArchivingNamespace("1234-1234-1234-1234-1234");
        retentionSettings.getRetentionPolicies().add(policyDescriptor);

        ArtifactRetentionMasterToken retentionMasterToken = new ArtifactRetentionMasterToken();
        retentionMasterToken.setTokenType("type");
        retentionMasterToken.setAccessToken("accesstoken");
        retentionMasterToken.setTokenId("tokenid");
        retentionMasterToken.setScope("scope");
        retentionMasterToken.setBaseUrl("url");
        retentionMasterToken.setExchangeUrl("exp_url");
        retentionMasterToken.setExpirationInMillis(10000);
        retentionMasterToken.setLastRefreshDateMillis(10000);
        retentionSettings.setJfrogColdStorageMasterToken(retentionMasterToken);
        retentionSettings.setJfrogColdStorageArchivingNamespace("namespace");
        cc.setRetentionSettings(retentionSettings);
        return cc;
    }

    @Nonnull
    private static ArtifactRetentionPolicySearchCriteriaDescriptor getSearchCriteriaForm() {
        ArtifactRetentionPolicySearchCriteriaDescriptor searchCriteriaDescriptor =
                new ArtifactRetentionPolicySearchCriteriaDescriptor();
        ArtifactRetentionPolicyPropertiesCriteriaDescriptor properties =
                new ArtifactRetentionPolicyPropertiesCriteriaDescriptor();
        ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor includeProps =
                new ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor();
        includeProps.setOperator("or");
        ArtifactRetentionPolicyPropertyCriteriaDescriptor property =
                new ArtifactRetentionPolicyPropertyCriteriaDescriptor();
        property.setPropertyKey("key");
        property.setPropertyValue("value");
        includeProps.setValues(Lists.newArrayList(property));
        properties.setInclude(includeProps);
        properties.setExclude(includeProps);
        searchCriteriaDescriptor.setProperties(properties);

        ArtifactRetentionPolicyPathsCriteriaDescriptor paths = new ArtifactRetentionPolicyPathsCriteriaDescriptor();
        ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor includePaths =
                new ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor();
        includePaths.setOperator("and");
        includePaths.setValues(new ArrayList<>());
        paths.setInclude(includePaths);
        paths.setExclude(includePaths);
        searchCriteriaDescriptor.setRepositories(paths);
        searchCriteriaDescriptor.setPaths(paths);
        searchCriteriaDescriptor.setNames(paths);
        searchCriteriaDescriptor.setCreatedBefore(1L);
        searchCriteriaDescriptor.setDownloadedBefore(1L);

        return searchCriteriaDescriptor;
    }

    private static PackageSearchCriteriaDescriptor getPackageCriteriaForm() {
        PackageSearchCriteriaDescriptor descriptor = new PackageSearchCriteriaDescriptor();
        descriptor.setPackageType("docker");
        descriptor.setComponentName("someComponentName");
        descriptor.setVersion("0.0.1");
        descriptor.setPackageRepos(new ArrayList<>(Collections.singleton("repo"))); // modifiable list
        descriptor.setCreatedBefore(1L);
        descriptor.setDownloadedBefore(1L);
        return descriptor;
    }

    // Reenable to print the configuration yaml for documentation purposes
    @Test(enabled = false)
    public void printFullCentralConfigYaml() {
        System.out.println(objectToYaml(getFullConfig(), ""));
    }

    private String objectToYaml(Object object, String prefix) {
        if (object instanceof Collection) {
            return ((Collection<Object>) object).stream()
                    .map(val -> objectToYaml(val, prefix + "  "))
                    .map(StringUtils::trim)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining("\n" + prefix + "- ", "\n" + prefix + "- ", ""));
        }

        if (object instanceof Map) {
            return ((Map<Object, Object>) object).entrySet().stream()
                    .map(entry -> prefix + entry.getKey() + ":" +
                            objectToYaml(entry.getValue(), prefix + "  "))
                    .map(StringUtils::trim)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining("\n" + prefix, "\n" + prefix, ""));
        }

        if (DiffMerger.isPrimitiveOrWrapperOrAtomic(object.getClass(), null)) {
            return "" + object;
        }

        return Stream.of(object.getClass().getMethods())
                .filter(method -> method.getName().startsWith("get") || method.getName().startsWith("is"))
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> method.getAnnotation(DiffIgnore.class) == null)
                .filter(method -> !method.getName().equals("getClass") &&
                        !method.getName().equals("getDeclaringClass") &&
                        !method.getName().equals("isEmpty"))
                .sorted((method1, method2) -> {
                    if (method1.getAnnotation(DiffKey.class) != null) {
                        return -1;
                    }
                    if (method2.getAnnotation(DiffKey.class) != null) {
                        return 1;
                    }
                    return method1.getName().compareTo(method2.getName());
                })
                .map(method -> {
                    try {
                        Object newObject = wrapException(() -> method.invoke(object), IllegalStateException.class);
                        if (newObject == null) {
                            System.out.println(method.getName() + " invoke is null");
                            return "";
                        }
                        if (StringUtils.isEmpty(newObject.toString())) {
                            return "";
                        }
                        if (ClassUtils.isAssignable(method.getReturnType(), Collection.class)) {
                            Class<?> parameterType = DiffMerger.findParameterType(
                                    ((ParameterizedType) method.getGenericReturnType())
                                            .getActualTypeArguments()[0]);
                            boolean primitive = DiffMerger.isPrimitiveOrWrapperOrAtomic(parameterType, null);
                            Function<Object, Object> keyMethod = primitive ? Function.identity() :
                                    v -> wrapException(() -> DiffMerger.findKeyMethod(parameterType).invoke(v),
                                            IllegalStateException.class);
                            if (method.getAnnotation(DiffReference.class) != null) {
                                newObject = ((Collection) newObject).stream()
                                        .map(keyMethod)
                                        .collect(Collectors.toList());
                            } else if (!primitive) {
                                String keyName =
                                        DiffUtils.toFieldName(DiffMerger.findKeyMethod(parameterType).getName());
                                newObject = ((Collection) newObject).stream()
                                        .collect(Collectors.toMap(keyMethod, Function.identity()));

                                ((Map) newObject).forEach((key, val) ->
                                        wrapException(() -> DiffMerger.findSetter(parameterType.getMethods(), keyName)
                                                .invoke(val, ""), IllegalStateException.class));
                            }
                        } else if (method.getAnnotation(DiffReference.class) != null) {
                            Method keyMethod = DiffMerger.findKeyMethod(method.getReturnType());
                            Object fNewObject = newObject;
                            newObject = wrapException(() -> keyMethod.invoke(fNewObject), IllegalStateException.class);
                        } else if (ClassUtils.isAssignable(method.getReturnType(), Map.class)) {
                            Class<?> parameterType = DiffMerger.findParameterType(
                                    ((ParameterizedType) method.getGenericReturnType())
                                            .getActualTypeArguments()[1]);
                            String keyName = DiffUtils.toFieldName(DiffMerger.findKeyMethod(parameterType).getName());

                            ((Map) newObject).forEach((key, val) ->
                                    wrapException(() -> DiffMerger.findSetter(parameterType.getMethods(), keyName)
                                            .invoke(val, ""), IllegalStateException.class));
                        }

                        String methodName = (method.getAnnotation(DiffElement.class) != null &&
                                !method.getAnnotation(DiffElement.class).name().isEmpty() ?
                                method.getAnnotation(DiffElement.class).name() : method.getName());

                        return prefix + DiffUtils.toFieldName(methodName) + ": " +
                                objectToYaml(newObject, prefix + "  ");
                    } catch (IllegalStateException | IllegalArgumentException e) {
                        throw new IllegalStateException("Error while processing " + method.getName(), e);
                    }
                })
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n", "\n", ""));
    }

    private static void addSecurityToCc(CentralConfigDescriptorImpl cc) {
        cc.setSecurity(new SecurityDescriptor());
        cc.getSecurity().setCentralOAuthProviders(new CentralOAuthProviders(false, new ArrayList<>()));
        cc.getSecurity().setPasswordSettings(new PasswordSettings());
        cc.getSecurity().getPasswordSettings().setExpirationPolicy(new PasswordExpirationPolicy());
        cc.getSecurity().getPasswordSettings().getExpirationPolicy().setCurrentPasswordValidFor(100);
        cc.getSecurity().getPasswordSettings().setResetPolicy(new PasswordResetPolicy());
        cc.getSecurity().getPasswordSettings().getResetPolicy().setMaxAttemptsPerAddress(1000);
        cc.getSecurity().setSigningKeysSettings(new SigningKeysSettings());
        cc.getSecurity().getSigningKeysSettings().setKeyStorePassword("abcaa");
        cc.getSecurity().getSigningKeysSettings().setPassphrase("aasscaa");
        cc.getSecurity().setSshServerSettings(new SshServerSettings());
    }

    private static RemoteReplicationDescriptor getRemoteReplication(CentralConfigDescriptorImpl cc, int i) {
        RemoteReplicationDescriptor rr = new RemoteReplicationDescriptor();
        rr.setCronExp("0 0/7 * * * ?" + i);
        rr.setRepoKey("repokey" + i);
        rr.setIncludePathPrefixPattern("http://momo.com" + i);
        rr.setExcludePathPrefixPattern("");
        rr.setReplicationKey("repokey" + i);
        return rr;
    }

    private static RepoLayout getRepoLayout(String key) {
        RepoLayout rl = new RepoLayout();
        rl.setFileIntegrationRevisionRegExp("fileIntg" + key);
        rl.setFolderIntegrationRevisionRegExp("folderintg" + key);
        rl.setDescriptorPathPattern("descPathPattern" + key);
        rl.setName("name" + key);
        rl.setArtifactPathPattern("artifactoryPathPattern" + key);
        return rl;
    }

    private static ReverseProxyDescriptor getReverseProxy(CentralConfigDescriptor cc, int i) {
        ReverseProxyDescriptor rp = new ReverseProxyDescriptor();
        rp.setKey("reverseproxy" + i);
        rp.setArtifactoryAppContext("app-context" + i);
        rp.setArtifactoryServerName("server-name" + i);
        rp.setDockerReverseProxyMethod(ReverseProxyMethod.NOVALUE);
        rp.setPublicAppContext("publicAppDcontext" + i);

        ReverseProxyRepoConfig reverseProxyRepoConfig = new ReverseProxyRepoConfig();
        reverseProxyRepoConfig.setRepoRef("local1");
        reverseProxyRepoConfig.setServerName("servername" + i);
        reverseProxyRepoConfig.setPort(1010 + i);

        ReverseProxyRepoConfig reverseProxyRepoConfig2 = new ReverseProxyRepoConfig();
        reverseProxyRepoConfig2.setRepoRef("local2");
        reverseProxyRepoConfig2.setServerName("servername2" + i);
        reverseProxyRepoConfig2.setPort(2020 + i);

        rp.setReverseProxyRepoConfigs(Lists.newArrayList(reverseProxyRepoConfig, reverseProxyRepoConfig2));
        rp.setServerName("servername" + i);
        rp.setServerNameExpression("servernameexpression" + i);
        rp.setSslCertificate("ssl-cert" + i);
        rp.setSslKey("ssl-key" + i);
        rp.setUpStreamName("upstreamname" + i);
        rp.setWebServerType(WebServerType.APACHE);

        return rp;
    }
}
