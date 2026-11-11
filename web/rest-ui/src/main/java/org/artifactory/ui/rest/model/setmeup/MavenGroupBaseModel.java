package org.artifactory.ui.rest.model.setmeup;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;

import java.util.Comparator;

public class MavenGroupBaseModel extends BaseModel {
    private static final String RELEASE_KEY = "release";
    private static final String PLUGIN_KEY = "plugin";
    private static final String SNAPSHOT_KEY = "snapshot";

    protected static final Comparator<RepoKeyType> releaseRepoKeyComparator = Comparator
            .comparing((RepoKeyType repoKeyType) -> isRelease(repoKeyType.getRepoKey()))
            .reversed()
            .thenComparing(RepoKeyType::getRepoKey);

    protected static final Comparator<RepoKeyType> pluginReleaseRepoKeyComparator = Comparator
            .comparing((RepoKeyType repoKeyType) -> isPluginOrRelease(repoKeyType.getRepoKey()))
            .reversed()
            .thenComparing(RepoKeyType::getRepoKey);

    protected static final Comparator<RepoKeyType> pluginSnapshotRepoKeyComparator = Comparator
            .comparing((RepoKeyType repoKeyType) -> isPluginOrSnapshot(repoKeyType.getRepoKey()))
            .reversed()
            .thenComparing(RepoKeyType::getRepoKey);

    protected static final Comparator<RepoKeyType> pluginResolverRepoKeyComparator = Comparator
            .comparing((RepoKeyType repoKeyType) -> isPluginAndRelease(repoKeyType.getRepoKey()))
            .reversed()
            .thenComparing(RepoKeyType::getRepoKey);

    protected static final Comparator<RepoKeyType> snapshotRepoKeyComparator = Comparator
            .comparing((RepoKeyType repoKeyType) -> isSnapshot(repoKeyType.getRepoKey()))
            .reversed()
            .thenComparing(RepoKeyType::getRepoKey);

    protected static final Comparator<RepoKeyType> anyMirrorComparator = Comparator
            .comparing((RepoKeyType repoKeyType) -> !containsRelease(repoKeyType.getRepoKey()))
            .reversed()
            .thenComparing(RepoKeyType::getRepoKey);

    private static boolean isRelease(String repoKey) {
        return StringUtils.containsIgnoreCase(repoKey, RELEASE_KEY) &&
                !StringUtils.containsIgnoreCase(repoKey, PLUGIN_KEY);
    }

    private static boolean containsRelease(String repoKey) {
        return StringUtils.containsIgnoreCase(repoKey, RELEASE_KEY);
    }

    private static boolean isSnapshot(String repoKey) {
        return StringUtils.containsIgnoreCase(repoKey, SNAPSHOT_KEY) &&
                !StringUtils.containsIgnoreCase(repoKey, PLUGIN_KEY);
    }

    private static boolean isPluginOrRelease(String repoKey) {
        return StringUtils.containsIgnoreCase(repoKey, PLUGIN_KEY) ||
                StringUtils.containsIgnoreCase(repoKey, RELEASE_KEY);
    }

    private static boolean isPluginOrSnapshot(String repoKey) {
        return StringUtils.containsIgnoreCase(repoKey, PLUGIN_KEY) ||
                StringUtils.containsIgnoreCase(repoKey, SNAPSHOT_KEY);
    }

    private static boolean isPluginAndRelease(String repoKey) {
        return StringUtils.containsIgnoreCase(repoKey, PLUGIN_KEY) &&
                StringUtils.containsIgnoreCase(repoKey, RELEASE_KEY);
    }
}