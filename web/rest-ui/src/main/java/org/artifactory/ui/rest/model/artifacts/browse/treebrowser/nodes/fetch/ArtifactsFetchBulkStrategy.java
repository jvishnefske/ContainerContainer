package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Implementation of {@link ArtifactsFetchStrategy} that fetches artifacts in bulk from the provided list of nodes.
 * This strategy fetches a bulk of artifacts based on the specified skip and limit parameters.
 *
 * @author Hussein Alshqerat
 */
@Slf4j
@NoArgsConstructor
public class ArtifactsFetchBulkStrategy implements ArtifactsFetchStrategy<BaseNode> {

    /**
     * @param nodes A list of {@link BaseNode} objects representing nodes for which artifacts should be fetched.
     * @return A list of {@link FetchFunction} objects, each capable of fetching artifacts for a specific node.
     * The returned list contains a single {@link FetchFunction} that fetches artifacts in bulk from
     * the provided list of nodes based on skip and limit parameters.
     */
    @Nonnull
    @Override
    public List<FetchFunction<BaseNode>> getFetchFunction(List<BaseNode> nodes) {
        return List.of(new FetchFunction<>((skip, limit) -> {
            List<BaseNode> artifacts =
                    StreamSupportUtils
                            .stream(nodes)
                            .skip(skip)
                            .limit(limit)
                            .toList();
            return new ContinueResult<>(skip + limit, artifacts);
        }, (long) nodes.size()));
    }
}