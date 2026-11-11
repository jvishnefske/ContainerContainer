package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import com.google.common.collect.Lists;
import lombok.Getter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.support.SupportAddon;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.artifactory.util.AolUtils;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.ContinueResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.repo.RepoDetailsType.SUPPORT_BUNDLES_REPO;
import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.getSystemRepo;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.RootFetchStrategy.adjustLimitInCaseShowRepoInTree;
import static org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.RootFetchStrategy.sortAndGetRepoConfigs;
import static org.jfrog.commons.continues.util.ContinueResultIndexUtils.getLastIndex;

/**
 * This service provides the fetch strategy to be used for the specified system repo
 *
 * @author Omri Ziv
 * @author Tomer Slutsky
 */
public class SystemRepoFetchService {
    private final RepositoryService repositoryService;
    private final RepositoryConfigService repositoryConfigService;
    private final AuthorizationService authService;
    private final SecurityService securityService;
    private final TreeFilter treeFilter;
    private final boolean defaultContext;
    private final DescriptorFilter descriptorFilter;
    @Getter(lazy = true)
    private final Set<ArtifactoryPermission> permissions = computePermissions();

    public SystemRepoFetchService(TreeFilter treeFilter, boolean defaultContext, DescriptorFilter descriptorFilter) {
        this.repositoryService = ContextHelper.get().getRepositoryService();
        this.repositoryConfigService = ContextHelper.get().getRepositoryConfigService();
        this.authService = ContextHelper.get().getAuthorizationService();
        this.securityService = ContextHelper.get().beanForType(SecurityService.class);
        this.treeFilter = treeFilter;
        this.defaultContext = defaultContext;
        this.descriptorFilter = descriptorFilter;
    }

    public ContinueResult<RepositoryNode> getSupportBundles(int skip, int limit) {
        List<RepositoryNode> ans = Lists.newArrayList();
        boolean containsInList = CollectionUtils.isNullOrEmpty(treeFilter.getPackageTypes())
                || treeFilter.getPackageTypes().contains(RepoType.Support);
        if (isSupportBundleVisible(skip, limit, containsInList)) {
            LocalRepoDescriptor supportBundles = repositoryService
                    .localOrFederatedRepoDescriptorByKey(SUPPORT_BUNDLE_REPO_KEY);
            ans.add(new RepositoryNode(supportBundles.getKey(), supportBundles.getType(), SUPPORT_BUNDLES_REPO));
        }
        return new ContinueResult<>(skip > 0 ? "0" : "1", ans);
    }

    private boolean isSupportBundleVisible(int skip, int limit, boolean containsInList) {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        boolean isAol = AolUtils.isAol();
        boolean isSupportAddonEnabled = addonsManager.addonByType(SupportAddon.class).isSupportAddonEnabled();
        return getSystemRepo(SUPPORT_BUNDLE_REPO_KEY).isVisible() && skip == 0 && limit > 0 && securityService.isAdmin()
                && isSupportAddonEnabled && containsInList && !isAol && defaultContext;
    }

    public ContinueResult<RepositoryNode> getTrashRepo(int skip, int limit) {
        List<RepositoryNode> ans = Lists.newArrayList();
        if (isTrashRepoVisible(skip, limit)) {
            LocalRepoDescriptor trashDescriptor =
                    repositoryService.localOrFederatedRepoDescriptorByKey(TRASH_REPO_KEY);
            ans.add(new RepositoryNode(trashDescriptor.getKey(), trashDescriptor.getType(), "trash"));
        }
        return new ContinueResult<>(skip > 0 ? "0" : "1", ans);
    }

    private boolean isTrashRepoVisible(int skip, int limit) {
        return getSystemRepo(TRASH_REPO_KEY).isVisible() && skip == 0 && limit > 0 && authService.canAccessTrashcan();
    }

    public ContinueResult<RepositoryNode> getUsageRepo(int skip, int limit) {
        List<RepositoryNode> res = Lists.newArrayList();
        if (isUsageRepoVisible(skip, limit)) {
            LocalRepoDescriptor usageRepo =
                    repositoryService.localOrFederatedRepoDescriptorByKey(USAGE_REPO_KEY);
            res.add(new RepositoryNode(usageRepo.getKey(), usageRepo.getType(), "usageRepo"));
        }
        return new ContinueResult<>(skip > 0 ? "0" : "1", res);
    }

    private boolean isUsageRepoVisible(int skip, int limit) {
        return getSystemRepo(USAGE_REPO_KEY).isVisible() && skip == 0 && limit > 0;
    }

    public ContinueResult<RepositoryNode> getLogShippingRepo(int skip, int limit) {
        List<RepositoryNode> res = Lists.newArrayList();
        if (isLogShippingVisible(skip, limit)) {
            LocalRepoDescriptor logShipping =
                    repositoryService.localOrFederatedRepoDescriptorByKey(LOG_SHIPPING_REPO_KEY);
            res.add(new RepositoryNode(logShipping.getKey(), logShipping.getType(), "logshipping"));
        }
        return new ContinueResult<>(skip > 0 ? "0" : "1", res);
    }

    private boolean isLogShippingVisible(int skip, int limit) {
        return getSystemRepo(LOG_SHIPPING_REPO_KEY).isVisible() && skip == 0 && limit > 0 && securityService.isAdmin();
    }

    public ContinueResult<RepositoryNode> getReleaseBundlesRepoNodes(int skip, int limit) {
        List<RepoConfig> repoConfigs = repositoryConfigService.getRepoConfigsByType(RepoDetailsType.RELEASE_BUNDLE);
        List<RepoConfig> originalSortedBundlesRepos = sortAndGetRepoConfigs(repoConfigs);
        Integer adjustedLimit = adjustLimitInCaseShowRepoInTree(treeFilter, originalSortedBundlesRepos, limit);
        List<RepoConfig> bundlesRepoConfigs = StreamSupportUtils.stream(originalSortedBundlesRepos)
                .skip(skip)
                .filter(repo -> descriptorFilter.accept(repo, getPermissions()))
                .limit(adjustedLimit)
                .toList();
        int lastIndex = getLastIndex(originalSortedBundlesRepos, bundlesRepoConfigs, adjustedLimit);
        return new ContinueResult<>(lastIndex, getReleaseBundlesNodes(bundlesRepoConfigs));
    }

    private List<RepositoryNode> getReleaseBundlesNodes(List<RepoConfig> repos) {
        List<RepositoryNode> items = new ArrayList<>(repos.size());
        repos.forEach(repo -> items.add(new RepositoryNode(repo.getKey(), repo.getPackageType(), RELEASE_BUNDLE_REPO)));
        return items;
    }

    private Set<ArtifactoryPermission> computePermissions() {
        return ConstantValues.treeBrowserCheckOnlyReadPermissionOnRootLevel.getBoolean() ?
                Set.of(ArtifactoryPermission.READ) : Set.of(ArtifactoryPermission.values());
    }
}
