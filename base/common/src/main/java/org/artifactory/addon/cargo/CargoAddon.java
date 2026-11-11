package org.artifactory.addon.cargo;

import org.artifactory.common.addon.Addon;
import org.artifactory.async.Async;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;


/**
 * @author Shahaf Golan
 */
public interface CargoAddon extends Addon {

    /**
     * Adds a Cargo crate to the registry
     */
    @Async(delayUntilAfterCommit = true)
    default void addPackage(VfsItem<?> fsItem) {
    }

    /**
     * Calculates metadata for crates downloaded from remote
     */
    default void calculateMetadata(VfsItem<?> fsItem) {
    }

    /**
     * Processes yank/unyank events.
     */
    default void yankPackage(VfsItem<?> fsItem) {
    }

    /**
     * Removes a Cargo Crate from the repository
     */
    @Async(delayUntilAfterCommit = true)
    default void removePackage(VfsItem<?> fsItem) {
    }

    /**
     * Request Cargo metadata calculation based on a repo key.
     * We use the Cargo Service and going threw the Async annotations based on the async param
     */
    default void requestCargoMetadataCalculation(RepoPath path) {
    }


    /**
     * Request Cargo metadata calculation of the entire repo
     * We use the Cargo Service and going threw the Async annotations based on the async param
     */
    @Async(delayUntilAfterCommit = true)
    default void reindex(String repoKey) {
    }

    /**
     * Used to extract helm metadata for a given helm chart
     */
    default CargoMetadataInfo getCargoMetadataToUiModel(RepoPath repoPath) {
        return null;
    }

    /**
     * Checks if file ends with "crate"
     */
    default boolean isCargoFile(String fileName) {
        return false;
    }
}