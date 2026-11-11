package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import com.google.common.collect.Iterables;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.artifactory.util.CollectionUtils;
import org.glassfish.jersey.internal.guava.Lists;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;

import java.util.List;

import static org.jfrog.commons.continues.util.ContinueResultIndexUtils.getLastIndex;

/**
 * @author Omri Ziv
 */
@Slf4j
public class RootFetchByPackageType extends RootFetchStrategy {

    public RootFetchByPackageType(TreeFilter treeFilter) {
        super(treeFilter);
    }

    @Override
    protected List<FetchFunction<RepositoryNode>> getFetchFunctions() {
        List<FetchFunction<RepositoryNode>> superFunctions = super.getFetchFunctions();
        List<FetchFunction<RepositoryNode>> functionByPackageTypes =
                StreamSupportUtils
                        .mapEntriesStream(repositoryService.getRepoDescriptorByPackageTypeCountAndProject(projectKey))
                        .filter(entry -> CollectionUtils.isNullOrEmpty(treeFilter.getPackageTypes()) ||
                                treeFilter.getPackageTypes().contains(entry.getKey()))
                        .map(entry -> byType(entry.getKey()))
                        .toList();
        List<FetchFunction<RepositoryNode>> allFunctions =
                Lists.newArrayList(Iterables.concat(superFunctions, functionByPackageTypes));
        log.debug("{} Candidates Fetch Function", allFunctions.size());
        return allFunctions;
    }

    private FetchFunction<RepositoryNode> byType(RepoType packageType) {
        List<RepoConfig> sortedRepoConfigs = StreamSupportUtils
                .stream(repositoryService.getRepoConfigsByPackageTypeAndProject(packageType, projectKey))
                .filter(virtualRepoVisibility)
                .toList();
        return new FetchFunction<>((skip, limit) -> {
            Integer adjustedLimit = adjustLimitInCaseShowRepoInTree(treeFilter, sortedRepoConfigs, limit);
            List<RepoConfig> allRepoConfigs = StreamSupportUtils.stream(sortedRepoConfigs)
                    .skip(skip)
                    .filter(repo -> descriptorFilter.accept(repo, fetchService.getPermissions()))
                    .limit(adjustedLimit)
                    .toList();
            int lastIndex = getLastIndex(sortedRepoConfigs, allRepoConfigs, adjustedLimit);
            return new ContinueResult<>(lastIndex, getNodesFromRepoConfigs(allRepoConfigs));
        }, (long) sortedRepoConfigs.size());
    }
}
