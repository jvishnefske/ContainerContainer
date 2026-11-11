package org.artifactory.repo.remote.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.fs.RepoResource;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;

/**
 * Cache Invalidation Note:
 * When utilizing the ThreadLocalCache feature in a remote repo, it's crucial to invalidate
 * the cache after an item has been downloaded. This ensures that subsequent queries will accurately
 * recognize the existence of the newly downloaded file, maintaining data consistency and avoiding
 * potential retrieval of outdated or stale data from the cache.
 *
 * Multi-threading Consideration:
 * It's important to note that cache invalidation is initially performed only for the current thread.
 * However, there could be multiple threads that are concurrently attempting to download the same file.
 * To ensure data consistency across all threads, we additionally invalidate the cache for all other
 * threads within the RemoteRepoBase class. This secondary invalidation is performed after obtaining
 * a lock, ensuring synchronized access and preventing potential race conditions among the threads.
 *
 * @author Igor Gumush
 */
@Slf4j
public class ThreadLocalCacheRemoteDownloadInterceptor implements RemoteRepoInterceptor {

    @Override
    public void afterRemoteDownload(RepoResource remoteResource) throws RepoRejectException {
        String repo = remoteResource.getRepoPath().getRepoKey();
        log.trace("Invalidate cache for " + repo);
        ReposItemsThreadLocalCacheService.invalidateCache(repo);
    }
}