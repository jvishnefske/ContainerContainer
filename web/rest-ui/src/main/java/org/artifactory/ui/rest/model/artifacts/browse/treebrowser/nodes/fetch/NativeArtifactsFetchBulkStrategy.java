package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import lombok.NoArgsConstructor;
import org.artifactory.api.repo.BaseBrowsableItem;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * This class representing a strategy for fetching artifacts from list of nodes for the native browser.
 * This strategy fetches a bulk of artifacts based on the specified skip and limit parameters.
 *
 * @author Hussein Alshqerat
 */
@NoArgsConstructor
public class NativeArtifactsFetchBulkStrategy implements ArtifactsFetchStrategy<BaseBrowsableItem> {

    @Nonnull
    @Override
    public List<FetchFunction<BaseBrowsableItem>> getFetchFunction(List<BaseBrowsableItem> items) {
        return List.of(new FetchFunction<>((skip, limit) -> {
            List<BaseBrowsableItem> artifacts =
                    StreamSupportUtils
                            .stream(items)
                            .skip(skip)
                            .limit(limit)
                            .toList();
            return new ContinueResult<>(skip + limit, artifacts);
        }, (long) items.size()));
    }
}
