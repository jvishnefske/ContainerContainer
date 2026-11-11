package org.artifactory.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.SerializationUtils;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.KeyPairDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.ProxyRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.ldap.LdapSetting;
import org.artifactory.descriptor.sumologic.SumoLogicConfigDescriptor;
import org.jfrog.access.client.model.CrowdSetting;
import org.jfrog.access.client.model.OAuthGeneralSettings;
import org.jfrog.access.client.model.OAuthSetting;
import org.jfrog.access.client.model.SamlSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.isImportAllowed;

/**
 * This class performs several sanitizations of the config descriptor
 * Should be used only for SAAS migration process
 *
 * @author Igor Usenko
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CentralConfigDescriptorSanitizer {

    public static MutableCentralConfigDescriptor merge(CentralConfigDescriptor source,
            MutableCentralConfigDescriptor target) {
        MutableCentralConfigDescriptor result = SerializationUtils.clone(target);
        mergeConfigDescriptors(source, result);
        return result;
    }

    private static void mergeConfigDescriptors(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        mergeLocalRepos(source, result);
        mergeRemoteRepos(source, result);
        mergeFederatedRepos(source, result);
        mergeVirtualRepos(source, result);
        mergeReleaseBundles(source, result);
        mergeSecurityDescriptors(source, result);
        mergePropertySets(source, result);
        mergeRepoLayouts(source, result);
        mergeRemoteReplications(source, result);
        mergeLocalReplications(source, result);
        mergeSumoLogicConfigs(source, result);
        mergeKeyPairs(source, result);
        mergeRetentionPolicies(source, result);

        result.setGlobalReplicationConfig(SerializationUtils.clone(source.getReplicationsConfig()));
        result.setTrashcanConfig(SerializationUtils.clone(source.getTrashcanConfig()));
        result.setReleaseBundlesConfig(SerializationUtils.clone(source.getReleaseBundlesConfig()));
        result.setSignedUrlConfig(SerializationUtils.clone(source.getSignedUrlConfig()));
        result.setEulaConfig(SerializationUtils.clone(source.getEulaConfig()));
        result.setSubscriptionConfig(SerializationUtils.clone(source.getSubscriptionConfig()));
        result.setAuthentication(SerializationUtils.clone(source.getAuthentication()));
    }

    private static void mergeKeyPairs(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        List<KeyPairDescriptor> sourceKeyPairs = source.getKeyPairs();
        if (sourceKeyPairs != null) {
            result.setKeyPairs(sourceKeyPairs.stream().map(SerializationUtils::clone).collect(
                    Collectors.toList()));
        }
    }

    private static void mergeSumoLogicConfigs(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        SumoLogicConfigDescriptor sourceSumoLogicConfig = source.getSumoLogicConfig();
        if (sourceSumoLogicConfig != null) {
            SumoLogicConfigDescriptor sumoLogic = SerializationUtils.clone(sourceSumoLogicConfig);
            sumoLogic.setProxy(null);
            result.setSumoLogicConfig(sumoLogic);
        }
    }

    private static void mergeLocalReplications(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        List<LocalReplicationDescriptor> sourceLocalReplications = source.getLocalReplications();
        if (sourceLocalReplications != null) {
            result.setLocalReplications(sourceLocalReplications.stream().map(localReplicationDescriptor -> {
                LocalReplicationDescriptor descriptor = SerializationUtils.clone(localReplicationDescriptor);
                descriptor.setProxy(null);
                return descriptor;
            }).collect(Collectors.toList()));
        }
    }

    private static void mergeRemoteReplications(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        List<RemoteReplicationDescriptor> sourceRemoteReplications = source.getRemoteReplications();
        if (sourceRemoteReplications != null) {
            result.setRemoteReplications(sourceRemoteReplications.stream().map(SerializationUtils::clone).collect(
                    Collectors.toList()));
        }
    }

    private static void mergeRepoLayouts(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        List<RepoLayout> sourceRepoLayouts = source.getRepoLayouts();
        if (sourceRepoLayouts != null) {
            result.setRepoLayouts(sourceRepoLayouts.stream().map(SerializationUtils::clone).collect(
                    Collectors.toList()));
        }
    }

    private static void mergePropertySets(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        List<PropertySet> sourcePropertySets = source.getPropertySets();
        if (sourcePropertySets != null) {
            result.setPropertySets(sourcePropertySets.stream().map(SerializationUtils::clone).collect(
                    Collectors.toList()));
        }
    }

    private static void mergeSecurityDescriptors(CentralConfigDescriptor source,
            MutableCentralConfigDescriptor result) {
        SecurityDescriptor sourceSecurity = source.getSecurity();
        if (sourceSecurity != null) {
            SecurityDescriptor resultSecurity = result.getSecurity();
            mergeLdapSettings();
            mergeCrowdSettings();
            mergeHttpSso();
            mergeSamlSettings();
            mergeOAuthSettings();

            resultSecurity.setHideUnauthorizedResources(sourceSecurity.isHideUnauthorizedResources());
            resultSecurity.setSigningKeysSettings(SerializationUtils.clone(sourceSecurity.getSigningKeysSettings()));
            resultSecurity.setBuildGlobalBasicReadAllowed(sourceSecurity.isBuildGlobalBasicReadAllowed());
            resultSecurity.setBuildGlobalBasicReadForAnonymous(sourceSecurity.isBuildGlobalBasicReadForAnonymous());

            result.setSecurity(resultSecurity);
        }
    }

    private static void mergeOAuthSettings() {
        if (!AccessConfigHolder.getInstance().isNullOAuthGeneralSetting()) {
            OAuthGeneralSettings oAuthGeneralSettings =
                    AccessConfigHolder.getInstance().drainOAuthGeneralSettings().toBuilder().enable(false).build();
            AccessConfigHolder.getInstance().addOAuthGeneralSettings(oAuthGeneralSettings);
        }
        if (!AccessConfigHolder.getInstance().isNullOAuthSettings()) {
            List<OAuthSetting> oAuthSettings = AccessConfigHolder.getInstance().drainOAuthSetting().stream()
                    .map(s -> s.toBuilder().enabled(false).build()).toList();
            AccessConfigHolder.getInstance().addOAuthSetting(oAuthSettings);
        }
    }

    private static void mergeSamlSettings() {
        if (AccessConfigHolder.getInstance().isNullSamlSetting()) {
            return;
        }
        SamlSetting samlSetting = AccessConfigHolder.getInstance().drainSamlSetting();
        if (!samlSetting.isEnableIntegration()) {
            AccessConfigHolder.getInstance().setSamlSetting(samlSetting);
        } else {
            SamlSetting samlSettingSanitised = SamlSetting
                    .builder()
                    .enableIntegration(false)
                    .loginUrl(samlSetting.getLoginUrl())
                    .logoutUrl(samlSetting.getLogoutUrl())
                    .serviceProviderName(samlSetting.getServiceProviderName())
                    .certificate(samlSetting.getCertificate())
                    .useEncryptedAssertion(samlSetting.isUseEncryptedAssertion())
                    .syncGroups(samlSetting.isSyncGroups())
                    .groupAttribute(samlSetting.getGroupAttribute())
                    .emailAttribute(samlSetting.getEmailAttribute())
                    .autoUserCreation(samlSetting.isAutoUserCreation())
                    .allowUserToAccessProfile(samlSetting.isAllowUserToAccessProfile())
                    .autoRedirect(samlSetting.isAutoRedirect())
                    .verifyAudienceRestriction(samlSetting.isVerifyAudienceRestriction())
                    .build();
            AccessConfigHolder.getInstance().setSamlSetting(samlSettingSanitised);
        }
    }

    /**
     * RTDEV-26275 - we are deleting httpsso in purpose as it not supported in saas
     */
    private static void mergeHttpSso() {
        AccessConfigHolder.getInstance().drainHttpSsoSetting();
    }

    private static void mergeCrowdSettings() {
        if (AccessConfigHolder.getInstance().isNullCrowdSetting()) {
            return;
        }
        CrowdSetting crowdSetting = AccessConfigHolder.getInstance().drainCrowdSetting();
        if (!crowdSetting.isEnable()) {
            AccessConfigHolder.getInstance().setCrowdSetting(crowdSetting);
        } else {
            CrowdSetting crowdSettingSanitizer = CrowdSetting
                    .builder()
                    .enable(false)
                    .serverUrl(crowdSetting.getServerUrl())
                    .applicationName(crowdSetting.getApplicationName())
                    .password(crowdSetting.getPassword())
                    .sessionValidationInterval(crowdSetting.getSessionValidationInterval())
                    .useDefaultProxy(crowdSetting.isUseDefaultProxy())
                    .autoUserCreation(crowdSetting.isAutoUserCreation())
                    .allowUserToAccessProfile(crowdSetting.isAllowUserToAccessProfile())
                    .customCookieTokenKey(crowdSetting.getCustomCookieTokenKey())
                    .directAuthentication(crowdSetting.isDirectAuthentication())
                    .overrideAllGroupsUponLogin(crowdSetting.isOverrideAllGroupsUponLogin())
                    .build();
            AccessConfigHolder.getInstance().setCrowdSetting(crowdSettingSanitizer);
        }
    }

    private static void mergeLdapSettings() {
        List<LdapSetting> ldapSettings = AccessConfigHolder.getInstance().drainLdapSetting();
        if (ldapSettings.isEmpty()) {
            return;
        }
        List<LdapSetting> ldapSettingsS = new ArrayList<>();
        ldapSettings.forEach(ldapSetting -> {
            ldapSetting.setEnabled(false);
            ldapSettingsS.add(new LdapSetting(ldapSetting));
        });
        AccessConfigHolder.getInstance().addLdapSetting(ldapSettingsS);
    }

    private static void mergeReleaseBundles(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        Map<String, ReleaseBundlesRepoDescriptor> sourceReleaseBundlesRepositoriesMap =
                source.getReleaseBundlesRepositoriesMap();
        if (sourceReleaseBundlesRepositoriesMap != null) {
            Map<String, ReleaseBundlesRepoDescriptor> releaseBundlesRepositories = sourceReleaseBundlesRepositoriesMap
                    .entrySet().stream()
                    .filter(e -> isImportAllowed(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> SerializationUtils.clone(e.getValue())));
            result.setReleaseBundlesRepositoriesMap(releaseBundlesRepositories);
        }
    }

    private static void mergeVirtualRepos(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        Map<String, VirtualRepoDescriptor> sourceVirtualRepositoriesMap = source.getVirtualRepositoriesMap();
        if (sourceVirtualRepositoriesMap != null) {
            Map<String, VirtualRepoDescriptor> virtualRepositories = sourceVirtualRepositoriesMap.entrySet().stream()
                    .filter(e -> isImportAllowed(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> SerializationUtils.clone(e.getValue())));
            result.setVirtualRepositoriesMap(virtualRepositories);
        }
    }

    private static void mergeFederatedRepos(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        Map<String, FederatedRepoDescriptor> sourceFederatedRepositoriesMap = source.getFederatedRepositoriesMap();
        if (sourceFederatedRepositoriesMap != null) {
            Map<String, FederatedRepoDescriptor> federatedRepositories =
                    sourceFederatedRepositoriesMap.entrySet().stream()
                            .filter(e -> isImportAllowed(e.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                                FederatedRepoDescriptor clone = SerializationUtils.clone(e.getValue());
                                clone.setProxy(null);
                                return clone;
                            }));
            result.setFederatedRepositoriesMap(federatedRepositories);
        }
    }

    private static void mergeRemoteRepos(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        Map<String, RemoteRepoDescriptor> sourceRemoteRepositoriesMap = source.getRemoteRepositoriesMap();
        if (sourceRemoteRepositoriesMap != null) {
            Map<String, RemoteRepoDescriptor> remoteRepositories = sourceRemoteRepositoriesMap.entrySet().stream()
                    .filter(e -> isImportAllowed(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                        RemoteRepoDescriptor clone = SerializationUtils.clone(e.getValue());
                        if (clone instanceof ProxyRepoDescriptor) {
                            ((ProxyRepoDescriptor) clone).setProxy(null);
                        }
                        return clone;
                    }));
            result.setRemoteRepositoriesMap(remoteRepositories);
        }
    }

    private static void mergeLocalRepos(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        Map<String, LocalRepoDescriptor> sourceLocalRepositoriesMap = source.getLocalRepositoriesMap();
        if (sourceLocalRepositoriesMap != null) {
            Map<String, LocalRepoDescriptor> localRepositories = sourceLocalRepositoriesMap.entrySet().stream()
                    .filter(e -> isImportAllowed(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> SerializationUtils.clone(e.getValue())));
            result.setLocalRepositoriesMap(localRepositories);
        }
    }

    private static void mergeRetentionPolicies(CentralConfigDescriptor source, MutableCentralConfigDescriptor result) {
        source.getRetentionPolicies().stream().map(SerializationUtils::clone).forEach(retentionPolicy -> {
            // Remove old retention policy to ensure that it gets overridden
            if (result.getRetentionPolicy(retentionPolicy.getKey()) != null) {
                result.removeRetentionPolicy(retentionPolicy.getKey());
            }
            result.addRetentionPolicy(retentionPolicy);
        });
    }
}
