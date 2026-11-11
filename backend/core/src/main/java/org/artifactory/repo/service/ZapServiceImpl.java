package org.artifactory.repo.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsFolder;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * @author Eli Fishman
 */
@Service
@Slf4j
public class ZapServiceImpl implements ZapService {

    @Override
    public int zap(RepoPath repoPath, LocalCacheRepo cacheRepo,
            LongSupplier timeSupplier) {
        int itemsZapped = 0;
        RemoteRepo<? extends RemoteRepoDescriptor, ? extends RemoteRepoConfig> remoteRepo = cacheRepo.getRemoteRepo();
        //Zap all nodes recursively from all retrieval caches
        VfsItem<?> fsItem = cacheRepo.getImmutableFsItem(repoPath);
        if (fsItem != null) {
            LocalCacheRepoDescriptor descriptor = cacheRepo.getDescriptor();
            long retrievalCachePeriodMillis = descriptor.getRetrievalCachePeriodMillis();
            long expiredLastUpdated = timeSupplier.getAsLong() - retrievalCachePeriodMillis;
            itemsZapped = zapRecursively(fsItem, cacheRepo, expiredLastUpdated, itemsZapped);

            // now remove all the caches related to this path and any sub paths
            remoteRepo.removeFromCaches(fsItem.getPath(), true);
            log.info("Zapped '{}' from local cache: {} items zapped.", repoPath, itemsZapped);
        }
        return itemsZapped;
    }

    private int zapRecursively(@Nonnull VfsItem<?> fsItem, LocalCacheRepo cacheRepo,
            long expiredLastUpdated, int itemsZapped) {
        if (fsItem.isFile()) {
            return getTransactionalMe().zapFile((VfsFile<?>) fsItem, cacheRepo, expiredLastUpdated, itemsZapped);
        } else {
            itemsZapped = getTransactionalMe().zapFolder((VfsFolder<?>) fsItem, cacheRepo,
                    expiredLastUpdated, itemsZapped);
            List<VfsItem<?>> children = ((VfsFolder) fsItem).getImmutableChildren();
            for (VfsItem<?> child : children) {
                itemsZapped = zapRecursively(child, cacheRepo, expiredLastUpdated, itemsZapped);
            }
        }
        return itemsZapped;
    }

    @Override
    public int zapFolder(@Nonnull VfsFolder<?> folder, LocalCacheRepo cacheRepo, long expiredLastUpdated,
            int itemsZapped) {
        MutableVfsFolder mutableFolder = cacheRepo.getMutableFolder(folder.getRepoPath());
        if (mutableFolder != null) {
            mutableFolder.setUpdated(expiredLastUpdated);
            return itemsZapped + 1;
        }
        return itemsZapped;
    }

    @Override
    public int zapFile(@Nonnull VfsFile<?> file, LocalCacheRepo cacheRepo, long expiredLastUpdated,
            int itemsZapped) {
        CacheExpiry cacheExpiry = ContextHelper.get().beanForType(CacheExpiry.class);
        if (cacheExpiry.isExpirable(cacheRepo.getDescriptor().getType(), cacheRepo.getKey(), file.getPath())) {
            // zap has a meaning only on non unique snapshot files
            MutableVfsFile mutableFile = cacheRepo.getMutableFile(file.getRepoPath());
            if (mutableFile != null) {
                mutableFile.setUpdated(expiredLastUpdated);
                return itemsZapped + 1;
            }
        }
        return itemsZapped;
    }

    /* *
     * This method is used to get the transactional instance of ZapService.
     * It is used to ensure that the zap operations are performed within a transaction,
     * otherwise if called privately the locking mechanism triggered by the lock annotation won't work.
     */
    private ZapService getTransactionalMe() {
        return ContextHelper.get().beanForType(ZapService.class);
    }

}
