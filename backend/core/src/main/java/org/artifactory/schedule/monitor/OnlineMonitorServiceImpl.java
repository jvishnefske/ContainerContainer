package org.artifactory.schedule.monitor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.Reloadable;
import org.artifactory.util.BasicHttpUtils;
import org.artifactory.util.HttpClientUtils;
import org.jfrog.client.util.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Alexei Vainshtein
 */
@Slf4j
@Service
@Reloadable(beanClass = OnlineMonitorService.class,
        initAfter = InternalCentralConfigService.class)
public class OnlineMonitorServiceImpl implements OnlineMonitorService {

    @VisibleForTesting
    final Map<String, ScheduledFuture> offlineReposFutures = new ConcurrentHashMap<>();
    @VisibleForTesting
    ScheduledExecutorService onlineMonitorScheduler;
    private static final List<Integer> offlineStatusCodes = Lists.newArrayList(502, 503, 504, 505);
    private final InternalRepositoryService repositoryService;

    @Autowired
    public OnlineMonitorServiceImpl(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
        initOnlineMonitorScheduler();
    }

    @Override
    public void createOfflineRepoMonitor(RemoteRepo repo) {
        String repoKey = repo.getKey();
        if (offlineReposFutures.containsKey(repoKey)) {
            log.debug("Found existing offline task for {}. No need to add another", repoKey);
            return;
        }
        log.info("Creating online scheduler for {}", repoKey);
        OnlineMonitorRunnable runnable = createRunnable(repoKey);
        ScheduledFuture<?> scheduledFuture =
                onlineMonitorScheduler
                        .schedule(runnable, 0, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> oldScheduled = offlineReposFutures.put(repoKey, scheduledFuture);
        // Checks if simultaneously were added two tasks. If so, we can cancel the other
        if (oldScheduled != null) {
            log.debug("Removing previous scheduled task for {}", repoKey);
            oldScheduled.cancel(true);
        }
    }

    @Nonnull
    OnlineMonitorRunnable createRunnable(String repoKey) {
        return new OnlineMonitorRunnable(repoKey);
    }

    void initOnlineMonitorScheduler() {
        if (this.onlineMonitorScheduler == null) {
            var threadsCount = ConstantValues.onlineMonitorThreadsCount.getInt();
            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(threadsCount,
                    new BasicThreadFactory.Builder()
                            .namingPattern("online-monitor-%d")
                            .daemon(true)
                            .build());
            ((ScheduledThreadPoolExecutor) executorService).setKeepAliveTime(60, TimeUnit.SECONDS);
            ((ScheduledThreadPoolExecutor) executorService).allowCoreThreadTimeOut(true);
            ((ScheduledThreadPoolExecutor) executorService).setRemoveOnCancelPolicy(true);
            ((ScheduledThreadPoolExecutor) executorService).setMaximumPoolSize(threadsCount);
            this.onlineMonitorScheduler = executorService;
        }
    }

    @Override
    public final boolean isResourceUnavailable(StatusLine status) {
        return offlineStatusCodes.contains(status.getStatusCode());
    }

    @Override
    public boolean stopMonitorForRepo(String remoteRepoKey) {
        return terminateOldTaskIfNeeded(remoteRepoKey);
    }

    @Override
    public boolean isRemoteRepoMonitored(String remoteRepoKey) {
        return offlineReposFutures.containsKey(remoteRepoKey);
    }

    @Override
    public void destroy() {
        if (this.onlineMonitorScheduler != null) {
            this.onlineMonitorScheduler.shutdown();
        }
    }

    /**
     * Removes and cancel the running task
     *
     * @param key - The repo key to stop and remove the monitor
     */
    boolean terminateOldTaskIfNeeded(String key) {
        ScheduledFuture onlineMonitorFuture = removeRepoFromOfflineMap(key);
        if (onlineMonitorFuture != null) {
            log.debug("Canceling previous task for {}", key);
            onlineMonitorFuture.cancel(true);
            return true;
        }
        return false;
    }

    /**
     * Removes the repo from the offline map
     *
     * @param key - repo name
     * @return the scheduled task of the repo
     */
    ScheduledFuture removeRepoFromOfflineMap(String key) {
        return offlineReposFutures.remove(key);
    }

    @VisibleForTesting
    class OnlineMonitorRunnable implements Runnable {
        /**
         * max attempts until reaching the maximum wait time
         */
        private static final int MAX_FAILED_ATTEMPTS = 10;

        /**
         * Failed requests counter
         */
        private int failedAttempts = 0;
        @Getter
        private final String repoKey;

        public OnlineMonitorRunnable(String repoKey) {
            this.repoKey = repoKey;
        }

        @Override
        public void run() {
            log.debug("Online monitor started for {}", repoKey);
            RemoteRepo<?, ?> remoteRepo = repositoryService.remoteRepositoryByKey(repoKey);
            if (remoteRepo == null) {
                log.warn("Repository {} was not found. Stopping online monitor", repoKey);
                removeRepoFromOfflineMap(repoKey);
                return;
            }
            if (checkOnline(remoteRepo)) {
                if (remoteRepo.isAssumedOffline()) {
                    log.info("{} is back online!", repoKey);
                }
                remoteRepo.resetAssumedOffline();
                // Task is back online, thread will end and need to remove the map
                removeRepoFromOfflineMap(repoKey);
                return;
            }
            if (!remoteRepo.isAssumedOffline()) {
                log.info("{} is inaccessible. Setting as offline!", repoKey);
                remoteRepo.setAssumedOffline();
            }
            long nextOnlineCheckDelay = calculateNextOnlineCheckDelay(remoteRepo);
            ScheduledFuture<?> scheduledFuture =
                    onlineMonitorScheduler
                            .schedule(this, nextOnlineCheckDelay, TimeUnit.MILLISECONDS);
            offlineReposFutures.put(repoKey, scheduledFuture);
            log.debug("Online monitor for repo {} will wait for {} millis", repoKey, nextOnlineCheckDelay);
        }

        /**
         * Calculates the next time the check for online need to happen.
         * This will increase exponentially.
         *
         * @return the time until the next request
         */
        long calculateNextOnlineCheckDelay(RemoteRepo<?, ?> repo) {
            long maxFailureCacheSecs = repo.getAssumedOfflinePeriodSecs();
            long maxFailureCacheMillis = TimeUnit.SECONDS.toMillis(maxFailureCacheSecs);    // always >= 1000

            long nextOnlineCheckDelayMillis;
            failedAttempts++;
            if (failedAttempts < MAX_FAILED_ATTEMPTS) {
                if (maxFailureCacheSecs / MAX_FAILED_ATTEMPTS < 2) {
                    long failurePenaltyMillis = maxFailureCacheMillis / MAX_FAILED_ATTEMPTS;
                    nextOnlineCheckDelayMillis = failedAttempts * failurePenaltyMillis;
                } else {
                    // exponential delay
                    // calculate the base of the exponential equation based on the MAX_FAILED_ATTEMPTS and max
                    //offline period
                    // BASE pow MAX_FAILED_ATTEMPTS = MAX_DELAY ==> BASE = MAX_DELAY pow 1/MAX_FAILED_ATTEMPTS
                    double base = Math.pow(maxFailureCacheMillis, 1.0 / (double) MAX_FAILED_ATTEMPTS);
                    nextOnlineCheckDelayMillis = (long) Math.pow(base, failedAttempts);
                    // in any case don't attempt too rapidly
                    nextOnlineCheckDelayMillis = Math.max(100, nextOnlineCheckDelayMillis);
                }
            } else {
                nextOnlineCheckDelayMillis = maxFailureCacheMillis;
            }
            return nextOnlineCheckDelayMillis;
        }

        /**
         * Checks if the given url back online
         *
         * @return - true if online, false otherwise
         */
        boolean checkOnline(RemoteRepo<?, ?> repo) {
            // always test with url trailing slash
            String url = PathUtils.addTrailingSlash(repo.getUrl());
            HttpGet getMethod = new HttpGet(BasicHttpUtils.encodeQuery(url));
            CloseableHttpClient httpClient = repo.getHttpClient();
            // Probe skips redirects so JFrogDefaultRedirectStrategy/denylist
            // DNS failures do not WARN-spam (RTDEV-82711).
            getMethod.setConfig(HttpClientUtils.copyOrCreateConfig(httpClient, getMethod)
                    .setRedirectsEnabled(false).build());
            try (CloseableHttpResponse response = httpClient.execute(getMethod)) {
                log.debug("Online monitor checking URL: {}", url);
                StatusLine status = response.getStatusLine();
                log.debug("Online monitor http method completed with no exception: {}: {}",
                        status.getStatusCode(), status.getReasonPhrase());

                if (isResourceUnavailable(status)) { //RTFACT-6528
                    log.warn("Considering repo {} as offline due to the return code '{}'", repo.getKey(),
                            status.getStatusCode());
                } else {
                    return true;
                }
            } catch (Exception e) {
                // IllegalStateException can be thrown while this tread in race with destroy()
                // i.e. closed client is accesses before interrupt() takes effect
                log.warn("Online monitor http method for {} failed: {}", repo.getKey(), e.getClass().getName(), e);
            }
            return false;
        }
    }
}
