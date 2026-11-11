package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.jfrog.commons.continues.FetchFunction;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * An interface representing a strategy for fetching artifacts from list of nodes.
 *
 * @author Hussein Alshqerat
 */
public interface ArtifactsFetchStrategy<T> {

    /**
     * Gets the fetch function that retrieves artifacts.
     *
     * @param nodes A list of {@link BaseNode} objects representing nodes for which artifacts should be fetched.
     * @return A list of {@link FetchFunction} objects, each capable of fetching artifacts for a specific node.
     */
    @Nonnull
    List<FetchFunction<T>> getFetchFunction(List<T> nodes);
}
