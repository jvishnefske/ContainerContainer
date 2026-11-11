package org.artifactory.repo.migration.mappers;

import jakarta.annotation.Nonnull;
import org.artifactory.descriptor.repo.releasebundle.ReleaseBundlesRepoDescriptor;
import org.artifactory.repo.RepoDetailsType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.migration.mappers.RepoConfigMigrationMapperUtils.buildLocalPackageTypeConfig;

public class ReleaseBundleRepositoryDescriptorMapper implements RepoConfigMapper<ReleaseBundlesRepoDescriptor> {

    @Override
    public Map<String, Object> map(ReleaseBundlesRepoDescriptor descriptor) {
        Map<String, Object> repositoryConfig = new HashMap<>();
        repositoryConfig.put("key", descriptor.getKey());
        repositoryConfig.put("packageType", descriptor.getType().getType());
        repositoryConfig.put("type", "releaseBundle");
        repositoryConfig.put("baseConfig", buildBaseConfig(descriptor));
        repositoryConfig.put("repoTypeConfig", buildRepoTypeConfig(descriptor));
        repositoryConfig.put("packageTypeConfig", new HashMap<>(buildLocalPackageTypeConfig(descriptor)));
        repositoryConfig.put("securityConfig", RepoConfigMigrationMapperUtils.buildSecurityConfig(descriptor));
        return repositoryConfig;
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.RELEASE_BUNDLE;
    }

    private Map<String, Object> buildBaseConfig(ReleaseBundlesRepoDescriptor descriptor) {
        Map<String, Object> baseConfig = new HashMap<>();
        baseConfig.put("key", descriptor.getKey());
        baseConfig.put("packageType", descriptor.getType().getType());
        baseConfig.put("description", descriptor.getDescription());
        baseConfig.put("notes", descriptor.getNotes());
        baseConfig.put("type", getRepoType());
        if (descriptor.getRepoLayout() != null) {
            baseConfig.put("repoLayoutRef", descriptor.getRepoLayout().getName());
        }
        return baseConfig;
    }

    @Nonnull
    protected Map<String, Object> buildRepoTypeConfig(ReleaseBundlesRepoDescriptor descriptor) {
        Map<String, Object> releaseBundleConfig = new HashMap<>();
        releaseBundleConfig.put("snapshotVersionBehavior",
                org.artifactory.repo.packagetype.SnapshotVersionBehavior.valueOf(
                        descriptor.getSnapshotVersionBehavior().name()));
        releaseBundleConfig.put("maxUniqueSnapshots", descriptor.getMaxUniqueSnapshots());
        releaseBundleConfig.put("handleReleases", descriptor.isHandleReleases());
        releaseBundleConfig.put("handleSnapshots", descriptor.isHandleSnapshots());
        if (descriptor.getCdnRedirectRepoConfig() != null) {
            Map<String, Object> cdnRedirectRepoConfig = new HashMap<>();
            cdnRedirectRepoConfig.put("enabled", descriptor.getCdnRedirectRepoConfig().isEnabled());
            releaseBundleConfig.put("cdnRedirectRepoConfig", cdnRedirectRepoConfig);
        }
        if (descriptor.getDownloadRedirectConfig() != null) {
            Map<String, Object> downloadRedirectConfig = new HashMap<>();
            downloadRedirectConfig.put("enabled", descriptor.getDownloadRedirectConfig().isEnabled());
            releaseBundleConfig.put("downloadRedirectConfig", downloadRedirectConfig);
        }
        List<String> propertySetRefs = new ArrayList<>();
        descriptor.getPropertySets().forEach(propertySet -> propertySetRefs.add(propertySet.getName()));
        releaseBundleConfig.put("propertySetRefs", propertySetRefs);
        releaseBundleConfig.put("blackedOut", descriptor.isBlackedOut());
        releaseBundleConfig.put("archiveBrowsingEnabled", descriptor.isArchiveBrowsingEnabled());
        releaseBundleConfig.put("checksumPolicyType",
                org.artifactory.repo.local.LocalRepoChecksumPolicyType.valueOf(
                        descriptor.getChecksumPolicyType().name()));
        releaseBundleConfig.put("priorityResolution", descriptor.isPriorityResolution());
        return releaseBundleConfig;
    }
}