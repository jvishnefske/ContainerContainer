package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.artifactory.ui.rest.model.artifacts.search.syntax.State;
import org.jfrog.storage.JdbcHelper;

import java.sql.SQLException;
import java.util.List;

/**
 * @author Igor Usenko
 */
public interface SearchFunctionProvider {

    List<Repository> queryRepositories(Query query, Filters filters, int limit, State state, boolean isExactMatch,
            JdbcHelper jdbcHelper, Deadline deadline)
            throws SQLException;

    List<Node> queryFolders(Query query, Filters filters, int limit, State state, boolean isExactMatch,
            JdbcHelper jdbcHelper, Deadline deadline)
            throws SQLException;

    List<Node> queryFiles(Query query, Filters filters, int limit, State state, boolean isExactMatch,
            JdbcHelper jdbcHelper, Deadline deadline)
            throws SQLException;

}
