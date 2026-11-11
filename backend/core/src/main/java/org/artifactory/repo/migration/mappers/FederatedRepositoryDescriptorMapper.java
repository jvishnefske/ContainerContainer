package org.artifactory.repo.migration.mappers;

import jakarta.annotation.Nonnull;
import org.artifactory.descriptor.repo.FederatedMember;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.repo.RepoDetailsType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.artifactory.repo.migration.mappers.RepoConfigMigrationMapperUtils.buildLocalPackageTypeConfig;

public class FederatedRepositoryDescriptorMapper implements RepoConfigMapper<FederatedRepoDescriptor> {

    @Override
    public Map<String, Object> map(FederatedRepoDescriptor descriptor) {
        Map<String, Object> repositoryConfig = new HashMap<>();
        repositoryConfig.put("key", descriptor.getKey());
        repositoryConfig.put("packageType", descriptor.getType());
        repositoryConfig.put("type", "federated");
        repositoryConfig.put("baseConfig",
                RepoConfigMigrationMapperUtils.buildBaseConfig(descriptor));
        repositoryConfig.put("repoTypeConfig", buildRepoTypeConfig(descriptor));
        repositoryConfig.put("packageTypeConfig", new HashMap<>(buildLocalPackageTypeConfig(descriptor)));
        repositoryConfig.put("securityConfig", RepoConfigMigrationMapperUtils.buildSecurityConfig(descriptor));
        return repositoryConfig;
    }

    @Override
    public RepoDetailsType getRepoType() {
        return RepoDetailsType.FEDERATED;
    }

    @Nonnull
    private Map<String, Object> buildRepoTypeConfig(FederatedRepoDescriptor descriptor) {
        Map<String, Object> federatedConfig = new HashMap<>();
        federatedConfig.put("snapshotVersionBehavior",
                org.artifactory.repo.packagetype.SnapshotVersionBehavior.valueOf(
                        descriptor.getSnapshotVersionBehavior().name()));
        federatedConfig.put("maxUniqueSnapshots", descriptor.getMaxUniqueSnapshots());
        federatedConfig.put("handleReleases", descriptor.isHandleReleases());
        federatedConfig.put("handleSnapshots", descriptor.isHandleSnapshots());
        if (descriptor.getCdnRedirectRepoConfig() != null) {
            Map<String, Object> cdnRedirectRepoConfig = new HashMap<>();
            cdnRedirectRepoConfig.put("enabled", descriptor.getCdnRedirectRepoConfig().isEnabled());
            federatedConfig.put("canRedirectRepoConfig", cdnRedirectRepoConfig);
        }
        if (descriptor.getDownloadRedirectConfig() != null) {
            Map<String, Object> downloadRedirectConfig = new HashMap<>();
            downloadRedirectConfig.put("enabled", descriptor.getDownloadRedirectConfig().isEnabled());
            federatedConfig.put("downloadRedirectConfig", downloadRedirectConfig);
        }
        List<String> propertySetRefs = new ArrayList<>();
        descriptor.getPropertySets().forEach(propertySet -> propertySetRefs.add(propertySet.getName()));
        federatedConfig.put("propertySetRefs", propertySetRefs);
        federatedConfig.put("blackedOut", descriptor.isBlackedOut());
        federatedConfig.put("archiveBrowsingEnabled", descriptor.isArchiveBrowsingEnabled());
        federatedConfig.put("checksumPolicyType",
                org.artifactory.repo.local.LocalRepoChecksumPolicyType.valueOf(
                        descriptor.getChecksumPolicyType().name()));

        List<Map<String, Object>> federatedMembersConfig = new ArrayList<>();
        for (FederatedMember federatedMemberDescriptor : descriptor.getFederatedMembers()) {
            Map<String, Object> federatedMember = new HashMap<>();
            federatedMember.put("enabled", federatedMemberDescriptor.isEnabled());
            federatedMember.put("mode", federatedMemberDescriptor.getMode());
            federatedMember.put("url", federatedMemberDescriptor.getUrlOrTemplate());
            federatedMembersConfig.add(federatedMember);
        }

        federatedConfig.put("federatedMembers", federatedMembersConfig);
        federatedConfig.put("modificationDate", descriptor.getModificationDate());
        if (descriptor.getProxy() != null) {
            federatedConfig.put("proxyRef", descriptor.getProxy().getKey());
        }
        federatedConfig.put("disableProxy", descriptor.isDisableProxy());
        if (descriptor.getMaxNumberOfThreads() != null) {
            federatedConfig.put("maxNumberOfThreads", descriptor.getMaxNumberOfThreads());
        }
        federatedConfig.put("priorityResolution", descriptor.isPriorityResolution());
        return federatedConfig;
    }
}