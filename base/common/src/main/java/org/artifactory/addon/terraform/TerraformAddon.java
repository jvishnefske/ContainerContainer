package org.artifactory.addon.terraform;

import org.artifactory.common.addon.Addon;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPath;

/**
 * @author BarakH
 */
public interface TerraformAddon extends Addon {

    default void addTerraformModule(FileInfo info) {
        //noop
    }

    default void addTerraformProvider(FileInfo info) {
        //noop
    }

    default void deleteTerraformModule(FileInfo info) {
        //noop
    }

    default void reindexTerraform(String repoKey) {
        //noop
    }

    /**
     * Checks if zip file
     */
    default boolean isTerraformFile(String fileName) {
        return false;
    }

    default boolean isTerraformStateFile(String fileName) {
        return false;
    }

    /**
     * Extracts Terraform metadata for a given Terraform module
     */
    default TerraformMetadataInfo getTerraformMetadataIntoUiModel(RepoPath repoPath) {
        return null;
    }

    /**
     * @return The decrypted content of the requested state file
     */
    default String getTerraformStateFileContent(RepoPath repoPath) {
        return null;
    }

    default void notifyStateCreation() {
    }
}
