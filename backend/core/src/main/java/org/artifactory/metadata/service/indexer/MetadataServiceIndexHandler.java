package org.artifactory.metadata.service.indexer;

import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

/**
 * A service to trigger direct metadata indexing in the Metadata service.
 * <p>
 * IMPORTANT NOTE
 * 1. When implementing a new package manager, consider upload, copy, move and replication events (Deletion in
 * already
 * handled).
 * 2. Add your package type to {@link  MetadataServiceIndexHandlerImpl#REALTIME_INDEXING_PACKAGES}
 * </p>
 *
 * @author Shahaf Golan
 */
public interface MetadataServiceIndexHandler {

    /**
     * Create an async deletion task for the given repo path.
     */
    void deletePackageAsync(ItemInfo repoPath, @Nullable Map<String, Collection<String>> props);


    /**
     * Create an async task to create or update a package.
     *
     * @param itemInfo   - Of the lead file of the package artifact.ll
     * @param properties - An updated properties.
     */
    void indexPackageAsync(ItemInfo itemInfo, Properties properties);

    /**
     * Notify a property change on the given path.
     *
     * @param repoPath   - Path that was updated
     * @param properties - Current properties at the path (after the change)
     */
    void notifyPropertyChange(RepoPath repoPath, @Nullable Properties properties);
}
