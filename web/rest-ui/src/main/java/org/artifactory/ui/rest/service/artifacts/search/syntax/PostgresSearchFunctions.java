package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.artifactory.ui.rest.model.artifacts.search.syntax.State;
import org.jfrog.storage.JdbcHelper;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.List;

/**
 * @author Igor Usenko
 */
@Component
class PostgresSearchFunctions implements SearchFunctionProvider {

    @Override
    public List<Repository> queryRepositories(Query query, Filters filters, int limit, State state,
            boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
        return SyntaxSearchQueryUtils.queryRepositories(query, filters, limit, state, isExactMatch, jdbcHelper,
                deadline, SyntaxSearchQueryUtils::getRepositories);
    }

    @Override
    public List<Node> queryFolders(Query query, Filters filters, int limit, State state, boolean isExactMatch,
            JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
        return SyntaxSearchQueryUtils.queryPaths(query, filters, limit, state, isExactMatch, jdbcHelper,
                deadline, SyntaxSearchQueryUtils::getNodes);
    }

    @Override
    public List<Node> queryFiles(Query query, Filters filters, int limit, State state, boolean isExactMatch,
            JdbcHelper jdbcHelper, Deadline deadline) throws SQLException {
        return SyntaxSearchQueryUtils.queryFiles(query, filters, limit, state, isExactMatch, jdbcHelper,
                deadline, SyntaxSearchQueryUtils::getNodes);
    }

}
