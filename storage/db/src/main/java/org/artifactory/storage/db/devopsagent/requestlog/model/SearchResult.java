package org.artifactory.storage.db.devopsagent.requestlog.model;

import java.util.List;

/**
 * Generic search result envelope. {@code hasMore} is true when the DAO returned
 * {@code limit + 1} rows; the service trims the extra row and emits {@code nextCursor} for
 * the agent to resume from.
 */
public record SearchResult<T>(List<T> rows, boolean hasMore, Cursor nextCursor) {
}
