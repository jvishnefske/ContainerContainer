package org.artifactory.repo.service;

import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.common.Lock;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;

import java.util.function.LongSupplier;

/**
 * @author Eli Fishman
 */
public interface ZapService {

    int zap(RepoPath repoPath, LocalCacheRepo cacheRepo,
            LongSupplier timeSupplier);

    @Lock
    int zapFolder(VfsFolder<?> folder, LocalCacheRepo cacheRepo, long expiredLastUpdated, int itemsZapped);

    @Lock
    int zapFile(VfsFile<?> file, LocalCacheRepo cacheRepo, long expiredLastUpdated, int itemsZapped);
}
