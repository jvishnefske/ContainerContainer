package org.artifactory.ui.rest.model.artifacts.search.syntax;

import java.util.List;

/**
 * @author Igor Usenko
 */
public record Response(List<Item> results, State state, boolean isPartialResponse, String traceId) {
}
