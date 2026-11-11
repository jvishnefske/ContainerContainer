package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service;

import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.ArtifactFilter;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.BaseNode;

import jakarta.annotation.Nonnull;
import java.util.stream.Stream;

/**
 * The StreamArtifactsService interface provides methods to fetch local, remote, and virtual repository nodes.
 * Each method returns a Stream of BaseNode objects, which represent the children.
 * The nodes are filtered based on the provided ArtifactFilter.
 *
 * @author Hussein Alshqerat
 */
public interface StreamArtifactsService {

    @Nonnull
    Stream<BaseNode> getNodesStreamFromLocal(@Nonnull ArtifactFilter artifactFilter);

    @Nonnull
    Stream<BaseNode> getNodesStreamFromRemote(@Nonnull ArtifactFilter artifactFilter);

    @Nonnull
    Stream<BaseNode> getNodesStreamFromVirtual(@Nonnull ArtifactFilter artifactFilter);
}