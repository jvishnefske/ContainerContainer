package org.artifactory.storage.db.util.limit;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.QueryBlockedException;
import org.jfrog.storage.wrapper.ResultSetProxy;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Eli Fishman
 */
@Slf4j
@Getter
public class LimitedResultSet extends ResultSetProxy {

    private final int limit;
    private final boolean toBlock;
    private long rowsCount = 0L;
    private boolean alerted = false;

    public LimitedResultSet(@Nonnull ResultSet resultSet, int limit, boolean toBlock, String query) {
        super(resultSet);
        this.limit = limit;
        this.toBlock = toBlock;
        this.query = query;
    }

    @Override
    public boolean next() throws SQLException {
        if (super.next()) {
            if (++this.rowsCount > limit) {
                if (toBlock) {
                    throw new QueryBlockedException(rowsCount, limit, query);
                } else if (!alerted) {
                    log.warn("Results set has come to the universal limit of {} rows, " +
                                    "but the query is still running. " +
                                    "This may lead to performance issues or unexpected behavior. " +
                                    "The query is {}",
                            limit,
                            query);
                    alerted = true;
                }
            }
            return true;
        }
        return false;
    }
}
