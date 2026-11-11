package org.artifactory.addon.alpine;

import org.artifactory.common.addon.Addon;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;

/**
 * @author BarakH
 * @author Yuval Reches
 */
public interface AlpineAddon extends Addon {

    /**
     * Checks if file ends with "apk"
     */
    default boolean isAlpineFile(String fileName) {
        return false;
    }

    /**
     * Extracts Alpine metadata for a given Alpine package
     */
    default AlpineMetadataInfo getMetadataIntoUiModel(RepoPath repoPath) {
        return null;
    }

    /**
     * Invokes index calculation for the package's folder APKINDEX.tar.gz file.
     * Will go over the index's packages and compare to the folder's content.
     * Packages will be added/removed from index file accordingly.
     *
     * @param fsItem Alpine file changed
     */
    default void invokeAlpineIndexCalculation(VfsItem<ItemInfo> fsItem) {
    }

    /**
     * Request virtual Alpine index calculation.
     * We generate a index file based on the current user's permission to aggregated repositories.
     *
     * @return the custom index path at the virtual repo's cache
     */
    default RepoPath requestVirtualAlpineCustomIndexCalculation(RepoPath indexRepoPath) {
        return indexRepoPath;
    }

    /**
     * Initiates Alpine metadata calculation of the entire repo.
     * We use the Alpine Service and going threw the Async annotations
     */
    default void requestReindex(String repoKey) {
    }

    /**
     * Triggering virtual metadata cache eviction for Alpine virtual repository
     *
     * @param virtualRepoKey Virtual repo key
     */
    default void invokeVirtualMetadataEviction(String virtualRepoKey) {
    }
}
