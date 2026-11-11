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

package org.artifactory.metrics.providers.features;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.callhome.FeatureGroup;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.descriptor.download.FolderDownloadConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.XrayRepoConfig;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.artifactory.descriptor.repo.LocalCacheRepoDescriptor.PATH_SUFFIX;
import static org.artifactory.util.FederatedMemberUtils.isLocal;

/**
 * This class represent the repositories feature group of the CallHome feature
 *
 * @author Shay Bagants
 */
@Component
public class RepositoriesFeature implements CallHomeFeature {
    private final CentralConfigService configService;
    private final InternalRepositoryService repoService;
    private final StorageSummaryService storageSummaryService;

    private static final String PACKAGE_TYPE = "package_type";

    public RepositoriesFeature(CentralConfigService configService,
            InternalRepositoryService repoService,
            StorageSummaryService storageSummaryService) {
        this.configService = configService;
        this.repoService = repoService;
        this.storageSummaryService = storageSummaryService;
    }

    @Override
    public FeatureGroup getFeature() {

        FeatureGroup repositoriesFeature = new FeatureGroup("repositories");
        FeatureGroup localRepositoriesFeature = new FeatureGroup("local repositories");
        FeatureGroup federatedRepositoriesFeature = new FeatureGroup("federated repositories");
        FeatureGroup remoteRepositoriesFeature = new FeatureGroup("remote repositories");
        FeatureGroup distributionRepositoriesFeature = new FeatureGroup("distribution repositories");

        FeatureGroup trashCanFeature = new FeatureGroup("Trashcan");
        TrashcanConfigDescriptor trashcanConfig = configService.getDescriptor().getTrashcanConfig();
        trashCanFeature.addFeatureAttribute("enabled",
                trashcanConfig.isEnabled());
        trashCanFeature.addFeatureAttribute("retention_period",
                trashcanConfig.getRetentionPeriodDays());

        FeatureGroup folderDownloadFeature = new FeatureGroup("Folder Download");
        FolderDownloadConfigDescriptor folderDownloadConfig = configService.getDescriptor().getFolderDownloadConfig();
        folderDownloadFeature.addFeatureAttribute("enabled", folderDownloadConfig.isEnabled());
        folderDownloadFeature.addFeatureAttribute("enabledForAnonymous", folderDownloadConfig.isEnabledForAnonymous());
        folderDownloadFeature.addFeatureAttribute("max_size", folderDownloadConfig.getMaxDownloadSizeMb());
        folderDownloadFeature.addFeatureAttribute("max_files", folderDownloadConfig.getMaxFiles());
        folderDownloadFeature
                .addFeatureAttribute("max_parallel_downloads", folderDownloadConfig.getMaxConcurrentRequests());
        folderDownloadFeature
                .addFeatureAttribute("enabledEmptyDirectories", folderDownloadConfig.isEnabledEmptyDirectories());

        List<RealRepoConfig> localAndRemoteRepositoryConfigs = repoService.getLocalFederatedAndRemoteRepoConfigs();

        localAndRemoteRepositoryConfigs.forEach(rr -> {
            if (rr.isLocal()) {
                if (rr.getRepoType().equals(RepoDetailsType.FEDERATED)) {
                    addFederatedRepoFeatures(federatedRepositoriesFeature, (FederatedRepoConfig) rr,
                            configService.getDescriptor().getFederatedRepoUrlBaseOrDefault());
                } else {
                    addLocalRepoFeatures(localRepositoriesFeature, rr);
                }
            } else {
                addRemoteRepoFeatures(remoteRepositoriesFeature, rr);
            }
        });

        long localCount = localAndRemoteRepositoryConfigs.stream()
                .filter(RealRepoConfig::isLocal)
                .filter(r -> !r.getRepoType().equals(RepoDetailsType.FEDERATED))
                .count();
        long federatedCount = localAndRemoteRepositoryConfigs.stream()
                .filter(RealRepoConfig::isLocal)
                .filter(r -> r.getRepoType().equals(RepoDetailsType.FEDERATED))
                .count();
        String numOfRepos = "number_of_repositories";
        localRepositoriesFeature.addFeatureAttribute(numOfRepos, localCount);
        federatedRepositoriesFeature.addFeatureAttribute(numOfRepos, federatedCount);
        remoteRepositoriesFeature.addFeatureAttribute(numOfRepos,
                localAndRemoteRepositoryConfigs.size() - localCount);

        repositoriesFeature.addFeature(localRepositoriesFeature);
        repositoriesFeature.addFeature(federatedRepositoriesFeature);
        repositoriesFeature.addFeature(remoteRepositoriesFeature);
        repositoriesFeature.addFeature(distributionRepositoriesFeature);

        // virtual repos
        FeatureGroup virtualRepositoriesFeature = new FeatureGroup("virtual repositories");
        List<VirtualRepo> virtualRepositories = repoService.getVirtualRepositories();
        virtualRepositoriesFeature
                .addFeatureAttribute(numOfRepos, virtualRepositories.size());
        addVirtualRepoFeatures(virtualRepositoriesFeature, virtualRepositories);
        repositoriesFeature.addFeature(virtualRepositoriesFeature);

        repositoriesFeature.addFeature(trashCanFeature);
        repositoriesFeature.addFeature(folderDownloadFeature);

        return repositoriesFeature;
    }

