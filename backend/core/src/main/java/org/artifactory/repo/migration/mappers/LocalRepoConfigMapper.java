package org.artifactory.repo.migration.mappers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.LocalRepositoryRepoTypeConfig;
import org.artifactory.repo.local.LocalRepoChecksumPolicyType;
import org.artifactory.repo.packagetype.SnapshotVersionBehavior;

import java.util.Map;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;

import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.buildBaseConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.buildLocalPackageTypeConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.buildSecurityConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.customValueOf;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setCdnRedirectRepoConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setDownloadRedirectRepoConfig;
import static org.artifactory.repo.migration.mappers.RepoConfigMigrationPatchUtils.setPropertySetRefs;

@Slf4j
public class LocalRepoConfigMapper {

    private LocalRepoConfigMapper() {
    }

    public static LocalRepoConfig mapLocalRepoConfig(LocalRepoConfig localRepoConfig, Map<String, Object> newValues,
            CentralConfigDescriptor descriptor) {
        buildBaseConfig(localRepoConfig.getBaseConfig(), newValues, descriptor);
        buildRepoTypeConfig(localRepoConfig.getRepoTypeConfig(), newValues, descriptor);
        buildLocalPackageTypeConfig(localRepoConfig.getPackageTypeConfig(),
                localRepoConfig.getPackageType(),
                newValues, descriptor.getRepositoriesGeneralConfig());
        buildSecurityConfig(localRepoConfig.getSecurityConfig(), newValues, descriptor);
        return localRepoConfig;
    }

    private static void buildRepoTypeConfig(LocalRepositoryRepoTypeConfig repositoryRepoTypeConfig,
            Map<String, Object> newValues, CentralConfigDescriptor descriptor) {
        for (Map.Entry<String, Object> newValue : newValues.entrySet()) {
            try {
                switch (newValue.getKey()) {
                    case "snapshotVersionBehavior" -> repositoryRepoTypeConfig.setSnapshotVersionBehavior(
                            SnapshotVersionBehavior.fromString(customValueOf(newValue.getValue())));
                    case "maxUniqueSnapshots" -> repositoryRepoTypeConfig.setMaxUniqueSnapshots(
                            parseInt(valueOf(newValue.getValue())));
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
                    case "checksumPolicyType" -> repositoryRepoTypeConfig
                            .setChecksumPolicyType(LocalRepoChecksumPolicyType
                                    .fromString(customValueOf(newValue.getValue())));
                    case "priorityResolution" -> repositoryRepoTypeConfig
                            .setPriorityResolution(parseBoolean(valueOf(newValue.getValue())));
                    case "propertySets" ->
                            setPropertySetRefs(descriptor, repositoryRepoTypeConfig, newValue.getValue());
                }
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("No values under " + newValue.getKey());
            }
        }
    }
}
