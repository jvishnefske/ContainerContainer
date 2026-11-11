package org.artifactory.ui.rest.service.artifacts.search.syntax;

/**
 * @author Igor Usenko
 */
public record Repository(String repo, String type, String packageType, long modified, long created) {
}
