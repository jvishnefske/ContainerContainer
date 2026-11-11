package org.artifactory.repo.cache;

import com.google.common.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.storage.db.fs.dao.RepoStatsDao;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.repoStatsCacheReloadFailureRetryDelayMillis;
import static org.artifactory.common.ConstantValues.repoStatsCacheReloadIntervalMillis;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepoStatsCacheService {

    private final ConcurrentHashMap<String, RepoStatDto> repoStats = new ConcurrentHashMap<>();
    private final AtomicLong lastCacheUpdateTime = new AtomicLong(0);
    private final AtomicLong lastFailureTime = new AtomicLong(-1);
    private final Object reloadLock = new Object();

    private final RepoStatsDao repoStatsDao;

    @Setter
    private LongSupplier currentTimeSupplier = System::currentTimeMillis;

    public List<RepoStatDto> getByKeys(Set<String> repoKeys) {
        reloadCacheIfNeeded();
        return repoKeys.stream()
                .map(repoStats::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void deleteByKeys(Set<String> repoKeys) {
        repoKeys.forEach(repoStats::remove);
    }

    /**
     * Reloads the cache if the reload interval ({@link repoStatsCacheReloadIntervalMillis}) has passed
     *      since the last update.
     * If a reload fails, it will reset lastCacheUpdateTime and will not attempt to reload again until
     *      the failure retry delay ({@link repoStatsCacheReloadFailureRetryDelayMillis}) has passed.
     * This method is thread-safe and ensures that only one thread can perform the reload at a time.
     * Setting the lastCacheUpdateTime before getting records from database to avoid multiple threads
     *      to be blocked on reload
     */
    private void reloadCacheIfNeeded() {
        long currentTime = currentTimeSupplier.getAsLong();
        if (currentTime - lastCacheUpdateTime.get() > repoStatsCacheReloadIntervalMillis.getLong()) {
            synchronized (reloadLock) {
                if (currentTime - lastCacheUpdateTime.get() > repoStatsCacheReloadIntervalMillis.getLong()) {
                    if (shouldSkipReloadOnRecentFailure()) {
                        return;
                    }
                    long lastTimestamp = lastCacheUpdateTime.get();
                    lastCacheUpdateTime.set(currentTime);

                    try {
                        Map<String, RepoStatDto> newStats = repoStatsDao.getAllByDelta(lastTimestamp).stream()
                                .collect(Collectors.toMap(RepoStatDto::getRepoKey, stat -> stat));
                        repoStats.putAll(newStats);
                        lastFailureTime.set(-1);
                        log.debug("Repo stats cache updated successfully {}", lastTimestamp);
                    } catch (SQLException e) {
                        log.error("Repo stats cache reload failed", e);
                        lastCacheUpdateTime.set(lastTimestamp);
                        lastFailureTime.set(currentTime);
                    }
                }
            }
        }
    }

    /**
     * Checks if a recent failure occurred and if the retry delay ({@link repoStatsCacheReloadFailureRetryDelayMillis})
     *      has not yet passed.
     */
    @VisibleForTesting
    boolean shouldSkipReloadOnRecentFailure() {
        long currentTime = currentTimeSupplier.getAsLong();
        if (lastFailureTime.get() >= 0 && (currentTime - lastFailureTime.get())
                < repoStatsCacheReloadFailureRetryDelayMillis.getLong()) {
            log.debug("Skipping repo stats cache reload due to recent failure, retry in {} seconds",
                    TimeUnit.MILLISECONDS.toSeconds(repoStatsCacheReloadFailureRetryDelayMillis.getLong()
                            - (currentTime - lastFailureTime.get())));
            return true;
        }
        return  false;
    }
}
