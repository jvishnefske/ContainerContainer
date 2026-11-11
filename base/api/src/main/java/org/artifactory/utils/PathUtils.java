package org.artifactory.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.RepoPath;

/**
 * @author Igor Gumush
 */
@UtilityClass
public class PathUtils {
    public static String removeDashCache(String repoKey) {
        return StringUtils.removeEnd(repoKey, RepoPath.REMOTE_CACHE_SUFFIX);
    }

    public static boolean isPathContainsSpecialChars(String path) {
        return path.contains("_") || path.contains("%");
    }

    public static String replaceSpecialChars(String path) {
        return path.replace("_", "^_").replace("%", "^%");
    }
}