package org.artifactory.aql;

import lombok.Getter;

import java.sql.SQLException;

/**
 * Thrown by {@code LimitedResultSet#next()} when the universal row-cap is configured in blocking
 * mode and result-set iteration exceeds the configured limit.
 * <p>
 * Extends {@link SQLException} so that existing {@code catch (SQLException e)} sites remain
 * unchanged. Callers that need to distinguish a blocked query from a genuine DB error can add a
 * narrower {@code catch (QueryBlockedException e)} clause before the generic one.
 * <p>
 * Lives in {@code base/api} so that both {@code storage/db} (throw site) and {@code web/rest}
 * (mapper) can reference it without introducing a cross-layer module dependency.
 *
 * @author Eli Fishman
 */
@Getter
public class QueryBlockedException extends SQLException {

    private final long rowsCount;
    private final int limit;
    private final String query;

    public QueryBlockedException(long rowsCount, int limit, String query) {
        super("Query blocked: result set exceeded the universal row limit of " + limit +
                " (actual rows seen: " + rowsCount + ")");
        this.rowsCount = rowsCount;
        this.limit = limit;
        this.query = query;
    }

}
