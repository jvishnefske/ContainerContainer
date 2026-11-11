package org.artifactory.unifiedevent.api.request;

import lombok.Builder;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.request.Request;

/**
 * ArtifactResourceContext is a wiring object used to transfer resource information
 * between the core and the unified event modules in Artifactory. It encapsulates
 * the {@link RepoResource}, {@link Properties}, and {@link Request} associated with
 * an artifact, providing a unified context for event processing and resource handling.
 * <p>
 * This record is typically used to pass artifact-related data between different layers
 * or modules, ensuring consistency and reducing coupling between core and event systems.
 */
@Builder
public record ArtifactResourceContext(
        RepoResource repoResource,
        Properties properties,
        Request artifactoryRequest
) {
}