    private void addFederatedRepoFeatures(FeatureGroup federatedRepositoriesFeature, FederatedRepoConfig fedRepoConfig,
            String urlBase) {
        FeatureGroup federatedRepo = createRepoFeatureGroup(fedRepoConfig);
        Optional<FederatedMember> localMember = fedRepoConfig.getRepoTypeConfig().getFederatedMembers().stream()
                .filter(member -> isLocal(member, fedRepoConfig.getKey(), urlBase))
                .findFirst();
        federatedRepo.addFeatureAttribute("enabled", localMember.isPresent() ? localMember.get().isEnabled() : false);
        federatedRepo.addFeatureAttribute("number_of_mirrors", fedRepoConfig.getRepoTypeConfig()
                .getFederatedMembers().size() - 1);
        addRepoXRaySettings(federatedRepo, fedRepoConfig);
        addRepoStorageAttributes(federatedRepo, fedRepoConfig.getKey());
        federatedRepositoriesFeature.addFeature(federatedRepo);
    }

    /**
     * Collects virtual repo metadata  {@see RTFACT-8412}
     */
    private void addLocalRepoFeatures(FeatureGroup localRepositoriesFeature, final RealRepoConfig realRepoConfig) {
        // local repos
        FeatureGroup localRepo = createRepoFeatureGroup(realRepoConfig);
        LocalReplicationDescriptor localReplication =
                configService.getDescriptor().getEnabledLocalReplication(realRepoConfig.getKey());
        if (localReplication != null && localReplication.isEnabled()) {
            List<LocalReplicationDescriptor> repls =
                    configService.getDescriptor().getMultiLocalReplications(realRepoConfig.getKey());
            localRepo.addFeatureAttribute("push_replication",
                    (repls == null || repls.isEmpty() ? "false" : (repls.size() > 1 ? "multi" : "true")));
            localRepo.addFeatureAttribute("event_replication", localReplication.isEnableEventReplication());
            localRepo.addFeatureAttribute("sync_properties", localReplication.isSyncProperties());
            localRepo.addFeatureAttribute("sync_deleted", localReplication.isSyncDeletes());
        } else if (localReplication == null) {
            localRepo.addFeatureAttribute("push_replication", false);
            localRepo.addFeatureAttribute("event_replication", false);
            localRepo.addFeatureAttribute("sync_deleted", false);
        }
        if (isJcrVersion() && realRepoConfig.getPackageType().isDockerGroup()) {
            localRepo.addFeatureAttribute("image_count", countNumberOfManifestInRepo(realRepoConfig.getKey()));
        }
        addRepoXRaySettings(localRepo, realRepoConfig);
        addRepoStorageAttributes(localRepo, realRepoConfig.getKey());
        localRepositoriesFeature.addFeature(localRepo);
    }

