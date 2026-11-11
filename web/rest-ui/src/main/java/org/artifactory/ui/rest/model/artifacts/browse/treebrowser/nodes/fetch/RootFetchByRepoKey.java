package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import com.google.common.collect.Iterables;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.TreeFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.glassfish.jersey.internal.guava.Lists;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;

import java.util.List;
import java.util.Map;

/**
 * @author Omri Ziv
 */
@Slf4j
public class RootFetchByRepoKey extends RootFetchStrategy {

    public RootFetchByRepoKey(TreeFilter treeFilter) {
        super(treeFilter);
    }

    @Override
    protected List<FetchFunction<RepositoryNode>> getFetchFunctions() {
        List<FetchFunction<RepositoryNode>> superFunctions = super.getFetchFunctions();
        Map<Character, List<String>> repoKeysByFirstCharMap =
                repositoryService.getAllRepoKeysByFirstCharMapAndProject(projectKey);
        List<FetchFunction<RepositoryNode>> functionsByFirstRepoChar =
                StreamSupportUtils.stream(repoKeysByFirstCharMap.keySet())
                        .map(c -> getFetchFunctionByFirstRepoChar(repoKeysByFirstCharMap, c))
                        .toList();
        List<FetchFunction<RepositoryNode>> allFunctions =
                Lists.newArrayList(Iterables.concat(superFunctions, functionsByFirstRepoChar));
        log.debug("{} Candidates Fetch Function", allFunctions.size());
        return allFunctions;
    }

    private FetchFunction<RepositoryNode> getFetchFunctionByFirstRepoChar(
            Map<Character, List<String>> repoKeysByFirstCharMap, Character c) {
        List<String> filteredSortedRepoByChar = StreamSupportUtils.stream(repoKeysByFirstCharMap.get(c))
                .filter(key -> !(repositoryService.repoDescriptorByKey(key) instanceof VirtualRepoDescriptor)
                        || virtualRepoVisibility.test(repositoryConfigService.getRepoConfigByKey(key)))
                .sorted()
                .toList();
        return new FetchFunction<>((skip, limit) -> {
            Integer adjustedLimit =
                    adjustLimitInCaseShowRepoInTree(treeFilter, filteredSortedRepoByChar, limit);
            List<RepoDescriptor> repoDescriptors = StreamSupportUtils.stream(filteredSortedRepoByChar)
                    .skip(skip)
                    .map(repositoryService::repoDescriptorByKey)
                    .filter(repo -> descriptorFilter.accept(repo, fetchService.getPermissions()))
                    .limit(adjustedLimit)
                    .toList();
            int lastIndex = getLastIndexOfKeys(filteredSortedRepoByChar, repoDescriptors, adjustedLimit);
            return new ContinueResult<>(lastIndex, getNodes(repoDescriptors));
        }, (long) filteredSortedRepoByChar.size());
    }
}
