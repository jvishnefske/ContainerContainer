package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.util.CollectionUtils;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.getSystemRepo;

/**
 * This class is used to filter out unneeded descriptors in the various fetch strategies
 *
 * @author Omri Ziv
 */
public class DescriptorFilter {
    private final TreeFilter treeFilter;
    private final AuthorizationService authService;
    private final Map<String, Boolean> canAccessRepoCache = new HashMap<>();

    public DescriptorFilter(TreeFilter treeFilter) {
        this.treeFilter = treeFilter;
        this.authService = ContextHelper.get().getAuthorizationService();
    }

    public boolean accept(RepoDescriptor repoDescriptor) {
        return accept(repoDescriptor, Set.of(ArtifactoryPermission.values()));
    }

    public boolean accept(RepoConfig repoConfig) {
        return accept(repoConfig, Set.of(ArtifactoryPermission.values()));
    }

    public boolean accept(RepoDescriptor repoDescriptor, @Nonnull Set<ArtifactoryPermission> permissions) {
        return matchRepoFromList(repoDescriptor.getKey()) &&
                matchRepoKey(repoDescriptor.getKey()) &&
                matchPackageType(repoDescriptor.getType()) &&
                matchRepoType(repoDescriptor) &&
                isObservabilityVisible(repoDescriptor.getKey()) &&
                (userCanReadAnywhereOnBuildRepo(authService, repoDescriptor.getKey(), repoDescriptor.getType())
                        || authService.userHasPermissionsOnRepositoryRoot(repoDescriptor.getKey(),
                        permissions, canAccessRepoCache));
    }

    public boolean accept(RepoConfig repoConfig, @Nonnull Set<ArtifactoryPermission> permissions) {
        return matchRepoFromList(repoConfig.getKey()) &&
                matchRepoKey(repoConfig.getKey()) &&
                matchPackageType(repoConfig.getPackageType()) &&
                matchRepoType(repoConfig) &&
                isObservabilityVisible(repoConfig.getKey()) &&
                (userCanReadAnywhereOnBuildRepo(authService, repoConfig.getKey(), repoConfig.getPackageType())
                        || authService.userHasPermissionsOnRepositoryRoot(repoConfig.getKey(),
                        permissions, canAccessRepoCache));
    }

    private boolean isObservabilityVisible(String repoKey) {
        return (!SystemRepos.isObservabilityRepo(repoKey) &&
                !SystemRepos.isFullSyncRepo(repoKey) &&
                !SystemRepos.isGridInfoRepo(repoKey)) || getSystemRepo(repoKey).isVisible();
    }

    private boolean matchRepoType(RepoDescriptor repoDescriptor) {
        return CollectionUtils.isNullOrEmpty(treeFilter.getRepositoryTypes()) ||
                treeFilter.getRepositoryTypes().contains(RepositoryType.byConfig(repoDescriptor));
    }

    private boolean matchRepoType(RepoConfig repoConfig) {
        return CollectionUtils.isNullOrEmpty(treeFilter.getRepositoryTypes()) ||
                treeFilter.getRepositoryTypes().contains(RepositoryType.byConfig(repoConfig));
    }

    private boolean matchPackageType(RepoType packageType) {
        return CollectionUtils.isNullOrEmpty(treeFilter.getPackageTypes()) ||
                treeFilter.getPackageTypes().contains(packageType);
    }

    private boolean matchRepoKey(String repoKey) {
        return StringUtils.isBlank(treeFilter.getByRepoKey()) ||
                StringUtils.contains(repoKey.toLowerCase(Locale.ROOT),
                        treeFilter.getByRepoKey().toLowerCase(Locale.ROOT));
    }

    private boolean matchRepoFromList(String repoKey) {
        return CollectionUtils.isNullOrEmpty(treeFilter.getRepositoryKeys()) ||
                treeFilter.getRepositoryKeys().contains(repoKey);
    }

    /**
     * Special case for the build info repo, since its improbable for admins to give users permissions on its root.
     * We allow any user that has read anywhere on the repo to see it, given that individual folders and files are
     * still filtered by permissions.
     */
    private boolean userCanReadAnywhereOnBuildRepo(AuthorizationService authService, String repoKey,
            RepoType repoType) {
        return RepoType.BuildInfo.equals(repoType) &&
                authService.hasBuildPermission(repoKey, ArtifactoryPermission.READ);
    }
}
