package org.artifactory.storage.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.support.SupportAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.hqc.enums.HeavyQueryCacheType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.RepoStorageSummary;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.storage.fs.service.FileService;
import org.jfrog.common.ExceptionLogUtils;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.hqc.exception.HeavyQueryCacheException;
import org.jfrog.storage.hqc.model.HeavyQueryCacheConfig;
import org.jfrog.storage.hqc.service.HeavyQueryCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.artifactory.util.RepoPathUtils.removeDashCache;

/**
 * @author annakv
 */
@Service
@Slf4j
public class StorageSummaryServiceImpl implements StorageSummaryService {
    private final RepositoryService repositoryService;
    private final ProjectsService projectsService;
    private final ArtifactoryBinaryService binaryStore;
    private final FileService fileService;
    private final AddonsManager addonsManager;
    private final HeavyQueryCacheService heavyQueryCacheService;
    private StorageSummaryInfo storageSummaryCache;

    @Autowired
    public StorageSummaryServiceImpl(RepositoryService repositoryService, ProjectsService projectsService,
            ArtifactoryBinaryService binaryStore, FileService fileService, AddonsManager addonsManager,
            HeavyQueryCacheService heavyQueryCacheService) {
        this.repositoryService = repositoryService;
        this.projectsService = projectsService;
        this.binaryStore = binaryStore;
        this.fileService = fileService;
        this.addonsManager = addonsManager;
        this.heavyQueryCacheService = heavyQueryCacheService;
        this.storageSummaryCache = null;
    }

    private void populate(boolean forceRefresh) {
        boolean hqcEnabled = ConstantValues.storageSummaryHqcEnable.getBoolean();
        log.debug("Populating Storage summary, hqc enabled = {}, forceRefresh = {}", hqcEnabled, forceRefresh);
        if (hqcEnabled) {
            try {
                HeavyQueryCacheConfig config = storageSummaryHqcConfig();
                storageSummaryCache = heavyQueryCacheService.get(this::fetchStorageSummaryInfo,
                        StorageSummaryInfo.class, "", HeavyQueryCacheType.STORAGE_INFO, config, forceRefresh);
            } catch (HeavyQueryCacheException e) {
                ExceptionLogUtils.debugOrWarn(log,
                        "Failed to get info from HQC " + HeavyQueryCacheType.STORAGE_INFO, e);
                storageSummaryCache = fetchStorageSummaryInfo();
            }
        } else {
            storageSummaryCache = fetchStorageSummaryInfo();
        }
        log.debug("Populated cache : {}", storageSummaryCache);
    }

    private static HeavyQueryCacheConfig storageSummaryHqcConfig() {
        return HeavyQueryCacheConfig.builder()
                .maxBlobSizeKb(102400) // 100Mb
                .minCallableDurationMillis(200)
                .expiryUpperBound(86_400_000) // 24 hours
                .expiryLowerBound(86_400_000) // 24 hours
                .build();
    }

    @Override
    public void replaceLocalCache(StorageSummaryInfo info) {
        storageSummaryCache = info;
        log.debug("Updated cache : {}", storageSummaryCache);
    }

    @Override
    public StorageSummaryInfo getLocalCache() throws CacheUnAvailableException {
        if (storageSummaryCache == null) {
            throw new CacheUnAvailableException();
        }
        log.debug("Storage summary cache hit.");
        return storageSummaryCache;
    }

    @Override
    public @NonNull StorageSummaryInfo get() {
        if (storageSummaryCache == null) {
            populate(false);
        }
        return storageSummaryCache;
    }

    @Override
    public StorageSummaryInfo updateAndGet() {
        populate(true);
        return storageSummaryCache;
    }

    @Override
    public StorageSummaryInfo getStorageSummaryInfoHqc() {
        return fetchStorageSummaryInfo();
    }

