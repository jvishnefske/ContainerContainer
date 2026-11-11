package org.artifactory.addon.replication;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.replication.ReplicationBaseDescriptor;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;

/**
 * Helper class for common replication functionality.
 *
 * @author Yossi Shaul
 */
public class ReplicationHelper {

    /**
     * Returns the root path to start replication from
     */
    public static RepoPath getRepoPath(ReplicationBaseDescriptor descriptor) {
        String pathPrefix = resolvePathPrefix(descriptor);
        return InternalRepoPathFactory.create(descriptor.getRepoKey(), pathPrefix);
    }

    /**
     * In case includePathPrefixPattern is from old style path prefix (not ant pattern) and no excludePathPrefixPattern,
     * return the includePathPrefixPattern as prefix. return / otherwise
     */
    private static String resolvePathPrefix(ReplicationBaseDescriptor descriptor) {
        String includePathPrefixPattern = descriptor.getIncludePathPrefixPattern();
        if (StringUtils.isBlank(descriptor.getExcludePathPrefixPattern()) &&
                includePathPrefixPattern != null && !includePathPrefixPattern.contains("*")) {
            return includePathPrefixPattern;
        }
        return "/";
    }

}
