package org.artifactory.repo.migration.mappers;

import jakarta.annotation.Nonnull;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.repo.RepoDetailsType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.migration.mappers.RepoConfigMigrationMapperUtils.buildLocalPackageTypeConfig;

public class LocalRepositoryDescriptorMapper implements RepoConfigMapper<LocalRepoDescriptor> {

    @Override
    public Map<String, Object> map(LocalRepoDescriptor descriptor) {
        Map<String, Object> repositoryConfig = new HashMap<>();
        repositoryConfig.put("key", descriptor.getKey());
        repositoryConfig.put("packageType", descriptor.getType());
        repositoryConfig.put("type", "local");
        repositoryConfig.put("baseConfig",
                RepoConfigMigrationMapperUtils.buildBaseConfig(descriptor));
        repositoryConfig.put("repoTypeConfig", buildRepoTypeConfig(descriptor));
        repositoryConfig.put("packageTypeConfig", new HashMap<>(buildLocalPackageTypeConfig(descriptor)));
        repositoryConfig.put("securityConfig", RepoConfigMigrationMapperUtils.buildSecurityConfig(descriptor));
        return repositoryConfig;
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.LOCAL;
    }

    @Nonnull
    private Map<String, Object> buildRepoTypeConfig(LocalRepoDescriptor descriptor) {
        Map<String, Object> localConfig = new HashMap<>();
        localConfig.put("snapshotVersionBehavior",
                org.artifactory.repo.packagetype.SnapshotVersionBehavior.valueOf(
                        descriptor.getSnapshotVersionBehavior().name()));
        localConfig.put("maxUniqueSnapshots", descriptor.getMaxUniqueSnapshots());
        localConfig.put("handleReleases", descriptor.isHandleReleases());
        localConfig.put("handleSnapshots", descriptor.isHandleSnapshots());
        if (descriptor.getCdnRedirectRepoConfig() != null) {
            Map<String, Object> cdnRedirectRepoConfig = new HashMap<>();
            cdnRedirectRepoConfig.put("enabled", descriptor.getCdnRedirectRepoConfig().isEnabled());
            localConfig.put("cdnRedirectRepoConfig", cdnRedirectRepoConfig);
        }
        if (descriptor.getDownloadRedirectConfig() != null) {
            Map<String, Object> downloadRedirectConfig = new HashMap<>();
            downloadRedirectConfig.put("enabled", descriptor.getDownloadRedirectConfig().isEnabled());
            localConfig.put("downloadRedirectConfig", downloadRedirectConfig);
        }
        List<String> propertySetRefs = new ArrayList<>();
        descriptor.getPropertySets().forEach(propertySet -> propertySetRefs.add(propertySet.getName()));
        localConfig.put("propertySetRefs", propertySetRefs);
        localConfig.put("blackedOut", descriptor.isBlackedOut());
        localConfig.put("archiveBrowsingEnabled", descriptor.isArchiveBrowsingEnabled());
        localConfig.put("checksumPolicyType",
                org.artifactory.repo.local.LocalRepoChecksumPolicyType.valueOf(
                        descriptor.getChecksumPolicyType().name()));
        localConfig.put("priorityResolution", descriptor.isPriorityResolution());
        return localConfig;
    }
}