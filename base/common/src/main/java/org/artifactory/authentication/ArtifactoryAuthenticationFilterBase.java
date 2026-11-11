package org.artifactory.authentication;

import jakarta.servlet.Filter;

/**
 * Author: einasa
 */
public interface ArtifactoryAuthenticationFilterBase extends Filter {
    /**
     * There are two types of artifactory authentication filters
     * ArtifactoryAuthenticationFilter and ArtifactoryAuthenticationContextFilter, both extends filter
     * The goal is to have intermediate class between ArtifactoryAuthenticationFilter,
     * ArtifactoryAuthenticationContextFilter and filter
     * The goal getType is hold the class name of each class that implement ArtifactoryAuthenticationFilter,
     * ArtifactoryAuthenticationContextFilter
     *
     * @return Enum class name of the filter
     */
    ArtifactoryAuthenticationType getType();
}
