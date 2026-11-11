package org.artifactory.addon.pub;

import org.artifactory.common.addon.Addon;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;

/**
 * @author Ori Yafe
 * @author Shahaf Golan
 */
public interface PubAddon extends Addon {
    /**
     * Adds a Pub to the repository
     */
    default void addPubPackage(VfsItem fsItem) {
    }

    /**
     * Removes a Pub from the repository
     */
    default void removePubPackage(VfsItem fsItem) {
    }

    /**
     * Reindex a Pub repository
     */
    default void reindexPubRepo(String repoKey) {
    }

    /**
     * Used to extract pub metadata for a given pub
     */
    default PubMetadataInfo getPubMetadataToUiModel(RepoPath repoPath) {
        return null;
    }

    /**
     * Checks if file is pub file
     */
    default boolean isPubFile(String fileName) {
        return false;
    }
}
