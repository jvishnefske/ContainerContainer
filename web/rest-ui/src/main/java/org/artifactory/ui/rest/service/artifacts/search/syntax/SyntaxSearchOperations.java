package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Search;
import org.artifactory.ui.rest.model.artifacts.search.syntax.State;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Igor Usenko
 */
public interface SyntaxSearchOperations {

    void execute(ArtifactoryRestRequest<Search> request, RestResponse response);

    void setSearchFunctionProvider(SearchFunctionProvider searchFunctionProvider);

    /**
     * Runs a single repository query in its own short-lived read-only transaction. This is the per-query unit
     * shared by both the full-results path (raced across planner variants) and the autocomplete path
     * (single-threaded): a timeout or abort in one query cannot poison any other query.
     */
    @Transactional(readOnly = true)
    StageResult<Repository> runRepoQuery(boolean disableIndexes, Query query, Filters filters, int limit,
            State state, boolean isExactMatch, Deadline deadline);

    /**
     * Runs a single folder/file query in its own short-lived read-only transaction.
     *
     * @param isFile {@code true} for the file stage, {@code false} for the folder stage.
     */
    @Transactional(readOnly = true)
    StageResult<Node> runNodeQuery(boolean disableIndexes, boolean isFile, Query query, Filters filters, int limit,
            State state, boolean isExactMatch, Deadline deadline);

    /**
     * Result of a single query execution: the returned rows, whether the query timed out, and which planner
     * variant produced it (so subsequent pagination chunks can reuse the winning plan).
     */
    record StageResult<T>(List<T> rows, boolean hasTimeout, boolean disableIndexesUsed) {
    }
}