    /**
     * Collects virtual repo metadata  {@see RTFACT-8412}
     */
    private void addRemoteRepoFeatures(FeatureGroup remoteRepositoriesFeature,
            final RealRepoConfig realRepoConfig) {
        // remote repos
        FeatureGroup remoteRepo = createRepoFeatureGroup(realRepoConfig);
        RemoteReplicationDescriptor remoteReplicationDescriptor =
                configService.getDescriptor().getRemoteReplication(realRepoConfig.getKey());
        if (remoteReplicationDescriptor != null) {
            remoteRepo.addFeatureAttribute("pull_replication", remoteReplicationDescriptor.isEnabled());
            if (remoteReplicationDescriptor.isEnabled()) {
                remoteRepo.addFeatureAttribute("pull_replication_url",
                        ((RemoteRepoConfig) realRepoConfig).getRepoTypeConfig().getUrl());
                remoteRepo.addFeatureAttribute("event_replication",
                        remoteReplicationDescriptor.isEnableEventReplication());
            }
        } else {
            remoteRepo.addFeatureAttribute("pull_replication", false);
        }
        if (isJcrVersion() && realRepoConfig.getPackageType().isDockerGroup()) {
            remoteRepo.addFeatureAttribute("is_smart_repo", isSmartRepo((RemoteRepoConfig) realRepoConfig));
            LocalCacheRepo localCacheRepo = repoService.remoteRepositoryByKey(realRepoConfig.getKey())
                    .getLocalCacheRepo();
            if (localCacheRepo != null) {
                remoteRepo.addFeatureAttribute("image_count", countNumberOfManifestInRepo(localCacheRepo.getKey()));
            }
        }
        addRepoXRaySettings(remoteRepo, realRepoConfig);
        addRemoteRepoCacheStorageAttributes(remoteRepo, realRepoConfig.getKey());
        remoteRepositoriesFeature.addFeature(remoteRepo);
    }

    private boolean isSmartRepo(RemoteRepoConfig remoteRepoConfig) {
        return remoteRepoConfig.getRepoTypeConfig().getContentSynchronisation().isEnabled();
    }

    private FeatureGroup createRepoFeatureGroup(RealRepoConfig repoConfig) {
        FeatureGroup repo = new FeatureGroup(repoConfig.getKey());
        repo.addFeatureAttribute(PACKAGE_TYPE, repoConfig.getPackageType().name());
        String repoLayout = repoConfig.getBaseConfig().getRepoLayoutRef();
        if (repoLayout != null) {
            repo.addFeatureAttribute("repository_layout", repoLayout);
        }
        return repo;
    }

    private int countNumberOfManifestInRepo(String repoKey) {
        return repoService.countFiles(repoKey, "manifest.json");
    }

    private boolean isJcrVersion() {
        return ContextHelper.get().beanForType(AddonsManager.class).getArtifactoryRunningMode().isJCR();
    }

    /**
     * Collects virtual repo metadata  {@see RTFACT-8412}
     */
    private void addVirtualRepoFeatures(FeatureGroup virtualRepositoriesFeature,
            List<VirtualRepo> virtualRepositories) {
        virtualRepositories.forEach(vr -> {
            FeatureGroup virtualRepo = new FeatureGroup(vr.getKey());

            int localMembers = vr.getResolvedLocalAndFederatedRepos().size();
            int remoteMembers = vr.getResolvedRemoteRepos().size();
            virtualRepo.addFeatureAttribute("number_of_included_repositories", localMembers + remoteMembers);
            virtualRepo.addFeatureAttribute("total_nested_local_repositories", localMembers);
            virtualRepo.addFeatureAttribute("total_nested_remote_repositories", remoteMembers);

            virtualRepo.addFeatureAttribute(PACKAGE_TYPE, vr.getDescriptor().getType().name());
            if (vr.getDescriptor().getRepoLayout() != null) {
                virtualRepo.addFeatureAttribute("repository_layout", vr.getDescriptor().getRepoLayout().getName());
            }
            if (vr.getDescriptor().getDefaultDeploymentRepo() != null) {
                virtualRepo.addFeatureAttribute("configured_local_deployment",
                        vr.getDescriptor().getDefaultDeploymentRepo().getKey());
            }
            addRepoXRaySettings(virtualRepo, vr.getRepoConfig());
            addVirtualRepoStorageAttributes(virtualRepo, vr);
            virtualRepositoriesFeature.addFeature(virtualRepo);
        });
    }

