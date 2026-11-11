package org.artifactory.metadata.service;

import org.artifactory.version.ArtifactoryServiceVersion;

/**
 * Metadata Server related conversions
 *
 * @author Uriah Levy
 */
public interface MetadataConverter {
    void convert();

    default boolean isInterested(ArtifactoryServiceVersion fromVersion) {
        return fromVersion != null && !fromVersion.isCurrent();
    }
}