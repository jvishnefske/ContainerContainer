package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import lombok.AllArgsConstructor;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;
import org.jfrog.commons.continues.ContinueResult;
import org.jfrog.commons.continues.FetchFunction;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of {@link ArtifactsFetchStrategy} that fetches artifacts by directly specifying a node name.
 * This strategy fetches a bulk of artifacts centered around the specified node name within the provided list of nodes.
 *
 * @author Hussein Alshqerat
 */
@AllArgsConstructor
public class ArtifactsFetchDirectBulkStrategy implements ArtifactsFetchStrategy<BaseNode> {

    /**
     * The name of the node around which artifacts will be fetched.
     */
    private String nodeName;
    private boolean isCompacted;

    /**
     * Retrieves a list of {@link FetchFunction} objects, each capable of fetching artifacts for a specific node within
     * the provided list of nodes.
     *
     * @param nodes A list of {@link BaseNode} objects representing nodes for which artifacts should be fetched.
     * @return A list containing a single {@link FetchFunction} that fetches artifacts centered around
     * the specified node name within the provided list of nodes. If the specified node name is not found in the list,
     * an empty list of artifacts will be returned.
     */
    @Nonnull
    @Override
    public List<FetchFunction<BaseNode>> getFetchFunction(List<BaseNode> nodes) {
        return List.of(new FetchFunction<>((skip, limit) -> {
            int index = findNodeIndex(nodes);
            if (index == -1) {
                // Node with the specified name not found in the list
                return new ContinueResult<>(0, Collections.emptyList());
            }
            int startIndex = Math.max(0, index - limit / 2);
            int endIndex = Math.min(nodes.size(), startIndex + limit);
            if (endIndex - startIndex < limit) {
                // Adjust startIndex if the sublist is smaller than the limit
                startIndex = Math.max(0, endIndex - limit);
            }
            List<BaseNode> resultNodes = nodes.subList(startIndex, endIndex);
            return new ContinueResult<>(endIndex, resultNodes);
        }, (long) nodes.size()));
    }

    /**
     * Finds the index of the node with the specified name within the given list of nodes.
     *
     * @param nodes A list of {@link BaseNode} objects to search for the specified node name.
     * @return The index of the node with the specified name, or -1 if not found.
     */
    private int findNodeIndex(List<BaseNode> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            BaseNode currentNode = nodes.get(i);
            boolean matchesNodeName;
            if (isCompacted) {
                matchesNodeName = currentNode.isFolder() ?
                        (currentNode.getPath().startsWith(nodeName) || currentNode.getPath().endsWith(nodeName)) :
                        currentNode.getText().equals(nodeName);
            } else {
                matchesNodeName = currentNode.getText().equals(nodeName);
            }
            if (matchesNodeName) {
                return i;
            }
        }
        return -1;
    }
}
