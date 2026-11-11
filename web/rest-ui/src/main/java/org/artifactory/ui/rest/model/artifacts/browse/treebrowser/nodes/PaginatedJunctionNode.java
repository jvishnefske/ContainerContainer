package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchBulkStrategy;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchDirectBulkStrategy;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchStrategy;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.ArtifactFetchService;
import org.artifactory.ui.rest.model.continuous.dtos.ContinueArtifactsDto;
import org.artifactory.ui.rest.model.continuous.translators.ContinueArtifactsTranslator;
import org.jfrog.commons.continues.ContinueResult;

import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.common.ConstantValues.treeBrowserStreamEnabled;
import static org.artifactory.common.ConstantValues.uiItemsBrowserPagingLimit;
import static org.artifactory.repo.RepoDetailsType.CACHED_REPO;
import static org.artifactory.repo.RepoDetailsType.DISTRIBUTION_REPO;
import static org.artifactory.repo.RepoDetailsType.FEDERATED_REPO;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.repo.RepoDetailsType.SUPPORT_BUNDLES_REPO;

/**
 * @author Alshqerat Hussein
 */
@Slf4j
@JsonTypeName("paginatedJunction")
public class PaginatedJunctionNode implements RestTreeNode {

    private static final Set<String> LOCAL_FEDERATED_CACHED_REPOS =
            Set.of(LOCAL_REPO, CACHED_REPO, FEDERATED_REPO, SUPPORT_BUNDLES_REPO, DISTRIBUTION_REPO,
                    RELEASE_BUNDLE_REPO);
    @Getter
    private final ArtifactFetchService artifactFetchService;
    private final CentralConfigService centralConfigService;
    @Setter
    @JsonUnwrapped
    private ContinueArtifactsDto continueArtifactsDto;

    public PaginatedJunctionNode() {
        ArtifactoryContext context = ContextHelper.get();
        artifactFetchService = context.beanForType(ArtifactFetchService.class);
        centralConfigService = context.beanForType(CentralConfigService.class);
    }

    @Override
    public ContinueResult<? extends RestModel> fetchItemTypeData(boolean isCompact, boolean isArchiveRequest) {
        addLimitIfNeeded(continueArtifactsDto);
        ArtifactFilter artifactFilter = ContinueArtifactsTranslator.toContinueArtifactFilter(continueArtifactsDto);
        if (StringUtils.isNotEmpty(artifactFilter.getRepoPkgType())) {
            CurrentActivity.PACKAGE.put(artifactFilter.getRepoPkgType().toLowerCase());
        }
        if (StringUtils.isNotEmpty(artifactFilter.getMustInclude())) {
            CurrentActivity.GROUP.put(RestApiGroup.BROWSE_SHOW_IN_TREE_API);
        }
        if (treeBrowserStreamEnabled.getBoolean()) {
            return artifactFetchService.fetchItemsStreamBased(artifactFilter, isCompact, isArchiveRequest);
        }
        ArtifactsFetchStrategy<?> artifactsFetchStrategy = chooseStrategy(artifactFilter, isCompact);
        return artifactFetchService.fetchItems(artifactsFetchStrategy, artifactFilter, isCompact, isArchiveRequest);
    }

    private void addLimitIfNeeded(ContinueArtifactsDto continueArtifactsDto) {
        if (continueArtifactsDto.getLimit() == null || continueArtifactsDto.getLimit() <= 0L
                || continueArtifactsDto.getLimit() > uiItemsBrowserPagingLimit.getInt()) {
            MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
            continueArtifactsDto.setLimit((long) mutableDescriptor.getItemsBrowserPagingSize());
        }
    }

    @VisibleForTesting
    ArtifactsFetchStrategy<BaseNode> chooseStrategy(ArtifactFilter artifactFilter, boolean isCompact) {
        String nodeName = artifactFilter.getMustInclude();
        boolean isDirectFetch = isNotBlank(nodeName);
        if (isDirectFetch) {
            return new ArtifactsFetchDirectBulkStrategy(nodeName, isCompact);
        } else {
            return new ArtifactsFetchBulkStrategy();
        }
    }

    @Override
    public boolean isArchiveExpendRequest() {
        return isLocalFederatedOrCachedRepo() && !continueArtifactsDto.isFolder() && continueArtifactsDto.isArchive();
    }

    private boolean isLocalFederatedOrCachedRepo() {
        return LOCAL_FEDERATED_CACHED_REPOS.contains(continueArtifactsDto.getRepoType());
    }
}