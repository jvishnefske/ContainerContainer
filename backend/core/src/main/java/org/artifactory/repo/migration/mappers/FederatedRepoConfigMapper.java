package org.artifactory.repo.migration.mappers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.FederatedRepositoryRepoTypeConfig;
import org.artifactory.repo.local.LocalRepoChecksumPolicyType;
import org.artifactory.repo.packagetype.SnapshotVersionBehavior;

import java.util.Map;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.valueOf;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setFederationMembers;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setCdnRedirectRepoConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setDownloadRedirectRepoConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setPropertySetRefs;

@Slf4j
public class FederatedRepoConfigMapper {
    private FederatedRepoConfigMapper() {
    }

    public static FederatedRepoConfig mapFederatedRepoConfig(FederatedRepoConfig federatedRepoConfig,
            Map<String, Object> newValues, CentralConfigDescriptor descriptor) {
        RepoConfigMigrationPatchUtils.buildBaseConfig(federatedRepoConfig.getBaseConfig(), newValues, descriptor);
        buildRepoTypeConfig(federatedRepoConfig, newValues, descriptor);
        RepoConfigMigrationPatchUtils.buildLocalPackageTypeConfig(federatedRepoConfig.getPackageTypeConfig(),
                federatedRepoConfig.getPackageType(),
                newValues, descriptor.getRepositoriesGeneralConfig());
        RepoConfigMigrationPatchUtils.buildSecurityConfig(federatedRepoConfig.getSecurityConfig(), newValues,
                descriptor);
        return federatedRepoConfig;
    }

    private static void buildRepoTypeConfig(FederatedRepoConfig federatedRepoConfig,
            Map<String, Object> newValues, CentralConfigDescriptor descriptor) {
        FederatedRepositoryRepoTypeConfig repositoryRepoTypeConfig = federatedRepoConfig.getRepoTypeConfig();
        for (Map.Entry<String, Object> newValue : newValues.entrySet()) {
            try {
                switch (newValue.getKey()) {
                    case "snapshotVersionBehavior" -> repositoryRepoTypeConfig
                            .setSnapshotVersionBehavior(SnapshotVersionBehavior
                                    .fromString(valueOf(newValue.getValue())));
                    case "maxUniqueSnapshots" -> repositoryRepoTypeConfig
                            .setMaxUniqueSnapshots(parseInt(valueOf(newValue.getValue())));
                    case "handleReleases" -> repositoryRepoTypeConfig
                            .setHandleReleases(parseBoolean(valueOf(newValue.getValue())));
                    case "handleSnapshots" -> repositoryRepoTypeConfig
                            .setHandleSnapshots(parseBoolean(valueOf(newValue.getValue())));
                    case "cdnRedirect" -> setCdnRedirectRepoConfig(repositoryRepoTypeConfig, newValue.getValue());
                    case "downloadRedirect" ->
                            setDownloadRedirectRepoConfig(repositoryRepoTypeConfig, newValue.getValue());
                    case "blackedOut" -> repositoryRepoTypeConfig
                            .setBlackedOut(parseBoolean(valueOf(newValue.getValue())));
                    case "archiveBrowsingEnabled" -> repositoryRepoTypeConfig
                            .setArchiveBrowsingEnabled(parseBoolean(valueOf(newValue.getValue())));
                    case "checksumPolicyType" -> repositoryRepoTypeConfig.setChecksumPolicyType(
                            LocalRepoChecksumPolicyType.fromString(valueOf(newValue.getValue())));
                    case "priorityResolution" -> repositoryRepoTypeConfig
                            .setPriorityResolution(parseBoolean(valueOf(newValue.getValue())));
                    case "propertySets" ->
                            setPropertySetRefs(descriptor, repositoryRepoTypeConfig, newValue.getValue());
                    case "federatedMembers" -> setFederationMembers(federatedRepoConfig, newValues);
                    case "modificationDate" -> federatedRepoConfig
                            .setModificationDate(parseLong(valueOf(newValue.getValue())));
                    case "proxy" -> setProxyRef(descriptor, repositoryRepoTypeConfig, newValue.getValue());
                    case "disableProxy" -> repositoryRepoTypeConfig
                            .setDisableProxy(parseBoolean(valueOf(newValue.getValue())));
                    case "maxNumberOfThreads" -> repositoryRepoTypeConfig
                            .setMaxNumberOfThreads(parseInt(valueOf(newValue.getValue())));
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("No values under " + newValue.getKey());
            }
        }
    }

    private static void setProxyRef(CentralConfigDescriptor descriptor,
            FederatedRepositoryRepoTypeConfig repoTypeConfig, Object value) {
        String proxyRef = String.valueOf(value);
        if (descriptor.getProxy(proxyRef) == null) {
            log.debug("Proxy '{}' does not exist in the config descriptor", proxyRef);
        } else {
            repoTypeConfig.setProxyRef(proxyRef);
        }
    }
}