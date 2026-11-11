package org.artifactory.storage.service;

import com.google.common.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigChangeReload;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.repo.cache.RepoStatsCacheService;
import org.artifactory.storage.db.fs.dao.RepoStatsDao;
import org.artifactory.storage.fs.service.RepoStatsService;
import org.artifactory.util.RepoPathUtils;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class RepoStatsServiceImpl implements RepoStatsService {

    private final RepoStatsDao repoStatsDao;
    private final RepoStatsCacheService repoStatsCacheService;
    private final RepositoryService repositoryService;

    @VisibleForTesting
    final Map<String, RepoStatDto> repoStatsBuffer = new ConcurrentHashMap<>();

    @Setter
    private LongSupplier currentTimeSupplier = System::currentTimeMillis;

    public void update(String repoKey, RepoStatDto.RepoStatUpdateType type, String originalRepoKey) {
        boolean isVirtual = isVirtualRepo(repoKey, originalRepoKey);
        if (isVirtual) {
            updateRepo(originalRepoKey, type);
        }
        updateRepo(repoKey, type);
    }

    public void updateRepo(String repoKey, RepoStatDto.RepoStatUpdateType type) {
        long currentTime = currentTimeSupplier.getAsLong();
        String withoutDashCacheRepoKey = RepoPathUtils.removeDashCache(repoKey);

        repoStatsBuffer.compute(withoutDashCacheRepoKey, (key, existing) -> {
            if (existing == null) {
                if (type == RepoStatDto.RepoStatUpdateType.DOWNLOAD) {
                    return RepoStatDto.builder()
                            .repoKey(withoutDashCacheRepoKey)
                            .lastDownloaded(currentTime)
                            .build();
                } else {
                    return RepoStatDto.builder()
                            .repoKey(withoutDashCacheRepoKey)
                            .lastUploaded(currentTime)
                            .build();
                }
            } else {
                if (type == RepoStatDto.RepoStatUpdateType.DOWNLOAD) {
                    existing.setLastDownloaded(currentTime);
                } else {
                    existing.setLastUploaded(currentTime);
                }
                return existing;
            }
        });
    }

    @Override
    public List<RepoStatDto> getByKeys(Set<String> repoKeys) {
        return repoStatsCacheService.getByKeys(repoKeys);
    }

    public void flush() {
        // Create a snapshot of the current entries in the map
        List<String> keysToFlush = new ArrayList<>(repoStatsBuffer.keySet());
        for (String key : keysToFlush) {
            // Remove and get value atomically
            RepoStatDto stat = repoStatsBuffer.remove(key);
            if (stat != null) {
                try {
                    // Save or update the entry in the database
                    repoStatsDao.upsert(key, stat);
                } catch (SQLException sqe) {
                    if (!repositoryService.existsByRepoKey(key)) {
                        log.warn("Failed to upsert repo stats for key '{}'. Repository no longer exists", key);
                    } else {
                        // Reinsert the entry back into the cache if the save fails
                        repoStatsBuffer.putIfAbsent(key, stat);
                        log.warn("Failed to upsert repo stats for key {}", key, sqe);
                    }
                }
            }
        }
    }

    @Override
    public void onRepoConfigReload(RepoConfigChangesReload repoConfigChangesReload) {
        Set<String> deletedRepoKeys = repoConfigChangesReload.filterBy(RepoConfigChange.ConfigAction.REPO_DELETE)
                .stream()
                .map(RepoConfigChangeReload::getRepoKey)
                .collect(Collectors.toSet());
        log.debug("Clearing {} deleted repo keys from repo stats cache: {}", deletedRepoKeys.size(),
                String.join(",", deletedRepoKeys));
        repoStatsCacheService.deleteByKeys(deletedRepoKeys);
        deletedRepoKeys.forEach(repoStatsBuffer::remove);
    }

    @VisibleForTesting
    boolean isVirtualRepo(String resolvedRepoKey, String originalRepoKey) {
        if (originalRepoKey != null && !originalRepoKey.equals(resolvedRepoKey)) {
            return repositoryService.existsByType(RepoDetailsType.VIRTUAL, originalRepoKey);
        }
        return false;
    }
}
