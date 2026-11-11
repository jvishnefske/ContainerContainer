package org.artifactory.ui.rest.service.artifacts.search.syntax;

/**
 * @author Igor Usenko
 */
public record Node(long nodeId, String repo, String path, String name, long binLength, long modified, long created) {
}
