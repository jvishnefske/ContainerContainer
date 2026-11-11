package org.artifactory.addon.swift;

import org.artifactory.common.addon.Addon;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPath;

import jakarta.annotation.Nullable;
import java.io.InputStream;

/**
 * @author Natalie Kirillov
 * @author Asaf Zalcman
 */

public interface SwiftAddon extends Addon {
    /**
     * Determines whether a certain deployed package is of a valid Swift package format.
     * The desired format is as following:
     * /{scope}/{packageName}/{version}.zip
     *
     * @param fileName The package hierarchy
     * @return Whether the package hierarchy corresponds to the required one for Swift
     */
    default boolean isSwiftFile(String fileName) {
        return false;
    }

    /**
     * Takes care of indexing when adding a Swift package
     *
     * @param fileInfo The package hierarchy
     */
    default void addSwiftPackage(FileInfo fileInfo) {}

    /**
     * Takes care of deleting corresponding indexed files and updating the releases.json
     *
     * @param fileInfo The package hierarchy
     */
    default void deleteSwiftPackage(FileInfo fileInfo) {}

    default void handleSwiftReleaseMetadataPropertyChange(FileInfo fileInfo) {}

    /**
     * Takes care of re-indexing the entire repository
     *
     * @param repoKey The repository we would like to re-index
     */
    default void reindexSwift(String repoKey) {}

    /**
     * Creates release info for the UI
     *
     * @return Release info
     */
    @Nullable
    default InputStream getReleaseInfo(RepoPath repoPath) {
        return null;
    }
}