    private StorageSummaryInfo fetchStorageSummaryInfo() {
        List<RepoConfig> repos = Lists.newArrayList();
        repos.addAll(repositoryService.getLocalFederatedAndCachedRepoConfigs());
        repos.addAll(repositoryService.getVirtualRepoConfigs());
        Set<RepoStorageSummary> summaries = getRepoStorageSummaries();

        final ImmutableMap<String, RepoConfig> reposMap =
                Maps.uniqueIndex(repos, new Function<>() {
                    @Nullable
                    @Override
                    public String apply(@Nullable RepoConfig input) {
                        if (input == null) {
                            return null;
                        }
                        return input.getKey();
                    }
                });

        Map<String, RepoStorageSummaryInfo> infos = summaries.stream().map(repoSum -> {
            RepoStorageSummaryInfo.RepositoryType repoType = getRepoType(repoSum.getRepoKey(), reposMap);
            RepoConfig repoConfig = reposMap.get(repoSum.getRepoKey());
            String repoTypeName = "NA";
            if (repoConfig != null) {
                repoTypeName = repoConfig.getPackageType().getDisplayName();
            }
            return new RepoStorageSummaryInfo(
                    repoSum.getRepoKey(), repoType, repoSum.getFoldersCount(), repoSum.getFilesCount(),
                    repoSum.getUsedSpace(),
                    repoTypeName, ProjectsService.DEFAULT_PROJECT);
        }).collect(Collectors.toMap(RepoStorageSummaryInfo::getRepoKey, info -> info));

        Map<String, Long> projectsTotalSize = new ConcurrentHashMap<>();
        Map<String, Long> projectsTotalFilesCount = new ConcurrentHashMap<>();
        Map<String, String> repoKeyToProjectMap = projectsService.getRepoKeyToProjectMap(infos.keySet(), false);
        infos.values().forEach(repoInfo -> {
            repoInfo.setProjectKey(repoKeyToProjectMap.getOrDefault(
                    removeDashCache(repoInfo.getRepoKey()), ProjectsService.DEFAULT_PROJECT)
            );
            long projectSize = projectsTotalSize.computeIfAbsent(repoInfo.getProjectKey(), k -> 0L);
            projectsTotalSize.put(repoInfo.getProjectKey(), projectSize + repoInfo.getUsedSpace());

            long projectFilesCount = projectsTotalFilesCount.computeIfAbsent(repoInfo.getProjectKey(), k -> 0L);
            projectsTotalFilesCount.put(repoInfo.getProjectKey(), projectFilesCount + repoInfo.getFilesCount());
        });

        BinariesInfo binariesInfo = binaryStore.getBinariesInfo();
        return new StorageSummaryInfo(infos, binariesInfo, projectsTotalSize, projectsTotalFilesCount);
    }

    @VisibleForTesting
    RepoStorageSummaryInfo.RepositoryType getRepoType(String repoKey,
            ImmutableMap<String, RepoConfig> repoConfigs) {
        RepoConfig repoConfig = repoConfigs.get(repoKey);
        if (repoConfig == null) {
            return RepoStorageSummaryInfo.RepositoryType.NA;
        } else if (repoConfig instanceof RemoteRepoConfig) {
            return RepoStorageSummaryInfo.RepositoryType.REMOTE;
        } else if (repoConfig instanceof VirtualRepoConfig) {
            return RepoStorageSummaryInfo.RepositoryType.VIRTUAL;
        } else if (repoConfig instanceof LocalCacheRepoConfig) {
            return RepoStorageSummaryInfo.RepositoryType.CACHE;
        } else if (repoConfig instanceof FederatedRepoConfig) {
            return RepoStorageSummaryInfo.RepositoryType.FEDERATED;
        } else if (repoConfig instanceof LocalRepoConfig) {
            return RepoStorageSummaryInfo.RepositoryType.LOCAL;
        } else {
            return RepoStorageSummaryInfo.RepositoryType.NA;
        }
    }

    @VisibleForTesting
    Set<RepoStorageSummary> getRepoStorageSummaries() {
        Set<RepoStorageSummary> summaries = fileService.getRepositoriesStorageSummary();

        SupportAddon supportAddon = addonsManager.addonByType(SupportAddon.class);
        if (!supportAddon.isSupportAddonEnabled()) {
            summaries = summaries.stream().filter(summary -> !summary.getRepoKey()
                    .equals(SystemRepos.SUPPORT_BUNDLE_REPO_KEY)).collect(Collectors.toSet());
        }
        return summaries;
    }

}
