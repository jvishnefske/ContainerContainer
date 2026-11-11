/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.utils;

import lombok.experimental.UtilityClass;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.artifactory.api.repo.RemoteBrowsableItem;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.util.bundles.BundleConfigurationUtil.isHiddenReleaseBundlePath;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundleNavigationDisabled;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidenceNavigationDisabled;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidencePath;

/**
 * @author Yoav Luft
 * @author Yossi Shaul
 */
@UtilityClass
public class BrowseUtils {
    // Machine Learning tmp directory regex pattern, should be in the 3th/4th level of the path
    private static final Pattern MACHINELEARNING_TMP_REGEX_PATTERN = Pattern.compile("^([^/]+/){2,3}tmp(/.*)?$");

    /**
     * @return A filtered list without the checksum items (md5, sha1 abd sha256).
     */
    public static List<BaseBrowsableItem> filterChecksums(Collection<BaseBrowsableItem> items) {
        return items.stream().filter(BrowseUtils::filterChecksums).collect(Collectors.toList());
    }

    private static boolean filterChecksums(BaseBrowsableItem i) {
        String itemName = i != null ? i.getName() : null;
        return itemName != null && !isChecksumFile(itemName);
    }

    public static List<BaseBrowsableItem> filterInternalMetadata(List<BaseBrowsableItem> items, boolean canManage) {
        if (!canManage) {
            return items.stream().filter(BrowseUtils::filterInternalMetadata).collect(Collectors.toList());
        }
        return items;
    }

    public static List<ItemInfo> filterItemsIfNeeded(List<ItemInfo> items, String repoType, boolean canManage) {
        return applyFilters(items.stream(), repoType, canManage).toList();
    }

    public static Stream<ItemInfo> filterStreamItemsIfNeeded(Stream<ItemInfo> items, String repoType,
            boolean canManage) {
        items = applyFilters(items, repoType, canManage);
        if (isEvidenceNavigationDisabled()) {
            items = items.filter(node -> !isEvidencePath(node.getRepoPath().getPath()));
        }
        if (isReleaseBundleNavigationDisabled()) {
            items = items.filter(node -> !isHiddenReleaseBundlePath(node.getRepoPath()));
        }
        return items;
    }

    private static Stream<ItemInfo> applyFilters(Stream<ItemInfo> items, String repoType, boolean canManage) {
        return items.filter(item -> passesCommonFilters(item.getName(), item.getRelPath(), repoType, canManage));
    }

    private static boolean filterInternalMetadata(BaseBrowsableItem i) {
        return !InternalMetadataChecker.isInternalMetadata(i.getRelativePath());
    }

    public static boolean filterChecksums(RemoteBrowsableItem remoteBrowsableItem) {
        String itemName = remoteBrowsableItem != null ? remoteBrowsableItem.getName() : null;
        return itemName != null && !isChecksumFile(itemName);
    }

    /**
     * Common filtering logic shared between tree browsing and search.
     * This is the single source of truth for common node/item filters.
     * When adding a new common filter, add it here so it applies to both tree and search.
     *
     * @return true if the item passes all common filters, false if it should be filtered out
     */
    public static boolean passesCommonFilters(String fileName, String relativePath, String repoType,
            boolean canManage) {
        if (isChecksumFile(fileName)) {
            return false;
        }
        if (RepoType.Cargo.getType().equals(repoType) && isCargoGitFolder(relativePath)) {
            return false;
        }
        if (!canManage && relativePath != null && InternalMetadataChecker.isInternalMetadata(relativePath)) {
            return false;
        }
        return canManage || repoType == null || !RepoType.MachineLearning.getType().equals(repoType.toLowerCase())
                || !isMachineLearningTmpFolder(relativePath);
    }

    /**
     * Checks if the given file name is a checksum file (md5, sha1, sha256, etc.)
     *
     * @param fileName the file name to check
     * @return true if the file is a checksum file, false otherwise
     */
    private static boolean isChecksumFile(String fileName) {
        return ChecksumType.forFilePath(fileName) != null;
    }

    /**
     * Checks if the given relative path starts with .git (Cargo repos specific)
     *
     * @param relativePath the relative path to check
     * @return true if the path starts with .git, false otherwise
     */
    private static boolean isCargoGitFolder(String relativePath) {
        return relativePath != null && relativePath.startsWith(".git");
    }

    /**
     * Checks if the given relative path matches the Machine Learning tmp folder pattern
     *
     * @param relativePath the relative path to check
     * @return true if the path matches the ML tmp pattern, false otherwise
     */
    private static boolean isMachineLearningTmpFolder(String relativePath) {
        return relativePath != null && MACHINELEARNING_TMP_REGEX_PATTERN.matcher(relativePath).matches();
    }
}
