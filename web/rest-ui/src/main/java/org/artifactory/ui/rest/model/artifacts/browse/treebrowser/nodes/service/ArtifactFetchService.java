package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service;

import org.artifactory.rest.common.model.RestModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.ArtifactFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchStrategy;
import org.jfrog.commons.continues.ContinueResult;

import jakarta.annotation.Nonnull;

/**
 * @author Hussein Alshqerat
 */
public interface ArtifactFetchService {

    /**
     * Fetches items based on the specified artifacts fetch strategy, artifact filter, compact mode, and archive flag.
     *
     * @param artifactsFetchStrategy The strategy to fetch artifacts.
     * @param artifactFilter         The filter to apply when fetching artifacts.
     * @param isCompact              A flag indicating whether to fetch items in compact mode.
     * @param isArchive              A flag indicating whether to fetch items as archives.
     * @return A {@link ContinueResult} containing items of type {@link RestModel}.
     */
    @Nonnull
    ContinueResult<? extends RestModel> fetchItems(ArtifactsFetchStrategy<?> artifactsFetchStrategy,
            ArtifactFilter artifactFilter, boolean isCompact, boolean isArchive);

    /**
     * Fetches items based on the specified artifact filter, compact mode, and archive flag.
     * This method is used to fetch items in a stream-based manner.
     *
     * @param artifactFilter   The filter to apply when fetching artifacts.
     * @param isCompact        A flag indicating whether to fetch items in compact mode.
     * @param isArchiveRequest A flag indicating whether to fetch items as archives.
     * @return A {@link ContinueResult} containing items of type {@link RestModel}.
     */
    @Nonnull
    ContinueResult<? extends RestModel> fetchItemsStreamBased(ArtifactFilter artifactFilter,
            boolean isCompact, boolean isArchiveRequest);
}
