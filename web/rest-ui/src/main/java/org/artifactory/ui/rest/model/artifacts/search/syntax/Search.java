package org.artifactory.ui.rest.model.artifacts.search.syntax;

/**
 * @author Igor Usenko
 */
public record Search(
        Query query,
        int limit,
        State state,
        Filters filters,
        boolean isExactMatch
) {
}