    static void addRepoXRaySettings(FeatureGroup target, RepoConfig repositoryConfig) {
        XrayRepoConfig xrayConfig = repositoryConfig.getSecurityConfig().getXrayConfig();
        boolean enabled = xrayConfig != null && xrayConfig.isEnabled();
        target.addFeatureAttribute("xray_enabled", enabled);
        if (enabled) {
            target.addFeatureAttribute("xray_data_ttl", xrayConfig.getDataTtl());
            if (xrayConfig.getXrayNotAvailableMode() != null) {
                target.addFeatureAttribute("xray_failure_mode", xrayConfig.getXrayNotAvailableMode().getValue());
            }
        }
    }

    private void addRepoStorageAttributes(FeatureGroup target, String repoKey) {
        RepoStorageSummaryInfo repoStorageSummary = this.storageSummaryService.get().getRepoStorageSummary(repoKey);
        if (repoStorageSummary == null) {
            // The storage summary is updated once in 6 hours, so it may miss some (ewn) repositories
            return;
        }

        target.addFeatureAttribute("total_files", repoStorageSummary.getFilesCount());
        target.addFeatureAttribute("total_folders", repoStorageSummary.getFoldersCount());
        target.addFeatureAttribute("used_space_bytes", repoStorageSummary.getUsedSpace());
    }

    private void addRemoteRepoCacheStorageAttributes(FeatureGroup target, String repoKey) {
        RepoStorageSummaryInfo repoStorageSummary = this.storageSummaryService.get().getRepoStorageSummary(
                repoKey + PATH_SUFFIX
        );
        if (repoStorageSummary == null) {
            return;
        }

        target.addFeatureAttribute("total_cache_files", repoStorageSummary.getFilesCount());
        target.addFeatureAttribute("total_cache_folders", repoStorageSummary.getFoldersCount());
        target.addFeatureAttribute("used_cache_space_bytes", repoStorageSummary.getUsedSpace());
    }

    private void addVirtualRepoStorageAttributes(FeatureGroup target, VirtualRepo virtualRepo) {
        StorageSummaryInfo storageSummary = this.storageSummaryService.get();
        long totalFiles = 0;
        long totalFolders = 0;
        long totalUsedSpace = 0;
        long totalCacheFiles = 0;
        long totalCacheFolders = 0;
        long totalCacheUsedSpace = 0;

        // Accumulate storage information for all members
        for (LocalRepo<?, ?> member : virtualRepo.getResolvedLocalFederatedAndCachedRepos()) {
            RepoStorageSummaryInfo repoStorageSummary = storageSummary.getRepoStorageSummary(member.getKey());
            if (repoStorageSummary != null) {
                long repoFiles = repoStorageSummary.getFilesCount();
                long repoFolders = repoStorageSummary.getFoldersCount();
                long repoUsedSpace = repoStorageSummary.getUsedSpace();

                totalFiles += repoFiles;
                totalFolders += repoFolders;
                totalUsedSpace += repoUsedSpace;

                if (member.isCache()) {
                    totalCacheFiles += repoFiles;
                    totalCacheFolders += repoFolders;
                    totalCacheUsedSpace += repoUsedSpace;
                }
            }
        }

        // Report
        target.addFeatureAttribute("total_files", totalFiles);
        target.addFeatureAttribute("total_folders", totalFolders);
        target.addFeatureAttribute("used_space_bytes", totalUsedSpace);

        target.addFeatureAttribute("total_cache_files", totalCacheFiles);
        target.addFeatureAttribute("total_cache_folders", totalCacheFolders);
        target.addFeatureAttribute("used_cache_space_bytes", totalCacheUsedSpace);
    }
}
